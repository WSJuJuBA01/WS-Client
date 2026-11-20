package com.wsclient

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
import android.widget.PopupMenu
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
    private val trafficHandler = Handler(Looper.getMainLooper())
    private val trafficUpdater = object : Runnable {
        override fun run() {
            if (connectionState == VPNService.STATE_CONNECTED) refreshTraffic()
            trafficHandler.postDelayed(this, 1000L)
        }
    }

    private var connectionState =
        VPNService.STATE_DISCONNECTED
    private var appearanceFingerprint: String = ""

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

        connectionState = if (XrayManager.running) VPNService.STATE_CONNECTED else VPNService.STATE_DISCONNECTED
        buildInterface()
        refreshProfiles()
        updateConnectionUi()
        appearanceFingerprint = currentAppearanceFingerprint()
        if (savedInstanceState == null && AutoConnect.enabled(this) &&
            VpnService.prepare(this) == null && ProfileStore.getAll(this).isNotEmpty() &&
            !XrayManager.running
        ) {
            connectVPN()
        }
    }

    override fun onStart() {
        super.onStart()
        registerStateReceiver()
        trafficHandler.removeCallbacks(trafficUpdater)
        trafficHandler.post(trafficUpdater)
    }

    override fun onStop() {
        trafficHandler.removeCallbacks(trafficUpdater)
        unregisterStateReceiver()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (appearanceFingerprint.isNotEmpty() && appearanceFingerprint != currentAppearanceFingerprint()) {
            recreate()
            return
        }
        if (XrayManager.running) connectionState = VPNService.STATE_CONNECTED
        refreshProfiles()
        updateConnectionUi()
    }

    private fun currentAppearanceFingerprint(): String = listOf(
        Customization.palette(this).id,
        Customization.accent(this),
        Customization.wallpaper(this)?.toString().orEmpty(),
        Customization.dim(this),
        Customization.cardAlpha(this)
    ).joinToString("|")

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
        val palette = Customization.palette(this)
        val pastel = palette.isLight
        val screenBackground = palette.background
        val foreground = palette.foreground

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(screenBackground)
        }
        applyWallpaperBackground(root)

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
        val headerActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val settingsButton = TextView(this).apply {
            text = "⚙"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(foreground)
            background = rounded(palette.surface, dp(14))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(42))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AppearanceActivity::class.java))
            }
        }
        headerActions.addView(settingsButton)
        val addButton = TextView(this).apply {
            text = "+"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Customization.accent(this@MainActivity), dp(14))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(42)).apply {
                topMargin = dp(6)
            }
            contentDescription = "Добавить сервер или подписку"
            setOnClickListener { showAddMenu(this) }
        }
        headerActions.addView(addButton)
        header.addView(headerActions)
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
            background = rounded(Customization.accent(this@MainActivity), dp(17))
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

        // Import actions are grouped under the header "+" menu.
        root.addView(space(8))

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
                withAlpha(Customization.palette(this@MainActivity).surface, Customization.cardAlpha(this@MainActivity)),
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
            setTextColor(Customization.palette(this@MainActivity).foreground)
            background = rounded(
                withAlpha(Customization.palette(this@MainActivity).surface, Customization.cardAlpha(this@MainActivity)),
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
            "↑ %s  (%s/s)     ↓ %s  (%s/s)\nВремя: %s",
            TrafficMonitor.format(snapshot.uploaded),
            TrafficMonitor.format(snapshot.uploadBytesPerSecond),
            TrafficMonitor.format(snapshot.downloaded),
            TrafficMonitor.format(snapshot.downloadBytesPerSecond),
            if (XrayManager.startedAtElapsedRealtime > 0L) TrafficMonitor.formatDuration(android.os.SystemClock.elapsedRealtime() - XrayManager.startedAtElapsedRealtime) else "—"
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

    private fun showAddMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Вставить из буфера обмена")
        menu.menu.add(0, 2, 1, "Добавить вручную")
        menu.menu.add(0, 3, 2, "Добавить подписку")
        menu.menu.add(0, 4, 3, "Пинг и инструменты")
        menu.menu.add(0, 5, 4, "Раздельное туннелирование")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> pasteFromClipboard()
                2 -> showManualImportDialog()
                3 -> showSubscriptionDialog()
                4 -> startActivity(Intent(this, NetworkToolsActivity::class.java))
                5 -> startActivity(Intent(this, SplitTunnelingActivity::class.java))
            }
            true
        }
        menu.show()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val value = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
        if (value.isNullOrBlank()) {
            showMessage("Буфер обмена пуст")
            return
        }
        importProfileValue(value)
    }

    private fun showManualImportDialog() {
        val input = EditText(this).apply {
            hint = "VLESS / VMess / Trojan / SS или JSON outbound"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("Добавить вручную")
            .setMessage("Вставь ссылку профиля или JSON-конфигурацию с proxy outbound.")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                importProfileValue(input.text.toString())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSubscriptionDialog() {
        val url = EditText(this).apply {
            hint = "https://example.com/subscription"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val title = EditText(this).apply {
            hint = "Название подписки (необязательно)"
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(url)
            addView(title)
        }
        AlertDialog.Builder(this)
            .setTitle("Добавить подписку")
            .setMessage("Укажи ссылку и, если нужно, своё название.")
            .setView(box)
            .setPositiveButton("Добавить") { _, _ ->
                val link = url.text.toString().trim()
                if (link.isBlank()) {
                    showMessage("Укажи ссылку подписки")
                } else {
                    updateSubscription(link, title.text.toString().trim().ifBlank { null })
                }
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Имитация клиента") { _, _ ->
                startActivity(Intent(this, NetworkToolsActivity::class.java))
            }
            .show()
    }

    private fun importProfileValue(value: String) {
        try {
            val profile = ProfileCodec.parse(value)
            val storedProfile = ProfileStore.add(this, profile)
            selectedId = storedProfile.id
            getSharedPreferences("ws_settings", MODE_PRIVATE).edit()
                .putString("active_profile", storedProfile.id).apply()
            refreshProfiles()
            showMessage("Сервер добавлен")
        } catch (e: Exception) {
            showMessage("Ошибка импорта: ${e.message}")
        }
    }

    private fun importProfile() = showManualImportDialog()

    private fun addSubscription() = showSubscriptionDialog()

    private fun updateSubscription(url: String, customTitle: String? = null) {
        Toast.makeText(
            this,
            "Загружаю подписку...",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            try {
                val imported = kotlinx.coroutines.runBlocking {
                    SubscriptionManager.updateDetailed(this@MainActivity, url)
                }
                SubscriptionStore.upsert(this@MainActivity, url, imported)
                if (!customTitle.isNullOrBlank()) {
                    SubscriptionStore.rename(this@MainActivity, SubscriptionStore.idFor(url), customTitle)
                }
                runOnUiThread {
                    refreshProfiles()
                    showMessage("Обновлено серверов: ${imported.size}")
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
            serverContainer.addView(TextView(this).apply {
                text = "Пока нет серверов\nИмпортируй ссылку или добавь подписку"
                textSize = 13f; gravity = Gravity.CENTER
                setTextColor(mutedColor(pastel)); setPadding(dp(12), dp(24), dp(12), dp(24))
                background = rounded(Customization.palette(this@MainActivity).surface, dp(16))
            })
            return
        }
        if (selectedId == null || profiles.none { it.id == selectedId }) {
            val savedId = getSharedPreferences("ws_settings", MODE_PRIVATE).getString("active_profile", null)
            selectedId = profiles.firstOrNull { it.id == savedId }?.id ?: profiles.first().id
        }

        val groups = SubscriptionStore.all(this).sortedByDescending { SubscriptionStore.isPinned(this, it.id) }
        val groupedIds = groups.flatMap { it.profileIds }.toSet()
        groups.forEach { group ->
            val members = profiles.filter { it.id in group.profileIds }
            if (members.isNotEmpty()) addSubscriptionGroup(group, members, pastel)
        }
        profiles.filterNot { it.id in groupedIds }.forEach { addProfileCard(it, pastel) }
    }

    private fun addSubscriptionGroup(group: SubscriptionGroup, members: List<ServerProfile>, pastel: Boolean) {
        val prefs = getSharedPreferences("ws_settings", MODE_PRIVATE)
        val collapsed = prefs.getBoolean("subscription_collapsed_${group.id}", false)
        val palette = Customization.palette(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(10))
            background = rounded(withAlpha(palette.surface, Customization.cardAlpha(this@MainActivity)), dp(18))
        }
        val headingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(this).apply {
            text = "${if (collapsed) "▸" else "▾"}  ${group.title}  ·  ${members.size}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.foreground)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener {
                prefs.edit().putBoolean("subscription_collapsed_${group.id}", !collapsed).apply()
                refreshProfiles()
            }
        }
        headingRow.addView(heading)
        headingRow.addView(iconAction("◉", "Проверить пинг") { pingSubscription(group, members) })
        headingRow.addView(iconAction("⟳", "Обновить подписку") { refreshSubscription(group) })
        headingRow.addView(iconAction("⋮", "Настройки подписки") { showSubscriptionOptions(group) })
        panel.addView(headingRow)
        if (!collapsed) {
            members.forEach { addProfileCard(it, pastel, panel) }
        }
        serverContainer.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun iconAction(symbol: String, description: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = symbol
            textSize = if (symbol == "⋮") 24f else 20f
            gravity = Gravity.CENTER
            contentDescription = description
            setTextColor(Customization.palette(this@MainActivity).foreground)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(40))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun showSubscriptionOptions(group: SubscriptionGroup) {
        val menu = PopupMenu(this, serverContainer)
        menu.menu.add("Изменить название")
        menu.menu.add("Изменить ссылку")
        menu.menu.add(if (SubscriptionStore.isPinned(this, group.id)) "Открепить сверху" else "Закрепить сверху")
        menu.menu.add("Имитация клиента")
        menu.menu.add("Удалить подписку")
        menu.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Изменить название" -> editSubscriptionTitle(group)
                "Изменить ссылку" -> editSubscriptionUrl(group)
                "Закрепить сверху", "Открепить сверху" -> {
                    SubscriptionStore.setPinned(this, group.id, !SubscriptionStore.isPinned(this, group.id))
                    refreshProfiles()
                }
                "Имитация клиента" -> startActivity(Intent(this, NetworkToolsActivity::class.java))
                "Удалить подписку" -> AlertDialog.Builder(this)
                    .setTitle("Удалить подписку?")
                    .setMessage("Серверы, импортированные из неё, останутся в списке.")
                    .setPositiveButton("Удалить") { _, _ ->
                        SubscriptionStore.remove(this, group.id)
                        refreshProfiles()
                    }
                    .setNegativeButton("Отмена", null).show()
            }
            true
        }
        menu.show()
    }

    private fun editSubscriptionTitle(group: SubscriptionGroup) {
        val input = EditText(this).apply { setText(group.title); hint = "Название группы" }
        AlertDialog.Builder(this).setTitle("Название подписки").setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                SubscriptionStore.rename(this, group.id, input.text.toString())
                refreshProfiles()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun editSubscriptionUrl(group: SubscriptionGroup) {
        val input = EditText(this).apply {
            setText(group.url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this).setTitle("Ссылка подписки").setView(input)
            .setMessage("После сохранения подписка будет обновлена по новой ссылке.")
            .setPositiveButton("Сохранить и обновить") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isBlank()) showMessage("Ссылка не может быть пустой")
                else {
                    SubscriptionStore.changeUrl(this, group.id, newUrl)
                    refreshProfiles()
                    updateSubscription(newUrl, group.title)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun addProfileCard(profile: ServerProfile, pastel: Boolean, parent: LinearLayout = serverContainer) {
        val selected = profile.id == selectedId
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(
                if (selected) withAlpha(Customization.accent(this@MainActivity), 30)
                else withAlpha(Customization.palette(this@MainActivity).surface, Customization.cardAlpha(this@MainActivity)), dp(14)
            )
            isClickable = true; isFocusable = true
            setOnClickListener { selectProfile(profile.id) }
            setOnLongClickListener { confirmDeleteProfile(profile); true }
        }
        item.addView(TextView(this).apply {
            text = profile.name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Customization.palette(this@MainActivity).foreground)
        })
        item.addView(TextView(this).apply {
            text = "${profile.protocol}  •  ${profile.address}:${profile.port}"
            textSize = 11f; setTextColor(mutedColor(pastel)); setPadding(0, dp(5), 0, 0)
        })
        item.addView(TextView(this).apply {
            text = if (selected) "ВЫБРАН" else "НАЖМИ, ЧТОБЫ ВЫБРАТЬ"
            textSize = 9f; letterSpacing = 1f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (selected) Color.rgb(64, 190, 132) else mutedColor(pastel))
            setPadding(0, dp(8), 0, 0)
        })
        parent.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun smallAction(title: String, action: () -> Unit): TextView = TextView(this).apply {
        text = title; textSize = 11f; gravity = Gravity.CENTER
        setTextColor(Customization.palette(this@MainActivity).foreground)
        background = rounded(withAlpha(Customization.accent(this@MainActivity), 38), dp(10))
        setOnClickListener { action() }
    }

    private fun refreshSubscription(group: SubscriptionGroup) {
        showMessage("Обновляю ${group.title}…")
        Thread {
            try {
                val imported = kotlinx.coroutines.runBlocking { SubscriptionManager.updateDetailed(this@MainActivity, group.url) }
                SubscriptionStore.upsert(this@MainActivity, group.url, imported)
                runOnUiThread { refreshProfiles(); showMessage("${group.title}: обновлено ${imported.size} серверов") }
            } catch (e: Exception) {
                runOnUiThread { showMessage("Ошибка обновления: ${e.message}") }
            }
        }.start()
    }

    private fun pingSubscription(group: SubscriptionGroup, members: List<ServerProfile>) {
        showMessage("Проверяю ${members.size} серверов…")
        Thread {
            val results = members.map { profile ->
                val result = runCatching { ServerLatency.check(profile) }
                profile.name to result.getOrNull()
            }
            runOnUiThread {
                val text = results.joinToString("\n") { (name, ms) -> "$name — ${ms?.let { "$it мс" } ?: "ошибка"}" }
                AlertDialog.Builder(this).setTitle("Пинг · ${group.title}").setMessage(text.ifBlank { "Нет серверов для проверки" }).setPositiveButton("Закрыть", null).show()
            }
        }.start()
    }

    private fun editSubscription(group: SubscriptionGroup) {
        val input = EditText(this).apply { setText(group.title); hint = "Название группы" }
        AlertDialog.Builder(this).setTitle("Настройки подписки")
            .setView(input)
            .setNeutralButton("Удалить группу") { _, _ ->
                SubscriptionStore.remove(this, group.id); refreshProfiles()
            }
            .setPositiveButton("Сохранить") { _, _ ->
                SubscriptionStore.rename(this, group.id, input.text.toString()); refreshProfiles()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun applyWallpaperBackground(target: View) {
        Customization.applyWallpaper(this, target)
    }

    private fun withAlpha(color: Int, percent: Int): Int =
        Color.argb((percent.coerceIn(0, 100) * 255) / 100, Color.red(color), Color.green(color), Color.blue(color))

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

    private fun mutedColor(pastel: Boolean): Int = Customization.palette(this).muted

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    companion object {
        private const val REQUEST_VPN = 100
    }
}
