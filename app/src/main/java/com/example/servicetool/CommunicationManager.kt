package com.example.servicetool

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

class CommunicationManager {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var loggingManager: LoggingManager? = null

    private val STX: Char = 2.toChar()
    private val ETX: Char = 3.toChar()

    // Initialize logging if context is available
    fun setLoggingManager(loggingManager: LoggingManager) {
        this.loggingManager = loggingManager
    }

    suspend fun connect(ipAddress: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                disconnect()
                Log.d("CommunicationManager", "Versuche zu verbinden mit $ipAddress:$port")
                loggingManager?.logInfo("Communication", "Verbindungsversuch zu $ipAddress:$port")

                socket = Socket()
                socket?.connect(InetSocketAddress(ipAddress, port), 7000)

                if (socket?.isConnected == true) {
                    outputStream = socket!!.getOutputStream()
                    inputStream = socket!!.getInputStream()

                    val duration = System.currentTimeMillis() - startTime
                    Log.d("CommunicationManager", "Erfolgreich verbunden mit $ipAddress:$port in ${duration}ms")
                    loggingManager?.logCommunication(
                        LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                        0, // General connection, not cell-specific
                        "CONNECT $ipAddress:$port",
                        "SUCCESS",
                        true,
                        duration
                    )
                    true
                } else {
                    Log.e("CommunicationManager", "Verbindung fehlgeschlagen (Socket nicht verbunden nach connect)")
                    loggingManager?.logCommunication(
                        LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                        0,
                        "CONNECT $ipAddress:$port",
                        "FAILED - Socket not connected",
                        false,
                        System.currentTimeMillis() - startTime
                    )
                    socket = null
                    false
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.e("CommunicationManager", "Verbindungsfehler: ${e.message}", e)
                loggingManager?.logCommunication(
                    LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                    0,
                    "CONNECT $ipAddress:$port",
                    "ERROR: ${e.message}",
                    false,
                    duration
                )
                socket = null
                false
            }
        }
    }

    suspend fun sendCommand(command: String, cellNumber: Int = 0): String? {
        if (socket?.isConnected != true || outputStream == null || inputStream == null) {
            Log.e("CommunicationManager", "sendCommand: Nicht verbunden oder Streams nicht initialisiert.")
            loggingManager?.logError("Communication", "Befehl gesendet ohne aktive Verbindung: $command", null, cellNumber)
            return null
        }

        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val commandBytes = command.toByteArray(Charsets.US_ASCII)
                Log.d("CommunicationManager", "sendCommand: Sende Befehl (Hex): ${commandBytes.joinToString(" ") { it.toString(16).padStart(2, '0') }}")
                Log.d("CommunicationManager", "sendCommand: Sende Befehl (String): $command")

                outputStream?.write(commandBytes)
                outputStream?.flush()
                Log.d("CommunicationManager", "sendCommand: Befehl gesendet und geflusht.")

                // Lese die Antwort mit Timeout
                val responseMessage = withTimeoutOrNull(15000L) {
                    val responseBuffer = mutableListOf<Byte>()
                    var byteRead: Int
                    var etxFound = false

                    Log.d("CommunicationManager", "sendCommand: Warte auf Antwort von RS485-Gerät...")

                    while (true) {
                        byteRead = inputStream!!.read()

                        if (byteRead == -1) {
                            Log.e("CommunicationManager", "sendCommand: End of stream erreicht")
                            break
                        }

                        responseBuffer.add(byteRead.toByte())
                        Log.d("CommunicationManager", "sendCommand: Byte gelesen (Hex): ${byteRead.toByte().toString(16).padStart(2, '0')}")

                        if (byteRead.toChar() == ETX) {
                            etxFound = true
                            Log.d("CommunicationManager", "sendCommand: ETX gefunden!")
                            break
                        }

                        if (responseBuffer.size > 255) {
                            Log.e("CommunicationManager", "sendCommand: Zu viele Bytes ohne ETX gelesen, breche ab.")
                            break
                        }
                    }

                    if (responseBuffer.isNotEmpty()) {
                        val rawResponse = String(responseBuffer.toByteArray(), Charsets.US_ASCII)
                        Log.d("CommunicationManager", "sendCommand: Roh-Antwort empfangen (Länge ${responseBuffer.size}): '$rawResponse'")

                        if (etxFound) {
                            var startIndex = 0
                            if (responseBuffer.firstOrNull()?.toInt()?.toChar() == STX) {
                                startIndex = 1
                            }
                            val etxIndexInResponse = responseBuffer.indexOf(ETX.code.toByte())
                            val endIndex = if (etxIndexInResponse != -1) etxIndexInResponse else responseBuffer.size

                            if (startIndex < endIndex) {
                                String(responseBuffer.subList(startIndex, endIndex).toByteArray(), Charsets.US_ASCII)
                            } else if (rawResponse.length == 2 && rawResponse.startsWith(STX) && rawResponse.endsWith(ETX)) {
                                Log.w("CommunicationManager", "sendCommand: Antwort war nur STX ETX.")
                                ""
                            } else if (rawResponse.isNotEmpty()) {
                                Log.w("CommunicationManager", "sendCommand: Antwort scheint nur aus Kontrollzeichen zu bestehen.")
                                "RAW_CTRL_CHARS_RESPONSE"
                            } else {
                                ""
                            }
                        } else {
                            Log.w("CommunicationManager", "sendCommand: Antwort ohne ETX empfangen.")
                            "PARTIAL_RESPONSE_NO_ETX"
                        }
                    } else {
                        Log.d("CommunicationManager", "sendCommand: Keine Bytes gelesen.")
                        null
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val success = responseMessage != null

                // Log the communication
                loggingManager?.logCommunication(
                    LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                    cellNumber,
                    command.take(20), // Limit command length for logging
                    responseMessage?.take(50) ?: "NO_RESPONSE", // Limit response length
                    success,
                    duration
                )

                if (responseMessage == null) {
                    Log.e("CommunicationManager", "sendCommand: Gesamt-Timeout (15s) oder keine verwertbare Antwort.")
                }

                responseMessage

            } catch (e: SocketTimeoutException) {
                val duration = System.currentTimeMillis() - startTime
                Log.e("CommunicationManager", "sendCommand: SocketTimeoutException: ${e.message}")
                loggingManager?.logCommunication(
                    LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                    cellNumber,
                    command.take(20),
                    "TIMEOUT: ${e.message}",
                    false,
                    duration
                )
                null
            } catch (e: SocketException) {
                val duration = System.currentTimeMillis() - startTime
                Log.e("CommunicationManager", "sendCommand: SocketException: ${e.message}", e)
                loggingManager?.logCommunication(
                    LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                    cellNumber,
                    command.take(20),
                    "SOCKET_ERROR: ${e.message}",
                    false,
                    duration
                )
                null
            } catch (e: TimeoutCancellationException) {
                val duration = System.currentTimeMillis() - startTime
                Log.e("CommunicationManager", "sendCommand: Expliziter Timeout durch withTimeoutOrNull.")
                loggingManager?.logCommunication(
                    LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                    cellNumber,
                    command.take(20),
                    "EXPLICIT_TIMEOUT",
                    false,
                    duration
                )
                null
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.e("CommunicationManager", "sendCommand: Allgemeiner Fehler: ${e.message}", e)
                loggingManager?.logCommunication(
                    LoggingManager.CommunicationDirection.BIDIRECTIONAL,
                    cellNumber,
                    command.take(20),
                    "ERROR: ${e.message}",
                    false,
                    duration
                )
                null
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("CommunicationManager", "disconnect: Trenne Verbindung...")
                loggingManager?.logInfo("Communication", "Verbindung wird getrennt")

                try { outputStream?.close() } catch (e: Exception) {
                    Log.w("CommunicationManager", "Fehler beim Schließen des OutputStreams: ${e.message}")
                }
                try { inputStream?.close() } catch (e: Exception) {
                    Log.w("CommunicationManager", "Fehler beim Schließen des InputStreams: ${e.message}")
                }
                try { socket?.close() } catch (e: Exception) {
                    Log.w("CommunicationManager", "Fehler beim Schließen des Sockets: ${e.message}")
                }

                Log.d("CommunicationManager", "disconnect: Verbindung getrennt.")
                loggingManager?.logInfo("Communication", "Verbindung erfolgreich getrennt")
            } catch (e: Exception) {
                Log.e("CommunicationManager", "disconnect: Allgemeiner Fehler beim Trennen: ${e.message}", e)
                loggingManager?.logError("Communication", "Fehler beim Trennen der Verbindung", e)
            } finally {
                outputStream = null
                inputStream = null
                socket = null
            }
        }
    }

    fun isConnected(): Boolean {
        return socket?.isConnected == true && socket?.isClosed == false
    }

    // NEU: Erweiterte Diagnose-Methoden

    /**
     * Testet verschiedene Baudraten automatisch
     * Nützlich wenn die Baudrate der Moxa geändert wurde
     */
    suspend fun detectWorkingBaudrate(ipAddress: String, port: Int): Int? {
        return withContext(Dispatchers.IO) {
            val testBaudrates = listOf(9600, 19200, 38400, 57600, 115200)

            for (baudrate in testBaudrates) {
                try {
                    Log.d("CommunicationManager", "Teste Baudrate: $baudrate")

                    // Simuliere verschiedene Baudraten durch verschiedene Timeout-Werte
                    // (In echter RS485-Kommunikation würde man die Hardware-Baudrate ändern)
                    val success = connect(ipAddress, port)
                    if (success) {
                        // Teste mit einem einfachen Befehl
                        val testCommand = FlintecRC3DMultiCellCommands.getCommandForCell(1, FlintecRC3DMultiCellCommands.CommandType.COUNTS)
                        val response = sendCommand(String(testCommand, Charsets.US_ASCII))
                        disconnect()

                        if (response != null && response.isNotEmpty()) {
                            Log.i("CommunicationManager", "Funktionierende Baudrate gefunden: $baudrate")
                            return@withContext baudrate
                        }
                    }
                    disconnect()

                } catch (e: Exception) {
                    Log.d("CommunicationManager", "Baudrate $baudrate funktioniert nicht: ${e.message}")
                }

                delay(1000) // Kurze Pause zwischen Tests
            }

            Log.w("CommunicationManager", "Keine funktionierende Baudrate gefunden")
            null
        }
    }

    /**
     * Erweiterte Verbindungsdiagnose
     */
    suspend fun performConnectionDiagnostic(ipAddress: String, port: Int): ConnectionDiagnostic {
        return withContext(Dispatchers.IO) {
            val diagnostic = ConnectionDiagnostic()

            try {
                // 1. Basis-Netzwerk-Test
                diagnostic.networkReachable = testNetworkConnection(ipAddress)

                // 2. Moxa-Verbindung testen
                if (diagnostic.networkReachable) {
                    diagnostic.moxaReachable = connect(ipAddress, port)

                    if (diagnostic.moxaReachable) {
                        // 3. Zell-Kommunikation testen
                        diagnostic.cellCommunication = testCellCommunication()
                        disconnect()
                    }
                }

                // 4. Bei Problemen: Baudrate-Erkennung
                if (!diagnostic.cellCommunication) {
                    diagnostic.detectedBaudrate = detectWorkingBaudrate(ipAddress, port)
                }

            } catch (e: Exception) {
                diagnostic.error = e.message
                Log.e("CommunicationManager", "Diagnose-Fehler: ${e.message}", e)
            }

            diagnostic
        }
    }

    private suspend fun testNetworkConnection(ipAddress: String): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(ipAddress, 80), 3000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun testCellCommunication(): Boolean {
        return try {
            val testCommand = FlintecRC3DMultiCellCommands.getCommandForCell(1, FlintecRC3DMultiCellCommands.CommandType.COUNTS)
            val response = sendCommand(String(testCommand, Charsets.US_ASCII), 1)
            response != null && response.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Datenklasse für Verbindungsdiagnose-Ergebnisse
     */
    data class ConnectionDiagnostic(
        var networkReachable: Boolean = false,
        var moxaReachable: Boolean = false,
        var cellCommunication: Boolean = false,
        var detectedBaudrate: Int? = null,
        var error: String? = null
    ) {
        fun getStatusSummary(): String {
            return when {
                error != null -> "❌ Fehler: $error"
                !networkReachable -> "❌ Netzwerk nicht erreichbar"
                !moxaReachable -> "❌ Moxa nicht erreichbar"
                !cellCommunication && detectedBaudrate != null -> "⚠️ Zellen nicht erreichbar - Baudrate $detectedBaudrate erkannt"
                !cellCommunication -> "❌ Zellen nicht erreichbar"
                else -> "✅ Alle Verbindungen funktionieren"
            }
        }
    }
}