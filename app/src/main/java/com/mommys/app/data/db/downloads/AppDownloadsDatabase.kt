package com.mommys.app.data.db.downloads

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room para cola de descargas
 * Como AppDownloadsDatabase en la app original
 */
@Database(
    entities = [DownloadItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDownloadsDatabase : RoomDatabase() {
    
    abstract fun downloadDao(): DownloadDao
    
    companion object {
        private const val DATABASE_NAME = "mommys-downloads"
        
        @Volatile
        private var INSTANCE: AppDownloadsDatabase? = null
        
        fun getInstance(context: Context): AppDownloadsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDownloadsDatabase::class.java,
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
