package com.mommys.app.ui.post

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.mommys.app.R
import com.mommys.app.data.model.Post
import com.mommys.app.data.model.getVideoUrl
import com.mommys.app.data.model.isVideo
import com.mommys.app.data.model.supportsNotes
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ItemPostPageBinding
import com.mommys.app.service.FollowingJobService
import com.mommys.app.util.ProgressDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Acciones del menú de post que se pueden delegar a la Activity
 */
sealed class PostMenuAction {
    data class Slideshow(val post: Post) : PostMenuAction()
    data class EditPost(val post: Post) : PostMenuAction()
    data class AddToSet(val post: Post) : PostMenuAction()
    data class ReloadPost(val post: Post, val position: Int) : PostMenuAction()
    data class CheckNotes(val post: Post) : PostMenuAction()
}

/**
 * Adapter para ViewPager2 que muestra cada post individual
 * Permite swipe horizontal entre posts como la app original
 * 
 * Manejo de players de video:
 * - HashMap G almacena players por posición (como la app original)
 * - pauseAllPlayers() pausa todos los videos activos
 * - releaseAllPlayers() libera todos los players
 */
class PostPagerAdapter(
    private val onTagClick: (String) -> Unit,
    private val onArtistClick: (String) -> Unit,
    private val onScrollStateChanged: (Boolean) -> Unit,
    private val onVoteUp: (Post) -> Unit = {},
    private val onVoteDown: (Post) -> Unit = {},
    private val onFavorite: (Post) -> Unit = {},
    private val onComment: (Post) -> Unit = {},
    private val onDownload: (Post) -> Unit = {},
    private val onMoreOptions: (Post) -> Unit = {},
    private val onMenuAction: (PostMenuAction) -> Unit = {},
    private val onNavigateToPost: (Int) -> Unit = {},    // Navegar a post por ID (para parent/children)
    private val onNavigateToPool: (Int) -> Unit = {}     // Navegar a pool por ID
) : RecyclerView.Adapter<PostPagerAdapter.PostViewHolder>() {

    private val posts = mutableListOf<Post>()
    
    // HashMap para almacenar los players por posición (como 'G' en la app original ei/e0.java)
    private val players = mutableMapOf<Int, ExoPlayer>()
    
    // HashMap para almacenar ViewHolders por postId para poder actualizarlos cuando llega respuesta del API
    private val viewHoldersByPostId = mutableMapOf<Int, PostViewHolder>()
    
    // ==================== VIDEO PREFERENCES ====================
    private var autoPlayVideos = false       // post_autoplay_videos
    private var muteVideos = false           // post_mute_videos
    private var fullscreenVideos = false     // post_fullscreen_videos
    private var landscapeVideos = false      // post_landscape_videos
    private var videoQuality = 1             // post_default_video_quality (0=original, 1=720p, 2=480p)
    private var videoFormat = 0              // post_default_video_format (0=webm, 1=mp4)
    
    // ==================== ACTION PREFERENCES ====================
    private var upvoteOnFavorite = false     // post_action_upvote_on_fav
    private var upvoteOnDownload = false     // post_action_upvote_on_download
    private var favoriteOnDownload = false   // post_action_fav_on_download
    
    // ==================== SHARE/OPEN PREFERENCES ====================
    private var disableShare = false         // post_disable_share
    private var useE621 = false              // use e621 instead of e926

    fun submitList(newPosts: List<Post>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    fun getPost(position: Int): Post? = posts.getOrNull(position)

    fun setAutoPlayVideos(autoPlay: Boolean) {
        autoPlayVideos = autoPlay
    }
    
    /**
     * Configura todas las preferencias de video a la vez
     */
    fun setVideoPreferences(
        autoPlay: Boolean,
        mute: Boolean,
        fullscreen: Boolean,
        landscape: Boolean,
        quality: Int,
        format: Int
    ) {
        autoPlayVideos = autoPlay
        muteVideos = mute
        fullscreenVideos = fullscreen
        landscapeVideos = landscape
        videoQuality = quality
        videoFormat = format
    }
    
    /**
     * Configura las preferencias de acciones automáticas
     */
    fun setActionPreferences(
        upvoteOnFav: Boolean,
        upvoteOnDl: Boolean,
        favOnDl: Boolean
    ) {
        upvoteOnFavorite = upvoteOnFav
        upvoteOnDownload = upvoteOnDl
        favoriteOnDownload = favOnDl
    }
    
    /**
     * Configura las preferencias de compartir/abrir
     */
    fun setSharePreferences(
        disableShareOption: Boolean,
        useE621Host: Boolean
    ) {
        disableShare = disableShareOption
        useE621 = useE621Host
    }
    
    /**
     * Obtiene el host base según preferencias
     */
    private fun getBaseUrl(): String {
        return if (useE621) "https://e621.net" else "https://e926.net"
    }

    /**
     * Pausa todos los players activos
     * Equivalente al método g() en la app original (ei/e0.java línea 840)
     * Se llama cuando:
     * - Se cambia de página en el ViewPager
     * - La activity entra en onPause()
     */
    fun pauseAllPlayers() {
        players.values.forEach { player ->
            try {
                player.pause()
                player.playWhenReady = false
            } catch (e: Exception) {
                // Player ya liberado
            }
        }
    }
    
    /**
     * Reproduce el video en una posición específica si autoplay está habilitado
     * Se llama después de pauseAllPlayers() cuando cambia de página
     */
    fun playVideoAtPosition(position: Int) {
        if (!autoPlayVideos) return
        
        val player = players[position] ?: return
        try {
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            // Player no disponible
        }
    }

    /**
     * Libera todos los players
     * Equivalente a lo que hace onDestroy() en la app original (PostActivity.java línea 559)
     * Se llama cuando la activity se destruye
     */
    fun releaseAllPlayers() {
        players.values.forEach { player ->
            try {
                player.release()
            } catch (e: Exception) {
                // Player ya liberado
            }
        }
        players.clear()
    }
    
    /**
     * Obtiene el player en una posición específica
     */
    fun getPlayerAt(position: Int): ExoPlayer? = players[position]
    
    /**
     * Registra un player para una posición específica
     */
    fun registerPlayer(position: Int, player: ExoPlayer) {
        // Liberar player anterior en esa posición si existe
        players[position]?.release()
        players[position] = player
    }
    
    /**
     * Elimina y libera el player de una posición
     */
    fun unregisterPlayer(position: Int) {
        players[position]?.let { player ->
            try {
                player.release()
            } catch (e: Exception) {
                // Ya liberado
            }
        }
        players.remove(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        // Registrar el ViewHolder para poder actualizarlo cuando llegue respuesta del API
        viewHoldersByPostId[post.id] = holder
        holder.bind(post, position, posts.size)
    }

    override fun getItemCount(): Int = posts.size

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        // Desregistrar el ViewHolder
        holder.getBoundPostId()?.let { postId ->
            viewHoldersByPostId.remove(postId)
        }
        // Liberar y desregistrar el player como en la app original (ei/e0.java línea 1215)
        holder.getBoundPosition()?.let { position ->
            unregisterPlayer(position)
        }
        holder.cleanupPlayer()
    }
    
    /**
     * Actualiza el estado del botón de voto cuando llega respuesta del API
     * Como la app original que solo actualiza en el callback exitoso
     * @param postId El ID del post
     * @param success Si la acción fue exitosa
     * @param newVoteState -1=downvote, 0=sin voto, 1=upvote
     */
    fun updateVoteState(postId: Int, success: Boolean, newVoteState: Int) {
        val holder = viewHoldersByPostId[postId] ?: return
        holder.onVoteResult(success, newVoteState)
    }
    
    /**
     * Actualiza el estado del botón de favorito cuando llega respuesta del API
     * @param postId El ID del post
     * @param success Si la acción fue exitosa
     * @param isFavorited true si ahora es favorito, false si no
     */
    fun updateFavoriteState(postId: Int, success: Boolean, isFavorited: Boolean) {
        val holder = viewHoldersByPostId[postId] ?: return
        holder.onFavoriteResult(success, isFavorited)
    }
    
    /**
     * Actualiza el estado del botón de descarga cuando termina la descarga
     * @param postId El ID del post
     * @param success Si la descarga fue exitosa
     * @param isDownloading true si aún está descargando
     */
    fun updateDownloadState(postId: Int, success: Boolean, isDownloading: Boolean) {
        val holder = viewHoldersByPostId[postId] ?: return
        holder.onDownloadResult(success, isDownloading)
    }
    
    /**
     * Actualiza el progreso de descarga
     * @param postId El ID del post
     * @param progress Progreso 0-100
     * @param isDownloading true si está descargando
     */
    fun updateDownloadProgress(postId: Int, progress: Int, isDownloading: Boolean) {
        val holder = viewHoldersByPostId[postId] ?: return
        holder.onDownloadProgress(progress, isDownloading)
    }

    inner class PostViewHolder(
        private val binding: ItemPostPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var player: ExoPlayer? = null
        private var currentPosition: Int = -1
        private var currentPostId: Int = -1
        
        // Estados actuales de los botones (para restaurar si hay error)
        private var currentVoteState: Int = 0  // -1, 0, 1
        private var currentFavoriteState: Boolean = false
        private var isDownloading: Boolean = false
        // Estados pendientes (mientras esperamos respuesta del API)
        private var pendingVoteState: Int? = null
        private var pendingFavoriteState: Boolean? = null

        fun getBoundPosition(): Int? = if (currentPosition >= 0) currentPosition else null
        fun getBoundPostId(): Int? = if (currentPostId >= 0) currentPostId else null
        
        /**
         * Limpia el player del ViewHolder
         * Similar a lo que hace onViewRecycled en la app original
         */
        fun cleanupPlayer() {
            binding.playerView.player = null
            binding.imgMute.visibility = View.GONE
            player = null
        }
        
        /**
         * Callback cuando llega respuesta del API de voto
         * Solo actualiza el icono si fue exitoso, si no restaura el estado anterior
         * Como la app original e0.k(view, true) para re-habilitar
         */
        fun onVoteResult(success: Boolean, newVoteState: Int) {
            pendingVoteState = null
            
            // Re-habilitar botones
            binding.btnUpvote.isEnabled = true
            binding.btnDownvote.isEnabled = true
            
            if (success) {
                // Actualizar estado actual
                currentVoteState = newVoteState
                updateVoteUpButton(newVoteState == 1)
                updateVoteDownButton(newVoteState == -1)
            } else {
                // Restaurar estado anterior (el botón ya tiene el estado anterior almacenado)
                updateVoteUpButton(currentVoteState == 1)
                updateVoteDownButton(currentVoteState == -1)
            }
        }
        
        /**
         * Callback cuando llega respuesta del API de favorito
         */
        fun onFavoriteResult(success: Boolean, isFavorited: Boolean) {
            pendingFavoriteState = null
            
            // Re-habilitar botón
            binding.btnFavorite.isEnabled = true
            
            if (success) {
                currentFavoriteState = isFavorited
                updateFavoriteButton(isFavorited)
            } else {
                // Restaurar estado anterior
                updateFavoriteButton(currentFavoriteState)
            }
        }
        
        /**
         * Callback cuando termina la descarga
         */
        fun onDownloadResult(success: Boolean, stillDownloading: Boolean) {
            isDownloading = stillDownloading
            
            // Re-habilitar botón
            binding.btnDownload.isEnabled = true
            binding.btnDownload.alpha = 1.0f
            
            if (success) {
                // Mostrar icono de completado brevemente
                binding.btnDownload.setImageResource(R.drawable.ic_check)
                binding.btnDownload.postDelayed({
                    binding.btnDownload.setImageResource(R.drawable.ic_download)
                }, 2000)
            } else if (!stillDownloading) {
                // Restaurar icono normal
                binding.btnDownload.setImageResource(R.drawable.ic_download)
            }
        }
        
        /**
         * Callback para actualizar progreso de descarga
         */
        fun onDownloadProgress(progress: Int, stillDownloading: Boolean) {
            isDownloading = stillDownloading
            
            if (stillDownloading) {
                // Mantener botón deshabilitado durante descarga
                binding.btnDownload.isEnabled = false
                binding.btnDownload.alpha = 0.5f
            } else {
                binding.btnDownload.isEnabled = true
                binding.btnDownload.alpha = 1.0f
            }
        }
        
        /**
         * Actualiza el icono de mute según el estado actual
         * Como la app original (ei/s.java línea 153-154):
         * cVar.m0.setVisibility(z6 ? 0 : 4) donde z6 = muteado
         */
        private fun updateMuteIcon() {
            val isMuted = muteVideos
            binding.imgMute.setImageResource(
                if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
            )
        }

        fun bind(post: Post, position: Int, total: Int) {
            val context = binding.root.context
            currentPosition = position
            currentPostId = post.id
            
            // Inicializar estados actuales desde el post
            currentVoteState = post.score.ourScore
            currentFavoriteState = post.isFavorited

            // --- Título Card ---
            setupTitleCard(post, position, total, context)

            // --- Relation Buttons ---
            setupRelationButtons(post)

            // --- Descripción ---
            setupDescription(post)

            // --- Tags (en orden correcto: Artist, Character, Copyright, Species, General, Lore, Meta) ---
            setupTagsInOrder(post, context)

            // --- Detalles ---
            setupDetails(post, context)

            // --- Media (Imagen o Video) ---
            setupMedia(post)

            // --- Barra de botones de acción (como la app original lLButtons) ---
            setupActionButtons(post, context)

            // --- Scroll listener para mini preview ---
            binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                onScrollStateChanged(scrollY > 200)
            }
        }

        private fun setupTitleCard(post: Post, position: Int, total: Int, context: Context) {
            // Artista
            val artists = post.tags.artist.joinToString(", ")
            binding.txtArtist.text = if (artists.isNotEmpty()) artists else context.getString(R.string.unknown_artist)
            binding.txtArtist.setOnClickListener {
                if (artists.isNotEmpty()) {
                    onArtistClick(post.tags.artist.first())
                }
            }

            // Posición
            binding.txtPosition.text = "${position + 1}/$total"

            // Rating (mostrar solo el que corresponde)
            binding.txtRatingE.visibility = if (post.rating == "e") View.VISIBLE else View.GONE
            binding.txtRatingQ.visibility = if (post.rating == "q") View.VISIBLE else View.GONE
            binding.txtRatingS.visibility = if (post.rating == "s") View.VISIBLE else View.GONE

            // Post ID
            binding.txtPostId.text = "#${post.id}"
            binding.txtPostId.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Post ID", post.id.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }

            // Score
            binding.txtScoreUp.text = post.score.up.toString()
            binding.txtScoreDown.text = kotlin.math.abs(post.score.down).toString()
            binding.txtFavCount.text = post.favCount.toString()

            // Comment badge (si tiene comentarios)
            if (post.commentCount > 0) {
                binding.txtCommentBadge.visibility = View.VISIBLE
                binding.txtCommentBadge.text = "C${post.commentCount}"
            } else {
                binding.txtCommentBadge.visibility = View.GONE
            }
        }

        /**
         * Configura los botones de relación: Parent, Children, Pools
         * Como la app original (ei/e0.java líneas ~1025-1100):
         * - Parent: Abre directamente el post padre
         * - Children: Si hay 1, abre directo. Si hay varios, muestra dialog con lista
         * - Pools: Si hay 1, abre directo. Si hay varios, muestra dialog con lista
         */
        private fun setupRelationButtons(post: Post) {
            val context = binding.root.context
            val hasParent = post.relationships.parentId != null && post.relationships.parentId > 0
            val hasChildren = post.relationships.hasActiveChildren || post.relationships.children.isNotEmpty()
            val hasPools = post.pools.isNotEmpty()

            if (hasParent || hasChildren || hasPools) {
                binding.relationButtonsLayout.visibility = View.VISIBLE

                // === PARENT BUTTON ===
                if (hasParent) {
                    binding.btnParent.visibility = View.VISIBLE
                    binding.btnParent.text = context.getString(R.string.post_parent, post.relationships.parentId.toString())
                    binding.btnParent.setOnClickListener {
                        post.relationships.parentId?.let { parentId ->
                            onNavigateToPost(parentId)
                        }
                    }
                } else {
                    binding.btnParent.visibility = View.GONE
                }

                // === CHILDREN BUTTON ===
                if (hasChildren) {
                    binding.btnChildren.visibility = View.VISIBLE
                    val childrenList = post.relationships.children
                    val childrenCount = childrenList.size
                    
                    binding.btnChildren.text = if (childrenCount == 1) {
                        context.getString(R.string.post_child, childrenList.first().toString())
                    } else if (childrenCount > 1) {
                        context.resources.getQuantityString(R.plurals.post_children, childrenCount, childrenCount.toString())
                    } else {
                        context.getString(R.string.post_has_children)
                    }
                    
                    binding.btnChildren.setOnClickListener {
                        if (childrenList.size == 1) {
                            // Abrir directamente si solo hay un child
                            onNavigateToPost(childrenList.first())
                        } else if (childrenList.size > 1) {
                            // Mostrar dialog con lista de children
                            val childrenIds = childrenList.map { "#$it" }.toTypedArray()
                            AlertDialog.Builder(context)
                                .setTitle(context.getString(R.string.post_select_child))
                                .setItems(childrenIds) { _, which ->
                                    onNavigateToPost(childrenList[which])
                                }
                                .show()
                        }
                    }
                } else {
                    binding.btnChildren.visibility = View.GONE
                }

                // === POOLS BUTTON ===
                if (hasPools) {
                    binding.btnPool.visibility = View.VISIBLE
                    val poolsList = post.pools
                    val poolsCount = poolsList.size
                    
                    binding.btnPool.text = if (poolsCount == 1) {
                        context.getString(R.string.post_pool, poolsList.first().toString())
                    } else {
                        context.resources.getQuantityString(R.plurals.post_pools, poolsCount, poolsCount.toString())
                    }
                    
                    binding.btnPool.setOnClickListener {
                        if (poolsList.size == 1) {
                            // Abrir directamente si solo hay un pool
                            onNavigateToPool(poolsList.first())
                        } else {
                            // Mostrar dialog con lista de pools
                            val poolIds = poolsList.map { "Pool #$it" }.toTypedArray()
                            AlertDialog.Builder(context)
                                .setTitle(context.getString(R.string.post_select_pool))
                                .setItems(poolIds) { _, which ->
                                    onNavigateToPool(poolsList[which])
                                }
                                .show()
                        }
                    }
                } else {
                    binding.btnPool.visibility = View.GONE
                }
            } else {
                binding.relationButtonsLayout.visibility = View.GONE
            }
        }

        private fun setupDescription(post: Post) {
            val desc = post.description
            if (!desc.isNullOrEmpty()) {
                binding.descriptionHeader.visibility = View.VISIBLE
                binding.txtDescription.text = desc
                binding.txtDescription.visibility = View.GONE

                var expanded = false
                binding.descriptionHeader.setOnClickListener {
                    expanded = !expanded
                    binding.txtDescription.visibility = if (expanded) View.VISIBLE else View.GONE
                    binding.imgDescriptionExpand.setImageResource(
                        if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                    )
                }
            } else {
                binding.descriptionHeader.visibility = View.GONE
                binding.txtDescription.visibility = View.GONE
            }
        }

        /**
         * Genera los tags en el orden correcto como la app original:
         * Artist -> Character -> Copyright -> Species -> General -> Lore -> Meta
         * Cada categoría tiene un header en negrita seguido de los tags
         */
        private fun setupTagsInOrder(post: Post, context: Context) {
            val tagsContainer = binding.tagsContainer
            tagsContainer.removeAllViews()

            // Tags expandidos por defecto
            var tagsExpanded = true
            tagsContainer.visibility = View.VISIBLE
            binding.imgTagsExpand.setImageResource(R.drawable.ic_expand_less)

            binding.tagsHeader.setOnClickListener {
                tagsExpanded = !tagsExpanded
                tagsContainer.visibility = if (tagsExpanded) View.VISIBLE else View.GONE
                binding.imgTagsExpand.setImageResource(
                    if (tagsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                )
            }

            // Orden de categorías como en la app original:
            // Artist -> Character -> Copyright -> Species -> General -> Lore -> Meta

            // 1. Artist (color amarillo)
            if (post.tags.artist.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_artist),
                    post.tags.artist,
                    ContextCompat.getColor(context, R.color.tagArtist),
                    context
                )
            }

            // 2. Character (color verde)
            if (post.tags.character.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_character),
                    post.tags.character,
                    ContextCompat.getColor(context, R.color.tagCharacter),
                    context
                )
            }

            // 3. Copyright (color magenta/púrpura)
            if (post.tags.copyright.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_copyright),
                    post.tags.copyright,
                    ContextCompat.getColor(context, R.color.tagCopyright),
                    context
                )
            }

            // 4. Species (color naranja)
            if (post.tags.species.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_species),
                    post.tags.species,
                    ContextCompat.getColor(context, R.color.tagSpecies),
                    context
                )
            }

            // 5. General (color azul)
            if (post.tags.general.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_general),
                    post.tags.general,
                    ContextCompat.getColor(context, R.color.tagGeneral),
                    context
                )
            }

            // 6. Lore (color verde oscuro)
            if (post.tags.lore.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_lore),
                    post.tags.lore,
                    ContextCompat.getColor(context, R.color.tagLore),
                    context
                )
            }

            // 7. Meta (color blanco/gris)
            if (post.tags.meta.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_meta),
                    post.tags.meta,
                    ContextCompat.getColor(context, R.color.tagMeta),
                    context
                )
            }

            // 8. Invalid (color rojo, si existe)
            if (post.tags.invalid.isNotEmpty()) {
                addTagCategory(
                    tagsContainer,
                    context.getString(R.string.tag_category_invalid),
                    post.tags.invalid,
                    ContextCompat.getColor(context, R.color.tagInvalid),
                    context
                )
            }
        }

        /**
         * Agrega una categoría de tags con header en negrita y los tags debajo
         * Como en adapter_post_item_tag_category.xml y adapter_post_item_tag.xml
         */
        private fun addTagCategory(
            container: LinearLayout,
            categoryName: String,
            tags: List<String>,
            tagColor: Int,
            context: Context
        ) {
            // Header de categoría (como adapter_post_item_tag_category.xml)
            val categoryHeader = TextView(context).apply {
                text = categoryName
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
                setPadding(8, 12, 8, 4)
            }
            container.addView(categoryHeader)

            // Cada tag (como adapter_post_item_tag.xml)
            tags.forEach { tagName ->
                val tagView = TextView(context).apply {
                    text = tagName
                    setTextColor(tagColor)
                    textSize = 13f
                    setPadding(16, 4, 8, 4)
                    isClickable = true
                    isFocusable = true
                    // Efecto ripple al presionar
                    val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                    val ta = context.obtainStyledAttributes(attrs)
                    background = ta.getDrawable(0)
                    ta.recycle()
                    
                    setOnClickListener {
                        showTagOptionsDialog(context, tagName)
                    }
                }
                container.addView(tagView)
            }
        }

        /**
         * Muestra un diálogo con opciones cuando se toca un tag
         * Como en la app original (PostActivity.java onTagClicked con post_tag_clicked array)
         * Orden de opciones (di/k.java líneas 120-170):
         * 0: Search
         * 1: Add to saved searches
         * 2: Add to blacklist
         * 3: Follow tag
         * 4: Unfollow tag
         * 5: Add to current search
         * 6: Remove from current search
         * 7: View wiki
         * 8: Copy to clipboard
         */
        private fun showTagOptionsDialog(context: Context, tagName: String) {
            val options = arrayOf(
                context.getString(R.string.tag_menu_search),                  // 0
                context.getString(R.string.tag_menu_add_to_saved),            // 1
                context.getString(R.string.tag_menu_add_to_blacklist),        // 2
                context.getString(R.string.tag_menu_follow),                  // 3
                context.getString(R.string.tag_menu_unfollow),                // 4
                context.getString(R.string.tag_menu_add_to_search),           // 5
                context.getString(R.string.tag_menu_remove_from_search),      // 6
                context.getString(R.string.tag_menu_view_wiki),               // 7
                context.getString(R.string.tag_menu_copy)                     // 8
            )

            AlertDialog.Builder(context)
                .setTitle(tagName)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> { // Search - abre MainActivity con este tag (como di/k.java línea 129)
                            searchTag(context, tagName)
                        }
                        1 -> { // Add to saved searches
                            addToSavedSearches(context, tagName)
                        }
                        2 -> { // Add to blacklist
                            addToBlacklist(context, tagName)
                        }
                        3 -> { // Follow tag
                            followTag(context, tagName)
                        }
                        4 -> { // Unfollow tag
                            unfollowTag(context, tagName)
                        }
                        5 -> { // Add to current search
                            addToCurrentSearch(context, tagName)
                        }
                        6 -> { // Remove from current search
                            removeFromCurrentSearch(context, tagName)
                        }
                        7 -> { // View wiki
                            openWikiPage(context, tagName)
                        }
                        8 -> { // Copy to clipboard
                            copyToClipboard(context, tagName)
                        }
                    }
                }
                .show()
        }

        /**
         * Busca con este tag - Inicia MainActivity con el tag
         * Como di/k.java línea 129: Intent.putExtra("tags", str4)
         */
        private fun searchTag(context: Context, tagName: String) {
            val intent = Intent(context, com.mommys.app.ui.main.MainActivity::class.java).apply {
                putExtra("tags", tagName)
                // Opcional: crear nueva tarea como la app original si la preferencia está activa
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }

        /**
         * Añade el tag a las búsquedas guardadas
         * Como g.f() en la app original - Usa Room database
         */
        private fun addToSavedSearches(context: Context, tagName: String) {
            val app = context.applicationContext as com.mommys.app.MommysApplication
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Check if already exists
                    val existing = app.database.savedSearchDao().searchSaved(tagName, 100)
                    val alreadyExists = existing.any { it.query.equals(tagName, ignoreCase = true) }
                    
                    if (alreadyExists) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.tag_already_in_saved, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Insert new saved search
                        val savedSearch = com.mommys.app.data.database.SavedSearchEntity(
                            name = tagName,
                            query = tagName,
                            page = 1
                        )
                        app.database.savedSearchDao().insert(savedSearch)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.tag_added_to_saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.tag_added_to_saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        /**
         * Añade el tag a la blacklist
         * Como g.e() en la app original - adds tag as a new line to blacklist_raw
         */
        private fun addToBlacklist(context: Context, tagName: String) {
            val prefsManager = PreferencesManager(context)
            
            // Get current blacklist as raw text
            val currentRaw = prefsManager.getBlacklistRaw()
            
            // Check if tag already exists (as a whole line)
            val lines = currentRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.contains(tagName)) {
                Toast.makeText(context, R.string.tag_already_in_blacklist, Toast.LENGTH_SHORT).show()
                return
            }
            
            // Add tag as new line
            val newRaw = if (currentRaw.isEmpty()) {
                tagName
            } else {
                "$currentRaw\n$tagName"
            }
            
            // Save using PreferencesManager (this also updates the Set version)
            prefsManager.setBlacklistRaw(newRaw)
            
            Toast.makeText(context, R.string.tag_added_to_blacklist, Toast.LENGTH_SHORT).show()
        }

        /**
         * Sigue el tag para recibir notificaciones
         * Como g.d() en la app original - Usa PreferencesManager.addFollowingTag()
         */
        private fun followTag(context: Context, tagName: String) {
            val prefsManager = PreferencesManager(context)
            val wasAdded = prefsManager.addFollowingTag(tagName)
            
            if (wasAdded) {
                Toast.makeText(context, R.string.tag_following, Toast.LENGTH_SHORT).show()
                // Schedule the job if following is enabled
                if (prefsManager.followingEnabled) {
                    FollowingJobService.schedule(context)
                }
            } else {
                Toast.makeText(context, R.string.tag_already_following, Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Deja de seguir el tag
         * Como g.P() en la app original - Usa PreferencesManager.removeFollowingTag()
         */
        private fun unfollowTag(context: Context, tagName: String) {
            val prefsManager = PreferencesManager(context)
            val wasRemoved = prefsManager.removeFollowingTag(tagName)
            
            if (wasRemoved) {
                Toast.makeText(context, R.string.tag_unfollowed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.tag_not_following, Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Añade el tag a la búsqueda actual
         * Como MainActivity.f24886p0.add(str4) en la app original
         */
        private fun addToCurrentSearch(context: Context, tagName: String) {
            // Guardar tag para agregar a la búsqueda actual cuando vuelva a MainActivity
            val prefs = context.getSharedPreferences("current_search", Context.MODE_PRIVATE)
            val pendingTags = prefs.getStringSet("pending_add", mutableSetOf()) ?: mutableSetOf()
            val updated = pendingTags.toMutableSet()
            updated.add(tagName)
            prefs.edit().putStringSet("pending_add", updated).apply()
            Toast.makeText(context, context.getString(R.string.tag_added_to_search), Toast.LENGTH_SHORT).show()
        }

        /**
         * Quita el tag de la búsqueda actual (agrega con -)
         * Como MainActivity.f24886p0.add("-" + str4) en la app original
         */
        private fun removeFromCurrentSearch(context: Context, tagName: String) {
            val prefs = context.getSharedPreferences("current_search", Context.MODE_PRIVATE)
            val pendingTags = prefs.getStringSet("pending_remove", mutableSetOf()) ?: mutableSetOf()
            val updated = pendingTags.toMutableSet()
            updated.add(tagName)
            prefs.edit().putStringSet("pending_remove", updated).apply()
            Toast.makeText(context, context.getString(R.string.tag_removed_from_search), Toast.LENGTH_SHORT).show()
        }

        /**
         * Abre la página wiki del tag
         * Como WikiShowActivity en la app original
         */
        private fun openWikiPage(context: Context, tagName: String) {
            val wikiUrl = "https://e926.net/wiki_pages/show_or_new?title=$tagName"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wikiUrl))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening wiki", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Copia el tag al portapapeles
         */
        private fun copyToClipboard(context: Context, tagName: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tag", tagName)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, R.string.tag_copied, Toast.LENGTH_SHORT).show()
        }

        private fun setupDetails(post: Post, context: Context) {
            val detailsContainer = binding.detailsContainer
            detailsContainer.removeAllViews()

            var detailsExpanded = false
            detailsContainer.visibility = View.GONE

            binding.detailsHeader.setOnClickListener {
                detailsExpanded = !detailsExpanded
                detailsContainer.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
                binding.imgDetailsExpand.setImageResource(
                    if (detailsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                )
            }

            // Agregar detalles dinámicamente
            val details = mutableListOf<Pair<String, String>>()

            // Fecha
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val date = inputFormat.parse(post.createdAt.substringBefore('.'))
                details.add("Created" to (date?.let { outputFormat.format(it) } ?: post.createdAt))
            } catch (e: Exception) {
                details.add("Created" to post.createdAt)
            }

            // Uploader
            post.uploaderId?.let {
                details.add("Uploader ID" to it.toString())
            }

            // Size
            details.add("Size" to "${post.file.width}x${post.file.height}")

            // Format
            details.add("Format" to post.file.ext.uppercase())

            // File size
            val fileSizeKb = (post.file.size ?: 0) / 1024
            val fileSizeMb = fileSizeKb / 1024.0
            val fileSizeStr = if (fileSizeMb >= 1) {
                String.format("%.2f MB", fileSizeMb)
            } else {
                "$fileSizeKb KB"
            }
            details.add("File size" to fileSizeStr)

            // Agregar cada detalle
            details.forEach { (label, value) ->
                val detailView = TextView(context).apply {
                    text = "$label: $value"
                    textSize = 12f
                    setPadding(8, 4, 8, 4)
                }
                detailsContainer.addView(detailView)
            }

            // Sources
            if (post.sources.isNotEmpty()) {
                val sourcesLabel = TextView(context).apply {
                    text = "Sources:"
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = 12f
                    setPadding(8, 12, 8, 4)
                }
                detailsContainer.addView(sourcesLabel)

                post.sources.forEach { source ->
                    val sourceView = TextView(context).apply {
                        text = source
                        setTextColor(ContextCompat.getColor(context, R.color.colorTextImportant))
                        textSize = 11f
                        setPadding(16, 2, 8, 2)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // URL inválida
                            }
                        }
                    }
                    detailsContainer.addView(sourceView)
                }
            }
        }

        private fun setupMedia(post: Post) {
            val ext = post.file.ext.lowercase()
            val isVideo = ext in listOf("webm", "mp4")
            val isGif = ext == "gif"

            when {
                isVideo -> setupVideo(post)
                isGif -> setupGif(post)  // GIFs son manejados por Glide con animación
                else -> setupImage(post)
            }
        }

        /**
         * Configura y carga GIF animado usando Glide
         * GIFs son cargados como imágenes animadas, no como videos
         * Similar a la implementación de la app original (adapter_post_item_gif.xml)
         */
        private fun setupGif(post: Post) {
            val context = binding.root.context
            
            binding.previewFrameParent.visibility = View.VISIBLE
            binding.videoContainer.visibility = View.GONE
            binding.errorLayout.visibility = View.GONE
            binding.loadingLayout.visibility = View.VISIBLE
            binding.progressBar.progress = 0
            binding.progressBar.max = 100

            val gifUrl = post.file.url ?: post.sample.url ?: post.preview.url
            val fileSize = post.file.size
            val fileSizeMb = fileSize / (1024.0 * 1024.0)
            
            binding.txtLoading.text = String.format("0.00 MB / %.2f MB", fileSizeMb)

            // Verificar si la URL es válida (similar a setupImage)
            if (gifUrl == null || gifUrl.isEmpty() || gifUrl == "null" || !gifUrl.startsWith("http")) {
                binding.loadingLayout.visibility = View.GONE
                binding.errorLayout.visibility = View.VISIBLE
                binding.txtError.text = context.getString(R.string.post_error_not_logged_in)
                return
            }

            // Cargar GIF con Glide que soporta animación nativa
            Glide.with(context)
                .asGif()  // Indicar explícitamente que es un GIF
                .load(gifUrl)
                .placeholder(R.drawable.placeholder_image)
                .listener(object : RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.loadingLayout.visibility = View.GONE
                        binding.errorLayout.visibility = View.VISIBLE
                        binding.txtError.text = e?.message ?: "Error loading GIF"
                        return false
                    }

                    override fun onResourceReady(
                        resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                        model: Any,
                        target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.loadingLayout.visibility = View.GONE
                        binding.errorLayout.visibility = View.GONE
                        // Iniciar la animación del GIF
                        resource.start()
                        return false
                    }
                })
                .into(binding.imgPreview)

            // PhotoView permite zoom incluso en GIFs
            binding.imgPreview.maximumScale = 5f
            binding.imgPreview.mediumScale = 2.5f

            // Retry button
            binding.btnRefresh.setOnClickListener {
                binding.errorLayout.visibility = View.GONE
                setupGif(post)
            }
        }

        /**
         * Configura y carga la imagen con tracking de progreso real
         * Similar a la implementación de la app original
         */
        private fun setupImage(post: Post) {
            val context = binding.root.context
            
            binding.previewFrameParent.visibility = View.VISIBLE
            binding.videoContainer.visibility = View.GONE
            binding.errorLayout.visibility = View.GONE
            
            // Mostrar loading
            binding.loadingLayout.visibility = View.VISIBLE
            binding.progressBar.progress = 0
            binding.progressBar.max = 100

            val imageUrl = post.file.url ?: post.sample.url ?: post.preview.url
            val fileSize = post.file.size
            val fileSizeMb = fileSize / (1024.0 * 1024.0)
            
            // Mostrar tamaño inicial
            binding.txtLoading.text = String.format("0.00 MB / %.2f MB", fileSizeMb)

            // Verificar si la URL es válida (no null, no vacía, no "null", empieza con http)
            // Similar a ii/m.java línea 750 y ei/e0.java línea 977 de la app original
            if (imageUrl == null || imageUrl.isEmpty() || imageUrl == "null" || !imageUrl.startsWith("http")) {
                binding.loadingLayout.visibility = View.GONE
                binding.errorLayout.visibility = View.VISIBLE
                binding.txtError.text = context.getString(R.string.post_error_not_logged_in)
                return
            }

            // Descargar imagen con progreso real
            ProgressDownloader.download(imageUrl, object : ProgressDownloader.ProgressListener {
                override fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean) {
                    if (contentLength > 0) {
                        val progress = ((bytesRead.toDouble() / contentLength.toDouble()) * 100).toInt()
                        val bytesReadMb = bytesRead / (1024.0 * 1024.0)
                        val totalMb = contentLength / (1024.0 * 1024.0)
                        
                        // Actualizar UI en el hilo principal
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            binding.progressBar.setProgress(progress, true)
                        } else {
                            binding.progressBar.progress = progress
                        }
                        binding.txtLoading.text = String.format("%.2f MB / %.2f MB", bytesReadMb, totalMb)
                    }
                }

                override fun onComplete(data: ByteArray?) {
                    binding.loadingLayout.visibility = View.GONE
                    
                    if (data != null) {
                        try {
                            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                            if (bitmap != null) {
                                binding.imgPreview.setImageBitmap(bitmap)
                                binding.errorLayout.visibility = View.GONE
                            } else {
                                // Fallback a Glide si no se puede decodificar
                                loadImageWithGlide(imageUrl, context)
                            }
                        } catch (e: Exception) {
                            loadImageWithGlide(imageUrl, context)
                        }
                    } else {
                        loadImageWithGlide(imageUrl, context)
                    }
                }

                override fun onError(exception: Exception) {
                    binding.loadingLayout.visibility = View.GONE
                    // Intentar con Glide como fallback
                    loadImageWithGlide(imageUrl, context)
                }
            })

            // PhotoView permite zoom
            binding.imgPreview.maximumScale = 5f
            binding.imgPreview.mediumScale = 2.5f

            // Retry button
            binding.btnRefresh.setOnClickListener {
                binding.errorLayout.visibility = View.GONE
                setupImage(post)
            }
        }

        /**
         * Fallback para cargar imagen con Glide
         */
        private fun loadImageWithGlide(imageUrl: String, context: Context) {
            Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_image)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.loadingLayout.visibility = View.GONE
                        binding.errorLayout.visibility = View.VISIBLE
                        binding.txtError.text = e?.message ?: "Error loading image"
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.loadingLayout.visibility = View.GONE
                        binding.errorLayout.visibility = View.GONE
                        return false
                    }
                })
                .into(binding.imgPreview)
        }

        /**
         * Configura el reproductor de video con buffering y controles mejorados
         * Similar a la implementación de la app original
         */
        private fun setupVideo(post: Post) {
            val context = binding.root.context
            
            // Limpiar player anterior si existe
            player?.release()
            player = null
            binding.playerView.player = null
            
            binding.previewFrameParent.visibility = View.GONE
            binding.videoContainer.visibility = View.VISIBLE
            binding.errorLayout.visibility = View.GONE
            binding.loadingLayout.visibility = View.VISIBLE
            binding.progressBar.progress = 0
            binding.progressBar.max = 100
            
            // Calcular dimensiones del video manteniendo aspect ratio
            // (como app original ei/e0.java método j)
            val videoWidth = post.file.width
            val videoHeight = post.file.height
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            
            // Calcular altura basada en el aspect ratio del video
            val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
            val calculatedHeight = (screenWidth / aspectRatio).toInt()
            
            // Limitar altura máxima al 70% de la pantalla para dejar espacio para la info
            val maxHeight = (screenHeight * 0.7).toInt()
            val finalHeight = minOf(calculatedHeight, maxHeight)
            
            // Aplicar dimensiones al container y playerView
            binding.videoContainer.layoutParams.height = finalHeight
            binding.playerView.layoutParams.height = finalHeight
            binding.imgVideoPreview.layoutParams.height = finalHeight

            val fileSize = post.file.size
            val fileSizeMb = fileSize / (1024.0 * 1024.0)
            binding.txtLoading.text = String.format("0.00 MB / %.2f MB", fileSizeMb)

            // Mostrar preview del video (thumbnail)
            val previewUrl = post.preview.url ?: post.sample.url
            if (previewUrl != null) {
                binding.imgVideoPreview.visibility = View.VISIBLE
                Glide.with(context)
                    .load(previewUrl)
                    .into(binding.imgVideoPreview)
                binding.imgPlay.visibility = View.VISIBLE
            }

            // Obtener URL del video según preferencias de calidad y formato
            // Usa la función de extensión getVideoUrl() que implementa la lógica de la app original
            // (ei/s.java líneas 85-120): calidad 0=original, 1=720p, 2=480p; formato 0=webm, 1=mp4
            val videoUrl = post.getVideoUrl(videoQuality, videoFormat)
            // Verificar si la URL es válida (similar a setupImage)
            if (videoUrl == null || videoUrl.isEmpty() || videoUrl == "null" || !videoUrl.startsWith("http")) {
                binding.loadingLayout.visibility = View.GONE
                binding.errorLayout.visibility = View.VISIBLE
                binding.txtError.text = context.getString(R.string.post_error_not_logged_in)
                return
            }

            // Configuración de LoadControl para mejor buffering (como la app original)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    5000,   // minBufferMs
                    30000,  // maxBufferMs
                    1500,   // bufferForPlaybackMs
                    3000    // bufferForPlaybackAfterRebufferMs
                )
                .build()

            // Crear ExoPlayer con configuración mejorada
            player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    
                    binding.playerView.player = this
                    binding.playerView.controllerShowTimeoutMs = 3000
                    
                    // Configurar media item
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    
                    // Configurar volumen según preferencia mute (como app original ei/s.java)
                    volume = if (muteVideos) 0.0f else 1.0f
                    
                    // Repeat mode (loop)
                    repeatMode = Player.REPEAT_MODE_ONE
                    
                    // Listener para estados del player
                    addListener(object : Player.Listener {
                        private var lastBufferedPosition = 0L
                        
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_IDLE -> {
                                    // Idle
                                }
                                Player.STATE_BUFFERING -> {
                                    binding.loadingLayout.visibility = View.VISIBLE
                                    binding.txtLoading.text = "Buffering..."
                                }
                                Player.STATE_READY -> {
                                    binding.loadingLayout.visibility = View.GONE
                                    binding.imgVideoPreview.visibility = View.GONE
                                    binding.imgPlay.visibility = View.GONE
                                    
                                    // Si autoplay Y landscape están habilitados, aplicar landscape
                                    if (autoPlayVideos && landscapeVideos && isPlaying) {
                                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    // Video terminó - debido al repeat mode, reiniciará
                                }
                            }
                        }
                        
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            binding.loadingLayout.visibility = View.GONE
                            binding.errorLayout.visibility = View.VISIBLE
                            binding.txtError.text = "Video error: ${error.message}"
                        }
                        
                        override fun onIsLoadingChanged(isLoading: Boolean) {
                            if (isLoading) {
                                // Actualizar progreso de buffering
                                val bufferedPosition = bufferedPosition
                                val duration = duration
                                if (duration > 0) {
                                    val progress = ((bufferedPosition.toDouble() / duration.toDouble()) * 100).toInt()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        binding.progressBar.setProgress(progress, true)
                                    } else {
                                        binding.progressBar.progress = progress
                                    }
                                }
                            }
                        }
                    })
                    
                    // Configurar autoplay ANTES de prepare() (importante para que funcione)
                    // Como la app original (ei/s.java): primero configura, luego prepare(), luego play()
                    playWhenReady = autoPlayVideos
                    
                    // Preparar el player
                    prepare()
                    
                    // Si autoplay está habilitado, iniciar reproducción explícitamente
                    if (autoPlayVideos) {
                        play()
                    }
                }

            // Registrar el player en el HashMap por posición (como la app original)
            player?.let { p ->
                registerPlayer(currentPosition, p)
            }
            
            // Configurar botón de mute (como m0 en app original ei/s.java línea 153)
            // Mostrar icono según estado inicial de mute
            updateMuteIcon()
            binding.imgMute.visibility = View.VISIBLE
            
            // Click handler para toggle mute (como di/f1.java case 7)
            binding.imgMute.setOnClickListener {
                player?.let { p ->
                    val newMute = p.volume > 0f
                    p.volume = if (newMute) 0.0f else 1.0f
                    // Actualizar variable local para reflejar el estado actual
                    muteVideos = newMute
                    updateMuteIcon()
                }
            }

            // Click para play/pause
            binding.imgPlay.setOnClickListener {
                player?.let { p ->
                    // Pausar todos los otros players antes de reproducir este
                    pauseAllPlayers()
                    p.playWhenReady = true
                    p.play()
                    binding.imgPlay.visibility = View.GONE
                    binding.imgVideoPreview.visibility = View.GONE
                    
                    // Si la preferencia de landscape está activada, rotar a landscape
                    // (como app original ei/s.java y ei/e0.java método n)
                    if (landscapeVideos) {
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            }

            // Click en el playerView para toggle play/pause
            binding.playerView.setOnClickListener {
                player?.let { p ->
                    if (p.isPlaying) {
                        p.pause()
                        // Si estaba en landscape, volver a portrait al pausar
                        if (landscapeVideos) {
                            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    } else {
                        // Pausar otros antes de reproducir este
                        pauseAllPlayers()
                        p.play()
                        // Si la preferencia de landscape está activada, rotar a landscape
                        if (landscapeVideos) {
                            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }
                }
            }

            // Retry button
            binding.btnRefresh.setOnClickListener {
                binding.errorLayout.visibility = View.GONE
                unregisterPlayer(currentPosition)
                player = null
                binding.playerView.player = null
                setupVideo(post)
            }
        }

        /**
         * Configura la barra de botones de acción inferior
         * Como la app original (lLButtons en adapter_post_item.xml)
         * Incluye: Upvote, Downvote, Favorite, Comments, Download, More
         */
        private fun setupActionButtons(post: Post, context: Context) {
            // Estado inicial de los botones basado en el post
            // Como la app original (e0.java métodos m, o, p para actualizar iconos)
            currentVoteState = post.score.ourScore
            currentFavoriteState = post.isFavorited
            
            updateVoteUpButton(post.score.ourScore == 1)
            updateVoteDownButton(post.score.ourScore == -1)
            updateFavoriteButton(post.isFavorited)
            
            // Asegurar que los botones están habilitados
            binding.btnUpvote.isEnabled = true
            binding.btnDownvote.isEnabled = true
            binding.btnFavorite.isEnabled = true

            // Mostrar contador de comentarios si hay
            if (post.commentCount > 0) {
                binding.txtCommentCount.visibility = View.VISIBLE
                binding.txtCommentCount.text = post.commentCount.toString()
            } else {
                binding.txtCommentCount.visibility = View.GONE
            }

            // Click listeners para cada botón
            // Como la app original (ei/n.java onClick cases 0, 1, 2)
            // IMPORTANTE: No actualizamos el estado visual inmediatamente
            // Solo deshabilitamos el botón y esperamos respuesta del API

            // Upvote
            binding.btnUpvote.setOnClickListener {
                // Deshabilitar botones mientras esperamos respuesta (como e0.k(view, false))
                binding.btnUpvote.isEnabled = false
                binding.btnUpvote.alpha = 0.5f
                binding.btnDownvote.isEnabled = false
                binding.btnDownvote.alpha = 0.5f
                
                // Guardar estado pendiente
                pendingVoteState = if (currentVoteState == 1) 0 else 1
                
                onVoteUp(post)
            }

            // Downvote
            binding.btnDownvote.setOnClickListener {
                // Deshabilitar botones mientras esperamos respuesta
                binding.btnUpvote.isEnabled = false
                binding.btnUpvote.alpha = 0.5f
                binding.btnDownvote.isEnabled = false
                binding.btnDownvote.alpha = 0.5f
                
                // Guardar estado pendiente
                pendingVoteState = if (currentVoteState == -1) 0 else -1
                
                onVoteDown(post)
            }

            // Favorite
            binding.btnFavorite.setOnClickListener {
                // Deshabilitar botón mientras esperamos respuesta
                binding.btnFavorite.isEnabled = false
                binding.btnFavorite.alpha = 0.5f
                
                // Guardar estado pendiente
                pendingFavoriteState = !currentFavoriteState
                
                onFavorite(post)
                
                // Auto-upvote on favorite (como app original ei/e0.java línea 392)
                // Solo si la preferencia está habilitada y no está ya upvoteado
                if (!currentFavoriteState && upvoteOnFavorite && currentVoteState != 1) {
                    binding.btnUpvote.isEnabled = false
                    binding.btnUpvote.alpha = 0.5f
                    binding.btnDownvote.isEnabled = false
                    binding.btnDownvote.alpha = 0.5f
                    onVoteUp(post)
                }
            }

            // Comments
            binding.btnComments.setOnClickListener {
                onComment(post)
            }

            // Download
            binding.btnDownload.setOnClickListener {
                // Evitar múltiples clics mientras descarga
                if (isDownloading) return@setOnClickListener
                
                // Deshabilitar botón durante la descarga
                isDownloading = true
                binding.btnDownload.isEnabled = false
                binding.btnDownload.alpha = 0.5f
                
                onDownload(post)
                
                // Auto-upvote on download
                if (upvoteOnDownload && currentVoteState != 1) {
                    binding.btnUpvote.isEnabled = false
                    binding.btnUpvote.alpha = 0.5f
                    binding.btnDownvote.isEnabled = false
                    binding.btnDownvote.alpha = 0.5f
                    onVoteUp(post)
                }
                
                // Auto-favorite on download
                if (favoriteOnDownload && !currentFavoriteState) {
                    binding.btnFavorite.isEnabled = false
                    binding.btnFavorite.alpha = 0.5f
                    onFavorite(post)
                }
            }

            // More options
            binding.btnMore.setOnClickListener {
                showMoreOptionsDialog(context, post)
            }
        }

        /**
         * Actualiza el icono del botón upvote según estado
         * Como la app original e0.p() (línea 254)
         */
        private fun updateVoteUpButton(isVoted: Boolean) {
            binding.btnUpvote.setImageResource(
                if (isVoted) R.drawable.ic_arrow_up_filled else R.drawable.ic_arrow_up
            )
            binding.btnUpvote.alpha = 1.0f
        }

        /**
         * Actualiza el icono del botón downvote según estado
         * Como la app original e0.o() (línea 250)
         */
        private fun updateVoteDownButton(isVoted: Boolean) {
            binding.btnDownvote.setImageResource(
                if (isVoted) R.drawable.ic_arrow_down_filled else R.drawable.ic_arrow_down
            )
            binding.btnDownvote.alpha = 1.0f
        }

        /**
         * Actualiza el icono del botón favorite según estado
         * Como la app original e0.m() (línea 246)
         */
        private fun updateFavoriteButton(isFavorited: Boolean) {
            binding.btnFavorite.setImageResource(
                if (isFavorited) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
            )
            binding.btnFavorite.alpha = 1.0f
        }

        /**
         * Muestra el popup menu de opciones
         * Como la app original ei/p.java usando PopupMenu anclado al botón
         */
        private fun showMoreOptionsDialog(context: Context, post: Post) {
            // Crear PopupMenu con estilo como la app original
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuOverlay)
            val popupMenu = PopupMenu(wrapper, binding.btnMore)
            
            // Inflar el menú según el tipo de post (video o imagen)
            val menuResId = if (post.isVideo()) R.menu.menu_post_video else R.menu.menu_post
            popupMenu.menuInflater.inflate(menuResId, popupMenu.menu)
            
            // Manejar clicks en las opciones
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.slideshow -> {
                        // Delegar a la Activity para mostrar diálogo de slideshow
                        onMenuAction(PostMenuAction.Slideshow(post))
                        true
                    }
                    R.id.edit_post -> {
                        // Delegar a la Activity para editar post
                        onMenuAction(PostMenuAction.EditPost(post))
                        true
                    }
                    R.id.add_to_set -> {
                        // Delegar a la Activity para agregar a set
                        onMenuAction(PostMenuAction.AddToSet(post))
                        true
                    }
                    R.id.open_post -> {
                        // Verificar si share está deshabilitado (como app original)
                        if (disableShare) {
                            Toast.makeText(context, R.string.post_menu_open_post_disabled, Toast.LENGTH_SHORT).show()
                        } else {
                            // Abrir en navegador usando el host correcto
                            val url = "${getBaseUrl()}/posts/${post.id}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                        true
                    }
                    R.id.open_post_in_vp -> {
                        // Abrir video en reproductor externo usando URL del archivo
                        val videoUrl = post.file.url
                        if (videoUrl != null) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(videoUrl), "video/*")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.post_menu_open_post_in_vp_error, Toast.LENGTH_SHORT).show()
                            }
                        }
                        true
                    }
                    R.id.share_post -> {
                        // Verificar si share está deshabilitado (como app original)
                        if (disableShare) {
                            Toast.makeText(context, R.string.post_menu_open_post_disabled, Toast.LENGTH_SHORT).show()
                        } else {
                            // Mostrar diálogo de opciones de compartir
                            showShareDialog(context, post)
                        }
                        true
                    }
                    R.id.copy_post_link -> {
                        // Copiar URL del post usando host correcto
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("url", "${getBaseUrl()}/posts/${post.id}")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.copy_post_tags -> {
                        // Copiar todos los tags del post
                        val allTags = mutableListOf<String>()
                        post.tags?.let { tags ->
                            tags.general?.let { allTags.addAll(it) }
                            tags.artist?.let { allTags.addAll(it) }
                            tags.copyright?.let { allTags.addAll(it) }
                            tags.character?.let { allTags.addAll(it) }
                            tags.species?.let { allTags.addAll(it) }
                            tags.meta?.let { allTags.addAll(it) }
                            tags.lore?.let { allTags.addAll(it) }
                        }
                        val tagsString = allTags.joinToString(" ")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Tags", tagsString)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.reload_post -> {
                        // Delegar a la Activity para recargar post
                        onMenuAction(PostMenuAction.ReloadPost(post, bindingAdapterPosition))
                        true
                    }
                    R.id.check_notes -> {
                        // Verificar si es imagen o GIF antes de mostrar notas
                        // Como la app original: f7.i.d(jVar.f18015d) == 0 || f7.i.d(jVar.f18015d) == 1
                        // Solo imágenes (PNG, JPG) y GIFs soportan notas
                        if (post.supportsNotes()) {
                            onMenuAction(PostMenuAction.CheckNotes(post))
                        } else {
                            Toast.makeText(context, R.string.notes_only_images, Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.view_json -> {
                        // Mostrar JSON del post
                        showJsonDialog(context, post)
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
            onMoreOptions(post)
        }
        
        /**
         * Muestra diálogo de opciones para compartir
         * Como la app original con opciones: Link, Image/Video
         */
        private fun showShareDialog(context: Context, post: Post) {
            val options = if (post.isVideo()) {
                arrayOf(
                    context.getString(R.string.share_link),
                    context.getString(R.string.share_video)
                )
            } else {
                arrayOf(
                    context.getString(R.string.share_link),
                    context.getString(R.string.share_image)
                )
            }
            
            AlertDialog.Builder(context)
                .setTitle(R.string.post_menu_share_post_as)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // Compartir link
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${getBaseUrl()}/posts/${post.id}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_post)))
                        }
                        1 -> {
                            // Compartir imagen/video directamente
                            val fileUrl = if (post.isVideo()) {
                                post.getVideoUrl(quality = 1, format = 0)
                            } else {
                                post.file.url
                            }
                            fileUrl?.let { url ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (post.isVideo()) "video/*" else "image/*"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_post)))
                            }
                        }
                    }
                }
                .show()
        }
        
        /**
         * Muestra un diálogo con el JSON del post
         */
        private fun showJsonDialog(context: Context, post: Post) {
            val json = try {
                com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(post)
            } catch (e: Exception) {
                "Error generating JSON: ${e.message}"
            }
            
            AlertDialog.Builder(context)
                .setTitle("Post #${post.id} JSON")
                .setMessage(json)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.copy) { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("JSON", json)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }
}
