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
        // WICHTIG: IPv4-Stack bevorzugen, um potenzielle ENETUNREACH-Probleme mit IPv6 zu umgehen
        // Dies sollte so früh wie möglich geschehen, bevor Netzwerkoperationen gestartet werden.
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
            Log.d("MainActivity", "onCreate: Suche UI-Elemente für Zelle $cellNumberForId (Index $i)")
            try {
                val titleViewId = resources.getIdentifier("textViewTitleZelle$cellNumberForId", "id", packageName)
                val statusViewId = resources.getIdentifier("textViewStatusZelle$cellNumberForId", "id", packageName)
                val resultsViewId = resources.getIdentifier("textViewResultsZelle$cellNumberForId", "id", packageName)
                val indicatorViewId = resources.getIdentifier("statusIndicatorZelle$cellNumberForId", "id", packageName)

                if (titleViewId != 0) cellTitleTextViews.add(findViewById(titleViewId)) else throw RuntimeException("TitleView ID für Zelle $cellNumberForId nicht gefunden")
                if (statusViewId != 0) cellStatusTextViews.add(findViewById(statusViewId)) else throw RuntimeException("StatusView ID für Zelle $cellNumberForId nicht gefunden")
                if (resultsViewId != 0) cellResultsTextViews.add(findViewById(resultsViewId)) else throw RuntimeException("ResultsView ID für Zelle $cellNumberForId nicht gefunden")
                if (indicatorViewId != 0) cellStatusIndicators.add(findViewById(indicatorViewId)) else throw RuntimeException("IndicatorView ID für Zelle $cellNumberForId nicht gefunden")

                Log.d("MainActivity", "onCreate: Alle UI-Elemente für Zelle $cellNumberForId gefunden.")

            } catch (e: Exception) {
                Log.e("MainActivity", "onCreate: Exception beim Finden oder Hinzufügen der UI-Elemente für Zelle $cellNumberForId: ${e.message}", e)
            }
            if (i < cellTitleTextViews.size) {
                updateCellUI(i)
            }
        }
        Log.d("MainActivity", "onCreate: UI-Initialisierung abgeschlossen. Gefundene Titel-Views: ${cellTitleTextViews.size}")


        buttonStartAllCells.setOnClickListener {
            startFullProcess()
        }
        buttonStopLiveUpdates.setOnClickListener {
            stopLiveUpdates()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

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
                    return@withContext "Fehler: Verbindung fehlgeschlagen (${e.javaClass.simpleName})" // Genauerer Fehler
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

    private var mockCountValues = mutableMapOf<String, Int>()
    private suspend fun mockMoxaRequest(command: MoxaCommand, isLive: Boolean): String {
        val requestDelay = if(isLive) 20L else 40L
        delay(requestDelay)
        if (Math.random() < 0.01 && isLive) {
            return "Fehler bei ${command.name}"
        }
        val countKey = "${command.address}_cell${command.cellId}"
        if (command.name.startsWith("Count")) {
            var currentValue = mockCountValues.getOrPut(countKey) {
                50 + command.cellId * 5 + (if (command.name.contains("B")) 25 else 0)
            }
            currentValue += (1..2).random()
            mockCountValues[countKey] = currentValue
            return "Wert: $currentValue"
        }
        return "OK für ${command.name}"
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
