package com.wsclient

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class SubscriptionUpdateResult(
    val imported: Int,
    val profiles: List<ServerProfile>
)

object SubscriptionManager {

    suspend fun update(
        context: Context,
        url: String
    ): Int = updateDetailed(context, url).imported

    suspend fun updateDetailed(
        context: Context,
        url: String
    ): SubscriptionUpdateResult =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty(
                "User-Agent",
                SubscriptionIdentity.userAgent(context)
            )

            val body = connection.getInputStream()
                .bufferedReader()
                .use { it.readText() }

            val decoded = runCatching {
                String(
                    Base64.decode(
                        body.trim(),
                        Base64.DEFAULT
                    )
                )
            }.getOrDefault(body)

            val profiles = decoded.lineSequence()
                .map { it.trim() }
                .filter { it.contains("://") }
                .mapNotNull {
                    runCatching {
                        ProfileCodec.parse(it)
                    }.getOrNull()
                }
                .toList()

            profiles.forEach {
                ProfileStore.add(context, it)
            }

            SubscriptionStore.upsert(
                context,
                url,
                profiles.mapNotNull { incoming ->
                    ProfileStore.getAll(context).firstOrNull {
                        ProfileStore.sameConnection(it, incoming)
                    }
                }
            )

            SubscriptionUpdateResult(
                imported = profiles.size,
                profiles = profiles
            )
        }
}
