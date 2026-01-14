package com.mommys.app.ui.saved

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.R
import com.mommys.app.data.database.SavedSearchEntity

/**
 * Adapter para la lista de búsquedas guardadas
 * Similar al adapter de SavedSearchesActivity de la app original
 */
class SavedSearchesAdapter(
    private val onItemClick: (SavedSearchEntity) -> Unit,
    private val onMoreClick: (SavedSearchEntity) -> Unit
) : ListAdapter<SavedSearchEntity, SavedSearchesAdapter.ViewHolder>(DIFF_CALLBACK) {
    
    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SavedSearchEntity>() {
            override fun areItemsTheSame(oldItem: SavedSearchEntity, newItem: SavedSearchEntity): Boolean {
                return oldItem.id == newItem.id
            }
            
            override fun areContentsTheSame(oldItem: SavedSearchEntity, newItem: SavedSearchEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_search, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textQuery: TextView = itemView.findViewById(R.id.textQuery)
        private val textInfo: TextView = itemView.findViewById(R.id.textInfo)
        private val badgeNew: TextView = itemView.findViewById(R.id.badgeNew)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
        
        fun bind(search: SavedSearchEntity) {
            textName.text = search.name
            textQuery.text = search.query
            
            // Info: página y último uso
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                search.lastUsedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            textInfo.text = itemView.context.getString(
                R.string.saved_page_info, 
                search.page,
                relativeTime
            )
            
            // Badge de nuevos posts (si tiene contador)
            if (search.useCount > 0) {
                // Podríamos usar este campo para nuevos posts en el futuro
                badgeNew.visibility = View.GONE
            } else {
                badgeNew.visibility = View.GONE
            }
            
            // Click en item
            itemView.setOnClickListener { onItemClick(search) }
            
            // Click en botón más
            btnMore.setOnClickListener { onMoreClick(search) }
        }
    }
}
