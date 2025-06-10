package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    private lateinit var switchAutoDetectBaudrate: Switch
    private lateinit var buttonBackupConfig: Button
    private lateinit var buttonRestoreConfig: Button

    // Services
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager

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

        disableHttpControls()

        lifecycleScope.launch {
            delay(500)
            testMoxaConnectionViaTelnet()
        }
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())
        loggingManager.logInfo("MoxaSettings", "Moxa-Einstellungen Fragment gestartet (Telnet-Modus)")
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
        switchAutoDetectBaudrate = view.findViewById(R.id.switchAutoDetectBaudrate)
        buttonBackupConfig = view.findViewById(R.id.buttonBackupConfig)
        buttonRestoreConfig = view.findViewById(R.id.buttonRestoreConfig)

        updateConnectionStatus("Nicht getestet", false)
        updateSystemStatus("Bereit")
    }

    private fun disableHttpControls() {
        spinnerPort1Baudrate.isEnabled = false
        spinnerPort2Baudrate.isEnabled = false
        buttonApplyPort1.isEnabled = false
        buttonApplyPort2.isEnabled = false
        switchAutoDetectBaudrate.isEnabled = false
        textViewPort1Status.text = "Port 1: Konfiguration via Web-UI"
        textViewPort2Status.text = "Port 2: Konfiguration via Web-UI"
    }

    private fun setupListeners() {
        editTextMoxaIp.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setMoxaIpAddress(s.toString())
                updateConnectionStatus("Nicht getestet", false)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        buttonTestMoxaConnection.setOnClickListener { testMoxaConnectionViaTelnet() }

        buttonRestartMoxa.setOnClickListener { showTelnetRestartConfirmation() }
    }

    private fun testMoxaConnectionViaTelnet() {
        buttonTestMoxaConnection.isEnabled = false
        buttonRestartMoxa.isEnabled = false
        updateConnectionStatus("Teste Telnet Port (23)...", false)
        showProgress(true)

        lifecycleScope.launch {
            try {
                val moxaIp = settingsManager.getMoxaIpAddress()
                val telnetController = MoxaTelnetController(moxaIp)
                val isReachable = telnetController.testConnection()

                withContext(Dispatchers.Main) {
                    if (isReachable) {
                        updateConnectionStatus("✅ Telnet Port erreichbar", true)
                        textViewMoxaModel.text = "Moxa NPort 5232 (Telnet)"
                        loggingManager.logInfo("MoxaSettings", "Moxa-Verbindungstest (Telnet) erfolgreich")
                        buttonTestMoxaConnection.isEnabled = true
                        buttonRestartMoxa.isEnabled = true
                    } else {
                        updateConnectionStatus("❌ Telnet Port nicht erreichbar", false)
                        textViewMoxaModel.text = "Unbekannt"
                        loggingManager.logWarning("MoxaSettings", "Moxa-Verbindungstest (Telnet) fehlgeschlagen")
                        buttonTestMoxaConnection.isEnabled = true
                        buttonRestartMoxa.isEnabled = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateConnectionStatus("❌ Verbindungsfehler: ${e.message}", false)
                    loggingManager.logError("MoxaSettings", "Moxa-Verbindungsfehler (Telnet)", e)
                    buttonTestMoxaConnection.isEnabled = true
                    buttonRestartMoxa.isEnabled = false
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showProgress(false)
                }
            }
        }
    }

    private fun showTelnetRestartConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Moxa Neustart via Telnet")
            .setMessage("Möchten Sie die Moxa wirklich neu starten?\n\n⚠️ Dies geschieht über Telnet und trennt alle Verbindungen für ca. 45 Sekunden.")
            .setPositiveButton("Ja, neu starten") { _, _ ->
                showToast("Neustart-Prozess wird gestartet...")
                restartMoxaViaTelnet()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun restartMoxaViaTelnet() {
        buttonTestMoxaConnection.isEnabled = false
        buttonRestartMoxa.isEnabled = false
        updateSystemStatus("Starte Moxa über Telnet neu...")
        loggingManager.logInfo("MoxaSettings", "Telnet-Neustart wird eingeleitet...")

        lifecycleScope.launch(Dispatchers.IO) {
            var success: Boolean
            try {
                val moxaIp = settingsManager.getMoxaIpAddress()
                val password = editTextPassword.text.toString().ifEmpty { "moxa" }
                val telnetController = MoxaTelnetController(moxaIp)

                Log.i("MoxaSettings", "Führe telnetController.restart aus im Thread: ${Thread.currentThread().name}")

                success = telnetController.restart(password)

                withContext(Dispatchers.Main) {
                    if (success) {
                        updateSystemStatus("✅ Telnet-Befehle gesendet. Warte auf Neustart...")
                        loggingManager.logInfo("MoxaSettings", "Moxa Telnet-Neustart erfolgreich eingeleitet")
                        monitorRestartProgress()
                    } else {
                        updateSystemStatus("❌ Telnet-Neustart fehlgeschlagen. Siehe Logs.")
                        loggingManager.logError("MoxaSettings", "Moxa Telnet-Neustart fehlgeschlagen")
                        buttonTestMoxaConnection.isEnabled = true
                        buttonRestartMoxa.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MoxaSettings", "Kritischer Fehler im Telnet-Neustart-Prozess.", e)
                withContext(Dispatchers.Main) {
                    updateSystemStatus("❌ Neustart-Fehler: ${e.message}")
                    loggingManager.logError("MoxaSettings", "Fehler im Telnet-Neustart-Prozess", e)
                    buttonTestMoxaConnection.isEnabled = true
                    buttonRestartMoxa.isEnabled = true
                }
            }
        }
    }

    private suspend fun monitorRestartProgress() {
        withContext(Dispatchers.Main) {
            // GEÄNDERT: Wartezeit auf 5 Sekunden reduziert.
            updateSystemStatus("Moxa fährt herunter... (Warte 5s)")
        }

        // GEÄNDERT: Initiale Wartezeit auf 5 Sekunden reduziert.
        delay(5000)

        for (attempt in 1..15) {
            withContext(Dispatchers.Main) {
                updateSystemStatus("🔍 Überwache Neustart... Versuch $attempt/15")
            }
            try {
                val telnetController = MoxaTelnetController(settingsManager.getMoxaIpAddress())
                if (telnetController.testConnection()) {
                    withContext(Dispatchers.Main) {
                        updateSystemStatus("✅ Moxa ist nach Neustart wieder online!")
                        updateConnectionStatus("Bitte Verbindung erneut testen", false)
                        buttonTestMoxaConnection.isEnabled = true
                        buttonRestartMoxa.isEnabled = false // Wichtig: Deaktiviert lassen
                    }
                    return
                }
            } catch (e: Exception) { /* Erwartet während Neustart */ }

            delay(5000)
        }

        withContext(Dispatchers.Main) {
            updateSystemStatus("⚠️ Moxa antwortet nicht. Bitte manuell prüfen.")
            updateConnectionStatus("Verbindung fehlgeschlagen", false)
            buttonTestMoxaConnection.isEnabled = true
            buttonRestartMoxa.isEnabled = false
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
        textViewConnectionStatus.text = "Verbindung: $status"
        if(context != null) {
            textViewConnectionStatus.setTextColor(requireContext().getColor(if (isSuccess) R.color.status_success_color else R.color.status_error_color))
        }
    }

    private fun updateSystemStatus(status: String) {
        textViewSystemStatus.text = "System: $status"
    }

    private fun showProgress(show: Boolean) {
        progressBarConnection.visibility = if (show) View.VISIBLE else View.GONE
    }
}
