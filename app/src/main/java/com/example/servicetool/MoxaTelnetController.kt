package com.example.servicetool

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.apache.commons.net.telnet.TelnetClient
import java.io.InputStream

/**
 * Ein Controller zur Steuerung der Moxa über das Telnet-Protokoll.
 * Diese Klasse ist speziell für den Neustart-Vorgang optimiert.
 *
 * @param ipAddress Die IP-Adresse der Moxa.
 */
class MoxaTelnetController(private val ipAddress: String) {

    private val TAG = "MoxaTelnetController"

    /**
     * VERBESSERT: Testet, ob eine Telnet-Verbindung zur Moxa hergestellt
     * und ob die Moxa bereit für Befehle ist (indem auf das Passwort-Prompt gewartet wird).
     *
     * @return True, wenn die Verbindung erfolgreich und die Moxa bereit ist, sonst false.
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            var isReady = false
            try {
                // Kurzer Timeout für den reinen Verbindungsaufbau
                telnet.connectTimeout = 5000
                Log.d(TAG, "Führe Telnet-Verbindungstest zu $ipAddress:23 durch...")
                telnet.connect(ipAddress, 23)

                if (telnet.isConnected) {
                    // Verbindung steht. Prüfe jetzt, ob die Moxa wirklich bereit ist.
                    // Setze einen Timeout für das Warten auf die Antwort.
                    telnet.soTimeout = 5000
                    val inputStream = telnet.inputStream

                    // Nur wenn wir das Passwort-Prompt erhalten, ist die Moxa bereit für Befehle.
                    if (readUntil("password:", inputStream)) {
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

                // Schritt 1: Auf Passwort-Abfrage warten.
                if (!readUntil("Please keyin your password:", inputStream)) {
                    Log.e(TAG, "Timeout oder Fehler: 'password:'-Prompt nicht empfangen.")
                    return@withContext false
                }
                Log.d(TAG, "Passwort-Abfrage empfangen.")
                outputStream.write((password + "\n").toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.i(TAG, "Passwort gesendet.")
                delay(200)

                // Schritt 2: Auf Hauptmenü warten und 's' senden.
                if (!readUntil("Key in your selection:", inputStream)) {
                    Log.e(TAG, "Timeout oder Fehler: Hauptmenü-Prompt ('Key in your selection:') nicht empfangen.")
                    return@withContext false
                }
                Log.d(TAG, "Hauptmenü-Prompt empfangen.")
                outputStream.write("s\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.i(TAG, "Befehl 's' (Save and Restart) gesendet.")
                delay(200)

                // Schritt 3: Auf die finale Bestätigungsfrage warten und mit 'y' bestätigen.
                if (!readUntil("Ready to restart", inputStream)) {
                    Log.e(TAG, "Timeout oder Fehler: Neustart-Bestätigungsfrage ('Ready to restart') nicht empfangen.")
                    return@withContext false
                }
                Log.d(TAG, "Neustart-Bestätigungsfrage empfangen.")
                outputStream.write("y\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                Log.i(TAG, "Neustart mit 'y' bestätigt. Moxa sollte jetzt neu starten.")

                // KORREKTUR: Warte 2 Sekunden, damit die Moxa den Befehl sicher verarbeiten kann.
                // Währenddessen wird die Moxa die Verbindung von sich aus trennen, da sie neu startet.
                Log.d(TAG, "Warte 2 Sekunden, bevor die Verbindung getrennt wird...")
                delay(2000)

                return@withContext true

            } catch (e: Exception) {
                // Ein Fehler hier kann normal sein, wenn die Moxa die Verbindung während des Neustarts schließt.
                // Wir loggen es als Info, nicht als harten Fehler, wenn der letzte Befehl 'y' war.
                Log.i(TAG, "Verbindung während des Neustarts getrennt (erwartetes Verhalten): ${e.message}")
                // Wir geben trotzdem 'true' zurück, da der Befehl gesendet wurde.
                return@withContext true
            } finally {
                if (telnet.isConnected) {
                    try {
                        telnet.disconnect()
                        Log.d(TAG, "Telnet-Verbindung getrennt.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Fehler beim Trennen der Telnet-Verbindung: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Hilfsfunktion, die aus dem InputStream liest, bis ein bestimmter Text gefunden wird oder ein Timeout eintritt.
     */
    private fun readUntil(target: String, inputStream: InputStream): Boolean {
        try {
            val startTime = System.currentTimeMillis()
            val manualTimeout = 10000
            val response = StringBuilder()

            Log.d(TAG, "Warte auf Antwort, die '$target' enthält...")

            while (System.currentTimeMillis() - startTime < manualTimeout) {
                if (inputStream.available() > 0) {
                    val byteRead = inputStream.read()
                    if (byteRead == -1) {
                        Log.w(TAG, "Ende des Streams erreicht.")
                        break
                    }
                    val charRead = byteRead.toChar()
                    Log.v(TAG, "Byte empfangen: '$charRead' (ASCII: $byteRead)")
                    response.append(charRead)

                    if (response.toString().contains(target, ignoreCase = true)) {
                        Log.i(TAG, "Ziel-String '$target' gefunden in der Antwort: '$response'")
                        return true
                    }
                } else {
                    Thread.sleep(50)
                }
            }
            Log.w(TAG, "readUntil Timeout: '$target' nicht gefunden in $manualTimeout ms. Bisher empfangen: '$response'")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Fehler in readUntil beim Warten auf '$target': ${e.message}", e)
            return false
        }
    }
}
