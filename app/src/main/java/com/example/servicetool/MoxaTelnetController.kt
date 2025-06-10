package com.example.servicetool

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.apache.commons.net.telnet.TelnetClient
import java.io.InputStream
import java.io.OutputStream

/**
 * Ein Controller zur Steuerung der Moxa über das Telnet-Protokoll.
 * Diese Klasse ist für den Neustart und die Konfiguration der Ports optimiert.
 *
 * @param ipAddress Die IP-Adresse der Moxa.
 */
class MoxaTelnetController(private val ipAddress: String) {

    private val TAG = "MoxaTelnetController"
    // KORRIGIERT: Liste der unterstützten Baudraten, passend zum finalen Screenshot
    val supportedBaudRates = listOf(110, 134, 150, 300, 600, 1200, 1800, 2400, 4800, 7200, 9600, 19200, 38400, 57600, 115200, 230400)
    // Die Indices, die gesendet werden müssen (a, b, c, d, e, f für höhere Raten)
    private val baudRateIndices = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")


    // Datenklasse zur Speicherung der Port-Konfiguration
    data class PortSettings(val baudRate: Int, val rawData: String)

    /**
     * Testet, ob eine Telnet-Verbindung zur Moxa hergestellt und ob die Moxa bereit für Befehle ist.
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            var isReady = false
            try {
                telnet.connectTimeout = 5000
                Log.d(TAG, "Führe Telnet-Verbindungstest zu $ipAddress:23 durch...")
                telnet.connect(ipAddress, 23)

                if (telnet.isConnected) {
                    telnet.soTimeout = 5000
                    val inputStream = telnet.inputStream

                    if (readUntil("password:", inputStream) != null) {
                        Log.i(TAG, "Telnet-Verbindungstest erfolgreich: Moxa ist bereit.")
                        isReady = true
                    } else {
                        Log.w(TAG, "Telnet-Verbindungstest fehlgeschlagen: Moxa antwortet nicht mit Passwort-Prompt.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Telnet-Verbindungstest fehlgeschlagen: ${e.message}")
                isReady = false
            } finally {
                if (telnet.isConnected) {
                    try {
                        telnet.disconnect()
                    } catch (e: Exception) {
                        // Fehler beim Trennen ignorieren
                    }
                }
            }
            return@withContext isReady
        }
    }

    /**
     * Führt einen Neustart der Moxa über Telnet aus.
     */
    suspend fun restart(password: String = "moxa"): Boolean {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            try {
                telnet.connectTimeout = 10000

                Log.d(TAG, "Versuche Telnet-Verbindung für Neustart zu $ipAddress:23.")
                telnet.connect(ipAddress, 23)
                Log.i(TAG, "Telnet-Verbindung für Neustart erfolgreich hergestellt.")

                telnet.soTimeout = 10000

                val inputStream = telnet.inputStream
                val outputStream = telnet.outputStream

                if (readUntil("Please keyin your password:", inputStream) == null) {
                    return@withContext false
                }
                outputStream.write((password + "\n").toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                delay(200)

                if (readUntil("Key in your selection:", inputStream) == null) {
                    return@withContext false
                }
                outputStream.write("s\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                delay(200)

                // KORREKTUR für direkten Neustart
                if (readUntil("Save change?", inputStream) == null) {
                    return@withContext false
                }
                outputStream.write("y\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.i(TAG, "Neustart mit 'y' bestätigt. Moxa sollte jetzt neu starten.")

                delay(2000)

                return@withContext true

            } catch (e: Exception) {
                Log.i(TAG, "Verbindung während des Neustarts getrennt (erwartetes Verhalten): ${e.message}")
                return@withContext true
            } finally {
                if (telnet.isConnected) {
                    try {
                        telnet.disconnect()
                    } catch (e: Exception) {
                        Log.w(TAG, "Fehler beim Trennen der Telnet-Verbindung: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Liest die Konfigurationen für beide Ports sequenziell aus.
     */
    suspend fun getPortSettings(password: String = "moxa"): Map<Int, PortSettings>? {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            try {
                telnet.connectTimeout = 10000
                telnet.connect(ipAddress, 23)
                telnet.soTimeout = 10000

                val inputStream = telnet.inputStream
                val outputStream = telnet.outputStream

                if (!login(password, inputStream, outputStream)) return@withContext null

                outputStream.write("v\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.d(TAG, "Befehl 'v' gesendet.")

                if (readUntil("Press any key to continue...", inputStream) == null) return@withContext null
                outputStream.write("\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.d(TAG, "Erstes Info-Fenster übersprungen.")
                delay(200)

                if (readUntil("Press any key to continue...", inputStream) == null) return@withContext null
                outputStream.write("\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.d(TAG, "Zweites Info-Fenster übersprungen.")
                delay(200)

                val port1Buffer = readUntil("Press any key to continue...", inputStream, returnFullBuffer = true)
                if (port1Buffer == null) {
                    Log.e(TAG, "Konnte Port 1 Daten nicht lesen.")
                    return@withContext null
                }
                val port1Settings = parseBaudRate(port1Buffer)
                Log.i(TAG, "Port 1 Daten erfolgreich geparst.")
                outputStream.write("\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                delay(200)

                val port2Buffer = readUntil("Press any key to continue...", inputStream, returnFullBuffer = true)
                if (port2Buffer == null) {
                    Log.e(TAG, "Konnte Port 2 Daten nicht lesen.")
                    return@withContext null
                }
                val port2Settings = parseBaudRate(port2Buffer)
                Log.i(TAG, "Port 2 Daten erfolgreich geparst.")

                if (port1Settings != null && port2Settings != null) {
                    return@withContext mapOf(1 to port1Settings, 2 to port2Settings)
                } else {
                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Auslesen der Port-Settings: ${e.message}", e)
                null
            } finally {
                if (telnet.isConnected) telnet.disconnect()
            }
        }
    }

    /**
     * KORRIGIERT: Ändert die Baudrate für einen spezifischen Port basierend auf dem finalen Screenshot-Ablauf.
     */
    suspend fun setBaudRate(port: Int, baudRate: Int, password: String = "moxa"): Boolean {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            try {
                telnet.connectTimeout = 15000
                telnet.connect(ipAddress, 23)
                telnet.soTimeout = 15000

                val inputStream = telnet.inputStream
                val outputStream = telnet.outputStream

                if (!login(password, inputStream, outputStream)) return@withContext false

                // 1. In "Serial settings" navigieren
                outputStream.write("3\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                if (readUntil("Key in your selection:", inputStream) == null) return@withContext false
                Log.d(TAG, "Im Serial-Settings-Menü.")

                // 2. Port auswählen
                outputStream.write("$port\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                if (readUntil("Key in your selection:", inputStream) == null) return@withContext false
                Log.d(TAG, "Port $port Einstellungsmenü erreicht.")

                // 3. "Baud rate" auswählen
                outputStream.write("2\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                if (readUntil("Baud rate (", inputStream) == null) return@withContext false
                Log.d(TAG, "Im Baudraten-Menü.")

                // 4. Index der neuen Baudrate senden
                val baudIndexPosition = supportedBaudRates.indexOf(baudRate)
                if (baudIndexPosition == -1) {
                    Log.e(TAG, "Ungültige Baudrate: $baudRate")
                    return@withContext false
                }
                val baudIndexChar = baudRateIndices[baudIndexPosition]
                outputStream.write("$baudIndexChar\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.d(TAG, "Baudraten-Index '$baudIndexChar' für '$baudRate' gesendet.")

                // 5. Zurück zum Hauptmenü navigieren
                if (readUntil("Key in your selection:", inputStream) == null) return@withContext false
                outputStream.write("m\n".toByteArray(Charsets.US_ASCII)) // Zurück zum Main Menu
                outputStream.flush()
                Log.d(TAG, "Zurück zum Hauptmenü.")

                // 6. Konfiguration speichern und Neustart
                if (readUntil("Key in your selection:", inputStream) == null) return@withContext false
                outputStream.write("s\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()

                if (readUntil("Save change?", inputStream) == null) return@withContext false
                Log.d(TAG, "Speichern-Bestätigung empfangen.")

                outputStream.write("y\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.i(TAG, "Baudrate für Port $port geändert und Moxa-Neustart eingeleitet.")

                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Setzen der Baudrate für Port $port: ${e.message}", e)
                false
            } finally {
                if (telnet.isConnected) telnet.disconnect()
            }
        }
    }


    /**
     * Private Hilfsfunktionen
     */
    private suspend fun login(password: String, inputStream: InputStream, outputStream: OutputStream): Boolean {
        if (readUntil("password:", inputStream) == null) {
            Log.e(TAG, "Login fehlgeschlagen: Passwort-Prompt nicht gefunden.")
            return false
        }
        outputStream.write((password + "\n").toByteArray(Charsets.US_ASCII))
        outputStream.flush()
        if (readUntil("Key in your selection:", inputStream) == null) {
            Log.e(TAG, "Login fehlgeschlagen: Hauptmenü nicht erreicht.")
            return false
        }
        Log.i(TAG, "Login erfolgreich.")
        return true
    }

    private fun parseBaudRate(output: String): PortSettings? {
        val regex = Regex("""^\s*Baud rate\s*:\s*(\d+)""", RegexOption.MULTILINE)
        val match = regex.find(output)
        return match?.let {
            val baudRate = it.groupValues[1].toIntOrNull() ?: 9600
            PortSettings(baudRate, output)
        }
    }

    private suspend fun readUntil(target: String, inputStream: InputStream, returnFullBuffer: Boolean = false): String? {
        try {
            val startTime = System.currentTimeMillis()
            val manualTimeout = 10000
            val response = StringBuilder()

            Log.d(TAG, "Warte auf Antwort, die '$target' enthält...")

            while (System.currentTimeMillis() - startTime < manualTimeout) {
                if (inputStream.available() > 0) {
                    val byteRead = inputStream.read()
                    if (byteRead == -1) break

                    val charRead = byteRead.toChar()
                    Log.v(TAG, "Byte empfangen: '$charRead' (ASCII: $byteRead)")
                    response.append(charRead)

                    if (response.toString().contains(target, ignoreCase = true)) {
                        Log.i(TAG, "Ziel-String '$target' gefunden.")
                        delay(200)
                        if(inputStream.available() > 0) {
                            val buffer = ByteArray(inputStream.available())
                            inputStream.read(buffer)
                            response.append(String(buffer, Charsets.US_ASCII))
                        }
                        return if(returnFullBuffer) response.toString() else target
                    }
                } else {
                    delay(50)
                }
            }
            Log.w(TAG, "readUntil Timeout: '$target' nicht gefunden. Bisher empfangen: '$response'")
            return null

        } catch (e: Exception) {
            Log.e(TAG, "Fehler in readUntil beim Warten auf '$target': ${e.message}", e)
            return null
        }
    }
}
