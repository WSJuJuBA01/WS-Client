package com.wsclient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProfileStore {
    private const val PREFS_NAME = "ws_profiles"
    private const val KEY_PROFILES = "profiles"

    @Synchronized
    fun getAll(context: Context): List<ServerProfile> {
        val raw = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_PROFILES, null) ?: return emptyList()

        val array = runCatching {
            JSONArray(raw)
        }.getOrNull() ?: return emptyList()

        val result = mutableListOf<ServerProfile>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val profile = runCatching {
                decode(item)
            }.getOrNull() ?: continue

            if (result.none { sameConnection(it, profile) }) {
                result += profile
            }
        }

        return result
    }

    @Synchronized
    fun add(
        context: Context,
        profile: ServerProfile
    ): ServerProfile {
        val profiles = getAll(context).toMutableList()

        val index = profiles.indexOfFirst {
            sameConnection(it, profile)
        }

        val stored = if (index >= 0) {
            profile.copy(id = profiles[index].id)
        } else {
            profile
        }

        if (index >= 0) {
            profiles[index] = stored
        } else {
            profiles += stored
        }

        save(context, profiles)
        return stored
    }

    @Synchronized
    fun addAll(
        context: Context,
        incoming: List<ServerProfile>
    ) {
        if (incoming.isEmpty()) return

        val profiles = getAll(context).toMutableList()

        incoming.forEach { candidate ->
            val index = profiles.indexOfFirst {
                sameConnection(it, candidate)
            }

            if (index >= 0) {
                profiles[index] = candidate.copy(
                    id = profiles[index].id
                )
            } else {
                profiles += candidate
            }
        }

        save(context, profiles)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        save(
            context,
            getAll(context).filterNot { it.id == id }
        )
    }

    fun sameConnection(
        a: ServerProfile,
        b: ServerProfile
    ): Boolean =
        a.protocol.equals(b.protocol, true) &&
            a.address.equals(b.address, true) &&
            a.port == b.port &&
            canonicalJson(a.config) == canonicalJson(b.config)

    private fun canonicalJson(value: JSONObject): String {
        fun canonical(v: Any?): String =
            when (v) {
                is JSONObject -> v.keys()
                    .asSequence()
                    .toList()
                    .sorted()
                    .joinToString(",", "{", "}") { key ->
                        JSONObject.quote(key) +
                            ":" +
                            canonical(v.opt(key))
                    }

                is JSONArray -> (0 until v.length())
                    .joinToString(",", "[", "]") {
                        canonical(v.opt(it))
                    }

                JSONObject.NULL -> "null"
                is String -> JSONObject.quote(v)
                else -> v.toString()
            }

        return canonical(value)
    }

    private fun decode(item: JSONObject): ServerProfile {
        val id = item.optString("id").trim()
        val protocol = item.optString("protocol").trim()
        val address = item.optString("address").trim()
        val port = item.optInt("port", -1)

        require(
            id.isNotEmpty() &&
                protocol.isNotEmpty() &&
                address.isNotEmpty() &&
                port in 1..65535
        )

        val config = item.optJSONObject("config")
            ?: error("Missing profile config")

        return ServerProfile(
            id,
            item.optString("name")
                .ifBlank { "$protocol · $address" },
            protocol,
            address,
            port,
            item.optString("rawLink", ""),
            JSONObject(config.toString())
        )
    }

    private fun save(
        context: Context,
        profiles: List<ServerProfile>
    ) {
        val array = JSONArray()

        profiles.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("protocol", p.protocol)
                    put("address", p.address)
                    put("port", p.port)
                    put("rawLink", p.rawLink)
                    put(
                        "config",
                        JSONObject(p.config.toString())
                    )
                }
            )
        }

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_PROFILES, array.toString())
            .apply()
    }
}
