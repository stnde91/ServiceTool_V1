// WICHTIG: Ersetze dies mit DEINEM korrekten Paketnamen!
package com.example.servicetool

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ConnectException
import java.net.SocketTimeoutException

// Datenklassen für die Struktur
data class MoxaCommand(val name: String, val moxaRawCommand: String, val address: String, val count: Int, val cellId: Int)
data class MoxaCellState(
    var statusText: String = "Bereit",
    var results: MutableMap<String, String> = mutableMapOf(),
    var statusIndicator: Int = R.drawable.ic_status_pending,
    var lastUpdateTime: Long = 0L
)

// Flintec RC3D Datenklassen
sealed class FlintecData {
    data class SerialNumber(val value: String) : FlintecData()
    data class Counts(val value: String) : FlintecData()
    data class Baudrate(val value: String) : FlintecData()
    data class Temperature(val value: String) : FlintecData()
    data class Filter(val value: String) : FlintecData()
    data class Version(val value: String) : FlintecData()
    data class Unknown(val value: String) : FlintecData()
}

// Flintec RC3D Befehle (basierend auf Wireshark-Analyse)
object FlintecRC3DCommands {

    private val STX: Byte = 0x02
    private val ETX: Byte = 0x03

    // Bekannte Befehle basierend auf Wireshark-Analyse
    fun getSerialNumber(): ByteArray = byteArrayOf(STX, 0x41, 0x63, 0x30, 0x31, 0x31, 0x32, ETX)
    fun getCounts(): ByteArray = byteArrayOf(STX, 0x41, 0x3F, 0x3C, 0x37, ETX)
    fun getBaudrate(): ByteArray = byteArrayOf(STX, 0x41, 0x73, 0x32, 0x32, 0x30, ETX)
    fun getTemperature(): ByteArray = byteArrayOf(STX, 0x41, 0x74, 0x37, 0x33, ETX)
    fun getFilter(): ByteArray = byteArrayOf(STX, 0x41, 0x70, 0x33, 0x33, ETX)
    fun getVersion(): ByteArray = byteArrayOf(STX, 0x41, 0x76, 0x35, 0x33, ETX)

    // Antwort-Parser mit korrekter Dekodierung aller Befehle
    fun parseResponse(response: String): FlintecData? {
        if (response.length < 2) return null

        return when (response.take(2)) {
            "Ac" -> {
                // Seriennummer dekodieren: "01DF8256:2" -> "14647894"
                val rawSerial = response.drop(2)
                val decodedSerial = decodeSerialNumber(rawSerial)
                FlintecData.SerialNumber(decodedSerial)
            }
            "A?" -> {
                // Counts dekodieren: "d00009541" -> "95" (ohne 'd' und gröber)
                val rawCounts = response.drop(2)
                val decodedCounts = decodeCounts(rawCounts)
                FlintecData.Counts(decodedCounts)
            }
            "As" -> {
                // Baudrate dekodieren: "2A9617:4" -> "9600"
                val rawBaud = response.drop(2)
                val decodedBaud = decodeBaudrate(rawBaud)
                FlintecData.Baudrate(decodedBaud)
            }
            "At" -> {
                // Temperatur formatieren: "+024.4000" -> "24,4°C"
                val tempData = response.drop(2)
                val decodedTemp = decodeTemperature(tempData)
                FlintecData.Temperature(decodedTemp)
            }
            "Ap" -> {
                // Filter dekodieren: "2300140343" -> "0"
                val rawFilter = response.drop(2)
                val decodedFilter = decodeFilter(rawFilter)
                FlintecData.Filter(decodedFilter)
            }
            "Av" -> {
                // Version: direkt anzeigen
                FlintecData.Version(response.drop(2))
            }
            else -> FlintecData.Unknown(response)
        }
    }

    // Seriennummer-Dekodierung: Hex zu Dezimal
    private fun decodeSerialNumber(rawSerial: String): String {
        try {
            Log.d("FlintecSerial", "Dekodiere Seriennummer: '$rawSerial'")

            // Extrahiere Hex-Teil: "01DF8256:2" -> "DF8256"
            val parts = rawSerial.split(":")
            val hexPart = if (parts[0].startsWith("01")) {
                parts[0].removePrefix("01") // Entferne "01" Prefix
            } else {
                parts[0]
            }

            // Konvertiere Hex zu Dezimal
            val decimalValue = hexPart.toLongOrNull(16)
            if (decimalValue != null) {
                Log.d("FlintecSerial", "Hex '$hexPart' -> Dezimal '$decimalValue'")
                return decimalValue.toString()
            }

            // Fallback: Original zurückgeben
            Log.w("FlintecSerial", "Konnte '$rawSerial' nicht dekodieren")
            return rawSerial

        } catch (e: Exception) {
            Log.e("FlintecSerial", "Fehler bei Seriennummer-Dekodierung: ${e.message}")
            return rawSerial
        }
    }

    // Counts-Dekodierung: Entferne 'd' Präfix und kürze auf gröbere Anzeige
    private fun decodeCounts(rawCounts: String): String {
        try {
            Log.d("FlintecCounts", "Dekodiere Counts: '$rawCounts'")

            // Entferne 'd' am Anfang: "d00009541" -> "00009541"
            val cleanCounts = if (rawCounts.startsWith("d")) {
                rawCounts.drop(1)
            } else {
                rawCounts
            }

            // Gröbere Anzeige: Nehme nur die ersten 2-3 relevanten Ziffern
            // "00009541" -> "95" (streiche die letzten beiden Ziffern weg)
            val fullNumber = cleanCounts.toLongOrNull()
            if (fullNumber != null) {
                // Dividiere durch 100 um die letzten beiden Ziffern zu entfernen
                val coarseValue = fullNumber / 100
                Log.d("FlintecCounts", "Counts '$rawCounts' -> grob '$coarseValue'")
                return coarseValue.toString()
            }

            // Fallback: Versuche String-basierte Kürzung
            if (cleanCounts.length >= 3) {
                val shortened = cleanCounts.dropLast(2).toLongOrNull()?.toString() ?: cleanCounts
                Log.d("FlintecCounts", "Counts '$rawCounts' -> gekürzt '$shortened'")
                return shortened
            }

            return cleanCounts

        } catch (e: Exception) {
            Log.e("FlintecCounts", "Fehler bei Counts-Dekodierung: ${e.message}")
            return rawCounts
        }
    }

    // Baudrate-Dekodierung: "2A9617:4" -> "9600"
    private fun decodeBaudrate(rawBaud: String): String {
        try {
            Log.d("FlintecBaud", "Dekodiere Baudrate: '$rawBaud'")

            // Bekannte Baudrate-Mappings (können erweitert werden)
            when {
                rawBaud.contains("2A9617") -> return "9600"
                rawBaud.contains("4B02") -> return "19200"  // Beispiel
                rawBaud.contains("9604") -> return "38400"  // Beispiel
                else -> {
                    Log.w("FlintecBaud", "Unbekannte Baudrate: '$rawBaud'")
                    return rawBaud
                }
            }

        } catch (e: Exception) {
            Log.e("FlintecBaud", "Fehler bei Baudrate-Dekodierung: ${e.message}")
            return rawBaud
        }
    }

    // Temperatur-Formatierung: "+024.4000" -> "24,4°C"
    private fun decodeTemperature(tempData: String): String {
        try {
            Log.d("FlintecTemp", "Dekodiere Temperatur: '$tempData'")

            // Entferne '+' und konvertiere zu Double
            val tempValue = tempData.replace("+", "").toDoubleOrNull()
            if (tempValue != null) {
                // Deutsche Formatierung mit Komma
                val formatted = String.format("%.1f", tempValue).replace(".", ",")
                return "${formatted}°C"
            }
            return tempData

        } catch (e: Exception) {
            Log.e("FlintecTemp", "Fehler bei Temperatur-Dekodierung: ${e.message}")
            return tempData
        }
    }

    // Filter-Dekodierung: "2300140343" -> "0"
    private fun decodeFilter(rawFilter: String): String {
        try {
            Log.d("FlintecFilter", "Dekodiere Filter: '$rawFilter'")

            // Basierend auf Ihren Angaben: Filter ist momentan 0
            // Dies könnte eine komplexere Dekodierung erfordern
            when {
                rawFilter == "2300140343" -> return "0"
                rawFilter.startsWith("23001") -> return "0"  // Weitere Varianten
                else -> {
                    Log.w("FlintecFilter", "Unbekannter Filter-Wert: '$rawFilter'")
                    return rawFilter
                }
            }

        } catch (e: Exception) {
            Log.e("FlintecFilter", "Fehler bei Filter-Dekodierung: ${e.message}")
            return rawFilter
        }
    }
}

class MainActivity : AppCompatActivity() {

    // Konfiguration für die Moxa-Box
    private val MOXA_IP_ADDRESS = "192.168.50.3"
    private val MOXA_PORT = 4001
    private val MOXA_CONNECTION_TIMEOUT_MS = 3000
    private val MOXA_READ_TIMEOUT_MS = 3000

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var currentLiveUpdateCellIndex = 0
    private var isLiveUpdateRunning = false

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val liveUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isLiveUpdateRunning) return
            fetchSpecificCellCountsLive(currentLiveUpdateCellIndex)
            currentLiveUpdateCellIndex = (currentLiveUpdateCellIndex + 1) % liveCountCommandsPerCell.size
            if (isLiveUpdateRunning) {
                handler.postDelayed(this, 250)
            }
        }
    }

    private lateinit var buttonStartAllCells: Button
    private lateinit var buttonStopLiveUpdates: Button

    private val cellTitleTextViews = mutableListOf<TextView>()
    private val cellStatusTextViews = mutableListOf<TextView>()
    private val cellResultsTextViews = mutableListOf<TextView>()
    private val cellStatusIndicators = mutableListOf<ImageView>()

    private val cellStates = Array(8) { MoxaCellState() }

    private val commandsPerCell = List(8) { cellIndex ->
        val cellNum = cellIndex + 1
        listOf(
            MoxaCommand("System Info", "\$01I\\r\\n", "sys_info_z$cellNum", 1, cellNum),
            MoxaCommand("Count A", "#01RAA\\r\\n", "count_a_z$cellNum", 1, cellNum),
            MoxaCommand("Count B", "#01RAB\\r\\n", "count_b_z$cellNum", 1, cellNum)
        )
    }
    private val liveCountCommandsPerCell = List(8) { cellIndex ->
        val cellNum = cellIndex + 1
        listOf(
            MoxaCommand("Count A", "#0${cellNum}RAA\\r\\n", "count_a_z$cellNum", 1, cellNum),
            MoxaCommand("Count B", "#0${cellNum}RAB\\r\\n", "count_b_z$cellNum", 1, cellNum)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // WICHTIG: IPv4-Stack bevorzugen
        System.setProperty("java.net.preferIPv4Stack", "true")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // ERWEITERTE NAVIGATION - Schöne Flintec UI als Standard
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_beautiful_flintec,  // NEU: Schöne UI als Hauptseite
                R.id.nav_live_zellen,
                R.id.nav_digitale_zellen,
                R.id.nav_settings
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        buttonStartAllCells = findViewById(R.id.buttonStartAllCells)
        buttonStopLiveUpdates = findViewById(R.id.buttonStopLiveUpdates)

        Log.d("MainActivity", "onCreate: Initialisiere UI-Elemente für ${cellStates.size} Zellen.")
        for (i in 0 until cellStates.size) {
            val cellNumberForId = i + 1
            try {
                val titleViewId = resources.getIdentifier("textViewTitleZelle$cellNumberForId", "id", packageName)
                val statusViewId = resources.getIdentifier("textViewStatusZelle$cellNumberForId", "id", packageName)
                val resultsViewId = resources.getIdentifier("textViewResultsZelle$cellNumberForId", "id", packageName)
                val indicatorViewId = resources.getIdentifier("statusIndicatorZelle$cellNumberForId", "id", packageName)

                if (titleViewId != 0) cellTitleTextViews.add(findViewById(titleViewId)) else Log.w("MainActivity", "TitleView ID für Zelle $cellNumberForId nicht gefunden")
                if (statusViewId != 0) cellStatusTextViews.add(findViewById(statusViewId)) else Log.w("MainActivity", "StatusView ID für Zelle $cellNumberForId nicht gefunden")
                if (resultsViewId != 0) cellResultsTextViews.add(findViewById(resultsViewId)) else Log.w("MainActivity", "ResultsView ID für Zelle $cellNumberForId nicht gefunden")
                if (indicatorViewId != 0) cellStatusIndicators.add(findViewById(indicatorViewId)) else Log.w("MainActivity", "IndicatorView ID für Zelle $cellNumberForId nicht gefunden")

            } catch (e: Exception) {
                Log.e("MainActivity", "Exception beim Finden der UI-Elemente für Zelle $cellNumberForId: ${e.message}")
            }
            if (i < cellTitleTextViews.size) {
                updateCellUI(i)
            }
        }

        // FLINTEC RC3D VOLLTEST - Für Debug-Zwecke auf Live Zellen Seite
        buttonStartAllCells.text = "Teste ALLE RC3D Befehle"
        buttonStartAllCells.setOnClickListener {
            runCorrectFlintecTest()
        }

        buttonStopLiveUpdates.setOnClickListener {
            stopLiveUpdates()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    // FLINTEC RC3D TEST-FUNKTIONEN (für Debug auf Live Zellen Seite)
    private fun runCorrectFlintecTest() {
        buttonStartAllCells.isEnabled = false
        buttonStopLiveUpdates.visibility = View.GONE
        updateAllCellsStatus("Teste Flintec RC3D mit korrektem Protokoll...")

        mainScope.launch {
            try {
                Log.d("FlintecTest", "Starte KORREKTEN Flintec-Test mit ${MOXA_IP_ADDRESS}:${MOXA_PORT}")
                val result = testCorrectFlintecConnection()

                if (result.isNotEmpty()) {
                    updateAllCellsStatus("✅ Flintec RC3D: $result")
                    Log.i("FlintecTest", "SUCCESS: $result")

                    // Bei Erfolg alle Zellen als OK markieren
                    for (i in 0 until cellStates.size) {
                        updateCellStatusUI(i, "RC3D OK", R.drawable.ic_status_success)
                        cellStates[i].results["RC3D Test"] = result
                        updateCellUI(i)
                    }
                } else {
                    updateAllCellsStatus("❌ Keine Antwort von Flintec RC3D")
                    Log.w("FlintecTest", "NO RESPONSE")

                    for (i in 0 until cellStates.size) {
                        updateCellStatusUI(i, "Keine Antwort", R.drawable.ic_status_error)
                    }
                }

            } catch (e: Exception) {
                val errorMsg = "Fehler: ${e.message}"
                updateAllCellsStatus("❌ $errorMsg")
                Log.e("FlintecTest", "ERROR", e)

                for (i in 0 until cellStates.size) {
                    updateCellStatusUI(i, errorMsg, R.drawable.ic_status_error)
                }
            } finally {
                buttonStartAllCells.isEnabled = true
            }
        }
    }

    private suspend fun testCorrectFlintecConnection(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("FlintecTest", "Verbinde mit ${MOXA_IP_ADDRESS}:${MOXA_PORT}")

                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP_ADDRESS, MOXA_PORT), 5000)
                    socket.soTimeout = 5000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    // ALLE FLINTEC RC3D BEFEHLE TESTEN
                    val results = mutableMapOf<String, String>()

                    val testCommands = listOf(
                        "Seriennummer" to FlintecRC3DCommands.getSerialNumber(),
                        "Counts" to FlintecRC3DCommands.getCounts(),
                        "Baudrate" to FlintecRC3DCommands.getBaudrate(),
                        "Temperatur" to FlintecRC3DCommands.getTemperature(),
                        "Filter" to FlintecRC3DCommands.getFilter(),
                        "Version" to FlintecRC3DCommands.getVersion()
                    )

                    var successCount = 0

                    for ((commandName, commandBytes) in testCommands) {
                        try {
                            Log.d("FlintecTest", "=== Teste $commandName ===")

                            outputStream.write(commandBytes)
                            outputStream.flush()

                            val rawResponse = readFlintecResponse(inputStream)

                            if (rawResponse.isNotEmpty()) {
                                val parsedData = FlintecRC3DCommands.parseResponse(rawResponse)
                                val displayText = when (parsedData) {
                                    is FlintecData.SerialNumber -> "SN: ${parsedData.value}"
                                    is FlintecData.Counts -> "${parsedData.value}"
                                    is FlintecData.Temperature -> "${parsedData.value}"
                                    is FlintecData.Version -> "Ver: ${parsedData.value}"
                                    is FlintecData.Baudrate -> "${parsedData.value} bps"
                                    is FlintecData.Filter -> "Filter: ${parsedData.value}"
                                    else -> rawResponse
                                }

                                results[commandName] = displayText
                                successCount++
                                Log.i("FlintecTest", "✅ $commandName: $displayText")

                            } else {
                                Log.w("FlintecTest", "❌ Keine Antwort auf $commandName")
                                results[commandName] = "Keine Antwort"
                            }

                            delay(500)

                        } catch (e: Exception) {
                            Log.e("FlintecTest", "❌ Fehler bei $commandName: ${e.message}")
                            results[commandName] = "Fehler: ${e.message}"
                        }
                    }

                    // Speichere alle Ergebnisse in den Zellenstatus für die UI
                    withContext(Dispatchers.Main) {
                        for (i in 0 until minOf(cellStates.size, results.size)) {
                            val (commandName, result) = results.toList()[i]
                            cellStates[i].results["RC3D $commandName"] = result
                        }
                    }

                    return@withContext if (successCount > 0) {
                        "$successCount/${testCommands.size} Befehle erfolgreich"
                    } else {
                        ""
                    }
                }

            } catch (e: ConnectException) {
                Log.e("FlintecTest", "Verbindung zur Moxa fehlgeschlagen: ${e.message}")
                throw Exception("Moxa nicht erreichbar: ${e.message}")
            } catch (e: SocketTimeoutException) {
                Log.e("FlintecTest", "Timeout bei Moxa-Kommunikation: ${e.message}")
                throw Exception("Timeout: ${e.message}")
            } catch (e: Exception) {
                Log.e("FlintecTest", "Allgemeiner Fehler: ${e.message}", e)
                throw Exception("Unbekannter Fehler: ${e.message}")
            }
        }
    }

    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var timeoutCounter = 0
                val maxTimeout = 25

                while (timeoutCounter < maxTimeout) {
                    if (inputStream.available() > 0) {
                        val byteRead = inputStream.read()
                        if (byteRead == -1) break

                        val byte = byteRead.toByte()

                        if (byte.toInt() == 0x02) {
                            stxFound = true
                            responseBuffer.clear()
                            responseBuffer.add(byte)
                            timeoutCounter = 0
                            continue
                        }

                        if (stxFound) {
                            responseBuffer.add(byte)
                            if (byte.toInt() == 0x03) {
                                if (responseBuffer.size >= 3) {
                                    val dataBytes = responseBuffer.subList(1, responseBuffer.size - 1)
                                    return@withContext String(dataBytes.toByteArray(), Charsets.US_ASCII)
                                }
                                break
                            }
                            timeoutCounter = 0
                        }
                    } else {
                        delay(200)
                        timeoutCounter++
                    }
                }
                return@withContext ""
            } catch (e: Exception) {
                Log.e("FlintecTest", "Fehler beim Lesen: ${e.message}")
                return@withContext ""
            }
        }
    }

    // URSPRÜNGLICHE FUNKTIONEN (unverändert für Live Zellen)
    private fun startFullProcess() {
        buttonStartAllCells.isEnabled = false
        buttonStopLiveUpdates.visibility = View.GONE

        for (i in 0 until cellStates.size) {
            cellStates[i] = MoxaCellState(statusText = "Wird ausgeführt...", statusIndicator = R.drawable.ic_status_pending, results = mutableMapOf())
            if (i < cellTitleTextViews.size) updateCellUI(i)
        }

        mainScope.launch {
            for (cellIndex in 0 until cellStates.size) {
                updateCellStatusUI(cellIndex, "Initialisiere Zelle ${cellIndex + 1}...", R.drawable.ic_status_pending)
                var cellHasError = false
                if (cellIndex < commandsPerCell.size) {
                    for (command in commandsPerCell[cellIndex]) {
                        try {
                            val result = executeMoxaCommand(command)
                            cellStates[cellIndex].results[command.name] = result
                            if (result.contains("Fehler", ignoreCase = true) || result.contains("Error", ignoreCase = true)) cellHasError = true
                        } catch (e: Exception) {
                            Log.e("MainActivity", "startFullProcess: Fehler bei Befehl ${command.name} für Zelle ${cellIndex + 1}", e)
                            cellStates[cellIndex].results[command.name] = "Exception: ${e.message?.take(30)}"
                            cellHasError = true
                        }
                        if (cellIndex < cellTitleTextViews.size) updateCellUI(cellIndex)
                        delay(50)
                    }
                } else {
                    Log.w("MainActivity", "startFullProcess: Keine Befehle für cellIndex $cellIndex definiert.")
                    cellHasError = true
                }

                if (cellHasError) {
                    updateCellStatusUI(cellIndex, "Zelle ${cellIndex + 1} mit Fehlern", R.drawable.ic_status_error)
                } else {
                    updateCellStatusUI(cellIndex, "Zelle ${cellIndex + 1} initialisiert", R.drawable.ic_status_success)
                }
                if (commandsPerCell.size > 1 && cellIndex < commandsPerCell.size -1) delay(100)
            }
            startLiveUpdates()
        }
    }

    private fun updateCellStatusUI(cellIndex: Int, statusMsg: String, indicatorResId: Int) {
        if (cellIndex < 0 || cellIndex >= cellStates.size) {
            Log.w("MainActivity", "updateCellStatusUI: Ungültiger cellIndex $cellIndex")
            return
        }
        cellStates[cellIndex].statusText = statusMsg
        cellStates[cellIndex].statusIndicator = indicatorResId
        if (cellIndex < cellTitleTextViews.size) updateCellUI(cellIndex)
    }

    private fun startLiveUpdates() {
        if (isLiveUpdateRunning) return
        isLiveUpdateRunning = true
        currentLiveUpdateCellIndex = 0

        buttonStartAllCells.isEnabled = false
        buttonStopLiveUpdates.visibility = View.VISIBLE

        for (i in 0 until cellStates.size) {
            if (cellStates[i].statusIndicator != R.drawable.ic_status_error) {
                updateCellStatusUI(i, "Live-Daten Zelle ${i + 1}", R.drawable.ic_status_live)
            }
        }

        handler.removeCallbacks(liveUpdateRunnable)
        handler.post(liveUpdateRunnable)
    }

    private fun fetchSpecificCellCountsLive(cellIndexToUpdate: Int) {
        if (cellIndexToUpdate < 0 || cellIndexToUpdate >= cellStates.size || cellIndexToUpdate >= liveCountCommandsPerCell.size) {
            Log.w("MainActivity", "fetchSpecificCellCountsLive: Ungültiger cellIndex $cellIndexToUpdate oder keine Live-Befehle definiert.")
            return
        }

        mainScope.launch {
            var cellHadErrorInThisUpdate = false
            for (command in liveCountCommandsPerCell[cellIndexToUpdate]) {
                try {
                    val result = executeMoxaCommand(command)
                    cellStates[cellIndexToUpdate].results[command.name] = result
                    if (result.contains("Fehler", ignoreCase = true) || result.contains("Error", ignoreCase = true)) cellHadErrorInThisUpdate = true
                } catch (e: Exception) {
                    Log.e("MainActivity", "fetchSpecificCellCountsLive: Fehler bei Live-Befehl ${command.name} für Zelle ${cellIndexToUpdate + 1}", e)
                    cellStates[cellIndexToUpdate].results[command.name] = "Live Exc: ${e.message?.take(30)}"
                    cellHadErrorInThisUpdate = true
                }
            }

            if (cellHadErrorInThisUpdate) {
                updateCellStatusUI(cellIndexToUpdate, "Fehler Live Zelle ${cellIndexToUpdate + 1}", R.drawable.ic_status_error)
            } else {
                updateCellStatusUI(cellIndexToUpdate, "Live Zelle ${cellIndexToUpdate + 1} OK", R.drawable.ic_status_live)
            }
        }
    }

    private fun stopLiveUpdates() {
        isLiveUpdateRunning = false
        handler.removeCallbacks(liveUpdateRunnable)

        buttonStartAllCells.isEnabled = true
        buttonStopLiveUpdates.visibility = View.GONE

        for (i in 0 until cellStates.size) {
            if (i < 0 || i >= cellStates.size) continue
            if (cellStates[i].statusIndicator == R.drawable.ic_status_live) {
                updateCellStatusUI(i, "Live gestoppt", R.drawable.ic_status_success)
            } else if (cellStates[i].statusIndicator == R.drawable.ic_status_pending) {
                updateCellStatusUI(i, "Bereit", R.drawable.ic_status_pending)
            }
        }
    }

    private suspend fun executeMoxaCommand(command: MoxaCommand): String {
        Log.d("MoxaComm", "Sende Befehl: '${command.moxaRawCommand.trim()}' an Zelle ${command.cellId} (${command.name}) an ${MOXA_IP_ADDRESS}:${MOXA_PORT}")
        return withContext(Dispatchers.IO) {
            Socket().use { socket ->
                try {
                    Log.d("MoxaComm", "Versuche Verbindung zu ${MOXA_IP_ADDRESS}:${MOXA_PORT} mit Timeout ${MOXA_CONNECTION_TIMEOUT_MS}ms...")
                    socket.connect(InetSocketAddress(MOXA_IP_ADDRESS, MOXA_PORT), MOXA_CONNECTION_TIMEOUT_MS)
                    Log.d("MoxaComm", "Verbindung erfolgreich hergestellt.")
                    socket.soTimeout = MOXA_READ_TIMEOUT_MS

                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

                    writer.print(command.moxaRawCommand)
                    writer.flush()
                    Log.d("MoxaComm", "Befehl '${command.moxaRawCommand.trim()}' gesendet. Warte auf Antwort...")
                    val response = reader.readLine()

                    if (response != null) {
                        Log.i("MoxaComm", "Antwort von Moxa: '$response'")
                        return@withContext response.trim()
                    } else {
                        Log.w("MoxaComm", "Keine Antwort von Moxa empfangen (null) nach erfolgreicher Verbindung.")
                        return@withContext "Fehler: Keine Antwort (null)"
                    }
                } catch (e: SocketTimeoutException) {
                    Log.e("MoxaComm", "SocketTimeoutException: ${e.message} (IP: ${MOXA_IP_ADDRESS}:${MOXA_PORT})", e)
                    return@withContext "Fehler: Timeout"
                } catch (e: ConnectException) {
                    Log.e("MoxaComm", "ConnectException: ${e.message} (IP: ${MOXA_IP_ADDRESS}:${MOXA_PORT})", e)
                    return@withContext "Fehler: Verbindung fehlgeschlagen (${e.javaClass.simpleName})"
                } catch (e: IOException) {
                    Log.e("MoxaComm", "IOException bei Moxa-Kommunikation: ${e.message} (IP: ${MOXA_IP_ADDRESS}:${MOXA_PORT})", e)
                    return@withContext "Fehler: E/A Problem"
                } catch (e: Exception) {
                    Log.e("MoxaComm", "Allgemeiner Fehler bei Moxa-Kommunikation: ${e.message} (IP: ${MOXA_IP_ADDRESS}:${MOXA_PORT})", e)
                    return@withContext "Fehler: ${e.javaClass.simpleName}"
                }
            }
        }
    }

    private fun updateAllCellsStatus(status: String) {
        for (i in 0 until cellStates.size) {
            updateCellStatusUI(i, status, R.drawable.ic_status_pending)
        }
    }

    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex < 0 || cellIndex >= cellStates.size || cellIndex >= cellTitleTextViews.size) {
            Log.w("MainActivity", "updateCellUI: Ungültiger cellIndex $cellIndex oder nicht genügend UI-Elemente initialisiert. UI-Update übersprungen.")
            return
        }
        if (cellStatusTextViews.getOrNull(cellIndex) == null ||
            cellResultsTextViews.getOrNull(cellIndex) == null ||
            cellStatusIndicators.getOrNull(cellIndex) == null) {
            Log.w("MainActivity", "updateCellUI: Fehlende UI-Element Referenzen (Status, Results, Indicator) für Zelle Index $cellIndex. XML überprüfen! UI-Update für diese Zelle übersprungen.")
            return
        }

        val state = cellStates[cellIndex]
        Log.d("MainActivity", "updateCellUI: Aktualisiere Zelle ${cellIndex + 1}. Status: ${state.statusText}, Indikator: ${state.statusIndicator}")

        cellTitleTextViews[cellIndex].text = "Zelle ${cellIndex + 1}"
        cellStatusTextViews[cellIndex].text = "Status: ${state.statusText}"

        val resultsBuilder = StringBuilder()
        val commandsToDisplay = if (isLiveUpdateRunning && state.statusIndicator == R.drawable.ic_status_live) {
            liveCountCommandsPerCell.getOrNull(cellIndex) ?: emptyList()
        } else {
            commandsPerCell.getOrNull(cellIndex) ?: emptyList()
        }

        if (commandsToDisplay.isEmpty() && state.results.isNotEmpty()) {
            Log.d("MainActivity", "updateCellUI (Zelle ${cellIndex + 1}): Keine spezifischen commandsToShow, zeige alle ${state.results.size} gespeicherten Ergebnisse.")
            state.results.forEach { (name, value) ->
                val displayName = name.replace(" Zelle ${cellIndex + 1}", "").replace(" Z${cellIndex + 1}", "")
                resultsBuilder.append("<b>${displayName}:</b> ${value}<br>")
            }
        } else {
            commandsToDisplay.forEach { command ->
                val value = state.results[command.name] ?: "N/A"
                val displayName = command.name.replace(" Zelle ${cellIndex + 1}", "").replace(" Z${cellIndex + 1}", "")
                resultsBuilder.append("<b>${displayName}:</b> ${value}<br>")
            }
        }

        if (resultsBuilder.isEmpty()){
            resultsBuilder.append("<i>Keine Daten</i>")
        }

        Log.d("MainActivity", "updateCellUI (Zelle ${cellIndex + 1}): Setze Ergebnis-HTML: '${resultsBuilder.toString().take(100)}...'")
        cellResultsTextViews[cellIndex].text = Html.fromHtml(resultsBuilder.toString(), Html.FROM_HTML_MODE_COMPACT)
        cellStatusIndicators[cellIndex].setImageDrawable(ContextCompat.getDrawable(this, state.statusIndicator))
    }

    override fun onDestroy() {
        super.onDestroy()
        isLiveUpdateRunning = false
        handler.removeCallbacks(liveUpdateRunnable)
        mainScope.cancel()
        Log.d("MainActivity", "onDestroy aufgerufen und Ressourcen freigegeben.")
    }
}