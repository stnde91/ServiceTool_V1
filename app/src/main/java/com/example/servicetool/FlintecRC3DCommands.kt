package com.example.servicetool

import java.util.*

/**
 * Flintec RC3D Befehle für einzelne Zellen (basierend auf Wireshark-Analyse)
 */
object FlintecRC3DCommands {

    private val STX: Byte = 0x02
    private val ETX: Byte = 0x03

    // Bekannte Befehle basierend auf Wireshark-Analyse
    fun getSerialNumber(): ByteArray = byteArrayOf(STX, 0x41, 0x63, 0x30, 0x31, 0x31, 0x32, ETX)
    fun getCounts(): ByteArray = byteArrayOf(STX, 0x41, 0x3F, 0x3C, 0x37, ETX)
    fun getBaudrate(): ByteArray = byteArrayOf(STX, 0x41, 0x73, 0x32, 0x32, 0x30, ETX)
    fun getTemperature(): ByteArray = byteArrayOf(STX, 0x41, 0x74, 0x37, 0x33, ETX)
    fun getFilter(): ByteArray = byteArrayOf(STX, 0x41, 0x70, 0x33, 0x33, ETX)
    fun setFilter(filterValue: Int): ByteArray {
        // Validation: Filter value should be between 0 and 15
        val validatedFilter = filterValue.coerceIn(0, 15)
        // Based on Wireshark analysis: Aw43 (6 bytes) sets filter to 0
        // Format: STX + 'A' + 'w' + filter_hex_byte + checksum + ETX
        val filterHex = validatedFilter.toString(16).padStart(1, '0')[0].code.toByte()
        val checksum = (0x33 + validatedFilter).toByte() // Simple checksum calculation
        return byteArrayOf(STX, 0x41, 0x77, filterHex, checksum, ETX)
    }
    fun getVersion(): ByteArray = byteArrayOf(STX, 0x41, 0x76, 0x35, 0x33, ETX)

    // Antwort-Parser mit korrekter Dekodierung aller Befehle
    fun parseResponse(response: String): FlintecData? {
        if (response.length < 2) return null

        return when (response.take(2)) {
            "Ac" -> {
                val rawSerial = response.drop(2)
                val decodedSerial = decodeSerialNumber(rawSerial)
                FlintecData.SerialNumber(decodedSerial)
            }
            "A?" -> {
                val rawCounts = response.drop(2)
                val decodedCounts = decodeCounts(rawCounts)
                FlintecData.Counts(decodedCounts)
            }
            "As" -> {
                val rawBaud = response.drop(2)
                val decodedBaud = decodeBaudrate(rawBaud)
                FlintecData.Baudrate(decodedBaud)
            }
            "At" -> {
                val tempData = response.drop(2)
                val decodedTemp = decodeTemperature(tempData)
                FlintecData.Temperature(decodedTemp)
            }
            "Ap" -> {
                val rawFilter = response.drop(2)
                val decodedFilter = decodeFilter(rawFilter)
                FlintecData.Filter(decodedFilter)
            }
            "AP", "Aw" -> {
                // Both AP and Aw responses indicate filter set result
                val filterSetResult = response.drop(2)
                FlintecData.FilterSetResult(filterSetResult.isNotEmpty())
            }
            "Av" -> {
                FlintecData.Version(response.drop(2))
            }
            else -> FlintecData.Unknown(response)
        }
    }

    private fun decodeSerialNumber(rawSerial: String): String {
        try {
            val parts = rawSerial.split(":")
            val hexPart = if (parts[0].startsWith("01")) {
                parts[0].removePrefix("01")
            } else {
                parts[0]
            }

            val decimalValue = hexPart.toLongOrNull(16)
            if (decimalValue != null) {
                return decimalValue.toString()
            }
            return rawSerial
        } catch (e: Exception) {
            return rawSerial
        }
    }

    private fun decodeCounts(rawCounts: String): String {
        try {
            val cleanCounts = if (rawCounts.startsWith("d")) {
                rawCounts.drop(1)
            } else {
                rawCounts
            }

            val fullNumber = cleanCounts.toLongOrNull()
            if (fullNumber != null) {
                val coarseValue = fullNumber / 100
                return coarseValue.toString()
            }

            if (cleanCounts.length >= 3) {
                val shortened = cleanCounts.dropLast(2).toLongOrNull()?.toString() ?: cleanCounts
                return shortened
            }
            return cleanCounts
        } catch (e: Exception) {
            return rawCounts
        }
    }

    private fun decodeBaudrate(rawBaud: String): String {
        return when {
            rawBaud.contains("2A9617") -> "9600"
            rawBaud.contains("4B02") -> "19200"
            rawBaud.contains("9604") -> "38400"
            else -> rawBaud
        }
    }

    // KORRIGIERTE Temperatur-Formatierung: "+024.4000" -> "24,4°C"
    private fun decodeTemperature(tempData: String): String {
        try {
            val cleanTempString = tempData.replace("+", "").trim()
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

    private fun decodeFilter(rawFilter: String): String {
        return when {
            rawFilter == "2300140343" -> "0"
            rawFilter.startsWith("23001") -> "0"
            else -> rawFilter
        }
    }
}