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

/** Заголовки, которые конкретный клиент реально отправляет. */
enum class IdentityField {
    ACCEPT,
    ACCEPT_LANGUAGE,
    CLIENT,
    APP_VERSION,
    DEVICE_MODEL,
    OS_VERSION,
    DEVICE_OS,
    DEVICE_LOCALE
}

/** Формат HWID: у каждого клиента он свой. */
enum class HwidFormat {
    UPPER_HEX16,   // 86B787E5FF81CBDA
    LOWER_HEX16,   // 86b787e5ff81cbda
    UUID_UPPER     // 86B787E5-FF81-CBDA-6963-994FFCC0F2B5
}

data class DeviceProfile(
    val label: String,
    val model: String,
    val osVersion: String,
    val os: String = "Android"
)

data class ClientPreset(
    val id: String,
    val label: String,
    val userAgent: String,
    val hwidFormat: HwidFormat,
    val device: DeviceProfile,
    val fields: Set<IdentityField>,
    val appVersion: String = "",
    val clientHeader: String = "",
    val accept: String = "",
    val acceptLanguage: String = "",
    val deviceLocale: String = ""
)

object SubscriptionIdentity {

    const val PRESET_NONE = "none"
    const val PRESET_HAPP = "happ"
    const val PRESET_V2RAYTUN = "v2raytun"
    const val PRESET_INCY = "incy"

    private const val PREFS = "ws_identity"

    private const val KEY_CLIENT = "client_preset"
    private const val KEY_USER_AGENT = "user_agent"

    private const val KEY_SEND_HWID = "send_hwid"
    private const val KEY_CUSTOM_HWID = "huid"
    private const val KEY_STABLE_HWID = "stable_hwid"
    private const val KEY_STABLE_HWID_EXT = "stable_hwid_ext"

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

    private val hex16Regex = Regex("^[0-9a-fA-F]{16}$")

    // ───────────── Профили устройств ─────────────

    private val deviceS23 = DeviceProfile(
        label = "SM-S918B · Android 14",
        model = "Samsung SM-S918B",
        osVersion = "14"
    )

    private val deviceS26 = DeviceProfile(
        label = "SM-S948B · Android 16",
        model = "Samsung SM-S948B",
        osVersion = "16"
    )

    private val devicePixel3 = DeviceProfile(
        label = "Pixel 3 XL · Android 12",
        model = "Google Pixel 3 XL",
        osVersion = "Android 12"
    )

    private val devicePixel10 = DeviceProfile(
        label = "Pixel 10 · Android 16",
        model = "Google Pixel 10",
        osVersion = "16"
    )

    private val deviceS22 = DeviceProfile(
        label = "SM-S901B · Android 13",
        model = "SM-S901B",
        osVersion = "13"
    )

    private val deviceRedmi = DeviceProfile(
        label = "Redmi Note 5 Pro · Android 8",
        model = "Xiaomi Redmi Note 5 Pro",
        osVersion = "Android 8"
    )

    val DEVICE_PROFILES: List<DeviceProfile> = listOf(
        deviceS23,
        deviceS26,
        devicePixel3,
        devicePixel10,
        deviceS22,
        deviceRedmi
    )

    // ───────────── Профили приложений ─────────────

    private val presetNone = ClientPreset(
        id = PRESET_NONE,
        label = "Нет",
        userAgent = "WS Client/1.0",
        hwidFormat = HwidFormat.UPPER_HEX16,
        device = deviceS23,
        fields = IdentityField.values().toSet(),
        accept = "*/*",
        acceptLanguage = "ru-MM",
        deviceLocale = "ru_MM"
    )

    private val presetHapp = ClientPreset(
        id = PRESET_HAPP,
        label = "Happ",
        userAgent = "Happ/4.11.2/Android/17839452147361875676",
        hwidFormat = HwidFormat.LOWER_HEX16,
        device = deviceS22,
        fields = setOf(
            IdentityField.DEVICE_MODEL,
            IdentityField.OS_VERSION,
            IdentityField.DEVICE_OS,
            IdentityField.DEVICE_LOCALE
        ),
        deviceLocale = "ru"
    )

    private val presetV2RayTun = ClientPreset(
        id = PRESET_V2RAYTUN,
        label = "v2RayTun",
        userAgent = "v2raytun/android",
        hwidFormat = HwidFormat.UPPER_HEX16,
        device = deviceRedmi,
        fields = setOf(
            IdentityField.APP_VERSION,
            IdentityField.DEVICE_MODEL,
            IdentityField.OS_VERSION,
            IdentityField.DEVICE_OS
        ),
        appVersion = "5.25.80"
    )

    private val presetIncy = ClientPreset(
        id = PRESET_INCY,
        label = "Incy",
        userAgent = "INCY/3.9.9/android Dalvik/2.1.0",
        hwidFormat = HwidFormat.UUID_UPPER,
        device = devicePixel10,
        fields = setOf(
            IdentityField.ACCEPT,
            IdentityField.ACCEPT_LANGUAGE,
            IdentityField.CLIENT,
            IdentityField.DEVICE_LOCALE,
            IdentityField.APP_VERSION,
            IdentityField.DEVICE_MODEL,
            IdentityField.OS_VERSION,
            IdentityField.DEVICE_OS
        ),
        appVersion = "3.9.9",
        clientHeader = "INCY",
        accept = "*/*",
        acceptLanguage = "ru-MM",
        deviceLocale = "ru_MM"
    )

    val PRESETS: List<ClientPreset> = listOf(
        presetNone,
        presetHapp,
        presetV2RayTun,
        presetIncy
    )

    fun preset(id: String): ClientPreset {
        val key = id.lowercase(Locale.ROOT)
        return PRESETS.firstOrNull { it.id == key } ?: presetNone
    }

    // ───────────── Загрузка / сохранение ─────────────

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

    /**
     * Применяет профиль приложения целиком: User-Agent, HWID в нужном
     * формате, профиль устройства и набор заголовков именно этого клиента.
     */
    fun withClientPreset(
        context: Context,
        current: ClientIdentitySettings,
        presetId: String
    ): ClientIdentitySettings {
        val preset = preset(presetId)

        // Свой вручную введённый HWID не затираем,
        // а сгенерированный подменяем на формат нового клиента.
        val hwid = when {
            !isGeneratedHwid(context, current.customHwid) ->
                current.customHwid

            preset.id == PRESET_NONE -> ""

            else -> hwidFor(context, preset.id)
        }

        if (preset.id == PRESET_NONE) {
            return current.copy(
                clientPreset = PRESET_NONE,
                userAgent = preset.userAgent,
                appVersion = "",
                clientHeader = "",
                customHwid = hwid
            )
        }

        return current.copy(
            clientPreset = preset.id,
            userAgent = preset.userAgent,

            sendHwid = true,
            customHwid = hwid,

            emulateDevice = true,
            deviceProfile = preset.device.label,
            deviceModel = preset.device.model,
            osVersion = preset.device.osVersion,
            deviceOs = preset.device.os,
            deviceLocale = preset.deviceLocale,

            appVersion = preset.appVersion,
            accept = preset.accept,
            acceptLanguage = preset.acceptLanguage,
            clientHeader = preset.clientHeader
        )
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
                .ifBlank { hwidFor(context, settings.clientPreset) }

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

    // ───────────── HWID ─────────────

    /** HWID в формате выбранного клиента, построенный из одного стабильного ID. */
    fun hwidFor(
        context: Context,
        presetId: String
    ): String {
        return formatHwid(
            context,
            preset(presetId).hwidFormat
        )
    }

    private fun formatHwid(
        context: Context,
        format: HwidFormat
    ): String {
        val base = storedHex(context, KEY_STABLE_HWID)

        return when (format) {
            HwidFormat.LOWER_HEX16 -> base

            HwidFormat.UPPER_HEX16 -> base.uppercase(Locale.ROOT)

            HwidFormat.UUID_UPPER -> {
                val full = base + storedHex(context, KEY_STABLE_HWID_EXT)

                listOf(
                    full.substring(0, 8),
                    full.substring(8, 12),
                    full.substring(12, 16),
                    full.substring(16, 20),
                    full.substring(20, 32)
                ).joinToString("-")
                    .uppercase(Locale.ROOT)
            }
        }
    }

    /** Пусто или совпадает с одним из автоматически созданных HWID. */
    private fun isGeneratedHwid(
        context: Context,
        value: String
    ): Boolean {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) return true

        return HwidFormat.values().any {
            formatHwid(context, it) == cleaned
        }
    }

    private fun storedHex(
        context: Context,
        key: String
    ): String {
        val prefs = context.getSharedPreferences(PREFS, 0)

        val existing = prefs.getString(key, null)

        if (
            existing != null &&
            hex16Regex.matches(existing)
        ) {
            return existing.lowercase(Locale.ROOT)
        }

        val random = ByteArray(8)
        SecureRandom().nextBytes(random)

        val generated = random.joinToString("") {
            "%02x".format(
                Locale.ROOT,
                it.toInt() and 0xFF
            )
        }

        prefs.edit()
            .putString(key, generated)
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
