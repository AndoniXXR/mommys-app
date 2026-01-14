package com.mommys.app.data.db.downloads

import androidx.room.*

/**
 * DAO para operaciones con Download queue
 * Como ji.d en la app original
 */
@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM Download ORDER BY added_date ASC")
    suspend fun getAllDownloads(): List<DownloadItem>
    
    @Query("SELECT * FROM Download WHERE error IS NULL ORDER BY added_date ASC LIMIT 1")
    suspend fun getNextPendingDownload(): DownloadItem?
    
    @Query("SELECT COUNT(fileUrl) FROM Download")
    suspend fun getCount(): Int
    
    @Query("SELECT * FROM Download WHERE fileUrl = :fileUrl LIMIT 1")
    suspend fun getByFileUrl(fileUrl: String): DownloadItem?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: DownloadItem): Long
    
    @Update
    suspend fun update(item: DownloadItem)
    
    @Delete
    suspend fun delete(item: DownloadItem)
    
    @Query("DELETE FROM Download WHERE fileUrl = :fileUrl")
    suspend fun deleteByFileUrl(fileUrl: String)
    
    @Query("DELETE FROM Download")
    suspend fun deleteAll()
    
    @Query("UPDATE Download SET error = NULL WHERE fileUrl = :fileUrl")
    suspend fun clearError(fileUrl: String)
}
