package com.wsclient

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SplitTunnelingActivity : Activity() {
    private val selected = linkedSetOf<String>()
    private lateinit var allowList: CheckBox
    private lateinit var list: LinearLayout

    private val palette
        get() = Customization.palette(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            "ws_settings",
            MODE_PRIVATE
        )

        selected.addAll(
            prefs.getStringSet(
                "split_packages",
                emptySet()
            ) ?: emptySet()
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(18),
                dp(20),
                dp(18),
                dp(24)
            )
            setBackgroundColor(palette.background)
        }

        Customization.applyWallpaper(this, root)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = "Раздельное туннелирование"
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(palette.foreground)
        })

        root.addView(TextView(this).apply {
            text =
                "Выбери приложения, которые должны идти через VPN " +
                "или обходить его. Изменения применяются при следующем подключении."
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(7), 0, dp(15))
        })

        allowList = CheckBox(this).apply {
            text = "Только выбранные приложения через VPN"
            setTextColor(palette.foreground)
            isChecked = prefs.getBoolean(
                "split_allowlist",
                false
            )
        }

        root.addView(allowList)

        root.addView(TextView(this).apply {
            text =
                "Режим выключен: выбранные приложения обходят VPN.\n" +
                "Режим включён: через VPN идут только выбранные приложения."
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(0, 0, 0, dp(12))
        })

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(list)
        loadApps()

        root.addView(TextView(this).apply {
            text = "СОХРАНИТЬ"
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(palette.foreground)

            background =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(palette.surface)
                }

            setPadding(0, dp(16), 0, dp(16))

            setOnClickListener {
                if (allowList.isChecked && selected.isEmpty()) {
                    Toast.makeText(
                        this@SplitTunnelingActivity,
                        "В режиме списка разрешённых выбери хотя бы одно приложение",
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
                    "Настройки сохранены. Переподключи VPN для применения.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        })

        setContentView(scroll)
    }

    private fun loadApps() {
        val pm = packageManager

        val apps = pm.getInstalledApplications(0)
            .filter {
                it.packageName != packageName &&
                    pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .sortedBy {
                pm.getApplicationLabel(it)
                    .toString()
                    .lowercase()
            }

        apps.forEach { app ->
            list.addView(CheckBox(this).apply {
                text =
                    "${pm.getApplicationLabel(app)}\n${app.packageName}"
                textSize = 13f
                setTextColor(palette.foreground)
                isChecked = app.packageName in selected
                setPadding(0, dp(6), 0, dp(6))

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selected.add(app.packageName)
                    } else {
                        selected.remove(app.packageName)
                    }
                }
            })
        }
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()
}
