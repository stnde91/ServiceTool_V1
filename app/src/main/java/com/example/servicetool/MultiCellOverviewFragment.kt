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

    // Multi-Cell Konfiguration
    private val MOXA_IP = MultiCellConfig.MOXA_IP
    private val MOXA_PORT = MultiCellConfig.MOXA_PORT
    private val availableCells = MultiCellConfig.availableCells.sorted() // Sicherstellen, dass die Zellen sortiert abgefragt werden
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

        // Initiale Datenabfrage für alle verfügbaren Zellen
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
        Log.i("MultiCellOverview", "Initialisiert für ${availableCells.size} verfügbare Zellen: ${availableCells.joinToString(", ")}")
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
        updateOverallStatus("Verbinde mit ${availableCells.size} verfügbaren Zellen...", StatusType.CONNECTING)

        lifecycleScope.launch {
            try {
                var successCount = 0
                val totalCells = availableCells.size
                val fetchedResults = mutableListOf<Pair<Int, CellDisplayData?>>()

                // Lade Daten für alle verfügbaren Zellen sequenziell mit Verzögerung
                for ((index, cellNumber) in availableCells.withIndex()) {
                    if (!isActive) { // Überprüfe vor jeder Abfrage, ob die Coroutine noch aktiv ist
                        Log.i("MultiCellOverview", "refreshAllCells Coroutine abgebrochen.")
                        return@launch
                    }
                    Log.d("MultiCellOverview", "Starte Datenabfrage für Zelle $cellNumber")
                    val result = fetchCellData(cellNumber)
                    fetchedResults.add(cellNumber to result)

                    // Füge eine Verzögerung ein, außer bei der letzten Zelle
                    if (index < availableCells.size - 1) {
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
                    val arrayIndex = cellNumber - 1 // Zelle 1 -> Index 0, Zelle 2 -> Index 1, etc.

                    if (cellData != null && arrayIndex < maxCellsToDisplay) {
                        cellDataArray[arrayIndex] = cellData
                        successCount++
                        updateCellUI(arrayIndex) // Direkte UI-Aktualisierung ohne Animation hier
                        updateCellStatus(arrayIndex, StatusType.CONNECTED)
                        Log.i("MultiCellOverview", "Zelle $cellNumber erfolgreich geladen")
                    } else if (arrayIndex < maxCellsToDisplay) {
                        Log.w("MultiCellOverview", "Zelle $cellNumber konnte nicht geladen werden")
                        updateCellStatus(arrayIndex, StatusType.ERROR)
                        // Initialisiere mit Fehlerdaten, um N/A anzuzeigen
                        cellDataArray[arrayIndex] = CellDisplayData(
                            cellNumber = cellNumber,
                            counts = "Fehler",
                            lastUpdate = System.currentTimeMillis()
                        )
                        updateCellUI(arrayIndex) // Direkte UI-Aktualisierung
                    }
                }

                // Setze nicht verfügbare Zellen auf "Nicht konfiguriert"
                for (i in 0 until maxCellsToDisplay) {
                    val cellNumberLoop = i + 1 // Muss anders benannt werden als cellNumber aus der Schleife oben
                    if (!availableCells.contains(cellNumberLoop)) {
                        cellDataArray[i] = CellDisplayData().apply {
                            this.cellNumber = cellNumberLoop
                            this.counts = "N/A"
                            this.lastUpdate = System.currentTimeMillis()
                        }
                        updateCellUI(i) // Direkte UI-Aktualisierung
                        updateCellStatus(i, StatusType.PENDING)
                        Log.d("MultiCellOverview", "Zelle $cellNumberLoop als nicht konfiguriert markiert")
                    }
                }

                // Lade gemeinsame Daten von der ersten funktionierenden Zelle
                if (successCount > 0) {
                    loadCommonData()
                    updateOverallStatus("$successCount/$totalCells Zellen verbunden (${availableCells.size} konfiguriert)", StatusType.CONNECTED)
                } else {
                    updateOverallStatus("Keine Zellen erreichbar", StatusType.ERROR)
                }

                animateDataUpdate() // Allgemeine UI-Feedback-Animation für den gesamten Refresh

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

        isLiveMode = true
        updateButtonStates()
        updateOverallStatus("Live-Modus aktiv für ${availableCells.size} Zellen", StatusType.LIVE)

        liveUpdateJob = lifecycleScope.launch {
            while (isLiveMode && isActive) { // isActive hier auch prüfen
                try {
                    val fetchedCounts = mutableListOf<Pair<Int, String?>>()

                    // Aktualisiere nur verfügbare Zellen sequenziell mit Verzögerung
                    for ((index, cellNumber) in availableCells.withIndex()) {
                        if (!isLiveMode || !isActive) break

                        val counts = fetchSingleCellCounts(cellNumber)
                        fetchedCounts.add(cellNumber to counts)

                        if (isLiveMode && isActive && index < availableCells.size - 1) {
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
                            updateCellStatus(arrayIndex, StatusType.ERROR)
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
        updateOverallStatus("Live-Modus gestoppt", StatusType.CONNECTED)

        for (i in 0 until maxCellsToDisplay) {
            val cellNumber = i + 1
            if (availableCells.contains(cellNumber) && cellDataArray[i].counts.isNotEmpty() && cellDataArray[i].counts != "N/A" && cellDataArray[i].counts != "Fehler") {
                updateCellStatus(i, StatusType.CONNECTED)
            } else if (availableCells.contains(cellNumber) && (cellDataArray[i].counts == "Fehler" || cellDataArray[i].counts.isEmpty())) {
                updateCellStatus(i, StatusType.ERROR)
            }
        }
    }

    private suspend fun fetchCellData(cellNumber: Int): CellDisplayData? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null // Frühe Prüfung
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
                    ) ?: "0" // Default "0" wenn null
                    if (!isActive) return@withContext null

                    if (cellNumber == availableCells.firstOrNull()) {
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
            if (!isActive) return@withContext null // Frühe Prüfung
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
        val firstConfiguredCellNumber = availableCells.firstOrNull()
        if (firstConfiguredCellNumber != null) {
            val firstCellIndex = firstConfiguredCellNumber - 1
            if (firstCellIndex >= 0 && firstCellIndex < cellDataArray.size) { // Sicherstellen, dass der Index gültig ist
                val firstCellData = cellDataArray[firstCellIndex]
                // Nur aktualisieren, wenn die Daten tatsächlich erfolgreich geladen wurden
                if (firstCellData.counts != "0" && firstCellData.counts != "N/A" && firstCellData.counts != "Fehler") {
                    commonData.temperature = firstCellData.temperature
                    commonData.baudrate = firstCellData.baudrate
                    commonData.filter = firstCellData.filter
                    commonData.version = firstCellData.version
                    commonData.lastUpdate = firstCellData.lastUpdate

                    withContext(Dispatchers.Main) {
                        updateCommonUI()
                    }
                } else {
                    Log.w("MultiCellOverview", "Erste konfigurierte Zelle ($firstConfiguredCellNumber) hat keine gültigen Daten für CommonDisplay (Counts: ${firstCellData.counts}).")
                }
            } else {
                Log.w("MultiCellOverview", "Ungültiger Index $firstCellIndex für erste konfigurierte Zelle $firstConfiguredCellNumber.")
            }
        } else {
            Log.w("MultiCellOverview", "Keine Zellen konfiguriert, um CommonData zu laden.")
        }
    }

    private suspend fun fetchSingleCellCommand(
        commandName: String,
        commandBytes: ByteArray,
        outputStream: OutputStream,
        inputStream: InputStream,
        cellNumber: Int
    ): String? {
        if (!coroutineContext.isActive) { // Prüft den Context der aufrufenden Coroutine
            Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv).")
            return null
        }
        return try {
            Log.d("MultiCellOverview", "Sende an Zelle $cellNumber ($commandName): ${commandBytes.joinToString(" ") { "%02X".format(it) }}")

            outputStream.write(commandBytes)
            outputStream.flush()

            val rawResponse = readFlintecResponse(inputStream)
            if (!coroutineContext.isActive && rawResponse.isEmpty()) { // Prüft erneut nach dem Lesen
                Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen während readFlintecResponse.")
                return null
            }

            if (rawResponse.isNotEmpty()) {
                Log.d("MultiCellOverview", "Zelle $cellNumber Antwort ($commandName): '$rawResponse'")

                val parsedData = FlintecRC3DMultiCellCommands.parseMultiCellResponse(rawResponse, cellNumber)
                val result: String? = when (parsedData) {
                    is FlintecData.Counts -> parsedData.value
                    is FlintecData.Temperature -> parsedData.value
                    is FlintecData.Version -> parsedData.value
                    is FlintecData.Baudrate -> "${parsedData.value} bps"
                    is FlintecData.Filter -> parsedData.value
                    // TODO: Definiere FlintecData.Error mit einer 'message'-Eigenschaft in deiner FlintecData sealed class/interface
                    // is FlintecData.Error -> {
                    //     Log.w("MultiCellOverview", "Fehler von Zelle $cellNumber ($commandName) geparsed: ${parsedData.message}")
                    //     null // Oder einen spezifischen Fehlerstring zurückgeben
                    // }
                    else -> {
                        Log.w("MultiCellOverview", "Unbekannter oder nicht behandelter Datentyp von Zelle $cellNumber ($commandName) geparsed: '$rawResponse'. Typ: ${parsedData?.javaClass?.simpleName}")
                        val stringToClean = parsedData?.toString() ?: rawResponse

                        // Nur bereinigen, wenn es nicht bereits ein bekannter, sauber geparster Typ war
                        if (parsedData !is FlintecData.Counts &&
                            parsedData !is FlintecData.Temperature &&
                            parsedData !is FlintecData.Version &&
                            parsedData !is FlintecData.Baudrate &&
                            parsedData !is FlintecData.Filter) {

                            // Versuche, nur führende numerische Zeichen zu extrahieren, bevor nicht-numerische Zeichen kommen
                            val potentialNumericPrefix = stringToClean.takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }
                            if (potentialNumericPrefix.isNotEmpty() && potentialNumericPrefix.any {it.isDigit()}) {
                                Log.d("MultiCellOverview", "Extrahierter potenziell numerischer Teil aus '$stringToClean': '$potentialNumericPrefix'")
                                potentialNumericPrefix
                            } else {
                                Log.d("MultiCellOverview", "Kein numerischer Teil in '$stringToClean' gefunden, verwende rohe Antwort: '$rawResponse'")
                                rawResponse
                            }
                        } else {
                            Log.d("MultiCellOverview", "Bekannter Typ oder bereits String im Else-Zweig: '$stringToClean'")
                            stringToClean
                        }
                    }
                }
                if (result != null) {
                    Log.i("MultiCellOverview", "Zelle $cellNumber $commandName erfolgreich: $result")
                }
                return result
            } else {
                Log.w("MultiCellOverview", "Zelle $cellNumber $commandName: Keine (gültige) Antwort")
                return null
            }
        } catch (e: CancellationException) {
            Log.i("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber abgebrochen (CancellationException).")
            throw e // Wichtig, damit die äußere Coroutine den Abbruch mitbekommt
        }
        catch (e: Exception) {
            Log.e("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber fehlgeschlagen: ${e.message}", e)
            return null
        }
    }


    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            if(!isActive) return@withContext "" // Frühe Prüfung
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var etxFound = false
                val tempReadBuffer = ByteArray(64) // Puffer für read()

                val startTime = System.currentTimeMillis()
                // Lese-Timeout für die gesamte Antwort, nicht pro Byte
                val readOverallTimeout = MultiCellConfig.READ_TIMEOUT

                while (isActive && System.currentTimeMillis() - startTime < readOverallTimeout && !etxFound) {
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(tempReadBuffer)
                        if (bytesRead == -1) { // End of stream erreicht
                            Log.w("MultiCellOverview", "End of stream erreicht beim Lesen der Antwort.")
                            break
                        }

                        for (k in 0 until bytesRead) {
                            if(!isActive) return@withContext "" // Prüfung innerhalb der Schleife

                            val byte = tempReadBuffer[k]
                            if (byte.toInt() == 0x02 /* STX */) {
                                stxFound = true
                                responseBuffer.clear() // Beginne neue Nachricht
                                // STX wird nicht Teil des Inhalts
                                continue
                            }

                            if (stxFound) {
                                if (byte.toInt() == 0x03 /* ETX */) {
                                    etxFound = true // ETX gefunden, Nachricht komplett
                                    break // Innere Schleife beenden
                                }
                                responseBuffer.add(byte)
                            }
                        }
                    } else {
                        // Kurze Pause, um CPU nicht zu überlasten, wenn keine Daten verfügbar sind
                        delay(50)
                    }
                }

                if(!isActive && !etxFound) { // Wenn Coroutine abgebrochen wurde, bevor ETX kam
                    Log.i("MultiCellOverview", "readFlintecResponse abgebrochen, bevor ETX gefunden wurde.")
                    return@withContext ""
                }

                if (stxFound && etxFound) {
                    // Nur wenn STX und ETX gefunden wurden, ist die Nachricht gültig (kann auch leer sein)
                    val responseString = String(responseBuffer.toByteArray(), Charsets.US_ASCII)
                    Log.d("MultiCellOverview", "Gültige Antwort empfangen: '$responseString'")
                    return@withContext responseString
                } else if (stxFound && !etxFound) {
                    Log.w("MultiCellOverview", "Antwort begonnen (STX) aber nicht beendet (ETX) innerhalb Timeout. Buffer: ${responseBuffer.joinToString("") { "%02X".format(it) }}")
                } else if (!stxFound) {
                    Log.w("MultiCellOverview", "Kein STX in der Antwort gefunden innerhalb Timeout.")
                }
                return@withContext "" // Fallback: leere Antwort
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
            // Log.d("AnimCounts", "Keine Änderung für Zelle $cellIndex: $newValueString")
            return
        }

        // Logge den Wert, der hier ankommt, um sicherzustellen, dass er bereits bereinigt ist.
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
