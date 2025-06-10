package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoxaSettingsFragment : Fragment() {

    // UI Components
    private lateinit var editTextMoxaIp: TextInputEditText
    private lateinit var editTextMoxaPort: TextInputEditText
    private lateinit var textViewMoxaModel: TextView
    private lateinit var buttonTestMoxaConnection: Button
    private lateinit var textViewConnectionStatus: TextView
    private lateinit var progressBarConnection: ProgressBar
    private lateinit var spinnerPort1Baudrate: Spinner
    private lateinit var spinnerPort2Baudrate: Spinner
    private lateinit var buttonApplyPort1: Button
    private lateinit var buttonApplyPort2: Button
    private lateinit var textViewPort1Status: TextView
    private lateinit var textViewPort2Status: TextView
    private lateinit var buttonRestartMoxa: Button
    private lateinit var buttonFactoryReset: Button
    private lateinit var buttonDiagnostic: Button
    private lateinit var textViewSystemStatus: TextView
    private lateinit var editTextUsername: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonBackupConfig: Button
    private lateinit var buttonRestoreConfig: Button

    // Services
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager
    private lateinit var telnetController: MoxaTelnetController

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_moxa_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeServices()
        initializeViews(view)
        setupListeners()
        loadCurrentSettings()

        lifecycleScope.launch {
            delay(500)
            loadPortConfigurations()
        }
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())
        updateTelnetController()
        loggingManager.logInfo("MoxaSettings", "Moxa-Einstellungen Fragment gestartet (Telnet-Modus)")
    }

    private fun updateTelnetController() {
        val moxaIp = settingsManager.getMoxaIpAddress()
        telnetController = MoxaTelnetController(moxaIp)
    }

    private fun initializeViews(view: View) {
        editTextMoxaIp = view.findViewById(R.id.editTextMoxaIp)
        editTextMoxaPort = view.findViewById(R.id.editTextMoxaPort)
        textViewMoxaModel = view.findViewById(R.id.textViewMoxaModel)
        buttonTestMoxaConnection = view.findViewById(R.id.buttonTestMoxaConnection)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)
        progressBarConnection = view.findViewById(R.id.progressBarConnection)
        spinnerPort1Baudrate = view.findViewById(R.id.spinnerPort1Baudrate)
        spinnerPort2Baudrate = view.findViewById(R.id.spinnerPort2Baudrate)
        buttonApplyPort1 = view.findViewById(R.id.buttonApplyPort1)
        buttonApplyPort2 = view.findViewById(R.id.buttonApplyPort2)
        textViewPort1Status = view.findViewById(R.id.textViewPort1Status)
        textViewPort2Status = view.findViewById(R.id.textViewPort2Status)
        buttonRestartMoxa = view.findViewById(R.id.buttonRestartMoxa)
        buttonFactoryReset = view.findViewById(R.id.buttonFactoryReset)
        buttonDiagnostic = view.findViewById(R.id.buttonDiagnostic)
        textViewSystemStatus = view.findViewById(R.id.textViewSystemStatus)
        editTextUsername = view.findViewById(R.id.editTextUsername)
        editTextPassword = view.findViewById(R.id.editTextPassword)
        buttonBackupConfig = view.findViewById(R.id.buttonBackupConfig)
        buttonRestoreConfig = view.findViewById(R.id.buttonRestoreConfig)

        updateConnectionStatus("Nicht getestet", false)
        updateSystemStatus("Bereit")

        spinnerPort1Baudrate.isEnabled = false
        spinnerPort2Baudrate.isEnabled = false
        buttonApplyPort1.isEnabled = false
        buttonApplyPort2.isEnabled = false
    }

    private fun setupListeners() {
        editTextMoxaIp.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setMoxaIpAddress(s.toString())
                updateTelnetController()
                updateConnectionStatus("Nicht getestet", false)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        buttonTestMoxaConnection.setOnClickListener { loadPortConfigurations() }
        buttonRestartMoxa.setOnClickListener { showTelnetRestartConfirmation() }
        buttonApplyPort1.setOnClickListener { applyBaudRateChange(1) }
        buttonApplyPort2.setOnClickListener { applyBaudRateChange(2) }
    }

    private fun loadPortConfigurations() {
        setUIEnabled(false, keepRestartEnabled = false)
        updatePortStatus(1, "Lade Konfiguration...")
        updatePortStatus(2, "Lade Konfiguration...")
        showProgress(true)

        lifecycleScope.launch {
            val portSettings = telnetController.getPortSettings(getPassword())

            withContext(Dispatchers.Main) {
                setUIEnabled(true, keepRestartEnabled = portSettings != null)
                showProgress(false)
                if (portSettings != null) {
                    updateConnectionStatus("✅ Konfiguration geladen", true)

                    portSettings[1]?.let {
                        updatePortStatus(1, "Aktuell: ${it.baudRate} bps")
                        setupBaudRateSpinner(spinnerPort1Baudrate, it.baudRate)
                    }

                    portSettings[2]?.let {
                        updatePortStatus(2, "Aktuell: ${it.baudRate} bps")
                        setupBaudRateSpinner(spinnerPort2Baudrate, it.baudRate)
                    }
                } else {
                    updateConnectionStatus("❌ Konfiguration konnte nicht geladen werden", false)
                    updatePortStatus(1, "Fehler beim Laden")
                    updatePortStatus(2, "Fehler beim Laden")
                }
            }
        }
    }

    private fun applyBaudRateChange(port: Int) {
        val spinner = if (port == 1) spinnerPort1Baudrate else spinnerPort2Baudrate
        val selectedBaudRate = spinner.selectedItem.toString().toIntOrNull() ?: 9600

        setUIEnabled(false, keepRestartEnabled = false)
        updatePortStatus(port, "Ändere auf $selectedBaudRate bps...")
        showProgress(true)

        lifecycleScope.launch {
            val success = telnetController.setBaudRate(port, selectedBaudRate, getPassword())

            withContext(Dispatchers.Main) {
                if (success) {
                    updatePortStatus(port, "Baudrate geändert. Neustart wird eingeleitet...")
                    monitorRestartProgress()
                } else {
                    updatePortStatus(port, "❌ Fehler beim Ändern der Baudrate")
                    setUIEnabled(true)
                    showProgress(false)
                }
            }
        }
    }

    private fun setupBaudRateSpinner(spinner: Spinner, currentBaud: Int) {
        val rates = telnetController.supportedBaudRates.map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentIndex = rates.indexOf(currentBaud.toString())
        if (currentIndex != -1) {
            spinner.setSelection(currentIndex)
        }
    }

    private fun getPassword(): String {
        return editTextPassword.text.toString().ifEmpty { "moxa" }
    }

    private fun showTelnetRestartConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Moxa Neustart via Telnet")
            .setMessage("Möchten Sie die Moxa wirklich neu starten?")
            .setPositiveButton("Ja, neu starten") { _, _ ->
                showToast("Neustart-Prozess wird gestartet...")
                restartMoxaViaTelnet()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun restartMoxaViaTelnet() {
        setUIEnabled(false, keepRestartEnabled = false)
        updateSystemStatus("Starte Moxa über Telnet neu...")
        loggingManager.logInfo("MoxaSettings", "Telnet-Neustart wird eingeleitet...")

        lifecycleScope.launch(Dispatchers.IO) {
            val success = telnetController.restart(getPassword())

            withContext(Dispatchers.Main) {
                if (success) {
                    updateSystemStatus("✅ Telnet-Befehle gesendet. Warte auf Neustart...")
                    monitorRestartProgress()
                } else {
                    updateSystemStatus("❌ Telnet-Neustart fehlgeschlagen.")
                    setUIEnabled(true)
                }
            }
        }
    }

    private suspend fun monitorRestartProgress() {
        withContext(Dispatchers.Main) {
            updateSystemStatus("Moxa startet neu... (Warte 5s)")
        }
        delay(5000)

        for (attempt in 1..15) {
            withContext(Dispatchers.Main) {
                updateSystemStatus("🔍 Überwache Neustart... Versuch $attempt/15")
            }
            try {
                if (telnetController.testConnection()) {
                    withContext(Dispatchers.Main) {
                        updateSystemStatus("✅ Moxa ist wieder online!")
                        loadPortConfigurations()
                    }
                    return
                }
            } catch (e: Exception) { /* Erwartet */ }

            delay(5000)
        }

        withContext(Dispatchers.Main) {
            updateSystemStatus("⚠️ Moxa antwortet nicht.")
            updateConnectionStatus("Verbindung fehlgeschlagen", false)
            setUIEnabled(true)
        }
    }

    private fun loadCurrentSettings() {
        editTextMoxaIp.setText(settingsManager.getMoxaIpAddress())
        editTextMoxaPort.setText(settingsManager.getMoxaPort().toString())
        editTextPassword.setText("moxa")
    }

    private fun showToast(message: String) {
        if (context != null) {
            activity?.runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateConnectionStatus(status: String, isSuccess: Boolean) {
        if(context != null) {
            textViewConnectionStatus.text = "Verbindung: $status"
            textViewConnectionStatus.setTextColor(requireContext().getColor(if (isSuccess) R.color.status_success_color else R.color.status_error_color))
        }
    }

    private fun updateSystemStatus(status: String) {
        textViewSystemStatus.text = "System: $status"
    }

    private fun updatePortStatus(port: Int, status: String) {
        val textView = if (port == 1) textViewPort1Status else textViewPort2Status
        textView.text = "Port $port: $status"
    }

    private fun setUIEnabled(enabled: Boolean, keepRestartEnabled: Boolean? = null) {
        val finalRestartEnabled = keepRestartEnabled ?: enabled
        buttonRestartMoxa.isEnabled = finalRestartEnabled
        buttonTestMoxaConnection.isEnabled = enabled
        spinnerPort1Baudrate.isEnabled = enabled
        spinnerPort2Baudrate.isEnabled = enabled
        buttonApplyPort1.isEnabled = enabled
        buttonApplyPort2.isEnabled = enabled
    }

    private fun showProgress(show: Boolean) {
        progressBarConnection.visibility = if (show) View.VISIBLE else View.GONE
    }
}
