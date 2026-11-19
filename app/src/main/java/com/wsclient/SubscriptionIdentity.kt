package com.wsclient

import android.content.Context
import java.net.URLConnection
import java.security.SecureRandom
import java.util.Locale

data class ClientIdentitySettings(
    val clientPreset: String = "none",
    val userAgent: String = "WS Client/1.0",

    val sendHwid: Boolean = false,
    val customHwid: String = "",

    val emulateDevice: Boolean = false,
    val deviceProfile: String = "SM-S918B · Android 14",
    val deviceModel: String = "SM-S918B",
    val osVersion: String = "14",
    val deviceOs: String = "Android",
    val deviceLocale: String = "ru_MM",

    val appVersion: String = "",
    val accept: String = "*/*",
    val acceptLanguage: String = "ru-MM",
    val clientHeader: String = "",

    val extraHeaders: String = ""
)

object SubscriptionIdentity {

    private const val PREFS = "ws_identity"

    private const val KEY_CLIENT = "client_preset"
    private const val KEY_USER_AGENT = "user_agent"

    private const val KEY_SEND_HWID = "send_hwid"
    private const val KEY_CUSTOM_HWID = "huid"
    private const val KEY_STABLE_HWID = "stable_hwid"

    private const val KEY_EMULATE_DEVICE = "emulate_device"
    private const val KEY_DEVICE_PROFILE = "device_profile"
    private const val KEY_DEVICE_MODEL = "device_model"
    private const val KEY_OS_VERSION = "os_version"
    private const val KEY_DEVICE_OS = "device_os"
    private const val KEY_DEVICE_LOCALE = "device_locale"

    private const val KEY_APP_VERSION = "app_version"
    private const val KEY_ACCEPT = "accept"
    private const val KEY_ACCEPT_LANGUAGE = "accept_language"
    private const val KEY_CLIENT_HEADER = "client_header"
    private const val KEY_EXTRA_HEADERS = "extra_headers"

    private val headerNameRegex =
        Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    fun load(context: Context): ClientIdentitySettings {
        val prefs = context.getSharedPreferences(PREFS, 0)

        return ClientIdentitySettings(
            clientPreset = prefs.getString(
                KEY_CLIENT,
                "none"
            ) ?: "none",

            userAgent = prefs.getString(
                KEY_USER_AGENT,
                "WS Client/1.0"
            ) ?: "WS Client/1.0",

            sendHwid = prefs.getBoolean(
                KEY_SEND_HWID,
                false
            ),

            customHwid = prefs.getString(
                KEY_CUSTOM_HWID,
                ""
            ) ?: "",

            emulateDevice = prefs.getBoolean(
                KEY_EMULATE_DEVICE,
                false
            ),

            deviceProfile = prefs.getString(
                KEY_DEVICE_PROFILE,
                "SM-S918B · Android 14"
            ) ?: "SM-S918B · Android 14",

            deviceModel = prefs.getString(
                KEY_DEVICE_MODEL,
                "SM-S918B"
            ) ?: "SM-S918B",

            osVersion = prefs.getString(
                KEY_OS_VERSION,
                "14"
            ) ?: "14",

            deviceOs = prefs.getString(
                KEY_DEVICE_OS,
                "Android"
            ) ?: "Android",

            deviceLocale = prefs.getString(
                KEY_DEVICE_LOCALE,
                "ru_MM"
            ) ?: "ru_MM",

            appVersion = prefs.getString(
                KEY_APP_VERSION,
                ""
            ) ?: "",

            accept = prefs.getString(
                KEY_ACCEPT,
                "*/*"
            ) ?: "*/*",

            acceptLanguage = prefs.getString(
                KEY_ACCEPT_LANGUAGE,
                "ru-MM"
            ) ?: "ru-MM",

            clientHeader = prefs.getString(
                KEY_CLIENT_HEADER,
                ""
            ) ?: "",

            extraHeaders = prefs.getString(
                KEY_EXTRA_HEADERS,
                ""
            ) ?: ""
        )
    }

    fun save(
        context: Context,
        settings: ClientIdentitySettings
    ) {
        context.getSharedPreferences(PREFS, 0)
            .edit()
            .putString(KEY_CLIENT, settings.clientPreset)
            .putString(KEY_USER_AGENT, settings.userAgent)
            .putBoolean(KEY_SEND_HWID, settings.sendHwid)
            .putString(KEY_CUSTOM_HWID, settings.customHwid)
            .putBoolean(KEY_EMULATE_DEVICE, settings.emulateDevice)
            .putString(KEY_DEVICE_PROFILE, settings.deviceProfile)
            .putString(KEY_DEVICE_MODEL, settings.deviceModel)
            .putString(KEY_OS_VERSION, settings.osVersion)
            .putString(KEY_DEVICE_OS, settings.deviceOs)
            .putString(KEY_DEVICE_LOCALE, settings.deviceLocale)
            .putString(KEY_APP_VERSION, settings.appVersion)
            .putString(KEY_ACCEPT, settings.accept)
            .putString(KEY_ACCEPT_LANGUAGE, settings.acceptLanguage)
            .putString(KEY_CLIENT_HEADER, settings.clientHeader)
            .putString(KEY_EXTRA_HEADERS, settings.extraHeaders)
            .apply()
    }

    fun withClientPreset(
        current: ClientIdentitySettings,
        preset: String
    ): ClientIdentitySettings {
        return when (preset.lowercase(Locale.ROOT)) {
            "happ" -> current.copy(
                clientPreset = "happ",
                userAgent =
                    "Happ/3.26.3/Android/17839452147361875676",
                appVersion = "3.26.3",
                clientHeader = "Happ"
            )

            "v2raytun" -> current.copy(
                clientPreset = "v2raytun",
                userAgent = "v2raytun/android",
                appVersion = "",
                clientHeader = "v2raytun"
            )

            "inocy" -> current.copy(
                clientPreset = "inocy",
                userAgent =
                    "INCY/3.9.9/android Dalvik/2.1.0",
                appVersion = "3.9.9",
                clientHeader = "INCY"
            )

            else -> current.copy(
                clientPreset = "none",
                userAgent = "WS Client/1.0",
                appVersion = "",
                clientHeader = ""
            )
        }
    }

    fun userAgent(context: Context): String {
        return load(context).userAgent
            .ifBlank { "WS Client/1.0" }
    }

    fun setUserAgent(
        context: Context,
        value: String
    ) {
        context.getSharedPreferences(PREFS, 0)
            .edit()
            .putString(KEY_USER_AGENT, value)
            .apply()
    }

    /**
     * Возвращает пользовательский HWID.
     * Пустая строка означает использование локального стабильного ID.
     */
    fun huid(context: Context): String {
        return load(context).customHwid
    }

    fun setHuid(
        context: Context,
        value: String
    ) {
        context.getSharedPreferences(PREFS, 0)
            .edit()
            .putString(KEY_CUSTOM_HWID, value)
            .apply()
    }

    /**
     * Устанавливает HTTP-заголовки запроса подписки.
     * Не меняет сам процесс загрузки и разбора подписки.
     */
    fun applyHeaders(
        context: Context,
        connection: URLConnection
    ) {
        val settings = load(context)
        val headers = linkedMapOf<String, String>()

        fun add(name: String, value: String) {
            val cleaned = value.trim()
            if (cleaned.isNotEmpty()) {
                headers[name] = cleaned
            }
        }

        add(
            "User-Agent",
            settings.userAgent.ifBlank { "WS Client/1.0" }
        )

        add("Accept", settings.accept)
        add("Accept-Language", settings.acceptLanguage)
        add("X-Client", settings.clientHeader)
        add("X-App-Version", settings.appVersion)

        if (settings.sendHwid) {
            val hwid = settings.customHwid
                .trim()
                .ifBlank { stableHwid(context) }

            add("X-HWID", hwid)
        }

        if (settings.emulateDevice) {
            add("X-Device-Model", settings.deviceModel)
            add("X-Ver-Os", settings.osVersion)
            add("X-Device-Os", settings.deviceOs)
            add("X-Device-Locale", settings.deviceLocale)
        }

        parseExtraHeaders(settings.extraHeaders)
            .forEach { (name, value) ->
                headers[name] = value
            }

        headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }
    }

    private fun stableHwid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, 0)

        val existing = prefs.getString(
            KEY_STABLE_HWID,
            null
        )

        if (!existing.isNullOrBlank()) {
            return existing
        }

        val random = ByteArray(8)
        SecureRandom().nextBytes(random)

        val generated = random.joinToString("") {
            "%02X".format(
                Locale.ROOT,
                it.toInt() and 0xFF
            )
        }

        prefs.edit()
            .putString(KEY_STABLE_HWID, generated)
            .apply()

        return generated
    }

    private fun parseExtraHeaders(
        source: String
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()

        source.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach

            val name = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()

            if (!headerNameRegex.matches(name)) {
                return@forEach
            }

            if (
                value.contains('\r') ||
                value.contains('\n')
            ) {
                return@forEach
            }

            when (name.lowercase(Locale.ROOT)) {
                "host",
                "content-length",
                "transfer-encoding" -> return@forEach
            }

            if (value.isNotEmpty()) {
                result[name] = value
            }
        }

        return result
    }
}
