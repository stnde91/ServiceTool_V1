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

    // Daten für alle Zellen
    private val cellDataArray = Array(8) { CellDisplayData() }
    private var commonData = CommonDisplayData()
    private var isLiveMode = false
    private var liveUpdateJob: Job? = null

    // Moxa-Konfiguration
    private val MOXA_IP = "192.168.50.3"
    private val MOXA_PORT = 4001

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

        // Initiale Datenabfrage
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

        // Initialize cell arrays
        for (i in 0 until 8) {
            val cellNum = i + 1
            cellCountsTextViews[i] = view.findViewById(resources.getIdentifier("textCountsCell$cellNum", "id", requireContext().packageName))
            cellStatusIndicators[i] = view.findViewById(resources.getIdentifier("statusIndicatorCell$cellNum", "id", requireContext().packageName))
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

    private fun refreshAllCells() {
        if (isLiveMode) return

        showLoading(true)
        updateOverallStatus("Verbinde mit allen Zellen...", StatusType.CONNECTING)

        lifecycleScope.launch {
            try {
                var successCount = 0
                var totalCells = 8

                // Simuliere das Laden von mehreren Zellen
                // In der Realität würdest du hier mehrere RC3D-Geräte abfragen
                for (i in 0 until totalCells) {
                    try {
                        val cellData = fetchCellData(i + 1)
                        if (cellData != null) {
                            cellDataArray[i] = cellData
                            successCount++
                            updateCellUI(i)
                        } else {
                            updateCellStatus(i, StatusType.ERROR)
                        }
                    } catch (e: Exception) {
                        Log.e("MultiCell", "Fehler bei Zelle ${i + 1}: ${e.message}")
                        updateCellStatus(i, StatusType.ERROR)
                    }

                    // Kurze Pause zwischen den Zellen
                    delay(200)
                }

                // Gemeinsame Daten laden (z.B. von der ersten funktionierenden Zelle)
                if (successCount > 0) {
                    loadCommonData()
                    updateOverallStatus("$successCount/$totalCells Zellen verbunden", StatusType.CONNECTED)
                } else {
                    updateOverallStatus("Keine Zellen erreichbar", StatusType.ERROR)
                }

                animateDataUpdate()

            } catch (e: Exception) {
                Log.e("MultiCell", "Fehler beim Laden: ${e.message}")
                updateOverallStatus("Fehler: ${e.message}", StatusType.ERROR)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun startLiveMode() {
        if (isLiveMode) return

        isLiveMode = true
        updateButtonStates()
        updateOverallStatus("Live-Modus aktiv für alle Zellen", StatusType.LIVE)

        liveUpdateJob = lifecycleScope.launch {
            while (isLiveMode) {
                try {
                    // Aktualisiere alle Zellen-Counts
                    for (i in 0 until 8) {
                        try {
                            val newCounts = fetchSingleCellCounts(i + 1)
                            if (newCounts != null) {
                                cellDataArray[i].counts = newCounts
                                updateCellUI(i)
                                updateCellStatus(i, StatusType.LIVE)
                            }
                        } catch (e: Exception) {
                            updateCellStatus(i, StatusType.ERROR)
                        }
                    }

                    commonData.lastUpdate = System.currentTimeMillis()
                    updateCommonUI()

                } catch (e: Exception) {
                    Log.e("MultiCell", "Live-Update Fehler: ${e.message}")
                }

                delay(1000) // 1-Sekunden-Updates
            }
        }
    }

    private fun stopLiveMode() {
        isLiveMode = false
        liveUpdateJob?.cancel()
        updateButtonStates()
        updateOverallStatus("Live-Modus gestoppt", StatusType.CONNECTED)

        // Setze alle Live-Status zurück auf Connected
        for (i in 0 until 8) {
            if (cellDataArray[i].counts.isNotEmpty()) {
                updateCellStatus(i, StatusType.CONNECTED)
            }
        }
    }

    private suspend fun fetchCellData(cellNumber: Int): CellDisplayData? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 5000)
                    socket.soTimeout = 3000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    val data = CellDisplayData()

                    // Hier würdest du die spezifischen Befehle für jede Zelle senden
                    // Momentan simulieren wir das mit der ersten Zelle
                    data.cellNumber = cellNumber
                    data.counts = fetchSingleCommand("Counts", FlintecRC3DCommands.getCounts(), outputStream, inputStream) ?: "0"
                    data.lastUpdate = System.currentTimeMillis()

                    return@withContext data
                }
            } catch (e: Exception) {
                Log.e("MultiCell", "Fehler beim Laden von Zelle $cellNumber: ${e.message}")
                return@withContext null
            }
        }
    }

    private suspend fun fetchSingleCellCounts(cellNumber: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 3000)
                    socket.soTimeout = 2000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    return@withContext fetchSingleCommand("Counts", FlintecRC3DCommands.getCounts(), outputStream, inputStream)
                }
            } catch (e: Exception) {
                Log.e("MultiCell", "Fehler bei Counts von Zelle $cellNumber: ${e.message}")
                return@withContext null
            }
        }
    }

    private suspend fun loadCommonData() {
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 5000)
                    socket.soTimeout = 3000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    // Lade gemeinsame Daten (Temperatur, Baudrate, etc.)
                    commonData.temperature = fetchSingleCommand("Temperatur", FlintecRC3DCommands.getTemperature(), outputStream, inputStream) ?: "Unbekannt"
                    commonData.baudrate = fetchSingleCommand("Baudrate", FlintecRC3DCommands.getBaudrate(), outputStream, inputStream) ?: "Unbekannt"
                    commonData.filter = fetchSingleCommand("Filter", FlintecRC3DCommands.getFilter(), outputStream, inputStream) ?: "Unbekannt"
                    commonData.version = fetchSingleCommand("Version", FlintecRC3DCommands.getVersion(), outputStream, inputStream) ?: "Unbekannt"
                    commonData.lastUpdate = System.currentTimeMillis()

                    withContext(Dispatchers.Main) {
                        updateCommonUI()
                    }
                }
            } catch (e: Exception) {
                Log.e("MultiCell", "Fehler beim Laden gemeinsamer Daten: ${e.message}")
            }
        }
    }

    private suspend fun fetchSingleCommand(
        commandName: String,
        commandBytes: ByteArray,
        outputStream: OutputStream,
        inputStream: InputStream
    ): String? {
        return try {
            outputStream.write(commandBytes)
            outputStream.flush()

            val rawResponse = readFlintecResponse(inputStream)
            if (rawResponse.isNotEmpty()) {
                val parsedData = FlintecRC3DCommands.parseResponse(rawResponse)
                when (parsedData) {
                    is FlintecData.Counts -> parsedData.value
                    is FlintecData.Temperature -> parsedData.value
                    is FlintecData.Version -> parsedData.value
                    is FlintecData.Baudrate -> "${parsedData.value} bps"
                    is FlintecData.Filter -> parsedData.value
                    else -> rawResponse
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MultiCell", "Befehl $commandName fehlgeschlagen: ${e.message}")
            null
        }
    }

    private suspend fun readFlintecResponse(inputStream: InputStream): String {
        return withContext(Dispatchers.IO) {
            try {
                val responseBuffer = mutableListOf<Byte>()
                var stxFound = false
                var timeoutCounter = 0
                val maxTimeout = 15

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
                Log.e("MultiCell", "Fehler beim Lesen: ${e.message}")
                return@withContext ""
            }
        }
    }

    private fun updateUI() {
        updateCommonUI()
        for (i in 0 until 8) {
            updateCellUI(i)
        }
    }

    private fun updateCellUI(cellIndex: Int) {
        if (cellIndex < 0 || cellIndex >= 8) return

        val cellData = cellDataArray[cellIndex]

        cellCountsTextViews[cellIndex]?.text = cellData.counts

        // Animiere Counts-Updates
        if (isLiveMode && cellData.counts.isNotEmpty()) {
            animateCellCountsUpdate(cellIndex, cellData.counts)
        }
    }

    private fun updateCommonUI() {
        textTemperatureAll.text = commonData.temperature
        textBaudrateAll.text = commonData.baudrate
        textFilterAll.text = "Filter: ${commonData.filter}"
        textVersionAll.text = commonData.version

        if (commonData.lastUpdate > 0) {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            textLastUpdateAll.text = "Letzte Aktualisierung: ${formatter.format(Date(commonData.lastUpdate))}"
        }
    }

    private fun updateCellStatus(cellIndex: Int, type: StatusType) {
        if (cellIndex < 0 || cellIndex >= 8) return

        val color = when (type) {
            StatusType.CONNECTED -> ContextCompat.getColor(requireContext(), R.color.status_success_color)
            StatusType.CONNECTING -> ContextCompat.getColor(requireContext(), R.color.status_pending_color)
            StatusType.LIVE -> ContextCompat.getColor(requireContext(), R.color.status_live_color)
            StatusType.ERROR -> ContextCompat.getColor(requireContext(), R.color.status_error_color)
        }

        val drawable = when (type) {
            StatusType.CONNECTED -> R.drawable.ic_status_success
            StatusType.CONNECTING -> R.drawable.ic_status_pending
            StatusType.LIVE -> R.drawable.ic_status_live
            StatusType.ERROR -> R.drawable.ic_status_error
        }

        cellStatusIndicators[cellIndex]?.setImageResource(drawable)
    }

    private fun updateOverallStatus(status: String, type: StatusType) {
        textOverallStatus.text = status

        val color = when (type) {
            StatusType.CONNECTED -> ContextCompat.getColor(requireContext(), R.color.status_success_color)
            StatusType.CONNECTING -> ContextCompat.getColor(requireContext(), R.color.status_pending_color)
            StatusType.LIVE -> ContextCompat.getColor(requireContext(), R.color.status_live_color)
            StatusType.ERROR -> ContextCompat.getColor(requireContext(), R.color.status_error_color)
        }

        textOverallStatus.setTextColor(color)
    }

    private fun animateDataUpdate() {
        // Kurze Fade-Animation für die ganze Ansicht
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

    private fun animateCellCountsUpdate(cellIndex: Int, newValue: String) {
        val textView = cellCountsTextViews[cellIndex] ?: return
        val currentValue = textView.text.toString().toIntOrNull() ?: 0
        val newIntValue = newValue.toIntOrNull() ?: 0

        if (currentValue != newIntValue) {
            ValueAnimator.ofInt(currentValue, newIntValue).apply {
                duration = 300
                addUpdateListener { animator ->
                    textView.text = animator.animatedValue.toString()
                }
                start()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressIndicatorOverall.visibility = if (show) View.VISIBLE else View.GONE
        buttonRefreshAll.isEnabled = !show
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

    // Datenklassen
    data class CellDisplayData(
        var cellNumber: Int = 0,
        var counts: String = "0",
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
        CONNECTED, CONNECTING, LIVE, ERROR
    }
}