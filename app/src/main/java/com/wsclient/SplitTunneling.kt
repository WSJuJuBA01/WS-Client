
package com.wsclient

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale
import java.util.concurrent.Executors

class SplitTunnelingActivity : Activity() {

    private data class AppEntry(
        val packageName: String,
        val label: String
    )

    private val selected = linkedSetOf<String>()

    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var allowList: CheckBox
    private lateinit var list: ListView
    private lateinit var search: EditText
    private lateinit var countText: TextView
    private lateinit var adapter: AppsAdapter

    private val allApps = mutableListOf<AppEntry>()
    private val filteredApps = mutableListOf<AppEntry>()

    private var pendingApps: List<ResolveInfo> = emptyList()
    private var nextBatch = 0

    private var loadGeneration = 0
    private var searchGeneration = 0
    private var searchTask: Runnable? = null
    private var currentQuery = ""

    private var loadingFinished = false

    private val batchSize = 20
    private val batchDelay = 70L

    private val palette
        get() = Customization.palette(this)

    private val prefs
        get() = getSharedPreferences("ws_settings", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selected.addAll(
            prefs.getStringSet(
                "split_packages",
                emptySet()
            ) ?: emptySet()
        )

        buildScreen()
        loadApps()
    }

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(18),
                dp(20),
                dp(18),
                dp(16)
            )
            setBackgroundColor(palette.background)
        }

        Customization.applyWallpaper(this, root)

        root.addView(
            TextView(this).apply {
                text = "Раздельное туннелирование"
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.foreground)
            }
        )

        root.addView(
            TextView(this).apply {
                text = "Выбери приложения, которые должны идти через VPN или обходить его."
                textSize = 12f
                setTextColor(palette.muted)
                setPadding(0, dp(7), 0, dp(14))
            }
        )

        allowList = CheckBox(this).apply {
            text = "Только выбранные приложения через VPN"
            textSize = 13f
            setTextColor(palette.foreground)
            isChecked = prefs.getBoolean(
                "split_allowlist",
                false
            )
        }

        root.addView(allowList)

        root.addView(
            TextView(this).apply {
                text =
                    "Выключено: выбранные приложения обходят VPN.\n" +
                    "Включено: через VPN идут только выбранные приложения."
                textSize = 11f
                setTextColor(palette.muted)
                setPadding(0, dp(3), 0, dp(12))
            }
        )

        search = EditText(this).apply {
            hint = "Поиск приложения"
            textSize = 14f
            setSingleLine(true)
            maxLines = 1
            setTextColor(palette.foreground)
            setHintTextColor(palette.muted)
            background = rounded(palette.surface, 13)
            setPadding(dp(13), dp(11), dp(13), dp(11))
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            }
        )

        countText = TextView(this).apply {
            text = "Подготовка списка…"
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(dp(2), dp(3), 0, dp(7))
        }

        root.addView(countText)

        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            cacheColorHint = android.graphics.Color.TRANSPARENT
            background = null
            itemsCanFocus = false
            isVerticalScrollBarEnabled = true
        }

        adapter = AppsAdapter()
        list.adapter = adapter

        root.addView(
            list,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        val save = TextView(this).apply {
            text = "СОХРАНИТЬ"
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            background = rounded(
                Customization.accent(this@SplitTunnelingActivity),
                14
            )
            setPadding(0, dp(16), 0, dp(16))

            setOnClickListener {
                saveSettings()
            }
        }

        root.addView(
            save,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )

        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    scheduleSearch(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        setContentView(root)

        Ui.prepareWindow(this, palette)
        Ui.padWithInsets(root)
    }

    // ---------------------------------------------------------
    // Progressive app loading
    // ---------------------------------------------------------

    private fun loadApps() {
        val generation = ++loadGeneration

        loadingFinished = false
        nextBatch = 0
        allApps.clear()
        filteredApps.clear()
        pendingApps = emptyList()

        adapter.notifyDataSetChanged()
        countText.text = "Поиск приложений…"

        worker.execute {
            val result = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                packageManager
                    .queryIntentActivities(intent, 0)
                    .distinctBy {
                        it.activityInfo?.packageName.orEmpty()
                    }
                    .filter {
                        it.activityInfo?.packageName != packageName
                    }
            }

            handler.post {
                if (isFinishing || isDestroyed) return@post
                if (generation != loadGeneration) return@post

                result.onSuccess { apps ->
                    pendingApps = apps
                    countText.text =
                        "Найдено приложений: ${apps.size}. Загружаю…"

                    if (apps.isEmpty()) {
                        loadingFinished = true
                        updateCount()
                    } else {
                        loadNextBatch(generation)
                    }
                }.onFailure {
                    loadingFinished = true
                    countText.text = "Не удалось загрузить приложения"

                    Toast.makeText(
                        this,
                        "Ошибка загрузки списка приложений",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadNextBatch(generation: Int) {
        if (isFinishing || isDestroyed) return
        if (generation != loadGeneration) return

        if (nextBatch >= pendingApps.size) {
            loadingFinished = true
            updateCount()
            return
        }

        val start = nextBatch
        val end = minOf(
            start + batchSize,
            pendingApps.size
        )

        val batch = pendingApps.subList(start, end).toList()

        worker.execute {
            val entries = batch.mapNotNull { info ->
                runCatching {
                    val appInfo = info.activityInfo?.applicationInfo
                        ?: return@mapNotNull null

                    val packageName = appInfo.packageName

                    if (packageName.isBlank()) {
                        return@mapNotNull null
                    }

                    val label = packageManager
                        .getApplicationLabel(appInfo)
                        .toString()
                        .ifBlank { packageName }

                    AppEntry(
                        packageName = packageName,
                        label = label
                    )
                }.getOrNull()
            }

            handler.post {
                if (isFinishing || isDestroyed) return@post
                if (generation != loadGeneration) return@post

                allApps.addAll(entries)

                allApps.sortBy {
                    it.label.lowercase(Locale.ROOT)
                }

                nextBatch = end

                applyCurrentFilter()

                countText.text =
                    "Загружено ${allApps.size} из ${pendingApps.size}"

                if (nextBatch >= pendingApps.size) {
                    loadingFinished = true
                    updateCount()
                } else {
                    handler.postDelayed(
                        {
                            loadNextBatch(generation)
                        },
                        batchDelay
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Search
    // ---------------------------------------------------------

    private fun scheduleSearch(query: String) {
        currentQuery = query
        searchGeneration++

        val generation = searchGeneration

        searchTask?.let(handler::removeCallbacks)

        val task = Runnable {
            if (isFinishing || isDestroyed) return@Runnable

            val normalized = query
                .trim()
                .lowercase(Locale.ROOT)

            worker.execute {
                val snapshot = synchronized(allApps) {
                    allApps.toList()
                }

                val result = if (normalized.isEmpty()) {
                    snapshot
                } else {
                    snapshot.filter {
                        it.label.lowercase(Locale.ROOT)
                            .contains(normalized) ||
                            it.packageName.lowercase(Locale.ROOT)
                                .contains(normalized)
                    }
                }

                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    if (generation != searchGeneration) return@post

                    filteredApps.clear()
                    filteredApps.addAll(result)

                    adapter.notifyDataSetChanged()
                    updateCount()
                }
            }
        }

        searchTask = task
        handler.postDelayed(task, 250L)
    }

    private fun applyCurrentFilter() {
        val normalized = currentQuery
            .trim()
            .lowercase(Locale.ROOT)

        filteredApps.clear()

        if (normalized.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            filteredApps.addAll(
                allApps.filter {
                    it.label.lowercase(Locale.ROOT)
                        .contains(normalized) ||
                        it.packageName.lowercase(Locale.ROOT)
                            .contains(normalized)
                }
            )
        }

        adapter.notifyDataSetChanged()
    }

    private fun updateCount() {
        val progress = if (loadingFinished) {
            "Загрузка завершена"
        } else {
            "Загружено ${allApps.size} из ${pendingApps.size}"
        }

        countText.text =
            "$progress · выбрано ${selected.size}"
    }

    // ---------------------------------------------------------
    // Adapter: ListView reuses visible rows
    // ---------------------------------------------------------

    private inner class AppsAdapter : BaseAdapter() {

        override fun getCount(): Int = filteredApps.size

        override fun getItem(position: Int): AppEntry =
            filteredApps[position]

        override fun getItemId(position: Int): Long =
            getItem(position).packageName.hashCode().toLong()

        override fun hasStableIds(): Boolean = false

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val checkbox = (convertView as? CheckBox)
                ?: CheckBox(this@SplitTunnelingActivity).apply {
                    textSize = 12f
                    setTextColor(palette.foreground)
                    setPadding(
                        dp(3),
                        dp(7),
                        dp(3),
                        dp(7)
                    )

                    buttonTintList =
                        android.content.res.ColorStateList.valueOf(
                            Customization.accent(
                                this@SplitTunnelingActivity
                            )
                        )
                }

            val app = getItem(position)

            checkbox.setOnCheckedChangeListener(null)

            checkbox.text =
                "${app.label}\n${app.packageName}"

            checkbox.isChecked =
                app.packageName in selected

            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selected.add(app.packageName)
                } else {
                    selected.remove(app.packageName)
                }

                updateCount()
            }

            return checkbox
        }
    }

    // ---------------------------------------------------------
    // Save
    // ---------------------------------------------------------

    private fun saveSettings() {
        if (allowList.isChecked && selected.isEmpty()) {
            Toast.makeText(
                this,
                "Выбери хотя бы одно приложение",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        prefs.edit()
            .putStringSet(
                "split_packages",
                selected.toSet()
            )
            .putBoolean(
                "split_allowlist",
                allowList.isChecked
            )
            .apply()

        Toast.makeText(
            this,
            "Сохранено. Переподключи VPN.",
            Toast.LENGTH_LONG
        ).show()

        finish()
    }

    // ---------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------

    override fun onDestroy() {
        loadGeneration++
        searchGeneration++

        searchTask?.let(handler::removeCallbacks)

        worker.shutdownNow()

        super.onDestroy()
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
