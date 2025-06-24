package com.example.servicetool

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Modern notification helper for Android 14+
 * Handles notification channels and modern notification features
 */
object NotificationHelper {
    
    const val CHANNEL_CONNECTION = "connection_status"
    const val CHANNEL_UPDATES = "app_updates"
    const val CHANNEL_SYSTEM = "system_alerts"
    
    fun createNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Connection Status Channel
        val connectionChannel = NotificationChannel(
            CHANNEL_CONNECTION,
            "Verbindungsstatus",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen über Moxa und Zellen-Verbindungen"
            enableVibration(false)
            setShowBadge(true)
        }
        
        // App Updates Channel
        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "App Updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Benachrichtigungen über verfügbare App-Updates"
            enableVibration(false)
            setShowBadge(false)
        }
        
        // System Alerts Channel
        val systemChannel = NotificationChannel(
            CHANNEL_SYSTEM,
            "System Warnungen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wichtige System- und Fehler-Benachrichtigungen"
            enableVibration(true)
            setShowBadge(true)
        }
        
        notificationManager.createNotificationChannels(listOf(
            connectionChannel,
            updatesChannel,
            systemChannel
        ))
    }
}