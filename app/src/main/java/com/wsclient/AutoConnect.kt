package com.wsclient

import android.content.Context

object AutoConnect {
    private const val PREFS = "ws_settings"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, 0)
            .getBoolean("auto_connect", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, 0)
            .edit()
            .putBoolean("auto_connect", enabled)
            .apply()
    }
}
