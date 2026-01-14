package com.mommys.app.ui.main

import android.content.Context
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.data.db.seen.AppSeenDatabase
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.BlacklistHelper
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PageHandler - Maneja el estado y datos de una página individual.
 * Similar a ii.h de la app original.
 * 
 * Cada PageHandler:
 * - Tiene su propio número de página (pageNumber)
 * - Mantiene la lista de posts
 * - Tiene referencias débiles al ViewHolder cuando está visible
 * - Maneja su propio estado de carga/error
 * - Soporta selección múltiple mediante SelectionCallback
 */
class PageHandler(
    private val context: Context,
    val pageNumber: Int,
    private var gridColumns: Int,
    private val prefs: PreferencesManager,
    private val onPostClick: (Post) -> Unit,
    private val onLoadPage: (Int) -> Unit,
    private val selectionCallback: SelectionCallback? = null
) {
    // Lista de posts de esta página (original sin filtrar)
    private val originalPosts = mutableListOf<Post>()
    
    // Lista de posts filtrados/ordenados (lo que se muestra)
    val posts = mutableListOf<Post>()
    
    // Estado de carga
    var isLoading: Boolean = true
        private set
    
    // Estado de error
    var hasError: Boolean = false
        private set
    var errorMessage: String? = null
        private set
    
    // Si esta es la última página (no hay más posts)
    // Se determina cuando los posts recibidos son menos que el límite solicitado
    var isLastPage: Boolean = false
        private set
    
    // Si ya se solicitó la carga de datos
    val loadRequested = AtomicBoolean(false)
    
    // Referencia débil al ViewHolder actual (cuando está visible)
    var recyclerView: WeakReference<RecyclerView>? = null
    var progressBar: WeakReference<ProgressBar>? = null
    var emptyText: WeakReference<TextView>? = null
    var errorText: WeakReference<TextView>? = null
    var retryButton: WeakReference<ImageButton>? = null
    
    // Adapter para el RecyclerView de esta página
    var postsAdapter: PostsAdapter? = null
    
    /**
     * Llamado cuando el ViewHolder se bindea a este PageHandler
     */
    fun bindViewHolder(
        rv: RecyclerView,
        pb: ProgressBar,
        emptyTv: TextView,
        errorTv: TextView,
        retryBtn: ImageButton
    ) {
        recyclerView = WeakReference(rv)
        progressBar = WeakReference(pb)
        emptyText = WeakReference(emptyTv)
        errorText = WeakReference(errorTv)
        retryButton = WeakReference(retryBtn)
        
        // Configurar RecyclerView si no tiene adapter
        if (rv.adapter == null || postsAdapter == null) {
            postsAdapter = PostsAdapter(
                onPostClick = { post -> onPostClick(post) },
                onInfoClick = null,
                selectionCallback = selectionCallback
            ).apply {
                // Configurar opciones del grid desde las preferencias
                aspectRatio = prefs.gridAspectRatio
                showStats = prefs.gridStats
                showInfoButton = prefs.gridInfo
                showStatusColors = prefs.gridColours
                showNewLabel = prefs.gridNewLabel
                darkenSeen = prefs.gridDarkenSeen
                showGifs = prefs.gridGifs
                hideSeen = prefs.gridHideSeen
            }
            rv.layoutManager = GridLayoutManager(rv.context, gridColumns)
            rv.setHasFixedSize(true)
            rv.adapter = postsAdapter
        } else {
            // Actualizar preferencias cada vez que se rebindea
            postsAdapter?.apply {
                aspectRatio = prefs.gridAspectRatio
                showStats = prefs.gridStats
                showInfoButton = prefs.gridInfo
                showStatusColors = prefs.gridColours
                showNewLabel = prefs.gridNewLabel
                darkenSeen = prefs.gridDarkenSeen
                showGifs = prefs.gridGifs
                hideSeen = prefs.gridHideSeen
            }
            rv.adapter = postsAdapter
        }
        
        // Solicitar carga si no se ha hecho
        if (loadRequested.compareAndSet(false, true)) {
            onLoadPage(pageNumber)
        }
        
        // Actualizar UI con el estado actual
        updateUI()
    }
    
    /**
     * Actualiza las preferencias del grid incluyendo columnas y aspectRatio.
     * Llamado desde MainActivity.updateGridPreferences() cuando vuelve de Settings.
     */
    fun updateGridSettings(newColumns: Int, seenIds: Set<Int>) {
        // Actualizar columnas si cambiaron
        if (newColumns != gridColumns) {
            gridColumns = newColumns
            recyclerView?.get()?.let { rv ->
                (rv.layoutManager as? GridLayoutManager)?.spanCount = newColumns
            }
        }
        
        // Actualizar todas las preferencias del adapter
        postsAdapter?.apply {
            aspectRatio = prefs.gridAspectRatio
            showStats = prefs.gridStats
            showInfoButton = prefs.gridInfo
            showStatusColors = prefs.gridColours
            showNewLabel = prefs.gridNewLabel
            darkenSeen = prefs.gridDarkenSeen
            hideSeen = prefs.gridHideSeen
            showGifs = prefs.gridGifs
            seenPostIds = seenIds
            notifyDataSetChanged()
        }
    }
    
    // Contador de posts ocultos (posts con URLs nulas - contenido restringido)
    var hiddenPostsCount: Int = 0
        private set
    
    // Contador de posts blacklisted (filtrados por blacklist)
    var blacklistedPostsCount: Int = 0
        private set
    
    /**
     * Llamado cuando llegan los posts del servidor
     * @param newPosts Lista de posts recibidos
     * @param requestedLimit El límite que se pidió a la API
     */
    fun onPostsReceived(newPosts: List<Post>, requestedLimit: Int = 75) {
        posts.clear()
        
        // Cargar seenIds para filtrar/marcar posts vistos
        val seenIds = AppSeenDatabase.getAllSeenIdsSync(context)
        
        // PRIMERO: Filtrar posts con URLs nulas (posts especiales/restringidos)
        // Estos son posts que la API devuelve pero sin URLs porque el usuario
        // no tiene acceso al contenido (no usa e621 o no está logueado)
        // La API puede devolver null, "" o literalmente "null" como string
        // Similar a dVar.z() y dVar.y() en la app original (h7/d.java)
        val postsWithValidUrls = newPosts.filter { post ->
            val previewUrl = post.preview.url
            val sampleUrl = post.sample?.url
            val fileUrl = post.file.url
            
            // Verificar que al menos una URL sea válida (no null, no vacía, no "null")
            isValidUrl(previewUrl) || isValidUrl(sampleUrl) || isValidUrl(fileUrl)
        }
        
        // Contar los posts ocultos (posts sin URLs válidas)
        hiddenPostsCount = newPosts.size - postsWithValidUrls.size
        
        // SEGUNDO: Filtrar posts blacklisted (si blacklist está habilitado)
        // Como vi/u.java onPostExecute() - filtra antes de mostrar en grid
        val nonBlacklistedPosts = if (prefs.blacklistEnabled) {
            val filtered = postsWithValidUrls.filter { post ->
                !BlacklistHelper.isPostBlacklisted(post, prefs)
            }
            blacklistedPostsCount = postsWithValidUrls.size - filtered.size
            filtered
        } else {
            blacklistedPostsCount = 0
            postsWithValidUrls
        }
        
        // Guardar los posts NO blacklisted en originalPosts
        // Esto asegura que applyFiltersAndSort() no incluya posts blacklisted
        originalPosts.clear()
        originalPosts.addAll(nonBlacklistedPosts)
        
        // Aplicar filtro de posts vistos si hideSeen está habilitado
        var filteredPosts = nonBlacklistedPosts.toMutableList()
        if (prefs.gridHideSeen) {
            filteredPosts = filteredPosts.filter { !seenIds.contains(it.id) }.toMutableList()
        }
        
        posts.addAll(filteredPosts)
        
        // Actualizar el adapter con los seenIds
        postsAdapter?.seenPostIds = seenIds
        
        isLoading = false
        hasError = false
        errorMessage = null
        
        // Si recibimos menos posts que el límite, es la última página
        // (no hay más posts disponibles para esta búsqueda)
        isLastPage = newPosts.size < requestedLimit
        
        updateUI()
    }
    
    /**
     * Aplica filtros y ordenamiento LOCAL a los posts.
     * Similar al case 17/18 en k1.java de la app original.
     * 
     * @param ratingFilter 0=all, 1=safe, 2=questionable, 3=explicit
     * @param typeFilter 0=all, 1=images, 2=videos, 3=gifs
     * @param orderType 0=newest(default), 1=oldest, 2=score, 3=favcount
     */
    fun applyFiltersAndSort(ratingFilter: Int, typeFilter: Int, orderType: Int) {
        // Empezar con los posts originales
        var filtered = originalPosts.toMutableList()
        
        // Filtrar por rating
        if (ratingFilter > 0) {
            val ratingLetter = when (ratingFilter) {
                1 -> "s" // safe
                2 -> "q" // questionable
                3 -> "e" // explicit
                else -> null
            }
            if (ratingLetter != null) {
                filtered = filtered.filter { it.rating == ratingLetter }.toMutableList()
            }
        }
        
        // Filtrar por tipo de archivo
        if (typeFilter > 0) {
            filtered = filtered.filter { post ->
                val ext = post.file.ext?.lowercase() ?: ""
                when (typeFilter) {
                    1 -> ext in listOf("jpg", "jpeg", "png", "webp") // images
                    2 -> ext in listOf("mp4", "webm") // videos
                    3 -> ext == "gif" // gifs
                    else -> true
                }
            }.toMutableList()
        }
        
        // Ordenar según el tipo
        when (orderType) {
            0 -> { /* newest - orden original, no hacer nada */ }
            1 -> { 
                // oldest - invertir el orden original
                filtered.reverse()
            }
            2 -> {
                // score - ordenar por score descendente (mayor primero)
                filtered.sortByDescending { it.score.total }
            }
            3 -> {
                // favcount - ordenar por favcount descendente (mayor primero)
                filtered.sortByDescending { it.favCount }
            }
        }
        
        // Actualizar la lista de posts mostrados
        posts.clear()
        posts.addAll(filtered)
        updateUI()
    }
    
    /**
     * Llamado cuando hay un error al cargar
     */
    fun onError(error: String?) {
        isLoading = false
        hasError = true
        errorMessage = error ?: "Error"
        posts.clear()
        updateUI()
    }
    
    /**
     * Reintentar carga
     */
    fun retry() {
        isLoading = true
        hasError = false
        errorMessage = null
        isLastPage = false
        loadRequested.set(false)
        
        // Solicitar nueva carga
        if (loadRequested.compareAndSet(false, true)) {
            onLoadPage(pageNumber)
        }
        
        updateUI()
    }
    
    /**
     * Verifica si esta página está "vacía" y no debería añadirse más páginas después.
     * Similar al método c() de ii.h en la app original.
     * 
     * @return true si está vacía Y es la última página (no añadir más)
     */
    fun isEmptyAndLast(): Boolean {
        return originalPosts.isEmpty() && isLastPage && !isLoading && !hasError
    }
    
    /**
     * Verifica si no se deben añadir más páginas después de esta.
     * 
     * @return true si es la última página o está vacía
     */
    fun shouldStopAddingPages(): Boolean {
        // No añadir más si:
        // 1. Es la última página (posts < limit)
        // 2. Está vacía y no hay error (sin más resultados)
        return isLastPage || (originalPosts.isEmpty() && !isLoading && !hasError)
    }
    
    /**
     * Actualiza la UI basándose en el estado actual.
     * Similar a k1.run() case 15 de la app original.
     */
    fun updateUI() {
        val rv = recyclerView?.get()
        val pb = progressBar?.get()
        val emptyTv = emptyText?.get()
        val errorTv = errorText?.get()
        val retryBtn = retryButton?.get()
        
        // Si no tenemos referencias, no hay nada que actualizar
        if (rv == null) return
        
        rv.post {
            // Lógica exacta de la app original (k1.java líneas 344-360):
            // f17999m = hasError
            // f17998l = isLoading (pero invertido - false significa "carga finalizada")
            // f17992e = posts
            
            // Error text y botón retry: visible si hay error
            errorTv?.visibility = if (hasError) android.view.View.VISIBLE else android.view.View.GONE
            retryBtn?.visibility = if (hasError) android.view.View.VISIBLE else android.view.View.GONE
            
            // ProgressBar: visible si está cargando Y NO hay error
            // En la app original: visible si (!isLoading || hasError) ? GONE : VISIBLE
            // O sea: visible cuando NO ha terminado de cargar Y NO hay error
            pb?.visibility = if (isLoading && !hasError) android.view.View.VISIBLE else android.view.View.GONE
            
            // Empty text: visible si lista vacía Y NO hay error Y carga terminada
            // Mostrar mensaje diferente según si es página 1 o páginas posteriores
            if (posts.isEmpty() && !hasError && !isLoading) {
                emptyTv?.visibility = android.view.View.VISIBLE
                // Si es página 1 = "No results found"
                // Si es página > 1 = "End of results" (no hay más páginas)
                val ctx = emptyTv?.context
                if (ctx != null) {
                    val msgResId = if (pageNumber == 1) {
                        com.mommys.app.R.string.no_results
                    } else {
                        com.mommys.app.R.string.no_more_results
                    }
                    emptyTv.text = ctx.getString(msgResId)
                }
            } else {
                emptyTv?.visibility = android.view.View.GONE
            }
            
            // RecyclerView: visible si hay posts o está cargando (para no mostrar vacío de golpe)
            rv.visibility = if (!hasError && (posts.isNotEmpty() || isLoading)) {
                android.view.View.VISIBLE
            } else if (hasError) {
                android.view.View.GONE
            } else {
                android.view.View.GONE
            }
            
            // Actualizar lista de posts
            postsAdapter?.submitList(posts.toList())
        }
    }
    
    /**
     * Verifica si la página está vacía (sin posts y carga finalizada sin error)
     */
    fun isEmpty(): Boolean {
        return posts.isEmpty() && !isLoading && !hasError
    }
    
    /**
     * Obtiene la lista actual de posts filtrados
     */
    fun getCurrentPosts(): List<Post> {
        return posts.toList()
    }
    
    /**
     * Limpia las selecciones en el adapter de esta página
     * Llamado desde MainActivity.clearAllSelections()
     */
    fun clearSelections() {
        postsAdapter?.clearLocalSelections()
    }
    
    /**
     * Selecciona todos los posts de esta página
     * Similar a hVar.e(true) + iterator en di/w.java líneas 99-104
     * 
     * @param callback El callback de selección para notificar cada selección
     */
    fun selectAll(callback: SelectionCallback) {
        posts.forEach { post ->
            callback.onPostSelected(post)
        }
        postsAdapter?.selectAll()
    }
    
    /**
     * Sincroniza el estado de selección con el set global
     * Llamado cuando se navega entre páginas
     */
    fun syncSelections(selectedIds: Set<Int>) {
        postsAdapter?.syncSelections(selectedIds)
    }
    
    /**
     * Verifica si una URL es válida para mostrar contenido.
     * La API puede devolver null, cadena vacía "", o literalmente "null".
     * Similar a la verificación en ii/m.java línea 750 de la app original:
     * if (kVar != null && (str = (String) kVar.f9061c) != null && !str.equals("null"))
     */
    private fun isValidUrl(url: String?): Boolean {
        return url != null && url.isNotEmpty() && url != "null" && url.startsWith("http")
    }
}
