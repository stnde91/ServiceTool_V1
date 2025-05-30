package com.example.servicetool

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CellAdapter(private val cellDataList: List<String>) :
    RecyclerView.Adapter<CellAdapter.CellViewHolder>() {

    class CellViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cellNumberTextView: TextView = itemView.findViewById(R.id.cell_number)
        val cellValueTextView: TextView = itemView.findViewById(R.id.cell_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.cell_item, parent, false)
        return CellViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        val currentItem = cellDataList[position]

        val parts = currentItem.split(" ")
        if (parts.size == 2) {
            holder.cellNumberTextView.text = parts[1]
        } else {
            holder.cellNumberTextView.text = "?"
        }

        holder.cellValueTextView.text = ""

        if (position == 0) {
            holder.itemView.setBackgroundColor(Color.parseColor("#5F9EA0")) // CadetBlue
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun getItemCount() = cellDataList.size
}