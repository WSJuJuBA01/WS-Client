
package com.wsclient

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (XrayManager.running) {
            sendVpnCommand(VPNService.ACTION_STOP)
            updateTileState()
            return
        }

        // Android требует предварительного разрешения пользователя.
        if (VpnService.prepare(this) != null) {
            val launchIntent = Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            startActivityAndCollapseCompat(launchIntent)
            return
        }

        sendVpnCommand(VPNService.ACTION_START)
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.label = "WS Client"
        qsTile?.updateTile()
    }

    private fun sendVpnCommand(action: String) {
        val intent = Intent(
            this,
            VPNService::class.java
        ).apply {
            this.action = action
        }

        if (action == VPNService.ACTION_START) {
            ContextCompat.startForegroundService(
                this,
                intent
            )
        } else {
            startService(intent)
        }
    }

    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                200,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )

            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        val connected = XrayManager.running

        tile.state =
            if (connected) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }

        tile.label = "WS Client"
        tile.contentDescription =
            if (connected) {
                "VPN подключён. Нажмите для отключения"
            } else {
                "VPN отключён. Нажмите для подключения"
            }

        tile.updateTile()
    }
}
