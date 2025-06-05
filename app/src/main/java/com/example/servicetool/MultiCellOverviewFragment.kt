package com.example.servicetool

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.coroutineContext

class MultiCellOverviewFragment : Fragment() {

    // UI Komponenten für den Header und die Steuerung
    private lateinit var textOverallStatus: TextView
    private lateinit var progressIndicatorOverall: CircularProgressIndicator
    private lateinit var buttonRefreshAll: Button
    private lateinit var buttonStartLiveAll: Button
    private lateinit var buttonStopLiveAll: Button
    private lateinit var textLastUpdateAll: TextView
    private lateinit var spinnerActiveCells: Spinner

    // UI Komponenten für gemeinsame Details
    private lateinit var textTemperatureAll: TextView
    private lateinit var textBaudrateAll: TextView
    private lateinit var textFilterAll: TextView
    private lateinit var textVersionAll: TextView

    // Arrays für die UI-Elemente der einzelnen Zellen (bis zu maxDisplayCells)
    private val cellCountsTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells)
    private val cellStatusIndicators = arrayOfNulls<ImageView>(MultiCellConfig.maxDisplayCells)
    private val cellLayouts = arrayOfNulls<LinearLayout>(MultiCellConfig.maxDisplayCells)
    private val cellSerialTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells) // ✅ NEU: Seriennummern

    // Datenhaltung
    private val cellDataArray = Array(MultiCellConfig.maxDisplayCells) { CellDisplayData() }
    private var commonData = CommonDisplayData()
    private var isLiveMode = false
    private var liveUpdateJob: Job? = null

    // Konfiguration
    private var configuredCells: List<Int> = MultiCellConfig.availableCells.toList()

    // Settings und Logging Manager
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager

    // Multi-Cell Konfiguration (aus Settings)
    private fun getMoxaIpAddress(): String = MultiCellConfig.getMoxaIpAddress()
    private fun getMoxaPort(): Int = MultiCellConfig.getMoxaPort()
    private fun getConnectionTimeout(): Int = MultiCellConfig.getConnectionTimeout()
    private fun getReadTimeout(): Int = MultiCellConfig.getReadTimeout()
    private val MAX_DISPLAY_CELLS = MultiCellConfig.maxDisplayCells
    private val CELL_QUERY_DELAY_MS = MultiCellConfig.CELL_QUERY_DELAY_MS

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_multicell_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeServices()
        initializeViews(view)
        setupSpinner()
        setupClickListeners()

        // Initialisiere die UI basierend auf dem Startwert des Spinners
        val initialSpinnerPosition = spinnerActiveCells.selectedItemPosition
        val initialCellCount = if (initialSpinnerPosition >= 0) initialSpinnerPosition + 1 else 1
        MultiCellConfig.updateAvailableCells(initialCellCount)
        configuredCells = MultiCellConfig.availableCells.toList()
        updateActiveCellViews(initialCellCount)

        updateUI()
        refreshAllCells()
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())

        loggingManager.logInfo("MultiCellOverview", "Fragment gestartet mit Moxa: ${getMoxaIpAddress()}:${getMoxaPort()}")
    }

    private fun initializeViews(view: View) {
        // Header UI
        textOverallStatus = view.findViewById(R.id.textOverallStatus)
        progressIndicatorOverall = view.findViewById(R.id.progressIndicatorOverall)
        spinnerActiveCells = view.findViewById(R.id.spinnerActiveCells)

        // Control buttons
        buttonRefreshAll = view.findViewById(R.id.buttonRefreshAll)
        buttonStartLiveAll = view.findViewById(R.id.buttonStartLiveAll)
        buttonStopLiveAll = view.findViewById(R.id.buttonStopLiveAll)
        textLastUpdateAll = view.findViewById(R.id.textLastUpdateAll)

        // Common details
        textTemperatureAll = view.findViewById(R.id.textTemperatureAll)
        textBaudrateAll = view.findViewById(R.id.textBaudrateAll)
        textFilterAll = view.findViewById(R.id.textFilterAll)
        textVersionAll = view.findViewById(R.id.textVersionAll)

        // Dynamisches Finden der UI-Elemente für jede Zelle
        for (i in 0 until MAX_DISPLAY_CELLS) {
            val cellNum = i + 1
            try {
                val layoutResId = resources.getIdentifier("layoutCell$cellNum", "id", requireContext().packageName)
                val countsResId = resources.getIdentifier("textCountsCell$cellNum", "id", requireContext().packageName)
                val statusResId = resources.getIdentifier("statusIndicatorCell$cellNum", "id", requireContext().packageName)
                val serialResId = resources.getIdentifier("textSerialCell$cellNum", "id", requireContext().packageName) // ✅ NEU

                if (layoutResId != 0) cellLayouts[i] = view.findViewById(layoutResId)
                if (countsResId != 0) cellCountsTextViews[i] = view.findViewById(countsResId)
                if (statusResId != 0) cellStatusIndicators[i] = view.findViewById(statusResId)
                if (serialResId != 0) cellSerialTextViews[i] = view.findViewById(serialResId) // ✅ NEU

            } catch (e: Exception) {
                Log.w("MultiCellOverview", "UI Element für Zelle $cellNum nicht gefunden: ${e.message}")
            }
        }
        Log.i("MultiCellOverview", "Views initialisiert. Maximale Zellen-Layouts: $MAX_DISPLAY_CELLS")
    }

    private fun setupSpinner() {
        val spinnerItems = (1..MAX_DISPLAY_CELLS).map {
            if (it == 1) "$it Zelle" else "$it Zellen"
        }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActiveCells.adapter = adapter

        spinnerActiveCells.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCellCount = position + 1

                Log.d("MultiCellOverview", "Spinner: Auswahl '$selectedCellCount Zellen' an Position $position.")

                if (isLiveMode) {
                    stopLiveMode()
                }

                MultiCellConfig.updateAvailableCells(selectedCellCount)
                configuredCells = MultiCellConfig.availableCells.toList()

                updateActiveCellViews(selectedCellCount)
                refreshAllCells()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val currentConfiguredCellsCount = MultiCellConfig.getAvailableCellCount()
        if (currentConfiguredCellsCount > 0 && currentConfiguredCellsCount <= MAX_DISPLAY_CELLS) {
            spinnerActiveCells.setSelection(currentConfiguredCellsCount - 1, false)
        } else {
            spinnerActiveCells.setSelection(0, false)
        }
    }

    private fun setupClickListeners() {
        buttonRefreshAll.setOnClickListener {
            refreshAllCells()
        }
        buttonStartLiveAll.setOnClickListener {
            startLiveMode()
        }
        buttonStopLiveAll.setOnClickListener {
            stopLiveMode()
        }
    }

    private fun updateActiveCellViews(activeCellCount: Int) {
        Log.d("MultiCellOverview", "updateActiveCellViews: Zeige $activeCellCount Zellen-Layouts an.")
        for (i in 0 until MAX_DISPLAY_CELLS) {
            val cellLayout = cellLayouts[i]
            val cellCountsTextView = cellCountsTextViews[i]
            val cellSerialTextView = cellSerialTextViews[i] // ✅ NEU
            val cellStatusIndicator = cellStatusIndicators[i]

            if (i < activeCellCount) {
                cellLayout?.visibility = View.VISIBLE
            } else {
                cellLayout?.visibility = View.GONE
                cellCountsTextView?.text = "N/A"
                cellSerialTextView?.text = "" // ✅ NEU: Leere Seriennummer
                cellStatusIndicator?.setImageResource(R.drawable.ic_status_pending)
                cellDataArray[i] = CellDisplayData(cellNumber = i + 1, counts = "N/A")
            }
        }
    }

    private fun refreshAllCells() {
        if (isLiveMode) {
            Log.d("MultiCellOverview", "refreshAllCells: Im Live-Modus, kein Refresh.")
            return
        }
        if (configuredCells.isEmpty()) {
            Log.w("MultiCellOverview", "refreshAllCells: Keine Zellen konfiguriert, kein Refresh.")
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
            showLoading(false)
            updateActiveCellViews(0)
            commonData = CommonDisplayData()
            updateCommonUI()
            return
        }

        showLoading(true)
        updateOverallStatus("Lade Daten für ${configuredCells.size} Zellen mit ${getMoxaIpAddress()}:${getMoxaPort()}...", StatusType.CONNECTING)
        loggingManager.logInfo("MultiCellOverview", "Refresh aller Zellen gestartet mit ${getMoxaIpAddress()}:${getMoxaPort()}")

        lifecycleScope.launch {
            try {
                var successfulFetches = 0
                val fetchedDataMap = mutableMapOf<Int, CellDisplayData?>()

                for ((index, cellNumber) in configuredCells.withIndex()) {
                    if (!isActive) {
                        Log.i("MultiCellOverview", "refreshAllCells Coroutine abgebrochen (vor fetch).")
                        return@launch
                    }
                    Log.d("MultiCellOverview", "Starte Datenabfrage für Zelle $cellNumber (Index $index)")
                    updateCellStatus(cellNumber - 1, StatusType.CONNECTING)

                    val result = fetchCellData(cellNumber)
                    fetchedDataMap[cellNumber] = result

                    if (!isActive && index < configuredCells.size - 1) {
                        Log.i("MultiCellOverview", "refreshAllCells Coroutine abgebrochen (nach fetch, vor Delay).")
                        return@launch
                    }
                    if (index < configuredCells.size - 1) {
                        delay(CELL_QUERY_DELAY_MS)
                    }
                }

                if (!isActive) {
                    Log.i("MultiCellOverview", "refreshAllCells Coroutine nach allen Datenabfragen abgebrochen.")
                    return@launch
                }

                // Verarbeite die gesammelten Ergebnisse
                configuredCells.forEach { cellNumber ->
                    val arrayIndex = cellNumber - 1
                    val cellResult = fetchedDataMap[cellNumber]

                    if (cellResult != null) {
                        cellDataArray[arrayIndex] = cellResult
                        updateCellUI(arrayIndex)
                        updateCellStatus(arrayIndex, StatusType.CONNECTED)
                        successfulFetches++
                        Log.i("MultiCellOverview", "Zelle $cellNumber erfolgreich geladen: Counts=${cellResult.counts}, Serial=${cellResult.serialNumber}")
                    } else {
                        cellDataArray[arrayIndex] = CellDisplayData(cellNumber = cellNumber, counts = "Fehler")
                        updateCellUI(arrayIndex)
                        updateCellStatus(arrayIndex, StatusType.ERROR)
                        Log.w("MultiCellOverview", "Zelle $cellNumber konnte nicht geladen werden.")
                    }
                }

                if (successfulFetches > 0) {
                    loadCommonDataFromFetched(fetchedDataMap)
                    updateOverallStatus("$successfulFetches/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
                    loggingManager.logInfo("MultiCellOverview", "Refresh abgeschlossen: $successfulFetches/${configuredCells.size} Zellen erfolgreich")
                } else if (configuredCells.isNotEmpty()) {
                    updateOverallStatus("Keine der ${configuredCells.size} Zellen erreichbar", StatusType.ERROR)
                    commonData = CommonDisplayData()
                    updateCommonUI()
                    loggingManager.logError("MultiCellOverview", "Refresh fehlgeschlagen: Keine Zellen erreichbar")
                } else {
                    updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
                }

                animateDataUpdate()

            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "refreshAllCells Coroutine wurde abgebrochen (CancellationException).")
                updateOverallStatus("Ladevorgang abgebrochen", StatusType.PENDING)
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden aller Zellen: ${e.message}", e)
                updateOverallStatus("Fehler: ${e.localizedMessage ?: e.message}", StatusType.ERROR)
                loggingManager.logError("MultiCellOverview", "Refresh-Fehler", e)
            } finally {
                if (isActive) {
                    showLoading(false)
                }
            }
        }
    }

    private fun startLiveMode() {
        if (isLiveMode) return

        val cellsToQueryInLiveMode = configuredCells.filter { cellNum ->
            val data = cellDataArray.getOrNull(cellNum - 1)
            data != null && data.counts != "Fehler" && data.counts != "N/A"
        }

        if (cellsToQueryInLiveMode.isEmpty()) {
            Log.w("MultiCellOverview", "Live-Modus nicht gestartet: Keine Zellen für Live-Abfrage verfügbar")
            updateOverallStatus("Keine Zellen für Live-Modus", StatusType.ERROR)
            buttonStartLiveAll.isEnabled = true
            buttonStopLiveAll.isEnabled = false
            spinnerActiveCells.isEnabled = true
            return
        }

        isLiveMode = true
        updateButtonStates()
        updateOverallStatus("Live-Modus aktiv für ${cellsToQueryInLiveMode.size} Zellen mit ${getMoxaIpAddress()}:${getMoxaPort()}", StatusType.LIVE)
        Log.i("MultiCellOverview", "Starte Live-Modus für Zellen: ${cellsToQueryInLiveMode.joinToString(", ")} mit ${getMoxaIpAddress()}:${getMoxaPort()}")
        loggingManager.logInfo("MultiCellOverview", "Live-Modus gestartet für ${cellsToQueryInLiveMode.size} Zellen")

        liveUpdateJob = lifecycleScope.launch {
            while (isLiveMode && isActive) {
                try {
                    val fetchedCounts = mutableListOf<Pair<Int, String?>>()

                    for ((index, cellNumber) in cellsToQueryInLiveMode.withIndex()) {
                        if (!isLiveMode || !isActive) break

                        Log.d("MultiCellOverview_Live", "Live-Abfrage für Zelle $cellNumber")
                        val counts = fetchSingleCellCounts(cellNumber)
                        fetchedCounts.add(cellNumber to counts)

                        if (isLiveMode && isActive && index < cellsToQueryInLiveMode.size - 1) {
                            delay(CELL_QUERY_DELAY_MS)
                        }
                    }

                    if (!isLiveMode || !isActive) break

                    var successfulLiveFetches = 0
                    for ((cellNumber, newCounts) in fetchedCounts) {
                        val arrayIndex = cellNumber - 1
                        if (arrayIndex < 0 || arrayIndex >= MAX_DISPLAY_CELLS) continue

                        if (newCounts != null) {
                            cellDataArray[arrayIndex].counts = newCounts
                            cellDataArray[arrayIndex].lastUpdate = System.currentTimeMillis()
                            animateCellCountsUpdate(arrayIndex, newCounts)
                            updateCellStatus(arrayIndex, StatusType.LIVE)
                            successfulLiveFetches++
                        } else {
                            updateCellStatus(arrayIndex, StatusType.ERROR)
                            Log.w("MultiCellOverview_Live", "Zelle $cellNumber hat im Live-Modus nicht geantwortet.")
                        }
                    }

                    if (successfulLiveFetches > 0) {
                        commonData.lastUpdate = System.currentTimeMillis()
                        updateCommonUI()
                    } else {
                        Log.w("MultiCellOverview_Live", "Keine Zelle hat im Live-Zyklus geantwortet.")
                    }

                } catch (e: CancellationException) {
                    Log.i("MultiCellOverview_Live", "Live-Update Job wurde abgebrochen (CancellationException).")
                    break
                } catch (e: Exception) {
                    Log.e("MultiCellOverview_Live", "Allgemeiner Live-Update Fehler: ${e.message}", e)
                    delay(2000)
                }
                if (isLiveMode && isActive) {
                    delay(MultiCellConfig.LIVE_UPDATE_INTERVAL)
                }
            }
            Log.i("MultiCellOverview", "Live-Modus Schleife beendet. isLiveMode: $isLiveMode, Coroutine isActive: $isActive")

            if (isActive) {
                updateButtonStates()
                configuredCells.forEach { cellNum ->
                    val idx = cellNum - 1
                    if (idx >= 0 && idx < MAX_DISPLAY_CELLS) {
                        if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A") {
                            updateCellStatus(idx, StatusType.CONNECTED)
                        } else if (cellDataArray[idx].counts != "N/A") {
                            updateCellStatus(idx, StatusType.ERROR)
                        }
                    }
                }
                val responsiveCount = configuredCells.count { cellNum ->
                    cellDataArray.getOrNull(cellNum - 1)?.let { it.counts != "Fehler" && it.counts != "N/A" } == true
                }
                if (configuredCells.isNotEmpty()) {
                    updateOverallStatus("$responsiveCount/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
                } else {
                    updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
                }
            }
        }
    }

    private fun stopLiveMode() {
        if (!isLiveMode && liveUpdateJob == null) return

        Log.i("MultiCellOverview", "stopLiveMode aufgerufen.")
        loggingManager.logInfo("MultiCellOverview", "Live-Modus gestoppt")
        isLiveMode = false
        liveUpdateJob?.cancel()
        liveUpdateJob = null

        updateButtonStates()
        val responsiveCount = configuredCells.count { cellNum ->
            val data = cellDataArray.getOrNull(cellNum - 1)
            data != null && data.counts != "Fehler" && data.counts != "N/A"
        }

        configuredCells.forEach { cellNum ->
            val idx = cellNum - 1
            if (idx >= 0 && idx < MAX_DISPLAY_CELLS) {
                if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A") {
                    updateCellStatus(idx, StatusType.CONNECTED)
                } else if (cellDataArray[idx].counts != "N/A") {
                    updateCellStatus(idx, StatusType.ERROR)
                }
            }
        }

        if (configuredCells.isNotEmpty()) {
            updateOverallStatus("$responsiveCount/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
        } else {
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
        }
    }

    // ✅ ERWEITERTE fetchCellData MIT SERIENNUMMER
    private suspend fun fetchCellData(cellNumber: Int): CellDisplayData? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            Log.d("MultiCellOverview", "fetchCellData für Zelle $cellNumber mit ${getMoxaIpAddress()}:${getMoxaPort()} - Thread: ${Thread.currentThread().name}")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(getMoxaIpAddress(), getMoxaPort()), getConnectionTimeout())
                    socket.soTimeout = getReadTimeout()

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    val data = CellDisplayData(cellNumber = cellNumber)
                    var commandSuccess = true

                    // ✅ ZUERST: Seriennummer abfragen
                    data.serialNumber = fetchSingleCellCommand(
                        "SerialNumber",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.SERIAL_NUMBER),
                        outputStream, inputStream, cellNumber
                    ) ?: run {
                        Log.w("MultiCellOverview", "Seriennummer für Zelle $cellNumber konnte nicht geladen werden")
                        "S/N: Unbekannt"
                    }
                    if (!isActive) return@withContext null

                    // Counts abfragen
                    data.counts = fetchSingleCellCommand(
                        "Counts",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        outputStream, inputStream, cellNumber
                    ) ?: run { commandSuccess = false; "Fehler" }
                    if (!isActive) return@withContext null

                    // Gemeinsame Daten nur abfragen, wenn Counts erfolgreich waren
                    if (commandSuccess) {
                        data.temperature = fetchSingleCellCommand("Temperatur", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.TEMPERATURE), outputStream, inputStream, cellNumber) ?: "0°C"
                        if (!isActive) return@withContext null
                        data.baudrate = fetchSingleCellCommand("Baudrate", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.BAUDRATE), outputStream, inputStream, cellNumber) ?: "Unbekannt"
                        if (!isActive) return@withContext null
                        data.filter = fetchSingleCellCommand("Filter", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.FILTER), outputStream, inputStream, cellNumber) ?: "Unbekannt"
                        if (!isActive) return@withContext null
                        data.version = fetchSingleCellCommand("Version", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.VERSION), outputStream, inputStream, cellNumber) ?: "Unbekannt"
                        if (!isActive) return@withContext null
                    }

                    data.lastUpdate = System.currentTimeMillis()
                    return@withContext if (commandSuccess) data else null
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "fetchCellData für Zelle $cellNumber abgebrochen (CancellationException).")
                return@withContext null
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden von Zelle $cellNumber mit ${getMoxaIpAddress()}:${getMoxaPort()}: ${e.message}", e)
                loggingManager.logError("MultiCellOverview", "Zelle $cellNumber Fetch-Fehler", e, cellNumber)
                return@withContext null
            }
        }
    }

    private suspend fun loadCommonDataFromFetched(fetchedDataMap: Map<Int, CellDisplayData?>) {
        val firstSuccessfulCellNumber = configuredCells.firstOrNull { fetchedDataMap[it] != null }

        if (firstSuccessfulCellNumber != null) {
            val successfulCellData = fetchedDataMap[firstSuccessfulCellNumber]!!
            commonData.temperature = successfulCellData.temperature
            commonData.baudrate = successfulCellData.baudrate
            commonData.filter = successfulCellData.filter
            commonData.version = successfulCellData.version
            commonData.lastUpdate = successfulCellData.lastUpdate

            withContext(Dispatchers.Main) {
                updateCommonUI()
            }
            Log.i("MultiCellOverview", "Gemeinsame Daten von Zelle $firstSuccessfulCellNumber geladen.")
        } else {
            Log.w("MultiCellOverview", "Keine erfolgreiche Zelle gefunden, um CommonData zu laden. Setze auf Standard.")
            commonData = CommonDisplayData()
            withContext(Dispatchers.Main) {
                updateCommonUI()
            }
        }
    }

    private suspend fun fetchSingleCellCounts(cellNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            Log.d("MultiCellOverview_Live", "fetchSingleCellCounts für Zelle $cellNumber - Thread: ${Thread.currentThread().name}")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(getMoxaIpAddress(), getMoxaPort()), 3000) // Kürzerer Timeout für Live
                    socket.soTimeout = 2000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    return@withContext fetchSingleCellCommand(
                        "Counts (Live)",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        outputStream, inputStream, cellNumber
                    )
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview_Live", "fetchSingleCellCounts für Zelle $cellNumber abgebrochen.")
                return@withContext null
            } catch (e: Exception) {
                Log.e("MultiCellOverview_Live", "Live-Fehler bei Counts von Zelle $cellNumber: ${e.message}")
                return@withContext null
            }
        }
    }

    private suspend fun fetchSingleCellCommand(
        commandName: String,
        commandBytes: ByteArray,
        outputStream: OutputStream,
        inputStream: InputStream,
        cellNumber: Int
    ): String? {
        if (!coroutineContext.isActive) {
            Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv vor Sendung).")
            return null
        }
        return try {
            Log.d("MultiCellOverview", "Sende an Zelle $cellNumber ($commandName): ${commandBytes.joinToString(" ") { "%02X".format(it) }}")
            outputStream.write(commandBytes)
            outputStream.flush()

            if (!coroutineContext.isActive) {
                Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv nach Sendung, vor Antwort).")
                return null
            }

            val rawResponse = readFlintecResponse(inputStream)

            if (!coroutineContext.isActive && rawResponse.isEmpty()) {
                Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv nach Antwort).")
                return null
            }

            if (rawResponse.isNotEmpty()) {
                Log.d("MultiCellOverview", "Zelle $cellNumber Antwort ($commandName): '$rawResponse'")
                val parsedData = FlintecRC3DMultiCellCommands.parseMultiCellResponse(rawResponse)
                val valueToReturn = when (parsedData) {
                    is FlintecData.Counts -> parsedData.value
                    is FlintecData.Temperature -> parsedData.value
                    is FlintecData.Version -> parsedData.value
                    is FlintecData.Baudrate -> parsedData.value
                    is FlintecData.Filter -> parsedData.value
                    is FlintecData.SerialNumber -> parsedData.value // ✅ NEU: Seriennummer-Handling
                    is FlintecData.Unknown -> {
                        Log.w("MultiCellOverview", "Unbekannte Antwort von Zelle $cellNumber ($commandName): '$rawResponse'")
                        rawResponse
                    }
                    null -> {
                        Log.w("MultiCellOverview", "Parsing-Fehler oder keine verwertbaren Daten von Zelle $cellNumber ($commandName). Rohantwort: '$rawResponse'")
                        rawResponse
                    }
                }
                Log.i("MultiCellOverview", "Zelle $cellNumber $commandName erfolgreich verarbeitet: '$valueToReturn'")
                valueToReturn
            } else {
                Log.w("MultiCellOverview", "Zelle $cellNumber $commandName: Keine (gültige) Antwort erhalten.")
                null
            }
        } catch (e: CancellationException) {
            Log.i("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber wurde abgebrochen (CancellationException).")
            throw e
        } catch (e: Exception) {
            Log.e("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber fehlgeschlagen: ${e.message}", e)
            null
        }
    }

    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext ""
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var etxFound = false
                val tempReadBuffer = ByteArray(128)

                val startTime = System.currentTimeMillis()
                val readOverallTimeout = MultiCellConfig.READ_TIMEOUT.toLong()

                while (isActive && System.currentTimeMillis() - startTime < readOverallTimeout && !etxFound) {
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(tempReadBuffer)
                        if (bytesRead == -1) {
                            Log.w("MultiCellOverview", "End of stream erreicht beim Lesen der Antwort.")
                            break
                        }

                        for (k in 0 until bytesRead) {
                            if (!isActive) return@withContext ""

                            val byte = tempReadBuffer[k]
                            if (!stxFound && byte.toInt() == 0x02 /* STX */) {
                                stxFound = true
                                responseBuffer.clear()
                                continue
                            }

                            if (stxFound) {
                                if (byte.toInt() == 0x03 /* ETX */) {
                                    etxFound = true
                                    break
                                }
                                responseBuffer.add(byte)
                            }
                        }
                    } else {
                        delay(50)
                    }
                }

                if (!isActive && !etxFound) {
                    Log.i("MultiCellOverview", "readFlintecResponse abgebrochen, bevor ETX gefunden wurde.")
                    return@withContext ""
                }

                if (stxFound && etxFound) {
                    val responseString = String(responseBuffer.toByteArray(), Charsets.US_ASCII)
                    Log.d("MultiCellOverview", "Gültige Antwort empfangen: '$responseString'")
                    return@withContext responseString
                } else {
                    if (!stxFound) Log.w("MultiCellOverview", "Kein STX in der Antwort gefunden (Timeout).")
                    else if (!etxFound) Log.w("MultiCellOverview", "STX gefunden, aber kein ETX (Timeout). Buffer: ${responseBuffer.joinToString("") { "%02X".format(it) }}")
                    return@withContext ""
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "readFlintecResponse wurde abgebrochen (CancellationException).")
                return@withContext ""
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Lesen der Flintec Antwort: ${e.message}", e)
                return@withContext ""
            }
        }
    }

    // === UI UPDATE FUNKTIONEN ===
    private fun updateUI() {
        updateCommonUI()
        for (i in 0 until MAX_DISPLAY_CELLS) {
            updateCellUI(i)
        }
    }

    // ✅ ERWEITERTE updateCellUI MIT SERIENNUMMER
    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex < 0 || cellIndex >= MAX_DISPLAY_CELLS) return
        val countsTextView = cellCountsTextViews[cellIndex] ?: return
        val serialTextView = cellSerialTextViews[cellIndex] ?: return // ✅ NEU
        val cellData = cellDataArray[cellIndex]

        val cellNumberForConfigCheck = cellIndex + 1
        if (!configuredCells.contains(cellNumberForConfigCheck) && cellLayouts[cellIndex]?.visibility == View.GONE) {
            countsTextView.text = "N/A"
            serialTextView.text = "" // ✅ NEU: Leere Seriennummer für inaktive Zellen
        } else {
            countsTextView.text = cellData.counts
            // ✅ NEU: Seriennummer anzeigen (formatiert)
            serialTextView.text = formatSerialNumber(cellData.serialNumber)
        }
    }

    private fun updateCommonUI() {
        textTemperatureAll.text = commonData.temperature
        textBaudrateAll.text = if (commonData.baudrate != "Unbekannt") "${commonData.baudrate} bps" else "Unbekannt"
        textFilterAll.text = "Filter: ${commonData.filter}"
        textVersionAll.text = commonData.version

        if (commonData.lastUpdate > 0) {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            textLastUpdateAll.text = "Letzte Aktualisierung: ${formatter.format(Date(commonData.lastUpdate))}"
        } else {
            textLastUpdateAll.text = "Letzte Aktualisierung: -"
        }
    }

    private fun updateCellStatus(cellIndex: Int, type: StatusType) {
        if (cellIndex < 0 || cellIndex >= MAX_DISPLAY_CELLS) return
        val indicator = cellStatusIndicators[cellIndex] ?: return

        val drawableId = when (type) {
            StatusType.CONNECTED -> R.drawable.ic_status_success
            StatusType.CONNECTING -> R.drawable.ic_status_pending
            StatusType.LIVE -> R.drawable.ic_status_live
            StatusType.ERROR -> R.drawable.ic_status_error
            StatusType.PENDING -> R.drawable.ic_status_pending
        }
        indicator.setImageResource(drawableId)
    }

    private fun updateOverallStatus(status: String, type: StatusType) {
        textOverallStatus.text = status
        val colorId = when (type) {
            StatusType.CONNECTED -> R.color.status_success_color
            StatusType.CONNECTING -> R.color.status_pending_color
            StatusType.LIVE -> R.color.status_live_color
            StatusType.ERROR -> R.color.status_error_color
            StatusType.PENDING -> R.color.status_pending_color
        }
        try {
            textOverallStatus.setTextColor(ContextCompat.getColor(requireContext(), colorId))
        } catch (e: IllegalStateException) {
            Log.e("MultiCellOverview", "Fehler beim Setzen der Textfarbe für OverallStatus (Fragment möglicherweise nicht mehr attached): ${e.message}")
        }
    }

    private fun animateDataUpdate() {
        view?.animate()?.alpha(0.8f)?.setDuration(150)?.withEndAction {
            view?.animate()?.alpha(1.0f)?.setDuration(150)?.start()
        }?.start()
    }

    private fun animateCellCountsUpdate(cellIndex: Int, newValueString: String) {
        val textView = cellCountsTextViews[cellIndex] ?: return
        if (textView.text.toString() != newValueString) {
            textView.animate().alpha(0.0f).setDuration(150).withEndAction {
                textView.text = newValueString
                textView.animate().alpha(1.0f).setDuration(150).start()
            }.start()
        }
    }

    private fun showLoading(show: Boolean) {
        progressIndicatorOverall.visibility = if (show) View.VISIBLE else View.GONE
        buttonRefreshAll.isEnabled = !show
        spinnerActiveCells.isEnabled = !show

        if (show) {
            buttonStartLiveAll.isEnabled = false
            buttonStopLiveAll.isEnabled = false
        } else {
            if (!isLiveMode) {
                spinnerActiveCells.isEnabled = true
            }
            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        val isLoading = progressIndicatorOverall.visibility == View.VISIBLE
        buttonStartLiveAll.isEnabled = !isLiveMode && !isLoading
        buttonStopLiveAll.isEnabled = isLiveMode && !isLoading
        buttonRefreshAll.isEnabled = !isLiveMode && !isLoading
        spinnerActiveCells.isEnabled = !isLiveMode && !isLoading
    }

    // ✅ NEU: SERIENNUMMER-FORMATIERUNG
    private fun formatSerialNumber(serialNumber: String): String {
        return when {
            serialNumber.isBlank() || serialNumber == "Unbekannt" -> ""
            serialNumber.startsWith("S/N:") -> serialNumber
            serialNumber.length > 8 -> "S/N: ${serialNumber.takeLast(6)}" // Zeige nur die letzten 6 Stellen
            else -> "S/N: $serialNumber"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLiveMode()
        liveUpdateJob?.cancel()
        liveUpdateJob = null
        Log.d("MultiCellOverview", "onDestroyView aufgerufen.")
    }

    // === DATENKLASSEN ===
    data class CellDisplayData(
        var cellNumber: Int = 0,
        var counts: String = "0",
        var serialNumber: String = "Unbekannt", // ✅ NEU: Seriennummer hinzugefügt
        var temperature: String = "0°C",
        var baudrate: String = "Unbekannt",
        var filter: String = "Unbekannt",
        var version: String = "Unbekannt",
        var lastUpdate: Long = 0L
    )

    data class CommonDisplayData(
        var temperature: String = "Unbekannt",
        var baudrate: String = "Unbekannt",
        var filter: String = "Unbekannt",
        var version: String = "Unbekannt",
        var lastUpdate: Long = 0L
    )

    enum class StatusType {
        CONNECTED, CONNECTING, LIVE, ERROR, PENDING
    }
}