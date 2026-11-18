package com.wsclient

data class TrafficSnapshot(
    val uploaded: Long,
    val downloaded: Long
)

object TrafficMonitor {
    fun read(): TrafficSnapshot {
        var up = 0L
        var down = 0L

        XrayManager.trafficStats()
            .split(";")
            .forEach { row ->
                val parts = row.split(",")
                if (parts.size >= 3) {
                    val value = parts[2].toLongOrNull() ?: 0L
                    when (parts[1]) {
                        "uplink" -> up += value
                        "downlink" -> down += value
                    }
                }
            }

        return TrafficSnapshot(up, down)
    }

    fun format(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var index = 0

        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }

        return "%.2f %s".format(size, units[index])
    }
}
