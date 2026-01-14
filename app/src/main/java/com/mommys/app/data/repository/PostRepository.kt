package com.mommys.app.data.repository

import androidx.lifecycle.LiveData
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.database.*
import com.mommys.app.data.model.Post
import com.mommys.app.data.model.PostsResponse
import com.mommys.app.data.model.SinglePostResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository(private val database: AppDatabase) {
    
    private val apiService = ApiClient.apiService
    
    // API calls
    suspend fun getPosts(
        tags: String? = null,
        page: Int = 1,
        limit: Int = 75
    ): PostsResponse? = withContext(Dispatchers.IO) {
        val response = apiService.getPosts(tags, page, limit)
        if (response.isSuccessful) response.body() else null
    }
    
    suspend fun getPost(postId: Int): SinglePostResponse? = withContext(Dispatchers.IO) {
        val response = apiService.getPost(postId)
        if (response.isSuccessful) response.body() else null
    }
    
    suspend fun getPopularPosts(): PostsResponse? = withContext(Dispatchers.IO) {
        val response = apiService.getPosts(tags = "order:score", limit = 50)
        if (response.isSuccessful) response.body() else null
    }
    
    // Favorites
    fun getFavorites(): LiveData<List<FavoriteEntity>> = database.favoriteDao().getAllFavorites()
    
    suspend fun isFavorite(postId: Int): Boolean = database.favoriteDao().isFavorite(postId)
    
    fun isFavoriteLive(postId: Int): LiveData<Boolean> = database.favoriteDao().isFavoriteLive(postId)
    
    suspend fun addFavorite(postId: Int) {
        database.favoriteDao().insert(FavoriteEntity(postId))
    }
    
    suspend fun removeFavorite(postId: Int) {
        database.favoriteDao().delete(postId)
    }
    
    // Seen posts
    suspend fun markAsSeen(postId: Int) {
        database.seenPostDao().insert(SeenPostEntity(postId))
    }
    
    suspend fun isSeen(postId: Int): Boolean = database.seenPostDao().isSeen(postId)
    
    suspend fun getSeenIds(): List<Int> = database.seenPostDao().getAllSeenIds()
    
    // Downloads
    fun getDownloads(): LiveData<List<DownloadEntity>> = database.downloadDao().getAllDownloads()
    
    suspend fun saveDownload(postId: Int, filePath: String, thumbnailPath: String?, fileType: String) {
        database.downloadDao().insert(DownloadEntity(postId, filePath, thumbnailPath, fileType))
    }
    
    suspend fun isDownloaded(postId: Int): Boolean = database.downloadDao().isDownloaded(postId)
    
    suspend fun removeDownload(postId: Int) {
        database.downloadDao().delete(postId)
    }
    
    // Saved queries
    fun getSavedQueries(): LiveData<List<SavedQueryEntity>> = database.savedQueryDao().getAllQueries()
    
    suspend fun getRecentQueries(limit: Int = 20): List<SavedQueryEntity> = 
        database.savedQueryDao().getRecentQueries(limit)
    
    suspend fun saveQuery(query: String) {
        database.savedQueryDao().insert(SavedQueryEntity(query = query))
    }
    
    // Following tags
    fun getFollowingTags(): LiveData<List<FollowingTagEntity>> = database.followingTagDao().getAllFollowing()
    
    suspend fun isFollowing(tagName: String): Boolean = database.followingTagDao().isFollowing(tagName)
    
    suspend fun followTag(tagName: String) {
        database.followingTagDao().insert(FollowingTagEntity(tagName))
    }
    
    suspend fun unfollowTag(tagName: String) {
        database.followingTagDao().delete(tagName)
    }
    
    // Blacklist
    fun getBlacklist(): LiveData<List<BlacklistTagEntity>> = database.blacklistTagDao().getAllBlacklist()
    
    suspend fun getBlacklistTags(): List<String> = database.blacklistTagDao().getAllBlacklistTags()
    
    suspend fun addToBlacklist(tagName: String) {
        database.blacklistTagDao().insert(BlacklistTagEntity(tagName))
    }
    
    suspend fun removeFromBlacklist(tagName: String) {
        database.blacklistTagDao().delete(tagName)
    }
    
    // Filter posts by blacklist
    suspend fun filterByBlacklist(posts: List<Post>): List<Post> {
        val blacklist = getBlacklistTags().toSet()
        if (blacklist.isEmpty()) return posts
        
        return posts.filter { post ->
            val allTags = post.tags.general + post.tags.artist + post.tags.species + 
                          post.tags.character + post.tags.copyright + post.tags.meta
            allTags.none { it in blacklist }
        }
    }
}
