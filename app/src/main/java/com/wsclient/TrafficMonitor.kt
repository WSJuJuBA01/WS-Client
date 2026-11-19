package com.wsclient

import android.os.SystemClock
import java.util.Locale
import kotlin.math.max

data class TrafficSnapshot(
    val uploaded: Long,
    val downloaded: Long,
    val uploadBytesPerSecond: Long = 0L,
    val downloadBytesPerSecond: Long = 0L
)

object TrafficMonitor {
    private var previousUp = 0L
    private var previousDown = 0L
    private var previousTime = 0L
    private var initialized = false

    @Synchronized
    fun read(): TrafficSnapshot {
        var up = 0L
        var down = 0L

        val raw = XrayManager.trafficStats()

        raw.split(';', '\n').forEach { row ->
            val trimmed = row.trim()
            if (trimmed.isEmpty()) return@forEach

            val csv = trimmed.split(',')

            if (csv.size >= 3) {
                val value = csv.last().trim().toLongOrNull()

                when (csv[csv.size - 2].trim().lowercase()) {
                    "uplink" -> if (value != null) up += value
                    "downlink" -> if (value != null) down += value
                }
            }

            val match = Regex(
                "(?i)(uplink|downlink)\\s*[:=]\\s*(\\d+)"
            ).find(trimmed)

            if (match != null) {
                val value =
                    match.groupValues[2].toLongOrNull() ?: 0L

                if (match.groupValues[1].equals("uplink", true)) {
                    up += value
                } else {
                    down += value
                }
            }
        }

        val now = SystemClock.elapsedRealtime()

        val elapsed = if (initialized) {
            (now - previousTime).coerceAtLeast(1L)
        } else {
            0L
        }

        val upDelta =
            if (initialized && up >= previousUp) up - previousUp else 0L

        val downDelta =
            if (initialized && down >= previousDown) down - previousDown else 0L

        previousUp = up
        previousDown = down
        previousTime = now
        initialized = true

        return TrafficSnapshot(
            up,
            down,
            if (elapsed > 0) upDelta * 1000L / elapsed else 0L,
            if (elapsed > 0) downDelta * 1000L / elapsed else 0L
        )
    }

    @Synchronized
    fun reset() {
        previousUp = 0L
        previousDown = 0L
        previousTime = 0L
        initialized = false
    }

    fun format(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = max(0L, bytes).toDouble()
        var index = 0

        while (size >= 1024.0 && index < units.lastIndex) {
            size /= 1024.0
            index++
        }

        return String.format(
            Locale.ROOT,
            "%.2f %s",
            size,
            units[index]
        )
    }

    fun formatDuration(milliseconds: Long): String {
        val total = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60

        return if (hours > 0) {
            String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
        } else {
            String.format(
                Locale.ROOT,
                "%02d:%02d",
                minutes,
                seconds
            )
        }
    }
}
