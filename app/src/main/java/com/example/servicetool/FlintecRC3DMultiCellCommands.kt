package com.example.servicetool

import android.util.Log

/**
 * Flintec RC3D Multi-Cell Befehle - Funktionierende Version vom 23.06
 */
object FlintecRC3DMultiCellCommands {

    private const val STX: Byte = 0x02
    private const val ETX: Byte = 0x03

    enum class CommandType {
        SERIAL_NUMBER, COUNTS, BAUDRATE, TEMPERATURE, FILTER, SET_FILTER, VERSION
    }

    fun getCommandForCell(cellNumber: Int, commandType: CommandType): ByteArray {
        if (cellNumber !in 1..8) {
            Log.w("FlintecRC3D", "Invalid cell number: $cellNumber. Must be 1-8.")
            return byteArrayOf()
        }

        val cellPrefix = when (cellNumber) {
            1 -> 0x41  // A
            2 -> 0x42  // B
            3 -> 0x43  // C
            4 -> 0x44  // D
            5 -> 0x45  // E
            6 -> 0x46  // F
            7 -> 0x47  // G
            8 -> 0x48  // H
            else -> return byteArrayOf()
        }

        return when (commandType) {
            CommandType.SERIAL_NUMBER -> byteArrayOf(STX, cellPrefix.toByte(), 0x63.toByte(), 0x30.toByte(), 0x31.toByte(), 0x31.toByte(), 0x32.toByte(), ETX)
            CommandType.COUNTS -> byteArrayOf(STX, cellPrefix.toByte(), 0x3F.toByte(), 0x3C.toByte(), 0x37.toByte(), ETX)
            CommandType.BAUDRATE -> byteArrayOf(STX, cellPrefix.toByte(), 0x73.toByte(), 0x32.toByte(), 0x32.toByte(), 0x30.toByte(), ETX)
            CommandType.TEMPERATURE -> byteArrayOf(STX, cellPrefix.toByte(), 0x74.toByte(), 0x37.toByte(), 0x33.toByte(), ETX)
            CommandType.FILTER -> byteArrayOf(STX, cellPrefix.toByte(), 0x70.toByte(), 0x33.toByte(), 0x33.toByte(), ETX)
            CommandType.VERSION -> byteArrayOf(STX, cellPrefix.toByte(), 0x76.toByte(), 0x35.toByte(), 0x33.toByte(), ETX)
            CommandType.SET_FILTER -> createFilterCommandByteArray(cellNumber, 5) // Default filter value
        }
    }

    fun createFilterCommandByteArray(cellNumber: Int, filterValue: Int): ByteArray {
        if (cellNumber !in 1..8) {
            return byteArrayOf()
        }
        
        val validatedFilter = filterValue.coerceIn(0, 15)
        val cellPrefix = when (cellNumber) {
            1 -> 0x41; 2 -> 0x42; 3 -> 0x43; 4 -> 0x44
            5 -> 0x45; 6 -> 0x46; 7 -> 0x47; 8 -> 0x48
            else -> return byteArrayOf()
        }
        
        val useAlternativeFormat = cellNumber in listOf(3, 5, 6, 7, 8)
        
        return if (useAlternativeFormat) {
            val filterChar = when (validatedFilter) {
                in 0..9 -> ('0' + validatedFilter).code.toByte()
                in 10..15 -> ('A' + (validatedFilter - 10)).code.toByte()
                else -> '0'.code.toByte()
            }
            val commandByte = '0'.code.toByte()
            byteArrayOf(STX, cellPrefix.toByte(), 0x77.toByte(), filterChar, commandByte, ETX)
        } else {
            val cellSpecificByte = when (cellPrefix) {
                0x41 -> 0x34; 0x42 -> 0x37; 0x44 -> 0x37; else -> 0x34
            }
            val fixedByte = 0x33
            byteArrayOf(STX, cellPrefix.toByte(), 0x77.toByte(), cellSpecificByte.toByte(), fixedByte.toByte(), ETX)
        }
    }

    fun parseMultiCellResponse(response: String): FlintecData? {
        if (response.isEmpty() || response.length < 2) {
            return null
        }

        val responseCommandType = response.drop(1).take(1)
        val dataPayload = response.drop(2)

        return when (responseCommandType) {
            "c" -> FlintecData.SerialNumber(decodeSerialNumber(dataPayload))
            "?" -> FlintecData.Counts(decodeCounts(dataPayload))
            "s" -> FlintecData.Baudrate(decodeBaudrate(dataPayload))
            "t" -> FlintecData.Temperature(decodeTemperature(dataPayload))
            "p" -> FlintecData.Filter(decodeFilter(dataPayload))
            "v" -> FlintecData.Version(decodeVersion(dataPayload))
            else -> FlintecData.Unknown(response)
        }
    }

    private fun decodeSerialNumber(rawData: String): String {
        return try {
            if (rawData.isEmpty()) return "Unbekannt"

            // Entferne das Präfix "01" falls vorhanden
            var hexString = rawData
            if (hexString.startsWith("01") && hexString.length > 2) {
                hexString = hexString.substring(2)
            }

            // Bereinige die Hex-Daten
            hexString = hexString.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

            if (hexString.isEmpty() || hexString.length % 2 != 0) {
                return "Unbekannt"
            }

            // Konvertiere Hex zu ASCII
            val result = StringBuilder()
            for (i in hexString.indices step 2) {
                if (i + 1 < hexString.length) {
                    val hexPair = hexString.substring(i, i + 2)
                    val byteValue = hexPair.toInt(16)
                    if (byteValue in 32..126) { // Druckbare ASCII-Zeichen
                        result.append(byteValue.toChar())
                    }
                }
            }

            val serialNumber = result.toString().trim()
            serialNumber.ifEmpty { "Unbekannt" }

        } catch (e: Exception) {
            Log.e("FlintecRC3DMultiCell", "Fehler beim Dekodieren der Seriennummer: ${e.message}")
            "Unbekannt"
        }
    }

    private fun decodeCounts(rawCountsData: String): String {
        return try {
            if (rawCountsData.isEmpty()) return "0"

            // Bereinige die Antwort - nur Hex-Zeichen (0-9, A-F)
            val cleanedResponse = rawCountsData.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

            if (cleanedResponse.length < 8) {
                return "0"
            }

            // Nimm die ersten 8 Hex-Zeichen (repräsentieren 4 Bytes)
            val hexString = cleanedResponse.take(8)

            // Konvertiere jeden 2-Zeichen Hex-String zu einem Byte
            val bytes = ByteArray(4)
            for (i in 0 until 4) {
                val hexPair = hexString.substring(i * 2, i * 2 + 2)
                bytes[i] = hexPair.toInt(16).toByte()
            }

            // Konvertiere die 4 Bytes zu einem 32-Bit Integer (Big Endian)
            val result = ((bytes[0].toInt() and 0xFF) shl 24) or
                        ((bytes[1].toInt() and 0xFF) shl 16) or
                        ((bytes[2].toInt() and 0xFF) shl 8) or
                        (bytes[3].toInt() and 0xFF)

            // Formatiere als 6-stellige Zahl mit führenden Nullen
            String.format("%06d", result)

        } catch (e: Exception) {
            Log.e("FlintecRC3DMultiCell", "Fehler beim Dekodieren der Counts: ${e.message}")
            "0"
        }
    }

    private fun decodeBaudrate(rawData: String): String {
        return try {
            if (rawData.isEmpty()) return "Unbekannt"

            val cleanedResponse = rawData.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

            if (cleanedResponse.isEmpty() || cleanedResponse.length % 2 != 0) {
                return "Unbekannt"
            }

            // Konvertiere Hex zu ASCII
            val result = StringBuilder()
            for (i in cleanedResponse.indices step 2) {
                if (i + 1 < cleanedResponse.length) {
                    val hexPair = cleanedResponse.substring(i, i + 2)
                    val byteValue = hexPair.toInt(16)
                    if (byteValue in 32..126) {
                        result.append(byteValue.toChar())
                    }
                }
            }

            val baudrate = result.toString().trim().filter { it.isDigit() }
            baudrate.ifEmpty { "Unbekannt" }

        } catch (e: Exception) {
            Log.e("FlintecRC3DMultiCell", "Fehler beim Dekodieren der Baudrate: ${e.message}")
            "Unbekannt"
        }
    }

    private fun decodeTemperature(tempData: String): String {
        return if (tempData.isEmpty()) {
            "N/A"
        } else {
            val cleanData = tempData.takeWhile { it.isLetterOrDigit() }
            val tempValue = cleanData.filter { it.isDigit() || it == '.' || it == '-' }
            tempValue.ifEmpty { "N/A" }
        }
    }

    private fun decodeFilter(rawData: String): String {
        return try {
            if (rawData.isEmpty()) return "Unbekannt"

            val cleanedResponse = rawData.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

            if (cleanedResponse.isEmpty() || cleanedResponse.length % 2 != 0) {
                return "Unbekannt"
            }

            // Konvertiere Hex zu ASCII
            val result = StringBuilder()
            for (i in cleanedResponse.indices step 2) {
                if (i + 1 < cleanedResponse.length) {
                    val hexPair = cleanedResponse.substring(i, i + 2)
                    val byteValue = hexPair.toInt(16)
                    if (byteValue in 32..126) {
                        result.append(byteValue.toChar())
                    }
                }
            }

            val filter = result.toString().trim().filter { it.isDigit() }
            filter.ifEmpty { "Unbekannt" }

        } catch (e: Exception) {
            Log.e("FlintecRC3DMultiCell", "Fehler beim Dekodieren des Filters: ${e.message}")
            "Unbekannt"
        }
    }

    private fun decodeVersion(rawData: String): String {
        return try {
            if (rawData.isEmpty()) return "Unbekannt"

            val cleanedResponse = rawData.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

            if (cleanedResponse.isEmpty() || cleanedResponse.length % 2 != 0) {
                return "Unbekannt"
            }

            // Konvertiere Hex zu ASCII
            val result = StringBuilder()
            for (i in cleanedResponse.indices step 2) {
                if (i + 1 < cleanedResponse.length) {
                    val hexPair = cleanedResponse.substring(i, i + 2)
                    val byteValue = hexPair.toInt(16)
                    if (byteValue in 32..126) {
                        result.append(byteValue.toChar())
                    }
                }
            }

            val version = result.toString().trim()
            version.ifEmpty { "Unbekannt" }

        } catch (e: Exception) {
            Log.e("FlintecRC3DMultiCell", "Fehler beim Dekodieren der Version: ${e.message}")
            "Unbekannt"
        }
    }

    fun createFilterCommand(cellNumber: Int, filterValue: Int): String {
        if (cellNumber !in 1..8) return ""
        
        val validatedFilter = filterValue.coerceIn(0, 15)
        val cellChar = when (cellNumber) {
            1 -> 'A'; 2 -> 'B'; 3 -> 'C'; 4 -> 'D'
            5 -> 'E'; 6 -> 'F'; 7 -> 'G'; 8 -> 'H'
            else -> return ""
        }
        
        val useAlternativeFormat = cellNumber in listOf(3, 5, 6, 7, 8)
        
        return if (useAlternativeFormat) {
            val filterChar = when (validatedFilter) {
                in 0..9 -> ('0' + validatedFilter)
                in 10..15 -> ('A' + (validatedFilter - 10))
                else -> '0'
            }
            "\u0002${cellChar}w${filterChar}0\u0003"
        } else {
            val cellSpecificByte = when (cellChar) {
                'A' -> '4'; 'B' -> '7'; 'D' -> '7'; else -> '4'
            }
            "\u0002${cellChar}w${cellSpecificByte}3\u0003"
        }
    }

    fun createFilterStatusCommand(cellNumber: Int): String {
        if (cellNumber !in 1..8) return ""
        
        val cellChar = when (cellNumber) {
            1 -> 'A'; 2 -> 'B'; 3 -> 'C'; 4 -> 'D'
            5 -> 'E'; 6 -> 'F'; 7 -> 'G'; 8 -> 'H'
            else -> return ""
        }
        
        return "\u0002${cellChar}p33\u0003"
    }

    fun getCommandDescription(cellNumber: Int, commandType: CommandType): String {
        val cellName = when (cellNumber) {
            1 -> "A (Zelle 1)"; 2 -> "B (Zelle 2)"; 3 -> "C (Zelle 3)"; 4 -> "D (Zelle 4)"
            5 -> "E (Zelle 5)"; 6 -> "F (Zelle 6)"; 7 -> "G (Zelle 7)"; 8 -> "H (Zelle 8)"
            else -> "? (Unbekannt)"
        }

        val command = when (commandType) {
            CommandType.SERIAL_NUMBER -> "Seriennummer"
            CommandType.COUNTS -> "Zählwert"
            CommandType.BAUDRATE -> "Baudrate"
            CommandType.TEMPERATURE -> "Temperatur"
            CommandType.FILTER -> "Filter"
            CommandType.SET_FILTER -> "Filter setzen"
            CommandType.VERSION -> "Version"
        }
        return "$command für $cellName"
    }
}