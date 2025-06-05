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
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MoxaSettingsFragment : Fragment() {

    // UI Components
    private lateinit var editTextMoxaIp: TextInputEditText
    private lateinit var editTextMoxaPort: TextInputEditText
    private lateinit var textViewMoxaModel: TextView
    private lateinit var buttonTestMoxaConnection: Button
    private lateinit var textViewConnectionStatus: TextView
    private lateinit var progressBarConnection: ProgressBar

    // Port Configuration
    private lateinit var spinnerPort1Baudrate: Spinner
    private lateinit var spinnerPort2Baudrate: Spinner
    private lateinit var buttonApplyPort1: Button
    private lateinit var buttonApplyPort2: Button
    private lateinit var textViewPort1Status: TextView
    private lateinit var textViewPort2Status: TextView

    // Device Management
    private lateinit var buttonRestartMoxa: Button
    private lateinit var buttonFactoryReset: Button
    private lateinit var buttonDiagnostic: Button
    private lateinit var textViewSystemStatus: TextView

    // Advanced Settings
    private lateinit var editTextUsername: TextInputEditText
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var switchAutoDetectBaudrate: Switch
    private lateinit var buttonBackupConfig: Button
    private lateinit var buttonRestoreConfig: Button

    // Services
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager
    private lateinit var moxaController: Moxa5232Controller

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_moxa_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // WICHTIG: Reihenfolge geändert - Views zuerst initialisieren
        initializeViews(view)
        initializeServices()
        setupSpinners()
        setupListeners()
        loadCurrentSettings()

        // Auto-Test beim Start
        lifecycleScope.launch {
            delay(1000)
            testMoxaConnection()
        }
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())

        // Jetzt sind die Views initialisiert, updateMoxaController ist sicher
        updateMoxaController()

        loggingManager.logInfo("MoxaSettings", "Moxa-Einstellungen Fragment gestartet")
    }

    private fun updateMoxaController() {
        // Sichere Überprüfung ob Views initialisiert sind
        val ip = if (::editTextMoxaIp.isInitialized) {
            editTextMoxaIp.text?.toString() ?: settingsManager.getMoxaIpAddress()
        } else {
            settingsManager.getMoxaIpAddress()
        }

        val username = if (::editTextUsername.isInitialized) {
            editTextUsername.text?.toString() ?: "admin"
        } else {
            "admin"
        }

        val password = if (::editTextPassword.isInitialized) {
            editTextPassword.text?.toString() ?: "moxa"
        } else {
            "moxa"
        }

        moxaController = Moxa5232Controller(ip, username, password)
    }

    private fun initializeViews(view: View) {
        // Connection Settings
        editTextMoxaIp = view.findViewById(R.id.editTextMoxaIp)
        editTextMoxaPort = view.findViewById(R.id.editTextMoxaPort)
        textViewMoxaModel = view.findViewById(R.id.textViewMoxaModel)
        buttonTestMoxaConnection = view.findViewById(R.id.buttonTestMoxaConnection)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)
        progressBarConnection = view.findViewById(R.id.progressBarConnection)

        // Port Configuration
        spinnerPort1Baudrate = view.findViewById(R.id.spinnerPort1Baudrate)
        spinnerPort2Baudrate = view.findViewById(R.id.spinnerPort2Baudrate)
        buttonApplyPort1 = view.findViewById(R.id.buttonApplyPort1)
        buttonApplyPort2 = view.findViewById(R.id.buttonApplyPort2)
        textViewPort1Status = view.findViewById(R.id.textViewPort1Status)
        textViewPort2Status = view.findViewById(R.id.textViewPort2Status)

        // Device Management
        buttonRestartMoxa = view.findViewById(R.id.buttonRestartMoxa)
        buttonFactoryReset = view.findViewById(R.id.buttonFactoryReset)
        buttonDiagnostic = view.findViewById(R.id.buttonDiagnostic)
        textViewSystemStatus = view.findViewById(R.id.textViewSystemStatus)

        // Advanced Settings
        editTextUsername = view.findViewById(R.id.editTextUsername)
        editTextPassword = view.findViewById(R.id.editTextPassword)
        switchAutoDetectBaudrate = view.findViewById(R.id.switchAutoDetectBaudrate)
        buttonBackupConfig = view.findViewById(R.id.buttonBackupConfig)
        buttonRestoreConfig = view.findViewById(R.id.buttonRestoreConfig)

        // Initial Status
        updateConnectionStatus("Nicht getestet", false)
        updatePortStatus(1, "Nicht geladen")
        updatePortStatus(2, "Nicht geladen")
        updateSystemStatus("Bereit")
    }

    private fun setupSpinners() {
        val baudrateOptions = Moxa5232Controller.SUPPORTED_BAUDRATES.map { "$it bps" }

        // Port 1 Spinner
        val adapter1 = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, baudrateOptions)
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPort1Baudrate.adapter = adapter1

        // Port 2 Spinner
        val adapter2 = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, baudrateOptions)
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPort2Baudrate.adapter = adapter2

        // Standard: 9600 bps
        val defaultIndex = Moxa5232Controller.SUPPORTED_BAUDRATES.indexOf(9600)
        if (defaultIndex >= 0) {
            spinnerPort1Baudrate.setSelection(defaultIndex)
            spinnerPort2Baudrate.setSelection(defaultIndex)
        }
    }

    private fun setupListeners() {
        // Connection Settings Listeners
        editTextMoxaIp.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setMoxaIpAddress(s.toString())
                updateMoxaController()
                updateConnectionStatus("Nicht getestet", false)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editTextMoxaPort.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val port = s.toString().toIntOrNull()
                if (port != null && port in 1..65535) {
                    settingsManager.setMoxaPort(port)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Authentication Listeners
        editTextUsername.addTextChangedListener(createAuthTextWatcher())
        editTextPassword.addTextChangedListener(createAuthTextWatcher())

        // Button Listeners
        buttonTestMoxaConnection.setOnClickListener { testMoxaConnection() }
        buttonApplyPort1.setOnClickListener { applyPortBaudrate(1) }
        buttonApplyPort2.setOnClickListener { applyPortBaudrate(2) }
        buttonRestartMoxa.setOnClickListener { showRestartConfirmation() }
        buttonFactoryReset.setOnClickListener { showFactoryResetConfirmation() }
        buttonDiagnostic.setOnClickListener { runComprehensiveDiagnostic() }
        buttonBackupConfig.setOnClickListener { backupMoxaConfiguration() }
        buttonRestoreConfig.setOnClickListener { restoreMoxaConfiguration() }

        // Auto-Detect Switch
        switchAutoDetectBaudrate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                autoDetectBaudrates()
            }
        }
    }

    private fun createAuthTextWatcher(): TextWatcher {
        return object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateMoxaController()
                updateConnectionStatus("Anmeldedaten geändert - erneut testen", false)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
    }

    private fun testMoxaConnection() {
        setUIEnabled(false)
        updateConnectionStatus("Teste Verbindung...", false)
        showProgress(true)

        lifecycleScope.launch {
            try {
                val isReachable = moxaController.testConnection()

                if (isReachable) {
                    updateConnectionStatus("✅ Moxa NPort 5232 erreichbar", true)
                    textViewMoxaModel.text = "Moxa NPort 5232"

                    // Lade Port-Konfigurationen
                    loadPortConfigurations()

                    loggingManager.logInfo("MoxaSettings", "Moxa-Verbindungstest erfolgreich")
                } else {
                    updateConnectionStatus("❌ Moxa nicht erreichbar", false)
                    textViewMoxaModel.text = "Unbekannt"
                    loggingManager.logWarning("MoxaSettings", "Moxa-Verbindungstest fehlgeschlagen")
                }

            } catch (e: Exception) {
                updateConnectionStatus("❌ Verbindungsfehler: ${e.message}", false)
                loggingManager.logError("MoxaSettings", "Moxa-Verbindungsfehler", e)
            } finally {
                setUIEnabled(true)
                showProgress(false)
            }
        }
    }

    private fun loadPortConfigurations() {
        lifecycleScope.launch {
            try {
                // Port 1 Konfiguration laden
                val port1Config = moxaController.getPortConfiguration(1)
                if (port1Config != null) {
                    updatePortStatus(1, "Baudrate: ${port1Config.baudrate} bps")
                    val baudrateIndex = Moxa5232Controller.SUPPORTED_BAUDRATES.indexOf(port1Config.baudrate)
                    if (baudrateIndex >= 0) {
                        spinnerPort1Baudrate.setSelection(baudrateIndex)
                    }
                } else {
                    updatePortStatus(1, "❌ Konfiguration nicht lesbar")
                }

                // Port 2 Konfiguration laden
                val port2Config = moxaController.getPortConfiguration(2)
                if (port2Config != null) {
                    updatePortStatus(2, "Baudrate: ${port2Config.baudrate} bps")
                    val baudrateIndex = Moxa5232Controller.SUPPORTED_BAUDRATES.indexOf(port2Config.baudrate)
                    if (baudrateIndex >= 0) {
                        spinnerPort2Baudrate.setSelection(baudrateIndex)
                    }
                } else {
                    updatePortStatus(2, "❌ Konfiguration nicht lesbar")
                }

            } catch (e: Exception) {
                updatePortStatus(1, "❌ Fehler beim Laden")
                updatePortStatus(2, "❌ Fehler beim Laden")
                loggingManager.logError("MoxaSettings", "Port-Konfiguration laden fehlgeschlagen", e)
            }
        }
    }

    private fun applyPortBaudrate(port: Int) {
        val spinner = if (port == 1) spinnerPort1Baudrate else spinnerPort2Baudrate
        val button = if (port == 1) buttonApplyPort1 else buttonApplyPort2

        val selectedBaudrate = Moxa5232Controller.SUPPORTED_BAUDRATES[spinner.selectedItemPosition]

        button.isEnabled = false
        updatePortStatus(port, "Ändere Baudrate auf $selectedBaudrate bps...")

        lifecycleScope.launch {
            try {
                val success = moxaController.setBaudrate(port, selectedBaudrate)

                if (success) {
                    updatePortStatus(port, "✅ Baudrate erfolgreich auf $selectedBaudrate bps geändert")
                    loggingManager.logInfo("MoxaSettings", "Port $port Baudrate geändert: $selectedBaudrate")

                    // Bei Port 1: Teste Zell-Kommunikation
                    if (port == 1) {
                        delay(2000)
                        testCellCommunicationAfterBaudrateChange()
                    }
                } else {
                    updatePortStatus(port, "❌ Baudrate-Änderung fehlgeschlagen")
                    loggingManager.logError("MoxaSettings", "Port $port Baudrate-Änderung fehlgeschlagen")
                }

            } catch (e: Exception) {
                updatePortStatus(port, "❌ Fehler: ${e.message}")
                loggingManager.logError("MoxaSettings", "Port $port Baudrate-Fehler", e)
            } finally {
                button.isEnabled = true
            }
        }
    }

    private fun showRestartConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Moxa NPort 5232 neu starten")
            .setMessage("Möchten Sie die Moxa wirklich neu starten?\n\n⚠️ Alle Zell-Verbindungen werden für ca. 45 Sekunden unterbrochen.")
            .setPositiveButton("Neu starten") { _, _ ->
                restartMoxa()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun restartMoxa() {
        setUIEnabled(false)
        updateSystemStatus("Starte Moxa neu...")

        lifecycleScope.launch {
            try {
                val success = moxaController.restartDevice()

                if (success) {
                    updateSystemStatus("✅ Neustart eingeleitet - warte 45 Sekunden...")
                    loggingManager.logInfo("MoxaSettings", "Moxa Neustart eingeleitet")

                    delay(45000) // 45 Sekunden warten

                    updateSystemStatus("Teste Verbindung nach Neustart...")
                    testMoxaConnection()
                } else {
                    updateSystemStatus("❌ Neustart fehlgeschlagen")
                    loggingManager.logError("MoxaSettings", "Moxa Neustart fehlgeschlagen")
                }

            } catch (e: Exception) {
                updateSystemStatus("❌ Neustart-Fehler: ${e.message}")
                loggingManager.logError("MoxaSettings", "Moxa Neustart-Fehler", e)
            } finally {
                setUIEnabled(true)
            }
        }
    }

    private fun autoDetectBaudrates() {
        switchAutoDetectBaudrate.isEnabled = false
        updateSystemStatus("Erkenne Baudraten automatisch...")

        lifecycleScope.launch {
            try {
                val communicationManager = CommunicationManager()
                val detectedBaudrate = communicationManager.detectWorkingBaudrate(
                    settingsManager.getMoxaIpAddress(),
                    settingsManager.getMoxaPort()
                )

                if (detectedBaudrate != null) {
                    updateSystemStatus("✅ Funktionierende Baudrate erkannt: $detectedBaudrate bps")

                    // Setze erkannte Baudrate in Port 1 Spinner
                    val baudrateIndex = Moxa5232Controller.SUPPORTED_BAUDRATES.indexOf(detectedBaudrate)
                    if (baudrateIndex >= 0) {
                        spinnerPort1Baudrate.setSelection(baudrateIndex)
                    }

                    loggingManager.logInfo("MoxaSettings", "Baudrate automatisch erkannt: $detectedBaudrate")
                } else {
                    updateSystemStatus("❌ Keine funktionierende Baudrate gefunden")
                    loggingManager.logWarning("MoxaSettings", "Automatische Baudrate-Erkennung fehlgeschlagen")
                }

            } catch (e: Exception) {
                updateSystemStatus("❌ Auto-Erkennung fehlgeschlagen: ${e.message}")
                loggingManager.logError("MoxaSettings", "Baudrate Auto-Erkennung Fehler", e)
            } finally {
                switchAutoDetectBaudrate.isEnabled = true
                switchAutoDetectBaudrate.isChecked = false
            }
        }
    }

    private fun runComprehensiveDiagnostic() {
        setUIEnabled(false)
        updateSystemStatus("Führe umfassende Diagnose durch...")

        lifecycleScope.launch {
            try {
                val communicationManager = CommunicationManager()
                val diagnostic = communicationManager.performConnectionDiagnostic(
                    settingsManager.getMoxaIpAddress(),
                    settingsManager.getMoxaPort()
                )

                val moxaReachable = moxaController.testConnection()

                showDiagnosticResults(diagnostic, moxaReachable)
                updateSystemStatus("Diagnose abgeschlossen")

            } catch (e: Exception) {
                updateSystemStatus("❌ Diagnose fehlgeschlagen: ${e.message}")
                loggingManager.logError("MoxaSettings", "Umfassende Diagnose fehlgeschlagen", e)
            } finally {
                setUIEnabled(true)
            }
        }
    }

    private fun testCellCommunicationAfterBaudrateChange() {
        lifecycleScope.launch {
            try {
                updatePortStatus(1, "Teste Zell-Kommunikation mit neuer Baudrate...")

                val communicationManager = CommunicationManager()
                val success = communicationManager.connect(
                    settingsManager.getMoxaIpAddress(),
                    settingsManager.getMoxaPort()
                )

                if (success) {
                    communicationManager.disconnect()
                    updatePortStatus(1, "✅ Baudrate geändert - Zellen erreichbar")
                } else {
                    updatePortStatus(1, "⚠️ Baudrate geändert, aber Zellen nicht erreichbar")
                }

            } catch (e: Exception) {
                updatePortStatus(1, "⚠️ Zell-Test nach Baudrate-Änderung fehlgeschlagen")
            }
        }
    }

    private fun loadCurrentSettings() {
        editTextMoxaIp.setText(settingsManager.getMoxaIpAddress())
        editTextMoxaPort.setText(settingsManager.getMoxaPort().toString())
        editTextUsername.setText("admin")
        editTextPassword.setText("moxa")
    }

    // UI Helper Methods
    private fun updateConnectionStatus(status: String, isSuccess: Boolean) {
        textViewConnectionStatus.text = "Verbindung: $status"
        textViewConnectionStatus.setTextColor(
            requireContext().getColor(
                if (isSuccess) R.color.status_success_color else R.color.status_error_color
            )
        )
    }

    private fun updatePortStatus(port: Int, status: String) {
        val textView = if (port == 1) textViewPort1Status else textViewPort2Status
        textView.text = "Port $port: $status"
    }

    private fun updateSystemStatus(status: String) {
        textViewSystemStatus.text = "System: $status"
    }

    private fun setUIEnabled(enabled: Boolean) {
        buttonTestMoxaConnection.isEnabled = enabled
        buttonApplyPort1.isEnabled = enabled
        buttonApplyPort2.isEnabled = enabled
        buttonRestartMoxa.isEnabled = enabled
        buttonDiagnostic.isEnabled = enabled
        spinnerPort1Baudrate.isEnabled = enabled
        spinnerPort2Baudrate.isEnabled = enabled
    }

    private fun showProgress(show: Boolean) {
        progressBarConnection.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showDiagnosticResults(diagnostic: CommunicationManager.ConnectionDiagnostic, moxaReachable: Boolean) {
        val message = buildString {
            appendLine("🔍 Umfassende Moxa-Diagnose:")
            appendLine()
            appendLine("📡 Netzwerk: ${if (diagnostic.networkReachable) "✅ Erreichbar" else "❌ Nicht erreichbar"}")
            appendLine("🌐 Moxa Web-Interface: ${if (moxaReachable) "✅ Erreichbar" else "❌ Nicht erreichbar"}")
            appendLine("📞 Moxa Datenport: ${if (diagnostic.moxaReachable) "✅ Verbunden" else "❌ Nicht verbunden"}")
            appendLine("⚖️ Zell-Kommunikation: ${if (diagnostic.cellCommunication) "✅ Funktioniert" else "❌ Fehlgeschlagen"}")

            if (diagnostic.detectedBaudrate != null) {
                appendLine("🔧 Erkannte Baudrate: ${diagnostic.detectedBaudrate} bps")
            }

            appendLine()
            appendLine("📋 Empfehlung: ${diagnostic.getStatusSummary()}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Moxa-Diagnose Ergebnisse")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFactoryResetConfirmation() {
        // Placeholder - würde Factory Reset implementieren
        AlertDialog.Builder(requireContext())
            .setTitle("Factory Reset")
            .setMessage("Factory Reset ist noch nicht implementiert.\n\nDiese Funktion würde die Moxa auf Werkseinstellungen zurücksetzen.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun backupMoxaConfiguration() {
        // Placeholder für Backup-Funktion
        updateSystemStatus("⚠️ Backup-Funktion noch nicht implementiert")
    }

    private fun restoreMoxaConfiguration() {
        // Placeholder für Restore-Funktion
        updateSystemStatus("⚠️ Restore-Funktion noch nicht implementiert")
    }
}