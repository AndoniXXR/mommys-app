package com.mommys.app.data.db.following

import androidx.room.*

/**
 * DAO para operaciones con FollowingPost
 * Como C2884b en la app original
 */
@Dao
interface FollowingPostDao {
    
    @Query("SELECT * FROM FollowingPost ORDER BY added_date DESC")
    suspend fun getAllPosts(): List<FollowingPost>
    
    @Query("SELECT * FROM FollowingPost ORDER BY added_date ASC")
    suspend fun getAllPostsOldestFirst(): List<FollowingPost>
    
    @Query("SELECT * FROM FollowingPost ORDER BY added_date DESC")
    suspend fun getAllPostsNewestFirst(): List<FollowingPost>
    
    @Query("SELECT * FROM FollowingPost WHERE postID = :postId")
    suspend fun getPostById(postId: Int): FollowingPost?
    
    @Query("SELECT postID FROM FollowingPost")
    suspend fun getAllPostIds(): List<Int>
    
    @Query("SELECT COUNT(*) FROM FollowingPost")
    suspend fun getCount(): Int
    
    @Query("SELECT * FROM FollowingPost WHERE query_string = :tag ORDER BY added_date DESC")
    suspend fun getPostsByTag(tag: String): List<FollowingPost>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: FollowingPost): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(posts: List<FollowingPost>): List<Long>
    
    @Delete
    suspend fun delete(post: FollowingPost)
    
    @Query("DELETE FROM FollowingPost WHERE postID = :postId")
    suspend fun deleteById(postId: Int)
    
    @Query("DELETE FROM FollowingPost")
    suspend fun deleteAll()
    
    @Query("DELETE FROM FollowingPost WHERE added_date < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM FollowingPost WHERE added_date <= :timestamp")
    suspend fun deleteOlderThanOrEqual(timestamp: Long)
    
    @Query("DELETE FROM FollowingPost WHERE added_date >= :timestamp")
    suspend fun deleteNewerThanOrEqual(timestamp: Long)
}
