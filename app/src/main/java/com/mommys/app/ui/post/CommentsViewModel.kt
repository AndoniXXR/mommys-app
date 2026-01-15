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
    
    private val _commentEdited = MutableLiveData<Boolean?>()
    val commentEdited: LiveData<Boolean?> = _commentEdited
    
    private val _commentDeleted = MutableLiveData<Boolean?>()
    val commentDeleted: LiveData<Boolean?> = _commentDeleted
    
    private val _voteResult = MutableLiveData<VoteResult?>()
    val voteResult: LiveData<VoteResult?> = _voteResult

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
     * Editar un comentario existente
     * PATCH /comments/{id}.json
     */
    fun editComment(commentId: Int, newBody: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _commentEdited.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.editComment(
                        commentId = commentId,
                        body = newBody
                    )
                }

                if (response.isSuccessful) {
                    _commentEdited.value = true
                    // Actualizar el comentario en la lista local
                    val updatedComment = response.body()
                    if (updatedComment != null) {
                        val currentList = _comments.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == commentId }
                        if (index >= 0) {
                            currentList[index] = updatedComment
                            _comments.value = currentList
                        }
                    }
                } else {
                    val errorCode = response.code()
                    _error.value = when (errorCode) {
                        403, 401 -> "Not authorized to edit this comment"
                        404 -> "Comment not found"
                        422 -> "Invalid comment"
                        else -> "Error: $errorCode"
                    }
                    _commentEdited.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _commentEdited.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Eliminar (ocultar) un comentario
     * DELETE /comments/{id}.json
     */
    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _commentDeleted.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.deleteComment(commentId)
                }

                if (response.isSuccessful) {
                    _commentDeleted.value = true
                    // Remover el comentario de la lista local
                    val currentList = _comments.value?.toMutableList() ?: mutableListOf()
                    currentList.removeAll { it.id == commentId }
                    _comments.value = currentList
                } else {
                    val errorCode = response.code()
                    _error.value = when (errorCode) {
                        403, 401 -> "Not authorized to delete this comment"
                        404 -> "Comment not found"
                        else -> "Error: $errorCode"
                    }
                    _commentDeleted.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _commentDeleted.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Votar un comentario
     * POST /comments/{id}/votes.json
     * @param score 1 = upvote, -1 = downvote
     */
    fun voteComment(commentId: Int, score: Int) {
        viewModelScope.launch {
            _error.value = null
            _voteResult.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.voteComment(
                        commentId = commentId,
                        score = score,
                        noUnvote = false
                    )
                }

                if (response.isSuccessful) {
                    val voteResponse = response.body()
                    if (voteResponse != null) {
                        // Actualizar el score del comentario en la lista local
                        val currentList = _comments.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == commentId }
                        if (index >= 0) {
                            val oldComment = currentList[index]
                            // Crear copia con nuevo score
                            currentList[index] = oldComment.copy(score = voteResponse.score)
                            _comments.value = currentList
                        }
                        
                        _voteResult.value = VoteResult(
                            commentId = commentId,
                            newScore = voteResponse.score,
                            ourScore = voteResponse.our_score,
                            success = true
                        )
                    }
                } else {
                    val errorCode = response.code()
                    _error.value = when (errorCode) {
                        403, 401 -> "Login required to vote"
                        422 -> "You cannot vote on your own comment"
                        else -> "Error: $errorCode"
                    }
                    _voteResult.value = VoteResult(commentId = commentId, success = false)
                }
            } catch (e: Exception) {
                _error.value = e.message
                _voteResult.value = VoteResult(commentId = commentId, success = false)
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
    
    /**
     * Obtener el postId actual
     */
    fun getCurrentPostId(): Int = currentPostId
    
    /**
     * Resultado de votación
     */
    data class VoteResult(
        val commentId: Int,
        val newScore: Int = 0,
        val ourScore: Int = 0,
        val success: Boolean
    )
}
