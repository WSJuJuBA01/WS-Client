package com.wsclient

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class ServerProfile(
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val rawLink: String,
    val config: JSONObject
)

object ProfileCodec {

    fun parse(link: String): ServerProfile {
        val input = link.trim()

        return when {
            input.startsWith("vless://") -> parseVless(input)
            input.startsWith("vmess://") -> parseVmess(input)
            input.startsWith("trojan://") -> parseTrojan(input)
            input.startsWith("ss://") -> parseShadowsocks(input)
            input.startsWith("hysteria2://") ||
                input.startsWith("hy2://") ->
                throw IllegalArgumentException(
                    "Hysteria2 не поддерживается Xray-core"
                )
            else -> throw IllegalArgumentException("Неизвестный формат ссылки")
        }
    }

    private fun parseVless(link: String): ServerProfile {
        val uri = URI(link)
        val user = uri.userInfo ?: error("Нет UUID")
        val params = query(uri.rawQuery)
        val name = decode(uri.fragment ?: "VLESS")

        val config = JSONObject().apply {
            put("protocol", "vless")
            put("settings", JSONObject().put(
                "vnext",
                org.json.JSONArray().put(JSONObject().apply {
                    put("address", uri.host)
                    put("port", uri.port)
                    put("users", org.json.JSONArray().put(JSONObject().apply {
                        put("id", user)
                        put("encryption", params["encryption"] ?: "none")
                        put("flow", params["flow"] ?: "")
                    }))
                })
            ))
            put("streamSettings", streamSettings(params))
        }

        return ServerProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            protocol = "VLESS",
            address = uri.host,
            port = uri.port,
            rawLink = link,
            config = config
        )
    }

    private fun parseTrojan(link: String): ServerProfile {
        val uri = URI(link)
        val password = uri.userInfo ?: error("Нет пароля")
        val params = query(uri.rawQuery)

        val config = JSONObject().apply {
            put("protocol", "trojan")
            put("settings", JSONObject().put(
                "servers",
                org.json.JSONArray().put(JSONObject().apply {
                    put("address", uri.host)
                    put("port", uri.port)
                    put("password", password)
                })
            ))
            put("streamSettings", streamSettings(params))
        }

        return ServerProfile(
            java.util.UUID.randomUUID().toString(),
            decode(uri.fragment ?: "Trojan"),
            "Trojan",
            uri.host,
            uri.port,
            link,
            config
        )
    }

    private fun parseVmess(link: String): ServerProfile {
        val encoded = link.removePrefix("vmess://")
        val decoded = String(
            Base64.decode(
                encoded,
                Base64.DEFAULT or Base64.NO_WRAP
            )
        )
        val data = JSONObject(decoded)

        val address = data.getString("add")
        val port = data.getInt("port")
        val uuid = data.getString("id")
        val tls = data.optString("tls", "")

        val config = JSONObject().apply {
            put("protocol", "vmess")
            put("settings", JSONObject().put(
                "vnext",
                org.json.JSONArray().put(JSONObject().apply {
                    put("address", address)
                    put("port", port)
                    put("users", org.json.JSONArray().put(JSONObject().apply {
                        put("id", uuid)
                        put("alterId", data.optInt("aid", 0))
                        put("security", data.optString("scy", "auto"))
                    }))
                })
            ))
            put("streamSettings", JSONObject().apply {
                put("network", data.optString("net", "tcp"))
                if (tls.isNotBlank()) {
                    put("security", tls)
                    put("tlsSettings", JSONObject().put(
                        "serverName",
                        data.optString("sni", address)
                    ))
                }
            })
        }

        return ServerProfile(
            java.util.UUID.randomUUID().toString(),
            data.optString("ps", "VMess"),
            "VMess",
            address,
            port,
            link,
            config
        )
    }

    private fun parseShadowsocks(link: String): ServerProfile {
        val uri = URI(link)
        val userInfo = uri.userInfo ?: error("Нет данных Shadowsocks")
        val decoded = try {
            String(Base64.decode(userInfo, Base64.DEFAULT))
        } catch (_: Exception) {
            userInfo
        }

        val split = decoded.split(":", limit = 2)
        require(split.size == 2) { "Неверный Shadowsocks URI" }

        val config = JSONObject().apply {
            put("protocol", "shadowsocks")
            put("settings", JSONObject().put(
                "servers",
                org.json.JSONArray().put(JSONObject().apply {
                    put("address", uri.host)
                    put("port", uri.port)
                    put("method", split[0])
                    put("password", split[1])
                })
            ))
        }

        return ServerProfile(
            java.util.UUID.randomUUID().toString(),
            decode(uri.fragment ?: "Shadowsocks"),
            "Shadowsocks",
            uri.host,
            uri.port,
            link,
            config
        )
    }

    private fun streamSettings(params: Map<String, String>): JSONObject {
        val network = params["type"] ?: "tcp"
        val security = params["security"] ?: "none"

        return JSONObject().apply {
            put("network", network)
            put("security", security)

            if (security == "tls") {
                put("tlsSettings", JSONObject().apply {
                    put("serverName", params["sni"] ?: "")
                    put("allowInsecure", false)
                })
            }

            if (security == "reality") {
                put("realitySettings", JSONObject().apply {
                    put("serverName", params["sni"] ?: "")
                    put("fingerprint", params["fp"] ?: "chrome")
                    put("publicKey", params["pbk"] ?: "")
                    put("shortId", params["sid"] ?: "")
                    put("spiderX", params["spx"] ?: "/")
                })
            }

            if (network == "ws") {
                put("wsSettings", JSONObject().put(
                    "path", params["path"] ?: "/"
                ))
            }

            if (network == "grpc") {
                put("grpcSettings", JSONObject().put(
                    "serviceName", params["serviceName"] ?: ""
                ))
            }
        }
    }

    private fun query(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()

        return raw.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                decode(parts[0]) to decode(parts[1])
            } else null
        }.toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, "UTF-8")
}
