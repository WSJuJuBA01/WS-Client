package com.wsclient

import android.util.Log
import android.os.SystemClock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object XrayManager {
    private const val TAG = "WS-Xray"
    private const val START_TIMEOUT_SECONDS = 12L

    @Volatile private var controller: CoreController? = null
    @Volatile var running: Boolean = false
        private set
    @Volatile var startedAtElapsedRealtime: Long = 0L
        private set

    @Synchronized
    fun start(config: String, tunFd: Int) {
        if (running || controller != null) return
        require(tunFd >= 0) { "Некорректный TUN-дескриптор" }

        val started = CountDownLatch(1)
        val callback = object : CoreCallbackHandler {
            override fun startup(): Long {
                running = true
                startedAtElapsedRealtime = SystemClock.elapsedRealtime()
                TrafficMonitor.reset()
                Log.i(TAG, "Xray startup callback")
                started.countDown()
                return 0L
            }

            override fun shutdown(): Long {
                running = false
                startedAtElapsedRealtime = 0L
                Log.i(TAG, "Xray shutdown callback")
                return 0L
            }

            override fun onEmitStatus(status: Long, message: String): Long {
                Log.i(TAG, "$status: $message")
                return 0L
            }
        }

        val core = Libv2ray.newCoreController(callback)
        controller = core

        try {
            core.startLoop(config, tunFd)

            if (!started.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                runCatching { core.stopLoop() }
                throw IllegalStateException(
                    "Xray не подтвердил запуск за ${START_TIMEOUT_SECONDS} секунд"
                )
            }

            check(running) {
                "Xray остановился сразу после запуска"
            }
        } catch (e: Exception) {
            running = false
            startedAtElapsedRealtime = 0L
            controller = null
            runCatching { core.stopLoop() }

            throw IllegalStateException(
                "Не удалось запустить Xray: ${e.message}",
                e
            )
        }
    }

    @Synchronized
    fun stop() {
        val core = controller
        controller = null

        try {
            core?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Stop error", e)
        } finally {
            running = false
            startedAtElapsedRealtime = 0L
            TrafficMonitor.reset()
        }
    }

    fun trafficStats(): String = try {
        if (!running) "" else
            controller?.queryAllOutboundTrafficStats().orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Traffic stats unavailable", e)
        ""
    }
}
