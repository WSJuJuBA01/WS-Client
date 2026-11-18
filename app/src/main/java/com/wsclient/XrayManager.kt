package com.wsclient

import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

object XrayManager {
    private const val TAG = "WS-Xray"

    private var controller: CoreController? = null

    @Volatile
    var running = false
        private set

    fun start(config: String, tunFd: Int) {
        if (running) return

        val callback = object : CoreCallbackHandler {
            override fun startup(): Int {
                running = true
                Log.i(TAG, "Xray started")
                return 0
            }

            override fun shutdown(): Int {
                running = false
                Log.i(TAG, "Xray stopped")
                return 0
            }

            override fun onEmitStatus(status: Int, message: String): Int {
                Log.i(TAG, "$status: $message")
                return 0
            }
        }

        val core = Libv2ray.newCoreController(callback)
        controller = core

        try {
            core.startLoop(config, tunFd.toLong().toInt())
            running = true
        } catch (e: Exception) {
            running = false
            controller = null
            throw IllegalStateException("Не удалось запустить Xray: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            controller?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        } finally {
            controller = null
            running = false
        }
    }

    fun trafficStats(): String {
        return try {
            controller?.queryAllOutboundTrafficStats().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
