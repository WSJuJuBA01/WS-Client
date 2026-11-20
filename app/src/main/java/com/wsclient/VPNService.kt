
package com.wsclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class VPNService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var starting = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> startTunnel()
            ACTION_STOP -> stopTunnel()
            else -> Log.w(TAG, "Unknown service action")
        }

        return START_NOT_STICKY
    }

    private fun startTunnel() {
        if (tun != null || starting) {
            Log.i(TAG, "VPN is already running or starting")
            return
        }

        starting = true
        sendState(STATE_CONNECTING)

        try {
            promoteToForeground("Подключение к серверу…")

            val prefs = getSharedPreferences(
                "ws_settings",
                Context.MODE_PRIVATE
            )

            val profiles = ProfileStore.getAll(this)
            val activeId = prefs.getString("active_profile", null)

            val profile = profiles.firstOrNull {
                it.id == activeId
            } ?: profiles.firstOrNull()
                ?: error("Нет серверов. Сначала импортируй профиль.")

            val builder = Builder()
                .setSession("WS Client")
                .setMtu(1500)
                .addAddress("10.0.0.2", 30)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)

            val packages = prefs.getStringSet(
                "split_packages",
                emptySet()
            ) ?: emptySet()

            val allowList = prefs.getBoolean(
                "split_allowlist",
                false
            )

            if (allowList && packages.isEmpty()) {
                error(
                    "Список разрешённых приложений пуст. " +
                        "Выбери хотя бы одно приложение."
                )
            }

            // Xray должен ходить напрямую, чтобы не создать VPN-петлю.
            if (!allowList) {
                builder.addDisallowedApplication(packageName)
            }

            if (packages.isNotEmpty()) {
                SplitTunneling.apply(
                    builder,
                    packageManager,
                    packages,
                    allowList
                )
            }

            val descriptor = builder.establish()
                ?: error("Android не выдал TUN-дескриптор.")

            tun = descriptor

            XrayManager.start(
                XrayConfigBuilder.build(profile),
                descriptor.fd
            )

            check(XrayManager.running) {
                "Xray не перешёл в рабочее состояние"
            }

            starting = false

            updateForegroundNotification(
                "Подключено · ${profile.name}"
            )

            sendState(
                STATE_CONNECTED,
                "Подключено: ${profile.name}"
            )

            Log.i(TAG, "VPN connected: ${profile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed", e)

            starting = false

            val reason = e.message
                ?.takeIf { it.isNotBlank() }
                ?: e.javaClass.simpleName

            sendState(
                STATE_ERROR,
                "Не удалось подключиться: $reason"
            )

            cleanupTunnel()
        }
    }

    private fun promoteToForeground(text: String) {
        createNotificationChannel()

        val notification = createNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun updateForegroundNotification(text: String) {
        createNotificationChannel()

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "WS Client VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Состояние VPN-подключения"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val openIntent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(
            this,
            VPNService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_ws_notification)
            .setContentTitle("WS Client")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Отключить VPN",
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    private fun stopTunnel() {
        starting = false
        cleanupTunnel()
        sendState(STATE_DISCONNECTED)
    }

    private fun cleanupTunnel() {
        try {
            XrayManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Xray stop failed", e)
        }

        try {
            tun?.close()
        } catch (e: Exception) {
            Log.e(TAG, "TUN close failed", e)
        }

        tun = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground cleanup failed", e)
        }

        stopSelf()
    }

    private fun sendState(
        state: String,
        message: String? = null
    ) {
        val intent = Intent(ACTION_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)

            if (message != null) {
                putExtra(EXTRA_MESSAGE, message)
            }
        }

        sendBroadcast(intent)
    }

    override fun onDestroy() {
        starting = false
        cleanupTunnel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WS-VPN"

        const val ACTION_START = "com.wsclient.START"
        const val ACTION_STOP = "com.wsclient.STOP"
        const val ACTION_STATE = "com.wsclient.STATE"

        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"

        const val STATE_CONNECTING = "connecting"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_ERROR = "error"

        const val NOTIFICATION_ID = 301
        private const val CHANNEL_ID = "ws_vpn"
    }
}
