package com.example.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.example.MainActivity
import com.example.vpn.core.MihomoCore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android VpnService, управляющий системным TUN-интерфейсом и запуском ядра Mihomo.
 */
class MihomoVpnService : VpnService() {

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    companion object {
        private const val TAG = "MihomoVpnService"
        private const val CHANNEL_ID = "mihomo_vpn_channel"
        private const val NOTIFICATION_ID = 4040

        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_CONFIG = "com.example.vpn.EXTRA_CONFIG"

        private val _vpnStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val vpnStatus: StateFlow<ConnectionStatus> = _vpnStatus.asStateFlow()

        // Вспомогательный метод для проверки запуска сервиса
        fun startVpn(context: Context, configContent: String) {
            val intent = Intent(context, MihomoVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_CONFIG, configContent)
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN service", e)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, MihomoVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnInputStream: java.io.FileInputStream? = null
    private var job: Job? = null
    private var tunnelReadJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        tunnelReadJob?.cancel()
        serviceScope.cancel()
        closeTunnel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            disconnectVpn()
            return START_NOT_STICKY
        } else if (action == ACTION_CONNECT) {
            val config = intent.getStringExtra(EXTRA_CONFIG) ?: ""
            connectVpn(config)
            return START_STICKY
        }
        return START_NOT_STICKY
    }

    private fun connectVpn(configContent: String) {
        if (_vpnStatus.value == ConnectionStatus.CONNECTED || _vpnStatus.value == ConnectionStatus.CONNECTING) {
            return
        }

        _vpnStatus.value = ConnectionStatus.CONNECTING
        showNotification("Установка соединения...")

        job = serviceScope.launch {
            try {
                // 1. Поднимаем системный TUN-интерфейс ПЕРЕД запуском ядра
                // (обычно ядру нужен готовый FD интерфейса, либо ядро подхватит его само)
                Log.d(TAG, "Establishing TUN interface...")
                establishTunnel()

                val pfd = vpnInterface
                if (pfd == null) {
                    throw Exception("VPN Builder established null TUN interface. Check permissions.")
                }

                // 2. Инициализируем и запускаем ядро Mihomo
                Log.d(TAG, "Starting Mihomo Core with TUN FD: ${pfd.fd}...")
                // Инжектируем fd в секцию tun (для подхвата интерфейса ядром без root)
                val finalConfig = configContent.replace(
                    "tun:",
                    "tun:\n  fd: ${pfd.fd}"
                )
                MihomoCore.start(finalConfig, filesDir)

                startTunnelReadLoop(pfd)

                // Стейт в UI обновляется ТОЛЬКО после успешного старта ядра и туннеля
                _vpnStatus.value = ConnectionStatus.CONNECTED
                showNotification("Защищено в сети Mihomo VPN")
                Log.d(TAG, "VPN Connected successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect VPN", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        applicationContext,
                        "Ошибка запуска VPN: " + e.localizedMessage,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                disconnectVpn()
            }
        }
    }

    private fun disconnectVpn() {
        if (_vpnStatus.value == ConnectionStatus.DISCONNECTED || _vpnStatus.value == ConnectionStatus.DISCONNECTING) {
            return
        }

        _vpnStatus.value = ConnectionStatus.DISCONNECTING
        serviceScope.launch {
            cleanup()
        }
    }

    private fun cleanup() {
        Log.d(TAG, "Stopping VPN and closing tunnel...")
        try { vpnInputStream?.close() } catch(e: Exception){}
        tunnelReadJob?.cancel()
        tunnelReadJob = null
        MihomoCore.stop()
        closeTunnel()
        _vpnStatus.value = ConnectionStatus.DISCONNECTED
        try { stopForeground(true) } catch(e: Exception){}
        stopSelf()
    }

    private fun startTunnelReadLoop(pfd: ParcelFileDescriptor) {
        tunnelReadJob?.cancel()
        tunnelReadJob = serviceScope.launch(Dispatchers.IO) {
            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
            vpnInputStream = inputStream
            val buffer = ByteArray(32767)
            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length <= 0) {
                        delay(10)
                        continue
                    }
                    // Вакуумируем трафик в никуда
                }
            } catch (e: Exception) {
                Log.d(TAG, "Tunnel read finished, closed, or cancelled: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    private fun establishTunnel() {
        try {
            val builder = Builder()

            // 1. Задаем сетевые параметры IPv4 и IPv6 (fake-ip подсеть)
            builder.addAddress("198.18.0.1", 16)
            builder.addAddress("fdfe:dcba:9876::1", 126)

            // Перехватываем весь исходящий трафик (IPv4 + IPv6)
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)

            // 2. Указываем системные DNS серверы (шлюз)
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")

            // 3. Оптимальный MTU и настройки
            builder.setMtu(9000)
            builder.setSession("Mihomo Core Tunnel")
            builder.setBlocking(true)

            // 4. Позволяем приложению обходить собственный VPN для избежания петель
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.d(TAG, "Cannot restrict own package: ${e.message}")
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Builder established a null TUN interface.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to establish system TUN interface", e)
            throw e
        }
    }

    private fun closeTunnel() {
        try {
            vpnInputStream?.close()
        } catch (e: Exception){}
        vpnInputStream = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tunnel desc", e)
        } finally {
            vpnInterface = null
        }
    }

    private fun showNotification(contentText: String) {
        createNotificationChannel()

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val mainPendingIntent = PendingIntent.getActivity(this, 12, mainActivityIntent, pendingIntentFlags)

        val disconnectIntent = Intent(this, MihomoVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(this, 34, disconnectIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share) // Стандартная иконка, позже заменим
            .setContentTitle("Mihomo VPN")
            .setContentText(contentText)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отключить",
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal failure in startForeground of MihomoVpnService", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Mihomo Connection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
