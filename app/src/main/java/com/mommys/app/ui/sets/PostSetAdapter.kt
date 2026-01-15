package com.mommys.app.ui.sets

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.R
import com.mommys.app.data.model.PostSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostSetAdapter(
    private val onSetClick: (PostSet) -> Unit,
    private val isSelectMode: Boolean = false
) : ListAdapter<PostSet, PostSetAdapter.SetViewHolder>(SetDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_set, parent, false)
        return SetViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val constraintLayout: ConstraintLayout = itemView.findViewById(R.id.constraintLayout)
        private val txtName: TextView = itemView.findViewById(R.id.txtName)
        private val txtShortName: TextView = itemView.findViewById(R.id.txtShortName)
        private val txtInfo: TextView = itemView.findViewById(R.id.txtInfo)
        private val txtDescription: TextView = itemView.findViewById(R.id.txtDescription)
        private val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        
        fun bind(set: PostSet) {
            val context = itemView.context
            
            // Name
            txtName.text = set.name
            
            // Short name
            txtShortName.text = set.shortname
            
            // Info: Posts, id, user, public (como el original)
            txtInfo.text = context.getString(
                R.string.browse_sets_item_info,
                set.postCount,
                set.id,
                set.creatorId,
                set.isPublic
            )
            
            // Description
            if (!set.description.isNullOrEmpty()) {
                val descHtml = context.getString(R.string.browse_sets_item_description, set.description)
                txtDescription.text = Html.fromHtml(descHtml, Html.FROM_HTML_MODE_COMPACT)
                txtDescription.visibility = View.VISIBLE
            } else {
                txtDescription.visibility = View.GONE
            }
            
            // Dates (como el original con formato yyyy-MM-dd HH:mm:ss)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
            val createdStr = try {
                set.createdAt?.let { parseIsoDate(it)?.let { d -> dateFormat.format(d) } } ?: "?"
            } catch (e: Exception) { "?" }
            val updatedStr = try {
                set.updatedAt?.let { parseIsoDate(it)?.let { d -> dateFormat.format(d) } } ?: "?"
            } catch (e: Exception) { "?" }
            txtDate.text = context.getString(R.string.browse_sets_item_date, createdStr, updatedStr)
            
            // En modo selección, ocultar algunos campos
            if (isSelectMode) {
                txtShortName.visibility = View.GONE
                txtInfo.visibility = View.GONE
                txtDescription.visibility = View.GONE
            } else {
                txtShortName.visibility = View.VISIBLE
                txtInfo.visibility = View.VISIBLE
            }
            
            constraintLayout.setOnClickListener {
                onSetClick(set)
            }
        }
        
        private fun parseIsoDate(isoString: String): Date? {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                format.parse(isoString.substringBefore("."))
            } catch (e: Exception) {
                null
            }
        }
    }
    
    class SetDiffCallback : DiffUtil.ItemCallback<PostSet>() {
        override fun areItemsTheSame(oldItem: PostSet, newItem: PostSet): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: PostSet, newItem: PostSet): Boolean {
            return oldItem == newItem
        }
    }
}
