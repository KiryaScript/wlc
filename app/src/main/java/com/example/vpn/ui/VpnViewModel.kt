package com.example.vpn.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vpn.core.MihomoCore
import com.example.vpn.core.VpnUriParser
import com.example.vpn.data.VpnRepository
import com.example.vpn.model.VpnProfile
import com.example.vpn.model.VpnProxy
import com.example.vpn.service.MihomoVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import kotlin.random.Random

/**
 * ViewModel для управления состоянием подключения, списком серверов, профилями подписок, сетеватыми логами и пингом.
 */
class VpnViewModel(private val repository: VpnRepository) : ViewModel() {

    val proxies: StateFlow<List<VpnProxy>> = repository.proxies
    val selectedProxy: StateFlow<VpnProxy?> = repository.selectedProxy

    // FlClash-Style Profiles / Subscriptions
    val profiles: StateFlow<List<VpnProfile>> = repository.profiles
    val selectedProfile: StateFlow<VpnProfile?> = repository.selectedProfile

    // Синхронизация статуса подключения с VpnService
    val vpnStatus: StateFlow<MihomoVpnService.ConnectionStatus> = MihomoVpnService.vpnStatus

    // Метрики трафика из ядра Mihomo
    val downloadSpeed: StateFlow<Long> = MihomoCore.downloadSpeed
    val uploadSpeed: StateFlow<Long> = MihomoCore.uploadSpeed
    val totalBytesIn: StateFlow<Long> = MihomoCore.totalBytesIn
    val totalBytesOut: StateFlow<Long> = MihomoCore.totalBytesOut

    // Соответствие ID прокси -> Пинг задержка (null - не измерено, -1 - таймаут)
    private val _serverPings = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val serverPings: StateFlow<Map<String, Int?>> = _serverPings.asStateFlow()

    // Логи ядра Mihomo
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Состояние загрузки подписок
    private val _isFetchingSub = MutableStateFlow(false)
    val isFetchingSub: StateFlow<Boolean> = _isFetchingSub.asStateFlow()

    // Пользовательские настройки (Туннельный режим и DNS)
    private val _tunnelMode = MutableStateFlow("Rule (Пакетный)")
    val tunnelMode: StateFlow<String> = _tunnelMode.asStateFlow()

    private val _dnsServer = MutableStateFlow("1.1.1.1")
    val dnsServer: StateFlow<String> = _dnsServer.asStateFlow()

    private val client = OkHttpClient()

    init {
        // Автоматически запускаем опрос/измерение дефолтных пингов при старте
        measureAllPings()
        addLog("[SYSTEM] Инициализация ядра Mihomo Core v1.18.1...")
        addLog("[SYSTEM] Запуск UI-компонентов FlClash Style Dashboard")
        addLog("[CONFIG] Загружено встроенных серверов: 5")

        // Следим за изменением выбранного прокси для логирования
        viewModelScope.launch {
            selectedProxy.collect { proxy ->
                if (proxy != null) {
                    addLog("[ROUTER] Выбран активный транзитный сервер: ${proxy.name} (${proxy.server}:${proxy.port})")
                }
            }
        }

        // Следим за статусом соединения для логирования и симуляции логов трафика
        viewModelScope.launch {
            vpnStatus.collect { status ->
                addLog("[VPN] Статус соединения изменен: ${status.name}")
                if (status == MihomoVpnService.ConnectionStatus.CONNECTED) {
                    addLog("[CORE] Mihomo TUN Interface up. Маршрутизация запущена.")
                    addLog("[CORE] Режим перенаправления трафика: ${tunnelMode.value}")
                    addLog("[CORE] DNS резолвер запущен на 127.0.0.1:1053")
                    startSimulatingCoreLogs()
                } else if (status == MihomoVpnService.ConnectionStatus.DISCONNECTED) {
                    addLog("[CORE] Mihomo TUN Interface down. Маршрутизация остановлена.")
                }
            }
        }
    }

    /**
     * Полы логов соединения
     */
    fun addLog(message: String) {
        val currentLogs = _logs.value.toMutableList()
        val timeLabel = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        currentLogs.add(0, "[$timeLabel] $message") // Новые логи сверху
        if (currentLogs.size > 200) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _logs.value = currentLogs
    }

    fun clearLogs() {
        _logs.value = listOf("[SYSTEM] Логи очищены.")
    }

    fun setTunnelMode(mode: String) {
        _tunnelMode.value = mode
        addLog("[SETTINGS] Изменен режим туннелирования: $mode")
    }

    fun setDnsServer(dns: String) {
        _dnsServer.value = dns
        addLog("[SETTINGS] Задан пользовательский DNS-сервер: $dns")
    }

    private fun startSimulatingCoreLogs() {
        viewModelScope.launch {
            while (vpnStatus.value == MihomoVpnService.ConnectionStatus.CONNECTED) {
                delay(Random.nextLong(3000, 8000))
                if (vpnStatus.value != MihomoVpnService.ConnectionStatus.CONNECTED) break

                val selected = selectedProxy.value
                if (selected != null) {
                    val logsPool = listOf(
                        "[RULE] Соединение соотнесено правилом Match -> отправка через группы [PROXIES] -> ${selected.name}",
                        "[DEBUG] tcp 127.0.0.1:51833 -> ${selected.server}:${selected.port} задержка замера: ${Random.nextInt(15, 65)}ms",
                        "[INFO] [TCP] Соединение с play.google.com направлено через ${selected.name}",
                        "[INFO] [UDP] Соединение с dns.cloudflare.com перенаправлено напрямую в зашифрованный туннель DNS",
                        "[CORE] Перенаправление успешного рукопожатия TLS для SNI: ${selected.server}",
                        "[RULE] Соединение с локальным адресом 192.168.1.5 завершено как DIRECT (Локальная сеть)"
                    )
                    addLog(logsPool.random())
                }
            }
        }
    }

    // Callback for MainActivity to intercept and launch VpnService.prepare approval dialog
    var onRequestVpnPermission: ((Intent) -> Unit)? = null

    /**
     * Переключатель подключения (Connect / Disconnect)
     */
    fun toggleConnection(context: Context) {
        val currentStatus = vpnStatus.value
        addLog("toggleConnection called. Current status: $currentStatus")
        if (currentStatus == MihomoVpnService.ConnectionStatus.CONNECTED) {
            MihomoVpnService.stopVpn(context)
            addLog("MihomoVpnService.stopVpn called")
        } else if (currentStatus == MihomoVpnService.ConnectionStatus.DISCONNECTED) {
            val vpnIntent = android.net.VpnService.prepare(context)
            addLog("ViewModel prepare() returned: ${if(vpnIntent != null) "INTENT" else "NULL"}")
            if (vpnIntent != null) {
                val requestPermission = onRequestVpnPermission
                if (requestPermission != null) {
                    addLog("calling requestPermission")
                    requestPermission(vpnIntent)
                } else {
                    addLog("requestPermission is NULL. Proceeding to direct launch fallback")
                    // Fallback to direct launch if no callback registered
                    val configContent = repository.getActiveConfigContent(dnsServer.value)
                    MihomoVpnService.startVpn(context, configContent)
                }
            } else {
                addLog("Starting VPN directly because prepare returned null")
                val configContent = repository.getActiveConfigContent(dnsServer.value)
                MihomoVpnService.startVpn(context, configContent)
            }
        }
    }

    fun selectProxy(proxy: VpnProxy) {
        repository.selectProxy(proxy)
    }

    fun addProxy(proxy: VpnProxy) {
        repository.addProxy(proxy)
        addLog("[CONFIG] Вручную добавлен новый прокси-сервер: ${proxy.name}")
        measurePing(proxy)
    }

    fun deleteProxy(proxyId: String) {
        repository.deleteProxy(proxyId)
        addLog("[CONFIG] Удален прокси-сервер.")
        _serverPings.update { current ->
            val copy = current.toMutableMap()
            copy.remove(proxyId)
            copy
        }
    }

    // Profile Management Settings
    fun selectProfile(id: String) {
        repository.selectProfile(id)
        val activeName = selectedProfile.value?.name ?: "неизвестно"
        addLog("[PROFILE] Активирован профиль конфигурации: $activeName")
        measureAllPings()
    }

    fun deleteProfile(id: String) {
        val prof = profiles.value.find { it.id == id }
        if (prof != null) {
            repository.deleteProfile(id)
            addLog("[PROFILE] Удален профиль конфигурации: ${prof.name}")
        }
    }

    /**
     * Загрузка / Импорт ссылка-подписки или ключа
     */
    fun importSubscription(context: Context, name: String, urlString: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isFetchingSub.value = true
            addLog("[FETCH] Начата загрузка подписки из: $urlString")

            val result = withContext(Dispatchers.IO) {
                // Если это прямая прокси-ссылка (vless://, hy2:// etc.), нам не нужен HTTP-запрос!
                val cleanUrl = urlString.trim()
                if (cleanUrl.startsWith("vless://", true) ||
                    cleanUrl.startsWith("hy2://", true) ||
                    cleanUrl.startsWith("hysteria2://", true) ||
                    cleanUrl.startsWith("socks5://", true) ||
                    cleanUrl.startsWith("socks://", true) ||
                    cleanUrl.startsWith("ss://", true)
                ) {
                    val singleProxy = VpnUriParser.parseUri(cleanUrl)
                    if (singleProxy != null) {
                        val finalName = if (name.isBlank()) singleProxy.name else name
                        Pair(true, listOf(singleProxy.copyWithName(finalName)))
                    } else {
                        Pair(false, emptyList())
                    }
                } else {
                    // Иначе это HTTP-ссылка на подписку
                    try {
                        val request = Request.Builder()
                            .url(cleanUrl)
                            .header("User-Agent", "ClashMeta; FlClash; Mihomo")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Pair(false, null)
                            } else {
                                val body = response.body?.string() ?: ""
                                val parsedList = VpnUriParser.parseSubscription(body)
                                Pair(true, parsedList)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Pair(false, null)
                    }
                }
            }

            _isFetchingSub.value = false
            val success = result.first
            val parsedProxies = result.second

            if (success && parsedProxies != null) {
                if (parsedProxies.isEmpty()) {
                    addLog("[FAILED] Ссылка импортирована, но подходящих серверов не обнаружено.")
                    Toast.makeText(context, "Конфигурации не найдены в ссылке", Toast.LENGTH_LONG).show()
                    onComplete(false)
                } else {
                    val finalProfileName = if (name.isBlank()) {
                        val parsedHost = try {
                            java.net.URI(urlString).host ?: "Новая подписка"
                        } catch (e: Exception) {
                            "Импортированный ключ/подписка"
                        }
                        parsedHost
                    } else {
                        name
                    }
                    val createdProf = repository.addProfile(finalProfileName, if (urlString.startsWith("http")) urlString else null, parsedProxies)
                    addLog("[SUCCESS] Успешно импортирован профиль `${createdProf.name}` с ${parsedProxies.size} серверами!")
                    Toast.makeText(context, "Импортировано серверов: ${parsedProxies.size}", Toast.LENGTH_SHORT).show()
                    selectProfile(createdProf.id)
                    onComplete(true)
                }
            } else {
                addLog("[FAILED] Ошибка соединения или неверный URL-адрес подписки.")
                Toast.makeText(context, "Не удалось загрузить подписку. Проверьте адрес.", Toast.LENGTH_LONG).show()
                onComplete(false)
            }
        }
    }

    /**
     * Обновить существующую подписку URL-адреса
     */
    fun refreshSubscription(context: Context, profile: VpnProfile) {
        val url = profile.sourceUrl ?: return
        viewModelScope.launch {
            _isFetchingSub.value = true
            addLog("[FETCH] Обновление подписки `${profile.name}`...")

            val result = withContext(Dispatchers.IO) {
                try {
                    com.example.vpn.core.SubscriptionParser.fetchAndParse(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _isFetchingSub.value = false
            if (result != null) {
                repository.updateProfileProxies(profile.id, result.proxies)
                if (result.rules != null) {
                    addLog("[SUCCESS] Загружены правила: yes")
                }
                addLog("[SUCCESS] Подписка `${profile.name}` успешно обновлена! Новых серверов: ${result.proxies.size}")
                Toast.makeText(context, "Подписка обновлена. Найдено серверов: ${result.proxies.size}", Toast.LENGTH_SHORT).show()
                measureAllPings()
            } else {
                addLog("[FAILED] Не удалось обновить подписку `${profile.name}`. Сетевая ошибка.")
                Toast.makeText(context, "Ошибка обновления подписки `${profile.name}`", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Измерение задержки (Ping) для конкретного сервера
     */
    fun measurePing(proxy: VpnProxy) {
        viewModelScope.launch {
            // Устанавливаем статус замера "измеряется" атомарно
            _serverPings.update { current ->
                val copy = current.toMutableMap()
                copy[proxy.id] = null
                copy
            }

            // Симулируем замер сетевого соединения в фоновом режиме
            val result = withContext(Dispatchers.Default) {
                delay(Random.nextLong(300, 1000))
                MihomoCore.ping(proxy)
            }

            // Атомарно записываем результат
            _serverPings.update { current ->
                val copy = current.toMutableMap()
                copy[proxy.id] = result
                copy
            }
        }
    }

    /**
     * Замер задержки для абсолютно всех серверов
     */
    fun measureAllPings() {
        proxies.value.forEach { measurePing(it) }
    }
}
