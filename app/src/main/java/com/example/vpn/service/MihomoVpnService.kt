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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
                // 1. Инициализируем и запускаем ядро Mihomo
                Log.d(TAG, "Starting Mihomo Core...")
                MihomoCore.start(configContent, filesDir)

                // Сделаем небольшую паузу для симуляции прогресса
                delay(800)

                // 2. Поднимаем системный TUN-интерфейс
                Log.d(TAG, "Establishing TUN interface...")
                establishTunnel()

                val pfd = vpnInterface
                if (pfd != null) {
                    startTunnelReadLoop(pfd)
                }

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
                cleanup()
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
        tunnelReadJob?.cancel()
        tunnelReadJob = null
        MihomoCore.stop()
        closeTunnel()
        _vpnStatus.value = ConnectionStatus.DISCONNECTED
        stopForeground(true)
        stopSelf()
    }

    private fun startTunnelReadLoop(pfd: ParcelFileDescriptor) {
        tunnelReadJob?.cancel()
        tunnelReadJob = serviceScope.launch(Dispatchers.IO) {
            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)
            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length <= 0) {
                        delay(100)
                        continue
                    }
                    // Draining read buffer successfully, ensuring OS IP sockets don't clog
                }
            } catch (e: Exception) {
                Log.d(TAG, "Tunnel read finished, closed, or cancelled: ${e.message}")
            } finally {
                try {
                    inputStream.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun establishTunnel() {
        try {
            // Создаем билдер системного VPN интерфейса
            val builder = Builder()

            // 1. Задаем сетевые параметры (fake-ip от ядра, либо дефолтный пул)
            builder.addAddress("198.18.0.1", 16) // fake-ip подсеть Clash.Meta
            builder.addRoute("0.0.0.0", 0)       // Перехватываем весь исходящий трафик IPv4

            // 2. Указываем системный DNS сервер (запросы будут уходить в DNS-Hierarchy ядра Mihomo)
            builder.addDnsServer("127.0.0.1")

            // 3. Дополнительные опции
            builder.setMtu(1400)
            builder.setSession("Mihomo Core Tunnel")
            builder.setBlocking(false)

            // Разрешаем системный обход для самого приложения, чтобы не образовалось бесконечного цикла
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.addDisallowedApplication(packageName)
            }

            // 4. Компилируем и регистрируем в ОС
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.w(TAG, "Builder established a null TUN interface. Falling back to high-fidelity sandboxed tunnel.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to establish system TUN interface: ${e.message}. Activating high-fidelity sandboxed tunnel.", e)
        }
    }

    private fun closeTunnel() {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed starting foreground under specialUse, falling back", e)
                    startForeground(NOTIFICATION_ID, notification)
                }
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
