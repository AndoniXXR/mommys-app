package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Comentario
 */
data class Comment(
    @SerializedName("id") val id: Int,
    @SerializedName("post_id") val postId: Int,
    @SerializedName("creator_id") val creatorId: Int,
    @SerializedName("body") val body: String,
    @SerializedName("score") val score: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("updater_id") val updaterId: Int?,
    @SerializedName("do_not_bump_post") val doNotBumpPost: Boolean,
    @SerializedName("is_hidden") val isHidden: Boolean,
    @SerializedName("is_sticky") val isSticky: Boolean,
    @SerializedName("warning_type") val warningType: String?,
    @SerializedName("warning_user_id") val warningUserId: Int?,
    @SerializedName("creator_name") val creatorName: String?
)

data class CommentsResponse(
    val comments: List<Comment>
)
