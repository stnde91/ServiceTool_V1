package com.example.servicetool

import android.util.Log

/**
 * Zentrale Konfiguration für Multi-Cell Support
 */
object MultiCellConfig {

    // Definiere hier, welche Zellen tatsächlich verfügbar sind.
    // Wird jetzt dynamisch durch den Spinner im Fragment gesetzt.
    // Initialwert kann z.B. nur Zelle 1 sein oder die erste Option des Spinners.
    var availableCells: List<Int> = listOf(1) // Startet mit einer Zelle
        private set // Nur intern über updateAvailableCells änderbar

    // Maximale Anzahl Zellen in der UI (und im Spinner)
    const val maxDisplayCells = 8

    // Moxa-Konfiguration
    const val MOXA_IP = "192.168.50.3" // Stelle sicher, dass dies die korrekte IP für dein Moxa-Gerät ist
    const val MOXA_PORT = 4001

    // Timeouts
    const val CONNECTION_TIMEOUT = 5000 // in Millisekunden
    const val READ_TIMEOUT = 3000       // in Millisekunden
    const val LIVE_UPDATE_INTERVAL = 1000L // in Millisekunden

    /**
     * Aktualisiert die Liste der verfügbaren Zellen basierend auf der Auswahl.
     * @param count Die Anzahl der Zellen, die aktiv sein sollen (beginnend bei Zelle 1).
     */
    fun updateAvailableCells(count: Int) {
        if (count in 1..maxDisplayCells) {
            availableCells = List(count) { it + 1 } // Erzeugt eine Liste [1, 2, ..., count]
            Log.i("MultiCellConfig", "Verfügbare Zellen aktualisiert auf: ${availableCells.joinToString(", ")}")
        } else {
            Log.w("MultiCellConfig", "Ungültige Anzahl für updateAvailableCells: $count")
        }
    }

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
        return "Aktuell aktive Zellen: ${availableCells.joinToString(", ")} " +
                "(${availableCells.size} ausgewählt)"
    }

    /**
     * Erweiterte Konfiguration für zukünftige Zellen
     */
    data class CellConfig(
        val cellNumber: Int,
        val displayName: String,
        val isEnabled: Boolean = true,
        val customCommands: Map<FlintecRC3DMultiCellCommands.CommandType, ByteArray>? = null
    )

    private val cellConfigs = mapOf(
        1 to CellConfig(1, "Zelle A"),
        2 to CellConfig(2, "Zelle B"),
        3 to CellConfig(3, "Zelle C"),
        4 to CellConfig(4, "Zelle D"),
        5 to CellConfig(5, "Zelle E"),
        6 to CellConfig(6, "Zelle F"),
        7 to CellConfig(7, "Zelle G"),
        8 to CellConfig(8, "Zelle H")
    )

    fun getCellConfig(cellNumber: Int): CellConfig? {
        return cellConfigs[cellNumber]
    }
}
