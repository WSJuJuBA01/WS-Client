
package com.wsclient

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

data class SubscriptionUpdateResult(
    val imported: Int,
    val profiles: List<ServerProfile>
)

object SubscriptionManager {

    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 30_000
    private const val MAX_RESPONSE_SIZE = 10 * 1024 * 1024

    suspend fun update(
        context: Context,
        url: String
    ): Int = updateDetailed(context, url).imported

    suspend fun updateDetailed(
        context: Context,
        url: String
    ): SubscriptionUpdateResult =
        withContext(Dispatchers.IO) {

            require(
                url.startsWith("https://", true) ||
                    url.startsWith("http://", true)
            ) {
                "Некорректный URL подписки"
            }

            val connection = URL(url)
                .openConnection() as? HttpURLConnection
                ?: error("Не удалось создать HTTP-подключение")

            try {
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    instanceFollowRedirects = true
                    useCaches = false

                    setRequestProperty(
                        "Accept",
                        "application/json, */*"
                    )

                    setRequestProperty(
                        "Accept-Encoding",
                        "gzip"
                    )

                    SubscriptionIdentity.applyHeaders(
                        context,
                        this
                    )
                }

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    val errorBody = runCatching {
                        connection.errorStream?.use {
                            readLimited(it)
                        }.orEmpty()
                    }.getOrDefault("")

                    error(
                        buildString {
                            append("Ошибка загрузки подписки: HTTP ")
                            append(responseCode)

                            if (errorBody.isNotBlank()) {
                                append("\n")
                                append(errorBody.take(500))
                            }
                        }
                    )
                }

                val input = connection.inputStream
                    ?: error("Сервер вернул пустой ответ")

                val decodedStream: InputStream =
                    if (
                        connection.contentEncoding
                            ?.contains("gzip", true) == true
                    ) {
                        GZIPInputStream(input)
                    } else {
                        input
                    }

                val body = decodedStream.use {
                    readLimited(it)
                }.trimStart('\uFEFF').trim()

                if (body.isBlank()) {
                    error("Сервер вернул пустой JSON")
                }

                val contentType =
                    connection.contentType.orEmpty()

                if (
                    !body.startsWith("{") &&
                    !body.startsWith("[")
                ) {
                    error(
                        "Ответ сервера не является JSON.\n" +
                            "Content-Type: $contentType\n" +
                            "Начало ответа: ${body.take(180)}"
                    )
                }

                val parsedProfiles =
                    XrayJsonSubscriptionParser.parse(body)

                if (parsedProfiles.isEmpty()) {
                    error("В подписке нет поддерживаемых серверов")
                }

                // Сохраняем профили через существующее хранилище.
                ProfileStore.addAll(
                    context,
                    parsedProfiles
                )

                // Получаем уже сохранённые версии с постоянными ID.
                val storedProfiles = ProfileStore.getAll(context)

                val actualProfiles = parsedProfiles.mapNotNull { incoming ->
                    storedProfiles.firstOrNull {
                        ProfileStore.sameConnection(it, incoming)
                    }
                }.distinctBy { it.id }

                if (actualProfiles.isEmpty()) {
                    error("Профили разобраны, но не удалось сохранить их")
                }

                // Группа подписки связывается с импортированными профилями.
                SubscriptionStore.upsert(
                    context,
                    url,
                    actualProfiles
                )

                SubscriptionUpdateResult(
                    imported = actualProfiles.size,
                    profiles = actualProfiles
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0

        while (true) {
            val count = input.read(buffer)

            if (count < 0) break

            total += count

            require(total <= MAX_RESPONSE_SIZE) {
                "Ответ подписки превышает 10 МБ"
            }

            output.write(buffer, 0, count)
        }

        return output.toString(
            StandardCharsets.UTF_8.name()
        )
    }
}
