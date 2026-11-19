package com.wsclient

import libv2ray.Libv2ray
import java.net.URL

object ServerLatency {

    const val DEFAULT_TEST_URL =
        "https://www.google.com/generate_204"

    /**
     * Измеряет задержку HTTP GET через outbound конкретного профиля.
     *
     * Возвращает задержку в миллисекундах.
     * При сетевой ошибке или ошибке конфигурации выбрасывает исключение.
     */
    fun check(
        profile: ServerProfile,
        testUrl: String = DEFAULT_TEST_URL
    ): Long {
        val parsedUrl = URL(testUrl)

        require(
            parsedUrl.protocol.equals("https", ignoreCase = true) ||
                parsedUrl.protocol.equals("http", ignoreCase = true)
        ) {
            "URL должен начинаться с http:// или https://"
        }

        val config = XrayConfigBuilder.buildForPing(profile)

        val delay = Libv2ray.measureOutboundDelay(
            config,
            testUrl
        )

        if (delay < 0L) {
            throw IllegalStateException(
                "Xray вернул отрицательное значение задержки"
            )
        }

        return delay
    }
}
