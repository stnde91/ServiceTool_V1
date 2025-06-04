package com.example.servicetool

import android.util.Log

/**
 * Zentrale Konfiguration für Multi-Cell Support
 */
object MultiCellConfig {

    // Definiere hier, welche Zellen tatsächlich verfügbar sind
    // Momentan nur Zelle 1 und 2, kann aber einfach erweitert werden
    val availableCells = listOf(1, 2)

    // Maximale Anzahl Zellen in der UI (8 in deinem Layout)
    const val maxDisplayCells = 8

    // Moxa-Konfiguration
    const val MOXA_IP = "192.168.50.3"
    const val MOXA_PORT = 4001

    // Timeouts
    const val CONNECTION_TIMEOUT = 5000
    const val READ_TIMEOUT = 3000
    const val LIVE_UPDATE_INTERVAL = 1000L

    /**
     * Gibt zurück, ob eine bestimmte Zelle konfiguriert ist
     */
    fun isCellAvailable(cellNumber: Int): Boolean {
        return availableCells.contains(cellNumber)
    }

    /**
     * Gibt die Anzahl der konfigurierten Zellen zurück
     */
    fun getAvailableCellCount(): Int {
        return availableCells.size
    }

    /**
     * Debug-Information über die Konfiguration
     */
    fun getConfigSummary(): String {
        return "Verfügbare Zellen: ${availableCells.joinToString(", ")} " +
                "(${availableCells.size}/$maxDisplayCells konfiguriert)"
    }

    /**
     * Erweiterte Konfiguration für zukünftige Zellen
     * Hier kannst du später spezielle Einstellungen pro Zelle definieren
     */
    data class CellConfig(
        val cellNumber: Int,
        val displayName: String,
        val isEnabled: Boolean = true,
        val customCommands: Map<FlintecRC3DMultiCellCommands.CommandType, ByteArray>? = null
    )

    // Wenn du später spezielle Konfigurationen brauchst:
    private val cellConfigs = mapOf(
        1 to CellConfig(1, "Hauptzelle A"),
        2 to CellConfig(2, "Nebenzelle B")
        // 3 to CellConfig(3, "Zelle C"), // Für später
        // 4 to CellConfig(4, "Zelle D"), // Für später
    )

    /**
     * Hole Konfiguration für eine bestimmte Zelle
     */
    fun getCellConfig(cellNumber: Int): CellConfig? {
        return cellConfigs[cellNumber]
    }

    /**
     * Einfache Erweiterung: Neue Zelle hinzufügen
     * Rufe diese Funktion auf, wenn du eine neue Zelle aktivieren möchtest
     */
    fun addCell(cellNumber: Int): Boolean {
        return if (cellNumber in 1..maxDisplayCells && !availableCells.contains(cellNumber)) {
            // Hier würdest du die Zelle zur availableCells Liste hinzufügen
            // Da availableCells momentan als val definiert ist, müsstest du das zu var ändern
            // oder eine andere Speichermethode verwenden (SharedPreferences, etc.)
            Log.i("MultiCellConfig", "Zelle $cellNumber würde hinzugefügt werden")
            true
        } else {
            false
        }
    }
}