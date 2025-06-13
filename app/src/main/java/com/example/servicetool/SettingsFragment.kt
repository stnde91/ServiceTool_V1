package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager

    // Connection Settings
    private lateinit var editTextIpAddress: TextInputEditText
    private lateinit var editTextPort: TextInputEditText
    private lateinit var buttonConnect: Button
    private lateinit var textViewConnectionStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
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
        buttonConnect = view.findViewById(R.id.buttonConnect)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)
    }


    private fun setupListeners() {
        // IP Address change
        editTextIpAddress.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                settingsManager.setMoxaIpAddress(s.toString())
                updateConnectionStatus("Bereit für Verbindungstest")
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
                    updateConnectionStatus("Bereit für Verbindungstest")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Test Connection
        buttonConnect.setOnClickListener {
            testConnection()
        }
    }

    private fun loadCurrentSettings() {
        editTextIpAddress.setText(settingsManager.getMoxaIpAddress())
        editTextPort.setText(settingsManager.getMoxaPort().toString())
        updateConnectionStatus("Bereit für Verbindungstest")
    }

    private fun testConnection() {
        buttonConnect.isEnabled = false
        updateConnectionStatus("Teste Verbindung...")

        lifecycleScope.launch {
            try {
                CommunicationManager().apply {
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
                buttonConnect.isEnabled = true
            }
        }
    }

    private fun updateConnectionStatus(status: String) {
        textViewConnectionStatus.text = "Status: $status"
    }
}