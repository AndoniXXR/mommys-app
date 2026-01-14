package com.mommys.app.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): LiveData<List<FavoriteEntity>>
    
    @Query("SELECT postId FROM favorites")
    suspend fun getAllFavoriteIds(): List<Int>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE postId = :postId)")
    suspend fun isFavorite(postId: Int): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE postId = :postId)")
    fun isFavoriteLive(postId: Int): LiveData<Boolean>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)
    
    @Query("DELETE FROM favorites WHERE postId = :postId")
    suspend fun delete(postId: Int)
    
    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}

@Dao
interface SeenPostDao {
    @Query("SELECT * FROM seen_posts ORDER BY seenAt DESC LIMIT :limit")
    fun getRecentSeen(limit: Int = 100): LiveData<List<SeenPostEntity>>
    
    @Query("SELECT postId FROM seen_posts")
    suspend fun getAllSeenIds(): List<Int>
    
    @Query("SELECT EXISTS(SELECT 1 FROM seen_posts WHERE postId = :postId)")
    suspend fun isSeen(postId: Int): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(seen: SeenPostEntity)
    
    @Query("DELETE FROM seen_posts WHERE postId = :postId")
    suspend fun delete(postId: Int)
    
    @Query("DELETE FROM seen_posts")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): LiveData<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE postId = :postId")
    suspend fun getDownload(postId: Int): DownloadEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE postId = :postId)")
    suspend fun isDownloaded(postId: Int): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)
    
    @Query("DELETE FROM downloads WHERE postId = :postId")
    suspend fun delete(postId: Int)
    
    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}

@Dao
interface SavedQueryDao {
    @Query("SELECT * FROM saved_queries ORDER BY createdAt DESC")
    fun getAllQueries(): LiveData<List<SavedQueryEntity>>
    
    @Query("SELECT * FROM saved_queries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentQueries(limit: Int = 20): List<SavedQueryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(query: SavedQueryEntity)
    
    @Query("DELETE FROM saved_queries WHERE id = :id")
    suspend fun delete(id: Int)
    
    @Query("DELETE FROM saved_queries")
    suspend fun deleteAll()
}

@Dao
interface FollowingTagDao {
    @Query("SELECT * FROM following_tags ORDER BY tagName ASC")
    fun getAllFollowing(): LiveData<List<FollowingTagEntity>>
    
    @Query("SELECT * FROM following_tags WHERE newPostsCount > 0")
    suspend fun getTagsWithNewPosts(): List<FollowingTagEntity>
    
    @Query("SELECT EXISTS(SELECT 1 FROM following_tags WHERE tagName = :tagName)")
    suspend fun isFollowing(tagName: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: FollowingTagEntity)
    
    @Update
    suspend fun update(tag: FollowingTagEntity)
    
    @Query("DELETE FROM following_tags WHERE tagName = :tagName")
    suspend fun delete(tagName: String)
}

@Dao
interface BlacklistTagDao {
    @Query("SELECT * FROM blacklist_tags ORDER BY tagName ASC")
    fun getAllBlacklist(): LiveData<List<BlacklistTagEntity>>
    
    @Query("SELECT tagName FROM blacklist_tags")
    suspend fun getAllBlacklistTags(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: BlacklistTagEntity)
    
    @Query("DELETE FROM blacklist_tags WHERE tagName = :tagName")
    suspend fun delete(tagName: String)
    
    @Query("DELETE FROM blacklist_tags")
    suspend fun deleteAll()
}

/**
 * DAO para historial de búsquedas
 */
@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 50): LiveData<List<SearchHistoryEntity>>
    
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun getRecentSearchesSync(limit: Int = 50): List<SearchHistoryEntity>
    
    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY useCount DESC, searchedAt DESC LIMIT :limit")
    suspend fun searchHistory(query: String, limit: Int = 20): List<SearchHistoryEntity>
    
    @Query("SELECT * FROM search_history WHERE query = :query LIMIT 1")
    suspend fun findByQuery(query: String): SearchHistoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistoryEntity)
    
    @Query("UPDATE search_history SET useCount = useCount + 1, searchedAt = :timestamp WHERE query = :query")
    suspend fun incrementUseCount(query: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Int)
    
    @Query("DELETE FROM search_history")
    suspend fun deleteAll()
    
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY searchedAt DESC LIMIT :keepCount)")
    suspend fun pruneOldEntries(keepCount: Int = 100)
}

/**
 * DAO para búsquedas guardadas (favoritas)
 */
@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY lastUsedAt DESC")
    fun getAllSavedSearches(): LiveData<List<SavedSearchEntity>>
    
    @Query("SELECT * FROM saved_searches ORDER BY lastUsedAt DESC")
    suspend fun getAllSavedSearchesSync(): List<SavedSearchEntity>
    
    @Query("SELECT * FROM saved_searches WHERE name LIKE '%' || :query || '%' OR query LIKE '%' || :query || '%' ORDER BY useCount DESC LIMIT :limit")
    suspend fun searchSaved(query: String, limit: Int = 20): List<SavedSearchEntity>
    
    @Query("SELECT * FROM saved_searches WHERE id = :id")
    suspend fun getById(id: Int): SavedSearchEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(search: SavedSearchEntity): Long
    
    @Update
    suspend fun update(search: SavedSearchEntity)
    
    @Query("UPDATE saved_searches SET name = :name, query = :query WHERE id = :id")
    suspend fun updateNameAndQuery(id: Int, name: String, query: String)
    
    @Query("UPDATE saved_searches SET useCount = useCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUseCount(id: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM saved_searches WHERE id = :id")
    suspend fun delete(id: Int)
    
    @Query("DELETE FROM saved_searches")
    suspend fun deleteAll()
}
