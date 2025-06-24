package com.example.servicetool

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuickActionAdapter(
    private val quickActions: List<DashboardFragment.QuickAction>
) : RecyclerView.Adapter<QuickActionAdapter.QuickActionViewHolder>() {

    class QuickActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconQuickAction: ImageView = itemView.findViewById(R.id.iconQuickAction)
        val textQuickActionTitle: TextView = itemView.findViewById(R.id.textQuickActionTitle)
        val textQuickActionDescription: TextView = itemView.findViewById(R.id.textQuickActionDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickActionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_action, parent, false)
        return QuickActionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: QuickActionViewHolder, position: Int) {
        val currentAction = quickActions[position]

        holder.iconQuickAction.setImageResource(currentAction.iconRes)
        holder.textQuickActionTitle.text = currentAction.title
        holder.textQuickActionDescription.text = currentAction.description

        // Set click listener
        holder.itemView.setOnClickListener {
            currentAction.action()
        }
    }

    override fun getItemCount() = quickActions.size
}