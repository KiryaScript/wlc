package com.example.vpn.core

import com.example.vpn.model.VpnProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.util.Base64

class SubscriptionResult(
    val proxies: List<VpnProxy>,
    val rawConfig: String,
    val rules: String? = null,
    val ruleProviders: String? = null
)

object SubscriptionParser {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAndParse(url: String): SubscriptionResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP Error: ${response.code}")
        }
        
        val bodyText = response.body?.string() ?: throw Exception("Empty response body")
        
        // 1. Пытаемся понять, Base64 это или YAML (или просто URI-lines)
        val decodedBody = tryDecodeBase64(bodyText.trim()) ?: bodyText.trim()
        
        // 2. Используем существующий механизм парсинга из VpnUriParser
        val proxies = VpnUriParser.parseSubscription(decodedBody)
        
        // 3. Извлекаем блоки rules и rule-providers для Hysteria2/Clash yaml файлов
        val rulesExtract = extractYamlBlock(decodedBody, "rules:")
        val ruleProvidersExtract = extractYamlBlock(decodedBody, "rule-providers:")

        SubscriptionResult(proxies, decodedBody, rulesExtract, ruleProvidersExtract)
    }

    private fun extractYamlBlock(yaml: String, blockKey: String): String? {
        if (!yaml.contains(blockKey)) return null
        val lines = yaml.split("\n")
        var inBlock = false
        val blockContent = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(blockKey) || trimmed == blockKey.dropLast(1)) {
                inBlock = true
                blockContent.appendLine(line)
                continue
            }
            if (inBlock) {
                if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("-") && trimmed.contains(":")) {
                    break // Вышли из блока
                }
                blockContent.appendLine(line)
            }
        }
        val result = blockContent.toString().trim()
        return if (result.isEmpty()) null else result
    }
    
    private fun tryDecodeBase64(str: String): String? {
        val clean = str.replace("\r", "").replace("\n", "").replace(" ", "").trim()
        if (clean.isEmpty() || clean.contains("proxies:") || clean.contains("://")) return null

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
                if (decodedStr.isNotEmpty() && (
                        decodedStr.contains("://") || 
                        decodedStr.contains("proxies:") || 
                        decodedStr.contains("rules:") || 
                        decodedStr.contains("rule-providers:")
                    )) {
                    return decodedStr
                }
            } catch (e: Exception) {
                // Ignore and try next
            }
        }
        
        // Normalized base64 fallback
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
}
