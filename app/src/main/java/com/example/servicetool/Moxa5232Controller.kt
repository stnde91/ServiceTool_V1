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
 * Korrigiert für passwort-basiertes Login (ohne Benutzername)
 * Unterstützt Baudrate-Änderung und Neustart über Web-Interface
 */
class Moxa5232Controller(
    private val moxaIpAddress: String,
    private val username: String = "", // Wird nicht verwendet bei Moxa
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
                    Log.w(TAG, "Test Connection: Non-OK response ($responseCode)")
                }

                connection.disconnect()

                val containsMoxa = content.contains("moxa", ignoreCase = true)
                val containsNport = content.contains("nport", ignoreCase = true)
                val containsLogin = content.contains("login", ignoreCase = true)
                val containsPassword = content.contains("password", ignoreCase = true)
                val containsAdmin = content.contains("administration", ignoreCase = true)

                Log.d(TAG, "Content checks: moxa=$containsMoxa, nport=$containsNport, login=$containsLogin, password=$containsPassword, admin=$containsAdmin")

                val isSuccess = (responseCode == HttpURLConnection.HTTP_OK) &&
                        (containsMoxa || containsNport || containsLogin || containsPassword || containsAdmin)

                if (isSuccess) {
                    Log.i(TAG, "Connection test successful (HTTP $responseCode)")
                } else {
                    Log.w(TAG, "Connection test FAILED. HTTP $responseCode. No Moxa indicators found.")
                }
                isSuccess

            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed: ${e.message}", e)
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
                debug.appendLine("=== Moxa Password-Only Login Debug ===")
                debug.appendLine("Target: http://$moxaIpAddress/")
                debug.appendLine("Password: ${password.replace(Regex("."), "*")}")
                debug.appendLine()

                debug.appendLine("1. Testing basic connection...")
                val connectionOk = testConnection()
                debug.appendLine("   Result: $connectionOk")
                debug.appendLine()

                if (!connectionOk) {
                    debug.appendLine("ERROR: Basic connection failed!")
                    return@withContext debug.toString()
                }

                debug.appendLine("2. Testing password-only login process...")
                val sessionId = login()
                debug.appendLine("   Session ID: ${if (sessionId.isEmpty()) "FAILED" else "SUCCESS (${sessionId.take(10)}...)"}")
                debug.appendLine()

                if (sessionId.isNotEmpty()) {
                    debug.appendLine("3. Testing administrative functions...")

                    // Test Port-Konfiguration
                    val portConfig = getPortConfigInternal(sessionId, 1)
                    debug.appendLine("   Port 1 Config Access: ${if (portConfig != null) "SUCCESS" else "FAILED"}")

                    // Test Restart-Zugriff (ohne tatsächlichen Restart)
                    debug.appendLine("   Restart Access: Testing...")
                    val canRestart = testRestartAccess(sessionId)
                    debug.appendLine("   Restart Access: ${if (canRestart) "SUCCESS" else "FAILED"}")
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
                "http://$moxaIpAddress:8080/"
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

                    if (responseCode in 200..299) {
                        result.workingUrl = testUrl
                        result.responseCode = responseCode

                        if (responseCode == 200) {
                            val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            if (content.contains("password", ignoreCase = true)) {
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
     * Korrigierte Login-Methode für passwort-basiertes Login
     */
    private suspend fun login(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting password-only login process...")

                // Schritt 1: GET Login-Seite und analysiere das Formular
                val loginPageUrl = URL("http://$moxaIpAddress/")
                val loginPageConnection = loginPageUrl.openConnection() as HttpURLConnection

                loginPageConnection.requestMethod = "GET"
                loginPageConnection.connectTimeout = TIMEOUT_MS
                loginPageConnection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(loginPageConnection)

                val loginPageResponse = loginPageConnection.responseCode
                val initialCookies = loginPageConnection.headerFields["Set-Cookie"]

                Log.d(TAG, "Login page: HTTP $loginPageResponse")

                if (loginPageResponse == 200) {
                    // Lese den HTML-Content um das Formular zu analysieren
                    val loginPageContent = loginPageConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                    // Suche nach dem Formular-Action
                    val actionPattern = Regex("action=\"([^\"]*)\"|action='([^']*)'", RegexOption.IGNORE_CASE)
                    val actionMatch = actionPattern.find(loginPageContent)
                    val formAction = actionMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
                        ?: actionMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
                        ?: "home.htm" // Fallback basierend auf Browser-Logs

                    Log.d(TAG, "Found form action: $formAction")

                    // Suche nach Token-Parametern
                    val tokenPattern = Regex("name=\"([^\"]*token[^\"]*)\"|name=\"([^\"]*challenge[^\"]*)\"|name=\"([^\"]*Challenge[^\"]*)", RegexOption.IGNORE_CASE)
                    val tokenMatch = tokenPattern.find(loginPageContent)
                    val tokenFieldName = tokenMatch?.groupValues?.find { it.isNotEmpty() && it != "name=" }

                    // Suche nach dem Token-Wert
                    var tokenValue = ""
                    if (!tokenFieldName.isNullOrEmpty()) {
                        val tokenValuePattern = Regex("name=\"$tokenFieldName\"[^>]*value=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                        val tokenValueMatch = tokenValuePattern.find(loginPageContent)
                        tokenValue = tokenValueMatch?.groupValues?.get(1) ?: ""
                        Log.d(TAG, "Found token field: $tokenFieldName = $tokenValue")
                    }

                    loginPageConnection.disconnect()

                    // Schritt 2: POST das Passwort (ohne Benutzername)
                    val loginUrl = if (formAction.startsWith("http")) {
                        URL(formAction)
                    } else {
                        URL("http://$moxaIpAddress/$formAction")
                    }

                    Log.d(TAG, "Posting to: $loginUrl")

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

                    // Login-Daten zusammenstellen (nur Passwort + evtl. Token)
                    val postDataBuilder = StringBuilder()

                    // Verschiedene mögliche Passwort-Feldnamen probieren
                    val possiblePasswordFields = listOf("Password", "password", "pass", "passwd", "pwd", "token_text")
                    val passwordField = possiblePasswordFields.find { field ->
                        loginPageContent.contains("name=\"$field\"", ignoreCase = true)
                    } ?: "Password" // Fallback

                    postDataBuilder.append("$passwordField=$password")

                    // Token hinzufügen falls gefunden
                    if (!tokenFieldName.isNullOrEmpty() && tokenValue.isNotEmpty()) {
                        postDataBuilder.append("&$tokenFieldName=$tokenValue")
                    }

                    // Submit-Button
                    postDataBuilder.append("&Submit=Submit")

                    val postData = postDataBuilder.toString()
                    Log.d(TAG, "Sending login data: $postData")

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

                    // Bei der Moxa könnte auch ein 200 OK mit Weiterleitung ein erfolgreicher Login sein
                    val isSuccess = when {
                        // Klassischer erfolgreicher Login mit Redirect
                        responseCode == 302 && !cookies.isNullOrEmpty() -> true
                        // Moxa-spezifisch: 200 OK und Content deutet auf Erfolg hin
                        responseCode == 200 -> {
                            try {
                                val responseContent = loginConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                val isMainPage = responseContent.contains("main.htm", ignoreCase = true) ||
                                        responseContent.contains("home.htm", ignoreCase = true) ||
                                        responseContent.contains("administration", ignoreCase = true) ||
                                        responseContent.contains("serial port", ignoreCase = true) ||
                                        responseContent.contains("configuration", ignoreCase = true) ||
                                        responseContent.contains("restart", ignoreCase = true)
                                Log.d(TAG, "Login response analysis: isMainPage = $isMainPage")
                                isMainPage
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not read login response content: ${e.message}")
                                false
                            }
                        }
                        else -> false
                    }

                    loginConnection.disconnect()

                    if (isSuccess) {
                        // Session-ID extrahieren oder Dummy verwenden
                        val sessionId = cookies?.firstOrNull { cookie ->
                            cookie.contains("JSESSIONID=", ignoreCase = true) ||
                                    cookie.contains("sessionid=", ignoreCase = true) ||
                                    cookie.contains("session", ignoreCase = true)
                        }?.let { cookie ->
                            cookie.substringAfter("=").substringBefore(";")
                        } ?: "MOXA_LOGIN_SUCCESS" // Dummy-Session-ID

                        Log.i(TAG, "Password-only login successful, session: ${sessionId.take(15)}...")
                        return@withContext sessionId
                    } else {
                        Log.e(TAG, "Password-only login failed: HTTP $responseCode")
                        return@withContext ""
                    }

                } else {
                    Log.e(TAG, "Login page not accessible: $loginPageResponse")
                    loginPageConnection.disconnect()
                    return@withContext ""
                }

            } catch (e: Exception) {
                Log.e(TAG, "Password-only login error: ${e.message}", e)
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

            if (sessionId != "MOXA_LOGIN_SUCCESS") {
                connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
            }

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

            if (sessionId != "MOXA_LOGIN_SUCCESS") {
                connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
            }

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

    /**
     * Erweiterte Restart-Methode die verschiedene Pfade probiert
     */
    private fun restartInternal(sessionId: String): Boolean {
        // Verschiedene mögliche Restart-Pfade für Moxa
        val restartPaths = listOf(
            "home.htm?Submit=Restart",
            "forms/restart",
            "restart.htm",
            "admin/restart",
            "main.htm?action=restart",
            "forms/reboot"
        )

        for (restartPath in restartPaths) {
            try {
                Log.d(TAG, "Trying restart path: $restartPath")

                val url = URL("http://$moxaIpAddress/$restartPath")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = if (restartPath.contains("?")) "GET" else "POST"
                if (connection.requestMethod == "POST") {
                    connection.doOutput = true
                }
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(connection)
                connection.setRequestProperty("Referer", "http://$moxaIpAddress/home.htm")

                // Session-Cookie setzen falls vorhanden
                if (sessionId != "MOXA_LOGIN_SUCCESS") {
                    connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                }

                if (connection.requestMethod == "POST") {
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write("Submit=Restart")
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Restart attempt on $restartPath: HTTP $responseCode")

                connection.disconnect()

                if (responseCode in 200..399) {
                    Log.i(TAG, "Restart successful via: $restartPath")
                    return true
                }

            } catch (e: Exception) {
                Log.d(TAG, "Restart path $restartPath failed: ${e.message}")
            }
        }

        Log.e(TAG, "All restart paths failed")
        return false
    }

    private fun getPortConfigInternal(sessionId: String, port: Int): PortConfiguration? {
        try {
            val url = URL("http://$moxaIpAddress/main/serial_port$port.htm")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            setBrowserHeaders(connection)

            if (sessionId != "MOXA_LOGIN_SUCCESS") {
                connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
            }

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
     * Test ob Restart-Zugriff funktioniert (ohne tatsächlichen Restart)
     */
    private fun testRestartAccess(sessionId: String): Boolean {
        return try {
            val url = URL("http://$moxaIpAddress/home.htm")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            setBrowserHeaders(connection)

            if (sessionId != "MOXA_LOGIN_SUCCESS") {
                connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
            }

            val responseCode = connection.responseCode
            val content = if (responseCode == 200) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else ""

            connection.disconnect()

            responseCode == 200 && (content.contains("restart", ignoreCase = true) ||
                    content.contains("reboot", ignoreCase = true))

        } catch (e: Exception) {
            Log.d(TAG, "Restart access test failed: ${e.message}")
            false
        }
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