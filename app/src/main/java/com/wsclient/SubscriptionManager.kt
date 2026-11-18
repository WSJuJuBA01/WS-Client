package com.wsclient

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object SubscriptionManager {

    suspend fun update(context: Context, url: String): Int =
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
                String(Base64.decode(body.trim(), Base64.DEFAULT))
            }.getOrDefault(body)

            val profiles = decoded
                .lineSequence()
                .map { it.trim() }
                .filter { it.contains("://") }
                .mapNotNull { runCatching { ProfileCodec.parse(it) }.getOrNull() }
                .toList()

            profiles.forEach { ProfileStore.add(context, it) }
            profiles.size
        }
}
