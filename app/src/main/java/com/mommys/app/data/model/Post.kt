package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo principal de Post basado en la API de e621/e926
 */
data class Post(
    @SerializedName("id") val id: Int,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("file") val file: FileInfo,
    @SerializedName("preview") val preview: PreviewInfo,
    @SerializedName("sample") val sample: SampleInfo = SampleInfo(false, null, null, null),
    @SerializedName("score") val score: Score,
    @SerializedName("tags") val tags: Tags,
    @SerializedName("locked_tags") val lockedTags: List<String> = emptyList(),
    @SerializedName("change_seq") val changeSeq: Long?,
    @SerializedName("flags") val flags: Flags,
    @SerializedName("rating") val rating: String,
    @SerializedName("fav_count") val favCount: Int,
    @SerializedName("sources") val sources: List<String> = emptyList(),
    @SerializedName("pools") val pools: List<Int> = emptyList(),
    @SerializedName("relationships") val relationships: Relationships = Relationships(null, false, false, emptyList()),
    @SerializedName("approver_id") val approverId: Int?,
    @SerializedName("uploader_id") val uploaderId: Int?,
    @SerializedName("description") val description: String?,
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("is_favorited") val isFavorited: Boolean = false,
    @SerializedName("has_notes") val hasNotes: Boolean = false
) {
    /**
     * Campo mutable para marcar si el post ha sido visto.
     * Similar a f18022c en la app original (ii/m.java).
     * Se marca como true cuando el usuario ve el post en PostActivity.
     * NO se serializa/deserializa con Gson.
     */
    @Transient
    var isSeen: Boolean = false
}

data class FileInfo(
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("ext") val ext: String,
    @SerializedName("size") val size: Long,
    @SerializedName("md5") val md5: String?,
    @SerializedName("url") val url: String?
)

data class PreviewInfo(
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("url") val url: String?
)

data class SampleInfo(
    @SerializedName("has") val has: Boolean,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("url") val url: String?,
    @SerializedName("alternates") val alternates: VideoAlternates? = null
)

/**
 * Variantes de un post de video (mapea la estructura real de la API e621/e926).
 * Estructura: sample.alternates = { has, original, variants{mp4}, samples{480p,720p} }
 */
data class VideoAlternates(
    @SerializedName("has") val has: Boolean = false,
    @SerializedName("original") val original: VideoVariant? = null,
    @SerializedName("variants") val variants: Map<String, VideoVariant>? = null,
    @SerializedName("samples") val samples: Map<String, VideoVariant>? = null
)

/**
 * Una variante concreta de un video (original / variants["mp4"] / samples["480p"|"720p"]).
 * Campos reales de la API: codec, fps, size, width, height, url.
 */
data class VideoVariant(
    @SerializedName("type") val type: String? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("fps") val fps: Double? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("url") val url: String? = null
)

data class Score(
    @SerializedName("up") val up: Int,
    @SerializedName("down") val down: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("our_score") val ourScore: Int = 0  // Nuestro voto: 1 = upvote, -1 = downvote, 0 = sin voto
)

data class Tags(
    @SerializedName("general") val general: List<String> = emptyList(),
    @SerializedName("species") val species: List<String> = emptyList(),
    @SerializedName("character") val character: List<String> = emptyList(),
    @SerializedName("artist") val artist: List<String> = emptyList(),
    @SerializedName("copyright") val copyright: List<String> = emptyList(),
    @SerializedName("meta") val meta: List<String> = emptyList(),
    @SerializedName("lore") val lore: List<String> = emptyList(),
    @SerializedName("invalid") val invalid: List<String> = emptyList()
) {
    fun getAllTags(): List<Pair<String, TagCategory>> {
        val allTags = mutableListOf<Pair<String, TagCategory>>()
        artist.forEach { allTags.add(it to TagCategory.ARTIST) }
        copyright.forEach { allTags.add(it to TagCategory.COPYRIGHT) }
        character.forEach { allTags.add(it to TagCategory.CHARACTER) }
        species.forEach { allTags.add(it to TagCategory.SPECIES) }
        general.forEach { allTags.add(it to TagCategory.GENERAL) }
        meta.forEach { allTags.add(it to TagCategory.META) }
        lore.forEach { allTags.add(it to TagCategory.LORE) }
        invalid.forEach { allTags.add(it to TagCategory.INVALID) }
        return allTags
    }
}

enum class TagCategory {
    GENERAL, ARTIST, COPYRIGHT, CHARACTER, SPECIES, META, LORE, INVALID
}

data class Flags(
    @SerializedName("pending") val pending: Boolean,
    @SerializedName("flagged") val flagged: Boolean,
    @SerializedName("note_locked") val noteLocked: Boolean,
    @SerializedName("status_locked") val statusLocked: Boolean,
    @SerializedName("rating_locked") val ratingLocked: Boolean,
    @SerializedName("deleted") val deleted: Boolean
)

data class Relationships(
    @SerializedName("parent_id") val parentId: Int?,
    @SerializedName("has_children") val hasChildren: Boolean = false,
    @SerializedName("has_active_children") val hasActiveChildren: Boolean = false,
    @SerializedName("children") val children: List<Int> = emptyList()
)

/**
 * Response wrapper para la lista de posts
 */
data class PostsResponse(
    @SerializedName("posts") val posts: List<Post>
)

/**
 * Response wrapper para un solo post
 */
data class SinglePostResponse(
    @SerializedName("post") val post: Post
)

/**
 * Obtiene la URL del video según las preferencias de calidad y formato.
 * Mapea la estructura real de la API: sample.alternates.{original, variants["mp4"], samples["480p"|"720p"]}.
 *
 * @param quality 0=Original, 1=720p (default), 2=480p
 * @param format 0=WebM (default), 1=MP4
 * @return URL del video o file.url si no hay variantes disponibles
 */
fun Post.getVideoUrl(quality: Int, format: Int): String? {
    val alts = sample?.alternates ?: return file.url
    val preferMp4 = format == 1

    // URL del "original" respetando el formato preferido.
    // Las variants SIEMPRE son mp4/H.264 (existen si el original NO es H.264).
    fun originalPreferred(): String? {
        val origUrl = alts.original?.url
        val origIsH264 = alts.original?.codec?.startsWith("avc") == true  // H.264: siempre reproducible
        val mp4Variant = alts.variants?.get("mp4")?.url                   // H.264 transcódito del original
        return when {
            preferMp4 && origIsH264 -> origUrl                  // original ya es H.264 mp4
            preferMp4 && mp4Variant != null -> mp4Variant       // variants.mp4 (H.264); cubre AV1/VP9/VP8
            !preferMp4 && origUrl?.endsWith(".webm") == true -> origUrl  // webm
            origUrl != null -> origUrl                          // lo que haya
            else -> mp4Variant
        }
    }

    // 480p/720p viven en samples (siempre H.264 mp4); el formato no aplica ahí.
    val url = when (quality) {
        0 -> originalPreferred()                              // Original
        1 -> alts.samples?.get("720p")?.url                  // 720p
            ?: originalPreferred()
            ?: alts.samples?.get("480p")?.url
        2 -> alts.samples?.get("480p")?.url                  // 480p
            ?: alts.samples?.get("720p")?.url
            ?: originalPreferred()
        else -> alts.samples?.get("720p")?.url ?: originalPreferred()
    }

    return url ?: file.url
}

/**
 * Verifica si el post es un video
 */
fun Post.isVideo(): Boolean {
    return file.ext in listOf("webm", "mp4", "avi", "mov", "mkv")
}

/**
 * Verifica si el post es una imagen estática (PNG, JPG) o GIF
 * Solo estos tipos soportan notas en la app original
 * Basado en f7.i.d() que retorna 0 para imágenes, 1 para GIFs
 */
fun Post.supportsNotes(): Boolean {
    return file.ext in listOf("png", "jpg", "jpeg", "gif")
}
