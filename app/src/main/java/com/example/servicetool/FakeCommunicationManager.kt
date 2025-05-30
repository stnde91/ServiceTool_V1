package com.example.servicetool

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.random.Random

class FakeCommunicationManager {

    private var isConnected = false

    suspend fun connect(ipAddress: String, port: Int): Boolean {
        Log.d("FakeCommunicationManager", "DEMO: Verbinde mit $ipAddress:$port")
        delay(500)
        isConnected = true
        Log.d("FakeCommunicationManager", "DEMO: Erfolgreich verbunden.")
        return true
    }

    suspend fun sendCommand(command: String): String? {
        if (!isConnected) {
            return null
        }
        Log.d("FakeCommunicationManager", "DEMO: Sende Befehl: $command")
        delay(150)

        val commandIdentifier = command.drop(3).firstOrNull()

        val response = when (commandIdentifier) {
            '?' -> "C${Random.nextInt(10000, 50000)}"
            't' -> "T${Random.nextDouble(20.0, 25.0).toString().take(5)}"
            'p' -> "P000004"
            'v' -> "V1.23-beta"
            'c' -> "SN-DEMO-12345678"
            'P' -> commandIdentifier.toString()
            else -> "E01"
        }
        Log.d("FakeCommunicationManager", "DEMO: Sende gefälschte Antwort: $response")
        return response
    }

    suspend fun disconnect() {
        Log.d("FakeCommunicationManager", "DEMO: Verbindung getrennt.")
        delay(200)
        isConnected = false
    }

    fun isConnected(): Boolean {
        return isConnected
    }
}