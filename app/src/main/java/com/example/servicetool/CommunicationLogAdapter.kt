package com.example.servicetool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat

data class CommunicationLogEntry(
    val timestamp: Long,
    val direction: String, // "→" for outgoing, "←" for incoming
    val message: String,
    val status: String, // "SUCCESS", "ERROR", "TIMEOUT"
    val duration: Long? = null // in milliseconds
)

class CommunicationLogAdapter : RecyclerView.Adapter<CommunicationLogAdapter.LogViewHolder>() {

    private val logEntries = mutableListOf<CommunicationLogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val maxEntries = 50 // Maximum number of log entries to keep

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTime: TextView = itemView.findViewById(R.id.textLogTime)
        val textDirection: TextView = itemView.findViewById(R.id.textLogDirection)
        val textMessage: TextView = itemView.findViewById(R.id.textLogMessage)
        val textStatus: TextView = itemView.findViewById(R.id.textLogStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_communication_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logEntries[position]
        
        holder.textTime.text = timeFormat.format(Date(entry.timestamp))
        holder.textDirection.text = entry.direction
        holder.textMessage.text = entry.message
        
        // Status with duration if available
        val statusText = if (entry.duration != null) {
            "${entry.status} (${entry.duration}ms)"
        } else {
            entry.status
        }
        holder.textStatus.text = statusText
        
        // Color coding based on status
        val context = holder.itemView.context
        val textColor = when (entry.status) {
            "SUCCESS" -> context.getColor(R.color.status_success_color)
            "ERROR" -> context.getColor(R.color.status_error_color)
            "TIMEOUT" -> context.getColor(R.color.status_warning)
            else -> context.getColor(R.color.md_theme_light_onSurfaceVariant)
        }
        holder.textStatus.setTextColor(textColor)
        
        // Direction color
        val directionColor = when (entry.direction) {
            "→" -> context.getColor(R.color.md_theme_light_primary)
            "←" -> context.getColor(R.color.md_theme_light_secondary)
            else -> context.getColor(R.color.md_theme_light_onSurfaceVariant)
        }
        holder.textDirection.setTextColor(directionColor)
    }

    override fun getItemCount(): Int = logEntries.size

    fun addLogEntry(entry: CommunicationLogEntry) {
        logEntries.add(0, entry) // Add to top
        
        // Remove oldest entries if we exceed the limit
        if (logEntries.size > maxEntries) {
            logEntries.removeLastOrNull()
        }
        
        notifyItemInserted(0)
        
        // Auto-scroll to top to show newest entry
        if (logEntries.size > 1) {
            notifyItemRangeChanged(0, logEntries.size)
        }
    }

    fun clearLog() {
        val oldSize = logEntries.size
        logEntries.clear()
        notifyItemRangeRemoved(0, oldSize)
    }

    fun getLogEntries(): List<CommunicationLogEntry> = logEntries.toList()
}