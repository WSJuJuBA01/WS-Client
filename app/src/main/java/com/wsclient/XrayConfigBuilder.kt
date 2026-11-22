package com.wsclient

import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

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

    fun build(profile: ServerProfile): String {
        val stored = JSONObject(profile.config.toString())

        return if (isCompleteXrayConfig(stored)) {
            buildCompleteConfig(stored)
        } else {
            buildSingleOutboundConfig(stored)
        }
    }

    /**
     * Builds a complete client config from a subscription JSON while preserving
     * all of its proxy outbounds, balancers, routing rules, TLS/WS settings,
     * mux, sockopt and observatory configuration. Only the local TUN inbound
     * and a final TUN fallback routing rule are injected.
     */
    private fun buildCompleteConfig(source: JSONObject): String {
        val config = JSONObject(source.toString())

        // These are subscription/client metadata, not Xray runtime fields.
        config.remove("uuid")
        config.remove("remarks")
        config.remove("meta")

        if (!config.has("log")) {
            config.put(
                "log",
                JSONObject().put("loglevel", "warning")
            )
        }

        val outbounds = config.optJSONArray("outbounds")
            ?: error("Полный Xray-конфиг не содержит outbounds")

        require(findFirstProxyOutbound(outbounds) != null) {
            "В полном Xray-конфиге нет поддерживаемого proxy outbound"
        }

        ensureDirectOutbound(outbounds)

        // Preserve local SOCKS/HTTP inbounds from the original JSON.
        // Replace only an old tun inbound, if one is already present.
        val originalInbounds =
            config.optJSONArray("inbounds") ?: JSONArray()

        val inbounds = JSONArray()

        for (index in 0 until originalInbounds.length()) {
            val inbound = originalInbounds.optJSONObject(index)
                ?: continue

            val tag = inbound.optString("tag")
            val protocol = inbound.optString("protocol")

            if (tag == TUN_TAG || protocol == "tun") continue

            inbounds.put(JSONObject(inbound.toString()))
        }

        inbounds.put(createTunInbound())
        config.put("inbounds", inbounds)

        val routing = config.optJSONObject("routing")
            ?: JSONObject().put("domainStrategy", "AsIs")

        val rules = routing.optJSONArray("rules") ?: JSONArray()

        val alreadyHasTunRule = (0 until rules.length()).any { index ->
            val rule = rules.optJSONObject(index)
                ?: return@any false

            val tags = rule.optJSONArray("inboundTag")
                ?: return@any false

            (0 until tags.length()).any {
                tags.optString(it) == TUN_TAG
            }
        }

        if (!alreadyHasTunRule) {
            val balancerTag = firstBalancerTag(routing)
            val outboundTag = firstProxyTag(outbounds)

            val fallbackRule = JSONObject().apply {
                put(
                    "type",
                    "field"
                )

                put(
                    "inboundTag",
                    JSONArray().put(TUN_TAG)
                )

                if (balancerTag != null) {
                    put("balancerTag", balancerTag)
                } else {
                    put("outboundTag", outboundTag)
                }
            }

            // Keep existing rules first (domain-specific DIRECT rules and
            // global balancer rules must preserve their original precedence).
            rules.put(fallbackRule)
        }

        routing.put("rules", rules)
        config.put("routing", routing)

        ensureStatsAndPolicy(config)

        return config.toString()
    }

    /** Existing simple single-outbound profiles keep their original behavior. */
    private fun buildSingleOutboundConfig(
        outboundSource: JSONObject
    ): String {
        val outbound = JSONObject(outboundSource.toString())
        outbound.put("tag", "proxy")

        val config = JSONObject()

        config.put(
            "log",
            JSONObject().put("loglevel", "warning")
        )

        config.put(
            "inbounds",
            JSONArray().put(createTunInbound())
        )

        config.put(
            "outbounds",
            JSONArray()
                .put(outbound)
                .put(
                    JSONObject().apply {
                        put("tag", "direct")
                        put("protocol", "freedom")
                    }
                )
        )

        config.put(
            "routing",
            JSONObject().apply {
                put("domainStrategy", "AsIs")

                put(
                    "rules",
                    JSONArray().put(
                        JSONObject().apply {
                            put("type", "field")

                            put(
                                "inboundTag",
                                JSONArray().put(TUN_TAG)
                            )

                            put("outboundTag", "proxy")
                        }
                    )
                )
            }
        )

        ensureStatsAndPolicy(config)

        return config.toString()
    }

    /**
     * measureOutboundDelay needs a standalone outbound. For a logical country
     * config, test its first proxy endpoint instead of handing it the complete
     * inbound/routing/balancer config as if it were a single outbound.
     */
    fun buildForPing(profile: ServerProfile): String {
        val stored = JSONObject(profile.config.toString())

        val outbounds = stored.optJSONArray("outbounds")

        val outbound = if (outbounds != null) {
            findFirstProxyOutbound(outbounds)
                ?: error(
                    "В JSON-профиле не найден proxy outbound для проверки"
                )
        } else {
            stored
        }

        val proxy = JSONObject(outbound.toString()).apply {
            put("tag", "proxy")
        }

        return JSONObject().apply {
            put(
                "log",
                JSONObject().put("loglevel", "warning")
            )

            put(
                "outbounds",
                JSONArray().put(proxy)
            )
        }.toString()
    }

    private fun isCompleteXrayConfig(
        config: JSONObject
    ): Boolean {
        val outbounds = config.optJSONArray("outbounds")
            ?: return false

        return config.has("inbounds") ||
            config.has("routing") ||
            config.has("policy") ||
            config.has("observatory") ||
            config.has("balancers") ||
            (
                config.optString("remarks").isNotBlank() &&
                    outbounds.length() > 1
                )
    }

    private fun findFirstProxyOutbound(
        outbounds: JSONArray
    ): JSONObject? {
        val candidates = (0 until outbounds.length()).mapNotNull {
            outbounds.optJSONObject(it)
        }.sortedBy {
            if (it.optString("tag") == "proxy") 0 else 1
        }

        return candidates.firstOrNull { outbound ->
            val protocol = outbound
                .optString("protocol")
                .lowercase()

            if (protocol !in supportedProtocols) {
                return@firstOrNull false
            }

            val settings = outbound.optJSONObject("settings")
                ?: return@firstOrNull false

            val arrayName = serverArrayNames[protocol]
                ?: return@firstOrNull false

            val servers = settings.optJSONArray(arrayName)
                ?: return@firstOrNull false

            (0 until servers.length()).any { index ->
                val server = servers.optJSONObject(index)
                    ?: return@any false

                server.optString("address").isNotBlank() &&
                    server.optInt("port", -1) in 1..65535
            }
        }
    }

    private fun firstProxyTag(
        outbounds: JSONArray
    ): String {
        val proxy = findFirstProxyOutbound(outbounds)
            ?: error("Не найден proxy outbound")

        return proxy.optString("tag").ifBlank {
            "proxy"
        }
    }

    private fun firstBalancerTag(
        routing: JSONObject
    ): String? {
        val balancers = routing.optJSONArray("balancers")
            ?: return null

        for (index in 0 until balancers.length()) {
            val tag = balancers.optJSONObject(index)
                ?.optString("tag")
                ?.takeIf { it.isNotBlank() }

            if (tag != null) return tag
        }

        return null
    }

    private fun ensureDirectOutbound(
        outbounds: JSONArray
    ) {
        val hasFreedom = (0 until outbounds.length()).any { index ->
            outbounds.optJSONObject(index)
                ?.optString("protocol") == "freedom"
        }

        if (!hasFreedom) {
            outbounds.put(
                JSONObject().apply {
                    put("tag", "direct")
                    put("protocol", "freedom")
                }
            )
        }
    }

    private fun createTunInbound(): JSONObject =
        JSONObject().apply {
            put("tag", TUN_TAG)
            put("protocol", "tun")

            put(
                "settings",
                JSONObject().apply {
                    put("name", "ws-tun")
                    put("MTU", TUN_MTU)
                    put("userLevel", 0)
                }
            )

            put(
                "sniffing",
                JSONObject().apply {
                    put("enabled", true)
                    put("routeOnly", true)

                    put(
                        "destOverride",
                        JSONArray()
                            .put("http")
                            .put("tls")
                            .put("quic")
                    )
                }
            )
        }

    private fun ensureStatsAndPolicy(
        config: JSONObject
    ) {
        val stats = config.optJSONObject("stats")
            ?: JSONObject()

        config.put("stats", stats)

        val policy = config.optJSONObject("policy")
            ?: JSONObject()

        val levels = policy.optJSONObject("levels")
            ?: JSONObject()

        val levelZero = levels.optJSONObject("0")
            ?: JSONObject()

        // WS Client's statistics screen reads Xray user traffic counters.
        levelZero.put("statsUserUplink", true)
        levelZero.put("statsUserDownlink", true)

        levels.put("0", levelZero)
        policy.put("levels", levels)

        config.put("policy", policy)
    }

    private const val TUN_TAG = "tun-in"
    private const val TUN_MTU = 1500
}
