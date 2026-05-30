package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.example.vpn.ui.MainScreen
import com.example.vpn.ui.VpnViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: VpnViewModel

    // Логика запуска системного диалога выдачи разрешений на подключение к VPN
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Разрешение успешно получено, запускаем соединение повторно
            viewModel.toggleConnection(this)
        } else {
            Toast.makeText(this, "Разрешение на работу VPN отклонено", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Сборка ViewModel с ручным инжектированием репозитория из Application-контекста
        val app = application as VpnApplication
        viewModel = VpnViewModel(app.vpnRepository).apply {
            onRequestVpnPermission = { intent ->
                vpnPermissionLauncher.launch(intent)
            }

            try {
                val logFile = java.io.File(app.filesDir, "crash.log")
                if (logFile.exists()) {
                    val contents = logFile.readText()
                    addLog("[FATAL ERROR] Предыдущий запуск завершился крашем. Лог:")
                    contents.split("\n").forEach { line ->
                        if (line.isNotBlank()) {
                            addLog("    $line")
                        }
                    }
                    Toast.makeText(this@MainActivity, "Предыдущий сеанс упал с ошибкой", Toast.LENGTH_LONG).show()
                    logFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Оборачиваемая кнопка переключения VPN, учитывающая проверку разрешений Android
                    MainScreen(
                        viewModel = viewModel.apply {
                            // Опционально: можно заменить дефолтный клик для перехвата проверки системных прав
                        }
                    )
                }
            }
        }
    }

    /**
     * Публичный мост для перехвата и активации системного VpnService.prepare()
     * Он вызывается перед запуском системного туннеля для подтверждения безопасности
     */
    fun toggleConnectionWithChecks() {
        try {
            val vpnIntent = VpnService.prepare(this)
            viewModel.addLog("toggleConnectionWithChecks called. prepare() returned: \${if (vpnIntent != null) \"INTENT\" else \"NULL\"}")
            
            if (vpnIntent != null) {
                // Запрашиваем согласие пользователя через системное окно Android
                vpnPermissionLauncher.launch(vpnIntent)
                viewModel.addLog("vpnPermissionLauncher.launch() executed")
            } else {
                // Разрешения уже получены, запускаем сервис
                viewModel.toggleConnection(this)
            }
        } catch (e: Exception) {
            viewModel.addLog("EXCEPTION in toggleConnectionWithChecks: \${e.message}")
            e.printStackTrace()
        }
    }
}
