package com.mommys.app.data.db.seen

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a viewed/seen post.
 * Stores the post ID for posts that have been viewed.
 */
@Entity(tableName = "seen")
data class SeenEntity(
    @PrimaryKey
    val id: Int  // Post ID
)
