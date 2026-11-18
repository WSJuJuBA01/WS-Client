package com.wsclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object ServerLatency {
    suspend fun check(profile: ServerProfile): Long =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()

            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(profile.address, profile.port),
                    5000
                )
            }

            System.currentTimeMillis() - start
        }
}
