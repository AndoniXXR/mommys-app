package com.mommys.app.data.db.downloads

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity para items en la cola de descargas
 * Como ji.c en la app original
 */
@Entity(tableName = "Download")
data class DownloadItem(
    @PrimaryKey
    @ColumnInfo(name = "fileUrl")
    val fileUrl: String,
    
    @ColumnInfo(name = "added_date")
    val addedDate: Long,
    
    @ColumnInfo(name = "post_id")
    val postId: Int,
    
    @ColumnInfo(name = "file_ext")
    val fileExt: String? = null,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,
    
    @ColumnInfo(name = "artists")
    val artists: String? = null,
    
    @ColumnInfo(name = "md5")
    val md5: String? = null,
    
    @ColumnInfo(name = "thumbUrl")
    val thumbUrl: String? = null,
    
    @ColumnInfo(name = "tags")
    val tags: String? = null,
    
    @ColumnInfo(name = "characters")
    val characters: String? = null,
    
    @ColumnInfo(name = "score")
    val score: Int = 0,
    
    @ColumnInfo(name = "favs")
    val favs: Int = 0,
    
    @ColumnInfo(name = "apng")
    val apng: Int = 0,
    
    @ColumnInfo(name = "rating")
    val rating: String? = null,
    
    @ColumnInfo(name = "error")
    var error: String? = null,
    
    @ColumnInfo(name = "is_ai", defaultValue = "0")
    val isAi: Int = 0
) {
    companion object {
        /**
         * Transformar espacios a guiones bajos en tags y characters
         * como hace la app original
         */
        fun sanitizeTags(input: String?): String? {
            return input?.replace(" ", "_")
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadItem) return false
        return fileUrl == other.fileUrl
    }
    
    override fun hashCode(): Int = fileUrl.hashCode()
}
