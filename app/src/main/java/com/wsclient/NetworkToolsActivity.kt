
package com.wsclient

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class NetworkToolsActivity : Activity() {

    private lateinit var root: LinearLayout

    private lateinit var clientSelector: TextView
    private lateinit var deviceSelector: TextView

    private lateinit var hwidToggle: CheckBox
    private lateinit var deviceToggle: CheckBox

    private lateinit var userAgentField: EditText
    private lateinit var customHwidField: EditText

    private lateinit var deviceModelField: EditText
    private lateinit var osVersionField: EditText
    private lateinit var deviceOsField: EditText
    private lateinit var deviceLocaleField: EditText

    private lateinit var appVersionField: EditText
    private lateinit var acceptField: EditText
    private lateinit var acceptLanguageField: EditText
    private lateinit var clientHeaderField: EditText
    private lateinit var extraHeadersField: EditText

    private lateinit var pingUrlField: EditText
    private lateinit var pingContainer: LinearLayout

    private var settings = ClientIdentitySettings()

    private val pastel: Boolean
        get() = Customization.isPastel(this)

    private val primaryTextColor: Int
        get() = if (pastel) {
            Color.rgb(55, 39, 67)
        } else {
            Color.rgb(242, 245, 250)
        }

    private val backgroundColor: Int
        get() = if (pastel) {
            Color.rgb(248, 238, 249)
        } else {
            Color.rgb(13, 16, 24)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = SubscriptionIdentity.load(this)
        buildInterface()
        loadSettingsIntoFields()
        refreshPingProfiles()
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        val title = TextView(this).apply {
            text = "Инструменты"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryTextColor)
        }

        val description = TextView(this).apply {
            text = "Проверка серверов и идентификация HTTP-клиента"
            textSize = 12f
            setTextColor(mutedColor())
            setPadding(0, dp(5), 0, dp(20))
        }

        root.addView(title)
        root.addView(description)

        buildEmulationSection()
        root.addView(space(24))
        buildPingSection()

        setContentView(scroll)
    }

    private fun buildEmulationSection() {
        root.addView(sectionTitle("ИМИТАЦИЯ VPN-КЛИЕНТА"))

        val panel = card()

        clientSelector = selector()
        clientSelector.setOnClickListener {
            chooseClientPreset()
        }

        panel.addView(fieldLabel("Профиль приложения"))
        panel.addView(clientSelector)

        hwidToggle = CheckBox(this).apply {
            text = "Поддержка HWID (отправлять X-HWID)"
            textSize = 13f
            setTextColor(primaryTextColor)
            buttonTintList = android.content.res.ColorStateList.valueOf(
                accentColor()
            )
        }

        panel.addView(hwidToggle)

        customHwidField = addField(
            panel,
            "Подмена HWID",
            "Оставь пустым для локального сохранённого ID"
        )

        deviceToggle = CheckBox(this).apply {
            text = "Подмена устройства в HTTP-заголовках"
            textSize = 13f
            setTextColor(primaryTextColor)
            buttonTintList = android.content.res.ColorStateList.valueOf(
                accentColor()
            )
        }

        panel.addView(space(4))
        panel.addView(deviceToggle)

        deviceSelector = selector()
        deviceSelector.setOnClickListener {
            chooseDeviceProfile()
        }

        panel.addView(fieldLabel("Профиль устройства"))
        panel.addView(deviceSelector)

        userAgentField = addField(
            panel,
            "User-Agent",
            "Например: v2raytun/android"
        )

        appVersionField = addField(
            panel,
            "X-App-Version",
            "Например: 3.9.9"
        )

        clientHeaderField = addField(
            panel,
            "X-Client",
            "Например: INCY"
        )

        acceptField = addField(
            panel,
            "Accept",
            "*/*"
        )

        acceptLanguageField = addField(
            panel,
            "Accept-Language",
            "ru-MM"
        )

        deviceModelField = addField(
            panel,
            "X-Device-Model",
            "Например: Samsung SM-S918B"
        )

        osVersionField = addField(
            panel,
            "X-Ver-Os",
            "Например: 14"
        )

        deviceOsField = addField(
            panel,
            "X-Device-Os",
            "Android"
        )

        deviceLocaleField = addField(
            panel,
            "X-Device-Locale",
            "ru_MM"
        )

        extraHeadersField = addField(
            panel,
            "Дополнительные заголовки",
            "X-Example: value\nX-Another: value",
            multiline = true
        )

        val hint = TextView(this).apply {
            text =
                "Дополнительные заголовки указываются по одному на строку: " +
                    "Название: значение.\n\n" +
                    "Эти параметры применяются к HTTP-запросам подписки. " +
                    "Они не меняют реальные параметры Android."
            textSize = 11f
            setTextColor(mutedColor())
            setPadding(0, dp(10), 0, dp(12))
        }

        panel.addView(hint)

        panel.addView(
            actionButton("СОХРАНИТЬ ЭМУЛЯЦИЮ") {
                saveSettings()
            }
        )

        root.addView(panel)
    }

    private fun buildPingSection() {
        root.addView(sectionTitle("HTTP GET • ЗАДЕРЖКА СЕРВЕРОВ"))

        val panel = card()

        val explanation = TextView(this).apply {
            text =
                "Проверка выполняется через Xray и выбранный сервер. " +
                    "Это HTTP GET, не ICMP и не простой TCP connect."
            textSize = 12f
            setTextColor(mutedColor())
            setPadding(0, 0, 0, dp(12))
        }

        panel.addView(explanation)

        pingUrlField = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
            hint = ServerLatency.DEFAULT_TEST_URL
            setText(
                getSharedPreferences("ws_ping", 0)
                    .getString(
                        "test_url",
                        ServerLatency.DEFAULT_TEST_URL
                    )
            )
            setTextColor(primaryTextColor)
            setHintTextColor(mutedColor())
            background = rounded(
                if (pastel) Color.rgb(248, 243, 250)
                else Color.rgb(15, 19, 29),
                dp(12)
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        panel.addView(fieldLabel("URL проверки"))
        panel.addView(pingUrlField)
        panel.addView(space(10))

        panel.addView(
            actionButton("СОХРАНИТЬ URL") {
                savePingUrl()
            }
        )

        panel.addView(space(16))

        pingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        panel.addView(pingContainer)
        root.addView(panel)
    }

    private fun refreshPingProfiles() {
        if (!::pingContainer.isInitialized) return

        pingContainer.removeAllViews()

        val profiles = ProfileStore.getAll(this)

        if (profiles.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Нет импортированных серверов"
                textSize = 13f
                setTextColor(mutedColor())
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }

            pingContainer.addView(empty)
            return
        }

        profiles.forEach { profile ->
            pingContainer.addView(
                createPingRow(profile)
            )
        }
    }

    private fun createPingRow(
        profile: ServerProfile
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(
                if (pastel) Color.rgb(248, 243, 250)
                else Color.rgb(15, 19, 29),
                dp(14)
            )
        }

        val name = TextView(this).apply {
            text = profile.name
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryTextColor)
        }

        val address = TextView(this).apply {
            text = "${profile.protocol} • ${profile.address}:${profile.port}"
            textSize = 11f
            setTextColor(mutedColor())
            setPadding(0, dp(4), 0, dp(8))
        }

        val result = TextView(this).apply {
            text = "HTTP GET: не проверен"
            textSize = 12f
            setTextColor(mutedColor())
            setPadding(0, 0, 0, dp(8))
        }

        val checkButton = actionButton("ПРОВЕРИТЬ") {
            runPing(profile, result, it)
        }

        row.addView(name)
        row.addView(address)
        row.addView(result)
        row.addView(checkButton)

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(9))
            addView(row)
        }

        return wrapper
    }

    private fun runPing(
        profile: ServerProfile,
        resultView: TextView,
        button: TextView
    ) {
        val url = pingUrlField.text.toString().trim()

        if (
            !url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) {
            resultView.text = "Укажи корректный HTTP или HTTPS URL"
            return
        }

        button.isEnabled = false
        button.alpha = 0.55f
        resultView.text = "HTTP GET: проверка..."

        Thread {
            try {
                val delay = ServerLatency.check(
                    profile,
                    url
                )

                runOnUiThread {
                    resultView.text = "HTTP GET: ${delay} мс"
                    resultView.setTextColor(
                        Color.rgb(64, 190, 132)
                    )
                    button.isEnabled = true
                    button.alpha = 1f
                }
            } catch (e: Exception) {
                val error = e.message
                    ?.takeIf { it.isNotBlank() }
                    ?: e.javaClass.simpleName

                runOnUiThread {
                    resultView.text = "Ошибка: $error"
                    resultView.setTextColor(
                        Color.rgb(235, 91, 91)
                    )
                    button.isEnabled = true
                    button.alpha = 1f
                }
            }
        }.start()
    }

    private fun chooseClientPreset() {
        settings = collectSettings()

        val ids = arrayOf(
            "none",
            "happ",
            "v2raytun",
            "inocy"
        )

        val labels = arrayOf(
            "Нет",
            "Happ",
            "v2RayTun",
            "Inocy"
        )

        val selectedIndex = ids.indexOf(
            settings.clientPreset
        ).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Имитация приложения")
            .setSingleChoiceItems(
                labels,
                selectedIndex
            ) { dialog, which ->
                settings = SubscriptionIdentity.withClientPreset(
                    settings,
                    ids[which]
                )

                loadSettingsIntoFields()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun chooseDeviceProfile() {
        settings = collectSettings()

        val labels = arrayOf(
            "SM-S918B · Android 14",
            "SM-S948B · Android 16",
            "Pixel 3 XL · Android 12"
        )

        AlertDialog.Builder(this)
            .setTitle("Профиль устройства")
            .setItems(labels) { _, which ->
                settings = when (which) {
                    0 -> settings.copy(
                        deviceProfile = labels[0],
                        deviceModel = "Samsung SM-S918B",
                        osVersion = "14",
                        deviceOs = "Android"
                    )

                    1 -> settings.copy(
                        deviceProfile = labels[1],
                        deviceModel = "Samsung SM-S948B",
                        osVersion = "16",
                        deviceOs = "Android"
                    )

                    else -> settings.copy(
                        deviceProfile = labels[2],
                        deviceModel = "Google Pixel 3 XL",
                        osVersion = "Android 12",
                        deviceOs = "Android"
                    )
                }

                loadSettingsIntoFields()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadSettingsIntoFields() {
        if (!::clientSelector.isInitialized) return

        clientSelector.text = when (settings.clientPreset) {
            "happ" -> "Happ"
            "v2raytun" -> "v2RayTun"
            "inocy" -> "Inocy"
            else -> "Нет"
        }

        deviceSelector.text = settings.deviceProfile

        hwidToggle.isChecked = settings.sendHwid
        deviceToggle.isChecked = settings.emulateDevice

        userAgentField.setText(settings.userAgent)
        customHwidField.setText(settings.customHwid)

        deviceModelField.setText(settings.deviceModel)
        osVersionField.setText(settings.osVersion)
        deviceOsField.setText(settings.deviceOs)
        deviceLocaleField.setText(settings.deviceLocale)

        appVersionField.setText(settings.appVersion)
        acceptField.setText(settings.accept)
        acceptLanguageField.setText(settings.acceptLanguage)
        clientHeaderField.setText(settings.clientHeader)
        extraHeadersField.setText(settings.extraHeaders)
    }

    private fun collectSettings(): ClientIdentitySettings {
        return settings.copy(
            sendHwid = hwidToggle.isChecked,
            customHwid = customHwidField.text.toString().trim(),

            emulateDevice = deviceToggle.isChecked,

            userAgent = userAgentField.text.toString().trim(),

            deviceModel = deviceModelField.text.toString().trim(),
            osVersion = osVersionField.text.toString().trim(),
            deviceOs = deviceOsField.text.toString().trim(),
            deviceLocale = deviceLocaleField.text.toString().trim(),

            appVersion = appVersionField.text.toString().trim(),
            accept = acceptField.text.toString().trim(),
            acceptLanguage = acceptLanguageField.text.toString().trim(),
            clientHeader = clientHeaderField.text.toString().trim(),

            extraHeaders = extraHeadersField.text.toString()
        )
    }

    private fun saveSettings() {
        val newSettings = collectSettings()

        if (newSettings.userAgent.isBlank()) {
            Toast.makeText(
                this,
                "User-Agent не должен быть пустым",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        SubscriptionIdentity.save(
            this,
            newSettings
        )

        settings = newSettings

        Toast.makeText(
            this,
            "Настройки эмуляции сохранены",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun savePingUrl() {
        val url = pingUrlField.text.toString().trim()

        if (
            !url.startsWith("https://", true) &&
            !url.startsWith("http://", true)
        ) {
            Toast.makeText(
                this,
                "Нужен адрес HTTP или HTTPS",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        getSharedPreferences("ws_ping", 0)
            .edit()
            .putString("test_url", url)
            .apply()

        Toast.makeText(
            this,
            "URL проверки сохранён",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun addField(
        parent: LinearLayout,
        label: String,
        hint: String,
        multiline: Boolean = false
    ): EditText {
        parent.addView(fieldLabel(label))

        val field = EditText(this).apply {
            this.hint = hint

            inputType = if (multiline) {
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                InputType.TYPE_CLASS_TEXT
            }

            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
            } else {
                setSingleLine(true)
            }

            textSize = 13f
            setTextColor(primaryTextColor)
            setHintTextColor(mutedColor())
            background = rounded(
                if (pastel) Color.rgb(248, 243, 250)
                else Color.rgb(15, 19, 29),
                dp(12)
            )
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }

        parent.addView(field)
        parent.addView(space(9))

        return field
    }

    private fun fieldLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mutedColor())
            setPadding(0, dp(10), 0, dp(6))
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            letterSpacing = 1.2f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mutedColor())
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun selector(): TextView {
        return TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryTextColor)
            background = rounded(
                if (pastel) Color.rgb(239, 225, 246)
                else Color.rgb(34, 43, 62),
                dp(12)
            )
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
    }

    private fun actionButton(
        text: String,
        action: (TextView) -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accentColor(), dp(12))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                action(this)
            }
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(
                if (pastel) Color.WHITE
                else Color.rgb(24, 29, 41),
                dp(18)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun accentColor(): Int {
        return if (pastel) {
            Color.rgb(151, 104, 181)
        } else {
            Color.rgb(76, 116, 245)
        }
    }

    private fun mutedColor(): Int {
        return if (pastel) {
            Color.rgb(133, 113, 145)
        } else {
            Color.rgb(145, 157, 180)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
