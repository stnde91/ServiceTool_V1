package com.example.servicetool

import java.util.Locale

// Erweiterte Flintec RC3D Befehle für Multi-Cell Support
object FlintecRC3DMultiCellCommands {

    private val STX: Byte = 0x02
    private val ETX: Byte = 0x03

    // Befehlstypen Enum
    enum class CommandType {
        SERIAL_NUMBER, COUNTS, BAUDRATE, TEMPERATURE, FILTER, VERSION
    }

    // --- Befehlsgenerierung ---

    // Spezifische Befehle für Zelle 1 (A)
    fun getSerialNumberCell1(): ByteArray = getCommandForCell(1, CommandType.SERIAL_NUMBER)
    fun getCountsCell1(): ByteArray = getCommandForCell(1, CommandType.COUNTS)
    fun getBaudrateCell1(): ByteArray = getCommandForCell(1, CommandType.BAUDRATE)
    fun getTemperatureCell1(): ByteArray = getCommandForCell(1, CommandType.TEMPERATURE)
    fun getFilterCell1(): ByteArray = getCommandForCell(1, CommandType.FILTER)
    fun getVersionCell1(): ByteArray = getCommandForCell(1, CommandType.VERSION)

    // Spezifische Befehle für Zelle 2 (B)
    fun getSerialNumberCell2(): ByteArray = getCommandForCell(2, CommandType.SERIAL_NUMBER)
    fun getCountsCell2(): ByteArray = getCommandForCell(2, CommandType.COUNTS)
    fun getBaudrateCell2(): ByteArray = getCommandForCell(2, CommandType.BAUDRATE)
    fun getTemperatureCell2(): ByteArray = getCommandForCell(2, CommandType.TEMPERATURE)
    fun getFilterCell2(): ByteArray = getCommandForCell(2, CommandType.FILTER)
    fun getVersionCell2(): ByteArray = getCommandForCell(2, CommandType.VERSION)

    // ... (Funktionen für Zellen 3-8 können hinzugefügt werden, die getCommandForCell nutzen) ...
    fun getSerialNumberCell3(): ByteArray = getCommandForCell(3, CommandType.SERIAL_NUMBER)
    fun getCountsCell3(): ByteArray = getCommandForCell(3, CommandType.COUNTS)
    // ... und so weiter für alle Befehle und Zellen 3-8


    // Generische Funktion zur Befehlserstellung basierend auf Wireshark-Logs und Annahmen
    fun getCommandForCell(cellNumber: Int, commandType: CommandType): ByteArray {
        val cellCharByte = when (cellNumber) {
            1 -> 0x41 // 'A'
            2 -> 0x42 // 'B'
            3 -> 0x43 // 'C'
            4 -> 0x44 // 'D'
            5 -> 0x45 // 'E' (Annahme)
            6 -> 0x46 // 'F' (Annahme)
            7 -> 0x47 // 'G' (Annahme)
            8 -> 0x48 // 'H' (Annahme)
            else -> 0x41 // Default Zelle 1
        }

        return when (commandType) {
            CommandType.SERIAL_NUMBER -> // Pattern: Xc01N2 (N ist Zellennummer)
                byteArrayOf(STX, cellCharByte.toByte(), 0x63, 0x30, 0x31, (0x30 + cellNumber).toByte(), 0x32, ETX)

            CommandType.COUNTS -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellCharByte.toByte(), 0x3F, 0x3C, 0x37, ETX) // A?<?7
                2 -> byteArrayOf(STX, cellCharByte.toByte(), 0x3F, 0x3F, 0x37, ETX) // B??7
                3 -> byteArrayOf(STX, cellCharByte.toByte(), 0x3F, 0x3E, 0x37, ETX) // C?>7 (laut Log)
                4 -> byteArrayOf(STX, cellCharByte.toByte(), 0x3F, 0x39, 0x37, ETX) // D?97 (laut Log)
                else -> byteArrayOf(STX, cellCharByte.toByte(), 0x3F, 0x3F, 0x37, ETX) // Fallback: B-Pattern
            }
            CommandType.BAUDRATE -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellCharByte.toByte(), 0x73, 0x32, 0x32, 0x30, ETX) // As220
                2 -> byteArrayOf(STX, cellCharByte.toByte(), 0x73, 0x32, 0x31, 0x30, ETX) // Bs210
                3 -> byteArrayOf(STX, cellCharByte.toByte(), 0x73, 0x32, 0x30, 0x30, ETX) // Cs200 (laut Log)
                4 -> byteArrayOf(STX, cellCharByte.toByte(), 0x73, 0x32, 0x37, 0x30, ETX) // Ds270 (laut Log)
                else -> byteArrayOf(STX, cellCharByte.toByte(), 0x73, 0x32, 0x31, 0x30, ETX) // Fallback: B-Pattern
            }
            CommandType.TEMPERATURE -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellCharByte.toByte(), 0x74, 0x37, 0x33, ETX) // At73
                2 -> byteArrayOf(STX, cellCharByte.toByte(), 0x74, 0x34, 0x33, ETX) // Bt43
                3 -> byteArrayOf(STX, cellCharByte.toByte(), 0x74, 0x35, 0x33, ETX) // Ct53 (laut Log)
                4 -> byteArrayOf(STX, cellCharByte.toByte(), 0x74, 0x32, 0x33, ETX) // Dt23 (laut Log)
                else -> byteArrayOf(STX, cellCharByte.toByte(), 0x74, 0x34, 0x33, ETX) // Fallback: B-Pattern
            }
            CommandType.FILTER -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellCharByte.toByte(), 0x70, 0x33, 0x33, ETX) // Ap33
                2 -> byteArrayOf(STX, cellCharByte.toByte(), 0x70, 0x30, 0x33, ETX) // Bp03
                3 -> byteArrayOf(STX, cellCharByte.toByte(), 0x70, 0x31, 0x33, ETX) // Cp13 (laut Log)
                4 -> byteArrayOf(STX, cellCharByte.toByte(), 0x70, 0x36, 0x33, ETX) // Dp63 (laut Log)
                else -> byteArrayOf(STX, cellCharByte.toByte(), 0x70, 0x30, 0x33, ETX) // Fallback: B-Pattern
            }
            CommandType.VERSION -> when (cellNumber) {
                1 -> byteArrayOf(STX, cellCharByte.toByte(), 0x76, 0x35, 0x33, ETX) // Av53
                2 -> byteArrayOf(STX, cellCharByte.toByte(), 0x76, 0x36, 0x33, ETX) // Bv63 (laut erstem Log)
                3 -> byteArrayOf(STX, cellCharByte.toByte(), 0x76, 0x37, 0x33, ETX) // Cv73 (laut neuem Log)
                4 -> byteArrayOf(STX, cellCharByte.toByte(), 0x76, 0x30, 0x33, ETX) // Dv03 (laut neuem Log)
                else -> { // Annahme: XvN3, N = (Ziffer '5' + Zelle - 1), begrenzt auf '0'-'9'
                    val middleByte = (0x35 + cellNumber - 1)
                    val safeMiddleByte = if (middleByte in 0x30..0x39) middleByte else 0x35 // Fallback auf '5'
                    byteArrayOf(STX, cellCharByte.toByte(), 0x76, safeMiddleByte.toByte(), 0x33, ETX)
                }
            }
        }
    }

    // --- Antwort-Parsing ---

    fun parseMultiCellResponse(response: String, expectedCell: Int = 0): FlintecData? {
        if (response.length < 2) {
            android.util.Log.w("Parser", "Antwort zu kurz: '$response'")
            return null
        }

        // Das erste Zeichen der Antwort ist oft das Zell-Präfix (A, B, C, D),
        // das zweite der Befehlstyp (c, ?, s, t, p, v).
        // Der 'data'-Teil beginnt dann ab dem dritten Zeichen.
        // Beispiel: Antwort "Cc01D7E76F25" -> responseCellPrefix="C", responseCommandType="c", data="01D7E76F25"

        val responseCellPrefix = response.take(1) // z.B. "C"
        val responseCommandType = response.drop(1).take(1) // z.B. "c"
        val dataPayload = response.drop(2) // z.B. "01D7E76F25"

        android.util.Log.d("Parser", "parseMultiCellResponse: Input='$response', Prefix='$responseCellPrefix', Type='$responseCommandType', Payload='$dataPayload'")


        return when (responseCommandType) {
            "c" -> FlintecData.SerialNumber(decodeSerialNumber(dataPayload))
            "?" -> FlintecData.Counts(decodeCountsMultiCell(dataPayload))
            "s" -> FlintecData.Baudrate(decodeBaudrateMultiCell(dataPayload, responseCellPrefix)) // Übergebe Zellprefix für Kontext
            "t" -> FlintecData.Temperature(decodeTemperatureMultiCell(dataPayload))
            "p" -> FlintecData.Filter(decodeFilterMultiCell(dataPayload))
            "v" -> FlintecData.Version(dataPayload) // Version-String scheint oft direkt verwendbar oder komplexer
            else -> {
                android.util.Log.w("Parser", "Unbekannter Befehlstyp '$responseCommandType' in Antwort: '$response'")
                FlintecData.Unknown(response)
            }
        }
    }

    private fun decodeSerialNumber(rawData: String): String {
        android.util.Log.d("Decoder", "decodeSerialNumber - Input: '$rawData'")
        try {
            // Entferne mögliche nicht-Hex-Zeichen am Ende (wie '?' in "c01DF8256?2")
            val cleanHex = rawData.takeWhile { it.isLetterOrDigit() }

            // Erwartetes Format: "01<HEXWERT>" oder nur "<HEXWERT>"
            val hexToConvert = if (cleanHex.startsWith("01") && cleanHex.length > 2) {
                cleanHex.substring(2)
            } else {
                cleanHex
            }

            if (hexToConvert.isEmpty()) return rawData // Fallback, wenn nichts übrig bleibt

            val decimalValue = hexToConvert.toLongOrNull(16)
            val result = decimalValue?.toString() ?: cleanHex // Fallback auf cleanHex wenn Konvertierung fehlschlägt
            android.util.Log.d("Decoder", "decodeSerialNumber - Result: '$result'")
            return result
        } catch (e: Exception) {
            android.util.Log.e("Decoder", "Fehler bei decodeSerialNumber für '$rawData': ${e.message}")
            return rawData
        }
    }

    private fun decodeCountsMultiCell(rawCountsData: String): String {
        android.util.Log.d("CountsDecoder", "decodeCountsMultiCell - Input Rohdaten: '$rawCountsData'")
        try {
            val stringAfterDPrefix = if (rawCountsData.startsWith("d", ignoreCase = true)) {
                rawCountsData.drop(1)
            } else {
                rawCountsData
            }
            android.util.Log.d("CountsDecoder", "Nach 'd'-Präfix Entfernung: '$stringAfterDPrefix'")

            val mainValuePart = stringAfterDPrefix.split(";", ":", ",", ">", "<", "=", "?")[0]
            android.util.Log.d("CountsDecoder", "Nach Split durch Trennzeichen: '$mainValuePart'")

            val candidatePortion = mainValuePart.take(6)
            android.util.Log.d("CountsDecoder", "Kandidat (max 6 Zeichen): '$candidatePortion'")

            val finalNumericString = candidatePortion.filter { it.isDigit() }
            android.util.Log.d("CountsDecoder", "Finaler numerischer String: '$finalNumericString'")

            if (finalNumericString.isEmpty()) {
                android.util.Log.w("CountsDecoder", "Kein numerischer Teil nach Filterung, Ergebnis: '0'")
                return "0"
            }

            val asLong = finalNumericString.toLongOrNull()
            val result = if (asLong != null) {
                asLong.toString()
            } else {
                android.util.Log.w("CountsDecoder", "Konnte '$finalNumericString' nicht zu Long parsen. Verwende gefilterten String.")
                finalNumericString
            }

            android.util.Log.i("CountsDecoder", "Verarbeiteter Wert für '$rawCountsData': '$result'")
            return result
        } catch (e: Exception) {
            android.util.Log.e("CountsDecoder", "Fehler beim Dekodieren der Counts für '$rawCountsData': ${e.message}")
            return "ERR"
        }
    }

    private fun decodeBaudrateMultiCell(rawData: String, cellPrefix: String): String {
        android.util.Log.d("Decoder", "decodeBaudrateMultiCell - Input: '$rawData', CellPrefix: '$cellPrefix'")
        // Beispielantworten: "2C9617:4" (für Cs2...), "2D9617:4" (für Ds2...)
        // Das "C" oder "D" ist das Zell-Präfix, das bereits in `responseCellPrefix` steht.
        // Der relevante Teil für die Baudrate scheint "9617" zu sein, was 9600 entspricht.

        val dataPart = rawData.split(":")[0] // z.B. "2C9617" oder "2D9617"
        // oder für Zelle A/B: "2A9617" oder "2B9617" (aus älteren Logs)
        // oder "A9617" (aus MainActivity)

        // Entferne das Zellpräfix und das erste Zeichen (oft '2') um den Kern-Code zu erhalten
        val coreCode = when {
            dataPart.length > 1 && dataPart.substring(1).startsWith(cellPrefix) -> dataPart.substring(2) // z.B. "2C9617" -> "9617"
            dataPart.startsWith(cellPrefix) -> dataPart.substring(1) // z.B. "C9617" -> "9617"
            else -> dataPart // Fallback
        }
        android.util.Log.d("Decoder", "decodeBaudrateMultiCell - CoreCode: '$coreCode'")


        return when {
            coreCode.contains("9617") -> "9600" // Allgemeines Pattern für 9600
            coreCode.contains("4B02") -> "19200" // Annahme basierend auf älterer Logik
            coreCode.contains("9604") -> "38400" // Annahme basierend auf älterer Logik
            else -> {
                android.util.Log.w("Decoder", "Unbekannter Baudraten-Kern-Code: '$coreCode' (aus Rohdaten '$rawData')")
                rawData // Fallback
            }
        }
    }

    private fun decodeTemperatureMultiCell(tempData: String): String {
        android.util.Log.d("Decoder", "decodeTemperatureMultiCell - Input: '$tempData'")
        try {
            // Beispiele: "+023.5040", "+023.93<0"
            var cleanTempString = tempData.replace("+", "").trim()
            // Nimm nur den Teil vor dem ersten unerwarteten Zeichen
            cleanTempString = cleanTempString.takeWhile { it.isDigit() || it == '.' || it == '-' }

            val tempValue = cleanTempString.toDoubleOrNull()

            val result = if (tempValue != null) {
                String.format(Locale.GERMAN, "%.1f°C", tempValue)
            } else {
                android.util.Log.w("Decoder", "Konnte Temperatur nicht parsen: '$tempData'")
                "${tempData}°C"
            }
            android.util.Log.d("Decoder", "decodeTemperatureMultiCell - Result: '$result'")
            return result
        } catch (e: Exception) {
            android.util.Log.e("Decoder", "Fehler bei decodeTemperatureMultiCell für '$tempData': ${e.message}")
            return "${tempData}°C"
        }
    }

    private fun decodeFilterMultiCell(rawData: String): String {
        android.util.Log.d("Decoder", "decodeFilterMultiCell - Input: '$rawData'")
        // Beispiele: "2300140363", "2300140313"
        // Annahme: Wenn es mit "23001" beginnt, ist der Filter "0". Ansonsten ist es der Wert selbst.
        return if (rawData.startsWith("23001") && rawData.length > 5) { // Länge > 5 um sicherzustellen, dass es nicht nur "23001" ist
            android.util.Log.i("Decoder", "Filter-Regel '23001...' -> '0' angewendet für '$rawData'")
            "0"
        } else if (rawData.all { it.isDigit() }) {
            val result = rawData.toIntOrNull()?.toString() ?: rawData
            android.util.Log.d("Decoder", "Filter ist numerisch: '$rawData' -> '$result'")
            result
        } else {
            android.util.Log.w("Decoder", "Unbekannter Filter-Code, verwende Rohdaten: '$rawData'")
            rawData
        }
    }

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
