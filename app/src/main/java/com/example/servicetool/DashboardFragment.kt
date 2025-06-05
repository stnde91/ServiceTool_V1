package com.example.servicetool

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    // UI Components
    private lateinit var textCurrentTime: TextView
    private lateinit var cardSystemStatus: MaterialCardView
    private lateinit var iconFlintecStatus: ImageView
    private lateinit var textFlintecStatus: TextView
    private lateinit var textFlintecCount: TextView
    private lateinit var iconMoxaStatus: ImageView
    private lateinit var textMoxaStatus: TextView
    private lateinit var textMoxaPing: TextView
    private lateinit var recyclerViewQuickActions: RecyclerView

    // Statistics UI
    private lateinit var textSuccessfulConnections: TextView
    private lateinit var textErrorCount: TextView
    private lateinit var textSuccessRate: TextView
    private lateinit var textAverageResponseTime: TextView

    // Services
    private lateinit var settingsManager: SettingsManager
    private lateinit var loggingManager: LoggingManager

    // Quick Actions Adapter
    private lateinit var quickActionAdapter: QuickActionAdapter

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
        setupQuickActions()
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
        // Time
        textCurrentTime = view.findViewById(R.id.textCurrentTime)

        // System Status
        cardSystemStatus = view.findViewById(R.id.cardSystemStatus)
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

        // Quick Actions
        recyclerViewQuickActions = view.findViewById(R.id.recyclerViewQuickActions)
        recyclerViewQuickActions.layoutManager = LinearLayoutManager(context)
    }

    private fun setupQuickActions() {
        val quickActions = listOf(
            QuickAction(
                title = "Multi-Cell Übersicht",
                description = "Alle Zellen anzeigen und überwachen",
                iconRes = R.drawable.ic_weight_24,
                action = {
                    loggingManager.logInfo("Dashboard", "Navigation zu Multi-Cell Übersicht")
                    findNavController().navigate(R.id.nav_multicell_overview)
                }
            ),

            QuickAction(
                title = "System-Diagnose",
                description = "Verbindung und Hardware-Status prüfen",
                iconRes = R.drawable.ic_diagnostic_24,
                action = {
                    loggingManager.logInfo("Dashboard", "System-Diagnose gestartet")
                    runSystemDiagnosis()
                }
            ),

            // NEU: Moxa Einstellungen als prominente Quick Action
            QuickAction(
                title = "Moxa Einstellungen",
                description = "Device Server konfigurieren und steuern",
                iconRes = R.drawable.ic_digital_24,
                action = {
                    loggingManager.logInfo("Dashboard", "Navigation zu Moxa Einstellungen")
                    findNavController().navigate(R.id.nav_moxa_settings)
                }
            ),

            QuickAction(
                title = "Zellen Konfiguration",
                description = "Zelladressen ändern und konfigurieren",
                iconRes = R.drawable.ic_settings_24,
                action = {
                    loggingManager.logInfo("Dashboard", "Navigation zu Zellen Konfiguration")
                    findNavController().navigate(R.id.cellConfigurationFragment)
                }
            ),

            QuickAction(
                title = "App Einstellungen",
                description = "Anwendung konfigurieren und anpassen",
                iconRes = R.drawable.ic_settings_24,
                action = {
                    loggingManager.logInfo("Dashboard", "Navigation zu App Einstellungen")
                    findNavController().navigate(R.id.nav_settings)
                }
            )
        )

        quickActionAdapter = QuickActionAdapter(quickActions)
        recyclerViewQuickActions.adapter = quickActionAdapter
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
        textCurrentTime.text = timeFormat.format(Date())
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

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("System-Diagnose")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Moxa Einstellungen") { _, _ ->
                findNavController().navigate(R.id.nav_moxa_settings)
            }
            .show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
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