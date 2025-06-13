package com.example.servicetool

/**
 * Datenklassen für Flintec RC3D Antworten
 */
sealed class FlintecData {
    data class SerialNumber(val value: String) : FlintecData()
    data class Counts(val value: String) : FlintecData()
    data class Baudrate(val value: String) : FlintecData()
    data class Temperature(val value: String) : FlintecData()
    data class Filter(val value: String) : FlintecData()
    data class FilterSetResult(val success: Boolean) : FlintecData()
    data class Version(val value: String) : FlintecData()
    data class Unknown(val value: String) : FlintecData()
}