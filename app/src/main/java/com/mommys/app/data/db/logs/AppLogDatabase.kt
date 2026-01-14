package com.mommys.app.data.db.logs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for storing application logs related to Following notifications
 */
@Database(entities = [LogEntity::class], version = 1, exportSchema = false)
abstract class AppLogDatabase : RoomDatabase() {
    
    abstract fun logDao(): LogDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppLogDatabase? = null
        
        fun getInstance(context: Context): AppLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLogDatabase::class.java,
                    "tws-log"  // Same name as original app for compatibility
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Insert a log entry - can be called from any thread
         */
        fun log(context: Context, message: String) {
            Thread {
                try {
                    getInstance(context).logDao().insert(
                        LogEntity(message = message)
                    )
                } catch (e: Exception) {
                    // Ignore errors
                }
            }.start()
        }
    }
}
