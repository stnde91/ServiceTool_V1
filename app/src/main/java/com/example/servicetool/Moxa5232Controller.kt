package com.example.servicetool

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Controller für Moxa NPort 5232 Device Server
 * Unterstützt Baudrate-Änderung und Neustart über Web-Interface
 */
class Moxa5232Controller(
    private val moxaIpAddress: String,
    private val username: String = "admin",
    private val password: String = "moxa"
) {

    companion object {
        private const val TAG = "Moxa5232Controller"
        private const val TIMEOUT_MS = 10000

        // Verfügbare Baudraten für NPort 5232
        val SUPPORTED_BAUDRATES = listOf(
            1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200
        )
    }

    private fun getAuthString(): String {
        val credentials = "$username:$password"
        return Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    /**
     * Ändert die Baudrate für einen spezifischen Port
     * @param port Port-Nummer (1 oder 2 für NPort 5232)
     * @param baudrate Neue Baudrate
     * @return true bei Erfolg, false bei Fehler
     */
    suspend fun setBaudrate(port: Int, baudrate: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Ändere Baudrate für Port $port auf $baudrate")

                if (port !in 1..2) {
                    Log.e(TAG, "Ungültiger Port: $port (nur 1-2 erlaubt)")
                    return@withContext false
                }

                if (baudrate !in SUPPORTED_BAUDRATES) {
                    Log.e(TAG, "Nicht unterstützte Baudrate: $baudrate")
                    return@withContext false
                }

                // Schritt 1: Aktuelle Konfiguration abrufen
                val sessionId = login()
                if (sessionId.isEmpty()) {
                    Log.e(TAG, "Login fehlgeschlagen")
                    return@withContext false
                }

                // Schritt 2: Baudrate setzen
                val baudrateSet = setBaudrateInternal(sessionId, port, baudrate)
                if (!baudrateSet) {
                    Log.e(TAG, "Baudrate-Änderung fehlgeschlagen")
                    return@withContext false
                }

                // Schritt 3: Konfiguration speichern
                val saved = saveConfiguration(sessionId)
                if (!saved) {
                    Log.e(TAG, "Konfiguration speichern fehlgeschlagen")
                    return@withContext false
                }

                Log.i(TAG, "Baudrate für Port $port erfolgreich auf $baudrate geändert")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Setzen der Baudrate: ${e.message}", e)
                return@withContext false
            }
        }
    }

    /**
     * Startet die Moxa 5232 neu
     * @return true bei Erfolg, false bei Fehler
     */
    suspend fun restartDevice(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starte Moxa 5232 neu")

                val sessionId = login()
                if (sessionId.isEmpty()) {
                    Log.e(TAG, "Login für Neustart fehlgeschlagen")
                    return@withContext false
                }

                val restarted = restartInternal(sessionId)
                if (restarted) {
                    Log.i(TAG, "Moxa 5232 Neustart eingeleitet")
                } else {
                    Log.e(TAG, "Neustart fehlgeschlagen")
                }

                return@withContext restarted

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Neustart: ${e.message}", e)
                return@withContext false
            }
        }
    }

    /**
     * Ruft aktuelle Port-Konfiguration ab
     * @param port Port-Nummer (1 oder 2)
     * @return PortConfiguration oder null bei Fehler
     */
    suspend fun getPortConfiguration(port: Int): PortConfiguration? {
        return withContext(Dispatchers.IO) {
            try {
                val sessionId = login()
                if (sessionId.isEmpty()) return@withContext null

                val config = getPortConfigInternal(sessionId, port)
                Log.d(TAG, "Port $port Konfiguration: $config")
                config

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Abrufen der Port-Konfiguration: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Testet die Verbindung zur Moxa
     * @return true wenn erreichbar, false sonst
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$moxaIpAddress/")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                connection.disconnect()

                val isReachable = responseCode in 200..299 || responseCode == 401
                Log.d(TAG, "Verbindungstest: ${if (isReachable) "erfolgreich" else "fehlgeschlagen"} (Code: $responseCode)")
                isReachable

            } catch (e: Exception) {
                Log.e(TAG, "Verbindungstest fehlgeschlagen: ${e.message}", e)
                false
            }
        }
    }

    // Private Helper-Methoden

    private fun login(): String {
        try {
            val url = URL("http://$moxaIpAddress/forms/web_userlogin")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val postData = "Username=$username&Password=$password&Submit=Login"

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val cookies = connection.headerFields["Set-Cookie"]

            connection.disconnect()

            if (responseCode == 302 && cookies != null) {
                // Extrahiere Session-ID aus Cookies
                return cookies.firstOrNull { it.contains("JSESSIONID") }
                    ?.substringAfter("JSESSIONID=")
                    ?.substringBefore(";") ?: ""
            }

            return ""
        } catch (e: Exception) {
            Log.e(TAG, "Login-Fehler: ${e.message}", e)
            return ""
        }
    }

    private fun setBaudrateInternal(sessionId: String, port: Int, baudrate: Int): Boolean {
        try {
            val url = URL("http://$moxaIpAddress/forms/serial_port$port")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")

            val baudrateIndex = SUPPORTED_BAUDRATES.indexOf(baudrate)
            val postData = buildString {
                append("PortEnable=1&")
                append("BaudRate=$baudrateIndex&")  // Index in der Baudrate-Liste
                append("DataBits=3&")              // 8 Databits (Index 3)
                append("StopBits=0&")              // 1 Stopbit (Index 0)
                append("Parity=0&")                // No Parity (Index 0)
                append("FlowControl=0&")           // None (Index 0)
                append("FIFOEnable=1&")
                append("Submit=Apply")
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            return responseCode in 200..299

        } catch (e: Exception) {
            Log.e(TAG, "Baudrate setzen fehlgeschlagen: ${e.message}", e)
            return false
        }
    }

    private fun saveConfiguration(sessionId: String): Boolean {
        try {
            val url = URL("http://$moxaIpAddress/forms/save_config")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write("Submit=Save+Configuration")
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            return responseCode in 200..299

        } catch (e: Exception) {
            Log.e(TAG, "Konfiguration speichern fehlgeschlagen: ${e.message}", e)
            return false
        }
    }

    private fun restartInternal(sessionId: String): Boolean {
        try {
            val url = URL("http://$moxaIpAddress/forms/restart")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write("Submit=Restart")
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            return responseCode in 200..299

        } catch (e: Exception) {
            Log.e(TAG, "Neustart fehlgeschlagen: ${e.message}", e)
            return false
        }
    }

    private fun getPortConfigInternal(sessionId: String, port: Int): PortConfiguration? {
        try {
            val url = URL("http://$moxaIpAddress/main/serial_port$port.htm")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            connection.disconnect()

            // Parse HTML response für aktuelle Konfiguration
            return parsePortConfiguration(response)

        } catch (e: Exception) {
            Log.e(TAG, "Port-Konfiguration abrufen fehlgeschlagen: ${e.message}", e)
            return null
        }
    }

    private fun parsePortConfiguration(html: String): PortConfiguration {
        // Vereinfachtes HTML-Parsing für Baudrate-Extraktion
        val baudratePattern = Regex("selected.*?>(\\d+)</option")
        val baudrateMatch = baudratePattern.find(html)
        val baudrate = baudrateMatch?.groupValues?.get(1)?.toIntOrNull() ?: 9600

        return PortConfiguration(
            baudrate = baudrate,
            dataBits = 8,
            stopBits = 1,
            parity = "None",
            flowControl = "None"
        )
    }

    /**
     * Datenklasse für Port-Konfiguration
     */
    data class PortConfiguration(
        val baudrate: Int,
        val dataBits: Int,
        val stopBits: Int,
        val parity: String,
        val flowControl: String
    )
}