package com.example.servicetool

import java.util.Locale

// Erweiterte Flintec RC3D Befehle für Multi-Cell Support
object FlintecRC3DMultiCellCommands {

    private val STX: Byte = 0x02
    private val ETX: Byte = 0x03

    // ===== ZELLE 1 (Original A-Befehle) =====
    fun getSerialNumberCell1(): ByteArray = byteArrayOf(STX, 0x41, 0x63, 0x30, 0x31, 0x31, 0x32, ETX)
    fun getCountsCell1(): ByteArray = byteArrayOf(STX, 0x41, 0x3F, 0x3C, 0x37, ETX)
    fun getBaudrateCell1(): ByteArray = byteArrayOf(STX, 0x41, 0x73, 0x32, 0x32, 0x30, ETX)
    fun getTemperatureCell1(): ByteArray = byteArrayOf(STX, 0x41, 0x74, 0x37, 0x33, ETX)
    fun getFilterCell1(): ByteArray = byteArrayOf(STX, 0x41, 0x70, 0x33, 0x33, ETX)
    fun getVersionCell1(): ByteArray = byteArrayOf(STX, 0x41, 0x76, 0x35, 0x33, ETX)

    // ===== ZELLE 2 (Neue B-Befehle basierend auf Wireshark) =====
    fun getSerialNumberCell2(): ByteArray = byteArrayOf(STX, 0x42, 0x63, 0x30, 0x31, 0x32, 0x32, ETX)
    fun getCountsCell2(): ByteArray = byteArrayOf(STX, 0x42, 0x3F, 0x3F, 0x37, ETX)
    fun getBaudrateCell2(): ByteArray = byteArrayOf(STX, 0x42, 0x73, 0x32, 0x31, 0x30, ETX)
    fun getTemperatureCell2(): ByteArray = byteArrayOf(STX, 0x42, 0x74, 0x34, 0x33, ETX)
    fun getFilterCell2(): ByteArray = byteArrayOf(STX, 0x42, 0x70, 0x30, 0x33, ETX)
    fun getVersionCell2(): ByteArray = byteArrayOf(STX, 0x42, 0x76, 0x36, 0x33, ETX)

    // Generische Funktion für beliebige Zelle (A=1, B=2, C=3, etc.)
    fun getCommandForCell(cellNumber: Int, commandType: CommandType): ByteArray {
        val cellChar = when (cellNumber) {
            1 -> 0x41 // 'A'
            2 -> 0x42 // 'B'
            3 -> 0x43 // 'C'
            4 -> 0x44 // 'D'
            5 -> 0x45 // 'E'
            6 -> 0x46 // 'F'
            7 -> 0x47 // 'G'
            8 -> 0x48 // 'H'
            else -> 0x41 // Default auf Zelle 1
        }

        return when (commandType) {
            CommandType.SERIAL_NUMBER -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellChar.toByte(), 0x63, 0x30, 0x31, 0x31, 0x32, ETX)
                2 -> byteArrayOf(STX, cellChar.toByte(), 0x63, 0x30, 0x31, 0x32, 0x32, ETX)
                else -> byteArrayOf(STX, cellChar.toByte(), 0x63, 0x30, 0x31, (0x31 + cellNumber - 1).toByte(), 0x32, ETX)
            }
            CommandType.COUNTS -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellChar.toByte(), 0x3F, 0x3C, 0x37, ETX)
                2 -> byteArrayOf(STX, cellChar.toByte(), 0x3F, 0x3F, 0x37, ETX)
                else -> byteArrayOf(STX, cellChar.toByte(), 0x3F, 0x3F, 0x37, ETX) // Verwende B-Pattern als Default
            }
            CommandType.BAUDRATE -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellChar.toByte(), 0x73, 0x32, 0x32, 0x30, ETX)
                2 -> byteArrayOf(STX, cellChar.toByte(), 0x73, 0x32, 0x31, 0x30, ETX)
                else -> byteArrayOf(STX, cellChar.toByte(), 0x73, 0x32, (0x30 + cellNumber).toByte(), 0x30, ETX)
            }
            CommandType.TEMPERATURE -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellChar.toByte(), 0x74, 0x37, 0x33, ETX)
                2 -> byteArrayOf(STX, cellChar.toByte(), 0x74, 0x34, 0x33, ETX)
                else -> byteArrayOf(STX, cellChar.toByte(), 0x74, (0x34 + cellNumber - 2).toByte(), 0x33, ETX)
            }
            CommandType.FILTER -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellChar.toByte(), 0x70, 0x33, 0x33, ETX)
                2 -> byteArrayOf(STX, cellChar.toByte(), 0x70, 0x30, 0x33, ETX)
                else -> byteArrayOf(STX, cellChar.toByte(), 0x70, 0x30, 0x33, ETX)
            }
            CommandType.VERSION -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellChar.toByte(), 0x76, 0x35, 0x33, ETX)
                2 -> byteArrayOf(STX, cellChar.toByte(), 0x76, 0x36, 0x33, ETX)
                else -> byteArrayOf(STX, cellChar.toByte(), 0x76, (0x35 + cellNumber - 1).toByte(), 0x33, ETX)
            }
        }
    }

    enum class CommandType {
        SERIAL_NUMBER, COUNTS, BAUDRATE, TEMPERATURE, FILTER, VERSION
    }

    // Erweiterte Response-Parser
    fun parseMultiCellResponse(response: String, expectedCell: Int = 0): FlintecData? {
        if (response.length < 2) return null

        val cellPrefix = response.take(1)
        val commandType = response.drop(1).take(1)
        val data = response.drop(2)

        return when (commandType) {
            "c" -> {
                val decodedSerial = decodeSerialNumber(data)
                FlintecData.SerialNumber(decodedSerial)
            }
            "?" -> {
                val decodedCounts = decodeCountsMultiCell(data)
                FlintecData.Counts(decodedCounts)
            }
            "s" -> {
                val decodedBaud = decodeBaudrateMultiCell(data)
                FlintecData.Baudrate(decodedBaud)
            }
            "t" -> {
                val decodedTemp = decodeTemperatureMultiCell(data)
                FlintecData.Temperature(decodedTemp)
            }
            "p" -> {
                val decodedFilter = decodeFilterMultiCell(data)
                FlintecData.Filter(decodedFilter)
            }
            "v" -> {
                val cleanVersion = if (data.startsWith("XRC1/")) data else data
                FlintecData.Version(cleanVersion)
            }
            else -> FlintecData.Unknown("$cellPrefix$commandType$data")
        }
    }

    private fun decodeSerialNumber(rawSerial: String): String {
        try {
            // Für "01DF8256924" - entferne "01" Prefix falls vorhanden
            val cleanSerial = if (rawSerial.startsWith("01")) {
                rawSerial.removePrefix("01")
            } else {
                rawSerial
            }

            // Versuche Hex-zu-Dezimal Konvertierung
            val hexPart = if (cleanSerial.contains(":")) {
                cleanSerial.split(":")[0]
            } else {
                cleanSerial
            }

            val decimalValue = hexPart.toLongOrNull(16)
            return if (decimalValue != null) {
                decimalValue.toString()
            } else {
                cleanSerial
            }
        } catch (e: Exception) {
            return rawSerial
        }
    }

    private fun decodeCountsMultiCell(rawCounts: String): String {
        try {
            // Debug: Zeige die Rohdaten
            android.util.Log.d("CountsDecoder", "Verarbeite Rohdaten: '$rawCounts'")
            android.util.Log.d("CountsDecoder", "Rohdaten (Hex): ${rawCounts.toByteArray().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}")

            // Prüfe ob die Daten sinnvoll aussehen
            if (rawCounts.any { it.code < 32 || it.code > 126 }) {
                android.util.Log.w("CountsDecoder", "Daten enthalten nicht-druckbare Zeichen!")
                return "FEHLER"
            }

            // Für verschiedene Formate: "d000993;1", "000125>1", etc.
            val cleanCounts = when {
                rawCounts.startsWith("d") -> rawCounts.drop(1)
                else -> rawCounts
            }

            // Entferne alles nach Trennzeichen wie ;, :, >, <, =
            val mainValue = cleanCounts.split(";", ":", ",", ">", "<", "=")[0]

            // Prüfe ob es nur Ziffern enthält
            if (mainValue.all { it.isDigit() }) {
                // Entferne führende Nullen für saubere Anzeige
                val trimmedValue = mainValue.trimStart('0').ifEmpty { "0" }

                // Versuche Zahl-Konvertierung (manche Geräte senden Werte * 100)
                val fullNumber = trimmedValue.toLongOrNull()
                if (fullNumber != null) {
                    // Wenn der Wert sehr groß ist (>10000), teile durch 100
                    return if (fullNumber > 10000) {
                        (fullNumber / 100).toString()
                    } else {
                        fullNumber.toString()
                    }
                }

                return trimmedValue
            } else {
                android.util.Log.w("CountsDecoder", "Hauptwert '$mainValue' enthält nicht nur Ziffern")
                return "RAW:$rawCounts"
            }

        } catch (e: Exception) {
            android.util.Log.e("CountsDecoder", "Fehler beim Dekodieren: ${e.message}")
            return "ERR:$rawCounts"
        }
    }

    private fun decodeBaudrateMultiCell(rawBaud: String): String {
        return when {
            rawBaud.contains("2B96174") -> "9600" // Neues Pattern von Zelle 2
            rawBaud.contains("2A9617") -> "9600"  // Original Pattern von Zelle 1
            rawBaud.contains("4B02") -> "19200"
            rawBaud.contains("9604") -> "38400"
            else -> rawBaud
        }
    }

    private fun decodeTemperatureMultiCell(tempData: String): String {
        try {
            // Für verschiedene Formate: "+024.5640", "+024.18=0", etc.
            var cleanTempString = tempData.replace("+", "").trim()

            // Entferne alles nach = oder anderen Trennzeichen
            cleanTempString = cleanTempString.split("=", ";", ">", "<")[0]

            val tempValue = cleanTempString.toDoubleOrNull()

            if (tempValue != null) {
                val formatted = String.format(Locale.GERMAN, "%.1f", tempValue)
                return "${formatted}°C"
            }
            return "${tempData}°C"
        } catch (e: Exception) {
            return "${tempData}°C"
        }
    }

    private fun decodeFilterMultiCell(rawFilter: String): String {
        return when {
            rawFilter == "2300140373" -> "0" // Neues Pattern von Zelle 2
            rawFilter == "2300140343" -> "0" // Original Pattern von Zelle 1
            rawFilter.startsWith("23001") -> "0"
            else -> rawFilter
        }
    }

    // Hilfsfunktion für Debug-Output
    fun getCommandDescription(cellNumber: Int, commandType: CommandType): String {
        val cellName = when (cellNumber) {
            1 -> "A (Zelle 1)"
            2 -> "B (Zelle 2)"
            3 -> "C (Zelle 3)"
            4 -> "D (Zelle 4)"
            5 -> "E (Zelle 5)"
            6 -> "F (Zelle 6)"
            7 -> "G (Zelle 7)"
            8 -> "H (Zelle 8)"
            else -> "? (Unbekannt)"
        }

        val command = when (commandType) {
            CommandType.SERIAL_NUMBER -> "Seriennummer"
            CommandType.COUNTS -> "Zählwert"
            CommandType.BAUDRATE -> "Baudrate"
            CommandType.TEMPERATURE -> "Temperatur"
            CommandType.FILTER -> "Filter"
            CommandType.VERSION -> "Version"
        }

        return "$command für $cellName"
    }
}