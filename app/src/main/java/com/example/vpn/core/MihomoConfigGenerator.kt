package com.example.vpn.core

import com.example.vpn.model.VpnProxy
import java.io.File

/**
 * Генератор конфигураций ядра Mihomo (Clash.Meta).
 * Настраивает туннелирование, DNS, прокси-серверы и правила маршрутизации.
 */
object MihomoConfigGenerator {

    fun generateConfig(
        proxies: List<VpnProxy>,
        activeProxy: VpnProxy?,
        dnsServer: String = "1.1.1.1"
    ): String {
        val sb = StringBuilder()

        // 1. Базовые глобальные параметры
        sb.appendLine("mixed-port: 7890")
        sb.appendLine("allow-lan: false")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("ipv6: false")
        sb.appendLine("external-controller: 127.0.0.1:9090")
        sb.appendLine()

        // 2. Секция TUN контроля трафика (auto-route: false, так как роутингом управляет Android VpnService)
        sb.appendLine("tun:")
        sb.appendLine("  enable: true")
        sb.appendLine("  stack: gvisor")
        sb.appendLine("  auto-route: false")
        sb.appendLine("  auto-detect-interface: true")
        sb.appendLine("  dns-hierarchy: true")
        sb.appendLine()

        // 3. Секция DNS сервера ядра
        sb.appendLine("dns:")
        sb.appendLine("  enable: true")
        sb.appendLine("  ipv6: false")
        sb.appendLine("  listen: 127.0.0.1:1053")
        sb.appendLine("  enhanced-mode: redir-host")
        sb.appendLine("  nameserver:")
        sb.appendLine("    - $dnsServer")
        sb.appendLine("    - 8.8.8.8")
        sb.appendLine("  fallback:")
        sb.appendLine("    - https://dns.cloudflare.com/dns-query")
        sb.appendLine()

        // 4. Секция Proxies
        sb.appendLine("proxies:")
        if (proxies.isEmpty()) {
            // Если прокси пусты, добавляем заглушку, чтобы ядро не упало
            sb.appendLine("  - name: \"Direct-Fallback\"")
            sb.appendLine("    type: socks5")
            sb.appendLine("    server: 127.0.0.1")
            sb.appendLine("    port: 1080")
        } else {
            for (proxy in proxies) {
                appendProxyYaml(sb, proxy)
            }
        }
        sb.appendLine()

        // 5. Proxy Groups (Группы прокси для выбора)
        sb.appendLine("proxy-groups:")
        sb.appendLine("  - name: PROXIES")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        if (proxies.isNotEmpty()) {
            for (proxy in proxies) {
                sb.appendLine("      - \"${proxy.name}\"")
            }
        } else {
            sb.appendLine("      - \"Direct-Fallback\"")
        }
        sb.appendLine("      - DIRECT")
        sb.appendLine()

        // 6. Правила маршрутизации
        sb.appendLine("rules:")
        sb.appendLine("  - GEOIP,lan,DIRECT")
        sb.appendLine("  - MATCH,PROXIES")

        return sb.toString()
    }

    private fun appendProxyYaml(sb: StringBuilder, proxy: VpnProxy) {
        when (proxy) {
            is VpnProxy.VlessReality -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: vless")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                sb.appendLine("    uuid: ${proxy.uuid}")
                sb.appendLine("    udp: true")
                sb.appendLine("    tls: true")
                sb.appendLine("    flow: ${proxy.flow}")
                sb.appendLine("    network: tcp")
                sb.appendLine("    client-fingerprint: chrome")
                sb.appendLine("    servername: ${proxy.serverName}")
                sb.appendLine("    reality-opts:")
                sb.appendLine("      public-key: ${proxy.publicKey}")
                sb.appendLine("      short-id: ${proxy.shortId}")
            }
            is VpnProxy.Hysteria2 -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: hysteria2")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                sb.appendLine("    auth: ${proxy.auth}")
                sb.appendLine("    sni: ${proxy.sni}")
                sb.appendLine("    fast-open: true")
                if (proxy.insecure) {
                    sb.appendLine("    skip-cert-verify: true")
                }
            }
            is VpnProxy.WireGuard -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: wireguard")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                sb.appendLine("    ip: ${proxy.ipLocal}")
                sb.appendLine("    public-key: ${proxy.publicKey}")
                sb.appendLine("    private-key: ${proxy.privateKey}")
                sb.appendLine("    udp: true")
                sb.appendLine("    mtu: ${proxy.mtu}")
                sb.appendLine("    dns: [${proxy.dns}]")
                if (!proxy.reserved.isNullOrBlank()) {
                    sb.appendLine("    reserved: [${proxy.reserved}]")
                }
            }
            is VpnProxy.Shadowsocks -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: ss")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                sb.appendLine("    cipher: ${proxy.cipher}")
                sb.appendLine("    password: ${proxy.password}")
                sb.appendLine("    udp: true")
            }
            is VpnProxy.Socks5 -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: socks5")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                if (!proxy.username.isNullOrBlank()) {
                    sb.appendLine("    username: ${proxy.username}")
                }
                if (!proxy.password.isNullOrBlank()) {
                    sb.appendLine("    password: ${proxy.password}")
                }
                sb.appendLine("    udp: true")
            }
            is VpnProxy.Trojan -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: trojan")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                sb.appendLine("    password: ${proxy.password}")
                sb.appendLine("    udp: true")
                if (!proxy.sni.isNullOrBlank()) {
                    sb.appendLine("    sni: ${proxy.sni}")
                }
                if (proxy.insecure) {
                    sb.appendLine("    skip-cert-verify: true")
                }
            }
            is VpnProxy.Vmess -> {
                sb.appendLine("  - name: \"${proxy.name}\"")
                sb.appendLine("    type: vmess")
                sb.appendLine("    server: ${proxy.server}")
                sb.appendLine("    port: ${proxy.port}")
                sb.appendLine("    uuid: ${proxy.uuid}")
                sb.appendLine("    alterId: ${proxy.alterId}")
                sb.appendLine("    cipher: ${proxy.cipher}")
                sb.appendLine("    udp: true")
                sb.appendLine("    network: ${proxy.network}")
                if (proxy.tls) {
                    sb.appendLine("    tls: true")
                    if (!proxy.sni.isNullOrBlank()) {
                        sb.appendLine("    servername: ${proxy.sni}")
                    }
                }
            }
        }
    }
}
