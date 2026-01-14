package com.mommys.app.data.db.logs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Data Access Object for log entries
 */
@Dao
interface LogDao {
    
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): List<LogEntity>
    
    @Insert
    fun insert(log: LogEntity)
    
    @Query("DELETE FROM logs")
    fun clearAll()
}
