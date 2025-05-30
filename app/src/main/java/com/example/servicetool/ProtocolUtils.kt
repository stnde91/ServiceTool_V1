package com.example.servicetool // Dein Paketname

/**
 * Berechnet eine XOR-Prüfsumme für den übergebenen Befehlsstring.
 * Diese Funktion ist eine Portierung der VB.NET-Funktion 'Digi_Checksum_erstellen'.
 *
 * Die Berechnung ignoriert das erste Zeichen des Strings (typischerweise STX),
 * führt eine XOR-Operation über die ASCII/Unicode-Werte der restlichen Zeichen durch
 * und gibt das Ergebnis als zweistelligen Hexadezimal-String zurück.
 *
 * @param command Der vollständige Befehlsstring, inklusive des STX-Zeichens am Anfang.
 * @return Die berechnete Prüfsumme als 2-stelliger Hex-String (z.B. "4A").
 */
fun createDigitalChecksum(command: String): String {
    if (command.length < 2) {
        return "00"
    }
    var checksum = 0
    val commandToProcess = command.drop(1)
    for (char in commandToProcess) {
        checksum = checksum xor char.code
    }
    return String.format("%02X", checksum)
}