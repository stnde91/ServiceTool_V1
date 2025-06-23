package com.example.servicetool

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.apache.commons.net.telnet.TelnetClient
import java.io.InputStream
import java.io.OutputStream

class MoxaTelnetController(private val ipAddress: String) {

    private val TAG = "MoxaTelnetController"
    val supportedBaudRates = listOf(110, 134, 150, 300, 600, 1200, 1800, 2400, 4800, 7200, 9600, 19200, 38400, 57600, 115200, 230400)
    private val baudRateIndices = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")

    // Datenklasse für das Abrufen von Einstellungen
    data class PortSettings(
        val baudRate: Int,
        val dataBits: Int,
        val stopBits: Int,
        val parity: String,
        val flowControl: String,
        val fifo: String,
        val interfaceType: String,
        val rawData: String
    )

    // NEU: Datenklasse für das Übergeben von zu ändernden Einstellungen
    data class PortSettingsUpdate(
        val baudRate: Int,
        val dataBits: Int,
        val stopBits: Int,
        val parity: String,
        val flowControl: String,
        val fifoEnabled: Boolean
    )

    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            var isReady = false
            try {
                telnet.connectTimeout = 5000
                telnet.connect(ipAddress, 23)

                if (telnet.isConnected) {
                    telnet.soTimeout = 5000
                    if (readUntil("password:", telnet.inputStream) != null) {
                        isReady = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Telnet-Verbindungstest fehlgeschlagen: ${e.message}")
            } finally {
                if (telnet.isConnected) telnet.disconnect()
            }
            return@withContext isReady
        }
    }

    suspend fun restart(password: String = "moxa"): Boolean {
        return withContext(Dispatchers.IO) {
            executeSaveAndRestart(password)
        }
    }

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

                // View settings
                outputStream.write("v\n".toByteArray(Charsets.US_ASCII))
                outputStream.flush()

                // Skip info screens
                readUntil("Press any key to continue...", inputStream) ?: return@withContext null
                outputStream.write("\n".toByteArray(Charsets.US_ASCII)); outputStream.flush(); delay(200)
                readUntil("Press any key to continue...", inputStream) ?: return@withContext null
                outputStream.write("\n".toByteArray(Charsets.US_ASCII)); outputStream.flush(); delay(200)

                // Read Port 1
                val port1Buffer = readUntil("Press any key to continue...", inputStream, returnFullBuffer = true) ?: return@withContext null
                val port1Settings = parsePortSettings(port1Buffer)
                outputStream.write("\n".toByteArray(Charsets.US_ASCII)); outputStream.flush(); delay(200)

                // Read Port 2
                val port2Buffer = readUntil("Press any key to continue...", inputStream, returnFullBuffer = true) ?: return@withContext null
                val port2Settings = parsePortSettings(port2Buffer)

                if (port1Settings != null && port2Settings != null) {
                    mapOf(1 to port1Settings, 2 to port2Settings)
                } else {
                    null
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
     * NEU: Hauptfunktion zum Ändern ALLER Einstellungen für einen Port und anschließendem Neustart.
     */
    suspend fun applyPortSettings(port: Int, settings: PortSettingsUpdate, password: String = "moxa"): Boolean {
        return withContext(Dispatchers.IO) {
            val telnet = TelnetClient()
            try {
                telnet.connectTimeout = 15000
                telnet.connect(ipAddress, 23)
                telnet.soTimeout = 15000

                val inputStream = telnet.inputStream
                val outputStream = telnet.outputStream

                if (!login(password, inputStream, outputStream)) return@withContext false

                // Navigate to serial settings
                if (!executeTelnetCommand(outputStream, "3\n", "Key in your selection:", inputStream)) return@withContext false
                // Select port
                if (!executeTelnetCommand(outputStream, "$port\n", "Key in your selection:", inputStream)) return@withContext false

                // Apply all individual settings
                if (!setBaudRateInternal(outputStream, inputStream, settings.baudRate)) return@withContext false
                if (!setDataBitsInternal(outputStream, inputStream, settings.dataBits)) return@withContext false
                if (!setStopBitsInternal(outputStream, inputStream, settings.stopBits)) return@withContext false
                if (!setParityInternal(outputStream, inputStream, settings.parity)) return@withContext false
                if (!setFlowControlInternal(outputStream, inputStream, settings.flowControl)) return@withContext false
                if (!setFifoInternal(outputStream, inputStream, settings.fifoEnabled)) return@withContext false

                // Go back to main menu
                if (!executeTelnetCommand(outputStream, "m\n", "Key in your selection:", inputStream)) return@withContext false

                // Save and restart
                return@withContext executeSaveAndRestart(password, telnet)

            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei applyPortSettings für Port $port: ${e.message}", e)
                false
            } finally {
                if (telnet.isConnected) telnet.disconnect()
            }
        }
    }

    // --- Private Helper Functions for setting individual values ---

    private suspend fun setBaudRateInternal(outputStream: OutputStream, inputStream: InputStream, baudRate: Int): Boolean {
        val index = supportedBaudRates.indexOf(baudRate)
        if (index == -1) return false
        return executeTelnetCommand(outputStream, "2\n", "Baud rate (", inputStream) &&
                executeTelnetCommand(outputStream, "${baudRateIndices[index]}\n", "Key in your selection:", inputStream)
    }

    private suspend fun setDataBitsInternal(outputStream: OutputStream, inputStream: InputStream, dataBits: Int): Boolean {
        val index = dataBits - 5
        if (index !in 0..3) return false
        return executeTelnetCommand(outputStream, "3\n", "Data bits (", inputStream) &&
                executeTelnetCommand(outputStream, "$index\n", "Key in your selection:", inputStream)
    }

    private suspend fun setStopBitsInternal(outputStream: OutputStream, inputStream: InputStream, stopBits: Int): Boolean {
        val index = stopBits - 1
        if (index !in 0..1) return false
        return executeTelnetCommand(outputStream, "4\n", "Stop bits (", inputStream) &&
                executeTelnetCommand(outputStream, "$index\n", "Key in your selection:", inputStream)
    }

    private suspend fun setParityInternal(outputStream: OutputStream, inputStream: InputStream, parity: String): Boolean {
        val index = listOf("None", "Even", "Odd", "Space", "Mark").indexOfFirst { it.equals(parity, true) }
        if (index == -1) return false
        return executeTelnetCommand(outputStream, "5\n", "Parity (", inputStream) &&
                executeTelnetCommand(outputStream, "$index\n", "Key in your selection:", inputStream)
    }

    private suspend fun setFlowControlInternal(outputStream: OutputStream, inputStream: InputStream, flowControl: String): Boolean {
        val index = listOf("None", "RTS/CTS", "XON/XOFF", "DTR/DSR").indexOfFirst { it.equals(flowControl, true) }
        if (index == -1) return false
        return executeTelnetCommand(outputStream, "6\n", "Flow control (", inputStream) &&
                executeTelnetCommand(outputStream, "$index\n", "Key in your selection:", inputStream)
    }

    private suspend fun setFifoInternal(outputStream: OutputStream, inputStream: InputStream, enabled: Boolean): Boolean {
        val index = if (enabled) "0" else "1"
        return executeTelnetCommand(outputStream, "7\n", "FIFO (", inputStream) &&
                executeTelnetCommand(outputStream, "$index\n", "Key in your selection:", inputStream)
    }

    // --- Other private helpers ---

    private suspend fun executeSaveAndRestart(password: String, existingClient: TelnetClient? = null): Boolean {
        val telnet = existingClient ?: TelnetClient().apply {
            connectTimeout = 10000
            connect(ipAddress, 23)
            soTimeout = 10000
        }
        if (!telnet.isConnected) return false

        try {
            val inputStream = telnet.inputStream
            val outputStream = telnet.outputStream

            // If we are using a new client, we need to login first.
            if (existingClient == null) {
                if (!login(password, inputStream, outputStream)) return false
            }

            if (!executeTelnetCommand(outputStream, "s\n", "Ready to restart", inputStream)) return false
            if (!executeTelnetCommand(outputStream, "y\n", null, inputStream, 2000)) return false
            Log.i(TAG, "Save and Restart command sequence successful.")
            return true
        } catch(e: Exception) {
            Log.i(TAG, "Verbindung während des Neustarts getrennt (erwartet): ${e.message}")
            return true // It's expected to disconnect
        } finally {
            // Only disconnect if we created a new client for this operation
            if (existingClient == null && telnet.isConnected) telnet.disconnect()
        }
    }

    private suspend fun executeTelnetCommand(outputStream: OutputStream, command: String, expectedResponse: String?, inputStream: InputStream, waitAfter: Long = 200): Boolean {
        outputStream.write(command.toByteArray(Charsets.US_ASCII))
        outputStream.flush()
        if (expectedResponse != null) {
            val actualResponse = readUntil(expectedResponse, inputStream, returnFullBuffer = true)
            if (actualResponse == null) {
                Log.w(TAG, "Expected response '$expectedResponse' not received after command '$command'.")
                return false
            } else {
                Log.d(TAG, "Command '$command' successful. Response: '$actualResponse'")
            }
        }
        delay(waitAfter)
        return true
    }

    private fun parsePortSettings(output: String): PortSettings? {
        try {
            val baudRate = Regex("""Baud rate\s*:\s*(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 9600
            val dataBits = Regex("""Data bits\s*:\s*(\d)""").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 8
            val stopBits = Regex("""Stop bits\s*:\s*(\d)""").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val parity = Regex("""Parity\s*:\s*(.+)""").find(output)?.groupValues?.get(1)?.trim() ?: "None"
            val flowControl = Regex("""Flow control\s*:\s*(.+)""").find(output)?.groupValues?.get(1)?.trim() ?: "None"
            val fifo = Regex("""FIFO\s*:\s*(.+)""").find(output)?.groupValues?.get(1)?.trim() ?: "Disabled"
            val interfaceType = Regex("""Interface\s*:\s*(.+)""").find(output)?.groupValues?.get(1)?.trim() ?: "RS-232"

            return PortSettings(baudRate, dataBits, stopBits, parity, flowControl, fifo, interfaceType, output)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Parsen der Port-Einstellungen: ${e.message}")
            return null
        }
    }

    private suspend fun login(password: String, inputStream: InputStream, outputStream: OutputStream): Boolean {
        if (readUntil("password:", inputStream) == null) {
            return false
        }
        outputStream.write((password + "\n").toByteArray(Charsets.US_ASCII))
        outputStream.flush()
        if (readUntil("Key in your selection:", inputStream) == null) {
            return false
        }
        return true
    }

    private suspend fun readUntil(target: String, inputStream: InputStream, returnFullBuffer: Boolean = false): String? {
        try {
            val startTime = System.currentTimeMillis()
            val manualTimeout = 10000
            val response = StringBuilder()

            while (System.currentTimeMillis() - startTime < manualTimeout) {
                if (inputStream.available() > 0) {
                    val byteRead = inputStream.read()
                    if (byteRead == -1) break
                    response.append(byteRead.toChar())
                    if (response.toString().contains(target, ignoreCase = true)) {
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
            return null
        } catch (e: Exception) {
            return null
        }
    }
}
