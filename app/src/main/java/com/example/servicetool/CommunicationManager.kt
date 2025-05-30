package com.example.servicetool // Stellen Sie sicher, dass dies Ihr korrekter Paketname ist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    private val STX: Char = 2.toChar()
    private val ETX: Char = 3.toChar()

    suspend fun connect(ipAddress: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()
                Log.d("CommunicationManager", "Versuche zu verbinden mit $ipAddress:$port")
                socket = Socket()
                // TCP_NODELAY wird nicht explizit gesetzt, um das Standardverhalten zu nutzen (meistens Nagle aktiv).
                // socket?.tcpNoDelay = true // Auskommentiert

                socket?.connect(InetSocketAddress(ipAddress, port), 7000) // Verbindungs-Timeout auf 7 Sekunden erhöht

                if (socket?.isConnected == true) {
                    outputStream = socket!!.getOutputStream()
                    inputStream = socket!!.getInputStream()
                    // KEIN expliziter soTimeout hier. read() blockiert, bis Daten kommen
                    // oder der Socket anderweitig geschlossen wird / Fehler auftritt.
                    // Der withTimeoutOrNull in sendCommand ist die Haupt-Timeout-Kontrolle für den Empfang.
                    // socket?.soTimeout = 7000 // Diese Zeile ist in dieser Version entfernt/auskommentiert
                    Log.d("CommunicationManager", "Erfolgreich verbunden mit $ipAddress:$port.")
                    true
                } else {
                    Log.e("CommunicationManager", "Verbindung fehlgeschlagen (Socket nicht verbunden nach connect)")
                    socket = null
                    false
                }
            } catch (e: Exception) {
                Log.e("CommunicationManager", "Verbindungsfehler: ${e.message}", e)
                socket = null
                false
            }
        }
    }

    suspend fun sendCommand(command: String): String? {
        if (socket?.isConnected != true || outputStream == null || inputStream == null) {
            Log.e("CommunicationManager", "sendCommand: Nicht verbunden oder Streams nicht initialisiert.")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val commandBytes = command.toByteArray(Charsets.US_ASCII)
                Log.d("CommunicationManager", "sendCommand: Sende Befehl (Hex): ${commandBytes.joinToString(" ") { it.toString(16).padStart(2, '0') }}")
                Log.d("CommunicationManager", "sendCommand: Sende Befehl (String): $command")

                outputStream?.write(commandBytes)
                outputStream?.flush()
                Log.d("CommunicationManager", "sendCommand: Befehl gesendet und geflusht.")

                // Lese die Antwort Byte für Byte und suche nach ETX
                val responseMessage = withTimeoutOrNull(15000L) { // Gesamt-Timeout für die komplette Antwort: 15 Sekunden
                    val responseBuffer = mutableListOf<Byte>()
                    var byteRead: Int
                    var etxFound = false

                    Log.d("CommunicationManager", "sendCommand: Warte auf Antwort von RS485-Gerät...")

                    while (true) {
                        // inputStream.read() blockiert jetzt potenziell unbegrenzt, bis ein Byte verfügbar ist,
                        // der Stream geschlossen wird (-1), eine SocketException auftritt,
                        // oder der äußere withTimeoutOrNull (15s) zuschlägt.
                        byteRead = inputStream!!.read()

                        if (byteRead == -1) {
                            Log.e("CommunicationManager", "sendCommand: End of stream erreicht (Verbindung von Gegenseite geschlossen).")
                            break
                        }

                        responseBuffer.add(byteRead.toByte())
                        Log.d("CommunicationManager", "sendCommand: Byte gelesen (Hex): ${byteRead.toByte().toString(16).padStart(2, '0')}")

                        if (byteRead.toChar() == ETX) {
                            etxFound = true
                            Log.d("CommunicationManager", "sendCommand: ETX gefunden!")
                            break
                        }

                        if (responseBuffer.size > 255) { // Sicherheitsabbruch
                            Log.e("CommunicationManager", "sendCommand: Zu viele Bytes (>255) ohne ETX gelesen, breche ab.")
                            break
                        }
                    } // Ende der Lese-Schleife

                    if (responseBuffer.isNotEmpty()) {
                        val rawResponse = String(responseBuffer.toByteArray(), Charsets.US_ASCII)
                        Log.d("CommunicationManager", "sendCommand: Roh-Antwort empfangen (Länge ${responseBuffer.size}): '$rawResponse'")
                        Log.d("CommunicationManager", "sendCommand: Roh-Antwort (Hex): ${responseBuffer.joinToString(" ") { it.toString(16).padStart(2, '0') }}")

                        if (etxFound) {
                            var startIndex = 0
                            if (responseBuffer.firstOrNull()?.toInt()?.toChar() == STX) {
                                startIndex = 1
                            }
                            val etxIndexInResponse = responseBuffer.indexOf(ETX.code.toByte())
                            // Nimm Daten bis zum ersten ETX, auch wenn es das letzte Byte ist.
                            val endIndex = if (etxIndexInResponse != -1) etxIndexInResponse else responseBuffer.size

                            if (startIndex < endIndex) {
                                String(responseBuffer.subList(startIndex, endIndex).toByteArray(), Charsets.US_ASCII)
                            } else if (rawResponse.length == 2 && rawResponse.startsWith(STX) && rawResponse.endsWith(ETX)) { // STX ETX ohne Nutzdaten
                                Log.w("CommunicationManager", "sendCommand: Antwort war nur STX ETX.")
                                "" // Leerer String für STX ETX
                            } else if (rawResponse.isNotEmpty()){
                                Log.w("CommunicationManager", "sendCommand: Antwort scheint nur aus Kontrollzeichen bis ETX zu bestehen oder ungültiger Frame.")
                                "RAW_CTRL_CHARS_RESPONSE"
                            } else {
                                ""
                            }
                        } else {
                            Log.w("CommunicationManager", "sendCommand: Antwort ohne ETX empfangen (oder Timeout davor).")
                            "PARTIAL_RESPONSE_NO_ETX"
                        }
                    } else {
                        Log.d("CommunicationManager", "sendCommand: Keine Bytes gelesen (responseBuffer ist leer).")
                        null
                    }
                } // Ende von withTimeoutOrNull

                if (responseMessage == null) {
                    Log.e("CommunicationManager", "sendCommand: Gesamt-Timeout (15s) oder keine verwertbare Antwort vom RS485-Gerät.")
                }
                responseMessage

            } catch (e: SocketTimeoutException) {
                Log.e("CommunicationManager", "sendCommand: SocketTimeoutException beim Lesen eines Bytes: ${e.message}")
                null
            } catch (e: SocketException) {
                Log.e("CommunicationManager", "sendCommand: SocketException: ${e.message}", e)
                null
            } catch (e: TimeoutCancellationException) {
                Log.e("CommunicationManager", "sendCommand: Expliziter Gesamt-Timeout (15s) durch withTimeoutOrNull.")
                null
            } catch (e: Exception) {
                Log.e("CommunicationManager", "sendCommand: Allgemeiner Fehler: ${e.message}", e)
                null
            }
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("CommunicationManager", "disconnect: Trenne Verbindung...")
                try { outputStream?.close() } catch (e: Exception) { Log.w("CommunicationManager", "Fehler beim Schließen des OutputStreams: ${e.message}") }
                try { inputStream?.close() } catch (e: Exception) { Log.w("CommunicationManager", "Fehler beim Schließen des InputStreams: ${e.message}") }
                try { socket?.close() } catch (e: Exception) { Log.w("CommunicationManager", "Fehler beim Schließen des Sockets: ${e.message}") }
                Log.d("CommunicationManager", "disconnect: Verbindung getrennt.")
            } catch (e: Exception) {
                Log.e("CommunicationManager", "disconnect: Allgemeiner Fehler beim Trennen: ${e.message}", e)
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
}
