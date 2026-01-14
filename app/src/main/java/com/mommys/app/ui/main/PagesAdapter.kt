package com.mommys.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mommys.app.R
import com.mommys.app.data.model.Post

/**
 * Adaptador para el ViewPager2 que maneja las páginas.
 * Similar a ei.k de la app original.
 * 
 * Cada posición del ViewPager corresponde a un PageHandler que mantiene
 * su propio estado y lista de posts.
 */
class PagesAdapter(
    private val pageHandlers: MutableList<PageHandler>,
    private val viewPager: ViewPager2
) : RecyclerView.Adapter<PagesAdapter.PageViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false) as FrameLayout
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        if (position < pageHandlers.size) {
            val pageHandler = pageHandlers[holder.bindingAdapterPosition]
            
            // Bindear el ViewHolder con el PageHandler
            pageHandler.bindViewHolder(
                holder.recyclerView,
                holder.progressBar,
                holder.txtEmpty,
                holder.txtError,
                holder.btnRetry
            )
            
            // Configurar botón retry
            holder.btnRetry.setOnClickListener {
                pageHandler.retry()
            }
        }
    }

    override fun getItemCount(): Int = pageHandlers.size

    /**
     * Notifica que se insertaron nuevas páginas
     */
    fun notifyPagesInserted(startPosition: Int, count: Int) {
        notifyItemRangeInserted(startPosition, count)
    }
    
    /**
     * Notifica que todas las páginas cambiaron (para refresh)
     */
    fun notifyPagesChanged() {
        notifyDataSetChanged()
    }

    /**
     * ViewHolder para cada página.
     * Similar a wi.a de la app original.
     */
    class PageViewHolder(view: FrameLayout) : RecyclerView.ViewHolder(view) {
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val txtEmpty: TextView = view.findViewById(R.id.txtPageEmpty)
        val txtError: TextView = view.findViewById(R.id.txtError)
        val btnRetry: ImageButton = view.findViewById(R.id.btnRetry)
    }
}
