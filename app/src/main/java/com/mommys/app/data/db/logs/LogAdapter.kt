package com.mommys.app.data.db.logs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying log entries in a RecyclerView
 */
class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
    
    private val logs = mutableListOf<LogEntity>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    fun setLogs(newLogs: List<LogEntity>) {
        logs.clear()
        logs.addAll(newLogs)
        notifyDataSetChanged()
    }
    
    fun getLogs(): List<LogEntity> = logs.toList()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }
    
    override fun getItemCount(): Int = logs.size
    
    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtTimestamp: TextView = itemView.findViewById(R.id.txtTimestamp)
        private val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
        
        fun bind(log: LogEntity) {
            txtTimestamp.text = dateFormat.format(Date(log.timestamp))
            txtMessage.text = log.message
        }
    }
}
