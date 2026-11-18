package com.wsclient

import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    fun build(
        profile: ServerProfile,
        outboundInterface: String
    ): String {
        val config = JSONObject()

        config.put("log", JSONObject().put("loglevel", "warning"))

        config.put("inbounds", JSONArray().put(
            JSONObject().apply {
                put("tag", "tun-in")
                put("protocol", "tun")
                put("settings", JSONObject().apply {
                    put("name", "ws-tun")
                    put("mtu", 1500)
                    put("gateway", JSONArray().put("10.0.0.1/30"))
                    put("dns", JSONArray().put("1.1.1.1"))
                    put("autoOutboundsInterface", outboundInterface)
                })
            }
        ))

        val outbound = JSONObject(profile.config.toString())
        outbound.put("tag", "proxy")

        config.put("outbounds", JSONArray().put(outbound).put(
            JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
            }
        ))

        config.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", JSONArray().put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().put("tun-in"))
                put("outboundTag", "proxy")
            }))
        })

        config.put("stats", JSONObject())
        config.put("policy", JSONObject().put(
            "levels",
            JSONObject().put("0", JSONObject().apply {
                put("statsUserUplink", true)
                put("statsUserDownlink", true)
            })
        ))

        return config.toString()
    }
}
