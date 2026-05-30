package com.example

import com.example.vpn.core.VpnUriParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testRealSubscriptions() {
        val client = OkHttpClient()
        val urls = listOf(
            "https://devikteam.online:2053/sub/vless/5081397186",
            "https://devikteam.online:2053/sub/5081397186"
        )
        for (url in urls) {
            println("=== Fetching: $url ===")
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    val content = response.body?.string() ?: ""
                    println("Response Code: ${response.code}")
                    println("Raw Length: ${content.length}")
                    val preview = if (content.length > 500) content.substring(0, 500) + "..." else content
                    println("Content Preview:\n$preview")
                    
                    // Parse with VpnUriParser
                    val proxies = VpnUriParser.parseSubscription(content)
                    println("Parsed ${proxies.size} proxies from $url:")
                    for (p in proxies) {
                        println("  - Proxy: ${p.javaClass.simpleName} / Name: ${p.name} / Server: ${p.server}:${p.port}")
                    }
                    if (proxies.isEmpty()) {
                        System.err.println("WARNING: Parsed 0 proxies!")
                    }
                }
            } catch (e: IOException) {
                println("Failed to fetch $url: ${e.message}")
            }
        }
    }
}

