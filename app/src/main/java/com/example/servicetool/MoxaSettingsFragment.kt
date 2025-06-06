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
        updateMoxaController()
        loggingManager.logInfo("MoxaSettings", "Moxa-Einstellungen Fragment gestartet")
    }

    private fun updateMoxaController() {
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
            .setMessage("Möchten Sie die Moxa wirklich neu starten?\n\n⚠️ Alle Zell-Verbindungen werden für ca. 45 Sekunden unterbrochen.\n\n🔧 Verwendet korrigierte Token-basierte Restart-Methode.")
            .setPositiveButton("Neu starten") { _, _ ->
                restartMoxaWithCorrectMethod()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun restartMoxaWithCorrectMethod() {
        setUIEnabled(false)
        updateSystemStatus("Starte Moxa mit korrigierter Methode neu...")

        lifecycleScope.launch {
            try {
                // Verwende die korrigierte Restart-Methode
                val success = moxaController.restartDevice()

                if (success) {
                    updateSystemStatus("✅ Hardware-Neustart eingeleitet - warte 45 Sekunden...")
                    loggingManager.logInfo("MoxaSettings", "Moxa Hardware-Neustart eingeleitet")
                    monitorRestartProgress()
                } else {
                    updateSystemStatus("❌ Hardware-Neustart fehlgeschlagen")
                    loggingManager.logError("MoxaSettings", "Moxa Hardware-Neustart fehlgeschlagen")
                    showRestartTroubleshooting()
                }

            } catch (e: Exception) {
                updateSystemStatus("❌ Neustart-Fehler: ${e.message}")
                loggingManager.logError("MoxaSettings", "Moxa Neustart-Fehler", e)
                showRestartTroubleshooting()
            } finally {
                setUIEnabled(true)
            }
        }
    }

    private suspend fun monitorRestartProgress() {
        // Überwache den Restart-Fortschritt
        for (attempt in 1..15) { // 15 Versuche = ca. 75 Sekunden
            delay(5000) // 5 Sekunden warten

            updateSystemStatus("🔍 Überwache Neustart... Versuch $attempt/15")

            try {
                val isOnline = moxaController.testConnection()
                if (isOnline) {
                    updateSystemStatus("✅ Moxa ist nach Neustart wieder online!")

                    // Automatisch Port-Konfigurationen neu laden
                    delay(2000)
                    loadPortConfigurations()
                    return
                }
            } catch (e: Exception) {
                // Erwartet während Neustart
            }
        }

        // Nach 75 Sekunden immer noch offline
        updateSystemStatus("⚠️ Moxa antwortet nach Neustart nicht - prüfen Sie die Hardware")
        showRestartTroubleshooting()
    }

    private fun showRestartTroubleshooting() {
        val message = """
            🔧 Neustart-Troubleshooting:
            
            1. ✅ Netzwerk-Ping testen:
               ping ${settingsManager.getMoxaIpAddress()}
               
            2. 🔌 Hardware prüfen:
               • Power-LED leuchtet?
               • Ethernet-LED blinkt?
               
            3. 🔄 Manual Reset:
               • Reset-Button 5 Sek drücken
               • Stromkabel aus/einstecken
               
            4. 📡 IP-Adresse prüfen:
               • DHCP könnte neue IP vergeben haben
               • Standard-IP: 192.168.127.254
               
            5. 🌐 Browser-Test:
               http://${settingsManager.getMoxaIpAddress()}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Neustart-Problembehebung")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Erneut testen") { _, _ ->
                testMoxaConnection()
            }
            .show()
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
        AlertDialog.Builder(requireContext())
            .setTitle("Factory Reset")
            .setMessage("Factory Reset ist noch nicht implementiert.\n\nDiese Funktion würde die Moxa auf Werkseinstellungen zurücksetzen.\n\n⚠️ WARNUNG: Alle Konfigurationen gehen verloren!")
            .setPositiveButton("OK", null)
            .setNeutralButton("Mehr Info") { _, _ ->
                showFactoryResetInfo()
            }
            .show()
    }

    private fun showFactoryResetInfo() {
        val message = """
            🏭 Factory Reset Informationen:
            
            📋 Was wird zurückgesetzt:
            • IP-Adresse → 192.168.127.254
            • Passwort → moxa (Standard)
            • Baudrate → 9600 bps
            • Alle Port-Konfigurationen
            
            🔧 Manuelle Reset-Methoden:
            1. Hardware Reset-Button:
               • 10 Sekunden gedrückt halten
               • Bei laufender Moxa
               
            2. 30-30-30 Reset:
               • 30s bei eingeschalteter Moxa
               • 30s beim Ausschalten
               • 30s bei ausgeschalteter Moxa
               
            3. Web-Interface:
               • Administration → Factory Default
               • Confirm → Reset
               
            ⚠️ Nach Reset: IP-Adresse ändern!
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Factory Reset Anleitung")
            .setMessage(message)
            .setPositiveButton("Verstanden", null)
            .show()
    }

    private fun backupMoxaConfiguration() {
        updateSystemStatus("⚠️ Backup-Funktion ist geplant...")

        val message = """
            💾 Moxa-Konfiguration Backup:
            
            🔧 Manuelles Backup:
            1. Web-Interface öffnen
            2. Administration → Import/Export
            3. "Export Configuration" klicken
            4. .cfg Datei speichern
            
            📋 Was wird gesichert:
            • Port-Konfigurationen
            • Netzwerk-Einstellungen
            • Benutzer-Konten
            • Alle System-Parameter
            
            🔄 Automatisches Backup:
            Diese Funktion wird in einem
            zukünftigen Update implementiert.
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Konfiguration Backup")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Web-Interface öffnen") { _, _ ->
                openMoxaWebInterface()
            }
            .show()
    }

    private fun restoreMoxaConfiguration() {
        updateSystemStatus("⚠️ Restore-Funktion ist geplant...")

        val message = """
            📥 Moxa-Konfiguration Restore:
            
            🔧 Manueller Restore:
            1. Web-Interface öffnen
            2. Administration → Import/Export
            3. "Import Configuration" klicken
            4. .cfg Datei auswählen
            5. "Upload" und "Apply"
            
            ⚠️ Wichtige Hinweise:
            • Moxa startet nach Import neu
            • IP-Adresse kann sich ändern
            • Alle aktuellen Einstellungen werden überschrieben
            
            🔄 Automatischer Restore:
            Diese Funktion wird in einem
            zukünftigen Update implementiert.
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Konfiguration Restore")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Web-Interface öffnen") { _, _ ->
                openMoxaWebInterface()
            }
            .show()
    }

    private fun openMoxaWebInterface() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("http://${settingsManager.getMoxaIpAddress()}")
            startActivity(intent)

            updateSystemStatus("Web-Interface in Browser geöffnet")

        } catch (e: Exception) {
            updateSystemStatus("Fehler beim Öffnen des Browsers: ${e.message}")

            // Fallback: URL in Zwischenablage kopieren
            try {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Moxa URL", "http://${settingsManager.getMoxaIpAddress()}")
                clipboard.setPrimaryClip(clip)

                showToast("URL in Zwischenablage kopiert: http://${settingsManager.getMoxaIpAddress()}")
            } catch (e2: Exception) {
                showToast("Browser-Fehler: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere Controller falls sich IP-Adresse geändert hat
        updateMoxaController()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loggingManager.logInfo("MoxaSettings", "Moxa-Einstellungen Fragment beendet")
    }
}