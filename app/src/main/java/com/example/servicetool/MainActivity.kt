package com.example.servicetool // Stellen Sie sicher, dass dies Ihr korrekter Paketname ist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.NumberFormatException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // --- UI-Elemente ---
    private lateinit var editTextIpAddress: TextInputEditText
    private lateinit var editTextPort: TextInputEditText
    private lateinit var buttonConnect: Button
    private lateinit var textViewStatus: TextView
    private lateinit var switchDemoMode: SwitchMaterial
    private lateinit var editTextFilterValue: TextInputEditText
    private lateinit var buttonSetFilter: Button
    private lateinit var buttonGetInitialData: Button
    private lateinit var textViewSerialNumberResult: TextView
    private lateinit var buttonGetCounts: Button
    private lateinit var textViewCountsResult: TextView
    private lateinit var buttonGetBaudrate: Button
    private lateinit var textViewBaudrateResult: TextView
    private lateinit var buttonGetTemperature: Button
    private lateinit var textViewTemperatureResult: TextView
    private lateinit var buttonGetDigitalFilter: Button
    private lateinit var textViewDigitalFilterResult: TextView
    private lateinit var buttonGetVersion: Button
    private lateinit var textViewVersionResult: TextView


    // --- Logik-Komponenten ---
    private val realCommunicationManager = CommunicationManager()
    private val fakeCommunicationManager = FakeCommunicationManager()

    private val STX: Char = 2.toChar()
    private val ETX: Char = 3.toChar()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        setupListeners()
        updateUIState()

        if (savedInstanceState == null) {
            loadCellFragment()
        }
    }

    private fun loadCellFragment(): Unit {
        val cellFragment = CellGridFragment.newInstance(1, 8)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_view, cellFragment)
            .commit()
    }

    private fun setupUI(): Unit {
        editTextIpAddress = findViewById(R.id.editTextIpAddress)
        editTextPort = findViewById(R.id.editTextPort)
        buttonConnect = findViewById(R.id.buttonConnect)
        textViewStatus = findViewById(R.id.textViewStatus)
        switchDemoMode = findViewById(R.id.switchDemoMode)
        editTextFilterValue = findViewById(R.id.editTextFilterValue)
        buttonSetFilter = findViewById(R.id.buttonSetFilter)
        buttonGetInitialData = findViewById(R.id.buttonGetSerialNumber)
        textViewSerialNumberResult = findViewById(R.id.textViewSerialNumberResult)
        buttonGetCounts = findViewById(R.id.buttonGetCounts)
        textViewCountsResult = findViewById(R.id.textViewCountsResult)
        buttonGetBaudrate = findViewById(R.id.buttonGetBaudrate)
        textViewBaudrateResult = findViewById(R.id.textViewBaudrateResult)
        buttonGetTemperature = findViewById(R.id.buttonGetTemperature)
        textViewTemperatureResult = findViewById(R.id.textViewTemperatureResult)
        buttonGetDigitalFilter = findViewById(R.id.buttonGetDigitalFilter)
        textViewDigitalFilterResult = findViewById(R.id.textViewDigitalFilterResult)
        buttonGetVersion = findViewById(R.id.buttonGetVersion)
        textViewVersionResult = findViewById(R.id.textViewVersionResult)


        editTextIpAddress.setText("192.168.50.3")
        editTextPort.setText("4001")
        textViewStatus.text = "Status: Nicht verbunden"
        textViewSerialNumberResult.text = "Seriennummer: -"
        textViewCountsResult.text = "Counts: -"
        textViewBaudrateResult.text = "Baudrate: -"
        textViewTemperatureResult.text = "Temperatur: -"
        textViewDigitalFilterResult.text = "Digitalfilter: -"
        textViewVersionResult.text = "Version: -"
    }

    private fun setupListeners(): Unit {
        buttonConnect.setOnClickListener {
            val isDemo = switchDemoMode.isChecked
            val currentlyConnected = if (isDemo) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()

            if (currentlyConnected) {
                disconnectFromServer()
            } else {
                connectToServer()
            }
        }

        switchDemoMode.setOnCheckedChangeListener { _, _ ->
            disconnectFromServer()
            updateUIState()
        }

        buttonSetFilter.setOnClickListener {
            setFilterForAllCells()
        }

        buttonGetInitialData.setOnClickListener {
            getDeviceSerialNumber()
        }

        buttonGetCounts.setOnClickListener {
            getDeviceCounts()
        }

        buttonGetBaudrate.setOnClickListener {
            getDeviceBaudrate()
        }

        buttonGetTemperature.setOnClickListener {
            getDeviceTemperature()
        }

        buttonGetDigitalFilter.setOnClickListener {
            getDeviceDigitalFilter()
        }
        buttonGetVersion.setOnClickListener {
            getDeviceVersion()
        }
    }

    private fun connectToServer(): Unit {
        val isDemo = switchDemoMode.isChecked
        val ipAddress = editTextIpAddress.text.toString()
        val portString = editTextPort.text.toString()

        if (!isDemo && (ipAddress.isEmpty() || portString.isEmpty())) {
            Toast.makeText(this, "Bitte IP und Port eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        textViewStatus.text = "Status: Verbinde..."
        lifecycleScope.launch {
            val success = if (isDemo) {
                fakeCommunicationManager.connect(ipAddress, portString.toIntOrNull() ?: 0)
            } else {
                try {
                    realCommunicationManager.connect(ipAddress, portString.toInt())
                } catch (e: NumberFormatException) {
                    Toast.makeText(this@MainActivity, "Ungültiger Port", Toast.LENGTH_SHORT).show()
                    textViewStatus.text = "Status: Ungültiger Port"
                    false
                } catch (e: Exception) {
                    Log.e("MainActivityConnect", "Verbindungsfehler: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Verbindungsfehler: ${e.message}", Toast.LENGTH_SHORT).show()
                    textViewStatus.text = "Status: Verbindungsfehler"
                    false
                }
            }

            if (success) {
                Toast.makeText(this@MainActivity, "Verbunden!", Toast.LENGTH_SHORT).show()
                val mode = if (isDemo) "(Demo)" else ""
                textViewStatus.text = "Status: Verbunden $mode"
            } else {
                if (textViewStatus.text.contains("Verbinde")) {
                    Toast.makeText(this@MainActivity, "Verbindung fehlgeschlagen", Toast.LENGTH_LONG).show()
                    textViewStatus.text = "Status: Verbindung fehlgeschlagen"
                }
            }
            updateUIState()
        }
    }

    private fun disconnectFromServer(): Unit {
        val isDemo = switchDemoMode.isChecked
        lifecycleScope.launch {
            if (isDemo) {
                fakeCommunicationManager.disconnect()
            } else {
                realCommunicationManager.disconnect()
            }
            textViewStatus.text = "Status: Nicht verbunden"
            textViewSerialNumberResult.text = "Seriennummer: -"
            textViewCountsResult.text = "Counts: -"
            textViewBaudrateResult.text = "Baudrate: -"
            textViewTemperatureResult.text = "Temperatur: -"
            textViewDigitalFilterResult.text = "Digitalfilter: -"
            textViewVersionResult.text = "Version: -"
            updateUIState()
        }
    }

    private fun getDeviceSerialNumber(): Unit {
        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()
        if (!currentlyConnectedGlobal) { Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show(); return }
        val commandData = "Ac0112"
        val fullCommand = "$STX$commandData$ETX"
        textViewStatus.text = "Sende SN-Befehl ($commandData)..."; textViewSerialNumberResult.text = "Seriennummer: wird geladen..."
        Log.d("MainActivity", "getDeviceSerialNumber: Sende Befehl (String): $fullCommand")
        Log.d("MainActivity", "getDeviceSerialNumber: Sende Befehl (Hex): ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")}")
        lifecycleScope.launch {
            val response = if (isDemoGlobal) { "Ac01DF8256:2" } else { realCommunicationManager.sendCommand(fullCommand) }
            if (response != null) {
                Log.i("MainActivity", "Empfangene Roh-Antwort für SN ($commandData): '$response'")
                var processedSerialNumber = "Formatfehler in SN-Antwort"
                if (response.startsWith("Ac01") && response.length >= 10 && response.contains(":")) {
                    try {
                        val hexPartEndIndex = response.indexOf(':')
                        if (hexPartEndIndex > 4 && hexPartEndIndex < response.length -1 ) {
                            val hexSerialNumberPart = response.substring(4, hexPartEndIndex)
                            if (hexSerialNumberPart.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) {
                                val decimalSerialNumber = hexSerialNumberPart.toLong(16)
                                processedSerialNumber = decimalSerialNumber.toString()
                                textViewSerialNumberResult.text = "Seriennummer: $processedSerialNumber"
                                Toast.makeText(this@MainActivity, "Seriennummer: $processedSerialNumber", Toast.LENGTH_LONG).show()
                                Log.i("MainActivity", "Erfolgreich geparste Seriennummer: $processedSerialNumber (Hex: $hexSerialNumberPart)")
                            } else { Log.e("MainActivity", "SN Hex-Teil '$hexSerialNumberPart' enthält ungültige Zeichen in Antwort: '$response'"); textViewSerialNumberResult.text = "Seriennummer: Ungültige Hex-Zeichen" }
                        } else { Log.e("MainActivity", "Unerwartete Struktur (Position von ':') in SN-Antwort: '$response'"); textViewSerialNumberResult.text = "Seriennummer: Formatfehler (Struktur)" }
                    } catch (e: Exception) { Log.e("MainActivity", "Fehler beim Parsen der SN: '$response'", e); textViewSerialNumberResult.text = "Seriennummer: Parsing Fehler" }
                } else { Log.w("MainActivity", "Unerwartetes Antwortformat für Seriennummer: '$response'"); textViewSerialNumberResult.text = "Seriennummer: Unerwartetes Format" }
                textViewStatus.text = "Letzte Roh-Antwort (SN): $response"
            } else { textViewStatus.text = "Status ($commandData): Keine Antwort oder Fehler"; textViewSerialNumberResult.text = "Seriennummer: Keine Antwort"; Toast.makeText(this@MainActivity, "Keine Antwort oder Fehler für SN", Toast.LENGTH_LONG).show(); Log.w("MainActivity", "Keine Antwort oder Fehler für SN-Befehl ($commandData)") }
        }
    }

    private fun getDeviceCounts(): Unit {
        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()
        if (!currentlyConnectedGlobal) { Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show(); return }
        val commandData = "A?<7"
        val fullCommand = "$STX$commandData$ETX"
        textViewStatus.text = "Sende Counts-Befehl ($commandData)..."; textViewCountsResult.text = "Counts: wird geladen..."
        Log.d("MainActivity", "getDeviceCounts: Sende Befehl (String): $fullCommand")
        Log.d("MainActivity", "getDeviceCounts: Sende Befehl (Hex): ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")}")
        lifecycleScope.launch {
            val response = if (isDemoGlobal) { "A?d00008441" } else { realCommunicationManager.sendCommand(fullCommand) }
            if (response != null) {
                Log.i("MainActivity", "Empfangene Roh-Antwort für Counts ($commandData): '$response'")
                var displayedCountsValue = "Formatfehler"
                if (response.startsWith("A?d") && response.length == 11) {
                    try {
                        val countsDataPart = response.substring(3)
                        val count1String = countsDataPart.substring(0, 4)
                        val decimalCount1Raw = count1String.toIntOrNull() ?: 0
                        Log.d("MainActivity", "Counts Rohwert 1 (ASCII): $decimalCount1Raw")
                        val count2StringFull = countsDataPart.substring(4, 8)
                        if (count2StringFull.length >= 2) {
                            val count2DisplayPart = count2StringFull.substring(0, 2)
                            val decimalCount2Display = count2DisplayPart.toIntOrNull()
                            if (decimalCount2Display != null) {
                                displayedCountsValue = decimalCount2Display.toString()
                                textViewCountsResult.text = "Counts: $displayedCountsValue"
                                Toast.makeText(this@MainActivity, "Counts: $displayedCountsValue", Toast.LENGTH_LONG).show()
                                Log.i("MainActivity", "Angezeigter Count (von C2, erste 2 Ziffern): $displayedCountsValue (Roh-C2: $count2StringFull, Roh-C1: $decimalCount1Raw)")
                            } else { Log.e("MainActivity", "Counts-Teil 2 ('${count2DisplayPart}') konnte nicht in Zahl umgewandelt werden."); textViewCountsResult.text = "Counts: Formatfehler C2"; displayedCountsValue = "Fehler C2 Parse" }
                        } else { Log.e("MainActivity", "Counts-Teil 2 ('${count2StringFull}') ist zu kurz für die ersten 2 Ziffern."); textViewCountsResult.text = "Counts: Formatfehler C2 Länge"; displayedCountsValue = "Fehler C2 Länge" }
                    } catch (e: Exception) { Log.e("MainActivity", "Fehler beim Parsen der Counts-Antwort: '$response'", e); textViewCountsResult.text = "Counts: Parsing Fehler"; displayedCountsValue = "Parsing Fehler" }
                } else { Log.w("MainActivity", "Unerwartetes Antwortformat für Counts: '$response'"); textViewCountsResult.text = "Counts: Unerwartetes Format" }
                textViewStatus.text = "Letzte Roh-Antwort (Counts): $response (Verarbeitet: $displayedCountsValue)"
            } else { textViewStatus.text = "Status ($commandData): Keine Antwort oder Fehler"; textViewCountsResult.text = "Counts: Keine Antwort"; Toast.makeText(this@MainActivity, "Keine Antwort für Counts", Toast.LENGTH_LONG).show(); Log.w("MainActivity", "Keine Antwort oder Fehler für Counts-Befehl ($commandData)") }
        }
    }

    private fun getDeviceBaudrate(): Unit {
        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()
        if (!currentlyConnectedGlobal) { Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show(); return }
        val commandData = "As220"
        val fullCommand = "$STX$commandData$ETX"
        textViewStatus.text = "Sende Baudraten-Befehl ($commandData)..."; textViewBaudrateResult.text = "Baudrate: wird geladen..."
        Log.d("MainActivity", "getDeviceBaudrate: Sende Befehl (String): $fullCommand")
        Log.d("MainActivity", "getDeviceBaudrate: Sende Befehl (Hex): ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")}")
        lifecycleScope.launch {
            val response = if (isDemoGlobal) { "As2A9617:4" } else { realCommunicationManager.sendCommand(fullCommand) }
            if (response != null) {
                Log.i("MainActivity", "Empfangene Roh-Antwort für Baudrate ($commandData): '$response'")
                var processedBaudrate = "Formatfehler"
                if (response.startsWith("As2A") && response.length >= 7 && response.contains(":")) {
                    try {
                        val baudPart = response.substring(4)
                        if (baudPart.length >= 2) {
                            val baudRateIndicator = baudPart.substring(0, 2)
                            if (baudRateIndicator == "96") {
                                processedBaudrate = "9600"
                                textViewBaudrateResult.text = "Baudrate: $processedBaudrate"
                                Toast.makeText(this@MainActivity, "Baudrate: $processedBaudrate", Toast.LENGTH_LONG).show()
                                Log.i("MainActivity", "Erfolgreich geparste Baudrate: $processedBaudrate (aus '$baudRateIndicator')")
                            } else { Log.e("MainActivity", "Unerwarteter Baudraten-Indikator: '$baudRateIndicator' in '$response'"); textViewBaudrateResult.text = "Baudrate: Unerwarteter Wert ($baudRateIndicator)"; processedBaudrate = "Unbekannt ($baudRateIndicator)" }
                        } else { Log.e("MainActivity", "Baudraten-Teil zu kurz in Antwort: '$response'"); textViewBaudrateResult.text = "Baudrate: Formatfehler (Länge)"; processedBaudrate = "Fehler Länge" }
                    } catch (e: Exception) { Log.e("MainActivity", "Fehler beim Parsen der Baudraten-Antwort: '$response'", e); textViewBaudrateResult.text = "Baudrate: Parsing Fehler"; processedBaudrate = "Parsing Fehler" }
                } else { Log.w("MainActivity", "Unerwartetes Antwortformat für Baudrate: '$response'"); textViewBaudrateResult.text = "Baudrate: Unerwartetes Format" }
                textViewStatus.text = "Letzte Roh-Antwort (Baud): $response (Verarbeitet: $processedBaudrate)"
            } else { textViewStatus.text = "Status ($commandData): Keine Antwort oder Fehler"; textViewBaudrateResult.text = "Baudrate: Keine Antwort"; Toast.makeText(this@MainActivity, "Keine Antwort für Baudrate", Toast.LENGTH_LONG).show(); Log.w("MainActivity", "Keine Antwort oder Fehler für Baudraten-Befehl ($commandData)") }
        }
    }

    private fun getDeviceTemperature(): Unit {
        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()
        if (!currentlyConnectedGlobal) { Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show(); return }
        val commandData = "At73"
        val fullCommand = "$STX$commandData$ETX"
        textViewStatus.text = "Sende Temperatur-Befehl ($commandData)..."; textViewTemperatureResult.text = "Temperatur: wird geladen..."
        Log.d("MainActivity", "getDeviceTemperature: Sende Befehl (String): $fullCommand")
        Log.d("MainActivity", "getDeviceTemperature: Sende Befehl (Hex): ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")}")
        lifecycleScope.launch {
            val response = if (isDemoGlobal) { "At+023.3110" } else { realCommunicationManager.sendCommand(fullCommand) }
            if (response != null) {
                Log.i("MainActivity", "Empfangene Roh-Antwort für Temperatur ($commandData): '$response'")
                var processedTemperature = "Formatfehler"
                if (response.startsWith("At") && response.length > 3) {
                    try {
                        val valuePart = response.substring(2)
                        val tempRegex = Regex("([+-]?\\d+\\.\\d+)")
                        val matchResult = tempRegex.find(valuePart)
                        if (matchResult != null) {
                            val temperatureString = matchResult.groupValues[1]
                            val temperatureFloat = temperatureString.toFloatOrNull()
                            if (temperatureFloat != null) {
                                processedTemperature = String.format(Locale.GERMAN, "%.2f °C", temperatureFloat)
                                textViewTemperatureResult.text = "Temperatur: $processedTemperature"
                                Toast.makeText(this@MainActivity, "Temperatur: $processedTemperature", Toast.LENGTH_LONG).show()
                                Log.i("MainActivity", "Erfolgreich geparste Temperatur: $processedTemperature (aus '$temperatureString')")
                            } else { Log.e("MainActivity", "Temperatur-Zahlenteil '$temperatureString' konnte nicht umgewandelt werden."); textViewTemperatureResult.text = "Temperatur: Wert-Formatfehler"; processedTemperature = "Fehler Wertparse" }
                        } else { Log.e("MainActivity", "Kein gültiger Temperaturwert in '$valuePart' gefunden."); textViewTemperatureResult.text = "Temperatur: Kein Wert gefunden"; processedTemperature = "Kein Wert" }
                    } catch (e: Exception) { Log.e("MainActivity", "Fehler beim Parsen der Temperatur-Antwort: '$response'", e); textViewTemperatureResult.text = "Temperatur: Parsing Fehler"; processedTemperature = "Parsing Fehler" }
                } else { Log.w("MainActivity", "Unerwartetes Antwortformat für Temperatur: '$response'"); textViewTemperatureResult.text = "Temperatur: Unerwartetes Format" }
                textViewStatus.text = "Letzte Roh-Antwort (Temp): $response (Verarbeitet: $processedTemperature)"
            } else { textViewStatus.text = "Status ($commandData): Keine Antwort oder Fehler"; textViewTemperatureResult.text = "Temperatur: Keine Antwort"; Toast.makeText(this@MainActivity, "Keine Antwort für Temperatur", Toast.LENGTH_LONG).show(); Log.w("MainActivity", "Keine Antwort oder Fehler für Temperatur-Befehl ($commandData)") }
        }
    }

    private fun getDeviceDigitalFilter(): Unit {
        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()

        if (!currentlyConnectedGlobal) {
            Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show()
            return
        }

        val commandData = "Ap33"
        val fullCommand = "$STX$commandData$ETX"

        textViewStatus.text = "Sende Digitalfilter-Befehl ($commandData)..."
        textViewDigitalFilterResult.text = "Digitalfilter: wird geladen..."
        Log.d("MainActivity", "getDeviceDigitalFilter: Sende Befehl (String): $fullCommand")
        Log.d("MainActivity", "getDeviceDigitalFilter: Sende Befehl (Hex): ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")}")

        lifecycleScope.launch {
            val response = if (isDemoGlobal) {
                "Ap2300140343"
            } else {
                realCommunicationManager.sendCommand(fullCommand)
            }

            if (response != null) {
                Log.i("MainActivity", "Empfangene Roh-Antwort für Digitalfilter ($commandData): '$response'")
                var processedFilterValue = "Formatfehler"

                if (response.startsWith("Ap") && response.length >= 6) {
                    try {
                        val filterChar = response[5]

                        if (filterChar.isDigit()) {
                            processedFilterValue = filterChar.toString()
                            textViewDigitalFilterResult.text = "Digitalfilter: $processedFilterValue"
                            Toast.makeText(this@MainActivity, "Digitalfilter: $processedFilterValue", Toast.LENGTH_LONG).show()
                            Log.i("MainActivity", "Erfolgreich geparster Digitalfilter: $processedFilterValue")
                        } else {
                            Log.e("MainActivity", "Digitalfilter-Zeichen '$filterChar' ist keine Ziffer in '$response'")
                            textViewDigitalFilterResult.text = "Digitalfilter: Ungültiges Zeichen"
                            processedFilterValue = "Ungültig ($filterChar)"
                        }
                    } catch (e: IndexOutOfBoundsException) {
                        Log.e("MainActivity", "Fehler beim Extrahieren des Digitalfilter-Zeichens (Index) aus: '$response'", e)
                        textViewDigitalFilterResult.text = "Digitalfilter: Formatfehler (Index)"
                        processedFilterValue = "Fehler Index"
                    }
                } else {
                    Log.w("MainActivity", "Unerwartetes Antwortformat für Digitalfilter. Erwartet: 'Ap' und Länge >= 6. Bekommen: '$response' (Länge: ${response?.length})")
                    textViewDigitalFilterResult.text = "Digitalfilter: Unerwartetes Format"
                }
                textViewStatus.text = "Letzte Roh-Antwort (Filter): $response (Verarbeitet: $processedFilterValue)"
            } else {
                textViewStatus.text = "Status ($commandData): Keine Antwort oder Fehler"
                textViewDigitalFilterResult.text = "Digitalfilter: Keine Antwort"
                Toast.makeText(this@MainActivity, "Keine Antwort für Digitalfilter", Toast.LENGTH_LONG).show()
                Log.w("MainActivity", "Keine Antwort oder Fehler für Digitalfilter-Befehl ($commandData)")
            }
        }
    }

    private fun getDeviceVersion(): Unit {
        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()

        if (!currentlyConnectedGlobal) {
            Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show()
            return
        }

        val commandData = "Av53"
        val fullCommand = "$STX$commandData$ETX"

        textViewStatus.text = "Sende Versions-Befehl ($commandData)..."
        textViewVersionResult.text = "Version: wird geladen..."
        Log.d("MainActivity", "getDeviceVersion: Sende Befehl (String): $fullCommand")
        Log.d("MainActivity", "getDeviceVersion: Sende Befehl (Hex): ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")}")

        lifecycleScope.launch {
            val response = if (isDemoGlobal) {
                "AvXRC1/3310106"
            } else {
                realCommunicationManager.sendCommand(fullCommand)
            }

            if (response != null) {
                Log.i("MainActivity", "Empfangene Roh-Antwort für Version ($commandData): '$response'")
                var processedVersion = "Formatfehler"

                val expectedXrcPrefix = "AvXRC1/"
                val xrcVersionLength = 11

                if (response.startsWith(expectedXrcPrefix) && response.length >= (expectedXrcPrefix.length -1 + xrcVersionLength) ) {
                    try {
                        processedVersion = response.substring(2, 2 + xrcVersionLength)
                        textViewVersionResult.text = "Version: $processedVersion"
                        Toast.makeText(this@MainActivity, "Version: $processedVersion", Toast.LENGTH_LONG).show()
                        Log.i("MainActivity", "Erfolgreich geparste Version (Typ XRC): $processedVersion")
                    } catch (e: IndexOutOfBoundsException) {
                        Log.e("MainActivity", "Fehler beim Extrahieren des XRC-Versions-Teils: '$response'", e)
                        textViewVersionResult.text = "Version: Formatfehler (XRC Index)"
                        processedVersion = "Fehler XRC Index"
                    }
                }
                else if (response.startsWith("Av ") && response.length > 3) {
                    try {
                        processedVersion = response.substring(3)
                        textViewVersionResult.text = "Version: $processedVersion"
                        Toast.makeText(this@MainActivity, "Version: $processedVersion", Toast.LENGTH_LONG).show()
                        Log.i("MainActivity", "Erfolgreich geparste Version (Typ Av ): $processedVersion")
                    } catch (e: IndexOutOfBoundsException) {
                        Log.e("MainActivity", "Fehler beim Extrahieren des 'Av '-Versions-Teils: '$response'", e)
                        textViewVersionResult.text = "Version: Formatfehler (Av Index)"
                        processedVersion = "Fehler Av Index"
                    }
                }
                else {
                    Log.w("MainActivity", "Unerwartetes Antwortformat für Version. Roh: '$response', Länge: ${response?.length}. Erwartet Start mit '$expectedXrcPrefix' oder 'Av '.")
                    textViewVersionResult.text = "Version: Unerwartetes Format"
                }
                textViewStatus.text = "Letzte Roh-Antwort (Version): $response (Verarbeitet: $processedVersion)"
            } else {
                textViewStatus.text = "Status ($commandData): Keine Antwort oder Fehler"
                textViewVersionResult.text = "Version: Keine Antwort"
                Toast.makeText(this@MainActivity, "Keine Antwort für Version", Toast.LENGTH_LONG).show()
                Log.w("MainActivity", "Keine Antwort oder Fehler für Versions-Befehl ($commandData)")
            }
        }
    }


    private fun setFilterForAllCells(): Unit {
        val filterValueString = editTextFilterValue.text.toString()
        if (filterValueString.isEmpty()) {
            Toast.makeText(this, "Bitte einen Filter-Wert eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        val isDemoGlobal = switchDemoMode.isChecked
        val currentlyConnectedGlobal = if (isDemoGlobal) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()
        if (!currentlyConnectedGlobal) {
            Toast.makeText(this, "Nicht verbunden", Toast.LENGTH_SHORT).show()
            return
        }

        buttonSetFilter.isEnabled = false
        textViewStatus.text = "Starte Filter-Update..."

        lifecycleScope.launch {
            val totalCells = 8
            var successCount = 0

            for (i in 1..totalCells) {
                textViewStatus.text = "Setze Filter für Zelle $i/$totalCells..."
                val cellAddressChar = ('A'.code + i - 1).toChar().toString()
                val formattedFilterValue = filterValueString.padStart(6, '0')
                val commandPayload = "P$formattedFilterValue"
                val rawCommandForChecksum = "$cellAddressChar$commandPayload"
                val checksum = createDigitalChecksum(rawCommandForChecksum)
                val fullCommand = "$STX$cellAddressChar$commandPayload$checksum$ETX"

                Log.d("MainActivity", "Sende Filter-Befehl für Zelle $cellAddressChar: $fullCommand (Hex: ${fullCommand.map { it.code.toString(16).padStart(2, '0') }.joinToString(" ")})")

                val response = if (isDemoGlobal) {
                    fakeCommunicationManager.sendCommand(fullCommand)
                } else {
                    realCommunicationManager.sendCommand(fullCommand)
                }

                if (response != null) {
                    Log.d("MainActivity", "Antwort auf Filter-Setzen für Zelle $cellAddressChar: $response")
                    successCount++
                } else {
                    Log.w("MainActivity", "Keine Antwort auf Filter-Setzen für Zelle $cellAddressChar")
                }
                delay(300)
            }

            textViewStatus.text = "Filter-Update abgeschlossen: $successCount/$totalCells erfolgreich."
            Toast.makeText(this@MainActivity, "Filter-Update fertig!", Toast.LENGTH_LONG).show()
            buttonSetFilter.isEnabled = true
        }
    }


    private fun updateUIState(): Unit {
        val isDemo = switchDemoMode.isChecked
        val isConnected = if (isDemo) fakeCommunicationManager.isConnected() else realCommunicationManager.isConnected()

        editTextIpAddress.isEnabled = !isConnected && !isDemo
        editTextPort.isEnabled = !isConnected && !isDemo
        switchDemoMode.isEnabled = !isConnected
        buttonConnect.text = if (isConnected) "Trennen" else "Verbinden"
        buttonSetFilter.isEnabled = isConnected
        buttonGetInitialData.isEnabled = isConnected
        buttonGetCounts.isEnabled = isConnected
        buttonGetBaudrate.isEnabled = isConnected
        buttonGetTemperature.isEnabled = isConnected
        buttonGetDigitalFilter.isEnabled = isConnected
        buttonGetVersion.isEnabled = isConnected
    }

    private fun createDigitalChecksum(commandContent: String): String {
        var checksum = 0
        for (char_Renamed in commandContent) {
            checksum = checksum xor char_Renamed.code
        }
        return String.format("%02X", checksum)
    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy aufgerufen")
        disconnectFromServer()
        super.onDestroy()
    }
}
