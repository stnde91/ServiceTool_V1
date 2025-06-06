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
import javax.net.ssl.*
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * Controller für Moxa NPort 5232 Device Server
 * Unterstützt Baudrate-Änderung und Neustart über Web-Interface
 * Korrigiert für Browser-kompatible Kommunikation
 */
class Moxa5232Controller(
    private val moxaIpAddress: String,
    private val username: String = "admin",
    private val password: String = "moxa"
) {

    companion object {
        private const val TAG = "Moxa5232Controller"
        private const val TIMEOUT_MS = 15000

        // Verfügbare Baudraten für NPort 5232
        val SUPPORTED_BAUDRATES = listOf(
            1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200
        )
    }

    /**
     * Testet die Verbindung zur Moxa mit Browser-kompatiblen Headers
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            val targetUrlStr = "http://$moxaIpAddress/"
            Log.d(TAG, "Attempting to connect to: $targetUrlStr")
            try {
                val url = URL(targetUrlStr)
                val connection = url.openConnection() as HttpURLConnection

                // Browser-ähnliche Headers setzen
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(connection)

                val responseCode = connection.responseCode
                Log.d(TAG, "Test Connection: HTTP Response Code = $responseCode")
                val contentType = connection.contentType
                Log.d(TAG, "Test Connection: Content-Type = $contentType")

                var content = ""
                var exceptionMessage: String? = null

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try {
                        content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        Log.d(TAG, "Test Connection: Received content (first 300 chars): ${content.take(300).replace("\n", " ")}")
                    } catch (e: Exception) {
                        exceptionMessage = e.message
                        Log.e(TAG, "Test Connection: Error reading response content: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "Test Connection: Non-OK response ($responseCode). Not attempting to read primary content.")
                    try {
                        val errorStreamContent = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "No error stream content"
                        Log.d(TAG, "Test Connection: Error stream content (first 300 chars): ${errorStreamContent.take(300).replace("\n", " ")}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Test Connection: Could not read error stream: ${e.message}")
                    }
                }

                connection.disconnect()

                val containsMoxa = content.contains("moxa", ignoreCase = true)
                val containsNport = content.contains("nport", ignoreCase = true)
                val containsLogin = content.contains("login", ignoreCase = true)
                val containsAdmin = content.contains("administration", ignoreCase = true)
                val containsPassword = content.contains("password", ignoreCase = true)

                Log.d(TAG, "Content keyword checks: moxa=$containsMoxa, nport=$containsNport, login=$containsLogin, administration=$containsAdmin, password=$containsPassword")

                val isSuccess = (responseCode == HttpURLConnection.HTTP_OK) &&
                        (containsMoxa || containsNport || containsLogin || containsAdmin || containsPassword)

                if (isSuccess) {
                    Log.i(TAG, "Connection test to $targetUrlStr successful (HTTP $responseCode)")
                } else {
                    Log.w(TAG, "Connection test to $targetUrlStr FAILED. HTTP Response: $responseCode. Content checks: moxa=$containsMoxa, nport=$containsNport, login=$containsLogin, admin=$containsAdmin, password=$containsPassword. Exception during content read: $exceptionMessage")
                }
                isSuccess

            } catch (e: Exception) {
                Log.e(TAG, "Connection test to $targetUrlStr critically failed: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Ändert die Baudrate für einen spezifischen Port
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

                val sessionId = login()
                if (sessionId.isEmpty()) {
                    Log.e(TAG, "Baudrate-Änderung fehlgeschlagen: Login nicht möglich")
                    return@withContext false
                }

                val baudrateSet = setBaudrateInternal(sessionId, port, baudrate)
                if (!baudrateSet) {
                    Log.e(TAG, "Baudrate-Änderung fehlgeschlagen")
                    return@withContext false
                }

                val saved = saveConfiguration(sessionId)
                if (!saved) {
                    Log.e(TAG, "Konfiguration speichern fehlgeschlagen")
                    return@withContext false
                }

                Log.i(TAG, "Baudrate für Port $port erfolgreich auf $baudrate geändert")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Setzen der Baudrate: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Startet die Moxa 5232 neu
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

                restarted

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Neustart: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Ruft aktuelle Port-Konfiguration ab
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
     * Debug-Methode um Login-Prozess zu analysieren
     */
    suspend fun debugLoginProcess(): String {
        return withContext(Dispatchers.IO) {
            val debug = StringBuilder()

            try {
                debug.appendLine("=== Moxa Login Debug ===")
                debug.appendLine("Target: http://$moxaIpAddress/")
                debug.appendLine("Credentials: $username / ${password.replace(Regex("."), "*")}")
                debug.appendLine()

                debug.appendLine("1. Testing basic connection...")
                val connectionOk = testConnection()
                debug.appendLine("   Result: $connectionOk")
                debug.appendLine()

                if (!connectionOk) {
                    debug.appendLine("ERROR: Basic connection failed!")
                    return@withContext debug.toString()
                }

                debug.appendLine("2. Testing login process...")
                val sessionId = login()
                debug.appendLine("   Session ID: ${if (sessionId.isEmpty()) "FAILED" else "SUCCESS (${sessionId.take(10)}...)"}")
                debug.appendLine()

                if (sessionId.isNotEmpty()) {
                    debug.appendLine("3. Testing port configuration access...")
                    val portConfig = getPortConfigInternal(sessionId, 1)
                    debug.appendLine("   Port 1 Config: ${if (portConfig != null) "SUCCESS" else "FAILED"}")
                    debug.appendLine()
                }

                debug.appendLine("=== Debug Complete ===")

            } catch (e: Exception) {
                debug.appendLine("ERROR: ${e.message}")
            }

            debug.toString()
        }
    }

    /**
     * Erweiterte Web-Diagnose
     */
    suspend fun performDetailedWebDiagnostic(): WebDiagnosticResult {
        return withContext(Dispatchers.IO) {
            val result = WebDiagnosticResult()

            val testUrls = listOf(
                "http://$moxaIpAddress/",
                "https://$moxaIpAddress/",
                "http://$moxaIpAddress:80/",
                "http://$moxaIpAddress:8080/",
                "https://$moxaIpAddress:443/",
                "https://$moxaIpAddress:8443/"
            )

            for (testUrl in testUrls) {
                try {
                    val url = URL(testUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    setBrowserHeaders(connection)

                    if (testUrl.startsWith("https")) {
                        setupHTTPS(connection as HttpsURLConnection)
                    }

                    val responseCode = connection.responseCode
                    val responseMessage = connection.responseMessage

                    result.testResults[testUrl] = "Code: $responseCode - $responseMessage"

                    if (responseCode in 200..299 || responseCode == 401) {
                        result.workingUrl = testUrl
                        result.responseCode = responseCode

                        if (responseCode == 200) {
                            val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            if (content.contains("login", ignoreCase = true) ||
                                content.contains("username", ignoreCase = true)) {
                                result.loginPageFound = true
                            }
                        }
                    }

                    connection.disconnect()

                } catch (e: Exception) {
                    result.testResults[testUrl] = "Fehler: ${e.message}"
                }
            }

            result
        }
    }

    // Private Helper-Methoden

    /**
     * Browser-ähnliche Headers setzen
     */
    private fun setBrowserHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
        connection.setRequestProperty("Connection", "keep-alive")
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
    }

    /**
     * HTTPS-Verbindungen konfigurieren
     */
    private fun setupHTTPS(connection: HttpsURLConnection) {
        try {
            connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            connection.sslSocketFactory = sslContext.socketFactory
        } catch (e: Exception) {
            Log.w(TAG, "HTTPS setup failed: ${e.message}")
        }
    }

    /**
     * Korrigierte Login-Methode
     */
    private suspend fun login(): String {
        return withContext(Dispatchers.IO) {
            try {
                // Schritt 1: GET Login-Seite
                val loginPageUrl = URL("http://$moxaIpAddress/")
                val loginPageConnection = loginPageUrl.openConnection() as HttpURLConnection

                loginPageConnection.requestMethod = "GET"
                loginPageConnection.connectTimeout = TIMEOUT_MS
                loginPageConnection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(loginPageConnection)

                val loginPageResponse = loginPageConnection.responseCode
                val initialCookies = loginPageConnection.headerFields["Set-Cookie"]

                Log.d(TAG, "Login page: HTTP $loginPageResponse")

                loginPageConnection.disconnect()

                if (loginPageResponse != 200) {
                    Log.e(TAG, "Login page not accessible: $loginPageResponse")
                    return@withContext ""
                }

                // Schritt 2: POST Login-Daten
                val loginUrl = URL("http://$moxaIpAddress/forms/web_userlogin")
                val loginConnection = loginUrl.openConnection() as HttpURLConnection

                loginConnection.requestMethod = "POST"
                loginConnection.doOutput = true
                loginConnection.connectTimeout = TIMEOUT_MS
                loginConnection.readTimeout = TIMEOUT_MS

                setBrowserHeaders(loginConnection)
                loginConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                loginConnection.setRequestProperty("Referer", "http://$moxaIpAddress/")

                // Initial Cookies mitschicken falls vorhanden
                if (!initialCookies.isNullOrEmpty()) {
                    val cookieHeader = initialCookies.joinToString("; ") { cookie ->
                        cookie.substringBefore(";")
                    }
                    loginConnection.setRequestProperty("Cookie", cookieHeader)
                    Log.d(TAG, "Sending initial cookies: $cookieHeader")
                }

                // Login-Daten senden
                val postData = "Username=$username&Password=$password&Submit=Login"

                loginConnection.outputStream.use { output ->
                    output.write(postData.toByteArray(Charsets.UTF_8))
                    output.flush()
                }

                val responseCode = loginConnection.responseCode
                val responseMessage = loginConnection.responseMessage
                val location = loginConnection.getHeaderField("Location")
                val cookies = loginConnection.headerFields["Set-Cookie"]

                Log.d(TAG, "Login attempt: HTTP $responseCode $responseMessage")
                Log.d(TAG, "Location header: $location")
                Log.d(TAG, "Response cookies: $cookies")

                loginConnection.disconnect()

                // Erfolgreicher Login = 302 Redirect mit Session Cookie
                if (responseCode == 302 && !cookies.isNullOrEmpty()) {
                    val sessionId = cookies.firstOrNull { cookie ->
                        cookie.contains("JSESSIONID=", ignoreCase = true) ||
                                cookie.contains("sessionid=", ignoreCase = true) ||
                                cookie.contains("session_id=", ignoreCase = true)
                    }?.let { cookie ->
                        when {
                            cookie.contains("JSESSIONID=") ->
                                cookie.substringAfter("JSESSIONID=").substringBefore(";")
                            cookie.contains("sessionid=") ->
                                cookie.substringAfter("sessionid=").substringBefore(";")
                            cookie.contains("session_id=") ->
                                cookie.substringAfter("session_id=").substringBefore(";")
                            else -> cookie.substringBefore(";")
                        }
                    }

                    if (!sessionId.isNullOrEmpty()) {
                        Log.i(TAG, "Login successful, session: ${sessionId.take(10)}...")
                        return@withContext sessionId
                    }
                }

                Log.e(TAG, "Login failed: HTTP $responseCode, no valid session cookie")
                return@withContext ""

            } catch (e: Exception) {
                Log.e(TAG, "Login error: ${e.message}", e)
                return@withContext ""
            }
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
            setBrowserHeaders(connection)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Referer", "http://$moxaIpAddress/main.htm")
            connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")

            val baudrateIndex = SUPPORTED_BAUDRATES.indexOf(baudrate)
            val postData = buildString {
                append("PortEnable=1&")
                append("BaudRate=$baudrateIndex&")
                append("DataBits=3&")
                append("StopBits=0&")
                append("Parity=0&")
                append("FlowControl=0&")
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
            setBrowserHeaders(connection)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Referer", "http://$moxaIpAddress/main.htm")
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
            setBrowserHeaders(connection)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Referer", "http://$moxaIpAddress/main.htm")
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
            setBrowserHeaders(connection)
            connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")

            val response = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            connection.disconnect()

            return parsePortConfiguration(response)

        } catch (e: Exception) {
            Log.e(TAG, "Port-Konfiguration abrufen fehlgeschlagen: ${e.message}", e)
            return null
        }
    }

    private fun parsePortConfiguration(html: String): PortConfiguration {
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

    /**
     * Datenklasse für Web-Diagnose-Ergebnisse
     */
    data class WebDiagnosticResult(
        var workingUrl: String? = null,
        var responseCode: Int = 0,
        var loginPageFound: Boolean = false,
        val testResults: MutableMap<String, String> = mutableMapOf()
    )
}