package com.example.servicetool

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager
    private lateinit var updateManager: UpdateManager

    // Connection Settings
    private lateinit var editTextIpAddress: TextInputEditText
    private lateinit var editTextPort: TextInputEditText
    private lateinit var buttonConnect: Button
    private lateinit var textViewConnectionStatus: TextView
    
    // Update UI Components
    private lateinit var textViewUpdateStatus: TextView
    private lateinit var textViewUpdateAvailable: TextView
    private lateinit var textViewUpdateInfo: TextView
    private lateinit var progressBarUpdate: ProgressBar
    private lateinit var buttonCheckUpdate: Button
    private lateinit var buttonInstallUpdate: Button
    
    private var currentUpdateInfo: UpdateManager.UpdateInfo? = null

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
        updateManager = UpdateManager(requireContext())
    }

    private fun initializeViews(view: View) {
        // Connection Settings
        editTextIpAddress = view.findViewById(R.id.editTextIpAddress)
        editTextPort = view.findViewById(R.id.editTextPort)
        buttonConnect = view.findViewById(R.id.buttonConnect)
        textViewConnectionStatus = view.findViewById(R.id.textViewConnectionStatus)
        
        // Update Components
        textViewUpdateStatus = view.findViewById(R.id.textViewUpdateStatus)
        textViewUpdateAvailable = view.findViewById(R.id.textViewUpdateAvailable)
        textViewUpdateInfo = view.findViewById(R.id.textViewUpdateInfo)
        progressBarUpdate = view.findViewById(R.id.progressBarUpdate)
        buttonCheckUpdate = view.findViewById(R.id.buttonCheckUpdate)
        buttonInstallUpdate = view.findViewById(R.id.buttonInstallUpdate)
        
        // Set current version
        val currentVersion = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "1.1"
        }
        textViewUpdateStatus.text = "Aktuelle Version: $currentVersion"
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
        
        // Check for Updates
        buttonCheckUpdate.setOnClickListener {
            checkForUpdates()
        }
        
        // Install Update
        buttonInstallUpdate.setOnClickListener {
            currentUpdateInfo?.let { updateInfo ->
                showUpdateConfirmDialog(updateInfo)
            }
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
    
    private fun checkForUpdates() {
        buttonCheckUpdate.isEnabled = false
        progressBarUpdate.visibility = View.VISIBLE
        progressBarUpdate.isIndeterminate = true
        textViewUpdateInfo.text = "Suche nach Updates..."
        textViewUpdateInfo.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val updateInfo = updateManager.checkForUpdate()
                
                if (updateInfo != null) {
                    currentUpdateInfo = updateInfo
                    
                    if (updateInfo.isUpdateAvailable) {
                        textViewUpdateAvailable.visibility = View.VISIBLE
                        buttonInstallUpdate.visibility = View.VISIBLE
                        textViewUpdateInfo.text = "Version ${updateInfo.version} verfügbar\n" +
                                "Größe: ${formatFileSize(updateInfo.fileSize)}\n\n" +
                                "Änderungen:\n${updateInfo.changelog}"
                        
                        Snackbar.make(requireView(), 
                            "Update verfügbar: Version ${updateInfo.version}", 
                            Snackbar.LENGTH_LONG).show()
                    } else {
                        textViewUpdateAvailable.visibility = View.GONE
                        buttonInstallUpdate.visibility = View.GONE
                        textViewUpdateInfo.text = "Sie verwenden bereits die neueste Version"
                    }
                } else {
                    textViewUpdateInfo.text = "Update-Prüfung fehlgeschlagen. Bitte später erneut versuchen."
                }
            } catch (e: Exception) {
                textViewUpdateInfo.text = "Fehler bei der Update-Prüfung: ${e.message}"
                loggingManager.logError("UpdateCheck", "Fehler bei Update-Prüfung", e)
            } finally {
                buttonCheckUpdate.isEnabled = true
                progressBarUpdate.visibility = View.GONE
            }
        }
    }
    
    private fun showUpdateConfirmDialog(updateInfo: UpdateManager.UpdateInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("Update installieren?")
            .setMessage("Version ${updateInfo.version} wird heruntergeladen und installiert.\n\n" +
                    "Die App wird beendet und die Installation startet automatisch.")
            .setPositiveButton("Installieren") { _, _ ->
                downloadAndInstallUpdate(updateInfo)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
    
    private fun downloadAndInstallUpdate(updateInfo: UpdateManager.UpdateInfo) {
        buttonInstallUpdate.isEnabled = false
        buttonCheckUpdate.isEnabled = false
        progressBarUpdate.visibility = View.VISIBLE
        progressBarUpdate.isIndeterminate = false
        progressBarUpdate.progress = 0
        textViewUpdateInfo.text = "Download läuft..."
        
        lifecycleScope.launch {
            try {
                val success = updateManager.downloadAndInstallUpdate(updateInfo) { progress ->
                    progressBarUpdate.progress = progress
                    textViewUpdateInfo.text = "Download: $progress%"
                }
                
                if (success) {
                    textViewUpdateInfo.text = "Download abgeschlossen. Installation wird gestartet..."
                } else {
                    textViewUpdateInfo.text = "Download fehlgeschlagen"
                    Snackbar.make(requireView(), 
                        "Update-Download fehlgeschlagen", 
                        Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                textViewUpdateInfo.text = "Fehler beim Download: ${e.message}"
                loggingManager.logError("UpdateDownload", "Fehler beim Update-Download", e)
            } finally {
                buttonInstallUpdate.isEnabled = true
                buttonCheckUpdate.isEnabled = true
                progressBarUpdate.visibility = View.GONE
            }
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}