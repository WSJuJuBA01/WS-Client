
package com.wsclient

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object XrayJsonSubscriptionParser {

    private val supportedProtocols = setOf(
        "vless",
        "vmess",
        "trojan",
        "shadowsocks"
    )

    private val serverArrayNames = mapOf(
        "vless" to "vnext",
        "vmess" to "vnext",
        "trojan" to "servers",
        "shadowsocks" to "servers"
    )

    fun parse(source: String): List<ServerProfile> {
        val text = source
            .trim()
            .removePrefix("\uFEFF")
            .trim()

        require(text.isNotBlank()) {
            "JSON подписки пустой"
        }

        val configs = when {
            text.startsWith("[") -> {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull {
                    array.optJSONObject(it)
                }
            }

            text.startsWith("{") -> {
                listOf(JSONObject(text))
            }

            else -> error(
                "Ожидался JSON-объект или JSON-массив"
            )
        }

        require(configs.isNotEmpty()) {
            "В JSON не найдено ни одного конфигурационного объекта"
        }

        val result = mutableListOf<ServerProfile>()
        val errors = mutableListOf<String>()

        configs.forEachIndexed { configIndex, root ->
            val outbounds = root.optJSONArray("outbounds")

            if (outbounds == null) {
                // Допускаем JSON, в котором передан непосредственно outbound.
                parseOutbound(
                    root = root,
                    outbound = root,
                    configIndex = configIndex,
                    outboundIndex = 0,
                    errors = errors,
                    destination = result
                )
                return@forEachIndexed
            }

            for (outboundIndex in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(outboundIndex)
                    ?: continue

                parseOutbound(
                    root = root,
                    outbound = outbound,
                    configIndex = configIndex,
                    outboundIndex = outboundIndex,
                    errors = errors,
                    destination = result
                )
            }
        }

        val unique = result.distinctBy {
            listOf(
                it.protocol.lowercase(),
                it.address.lowercase(),
                it.port,
                it.config.toString()
            ).joinToString("|")
        }

        if (unique.isEmpty()) {
            val details = errors
                .take(5)
                .joinToString("\n")

            error(
                buildString {
                    append(
                        "В JSON не найдено поддерживаемых серверов."
                    )

                    if (details.isNotBlank()) {
                        append("\n")
                        append(details)
                    }
                }
            )
        }

        return unique
    }

    private fun parseOutbound(
        root: JSONObject,
        outbound: JSONObject,
        configIndex: Int,
        outboundIndex: Int,
        errors: MutableList<String>,
        destination: MutableList<ServerProfile>
    ) {
        val protocol = outbound
            .optString("protocol")
            .lowercase()
            .trim()

        if (protocol !in supportedProtocols) {
            return
        }

        val settings = outbound.optJSONObject("settings")

        if (settings == null) {
            errors += "Объект ${configIndex + 1}: $protocol без settings"
            return
        }

        val arrayName = serverArrayNames[protocol] ?: return
        val servers = settings.optJSONArray(arrayName)

        if (servers == null || servers.length() == 0) {
            errors +=
                "Объект ${configIndex + 1}: $protocol без $arrayName"
            return
        }

        for (serverIndex in 0 until servers.length()) {
            val server = servers.optJSONObject(serverIndex)
                ?: continue

            val address = server
                .optString("address")
                .trim()

            val port = server.optInt("port", -1)

            if (address.isBlank() || port !in 1..65535) {
                errors +=
                    "Объект ${configIndex + 1}, outbound " +
                    "${outboundIndex + 1}: неверный адрес или порт"
                continue
            }

            val singleServerSettings = JSONObject(
                settings.toString()
            ).apply {
                put(
                    arrayName,
                    JSONArray().put(
                        JSONObject(server.toString())
                    )
                )
            }

            val singleOutbound = JSONObject(
                outbound.toString()
            ).apply {
                put("settings", singleServerSettings)
            }

            val rootName = root
                .optString("remarks")
                .trim()

            val outboundName = outbound
                .optString("remarks")
                .trim()

            val tag = outbound
                .optString("tag")
                .trim()

            val name = buildName(
                rootName = rootName,
                outboundName = outboundName,
                tag = tag,
                protocol = protocol,
                address = address,
                serverIndex = serverIndex
            )

            destination += ServerProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = protocol.uppercase(),
                address = address,
                port = port,
                rawLink = singleOutbound.toString(),
                config = singleOutbound
            )
        }
    }

    private fun buildName(
        rootName: String,
        outboundName: String,
        tag: String,
        protocol: String,
        address: String,
        serverIndex: Int
    ): String {
        val base = when {
            outboundName.isNotBlank() -> outboundName
            tag.isNotBlank() -> tag
            rootName.isNotBlank() -> rootName
            else -> "$protocol · $address"
        }

        return if (serverIndex > 0) {
            "$base · ${serverIndex + 1}"
        } else {
            base
        }
    }
}
