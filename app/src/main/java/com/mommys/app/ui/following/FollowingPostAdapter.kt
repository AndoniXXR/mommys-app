package com.mommys.app.ui.following

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager

/**
 * Adapter para el grid de posts en FollowingPostActivity
 * Implementación completa como C1881m en la app original
 */
class FollowingPostAdapter(
    private val items: List<FollowingPostItem>,
    private val prefsManager: PreferencesManager,
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Unit,
    private val onInfoClick: (Int) -> Unit,
    private val onSelectionChanged: (FollowingPostItem, Boolean) -> Unit
) : RecyclerView.Adapter<FollowingPostAdapter.ViewHolder>() {
    
    private val selectedPositions = mutableSetOf<Int>()
    private var selectionMode = false
    
    // Preferencias de grid (como la app original)
    private var showGifs: Boolean = prefsManager.gridGifs
    private var showInfoBtn: Boolean = prefsManager.gridInfo
    private var showStats: Boolean = prefsManager.gridStats
    private var showColours: Boolean = prefsManager.gridColours
    private var showNewLabel: Boolean = prefsManager.gridNewLabel
    private var darkenSeen: Boolean = prefsManager.gridDarkenSeen
    private var thumbQuality: Int = prefsManager.getThumbQuality()
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fLFlag: FrameLayout = view.findViewById(R.id.fLFlag)
        val imgPreview: ImageView = view.findViewById(R.id.imgPreview)
        val imgSelected: ImageView = view.findViewById(R.id.imgSelected)
        val txtInfo: TextView = view.findViewById(R.id.txtInfo)
        val imgInfoBtn: ImageView = view.findViewById(R.id.imgInfoBtn)
        val imgNewLabel: ImageView = view.findViewById(R.id.imgNewLabel)
        val imgVideo: ImageView = view.findViewById(R.id.imgVideo)
        val imgGif: ImageView = view.findViewById(R.id.imgGif)
        val fLOverlay: FrameLayout = view.findViewById(R.id.fLOverlay)
        val tagIndicator: View? = view.findViewById(R.id.tagIndicator)
        
        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (selectionMode) {
                        toggleSelection(pos)
                    } else {
                        onItemClick(pos)
                    }
                }
            }
            
            view.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (!selectionMode) {
                        // Entrar en modo selección
                        selectionMode = true
                        toggleSelection(pos)
                    } else {
                        onItemLongClick(pos)
                    }
                }
                true
            }
            
            // Botón info
            imgInfoBtn.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onInfoClick(pos)
                }
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_following_post, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        
        // Determinar URL según calidad de thumbnail
        val imageUrl = when (thumbQuality) {
            1 -> item.previewUrl  // Baja calidad
            2 -> item.previewUrl  // Media calidad (preview)
            else -> item.previewUrl // Alta calidad
        }
        
        // Cargar imagen
        if (!imageUrl.isNullOrEmpty()) {
            val glideRequest = Glide.with(context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
            
            // Si showGifs está activo y es un GIF, usar asGif()
            if (showGifs && item.fileExt?.lowercase() == "gif") {
                glideRequest.into(holder.imgPreview)
            } else {
                glideRequest.into(holder.imgPreview)
            }
        } else {
            holder.imgPreview.setImageResource(R.drawable.ic_menu_gallery)
        }
        
        // Mostrar checkmark si está seleccionado
        val isSelected = selectedPositions.contains(position)
        holder.imgSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
        
        // Mostrar stats (score, favs)
        if (showStats) {
            holder.txtInfo.visibility = View.VISIBLE
            holder.txtInfo.text = "↑${item.score} ♥${item.favCount}"
        } else {
            holder.txtInfo.visibility = View.GONE
        }
        
        // Botón info
        holder.imgInfoBtn.visibility = if (showInfoBtn) View.VISIBLE else View.GONE
        
        // Etiqueta NEW (para posts de últimas 24 horas)
        val isNew = (System.currentTimeMillis() - item.addedDate) < 24 * 60 * 60 * 1000
        holder.imgNewLabel.visibility = if (showNewLabel && isNew) View.VISIBLE else View.GONE
        
        // Indicadores de tipo de media
        val ext = item.fileExt?.lowercase() ?: ""
        when {
            ext in listOf("webm", "mp4") -> {
                holder.imgVideo.visibility = View.VISIBLE
                holder.imgGif.visibility = View.GONE
            }
            ext == "gif" -> {
                holder.imgVideo.visibility = View.GONE
                holder.imgGif.visibility = View.VISIBLE
            }
            else -> {
                holder.imgVideo.visibility = View.GONE
                holder.imgGif.visibility = View.GONE
            }
        }
        
        // Colorear borde según rating
        if (showColours) {
            val borderColor = when (item.rating?.lowercase()) {
                "e" -> Color.parseColor("#FF5252")  // Rojo
                "q" -> Color.parseColor("#FFEB3B")  // Amarillo
                "s" -> Color.parseColor("#4CAF50")  // Verde
                else -> Color.TRANSPARENT
            }
            holder.fLFlag.setBackgroundColor(borderColor)
        } else {
            holder.fLFlag.setBackgroundColor(Color.TRANSPARENT)
        }
        
        // Overlay oscuro para posts vistos (darken_seen)
        // TODO: Implementar tracking de posts vistos
        holder.fLOverlay.visibility = View.GONE
        
        // Indicador de tag (si está disponible)
        holder.tagIndicator?.visibility = 
            if (!item.queryString.isNullOrEmpty()) View.VISIBLE else View.GONE
    }
    
    override fun getItemCount(): Int = items.size
    
    private fun toggleSelection(position: Int) {
        if (position < 0 || position >= items.size) return
        
        val context = items[position].let { item ->
            // Obtener contexto para vibración
            null
        }
        
        val item = items[position]
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
            onSelectionChanged(item, false)
        } else {
            selectedPositions.add(position)
            onSelectionChanged(item, true)
        }
        
        // Salir de modo selección si no hay seleccionados
        if (selectedPositions.isEmpty()) {
            selectionMode = false
        }
        
        notifyItemChanged(position)
    }
    
    /**
     * Vibración háptica de 5ms al seleccionar (como la app original)
     */
    fun vibrateOnSelect(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(5L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(5L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(5L)
                }
            }
        } catch (e: Exception) {
            // Ignorar errores de vibración
        }
    }
    
    fun clearSelections() {
        selectedPositions.clear()
        selectionMode = false
        notifyDataSetChanged()
    }
    
    fun getSelectedCount(): Int = selectedPositions.size
    
    fun getSelectedItems(): List<FollowingPostItem> {
        return selectedPositions.mapNotNull { pos ->
            if (pos < items.size) items[pos] else null
        }
    }
    
    fun isInSelectionMode(): Boolean = selectionMode
    
    fun refreshPreferences() {
        showGifs = prefsManager.gridGifs
        showInfoBtn = prefsManager.gridInfo
        showStats = prefsManager.gridStats
        showColours = prefsManager.gridColours
        showNewLabel = prefsManager.gridNewLabel
        darkenSeen = prefsManager.gridDarkenSeen
        thumbQuality = prefsManager.getThumbQuality()
        notifyDataSetChanged()
    }
}
