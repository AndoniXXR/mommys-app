package com.mommys.app.ui.main

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.mommys.app.R
import com.mommys.app.data.db.seen.AppSeenDatabase
import com.mommys.app.data.model.Post
import com.mommys.app.databinding.ItemGridPostBinding
import com.mommys.app.ui.views.AspectRatioImageView

/**
 * Adapter para el grid de posts
 * Similar a ei.m de la app original
 * 
 * Soporta selección múltiple mediante long press como la app original.
 * Usa SelectionCallback para notificar cambios de selección a la Activity.
 */
class PostsAdapter(
    private val onPostClick: (Post) -> Unit,
    private val onInfoClick: ((Post) -> Unit)? = null,
    private val selectionCallback: SelectionCallback? = null
) : ListAdapter<Post, PostsAdapter.PostViewHolder>(PostDiffCallback()) {
    
    // Set de IDs de posts seleccionados (como this.T en MainActivity original)
    private val selectedPostIds = mutableSetOf<Int>()
    
    // Ratio de aspecto para las imágenes (grid_height / 100.0)
    // Valores: 1.0 (cuadrado) a 2.0 (muy alto)
    var aspectRatio: Double = 1.1
        set(value) {
            field = value.coerceIn(1.0, 2.0)
        }
    
    // Opciones de visualización como en la app original
    var showStats: Boolean = true           // grid_stats - Mostrar score, favs, rating
    var showInfoButton: Boolean = false     // grid_info - Mostrar botón de info
    var showStatusColors: Boolean = true    // grid_colours - Colores de estado (pending/flagged)
    var showNewLabel: Boolean = false       // grid_new_label - Mostrar icono de nuevo
    var darkenSeen: Boolean = false         // grid_darken_seen - Oscurecer posts vistos
    var hideSeen: Boolean = false           // grid_hide_seen - Ocultar posts vistos
    var showGifs: Boolean = true            // grid_gifs - Auto-reproducir GIFs
    
    // Set de IDs de posts vistos (cargado una vez para toda la lista)
    var seenPostIds: Set<Int> = emptySet()
    
    /**
     * Toggle de selección para un post
     * Similar a m.a(l, ii.m) de la app original (líneas 74-94)
     * 
     * @param post El post a seleccionar/deseleccionar
     * @param holder El ViewHolder para actualizar la UI
     */
    fun toggleSelection(post: Post, holder: PostViewHolder) {
        val wasSelected = selectedPostIds.contains(post.id)
        
        if (wasSelected) {
            // Deseleccionar
            selectedPostIds.remove(post.id)
            holder.setSelected(false)
            selectionCallback?.onPostDeselected(post)
        } else {
            // Seleccionar
            selectedPostIds.add(post.id)
            holder.setSelected(true)
            selectionCallback?.onPostSelected(post)
        }
    }
    
    /**
     * Verifica si un post está seleccionado
     */
    fun isSelected(post: Post): Boolean {
        return selectedPostIds.contains(post.id)
    }
    
    /**
     * Selecciona todos los posts de la lista actual
     * Similar a selected_select_all de la app original
     */
    fun selectAll() {
        currentList.forEach { post ->
            if (!selectedPostIds.contains(post.id)) {
                selectedPostIds.add(post.id)
                selectionCallback?.onPostSelected(post)
            }
        }
        notifyDataSetChanged()
    }
    
    /**
     * Limpia todas las selecciones
     * Similar a MainActivity.H() de la app original
     */
    fun clearAllSelections() {
        currentList.forEach { post ->
            if (selectedPostIds.contains(post.id)) {
                selectedPostIds.remove(post.id)
                selectionCallback?.onPostDeselected(post)
            }
        }
        selectedPostIds.clear()
        notifyDataSetChanged()
    }
    
    /**
     * Limpia solo las selecciones locales sin callbacks
     * Usado cuando se sincroniza con el estado global
     */
    fun clearLocalSelections() {
        selectedPostIds.clear()
        notifyDataSetChanged()
    }
    
    /**
     * Sincroniza el estado de selección con un set externo
     */
    fun syncSelections(selectedIds: Set<Int>) {
        selectedPostIds.clear()
        selectedPostIds.addAll(selectedIds)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemGridPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class PostViewHolder(
        private val binding: ItemGridPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            // Click normal - abrir post
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val post = getItem(position)
                    // Si hay selecciones activas, el click también hace toggle
                    // (como en la app original, una vez en modo selección, 
                    // tanto click como long click hacen toggle)
                    if (selectionCallback?.getSelectedCount() ?: 0 > 0) {
                        toggleSelection(post, this)
                        performHapticFeedback()
                    } else {
                        onPostClick(post)
                    }
                }
            }
            
            // Long click - toggle selección con vibración
            // Similar a ei.r de la app original
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val post = getItem(position)
                    toggleSelection(post, this)
                    performHapticFeedback()
                }
                true
            }
            
            // Click en botón de info (!) - mostrar popup de info
            // Similar a qi.a.k() de la app original
            binding.imgInfoBtn.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val post = getItem(position)
                    onInfoClick?.invoke(post)
                }
            }
        }
        
        /**
         * Vibración corta al seleccionar (5ms como la app original)
         * Similar a ei.m.a() líneas 85-93
         */
        private fun performHapticFeedback() {
            val context = binding.root.context
            val vibrator = context.getSystemService<Vibrator>()
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(5L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(5L)
                }
            }
        }
        
        /**
         * Actualiza la UI para mostrar/ocultar el checkmark de selección
         */
        fun setSelected(selected: Boolean) {
            binding.imgSelected.visibility = if (selected) View.VISIBLE else View.GONE
        }
        
        fun bind(post: Post) {
            // Actualizar el ratio de aspecto (puede cambiar desde Settings)
            (binding.imgPreview as? AspectRatioImageView)?.aspectRatio = aspectRatio
            
            // Cargar imagen con Glide
            val imageUrl = post.preview.url ?: post.sample?.url ?: post.file.url
            
            Glide.with(binding.imgPreview)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .centerCrop()
                .into(binding.imgPreview)
            
            // Indicador de tipo (video, gif, etc) - usar views separados como el original
            val fileExt = post.file.ext.lowercase()
            
            when (fileExt) {
                "webm", "mp4" -> {
                    binding.imgVideo.visibility = android.view.View.VISIBLE
                    binding.imgGif.visibility = android.view.View.GONE
                }
                "gif" -> {
                    binding.imgVideo.visibility = android.view.View.GONE
                    binding.imgGif.visibility = android.view.View.VISIBLE
                }
                else -> {
                    binding.imgVideo.visibility = android.view.View.GONE
                    binding.imgGif.visibility = android.view.View.GONE
                }
            }
            
            // Overlay para posts vistos (darkenSeen)
            // Usa post.isSeen (campo mutable) O seenPostIds (para posts cargados de DB al inicio)
            val isPostSeen = post.isSeen || seenPostIds.contains(post.id)
            if (darkenSeen && isPostSeen) {
                binding.fLOverlay.visibility = android.view.View.VISIBLE
                binding.fLOverlay.setBackgroundColor(0x80000000.toInt()) // 50% negro
            } else {
                binding.fLOverlay.visibility = android.view.View.GONE
            }
            
            // Checkmark de selección - mostrar si el post está seleccionado
            binding.imgSelected.visibility = if (selectedPostIds.contains(post.id)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            // Badge de nuevo (showNewLabel) - mostrar si NO está visto
            // Usa post.isSeen (campo mutable) O seenPostIds (para posts cargados de DB al inicio)
            if (showNewLabel && !isPostSeen) {
                binding.imgNewLabel.visibility = android.view.View.VISIBLE
            } else {
                binding.imgNewLabel.visibility = android.view.View.GONE
            }
            
            // Botón de info
            binding.imgInfoBtn.visibility = if (showInfoButton) android.view.View.VISIBLE else android.view.View.GONE
            
            // Texto de stats: score, favs, rating - exacto como la app original
            if (showStats) {
                binding.txtInfo.visibility = android.view.View.VISIBLE
                binding.txtInfo.text = android.text.Html.fromHtml(buildStatsText(post), android.text.Html.FROM_HTML_MODE_COMPACT)
            } else {
                binding.txtInfo.visibility = android.view.View.GONE
            }
            
            // Colores de estado (azul=pending, rojo=flagged)
            if (showStatusColors) {
                when {
                    post.flags.pending -> binding.fLFlag.setBackgroundColor(0x400000FF) // Azul semi-transparente
                    post.flags.flagged -> binding.fLFlag.setBackgroundColor(0x40FF0000) // Rojo semi-transparente
                    else -> binding.fLFlag.setBackgroundColor(0x00000000) // Transparente
                }
            } else {
                binding.fLFlag.setBackgroundColor(0x00000000)
            }
        }
        
        /**
         * Construye el texto de stats con HTML coloreado como la app original
         * Formato: ↑42 ♥15 Q o ↓-5 ♥3 C S
         */
        private fun buildStatsText(post: Post): String {
            val sb = StringBuilder()
            
            // Score con color
            val score = post.score.total
            when {
                score > 0 -> sb.append("<font color='#00AA00'>↑</font>")
                score < 0 -> sb.append("<font color='#DD0000'>↓</font>")
                else -> sb.append("↕")
            }
            sb.append(score)
            sb.append(" ")
            
            // Favoritos
            sb.append("<font color='#DD0000'>♥</font>")
            sb.append(post.favCount)
            sb.append(" ")
            
            // Comentarios (si hay)
            if (post.commentCount > 0) {
                sb.append("C ")
            }
            
            // Rating con color
            when (post.rating.lowercase()) {
                "q" -> sb.append("<font color='#DDDD00'>Q</font>")
                "s" -> sb.append("<font color='#00AA00'>S</font>")
                else -> sb.append("<font color='#DD0000'>E</font>")
            }
            
            // Parent/Children
            post.relationships?.let { rel ->
                if (rel.parentId != null && rel.parentId > 0) {
                    sb.append(" <font color='#FFA500'>P</font>")
                }
                if (rel.hasChildren) {
                    sb.append(" <font color='#FFA500'>C</font>")
                }
            }
            
            // Pools
            if (!post.pools.isNullOrEmpty()) {
                sb.append(" <font color='#00AA00'>P</font>")
            }
            
            return sb.toString()
        }
    }
    
    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
