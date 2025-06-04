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
    private lateinit var spinnerActiveCells: Spinner // Spinner für die Zellenauswahl

    // UI Komponenten für gemeinsame Details (Temperatur, Baudrate etc.)
    private lateinit var textTemperatureAll: TextView
    private lateinit var textBaudrateAll: TextView
    private lateinit var textFilterAll: TextView
    private lateinit var textVersionAll: TextView

    // Arrays für die UI-Elemente der einzelnen Zellen (bis zu maxDisplayCells)
    private val cellCountsTextViews = arrayOfNulls<TextView>(MultiCellConfig.maxDisplayCells)
    private val cellStatusIndicators = arrayOfNulls<ImageView>(MultiCellConfig.maxDisplayCells)
    private val cellLayouts = arrayOfNulls<LinearLayout>(MultiCellConfig.maxDisplayCells) // Für Sichtbarkeit

    // Datenhaltung
    private val cellDataArray = Array(MultiCellConfig.maxDisplayCells) { CellDisplayData() }
    private var commonData = CommonDisplayData()
    private var isLiveMode = false
    private var liveUpdateJob: Job? = null

    // Hält die aktuell vom Spinner ausgewählten und zu verarbeitenden Zellen.
    // Wird durch den Spinner und MultiCellConfig.availableCells aktualisiert.
    private var configuredCells: List<Int> = MultiCellConfig.availableCells.toList()

    // Multi-Cell Konfiguration (aus MultiCellConfig Objekt)
    private val MOXA_IP = MultiCellConfig.MOXA_IP
    private val MOXA_PORT = MultiCellConfig.MOXA_PORT
    private val MAX_DISPLAY_CELLS = MultiCellConfig.maxDisplayCells
    private val CELL_QUERY_DELAY_MS = MultiCellConfig.CELL_QUERY_DELAY_MS

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Layout für dieses Fragment inflaten
        return inflater.inflate(R.layout.fragment_multicell_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view) // UI-Elemente initialisieren
        setupSpinner()        // Spinner konfigurieren und Listener setzen
        setupClickListeners() // Klick-Listener für Buttons setzen

        // WICHTIG: Initialisiere die UI basierend auf dem Startwert des Spinners,
        // *bevor* die erste Datenabfrage (refreshAllCells) erfolgt.
        val initialSpinnerPosition = spinnerActiveCells.selectedItemPosition
        val initialCellCount = if (initialSpinnerPosition >= 0) initialSpinnerPosition + 1 else 1
        MultiCellConfig.updateAvailableCells(initialCellCount)
        configuredCells = MultiCellConfig.availableCells.toList() // Lokale Kopie aktualisieren
        updateActiveCellViews(initialCellCount) // Sichtbarkeit der Zellen-Layouts anpassen

        updateUI() // Generelles UI-Update (z.B. leere Felder initial anzeigen)
        refreshAllCells() // Initiale Datenabfrage für die ausgewählten Zellen
    }

    private fun initializeViews(view: View) {
        // Header UI
        textOverallStatus = view.findViewById(R.id.textOverallStatus)
        progressIndicatorOverall = view.findViewById(R.id.progressIndicatorOverall)
        spinnerActiveCells = view.findViewById(R.id.spinnerActiveCells) // Spinner initialisieren

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
            val cellNum = i + 1 // Zellennummer ist 1-basiert
            try {
                val layoutResId = resources.getIdentifier("layoutCell$cellNum", "id", requireContext().packageName)
                val countsResId = resources.getIdentifier("textCountsCell$cellNum", "id", requireContext().packageName)
                val statusResId = resources.getIdentifier("statusIndicatorCell$cellNum", "id", requireContext().packageName)

                if (layoutResId != 0) cellLayouts[i] = view.findViewById(layoutResId)
                if (countsResId != 0) cellCountsTextViews[i] = view.findViewById(countsResId)
                if (statusResId != 0) cellStatusIndicators[i] = view.findViewById(statusResId)

            } catch (e: Exception) {
                Log.w("MultiCellOverview", "UI Element für Zelle $cellNum nicht gefunden: ${e.message}")
            }
        }
        Log.i("MultiCellOverview", "Views initialisiert. Maximale Zellen-Layouts: $MAX_DISPLAY_CELLS")
    }

    private fun setupSpinner() {
        // Erstelle eine Liste von Strings für den Spinner, z.B. "1 Zelle", "2 Zellen", ..., "X Zellen"
        val spinnerItems = (1..MAX_DISPLAY_CELLS).map {
            if (it == 1) "$it Zelle" else "$it Zellen"
        }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item, // Standard Spinner Item Layout
            spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) // Standard Dropdown Layout
        spinnerActiveCells.adapter = adapter

        // OnItemSelectedListener setzen, um auf Benutzerauswahl zu reagieren
        spinnerActiveCells.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCellCount = position + 1 // Position ist 0-basiert, Zellanzahl 1-basiert

                Log.d("MultiCellOverview", "Spinner: Auswahl '$selectedCellCount Zellen' an Position $position.")

                // Nur handeln, wenn sich die Auswahl tatsächlich geändert hat, um unnötige Aktionen zu vermeiden
                // (besonders beim ersten Setzen des Adapters/Listeners)
                if (MultiCellConfig.getAvailableCellCount() == selectedCellCount && configuredCells.size == selectedCellCount) {
                    // Log.d("MultiCellOverview", "Spinner: Auswahl hat sich nicht geändert ($selectedCellCount), keine Aktion.")
                    // return // Frühzeitiger Ausstieg, wenn sich nichts geändert hat.
                    // ACHTUNG: Dieser Check kann dazu führen, dass beim ersten Start nach der Initialisierung
                    // refreshAllCells() nicht korrekt getriggert wird, wenn der Default-Wert des Spinners
                    // zufällig dem Initialwert von MultiCellConfig entspricht.
                    // Besser ist es, die Aktionen immer auszuführen und die Logik in refreshAllCells etc.
                    // idempotent zu gestalten oder den Zustand genauer zu prüfen.
                }


                if (isLiveMode) {
                    stopLiveMode() // Live-Modus stoppen, wenn die Zellanzahl geändert wird
                }

                MultiCellConfig.updateAvailableCells(selectedCellCount) // Globale Konfiguration aktualisieren
                configuredCells = MultiCellConfig.availableCells.toList() // Lokale Kopie der aktiven Zellen aktualisieren

                updateActiveCellViews(selectedCellCount) // Sichtbarkeit der Zellen-UI-Elemente anpassen
                refreshAllCells() // Daten für die neu ausgewählte Zellenanzahl laden
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Wird normalerweise nicht aufgerufen, wenn der Adapter nicht leer ist.
            }
        }

        // Setze die initiale Auswahl des Spinners basierend auf der aktuellen Konfiguration in MultiCellConfig.
        // Dies stellt sicher, dass der Spinner beim Start den korrekten Wert anzeigt.
        val currentConfiguredCellsCount = MultiCellConfig.getAvailableCellCount()
        if (currentConfiguredCellsCount > 0 && currentConfiguredCellsCount <= MAX_DISPLAY_CELLS) {
            spinnerActiveCells.setSelection(currentConfiguredCellsCount - 1, false) // false, um onItemSelected nicht auszulösen
        } else {
            spinnerActiveCells.setSelection(0, false) // Fallback auf die erste Zelle
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

    /**
     * Aktualisiert die Sichtbarkeit der Zellen-Layouts basierend auf der ausgewählten Anzahl.
     * Nicht aktive Zellen werden ausgeblendet und ihre Daten zurückgesetzt.
     */
    private fun updateActiveCellViews(activeCellCount: Int) {
        Log.d("MultiCellOverview", "updateActiveCellViews: Zeige $activeCellCount Zellen-Layouts an.")
        for (i in 0 until MAX_DISPLAY_CELLS) {
            val cellLayout = cellLayouts[i]
            val cellCountsTextView = cellCountsTextViews[i]
            val cellStatusIndicator = cellStatusIndicators[i]

            if (i < activeCellCount) {
                // Diese Zelle soll sichtbar sein
                cellLayout?.visibility = View.VISIBLE
            } else {
                // Diese Zelle soll ausgeblendet werden
                cellLayout?.visibility = View.GONE
                // Setze Daten für ausgeblendete Zellen zurück
                cellCountsTextView?.text = "N/A"
                cellStatusIndicator?.setImageResource(R.drawable.ic_status_pending) // Oder einen "disabled" Status
                // Setze auch die Daten im cellDataArray zurück, falls nötig
                cellDataArray[i] = CellDisplayData(cellNumber = i + 1, counts = "N/A")
            }
        }
    }


    private fun refreshAllCells() {
        if (isLiveMode) {
            Log.d("MultiCellOverview", "refreshAllCells: Im Live-Modus, kein Refresh.")
            return
        }
        if (configuredCells.isEmpty()){
            Log.w("MultiCellOverview", "refreshAllCells: Keine Zellen konfiguriert, kein Refresh.")
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
            showLoading(false)
            // Stelle sicher, dass alle Zellen-UIs als "N/A" oder leer angezeigt werden
            updateActiveCellViews(0) // Blendet alle Zellen aus und setzt sie zurück
            // Gemeinsame Daten auch zurücksetzen
            commonData = CommonDisplayData()
            updateCommonUI()
            return
        }

        showLoading(true)
        updateOverallStatus("Lade Daten für ${configuredCells.size} Zellen...", StatusType.CONNECTING)

        lifecycleScope.launch {
            try {
                var successfulFetches = 0
                val fetchedDataMap = mutableMapOf<Int, CellDisplayData?>()

                // Lade Daten für alle *aktuell konfigurierten* Zellen
                for ((index, cellNumber) in configuredCells.withIndex()) {
                    if (!isActive) {
                        Log.i("MultiCellOverview", "refreshAllCells Coroutine abgebrochen (vor fetch).")
                        return@launch
                    }
                    Log.d("MultiCellOverview", "Starte Datenabfrage für Zelle $cellNumber (Index $index)")
                    updateCellStatus(cellNumber - 1, StatusType.CONNECTING) // Setze Status auf "Laden" für die aktuelle Zelle

                    val result = fetchCellData(cellNumber)
                    fetchedDataMap[cellNumber] = result

                    if (!isActive && index < configuredCells.size -1) { // Wenn abgebrochen und nicht die letzte Zelle
                        Log.i("MultiCellOverview", "refreshAllCells Coroutine abgebrochen (nach fetch, vor Delay).")
                        return@launch
                    }
                    if (index < configuredCells.size - 1) { // Nur verzögern, wenn es nicht die letzte Zelle ist
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
                        Log.i("MultiCellOverview", "Zelle $cellNumber erfolgreich geladen: Counts=${cellResult.counts}")
                    } else {
                        cellDataArray[arrayIndex] = CellDisplayData(cellNumber = cellNumber, counts = "Fehler")
                        updateCellUI(arrayIndex)
                        updateCellStatus(arrayIndex, StatusType.ERROR)
                        Log.w("MultiCellOverview", "Zelle $cellNumber konnte nicht geladen werden.")
                    }
                }

                // Lade gemeinsame Daten, wenn mindestens eine Zelle erfolgreich war
                if (successfulFetches > 0) {
                    loadCommonDataFromFetched(fetchedDataMap)
                    updateOverallStatus("$successfulFetches/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
                } else if (configuredCells.isNotEmpty()) {
                    updateOverallStatus("Keine der ${configuredCells.size} Zellen erreichbar", StatusType.ERROR)
                    commonData = CommonDisplayData() // Gemeinsame Daten zurücksetzen
                    updateCommonUI()
                } else {
                    // Dieser Fall sollte durch den Check am Anfang abgedeckt sein
                    updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
                }

                animateDataUpdate()

            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "refreshAllCells Coroutine wurde abgebrochen (CancellationException).")
                updateOverallStatus("Ladevorgang abgebrochen", StatusType.PENDING)
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden aller Zellen: ${e.message}", e)
                updateOverallStatus("Fehler: ${e.localizedMessage ?: e.message}", StatusType.ERROR)
            } finally {
                if (isActive) { // Nur ausführen, wenn die Coroutine noch aktiv ist
                    showLoading(false)
                }
            }
        }
    }

    private fun startLiveMode() {
        if (isLiveMode) return

        // Filtere Zellen, die beim letzten Refresh erfolgreich waren oder noch nicht probiert wurden
        // (relevant, wenn direkt nach Spinner-Änderung Live gestartet wird)
        val cellsToQueryInLiveMode = configuredCells.filter { cellNum ->
            val data = cellDataArray.getOrNull(cellNum - 1)
            data != null && data.counts != "Fehler" && data.counts != "N/A"
        }

        if (cellsToQueryInLiveMode.isEmpty()) {
            Log.w("MultiCellOverview", "Live-Modus nicht gestartet: Keine Zellen für Live-Abfrage verfügbar (entweder Fehler beim letzten Refresh oder keine konfiguriert).")
            updateOverallStatus("Keine Zellen für Live-Modus", StatusType.ERROR)
            // Sicherstellen, dass Buttons korrekt gesetzt sind
            buttonStartLiveAll.isEnabled = true
            buttonStopLiveAll.isEnabled = false
            spinnerActiveCells.isEnabled = true
            return
        }

        isLiveMode = true
        updateButtonStates() // Deaktiviert auch den Spinner
        updateOverallStatus("Live-Modus aktiv für ${cellsToQueryInLiveMode.size} Zellen", StatusType.LIVE)
        Log.i("MultiCellOverview", "Starte Live-Modus für Zellen: ${cellsToQueryInLiveMode.joinToString(", ")}")

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
                            delay(CELL_QUERY_DELAY_MS) // Kurze Pause auch im Live-Modus
                        }
                    }

                    if (!isLiveMode || !isActive) break // Erneuter Check nach der Schleife

                    var successfulLiveFetches = 0
                    for ((cellNumber, newCounts) in fetchedCounts) {
                        val arrayIndex = cellNumber - 1
                        if (arrayIndex < 0 || arrayIndex >= MAX_DISPLAY_CELLS) continue

                        if (newCounts != null) {
                            cellDataArray[arrayIndex].counts = newCounts
                            cellDataArray[arrayIndex].lastUpdate = System.currentTimeMillis()
                            animateCellCountsUpdate(arrayIndex, newCounts) // UI für einzelne Zelle animieren
                            updateCellStatus(arrayIndex, StatusType.LIVE)
                            successfulLiveFetches++
                        } else {
                            // Zelle hat im Live-Modus nicht geantwortet
                            updateCellStatus(arrayIndex, StatusType.ERROR)
                            Log.w("MultiCellOverview_Live", "Zelle $cellNumber hat im Live-Modus nicht geantwortet.")
                        }
                    }

                    // Gemeinsame Daten (wie Zeitstempel) aktualisieren
                    if (successfulLiveFetches > 0) {
                        commonData.lastUpdate = System.currentTimeMillis()
                        updateCommonUI() // Nur Zeitstempel der gemeinsamen Daten aktualisieren
                    } else {
                        Log.w("MultiCellOverview_Live", "Keine Zelle hat im Live-Zyklus geantwortet.")
                    }


                } catch (e: CancellationException) {
                    Log.i("MultiCellOverview_Live", "Live-Update Job wurde abgebrochen (CancellationException).")
                    break
                } catch (e: Exception) {
                    Log.e("MultiCellOverview_Live", "Allgemeiner Live-Update Fehler: ${e.message}", e)
                    // Optional: Fehler im UI anzeigen oder nach kurzer Pause erneut versuchen
                    delay(2000) // Kurze Pause bei allgemeinem Fehler
                }
                if (isLiveMode && isActive) { // Nur verzögern, wenn Modus und Coroutine noch aktiv
                    delay(MultiCellConfig.LIVE_UPDATE_INTERVAL)
                }
            }
            Log.i("MultiCellOverview", "Live-Modus Schleife beendet. isLiveMode: $isLiveMode, Coroutine isActive: $isActive")
            // Nach Beendigung der Schleife (durch stopLiveMode oder Fehler/Abbruch)
            if (isActive) { // Nur wenn Fragment noch aktiv ist
                updateButtonStates() // Stellt sicher, dass Spinner wieder aktiviert wird etc.
                // Setze Status der Zellen zurück auf "CONNECTED" oder "ERROR" basierend auf dem letzten Refresh
                configuredCells.forEach { cellNum ->
                    val idx = cellNum -1
                    if (idx >= 0 && idx < MAX_DISPLAY_CELLS) {
                        if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A") {
                            updateCellStatus(idx, StatusType.CONNECTED)
                        } else if (cellDataArray[idx].counts != "N/A") { // Nur wenn nicht "N/A" (also konfiguriert aber fehlerhaft)
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
        if (!isLiveMode && liveUpdateJob == null) return // Bereits gestoppt

        Log.i("MultiCellOverview", "stopLiveMode aufgerufen.")
        isLiveMode = false
        liveUpdateJob?.cancel() // Coroutine sicher beenden
        liveUpdateJob = null

        // UI-Status wird jetzt am Ende der Live-Schleife oder hier direkt gesetzt
        updateButtonStates() // Stellt sicher, dass Spinner wieder aktiviert wird etc.
        val responsiveCount = configuredCells.count { cellNum ->
            val data = cellDataArray.getOrNull(cellNum - 1)
            data != null && data.counts != "Fehler" && data.counts != "N/A"
        }

        configuredCells.forEach { cellNum ->
            val idx = cellNum - 1
            if (idx >= 0 && idx < MAX_DISPLAY_CELLS) {
                if (cellDataArray[idx].counts != "Fehler" && cellDataArray[idx].counts != "N/A") {
                    updateCellStatus(idx, StatusType.CONNECTED)
                } else if (cellDataArray[idx].counts != "N/A") { // Nur wenn nicht "N/A" (also konfiguriert aber fehlerhaft)
                    updateCellStatus(idx, StatusType.ERROR)
                }
                // Für nicht konfigurierte Zellen (die in `configuredCells` nicht enthalten sind, aber im `cellDataArray` evtl. noch alte Daten haben)
                // wird der Status durch `updateActiveCellViews` beim nächsten Spinner-Event oder Refresh korrekt gesetzt.
            }
        }

        if (configuredCells.isNotEmpty()) {
            updateOverallStatus("$responsiveCount/${configuredCells.size} Zellen verbunden", StatusType.CONNECTED)
        } else {
            updateOverallStatus("Keine Zellen ausgewählt", StatusType.PENDING)
        }
    }


    /**
     * Lädt alle Daten (Counts und gemeinsame Daten) für eine einzelne Zelle.
     * Wird für den initialen Refresh verwendet.
     */
    private suspend fun fetchCellData(cellNumber: Int): CellDisplayData? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null // Früher Ausstieg, wenn Coroutine nicht mehr aktiv
            Log.d("MultiCellOverview", "fetchCellData für Zelle $cellNumber - Thread: ${Thread.currentThread().name}")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), MultiCellConfig.CONNECTION_TIMEOUT)
                    socket.soTimeout = MultiCellConfig.READ_TIMEOUT

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    val data = CellDisplayData(cellNumber = cellNumber)
                    var commandSuccess = true

                    // Counts abfragen
                    data.counts = fetchSingleCellCommand(
                        "Counts",
                        FlintecRC3DMultiCellCommands.getCommandForCell(cellNumber, FlintecRC3DMultiCellCommands.CommandType.COUNTS),
                        outputStream, inputStream, cellNumber
                    ) ?: run { commandSuccess = false; "Fehler" }
                    if (!isActive) return@withContext null


                    // Gemeinsame Daten nur abfragen, wenn Counts erfolgreich waren UND es die erste Zelle in der `configuredCells` Liste ist
                    // ODER wenn es die erste Zelle ist, die erfolgreich Counts geliefert hat (für Redundanz)
                    // Diese Logik wird jetzt in `loadCommonDataFromFetched` verfeinert.
                    // Hier laden wir die Daten für die aktuelle Zelle, wenn sie als Kandidat für gemeinsame Daten gilt.
                    // Die Entscheidung, welche gemeinsamen Daten letztendlich angezeigt werden, fällt später.
                    if (commandSuccess) { // Nur wenn Counts erfolgreich waren
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
                    return@withContext if (commandSuccess) data else null // Nur erfolgreiche Daten zurückgeben
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "fetchCellData für Zelle $cellNumber abgebrochen (CancellationException).")
                return@withContext null
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Laden von Zelle $cellNumber: ${e.message}", e)
                return@withContext null // Fehlerfall
            }
        }
    }

    /**
     * Lädt die gemeinsamen Daten (Temperatur, Baudrate etc.) von der ersten erfolgreich abgerufenen Zelle.
     */
    private suspend fun loadCommonDataFromFetched(fetchedDataMap: Map<Int, CellDisplayData?>) {
        // Finde die erste Zelle in der `configuredCells` Liste, für die Daten erfolgreich abgerufen wurden.
        val firstSuccessfulCellNumber = configuredCells.firstOrNull { fetchedDataMap[it] != null }

        if (firstSuccessfulCellNumber != null) {
            val successfulCellData = fetchedDataMap[firstSuccessfulCellNumber]!! // Ist nicht null wegen der Bedingung oben
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
            commonData = CommonDisplayData() // Zurücksetzen, wenn keine Zelle erfolgreich war
            withContext(Dispatchers.Main) {
                updateCommonUI()
            }
        }
    }


    /**
     * Fragt nur die Counts für eine einzelne Zelle ab.
     * Wird für den Live-Modus verwendet.
     */
    private suspend fun fetchSingleCellCounts(cellNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext null
            Log.d("MultiCellOverview_Live", "fetchSingleCellCounts für Zelle $cellNumber - Thread: ${Thread.currentThread().name}")
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 3000) // Kürzerer Timeout für Live
                    socket.soTimeout = 2000 // Kürzerer Read-Timeout

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
                return@withContext null // Fehlerfall
            }
        }
    }

    /**
     * Sendet einen einzelnen Befehl an eine Zelle und verarbeitet die Antwort.
     */
    private suspend fun fetchSingleCellCommand(
        commandName: String,
        commandBytes: ByteArray,
        outputStream: OutputStream,
        inputStream: InputStream,
        cellNumber: Int // Für Logging
    ): String? {
        // Prüfen, ob die Coroutine noch aktiv ist, bevor Netzwerkoperationen gestartet werden
        if (!coroutineContext.isActive) {
            Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv vor Sendung).")
            return null
        }
        return try {
            Log.d("MultiCellOverview", "Sende an Zelle $cellNumber ($commandName): ${commandBytes.joinToString(" ") { "%02X".format(it) }}")
            outputStream.write(commandBytes)
            outputStream.flush()

            // Erneut prüfen, ob Coroutine aktiv ist, bevor auf Antwort gewartet wird
            if (!coroutineContext.isActive) {
                Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv nach Sendung, vor Antwort).")
                return null
            }

            val rawResponse = readFlintecResponse(inputStream) // Diese Funktion muss auch isActive prüfen

            // Erneut prüfen nach der Antwort
            if (!coroutineContext.isActive && rawResponse.isEmpty()) { // Wenn abgebrochen und keine Antwort erhalten
                Log.d("MultiCellOverview", "fetchSingleCellCommand für Zelle $cellNumber ($commandName) abgebrochen (Coroutine nicht aktiv nach Antwort).")
                return null
            }

            if (rawResponse.isNotEmpty()) {
                Log.d("MultiCellOverview", "Zelle $cellNumber Antwort ($commandName): '$rawResponse'")
                val parsedData = FlintecRC3DMultiCellCommands.parseMultiCellResponse(rawResponse, cellNumber)
                val valueToReturn = when (parsedData) {
                    is FlintecData.Counts -> parsedData.value
                    is FlintecData.Temperature -> parsedData.value
                    is FlintecData.Version -> parsedData.value
                    is FlintecData.Baudrate -> parsedData.value // "bps" wird jetzt in MultiCellConfig nicht mehr angehängt, sondern hier oder im UI
                    is FlintecData.Filter -> parsedData.value
                    is FlintecData.SerialNumber -> parsedData.value // Falls benötigt
                    is FlintecData.Unknown -> {
                        Log.w("MultiCellOverview", "Unbekannte Antwort von Zelle $cellNumber ($commandName): '$rawResponse'")
                        rawResponse // Fallback auf Rohdaten
                    }
                    null -> {
                        Log.w("MultiCellOverview", "Parsing-Fehler oder keine verwertbaren Daten von Zelle $cellNumber ($commandName). Rohantwort: '$rawResponse'")
                        rawResponse // Fallback auf Rohdaten bei Parsing-Problem
                    }
                }
                Log.i("MultiCellOverview", "Zelle $cellNumber $commandName erfolgreich verarbeitet: '$valueToReturn'")
                valueToReturn
            } else {
                Log.w("MultiCellOverview", "Zelle $cellNumber $commandName: Keine (gültige) Antwort erhalten.")
                null // Keine Antwort
            }
        } catch (e: CancellationException) {
            Log.i("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber wurde abgebrochen (CancellationException).")
            throw e // Erneut werfen, damit die aufrufende Coroutine es behandeln kann
        } catch (e: Exception) {
            Log.e("MultiCellOverview", "Befehl $commandName für Zelle $cellNumber fehlgeschlagen: ${e.message}", e)
            null // Fehlerfall
        }
    }

    /**
     * Liest die Antwort vom Flintec-Gerät.
     * Beachtet STX/ETX und Timeouts.
     */
    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            if (!isActive) return@withContext "" // Früher Ausstieg
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var etxFound = false
                val tempReadBuffer = ByteArray(128) // Puffergröße für read()

                val startTime = System.currentTimeMillis()
                val readOverallTimeout = MultiCellConfig.READ_TIMEOUT.toLong()

                while (isActive && System.currentTimeMillis() - startTime < readOverallTimeout && !etxFound) {
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(tempReadBuffer)
                        if (bytesRead == -1) { // End of stream
                            Log.w("MultiCellOverview", "End of stream erreicht beim Lesen der Antwort.")
                            break
                        }

                        for (k in 0 until bytesRead) {
                            if (!isActive) return@withContext "" // Innerhalb der Schleife prüfen

                            val byte = tempReadBuffer[k]
                            if (!stxFound && byte.toInt() == 0x02 /* STX */) {
                                stxFound = true
                                responseBuffer.clear() // Beginne neu bei STX
                                // STX selbst wird nicht in den responseBuffer aufgenommen
                                continue
                            }

                            if (stxFound) {
                                if (byte.toInt() == 0x03 /* ETX */) {
                                    etxFound = true
                                    break // ETX gefunden, Schleife verlassen
                                }
                                responseBuffer.add(byte) // Daten zwischen STX und ETX sammeln
                            }
                        }
                    } else {
                        delay(50) // Kurze Pause, wenn keine Daten verfügbar sind, um CPU zu schonen
                    }
                }

                if (!isActive && !etxFound) { // Wenn abgebrochen und ETX nicht gefunden
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
                    return@withContext "" // Leere Antwort bei Timeout oder unvollständiger Nachricht
                }
            } catch (e: CancellationException) {
                Log.i("MultiCellOverview", "readFlintecResponse wurde abgebrochen (CancellationException).")
                return@withContext ""
            } catch (e: Exception) {
                Log.e("MultiCellOverview", "Fehler beim Lesen der Flintec Antwort: ${e.message}", e)
                return@withContext "" // Leere Antwort im Fehlerfall
            }
        }
    }

    // --- UI Update Funktionen ---
    private fun updateUI() {
        updateCommonUI()
        for (i in 0 until MAX_DISPLAY_CELLS) {
            updateCellUI(i) // Aktualisiert die Counts-Anzeige für jede Zelle
            // Der Status (Icon) wird separat durch updateCellStatus gesetzt
        }
    }

    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex < 0 || cellIndex >= MAX_DISPLAY_CELLS) return
        val textView = cellCountsTextViews[cellIndex] ?: return
        val cellData = cellDataArray[cellIndex]

        // Zeige "N/A" an, wenn die Zelle nicht Teil der `configuredCells` ist
        // oder wenn die Daten initial noch nicht geladen wurden.
        val cellNumberForConfigCheck = cellIndex + 1
        if (!configuredCells.contains(cellNumberForConfigCheck) && cellLayouts[cellIndex]?.visibility == View.GONE) {
            textView.text = "N/A" // Wird durch updateActiveCellViews gesetzt
        } else {
            textView.text = cellData.counts
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
            StatusType.CONNECTING -> R.drawable.ic_status_pending // Gelb für Laden
            StatusType.LIVE -> R.drawable.ic_status_live
            StatusType.ERROR -> R.drawable.ic_status_error
            StatusType.PENDING -> R.drawable.ic_status_pending // Grau/Neutral für nicht aktiv/ausstehend
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
            StatusType.PENDING -> R.color.status_pending_color // Gleiche Farbe wie Connecting oder eine neutrale
        }
        try {
            textOverallStatus.setTextColor(ContextCompat.getColor(requireContext(), colorId))
        } catch (e: IllegalStateException) {
            Log.e("MultiCellOverview", "Fehler beim Setzen der Textfarbe für OverallStatus (Fragment möglicherweise nicht mehr attached): ${e.message}")
        }
    }

    private fun animateDataUpdate() {
        // Einfache Alpha-Animation für das gesamte View des Fragments
        view?.animate()?.alpha(0.8f)?.setDuration(150)?.withEndAction {
            view?.animate()?.alpha(1.0f)?.setDuration(150)?.start()
        }?.start()
    }

    private fun animateCellCountsUpdate(cellIndex: Int, newValueString: String) {
        val textView = cellCountsTextViews[cellIndex] ?: return
        // Nur animieren, wenn sich der Wert tatsächlich ändert
        if (textView.text.toString() != newValueString) {
            // Fade-Out Animation
            textView.animate().alpha(0.0f).setDuration(150).withEndAction {
                textView.text = newValueString
                // Fade-In Animation
                textView.animate().alpha(1.0f).setDuration(150).start()
            }.start()
        }
    }

    private fun showLoading(show: Boolean) {
        progressIndicatorOverall.visibility = if (show) View.VISIBLE else View.GONE
        buttonRefreshAll.isEnabled = !show
        spinnerActiveCells.isEnabled = !show // Spinner (de)aktivieren während des Ladens

        if (show) {
            buttonStartLiveAll.isEnabled = false
            buttonStopLiveAll.isEnabled = false
        } else {
            // updateButtonStates() wird am Ende von refreshAllCells oder start/stopLiveMode aufgerufen
            // um den korrekten Zustand basierend auf isLiveMode wiederherzustellen.
            // Hier stellen wir sicher, dass der Spinner wieder aktiviert wird, wenn das Laden beendet ist
            // und wir nicht im Live-Modus sind.
            if (!isLiveMode) {
                spinnerActiveCells.isEnabled = true
            }
            updateButtonStates() // Stellt sicher, dass Buttons korrekt (re)aktiviert werden
        }
    }

    private fun updateButtonStates() {
        val isLoading = progressIndicatorOverall.visibility == View.VISIBLE
        buttonStartLiveAll.isEnabled = !isLiveMode && !isLoading
        buttonStopLiveAll.isEnabled = isLiveMode && !isLoading
        buttonRefreshAll.isEnabled = !isLiveMode && !isLoading
        spinnerActiveCells.isEnabled = !isLiveMode && !isLoading // Spinner (de)aktivieren
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLiveMode() // Stellt sicher, dass Coroutines und Jobs beendet werden
        liveUpdateJob?.cancel() // Explizit canceln
        liveUpdateJob = null
        // Binding-Variablen müssen hier nicht auf null gesetzt werden, da wir kein ViewBinding verwenden,
        // sondern findViewById. Die Views werden mit dem Fragment zerstört.
        Log.d("MultiCellOverview", "onDestroyView aufgerufen.")
    }

    // Datenklassen für die Anzeige
    data class CellDisplayData(
        var cellNumber: Int = 0,
        var counts: String = "0",
        var temperature: String = "0°C", // Wird nur für die erste erfolgreiche Zelle befüllt
        var baudrate: String = "Unbekannt", // dto.
        var filter: String = "Unbekannt", // dto.
        var version: String = "Unbekannt", // dto.
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
