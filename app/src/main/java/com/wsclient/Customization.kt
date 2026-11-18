package com.wsclient

import android.content.Context

object Customization {
    private const val PREFS = "ws_settings"
    private const val PASTEL = "pastel_theme"

    fun isPastel(context: Context): Boolean =
        context.getSharedPreferences(PREFS, 0)
            .getBoolean(PASTEL, false)

    fun togglePastel(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        prefs.edit()
            .putBoolean(PASTEL, !prefs.getBoolean(PASTEL, false))
            .apply()
    }
}
