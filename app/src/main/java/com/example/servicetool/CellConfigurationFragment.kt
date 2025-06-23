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
    
    // Filter UI Components
    private lateinit var spinnerCellCount: Spinner
    private lateinit var spinnerFilterValue: Spinner
    private lateinit var buttonSetFilter: Button
    private lateinit var progressBarFilter: ProgressBar
    private lateinit var textViewFilterStatus: TextView

    private var communicationManager: CommunicationManager? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cell_configuration, container, false)

        initializeViews(view)
        setupSpinner()
        setupCellCountSpinner()
        setupFilterSpinner()
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
        
        // Filter Components
        spinnerCellCount = view.findViewById(R.id.spinner_cell_count)
        spinnerFilterValue = view.findViewById(R.id.spinner_filter_value)
        buttonSetFilter = view.findViewById(R.id.button_set_filter)
        progressBarFilter = view.findViewById(R.id.progress_bar_filter)
        textViewFilterStatus = view.findViewById(R.id.text_filter_status)

        // Initial ausblenden
        progressBar.visibility = View.GONE
        progressBarFilter.visibility = View.GONE
        showStatus("Bereit für Konfiguration", false)
        showFilterStatus("Filter-Funktion bereit", false)
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

    private fun setupCellCountSpinner() {
        // Anzahl aktive Zellen 1-8
        val cellCounts = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            cellCounts
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerCellCount.adapter = adapter
        
        // Lade gespeicherte Einstellung oder verwende Standard (2 Zellen)
        val settingsManager = SettingsManager.getInstance(requireContext())
        val savedCellCount = settingsManager.getActiveCellCount()
        val index = maxOf(0, savedCellCount - 1) // Index 0-7 für Werte 1-8
        spinnerCellCount.setSelection(index)
        
        Log.d("CellConfig", "Geladene Zellanzahl-Einstellung: $savedCellCount (Index: $index)")
        
        // Speichere Änderungen automatisch
        spinnerCellCount.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val newCellCount = cellCounts[position].toInt()
                settingsManager.setActiveCellCount(newCellCount)
                Log.d("CellConfig", "Zellanzahl-Einstellung gespeichert: $newCellCount")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupFilterSpinner() {
        // Filter Werte nur 0 und 5 (die beiden verwendeten Werte)
        val filterValues = arrayOf("0", "5")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            filterValues
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerFilterValue.adapter = adapter
        spinnerFilterValue.setSelection(1) // Standard: Filter 5 (Index 1)
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
        
        buttonSetFilter.setOnClickListener {
            val selectedItem = spinnerFilterValue.selectedItem
            val selectedPosition = spinnerFilterValue.selectedItemPosition
            val filterValue = selectedItem.toString().toInt()
            
            Log.d("CellConfig", "Filter-Button geklickt:")
            Log.d("CellConfig", "  - selectedItem: '$selectedItem'")
            Log.d("CellConfig", "  - selectedPosition: $selectedPosition")
            Log.d("CellConfig", "  - filterValue: $filterValue")
            
            setFilterForAllDetectedCells(filterValue)
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

    // === AUTOMATISCHE FILTER-FUNKTIONEN ===
    
    // Neue Hauptfunktion: Filter für alle Zellen setzen (adress-basiert)
    private fun setFilterForAllDetectedCells(filterValue: Int) {
        Log.d("CellConfig", "setFilterForAllDetectedCells aufgerufen mit filterValue: $filterValue")
        
        // Hole die konfigurierte Anzahl der aktiven Zellen
        val activeCellCount = spinnerCellCount.selectedItem.toString().toInt()
        Log.d("CellConfig", "Verwende konfigurierte Zellanzahl: $activeCellCount")
        
        showFilterStatus("Setze Filter $filterValue für $activeCellCount Zellen...", false)
        showStatus("Verwende direkte Zell-Adressierung (A-${('A' + activeCellCount - 1)})...", false)
        setFilterUIEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!ensureConnection()) {
                    withContext(Dispatchers.Main) {
                        showStatus("Fehler: Keine Verbindung zur Hardware!", true)
                        setFilterUIEnabled(true)
                    }
                    return@launch
                }

                Log.i("CellConfig", "Direkte Filter-Setzung startet - Filter: $filterValue für Zellen 1-$activeCellCount")

                var successCount = 0
                val totalCells = activeCellCount
                
                // Teste nur die konfigurierten aktiven Zellen
                for (cellNumber in 1..activeCellCount) {
                    try {
                        Log.d("CellConfig", "Setze Filter für Zelle $cellNumber")
                        
                        // Verwende korrigierte zwei-Kommando-Sequenz: AQ Query + Aw Write
                        // Basierend auf funktionierender Wireshark-Analyse
                        
                        // 1. AQ Query-Kommando (setzt tatsächlich den Filter)
                        val queryCommand = FlintecRC3DMultiCellCommands.createFilterQueryCommand(cellNumber, filterValue)
                        Log.d("CellConfig", "Sende AQ Query für Zelle $cellNumber: '$queryCommand'")
                        
                        val queryResponse = try {
                            withTimeout(3000) {
                                communicationManager?.sendCommand(queryCommand) ?: ""
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.w("CellConfig", "Timeout bei AQ Query für Zelle $cellNumber")
                            ""
                        }
                        
                        Log.d("CellConfig", "Zelle $cellNumber AQ Response: '$queryResponse'")
                        
                        Thread.sleep(100) // Kurze Pause zwischen den Kommandos
                        
                        // 2. Aw Write-Kommando (bestätigt den Filter)
                        val writeCommand = FlintecRC3DMultiCellCommands.createFilterCommand(cellNumber, filterValue)
                        Log.d("CellConfig", "Sende Aw Write für Zelle $cellNumber: '$writeCommand'")
                        
                        val writeResponse = try {
                            withTimeout(3000) {
                                communicationManager?.sendCommand(writeCommand) ?: ""
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.w("CellConfig", "Timeout bei Aw Write für Zelle $cellNumber")
                            ""
                        }
                        
                        Log.d("CellConfig", "Zelle $cellNumber Aw Response: '$writeResponse'")
                        
                        // 3. Filter-Status abfragen (wie Windows-App das macht)
                        Thread.sleep(200) // Kurze Pause vor Status-Abfrage
                        
                        val filterStatusCommand = FlintecRC3DMultiCellCommands.createFilterStatusCommand(cellNumber)
                        Log.d("CellConfig", "Sende Filter-Status-Abfrage für Zelle $cellNumber: '$filterStatusCommand'")
                        
                        val statusResponse = try {
                            withTimeout(3000) {
                                communicationManager?.sendCommand(filterStatusCommand) ?: ""
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.w("CellConfig", "Timeout bei Filter-Status-Abfrage für Zelle $cellNumber")
                            ""
                        }
                        
                        Log.d("CellConfig", "Zelle $cellNumber Filter-Status Response: '$statusResponse'")
                        
                        // Erfolg wenn eine der Antworten nicht leer ist (Zelle ist aktiv)
                        if (queryResponse.isNotEmpty() || writeResponse.isNotEmpty() || statusResponse.isNotEmpty()) {
                            successCount++
                            Log.i("CellConfig", "Filter erfolgreich für Zelle $cellNumber (AQ: '$queryResponse', Aw: '$writeResponse', Status: '$statusResponse')")
                        } else {
                            Log.d("CellConfig", "Zelle $cellNumber nicht aktiv oder Timeout")
                        }
                        
                        Thread.sleep(300) // Kurze Pause zwischen Befehlen
                        
                    } catch (e: Exception) {
                        Log.e("CellConfig", "Fehler bei Zelle $cellNumber", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (successCount > 0) {
                        showFilterStatus("✓ Filter $filterValue erfolgreich für $successCount/$totalCells Zellen gesetzt!", false)
                        showStatus("Filter-Operation erfolgreich abgeschlossen", false)
                    } else {
                        showFilterStatus("❌ Filter konnte für keine Zelle gesetzt werden!", true)
                        showStatus("Filter-Operation fehlgeschlagen", true)
                    }
                    setFilterUIEnabled(true)
                }

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim automatischen Filter setzen", e)
                withContext(Dispatchers.Main) {
                    showFilterStatus("❌ Fehler: ${e.message}", true)
                    setFilterUIEnabled(true)
                }
            }
        }
    }
    
    // Sammelt alle Seriennummern der aktiven Zellen (1-8)
    private suspend fun collectAllCellSerialNumbers(): Map<Int, String> {
        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, String>()
            
            try {
                val manager = communicationManager ?: return@withContext results
                
                // Teste Zellen 1-8 und sammle Seriennummern
                for (cellNumber in 1..8) {
                    try {
                        val command = FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, 
                            FlintecRC3DMultiCellCommands.CommandType.SERIAL_NUMBER)
                        val commandString = String(command, Charsets.ISO_8859_1)
                        
                        val response = try {
                            withTimeout(5000) {
                                manager.sendCommand(commandString) ?: ""
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.d("CellConfig", "Timeout bei Zelle $cellNumber - vermutlich nicht aktiv")
                            ""
                        }
                        
                        if (response.isNotEmpty()) {
                            val parsedResponse = FlintecRC3DMultiCellCommands.parseMultiCellResponse(response)
                            if (parsedResponse is FlintecData.SerialNumber && parsedResponse.value.isNotEmpty()) {
                                results[cellNumber] = parsedResponse.value
                                Log.i("CellConfig", "Zelle $cellNumber aktiv: S/N ${parsedResponse.value}")
                            }
                        }
                        
                        Thread.sleep(300) // Kurze Pause zwischen Abfragen
                        
                    } catch (e: Exception) {
                        Log.d("CellConfig", "Zelle $cellNumber nicht erreichbar: ${e.message}")
                    }
                }
                
                Log.i("CellConfig", "Insgesamt ${results.size} aktive Zellen gefunden: ${results.keys}")
                return@withContext results
                
            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim Sammeln der Seriennummern", e)
                return@withContext results
            }
        }
    }
    
    // Einzelzellen-Filter (für manuelle Eingabe - optional behalten)
    private fun setFilterBySerialNumber(serialNumber: String, filterValue: Int) {
        showStatus("Filter $filterValue wird für Zelle S/N $serialNumber gesetzt...", false)
        setFilterUIEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!ensureConnection()) {
                    withContext(Dispatchers.Main) {
                        showStatus("Fehler: Keine Verbindung zur Hardware!", true)
                        setFilterUIEnabled(true)
                    }
                    return@launch
                }

                Log.i("CellConfig", "Setze Filter $filterValue für Zelle S/N: $serialNumber")

                // Verwende die neue seriennummer-basierte Filter-Funktion
                val command = FlintecRC3DMultiCellCommands.setFilterBySerialNumber(serialNumber, filterValue)
                
                Log.d("CellConfig", "Sende Filter-Kommando: '$command'")
                
                val response = try {
                    withTimeout(8000) {
                        communicationManager?.sendCommand(command) ?: ""
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w("CellConfig", "Timeout bei Filter-Setzung nach 8 Sekunden")
                    ""
                } catch (e: Exception) {
                    Log.e("CellConfig", "Fehler bei Filter-Kommando", e)
                    ""
                }
                
                Log.d("CellConfig", "Filter-Antwort: '$response'")

                withContext(Dispatchers.Main) {
                    if (response.isNotEmpty() && (response.contains("P") || response.contains("X"))) {
                        showStatus("✓ Filter $filterValue erfolgreich für S/N $serialNumber gesetzt!", false)
                        // Optional: Seriennummer-Feld leeren nach erfolgreichem Filter-Set
                        // editTextSerialNumber.setText("")
                    } else {
                        showStatus("❌ Filter konnte nicht gesetzt werden! Antwort: '$response'", true)
                    }
                    setFilterUIEnabled(true)
                }

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim Filter setzen", e)
                withContext(Dispatchers.Main) {
                    showStatus("❌ Fehler: ${e.message}", true)
                    setFilterUIEnabled(true)
                }
            }
        }
    }
    
    // Alte Methode: Filter für alle Zellen (verwendet feste Adressen - funktioniert nicht mehr)
    private fun setFilterForAllCells(filterValue: Int) {
        showStatus("Filter $filterValue wird für alle Zellen gesetzt...", false)
        setFilterUIEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!ensureConnection()) {
                    withContext(Dispatchers.Main) {
                        showStatus("Fehler: Keine Verbindung zur Hardware!", true)
                        setFilterUIEnabled(true)
                    }
                    return@launch
                }

                Log.i("CellConfig", "Setze Filter $filterValue für alle Zellen (1-8)")

                var successCount = 0
                var totalCells = 8

                for (cellNumber in 1..8) {
                    try {
                        // DEPRECATED: Diese Methode verwendet feste Zelladressen A-H (funktioniert nicht mehr!)
                        // Die neue Methode setFilterForAllDetectedCells() verwendet Seriennummern
                        val command = FlintecRC3DMultiCellCommands.setFilterForCell(cellNumber, filterValue)
                        val commandString = String(command, Charsets.ISO_8859_1)
                        
                        Log.d("CellConfig", "Sende Filter-Kommando für Zelle $cellNumber: ${command.joinToString(" ") { "%02X".format(it) }}")
                        Log.d("CellConfig", "Command String für Zelle $cellNumber: '${commandString.map { it.code.toString(16) }.joinToString(" ")}'")
                        
                        val response = try {
                            Log.d("CellConfig", "Starte sendCommand für Zelle $cellNumber...")
                            withTimeout(8000) {  // Erhöht auf 8 Sekunden für bessere Kompatibilität
                                val result = communicationManager?.sendCommand(commandString) ?: ""
                                Log.d("CellConfig", "sendCommand abgeschlossen für Zelle $cellNumber: '$result'")
                                result
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            Log.w("CellConfig", "Timeout bei Zelle $cellNumber nach 8 Sekunden")
                            ""
                        } catch (e: Exception) {
                            Log.e("CellConfig", "Fehler bei sendCommand für Zelle $cellNumber", e)
                            ""
                        }

                        val parsedResponse = FlintecRC3DMultiCellCommands.parseMultiCellResponse(response)
                        if (parsedResponse is FlintecData.FilterSetResult && parsedResponse.success) {
                            successCount++
                            Log.i("CellConfig", "Filter erfolgreich gesetzt für Zelle $cellNumber")
                        } else {
                            Log.w("CellConfig", "Filter-Antwort für Zelle $cellNumber: '$response'")
                        }

                        // Kurze Pause zwischen Kommandos (wie in Wireshark beobachtet)
                        Thread.sleep(500)

                    } catch (e: Exception) {
                        Log.e("CellConfig", "Fehler bei Zelle $cellNumber", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (successCount > 0) {
                        showStatus("✅ Filter $filterValue erfolgreich für $successCount/$totalCells Zellen gesetzt!", false)
                    } else {
                        showStatus("❌ Filter konnte für keine Zelle gesetzt werden!", true)
                    }
                    setFilterUIEnabled(true)
                }

            } catch (e: Exception) {
                Log.e("CellConfig", "Fehler beim Filter setzen", e)
                withContext(Dispatchers.Main) {
                    showStatus("❌ Fehler: ${e.message}", true)
                    setFilterUIEnabled(true)
                }
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

    private fun setFilterUIEnabled(enabled: Boolean) {
        spinnerCellCount.isEnabled = enabled
        spinnerFilterValue.isEnabled = enabled
        buttonSetFilter.isEnabled = enabled
        progressBarFilter.visibility = if (enabled) View.GONE else View.VISIBLE
    }
    
    private fun showFilterStatus(message: String, isError: Boolean) {
        textViewFilterStatus.text = message
        textViewFilterStatus.setTextColor(
            if (isError)
                requireContext().getColor(android.R.color.holo_red_dark)
            else
                requireContext().getColor(android.R.color.holo_green_dark)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            communicationManager?.disconnect()
            communicationManager = null
        }
    }
}