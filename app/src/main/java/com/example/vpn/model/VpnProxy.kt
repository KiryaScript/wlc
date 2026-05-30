package com.example.vpn.model

import java.util.UUID

sealed class VpnProxy {
    abstract val id: String
    abstract val name: String
    abstract val server: String
    abstract val port: Int

    abstract fun copyWithName(newName: String): VpnProxy

    data class VlessReality(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val uuid: String,
        val flow: String = "xtls-rprx-vision",
        val publicKey: String,
        val serverName: String, // SNI
        val shortId: String,
        val spiderX: String = "/"
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }

    data class Hysteria2(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val auth: String, // auth string / password
        val sni: String,
        val insecure: Boolean = true,
        val obsolete: Boolean = false
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }

    data class WireGuard(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val privateKey: String,
        val publicKey: String,
        val ipLocal: String = "10.0.0.2/32",
        val dns: String = "1.1.1.1",
        val mtu: Int = 1420,
        val reserved: String? = null // Reserved bytes (optional)
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }

    data class Shadowsocks(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val cipher: String = "2022-blake3-aes-128-gcm", // aes-128-gcm, chacha20-ietf-poly1305, etc.
        val password: String
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }

    data class Socks5(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val username: String? = null,
        val password: String? = null
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }

    data class Trojan(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val password: String,
        val sni: String? = null,
        val insecure: Boolean = true
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }

    data class Vmess(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        override val server: String,
        override val port: Int,
        val uuid: String,
        val alterId: Int = 0,
        val cipher: String = "auto",
        val network: String = "tcp",
        val tls: Boolean = false,
        val sni: String? = null
    ) : VpnProxy() {
        override fun copyWithName(newName: String): VpnProxy = copy(name = newName)
    }
}
