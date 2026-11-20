
package com.wsclient

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var connectionState = VPNService.STATE_DISCONNECTED
    private var appearanceFingerprint = ""

    private val trafficHandler = Handler(Looper.getMainLooper())

    private val trafficUpdater = object : Runnable {
        override fun run() {
            if (connectionState == VPNService.STATE_CONNECTED) {
                refreshTraffic()
            }
            trafficHandler.postDelayed(this, 1000L)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VPNService.ACTION_STATE) return

            connectionState = intent.getStringExtra(
                VPNService.EXTRA_STATE
            ) ?: return

            updateConnectionUi(
                intent.getStringExtra(VPNService.EXTRA_MESSAGE)
            )
        }
    }

    private val palette
        get() = Customization.palette(this)

    private val accent
        get() = Customization.accent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedId = getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        ).getString("active_profile", null)

        connectionState = if (XrayManager.running) {
            VPNService.STATE_CONNECTED
        } else {
            VPNService.STATE_DISCONNECTED
        }

        buildInterface()
        refreshProfiles()
        updateConnectionUi()

        appearanceFingerprint = currentAppearanceFingerprint()

        if (
            savedInstanceState == null &&
            AutoConnect.enabled(this) &&
            VpnService.prepare(this) == null &&
            ProfileStore.getAll(this).isNotEmpty() &&
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

        if (
            appearanceFingerprint.isNotEmpty() &&
            appearanceFingerprint != currentAppearanceFingerprint()
        ) {
            recreate()
            return
        }

        if (XrayManager.running) {
            connectionState = VPNService.STATE_CONNECTED
        }

        refreshProfiles()
        updateConnectionUi()
    }

    private fun currentAppearanceFingerprint(): String {
        return listOf(
            palette.id,
            accent,
            Customization.wallpaper(this)?.toString().orEmpty(),
            Customization.dim(this),
            Customization.cardAlpha(this)
        ).joinToString("|")
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

        runCatching {
            unregisterReceiver(stateReceiver)
        }

        receiverRegistered = false
    }

    // ---------------------------------------------------------
    // Main screen
    // ---------------------------------------------------------

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(palette.background)
        }

        Customization.applyWallpaper(this, root)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }

        buildHeader()
        addSpace(24)

        buildConnectionCard()
        addSpace(12)

        buildTrafficCard()
        addSpace(26)

        val section = row {
            text(
                "МОИ СЕРВЕРЫ",
                11f,
                palette.muted,
                true
            ).apply {
                letterSpacing = 1.3f
            }

            addView(
                View(this@MainActivity),
                LinearLayout.LayoutParams(0, 1, 1f)
            )

            text(
                "XRAY CORE",
                9f,
                palette.muted,
                true
            )
        }

        root.addView(section)
        addSpace(12)

        serverContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(serverContainer)

        addSpace(22)

        text(
            "WS CLIENT",
            10f,
            palette.muted,
            true
        ).apply {
            gravity = Gravity.CENTER
            letterSpacing = 1.5f
            root.addView(this)
        }

        setContentView(scroll)
    }

    private fun buildHeader() {
        val header = row()

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        }

        val title = text(
            "WS Client",
            28f,
            palette.foreground,
            true
        ).apply {
            setOnClickListener {
                titleClicks++

                if (titleClicks >= 5) {
                    titleClicks = 0
                    Customization.togglePastel(this@MainActivity)
                    recreate()
                }
            }
        }

        titleBlock.addView(title)

        titleBlock.addView(
            text(
                "YOUR PRIVATE CONNECTION",
                9f,
                palette.muted,
                true
            ).apply {
                letterSpacing = 1.4f
                setPadding(0, dp(4), 0, 0)
            }
        )

        header.addView(titleBlock)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        actions.addView(
            iconButton("⚙", palette.surface) {
                startActivity(
                    Intent(
                        this@MainActivity,
                        AppearanceActivity::class.java
                    )
                )
            }
        )

        val add = iconButton("+", accent, Color.WHITE) {
            showAddMenu()
        }

        add.textSize = 26f

        actions.addView(
            add,
            LinearLayout.LayoutParams(
                dp(46),
                dp(44)
            ).apply {
                topMargin = dp(7)
            }
        )

        header.addView(actions)
        root.addView(header)
    }

    private fun buildConnectionCard() {
        val card = makeCard()

        card.addView(
            text(
                "СОСТОЯНИЕ ПОДКЛЮЧЕНИЯ",
                10f,
                palette.muted,
                true
            ).apply {
                letterSpacing = 1.2f
            }
        )

        statusText = text(
            "Отключено",
            25f,
            palette.foreground,
            true
        ).apply {
            setPadding(0, dp(12), 0, dp(4))
        }

        statusDescription = text(
            "Выберите сервер и подключитесь",
            12f,
            palette.muted
        )

        card.addView(statusText)
        card.addView(statusDescription)

        root.addView(card)

        addSpace(12)

        val connectCard = makeCard()

        connectButton = text(
            "ПОДКЛЮЧИТЬСЯ",
            14f,
            Color.WHITE,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = rounded(accent, 17)
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

        connectCard.addView(
            text(
                "Нажмите ещё раз, чтобы отключиться",
                10f,
                palette.muted
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }
        )

        root.addView(connectCard)
    }

    private fun buildTrafficCard() {
        val card = makeCard()

        card.addView(
            text(
                "ТРАФИК СЕАНСА",
                10f,
                palette.muted,
                true
            ).apply {
                letterSpacing = 1.2f
            }
        )

        trafficText = text(
            "↑ 0 B       ↓ 0 B",
            15f,
            palette.foreground,
            true
        ).apply {
            setPadding(0, dp(12), 0, 0)
        }

        card.addView(trafficText)
        root.addView(card)
    }

    // ---------------------------------------------------------
    // Add menu
    // ---------------------------------------------------------

    private fun showAddMenu() {
        val options = arrayOf(
            "Вставить из буфера обмена",
            "Добавить вручную",
            "Добавить подписку",
            "Настройки эмуляции клиента",
            "Раздельное туннелирование"
        )

        AlertDialog.Builder(this)
            .setTitle("Добавить")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pasteFromClipboard()
                    1 -> importProfile()
                    2 -> showSubscriptionDialog()
                    3 -> startActivity(
                        Intent(
                            this,
                            NetworkToolsActivity::class.java
                        )
                    )
                    4 -> startActivity(
                        Intent(
                            this,
                            SplitTunnelingActivity::class.java
                        )
                    )
                }
            }
            .show()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

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

    private fun importProfile() {
        val input = EditText(this).apply {
            hint = "vless://, vmess://, trojan://, ss://"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }

        AlertDialog.Builder(this)
            .setTitle("Добавить сервер")
            .setMessage("Вставьте ссылку конфигурации")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                importProfileValue(
                    input.text.toString().trim()
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun importProfileValue(value: String) {
        try {
            val profile = ProfileCodec.parse(value)
            val stored = ProfileStore.add(this, profile)

            selectedId = stored.id

            getSharedPreferences(
                "ws_settings",
                MODE_PRIVATE
            ).edit()
                .putString("active_profile", stored.id)
                .apply()

            refreshProfiles()
            showMessage("Сервер добавлен")
        } catch (e: Exception) {
            showMessage("Ошибка импорта: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // Subscriptions
    // ---------------------------------------------------------

    private fun showSubscriptionDialog(
        existing: SubscriptionGroup? = null
    ) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        val urlInput = EditText(this).apply {
            hint = "https://example.com/subscription"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI

            if (existing != null) {
                setText(existing.url)
            }
        }

        val titleInput = EditText(this).apply {
            hint = "Название подписки (необязательно)"

            if (existing != null) {
                setText(existing.title)
            }
        }

        panel.addView(urlInput)
        panel.addView(titleInput)

        val emulate = TextView(this).apply {
            text = "Настроить эмуляцию клиента →"
            textSize = 13f
            setTextColor(accent)
            setPadding(0, dp(15), 0, dp(8))
            setOnClickListener {
                startActivity(
                    Intent(
                        this@MainActivity,
                        NetworkToolsActivity::class.java
                    )
                )
            }
        }

        panel.addView(emulate)

        AlertDialog.Builder(this)
            .setTitle(
                if (existing == null) {
                    "Новая подписка"
                } else {
                    "Изменить подписку"
                }
            )
            .setView(panel)
            .setPositiveButton(
                if (existing == null) "Добавить" else "Сохранить"
            ) { _, _ ->
                val url = urlInput.text.toString().trim()
                val title = titleInput.text.toString().trim()

                if (!url.startsWith("https://") &&
                    !url.startsWith("http://")
                ) {
                    showMessage("Укажите корректный URL подписки")
                    return@setPositiveButton
                }

                if (existing == null) {
                    updateSubscription(url, title)
                } else {
                    changeSubscriptionUrl(
                        existing,
                        url,
                        title
                    )
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSubscription(
        url: String,
        customTitle: String = ""
    ) {
        showMessage("Загружаю подписку…")

        Thread {
            try {
                val result = kotlinx.coroutines.runBlocking {
                    SubscriptionManager.updateDetailed(
                        this@MainActivity,
                        url
                    )
                }

                if (customTitle.isNotBlank()) {
                    SubscriptionStore.rename(
                        this@MainActivity,
                        SubscriptionStore.idFor(url),
                        customTitle
                    )
                }

                runOnUiThread {
                    refreshProfiles()
                    showMessage(
                        "Импортировано серверов: ${result.imported}"
                    )
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

    private fun changeSubscriptionUrl(
        group: SubscriptionGroup,
        newUrl: String,
        newTitle: String
    ) {
        try {
            SubscriptionStore.changeUrl(
                this,
                group.id,
                newUrl
            )

            val newId = SubscriptionStore.idFor(newUrl)

            if (newTitle.isNotBlank()) {
                SubscriptionStore.rename(
                    this,
                    newId,
                    newTitle
                )
            }

            refreshProfiles()
            updateSubscription(newUrl, newTitle)
        } catch (e: Exception) {
            showMessage("Не удалось изменить подписку: ${e.message}")
        }
    }

    private fun refreshSubscription(group: SubscriptionGroup) {
        showMessage("Обновляю ${group.title}…")
        updateSubscription(group.url, group.title)
    }

    // ---------------------------------------------------------
    // Subscription groups and profiles
    // ---------------------------------------------------------

    private fun refreshProfiles() {
        if (!::serverContainer.isInitialized) return

        serverContainer.removeAllViews()

        val profiles = ProfileStore.getAll(this)
        val groups = SubscriptionStore.all(this)

        if (
            selectedId == null ||
            profiles.none { it.id == selectedId }
        ) {
            val saved = getSharedPreferences(
                "ws_settings",
                MODE_PRIVATE
            ).getString("active_profile", null)

            selectedId = profiles.firstOrNull {
                it.id == saved
            }?.id ?: profiles.firstOrNull()?.id
        }

        if (profiles.isEmpty()) {
            serverContainer.addView(
                makeCard().apply {
                    addView(
                        text(
                            "Пока нет серверов",
                            16f,
                            palette.foreground,
                            true
                        ).apply {
                            gravity = Gravity.CENTER
                        }
                    )

                    addView(
                        text(
                            "Нажмите +, чтобы добавить сервер или подписку",
                            12f,
                            palette.muted
                        ).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(8), 0, 0)
                        }
                    )
                }
            )
            return
        }

        val groupedIds = groups
            .flatMap { it.profileIds }
            .toSet()

        val sortedGroups = groups.sortedByDescending {
            SubscriptionStore.isPinned(this, it.id)
        }

        sortedGroups.forEach { group ->
            val members = profiles.filter {
                it.id in group.profileIds
            }

            if (members.isNotEmpty()) {
                addSubscriptionGroup(group, members)
            }
        }

        profiles.filterNot {
            it.id in groupedIds
        }.forEach {
            addProfileCard(it, serverContainer)
        }
    }

    private fun addSubscriptionGroup(
        group: SubscriptionGroup,
        members: List<ServerProfile>
    ) {
        val prefs = getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        )

        val collapsed = prefs.getBoolean(
            "subscription_collapsed_${group.id}",
            false
        )

        val pinned = SubscriptionStore.isPinned(
            this,
            group.id
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(12))
            background = rounded(
                palette.surface,
                19
            )
        }

        val heading = row()

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        }

        titleBlock.addView(
            text(
                (if (collapsed) "▸ " else "▾ ") + group.title,
                14f,
                palette.foreground,
                true
            )
        )

        titleBlock.addView(
            text(
                "${members.size} серверов" +
                    if (pinned) "  ·  Закреплено" else "",
                10f,
                palette.muted
            ).apply {
                setPadding(0, dp(4), 0, 0)
            }
        )

        titleBlock.setOnClickListener {
            prefs.edit()
                .putBoolean(
                    "subscription_collapsed_${group.id}",
                    !collapsed
                )
                .apply()

            refreshProfiles()
        }

        heading.addView(titleBlock)

        heading.addView(
            groupAction("◷") {
                pingSubscription(group, members)
            }
        )

        heading.addView(
            groupAction("↻") {
                refreshSubscription(group)
            }
        )

        heading.addView(
            groupAction("⋮") {
                showGroupMenu(group)
            }
        )

        panel.addView(heading)

        if (!collapsed) {
            addSpaceTo(panel, 10)

            members.forEach { profile ->
                addProfileCard(profile, panel)
            }
        }

        serverContainer.addView(
            panel,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun showGroupMenu(group: SubscriptionGroup) {
        val pinned = SubscriptionStore.isPinned(
            this,
            group.id
        )

        val options = arrayOf(
            "Изменить URL",
            "Изменить название",
            if (pinned) "Открепить сверху" else "Закрепить сверху",
            "Настройки эмуляции клиента",
            "Удалить группу"
        )

        AlertDialog.Builder(this)
            .setTitle(group.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSubscriptionDialog(group)

                    1 -> renameSubscription(group)

                    2 -> {
                        SubscriptionStore.setPinned(
                            this,
                            group.id,
                            !pinned
                        )
                        refreshProfiles()
                    }

                    3 -> startActivity(
                        Intent(
                            this,
                            NetworkToolsActivity::class.java
                        )
                    )

                    4 -> confirmRemoveGroup(group)
                }
            }
            .show()
    }

    private fun renameSubscription(group: SubscriptionGroup) {
        val input = EditText(this).apply {
            setText(group.title)
            hint = "Название подписки"
        }

        AlertDialog.Builder(this)
            .setTitle("Название подписки")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                SubscriptionStore.rename(
                    this,
                    group.id,
                    input.text.toString()
                )
                refreshProfiles()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmRemoveGroup(group: SubscriptionGroup) {
        AlertDialog.Builder(this)
            .setTitle("Удалить подписку?")
            .setMessage(
                "Группа «${group.title}» будет удалена. " +
                    "Импортированные серверы останутся в списке."
            )
            .setPositiveButton("Удалить") { _, _ ->
                SubscriptionStore.remove(this, group.id)
                refreshProfiles()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addProfileCard(
        profile: ServerProfile,
        parent: LinearLayout
    ) {
        val selected = profile.id == selectedId

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))

            background = rounded(
                if (selected) {
                    withAlpha(accent, 22)
                } else {
                    withAlpha(
                        palette.background,
                        55
                    )
                },
                14
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

        val top = row()

        val name = text(
            profile.name,
            13f,
            palette.foreground,
            true
        )

        name.layoutParams = LinearLayout.LayoutParams(
            0,
            -2,
            1f
        )

        top.addView(name)

        if (selected) {
            top.addView(
                text(
                    "●",
                    10f,
                    Color.rgb(64, 190, 132),
                    true
                )
            )
        }

        card.addView(top)

        card.addView(
            text(
                "${profile.protocol}  ·  ${profile.address}:${profile.port}",
                10f,
                palette.muted
            ).apply {
                setPadding(0, dp(6), 0, 0)
            }
        )

        parent.addView(
            card,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(7)
            }
        )
    }

    private fun groupAction(
        label: String,
        action: () -> Unit
    ): TextView {
        return text(
            label,
            19f,
            palette.foreground,
            true
        ).apply {
            gravity = Gravity.CENTER
            background = rounded(
                withAlpha(accent, 25),
                11
            )
            layoutParams = LinearLayout.LayoutParams(
                dp(37),
                dp(37)
            ).apply {
                leftMargin = dp(5)
            }
            setOnClickListener { action() }
        }
    }

    private fun pingSubscription(
        group: SubscriptionGroup,
        members: List<ServerProfile>
    ) {
        showMessage("Проверяю ${members.size} серверов…")

        Thread {
            val results = members.map { profile ->
                profile.name to runCatching {
                    ServerLatency.check(profile)
                }.getOrNull()
            }

            runOnUiThread {
                val message = results.joinToString("\n") {
                    (name, ms) ->
                    "$name — ${ms?.let { "$it мс" } ?: "ошибка"}"
                }

                AlertDialog.Builder(this)
                    .setTitle("Пинг · ${group.title}")
                    .setMessage(message.ifBlank { "Нет серверов" })
                    .setPositiveButton("Закрыть", null)
                    .show()
            }
        }.start()
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

    private fun confirmDeleteProfile(profile: ServerProfile) {
        AlertDialog.Builder(this)
            .setTitle("Удалить сервер?")
            .setMessage("Профиль «${profile.name}» будет удалён.")
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

    // ---------------------------------------------------------
    // VPN
    // ---------------------------------------------------------

    private fun connectVPN() {
        val profiles = ProfileStore.getAll(this)

        if (profiles.isEmpty()) {
            showMessage("Сначала добавьте сервер")
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
                Intent(
                    this,
                    VPNService::class.java
                ).apply {
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
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                startVPNService()
            } else {
                connectionState = VPNService.STATE_DISCONNECTED
                updateConnectionUi("Разрешение VPN не выдано")
            }
        }
    }

    private fun updateConnectionUi(message: String? = null) {
        if (!::statusText.isInitialized) return

        when (connectionState) {
            VPNService.STATE_CONNECTING -> {
                statusText.text = "Подключение…"
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

                else -> palette.foreground
            }
        )

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
            if (XrayManager.startedAtElapsedRealtime > 0L) {
                TrafficMonitor.formatDuration(
                    android.os.SystemClock.elapsedRealtime() -
                        XrayManager.startedAtElapsedRealtime
                )
            } else {
                "—"
            }
        )
    }

    // ---------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(17))
            background = rounded(
                withAlpha(
                    palette.surface,
                    Customization.cardAlpha(this@MainActivity)
                ),
                19
            )
            elevation = dp(2).toFloat()

            layoutParams = LinearLayout.LayoutParams(
                -1,
                -2
            )
        }
    }

    private fun row(content: (LinearLayout.() -> Unit)? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            content?.invoke(this)
        }
    }

    private fun row(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun iconButton(
        symbol: String,
        backgroundColor: Int,
        textColor: Int = palette.foreground,
        action: () -> Unit
    ): TextView {
        return text(
            symbol,
            22f,
            textColor,
            true
        ).apply {
            gravity = Gravity.CENTER
            background = rounded(backgroundColor, 14)
            layoutParams = LinearLayout.LayoutParams(
                dp(46),
                dp(44)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun addSpace(height: Int) {
        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun addSpaceTo(
        parent: LinearLayout,
        height: Int
    ) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun withAlpha(
        color: Int,
        percent: Int
    ): Int {
        return Color.argb(
            percent.coerceIn(0, 100) * 255 / 100,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {
        private const val REQUEST_VPN = 100
    }
}
