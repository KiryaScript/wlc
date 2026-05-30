package com.example.vpn.core

import android.util.Base64
import com.example.vpn.model.VpnProxy
import java.net.URLDecoder
import java.util.UUID

object VpnUriParser {

    /**
     * Parses a single URI or configuration string and turns it into a VpnProxy.
     */
    fun parseUri(uri: String): VpnProxy? {
        val trimmed = uri.trim()
        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("hy2://", ignoreCase = true) || trimmed.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(trimmed)
                trimmed.startsWith("socks5://", ignoreCase = true) || trimmed.startsWith("socks://", ignoreCase = true) -> parseSocks5(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parsed Base64 or plain newline-separated subscription link contents or Clash YAML.
     */
    fun parseSubscription(content: String): List<VpnProxy> {
        val list = mutableListOf<VpnProxy>()
        if (content.isBlank()) return list

        val trimmedContent = content.trim()

        // 1. Check if Clash YAML format directly
        if (trimmedContent.contains("proxies:") || trimmedContent.contains("proxy-groups:")) {
            return parseClashYaml(trimmedContent)
        }

        // 2. Try to decode the entire string as a single base64 string
        val concatenatedBase64 = trimmedContent.replace("\r", "").replace("\n", "").replace(" ", "").replace("\t", "")
        val decodedFromWhole = tryDecodeBase64(concatenatedBase64)
        if (decodedFromWhole != null && (decodedFromWhole.contains("://") || decodedFromWhole.contains("proxies:"))) {
            return parseDecodedContent(decodedFromWhole)
        }

        // 3. Fallback to line-by-line processing
        val lines = trimmedContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            // Check if the raw line itself is already a valid URI
            val directProxy = parseUri(line)
            if (directProxy != null) {
                list.add(directProxy)
                continue
            }

            // Maybe the line is a Base64-encoded string
            val decodedLine = tryDecodeBase64(line)
            if (decodedLine != null) {
                if (decodedLine.trim().contains("://")) {
                    val p = parseUri(decodedLine.trim())
                    if (p != null) {
                        list.add(p)
                        continue
                    }
                }

                // A single decoded line might contain multiple URIs separated by newlines
                val subList = parseDecodedContent(decodedLine)
                if (subList.isNotEmpty()) {
                    list.addAll(subList)
                    continue
                }
            }
        }

        // 4. Default fallback: parse the plain text directly if everything else failed
        if (list.isEmpty()) {
            return parseDecodedContent(trimmedContent)
        }

        return list
    }

    private fun tryDecodeBase64(str: String): String? {
        val clean = str.replace("\r", "").replace("\n", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null

        val flagsList = listOf(
            Base64.DEFAULT,
            Base64.URL_SAFE,
            Base64.NO_PADDING,
            Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_PADDING
        )

        for (flags in flagsList) {
            try {
                val decodedBytes = Base64.decode(clean, flags)
                val decodedStr = String(decodedBytes, Charsets.UTF_8)
                if (decodedStr.isNotEmpty() && (decodedStr.contains("://") || decodedStr.contains("proxies:") || decodedStr.contains("name:") || decodedStr.contains("type:") || decodedStr.all { !it.isISOControl() || it.isWhitespace() })) {
                    return decodedStr
                }
            } catch (e: Exception) {
                // Continue trying other flags
            }
        }

        // Last-resort normalization fallback with android.util.Base64
        var normalized = clean.replace("-", "+").replace("_", "/")
        while (normalized.length % 4 != 0) {
            normalized += "="
        }
        return try {
            val decodedBytes = Base64.decode(normalized, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDecodedContent(decoded: String): List<VpnProxy> {
        val proxies = mutableListOf<VpnProxy>()
        val trimmed = decoded.trim()
        if (trimmed.contains("proxies:") || trimmed.contains("proxy-groups:")) {
            return parseClashYaml(trimmed)
        }
        val lines = trimmed.split("\n")
        for (line in lines) {
            val p = parseUri(line.trim())
            if (p != null) {
                proxies.add(p)
            }
        }
        return proxies
    }

    private fun parseVless(uri: String): VpnProxy.VlessReality? {
        // vless://uuid@host:port?query#label
        val mainPart = uri.substring(8)
        val hashIndex = mainPart.indexOf("#")
        val label = if (hashIndex != -1) {
            decodeUrl(mainPart.substring(hashIndex + 1))
        } else {
            "VLESS Reality Key"
        }
        val beforeHash = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart

        val atIndex = beforeHash.indexOf("@")
        if (atIndex == -1) return null
        val uuid = beforeHash.substring(0, atIndex)

        val remainder = beforeHash.substring(atIndex + 1)
        val qIndex = remainder.indexOf("?")
        val hostPortStr = if (qIndex != -1) remainder.substring(0, qIndex) else remainder
        val queryStr = if (qIndex != -1) remainder.substring(qIndex + 1) else ""

        val hpParts = hostPortStr.split(":")
        val server = hpParts[0]
        val port = hpParts.getOrNull(1)?.toIntOrNull() ?: 443

        val queryMap = parseQueryParams(queryStr)
        val sni = queryMap["sni"] ?: server
        val publicKey = queryMap["pbk"] ?: ""
        val shortId = queryMap["sid"] ?: ""
        val flow = queryMap["flow"] ?: "xtls-rprx-vision"

        return VpnProxy.VlessReality(
            name = label,
            server = server,
            port = port,
            uuid = uuid,
            publicKey = publicKey,
            serverName = sni,
            shortId = shortId,
            flow = flow
        )
    }

    private fun parseHysteria2(uri: String): VpnProxy.Hysteria2? {
        // hy2://auth@host:port?query#label or hysteria2://
        val prefixLen = if (uri.startsWith("hy2://", ignoreCase = true)) 6 else 12
        val mainPart = uri.substring(prefixLen)
        val hashIndex = mainPart.indexOf("#")
        val label = if (hashIndex != -1) {
            decodeUrl(mainPart.substring(hashIndex + 1))
        } else {
            "Hysteria 2 Profile"
        }
        val beforeHash = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart

        val atIndex = beforeHash.indexOf("@")
        val auth = if (atIndex != -1) beforeHash.substring(0, atIndex) else ""
        val remainder = if (atIndex != -1) beforeHash.substring(atIndex + 1) else beforeHash

        val qIndex = remainder.indexOf("?")
        val hostPortStr = if (qIndex != -1) remainder.substring(0, qIndex) else remainder
        val queryStr = if (qIndex != -1) remainder.substring(qIndex + 1) else ""

        val hpParts = hostPortStr.split(":")
        val server = hpParts[0]
        val port = hpParts.getOrNull(1)?.toIntOrNull() ?: 443

        val queryMap = parseQueryParams(queryStr)
        val sni = queryMap["sni"] ?: server
        val insecure = queryMap["insecure"]?.toBoolean() ?: true

        return VpnProxy.Hysteria2(
            name = label,
            server = server,
            port = port,
            auth = auth,
            sni = sni,
            insecure = insecure
        )
    }

    private fun parseSocks5(uri: String): VpnProxy.Socks5? {
        // socks5://username:password@host:port#label
        val prefixLen = if (uri.startsWith("socks5://", ignoreCase = true)) 9 else 8
        val mainPart = uri.substring(prefixLen)
        val hashIndex = mainPart.indexOf("#")
        val label = if (hashIndex != -1) {
            decodeUrl(mainPart.substring(hashIndex + 1))
        } else {
            "SOCKS5 Proxy"
        }
        val beforeHash = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart

        val atIndex = beforeHash.indexOf("@")
        val authPart = if (atIndex != -1) beforeHash.substring(0, atIndex) else null
        val remainder = if (atIndex != -1) beforeHash.substring(atIndex + 1) else beforeHash

        val hpParts = remainder.split(":")
        val server = hpParts[0]
        val port = hpParts.getOrNull(1)?.toIntOrNull() ?: 1080

        var user: String? = null
        var pass: String? = null
        if (authPart != null) {
            val authSplit = authPart.split(":")
            user = authSplit.getOrNull(0)
            pass = authSplit.getOrNull(1)
        }

        return VpnProxy.Socks5(
            name = label,
            server = server,
            port = port,
            username = user,
            password = pass
        )
    }

    private fun parseShadowsocks(uri: String): VpnProxy.Shadowsocks? {
        // ss://method:password@host:port#label or ss://base64encoded@host:port#label
        // Let's decode ss links
        val mainPart = uri.substring(5)
        val hashIndex = mainPart.indexOf("#")
        val label = if (hashIndex != -1) {
            decodeUrl(mainPart.substring(hashIndex + 1))
        } else {
            "Shadowsocks"
        }
        val beforeHash = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart

        val atIndex = beforeHash.indexOf("@")
        if (atIndex == -1) return null

        val credentialsPart = beforeHash.substring(0, atIndex)
        val remainder = beforeHash.substring(atIndex + 1)

        val hpParts = remainder.split(":")
        val server = hpParts[0]
        val port = hpParts.getOrNull(1)?.toIntOrNull() ?: 8388

        // Extract cipher / pass
        var cipher = "chacha20-ietf-poly1305"
        var password = "secret_password"

        val decodedCredentials = try {
            val decodedBytes = Base64.decode(credentialsPart, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            credentialsPart
        }

        if (decodedCredentials.contains(":")) {
            val splits = decodedCredentials.split(":")
            cipher = splits.getOrNull(0) ?: cipher
            password = splits.getOrNull(1) ?: password
        } else {
            password = decodedCredentials
        }

        return VpnProxy.Shadowsocks(
            name = label,
            server = server,
            port = port,
            cipher = cipher,
            password = password
        )
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isBlank()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val index = pair.indexOf("=")
            if (index != -1) {
                val key = decodeUrl(pair.substring(0, index))
                val value = decodeUrl(pair.substring(index + 1))
                result[key] = value
            }
        }
        return result
    }

    private fun decodeUrl(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }
    }

    private fun parseTrojan(uri: String): VpnProxy.Trojan? {
        // trojan://password@host:port?query#label
        val mainPart = uri.substring(9)
        val hashIndex = mainPart.indexOf("#")
        val label = if (hashIndex != -1) {
            decodeUrl(mainPart.substring(hashIndex + 1))
        } else {
            "Trojan Proxy"
        }
        val beforeHash = if (hashIndex != -1) mainPart.substring(0, hashIndex) else mainPart

        val atIndex = beforeHash.indexOf("@")
        if (atIndex == -1) return null
        val password = beforeHash.substring(0, atIndex)

        val remainder = beforeHash.substring(atIndex + 1)
        val qIndex = remainder.indexOf("?")
        val hostPortStr = if (qIndex != -1) remainder.substring(0, qIndex) else remainder
        val queryStr = if (qIndex != -1) remainder.substring(qIndex + 1) else ""

        val hpParts = hostPortStr.split(":")
        val server = hpParts[0]
        val port = hpParts.getOrNull(1)?.toIntOrNull() ?: 443

        val queryMap = parseQueryParams(queryStr)
        val sni = queryMap["sni"] ?: queryMap["peer"] ?: server
        val insecure = queryMap["issecure"]?.toFlexibleBoolean(true) ?: queryMap["insecure"]?.toFlexibleBoolean(true) ?: true

        return VpnProxy.Trojan(
            name = label,
            server = server,
            port = port,
            password = password,
            sni = sni,
            insecure = insecure
        )
    }

    private fun parseVmess(uri: String): VpnProxy.Vmess? {
        val b64Part = uri.substring(8).trim()
        val decoded = try {
            val decodedBytes = Base64.decode(b64Part, Base64.DEFAULT)
            var cleanString = String(decodedBytes, Charsets.UTF_8).trim()
            // Strip non-json junk if any
            if (cleanString.contains("{") && cleanString.contains("}")) {
                cleanString = cleanString.substring(cleanString.indexOf("{"), cleanString.lastIndexOf("}") + 1)
            }
            cleanString
        } catch (e: Exception) {
            return null
        }

        return try {
            val json = org.json.JSONObject(decoded)
            val name = json.optString("ps", "VMess Proxy")
            val server = json.optString("add", "127.0.0.1")
            val port = json.optInt("port", 443)
            val uuid = json.optString("id", "")
            val aid = json.optInt("aid", 0)
            val scy = json.optString("scy", "auto")
            val net = json.optString("net", "tcp")
            val tlsStr = json.optString("tls", "")
            val isTls = tlsStr.equals("tls", ignoreCase = true) || tlsStr.equals("1") || json.optBoolean("tls", false)
            val sni = json.optString("sni", "")

            VpnProxy.Vmess(
                name = name,
                server = server,
                port = port,
                uuid = uuid,
                alterId = aid,
                cipher = scy,
                network = net,
                tls = isTls,
                sni = if (sni.isNotBlank()) sni else null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun String?.toFlexibleBoolean(default: Boolean = false): Boolean {
        if (this == null) return default
        val clean = this.lowercase().trim()
        return clean == "true" || clean == "1" || (clean == "" && default)
    }

    private fun createProxyFromType(name: String, type: String, server: String, port: Int, keys: Map<String, String>): VpnProxy? {
        return when (type.lowercase()) {
            "vless" -> VpnProxy.VlessReality(
                name = name,
                server = server,
                port = port,
                uuid = keys["uuid"] ?: keys["password"] ?: UUID.randomUUID().toString(),
                publicKey = keys["public-key"] ?: keys["pbk"] ?: "",
                serverName = keys["servername"] ?: keys["sni"] ?: server,
                shortId = keys["short-id"] ?: keys["sid"] ?: "",
                flow = keys["flow"] ?: "xtls-rprx-vision"
            )
            "hysteria2", "hysteria" -> VpnProxy.Hysteria2(
                name = name,
                server = server,
                port = port,
                auth = keys["auth"] ?: keys["password"] ?: "",
                sni = keys["sni"] ?: keys["servername"] ?: server,
                insecure = keys["insecure"]?.toFlexibleBoolean(true) ?: true,
                obsolete = type.lowercase() == "hysteria"
            )
            "wireguard" -> VpnProxy.WireGuard(
                name = name,
                server = server,
                port = port,
                privateKey = keys["private-key"] ?: "dummy",
                publicKey = keys["public-key"] ?: "dummy",
                dns = keys["dns"] ?: "1.1.1.1",
                mtu = keys["mtu"]?.toIntOrNull() ?: 1420
            )
            "ss", "shadowsocks" -> VpnProxy.Shadowsocks(
                name = name,
                server = server,
                port = port,
                cipher = keys["cipher"] ?: "2022-blake3-aes-128-gcm",
                password = keys["password"] ?: "dummy"
            )
            "socks5", "socks" -> VpnProxy.Socks5(
                name = name,
                server = server,
                port = port,
                username = keys["username"],
                password = keys["password"]
            )
            "trojan" -> VpnProxy.Trojan(
                name = name,
                server = server,
                port = port,
                password = keys["password"] ?: keys["auth"] ?: "dummy",
                sni = keys["sni"] ?: keys["servername"] ?: server,
                insecure = keys["insecure"]?.toFlexibleBoolean(true) ?: true
            )
            "vmess" -> VpnProxy.Vmess(
                name = name,
                server = server,
                port = port,
                uuid = keys["uuid"] ?: keys["id"] ?: UUID.randomUUID().toString(),
                alterId = keys["alterid"]?.toIntOrNull() ?: keys["aid"]?.toIntOrNull() ?: 0,
                cipher = keys["cipher"] ?: keys["scy"] ?: "auto",
                network = keys["network"] ?: keys["net"] ?: "tcp",
                tls = keys["tls"]?.toFlexibleBoolean(false) ?: false,
                sni = keys["sni"] ?: keys["servername"]
            )
            else -> null
        }
    }

    /**
     * Fallback Clash YAML Parser. Uses regular expression to parse list of proxies.
     * We look for the main objects inside YAML proxies arrays:
     * - name: ...
     *   type: vless/hysteria2/ss/wireguard/socks5
     *   server: ...
     *   port: ...
     */
    private fun parseClashYaml(yaml: String): List<VpnProxy> {
        val proxies = mutableListOf<VpnProxy>()
        try {
            val lines = yaml.split("\n")
            var inProxiesBlock = false
            val currentProxyLines = mutableListOf<String>()

            fun flushProxy() {
                if (currentProxyLines.isEmpty()) return
                val keys = mutableMapOf<String, String>()
                var name = ""
                var type = ""
                var server = ""
                var port = 0

                for (l in currentProxyLines) {
                    val line = l.trim()
                    if (line.isEmpty()) continue

                    // Handle inline/flow format like: - {name: X, type: Y...}
                    if (line.startsWith("-") && line.contains("{") && line.contains("}")) {
                        val content = line.substring(line.indexOf("{") + 1, line.lastIndexOf("}")).trim()
                        val parts = content.split(",")
                        for (part in parts) {
                            val kv = part.split(":")
                            if (kv.size >= 2) {
                                val k = kv[0].trim().trim('"', '\'')
                                val v = kv.subList(1, kv.size).joinToString(":").trim().trim(' ', '"', '\'')
                                keys[k] = v
                                when (k.lowercase()) {
                                    "name" -> name = v
                                    "type" -> type = v
                                    "server" -> server = v
                                    "port" -> port = v.toIntOrNull() ?: 0
                                }
                            }
                        }
                        continue
                    }

                    // Handle regular block format
                    val cleanLine = if (line.startsWith("-")) line.substring(1).trim() else line
                    val colonIndex = cleanLine.indexOf(":")
                    if (colonIndex != -1) {
                        val k = cleanLine.substring(0, colonIndex).trim().trim('"', '\'')
                        val v = cleanLine.substring(colonIndex + 1).trim().trim(' ', '"', '\'')
                        keys[k] = v
                        when (k.lowercase()) {
                            "name" -> name = v
                            "type" -> type = v
                            "server" -> server = v
                            "port" -> port = v.toIntOrNull() ?: 0
                        }
                    }
                }

                if (name.isNotEmpty() && server.isNotEmpty() && port > 0) {
                    val p = createProxyFromType(name, type, server, port, keys)
                    if (p != null) {
                        proxies.add(p)
                    }
                }
                currentProxyLines.clear()
            }

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("proxies:") || trimmed == "proxies") {
                    inProxiesBlock = true
                    continue
                }
                // If we hit another root level key (ends with ":" without leading dash), exit proxies block
                if (inProxiesBlock && line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("-") && trimmed.contains(":")) {
                    flushProxy()
                    inProxiesBlock = false
                }

                if (inProxiesBlock) {
                    if (trimmed.startsWith("-")) {
                        flushProxy() // Flush previous proxy
                        currentProxyLines.add(trimmed)
                    } else if (line.startsWith(" ") || line.startsWith("\t")) {
                        currentProxyLines.add(trimmed)
                    }
                }
            }
            flushProxy() // Flush final
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return proxies
    }
}
