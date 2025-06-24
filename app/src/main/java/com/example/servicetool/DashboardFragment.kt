package com.example.servicetool

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    // UI Components  
    private lateinit var iconFlintecStatus: ImageView
    private lateinit var textFlintecStatus: TextView
    private lateinit var textFlintecCount: TextView
    private lateinit var iconMoxaStatus: ImageView
    private lateinit var textMoxaStatus: TextView
    private lateinit var textMoxaPing: TextView

    // Statistics UI
    private lateinit var textSuccessfulConnections: TextView
    private lateinit var textErrorCount: TextView
    private lateinit var textSuccessRate: TextView
    private lateinit var textAverageResponseTime: TextView
    
    // Update Status UI
    private lateinit var cardUpdateStatus: View
    private lateinit var iconUpdateStatus: ImageView
    private lateinit var textUpdateStatus: TextView
    private lateinit var textUpdateDetails: TextView
    private lateinit var buttonCheckUpdates: MaterialButton
    
    // Communication Log UI
    private lateinit var recyclerViewCommLog: RecyclerView
    private lateinit var buttonClearLog: MaterialButton
    private lateinit var commLogAdapter: CommunicationLogAdapter

    // Services
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager


    // Jobs
    private var statusUpdateJob: Job? = null
    private var timeUpdateJob: Job? = null

    // Time formatter
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeServices()
        initializeViews(view)
        startUpdates()

        // Initial update
        updateSystemStatus()
    }

    private fun initializeServices() {
        settingsManager = SettingsManager.getInstance(requireContext())
        loggingManager = LoggingManager.getInstance(requireContext())

        loggingManager.logInfo("Dashboard", "Dashboard gestartet mit Moxa-Integration")
    }

    private fun initializeViews(view: View) {
        // System Status
        iconFlintecStatus = view.findViewById(R.id.iconFlintecStatus)
        textFlintecStatus = view.findViewById(R.id.textFlintecStatus)
        textFlintecCount = view.findViewById(R.id.textFlintecCount)
        iconMoxaStatus = view.findViewById(R.id.iconMoxaStatus)
        textMoxaStatus = view.findViewById(R.id.textMoxaStatus)
        textMoxaPing = view.findViewById(R.id.textMoxaPing)

        // Statistics
        textSuccessfulConnections = view.findViewById(R.id.textSuccessfulConnections)
        textErrorCount = view.findViewById(R.id.textErrorCount)
        textSuccessRate = view.findViewById(R.id.textSuccessRate)
        textAverageResponseTime = view.findViewById(R.id.textAverageResponseTime)
        
        // Update Status
        cardUpdateStatus = view.findViewById(R.id.cardUpdateStatus)
        iconUpdateStatus = view.findViewById(R.id.iconUpdateStatus)
        textUpdateStatus = view.findViewById(R.id.textUpdateStatus)
        textUpdateDetails = view.findViewById(R.id.textUpdateDetails)
        buttonCheckUpdates = view.findViewById(R.id.buttonCheckUpdates)
        
        // Communication Log
        recyclerViewCommLog = view.findViewById(R.id.recyclerViewCommLog)
        buttonClearLog = view.findViewById(R.id.buttonClearLog)
        
        setupCommunicationLog()
        setupUpdateChecker()

    }
    
    private fun setupCommunicationLog() {
        commLogAdapter = CommunicationLogAdapter()
        recyclerViewCommLog.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commLogAdapter
        }
        
        buttonClearLog.setOnClickListener {
            commLogAdapter.clearLog()
            loggingManager.logInfo("Dashboard", "Kommunikations-Log geleert")
        }
        
        // Add some demo entries
        addLogEntry("→", "RESET", "SUCCESS", 45)
        addLogEntry("←", "02 01 03", "SUCCESS", null)
        addLogEntry("→", "READ_WEIGHT", "SUCCESS", 23)
        addLogEntry("←", "02 12.34 kg 03", "SUCCESS", null)
        addLogEntry("→", "CONFIG_ADDR", "TIMEOUT", 5000)
    }
    
    private fun setupUpdateChecker() {
        // Set current version
        textUpdateDetails.text = "Aktuell: v0.105"
        
        buttonCheckUpdates.setOnClickListener {
            checkForUpdates()
        }
        
        // Auto-check on startup
        checkForUpdates()
    }
    
    private fun checkForUpdates() {
        buttonCheckUpdates.isEnabled = false
        textUpdateStatus.text = "Prüfe auf Updates..."
        iconUpdateStatus.setImageResource(R.drawable.ic_refresh_24)
        
        lifecycleScope.launch {
            try {
                // Use existing GitHubUpdateService
                val updateService = GitHubUpdateService(requireContext())
                val latestRelease = updateService.checkForUpdates()
                
                withContext(Dispatchers.Main) {
                    if (latestRelease != null) {
                        val currentVersion = requireContext().packageManager
                            .getPackageInfo(requireContext().packageName, 0).versionName
                        
                        if (latestRelease.isNewVersion) {
                            // Update available
                            cardUpdateStatus.visibility = View.VISIBLE
                            textUpdateStatus.text = "Update verfügbar!"
                            textUpdateDetails.text = "Aktuell: v$currentVersion → Neu: v${latestRelease.version}"
                            iconUpdateStatus.setImageResource(R.drawable.ic_refresh_24)
                            iconUpdateStatus.setColorFilter(requireContext().getColor(R.color.status_success_color))
                        } else {
                            // Up to date
                            textUpdateStatus.text = "App ist aktuell"
                            textUpdateDetails.text = "Aktuell: v$currentVersion"
                            iconUpdateStatus.setImageResource(R.drawable.ic_status_success)
                            iconUpdateStatus.setColorFilter(requireContext().getColor(R.color.status_success_color))
                            
                            // Hide card after delay if up to date
                            delay(3000)
                            cardUpdateStatus.visibility = View.GONE
                        }
                    } else {
                        textUpdateStatus.text = "Update-Prüfung fehlgeschlagen"
                        textUpdateDetails.text = "Netzwerkfehler oder GitHub nicht erreichbar"
                        iconUpdateStatus.setImageResource(R.drawable.ic_status_error)
                        iconUpdateStatus.setColorFilter(requireContext().getColor(R.color.status_error_color))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textUpdateStatus.text = "Update-Prüfung fehlgeschlagen"
                    textUpdateDetails.text = "Fehler: ${e.message}"
                    iconUpdateStatus.setImageResource(R.drawable.ic_status_error)
                    iconUpdateStatus.setColorFilter(requireContext().getColor(R.color.status_error_color))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    buttonCheckUpdates.isEnabled = true
                }
            }
        }
    }
    
    private fun addLogEntry(direction: String, message: String, status: String, duration: Long?) {
        val entry = CommunicationLogEntry(
            timestamp = System.currentTimeMillis(),
            direction = direction,
            message = message,
            status = status,
            duration = duration
        )
        commLogAdapter.addLogEntry(entry)
    }

    private fun startUpdates() {
        // Time update every second
        timeUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateCurrentTime()
                delay(1000)
            }
        }

        // Status update every 10 seconds
        statusUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateSystemStatus()
                delay(10000)
            }
        }
    }

    private fun updateCurrentTime() {
        // Time display removed from minimalist design
    }

    private fun updateSystemStatus() {
        lifecycleScope.launch {
            try {
                // Get system status
                val systemStatus = getSystemStatus()
                val moxaStatus = getMoxaStatus()

                // Update UI
                updateFlintecStatus(systemStatus)
                updateMoxaStatus(moxaStatus)
                updateStatistics()

            } catch (e: Exception) {
                Log.e("Dashboard", "Fehler beim Status-Update: ${e.message}", e)
                loggingManager.logError("Dashboard", "Status-Update fehlgeschlagen", e)
            }
        }
    }

    private suspend fun getSystemStatus(): SystemStatus {
        return withContext(Dispatchers.IO) {
            try {
                val activeCells = MultiCellConfig.availableCells
                var responsiveCells = 0
                var totalResponseTime = 0L

                // Quick ping test for each configured cell
                for (cellNumber in activeCells) {
                    val startTime = System.currentTimeMillis()
                    val success = testCellConnection(cellNumber)
                    val responseTime = System.currentTimeMillis() - startTime

                    if (success) {
                        responsiveCells++
                        totalResponseTime += responseTime
                    }
                }

                val averageResponseTime = if (responsiveCells > 0) {
                    totalResponseTime / responsiveCells
                } else {
                    0L
                }

                SystemStatus(
                    totalCells = activeCells.size,
                    responsiveCells = responsiveCells,
                    averageResponseTime = averageResponseTime,
                    lastUpdate = System.currentTimeMillis()
                )

            } catch (e: Exception) {
                SystemStatus(
                    totalCells = MultiCellConfig.availableCells.size,
                    responsiveCells = 0,
                    averageResponseTime = 0L,
                    lastUpdate = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }
    }

    private suspend fun getMoxaStatus(): MoxaStatus {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                // NEU: Verwende Moxa5232Controller für besseren Test
                val moxaController = Moxa5232Controller(settingsManager.getMoxaIpAddress())
                val success = moxaController.testConnection()
                val responseTime = System.currentTimeMillis() - startTime

                MoxaStatus(
                    connected = success,
                    ipAddress = settingsManager.getMoxaIpAddress(),
                    port = settingsManager.getMoxaPort(),
                    responseTime = responseTime,
                    lastUpdate = System.currentTimeMillis()
                )

            } catch (e: Exception) {
                MoxaStatus(
                    connected = false,
                    ipAddress = settingsManager.getMoxaIpAddress(),
                    port = settingsManager.getMoxaPort(),
                    responseTime = 0L,
                    lastUpdate = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }
    }

    private suspend fun testCellConnection(cellNumber: Int): Boolean {
        return try {
            val communicationManager = CommunicationManager()
            val success = communicationManager.connect(
                settingsManager.getMoxaIpAddress(),
                settingsManager.getMoxaPort()
            )

            if (success) {
                // Try to send a simple command
                val command = FlintecRC3DMultiCellCommands.getCommandForCell(
                    cellNumber,
                    FlintecRC3DMultiCellCommands.CommandType.COUNTS
                )
                val response = communicationManager.sendCommand(String(command, Charsets.US_ASCII))
                communicationManager.disconnect()

                response != null
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun updateFlintecStatus(status: SystemStatus) {
        val (statusText, statusColor, iconRes) = when {
            status.error != null -> {
                Triple("Fehler: ${status.error}", R.color.status_error_color, R.drawable.ic_status_error)
            }
            status.responsiveCells == 0 -> {
                Triple("Alle Zellen offline", R.color.status_error_color, R.drawable.ic_status_error)
            }
            status.responsiveCells == status.totalCells -> {
                Triple("Alle Zellen online", R.color.status_success_color, R.drawable.ic_status_success)
            }
            else -> {
                Triple("${status.responsiveCells}/${status.totalCells} Zellen online", R.color.status_pending_color, R.drawable.ic_status_pending)
            }
        }

        textFlintecStatus.text = statusText
        textFlintecStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))
        textFlintecCount.text = "${status.responsiveCells}/${status.totalCells}"
        textFlintecCount.setTextColor(ContextCompat.getColor(requireContext(), statusColor))
        iconFlintecStatus.setImageResource(iconRes)
    }

    private fun updateMoxaStatus(status: MoxaStatus) {
        if (status.connected) {
            val ping = "${status.responseTime}ms"
            val pingColor = when {
                status.responseTime < 100 -> R.color.status_success_color
                status.responseTime < 500 -> R.color.status_pending_color
                else -> R.color.status_error_color
            }

            textMoxaStatus.text = "NPort 5232 - ${status.ipAddress}:${status.port}"
            textMoxaStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success_color))
            textMoxaPing.text = ping
            textMoxaPing.setTextColor(ContextCompat.getColor(requireContext(), pingColor))
            iconMoxaStatus.setImageResource(R.drawable.ic_status_success)
        } else {
            val errorMsg = status.error?.let { " ($it)" } ?: ""
            textMoxaStatus.text = "Moxa nicht erreichbar$errorMsg"
            textMoxaStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_error_color))
            textMoxaPing.text = "---"
            textMoxaPing.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_error_color))
            iconMoxaStatus.setImageResource(R.drawable.ic_status_error)
        }
    }

    private fun updateStatistics() {
        lifecycleScope.launch {
            try {
                // Get statistics from LoggingManager
                loggingManager.systemStatus.value.let { stats ->
                    textSuccessfulConnections.text = stats.successfulConnections.toString()
                    textErrorCount.text = stats.errorCount.toString()

                    val successRate = if (stats.totalConnections > 0) {
                        String.format("%.1f%%", stats.successRate)
                    } else {
                        "100%"
                    }
                    textSuccessRate.text = successRate

                    val avgResponseTime = if (stats.averageResponseTime > 0) {
                        "${stats.averageResponseTime}ms"
                    } else {
                        "0ms"
                    }
                    textAverageResponseTime.text = avgResponseTime
                }

            } catch (e: Exception) {
                Log.e("Dashboard", "Fehler beim Statistik-Update: ${e.message}", e)
            }
        }
    }

    private fun runSystemDiagnosis() {
        lifecycleScope.launch {
            try {
                loggingManager.logInfo("Dashboard", "Erweiterte System-Diagnose läuft...")

                // NEU: Erweiterte Diagnose mit Moxa-Test
                val communicationManager = CommunicationManager()
                val diagnostic = communicationManager.performConnectionDiagnostic(
                    settingsManager.getMoxaIpAddress(),
                    settingsManager.getMoxaPort()
                )

                // Moxa-Hardware-Test
                val moxaController = Moxa5232Controller(settingsManager.getMoxaIpAddress())
                val moxaReachable = moxaController.testConnection()

                // Ergebnisse anzeigen
                showDiagnosticResults(diagnostic, moxaReachable)

                // Force immediate status update
                updateSystemStatus()

                // Log current system state
                val activeCells = MultiCellConfig.availableCells
                loggingManager.logInfo("Dashboard", "Diagnose abgeschlossen - ${activeCells.size} Zellen konfiguriert, Moxa: ${if (moxaReachable) "OK" else "Fehler"}")

            } catch (e: Exception) {
                loggingManager.logError("Dashboard", "System-Diagnose fehlgeschlagen", e)
                showToast("Diagnose fehlgeschlagen: ${e.message}")
            }
        }
    }

    private fun showDiagnosticResults(diagnostic: CommunicationManager.ConnectionDiagnostic, moxaReachable: Boolean) {
        val message = buildString {
            appendLine("🔍 System-Diagnose Ergebnisse:")
            appendLine()
            appendLine("📡 Netzwerk: ${if (diagnostic.networkReachable) "✅ Erreichbar" else "❌ Nicht erreichbar"}")
            appendLine("🌐 Moxa Web-Interface: ${if (moxaReachable) "✅ Erreichbar" else "❌ Nicht erreichbar"}")
            appendLine("📞 Moxa Datenport: ${if (diagnostic.moxaReachable) "✅ Verbunden" else "❌ Nicht verbunden"}")
            appendLine("⚖️ Zell-Kommunikation: ${if (diagnostic.cellCommunication) "✅ Funktioniert" else "❌ Fehlgeschlagen"}")

            if (diagnostic.detectedBaudrate != null) {
                appendLine("🔧 Erkannte Baudrate: ${diagnostic.detectedBaudrate} bps")
            }

            if (diagnostic.error != null) {
                appendLine("❌ Fehler: ${diagnostic.error}")
            }

            appendLine()
            appendLine("💡 Empfehlung:")
            when {
                !diagnostic.networkReachable -> appendLine("• Netzwerkverbindung prüfen")
                !moxaReachable -> appendLine("• Moxa IP-Adresse in Einstellungen prüfen")
                !diagnostic.moxaReachable -> appendLine("• Moxa Port-Konfiguration prüfen")
                !diagnostic.cellCommunication -> appendLine("• Baudrate in Moxa-Einstellungen anpassen")
                else -> appendLine("• System funktioniert einwandfrei")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("System-Diagnose")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Moxa Einstellungen") { _, _ ->
                findNavController().navigate(R.id.nav_moxa_settings)
            }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusUpdateJob?.cancel()
        timeUpdateJob?.cancel()
        loggingManager.logInfo("Dashboard", "Dashboard beendet")
    }

    // Data Classes
    data class SystemStatus(
        val totalCells: Int,
        val responsiveCells: Int,
        val averageResponseTime: Long,
        val lastUpdate: Long,
        val error: String? = null
    )

    data class MoxaStatus(
        val connected: Boolean,
        val ipAddress: String,
        val port: Int,
        val responseTime: Long,
        val lastUpdate: Long,
        val error: String? = null
    )

    data class QuickAction(
        val title: String,
        val description: String,
        val iconRes: Int,
        val action: () -> Unit
    )
}
