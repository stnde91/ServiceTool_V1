package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager

    // Connection Settings
    private lateinit var editTextIpAddress: TextInputEditText
    private lateinit var editTextPort: TextInputEditText
    private lateinit var buttonTestConnection: Button
    private lateinit var textViewConnectionStatus: TextView

    // Cell Configuration
    private lateinit var spinnerActiveCells: Spinner
    private lateinit var switchAutoRefresh: Switch
    private lateinit var seekBarRefreshInterval: SeekBar
    private lateinit var textViewRefreshInterval: TextView

    // Advanced Settings
    private lateinit var editTextConnectionTimeout: TextInputEditText
    private lateinit var editTextReadTimeout: TextInputEditText
    private lateinit var spinnerLogLevel: Spinner

    // System Actions
    private lateinit var buttonClearLogs: Button
    private lateinit var buttonExportSettings: Button
    private lateinit var buttonResetToDefaults: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_enhanced_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeServices()
        initializeViews(view)
        setupListeners()
        loadCurrentSettings()
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        // Connection Settings
        editTextIpAddress = view.findViewById(R.id.editTextIpAddress)
        editTextPort = view.findViewById(R.id.editTextPort)
        buttonTestConnection = view.findViewById(R.id.buttonTestConnection)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)

        // Cell Configuration
        spinnerActiveCells = view.findViewById(R.id.spinnerActiveCells)
        switchAutoRefresh = view.findViewById(R.id.switchAutoRefresh)
        seekBarRefreshInterval = view.findViewById(R.id.seekBarRefreshInterval)
        textViewRefreshInterval = view.findViewById(R.id.textViewRefreshInterval)

        // Advanced Settings
        editTextConnectionTimeout = view.findViewById(R.id.editTextConnectionTimeout)
        editTextReadTimeout = view.findViewById(R.id.editTextReadTimeout)
        spinnerLogLevel = view.findViewById(R.id.spinnerLogLevel)

        // System Actions
        buttonClearLogs = view.findViewById(R.id.buttonClearLogs)
        buttonExportSettings = view.findViewById(R.id.buttonExportSettings)
        buttonResetToDefaults = view.findViewById(R.id.buttonResetToDefaults)

        setupSpinners()
        setupSeekBar()
    }

    private fun setupSpinners() {
        // Active Cells Spinner
        val cellCountOptions = (1..MultiCellConfig.maxDisplayCells).map {
            if (it == 1) "$it Zelle" else "$it Zellen"
        }
        val cellAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cellCountOptions)
        cellAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActiveCells.adapter = cellAdapter

        // Log Level Spinner
        val logLevels = SettingsManager.LogLevel.values().map { it.name }
        val logAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, logLevels)
        logAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLogLevel.adapter = logAdapter
    }

    private fun setupSeekBar() {
        seekBarRefreshInterval.max = 8 // 0.5s to 5s
        seekBarRefreshInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val intervalMs = getIntervalFromProgress(progress)
                updateRefreshIntervalText(intervalMs)
                if (fromUser) {
                    settingsManager.setRefreshInterval(intervalMs)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupListeners() {
        // IP Address change
        editTextIpAddress.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setMoxaIpAddress(s.toString())
                updateConnectionStatus("Nicht getestet")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Port change
        editTextPort.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val port = s.toString().toIntOrNull()
                if (port != null && port in 1..65535) {
                    settingsManager.setMoxaPort(port)
                    updateConnectionStatus("Nicht getestet")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Test Connection
        buttonTestConnection.setOnClickListener {
            testConnection()
        }

        // Active Cells
        spinnerActiveCells.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cellCount = position + 1
                settingsManager.setActiveCellCount(cellCount)
                loggingManager.logInfo("Settings", "Aktive Zellen geändert: $cellCount")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Auto Refresh
        switchAutoRefresh.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAutoRefreshEnabled(isChecked)
            seekBarRefreshInterval.isEnabled = isChecked
            loggingManager.logInfo("Settings", "Auto-Refresh: ${if (isChecked) "aktiviert" else "deaktiviert"}")
        }

        // Timeout settings
        editTextConnectionTimeout.addTextChangedListener(createTimeoutWatcher { timeout ->
            settingsManager.setConnectionTimeout(timeout)
        })

        editTextReadTimeout.addTextChangedListener(createTimeoutWatcher { timeout ->
            settingsManager.setReadTimeout(timeout)
        })

        // Log Level
        spinnerLogLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val logLevel = SettingsManager.LogLevel.values()[position]
                settingsManager.setLogLevel(logLevel)
                loggingManager.logInfo("Settings", "Log-Level geändert: ${logLevel.name}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // System Actions
        buttonClearLogs.setOnClickListener {
            loggingManager.clearLogs()
            updateConnectionStatus("Logs gelöscht")
        }

        buttonExportSettings.setOnClickListener {
            exportSettings()
        }

        buttonResetToDefaults.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun loadCurrentSettings() {
        editTextIpAddress.setText(settingsManager.getMoxaIpAddress())
        editTextPort.setText(settingsManager.getMoxaPort().toString())

        spinnerActiveCells.setSelection(settingsManager.getActiveCellCount() - 1)

        switchAutoRefresh.isChecked = settingsManager.getAutoRefreshEnabled()
        val intervalProgress = getProgressFromInterval(settingsManager.getRefreshInterval())
        seekBarRefreshInterval.progress = intervalProgress
        seekBarRefreshInterval.isEnabled = switchAutoRefresh.isChecked
        updateRefreshIntervalText(settingsManager.getRefreshInterval())

        editTextConnectionTimeout.setText(settingsManager.getConnectionTimeout().toString())
        editTextReadTimeout.setText(settingsManager.getReadTimeout().toString())

        val logLevelIndex = SettingsManager.LogLevel.values().indexOf(settingsManager.getLogLevel())
        spinnerLogLevel.setSelection(logLevelIndex)

        updateConnectionStatus("Nicht getestet")
    }

    private fun testConnection() {
        buttonTestConnection.isEnabled = false
        updateConnectionStatus("Teste Verbindung...")

        lifecycleScope.launch {
            try {
                val result = CommunicationManager().apply {
                    val success = connect(
                        settingsManager.getMoxaIpAddress(),
                        settingsManager.getMoxaPort()
                    )
                    if (success) {
                        disconnect()
                    }
                }
                updateConnectionStatus("Verbindung erfolgreich")
                loggingManager.logInfo("Settings", "Verbindungstest erfolgreich")
            } catch (e: Exception) {
                updateConnectionStatus("Verbindung fehlgeschlagen: ${e.message}")
                loggingManager.logError("Settings", "Verbindungstest fehlgeschlagen", e)
            } finally {
                buttonTestConnection.isEnabled = true
            }
        }
    }

    private fun createTimeoutWatcher(onTimeout: (Int) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val timeout = s.toString().toIntOrNull()
                if (timeout != null && timeout > 0) {
                    onTimeout(timeout)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
    }

    private fun getIntervalFromProgress(progress: Int): Long {
        return when (progress) {
            0 -> 500L   // 0.5s
            1 -> 1000L  // 1s
            2 -> 1500L  // 1.5s
            3 -> 2000L  // 2s
            4 -> 2500L  // 2.5s
            5 -> 3000L  // 3s
            6 -> 4000L  // 4s
            7 -> 5000L  // 5s
            else -> 2000L
        }
    }

    private fun getProgressFromInterval(intervalMs: Long): Int {
        return when (intervalMs) {
            500L -> 0
            1000L -> 1
            1500L -> 2
            2000L -> 3
            2500L -> 4
            3000L -> 5
            4000L -> 6
            5000L -> 7
            else -> 3 // Default to 2s
        }
    }

    private fun updateRefreshIntervalText(intervalMs: Long) {
        val text = if (intervalMs < 1000) {
            "${intervalMs}ms"
        } else {
            "${intervalMs / 1000.0}s"
        }
        textViewRefreshInterval.text = "Aktualisierungsintervall: $text"
    }

    private fun updateConnectionStatus(status: String) {
        textViewConnectionStatus.text = "Status: $status"
    }

    private fun exportSettings() {
        // TODO: Implement settings export
        updateConnectionStatus("Export noch nicht implementiert")
    }

    private fun resetToDefaults() {
        settingsManager.resetToDefaults()
        loadCurrentSettings()
        updateConnectionStatus("Einstellungen zurückgesetzt")
        loggingManager.logInfo("Settings", "Einstellungen auf Standard zurückgesetzt")
    }
}