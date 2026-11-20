
package com.wsclient

import android.app.Activity
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

    private var allApps: List<AppEntry> = emptyList()
    private var filteredApps: List<AppEntry> = emptyList()

    private var loadGeneration = 0
    private var searchTask: Runnable? = null

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
            singleLine = true
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
                if (allowList.isChecked && selected.isEmpty()) {
                    Toast.makeText(
                        this@SplitTunnelingActivity,
                        "Выбери хотя бы одно приложение",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
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
                    this@SplitTunnelingActivity,
                    "Сохранено. Переподключи VPN.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        }

        root.addView(
            save,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )

        setContentView(root)

        Ui.prepareWindow(this, palette)
        Ui.padWithInsets(root)
    }

    private fun loadApps() {
        val generation = ++loadGeneration

        countText.text = "Загрузка приложений…"

        worker.execute {
            val result = runCatching {
                val pm = packageManager

                pm.getInstalledApplications(0)
                    .asSequence()
                    .filter {
                        it.packageName != packageName
                    }
                    .mapNotNull { app ->
                        runCatching {
                            if (
                                pm.getLaunchIntentForPackage(
                                    app.packageName
                                ) == null
                            ) {
                                return@mapNotNull null
                            }

                            AppEntry(
                                packageName = app.packageName,
                                label = pm.getApplicationLabel(app)
                                    .toString()
                            )
                        }.getOrNull()
                    }
                    .sortedBy {
                        it.label.lowercase(Locale.ROOT)
                    }
                    .toList()
            }

            handler.post {
                if (isFinishing || isDestroyed) return@post
                if (generation != loadGeneration) return@post

                result.onSuccess { apps ->
                    allApps = apps
                    filteredApps = apps
                    adapter.notifyDataSetChanged()

                    updateCount()

                    if (apps.isEmpty()) {
                        countText.text = "Приложения не найдены"
                    }
                }.onFailure {
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

    private fun scheduleSearch(query: String) {
        searchTask?.let(handler::removeCallbacks)

        val task = Runnable {
            val normalized = query.trim().lowercase(Locale.ROOT)

            worker.execute {
                val result = if (normalized.isEmpty()) {
                    allApps
                } else {
                    allApps.filter {
                        it.label.lowercase(Locale.ROOT)
                            .contains(normalized) ||
                            it.packageName.lowercase(Locale.ROOT)
                                .contains(normalized)
                    }
                }

                handler.post {
                    if (isFinishing || isDestroyed) return@post

                    filteredApps = result
                    adapter.notifyDataSetChanged()
                    updateCount()
                }
            }
        }

        searchTask = task
        handler.postDelayed(task, 250L)
    }

    private fun updateCount() {
        countText.text =
            "${filteredApps.size} приложений · выбрано ${selected.size}"
    }

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

            // Сбрасываем слушатель до обновления состояния:
            // иначе переиспользованная строка может изменить другой пакет.
            checkbox.setOnCheckedChangeListener(null)

            checkbox.text = "${app.label}\n${app.packageName}"
            checkbox.isChecked = app.packageName in selected

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

    override fun onDestroy() {
        loadGeneration++
        searchTask?.let(handler::removeCallbacks)
        worker.shutdownNow()
        super.onDestroy()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
