package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo para Sets de posts de e621
 * Basado en la API de e621: /post_sets.json
 */
data class PostSet(
    val id: Int,
    val name: String,
    val shortname: String,
    val description: String?,
    
    @SerializedName("is_public")
    val isPublic: Boolean,
    
    @SerializedName("transfer_on_delete")
    val transferOnDelete: Boolean,
    
    @SerializedName("creator_id")
    val creatorId: Int,
    
    @SerializedName("post_ids")
    val postIds: List<Int>,
    
    @SerializedName("post_count")
    val postCount: Int,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?
)
