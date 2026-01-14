package com.mommys.app.ui.post

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel para manejar comentarios
 * Como la lógica en CommentsActivity de la app original
 */
class CommentsViewModel : ViewModel() {

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> = _comments

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _commentPosted = MutableLiveData<Boolean?>()
    val commentPosted: LiveData<Boolean?> = _commentPosted

    private var currentPage = 1
    private var hasMorePages = true
    private var currentPostId = 0

    /**
     * Cargar comentarios de un post
     * GET /comments.json?search[post_id]=xxx
     */
    fun loadComments(postId: Int, loadMore: Boolean = false) {
        if (_isLoading.value == true) return

        if (!loadMore) {
            currentPage = 1
            hasMorePages = true
            currentPostId = postId
        } else if (!hasMorePages) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.getComments(
                        postId = postId,
                        page = currentPage,
                        limit = 100
                    )
                }

                if (response.isSuccessful) {
                    val newComments = response.body() ?: emptyList()
                    
                    if (newComments.isEmpty()) {
                        hasMorePages = false
                    } else {
                        currentPage++
                        
                        val currentList = if (loadMore) {
                            (_comments.value ?: emptyList()) + newComments
                        } else {
                            newComments
                        }
                        
                        _comments.value = currentList
                    }
                } else {
                    val errorCode = response.code()
                    _error.value = when (errorCode) {
                        403, 401 -> "Login required"
                        404 -> "Post not found"
                        else -> "Error: $errorCode"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Publicar un nuevo comentario
     * POST /comments.json
     */
    fun postComment(postId: Int, body: String, doNotBump: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _commentPosted.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createComment(
                        postId = postId,
                        body = body,
                        doNotBump = doNotBump
                    )
                }

                if (response.isSuccessful) {
                    _commentPosted.value = true
                } else {
                    val errorCode = response.code()
                    _error.value = when (errorCode) {
                        403, 401 -> "Login required to post comments"
                        422 -> "Invalid comment"
                        else -> "Error: $errorCode"
                    }
                    _commentPosted.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _commentPosted.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Cargar más comentarios (paginación)
     */
    fun loadMore() {
        if (currentPostId > 0) {
            loadComments(currentPostId, loadMore = true)
        }
    }
}
