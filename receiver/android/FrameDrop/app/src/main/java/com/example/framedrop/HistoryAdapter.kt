package com.example.framedrop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val fileName: String,
    val timeTaken: String,
    val size: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val items = ArrayList<HistoryItem>()

    fun addItem(item: HistoryItem) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    fun setItems(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileNameText)
        val fileSize: TextView = view.findViewById(R.id.fileSizeText)
        val timeTaken: TextView = view.findViewById(R.id.timeText)
        val timestamp: TextView = view.findViewById(R.id.timestampText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.fileName.text = item.fileName
        holder.fileSize.text = item.size
        holder.timeTaken.text = item.timeTaken
        holder.timestamp.text = formatTime(item.timestamp)
    }

    override fun getItemCount(): Int = items.size

    private fun formatTime(time: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(time))
    }
}
