package com.mommys.app.ui.downloads

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mommys.app.R
import com.mommys.app.data.db.downloads.AppDownloadsDatabase
import com.mommys.app.data.db.downloads.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Adapter para la lista de descargas en cola
 * Como di/i.java en la app original
 */
class DownloadAdapter(
    private val items: MutableList<DownloadItem>,
    private val database: AppDownloadsDatabase,
    private val onItemRemoved: (Int) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.ViewHolder>() {
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view as CardView
        val imgThumb: ImageView = view.findViewById(R.id.imgThumb)
        val txtPostID: TextView = view.findViewById(R.id.txtPostID)
        val txtPostDetails: TextView = view.findViewById(R.id.txtPostDetails)
        val imgDelete: ImageView = view.findViewById(R.id.imgDelete)
        val errorLayout: LinearLayout = view.findViewById(R.id.errorLayout)
        val txtError: TextView = view.findViewById(R.id.txtError)
        val btnRetry: Button = view.findViewById(R.id.btnRetry)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        
        // Cargar thumbnail
        if (!item.thumbUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(item.thumbUrl)
                .placeholder(R.drawable.ic_menu_gallery)
                .into(holder.imgThumb)
        } else {
            holder.imgThumb.setImageResource(R.drawable.ic_menu_gallery)
        }
        
        // Título: "#postID - artists"
        val artists = item.artists ?: "Unknown"
        holder.txtPostID.text = context.getString(R.string.download_item_title, item.postId, artists)
        
        // Detalles: "EXT, SIZE MB, URL"
        val ext = item.fileExt?.uppercase(Locale.ENGLISH) ?: "?"
        val sizeMB = String.format(Locale.ENGLISH, "%.2f MB", item.fileSize / 1000000.0)
        holder.txtPostDetails.text = context.getString(R.string.download_item_details, ext, sizeMB, item.fileUrl)
        
        // Botón eliminar con feedback visual
        holder.imgDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                deleteItem(holder, pos, item)
            }
        }
        
        // Mostrar error si existe
        if (item.error != null) {
            holder.errorLayout.visibility = View.VISIBLE
            holder.txtError.text = context.getString(R.string.download_error_message, item.error)
            holder.btnRetry.setOnClickListener {
                retryItem(holder.bindingAdapterPosition, item)
            }
        } else {
            holder.errorLayout.visibility = View.GONE
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    private fun deleteItem(holder: ViewHolder, position: Int, item: DownloadItem) {
        // Feedback visual como la app original: deshabilitar y reducir opacidad
        holder.imgDelete.isEnabled = false
        holder.cardView.alpha = 0.5f
        
        CoroutineScope(Dispatchers.IO).launch {
            database.downloadDao().deleteByFileUrl(item.fileUrl)
            Handler(Looper.getMainLooper()).post {
                if (position < items.size) {
                    items.removeAt(position)
                    notifyItemRemoved(position)
                    onItemRemoved(items.size)
                }
            }
        }
    }
    
    private fun retryItem(position: Int, item: DownloadItem) {
        CoroutineScope(Dispatchers.IO).launch {
            // Primero eliminar de la lista actual
            database.downloadDao().deleteByFileUrl(item.fileUrl)
            
            // Re-encolar el item sin error (como hace la app original)
            val newItem = item.copy(error = null)
            database.downloadDao().insert(newItem)
            
            Handler(Looper.getMainLooper()).post {
                if (position < items.size) {
                    items.removeAt(position)
                    items.add(newItem)  // Añadir al final de la cola
                    notifyItemRemoved(position)
                    notifyItemInserted(items.size - 1)
                }
            }
        }
    }
}
