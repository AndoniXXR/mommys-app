package com.mommys.app.data.db.seen

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for storing seen/viewed post IDs.
 * Similar to se.zepiwolf.tws.data.db.seen.AppSeenDatabase
 */
@Database(entities = [SeenEntity::class], version = 1, exportSchema = false)
abstract class AppSeenDatabase : RoomDatabase() {
    
    abstract fun seenDao(): SeenDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppSeenDatabase? = null
        
        fun getInstance(context: Context): AppSeenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppSeenDatabase::class.java,
                    "tws-sdb"  // Same name as original app for compatibility
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Mark a post as seen in a background thread
         */
        fun markSeen(context: Context, postId: Int) {
            Thread {
                try {
                    getInstance(context).seenDao().markSeen(SeenEntity(postId))
                } catch (e: Exception) {
                    // Ignore errors
                }
            }.start()
        }
        
        /**
         * Check if a post has been seen (synchronous, use from background thread)
         */
        fun isSeenSync(context: Context, postId: Int): Boolean {
            return try {
                getInstance(context).seenDao().isSeen(postId)
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Get all seen post IDs (synchronous, use from background thread)
         */
        fun getAllSeenIdsSync(context: Context): Set<Int> {
            return try {
                getInstance(context).seenDao().getAllSeenIds().toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        
        /**
         * Get count of seen posts
         */
        fun getSeenCountSync(context: Context): Int {
            return try {
                getInstance(context).seenDao().getSeenCount()
            } catch (e: Exception) {
                0
            }
        }
        
        /**
         * Clear all seen posts
         */
        fun clearAll(context: Context, onComplete: (() -> Unit)? = null) {
            Thread {
                try {
                    getInstance(context).seenDao().clearAll()
                    onComplete?.invoke()
                } catch (e: Exception) {
                    // Ignore errors
                }
            }.start()
        }
    }
}
