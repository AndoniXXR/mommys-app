package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo para páginas Wiki de e621
 * Basado en la API de e621: /wiki_pages/{title}.json
 */
data class WikiPage(
    val id: Int,
    val title: String,
    val body: String,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("creator_id")
    val creatorId: Int?,
    
    @SerializedName("creator_name")
    val creatorName: String?,
    
    @SerializedName("updater_id")
    val updaterId: Int?,
    
    @SerializedName("other_names")
    val otherNames: List<String>?,
    
    @SerializedName("is_locked")
    val isLocked: Boolean?,
    
    @SerializedName("is_deleted")
    val isDeleted: Boolean?,
    
    @SerializedName("category_name")
    val categoryName: Int?
)
