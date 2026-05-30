package com.example.vpn.core

import com.example.vpn.model.VpnProxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.random.Random

/**
 * Оболочка-синглтон ядра Mihomo (Clash.Meta).
 * Управляет запуском/остановкой фонового процесса, собирает метрики скорости и пинга.
 */
object MihomoCore {

    private val _isCoreRunning = MutableStateFlow(false)
    val isCoreRunning: StateFlow<Boolean> = _isCoreRunning.asStateFlow()

    private val _downloadSpeed = MutableStateFlow(0L) // байт/сек
    val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow(0L) // байт/сек
    val uploadSpeed: StateFlow<Long> = _uploadSpeed.asStateFlow()

    private val _totalBytesIn = MutableStateFlow(0L) // байты
    val totalBytesIn: StateFlow<Long> = _totalBytesIn.asStateFlow()

    private val _totalBytesOut = MutableStateFlow(0L) // байты
    val totalBytesOut: StateFlow<Long> = _totalBytesOut.asStateFlow()

    private var simulationJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Кэш пингов для серверов
    private val pingCache = mutableMapOf<String, Int>()

    fun start(configContent: String, appFilesDir: File) {
        if (_isCoreRunning.value) return

        // Имитируем сохранение конфигурационного файла для ядра
        try {
            val configFile = File(appFilesDir, "mihomo_config.yaml")
            configFile.writeText(configContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _isCoreRunning.value = true
        startSpeedSimulation()
    }

    fun stop() {
        if (!_isCoreRunning.value) return

        _isCoreRunning.value = false
        simulationJob?.cancel()
        simulationJob = null

        _downloadSpeed.value = 0L
        _uploadSpeed.value = 0L
    }

    /**
     * Симуляция измерения пинга до выбранного сервера.
     * Возвращает задержку в миллисекундах.
     */
    fun ping(proxy: VpnProxy): Int {
        val cached = pingCache[proxy.id]
        if (cached != null) return cached

        // Генерация пинга на основе IP или имени сервера для добавления реализма
        val basePing = when {
            proxy.server.contains("ru") || proxy.server.contains("su") -> Random.nextInt(15, 45)
            proxy.server.contains("eu") || proxy.server.contains("de") -> Random.nextInt(40, 85)
            proxy.server.contains("us") -> Random.nextInt(120, 210)
            else -> Random.nextInt(50, 150)
        }
        pingCache[proxy.id] = basePing
        return basePing
    }

    /**
     * Позволяет вручную сбросить/обновить пинг сервера
     */
    fun refreshPing(proxy: VpnProxy, onResult: (Int) -> Unit) {
        coroutineScope.launch {
            delay(Random.nextLong(300, 1200)) // имитируем сетевой запрос
            val newPing = Random.nextInt(20, 180)
            pingCache[proxy.id] = newPing
            withContext(Dispatchers.Main) {
                onResult(newPing)
            }
        }
    }

    private fun startSpeedSimulation() {
        simulationJob?.cancel()
        simulationJob = coroutineScope.launch {
            while (isActive && _isCoreRunning.value) {
                // Имитируем случайную активность сетевого трафика
                val isDownloading = Random.nextInt(0, 10) > 2 // 80% времени есть закачка
                val isUploading = Random.nextInt(0, 10) > 4 // 60% времени есть отдача

                val down = if (isDownloading) {
                    Random.nextLong(1024, 7 * 1024 * 1024) // от 1KB до 7MB в сек
                } else {
                    Random.nextLong(50, 500) // фоновые байты
                }

                val up = if (isUploading) {
                    Random.nextLong(512, 1200 * 1024) // от 512B до 1.2MB в сек
                } else {
                    Random.nextLong(20, 200)
                }

                _downloadSpeed.value = down
                _uploadSpeed.value = up

                _totalBytesIn.value += down
                _totalBytesOut.value += up

                delay(1000)
            }
        }
    }
}
