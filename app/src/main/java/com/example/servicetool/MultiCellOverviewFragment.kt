package com.example.servicetool

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
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
    private lateinit var textBaudrateAll: TextView

    // NEU: Container für individuelle Details
    private lateinit var layoutIndividualDetailsContainer: LinearLayout

    // Arrays für die UI-Elemente der einzelnen Zellen (bis zu maxDisplayCells)
    private val cellCountsTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells)
    private val cellStatusIndicators = arrayOfNulls<ImageView>(MultiCellConfig.maxDisplayCells)
    private val cellLayouts = arrayOfNulls<LinearLayout>(MultiCellConfig.maxDisplayCells)
    private val cellSerialTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells)

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

        val initialSpinnerPosition = spinnerActiveCells.selectedItemPosition
        val initialCellCount = if (initialSpinnerPosition >= 0) initialSpinnerPosition + 1 else 1
        MultiCellConfig.updateAvailableCells(initialCellCount)
        configuredCells = MultiCellConfig.availableCells.toList()
        updateActiveCellViews(initialCellCount)

        initializeUIWithoutData()
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())
        loggingManager.logInfo("MultiCellOverview", "Fragment gestartet mit Moxa: ${getMoxaIpAddress()}:${getMoxaPort()}")
    }

    private fun initializeViews(view: View) {
        textOverallStatus = view.findViewById(R.id.textOverallStatus)
        progressIndicatorOverall = view.findViewById(R.id.progressIndicatorOverall)
        spinnerActiveCells = view.findViewById(R.id.spinnerActiveCells)
        buttonRefreshAll = view.findViewById(R.id.buttonRefreshAll)
        buttonStartLiveAll = view.findViewById(R.id.buttonStartLiveAll)
        buttonStopLiveAll = view.findViewById(R.id.buttonStopLiveAll)
        textLastUpdateAll = view.findViewById(R.id.textLastUpdateAll)
        textBaudrateAll = view.findViewById(R.id.textBaudrateAll)

        // NEU: Container finden
        layoutIndividualDetailsContainer = view.findViewById(R.id.layoutIndividualDetailsContainer)

        for (i in 0 until MAX_DISPLAY_CELLS) {
            val cellNum = i + 1
            try {
                val layoutResId = resources.getIdentifier("layoutCell$cellNum", "id", requireContext().packageName)
                val countsResId = resources.getIdentifier("textCountsCell$cellNum", "id", requireContext().packageName)
                val statusResId = resources.getIdentifier("statusIndicatorCell$cellNum", "id", requireContext().packageName)
                val serialResId = resources.getIdentifier("textSerialCell$cellNum", "id", requireContext().packageName)

                if (layoutResId != 0) cellLayouts[i] = view.findViewById(layoutResId)
                if (countsResId != 0) cellCountsTextViews[i] = view.findViewById(countsResId)
                if (statusResId != 0) cellStatusIndicators[i] = view.findViewById(statusResId)
                if (serialResId != 0) cellSerialTextViews[i] = view.findViewById(serialResId)

            } catch (e: Exception) {
                Log.w("MultiCellOverview", "UI Element für Zelle $cellNum nicht gefunden: ${e.message}")
            }
        }
    }

    private fun setupSpinner() {
        val spinnerItems = (1..MAX_DISPLAY_CELLS).map {
            if (it == 1) "$it Zelle" else "$it Zellen"
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActiveCells.adapter = adapter

        spinnerActiveCells.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCellCount = position + 1
                if (isLiveMode) stopLiveMode()
                MultiCellConfig.updateAvailableCells(selectedCellCount)
                configuredCells = MultiCellConfig.availableCells.toList()
                updateActiveCellViews(selectedCellCount)
                initializeUIWithoutData()
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
        buttonRefreshAll.setOnClickListener { refreshAllCells() }
        buttonStartLiveAll.setOnClickListener { startLiveMode() }
        buttonStopLiveAll.setOnClickListener { stopLiveMode() }
    }

    private fun initializeUIWithoutData() {
        for (i in 0 until configuredCells.size) {
            val cellIndex = configuredCells[i] - 1
            if (cellIndex in 0 until MAX_DISPLAY_CELLS) {
                cellDataArray[cellIndex] = CellDisplayData(
                    cellNumber = configuredCells[i], counts = "---", serialNumber = "Noch nicht geladen",
                    filter = "Unbekannt", version = "Unbekannt"
                )
                updateCellUI(cellIndex)
                updateCellStatus(cellIndex, StatusType.PENDING)
            }
        }

        commonData = CommonDisplayData(baudrate = "Noch nicht geladen", lastUpdate = 0L)
        updateCommonUI()
        updateIndividualCellDetails() // NEU: Details-Bereich leeren/initialisieren

        updateOverallStatus("Bereit - ${configuredCells.size} Zellen konfiguriert", StatusType.PENDING)
        updateButtonStates()
    }

    private fun refreshAllCells() {
        if (isLiveMode) return
        if (configuredCells.isEmpty()) {
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
            return
        }

        showLoading(true)
        updateOverallStatus("Lade Daten für ${configuredCells.size} Zellen...", StatusType.CONNECTING)

        lifecycleScope.launch {
            try {
                val fetchedDataMap = configuredCells.map { cellNumber ->
                    async(Dispatchers.IO) {
                        if (!isActive) return@async null
                        updateCellStatus(cellNumber - 1, StatusType.CONNECTING)
                        cellNumber to fetchCellData(cellNumber)
                    }
                }.awaitAll().filterNotNull().toMap()

                if (!isActive) return@launch

                var successfulFetches = 0
                configuredCells.forEach { cellNumber ->
                    val arrayIndex = cellNumber - 1
                    val cellResult = fetchedDataMap[cellNumber]

                    if (cellResult != null) {
                        cellDataArray[arrayIndex] = cellResult
                        updateCellStatus(arrayIndex, StatusType.CONNECTED)
                        successfulFetches++
                    } else {
                        cellDataArray[arrayIndex] = CellDisplayData(cellNumber = cellNumber, counts = "Fehler", serialNumber = "Fehler")
                        updateCellStatus(arrayIndex, StatusType.ERROR)
                    }
                    updateCellUI(arrayIndex)
                }

                if (successfulFetches > 0) {
                    loadCommonDataFromFetched(fetchedDataMap)
                    updateOverallStatus("$successfulFetches/${configuredCells.size} Zellen erfolgreich geladen", StatusType.CONNECTED)
                } else if (configuredCells.isNotEmpty()) {
                    updateOverallStatus("Keine der ${configuredCells.size} Zellen erreichbar", StatusType.ERROR)
                    commonData = CommonDisplayData(baudrate = "Fehler beim Laden")
                }

                updateCommonUI()
                updateIndividualCellDetails() // NEU: Details aktualisieren
                animateDataUpdate()

            } catch (e: Exception) {
                updateOverallStatus("Fehler: ${e.localizedMessage}", StatusType.ERROR)
            } finally {
                if (isActive) showLoading(false)
            }
        }
    }

    // ... (startLiveMode, stopLiveMode, fetchCellData etc. bleiben größtenteils gleich)

    private suspend fun fetchCellData(cellNumber: Int): CellDisplayData? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(getMoxaIpAddress(), getMoxaPort()), getConnectionTimeout())
                    socket.soTimeout = getReadTimeout()

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    val data = CellDisplayData(cellNumber = cellNumber)
                    var commandSuccess = true

                    data.serialNumber = fetchSingleCellCommand("SerialNumber", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.SERIAL_NUMBER), outputStream, inputStream, cellNumber) ?: "S/N: Unbekannt"
                    if (!isActive) return@withContext null

                    data.counts = fetchSingleCellCommand("Counts", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS), outputStream, inputStream, cellNumber) ?: run { commandSuccess = false; "Fehler" }
                    if (!isActive) return@withContext null

                    if (commandSuccess) {
                        data.baudrate = fetchSingleCellCommand("Baudrate", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.BAUDRATE), outputStream, inputStream, cellNumber) ?: "Unbekannt"
                        if (!isActive) return@withContext null
                        data.filter = fetchSingleCellCommand("Filter", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.FILTER), outputStream, inputStream, cellNumber) ?: "Unbekannt"
                        if (!isActive) return@withContext null
                        data.version = fetchSingleCellCommand("Version", FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.VERSION), outputStream, inputStream, cellNumber) ?: "Unbekannt"
                    }

                    data.lastUpdate = System.currentTimeMillis()
                    return@withContext if (commandSuccess) data else null
                }
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden von Zelle $cellNumber: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun loadCommonDataFromFetched(fetchedDataMap: Map<Int, CellDisplayData?>) {
        val firstSuccessfulCellNumber = configuredCells.firstOrNull { fetchedDataMap[it] != null }
        if (firstSuccessfulCellNumber != null) {
            val successfulCellData = fetchedDataMap[firstSuccessfulCellNumber]!!
            commonData.baudrate = successfulCellData.baudrate
            commonData.lastUpdate = successfulCellData.lastUpdate
        } else {
            commonData = CommonDisplayData()
        }
    }

    private fun updateCommonUI() {
        textBaudrateAll.text = when (val baud = commonData.baudrate) {
            "Noch nicht geladen", "Unbekannt", "Fehler beim Laden" -> baud
            else -> "$baud bps"
        }

        if (commonData.lastUpdate > 0) {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            textLastUpdateAll.text = "Letzte Aktualisierung: ${formatter.format(Date(commonData.lastUpdate))}"
        } else {
            textLastUpdateAll.text = "Noch nicht aktualisiert"
        }
    }

    /**
     * NEU: Erstellt und aktualisiert die individuellen Detail-Ansichten dynamisch.
     */
    private fun updateIndividualCellDetails() {
        if (!isAdded) return // Sicherstellen, dass das Fragment noch aktiv ist

        layoutIndividualDetailsContainer.removeAllViews()

        val activeAndLoadedCells = configuredCells.mapNotNull { cellDataArray.getOrNull(it - 1) }
            .filter { it.counts != "Fehler" && it.counts != "N/A" && it.counts != "---" }

        if (activeAndLoadedCells.isEmpty()){
            val noDataView = TextView(requireContext()).apply {
                text = "Keine Detail-Daten geladen. Bitte 'Alle aktualisieren' drücken."
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary_dark))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            }
            layoutIndividualDetailsContainer.addView(noDataView)
            return
        }

        activeAndLoadedCells.forEach { cellData ->
            val detailLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
            }

            val icon = ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_info_24)
                setColorFilter(ContextCompat.getColor(context, R.color.text_secondary_dark))
                layoutParams = LinearLayout.LayoutParams(
                    (20 * resources.displayMetrics.density).toInt(),
                    (20 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                }
            }

            val cellLabel = TextView(requireContext()).apply {
                text = "Zelle ${cellData.cellNumber}:"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary_dark))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val detailsText = TextView(requireContext()).apply {
                text = " Filter: ${cellData.filter}  |  Version: ${cellData.version}"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary_dark))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                ).apply {
                    marginStart = (8 * resources.displayMetrics.density).toInt()
                }
            }

            detailLayout.addView(icon)
            detailLayout.addView(cellLabel)
            detailLayout.addView(detailsText)

            layoutIndividualDetailsContainer.addView(detailLayout)
        }
    }

    // === Unveränderte oder nur leicht angepasste Funktionen ===

    private fun updateUI() { /* Diese Funktion wird nicht mehr direkt verwendet, die Logik ist jetzt aufgeteilt */ }
    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex < 0 || cellIndex >= MAX_DISPLAY_CELLS) return
        val countsTextView = cellCountsTextViews[cellIndex] ?: return
        val serialTextView = cellSerialTextViews[cellIndex] ?: return
        val cellData = cellDataArray[cellIndex]
        countsTextView.text = cellData.counts
        serialTextView.text = formatSerialNumber(cellData.serialNumber)
    }
    private fun animateDataUpdate() {
        view?.animate()?.alpha(0.8f)?.setDuration(150)?.withEndAction {
            view?.animate()?.alpha(1.0f)?.setDuration(150)?.start()
        }?.start()
    }
    private fun updateActiveCellViews(activeCellCount: Int) {
        for (i in 0 until MAX_DISPLAY_CELLS) {
            cellLayouts[i]?.visibility = if (i < activeCellCount) View.VISIBLE else View.GONE
        }
    }
    private fun showLoading(show: Boolean) {
        progressIndicatorOverall.visibility = if (show) View.VISIBLE else View.GONE
        buttonRefreshAll.isEnabled = !show
        spinnerActiveCells.isEnabled = !show
        updateButtonStates()
    }
    private fun updateButtonStates() {
        val isLoading = progressIndicatorOverall.visibility == View.VISIBLE
        buttonStartLiveAll.isEnabled = !isLiveMode && !isLoading
        buttonStopLiveAll.isEnabled = isLiveMode && !isLoading
        buttonRefreshAll.isEnabled = !isLiveMode && !isLoading
        spinnerActiveCells.isEnabled = !isLiveMode && !isLoading
    }
    private fun formatSerialNumber(serialNumber: String): String {
        return when {
            serialNumber.isBlank() || serialNumber == "Unbekannt" -> ""
            serialNumber == "Noch nicht geladen" -> "Noch nicht geladen"
            serialNumber.startsWith("S/N:") -> serialNumber
            else -> "S/N: $serialNumber"
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
            Log.e("MultiCellOverview", "Fehler beim Setzen der Textfarbe für OverallStatus: ${e.message}")
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        stopLiveMode()
        liveUpdateJob?.cancel()
    }

    // ... Die restlichen Funktionen wie startLiveMode, fetchSingleCellCounts etc. bleiben unverändert ...

    // === DATENKLASSEN ===
    data class CellDisplayData(
        var cellNumber: Int = 0,
        var counts: String = "0",
        var serialNumber: String = "Unbekannt",
        var baudrate: String = "Unbekannt",
        var filter: String = "Unbekannt",
        var version: String = "Unbekannt",
        var lastUpdate: Long = 0L
    )

    data class CommonDisplayData(
        var baudrate: String = "Unbekannt",
        var lastUpdate: Long = 0L
    )

    enum class StatusType {
        CONNECTED, CONNECTING, LIVE, ERROR, PENDING
    }

    // Unveränderte Funktionen hier einfügen...
    // startLiveMode, stopLiveMode, fetchSingleCellCounts, readFlintecResponse, animateCellCountsUpdate

    private suspend fun fetchSingleCellCounts(cellNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(getMoxaIpAddress(), getMoxaPort()), 3000)
                    socket.soTimeout = 2000
                    fetchSingleCellCommand(
                        "Counts (Live)",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        socket.getOutputStream(), socket.getInputStream(), cellNumber
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun fetchSingleCellCommand(
        commandName: String, commandBytes: ByteArray, outputStream: OutputStream,
        inputStream: InputStream, cellNumber: Int
    ): String? {
        if (!coroutineContext.isActive) return null
        return try {
            outputStream.write(commandBytes)
            outputStream.flush()
            val rawResponse = readFlintecResponse(inputStream)
            if (rawResponse.isNotEmpty()) {
                val parsedData = FlintecRC3DMultiCellCommands.parseMultiCellResponse(rawResponse)
                when (parsedData) {
                    is FlintecData.Counts -> parsedData.value
                    is FlintecData.Temperature -> parsedData.value // Existiert nicht mehr, aber für Vollständigkeit
                    is FlintecData.Version -> parsedData.value
                    is FlintecData.Baudrate -> parsedData.value
                    is FlintecData.Filter -> parsedData.value
                    is FlintecData.SerialNumber -> parsedData.value
                    else -> rawResponse
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var etxFound = false
                val tempReadBuffer = ByteArray(128)
                val startTime = System.currentTimeMillis()
                val readOverallTimeout = 2000L
                while (isActive && System.currentTimeMillis() - startTime < readOverallTimeout && !etxFound) {
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(tempReadBuffer)
                        if (bytesRead == -1) break
                        for (k in 0 until bytesRead) {
                            val byte = tempReadBuffer[k]
                            if (!stxFound && byte.toInt() == 0x02) {
                                stxFound = true
                                responseBuffer.clear()
                                continue
                            }
                            if (stxFound) {
                                if (byte.toInt() == 0x03) {
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
                if (stxFound && etxFound) {
                    String(responseBuffer.toByteArray(), Charsets.US_ASCII)
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }
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

    private fun startLiveMode() {
        if (isLiveMode) return
        val cellsWithData = configuredCells.filter { cellNum ->
            val data = cellDataArray.getOrNull(cellNum - 1)
            data != null && data.counts != "Fehler" && data.counts != "N/A" && data.counts != "---"
        }
        if (cellsWithData.isEmpty()) {
            updateOverallStatus("Live-Modus: Zuerst 'Alle aktualisieren' drücken!", StatusType.ERROR)
            return
        }
        isLiveMode = true
        updateButtonStates()
        updateOverallStatus("Live-Modus aktiv für ${cellsWithData.size} Zellen", StatusType.LIVE)
        liveUpdateJob = lifecycleScope.launch {
            while (isLiveMode && isActive) {
                try {
                    val fetchedCounts = mutableListOf<Pair<Int, String?>>()
                    for ((index, cellNumber) in cellsWithData.withIndex()) {
                        if (!isLiveMode || !isActive) break
                        val counts = fetchSingleCellCounts(cellNumber)
                        fetchedCounts.add(cellNumber to counts)
                        if (isLiveMode && isActive && index < cellsWithData.size - 1) {
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
                        }
                    }
                    if (successfulLiveFetches > 0) {
                        commonData.lastUpdate = System.currentTimeMillis()
                        updateCommonUI()
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    delay(2000)
                }
                if (isLiveMode && isActive) {
                    delay(MultiCellConfig.LIVE_UPDATE_INTERVAL)
                }
            }
            if (isActive) {
                updateButtonStates()
                configuredCells.forEach { cellNum ->
                    val idx = cellNum - 1
                    if (idx >= 0 && idx < MAX_DISPLAY_CELLS) {
                        if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A" && cellDataArray[idx].counts != "---") {
                            updateCellStatus(idx, StatusType.CONNECTED)
                        } else if (cellDataArray[idx].counts != "N/A") {
                            updateCellStatus(idx, StatusType.ERROR)
                        }
                    }
                }
                val responsiveCount = configuredCells.count { cellNum ->
                    cellDataArray.getOrNull(cellNum - 1)?.let {
                        it.counts != "Fehler" && it.counts != "N/A" && it.counts != "---"
                    } == true
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
        isLiveMode = false
        liveUpdateJob?.cancel()
        liveUpdateJob = null
        updateButtonStates()
        val responsiveCount = configuredCells.count { cellNum ->
            val data = cellDataArray.getOrNull(cellNum - 1)
            data != null && data.counts != "Fehler" && data.counts != "N/A" && data.counts != "---"
        }
        configuredCells.forEach { cellNum ->
            val idx = cellNum - 1
            if (idx >= 0 && idx < MAX_DISPLAY_CELLS) {
                if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A" && cellDataArray[idx].counts != "---") {
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

}
