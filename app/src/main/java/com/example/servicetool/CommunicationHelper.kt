package com.example.servicetool

import android.util.Log

/**
 * Helper-Klasse für Kommunikations-Debugging und Fehlerbehandlung
 */
object CommunicationHelper {
    
    /**
     * Prüft ob eine Antwort verdächtig ist (z.B. nur ein einzelnes Zeichen)
     */
    fun isSuspiciousResponse(response: String): Boolean {
        // Verdächtig wenn:
        // - Nur ein Zeichen
        // - Nur das letzte Zeichen eines typischen Befehls (0, 2, 3, 7)
        // - Nur Steuerzeichen
        // - BEL-Zeichen (0x07)
        // - Nur STX/ETX Zeichen
        
        if (response.isEmpty()) {
            Log.w("CommunicationHelper", "Leere Antwort - verdächtig")
            return true
        }
        
        if (response.length == 1) {
            val char = response[0]
            when (char) {
                '0', '2', '3', '7' -> {
                    Log.w("CommunicationHelper", "Verdächtige einstellige Antwort erkannt: '$char' - möglicherweise Echo des letzten Befehlszeichens")
                    return true
                }
                '\u0007' -> { // BEL-Zeichen
                    Log.w("CommunicationHelper", "BEL-Zeichen (0x07) empfangen - schwerwiegender Kommunikationsfehler")
                    return true
                }
                in '\u0000'..'\u001F' -> {
                    Log.w("CommunicationHelper", "Antwort enthält nur Steuerzeichen: 0x${char.code.toString(16).padStart(2, '0')}")
                    return true
                }
            }
        }
        
        // Spezielle Prüfung für bekannte problematische Antworten
        if (response in listOf("BEL_ERROR", "RAW_CTRL_CHARS_RESPONSE", "PARTIAL_RESPONSE_NO_ETX")) {
            Log.w("CommunicationHelper", "Bekannte problematische Antwort: '$response'")
            return true
        }
        
        // Prüfe ob die Antwort nur aus Steuerzeichen besteht
        if (response.all { it.code < 32 || it.code > 126 }) {
            Log.w("CommunicationHelper", "Antwort enthält nur nicht-druckbare Zeichen")
            return true
        }
        
        // Prüfe auf verdächtig kurze Antworten mit ungewöhnlichen Zeichen
        if (response.length <= 3 && response.any { it.code < 32 || it.code > 126 }) {
            Log.w("CommunicationHelper", "Verdächtig kurze Antwort mit Steuerzeichen: '$response'")
            return true
        }
        
        return false
    }
    
    /**
     * Analysiert die Kommunikation und gibt Debug-Informationen aus
     */
    fun analyzeResponse(command: ByteArray, response: String) {
        val commandStr = String(command, Charsets.US_ASCII)
        val commandHex = command.joinToString(" ") { "0x%02X".format(it) }
        val responseHex = response.toByteArray().joinToString(" ") { "0x%02X".format(it) }
        
        Log.d("CommunicationHelper", "=== Kommunikationsanalyse ===")
        Log.d("CommunicationHelper", "Befehl (String): $commandStr")
        Log.d("CommunicationHelper", "Befehl (Hex): $commandHex")
        Log.d("CommunicationHelper", "Antwort (String): '$response'")
        Log.d("CommunicationHelper", "Antwort (Hex): $responseHex")
        Log.d("CommunicationHelper", "Antwort-Länge: ${response.length}")
        
        // Warne wenn die Antwort nur das letzte Zeichen des Befehls ist
        if (response.length == 1 && command.size > 2) {
            val lastCommandChar = command[command.size - 2].toInt().toChar() // -2 wegen ETX
            if (response[0] == lastCommandChar) {
                Log.w("CommunicationHelper", "WARNUNG: Antwort ist identisch mit letztem Zeichen des Befehls!")
                Log.w("CommunicationHelper", "Dies deutet auf ein Echo oder Kommunikationsproblem hin")
            }
        }
        
        Log.d("CommunicationHelper", "=============================")
    }
    
    /**
     * Gibt Empfehlungen für die Fehlerbehandlung
     */
    fun getErrorRecommendation(response: String): String {
        return when {
            response == "7" -> "Kommunikationsfehler: Nur '7' empfangen. Prüfen Sie die RS485-Verbindung und Baudrate. Möglicherweise Verdrahtungsproblem."
            response == "2" -> "Kommunikationsfehler: Nur '2' empfangen. Mögliches Timing-Problem oder STX-Echo."
            response == "3" -> "Kommunikationsfehler: Nur '3' empfangen. ETX-Echo oder Protokollfehler möglich."
            response == "0" -> "Kommunikationsfehler: Nur '0' empfangen. Zelle antwortet nicht korrekt oder Befehlsecho."
            response.contains("\u0007") -> "BEL-Zeichen (0x07) empfangen. Schwerwiegender Kommunikationsfehler - prüfen Sie die Verkabelung und Baudrate."
            response == "BEL_ERROR" -> "BEL-Zeichen empfangen. RS485-Kommunikation gestört - prüfen Sie Verkabelung, Baudrate und Terminierung."
            response == "RAW_CTRL_CHARS_RESPONSE" -> "Nur Steuerzeichen empfangen. Protokoll-Synchronisation verloren."
            response == "PARTIAL_RESPONSE_NO_ETX" -> "Unvollständige Antwort ohne ETX. Timing-Problem oder Übertragungsfehler."
            response.length == 1 && response[0].code < 32 -> "Steuerzeichen empfangen: 0x${response[0].code.toString(16).padStart(2, '0')}. Kommunikationsfehler."
            response.length == 1 -> "Einstellige Antwort '$response'. Prüfen Sie die Verkabelung und ob es sich um ein Echo handelt."
            response.isEmpty() -> "Keine Antwort von der Zelle. Prüfen Sie die Verbindung, IP-Adresse und Port."
            response.all { it.code < 32 || it.code > 126 } -> "Nur nicht-druckbare Zeichen empfangen. Baudrate oder Protokoll-Problem."
            else -> "Unbekannter Kommunikationsfehler. Antwort: '$response'"
        }
    }
    
    /**
     * Analysiert Kommunikationsmuster und gibt detaillierte Diagnose-Informationen
     */
    fun analyzeCommunicationPattern(commandBytes: ByteArray, response: String): CommunicationAnalysis {
        val analysis = CommunicationAnalysis()
        
        analysis.commandHex = commandBytes.joinToString(" ") { "0x%02X".format(it) }
        analysis.responseHex = response.toByteArray().joinToString(" ") { "0x%02X".format(it) }
        analysis.isSuspicious = isSuspiciousResponse(response)
        
        // Prüfe auf Echo-Muster
        if (response.length == 1 && commandBytes.size > 2) {
            val lastCommandChar = commandBytes[commandBytes.size - 2].toInt().toChar() // -2 wegen ETX
            if (response[0] == lastCommandChar) {
                analysis.isEcho = true
                analysis.echoType = "Letztes Befehlszeichen"
            }
        }
        
        // Prüfe STX/ETX Echos
        if (response == "\u0002") {
            analysis.isEcho = true
            analysis.echoType = "STX-Echo"
        } else if (response == "\u0003") {
            analysis.isEcho = true
            analysis.echoType = "ETX-Echo"
        }
        
        // Timing-Analyse
        analysis.hasTimingIssues = response.length == 1 || response.isEmpty()
        
        // Pattern-Erkennung
        analysis.pattern = when {
            response == "7" -> "VERDRAHTUNGSFEHLER_7"
            response.length == 1 && response[0].isDigit() -> "EINZELZIFFER_ECHO"
            response.all { it.code < 32 } -> "NUR_STEUERZEICHEN"
            response.contains("\u0007") -> "BEL_FEHLER"
            else -> "UNBEKANNT"
        }
        
        return analysis
    }
    
    /**
     * Datenklasse für Kommunikationsanalyse
     */
    data class CommunicationAnalysis(
        var commandHex: String = "",
        var responseHex: String = "",
        var isSuspicious: Boolean = false,
        var isEcho: Boolean = false,
        var echoType: String = "",
        var hasTimingIssues: Boolean = false,
        var pattern: String = "NORMAL"
    ) {
        fun getDetailedReport(): String {
            val report = StringBuilder()
            report.appendLine("=== Kommunikationsanalyse ===")
            report.appendLine("Befehl (Hex): $commandHex")
            report.appendLine("Antwort (Hex): $responseHex")
            report.appendLine("Verdächtig: ${if (isSuspicious) "JA" else "NEIN"}")
            if (isEcho) {
                report.appendLine("Echo erkannt: $echoType")
            }
            if (hasTimingIssues) {
                report.appendLine("Timing-Probleme: Möglich")
            }
            report.appendLine("Pattern: $pattern")
            report.appendLine("===========================")
            return report.toString()
        }
    }
}