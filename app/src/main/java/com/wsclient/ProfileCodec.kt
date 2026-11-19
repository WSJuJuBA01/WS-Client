package com.wsclient

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ServerProfile(
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val rawLink: String,
    val config: JSONObject
)

/** Parsers for Xray-compatible share links. Unsupported transports fail explicitly. */
object ProfileCodec {
    fun parse(link: String): ServerProfile {
        val input = link.trim()
        require(input.isNotEmpty()) { "Пустая ссылка" }

        return when {
            input.startsWith("vless://", true) -> parseVless(input)
            input.startsWith("vmess://", true) -> parseVmess(input)
            input.startsWith("trojan://", true) -> parseTrojan(input)
            input.startsWith("ss://", true) -> parseShadowsocks(input)
            input.startsWith("{") -> parseJsonOutbound(input)
            input.startsWith("hysteria2://", true) ||
                input.startsWith("hy2://", true) ->
                throw IllegalArgumentException(
                    "Импорт Hysteria2 пока не реализован в WS Client"
                )

            else -> throw IllegalArgumentException(
                "Неподдерживаемый формат ссылки"
            )
        }
    }

    private fun parseJsonOutbound(raw: String): ServerProfile {
        val root = JSONObject(raw)
        val outbounds = root.optJSONArray("outbounds")

        val outbound = if (outbounds != null) {
            (0 until outbounds.length())
                .asSequence()
                .mapNotNull { outbounds.optJSONObject(it) }
                .firstOrNull {
                    it.optString("protocol") !in
                        setOf("freedom", "blackhole", "dns")
                } ?: error("В JSON не найден прокси-outbound")
        } else {
            root
        }

        val protocol = outbound.optString("protocol").lowercase()

        require(
            protocol in setOf(
                "vless",
                "vmess",
                "trojan",
                "shadowsocks"
            )
        ) {
            "JSON-протокол '$protocol' не поддерживается"
        }

        val settings = outbound.optJSONObject("settings")
            ?: error("В JSON отсутствует settings")

        val server = when (protocol) {
            "vless", "vmess" ->
                settings.optJSONArray("vnext")?.optJSONObject(0)

            else ->
                settings.optJSONArray("servers")?.optJSONObject(0)
        } ?: error("В JSON не найден сервер")

        val address = server.optString("address")
            .ifBlank { error("В JSON отсутствует address") }

        val port = requirePort(server.optInt("port", -1))
        val config = JSONObject(outbound.toString())

        val name = outbound.optString("remarks").ifBlank {
            outbound.optString("tag").ifBlank {
                "$protocol · $address"
            }
        }

        return ServerProfile(
            UUID.randomUUID().toString(),
            name,
            protocol.uppercase(),
            address,
            port,
            raw,
            config
        )
    }

    private fun parseVless(link: String): ServerProfile {
        val uri = URI(link)
        val address = requireHost(uri)
        val port = requirePort(uri.port)

        val uuid = uri.rawUserInfo?.let(::percentDecode)
            ?: error("В ссылке VLESS отсутствует UUID")

        require(uuid.isNotBlank()) {
            "Пустой UUID VLESS"
        }

        val q = query(uri.rawQuery)

        val security = q["security"] ?: "none"

        val config = JSONObject().apply {
            put("protocol", "vless")

            put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject().apply {
                            put("address", address)
                            put("port", port)

                            put(
                                "users",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("id", uuid)
                                        put(
                                            "encryption",
                                            q["encryption"] ?: "none"
                                        )

                                        q["flow"]
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { put("flow", it) }
                                    }
                                )
                            )
                        }
                    )
                )
            )

            put(
                "streamSettings",
                streamSettings(q, security)
            )
        }

        return profile(
            uri,
            "VLESS",
            address,
            port,
            config,
            "VLESS"
        )
    }

    private fun parseTrojan(link: String): ServerProfile {
        val uri = URI(link)
        val address = requireHost(uri)
        val port = requirePort(uri.port)

        val password = uri.rawUserInfo?.let(::percentDecode)
            ?: error("В ссылке Trojan отсутствует пароль")

        require(password.isNotEmpty()) {
            "Пустой пароль Trojan"
        }

        val q = query(uri.rawQuery)

        val config = JSONObject().apply {
            put("protocol", "trojan")

            put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject().apply {
                            put("address", address)
                            put("port", port)
                            put("password", password)
                        }
                    )
                )
            )

            put(
                "streamSettings",
                streamSettings(
                    q,
                    q["security"] ?: "tls"
                )
            )
        }

        return profile(
            uri,
            "Trojan",
            address,
            port,
            config,
            "Trojan"
        )
    }

    private fun parseVmess(link: String): ServerProfile {
        val encoded = link
            .substringAfter("vmess://", "")
            .substringBefore('#')
            .trim()

        val data = JSONObject(decodeBase64(encoded))

        val address = data.optString("add")
            .trim()
            .ifBlank { error("VMess: отсутствует адрес") }

        val port = parsePort(data.optString("port"))

        val uuid = data.optString("id")
            .ifBlank { error("VMess: отсутствует UUID") }

        val network = data.optString("net", "tcp").lowercase()

        val security = data.optString("tls", "")
            .lowercase()
            .ifBlank { "none" }

        val config = JSONObject().apply {
            put("protocol", "vmess")

            put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject().apply {
                            put("address", address)
                            put("port", port)

                            put(
                                "users",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("id", uuid)
                                        put("alterId", data.optInt("aid", 0))
                                        put(
                                            "security",
                                            data.optString("scy", "auto")
                                        )
                                    }
                                )
                            )
                        }
                    )
                )
            )

            put(
                "streamSettings",
                JSONObject().apply {
                    put("network", network)
                    put("security", security)

                    if (security == "tls") {
                        put(
                            "tlsSettings",
                            JSONObject().apply {
                                val sni = data.optString("sni")
                                    .ifBlank {
                                        data.optString("host", address)
                                    }

                                if (sni.isNotBlank()) {
                                    put("serverName", sni)
                                }

                                data.optString("fp")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { put("fingerprint", it) }

                                put("allowInsecure", false)
                            }
                        )
                    }

                    when (network) {
                        "ws" -> put(
                            "wsSettings",
                            JSONObject().apply {
                                put(
                                    "path",
                                    data.optString("path", "/")
                                        .ifBlank { "/" }
                                )

                                val host = data.optString("host")

                                if (host.isNotBlank()) {
                                    put(
                                        "headers",
                                        JSONObject().put("Host", host)
                                    )
                                }
                            }
                        )

                        "grpc" -> put(
                            "grpcSettings",
                            JSONObject().apply {
                                put(
                                    "serviceName",
                                    data.optString(
                                        "path",
                                        data.optString("serviceName", "")
                                    )
                                )
                                put(
                                    "multiMode",
                                    data.optString("type") == "multi"
                                )
                            }
                        )

                        "tcp" -> if (data.optString("type") == "http") {
                            put(
                                "tcpSettings",
                                JSONObject().put(
                                    "header",
                                    JSONObject().apply {
                                        put("type", "http")

                                        val host = data.optString("host")

                                        if (host.isNotBlank()) {
                                            put(
                                                "request",
                                                JSONObject().put(
                                                    "headers",
                                                    JSONObject().put(
                                                        "Host",
                                                        JSONArray().put(host)
                                                    )
                                                )
                                            )
                                        }
                                    }
                                )
                            )
                        }

                        "kcp", "http", "domainsocket", "quic" ->
                            throw IllegalArgumentException(
                                "VMess transport '$network' не включён в эту сборку"
                            )

                        "xhttp", "splithttp" -> {
                            put("network", "xhttp")

                            put(
                                "xhttpSettings",
                                JSONObject().apply {
                                    put("path", data.optString("path", "/"))

                                    data.optString("host")
                                        .takeIf { it.isNotBlank() }
                                        ?.let { put("host", it) }

                                    data.optString("mode")
                                        .takeIf { it.isNotBlank() }
                                        ?.let { put("mode", it) }
                                }
                            )
                        }

                        else -> if (
                            network !in setOf("tcp", "ws", "grpc")
                        ) {
                            throw IllegalArgumentException(
                                "Неизвестный VMess transport: $network"
                            )
                        }
                    }
                }
            )
        }

        val name = data.optString("ps", "VMess")
            .ifBlank { "VMess" }

        return ServerProfile(
            UUID.randomUUID().toString(),
            name,
            "VMess",
            address,
            port,
            link,
            config
        )
    }

    private fun parseShadowsocks(
        link: String
    ): ServerProfile {
        val uri = URI(link)
        val address = requireHost(uri)
        val port = requirePort(uri.port)

        val rawUser = uri.rawUserInfo
            ?: error("Нет метода и пароля Shadowsocks")

        val decoded = runCatching {
            decodeBase64(percentDecode(rawUser))
        }.getOrElse {
            percentDecode(rawUser)
        }

        val split = decoded.split(":", limit = 2)

        require(
            split.size == 2 &&
                split[0].isNotBlank() &&
                split[1].isNotBlank()
        ) {
            "Неверные данные Shadowsocks"
        }

        val config = JSONObject().apply {
            put("protocol", "shadowsocks")

            put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject().apply {
                            put("address", address)
                            put("port", port)
                            put("method", split[0])
                            put("password", split[1])
                        }
                    )
                )
            )
        }

        return profile(
            uri,
            "Shadowsocks",
            address,
            port,
            config,
            "Shadowsocks"
        )
    }

    private fun streamSettings(
        q: Map<String, String>,
        securityValue: String
    ): JSONObject {
        val network = (q["type"] ?: "tcp").lowercase()
        val security = securityValue.lowercase()

        require(
            network in setOf(
                "tcp",
                "ws",
                "grpc",
                "xhttp",
                "splithttp"
            )
        ) {
            "Транспорт '$network' не поддерживается"
        }

        require(
            security in setOf("none", "tls", "reality")
        ) {
            "Защита '$security' не поддерживается"
        }

        if (security == "reality") {
            require(!q["pbk"].isNullOrBlank()) {
                "Для REALITY отсутствует public key (pbk)"
            }

            require(!q["sni"].isNullOrBlank()) {
                "Для REALITY отсутствует SNI"
            }
        }

        return JSONObject().apply {
            put(
                "network",
                if (network == "splithttp") "xhttp" else network
            )

            put("security", security)

            if (security == "tls") {
                put(
                    "tlsSettings",
                    JSONObject().apply {
                        q["sni"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("serverName", it) }

                        q["fp"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("fingerprint", it) }

                        q["alpn"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                put(
                                    "alpn",
                                    JSONArray(
                                        it.split(',').map(String::trim)
                                    )
                                )
                            }

                        put(
                            "allowInsecure",
                            q["allowInsecure"] == "1" ||
                                q["allowInsecure"] == "true"
                        )
                    }
                )
            }

            if (security == "reality") {
                put(
                    "realitySettings",
                    JSONObject().apply {
                        q["sni"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("serverName", it) }

                        put("fingerprint", q["fp"] ?: "chrome")

                        q["pbk"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("publicKey", it) }

                        q["sid"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("shortId", it) }

                        put("spiderX", q["spx"] ?: "/")
                    }
                )
            }

            when (network) {
                "ws" -> put(
                    "wsSettings",
                    JSONObject().apply {
                        put("path", q["path"] ?: "/")

                        q["host"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                put(
                                    "headers",
                                    JSONObject().put("Host", it)
                                )
                            }
                    }
                )

                "grpc" -> put(
                    "grpcSettings",
                    JSONObject().apply {
                        put(
                            "serviceName",
                            q["serviceName"] ?: q["path"] ?: ""
                        )
                        put("multiMode", q["mode"] == "multi")
                    }
                )

                "xhttp", "splithttp" -> put(
                    "xhttpSettings",
                    JSONObject().apply {
                        put("path", q["path"] ?: "/")

                        q["host"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("host", it) }

                        q["mode"]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("mode", it) }
                    }
                )

                else -> Unit
            }
        }
    }

    private fun query(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()

        return raw.split('&').mapNotNull { part ->
            if (part.isEmpty()) return@mapNotNull null

            val split = part.split('=', limit = 2)

            percentDecode(split[0]) to
                if (split.size == 2) {
                    percentDecode(split[1])
                } else {
                    ""
                }
        }.toMap()
    }

    // URLDecoder treats '+' as a space; in URI query strings '+' is a literal plus.
    private fun percentDecode(value: String): String =
        URLDecoder.decode(
            value.replace("+", "%2B"),
            "UTF-8"
        )

    private fun decodeBase64(value: String): String {
        val clean = value.trim()
            .replace("-", "+")
            .replace("_", "/")
            .replace("\\s".toRegex(), "")

        val padded = clean +
            "=".repeat((4 - clean.length % 4) % 4)

        val bytes = Base64.decode(
            padded,
            Base64.DEFAULT
        )

        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun requireHost(uri: URI): String =
        uri.host?.takeIf { it.isNotBlank() }
            ?: error("В ссылке отсутствует корректный адрес сервера")

    private fun requirePort(port: Int): Int =
        port.takeIf { it in 1..65535 }
            ?: error("Некорректный порт сервера")

    private fun parsePort(value: String): Int =
        value.toIntOrNull()
            ?.let(::requirePort)
            ?: error("Некорректный порт сервера")

    private fun profile(
        uri: URI,
        protocol: String,
        address: String,
        port: Int,
        config: JSONObject,
        fallback: String
    ): ServerProfile {
        val name = uri.rawFragment
            ?.let(::percentDecode)
            ?.ifBlank { fallback }
            ?: fallback

        return ServerProfile(
            UUID.randomUUID().toString(),
            name,
            protocol,
            address,
            port,
            uri.toString(),
            config
        )
    }
}
