package com.mommys.app.ui.post

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.DownloadNotificationHelper
import com.mommys.app.util.PostDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resultado de una acción (voto, favorito, etc)
 * Contiene el postId para identificar qué post actualizar
 */
data class ActionResult(
    val postId: Int,
    val success: Boolean,
    val newState: Int = 0,  // Para votos: -1, 0, 1. Para favoritos: 0 o 1
    val messageResId: Int? = null
)

/**
 * Resultado de una descarga
 */
data class DownloadResult(
    val postId: Int,
    val success: Boolean,
    val fileName: String? = null,
    val messageResId: Int? = null,
    val errorMessage: String? = null
)

/**
 * Progreso de descarga
 */
data class DownloadProgress(
    val postId: Int,
    val progress: Int,  // 0-100
    val isDownloading: Boolean
)

class PostViewModel : ViewModel() {

    // Lista de posts para ViewPager2
    private val _posts = MutableLiveData<List<Post>>()
    val posts: LiveData<List<Post>> = _posts

    // Post actual (para compatibilidad)
    private val _post = MutableLiveData<Post?>()
    val post: LiveData<Post?> = _post

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isFavorite = MutableLiveData<Boolean>()
    val isFavorite: LiveData<Boolean> = _isFavorite

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _favoriteMessage = MutableLiveData<Int?>()
    val favoriteMessage: LiveData<Int?> = _favoriteMessage
    
    // Mensajes de votos
    private val _voteMessage = MutableLiveData<Int?>()
    val voteMessage: LiveData<Int?> = _voteMessage
    
    // Resultado de acciones de voto (para actualizar UI)
    private val _voteResult = MutableLiveData<ActionResult?>()
    val voteResult: LiveData<ActionResult?> = _voteResult
    
    // Resultado de acciones de favorito
    private val _favoriteResult = MutableLiveData<ActionResult?>()
    val favoriteResult: LiveData<ActionResult?> = _favoriteResult
    
    // Resultado de acciones de descarga
    private val _downloadResult = MutableLiveData<DownloadResult?>()
    val downloadResult: LiveData<DownloadResult?> = _downloadResult
    
    // Progreso de descarga (0-100)
    private val _downloadProgress = MutableLiveData<DownloadProgress?>()
    val downloadProgress: LiveData<DownloadProgress?> = _downloadProgress
    
    // Estado de votos por post (para tracking)
    // 1 = upvoted, -1 = downvoted, 0 = no vote
    private val voteStates = mutableMapOf<Int, Int>()
    
    // Set de posts favoritos (por ahora en memoria)
    private val favoriteIds = mutableSetOf<Int>()

    /**
     * Cargar un solo post por ID
     */
    fun loadPost(postId: Int) {
        Log.d("PostViewModel", "=== loadPost START ===")
        Log.d("PostViewModel", "Loading post with ID: $postId")
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                Log.d("PostViewModel", "Making API call for post $postId")
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getPost(postId)
                }
                Log.d("PostViewModel", "API response received, success: ${response.isSuccessful}, code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val post = response.body()?.post
                    Log.d("PostViewModel", "Post from response: ${post?.id ?: "null"}")
                    _post.value = post
                    if (post != null) {
                        Log.d("PostViewModel", "Setting _posts with single post")
                        _posts.value = listOf(post)
                        Log.d("PostViewModel", "_posts.value set, size: ${_posts.value?.size}")
                    }
                    checkIfFavorite(postId)
                } else {
                    Log.e("PostViewModel", "API error: ${response.code()}")
                    _error.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e("PostViewModel", "Exception in loadPost: ${e.message}", e)
                _error.value = e.message ?: "Error loading post"
            } finally {
                _isLoading.value = false
                Log.d("PostViewModel", "=== loadPost END ===")
            }
        }
    }
    
    /**
     * Cargar múltiples posts por sus IDs (para ViewPager2)
     */
    fun loadPosts(postIds: List<Int>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val loadedPosts = mutableListOf<Post>()
                
                // Cargar posts en paralelo con un límite
                postIds.chunked(10).forEach { chunk ->
                    chunk.forEach { postId ->
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.apiService.getPost(postId)
                            }
                            if (response.isSuccessful) {
                                response.body()?.post?.let { loadedPosts.add(it) }
                            }
                        } catch (e: Exception) {
                            // Continuar con los otros posts
                        }
                    }
                }
                
                if (loadedPosts.isNotEmpty()) {
                    _posts.value = loadedPosts
                    _post.value = loadedPosts.first()
                } else {
                    _error.value = "No posts found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Error loading posts"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Verificar si un post es favorito
     */
    private fun checkIfFavorite(postId: Int) {
        _isFavorite.value = favoriteIds.contains(postId)
    }
    
    /**
     * Verificar estado de favorito (público)
     */
    fun checkFavoriteStatus(postId: Int) {
        checkIfFavorite(postId)
    }

    /**
     * Toggle favorito legacy (para compatibilidad)
     */
    fun toggleFavorite() {
        val postId = _post.value?.id ?: return
        toggleFavorite(postId)
    }
    
    /**
     * Toggle favorito por ID
     * Como la app original ei/e0.java método a():
     * - Si es favorito: DELETE /favorites/{post_id}.json
     * - Si no es favorito: POST /favorites.json con body {post_id: id}
     */
    fun toggleFavorite(postId: Int) {
        viewModelScope.launch {
            try {
                val currentFavorite = favoriteIds.contains(postId)
                
                val response = withContext(Dispatchers.IO) {
                    if (currentFavorite) {
                        // Quitar favorito
                        ApiClient.apiService.removeFavorite(postId)
                    } else {
                        // Agregar favorito con body JSON
                        val body = mapOf("post_id" to postId)
                        ApiClient.apiService.addFavorite(body)
                    }
                }
                
                val responseCode = response.code()
                
                if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                    // Éxito
                    if (currentFavorite) {
                        favoriteIds.remove(postId)
                        _isFavorite.value = false
                        _favoriteMessage.value = R.string.action_favourite_removed
                    } else {
                        favoriteIds.add(postId)
                        _isFavorite.value = true
                        _favoriteMessage.value = R.string.action_favourite_added
                    }
                    
                    // Emitir resultado exitoso
                    _favoriteResult.value = ActionResult(
                        postId = postId,
                        success = true,
                        newState = if (currentFavorite) 0 else 1,
                        messageResId = _favoriteMessage.value
                    )
                } else if (responseCode == 403 || responseCode == 401) {
                    _favoriteMessage.value = R.string.action_forbidden
                    _favoriteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = if (currentFavorite) 1 else 0,  // Mantener estado actual
                        messageResId = R.string.action_forbidden
                    )
                } else if (responseCode == 422) {
                    // Ya está en favoritos o error de validación
                    _favoriteMessage.value = R.string.action_favourite_already_added
                    _favoriteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = 1,  // Ya es favorito
                        messageResId = R.string.action_favourite_already_added
                    )
                } else {
                    _error.value = "Error: $responseCode"
                    _favoriteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = if (currentFavorite) 1 else 0,
                        messageResId = null
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
                val currentFavorite = favoriteIds.contains(postId)
                _favoriteResult.value = ActionResult(
                    postId = postId,
                    success = false,
                    newState = if (currentFavorite) 1 else 0,
                    messageResId = null
                )
            }
        }
    }
    
    /**
     * Limpiar resultado de favorito
     */
    fun clearFavoriteResult() {
        _favoriteResult.value = null
    }
    
    /**
     * Votar arriba
     * Como la app original ei/n.java case 1:
     * /posts/{id}/votes.json?score=1&no_unvote=true/false
     * no_unvote=true significa que si ya votó, mantener el voto
     * no_unvote=false hace toggle del voto
     */
    fun voteUp(postId: Int) {
        viewModelScope.launch {
            try {
                val currentVote = voteStates[postId] ?: 0
                val isCurrentlyUpvoted = currentVote == 1
                
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.votePost(
                        id = postId,
                        score = 1,
                        noUnvote = false
                    )
                }
                
                val responseCode = response.code()
                
                if (responseCode == 200 || responseCode == 201 || responseCode == 204 || responseCode == 423) {
                    val voteResponse = response.body()
                    val newOurScore = voteResponse?.our_score ?: 0
                    voteStates[postId] = newOurScore
                    
                    _voteMessage.value = when {
                        newOurScore == 1 && !isCurrentlyUpvoted -> R.string.action_voted_up
                        newOurScore == 1 && isCurrentlyUpvoted -> R.string.action_voted_up_already
                        newOurScore == 0 -> R.string.action_voted_removed_up
                        else -> R.string.action_voted_up
                    }
                    
                    // Emitir resultado exitoso para actualizar UI
                    _voteResult.value = ActionResult(
                        postId = postId,
                        success = true,
                        newState = newOurScore,
                        messageResId = _voteMessage.value
                    )
                } else if (responseCode == 403 || responseCode == 401) {
                    _voteMessage.value = R.string.action_forbidden
                    // Emitir resultado fallido - el adaptador NO debe actualizar el botón
                    _voteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = currentVote,  // Mantener estado actual
                        messageResId = R.string.action_forbidden
                    )
                } else {
                    _error.value = "Error: $responseCode"
                    _voteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = currentVote,
                        messageResId = null
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
                val currentVote = voteStates[postId] ?: 0
                _voteResult.value = ActionResult(
                    postId = postId,
                    success = false,
                    newState = currentVote,
                    messageResId = null
                )
            }
        }
    }
    
    /**
     * Votar abajo
     * Como la app original ei/n.java case 2:
     * /posts/{id}/votes.json?score=-1&no_unvote=true/false
     */
    fun voteDown(postId: Int) {
        viewModelScope.launch {
            try {
                val currentVote = voteStates[postId] ?: 0
                val isCurrentlyDownvoted = currentVote == -1
                
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.votePost(
                        id = postId,
                        score = -1,
                        noUnvote = false
                    )
                }
                
                val responseCode = response.code()
                
                if (responseCode == 200 || responseCode == 201 || responseCode == 204 || responseCode == 423) {
                    val voteResponse = response.body()
                    val newOurScore = voteResponse?.our_score ?: 0
                    voteStates[postId] = newOurScore
                    
                    _voteMessage.value = when {
                        newOurScore == -1 && !isCurrentlyDownvoted -> R.string.action_voted_down
                        newOurScore == -1 && isCurrentlyDownvoted -> R.string.action_voted_down_already
                        newOurScore == 0 -> R.string.action_voted_removed_down
                        else -> R.string.action_voted_down
                    }
                    
                    // Emitir resultado exitoso
                    _voteResult.value = ActionResult(
                        postId = postId,
                        success = true,
                        newState = newOurScore,
                        messageResId = _voteMessage.value
                    )
                } else if (responseCode == 403 || responseCode == 401) {
                    _voteMessage.value = R.string.action_forbidden
                    _voteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = currentVote,
                        messageResId = R.string.action_forbidden
                    )
                } else {
                    _error.value = "Error: $responseCode"
                    _voteResult.value = ActionResult(
                        postId = postId,
                        success = false,
                        newState = currentVote,
                        messageResId = null
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
                val currentVote = voteStates[postId] ?: 0
                _voteResult.value = ActionResult(
                    postId = postId,
                    success = false,
                    newState = currentVote,
                    messageResId = null
                )
            }
        }
    }
    
    /**
     * Limpiar resultado de voto
     */
    fun clearVoteResult() {
        _voteResult.value = null
    }
    
    /**
     * Limpiar mensaje de voto
     */
    fun clearVoteMessage() {
        _voteMessage.value = null
    }
    
    /**
     * Descargar post
     * Como la app original ei/p.java case 0:
     * - Verifica que el post no esté borrado
     * - Descarga el archivo a la galería
     * - Auto-upvote si preferencia post_action_upvote_on_download está habilitada
     * - Auto-favorite si preferencia post_action_fav_on_download está habilitada
     */
    fun downloadPost(context: Context, post: Post, prefsManager: PreferencesManager) {
        viewModelScope.launch {
            // ID de notificación para este post
            var notificationId = -1
            
            // Generar nombre de archivo para mostrar en notificación
            val fileName = "Post #${post.id}"
            
            try {
                // Verificar que el post tenga URL
                if (post.file.url == null) {
                    _downloadResult.value = DownloadResult(
                        postId = post.id,
                        success = false,
                        messageResId = R.string.post_error_deleted
                    )
                    return@launch
                }
                
                // Notificar inicio de descarga
                _downloadProgress.value = DownloadProgress(
                    postId = post.id,
                    progress = 0,
                    isDownloading = true
                )
                
                // Mostrar notificación de inicio de descarga
                notificationId = DownloadNotificationHelper.showDownloadStartNotification(
                    context, post.id, fileName
                )
                
                // Descargar usando PostDownloader
                Log.d("PostViewModel", "Starting download for post ${post.id}, notificationId=$notificationId")
                var downloadedFileResult: PostDownloader.DownloadedFile? = null
                val result = PostDownloader.downloadPost(
                    context = context,
                    post = post,
                    prefsManager = prefsManager,
                    callback = object : PostDownloader.DownloadCallback {
                        override fun onStart() {
                            Log.d("PostViewModel", "Download onStart")
                        }
                        
                        override fun onProgress(progress: Int) {
                            _downloadProgress.postValue(DownloadProgress(
                                postId = post.id,
                                progress = progress,
                                isDownloading = true
                            ))
                            
                            // Actualizar notificación de progreso
                            DownloadNotificationHelper.updateDownloadProgress(
                                context, notificationId, progress, fileName
                            )
                        }
                        
                        override fun onSuccess(downloadedFile: PostDownloader.DownloadedFile) {
                            Log.d("PostViewModel", "Download onSuccess: ${downloadedFile.fileName}, uri=${downloadedFile.uri}")
                            downloadedFileResult = downloadedFile
                        }
                        
                        override fun onError(error: String) {
                            Log.e("PostViewModel", "Download onError: $error")
                        }
                    }
                )
                
                Log.d("PostViewModel", "Download completed, result=$result")
                
                if (result != null) {
                    // Descarga exitosa
                    _downloadResult.value = DownloadResult(
                        postId = post.id,
                        success = true,
                        messageResId = R.string.action_downloaded
                    )
                    
                    // Mostrar notificación de completado (en Main thread)
                    withContext(Dispatchers.Main) {
                        DownloadNotificationHelper.showDownloadCompleteNotification(
                            context, notificationId, fileName, result.uri, result.mimeType
                        )
                    }
                    
                    // Auto-acciones como la app original ei/p.java
                    // Auto-upvote on download
                    if (prefsManager.postUpvoteOnDownload) {
                        voteUp(post.id)
                    }
                    
                    // Auto-favorite on download
                    if (prefsManager.postFavOnDownload) {
                        if (!favoriteIds.contains(post.id)) {
                            toggleFavorite(post.id)
                        }
                    }
                } else {
                    _downloadResult.value = DownloadResult(
                        postId = post.id,
                        success = false,
                        messageResId = R.string.action_download_failed
                    )
                    
                    // Mostrar notificación de error (en Main thread)
                    withContext(Dispatchers.Main) {
                        DownloadNotificationHelper.showDownloadErrorNotification(
                            context, notificationId, fileName, 
                            context.getString(R.string.action_download_failed)
                        )
                    }
                }
                
                // Limpiar progreso
                _downloadProgress.value = DownloadProgress(
                    postId = post.id,
                    progress = 100,
                    isDownloading = false
                )
                
            } catch (e: Exception) {
                _downloadResult.value = DownloadResult(
                    postId = post.id,
                    success = false,
                    errorMessage = e.message
                )
                _downloadProgress.value = DownloadProgress(
                    postId = post.id,
                    progress = 0,
                    isDownloading = false
                )
                
                // Mostrar notificación de error (en Main thread)
                withContext(Dispatchers.Main) {
                    DownloadNotificationHelper.showDownloadErrorNotification(
                        context, notificationId, fileName,
                        e.message ?: context.getString(R.string.action_download_failed)
                    )
                }
            }
        }
    }
    
    /**
     * Limpiar resultado de descarga
     */
    fun clearDownloadResult() {
        _downloadResult.value = null
    }
    
    /**
     * Limpiar progreso de descarga
     */
    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }
    
    /**
     * Limpiar mensaje de favorito
     */
    fun clearFavoriteMessage() {
        _favoriteMessage.value = null
    }
    
    /**
     * Recargar un post desde el API
     * Como la app original com/applovin/impl/s9.java reload_post
     */
    fun reloadPost(postId: Int, callback: (Post?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getPost(postId)
                }
                if (response.isSuccessful) {
                    val post = response.body()?.post
                    withContext(Dispatchers.Main) {
                        callback(post)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
}
