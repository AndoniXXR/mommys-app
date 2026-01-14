package com.mommys.app.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.model.Post
import kotlinx.coroutines.launch

/**
 * ViewModel para MainActivity
 */
class MainViewModel : ViewModel() {
    
    private val api = ApiClient.getApiService()
    
    private val _posts = MutableLiveData<List<Post>>(emptyList())
    val posts: LiveData<List<Post>> = _posts
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error
    
    /**
     * Cargar posts (reemplaza lista actual)
     */
    fun loadPosts(tags: String, page: Int, limit: Int = 75) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val response = api.getPosts(
                    tags = tags.ifEmpty { null },
                    page = page,
                    limit = limit
                )
                
                if (response.isSuccessful) {
                    _posts.value = response.body()?.posts ?: emptyList()
                } else {
                    _error.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Cargar más posts (agrega a lista actual)
     */
    fun loadMorePosts(tags: String, page: Int, limit: Int = 75) {
        viewModelScope.launch {
            if (_isLoading.value == true) return@launch
            
            _isLoading.value = true
            
            try {
                val response = api.getPosts(
                    tags = tags.ifEmpty { null },
                    page = page,
                    limit = limit
                )
                
                if (response.isSuccessful) {
                    val currentList = _posts.value?.toMutableList() ?: mutableListOf()
                    val newPosts = response.body()?.posts ?: emptyList()
                    currentList.addAll(newPosts)
                    _posts.value = currentList
                } else {
                    _error.value = "Error: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
