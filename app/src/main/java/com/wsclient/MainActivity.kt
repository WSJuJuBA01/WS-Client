package com.wsclient

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusDescription: TextView
    private lateinit var connectButton: TextView
    private lateinit var serverContainer: LinearLayout
    private lateinit var trafficText: TextView

    private var selectedId: String? = null
    private var titleClicks = 0
    private var receiverRegistered = false

    private var connectionState =
        VPNService.STATE_DISCONNECTED

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VPNService.ACTION_STATE) return

            val state = intent.getStringExtra(
                VPNService.EXTRA_STATE
            ) ?: return

            val message = intent.getStringExtra(
                VPNService.EXTRA_MESSAGE
            )

            connectionState = state
            updateConnectionUi(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedId = getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        ).getString("active_profile", null)

        buildInterface()
        refreshProfiles()
        updateConnectionUi()
    }

    override fun onStart() {
        super.onStart()
        registerStateReceiver()
    }

    override fun onStop() {
        unregisterStateReceiver()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshProfiles()
    }

    private fun registerStateReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(VPNService.ACTION_STATE)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                stateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun unregisterStateReceiver() {
        if (!receiverRegistered) return

        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {
        }

        receiverRegistered = false
    }

    private fun buildInterface() {
        val pastel = Customization.isPastel(this)

        val screenBackground = if (pastel) {
            Color.rgb(248, 238, 249)
        } else {
            Color.rgb(13, 16, 24)
        }

        val foreground = if (pastel) {
            Color.rgb(55, 39, 67)
        } else {
            Color.rgb(242, 245, 250)
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(screenBackground)
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val title = TextView(this).apply {
            text = "WS Client"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(foreground)
            setOnClickListener {
                titleClicks++

                if (titleClicks >= 5) {
                    titleClicks = 0
                    Customization.togglePastel(this@MainActivity)
                    recreate()
                }
            }
        }

        val subtitle = TextView(this).apply {
            text = "YOUR PRIVATE CONNECTION"
            textSize = 10f
            letterSpacing = 1.5f
            setTextColor(mutedColor(pastel))
            setPadding(0, dp(4), 0, 0)
        }

        titleBlock.addView(title)
        titleBlock.addView(subtitle)

        val version = TextView(this).apply {
            text = "WS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(foreground)
            background = rounded(
                if (pastel) Color.rgb(232, 211, 241)
                else Color.rgb(34, 43, 62),
                dp(14)
            )
            layoutParams = LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        }

        header.addView(titleBlock)
        header.addView(version)
        root.addView(header)

        root.addView(space(24))

        val statusCard = card(pastel)

        val statusCaption = TextView(this).apply {
            text = "СОСТОЯНИЕ ПОДКЛЮЧЕНИЯ"
            textSize = 10f
            letterSpacing = 1.3f
            setTextColor(mutedColor(pastel))
        }

        statusText = TextView(this).apply {
            text = "Отключено"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(foreground)
            setPadding(0, dp(12), 0, dp(4))
        }

        statusDescription = TextView(this).apply {
            text = "Выберите сервер и подключитесь"
            textSize = 13f
            setTextColor(mutedColor(pastel))
        }

        statusCard.addView(statusCaption)
        statusCard.addView(statusText)
        statusCard.addView(statusDescription)

        root.addView(statusCard)
        root.addView(space(14))

        val connectCard = card(pastel)

        connectButton = TextView(this).apply {
            text = "ПОДКЛЮЧИТЬСЯ"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(
                if (pastel) Color.rgb(151, 104, 181)
                else Color.rgb(76, 116, 245),
                dp(17)
            )
            setPadding(dp(16), dp(19), dp(16), dp(19))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                when (connectionState) {
                    VPNService.STATE_CONNECTED,
                    VPNService.STATE_CONNECTING -> disconnectVPN()
                    else -> connectVPN()
                }
            }
        }

        connectCard.addView(connectButton)

        val disconnectHint = TextView(this).apply {
            text = "Нажми кнопку ещё раз, чтобы отключиться"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(mutedColor(pastel))
            setPadding(0, dp(10), 0, 0)
        }

        connectCard.addView(disconnectHint)
        root.addView(connectCard)

        root.addView(space(14))

        val trafficCard = card(pastel)

        val trafficTitle = TextView(this).apply {
            text = "ТРАФИК СЕАНСА"
            textSize = 10f
            letterSpacing = 1.3f
            setTextColor(mutedColor(pastel))
        }

        trafficText = TextView(this).apply {
            text = "↑ 0 B       ↓ 0 B"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(foreground)
            setPadding(0, dp(12), 0, 0)
        }

        trafficCard.addView(trafficTitle)
        trafficCard.addView(trafficText)
        root.addView(trafficCard)

        root.addView(space(26))

        val serversHeader = sectionHeader(
            "МОИ СЕРВЕРЫ",
            pastel
        )

        root.addView(serversHeader)

        serverContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(serverContainer)
        root.addView(space(12))

        root.addView(
            actionButton(
                "＋  Импортировать ссылку",
                pastel
            ) {
                importProfile()
            }
        )

        root.addView(space(8))

        root.addView(
            actionButton(
                "⇧  Добавить подписку",
                pastel
            ) {
                addSubscription()
            }
        )

        root.addView(space(8))

        root.addView(
            actionButton(
                "⌁  Пинг и имитация VPN-клиентов",
                pastel
            ) {
                startActivity(
                    Intent(
                        this,
                        NetworkToolsActivity::class.java
                    )
                )
            }
        )

        root.addView(space(22))

        val footer = TextView(this).apply {
            text = "WS CLIENT  •  XRAY CORE"
            textSize = 10f
            letterSpacing = 1.2f
            gravity = Gravity.CENTER
            setTextColor(mutedColor(pastel))
            setPadding(0, dp(12), 0, 0)
        }

        root.addView(footer)

        setContentView(scrollView)
    }

    private fun card(pastel: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(
                if (pastel) Color.WHITE
                else Color.rgb(24, 29, 41),
                dp(20)
            )
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun sectionHeader(
        text: String,
        pastel: Boolean
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            letterSpacing = 1.4f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mutedColor(pastel))
            setPadding(0, 0, 0, dp(12))
        }
    }

    private fun actionButton(
        label: String,
        pastel: Boolean,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                if (pastel) Color.rgb(75, 53, 88)
                else Color.rgb(226, 233, 247)
            )
            background = rounded(
                if (pastel) Color.rgb(239, 225, 246)
                else Color.rgb(29, 37, 54),
                dp(15)
            )
            setPadding(dp(14), dp(17), dp(14), dp(17))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun updateConnectionUi(message: String? = null) {
        if (!::statusText.isInitialized) return

        val pastel = Customization.isPastel(this)

        when (connectionState) {
            VPNService.STATE_CONNECTING -> {
                statusText.text = "Подключение..."
                statusDescription.text =
                    message ?: "Подготавливаем VPN-туннель"
                connectButton.text = "ОТМЕНИТЬ"
            }

            VPNService.STATE_CONNECTED -> {
                statusText.text = "Подключено"
                statusDescription.text =
                    message ?: "Защищённое соединение активно"
                connectButton.text = "ОТКЛЮЧИТЬСЯ"
            }

            VPNService.STATE_ERROR -> {
                statusText.text = "Ошибка"
                statusDescription.text =
                    message ?: "Не удалось запустить VPN"
                connectButton.text = "ПОПРОБОВАТЬ СНОВА"
            }

            else -> {
                statusText.text = "Отключено"
                statusDescription.text =
                    message ?: "Выберите сервер и подключитесь"
                connectButton.text = "ПОДКЛЮЧИТЬСЯ"
            }
        }

        statusText.setTextColor(
            when (connectionState) {
                VPNService.STATE_CONNECTED ->
                    Color.rgb(64, 190, 132)

                VPNService.STATE_ERROR ->
                    Color.rgb(235, 91, 91)

                else ->
                    if (pastel) Color.rgb(55, 39, 67)
                    else Color.rgb(242, 245, 250)
            }
        )

        if (connectionState == VPNService.STATE_ERROR) {
            Toast.makeText(
                this,
                message ?: "Ошибка подключения",
                Toast.LENGTH_LONG
            ).show()
        }

        refreshTraffic()
    }

    private fun refreshTraffic() {
        if (!::trafficText.isInitialized) return

        val snapshot = TrafficMonitor.read()

        trafficText.text = String.format(
            Locale.getDefault(),
            "↑ %s       ↓ %s",
            TrafficMonitor.format(snapshot.uploaded),
            TrafficMonitor.format(snapshot.downloaded)
        )
    }

    private fun connectVPN() {
        val profiles = ProfileStore.getAll(this)

        if (profiles.isEmpty()) {
            showMessage("Сначала импортируй сервер")
            return
        }

        val selected = profiles.firstOrNull {
            it.id == selectedId
        } ?: profiles.first()

        selectedId = selected.id

        getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        ).edit()
            .putString("active_profile", selected.id)
            .apply()

        val permission = VpnService.prepare(this)

        if (permission != null) {
            startActivityForResult(
                permission,
                REQUEST_VPN
            )
        } else {
            startVPNService()
        }
    }

    private fun startVPNService() {
        connectionState = VPNService.STATE_CONNECTING
        updateConnectionUi()

        try {
            val intent = Intent(
                this,
                VPNService::class.java
            ).apply {
                action = VPNService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            connectionState = VPNService.STATE_ERROR
            updateConnectionUi(
                "Не удалось запустить службу: ${e.message}"
            )
        }
    }

    private fun disconnectVPN() {
        try {
            startService(
                Intent(this, VPNService::class.java).apply {
                    action = VPNService.ACTION_STOP
                }
            )
        } catch (e: Exception) {
            showMessage("Ошибка отключения: ${e.message}")
        }

        connectionState = VPNService.STATE_DISCONNECTED
        updateConnectionUi()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                startVPNService()
            } else {
                connectionState = VPNService.STATE_DISCONNECTED
                updateConnectionUi("Разрешение VPN не выдано")
            }
        }
    }

    private fun importProfile() {
        val input = EditText(this).apply {
            hint = "vless://, vmess://, trojan://, ss://"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        AlertDialog.Builder(this)
            .setTitle("Импорт сервера")
            .setMessage("Вставь ссылку на конфигурацию")
            .setView(input)
            .setPositiveButton("Импортировать") { _, _ ->
                try {
                    val profile = ProfileCodec.parse(
                        input.text.toString()
                    )

                    ProfileStore.add(this, profile)
                    selectedId = profile.id

                    getSharedPreferences(
                        "ws_settings",
                        MODE_PRIVATE
                    ).edit()
                        .putString("active_profile", profile.id)
                        .apply()

                    refreshProfiles()
                    showMessage("Сервер добавлен")
                } catch (e: Exception) {
                    showMessage(
                        "Ошибка импорта: ${e.message}"
                    )
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addSubscription() {
        val input = EditText(this).apply {
            hint = "https://example.com/subscription"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        AlertDialog.Builder(this)
            .setTitle("Добавить подписку")
            .setMessage("Вставь URL подписки")
            .setView(input)
            .setPositiveButton("Обновить") { _, _ ->
                val url = input.text.toString().trim()

                if (url.isBlank()) {
                    showMessage("Укажи ссылку подписки")
                    return@setPositiveButton
                }

                updateSubscription(url)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSubscription(url: String) {
        Toast.makeText(
            this,
            "Загружаю подписку...",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            try {
                val count = kotlinx.coroutines.runBlocking {
                    SubscriptionManager.update(
                        this@MainActivity,
                        url
                    )
                }

                runOnUiThread {
                    refreshProfiles()
                    showMessage("Импортировано серверов: $count")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Ошибка подписки: ${e.message}"
                    )
                }
            }
        }.start()
    }

    private fun refreshProfiles() {
        if (!::serverContainer.isInitialized) return

        serverContainer.removeAllViews()

        val pastel = Customization.isPastel(this)
        val profiles = ProfileStore.getAll(this)

        if (profiles.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Пока нет серверов\nИмпортируй ссылку или добавь подписку"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(mutedColor(pastel))
                setPadding(
                    dp(12),
                    dp(24),
                    dp(12),
                    dp(24)
                )
                background = rounded(
                    if (pastel) Color.WHITE
                    else Color.rgb(24, 29, 41),
                    dp(16)
                )
            }

            serverContainer.addView(empty)
            return
        }

        if (
            selectedId == null ||
            profiles.none { it.id == selectedId }
        ) {
            selectedId = getSharedPreferences(
                "ws_settings",
                MODE_PRIVATE
            ).getString("active_profile", null)
        }

        profiles.forEach { profile ->
            val selected = profile.id == selectedId

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(16),
                    dp(15),
                    dp(16),
                    dp(15)
                )

                background = rounded(
                    when {
                        selected && pastel ->
                            Color.rgb(239, 225, 246)

                        selected ->
                            Color.rgb(37, 53, 83)

                        pastel ->
                            Color.WHITE

                        else ->
                            Color.rgb(24, 29, 41)
                    },
                    dp(16)
                )

                isClickable = true
                isFocusable = true

                setOnClickListener {
                    selectProfile(profile.id)
                }

                setOnLongClickListener {
                    confirmDeleteProfile(profile)
                    true
                }
            }

            val name = TextView(this).apply {
                text = profile.name
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    if (pastel) Color.rgb(55, 39, 67)
                    else Color.rgb(242, 245, 250)
                )
            }

            val details = TextView(this).apply {
                text =
                    "${profile.protocol}  •  ${profile.address}:${profile.port}"
                textSize = 11f
                setTextColor(mutedColor(pastel))
                setPadding(0, dp(6), 0, 0)
            }

            val selectedLabel = TextView(this).apply {
                text = if (selected) {
                    "ВЫБРАН"
                } else {
                    "НАЖМИ, ЧТОБЫ ВЫБРАТЬ"
                }

                textSize = 9f
                letterSpacing = 1f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    if (selected) Color.rgb(74, 166, 125)
                    else mutedColor(pastel)
                )
                setPadding(0, dp(10), 0, 0)
            }

            item.addView(name)
            item.addView(details)
            item.addView(selectedLabel)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(9)
            }

            serverContainer.addView(item, params)
        }
    }

    private fun selectProfile(id: String) {
        selectedId = id

        getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        ).edit()
            .putString("active_profile", id)
            .apply()

        refreshProfiles()
    }

    private fun confirmDeleteProfile(
        profile: ServerProfile
    ) {
        AlertDialog.Builder(this)
            .setTitle("Удалить сервер?")
            .setMessage(
                "Профиль «${profile.name}» будет удалён."
            )
            .setPositiveButton("Удалить") { _, _ ->
                ProfileStore.remove(this, profile.id)

                if (selectedId == profile.id) {
                    selectedId = null

                    getSharedPreferences(
                        "ws_settings",
                        MODE_PRIVATE
                    ).edit()
                        .remove("active_profile")
                        .apply()
                }

                refreshProfiles()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun space(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(heightDp)
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

    private fun mutedColor(pastel: Boolean): Int {
        return if (pastel) {
            Color.rgb(133, 113, 145)
        } else {
            Color.rgb(145, 157, 180)
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    companion object {
        private const val REQUEST_VPN = 100
    }
}
