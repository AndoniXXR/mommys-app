package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Usuario - Completo según la API de e621
 * Basado en: https://e621.net/users/{id}.json
 */
data class User(
    @SerializedName("id") val id: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("name") val name: String,
    @SerializedName("level") val level: Int,
    @SerializedName("level_string") val levelString: String,
    @SerializedName("base_upload_limit") val baseUploadLimit: Int,
    @SerializedName("post_upload_count") val postUploadCount: Int,
    @SerializedName("post_update_count") val postUpdateCount: Int,
    @SerializedName("note_update_count") val noteUpdateCount: Int,
    @SerializedName("is_banned") val isBanned: Boolean,
    @SerializedName("can_approve_posts") val canApprovePosts: Boolean,
    @SerializedName("can_upload_free") val canUploadFree: Boolean,
    @SerializedName("avatar_id") val avatarId: Int?,
    
    // Campos adicionales del perfil (solo visibles para el propio usuario o con auth)
    @SerializedName("last_logged_in_at") val lastLoggedInAt: String?,
    @SerializedName("favorite_count") val favoriteCount: Int?,
    @SerializedName("favorite_tags") val favoriteTags: String?,
    @SerializedName("blacklisted_tags") val blacklistedTags: String?,
    @SerializedName("recent_tags") val recentTags: String?,
    @SerializedName("comment_count") val commentCount: Int?,
    @SerializedName("forum_post_count") val forumPostCount: Int?,
    @SerializedName("pool_version_count") val poolVersionCount: Int?,
    @SerializedName("artist_version_count") val artistVersionCount: Int?,
    @SerializedName("wiki_page_version_count") val wikiPageVersionCount: Int?,
    @SerializedName("flag_count") val flagCount: Int?,
    @SerializedName("has_mail") val hasMail: Boolean?
)
