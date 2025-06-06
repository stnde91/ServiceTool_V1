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
 * Komplett neu geschrieben für optimalen Token-basierten Restart
 * Unterstützt Baudrate-Änderung und Hardware-Neustart über Web-Interface
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

                var content = ""
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try {
                        content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        Log.d(TAG, "Test Connection: Received content (first 300 chars): ${content.take(300).replace("\n", " ")}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Test Connection: Error reading response content: ${e.message}", e)
                    }
                }

                connection.disconnect()

                val containsMoxa = content.contains("moxa", ignoreCase = true)
                val containsNport = content.contains("nport", ignoreCase = true)
                val containsLogin = content.contains("login", ignoreCase = true)
                val containsPassword = content.contains("password", ignoreCase = true)
                val containsAdmin = content.contains("administration", ignoreCase = true)

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
     * HAUPTMETHODE: Startet die Moxa 5232 neu
     * Verwendet optimierte Token-Extraktion und mehrere Fallback-Methoden
     */
    suspend fun restartDevice(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== Starte Moxa 5232 Neustart-Prozess ===")

                val sessionId = login()
                if (sessionId.isEmpty()) {
                    Log.e(TAG, "Login für Neustart fehlgeschlagen")
                    return@withContext false
                }

                // Methode 1: Optimierter Token-basierter Restart
                Log.d(TAG, "Versuch 1: Optimierter Token-basierter Restart...")
                val tokenRestartSuccess = performOptimizedTokenRestart(sessionId)
                if (tokenRestartSuccess) {
                    Log.i(TAG, "✅ Optimierter Token-Restart erfolgreich!")
                    return@withContext true
                }

                // Methode 2: Direkte 09Set.htm Analyse
                Log.d(TAG, "Versuch 2: Direkte 09Set.htm Analyse...")
                val directRestartSuccess = performDirectSetPageRestart(sessionId)
                if (directRestartSuccess) {
                    Log.i(TAG, "✅ Direkter Set-Page Restart erfolgreich!")
                    return@withContext true
                }

                // Methode 3: Form-basierter POST Restart
                Log.d(TAG, "Versuch 3: Form-basierter POST Restart...")
                val formRestartSuccess = performFormBasedRestart(sessionId)
                if (formRestartSuccess) {
                    Log.i(TAG, "✅ Form-basierter Restart erfolgreich!")
                    return@withContext true
                }

                // Methode 4: Bekannte Restart-URLs
                Log.d(TAG, "Versuch 4: Bekannte Restart-URLs...")
                val knownUrlSuccess = tryKnownRestartUrls(sessionId)
                if (knownUrlSuccess) {
                    Log.i(TAG, "✅ Bekannte URL Restart erfolgreich!")
                    return@withContext true
                }

                Log.e(TAG, "❌ Alle Restart-Methoden fehlgeschlagen")
                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Restart-Fehler: ${e.message}", e)
                return@withContext false
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

    // =======================================
    // PRIVATE RESTART-METHODEN
    // =======================================

    /**
     * METHODE 1: Optimierter Token-basierter Restart
     */
    private suspend fun performOptimizedTokenRestart(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Extrahiere Token mit optimierter Methode...")

                // Lade die Hauptseite um Token zu finden
                val url = URL("http://$moxaIpAddress/")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(connection)

                if (sessionId != "MOXA_LOGIN_SUCCESS") {
                    connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                }

                val responseCode = connection.responseCode

                if (responseCode == 200) {
                    val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    connection.disconnect()

                    // Token extrahieren mit verbessertem Algorithmus
                    val token = extractOptimizedToken(content)
                    if (token != null) {
                        Log.d(TAG, "Optimierter Token gefunden: ${token.take(10)}...")
                        return@withContext executeRestartWithToken(token, sessionId)
                    } else {
                        Log.w(TAG, "Kein Token mit optimierter Methode gefunden")
                    }
                } else {
                    Log.e(TAG, "Hauptseite nicht zugänglich: HTTP $responseCode")
                    connection.disconnect()
                }

                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Optimierter Token-Restart Fehler: ${e.message}", e)
                return@withContext false
            }
        }
    }

    /**
     * METHODE 2: Direkte 09Set.htm Analyse
     */
    private suspend fun performDirectSetPageRestart(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Analysiere 09Set.htm Seite direkt...")

                val setPageUrl = URL("http://$moxaIpAddress/09Set.htm")
                val connection = setPageUrl.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(connection)

                if (sessionId != "MOXA_LOGIN_SUCCESS") {
                    connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                }

                val responseCode = connection.responseCode

                if (responseCode == 200) {
                    val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    connection.disconnect()

                    Log.d(TAG, "09Set.htm Content (erste 500 Zeichen): ${content.take(500)}")

                    // Token aus der Set-Seite extrahieren
                    val token = extractOptimizedToken(content)
                    if (token != null) {
                        Log.d(TAG, "Token aus 09Set.htm extrahiert: ${token.take(10)}...")
                        return@withContext executeRestartWithToken(token, sessionId)
                    }

                    // Auch ohne Token versuchen
                    Log.d(TAG, "Versuche Restart auf 09Set.htm ohne Token...")
                    return@withContext executeDirectSetPageRestart(sessionId)
                } else {
                    Log.e(TAG, "09Set.htm nicht zugänglich: HTTP $responseCode")
                    connection.disconnect()
                }

                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Direkte Set-Page Restart Fehler: ${e.message}", e)
                return@withContext false
            }
        }
    }

    /**
     * METHODE 3: Form-basierter POST Restart
     */
    private suspend fun performFormBasedRestart(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Führe Form-basierten POST Restart durch...")

                val url = URL("http://$moxaIpAddress/09Set.htm")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(connection)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Referer", "http://$moxaIpAddress/09Set.htm")

                if (sessionId != "MOXA_LOGIN_SUCCESS") {
                    connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                }

                // POST-Daten für Restart
                val postData = "Submit=Submit"

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Form-basierter Restart: HTTP $responseCode")

                connection.disconnect()

                if (responseCode in 200..399) {
                    Log.i(TAG, "Form-basierter Restart erfolgreich")
                    return@withContext true
                }

                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Form-basierter Restart Fehler: ${e.message}", e)
                return@withContext false
            }
        }
    }

    /**
     * METHODE 4: Bekannte Restart-URLs
     */
    private suspend fun tryKnownRestartUrls(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val knownUrls = listOf(
                "http://$moxaIpAddress/restart",
                "http://$moxaIpAddress/reboot",
                "http://$moxaIpAddress/system_restart",
                "http://$moxaIpAddress/admin/restart",
                "http://$moxaIpAddress/cgi-bin/restart",
                "http://$moxaIpAddress/forms/restart",
                "http://$moxaIpAddress/home.htm?Submit=Restart"
            )

            for (restartUrl in knownUrls) {
                try {
                    Log.d(TAG, "Probiere bekannte URL: $restartUrl")

                    val url = URL(restartUrl)
                    val connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    setBrowserHeaders(connection)

                    if (sessionId != "MOXA_LOGIN_SUCCESS") {
                        connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                    }

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Bekannte URL $restartUrl: HTTP $responseCode")

                    connection.disconnect()

                    if (responseCode in 200..399) {
                        Log.i(TAG, "Restart erfolgreich mit bekannter URL: $restartUrl")
                        return@withContext true
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Bekannte URL $restartUrl fehlgeschlagen: ${e.message}")
                }
            }

            return@withContext false
        }
    }

    // =======================================
    // TOKEN-EXTRAKTION UND RESTART-AUSFÜHRUNG
    // =======================================

    /**
     * OPTIMIERTE TOKEN-EXTRAKTION
     * Basierend auf dem Browser-Screenshot: Token = C6TVZdZEd3f6UJg8
     */
    private fun extractOptimizedToken(content: String): String? {
        try {
            Log.d(TAG, "=== Starte optimierte Token-Extraktion ===")

            // Pattern für alphanumerische Tokens (wie C6TVZdZEd3f6UJg8)
            val tokenPatterns = listOf(
                // Pattern 1: Direkte token_text= Zuweisung
                Regex("""token_text\s*=\s*['"]*([A-Za-z0-9]{10,20})['"]*"""),

                // Pattern 2: HTML Input Field
                Regex("""<input[^>]*name=["']?token_text["']?[^>]*value=["']([A-Za-z0-9]{10,20})["']"""),

                // Pattern 3: Verstecktes Input Field (umgekehrte Reihenfolge)
                Regex("""<input[^>]*value=["']([A-Za-z0-9]{10,20})["'][^>]*name=["']?token_text["']?"""),

                // Pattern 4: JavaScript Variable
                Regex("""var\s+token_text\s*=\s*["']([A-Za-z0-9]{10,20})["']"""),

                // Pattern 5: URL Parameter
                Regex("""[?&]token_text=([A-Za-z0-9]{10,20})"""),

                // Pattern 6: Meta Tag
                Regex("""<meta[^>]*name=["']?token_text["']?[^>]*content=["']([A-Za-z0-9]{10,20})["']""")
            )

            // Durchsuche mit spezifischen Patterns
            for ((index, pattern) in tokenPatterns.withIndex()) {
                try {
                    val match = pattern.find(content)
                    if (match != null) {
                        val token = match.groupValues[1]
                        if (isValidToken(token)) {
                            Log.i(TAG, "✅ Token mit Pattern ${index + 1} gefunden: ${token.take(10)}... (Länge: ${token.length})")
                            return token
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Pattern ${index + 1} fehlgeschlagen: ${e.message}")
                }
            }

            // Fallback: Suche nach allen alphanumerischen Strings der richtigen Länge
            Log.d(TAG, "Fallback: Suche alle möglichen Token-Kandidaten...")
            val candidatePattern = Regex("""([A-Za-z0-9]{12,18})""")
            val candidates = candidatePattern.findAll(content)
                .map { it.value }
                .distinct()
                .filter { isValidToken(it) }
                .toList()

            Log.d(TAG, "Token-Kandidaten gefunden: ${candidates.joinToString(", ")}")

            // Wähle den besten Kandidaten
            for (candidate in candidates) {
                if (candidate.any { it.isDigit() } && candidate.any { it.isLetter() }) {
                    Log.i(TAG, "✅ Bester Token-Kandidat ausgewählt: ${candidate.take(10)}...")
                    return candidate
                }
            }

            Log.w(TAG, "❌ Kein gültiger Token gefunden")
            return null

        } catch (e: Exception) {
            Log.e(TAG, "Token-Extraktion Fehler: ${e.message}", e)
            return null
        }
    }

    /**
     * Validiert ob ein String ein gültiger Token ist
     */
    private fun isValidToken(token: String): Boolean {
        return token.length in 10..20 &&
                token.matches(Regex("[A-Za-z0-9]+")) &&
                !token.equals("JavaScript", ignoreCase = true) &&
                !token.contains("function", ignoreCase = true) &&
                !token.contains("window", ignoreCase = true) &&
                !token.contains("document", ignoreCase = true) &&
                token.any { it.isDigit() } &&
                token.any { it.isLetter() }
    }

    /**
     * Führt Restart mit spezifischem Token aus
     */
    private fun executeRestartWithToken(token: String, sessionId: String): Boolean {
        try {
            // Verschiedene Token-URL-Formate probieren
            val restartUrls = listOf(
                "http://$moxaIpAddress/09Set.htm?Submit=Submit&token_text=$token",
                "http://$moxaIpAddress/09Set.htm?token_text=$token&Submit=Submit",
                "http://$moxaIpAddress/09Set.htm?Submit=Submit&token_text=$token&action=restart"
            )

            for (restartUrl in restartUrls) {
                try {
                    Log.d(TAG, "🚀 Führe Token-Restart aus: $restartUrl")

                    val url = URL(restartUrl)
                    val connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "GET"
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    setBrowserHeaders(connection)
                    connection.setRequestProperty("Referer", "http://$moxaIpAddress/09Set.htm")

                    if (sessionId != "MOXA_LOGIN_SUCCESS") {
                        connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                    }

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Token-Restart Response: HTTP $responseCode")

                    connection.disconnect()

                    if (responseCode in 200..399) {
                        Log.i(TAG, "✅ Token-Restart erfolgreich! URL: $restartUrl")
                        return true
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Token-URL $restartUrl fehlgeschlagen: ${e.message}")
                }
            }

            return false

        } catch (e: Exception) {
            Log.e(TAG, "executeRestartWithToken Fehler: ${e.message}", e)
            return false
        }
    }

    /**
     * Direkter Restart auf der Set-Seite ohne Token
     */
    private fun executeDirectSetPageRestart(sessionId: String): Boolean {
        try {
            val url = URL("http://$moxaIpAddress/09Set.htm?Submit=Submit")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            setBrowserHeaders(connection)

            if (sessionId != "MOXA_LOGIN_SUCCESS") {
                connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Direkter Set-Page Restart: HTTP $responseCode")

            connection.disconnect()

            if (responseCode in 200..399) {
                Log.i(TAG, "Direkter Set-Page Restart erfolgreich")
                return true
            }

            return false

        } catch (e: Exception) {
            Log.e(TAG, "Direkter Set-Page Restart Fehler: ${e.message}", e)
            return false
        }
    }

    // =======================================
    // LOGIN UND HELPER-METHODEN
    // =======================================

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
     * Login-Methode für passwort-basiertes Login
     */
    private suspend fun login(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== Starte Moxa Login-Prozess ===")

                // Schritt 1: Erste Verbindung zur Hauptseite
                val loginPageUrl = URL("http://$moxaIpAddress/")
                val loginPageConnection = loginPageUrl.openConnection() as HttpURLConnection

                loginPageConnection.requestMethod = "GET"
                loginPageConnection.connectTimeout = TIMEOUT_MS
                loginPageConnection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(loginPageConnection)

                val loginPageResponse = loginPageConnection.responseCode
                val initialCookies = loginPageConnection.headerFields["Set-Cookie"]

                Log.d(TAG, "Login-Seite Response: HTTP $loginPageResponse")
                Log.d(TAG, "Initial Cookies: $initialCookies")

                if (loginPageResponse == 200) {
                    val loginPageContent = loginPageConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    loginPageConnection.disconnect()

                    Log.d(TAG, "Login-Seite Content (erste 300 Zeichen): ${loginPageContent.take(300)}")

                    // Prüfe ob bereits eingeloggt
                    if (isAlreadyLoggedIn(loginPageContent)) {
                        Log.i(TAG, "✅ Bereits eingeloggt!")
                        return@withContext "ALREADY_LOGGED_IN"
                    }

                    // Suche Form-Action
                    val formAction = extractFormAction(loginPageContent)
                    Log.d(TAG, "Form Action gefunden: $formAction")

                    // Schritt 2: Login POST ausführen
                    val loginUrl = if (formAction.startsWith("http")) {
                        URL(formAction)
                    } else {
                        URL("http://$moxaIpAddress/$formAction")
                    }

                    Log.d(TAG, "Login POST URL: $loginUrl")

                    val loginConnection = loginUrl.openConnection() as HttpURLConnection

                    loginConnection.requestMethod = "POST"
                    loginConnection.doOutput = true
                    loginConnection.connectTimeout = TIMEOUT_MS
                    loginConnection.readTimeout = TIMEOUT_MS

                    setBrowserHeaders(loginConnection)
                    loginConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    loginConnection.setRequestProperty("Referer", "http://$moxaIpAddress/")

                    // Initial Cookies übertragen
                    if (!initialCookies.isNullOrEmpty()) {
                        val cookieHeader = initialCookies.joinToString("; ") { cookie ->
                            cookie.substringBefore(";")
                        }
                        loginConnection.setRequestProperty("Cookie", cookieHeader)
                        Log.d(TAG, "Cookies gesendet: $cookieHeader")
                    }

                    // Login-Daten erstellen - ERWEITERTE VERSION
                    val postData = createLoginPostData(loginPageContent)
                    Log.d(TAG, "Login POST Data: $postData")

                    loginConnection.outputStream.use { output ->
                        output.write(postData.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }

                    val responseCode = loginConnection.responseCode
                    val responseMessage = loginConnection.responseMessage
                    val location = loginConnection.getHeaderField("Location")
                    val responseCookies = loginConnection.headerFields["Set-Cookie"]

                    Log.d(TAG, "Login Response: HTTP $responseCode $responseMessage")
                    Log.d(TAG, "Location Header: $location")
                    Log.d(TAG, "Response Cookies: $responseCookies")

                    // ERWEITERTE LOGIN-VALIDIERUNG
                    val loginSuccess = validateLoginSuccess(loginConnection, responseCode, location, responseCookies)

                    loginConnection.disconnect()

                    if (loginSuccess.first) {
                        val sessionId = loginSuccess.second
                        Log.i(TAG, "✅ Login erfolgreich! Session: ${sessionId.take(15)}...")
                        return@withContext sessionId
                    } else {
                        Log.e(TAG, "❌ Login fehlgeschlagen - Validierung negativ")

                        // Debug: Versuche die Response zu lesen
                        debugLoginFailure(responseCode, location, responseCookies)

                        return@withContext ""
                    }

                } else {
                    Log.e(TAG, "Login-Seite nicht zugänglich: HTTP $loginPageResponse")
                    loginPageConnection.disconnect()
                    return@withContext ""
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login-Fehler: ${e.message}", e)
                return@withContext ""
            }
        }
    }

    /**
     * Prüft ob bereits eingeloggt
     */
    private fun isAlreadyLoggedIn(content: String): Boolean {
        val loggedInIndicators = listOf(
            "main.htm",
            "administration",
            "configuration",
            "serial port",
            "logout",
            "system status",
            "restart"
        )

        return loggedInIndicators.any { indicator ->
            content.contains(indicator, ignoreCase = true)
        }
    }
    /**
     * Extrahiert Form-Action aus HTML
     */
    private fun extractFormAction(content: String): String {
        val patterns = listOf(
            Regex("action=\"([^\"]*?)\"", RegexOption.IGNORE_CASE),
            Regex("action='([^']*?)'", RegexOption.IGNORE_CASE),
            Regex("action=([^\\s>]*)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) {
                val action = match.groupValues[1]
                if (action.isNotEmpty()) {
                    return action
                }
            }
        }

        return "home.htm" // Fallback
    }

    /**
     * Erstellt Login POST-Daten basierend auf HTML-Form
     */
    private fun createLoginPostData(content: String): String {
        val postDataBuilder = StringBuilder()

        // Standard Moxa-Login
        postDataBuilder.append("Password=$password")

        // Suche nach versteckten Input-Feldern
        val hiddenInputPattern = Regex("""<input[^>]*type=["']?hidden["']?[^>]*name=["']?([^"'\s>]+)["']?[^>]*value=["']?([^"'>\s]*)["']?""", RegexOption.IGNORE_CASE)
        val hiddenInputs = hiddenInputPattern.findAll(content)

        for (hiddenInput in hiddenInputs) {
            val name = hiddenInput.groupValues[1]
            val value = hiddenInput.groupValues[2]
            if (name.isNotEmpty()) {
                postDataBuilder.append("&$name=$value")
                Log.d(TAG, "Hidden Input gefunden: $name=$value")
            }
        }

        // Submit Button
        postDataBuilder.append("&Submit=Submit")

        return postDataBuilder.toString()
    }

    /**
     * ERWEITERTE LOGIN-VALIDIERUNG
     */
    private fun validateLoginSuccess(
        connection: HttpURLConnection,
        responseCode: Int,
        location: String?,
        cookies: List<String>?
    ): Pair<Boolean, String> {

        Log.d(TAG, "=== Login-Validierung ===")
        Log.d(TAG, "Response Code: $responseCode")
        Log.d(TAG, "Location: $location")
        Log.d(TAG, "Cookies: $cookies")

        try {
            // Methode 1: Redirect mit Session Cookie
            if (responseCode == 302 && !cookies.isNullOrEmpty()) {
                val sessionId = extractSessionId(cookies)
                if (sessionId.isNotEmpty()) {
                    Log.d(TAG, "✅ Validierung 1: Redirect + Session Cookie")
                    return Pair(true, sessionId)
                }
            }

            // Methode 2: HTTP 200 mit Login-Success-Content
            if (responseCode == 200) {
                try {
                    val responseContent = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    Log.d(TAG, "Response Content (erste 500 Zeichen): ${responseContent.take(500)}")

                    // Prüfe auf Success-Indikatoren
                    val successIndicators = listOf(
                        "main.htm",
                        "home.htm",
                        "administration",
                        "configuration",
                        "serial port",
                        "system status",
                        "device server",
                        "nport",
                        "restart",
                        "logout"
                    )

                    val hasSuccessIndicator = successIndicators.any { indicator ->
                        responseContent.contains(indicator, ignoreCase = true)
                    }

                    if (hasSuccessIndicator) {
                        Log.d(TAG, "✅ Validierung 2: HTTP 200 + Success Content")
                        val sessionId = extractSessionId(cookies) ?: "MOXA_LOGIN_SUCCESS"
                        return Pair(true, sessionId)
                    }

                    // Prüfe auf Fehler-Indikatoren
                    val errorIndicators = listOf(
                        "incorrect password",
                        "invalid password",
                        "password error",
                        "login failed",
                        "access denied"
                    )

                    val hasErrorIndicator = errorIndicators.any { indicator ->
                        responseContent.contains(indicator, ignoreCase = true)
                    }

                    if (hasErrorIndicator) {
                        Log.e(TAG, "❌ Login-Fehler in Response erkannt")
                        return Pair(false, "")
                    }

                    // Wenn keine klaren Indikatoren: Als Success werten wenn kein Password-Feld mehr da ist
                    val hasPasswordField = responseContent.contains("password", ignoreCase = true) &&
                            responseContent.contains("input", ignoreCase = true)

                    if (!hasPasswordField) {
                        Log.d(TAG, "✅ Validierung 3: Kein Password-Feld mehr vorhanden")
                        val sessionId = extractSessionId(cookies) ?: "MOXA_CONTENT_SUCCESS"
                        return Pair(true, sessionId)
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Fehler beim Lesen der Response: ${e.message}")
                }
            }

            // Methode 3: Jeder 2xx Response mit Cookies als Erfolg werten
            if (responseCode in 200..299 && !cookies.isNullOrEmpty()) {
                Log.d(TAG, "✅ Validierung 4: 2xx Response + Cookies vorhanden")
                val sessionId = extractSessionId(cookies) ?: "MOXA_COOKIE_SUCCESS"
                return Pair(true, sessionId)
            }

            Log.w(TAG, "❌ Keine Validierung erfolgreich")
            return Pair(false, "")

        } catch (e: Exception) {
            Log.e(TAG, "Validierung-Fehler: ${e.message}", e)
            return Pair(false, "")
        }
    }

    /**
     * Extrahiert Session-ID aus Cookies
     */
    private fun extractSessionId(cookies: List<String>?): String? {
        if (cookies.isNullOrEmpty()) return null

        for (cookie in cookies) {
            // Suche nach verschiedenen Session-Cookie-Namen
            val sessionPatterns = listOf(
                "JSESSIONID=([^;]+)",
                "sessionid=([^;]+)",
                "session=([^;]+)",
                "SESSIONID=([^;]+)"
            )

            for (pattern in sessionPatterns) {
                val match = Regex(pattern).find(cookie)
                if (match != null) {
                    val sessionId = match.groupValues[1]
                    if (sessionId.isNotEmpty()) {
                        return sessionId
                    }
                }
            }
        }

        return null
    }

    /**
     * Debug-Funktion für Login-Fehler
     */
    private fun debugLoginFailure(responseCode: Int, location: String?, cookies: List<String>?) {
        Log.e(TAG, "=== LOGIN FAILURE DEBUG ===")
        Log.e(TAG, "Response Code: $responseCode")
        Log.e(TAG, "Location Header: $location")
        Log.e(TAG, "Cookies: $cookies")
        Log.e(TAG, "Expected: HTTP 302 mit Session-Cookie ODER HTTP 200 mit Admin-Content")
        Log.e(TAG, "========================")
    }

    // =======================================
    // BAUDRATE UND KONFIGURATION
    // =======================================

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

    // =======================================
    // DEBUG UND ERWEITERTE FUNKTIONEN
    // =======================================

    /**
     * Debug-Methode um Login-Prozess zu analysieren
     */
    suspend fun debugLoginProcess(): String {
        return withContext(Dispatchers.IO) {
            val debug = StringBuilder()

            try {
                debug.appendLine("=== Moxa Login Debug ===")
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

                debug.appendLine("2. Testing login process...")
                val sessionId = login()
                debug.appendLine("   Session ID: ${if (sessionId.isEmpty()) "FAILED" else "SUCCESS (${sessionId.take(10)}...)"}")
                debug.appendLine()

                if (sessionId.isNotEmpty()) {
                    debug.appendLine("3. Testing token extraction...")

                    // Lade Hauptseite für Token-Test
                    val url = URL("http://$moxaIpAddress/")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    setBrowserHeaders(connection)

                    if (sessionId != "MOXA_LOGIN_SUCCESS") {
                        connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        connection.disconnect()

                        val token = extractOptimizedToken(content)
                        debug.appendLine("   Token Extraction: ${if (token != null) "SUCCESS (${token.take(10)}...)" else "FAILED"}")

                        if (token != null) {
                            debug.appendLine("   Token Details: Länge=${token.length}, Format=${if (isValidToken(token)) "VALID" else "INVALID"}")
                        }
                    } else {
                        connection.disconnect()
                        debug.appendLine("   Token Extraction: FAILED - HTTP $responseCode")
                    }
                }

                debug.appendLine()
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
                "http://$moxaIpAddress/09Set.htm"
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
     * Debug-Methode für vollständige Restart-Analyse
     */
    suspend fun debugCompleteRestartProcess(): String {
        return withContext(Dispatchers.IO) {
            val debug = StringBuilder()

            try {
                debug.appendLine("=== Vollständige Restart-Prozess Debug ===")
                debug.appendLine()

                // 1. Login testen
                debug.appendLine("1. Login-Test:")
                val sessionId = login()
                if (sessionId.isEmpty()) {
                    debug.appendLine("   ❌ Login fehlgeschlagen")
                    return@withContext debug.toString()
                }
                debug.appendLine("   ✅ Login erfolgreich: ${sessionId.take(10)}...")
                debug.appendLine()

                // 2. Token-Extraktion testen
                debug.appendLine("2. Token-Extraktion von Hauptseite:")
                val url = URL("http://$moxaIpAddress/")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                setBrowserHeaders(connection)

                if (sessionId != "MOXA_LOGIN_SUCCESS") {
                    connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    connection.disconnect()

                    val token = extractOptimizedToken(content)
                    if (token != null) {
                        debug.appendLine("   ✅ Token gefunden: ${token.take(10)}... (Länge: ${token.length})")
                        debug.appendLine("   Token vollständig: $token")

                        // 3. Restart-URL zusammenstellen
                        val restartUrl = "http://$moxaIpAddress/09Set.htm?Submit=Submit&token_text=$token"
                        debug.appendLine("   🔗 Restart-URL: $restartUrl")

                        // 4. Restart-URL testen (ohne tatsächlichen Restart)
                        debug.appendLine("   📡 URL-Test (ohne Restart)...")
                        try {
                            val testUrl = URL(restartUrl)
                            val testConnection = testUrl.openConnection() as HttpURLConnection
                            testConnection.requestMethod = "HEAD" // Nur Header abfragen
                            testConnection.connectTimeout = 5000
                            testConnection.readTimeout = 5000
                            setBrowserHeaders(testConnection)

                            if (sessionId != "MOXA_LOGIN_SUCCESS") {
                                testConnection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
                            }

                            val testResponseCode = testConnection.responseCode
                            testConnection.disconnect()

                            debug.appendLine("   🎯 URL-Test Ergebnis: HTTP $testResponseCode")
                            if (testResponseCode in 200..399) {
                                debug.appendLine("   ✅ URL ist gültig und würde funktionieren!")
                            } else {
                                debug.appendLine("   ❌ URL würde nicht funktionieren")
                            }
                        } catch (e: Exception) {
                            debug.appendLine("   ❌ URL-Test fehlgeschlagen: ${e.message}")
                        }

                    } else {
                        debug.appendLine("   ❌ Kein Token gefunden")
                        debug.appendLine("   📄 HTML-Content (erste 1000 Zeichen):")
                        debug.appendLine(content.take(1000))
                    }
                } else {
                    connection.disconnect()
                    debug.appendLine("   ❌ Hauptseite nicht zugänglich: HTTP $responseCode")
                }

                debug.appendLine()
                debug.appendLine("=== Debug Complete ===")

            } catch (e: Exception) {
                debug.appendLine("ERROR: ${e.message}")
            }

            debug.toString()
        }
    }

    // =======================================
    // DATENKLASSEN
    // =======================================

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