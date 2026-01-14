package com.mommys.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val postId: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "seen_posts")
data class SeenPostEntity(
    @PrimaryKey
    val postId: Int,
    val seenAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val postId: Int,
    val filePath: String,
    val thumbnailPath: String?,
    val fileType: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_queries")
data class SavedQueryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val query: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "following_tags")
data class FollowingTagEntity(
    @PrimaryKey
    val tagName: String,
    val lastCheckedAt: Long = 0,
    val newPostsCount: Int = 0
)

@Entity(tableName = "blacklist_tags")
data class BlacklistTagEntity(
    @PrimaryKey
    val tagName: String
)

/**
 * Entidad para historial de búsquedas recientes
 * Similar a la app original que guarda búsquedas con sus tags y metadatos
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val query: String,                          // Query completo (todos los tags)
    val displayText: String,                    // Texto para mostrar en sugerencias
    val searchedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 1                       // Contador de uso para ordenar por popularidad
)

/**
 * Entidad para búsquedas guardadas (favoritas)
 * Similar a ii.o de la app original
 */
@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,                           // Nombre de la búsqueda guardada
    val query: String,                          // Query con tags
    val page: Int = 1,                          // Página inicial
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0
)
