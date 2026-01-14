package com.mommys.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        SeenPostEntity::class,
        DownloadEntity::class,
        SavedQueryEntity::class,
        FollowingTagEntity::class,
        BlacklistTagEntity::class,
        SearchHistoryEntity::class,
        SavedSearchEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun favoriteDao(): FavoriteDao
    abstract fun seenPostDao(): SeenPostDao
    abstract fun downloadDao(): DownloadDao
    abstract fun savedQueryDao(): SavedQueryDao
    abstract fun followingTagDao(): FollowingTagDao
    abstract fun blacklistTagDao(): BlacklistTagDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savedSearchDao(): SavedSearchDao
    
    companion object {
        private const val DATABASE_NAME = "mommys_database"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
