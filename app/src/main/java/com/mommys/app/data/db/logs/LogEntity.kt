package com.mommys.app.data.db.logs

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a log entry for Following notifications
 */
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "$timestamp $message"
    }
}
