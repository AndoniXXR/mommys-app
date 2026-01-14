package com.mommys.app.data.db.following

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room para posts de tags seguidos
 * Como AppFollowingPostDatabase en la app original (nombre DB: "tws-fp")
 */
@Database(
    entities = [FollowingPost::class],
    version = 1,
    exportSchema = false
)
abstract class AppFollowingPostDatabase : RoomDatabase() {
    
    abstract fun followingPostDao(): FollowingPostDao
    
    companion object {
        private const val DATABASE_NAME = "mommys-fp"
        
        @Volatile
        private var INSTANCE: AppFollowingPostDatabase? = null
        
        fun getInstance(context: Context): AppFollowingPostDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppFollowingPostDatabase::class.java,
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
