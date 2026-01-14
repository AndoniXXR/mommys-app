package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Pool (colección de posts)
 */
data class Pool(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("creator_id") val creatorId: Int,
    @SerializedName("description") val description: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("category") val category: String,
    @SerializedName("post_ids") val postIds: List<Int>,
    @SerializedName("creator_name") val creatorName: String?,
    @SerializedName("post_count") val postCount: Int
)

data class PoolsResponse(
    val pools: List<Pool>
)
