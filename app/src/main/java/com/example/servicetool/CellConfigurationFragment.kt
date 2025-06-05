package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class CellConfigurationFragment : Fragment() {

    // UI Components
    private lateinit var textInputLayoutSerialNumber: TextInputLayout
    private lateinit var editTextSerialNumber: TextInputEditText
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
        setupSpinner()
        setupInputValidation()
        setupClickListeners()

        return view
    }

    private fun initializeViews(view: View) {
        textInputLayoutSerialNumber = view.findViewById(R.id.textInputLayoutSerialNumber)
        editTextSerialNumber = view.findViewById(R.id.editTextSerialNumber)
        spinnerNewCell = view.findViewById(R.id.spinner_new_cell)
        buttonChangeAddress = view.findViewById(R.id.button_change_address)
        textViewStatus = view.findViewById(R.id.text_status)
        progressBar = view.findViewById(R.id.progress_bar)

        // Initial ausblenden
        progressBar.visibility = View.GONE
        showStatus("Bereit für Konfiguration", false)
    }

    private fun setupSpinner() {
        // Zellnummern 1-8 für Spinner
        val cellNumbers = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            cellNumbers
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerNewCell.adapter = adapter
        spinnerNewCell.setSelection(1) // Standard: Zelle 2 (Index 1)
    }

    private fun setupInputValidation() {
        // CODE-BASIERTE HEX-FILTERUNG (statt android:digits)
        val hexFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val filtered = StringBuilder()
            for (i in start until end) {
                val char = source[i]
                // Nur Hex-Zeichen (0-9, A-F) erlauben
                if (char.isDigit() || char.uppercaseChar() in 'A'..'F') {
                    filtered.append(char.uppercaseChar())
                } else {
                    Log.d("CellConfig", "Ungültiges Zeichen gefiltert: '$char'")
                }
            }

            val result = filtered.toString()
            if (result == source.subSequence(start, end).toString()) {
                null // Keine Änderung nötig
            } else {
                result // Gefilterte Version
            }
        }

        // Filter anwenden
        editTextSerialNumber.filters = arrayOf(
            hexFilter,
            InputFilter.LengthFilter(12) // Max 12 Zeichen
        )

        // TextWatcher für Validierung
        editTextSerialNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validateInput()
            }
        })

        // Focus-Listener für Dezimal-zu-Hex Konvertierung
        editTextSerialNumber.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val input = editTextSerialNumber.text.toString().trim()
                val converted = tryConvertDecimalToHex(input)
                if (converted != input && converted.isNotEmpty()) {
                    editTextSerialNumber.setText(converted)
                    showStatus("Konvertiert: $input → $converted", false)
                }
            }
        }
    }

    // Intelligente Dezimal-zu-Hex Konvertierung
    private fun tryConvertDecimalToHex(input: String): String {
        if (input.isEmpty()) return input

        // Ist es bereits Hex? (enthält A-F)
        val hasHexChars = input.any { it.uppercaseChar() in 'A'..'F' }
        if (hasHexChars) {
            return input.uppercase()
        }

        // Ist es reine Dezimalzahl?
        val isDecimal = input.all { it.isDigit() }
        if (isDecimal) {
            try {
                val decimalValue = input.toLong()
                val hexValue = decimalValue.toString(16).uppercase()
                Log.d("CellConfig", "Dezimal zu Hex: $decimalValue → $hexValue")
                return hexValue
            } catch (e: NumberFormatException) {
                Log.e("CellConfig", "Dezimal-Konvertierung fehlgeschlagen: ${e.message}")
            }
        }

        return input.uppercase()
    }

    private fun validateInput() {
        val input = editTextSerialNumber.text.toString().trim()

        when {
            input.isEmpty() -> {
                textInputLayoutSerialNumber.error = null
                textInputLayoutSerialNumber.helperText = "Seriennummer eingeben (Hex oder Dezimal)"
                buttonChangeAddress.isEnabled = false
            }
            input.length < 4 -> {
                textInputLayoutSerialNumber.error = "Zu kurz (mindestens 4 Zeichen)"
                buttonChangeAddress.isEnabled = false
            }
            input.all { it.isDigit() } -> {
                // Reine Dezimalzahl
                textInputLayoutSerialNumber.error = null
                textInputLayoutSerialNumber.helperText = "Dezimalzahl erkannt - wird zu Hex konvertiert"
                buttonChangeAddress.isEnabled = true
            }
            input.length > 12 -> {
                textInputLayoutSerialNumber.error = "Zu lang (maximal 12 Zeichen)"
                buttonChangeAddress.isEnabled = false
            }
            else -> {
                // Hex-Format
                textInputLayoutSerialNumber.error = null
                textInputLayoutSerialNumber.helperText = "✓ Hex-Seriennummer erkannt"
                buttonChangeAddress.isEnabled = true
            }
        }
    }

    private fun setupClickListeners() {
        buttonChangeAddress.setOnClickListener {
            val rawInput = editTextSerialNumber.text.toString().trim()

            if (rawInput.isEmpty()) {
                showStatus("Fehler: Keine Eingabe!", true)
                return@setOnClickListener
            }

            val serialNumber = tryConvertDecimalToHex(rawInput)
            val newCell = spinnerNewCell.selectedItem.toString().toInt()

            if (serialNumber != rawInput) {
                showStatus("Verwende: $rawInput → $serialNumber", false)
                editTextSerialNumber.setText(serialNumber)
            }

            changeAddressBySerialNumber(serialNumber, newCell)
        }
    }

    private fun changeAddressBySerialNumber(serialNumber: String, toCell: Int) {
        showStatus("Adresse wird geändert...", false)
        setUIEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!ensureConnection()) {
                    withContext(Dispatchers.Main) {
                        showStatus("Fehler: Keine Verbindung zur Hardware!", true)
                        setUIEnabled(true)
                    }
                    return@launch
                }

                Log.i("CellConfig", "Ändere Zellenadresse für S/N: $serialNumber auf Zelle $toCell")

                val success = sendAddressChangeCommandWithSerial(serialNumber, toCell)

                withContext(Dispatchers.Main) {
                    if (success) {
                        showStatus("✅ Zelle S/N $serialNumber → Adresse $toCell erfolgreich!", false)
                        editTextSerialNumber.setText("")
                    } else {
                        showStatus("❌ Fehler bei der Adressänderung!", true)
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

    private suspend fun sendAddressChangeCommandWithSerial(serialNumber: String, toCell: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val manager = communicationManager ?: return@withContext false

                val STX = "\u0002"
                val ETX = "\u0003"

                // Schritt 1: Konfiguration setzen (S2-Befehl)
                val newAddressChar = when (toCell) {
                    1 -> "A"
                    2 -> "B"
                    3 -> "C"
                    4 -> "D"
                    5 -> "E"
                    6 -> "F"
                    7 -> "G"
                    8 -> "H"
                    else -> "A"
                }
                val baudrate = "96" // 9600 Baud
                val databits = "17" // 7 Databits, Even Parity

                val configCommand = STX + "<" + serialNumber + "S2" + newAddressChar + baudrate + databits
                val configWithChecksum = configCommand + calculateChecksum(configCommand) + ETX

                Log.d("CellConfig", "Sende Konfigurations-Befehl: '$configWithChecksum'")

                val configResponse = try {
                    withTimeout(5000) {
                        manager.sendCommand(configWithChecksum) ?: ""
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w("CellConfig", "Timeout beim Konfigurations-Befehl")
                    ""
                }

                Thread.sleep(1000)

                // Schritt 2: Schreibbefehl (w-Befehl)
                val writeCommand = STX + "<" + serialNumber + "w"
                val writeWithChecksum = writeCommand + calculateChecksum(writeCommand) + ETX

                Log.d("CellConfig", "Sende Schreib-Befehl: '$writeWithChecksum'")

                val writeResponse = try {
                    withTimeout(5000) {
                        manager.sendCommand(writeWithChecksum) ?: ""
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w("CellConfig", "Timeout beim Schreib-Befehl")
                    ""
                }

                Thread.sleep(1000)

                Log.i("CellConfig", "Adressänderung abgeschlossen: $serialNumber → Zelle $toCell")
                return@withContext true

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim Senden der Befehle", e)
                false
            }
        }
    }

    private fun calculateChecksum(command: String): String {
        var checksum = 0
        for (char in command) {
            checksum = checksum xor char.code
        }

        val lowNibble = (checksum % 16) + 0x30
        val highNibble = (checksum / 16) + 0x30

        return "${lowNibble.toChar()}${highNibble.toChar()}"
    }

    private suspend fun ensureConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (communicationManager == null) {
                    val settingsManager = SettingsManager.getInstance(requireContext())
                    val manager = CommunicationManager()

                    val connected = manager.connect(
                        settingsManager.getMoxaIpAddress(),
                        settingsManager.getMoxaPort()
                    )

                    if (connected) {
                        communicationManager = manager
                        return@withContext true
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("CellConfig", "Verbindungsfehler", e)
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
        editTextSerialNumber.isEnabled = enabled
        spinnerNewCell.isEnabled = enabled
        buttonChangeAddress.isEnabled = enabled && editTextSerialNumber.text.toString().trim().length >= 4
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            communicationManager?.disconnect()
            communicationManager = null
        }
    }
}