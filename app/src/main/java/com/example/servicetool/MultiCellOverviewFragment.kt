package com.example.servicetool

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.coroutineContext // Import für coroutineContext

class MultiCellOverviewFragment : Fragment() {

    // UI Komponenten
    private lateinit var textOverallStatus: TextView
    private lateinit var progressIndicatorOverall: CircularProgressIndicator
    private lateinit var buttonRefreshAll: Button
    private lateinit var buttonStartLiveAll: Button
    private lateinit var buttonStopLiveAll: Button
    private lateinit var textLastUpdateAll: TextView

    // Gemeinsame Details
    private lateinit var textTemperatureAll: TextView
    private lateinit var textBaudrateAll: TextView
    private lateinit var textFilterAll: TextView
    private lateinit var textVersionAll: TextView

    // Arrays für die 8 Zellen
    private val cellCountsTextViews = arrayOfNulls<TextView>(8)
    private val cellStatusIndicators = arrayOfNulls<ImageView>(8)

    // Daten für alle 8 Zellen
    private val cellDataArray = Array(8) { CellDisplayData() }
    private var commonData = CommonDisplayData()
    private var isLiveMode = false
    private var liveUpdateJob: Job? = null

    // NEU: Set zur Speicherung von Zellen, die beim Refresh nicht geantwortet haben
    private var unresponsiveCellsDuringRefresh = mutableSetOf<Int>()

    // Multi-Cell Konfiguration
    private val MOXA_IP = MultiCellConfig.MOXA_IP
    private val MOXA_PORT = MultiCellConfig.MOXA_PORT
    // availableCells wird jetzt beim Start des Live-Modus gefiltert
    private val configuredCells = MultiCellConfig.availableCells.sorted()
    private val maxCellsToDisplay = MultiCellConfig.maxDisplayCells
    private val CELL_QUERY_DELAY_MS = 1000L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_multicell_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
        updateUI()

        // Initiale Datenabfrage für alle konfigurierten Zellen
        refreshAllCells()
    }

    private fun initializeViews(view: View) {
        // Header UI
        textOverallStatus = view.findViewById(R.id.textOverallStatus)
        progressIndicatorOverall = view.findViewById(R.id.progressIndicatorOverall)

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

        // Initialize cell arrays - dynamisch die TextViews und ImageViews finden
        for (i in 0 until maxCellsToDisplay) {
            val cellNum = i + 1
            try {
                val countsResId = resources.getIdentifier("textCountsCell$cellNum", "id", requireContext().packageName)
                val statusResId = resources.getIdentifier("statusIndicatorCell$cellNum", "id", requireContext().packageName)

                if (countsResId != 0) {
                    cellCountsTextViews[i] = view.findViewById(countsResId)
                }
                if (statusResId != 0) {
                    cellStatusIndicators[i] = view.findViewById(statusResId)
                }
            } catch (e: Exception) {
                Log.w("MultiCellOverview", "UI Element für Zelle $cellNum nicht gefunden: ${e.message}")
            }
        }
        Log.i("MultiCellOverview", "Initialisiert für ${configuredCells.size} konfigurierte Zellen: ${configuredCells.joinToString(", ")}")
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

    private fun refreshAllCells() {
        if (isLiveMode) return

        showLoading(true)
        // NEU: Set der nicht antwortenden Zellen für diesen Refresh-Zyklus leeren
        unresponsiveCellsDuringRefresh.clear()
        updateOverallStatus("Verbinde mit ${configuredCells.size} konfigurierten Zellen...", StatusType.CONNECTING)

        lifecycleScope.launch {
            try {
                var successCount = 0
                val totalConfiguredCells = configuredCells.size
                val fetchedResults = mutableListOf<Pair<Int, CellDisplayData?>>()

                // Lade Daten für alle konfigurierten Zellen sequenziell mit Verzögerung
                for ((index, cellNumber) in configuredCells.withIndex()) {
                    if (!isActive) {
                        Log.i("MultiCellOverview", "refreshAllCells Coroutine abgebrochen.")
                        return@launch
                    }
                    Log.d("MultiCellOverview", "Starte Datenabfrage für Zelle $cellNumber")
                    val result = fetchCellData(cellNumber)
                    fetchedResults.add(cellNumber to result)

                    if (index < totalConfiguredCells - 1) {
                        Log.d("MultiCellOverview", "Warte ${CELL_QUERY_DELAY_MS}ms bis zur nächsten Zelle.")
                        delay(CELL_QUERY_DELAY_MS)
                    }
                }

                if (!isActive) {
                    Log.i("MultiCellOverview", "refreshAllCells Coroutine nach Datenabruf abgebrochen.")
                    return@launch
                }

                // Verarbeite die gesammelten Ergebnisse
                for ((cellNumber, cellData) in fetchedResults) {
                    val arrayIndex = cellNumber - 1

                    if (cellData != null && arrayIndex < maxCellsToDisplay) {
                        cellDataArray[arrayIndex] = cellData
                        successCount++
                        updateCellUI(arrayIndex)
                        updateCellStatus(arrayIndex, StatusType.CONNECTED)
                        unresponsiveCellsDuringRefresh.remove(cellNumber) // NEU: Erfolgreich, aus Set entfernen
                        Log.i("MultiCellOverview", "Zelle $cellNumber erfolgreich geladen")
                    } else if (arrayIndex < maxCellsToDisplay) {
                        Log.w("MultiCellOverview", "Zelle $cellNumber konnte nicht geladen werden")
                        updateCellStatus(arrayIndex, StatusType.ERROR)
                        cellDataArray[arrayIndex] = CellDisplayData(
                            cellNumber = cellNumber,
                            counts = "Fehler",
                            lastUpdate = System.currentTimeMillis()
                        )
                        updateCellUI(arrayIndex)
                        unresponsiveCellsDuringRefresh.add(cellNumber) // NEU: Fehler, zu Set hinzufügen
                    }
                }

                // Setze UI für Zellen, die nicht in MultiCellConfig.availableCells sind (falls maxDisplayCells > availableCells.size)
                for (i in 0 until maxCellsToDisplay) {
                    val cellNumberLoop = i + 1
                    if (!configuredCells.contains(cellNumberLoop)) {
                        cellDataArray[i] = CellDisplayData().apply {
                            this.cellNumber = cellNumberLoop
                            this.counts = "N/A" // Nicht konfiguriert
                            this.lastUpdate = System.currentTimeMillis()
                        }
                        updateCellUI(i)
                        updateCellStatus(i, StatusType.PENDING) // Eigener Status für nicht konfigurierte
                        Log.d("MultiCellOverview", "Zelle $cellNumberLoop als nicht konfiguriert markiert")
                    }
                }

                val currentlyActiveCellsCount = totalConfiguredCells - unresponsiveCellsDuringRefresh.size
                if (successCount > 0) { // successCount ist hier identisch mit currentlyActiveCellsCount
                    loadCommonData() // Lade gemeinsame Daten, wenn mindestens eine Zelle erfolgreich war
                    updateOverallStatus("$currentlyActiveCellsCount/$totalConfiguredCells Zellen verbunden", StatusType.CONNECTED)
                } else if (totalConfiguredCells > 0) {
                    updateOverallStatus("Keine der $totalConfiguredCells konfigurierten Zellen erreichbar", StatusType.ERROR)
                } else {
                    updateOverallStatus("Keine Zellen konfiguriert", StatusType.PENDING)
                }


                animateDataUpdate()

            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "refreshAllCells Coroutine wurde abgebrochen (CancellationException).")
            }
            catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden: ${e.message}", e)
                updateOverallStatus("Fehler: ${e.localizedMessage ?: e.message}", StatusType.ERROR)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun startLiveMode() {
        if (isLiveMode) return

        // NEU: Bestimme, welche Zellen im Live-Modus abgefragt werden sollen
        val cellsToQueryInLiveMode = configuredCells.filter { cellNum ->
            !unresponsiveCellsDuringRefresh.contains(cellNum)
        }

        if (cellsToQueryInLiveMode.isEmpty()) {
            Log.w("MultiCellOverview", "Live-Modus nicht gestartet, da keine Zellen beim letzten Refresh erreichbar waren oder keine konfiguriert sind.")
            updateOverallStatus("Keine Zellen für Live-Modus verfügbar", StatusType.ERROR)
            // Optional: Buttons entsprechend anpassen
            buttonStartLiveAll.isEnabled = true
            buttonStopLiveAll.isEnabled = false
            return
        }

        isLiveMode = true
        updateButtonStates()
        updateOverallStatus("Live-Modus aktiv für ${cellsToQueryInLiveMode.size} Zellen", StatusType.LIVE)
        Log.i("MultiCellOverview", "Starte Live-Modus für Zellen: ${cellsToQueryInLiveMode.joinToString(", ")}")


        liveUpdateJob = lifecycleScope.launch {
            while (isLiveMode && isActive) {
                try {
                    val fetchedCounts = mutableListOf<Pair<Int, String?>>()

                    // Aktualisiere nur die Zellen, die beim Refresh erreichbar waren
                    for ((index, cellNumber) in cellsToQueryInLiveMode.withIndex()) {
                        if (!isLiveMode || !isActive) break

                        val counts = fetchSingleCellCounts(cellNumber)
                        fetchedCounts.add(cellNumber to counts)

                        if (isLiveMode && isActive && index < cellsToQueryInLiveMode.size - 1) {
                            Log.d("MultiCellOverview_Live", "Warte ${CELL_QUERY_DELAY_MS}ms bis zur nächsten Zelle im Live-Modus.")
                            delay(CELL_QUERY_DELAY_MS)
                        }
                    }

                    if (!isLiveMode || !isActive) break

                    for ((cellNumber, newCounts) in fetchedCounts) {
                        val arrayIndex = cellNumber - 1

                        if (newCounts != null && arrayIndex < maxCellsToDisplay) {
                            cellDataArray[arrayIndex].counts = newCounts
                            cellDataArray[arrayIndex].lastUpdate = System.currentTimeMillis()
                            animateCellCountsUpdate(arrayIndex, newCounts)
                            updateCellStatus(arrayIndex, StatusType.LIVE)
                        } else if (arrayIndex < maxCellsToDisplay) {
                            // Wenn eine Zelle im Live-Modus plötzlich nicht mehr antwortet
                            updateCellStatus(arrayIndex, StatusType.ERROR)
                            Log.w("MultiCellOverview_Live", "Zelle $cellNumber hat im Live-Modus nicht geantwortet.")
                        }
                    }

                    commonData.lastUpdate = System.currentTimeMillis()
                    updateCommonUI()

                } catch (e: CancellationException) {
                    Log.i("MultiCellOverview", "Live-Update Job wurde abgebrochen (CancellationException).")
                    break
                } catch (e: Exception) {
                    Log.e("MultiCellOverview", "Live-Update Fehler: ${e.message}", e)
                }
                if (isLiveMode && isActive) {
                    delay(MultiCellConfig.LIVE_UPDATE_INTERVAL)
                }
            }
            Log.i("MultiCellOverview", "Live-Modus Schleife beendet. isLiveMode: $isLiveMode, isActive: $isActive")
        }
    }

    private fun stopLiveMode() {
        isLiveMode = false
        liveUpdateJob?.cancel()
        liveUpdateJob = null
        updateButtonStates()

        // Setze Status basierend auf dem letzten Refresh-Zustand
        var responsiveCount = 0
        configuredCells.forEach { cellNum ->
            val index = cellNum -1
            if (index < maxCellsToDisplay) {
                if (!unresponsiveCellsDuringRefresh.contains(cellNum) && cellDataArray[index].counts != "Fehler" && cellDataArray[index].counts != "N/A") {
                    updateCellStatus(index, StatusType.CONNECTED)
                    responsiveCount++
                } else if (cellDataArray[index].counts != "N/A") { // Wenn konfiguriert, aber fehlerhaft
                    updateCellStatus(index, StatusType.ERROR)
                }
            }
        }
        if (configuredCells.isNotEmpty()) {
            updateOverallStatus("$responsiveCount/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
        } else {
            updateOverallStatus("Keine Zellen konfiguriert", StatusType.PENDING)
        }
    }

    private suspend fun fetchCellData(cellNumber: Int): CellDisplayData? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            Log.d("MultiCellOverview", "fetchCellData für Zelle $cellNumber - Thread: ${Thread.currentThread().name}")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), MultiCellConfig.CONNECTION_TIMEOUT)
                    socket.soTimeout = MultiCellConfig.READ_TIMEOUT

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    val data = CellDisplayData()
                    data.cellNumber = cellNumber

                    data.counts = fetchSingleCellCommand(
                        "Counts",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        outputStream,
                        inputStream,
                        cellNumber
                    ) ?: "0"
                    if (!isActive) return@withContext null

                    // Lade gemeinsame Daten nur für die erste *konfigurierte und erreichbare* Zelle
                    // Diese Logik wird nun in loadCommonData() zentralisiert, basierend auf unresponsiveCellsDuringRefresh
                    if (cellNumber == configuredCells.firstOrNull { !unresponsiveCellsDuringRefresh.contains(it) } ) {
                        Log.d("MultiCellOverview", "Lade gemeinsame Daten von Zelle $cellNumber")
                        data.temperature = fetchSingleCellCommand(
                            "Temperatur",
                            FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.TEMPERATURE),
                            outputStream,
                            inputStream,
                            cellNumber
                        ) ?: "0°C"
                        if (!isActive) return@withContext null

                        data.baudrate = fetchSingleCellCommand(
                            "Baudrate",
                            FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.BAUDRATE),
                            outputStream,
                            inputStream,
                            cellNumber
                        ) ?: "Unbekannt"
                        if (!isActive) return@withContext null

                        data.filter = fetchSingleCellCommand(
                            "Filter",
                            FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.FILTER),
                            outputStream,
                            inputStream,
                            cellNumber
                        ) ?: "Unbekannt"
                        if (!isActive) return@withContext null

                        data.version = fetchSingleCellCommand(
                            "Version",
                            FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.VERSION),
                            outputStream,
                            inputStream,
                            cellNumber
                        ) ?: "Unbekannt"
                        if (!isActive) return@withContext null
                    }

                    data.lastUpdate = System.currentTimeMillis()
                    Log.i("MultiCellOverview", "Zelle $cellNumber geladen: Counts=${data.counts}")
                    return@withContext data
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "fetchCellData für Zelle $cellNumber abgebrochen (CancellationException).")
                return@withContext null
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden von Zelle $cellNumber: ${e.message}", e)
                return@withContext null
            }
        }
    }

    private suspend fun fetchSingleCellCounts(cellNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            Log.d("MultiCellOverview_Live", "fetchSingleCellCounts für Zelle $cellNumber - Thread: ${Thread.currentThread().name}")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 3000)
                    socket.soTimeout = 2000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    return@withContext fetchSingleCellCommand(
                        "Counts (Live)",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        outputStream,
                        inputStream,
                        cellNumber
                    )
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview_Live", "fetchSingleCellCounts für Zelle $cellNumber abgebrochen (CancellationException).")
                return@withContext null
            } catch (e: Exception) {
                Log.e("MultiCellOverview_Live", "Fehler bei Counts von Zelle $cellNumber: ${e.message}", e)
                return@withContext null
            }
        }
    }

    private suspend fun loadCommonData() {
        // Finde die erste konfigurierte Zelle, die beim letzten Refresh erreichbar war
        val firstResponsiveConfiguredCellNumber = configuredCells.firstOrNull {
            !unresponsiveCellsDuringRefresh.contains(it) &&
                    (cellDataArray.getOrNull(it - 1)?.counts?.let { c -> c != "0" && c != "N/A" && c != "Fehler" } == true)
        }

        if (firstResponsiveConfiguredCellNumber != null) {
            val firstCellIndex = firstResponsiveConfiguredCellNumber - 1
            // Es wird angenommen, dass die Daten bereits in cellDataArray[firstCellIndex] geladen wurden,
            // da fetchCellData die gemeinsamen Daten für die erste ERFOLGREICHE Zelle lädt.
            // Wir müssen hier sicherstellen, dass wir die Daten von einer Zelle nehmen, die auch erfolgreich war.
            val firstCellData = cellDataArray.getOrNull(firstCellIndex)

            if (firstCellData != null && firstCellData.counts != "0" && firstCellData.counts != "N/A" && firstCellData.counts != "Fehler") {
                commonData.temperature = firstCellData.temperature
                commonData.baudrate = firstCellData.baudrate
                commonData.filter = firstCellData.filter
                commonData.version = firstCellData.version
                commonData.lastUpdate = firstCellData.lastUpdate // Nimm die LastUpdate Zeit der Zelle

                withContext(Dispatchers.Main) {
                    updateCommonUI()
                }
                Log.i("MultiCellOverview", "Gemeinsame Daten von Zelle $firstResponsiveConfiguredCellNumber geladen.")
            } else {
                Log.w("MultiCellOverview", "Erste responsive Zelle ($firstResponsiveConfiguredCellNumber) hat keine gültigen Daten für CommonDisplay (Counts: ${firstCellData?.counts}). Gemeinsame Daten werden nicht aktualisiert.")
            }
        } else {
            Log.w("MultiCellOverview", "Keine responsive Zelle gefunden, um CommonData zu laden.")
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
            Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv).")
            return null
        }
        return try {
            Log.d("MultiCellOverview", "Sende an Zelle $cellNumber ($commandName): ${commandBytes.joinToString(" ") { "%02X".format(it) }}")

            outputStream.write(commandBytes)
            outputStream.flush()

            val rawResponse = readFlintecResponse(inputStream)
            if (!coroutineContext.isActive && rawResponse.isEmpty()) {
                Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen während readFlintecResponse.")
                return null
            }

            if (rawResponse.isNotEmpty()) {
                Log.d("MultiCellOverview", "Zelle $cellNumber Antwort ($commandName): '$rawResponse'")

                val parsedData = FlintecRC3DMultiCellCommands.parseMultiCellResponse(rawResponse, cellNumber)
                var valueToReturn: String? = null

                when (parsedData) {
                    is FlintecData.Counts -> valueToReturn = parsedData.value
                    is FlintecData.Temperature -> valueToReturn = parsedData.value
                    is FlintecData.Version -> valueToReturn = parsedData.value
                    is FlintecData.Baudrate -> valueToReturn = "${parsedData.value} bps"
                    is FlintecData.Filter -> valueToReturn = parsedData.value
                    else -> {
                        Log.w("MultiCellOverview", "Unbekannter oder nicht behandelter Datentyp von Zelle $cellNumber ($commandName). Parser gab zurück: ${parsedData?.javaClass?.simpleName}. Rohantwort: '$rawResponse'")
                        valueToReturn = rawResponse
                    }
                }

                if (valueToReturn != null) {
                    Log.i("MultiCellOverview", "Zelle $cellNumber $commandName erfolgreich verarbeitet: '$valueToReturn'")
                } else {
                    Log.w("MultiCellOverview", "Zelle $cellNumber $commandName - valueToReturn ist null nach Parsing.")
                    valueToReturn = rawResponse
                }
                return valueToReturn
            } else {
                Log.w("MultiCellOverview", "Zelle $cellNumber $commandName: Keine (gültige) Antwort")
                return null
            }
        } catch (e: CancellationException) {
            Log.i("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber abgebrochen (CancellationException).")
            throw e
        }
        catch (e: Exception) {
            Log.e("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber fehlgeschlagen: ${e.message}", e)
            return null
        }
    }


    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            if(!isActive) return@withContext ""
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var etxFound = false
                val tempReadBuffer = ByteArray(64)

                val startTime = System.currentTimeMillis()
                val readOverallTimeout = MultiCellConfig.READ_TIMEOUT

                while (isActive && System.currentTimeMillis() - startTime < readOverallTimeout && !etxFound) {
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(tempReadBuffer)
                        if (bytesRead == -1) {
                            Log.w("MultiCellOverview", "End of stream erreicht beim Lesen der Antwort.")
                            break
                        }

                        for (k in 0 until bytesRead) {
                            if(!isActive) return@withContext ""

                            val byte = tempReadBuffer[k]
                            if (byte.toInt() == 0x02 /* STX */) {
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

                if(!isActive && !etxFound) {
                    Log.i("MultiCellOverview", "readFlintecResponse abgebrochen, bevor ETX gefunden wurde.")
                    return@withContext ""
                }

                if (stxFound && etxFound) {
                    val responseString = String(responseBuffer.toByteArray(), Charsets.US_ASCII)
                    Log.d("MultiCellOverview", "Gültige Antwort empfangen (kann leer sein): '$responseString'")
                    return@withContext responseString
                } else if (stxFound && !etxFound) {
                    Log.w("MultiCellOverview", "Antwort begonnen (STX) aber nicht beendet (ETX) innerhalb Timeout. Buffer: ${responseBuffer.joinToString("") { "%02X".format(it) }}")
                } else if (!stxFound) {
                    Log.w("MultiCellOverview", "Kein STX in der Antwort gefunden innerhalb Timeout.")
                }
                return@withContext ""
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "readFlintecResponse abgebrochen (CancellationException).")
                return@withContext ""
            }
            catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Lesen der Flintec Antwort: ${e.message}", e)
                return@withContext ""
            }
        }
    }

    private fun updateUI() {
        updateCommonUI()
        for (i in 0 until maxCellsToDisplay) {
            updateCellUI(i)
        }
    }

    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex < 0 || cellIndex >= maxCellsToDisplay) return
        val textView = cellCountsTextViews[cellIndex] ?: return
        val cellData = cellDataArray[cellIndex]
        textView.text = cellData.counts
    }

    private fun updateCommonUI() {
        textTemperatureAll.text = commonData.temperature
        textBaudrateAll.text = commonData.baudrate
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
        if (cellIndex < 0 || cellIndex >= maxCellsToDisplay) return

        val drawableId = when (type) {
            StatusType.CONNECTED -> R.drawable.ic_status_success
            StatusType.CONNECTING -> R.drawable.ic_status_pending
            StatusType.LIVE -> R.drawable.ic_status_live
            StatusType.ERROR -> R.drawable.ic_status_error
            StatusType.PENDING -> R.drawable.ic_status_pending
        }
        cellStatusIndicators[cellIndex]?.setImageResource(drawableId)
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
        textOverallStatus.setTextColor(ContextCompat.getColor(requireContext(), colorId))
    }

    private fun animateDataUpdate() {
        view?.animate()
            ?.alpha(0.8f)
            ?.setDuration(150)
            ?.withEndAction {
                view?.animate()
                    ?.alpha(1.0f)
                    ?.setDuration(150)
                    ?.start()
            }
            ?.start()
    }

    private fun animateCellCountsUpdate(cellIndex: Int, newValueString: String) {
        val textView = cellCountsTextViews[cellIndex] ?: return
        val currentText = textView.text.toString()

        if (currentText == newValueString) {
            return
        }

        Log.d("AnimCounts", "Zelle $cellIndex: Animation von '$currentText' zu '$newValueString'.")

        textView.alpha = 0.0f
        textView.text = newValueString
        textView.animate().alpha(1.0f).setDuration(200).start()
    }


    private fun showLoading(show: Boolean) {
        progressIndicatorOverall.visibility = if (show) View.VISIBLE else View.GONE
        buttonRefreshAll.isEnabled = !show
        if (show) {
            buttonStartLiveAll.isEnabled = false
            buttonStopLiveAll.isEnabled = false
        } else {
            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        buttonStartLiveAll.isEnabled = !isLiveMode
        buttonStopLiveAll.isEnabled = isLiveMode
        buttonRefreshAll.isEnabled = !isLiveMode
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLiveMode()
    }

    data class CellDisplayData(
        var cellNumber: Int = 0,
        var counts: String = "0",
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

    companion object {
        // Beispiel: private const val SOME_CONSTANT = "value"
    }
}
