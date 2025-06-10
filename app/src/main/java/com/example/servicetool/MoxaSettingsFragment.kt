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
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
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

    // Port 1 UI
    private lateinit var spinnerPort1Baudrate: Spinner
    private lateinit var spinnerPort1DataBits: Spinner
    private lateinit var spinnerPort1StopBits: Spinner
    private lateinit var spinnerPort1Parity: Spinner
    private lateinit var spinnerPort1FlowControl: Spinner
    private lateinit var switchPort1Fifo: SwitchMaterial
    private lateinit var textViewPort1Interface: TextView
    private lateinit var buttonApplyPort1: Button
    private lateinit var textViewPort1Status: TextView

    // Port 2 UI
    private lateinit var spinnerPort2Baudrate: Spinner
    private lateinit var spinnerPort2DataBits: Spinner
    private lateinit var spinnerPort2StopBits: Spinner
    private lateinit var spinnerPort2Parity: Spinner
    private lateinit var spinnerPort2FlowControl: Spinner
    private lateinit var switchPort2Fifo: SwitchMaterial
    private lateinit var textViewPort2Interface: TextView
    private lateinit var buttonApplyPort2: Button
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

        // Port 1
        spinnerPort1Baudrate = view.findViewById(R.id.spinnerPort1Baudrate)
        spinnerPort1DataBits = view.findViewById(R.id.spinnerPort1DataBits)
        spinnerPort1StopBits = view.findViewById(R.id.spinnerPort1StopBits)
        spinnerPort1Parity = view.findViewById(R.id.spinnerPort1Parity)
        spinnerPort1FlowControl = view.findViewById(R.id.spinnerPort1FlowControl)
        switchPort1Fifo = view.findViewById(R.id.switchPort1Fifo)
        textViewPort1Interface = view.findViewById(R.id.textViewPort1Interface)
        buttonApplyPort1 = view.findViewById(R.id.buttonApplyPort1)
        textViewPort1Status = view.findViewById(R.id.textViewPort1Status)

        // Port 2
        spinnerPort2Baudrate = view.findViewById(R.id.spinnerPort2Baudrate)
        spinnerPort2DataBits = view.findViewById(R.id.spinnerPort2DataBits)
        spinnerPort2StopBits = view.findViewById(R.id.spinnerPort2StopBits)
        spinnerPort2Parity = view.findViewById(R.id.spinnerPort2Parity)
        spinnerPort2FlowControl = view.findViewById(R.id.spinnerPort2FlowControl)
        switchPort2Fifo = view.findViewById(R.id.switchPort2Fifo)
        textViewPort2Interface = view.findViewById(R.id.textViewPort2Interface)
        buttonApplyPort2 = view.findViewById(R.id.buttonApplyPort2)
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

        setPortUIEnabled(1, false)
        setPortUIEnabled(2, false)
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

        buttonApplyPort1.setOnClickListener { applyAllSettingsForPort(1) }
        buttonApplyPort2.setOnClickListener { applyAllSettingsForPort(2) }
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
                        updatePortStatus(1, "Aktuell: ${it.baudRate} bps, ${it.dataBits}N${it.stopBits}")
                        setupPortSpinners(1, it)
                    }

                    portSettings[2]?.let {
                        updatePortStatus(2, "Aktuell: ${it.baudRate} bps, ${it.dataBits}N${it.stopBits}")
                        setupPortSpinners(2, it)
                    }
                } else {
                    updateConnectionStatus("❌ Konfiguration konnte nicht geladen werden", false)
                    updatePortStatus(1, "Fehler beim Laden")
                    updatePortStatus(2, "Fehler beim Laden")
                }
            }
        }
    }

    /**
     * NEU: Sammelt alle Einstellungen aus der UI und wendet sie an.
     */
    private fun applyAllSettingsForPort(port: Int) {
        val baudSpinner = if (port == 1) spinnerPort1Baudrate else spinnerPort2Baudrate
        val dataBitsSpinner = if (port == 1) spinnerPort1DataBits else spinnerPort2DataBits
        val stopBitsSpinner = if (port == 1) spinnerPort1StopBits else spinnerPort2StopBits
        val paritySpinner = if (port == 1) spinnerPort1Parity else spinnerPort2Parity
        val flowControlSpinner = if (port == 1) spinnerPort1FlowControl else spinnerPort2FlowControl
        val fifoSwitch = if (port == 1) switchPort1Fifo else switchPort2Fifo

        val settingsUpdate = MoxaTelnetController.PortSettingsUpdate(
            baudRate = baudSpinner.selectedItem.toString().toIntOrNull() ?: 9600,
            dataBits = dataBitsSpinner.selectedItem.toString().toIntOrNull() ?: 8,
            stopBits = stopBitsSpinner.selectedItem.toString().toIntOrNull() ?: 1,
            parity = paritySpinner.selectedItem.toString(),
            flowControl = flowControlSpinner.selectedItem.toString(),
            fifoEnabled = fifoSwitch.isChecked
        )

        setUIEnabled(false, keepRestartEnabled = false)
        updatePortStatus(port, "Wende Einstellungen an & starte neu...")
        showProgress(true)

        lifecycleScope.launch {
            val success = telnetController.applyPortSettings(port, settingsUpdate, getPassword())

            withContext(Dispatchers.Main) {
                if (success) {
                    updatePortStatus(port, "Einstellungen angewendet. Neustart wird eingeleitet...")
                    monitorRestartProgress()
                } else {
                    updatePortStatus(port, "❌ Fehler beim Anwenden der Einstellungen")
                    setUIEnabled(true)
                    showProgress(false)
                }
            }
        }
    }

    private fun setupPortSpinners(port: Int, settings: MoxaTelnetController.PortSettings) {
        // Baudrate
        val baudSpinner = if (port == 1) spinnerPort1Baudrate else spinnerPort2Baudrate
        val baudRates = telnetController.supportedBaudRates.map { it.toString() }
        val baudAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, baudRates)
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        baudSpinner.adapter = baudAdapter
        val baudIndex = baudRates.indexOf(settings.baudRate.toString())
        if (baudIndex != -1) baudSpinner.setSelection(baudIndex)

        // Data Bits
        val dataBitsSpinner = if (port == 1) spinnerPort1DataBits else spinnerPort2DataBits
        val dataBits = listOf("5", "6", "7", "8")
        val dataBitsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dataBits)
        dataBitsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dataBitsSpinner.adapter = dataBitsAdapter
        val dataBitsIndex = dataBits.indexOf(settings.dataBits.toString())
        if (dataBitsIndex != -1) dataBitsSpinner.setSelection(dataBitsIndex)

        // Stop Bits
        val stopBitsSpinner = if (port == 1) spinnerPort1StopBits else spinnerPort2StopBits
        val stopBits = listOf("1", "2")
        val stopBitsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, stopBits)
        stopBitsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        stopBitsSpinner.adapter = stopBitsAdapter
        val stopBitsIndex = stopBits.indexOf(settings.stopBits.toString())
        if (stopBitsIndex != -1) stopBitsSpinner.setSelection(stopBitsIndex)

        // Parity
        val paritySpinner = if (port == 1) spinnerPort1Parity else spinnerPort2Parity
        val parity = listOf("None", "Even", "Odd", "Space", "Mark")
        val parityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, parity)
        parityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        paritySpinner.adapter = parityAdapter
        val parityIndex = parity.indexOfFirst { it.equals(settings.parity, ignoreCase = true) }
        if (parityIndex != -1) paritySpinner.setSelection(parityIndex)

        // Flow Control
        val flowControlSpinner = if (port == 1) spinnerPort1FlowControl else spinnerPort2FlowControl
        val flowControl = listOf("None", "RTS/CTS", "XON/XOFF", "DTR/DSR")
        val flowControlAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, flowControl)
        flowControlAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        flowControlSpinner.adapter = flowControlAdapter
        val flowControlIndex = flowControl.indexOfFirst { it.replace("/", "").equals(settings.flowControl.replace("/", ""), ignoreCase = true) }
        if (flowControlIndex != -1) flowControlSpinner.setSelection(flowControlIndex)

        // FIFO
        val fifoSwitch = if (port == 1) switchPort1Fifo else switchPort2Fifo
        fifoSwitch.isChecked = settings.fifo.equals("Enabled", ignoreCase = true)

        // Interface
        val interfaceTextView = if (port == 1) textViewPort1Interface else textViewPort2Interface
        interfaceTextView.text = settings.interfaceType

        setPortUIEnabled(port, true)
    }

    private fun getPassword(): String {
        return editTextPassword.text.toString().ifEmpty { "moxa" }
    }

    private fun showTelnetRestartConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Moxa Neustart")
            .setMessage("Möchten Sie die Moxa wirklich neu starten? Ungespeicherte Änderungen gehen dabei verloren.")
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
        if(!enabled) {
            setPortUIEnabled(1, false)
            setPortUIEnabled(2, false)
        }
    }

    private fun setPortUIEnabled(port: Int, enabled: Boolean) {
        if (port == 1) {
            spinnerPort1Baudrate.isEnabled = enabled
            spinnerPort1DataBits.isEnabled = enabled
            spinnerPort1StopBits.isEnabled = enabled
            spinnerPort1Parity.isEnabled = enabled
            spinnerPort1FlowControl.isEnabled = enabled
            switchPort1Fifo.isEnabled = enabled
            buttonApplyPort1.isEnabled = enabled
        } else {
            spinnerPort2Baudrate.isEnabled = enabled
            spinnerPort2DataBits.isEnabled = enabled
            spinnerPort2StopBits.isEnabled = enabled
            spinnerPort2Parity.isEnabled = enabled
            spinnerPort2FlowControl.isEnabled = enabled
            switchPort2Fifo.isEnabled = enabled
            buttonApplyPort2.isEnabled = enabled
        }
    }

    private fun showProgress(show: Boolean) {
        progressBarConnection.visibility = if (show) View.VISIBLE else View.GONE
    }
}
