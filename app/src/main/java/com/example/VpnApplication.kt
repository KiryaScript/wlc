package com.example

import android.app.Application
import android.util.Log
import com.example.vpn.data.VpnRepository

/**
 * Базовый класс приложения. Предоставляет глобальный контейнер зависимостей.
 */
class VpnApplication : Application() {

    // Единственный инстанс репозитория для всего жизненного цикла
    lateinit var vpnRepository: VpnRepository
        private set

    override fun onCreate() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val stackTrace = sw.toString()
                
                Log.e("VpnApplication", "CRASH DETECTED on thread ${thread.name}:")
                Log.e("VpnApplication", stackTrace)
                
                val logFile = java.io.File(filesDir, "crash.log")
                logFile.writeText("Thread: ${thread.name}\nMessage: ${throwable.localizedMessage}\nStackTrace:\n$stackTrace")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate()
        vpnRepository = VpnRepository()
    }
}
