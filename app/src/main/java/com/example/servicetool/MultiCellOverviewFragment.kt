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

    // UI Components
    private lateinit var textOverallStatus: TextView
    private lateinit var progressIndicatorOverall: CircularProgressIndicator
    private lateinit var buttonRefreshAll: Button
    private lateinit var buttonStartLiveAll: Button
    private lateinit var buttonStopLiveAll: Button
    private lateinit var textLastUpdateAll: TextView
    private lateinit var spinnerActiveCells: Spinner
    private lateinit var textBaudrateAll: TextView
    private lateinit var layoutIndividualDetailsContainer: LinearLayout

    // UI Element Arrays
    private val cellCountsTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells)
    private val cellStatusIndicators = arrayOfNulls<ImageView>(MultiCellConfig.maxDisplayCells)
    private val cellLayouts = arrayOfNulls<LinearLayout>(MultiCellConfig.maxDisplayCells)
    private val cellSerialTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells)

    // Data and State
    private val cellDataArray = Array(MultiCellConfig.maxDisplayCells) { CellDisplayData() }
    private var commonData = CommonDisplayData()
    private var isLiveMode = false
    private var liveUpdateJob: Job? = null
    private var configuredCells: List<Int> = emptyList()

    // Services
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager

    // --- NEU: Konstante für die Verzögerung ---
    // Diese Pause geben wir dem System zwischen jeder Zellen-Abfrage.
    private val CELL_QUERY_DELAY_MS = 250L

    // Helper to get settings
    private fun getMoxaIpAddress(): String = settingsManager.getMoxaIpAddress()
    private fun getMoxaPort(): Int = settingsManager.getMoxaPort()
    private fun getConnectionTimeout(): Int = settingsManager.getConnectionTimeout()
    private fun getReadTimeout(): Int = settingsManager.getReadTimeout()

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
        updateConfiguredCells()
        initializeUIWithoutData()
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())
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
        layoutIndividualDetailsContainer = view.findViewById(R.id.layoutIndividualDetailsContainer)

        for (i in 0 until MultiCellConfig.maxDisplayCells) {
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
        val spinnerItems = (1..MultiCellConfig.maxDisplayCells).map { if (it == 1) "$it Zelle" else "$it Zellen" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActiveCells.adapter = adapter

        val currentCellCount = settingsManager.getActiveCellCount()
        if (currentCellCount in 1..MultiCellConfig.maxDisplayCells) {
            spinnerActiveCells.setSelection(currentCellCount - 1, false)
        }

        spinnerActiveCells.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCellCount = position + 1
                if (settingsManager.getActiveCellCount() != selectedCellCount) {
                    if (isLiveMode) stopLiveMode()
                    settingsManager.setActiveCellCount(selectedCellCount)
                    updateConfiguredCells()
                    initializeUIWithoutData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        buttonRefreshAll.setOnClickListener { refreshAllCells() }
        buttonStartLiveAll.setOnClickListener { startLiveMode() }
        buttonStopLiveAll.setOnClickListener { stopLiveMode() }
    }

    private fun updateConfiguredCells() {
        configuredCells = (1..settingsManager.getActiveCellCount()).toList()
        updateActiveCellViews(configuredCells.size)
    }

    private fun updateActiveCellViews(activeCellCount: Int) {
        // Alle Zellen zuerst ausblenden
        for (i in 0 until MultiCellConfig.maxDisplayCells) {
            val cellLayout = cellLayouts[i]
            cellLayout?.visibility = View.GONE
        }
        
        // Definiere welche physischen Layout-Positionen für welche Zellen verwendet werden
        // Layout-Anordnung: [2,3,4,5] in erster Reihe, [1,8,7,6] in zweiter Reihe
        when (activeCellCount) {
            1 -> {
                // Nur Zelle 1 anzeigen
                cellLayouts[0]?.visibility = View.VISIBLE // Zelle 1 Layout
            }
            4 -> {
                // Gewünschte Anordnung: Reihe 1: [2,3], Reihe 2: [1,4]
                // Physische Layouts:   Reihe 1: [2,3,4,5], Reihe 2: [1,8,7,6]
                cellLayouts[1]?.visibility = View.VISIBLE // Zelle 2 - Position 0 in Reihe 1
                cellLayouts[2]?.visibility = View.VISIBLE // Zelle 3 - Position 1 in Reihe 1
                cellLayouts[0]?.visibility = View.VISIBLE // Zelle 1 - Position 0 in Reihe 2
                cellLayouts[7]?.visibility = View.VISIBLE // Layout Zelle 8, aber für Zelle 4 Daten
                // Aktualisiere die Beschriftungen für die umgemappten Layouts
                updateCellLabels(activeCellCount)
            }
            6 -> {
                // Gewünschte Anordnung: Reihe 1: [2,3,4], Reihe 2: [1,6,5]  
                // Physische Layouts:   Reihe 1: [2,3,4,5], Reihe 2: [1,8,7,6]
                cellLayouts[1]?.visibility = View.VISIBLE // Zelle 2 - Position 0 in Reihe 1
                cellLayouts[2]?.visibility = View.VISIBLE // Zelle 3 - Position 1 in Reihe 1
                cellLayouts[3]?.visibility = View.VISIBLE // Zelle 4 - Position 2 in Reihe 1
                cellLayouts[0]?.visibility = View.VISIBLE // Zelle 1 - Position 0 in Reihe 2
                cellLayouts[7]?.visibility = View.VISIBLE // Layout Zelle 8, aber für Zelle 6 Daten
                cellLayouts[6]?.visibility = View.VISIBLE // Layout Zelle 7, aber für Zelle 5 Daten
                // Aktualisiere die Beschriftungen für die umgemappten Layouts
                updateCellLabels(activeCellCount)
            }
            8 -> {
                // Alle anzeigen
                for (i in 0 until MultiCellConfig.maxDisplayCells) {
                    cellLayouts[i]?.visibility = View.VISIBLE
                }
                updateCellLabels(activeCellCount)
            }
            else -> {
                // Fallback
                for (i in 0 until minOf(activeCellCount, MultiCellConfig.maxDisplayCells)) {
                    cellLayouts[i]?.visibility = View.VISIBLE
                }
                updateCellLabels(activeCellCount)
            }
        }
    }
    
    private fun updateCellLabels(activeCellCount: Int) {
        // Finde die Label TextViews in den Layouts und aktualisiere sie
        when (activeCellCount) {
            4 -> {
                // Layout 7 (Zelle 8 Layout) zeigt jetzt Zelle 4 Daten
                val layout8 = cellLayouts[7]
                layout8?.let { findCellLabelInLayout(it, "Zelle 4") }
            }
            6 -> {
                // Layout 7 (Zelle 8 Layout) zeigt jetzt Zelle 6 Daten  
                val layout8 = cellLayouts[7]
                layout8?.let { findCellLabelInLayout(it, "Zelle 6") }
                
                // Layout 6 (Zelle 7 Layout) zeigt jetzt Zelle 5 Daten
                val layout7 = cellLayouts[6]
                layout7?.let { findCellLabelInLayout(it, "Zelle 5") }
            }
            8 -> {
                // Standardbeschriftungen wiederherstellen
                val layout8 = cellLayouts[7]
                layout8?.let { findCellLabelInLayout(it, "Zelle 8") }
                
                val layout7 = cellLayouts[6]
                layout7?.let { findCellLabelInLayout(it, "Zelle 7") }
            }
        }
    }
    
    private fun findCellLabelInLayout(layout: LinearLayout, labelText: String) {
        // Suche nach dem ersten TextView, das ein Zellen-Label ist (nicht Serial oder Counts)
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView && child.text.toString().startsWith("Zelle")) {
                child.text = labelText
                break
            }
        }
    }

    private fun initializeUIWithoutData() {
        configuredCells.forEach { cellNumber ->
            val cellIndex = cellNumber - 1
            cellDataArray[cellIndex] = CellDisplayData(cellNumber = cellNumber, counts = "---", serialNumber = "Lädt...")
            updateCellUI(cellIndex)
            updateCellStatus(cellIndex, StatusType.PENDING)
        }
        commonData = CommonDisplayData(baudrate = "N/A", lastUpdate = 0L)
        updateCommonUI()
        updateIndividualCellDetails()
        updateOverallStatus("Bereit - ${configuredCells.size} Zellen konfiguriert", StatusType.PENDING)
        updateButtonStates()
    }

    // --- KERNLOGIK: Die Abfrage der Zellen ---

    private fun refreshAllCells() {
        if (isLiveMode) return
        if (configuredCells.isEmpty()) {
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
            return
        }

        showLoading(true)
        updateOverallStatus("Lade Daten für ${configuredCells.size} Zellen...", StatusType.CONNECTING)

        lifecycleScope.launch {
            var successfulFetches = 0
            val allFetchedData = mutableMapOf<Int, CellDisplayData>()

            // --- WICHTIGSTE ÄNDERUNG: Sequenzielle Abfrage statt paralleler ---
            // Wir gehen jetzt jede Zelle einzeln durch.
            for (cellNumber in configuredCells) {
                if (!isActive) break // Job abbrechen, wenn das Fragment zerstört wird

                // UI-Update, um zu zeigen, welche Zelle gerade abgefragt wird
                withContext(Dispatchers.Main) {
                    updateOverallStatus("Frage Zelle $cellNumber ab...", StatusType.CONNECTING)
                    updateCellStatus(cellNumber - 1, StatusType.CONNECTING)
                }

                // Daten für die eine Zelle abrufen
                val cellResult = fetchCellData(cellNumber)

                // Ergebnis verarbeiten
                withContext(Dispatchers.Main) {
                    val arrayIndex = cellNumber - 1
                    if (cellResult != null) {
                        cellDataArray[arrayIndex] = cellResult
                        allFetchedData[cellNumber] = cellResult
                        updateCellStatus(arrayIndex, StatusType.CONNECTED)
                        successfulFetches++
                    } else {
                        cellDataArray[arrayIndex] = CellDisplayData(cellNumber = cellNumber, counts = "Fehler", serialNumber = "Fehler")
                        updateCellStatus(arrayIndex, StatusType.ERROR)
                    }
                    updateCellUI(arrayIndex)
                }

                // --- HIER IST DIE PAUSE ---
                // Wir warten kurz, bevor wir die nächste Zelle abfragen.
                delay(CELL_QUERY_DELAY_MS)
            }

            // Nach der Schleife die Gesamt-UI aktualisieren
            withContext(Dispatchers.Main) {
                if (isActive) {
                    if (successfulFetches > 0) {
                        loadCommonDataFromFetched(allFetchedData)
                        updateOverallStatus("$successfulFetches/${configuredCells.size} Zellen erfolgreich geladen", StatusType.CONNECTED)
                    } else if (configuredCells.isNotEmpty()) {
                        updateOverallStatus("Keine der ${configuredCells.size} Zellen erreichbar", StatusType.ERROR)
                        commonData = CommonDisplayData(baudrate = "Fehler")
                    }
                    updateCommonUI()
                    updateIndividualCellDetails()
                    animateDataUpdate()
                    showLoading(false)
                }
            }
        }
    }

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

                    // Wir fragen jetzt alle Daten pro Zelle in einer Verbindung ab
                    data.serialNumber = fetchSingleCellCommand(FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.SERIAL_NUMBER), outputStream, inputStream) ?: "S/N: Fehler"
                    delay(50) // Kleine Pause auch zwischen Befehlen

                    data.counts = fetchSingleCellCommand(FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS), outputStream, inputStream) ?: run { commandSuccess = false; "Fehler" }
                    delay(50)

                    if (commandSuccess) {
                        data.baudrate = fetchSingleCellCommand(FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.BAUDRATE), outputStream, inputStream) ?: "Fehler"
                        delay(50)
                        data.filter = fetchSingleCellCommand(FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.FILTER), outputStream, inputStream) ?: "Fehler"
                        delay(50)
                        data.version = fetchSingleCellCommand(FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.VERSION), outputStream, inputStream) ?: "Fehler"
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

    private suspend fun fetchSingleCellCommand(commandBytes: ByteArray, outputStream: OutputStream, inputStream: InputStream): String? {
        if (!coroutineContext.isActive) return null
        return try {
            outputStream.write(commandBytes)
            outputStream.flush()
            val rawResponse = readFlintecResponse(inputStream)
            FlintecRC3DMultiCellCommands.parseMultiCellResponse(rawResponse)?.let { data ->
                when (data) {
                    is FlintecData.Counts -> data.value
                    is FlintecData.SerialNumber -> data.value
                    is FlintecData.Baudrate -> data.value
                    is FlintecData.Filter -> data.value
                    is FlintecData.Version -> data.value
                    else -> rawResponse // Fallback
                }
            }
        } catch (e: Exception) {
            Log.w("MultiCellOverview", "Fehler beim Senden/Empfangen eines Befehls: ${e.message}")
            null
        }
    }

    // --- Live Modus ---
    // Auch hier wird jetzt sequenziell abgefragt.
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
                for (cellNumber in cellsWithData) {
                    if (!isLiveMode || !isActive) break

                    val counts = fetchSingleCellCounts(cellNumber)

                    withContext(Dispatchers.Main) {
                        val arrayIndex = cellNumber - 1
                        if (counts != null) {
                            cellDataArray[arrayIndex].counts = counts
                            cellDataArray[arrayIndex].lastUpdate = System.currentTimeMillis()
                            animateCellCountsUpdate(arrayIndex, counts)
                            updateCellStatus(arrayIndex, StatusType.LIVE)
                        } else {
                            updateCellStatus(arrayIndex, StatusType.ERROR)
                        }
                    }
                    if (isLiveMode && isActive) {
                        delay(CELL_QUERY_DELAY_MS) // Auch hier die Pause einhalten
                    }
                }
                if (isLiveMode && isActive) {
                    delay(1000L) // Eine Sekunde Pause zwischen kompletten Durchläufen
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
            if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A" && cellDataArray[idx].counts != "---") {
                updateCellStatus(idx, StatusType.CONNECTED)
            } else if (cellDataArray[idx].counts != "N/A") {
                updateCellStatus(idx, StatusType.ERROR)
            }
        }
        if (configuredCells.isNotEmpty()) {
            updateOverallStatus("$responsiveCount/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
        } else {
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
        }
    }

    private suspend fun fetchSingleCellCounts(cellNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(getMoxaIpAddress(), getMoxaPort()), 3000)
                    socket.soTimeout = 2000
                    fetchSingleCellCommand(
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        socket.getOutputStream(), socket.getInputStream()
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }


    // --- UI Update Funktionen ---
    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex !in 0 until MultiCellConfig.maxDisplayCells) return
        val cellNumber = cellIndex + 1
        val cellData = cellDataArray[cellIndex]
        
        // Finde das korrekte Layout für diese Zelle basierend auf der Konfiguration
        val layoutIndex = getPhysicalLayoutIndex(cellNumber, configuredCells.size)
        if (layoutIndex == null) return
        
        val countsTextView = cellCountsTextViews[layoutIndex] ?: return
        val serialTextView = cellSerialTextViews[layoutIndex] ?: return
        
        countsTextView.text = cellData.counts
        serialTextView.text = formatSerialNumber(cellData.serialNumber)
    }
    
    // Mapping von logischer Zellen-Nummer zu physischem Layout-Index
    private fun getPhysicalLayoutIndex(cellNumber: Int, activeCellCount: Int): Int? {
        return when (activeCellCount) {
            4 -> when (cellNumber) {
                1 -> 0  // Zelle 1 -> Layout Index 0 (layoutCell1)
                2 -> 1  // Zelle 2 -> Layout Index 1 (layoutCell2)
                3 -> 2  // Zelle 3 -> Layout Index 2 (layoutCell3)
                4 -> 7  // Zelle 4 -> Layout Index 7 (layoutCell8 Position)
                else -> null
            }
            6 -> when (cellNumber) {
                1 -> 0  // Zelle 1 -> Layout Index 0 (layoutCell1)
                2 -> 1  // Zelle 2 -> Layout Index 1 (layoutCell2)
                3 -> 2  // Zelle 3 -> Layout Index 2 (layoutCell3)
                4 -> 3  // Zelle 4 -> Layout Index 3 (layoutCell4)
                5 -> 6  // Zelle 5 -> Layout Index 6 (layoutCell7 Position)
                6 -> 7  // Zelle 6 -> Layout Index 7 (layoutCell8 Position)
                else -> null
            }
            8 -> cellNumber - 1  // Standard 1:1 Mapping
            else -> cellNumber - 1  // Standard Mapping für andere Konfigurationen
        }
    }

    private fun updateCommonUI() {
        textBaudrateAll.text = when (val baud = commonData.baudrate) {
            "N/A", "Unbekannt", "Fehler" -> baud
            else -> "$baud bps"
        }
        textLastUpdateAll.text = if (commonData.lastUpdate > 0) {
            "Letzte Aktualisierung: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(commonData.lastUpdate))}"
        } else {
            "Noch nicht aktualisiert"
        }
    }

    private fun updateIndividualCellDetails() {
        if (!isAdded) return
        layoutIndividualDetailsContainer.removeAllViews()
        val activeAndLoadedCells = configuredCells.mapNotNull { cellDataArray.getOrNull(it - 1) }
            .filter { it.counts != "Fehler" && it.counts != "N/A" && it.counts != "---" }
        if (activeAndLoadedCells.isEmpty()){
            val noDataView = TextView(requireContext()).apply {
                text = "Keine Detail-Daten geladen. Bitte 'Alle aktualisieren' drücken."
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
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
                setColorFilter(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(
                    (20 * resources.displayMetrics.density).toInt(),
                    (20 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                }
            }

            val cellLabel = TextView(requireContext()).apply {
                text = "Zelle ${cellData.cellNumber}:"
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val detailsText = TextView(requireContext()).apply {
                text = " Filter: ${cellData.filter}  |  Version: ${cellData.version}"
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
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

    private fun updateCellStatus(cellIndex: Int, type: StatusType) {
        if (cellIndex !in 0 until MultiCellConfig.maxDisplayCells) return
        val cellNumber = cellIndex + 1
        
        // Finde das korrekte Layout für diese Zelle basierend auf der Konfiguration
        val layoutIndex = getPhysicalLayoutIndex(cellNumber, configuredCells.size)
        if (layoutIndex == null) return
        
        val indicator = cellStatusIndicators[layoutIndex] ?: return
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
        if (!isAdded) return
        textOverallStatus.text = status
        val colorId = when (type) {
            StatusType.CONNECTED -> R.color.status_success_color
            StatusType.CONNECTING -> R.color.status_pending_color
            StatusType.LIVE -> R.color.status_live_color
            StatusType.ERROR -> R.color.status_error_color
            StatusType.PENDING -> R.color.status_pending_color
        }
        textOverallStatus.setTextColor(ContextCompat.getColor(requireContext(), colorId))
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

    // --- Utility & Lifecycle ---
    private fun loadCommonDataFromFetched(fetchedDataMap: Map<Int, CellDisplayData?>) {
        val firstSuccessfulData = fetchedDataMap.values.firstOrNull()
        if (firstSuccessfulData != null) {
            commonData.baudrate = firstSuccessfulData.baudrate
            commonData.lastUpdate = System.currentTimeMillis()
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

    private fun animateDataUpdate() {
        view?.animate()?.alpha(0.8f)?.setDuration(150)?.withEndAction {
            view?.animate()?.alpha(1.0f)?.setDuration(150)?.start()
        }?.start()
    }

    private fun animateCellCountsUpdate(cellIndex: Int, newValueString: String) {
        val cellNumber = cellIndex + 1
        val layoutIndex = getPhysicalLayoutIndex(cellNumber, configuredCells.size) ?: return
        val textView = cellCountsTextViews[layoutIndex] ?: return
        if (textView.text.toString() != newValueString) {
            textView.animate().alpha(0.0f).setDuration(150).withEndAction {
                textView.text = newValueString
                textView.animate().alpha(1.0f).setDuration(150).start()
            }.start()
        }
    }

    private fun formatSerialNumber(serialNumber: String): String {
        return when {
            serialNumber.isBlank() || serialNumber == "Unbekannt" || serialNumber == "Fehler" -> "S/N: ----"
            serialNumber.startsWith("S/N:") -> serialNumber
            else -> "S/N: $serialNumber"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLiveMode()
        liveUpdateJob?.cancel()
    }

    // Data Classes
    data class CellDisplayData(
        var cellNumber: Int = 0,
        var counts: String = "0",
        var serialNumber: String = "Unbekannt",
        var baudrate: String = "Unbekannt",
        var filter: String = "Unbekannt",
        var version: String = "Unbekannt",
        var lastUpdate: Long = 0L
    )
    data class CommonDisplayData(var baudrate: String = "Unbekannt", var lastUpdate: Long = 0L)
    enum class StatusType { CONNECTED, CONNECTING, LIVE, ERROR, PENDING }
}
