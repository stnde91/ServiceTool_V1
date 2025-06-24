package com.example.servicetool

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager
    private lateinit var gitHubUpdateService: GitHubUpdateService

    // Connection Settings
    private lateinit var editTextIpAddress: TextInputEditText
    private lateinit var editTextPort: TextInputEditText
    private lateinit var buttonConnect: Button
    private lateinit var textViewConnectionStatus: TextView
    
    // App Update Views
    private lateinit var textViewAppVersion: TextView
    private lateinit var buttonCheckUpdate: MaterialButton
    private lateinit var cardAppUpdate: MaterialCardView
    private lateinit var textUpdateVersion: TextView
    private lateinit var textUpdateDescription: TextView
    private lateinit var buttonDownloadUpdate: MaterialButton
    private lateinit var buttonIgnoreUpdate: MaterialButton
    private lateinit var layoutUpdateProgress: View
    private lateinit var textUpdateStatus: TextView
    private lateinit var progressUpdate: LinearProgressIndicator

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
        gitHubUpdateService = GitHubUpdateService(requireContext())
    }

    private fun initializeViews(view: View) {
        // Connection Settings
        editTextIpAddress = view.findViewById(R.id.editTextIpAddress)
        editTextPort = view.findViewById(R.id.editTextPort)
        buttonConnect = view.findViewById(R.id.buttonConnect)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)
        
        // App Update Views
        textViewAppVersion = view.findViewById(R.id.textViewAppVersion)
        buttonCheckUpdate = view.findViewById(R.id.buttonCheckUpdate)
        cardAppUpdate = view.findViewById(R.id.cardAppUpdate)
        textUpdateVersion = view.findViewById(R.id.textUpdateVersion)
        textUpdateDescription = view.findViewById(R.id.textUpdateDescription)
        buttonDownloadUpdate = view.findViewById(R.id.buttonDownloadUpdate)
        buttonIgnoreUpdate = view.findViewById(R.id.buttonIgnoreUpdate)
        layoutUpdateProgress = view.findViewById(R.id.layoutUpdateProgress)
        textUpdateStatus = view.findViewById(R.id.textUpdateStatus)
        progressUpdate = view.findViewById(R.id.progressUpdate)
        
        // Set current app version
        updateAppVersionDisplay()
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
        
        // Update Check
        buttonCheckUpdate.setOnClickListener {
            checkForUpdates()
        }
        
        // Download Update
        buttonDownloadUpdate.setOnClickListener {
            downloadUpdate()
        }
        
        // Ignore Update
        buttonIgnoreUpdate.setOnClickListener {
            cardAppUpdate.isVisible = false
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
    
    private fun updateAppVersionDisplay() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            textViewAppVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            textViewAppVersion.text = "Version unbekannt"
        }
    }
    
    private fun checkForUpdates() {
        buttonCheckUpdate.isEnabled = false
        buttonCheckUpdate.text = "Prüfe..."
        
        lifecycleScope.launch {
            try {
                val updateInfo = gitHubUpdateService.checkForUpdates()
                
                if (updateInfo != null && updateInfo.isNewVersion) {
                    // Update verfügbar
                    showUpdateAvailable(updateInfo)
                } else {
                    // Kein Update verfügbar
                    loggingManager.logInfo("UPDATE", "Kein App-Update verfügbar")
                    // Optional: Toast oder Snackbar anzeigen
                }
            } catch (e: Exception) {
                loggingManager.logError("UPDATE", "Update-Check Fehler: ${e.message}", e)
            } finally {
                buttonCheckUpdate.isEnabled = true
                buttonCheckUpdate.text = "Update prüfen"
            }
        }
    }
    
    private fun showUpdateAvailable(updateInfo: GitHubUpdateService.UpdateInfo) {
        cardAppUpdate.isVisible = true
        textUpdateVersion.text = "Version ${updateInfo.version} ist verfügbar"
        textUpdateDescription.text = updateInfo.description
        layoutUpdateProgress.isVisible = false
        buttonDownloadUpdate.isEnabled = true
        
        // Store update info for download
        requireView().setTag(R.id.cardAppUpdate, updateInfo)
    }
    
    private fun downloadUpdate() {
        val updateInfo = requireView().getTag(R.id.cardAppUpdate) as? GitHubUpdateService.UpdateInfo
        if (updateInfo == null) {
            loggingManager.logWarning("UPDATE", "Update-Info nicht verfügbar")
            return
        }
        
        buttonDownloadUpdate.isEnabled = false
        layoutUpdateProgress.isVisible = true
        progressUpdate.isIndeterminate = true
        
        gitHubUpdateService.downloadAndInstallUpdate(updateInfo) { status ->
            requireActivity().runOnUiThread {
                textUpdateStatus.text = status
                
                if (status.contains("abgeschlossen") || status.contains("Installation")) {
                    progressUpdate.isIndeterminate = false
                    progressUpdate.progress = 100
                }
            }
        }
    }
}