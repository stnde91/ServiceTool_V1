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
import java.util.Locale

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
                val rawSerial = response.drop(2)
                val decodedSerial = decodeSerialNumber(rawSerial)
                FlintecData.SerialNumber(decodedSerial)
            }
            "A?" -> {
                val rawCounts = response.drop(2)
                val decodedCounts = decodeCounts(rawCounts)
                FlintecData.Counts(decodedCounts)
            }
            "As" -> {
                val rawBaud = response.drop(2)
                val decodedBaud = decodeBaudrate(rawBaud)
                FlintecData.Baudrate(decodedBaud)
            }
            "At" -> {
                val tempData = response.drop(2)
                val decodedTemp = decodeTemperature(tempData)
                FlintecData.Temperature(decodedTemp)
            }
            "Ap" -> {
                val rawFilter = response.drop(2)
                val decodedFilter = decodeFilter(rawFilter)
                FlintecData.Filter(decodedFilter)
            }
            "Av" -> {
                FlintecData.Version(response.drop(2))
            }
            else -> FlintecData.Unknown(response)
        }
    }

    private fun decodeSerialNumber(rawSerial: String): String {
        try {
            val parts = rawSerial.split(":")
            val hexPart = if (parts[0].startsWith("01")) {
                parts[0].removePrefix("01")
            } else {
                parts[0]
            }

            val decimalValue = hexPart.toLongOrNull(16)
            if (decimalValue != null) {
                return decimalValue.toString()
            }
            return rawSerial
        } catch (e: Exception) {
            return rawSerial
        }
    }

    private fun decodeCounts(rawCounts: String): String {
        try {
            val cleanCounts = if (rawCounts.startsWith("d")) {
                rawCounts.drop(1)
            } else {
                rawCounts
            }

            val fullNumber = cleanCounts.toLongOrNull()
            if (fullNumber != null) {
                val coarseValue = fullNumber / 100
                return coarseValue.toString()
            }

            if (cleanCounts.length >= 3) {
                val shortened = cleanCounts.dropLast(2).toLongOrNull()?.toString() ?: cleanCounts
                return shortened
            }
            return cleanCounts
        } catch (e: Exception) {
            return rawCounts
        }
    }

    private fun decodeBaudrate(rawBaud: String): String {
        return when {
            rawBaud.contains("2A9617") -> "9600"
            rawBaud.contains("4B02") -> "19200"
            rawBaud.contains("9604") -> "38400"
            else -> rawBaud
        }
    }

    // KORRIGIERTE Temperatur-Formatierung: "+024.4000" -> "24,4°C"
    private fun decodeTemperature(tempData: String): String {
        try {
            val cleanTempString = tempData.replace("+", "").trim()
            val tempValue = cleanTempString.toDoubleOrNull()

            if (tempValue != null) {
                val formatted = String.format(Locale.GERMAN, "%.1f", tempValue)
                return "${formatted}°C"
            }
            return "${tempData}°C"
        } catch (e: Exception) {
            return "${tempData}°C"
        }
    }

    private fun decodeFilter(rawFilter: String): String {
        return when {
            rawFilter == "2300140343" -> "0"
            rawFilter.startsWith("23001") -> "0"
            else -> rawFilter
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

    override fun onCreate(savedInstanceState: Bundle?) {
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

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_beautiful_flintec,
                R.id.nav_live_zellen,
                R.id.nav_digitale_zellen,
                R.id.nav_settings
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        buttonStartAllCells = findViewById(R.id.buttonStartAllCells)
        buttonStopLiveUpdates = findViewById(R.id.buttonStopLiveUpdates)

        initializeCellViews()

        buttonStartAllCells.text = "Teste ALLE RC3D Befehle"
        buttonStartAllCells.setOnClickListener {
            runCorrectFlintecTest()
        }

        buttonStopLiveUpdates.setOnClickListener {
            stopLiveUpdates()
        }
    }

    private fun initializeCellViews() {
        for (i in 0 until cellStates.size) {
            val cellNumberForId = i + 1
            try {
                val titleViewId = resources.getIdentifier("textViewTitleZelle$cellNumberForId", "id", packageName)
                val statusViewId = resources.getIdentifier("textViewStatusZelle$cellNumberForId", "id", packageName)
                val resultsViewId = resources.getIdentifier("textViewResultsZelle$cellNumberForId", "id", packageName)
                val indicatorViewId = resources.getIdentifier("statusIndicatorZelle$cellNumberForId", "id", packageName)

                if (titleViewId != 0) cellTitleTextViews.add(findViewById(titleViewId))
                if (statusViewId != 0) cellStatusTextViews.add(findViewById(statusViewId))
                if (resultsViewId != 0) cellResultsTextViews.add(findViewById(resultsViewId))
                if (indicatorViewId != 0) cellStatusIndicators.add(findViewById(indicatorViewId))

            } catch (e: Exception) {
                Log.e("MainActivity", "Exception beim Finden der UI-Elemente für Zelle $cellNumberForId: ${e.message}")
            }

            if (i < cellTitleTextViews.size) {
                updateCellUI(i)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun runCorrectFlintecTest() {
        buttonStartAllCells.isEnabled = false
        buttonStopLiveUpdates.visibility = View.GONE
        updateAllCellsStatus("Teste Flintec RC3D mit korrektem Protokoll...")

        mainScope.launch {
            try {
                val result = testCorrectFlintecConnection()

                if (result.isNotEmpty()) {
                    updateAllCellsStatus("✅ Flintec RC3D: $result")

                    for (i in 0 until cellStates.size) {
                        updateCellStatusUI(i, "RC3D OK", R.drawable.ic_status_success)
                        cellStates[i].results["RC3D Test"] = result
                        updateCellUI(i)
                    }
                } else {
                    updateAllCellsStatus("❌ Keine Antwort von Flintec RC3D")

                    for (i in 0 until cellStates.size) {
                        updateCellStatusUI(i, "Keine Antwort", R.drawable.ic_status_error)
                    }
                }

            } catch (e: Exception) {
                val errorMsg = "Fehler: ${e.message}"
                updateAllCellsStatus("❌ $errorMsg")

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
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP_ADDRESS, MOXA_PORT), 5000)
                    socket.soTimeout = 5000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

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

                            } else {
                                results[commandName] = "Keine Antwort"
                            }

                            delay(500)

                        } catch (e: Exception) {
                            results[commandName] = "Fehler: ${e.message}"
                        }
                    }

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
                throw Exception("Moxa nicht erreichbar: ${e.message}")
            } catch (e: SocketTimeoutException) {
                throw Exception("Timeout: ${e.message}")
            } catch (e: Exception) {
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
                return@withContext ""
            }
        }
    }

    private fun updateCellStatusUI(cellIndex: Int, statusMsg: String, indicatorResId: Int) {
        if (cellIndex < 0 || cellIndex >= cellStates.size) return

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
            return
        }

        mainScope.launch {
            var cellHadErrorInThisUpdate = false
            for (command in liveCountCommandsPerCell[cellIndexToUpdate]) {
                try {
                    val result = executeMoxaCommand(command)
                    cellStates[cellIndexToUpdate].results[command.name] = result
                    if (result.contains("Fehler", ignoreCase = true) || result.contains("Error", ignoreCase = true)) {
                        cellHadErrorInThisUpdate = true
                    }
                } catch (e: Exception) {
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
            if (cellStates[i].statusIndicator == R.drawable.ic_status_live) {
                updateCellStatusUI(i, "Live gestoppt", R.drawable.ic_status_success)
            } else if (cellStates[i].statusIndicator == R.drawable.ic_status_pending) {
                updateCellStatusUI(i, "Bereit", R.drawable.ic_status_pending)
            }
        }
    }

    private suspend fun executeMoxaCommand(command: MoxaCommand): String {
        return withContext(Dispatchers.IO) {
            Socket().use { socket ->
                try {
                    socket.connect(InetSocketAddress(MOXA_IP_ADDRESS, MOXA_PORT), MOXA_CONNECTION_TIMEOUT_MS)
                    socket.soTimeout = MOXA_READ_TIMEOUT_MS

                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

                    writer.print(command.moxaRawCommand)
                    writer.flush()
                    val response = reader.readLine()

                    if (response != null) {
                        return@withContext response.trim()
                    } else {
                        return@withContext "Fehler: Keine Antwort (null)"
                    }
                } catch (e: SocketTimeoutException) {
                    return@withContext "Fehler: Timeout"
                } catch (e: ConnectException) {
                    return@withContext "Fehler: Verbindung fehlgeschlagen"
                } catch (e: IOException) {
                    return@withContext "Fehler: E/A Problem"
                } catch (e: Exception) {
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
            return
        }
        if (cellStatusTextViews.getOrNull(cellIndex) == null ||
            cellResultsTextViews.getOrNull(cellIndex) == null ||
            cellStatusIndicators.getOrNull(cellIndex) == null) {
            return
        }

        val state = cellStates[cellIndex]

        cellTitleTextViews[cellIndex].text = "Zelle ${cellIndex + 1}"
        cellStatusTextViews[cellIndex].text = "Status: ${state.statusText}"

        val resultsBuilder = StringBuilder()
        val commandsToDisplay = if (isLiveUpdateRunning && state.statusIndicator == R.drawable.ic_status_live) {
            liveCountCommandsPerCell.getOrNull(cellIndex) ?: emptyList()
        } else {
            commandsPerCell.getOrNull(cellIndex) ?: emptyList()
        }

        if (commandsToDisplay.isEmpty() && state.results.isNotEmpty()) {
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

        if (resultsBuilder.isEmpty()) {
            resultsBuilder.append("<i>Keine Daten</i>")
        }

        cellResultsTextViews[cellIndex].text = Html.fromHtml(resultsBuilder.toString(), Html.FROM_HTML_MODE_COMPACT)
        cellStatusIndicators[cellIndex].setImageDrawable(ContextCompat.getDrawable(this, state.statusIndicator))
    }

    override fun onDestroy() {
        super.onDestroy()
        isLiveUpdateRunning = false
        handler.removeCallbacks(liveUpdateRunnable)
        mainScope.cancel()
    }
}