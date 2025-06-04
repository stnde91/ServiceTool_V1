package com.example.servicetool

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class LoggingManager private constructor(private val context: Context) {

    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    private val _systemStatus = MutableStateFlow<SystemStatus>(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val maxRecentLogs = 100
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun logInfo(tag: String, message: String, cellNumber: Int? = null) {
        log(LogLevel.INFO, tag, message, cellNumber)
    }

    fun logWarning(tag: String, message: String, cellNumber: Int? = null) {
        log(LogLevel.WARN, tag, message, cellNumber)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null, cellNumber: Int? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log(LogLevel.ERROR, tag, fullMessage, cellNumber)
        updateErrorCount()
    }

    fun logDebug(tag: String, message: String, cellNumber: Int? = null) {
        log(LogLevel.DEBUG, tag, message, cellNumber)
    }

    fun logCommunication(
        direction: CommunicationDirection,
        cellNumber: Int,
        command: String,
        response: String? = null,
        success: Boolean = true,
        durationMs: Long? = null
    ) {
        val message = buildString {
            append("Cell $cellNumber - $direction: $command")
            if (response != null) {
                append(" → Response: $response")
            }
            if (durationMs != null) {
                append(" (${durationMs}ms)")
            }
            if (!success) {
                append(" [FAILED]")
            }
        }

        log(
            if (success) LogLevel.INFO else LogLevel.ERROR,
            "COMM",
            message,
            cellNumber
        )

        updateCommunicationStats(success, durationMs)
    }

    private fun log(level: LogLevel, tag: String, message: String, cellNumber: Int? = null) {
        val timestamp = System.currentTimeMillis()
        val logEntry = LogEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            cellNumber = cellNumber
        )

        // Add to queue
        logQueue.offer(logEntry)

        // Update recent logs
        updateRecentLogs()

        // Write to Android Log
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message)
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }

        // Write to file if enabled
        if (isFileLoggingEnabled()) {
            writeToFile(logEntry)
        }
    }

    private fun updateRecentLogs() {
        // Fix: Convert ConcurrentLinkedQueue to List properly
        val allLogs = logQueue.toList()
        val recentList = if (allLogs.size > maxRecentLogs) {
            allLogs.takeLast(maxRecentLogs)
        } else {
            allLogs
        }
        _recentLogs.value = recentList.sortedByDescending { it.timestamp }
    }

    private fun updateErrorCount() {
        val currentStatus = _systemStatus.value
        _systemStatus.value = currentStatus.copy(
            errorCount = currentStatus.errorCount + 1,
            lastErrorTime = System.currentTimeMillis()
        )
    }

    private fun updateCommunicationStats(success: Boolean, durationMs: Long?) {
        val currentStatus = _systemStatus.value
        val newStats = if (success) {
            currentStatus.copy(
                successfulConnections = currentStatus.successfulConnections + 1,
                lastSuccessfulConnection = System.currentTimeMillis(),
                averageResponseTime = if (durationMs != null) {
                    calculateAverageResponseTime(currentStatus.averageResponseTime, durationMs)
                } else {
                    currentStatus.averageResponseTime
                }
            )
        } else {
            currentStatus.copy(
                failedConnections = currentStatus.failedConnections + 1,
                lastFailedConnection = System.currentTimeMillis()
            )
        }
        _systemStatus.value = newStats
    }

    private fun calculateAverageResponseTime(currentAverage: Long, newTime: Long): Long {
        // Simple moving average with weight towards recent measurements
        return ((currentAverage * 0.8) + (newTime * 0.2)).toLong()
    }

    private fun isFileLoggingEnabled(): Boolean {
        return try {
            SettingsManager.getInstance(context).getLogLevel() != SettingsManager.LogLevel.ERROR
        } catch (e: Exception) {
            true // Default to enabled if settings not available
        }
    }

    private fun writeToFile(logEntry: LogEntry) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val fileName = "servicetool_${fileDateFormat.format(Date(logEntry.timestamp))}.log"
            val logFile = File(logDir, fileName)

            FileWriter(logFile, true).use { writer ->
                val formattedEntry = formatLogEntry(logEntry)
                writer.appendLine(formattedEntry)
            }

            // Clean old log files (keep last 7 days)
            cleanOldLogFiles(logDir)

        } catch (e: Exception) {
            Log.e("LoggingManager", "Failed to write log to file", e)
        }
    }

    private fun formatLogEntry(entry: LogEntry): String {
        val timestamp = dateFormat.format(Date(entry.timestamp))
        val cellInfo = if (entry.cellNumber != null) " [Cell ${entry.cellNumber}]" else ""
        return "$timestamp ${entry.level.name.padEnd(5)} ${entry.tag}$cellInfo: ${entry.message}"
    }

    private fun cleanOldLogFiles(logDir: File) {
        val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 days ago

        logDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("servicetool_") && file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }

    fun exportLogs(startTime: Long? = null, endTime: Long? = null): List<LogEntry> {
        val allLogs = logQueue.toList()
        return if (startTime != null || endTime != null) {
            allLogs.filter { entry ->
                (startTime == null || entry.timestamp >= startTime) &&
                        (endTime == null || entry.timestamp <= endTime)
            }
        } else {
            allLogs
        }
    }

    fun clearLogs() {
        logQueue.clear()
        _recentLogs.value = emptyList()

        // Reset statistics
        _systemStatus.value = SystemStatus()
    }

    fun getLogFile(date: Date): File? {
        val fileName = "servicetool_${fileDateFormat.format(date)}.log"
        val logFile = File(File(context.filesDir, "logs"), fileName)
        return if (logFile.exists()) logFile else null
    }

    fun getAvailableLogDates(): List<Date> {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) return emptyList()

        return logDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("servicetool_") && it.name.endsWith(".log") }
            ?.mapNotNull { file ->
                try {
                    val dateString = file.name.removePrefix("servicetool_").removeSuffix(".log")
                    fileDateFormat.parse(dateString)
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedDescending()
            ?: emptyList()
    }

    companion object {
        @Volatile
        private var INSTANCE: LoggingManager? = null

        fun getInstance(context: Context): LoggingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LoggingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Data Classes
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val cellNumber: Int? = null
    )

    data class SystemStatus(
        val successfulConnections: Int = 0,
        val failedConnections: Int = 0,
        val errorCount: Int = 0,
        val lastSuccessfulConnection: Long = 0,
        val lastFailedConnection: Long = 0,
        val lastErrorTime: Long = 0,
        val averageResponseTime: Long = 0,
        val systemStartTime: Long = System.currentTimeMillis()
    ) {
        val uptime: Long get() = System.currentTimeMillis() - systemStartTime
        val successRate: Double get() = if (totalConnections > 0) {
            (successfulConnections.toDouble() / totalConnections) * 100
        } else 0.0
        val totalConnections: Int get() = successfulConnections + failedConnections
    }

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    enum class CommunicationDirection {
        SENDING, RECEIVING, BIDIRECTIONAL
    }
}