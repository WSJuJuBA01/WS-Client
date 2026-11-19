package com.wsclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
            promoteToForeground()

            val prefs = getSharedPreferences(
                "ws_settings",
                Context.MODE_PRIVATE
            )

            val profiles = ProfileStore.getAll(this)
            val activeId = prefs.getString("active_profile", null)

            val profile = profiles.firstOrNull { it.id == activeId }
                ?: profiles.firstOrNull()
                ?: error("Нет серверов. Сначала импортируй профиль.")

            val interfaceName = getPhysicalInterfaceName()
                ?: error(
                    "Не удалось определить сетевой интерфейс. " +
                        "Проверь подключение к Wi-Fi или мобильной сети."
                )

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

            val config = XrayConfigBuilder.build(
                profile,
                interfaceName
            )

            XrayManager.start(config, descriptor.fd)

            starting = false
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

    private fun promoteToForeground() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getPhysicalInterfaceName(): String? {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return null
        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return null

        if (!capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
        ) {
            return null
        }

        if (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        ) {
            Log.w(TAG, "Active network is already a VPN")
        }

        return connectivityManager
            .getLinkProperties(network)
            ?.interfaceName
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

    private fun createNotification(): Notification {
        val channelId = "ws_vpn"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "WS Client VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Состояние VPN-подключения"
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val builder = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("WS Client")
            .setContentText("VPN-подключение активно")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
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
    }
}
