package com.example.servicetool

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

class BeautifulFlintecFragment : Fragment() {

    // UI Komponenten
    private lateinit var cardOverview: MaterialCardView
    private lateinit var cardDetails: MaterialCardView
    private lateinit var cardControls: MaterialCardView

    private lateinit var textSerialNumber: TextView
    private lateinit var textCounts: TextView
    private lateinit var textTemperature: TextView
    private lateinit var textBaudrate: TextView
    private lateinit var textFilter: TextView
    private lateinit var textVersion: TextView
    private lateinit var textLastUpdate: TextView
    private lateinit var textConnectionStatus: TextView

    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var buttonRefresh: Button
    private lateinit var buttonStartLive: Button
    private lateinit var buttonStopLive: Button

    // Flintec-Daten
    private var currentData = FlintecDisplayData()
    private var isLiveMode = false
    private var liveUpdateJob: Job? = null

    // Moxa-Konfiguration
    private val MOXA_IP = "192.168.50.3"
    private val MOXA_PORT = 4001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_beautiful_flintec, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
        updateUI()

        // Initiale Datenabfrage
        refreshData()
    }

    private fun initializeViews(view: View) {
        // Cards
        cardOverview = view.findViewById(R.id.cardOverview)
        cardDetails = view.findViewById(R.id.cardDetails)
        cardControls = view.findViewById(R.id.cardControls)

        // Haupt-Datenfelder
        textSerialNumber = view.findViewById(R.id.textSerialNumber)
        textCounts = view.findViewById(R.id.textCounts)
        textTemperature = view.findViewById(R.id.textTemperature)
        textBaudrate = view.findViewById(R.id.textBaudrate)
        textFilter = view.findViewById(R.id.textFilter)
        textVersion = view.findViewById(R.id.textVersion)
        textLastUpdate = view.findViewById(R.id.textLastUpdate)
        textConnectionStatus = view.findViewById(R.id.textConnectionStatus)

        // Buttons und Progress
        progressIndicator = view.findViewById(R.id.progressIndicator)
        buttonRefresh = view.findViewById(R.id.buttonRefresh)
        buttonStartLive = view.findViewById(R.id.buttonStartLive)
        buttonStopLive = view.findViewById(R.id.buttonStopLive)
    }

    private fun setupClickListeners() {
        buttonRefresh.setOnClickListener {
            refreshData()
        }

        buttonStartLive.setOnClickListener {
            startLiveMode()
        }

        buttonStopLive.setOnClickListener {
            stopLiveMode()
        }
    }

    private fun refreshData() {
        if (isLiveMode) return

        showLoading(true)
        updateConnectionStatus("Verbinde...", StatusType.CONNECTING)

        lifecycleScope.launch {
            try {
                val newData = fetchAllFlintecData()
                if (newData != null) {
                    currentData = newData
                    updateConnectionStatus("Verbunden", StatusType.CONNECTED)
                    animateDataUpdate()
                } else {
                    updateConnectionStatus("Keine Antwort", StatusType.ERROR)
                }
            } catch (e: Exception) {
                Log.e("FlintecUI", "Fehler beim Laden: ${e.message}")
                updateConnectionStatus("Fehler: ${e.message}", StatusType.ERROR)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun startLiveMode() {
        if (isLiveMode) return

        isLiveMode = true
        updateButtonStates()
        updateConnectionStatus("Live-Modus aktiv", StatusType.LIVE)

        liveUpdateJob = lifecycleScope.launch {
            while (isLiveMode) {
                try {
                    val counts = fetchSingleCommand("Counts", FlintecRC3DCommands.getCounts())
                    val temperature = fetchSingleCommand("Temperatur", FlintecRC3DCommands.getTemperature())

                    if (counts != null) {
                        currentData.counts = counts
                        animateCountsUpdate()
                    }

                    if (temperature != null) {
                        currentData.temperature = temperature
                    }

                    currentData.lastUpdate = System.currentTimeMillis()
                    updateUI()

                } catch (e: Exception) {
                    Log.e("FlintecUI", "Live-Update Fehler: ${e.message}")
                }

                delay(1000)
            }
        }
    }

    private fun stopLiveMode() {
        isLiveMode = false
        liveUpdateJob?.cancel()
        updateButtonStates()
        updateConnectionStatus("Live-Modus gestoppt", StatusType.CONNECTED)
    }

    private suspend fun fetchAllFlintecData(): FlintecDisplayData? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 5000)
                    socket.soTimeout = 3000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    val data = FlintecDisplayData()

                    data.serialNumber = fetchSingleCommand("Seriennummer", FlintecRC3DCommands.getSerialNumber(), outputStream, inputStream) ?: "Unbekannt"
                    data.counts = fetchSingleCommand("Counts", FlintecRC3DCommands.getCounts(), outputStream, inputStream) ?: "0"
                    data.temperature = fetchSingleCommand("Temperatur", FlintecRC3DCommands.getTemperature(), outputStream, inputStream) ?: "0°C"
                    data.baudrate = fetchSingleCommand("Baudrate", FlintecRC3DCommands.getBaudrate(), outputStream, inputStream) ?: "Unbekannt"
                    data.filter = fetchSingleCommand("Filter", FlintecRC3DCommands.getFilter(), outputStream, inputStream) ?: "Unbekannt"
                    data.version = fetchSingleCommand("Version", FlintecRC3DCommands.getVersion(), outputStream, inputStream) ?: "Unbekannt"
                    data.lastUpdate = System.currentTimeMillis()

                    return@withContext data
                }
            } catch (e: Exception) {
                Log.e("FlintecUI", "Fehler beim Laden aller Daten: ${e.message}")
                return@withContext null
            }
        }
    }

    private suspend fun fetchSingleCommand(commandName: String, commandBytes: ByteArray): String? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(MOXA_IP, MOXA_PORT), 3000)
                    socket.soTimeout = 2000

                    val outputStream = socket.getOutputStream()
                    val inputStream = socket.getInputStream()

                    return@withContext fetchSingleCommand(commandName, commandBytes, outputStream, inputStream)
                }
            } catch (e: Exception) {
                Log.e("FlintecUI", "Fehler bei $commandName: ${e.message}")
                return@withContext null
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
                    is FlintecData.SerialNumber -> parsedData.value
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
            Log.e("FlintecUI", "Befehl $commandName fehlgeschlagen: ${e.message}")
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
                Log.e("FlintecUI", "Fehler beim Lesen: ${e.message}")
                return@withContext ""
            }
        }
    }

    private fun updateUI() {
        textSerialNumber.text = currentData.serialNumber
        textCounts.text = currentData.counts
        textTemperature.text = currentData.temperature
        textBaudrate.text = currentData.baudrate
        textFilter.text = "Filter: ${currentData.filter}"
        textVersion.text = currentData.version

        if (currentData.lastUpdate > 0) {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            textLastUpdate.text = "Letzte Aktualisierung: ${formatter.format(Date(currentData.lastUpdate))}"
        }
    }

    private fun animateDataUpdate() {
        cardOverview.animate()
            .alpha(0.7f)
            .setDuration(150)
            .withEndAction {
                updateUI()
                cardOverview.animate()
                    .alpha(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun animateCountsUpdate() {
        val currentValue = textCounts.text.toString().toIntOrNull() ?: 0
        val newValue = currentData.counts.toIntOrNull() ?: 0

        if (currentValue != newValue) {
            ValueAnimator.ofInt(currentValue, newValue).apply {
                duration = 300
                addUpdateListener { animator ->
                    textCounts.text = animator.animatedValue.toString()
                }
                start()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
        buttonRefresh.isEnabled = !show
    }

    private fun updateButtonStates() {
        buttonStartLive.isEnabled = !isLiveMode
        buttonStopLive.isEnabled = isLiveMode
        buttonRefresh.isEnabled = !isLiveMode
    }

    private fun updateConnectionStatus(status: String, type: StatusType) {
        textConnectionStatus.text = status

        val color = when (type) {
            StatusType.CONNECTED -> ContextCompat.getColor(requireContext(), R.color.status_success_color)
            StatusType.CONNECTING -> ContextCompat.getColor(requireContext(), R.color.status_pending_color)
            StatusType.LIVE -> ContextCompat.getColor(requireContext(), R.color.status_live_color)
            StatusType.ERROR -> ContextCompat.getColor(requireContext(), R.color.status_error_color)
        }

        textConnectionStatus.setTextColor(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLiveMode()
    }

    // Datenklassen
    data class FlintecDisplayData(
        var serialNumber: String = "Unbekannt",
        var counts: String = "0",
        var temperature: String = "0°C",
        var baudrate: String = "Unbekannt",
        var filter: String = "Unbekannt",
        var version: String = "Unbekannt",
        var lastUpdate: Long = 0L
    )

    enum class StatusType {
        CONNECTED, CONNECTING, LIVE, ERROR
    }
}