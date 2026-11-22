package com.wsclient

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Parses both ordinary Xray outbound JSON and complete Xray client configs.
 *
 * A complete config (inbounds/routing/policy/balancers/observatory) is one
 * logical profile. Its whole JSON document must be preserved because routing,
 * balancers, mux, TLS, WS settings and all proxy outbounds work together.
 * Splitting such a document into individual outbounds destroys that behavior.
 */
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

            text.startsWith("{") -> listOf(JSONObject(text))

            else -> error("Ожидался JSON-объект или JSON-массив")
        }

        require(configs.isNotEmpty()) {
            "В JSON не найдено ни одного конфигурационного объекта"
        }

        val result = mutableListOf<ServerProfile>()
        val errors = mutableListOf<String>()

        configs.forEachIndexed { configIndex, root ->
            val outbounds = root.optJSONArray("outbounds")

            if (outbounds == null) {
                parseSingleOutbound(
                    root = root,
                    outbound = root,
                    configIndex = configIndex,
                    outboundIndex = 0,
                    errors = errors,
                    destination = result
                )
                return@forEachIndexed
            }

            if (isCompleteXrayConfig(root, outbounds)) {
                parseCompleteConfig(
                    root = root,
                    configIndex = configIndex,
                    errors = errors
                )?.let(result::add)

                return@forEachIndexed
            }

            // A simple JSON collection of independent outbounds is still
            // imported one outbound at a time.
            for (outboundIndex in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(outboundIndex)
                    ?: continue

                parseSingleOutbound(
                    root = root,
                    outbound = outbound,
                    configIndex = configIndex,
                    outboundIndex = outboundIndex,
                    errors = errors,
                    destination = result
                )
            }
        }

        val unique = result.distinctBy { profile ->
            listOf(
                profile.protocol.lowercase(),
                profile.address.lowercase(),
                profile.port,
                canonicalJson(profile.config)
            ).joinToString("|")
        }

        if (unique.isEmpty()) {
            val details = errors.take(8).joinToString("\n")

            error(
                buildString {
                    append("В JSON не найдено поддерживаемых серверов.")

                    if (details.isNotBlank()) {
                        append("\n")
                        append(details)
                    }
                }
            )
        }

        return unique
    }

    /**
     * Complete configs carry routing and inbounds at root level. They must be
     * stored as a single profile; otherwise an AUTO/country balancer becomes
     * dozens of unrelated servers and all root routing metadata is discarded.
     */
    private fun isCompleteXrayConfig(
        root: JSONObject,
        outbounds: JSONArray
    ): Boolean {
        return root.has("inbounds") ||
            root.has("routing") ||
            root.has("policy") ||
            root.has("observatory") ||
            root.has("balancers") ||
            (
                root.optString("remarks").isNotBlank() &&
                    outbounds.length() > 1
                )
    }

    private fun parseCompleteConfig(
        root: JSONObject,
        configIndex: Int,
        errors: MutableList<String>
    ): ServerProfile? {
        val outbounds = root.optJSONArray("outbounds") ?: return null

        val primary = findFirstServerOutbound(outbounds)

        if (primary == null) {
            errors +=
                "Объект ${configIndex + 1}: в полном Xray-конфиге нет поддерживаемого proxy outbound"

            return null
        }

        val protocol = primary.first
        val server = primary.second

        val address = server.optString("address").trim()
        val port = server.optInt("port", -1)

        if (address.isBlank() || port !in 1..65535) {
            errors +=
                "Объект ${configIndex + 1}: у первого proxy outbound неверный адрес или порт"

            return null
        }

        val meta = root.optJSONObject("meta")

        val name = root.optString("remarks").trim()
            .ifBlank {
                meta?.optString("remarks")?.trim().orEmpty()
            }
            .ifBlank {
                meta?.optString("name")?.trim().orEmpty()
            }
            .ifBlank {
                "${protocol.uppercase()} · $address"
            }

        // Keep the WHOLE config, not just the first VLESS outbound.
        // The builder will inject the app's TUN inbound while retaining
        // routing, balancers, observatory and all proxy outbound settings.
        val fullConfig = JSONObject(root.toString())

        return ServerProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            protocol = protocol.uppercase(),
            address = address,
            port = port,
            rawLink = fullConfig.toString(),
            config = fullConfig
        )
    }

    private fun findFirstServerOutbound(
        outbounds: JSONArray
    ): Pair<String, JSONObject>? {
        val candidates = (0 until outbounds.length()).mapNotNull {
            outbounds.optJSONObject(it)
        }

        // Prefer the conventional "proxy" outbound, then the first supported
        // proxy. Ignore freedom, blackhole, dns, loopback and balancer helpers.
        val ordered = candidates.sortedBy {
            if (it.optString("tag") == "proxy") 0 else 1
        }

        for (outbound in ordered) {
            val protocol = outbound
                .optString("protocol")
                .lowercase()
                .trim()

            if (protocol !in supportedProtocols) continue

            val settings = outbound.optJSONObject("settings")
                ?: continue

            val arrayName = serverArrayNames[protocol]
                ?: continue

            val servers = settings.optJSONArray(arrayName)
                ?: continue

            for (index in 0 until servers.length()) {
                val server = servers.optJSONObject(index)
                    ?: continue

                val address = server.optString("address").trim()
                val port = server.optInt("port", -1)

                if (address.isNotBlank() && port in 1..65535) {
                    return protocol to server
                }
            }
        }

        return null
    }

    private fun parseSingleOutbound(
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

        if (protocol !in supportedProtocols) return

        val settings = outbound.optJSONObject("settings")

        if (settings == null) {
            errors +=
                "Объект ${configIndex + 1}: $protocol без settings"

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

            val address = server.optString("address").trim()
            val port = server.optInt("port", -1)

            if (address.isBlank() || port !in 1..65535) {
                errors +=
                    "Объект ${configIndex + 1}, outbound ${outboundIndex + 1}: неверный адрес или порт"

                continue
            }

            val singleSettings = JSONObject(settings.toString()).apply {
                put(
                    arrayName,
                    JSONArray().put(
                        JSONObject(server.toString())
                    )
                )
            }

            val singleOutbound = JSONObject(outbound.toString()).apply {
                put("settings", singleSettings)
            }

            val rootName = root.optString("remarks").trim()
            val outboundName = outbound.optString("remarks").trim()
            val tag = outbound.optString("tag").trim()

            val baseName = when {
                rootName.isNotBlank() -> rootName
                outboundName.isNotBlank() -> outboundName
                tag.isNotBlank() -> tag
                else -> "${protocol.uppercase()} · $address"
            }

            val suffix = when {
                serverIndex > 0 ->
                    " · ${serverIndex + 1}"

                rootName.isNotBlank() && outboundIndex > 0 ->
                    " · ${outboundIndex + 1}"

                else -> ""
            }

            destination += ServerProfile(
                id = UUID.randomUUID().toString(),
                name = baseName + suffix,
                protocol = protocol.uppercase(),
                address = address,
                port = port,
                rawLink = singleOutbound.toString(),
                config = singleOutbound
            )
        }
    }

    private fun canonicalJson(value: JSONObject): String {
        fun canonical(item: Any?): String = when (item) {
            is JSONObject -> item.keys()
                .asSequence()
                .toList()
                .sorted()
                .joinToString(",", "{", "}") { key ->
                    JSONObject.quote(key) +
                        ":" +
                        canonical(item.opt(key))
                }

            is JSONArray -> (0 until item.length())
                .joinToString(",", "[", "]") {
                    canonical(item.opt(it))
                }

            JSONObject.NULL -> "null"

            is String -> JSONObject.quote(item)

            else -> item.toString()
        }

        return canonical(value)
    }
}
