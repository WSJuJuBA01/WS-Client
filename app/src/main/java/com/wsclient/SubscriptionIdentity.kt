package com.wsclient

import android.content.Context

object SubscriptionIdentity {
    private const val PREFS = "ws_identity"

    fun userAgent(context: Context): String {
        return context.getSharedPreferences(PREFS, 0)
            .getString("user_agent", "WS Client/1.0")!!
    }

    fun setUserAgent(context: Context, value: String) {
        context.getSharedPreferences(PREFS, 0)
            .edit()
            .putString("user_agent", value)
            .apply()
    }

    fun huid(context: Context): String {
        return context.getSharedPreferences(PREFS, 0)
            .getString("huid", "")!!
    }

    fun setHuid(context: Context, value: String) {
        context.getSharedPreferences(PREFS, 0)
            .edit()
            .putString("huid", value)
            .apply()
    }
}
