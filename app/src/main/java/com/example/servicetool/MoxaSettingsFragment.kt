package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
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
    private lateinit var buttonSaveMoxaSettings: Button
    private lateinit var buttonTestConnection: Button
    private lateinit var buttonPingMoxa: Button
    private lateinit var textViewConnectionResult: TextView
    private lateinit var buttonMoxaStatus: Button
    private lateinit var buttonMoxaRestart: Button
    private lateinit var textViewMoxaStatus: TextView
    private lateinit var progressBarConnection: ProgressBar

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
        buttonSaveMoxaSettings = view.findViewById(R.id.buttonSaveMoxaSettings)
        buttonTestConnection = view.findViewById(R.id.buttonTestConnection)
        buttonPingMoxa = view.findViewById(R.id.buttonPingMoxa)
        textViewConnectionResult = view.findViewById(R.id.textViewConnectionResult)
        buttonMoxaStatus = view.findViewById(R.id.buttonMoxaStatus)
        buttonMoxaRestart = view.findViewById(R.id.buttonMoxaRestart)
        textViewMoxaStatus = view.findViewById(R.id.textViewMoxaStatus)
        progressBarConnection = view.findViewById(R.id.progressBarConnection)

        updateConnectionStatus("Bereit für Verbindungstest", false)
    }

    private fun setupListeners() {
        editTextMoxaIp.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setMoxaIpAddress(s.toString())
                updateTelnetController()
                updateConnectionStatus("Bereit für Verbindungstest", false)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editTextMoxaPort.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val port = s.toString().toIntOrNull()
                if (port != null && port in 1..65535) {
                    settingsManager.setMoxaPort(port)
                    updateConnectionStatus("Bereit für Verbindungstest", false)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        buttonSaveMoxaSettings.setOnClickListener { 
            saveSettings()
        }

        buttonTestConnection.setOnClickListener { 
            testMoxaConnection() 
        }

        buttonPingMoxa.setOnClickListener { 
            pingMoxa() 
        }

        buttonMoxaStatus.setOnClickListener { 
            getMoxaStatus() 
        }

        buttonMoxaRestart.setOnClickListener { 
            showTelnetRestartConfirmation() 
        }
    }

    private fun saveSettings() {
        updateConnectionStatus("Einstellungen werden gespeichert...", false)
        showToast("Moxa-Einstellungen gespeichert")
        loggingManager.logInfo("MoxaSettings", "Einstellungen gespeichert: ${settingsManager.getMoxaIpAddress()}:${settingsManager.getMoxaPort()}")
        updateConnectionStatus("Einstellungen gespeichert", true)
    }

    private fun testMoxaConnection() {
        buttonTestConnection.isEnabled = false
        updateConnectionStatus("Teste Verbindung...", false)
        showProgress(true)

        lifecycleScope.launch {
            try {
                val communicationManager = CommunicationManager()
                val success = communicationManager.connect(
                    settingsManager.getMoxaIpAddress(),
                    settingsManager.getMoxaPort()
                )
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        communicationManager.disconnect()
                        updateConnectionStatus("✅ Verbindung erfolgreich", true)
                        loggingManager.logInfo("MoxaSettings", "Verbindungstest erfolgreich")
                    } else {
                        updateConnectionStatus("❌ Verbindung fehlgeschlagen", false)
                        loggingManager.logError("MoxaSettings", "Verbindungstest fehlgeschlagen", null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateConnectionStatus("❌ Fehler: ${e.message}", false)
                    loggingManager.logError("MoxaSettings", "Verbindungstest-Fehler", e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    buttonTestConnection.isEnabled = true
                    showProgress(false)
                }
            }
        }
    }

    private fun pingMoxa() {
        buttonPingMoxa.isEnabled = false
        updateConnectionStatus("Ping wird ausgeführt...", false)

        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val success = telnetController.testConnection()
                val responseTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    if (success) {
                        updateConnectionStatus("✅ Ping erfolgreich (${responseTime}ms)", true)
                    } else {
                        updateConnectionStatus("❌ Ping fehlgeschlagen", false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateConnectionStatus("❌ Ping-Fehler: ${e.message}", false)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    buttonPingMoxa.isEnabled = true
                }
            }
        }
    }

    private fun getMoxaStatus() {
        buttonMoxaStatus.isEnabled = false
        updateMoxaStatus("Lade Device-Info...")

        lifecycleScope.launch {
            try {
                val portSettings = telnetController.getPortSettings("moxa")
                
                withContext(Dispatchers.Main) {
                    if (portSettings != null) {
                        val status = buildString {
                            appendLine("✅ NPort 5232 Device Server")
                            appendLine("📍 IP: ${settingsManager.getMoxaIpAddress()}")
                            appendLine("🔌 Port: ${settingsManager.getMoxaPort()}")
                            
                            portSettings[1]?.let { port1 ->
                                appendLine("🔧 Port 1: ${port1.baudRate} bps, ${port1.dataBits}${port1.parity.take(1)}${port1.stopBits}")
                            }
                            
                            portSettings[2]?.let { port2 ->
                                appendLine("🔧 Port 2: ${port2.baudRate} bps, ${port2.dataBits}${port2.parity.take(1)}${port2.stopBits}")
                            }
                        }
                        updateMoxaStatus(status)
                    } else {
                        updateMoxaStatus("❌ Device-Info konnte nicht geladen werden")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateMoxaStatus("❌ Fehler beim Laden der Device-Info: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    buttonMoxaStatus.isEnabled = true
                }
            }
        }
    }


    private fun showTelnetRestartConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Moxa Neustart")
            .setMessage("Möchten Sie die Moxa wirklich neu starten?\n\nIP: ${settingsManager.getMoxaIpAddress()}\nUngespeicherte Änderungen gehen dabei verloren.")
            .setPositiveButton("Ja, neu starten") { _, _ ->
                showToast("Neustart-Prozess wird gestartet...")
                loggingManager.logInfo("MoxaSettings", "Benutzer bestätigt Neustart für IP: ${settingsManager.getMoxaIpAddress()}")
                restartMoxaViaTelnet()
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                loggingManager.logInfo("MoxaSettings", "Neustart abgebrochen")
            }
            .show()
    }

    private fun restartMoxaViaTelnet() {
        setUIEnabled(false)
        updateMoxaStatus("Starte Moxa über Telnet neu...")
        loggingManager.logInfo("MoxaSettings", "Telnet-Neustart wird eingeleitet...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Stelle sicher, dass wir die aktuelle IP-Adresse verwenden
                withContext(Dispatchers.Main) {
                    updateTelnetController()
                }
                
                loggingManager.logInfo("MoxaSettings", "Versuche Telnet-Neustart für IP: ${settingsManager.getMoxaIpAddress()}")
                val success = telnetController.restart("moxa")

                withContext(Dispatchers.Main) {
                    if (success) {
                        updateMoxaStatus("✅ Telnet-Befehle gesendet. Warte auf Neustart...")
                        loggingManager.logInfo("MoxaSettings", "Telnet-Neustart-Befehle erfolgreich gesendet")
                        monitorRestartProgress()
                    } else {
                        updateMoxaStatus("❌ Telnet-Neustart fehlgeschlagen.")
                        loggingManager.logError("MoxaSettings", "Telnet-Neustart fehlgeschlagen", null)
                        setUIEnabled(true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateMoxaStatus("❌ Fehler beim Neustart: ${e.message}")
                    loggingManager.logError("MoxaSettings", "Telnet-Neustart Ausnahme", e)
                    setUIEnabled(true)
                }
            }
        }
    }

    private suspend fun monitorRestartProgress() {
        withContext(Dispatchers.Main) {
            updateMoxaStatus("Moxa startet neu... (Warte 5s)")
        }
        delay(5000)

        for (attempt in 1..15) {
            withContext(Dispatchers.Main) {
                updateMoxaStatus("🔍 Überwache Neustart... Versuch $attempt/15")
            }
            try {
                if (telnetController.testConnection()) {
                    withContext(Dispatchers.Main) {
                        updateMoxaStatus("✅ Moxa ist wieder online!")
                        updateConnectionStatus("Neustart erfolgreich", true)
                        setUIEnabled(true)
                    }
                    return
                }
            } catch (e: Exception) { /* Erwartet */ }

            delay(5000)
        }

        withContext(Dispatchers.Main) {
            updateMoxaStatus("⚠️ Moxa antwortet nicht.")
            updateConnectionStatus("Verbindung fehlgeschlagen", false)
            setUIEnabled(true)
        }
    }

    private fun loadCurrentSettings() {
        editTextMoxaIp.setText(settingsManager.getMoxaIpAddress())
        editTextMoxaPort.setText(settingsManager.getMoxaPort().toString())
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
            textViewConnectionResult.text = status
            textViewConnectionResult.setTextColor(requireContext().getColor(if (isSuccess) R.color.status_success_color else R.color.status_error_color))
        }
    }

    private fun updateMoxaStatus(status: String) {
        textViewMoxaStatus.text = status
    }

    private fun setUIEnabled(enabled: Boolean) {
        buttonSaveMoxaSettings.isEnabled = enabled
        buttonTestConnection.isEnabled = enabled
        buttonPingMoxa.isEnabled = enabled
        buttonMoxaStatus.isEnabled = enabled
        buttonMoxaRestart.isEnabled = enabled
        editTextMoxaIp.isEnabled = enabled
        editTextMoxaPort.isEnabled = enabled
    }

    private fun showProgress(show: Boolean) {
        progressBarConnection.visibility = if (show) View.VISIBLE else View.GONE
    }
}
