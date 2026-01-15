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
 * Alternativas de video en diferentes calidades
 * Estructura: sample.alternates.{original|720p|480p}.urls[]
 */
data class VideoAlternates(
    @SerializedName("original") val original: VideoFormat? = null,
    @SerializedName("720p") val quality720p: VideoFormat? = null,
    @SerializedName("480p") val quality480p: VideoFormat? = null
)

/**
 * Formato de video con URLs de webm y mp4
 */
data class VideoFormat(
    @SerializedName("type") val type: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("urls") val urls: List<String>? = null
) {
    /** Obtiene URL de webm */
    val webmUrl: String?
        get() = urls?.find { it.endsWith(".webm") }
    
    /** Obtiene URL de mp4 */
    val mp4Url: String?
        get() = urls?.find { it.endsWith(".mp4") }
    
    /** Verifica si es video */
    val isVideo: Boolean
        get() = type == "video"
}

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
 * Lógica basada en la app original (ei/s.java líneas 85-120):
 * 
 * @param quality 0=Original, 1=720p (default), 2=480p
 * @param format 0=WebM (default), 1=MP4
 * @return URL del video o null si no hay video disponible
 */
fun Post.getVideoUrl(quality: Int, format: Int): String? {
    val alternates = sample.alternates ?: return file.url
    
    // Mapear quality: 0=original, 1=720p, 2=480p
    // Mapear format: 0=webm, 1=mp4
    val preferWebm = format == 0
    
    // Función para obtener URL de un VideoFormat según preferencia
    fun VideoFormat?.getUrl(): String? {
        if (this == null || !isVideo) return null
        return if (preferWebm) {
            webmUrl ?: mp4Url  // Si no hay webm, intentar mp4
        } else {
            mp4Url ?: webmUrl  // Si no hay mp4, intentar webm
        }
    }
    
    // Intentar obtener según calidad preferida, con fallback
    val url = when (quality) {
        0 -> { // Original
            alternates.original.getUrl()
                ?: alternates.quality720p.getUrl()
                ?: alternates.quality480p.getUrl()
        }
        1 -> { // 720p (default)
            alternates.quality720p.getUrl()
                ?: alternates.original.getUrl()
                ?: alternates.quality480p.getUrl()
        }
        2 -> { // 480p
            alternates.quality480p.getUrl()
                ?: alternates.quality720p.getUrl()
                ?: alternates.original.getUrl()
        }
        else -> alternates.quality720p.getUrl()
    }
    
    // Si no hay alternates, usar URL original del archivo
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
