package com.wsclient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

data class SubscriptionGroup(
    val id: String,
    val title: String,
    val url: String,
    val profileIds: Set<String>
)

object SubscriptionStore {
    private const val PREFS = "ws_subscriptions"
    private const val KEY = "groups"

    @Synchronized
    fun all(context: Context): List<SubscriptionGroup> {
        val raw = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getString(KEY, null) ?: return emptyList()

        val array = runCatching {
            JSONArray(raw)
        }.getOrNull() ?: return emptyList()

        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i)
                ?: return@mapNotNull null

            val id = o.optString("id")
            val url = o.optString("url")

            if (id.isBlank() || url.isBlank()) {
                return@mapNotNull null
            }

            val ids = o.optJSONArray("profiles") ?: JSONArray()

            SubscriptionGroup(
                id,
                o.optString("title").ifBlank {
                    hostTitle(url)
                },
                url,
                (0 until ids.length())
                    .mapNotNull {
                        ids.optString(it)
                            .takeIf(String::isNotBlank)
                    }
                    .toSet()
            )
        }
    }

    @Synchronized
    fun upsert(
        context: Context,
        url: String,
        profiles: List<ServerProfile>
    ) {
        val id = idFor(url)
        val existing = all(context).toMutableList()
        val index = existing.indexOfFirst { it.id == id }
        val previous = existing.getOrNull(index)

        val currentIds = profiles.map { it.id }.toSet()

        val group = SubscriptionGroup(
            id,
            previous?.title ?: hostTitle(url),
            url,
            currentIds
        )

        if (index >= 0) {
            existing[index] = group
        } else {
            existing += group
        }

        save(context, existing)
    }

    @Synchronized
    fun rename(
        context: Context,
        id: String,
        title: String
    ) {
        val groups = all(context).map {
            if (it.id == id) {
                it.copy(
                    title = title.trim().ifBlank {
                        hostTitle(it.url)
                    }
                )
            } else {
                it
            }
        }

        save(context, groups)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        save(
            context,
            all(context).filterNot { it.id == id }
        )
    }

    fun idFor(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.trim().toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") {
                "%02x".format(it)
            }

    private fun hostTitle(url: String): String =
        runCatching {
            URI(url).host
                ?.removePrefix("www.")
                ?.ifBlank { null }
                ?: "Подписка"
        }.getOrDefault("Подписка")

    private fun save(
        context: Context,
        groups: List<SubscriptionGroup>
    ) {
        val array = JSONArray()

        groups.forEach { g ->
            array.put(
                JSONObject().apply {
                    put("id", g.id)
                    put("title", g.title)
                    put("url", g.url)
                    put(
                        "profiles",
                        JSONArray(g.profileIds.toList())
                    )
                }
            )
        }

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
