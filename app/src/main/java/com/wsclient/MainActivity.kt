
package com.wsclient

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ConcurrentHashMap

private const val MP = -1
private const val WC = -2
private val GREEN = Color.rgb(64, 190, 132)
private val RED = Color.rgb(235, 91, 91)
private val AMBER = Color.rgb(240, 180, 60)

class MainActivity : Activity() {

    private val palette by lazy { Customization.palette(this) }
    private val accent by lazy { Customization.accent(this) }
    private val inner get() = Ui.mix(palette.surface, palette.foreground, 0.08f)
    private val activeColor get() =
        if (palette.isLight) accent else Ui.mix(accent, Color.WHITE, 0.3f)

    private lateinit var pages: List<ScrollView>
    private lateinit var navGlyphs: List<GlyphView>
    private lateinit var navLabels: List<TextView>

    private lateinit var statusText: TextView
    private lateinit var statusDescription: TextView
    private lateinit var connectButton: TextView
    private lateinit var powerButton: PowerButtonView
    private lateinit var homeServerName: TextView
    private lateinit var homeServerInfo: TextView
    private lateinit var serversList: LinearLayout
    private lateinit var statUp: TextView
    private lateinit var statUpSpeed: TextView
    private lateinit var statDown: TextView
    private lateinit var statDownSpeed: TextView
    private lateinit var statTime: TextView

    private val pingResults = ConcurrentHashMap<String, Long>() // -1 = ошибка

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

            connectionState =
                intent.getStringExtra(VPNService.EXTRA_STATE) ?: return

            updateConnectionUi(
                intent.getStringExtra(VPNService.EXTRA_MESSAGE)
            )
        }
    }

    // ---------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedId = prefs().getString("active_profile", null)

        connectionState =
            if (XrayManager.running) {
                VPNService.STATE_CONNECTED
            } else {
                VPNService.STATE_DISCONNECTED
            }

        buildInterface()
        refreshProfiles()
        updateConnectionUi()
        showTab(lastTab)

        appearanceFingerprint = currentAppearanceFingerprint()

        requestNotificationPermission()

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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
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

    // Берём свежие значения, а не закэшированные,
    // иначе смена темы не заметится.
    private fun currentAppearanceFingerprint(): String =
        listOf(
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

        runCatching {
            unregisterReceiver(stateReceiver)
        }

        receiverRegistered = false
    }

    // ---------------------------------------------------------
    // Shell: страницы + нижняя навигация
    // ---------------------------------------------------------

    private fun buildInterface() {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
            clipChildren = false
            clipToPadding = false
        }

        Customization.applyWallpaper(this, frame)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val host = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        pages = listOf(
            buildHomePage(),
            buildServersPage(),
            buildStatsPage(),
            buildSettingsPage()
        )

        pages.forEach { page ->
            host.addView(
                page,
                FrameLayout.LayoutParams(MP, MP)
            )
        }

        val nav = buildNav()

        column.addView(
            host,
            lp(MP, 0, 1f)
        )

        column.addView(
            nav,
            lp(MP, WC)
        )

        frame.addView(
            column,
            FrameLayout.LayoutParams(MP, MP)
        )

        setContentView(frame)

        Ui.prepareWindow(this, palette)

        Ui.onInsets(frame) { insets ->

            // Верхний системный отступ.
            column.setPadding(
                insets.left,
                insets.top,
                insets.right,
                0
            )

            // Нижняя панель учитывает системную навигацию
            // и область жестов Android.
            nav.setPadding(
                dp(10),
                dp(9),
                dp(10),
                dp(14) + insets.bottom
            )

            nav.minimumHeight = dp(76) + insets.bottom

            nav.clipChildren = false
            nav.clipToPadding = false
        }
    }

    private fun buildNav(): LinearLayout {
        val radius = dp(24).toFloat()

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false

            background = GradientDrawable().apply {
                setColor(
                    Ui.mix(
                        palette.surface,
                        palette.background,
                        0.1f
                    )
                )

                cornerRadii = floatArrayOf(
                    radius, radius,
                    radius, radius,
                    0f, 0f,
                    0f, 0f
                )
            }
        }

        val definitions = listOf(
            Glyph.HOME to "Главная",
            Glyph.LIST to "Серверы",
            Glyph.CHART to "Статистика",
            Glyph.GEAR to "Настройки"
        )

        val glyphs = ArrayList<GlyphView>()
        val labels = ArrayList<TextView>()

        definitions.forEachIndexed { index, (glyph, title) ->

            val icon = GlyphView(
                this,
                glyph,
                palette.muted
            )

            val label = text(
                title,
                11f,
                palette.muted
            ).apply {
                gravity = Gravity.CENTER
                includeFontPadding = true

                minHeight = dp(20)

                setPadding(
                    dp(1),
                    dp(3),
                    dp(1),
                    dp(3)
                )

                maxLines = 1
                isSingleLine = true
            }

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER

                clipChildren = false
                clipToPadding = false

                setPadding(
                    dp(2),
                    dp(5),
                    dp(2),
                    dp(5)
                )

                addView(
                    icon,
                    lp(dp(25), dp(25))
                )

                addView(
                    label,
                    lp(MP, WC)
                )

                minimumHeight = dp(62)
            }.onTap {
                showTab(index)
            }

            glyphs.add(icon)
            labels.add(label)

            nav.addView(
                item,
                lp(0, WC, 1f)
            )
        }

        navGlyphs = glyphs
        navLabels = labels

        return nav
    }

    private fun showTab(index: Int) {
        if (index !in pages.indices) return

        lastTab = index

        pages.forEachIndexed { i, page ->
            page.visibility =
                if (i == index) View.VISIBLE else View.GONE
        }

        navGlyphs.forEachIndexed { i, glyph ->
            glyph.tint(
                if (i == index) activeColor else palette.muted
            )
        }

        navLabels.forEachIndexed { i, label ->
            label.setTextColor(
                if (i == index) activeColor else palette.muted
            )

            label.typeface =
                if (i == index) {
                    Typeface.DEFAULT_BOLD
                } else {
                    Typeface.DEFAULT
                }
        }
    }

    private fun page(
        content: LinearLayout.() -> Unit
    ): ScrollView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(14),
                dp(18),
                dp(20)
            )

            content()
        }

        return ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(column)
        }
    }

    // ---------------------------------------------------------
    // Главная
    // ---------------------------------------------------------

    private fun buildHomePage() = page {

        val head = row()

        head.addView(
            logoTile(),
            lp(dp(46), dp(46))
        )

        val titles = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        titles.addView(
            text(
                "WS Client",
                22f,
                palette.foreground,
                true
            ).apply {
                setOnClickListener {
                    if (++titleClicks >= 5) {
                        titleClicks = 0

                        Customization.togglePastel(
                            this@MainActivity
                        )

                        recreate()
                    }
                }
            }
        )

        titles.addView(
            text(
                "Your connection. Your rules.",
                12f,
                palette.muted
            )
        )

        head.addView(
            titles,
            lp(0, WC, 1f)
        )

        addView(head)
        space(18)

        // Карточка состояния.
        val connectionCard = card(26, 20)

        connectionCard.addView(
            label("СОСТОЯНИЕ ПОДКЛЮЧЕНИЯ")
        )

        statusText = text(
            "Отключено",
            30f,
            palette.foreground,
            true
        ).apply {
            setPadding(
                0,
                dp(10),
                0,
                dp(4)
            )
        }

        statusDescription = text(
            "Выбери сервер и нажми кнопку подключения",
            13f,
            palette.muted
        )

        connectionCard.addView(statusText)
        connectionCard.addView(statusDescription)

        powerButton = PowerButtonView(this@MainActivity).apply {
            setOnClickListener {
                toggleConnection()
            }
        }

        connectionCard.addView(
            powerButton,
            lp(dp(132), dp(132)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(22)
                bottomMargin = dp(22)
            }
        )

        connectButton = text(
            "ПОДКЛЮЧИТЬСЯ",
            14f,
            Color.WHITE,
            true
        ).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }.onTap {
            toggleConnection()
        }

        connectionCard.addView(
            connectButton,
            lp(MP, dp(54))
        )

        addView(connectionCard)
        space(12)

        // Выбранный сервер.
        val serverCard = card(
            20,
            16,
            horizontal = true
        ).onTap {
            showTab(1)
        }

        val serverText = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        serverText.addView(
            text(
                "Выбранный сервер",
                12f,
                palette.muted
            )
        )

        homeServerName = text(
            "—",
            18f,
            palette.foreground,
            true
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(2))
        }

        homeServerInfo = text(
            "",
            12f,
            palette.muted
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        serverText.addView(homeServerName)
        serverText.addView(homeServerInfo)

        serverCard.addView(
            serverText,
            lp(0, WC, 1f)
        )

        serverCard.addView(
            GlyphView(
                this@MainActivity,
                Glyph.CHEVRON,
                palette.muted
            ),
            lp(dp(22), dp(22))
        )

        addView(serverCard)
        space(12)

        // Импорт / Подписки.
        val duo = row()

        duo.addView(
            tileCard(
                Glyph.DOWNLOAD,
                "Импорт",
                "Добавить профиль"
            ) {
                showAddMenu()
            },
            lp(0, WC, 1f).apply {
                rightMargin = dp(6)
            }
        )

        duo.addView(
            tileCard(
                Glyph.LAYERS,
                "Подписки",
                "Управление"
            ) {
                showTab(1)
            },
            lp(0, WC, 1f).apply {
                leftMargin = dp(6)
            }
        )

        addView(duo)
    }

    private fun logoTile() = FrameLayout(this).apply {
        background = rounded(
            Ui.withAlpha(accent, 24),
            14
        )

        val color =
            if (palette.isLight) {
                accent
            } else {
                Ui.mix(accent, Color.WHITE, 0.35f)
            }

        addView(
            text(
                "WS",
                16f,
                color,
                true
            ).apply {
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(MP, MP)
        )
    }

    private fun tileCard(
        glyph: Glyph,
        title: String,
        sub: String,
        action: () -> Unit
    ): LinearLayout {
        val card = card(20, 16).onTap(action)

        card.addView(
            GlyphView(this, glyph, activeColor),
            lp(dp(24), dp(24))
        )

        card.addView(
            text(
                title,
                17f,
                palette.foreground,
                true
            ).apply {
                setPadding(0, dp(14), 0, dp(2))
            }
        )

        card.addView(
            text(
                sub,
                12f,
                palette.muted
            )
        )

        return card
    }

    // ---------------------------------------------------------
    // Серверы
    // ---------------------------------------------------------

    private fun buildServersPage() = page {

        val head = row()

        head.addView(
            text(
                "Серверы",
                24f,
                palette.foreground,
                true
            ),
            lp(0, WC, 1f)
        )

        val plus = FrameLayout(this@MainActivity).apply {
            background = rounded(accent, 14)

            addView(
                GlyphView(
                    this@MainActivity,
                    Glyph.PLUS,
                    Color.WHITE
                ),
                FrameLayout.LayoutParams(
                    dp(22),
                    dp(22),
                    Gravity.CENTER
                )
            )
        }.onTap {
            showAddMenu()
        }

        head.addView(
            plus,
            lp(dp(44), dp(44))
        )

        addView(head)
        space(14)

        serversList = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }

        addView(serversList)
    }

    private fun refreshProfiles() {
        if (!::serversList.isInitialized) return

        val profiles = ProfileStore.getAll(this)
        val groups = SubscriptionStore.all(this)

        if (
            selectedId == null ||
            profiles.none { it.id == selectedId }
        ) {
            val saved = prefs().getString(
                "active_profile",
                null
            )

            selectedId =
                profiles.firstOrNull { it.id == saved }?.id
                    ?: profiles.firstOrNull()?.id
        }

        refreshHomeServer(profiles)
        serversList.removeAllViews()

        if (profiles.isEmpty()) {
            val empty = card(22, 20)

            empty.addView(
                text(
                    "Пока нет серверов",
                    16f,
                    palette.foreground,
                    true
                ).apply {
                    gravity = Gravity.CENTER
                },
                lp(MP, WC)
            )

            empty.addView(
                text(
                    "Нажмите +, чтобы добавить сервер или подписку",
                    12f,
                    palette.muted
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                },
                lp(MP, WC)
            )

            serversList.addView(empty)
            return
        }

        val groupedIds = groups
            .flatMap { it.profileIds }
            .toSet()

        groups
            .sortedByDescending {
                SubscriptionStore.isPinned(this, it.id)
            }
            .forEach { group ->
                val members = profiles.filter {
                    it.id in group.profileIds
                }

                if (members.isNotEmpty()) {
                    addServerCard(
                        group,
                        group.title,
                        members
                    )
                }
            }

        val loose = profiles.filterNot {
            it.id in groupedIds
        }

        if (loose.isNotEmpty()) {
            addServerCard(
                null,
                "Отдельные серверы",
                loose
            )
        }
    }

    private fun refreshHomeServer(
        profiles: List<ServerProfile>
    ) {
        if (!::homeServerName.isInitialized) return

        val server = profiles.firstOrNull {
            it.id == selectedId
        }

        homeServerName.text =
            server?.name ?: "Сервер не выбран"

        homeServerInfo.text =
            if (server != null) {
                "${server.protocol} · ${server.address}:${server.port}"
            } else {
                "Добавьте сервер или подписку"
            }
    }

    private fun addServerCard(
        group: SubscriptionGroup?,
        title: String,
        members: List<ServerProfile>
    ) {
        val preferences = prefs()

        val collapseKey =
            "subscription_collapsed_${group?.id}"

        val collapsed =
            group != null &&
                preferences.getBoolean(collapseKey, false)

        val pinned =
            group != null &&
                SubscriptionStore.isPinned(this, group.id)

        val card = card(22, 16)

        val head = row().onTap {
            if (group != null) {
                preferences.edit()
                    .putBoolean(collapseKey, !collapsed)
                    .apply()

                refreshProfiles()
            }
        }

        head.addView(
            GlyphView(
                this,
                Glyph.GLOBE,
                activeColor
            ),
            lp(dp(24), dp(24))
        )

        head.addView(
            text(
                title,
                17f,
                palette.foreground,
                true
            ).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10), 0, dp(8), 0)
            },
            lp(0, WC, 1f)
        )

        head.addView(
            text(
                "${members.size} серверов" +
                    if (pinned) " · ★" else "",
                12f,
                palette.muted
            )
        )

        card.addView(head)

        if (group != null) {
            card.space(14)

            val actions = row()

            actions.addView(
                groupButton(
                    Glyph.GAUGE,
                    "Пинг"
                ) {
                    pingServers(members)
                },
                lp(0, dp(68), 1f).apply {
                    rightMargin = dp(5)
                }
            )

            actions.addView(
                groupButton(
                    Glyph.REFRESH,
                    "Обновить"
                ) {
                    refreshSubscription(group)
                },
                lp(0, dp(68), 1f).apply {
                    leftMargin = dp(5)
                    rightMargin = dp(5)
                }
            )

            actions.addView(
                groupButton(
                    Glyph.MORE,
                    "Настройки"
                ) {
                    showGroupMenu(group)
                },
                lp(0, dp(68), 1f).apply {
                    leftMargin = dp(5)
                }
            )

            card.addView(actions)
        }

        if (!collapsed) {
            card.space(12)

            card.addView(
                View(this).apply {
                    setBackgroundColor(
                        Ui.withAlpha(palette.foreground, 10)
                    )
                },
                lp(MP, 1)
            )

            members.forEach {
                addServerRow(it, card)
            }
        }

        serversList.addView(
            card,
            lp(MP, WC).apply {
                bottomMargin = dp(12)
            }
        )
    }

    private fun groupButton(
        glyph: Glyph,
        label: String,
        action: () -> Unit
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(inner, 14)

            addView(
                GlyphView(
                    this@MainActivity,
                    glyph,
                    palette.muted
                ),
                lp(dp(22), dp(22))
            )

            addView(
                text(
                    label,
                    13f,
                    palette.muted
                ).apply {
                    setPadding(0, dp(5), 0, 0)
                }
            )
        }.onTap(action)

    private fun addServerRow(
        profile: ServerProfile,
        parent: LinearLayout
    ) {
        val selected = profile.id == selectedId

        val row = row()
            .apply {
                setPadding(
                    dp(2),
                    dp(11),
                    dp(2),
                    dp(11)
                )
            }
            .onTap {
                selectProfile(profile.id)
            }

        row.setOnLongClickListener {
            confirmDeleteProfile(profile)
            true
        }

        row.addView(
            GlyphView(
                this,
                Glyph.SERVER,
                if (selected) GREEN else palette.foreground
            ),
            lp(dp(22), dp(22))
        )

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        column.addView(
            text(
                profile.name,
                15f,
                palette.foreground,
                selected
            ).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )

        column.addView(
            text(
                "${profile.protocol} · ${profile.address}:${profile.port}",
                11f,
                palette.muted
            ).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )

        row.addView(
            column,
            lp(0, WC, 1f).apply {
                leftMargin = dp(12)
                rightMargin = dp(8)
            }
        )

        if (selected) {
            row.addView(
                badge("Выбран", GREEN)
            )
        } else {
            pingResults[profile.id]?.let { ms ->
                val color = when {
                    ms < 0 -> RED
                    ms < 250 -> GREEN
                    ms < 600 -> AMBER
                    else -> RED
                }

                row.addView(
                    text(
                        if (ms < 0) "ошибка" else "$ms мс",
                        12f,
                        color,
                        true
                    )
                )
            }
        }

        parent.addView(
            row,
            lp(MP, WC)
        )
    }

    private fun badge(
        label: String,
        color: Int
    ) =
        text(
            label,
            12f,
            color,
            true
        ).apply {
            setPadding(
                dp(11),
                dp(5),
                dp(11),
                dp(5)
            )

            background = rounded(
                Ui.withAlpha(color, 16),
                20
            )
        }

    private fun pingServers(
        members: List<ServerProfile>
    ) {
        showMessage(
            "Проверяю ${members.size} серверов…"
        )

        Thread {
            members.forEach { profile ->
                pingResults[profile.id] =
                    runCatching {
                        ServerLatency.check(profile)
                    }.getOrDefault(-1L)

                runOnUiThread {
                    if (!isFinishing) {
                        refreshProfiles()
                    }
                }
            }
        }.start()
    }

    // ---------------------------------------------------------
    // Статистика
    // ---------------------------------------------------------

    private fun buildStatsPage() = page {

        addView(
            text(
                "Статистика",
                24f,
                palette.foreground,
                true
            )
        )

        space(14)

        val duo = row()

        duo.addView(
            metricCard(
                "↑ ОТПРАВЛЕНО",
                GREEN
            ) { value, speed ->
                statUp = value
                statUpSpeed = speed
            },
            lp(0, WC, 1f).apply {
                rightMargin = dp(6)
            }
        )

        duo.addView(
            metricCard(
                "↓ ПОЛУЧЕНО",
                activeColor
            ) { value, speed ->
                statDown = value
                statDownSpeed = speed
            },
            lp(0, WC, 1f).apply {
                leftMargin = dp(6)
            }
        )

        addView(duo)
        space(12)

        val timeCard = card(20, 16)

        timeCard.addView(
            label("ВРЕМЯ СЕАНСА")
        )

        statTime = text(
            "—",
            24f,
            palette.foreground,
            true
        ).apply {
            setPadding(0, dp(8), 0, 0)
        }

        timeCard.addView(statTime)
        addView(timeCard)
    }

    private fun metricCard(
        title: String,
        color: Int,
        bind: (TextView, TextView) -> Unit
    ): LinearLayout {
        val card = card(20, 16)

        card.addView(label(title))

        val value = text(
            "0.00 B",
            20f,
            palette.foreground,
            true
        ).apply {
            setPadding(0, dp(10), 0, dp(3))
        }

        val speed = text(
            "0.00 B/s",
            12f,
            color
        )

        card.addView(value)
        card.addView(speed)

        bind(value, speed)

        return card
    }

    // ---------------------------------------------------------
    // Настройки
    // ---------------------------------------------------------

    private fun buildSettingsPage() = page {

        addView(
            text(
                "Настройки",
                24f,
                palette.foreground,
                true
            )
        )

        space(14)

        addView(
            settingRow(
                Glyph.GEAR,
                "Внешний вид",
                "Темы, акцент, обои"
            ) {
                startActivity(
                    Intent(
                        this@MainActivity,
                        AppearanceActivity::class.java
                    )
                )
            }
        )

        space(10)

        addView(
            settingRow(
                Glyph.LAYERS,
                "Раздельное туннелирование",
                "Выбор приложений для VPN"
            ) {
                startActivity(
                    Intent(
                        this@MainActivity,
                        SplitTunnelingActivity::class.java
                    )
                )
            }
        )

        space(10)

        addView(
            settingRow(
                Glyph.GLOBE,
                "Эмуляция клиента",
                "User-Agent, HWID, заголовки подписки"
            ) {
                startActivity(
                    Intent(
                        this@MainActivity,
                        NetworkToolsActivity::class.java
                    )
                )
            }
        )

        space(10)

        val switch = Switch(this@MainActivity).apply {
            isChecked = AutoConnect.enabled(this@MainActivity)

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            )

            thumbTintList = ColorStateList(
                states,
                intArrayOf(accent, palette.muted)
            )

            trackTintList = ColorStateList(
                states,
                intArrayOf(
                    Ui.withAlpha(accent, 40),
                    Ui.withAlpha(palette.muted, 30)
                )
            )

            setOnCheckedChangeListener { _, value ->
                AutoConnect.setEnabled(
                    this@MainActivity,
                    value
                )
            }
        }

        addView(
            settingRow(
                Glyph.POWER,
                "Автоподключение",
                "Подключаться при запуске",
                switch
            ) {
                switch.toggle()
            }
        )

        space(22)

        val version = runCatching {
            packageManager
                .getPackageInfo(packageName, 0)
                .versionName
        }.getOrNull()

        addView(
            text(
                "WS Client · Xray core" +
                    if (version != null) " · v$version" else "",
                11f,
                palette.muted
            ).apply {
                gravity = Gravity.CENTER
            },
            lp(MP, WC)
        )
    }

    private fun settingRow(
        glyph: Glyph,
        title: String,
        sub: String,
        trailing: View? = null,
        action: () -> Unit
    ): LinearLayout {
        val row = card(
            18,
            14,
            horizontal = true
        ).onTap(action)

        val tile = FrameLayout(this).apply {
            background = rounded(
                Ui.withAlpha(accent, 18),
                12
            )

            addView(
                GlyphView(
                    this@MainActivity,
                    glyph,
                    activeColor
                ),
                FrameLayout.LayoutParams(
                    dp(22),
                    dp(22),
                    Gravity.CENTER
                )
            )
        }

        row.addView(
            tile,
            lp(dp(42), dp(42))
        )

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)

            addView(
                text(
                    title,
                    15f,
                    palette.foreground,
                    true
                )
            )

            addView(
                text(
                    sub,
                    12f,
                    palette.muted
                )
            )
        }

        row.addView(
            column,
            lp(0, WC, 1f)
        )

        if (trailing != null) {
            row.addView(
                trailing,
                lp(WC, WC)
            )
        } else {
            row.addView(
                GlyphView(
                    this,
                    Glyph.CHEVRON,
                    palette.muted
                ),
                lp(dp(20), dp(20))
            )
        }

        return row
    }

    // ---------------------------------------------------------
    // Добавление серверов
    // ---------------------------------------------------------

    private fun showAddMenu() {
        val options = arrayOf(
            "Вставить из буфера обмена",
            "Добавить вручную",
            "Добавить подписку"
        )

        AlertDialog.Builder(this)
            .setTitle("Добавить")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pasteFromClipboard()
                    1 -> importProfile()
                    2 -> showSubscriptionDialog()
                }
            }
            .show()
    }

    private fun pasteFromClipboard() {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

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

            inputType =
                InputType.TYPE_CLASS_TEXT or
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

            prefs().edit()
                .putString("active_profile", stored.id)
                .apply()

            refreshProfiles()
            showMessage("Сервер добавлен")
        } catch (e: Exception) {
            showMessage(
                "Ошибка импорта: ${e.message}"
            )
        }
    }

    // ---------------------------------------------------------
    // Подписки
    // ---------------------------------------------------------

    private fun showSubscriptionDialog(
        existing: SubscriptionGroup? = null
    ) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(8),
                dp(20),
                0
            )
        }

        val urlInput = EditText(this).apply {
            hint = "https://example.com/subscription"

            inputType =
                InputType.TYPE_CLASS_TEXT or
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

        panel.addView(
            TextView(this).apply {
                text = "Настроить эмуляцию клиента →"
                textSize = 13f
                setTextColor(accent)

                setPadding(
                    0,
                    dp(15),
                    0,
                    dp(8)
                )

                setOnClickListener {
                    startActivity(
                        Intent(
                            this@MainActivity,
                            NetworkToolsActivity::class.java
                        )
                    )
                }
            }
        )

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

                if (
                    !url.startsWith("https://") &&
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
            showMessage(
                "Не удалось изменить подписку: ${e.message}"
            )
        }
    }

    private fun refreshSubscription(
        group: SubscriptionGroup
    ) {
        showMessage("Обновляю ${group.title}…")
        updateSubscription(group.url, group.title)
    }

    private fun showGroupMenu(
        group: SubscriptionGroup
    ) {
        val pinned =
            SubscriptionStore.isPinned(this, group.id)

        val options = arrayOf(
            "Изменить URL",
            "Изменить название",
            if (pinned) {
                "Открепить сверху"
            } else {
                "Закрепить сверху"
            },
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

                    3 -> {
                        startActivity(
                            Intent(
                                this,
                                NetworkToolsActivity::class.java
                            )
                        )
                    }

                    4 -> confirmRemoveGroup(group)
                }
            }
            .show()
    }

    private fun renameSubscription(
        group: SubscriptionGroup
    ) {
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

    private fun confirmRemoveGroup(
        group: SubscriptionGroup
    ) {
        AlertDialog.Builder(this)
            .setTitle("Удалить подписку?")
            .setMessage(
                "Группа «${group.title}» будет удалена. " +
                    "Импортированные серверы останутся в списке."
            )
            .setPositiveButton("Удалить") { _, _ ->
                SubscriptionStore.remove(
                    this,
                    group.id
                )

                refreshProfiles()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun selectProfile(id: String) {
        selectedId = id

        prefs().edit()
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
                ProfileStore.remove(
                    this,
                    profile.id
                )

                if (selectedId == profile.id) {
                    selectedId = null

                    prefs().edit()
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

    private fun toggleConnection() {
        when (connectionState) {
            VPNService.STATE_CONNECTED,
            VPNService.STATE_CONNECTING -> disconnectVPN()

            else -> connectVPN()
        }
    }

    private fun connectVPN() {
        val profiles = ProfileStore.getAll(this)

        if (profiles.isEmpty()) {
            showMessage("Сначала добавьте сервер")
            showTab(1)
            return
        }

        val selected =
            profiles.firstOrNull { it.id == selectedId }
                ?: profiles.first()

        selectedId = selected.id

        prefs().edit()
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
            showMessage(
                "Ошибка отключения: ${e.message}"
            )
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

                updateConnectionUi(
                    "Разрешение VPN не выдано"
                )
            }
        }
    }

    private fun updateConnectionUi(
        message: String? = null
    ) {
        if (!::statusText.isInitialized) return

        val title: String
        val description: String
        val button: String

        when (connectionState) {
            VPNService.STATE_CONNECTING -> {
                title = "Подключение…"
                description =
                    message ?: "Подготавливаем VPN-туннель"
                button = "ОТМЕНИТЬ"
            }

            VPNService.STATE_CONNECTED -> {
                title = "Подключено"
                description =
                    message ?: "Защищённое соединение активно"
                button = "ОТКЛЮЧИТЬСЯ"
            }

            VPNService.STATE_ERROR -> {
                title = "Ошибка"
                description =
                    message ?: "Не удалось запустить VPN"
                button = "ПОПРОБОВАТЬ СНОВА"
            }

            else -> {
                title = "Отключено"
                description =
                    message ?: "Выбери сервер и нажми кнопку подключения"
                button = "ПОДКЛЮЧИТЬСЯ"
            }
        }

        statusText.text = title
        statusDescription.text = description

        statusDescription.setTextColor(
            if (connectionState == VPNService.STATE_ERROR) {
                Ui.mix(RED, palette.foreground, 0.25f)
            } else {
                palette.muted
            }
        )

        connectButton.text = button

        statusText.setTextColor(
            when (connectionState) {
                VPNService.STATE_CONNECTED -> GREEN
                VPNService.STATE_ERROR -> RED
                else -> palette.foreground
            }
        )

        val ring = when (connectionState) {
            VPNService.STATE_CONNECTED -> GREEN
            VPNService.STATE_ERROR -> RED
            VPNService.STATE_CONNECTING -> accent
            else -> activeColor
        }

        powerButton.update(
            ring,
            Ui.withAlpha(ring, 14),
            connectionState == VPNService.STATE_CONNECTING
        )

        val bg: Int
        val fg: Int

        when (connectionState) {
            VPNService.STATE_CONNECTED -> {
                bg = Ui.withAlpha(RED, 18)
                fg = RED
            }

            VPNService.STATE_CONNECTING -> {
                bg = inner
                fg = palette.foreground
            }

            else -> {
                bg = accent
                fg = Color.WHITE
            }
        }

        connectButton.background = rounded(bg, 16)
        connectButton.setTextColor(fg)

        refreshTraffic()
    }

    private fun refreshTraffic() {
        if (!::statUp.isInitialized) return

        val stats = TrafficMonitor.read()

        statUp.text =
            TrafficMonitor.format(stats.uploaded)

        statUpSpeed.text =
            "${TrafficMonitor.format(stats.uploadBytesPerSecond)}/s"

        statDown.text =
            TrafficMonitor.format(stats.downloaded)

        statDownSpeed.text =
            "${TrafficMonitor.format(stats.downloadBytesPerSecond)}/s"

        statTime.text =
            if (XrayManager.startedAtElapsedRealtime > 0L) {
                TrafficMonitor.formatDuration(
                    SystemClock.elapsedRealtime() -
                        XrayManager.startedAtElapsedRealtime
                )
            } else {
                "—"
            }
    }

    // ---------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------

    private fun prefs() =
        getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        )

    private fun lp(
        width: Int,
        height: Int,
        weight: Float = 0f
    ) =
        LinearLayout.LayoutParams(
            width,
            height,
            weight
        )

    private fun <T : View> T.onTap(
        action: () -> Unit
    ): T {
        isClickable = true
        isFocusable = true

        Ui.pressable(this)

        setOnClickListener {
            action()
        }

        return this
    }

    private fun LinearLayout.space(height: Int) {
        addView(
            View(this@MainActivity),
            LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        )
    }

    private fun card(
        radius: Int = 22,
        pad: Int = 18,
        horizontal: Boolean = false
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation =
                if (horizontal) {
                    LinearLayout.HORIZONTAL
                } else {
                    LinearLayout.VERTICAL
                }

            if (horizontal) {
                gravity = Gravity.CENTER_VERTICAL
            }

            setPadding(
                dp(pad),
                dp(pad),
                dp(pad),
                dp(pad)
            )

            background = rounded(
                Ui.withAlpha(
                    palette.surface,
                    Customization.cardAlpha(this@MainActivity)
                ),
                radius
            )

            layoutParams = LinearLayout.LayoutParams(
                MP,
                WC
            )
        }

    private fun row(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun label(value: String) =
        text(
            value,
            11f,
            palette.muted,
            true
        ).apply {
            letterSpacing = 0.08f
        }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }

    private fun rounded(
        color: Int,
        radius: Int
    ) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {
        private const val REQUEST_VPN = 100
        private const val REQUEST_NOTIFICATION_PERMISSION = 300
        private var lastTab = 0
    }
}
