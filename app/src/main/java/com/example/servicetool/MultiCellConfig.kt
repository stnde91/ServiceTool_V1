package com.example.servicetool

import android.content.Context
import android.util.Log

/**
 * Zentrale Konfiguration für Multi-Cell Support - jetzt mit Settings Integration
 */
object MultiCellConfig {

    // Maximale Anzahl Zellen, die in der UI (und im Spinner) unterstützt werden
    const val maxDisplayCells = 8

    // Hält die Liste der aktuell vom Benutzer ausgewählten aktiven Zellen (Nummern 1 bis maxDisplayCells)
    var availableCells: List<Int> = listOf(1)
        private set // Nur intern über updateAvailableCells änderbar

    // ENTFERNT: Hardcoded IP/Port - wird jetzt aus Settings geholt
    // const val MOXA_IP = "192.168.50.3"
    // const val MOXA_PORT = 4001

    // Timeouts (diese bleiben hardcoded, da sie selten geändert werden)
    const val CONNECTION_TIMEOUT = 5000 // in Millisekunden
    const val READ_TIMEOUT = 3000       // in Millisekunden
    const val LIVE_UPDATE_INTERVAL = 1000L // in Millisekunden für den Live-Modus
    const val CELL_QUERY_DELAY_MS = 200L // Kurze Verzögerung zwischen Abfragen einzelner Zellen beim Refresh

    // Neue Funktionen für Settings-Integration
    private var settingsManager: SettingsManager? = null

    /**
     * Initialisiert MultiCellConfig mit SettingsManager
     */
    fun initialize(context: Context) {
        settingsManager = SettingsManager.getInstance(context)
        Log.i("MultiCellConfig", "Initialisiert mit SettingsManager")
    }

    /**
     * Gibt die aktuelle Moxa IP-Adresse aus den Settings zurück
     */
    fun getMoxaIpAddress(): String {
        return settingsManager?.getMoxaIpAddress() ?: "192.168.50.3" // Fallback
    }

    /**
     * Gibt den aktuellen Moxa Port aus den Settings zurück
     */
    fun getMoxaPort(): Int {
        return settingsManager?.getMoxaPort() ?: 4001 // Fallback
    }

    /**
     * Gibt Connection Timeout aus Settings zurück (falls konfigurierbar)
     */
    fun getConnectionTimeout(): Int {
        return settingsManager?.getConnectionTimeout() ?: CONNECTION_TIMEOUT
    }

    /**
     * Gibt Read Timeout aus Settings zurück (falls konfigurierbar)
     */
    fun getReadTimeout(): Int {
        return settingsManager?.getReadTimeout() ?: READ_TIMEOUT
    }

    /**
     * Aktualisiert die Liste der verfügbaren (aktiven) Zellen basierend auf der Benutzerauswahl.
     * @param count Die Anzahl der Zellen, die aktiv sein sollen (z.B. 3 bedeutet Zellen 1, 2, 3 sind aktiv).
     */
    fun updateAvailableCells(count: Int) {
        if (count in 1..maxDisplayCells) {
            // Erzeugt eine Liste von Zellennummern von 1 bis zur ausgewählten Anzahl.
            // Beispiel: count = 3 -> availableCells = [1, 2, 3]
            availableCells = List(count) { it + 1 }
            Log.i("MultiCellConfig", "Aktive Zellen aktualisiert auf: ${availableCells.joinToString(", ")} (Anzahl: $count)")
        } else {
            Log.w("MultiCellConfig", "Ungültige Anzahl für updateAvailableCells: $count. Muss zwischen 1 und $maxDisplayCells liegen.")
        }
    }

    /**
     * Gibt zurück, ob eine bestimmte Zelle (basierend auf ihrer Nummer) aktuell als aktiv konfiguriert ist.
     * @param cellNumber Die Nummer der Zelle (1-basiert).
     * @return True, wenn die Zelle aktiv ist, sonst false.
     */
    fun isCellAvailable(cellNumber: Int): Boolean {
        return availableCells.contains(cellNumber)
    }

    /**
     * Gibt die Anzahl der aktuell als aktiv konfigurierten Zellen zurück.
     * @return Die Anzahl der aktiven Zellen.
     */
    fun getAvailableCellCount(): Int {
        return availableCells.size
    }

    /**
     * Gibt eine Zusammenfassung der aktuellen Konfiguration für Debugging-Zwecke aus.
     */
    fun getConfigSummary(): String {
        return "Maximal anzeigbare Zellen: $maxDisplayCells. " +
                "Aktuell aktive Zellen (vom Benutzer ausgewählt): ${availableCells.joinToString(", ")} " +
                "(Anzahl: ${getAvailableCellCount()}). " +
                "Moxa: ${getMoxaIpAddress()}:${getMoxaPort()}"
    }

    // Erweiterte Konfiguration für einzelne Zellen (kann später genutzt werden)
    data class CellSpecificConfig(
        val cellNumber: Int,
        val displayName: String,
        val isEnabledByDefault: Boolean = true, // Ob diese Zelle generell verfügbar ist
        val customCommands: Map<FlintecRC3DMultiCellCommands.CommandType, ByteArray>? = null
    )

    // Beispielhafte Map für spezifische Konfigurationen pro Zelle, falls benötigt
    private val cellSpecificConfigs = (1..maxDisplayCells).associateWith {
        CellSpecificConfig(it, "Zelle ${('A' + it - 1)}")
    }

    fun getCellSpecificConfig(cellNumber: Int): CellSpecificConfig? {
        return cellSpecificConfigs[cellNumber]
    }
}