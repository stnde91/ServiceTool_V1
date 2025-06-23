package com.example.servicetool

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Observable Settings
    private val _moxaIpAddress = MutableStateFlow(getMoxaIpAddress())
    val moxaIpAddress: StateFlow<String> = _moxaIpAddress.asStateFlow()

    private val _moxaPort = MutableStateFlow(getMoxaPort())
    val moxaPort: StateFlow<Int> = _moxaPort.asStateFlow()

    private val _activeCellCount = MutableStateFlow(getActiveCellCount())
    val activeCellCount: StateFlow<Int> = _activeCellCount.asStateFlow()

    private val _autoRefreshEnabled = MutableStateFlow(getAutoRefreshEnabled())
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    private val _refreshInterval = MutableStateFlow(getRefreshInterval())
    val refreshInterval: StateFlow<Long> = _refreshInterval.asStateFlow()

    // Getters
    fun getMoxaIpAddress(): String = prefs.getString(KEY_MOXA_IP, DEFAULT_MOXA_IP) ?: DEFAULT_MOXA_IP

    fun getMoxaPort(): Int = prefs.getInt(KEY_MOXA_PORT, DEFAULT_MOXA_PORT)

    fun getActiveCellCount(): Int = prefs.getInt(KEY_ACTIVE_CELLS, DEFAULT_ACTIVE_CELLS)

    fun getAutoRefreshEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH, DEFAULT_AUTO_REFRESH)

    fun getRefreshInterval(): Long = prefs.getLong(KEY_REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL)

    fun getConnectionTimeout(): Int = prefs.getInt(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)

    fun getReadTimeout(): Int = prefs.getInt(KEY_READ_TIMEOUT, DEFAULT_READ_TIMEOUT)

    fun getRetryAttempts(): Int = prefs.getInt(KEY_RETRY_ATTEMPTS, DEFAULT_RETRY_ATTEMPTS)

    fun getLogLevel(): LogLevel = LogLevel.valueOf(
        prefs.getString(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL.name) ?: DEFAULT_LOG_LEVEL.name
    )

    // Setters
    fun setMoxaIpAddress(ip: String) {
        prefs.edit().putString(KEY_MOXA_IP, ip).apply()
        _moxaIpAddress.value = ip

        // Update MultiCellConfig if needed
        // MultiCellConfig.updateConnectionSettings(ip, getMoxaPort())
    }

    fun setMoxaPort(port: Int) {
        prefs.edit().putInt(KEY_MOXA_PORT, port).apply()
        _moxaPort.value = port
    }

    fun setActiveCellCount(count: Int) {
        if (count in 1..MultiCellConfig.maxDisplayCells) {
            prefs.edit().putInt(KEY_ACTIVE_CELLS, count).apply()
            _activeCellCount.value = count
            MultiCellConfig.updateAvailableCells(count)
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
        _autoRefreshEnabled.value = enabled
    }

    fun setRefreshInterval(intervalMs: Long) {
        prefs.edit().putLong(KEY_REFRESH_INTERVAL, intervalMs).apply()
        _refreshInterval.value = intervalMs
    }

    fun setConnectionTimeout(timeoutMs: Int) {
        prefs.edit().putInt(KEY_CONNECTION_TIMEOUT, timeoutMs).apply()
    }

    fun setReadTimeout(timeoutMs: Int) {
        prefs.edit().putInt(KEY_READ_TIMEOUT, timeoutMs).apply()
    }

    fun setRetryAttempts(attempts: Int) {
        prefs.edit().putInt(KEY_RETRY_ATTEMPTS, attempts).apply()
    }

    fun setLogLevel(level: LogLevel) {
        prefs.edit().putString(KEY_LOG_LEVEL, level.name).apply()
    }

    // Advanced Settings
    fun getCellConfiguration(cellNumber: Int): CellConfiguration {
        val prefix = "cell_${cellNumber}_"
        return CellConfiguration(
            cellNumber = cellNumber,
            enabled = prefs.getBoolean(prefix + "enabled", true),
            displayName = prefs.getString(prefix + "name", "Zelle $cellNumber") ?: "Zelle $cellNumber",
            calibrationFactor = prefs.getFloat(prefix + "calibration", 1.0f),
            tareValue = prefs.getFloat(prefix + "tare", 0.0f),
            unitType = WeightUnit.valueOf(
                prefs.getString(prefix + "unit", WeightUnit.KILOGRAM.name) ?: WeightUnit.KILOGRAM.name
            )
        )
    }

    fun setCellConfiguration(config: CellConfiguration) {
        val prefix = "cell_${config.cellNumber}_"
        prefs.edit()
            .putBoolean(prefix + "enabled", config.enabled)
            .putString(prefix + "name", config.displayName)
            .putFloat(prefix + "calibration", config.calibrationFactor)
            .putFloat(prefix + "tare", config.tareValue)
            .putString(prefix + "unit", config.unitType.name)
            .apply()
    }

    // Export/Import Settings - Fixed type mismatch
    fun exportSettings(): Map<String, Any> {
        val allPrefs = prefs.all ?: emptyMap<String, Any?>()
        // Filter out null values and ensure correct types
        return allPrefs.filterValues { it != null }.mapValues { it.value!! }
    }

    fun importSettings(settings: Map<String, Any>) {
        val editor = prefs.edit()
        settings.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                // Handle other types if needed
                else -> {
                    // Skip unsupported types or convert to string
                    editor.putString(key, value.toString())
                }
            }
        }
        editor.apply()

        // Refresh StateFlows
        _moxaIpAddress.value = getMoxaIpAddress()
        _moxaPort.value = getMoxaPort()
        _activeCellCount.value = getActiveCellCount()
        _autoRefreshEnabled.value = getAutoRefreshEnabled()
        _refreshInterval.value = getRefreshInterval()
    }

    // Reset to defaults
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _moxaIpAddress.value = DEFAULT_MOXA_IP
        _moxaPort.value = DEFAULT_MOXA_PORT
        _activeCellCount.value = DEFAULT_ACTIVE_CELLS
        _autoRefreshEnabled.value = DEFAULT_AUTO_REFRESH
        _refreshInterval.value = DEFAULT_REFRESH_INTERVAL
    }

    companion object {
        private const val PREFS_NAME = "servicetool_settings"

        // Keys
        private const val KEY_MOXA_IP = "moxa_ip_address"
        private const val KEY_MOXA_PORT = "moxa_port"
        private const val KEY_ACTIVE_CELLS = "active_cell_count"
        private const val KEY_AUTO_REFRESH = "auto_refresh_enabled"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval_ms"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout_ms"
        private const val KEY_READ_TIMEOUT = "read_timeout_ms"
        private const val KEY_RETRY_ATTEMPTS = "retry_attempts"
        private const val KEY_LOG_LEVEL = "log_level"

        // Defaults
        private const val DEFAULT_MOXA_IP = "192.168.50.3"
        private const val DEFAULT_MOXA_PORT = 4001
        private const val DEFAULT_ACTIVE_CELLS = 1
        private const val DEFAULT_AUTO_REFRESH = false
        private const val DEFAULT_REFRESH_INTERVAL = 2000L
        private const val DEFAULT_CONNECTION_TIMEOUT = 5000
        private const val DEFAULT_READ_TIMEOUT = 3000
        private const val DEFAULT_RETRY_ATTEMPTS = 3
        private val DEFAULT_LOG_LEVEL = LogLevel.INFO

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Data Classes
    data class CellConfiguration(
        val cellNumber: Int,
        val enabled: Boolean = true,
        val displayName: String,
        val calibrationFactor: Float = 1.0f,
        val tareValue: Float = 0.0f,
        val unitType: WeightUnit = WeightUnit.KILOGRAM
    )

    enum class WeightUnit(val symbol: String, val factor: Float) {
        GRAM("g", 1000f),
        KILOGRAM("kg", 1f),
        POUND("lb", 2.20462f),
        OUNCE("oz", 35.274f)
    }

    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}