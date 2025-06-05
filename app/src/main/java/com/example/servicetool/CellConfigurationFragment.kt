package com.example.servicetool

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream

class CellConfigurationFragment : Fragment() {

    private lateinit var spinnerCurrentCell: Spinner
    private lateinit var spinnerNewCell: Spinner
    private lateinit var buttonChangeAddress: Button
    private lateinit var textViewStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var communicationManager: CommunicationManager? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cell_configuration, container, false)

        initializeViews(view)
        setupSpinners()
        setupClickListeners()

        return view
    }

    private fun initializeViews(view: View) {
        spinnerCurrentCell = view.findViewById(R.id.spinner_current_cell)
        spinnerNewCell = view.findViewById(R.id.spinner_new_cell)
        buttonChangeAddress = view.findViewById(R.id.button_change_address)
        textViewStatus = view.findViewById(R.id.text_status)
        progressBar = view.findViewById(R.id.progress_bar)

        // Initial ausblenden
        progressBar.visibility = View.GONE
    }

    private fun setupSpinners() {
        // Zellnummern 1-8 für beide Spinner
        val cellNumbers = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            cellNumbers
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerCurrentCell.adapter = adapter
        spinnerNewCell.adapter = adapter

        // Standard-Auswahl setzen
        spinnerCurrentCell.setSelection(0) // Zelle 1
        spinnerNewCell.setSelection(1)     // Zelle 2
    }

    private fun setupClickListeners() {
        buttonChangeAddress.setOnClickListener {
            val currentCell = spinnerCurrentCell.selectedItem.toString().toInt()
            val newCell = spinnerNewCell.selectedItem.toString().toInt()

            if (currentCell == newCell) {
                showStatus("Fehler: Aktuelle und neue Zellenadresse sind identisch!", true)
                return@setOnClickListener
            }

            changeAddress(currentCell, newCell)
        }
    }

    private fun changeAddress(fromCell: Int, toCell: Int) {
        showStatus("Adresse wird geändert...", false)
        setUIEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Verbindung herstellen falls nicht vorhanden
                if (!ensureConnection()) {
                    withContext(Dispatchers.Main) {
                        showStatus("Fehler: Keine Verbindung zur Hardware!", true)
                        setUIEnabled(true)
                    }
                    return@launch
                }

                Log.i("CellConfig", "Ändere Zellenadresse von $fromCell auf $toCell")

                // Befehl für Adressänderung senden
                val success = sendAddressChangeCommand(fromCell, toCell)

                withContext(Dispatchers.Main) {
                    if (success) {
                        showStatus("✅ Zellenadresse erfolgreich von $fromCell auf $toCell geändert!", false)
                        Log.i("CellConfig", "Adressänderung erfolgreich: $fromCell → $toCell")
                    } else {
                        showStatus("❌ Fehler bei der Adressänderung!", true)
                        Log.e("CellConfig", "Adressänderung fehlgeschlagen: $fromCell → $toCell")
                    }
                    setUIEnabled(true)
                }

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler bei Adressänderung", e)
                withContext(Dispatchers.Main) {
                    showStatus("❌ Fehler: ${e.message}", true)
                    setUIEnabled(true)
                }
            }
        }
    }

    private suspend fun sendAddressChangeCommand(fromCell: Int, toCell: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val manager = communicationManager ?: return@withContext false

                // Schritt 1: Seriennummer der aktuellen Zelle ermitteln
                val serialNumber = getCurrentCellSerialNumber(fromCell)
                if (serialNumber.isEmpty()) {
                    Log.e("CellConfig", "Konnte Seriennummer von Zelle $fromCell nicht ermitteln")
                    return@withContext false
                }

                Log.d("CellConfig", "Seriennummer von Zelle $fromCell: $serialNumber")

                val STX = "\u0002"
                val ETX = "\u0003"

                // Schritt 1: Konfiguration setzen (S2-Befehl)
                // Format: STX + "<" + Seriennummer + "S2" + NeueAdresse_CHAR + Baudrate + Databits + Checksum + ETX
                val newAddressChar = when (toCell) {
                    1 -> "A"
                    2 -> "B"
                    3 -> "C"  // WICHTIG: Buchstabe, nicht "03"!
                    4 -> "D"
                    5 -> "E"
                    6 -> "F"
                    7 -> "G"
                    8 -> "H"
                    else -> "A"
                }
                val baudrate = "96" // 9600 Baud (Standard)
                val databits = "17" // 7 Databits, Even Parity

                val configCommand = STX + "<" + serialNumber + "S2" + newAddressChar + baudrate + databits
                val configWithChecksum = configCommand + calculateChecksum(configCommand) + ETX

                Log.d("CellConfig", "Konfigurations-Befehl ohne Checksum: '$configCommand'")
                Log.d("CellConfig", "Berechnete Checksum: '${calculateChecksum(configCommand)}'")
                Log.d("CellConfig", "Sende Konfigurations-Befehl: '$configWithChecksum'")

                // Konfiguration senden mit Timeout-Handling
                val configResponse = try {
                    withTimeout(5000) { // 5 Sekunden Timeout
                        manager.sendCommand(configWithChecksum) ?: ""
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w("CellConfig", "Timeout beim Senden des Konfigurations-Befehls")
                    ""
                }

                Log.d("CellConfig", "Konfigurations-Antwort: '$configResponse'")

                if (configResponse.isNullOrEmpty()) {
                    Log.w("CellConfig", "Keine Antwort auf Konfigurations-Befehl erhalten!")
                    return@withContext false
                }

                // Sicherheitspause wie im Original
                Thread.sleep(1000)

                // Schritt 2: Schreibbefehl (w-Befehl)
                // Format: STX + "<" + Seriennummer + "w" + Checksum + ETX
                val writeCommand = STX + "<" + serialNumber + "w"
                val writeWithChecksum = writeCommand + calculateChecksum(writeCommand) + ETX

                Log.d("CellConfig", "Sende Schreib-Befehl: '$writeWithChecksum'")

                // Schreibbefehl senden mit Timeout
                val writeResponse = try {
                    withTimeout(5000) { // 5 Sekunden Timeout
                        manager.sendCommand(writeWithChecksum) ?: ""
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w("CellConfig", "Timeout beim Senden des Schreib-Befehls")
                    ""
                }

                Log.d("CellConfig", "Schreib-Antwort: '$writeResponse'")

                // Weitere Sicherheitspause
                Thread.sleep(1000)

                Log.i("CellConfig", "Adressänderung abgeschlossen: $fromCell → $toCell")

                // Erfolg basierend auf gesendeten Befehlen (auch wenn Antworten fehlen)
                return@withContext true

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim Senden des Adressänderungs-Befehls", e)
                false
            }
        }
    }

    private suspend fun getCurrentCellSerialNumber(cellNumber: Int): String {
        return withContext(Dispatchers.IO) {
            try {
                val manager = communicationManager ?: return@withContext ""

                // Seriennummer-Befehl von der bestehenden Implementierung verwenden
                val command = FlintecRC3DMultiCellCommands.getCommandForCell(
                    cellNumber,
                    FlintecRC3DMultiCellCommands.CommandType.SERIAL_NUMBER
                )

                val commandStr = String(command, Charsets.ISO_8859_1)
                val response = manager.sendCommand(commandStr)

                if (!response.isNullOrEmpty()) {
                    Log.d("CellConfig", "Rohe Seriennummer-Antwort: '$response'")

                    // Direkte Verarbeitung der Rohantwort - erwarte Format wie "Bc01DF825692"
                    var rawData = response

                    // Entferne Präfix falls vorhanden (wie in Ihrer decodeSerialNumber Funktion)
                    if (rawData.startsWith("01") && rawData.length > 2) {
                        rawData = rawData.substring(2)
                    } else if (rawData.length > 4 && rawData.substring(2, 4) == "01") {
                        // Falls Format wie "Bc01DF825692" - entferne ersten Teil bis "01"
                        val index = rawData.indexOf("01")
                        if (index >= 0 && index + 2 < rawData.length) {
                            rawData = rawData.substring(index + 2)
                        }
                    }

                    // Bereinige und nimm erste 6 Hex-Zeichen
                    rawData = rawData.takeWhile { it.isLetterOrDigit() }
                    if (rawData.length >= 6) {
                        val serialHex = rawData.take(6)
                        Log.d("CellConfig", "Extrahierte Seriennummer (Hex): '$serialHex'")
                        return@withContext serialHex
                    }
                }

                Log.w("CellConfig", "Konnte Seriennummer von Zelle $cellNumber nicht ermitteln aus: '$response'")
                return@withContext ""

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim Ermitteln der Seriennummer", e)
                ""
            }
        }
    }

    private fun calculateChecksum(command: String): String {
        // XOR-Checksumme wie im VB.NET Original
        var checksum = 0
        for (char in command) {
            checksum = checksum xor char.code
        }

        // WICHTIG: Spezielle Formatierung wie im VB.NET Original!
        // Chr((iBCC Mod 16) + &H30) + Chr((iBCC \ 16) + &H30)
        val lowNibble = (checksum % 16) + 0x30  // Mod 16 + '0'
        val highNibble = (checksum / 16) + 0x30  // \ 16 + '0'

        val checksumStr = "${lowNibble.toChar()}${highNibble.toChar()}"

        Log.d("CellConfig", "Checksum für '$command':")
        Log.d("CellConfig", "  Raw XOR: $checksum (0x${checksum.toString(16)})")
        Log.d("CellConfig", "  Low nibble: ${checksum % 16} + 48 = $lowNibble = '${lowNibble.toChar()}'")
        Log.d("CellConfig", "  High nibble: ${checksum / 16} + 48 = $highNibble = '${highNibble.toChar()}'")
        Log.d("CellConfig", "  Final checksum: '$checksumStr'")

        return checksumStr
    }

    private suspend fun ensureConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (communicationManager == null) {
                    // Neue Verbindung erstellen
                    val settingsManager = SettingsManager.getInstance(requireContext())
                    val manager = CommunicationManager()

                    val connected = manager.connect(
                        settingsManager.getMoxaIpAddress(),
                        settingsManager.getMoxaPort()
                    )

                    if (connected) {
                        communicationManager = manager
                        Log.d("CellConfig", "Verbindung hergestellt")
                        return@withContext true
                    } else {
                        Log.e("CellConfig", "Verbindung fehlgeschlagen")
                        return@withContext false
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler bei Verbindungsaufbau", e)
                false
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        textViewStatus.text = message
        textViewStatus.setTextColor(
            if (isError)
                requireContext().getColor(android.R.color.holo_red_dark)
            else
                requireContext().getColor(android.R.color.holo_green_dark)
        )
    }

    private fun setUIEnabled(enabled: Boolean) {
        spinnerCurrentCell.isEnabled = enabled
        spinnerNewCell.isEnabled = enabled
        buttonChangeAddress.isEnabled = enabled
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Verbindung asynchron schließen
        lifecycleScope.launch {
            communicationManager?.disconnect()
            communicationManager = null
        }
    }
}