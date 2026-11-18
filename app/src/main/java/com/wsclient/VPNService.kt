package com.wsclient

import android.net.VpnService
import android.content.Intent
import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

class VPNService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_START -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        if (tun != null) return

        startForeground(NOTIFICATION_ID, notification())

        try {
            val builder = Builder()
                .setSession("WS Client")
                .setMtu(1500)
                .addAddress("10.0.0.2", 30)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)

            val prefs = getSharedPreferences("ws_settings", MODE_PRIVATE)
            val packages = prefs.getStringSet("split_packages", emptySet())
                ?: emptySet()
            val allowList = prefs.getBoolean("split_allowlist", false)

            if (packages.isNotEmpty()) {
                SplitTunneling.apply(
                    builder,
                    packageManager,
                    packages,
                    allowList
                )
            }

            val descriptor = builder.establish()
                ?: error("Android не выдал TUN-дескриптор")

            tun = descriptor

            val profile = ProfileStore.getAll(this)
                .firstOrNull { it.id == prefs.getString("active_profile", null) }
                ?: ProfileStore.getAll(this).firstOrNull()
                ?: error("Сначала импортируй сервер")

            val interfaceName = getPhysicalInterfaceName()
                ?: error("Не удалось определить сетевой интерфейс")

            val config = XrayConfigBuilder.build(profile, interfaceName)
            XrayManager.start(config, descriptor.fd)

        } catch (e: Exception) {
            Log.e("WS-VPN", "VPN start failed", e)
            stopTunnel()
        }
    }

    private fun getPhysicalInterfaceName(): String? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return null
        }

        return cm.getLinkProperties(network)?.interfaceName
    }

    private fun stopTunnel() {
        XrayManager.stop()

        try {
            tun?.close()
        } catch (_: Exception) {
        }

        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val channelId = "ws_vpn"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "WS Client VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("WS Client")
                .setContentText("VPN активен")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("WS Client")
                .setContentText("VPN активен")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build()
        }
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.wsclient.START"
        const val ACTION_STOP = "com.wsclient.STOP"
        const val NOTIFICATION_ID = 301
    }
}
