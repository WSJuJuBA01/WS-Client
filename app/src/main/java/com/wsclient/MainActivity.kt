package com.wsclient

import android.app.Activity
import android.net.VpnService
import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.view.Gravity
import android.graphics.Color
import android.text.InputType

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var serverList: LinearLayout
    private var selectedId: String? = null
    private var titleClicks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(15, 17, 24))
        }

        val title = TextView(this).apply {
            text = "WS Client"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
            setOnClickListener {
                titleClicks++
                if (titleClicks >= 5) {
                    titleClicks = 0
                    Customization.togglePastel(this@MainActivity)
                    recreate()
                }
            }
        }

        status = TextView(this).apply {
            text = "Статус: отключено"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
        }

        val connect = button("ПОДКЛЮЧИТЬ") {
            connectVPN()
        }

        val disconnect = button("ОТКЛЮЧИТЬ") {
            startService(Intent(this, VPNService::class.java).apply {
                action = VPNService.ACTION_STOP
            })
            status.text = "Статус: отключено"
        }

        val import = button("ИМПОРТ ССЫЛКИ") {
            importProfile()
        }

        val subscribe = button("ДОБАВИТЬ ПОДПИСКУ") {
            addSubscription()
        }

        serverList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(title)
        root.addView(status)
        root.addView(connect)
        root.addView(disconnect)
        root.addView(import)
        root.addView(subscribe)
        root.addView(TextView(this).apply {
            text = "СЕРВЕРЫ"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 24, 0, 12)
        })
        root.addView(serverList)

        setContentView(ScrollView(this).apply {
            addView(root)
        })

        refreshProfiles()
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
    }

    private fun connectVPN() {
        val profiles = ProfileStore.getAll(this)
        if (profiles.isEmpty()) {
            status.text = "Сначала импортируй сервер"
            return
        }

        val id = selectedId ?: profiles.first().id
        getSharedPreferences("ws_settings", MODE_PRIVATE)
            .edit()
            .putString("active_profile", id)
            .apply()

        val permission = VpnService.prepare(this)
        if (permission != null) {
            startActivityForResult(permission, REQUEST_VPN)
        } else {
            startVPNService()
        }
    }

    private fun startVPNService() {
        startForegroundService(
            Intent(this, VPNService::class.java).apply {
                action = VPNService.ACTION_START
            }
        )
        status.text = "Статус: запуск..."
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            startVPNService()
        }
    }

    private fun importProfile() {
        val input = EditText(this).apply {
            hint = "vless://, vmess://, trojan://, ss://"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        AlertDialogBuilder.show(this, "Импорт профиля", input) {
            try {
                ProfileStore.add(this, ProfileCodec.parse(input.text.toString()))
                refreshProfiles()
                Toast.makeText(this, "Профиль добавлен", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun addSubscription() {
        val input = EditText(this).apply {
            hint = "https://example.com/subscription"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }

        AlertDialogBuilder.show(this, "Подписка", input) {
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) {
                Thread {
                    try {
                        val count = kotlinx.coroutines.runBlocking {
                            SubscriptionManager.update(this@MainActivity, url)
                        }
                        runOnUiThread {
                            refreshProfiles()
                            Toast.makeText(
                                this,
                                "Импортировано: $count",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Ошибка подписки: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
        }
    }

    private fun refreshProfiles() {
        serverList.removeAllViews()

        ProfileStore.getAll(this).forEach { profile ->
            val item = TextView(this).apply {
                text = "${profile.name}\n${profile.protocol} • ${profile.address}:${profile.port}"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(12, 16, 12, 16)
                setOnClickListener {
                    selectedId = profile.id
                    getSharedPreferences("ws_settings", MODE_PRIVATE)
                        .edit()
                        .putString("active_profile", profile.id)
                        .apply()
                    Toast.makeText(
                        this@MainActivity,
                        "Выбран ${profile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                setOnLongClickListener {
                    ProfileStore.remove(this@MainActivity, profile.id)
                    refreshProfiles()
                    true
                }
            }

            serverList.addView(item)
        }
    }

    companion object {
        private const val REQUEST_VPN = 100
    }
}
