package com.mommys.app.data.db.seen

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for seen/viewed posts
 */
@Dao
interface SeenDao {
    
    @Query("SELECT id FROM seen")
    fun getAllSeenIds(): List<Int>
    
    @Query("SELECT COUNT(*) FROM seen")
    fun getSeenCount(): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM seen WHERE id = :postId)")
    fun isSeen(postId: Int): Boolean
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun markSeen(entity: SeenEntity)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun markSeenAll(entities: List<SeenEntity>)
    
    @Query("DELETE FROM seen WHERE id = :postId")
    fun removeSeen(postId: Int)
    
    @Query("DELETE FROM seen")
    fun clearAll()
}
