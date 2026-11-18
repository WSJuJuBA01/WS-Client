
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

        return try {
            val array = JSONArray(raw)
            val profiles = mutableListOf<ServerProfile>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue

                profiles.add(
                    ServerProfile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        protocol = item.getString("protocol"),
                        address = item.getString("address"),
                        port = item.getInt("port"),
                        rawLink = item.optString("rawLink", ""),
                        config = item.optJSONObject("config") ?: JSONObject()
                    )
                )
            }

            profiles
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, profile: ServerProfile) {
        val profiles = getAll(context)
            .filterNot { it.id == profile.id }
            .toMutableList()

        profiles.add(profile)
        save(context, profiles)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val profiles = getAll(context)
            .filterNot { it.id == id }

        save(context, profiles)
    }

    private fun save(
        context: Context,
        profiles: List<ServerProfile>
    ) {
        val array = JSONArray()

        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("protocol", profile.protocol)
                    put("address", profile.address)
                    put("port", profile.port)
                    put("rawLink", profile.rawLink)
                    put("config", profile.config)
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
