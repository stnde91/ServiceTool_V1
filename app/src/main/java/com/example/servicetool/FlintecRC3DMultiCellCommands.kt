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

    // Generische Funktion zur Befehlserstellung basierend auf Wireshark-Logs
    fun getCommandForCell(cellNumber: Int, commandType: CommandType): ByteArray {
        val cellCharByte = when (cellNumber) {
            1 -> 0x41 // 'A'
            2 -> 0x42 // 'B'
            3 -> 0x43 // 'C'
            4 -> 0x44 // 'D'
            5 -> 0x45 // 'E'
            6 -> 0x46 // 'F'
            7 -> 0x47 // 'G'
            8 -> 0x48 // 'H'
            else -> throw IllegalArgumentException("Ungültige Zellennummer: $cellNumber. Muss zwischen 1 und 8 liegen.")
        }

        return when (commandType) {
            CommandType.SERIAL_NUMBER -> when (cellNumber) {
                1 -> byteArrayOf(STX, 0x41, 0x63, 0x30, 0x31, 0x31, 0x32, ETX) // Ac0112
                2 -> byteArrayOf(STX, 0x42, 0x63, 0x30, 0x31, 0x32, 0x32, ETX) // Bc0122
                3 -> byteArrayOf(STX, 0x43, 0x63, 0x30, 0x31, 0x33, 0x32, ETX) // Cc0132
                4 -> byteArrayOf(STX, 0x44, 0x63, 0x30, 0x31, 0x34, 0x32, ETX) // Dc0142
                5 -> byteArrayOf(STX, 0x45, 0x63, 0x30, 0x31, 0x35, 0x32, ETX) // Ec0152
                6 -> byteArrayOf(STX, 0x46, 0x63, 0x30, 0x31, 0x36, 0x32, ETX) // Fc0162
                7 -> byteArrayOf(STX, 0x47, 0x63, 0x30, 0x31, 0x37, 0x32, ETX) // Gc0172
                8 -> byteArrayOf(STX, 0x48, 0x63, 0x30, 0x31, 0x38, 0x32, ETX) // Hc0182
                else -> byteArrayOf() // Sollte nicht erreicht werden
            }
            CommandType.COUNTS -> when (cellNumber) {
                1 -> byteArrayOf(STX, 0x41, 0x3F, 0x3C, 0x37, ETX)       // A?<?7
                2 -> byteArrayOf(STX, 0x42, 0x3F, 0x3F, 0x37, ETX)       // B??7
                3 -> byteArrayOf(STX, 0x43, 0x3F, 0x3E, 0x37, ETX)       // C?>7
                4 -> byteArrayOf(STX, 0x44, 0x3F, 0x39, 0x37, ETX)       // D?97
                5 -> byteArrayOf(STX, 0x45, 0x3F, 0x38, 0x37, ETX)       // E?87
                6 -> byteArrayOf(STX, 0x46, 0x3F, 0x3B, 0x37, ETX)       // F?;7
                7 -> byteArrayOf(STX, 0x47, 0x3F, 0x3A, 0x37, ETX)       // G?:7
                8 -> byteArrayOf(STX, 0x48, 0x3F, 0x35, 0x37, ETX)       // H?57
                else -> byteArrayOf()
            }
            CommandType.BAUDRATE -> when (cellNumber) {
                1 -> byteArrayOf(STX, 0x41, 0x73, 0x32, 0x32, 0x30, ETX) // As220
                2 -> byteArrayOf(STX, 0x42, 0x73, 0x32, 0x31, 0x30, ETX) // Bs210
                3 -> byteArrayOf(STX, 0x43, 0x73, 0x32, 0x30, 0x30, ETX) // Cs200
                4 -> byteArrayOf(STX, 0x44, 0x73, 0x32, 0x37, 0x30, ETX) // Ds270
                5 -> byteArrayOf(STX, 0x45, 0x73, 0x32, 0x36, 0x30, ETX) // Es260
                6 -> byteArrayOf(STX, 0x46, 0x73, 0x32, 0x35, 0x30, ETX) // Fs250
                7 -> byteArrayOf(STX, 0x47, 0x73, 0x32, 0x34, 0x30, ETX) // Gs240
                8 -> byteArrayOf(STX, 0x48, 0x73, 0x32, 0x3B, 0x30, ETX) // Hs2;0
                else -> byteArrayOf()
            }
            CommandType.TEMPERATURE -> when (cellNumber) {
                1 -> byteArrayOf(STX, 0x41, 0x74, 0x37, 0x33, ETX)       // At73
                2 -> byteArrayOf(STX, 0x42, 0x74, 0x34, 0x33, ETX)       // Bt43
                3 -> byteArrayOf(STX, 0x43, 0x74, 0x35, 0x33, ETX)       // Ct53
                4 -> byteArrayOf(STX, 0x44, 0x74, 0x32, 0x33, ETX)       // Dt23
                5 -> byteArrayOf(STX, 0x45, 0x74, 0x33, 0x33, ETX)       // Et33
                6 -> byteArrayOf(STX, 0x46, 0x74, 0x30, 0x33, ETX)       // Ft03
                7 -> byteArrayOf(STX, 0x47, 0x74, 0x31, 0x33, ETX)       // Gt13
                8 -> byteArrayOf(STX, 0x48, 0x74, 0x3E, 0x33, ETX)       // Ht>3
                else -> byteArrayOf()
            }
            CommandType.FILTER -> when (cellNumber) {
                1 -> byteArrayOf(STX, 0x41, 0x70, 0x33, 0x33, ETX)       // Ap33
                2 -> byteArrayOf(STX, 0x42, 0x70, 0x30, 0x33, ETX)       // Bp03
                3 -> byteArrayOf(STX, 0x43, 0x70, 0x31, 0x33, ETX)       // Cp13
                4 -> byteArrayOf(STX, 0x44, 0x70, 0x36, 0x33, ETX)       // Dp63
                5 -> byteArrayOf(STX, 0x45, 0x70, 0x37, 0x33, ETX)       // Ep73
                6 -> byteArrayOf(STX, 0x46, 0x70, 0x34, 0x33, ETX)       // Fp43
                7 -> byteArrayOf(STX, 0x47, 0x70, 0x35, 0x33, ETX)       // Gp53
                8 -> byteArrayOf(STX, 0x48, 0x70, 0x3A, 0x33, ETX)       // Hp:3
                else -> byteArrayOf()
            }
            CommandType.VERSION -> when (cellNumber) {
                1 -> byteArrayOf(STX, 0x41, 0x76, 0x35, 0x33, ETX)       // Av53
                2 -> byteArrayOf(STX, 0x42, 0x76, 0x36, 0x33, ETX)       // Bv63
                3 -> byteArrayOf(STX, 0x43, 0x76, 0x37, 0x33, ETX)       // Cv73
                4 -> byteArrayOf(STX, 0x44, 0x76, 0x30, 0x33, ETX)       // Dv03
                5 -> byteArrayOf(STX, 0x45, 0x76, 0x31, 0x33, ETX)       // Ev13
                6 -> byteArrayOf(STX, 0x46, 0x76, 0x32, 0x33, ETX)       // Fv23
                7 -> byteArrayOf(STX, 0x47, 0x76, 0x33, 0x33, ETX)       // Gv33
                8 -> byteArrayOf(STX, 0x48, 0x76, 0x3C, 0x33, ETX)       // Hv<3
                else -> byteArrayOf()
            }
        }
    }

    // --- Antwort-Parsing ---
    fun parseMultiCellResponse(response: String, expectedCell: Int = 0): FlintecData? {
        if (response.length < 2) {
            android.util.Log.w("Parser", "Antwort zu kurz: '$response'")
            return null
        }

        val responseCellPrefix = response.take(1)
        val responseCommandType = response.drop(1).take(1)
        val dataPayload = response.drop(2)

        android.util.Log.d("Parser", "parseMultiCellResponse: Input='$response', Prefix='$responseCellPrefix', Type='$responseCommandType', Payload='$dataPayload'")

        return when (responseCommandType) {
            "c" -> FlintecData.SerialNumber(decodeSerialNumber(dataPayload))
            "?" -> FlintecData.Counts(decodeCountsMultiCell(dataPayload))
            "s" -> FlintecData.Baudrate(decodeBaudrateMultiCell(dataPayload, responseCellPrefix))
            "t" -> FlintecData.Temperature(decodeTemperatureMultiCell(dataPayload))
            "p" -> FlintecData.Filter(decodeFilterMultiCell(dataPayload))
            "v" -> FlintecData.Version(decodeVersion(dataPayload))
            else -> {
                android.util.Log.w("Parser", "Unbekannter Befehlstyp '$responseCommandType' in Antwort: '$response'")
                FlintecData.Unknown(response)
            }
        }
    }

    private fun decodeSerialNumber(rawData: String): String {
        android.util.Log.d("DecoderSN", "Input: '$rawData'")
        var hexToConvert = rawData
        if (hexToConvert.startsWith("01") && hexToConvert.length > 2) {
            hexToConvert = hexToConvert.substring(2)
        }
        hexToConvert = hexToConvert.takeWhile { it.isLetterOrDigit() }

        if (hexToConvert.isEmpty()) return rawData

        val decimalValue = hexToConvert.toLongOrNull(16)
        val result = decimalValue?.toString() ?: hexToConvert
        android.util.Log.d("DecoderSN", "Result: '$result' from hex: '$hexToConvert'")
        return result
    }

    private fun decodeCountsMultiCell(rawCountsData: String): String {
        android.util.Log.d("CountsDecoder", "Input Rohdaten: '$rawCountsData'")
        var stringToProcess = rawCountsData
        if (stringToProcess.startsWith("d", ignoreCase = true)) {
            stringToProcess = stringToProcess.drop(1) // Remove 'd' or 'D'
        }
        android.util.Log.d("CountsDecoder", "Nach 'd'-Entfernung: '$stringToProcess'")

        // Nimm die ersten bis zu 6 Zeichen für den Zählwert
        val potentialCountsPart = stringToProcess.take(6)
        android.util.Log.d("CountsDecoder", "Potentieller Zählwert (erste 6 Zeichen): '$potentialCountsPart'")

        // Filtere alle Nicht-Ziffern aus diesem Teil heraus
        val numericOnlyPart = potentialCountsPart.filter { it.isDigit() }
        android.util.Log.d("CountsDecoder", "Nur Ziffern aus potentiellem Zählwert: '$numericOnlyPart'")

        if (numericOnlyPart.isEmpty()) {
            android.util.Log.w("CountsDecoder", "Keine Ziffern im relevanten Teil gefunden. Input: '$rawCountsData', Verarbeitet: '$potentialCountsPart'. Ergebnis: '0'")
            return "0" // Fallback, wenn keine Ziffern vorhanden sind
        }

        val asLong = numericOnlyPart.toLongOrNull()
        // Wenn die Konvertierung zu Long erfolgreich war, gib den Wert als String zurück (entfernt führende Nullen).
        // Ansonsten (sollte bei reinen Ziffern nicht passieren), gib den gefilterten String zurück.
        val result = asLong?.toString() ?: numericOnlyPart

        android.util.Log.i("CountsDecoder", "Verarbeiteter Zählwert für '$rawCountsData': '$result'")
        return result
    }


    private fun decodeBaudrateMultiCell(rawData: String, cellPrefix: String): String {
        android.util.Log.d("BaudrateDecoder", "Input: '$rawData', CellPrefix: '$cellPrefix'")
        val relevantPart = rawData.split(':', ';').firstOrNull { it.contains("961") || it.contains("192") || it.contains("384") } ?: rawData

        return when {
            relevantPart.contains("9617") || relevantPart.contains("961") -> "9600"
            relevantPart.contains("19200") || relevantPart.contains("192") -> "19200"
            relevantPart.contains("38400") || relevantPart.contains("384") -> "38400"
            else -> {
                android.util.Log.w("BaudrateDecoder", "Unbekannter Baudraten-Code: '$relevantPart' (aus Rohdaten '$rawData')")
                rawData
            }
        }
    }


    private fun decodeTemperatureMultiCell(tempData: String): String {
        android.util.Log.d("TempDecoder", "Input: '$tempData'")
        var cleanTempString = tempData.replace("+", "").trim()
        cleanTempString = cleanTempString.takeWhile { it.isDigit() || it == '.' || it == '-' }

        val tempValue = cleanTempString.toDoubleOrNull()
        val result = if (tempValue != null) {
            String.format(Locale.GERMAN, "%.1f°C", tempValue)
        } else {
            android.util.Log.w("TempDecoder", "Konnte Temperatur nicht parsen: '$tempData', clean: '$cleanTempString'")
            "${tempData}°C"
        }
        android.util.Log.d("TempDecoder", "Result: '$result'")
        return result
    }

    private fun decodeFilterMultiCell(rawData: String): String {
        android.util.Log.d("FilterDecoder", "Input: '$rawData'")
        val corePart = rawData.split('=', ':').firstOrNull() ?: rawData

        return if (corePart.startsWith("23001") && corePart.length > 5) {
            android.util.Log.i("FilterDecoder", "Filter-Regel '23001...' -> '0' angewendet für '$rawData'")
            "0"
        } else if (corePart.all { it.isDigit() }) {
            val result = corePart.toIntOrNull()?.toString() ?: corePart
            android.util.Log.d("FilterDecoder", "Filter ist numerisch: '$corePart' -> '$result'")
            result
        } else {
            val numericPrefix = corePart.takeWhile { it.isDigit() }
            if (numericPrefix.isNotEmpty() && numericPrefix.toLongOrNull() != null) {
                android.util.Log.d("FilterDecoder", "Filter extrahiert als numerischer Präfix: '$numericPrefix' aus '$corePart'")
                return numericPrefix
            }
            android.util.Log.w("FilterDecoder", "Unbekannter Filter-Code, verwende Rohdaten: '$rawData'")
            rawData
        }
    }

    private fun decodeVersion(rawData: String): String {
        android.util.Log.d("VersionDecoder", "Input: '$rawData'")
        var versionString = rawData
        // Keine spezielle Behandlung für 'X' am Anfang mehr, da es Teil der Version sein kann.
        // Entferne nur problematische Endzeichen, falls vorhanden.
        if (versionString.endsWith("=") || versionString.endsWith("?") || versionString.endsWith(">") || versionString.endsWith("<")) {
            // versionString = versionString.dropLast(1) // Vorsicht, könnte valide Zeichen entfernen.
        }
        android.util.Log.d("VersionDecoder", "Result: '$versionString'")
        return versionString
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