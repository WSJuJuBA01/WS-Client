package com.wsclient

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class LinkImportResult(
    val profiles: List<ServerProfile>,
    val errors: List<String>
)

/**
 * Текстовые подписки и вставка из буфера: ссылки построчно
 * либо весь список целиком в base64.
 */
object LinkListParser {

    private val schemes = listOf(
        "vless://", "vmess://", "trojan://", "ss://",
        "hysteria2://", "hy2://", "hysteria://", "hy://",
        "tuic://", "wireguard://", "socks://", "socks5://"
    )

    fun parse(raw: String): LinkImportResult {
        val text = raw.trim().trimStart('\uFEFF').trim()

        require(text.isNotEmpty()) { "Пустой ответ" }

        var lines = splitLines(text)

        if (lines.none(::isLink)) {
            decodeBase64OrNull(text)?.let { lines = splitLines(it) }
        }

        val links = lines.filter(::isLink)

        require(links.isNotEmpty()) {
            "Нет ссылок конфигураций (vless://, hy2:// и т.п.)"
        }

        val profiles = mutableListOf<ServerProfile>()
        val errors = mutableListOf<String>()

        for (link in links) {
            try {
                profiles += ProfileCodec.parse(link)
            } catch (e: Exception) {
                errors += "${link.take(32)}…: ${e.message}"
            }
        }

        return LinkImportResult(profiles, errors)
    }

    /**
     * Пробелы и эмодзи в имени (#🇩🇪 Germany) ломают java.net.URI.
     * Имя после '#' кодируем заново, пробелы в остальной части → %20.
     */
    fun sanitizeLink(link: String): String {
        val hash = link.indexOf('#')

        val head = (if (hash >= 0) link.substring(0, hash) else link)
            .replace(" ", "%20")

        if (hash < 0) return head

        val tail = link.substring(hash + 1)

        val decoded = runCatching {
            URLDecoder.decode(tail.replace("+", "%2B"), "UTF-8")
        }.getOrDefault(tail)

        val encoded = URLEncoder.encode(decoded, "UTF-8")
            .replace("+", "%20")

        return "$head#$encoded"
    }

    private fun splitLines(text: String): List<String> =
        text.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun isLink(line: String): Boolean =
        schemes.any { line.startsWith(it, ignoreCase = true) }

    private fun decodeBase64OrNull(value: String): String? =
        runCatching {
            val clean = value
                .filterNot { it.isWhitespace() }
                .replace('-', '+')
                .replace('_', '/')

            val padded = clean + "=".repeat((4 - clean.length % 4) % 4)

            String(
                Base64.decode(padded, Base64.DEFAULT),
                StandardCharsets.UTF_8
            )
        }.getOrNull()
}

/**
 * hysteria2://auth@host:port/?sni=..&insecure=1&obfs=salamander
 *   &obfs-password=..&pinSHA256=..&mport=20000-30000#name
 */
object Hysteria2Codec {

    fun parse(link: String): ServerProfile {
        val body = link.trim().substringAfter("://")
        val fragment = body.substringAfter('#', "")
        val noFragment = body.substringBefore('#')
        val rawQuery = noFragment.substringAfter('?', "")
        val authority = noFragment.substringBefore('?').substringBefore('/')

        val at = authority.lastIndexOf('@')

        val auth = if (at >= 0) decode(authority.substring(0, at)) else ""

        require(auth.isNotEmpty()) {
            "В ссылке Hysteria2 отсутствует пароль"
        }

        val hostPort = authority.substring(at + 1)

        val host: String
        val portSpec: String

        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            require(end > 0) { "Некорректный IPv6-адрес" }
            host = hostPort.substring(1, end)
            portSpec = hostPort.substring(end + 1).removePrefix(":")
        } else {
            host = hostPort.substringBefore(':')
            portSpec = hostPort.substringAfter(':', "")
        }

        require(host.isNotBlank()) {
            "В ссылке отсутствует адрес сервера"
        }

        val q = query(rawQuery)

        val port = portSpec.ifBlank { "443" }
            .substringBefore(',')
            .substringBefore('-')
            .trim()
            .toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: error("Некорректный порт сервера")

        // Port hopping: host:443,5000-6000 или ?mport=20000-30000
        val hopPorts = (q["mport"] ?: portSpec)
            .takeIf { it.contains(',') || it.contains('-') }
            ?.replace(':', '-')

        val obfs = q["obfs"].orEmpty()

        require(obfs.isEmpty() || obfs.equals("salamander", true)) {
            "Hysteria2 obfs '$obfs' не поддерживается"
        }

        val sni = q["sni"]?.takeIf { it.isNotBlank() } ?: host

        val insecure = listOf(q["insecure"], q["allowInsecure"]).any {
            it == "1" || it.equals("true", true)
        }

        val alpn = q["alpn"]
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim() }
            ?: listOf("h3")

        val tls = JSONObject().apply {
            put("serverName", sni)
            put("allowInsecure", insecure)
            put("alpn", JSONArray(alpn))

            q["pinSHA256"]
                ?.takeIf { it.isNotBlank() }
                ?.let { pin ->
                    put(
                        "pinnedPeerCertSha256",
                        JSONArray().put(pin.replace(":", "").lowercase())
                    )
                }
        }

        val finalMask = JSONObject()

        if (obfs.equals("salamander", true)) {
            val pass = q["obfs-password"].orEmpty()

            require(pass.isNotEmpty()) {
                "Для obfs=salamander не указан obfs-password"
            }

            finalMask.put(
                "udp",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "salamander")
                        put("settings", JSONObject().put("password", pass))
                    }
                )
            )
        }

        if (hopPorts != null) {
            finalMask.put(
                "quicParams",
                JSONObject().put(
                    "udpHop",
                    JSONObject().put("ports", hopPorts)
                )
            )
        }

        val stream = JSONObject().apply {
            put("network", "hysteria")
            put("security", "tls")
            put("tlsSettings", tls)
            put(
                "hysteriaSettings",
                JSONObject().put("version", 2).put("auth", auth)
            )

            if (finalMask.length() > 0) {
                put("finalmask", finalMask)
            }
        }

        val config = JSONObject().apply {
            put("protocol", "hysteria")
            put(
                "settings",
                JSONObject().apply {
                    put("version", 2)
                    put("address", host)
                    put("port", port)
                }
            )
            put("streamSettings", stream)
        }

        val name = runCatching { decode(fragment) }
            .getOrDefault(fragment)
            .ifBlank { "Hysteria2" }

        return ServerProfile(
            UUID.randomUUID().toString(),
            name,
            "Hysteria2",
            host,
            port,
            link.trim(),
            config
        )
    }

    private fun query(raw: String): Map<String, String> =
        raw.split('&')
            .filter { it.isNotEmpty() }
            .associate { part ->
                val kv = part.split('=', limit = 2)
                decode(kv[0]) to decode(kv.getOrElse(1) { "" })
            }

    // '+' в ссылках — обычный плюс, а не пробел.
    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
}
