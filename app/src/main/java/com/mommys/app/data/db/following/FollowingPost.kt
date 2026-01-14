package com.mommys.app.data.db.following

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity para posts encontrados por el sistema de Follow Tags
 * Como en la app original: tabla FollowingPost
 */
@Entity(tableName = "FollowingPost")
data class FollowingPost(
    @PrimaryKey
    @ColumnInfo(name = "postID")
    val postId: Int,
    
    @ColumnInfo(name = "json")
    val json: String,
    
    @ColumnInfo(name = "added_date")
    val addedDate: Long,
    
    @ColumnInfo(name = "query_string")
    val queryString: String? = null,
    
    @ColumnInfo(name = "is_ai", defaultValue = "0")
    val isAi: Int = 0
)
