package com.mommys.app.data.api

import com.mommys.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * API Service para e621/e926
 * Basado en la documentación oficial: https://e621.net/wiki_pages/2425
 */
interface ApiService {
    
    companion object {
        const val BASE_URL_E621 = "https://e621.net/"
        const val BASE_URL_E926 = "https://e926.net/"
    }
    
    // ==================== POSTS ====================
    
    /**
     * Obtener lista de posts
     */
    @GET("posts.json")
    suspend fun getPosts(
        @Query("tags") tags: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<PostsResponse>
    
    /**
     * Obtener un post específico
     */
    @GET("posts/{id}.json")
    suspend fun getPost(
        @Path("id") id: Int
    ): Response<SinglePostResponse>
    
    /**
     * Votar un post
     */
    @POST("posts/{id}/votes.json")
    suspend fun votePost(
        @Path("id") id: Int,
        @Query("score") score: Int,  // 1 = upvote, -1 = downvote, 0 = remove
        @Query("no_unvote") noUnvote: Boolean = false
    ): Response<VoteResponse>
    
    /**
     * Actualizar/editar un post (tags, rating)
     * Como la app original di/k.java: PATCH /posts/{id}.json
     * 
     * @param tagStringDiff Tags añadidos (sin prefijo) y eliminados (con prefijo "-")
     *                      Ejemplo: "tag1 tag2 -removed_tag"
     * @param rating Nueva rating (s, q, e)
     * @param oldRating Rating anterior (requerido si se cambia)
     * @param editReason Razón del cambio (opcional)
     */
    @FormUrlEncoded
    @PATCH("posts/{id}.json")
    suspend fun updatePost(
        @Path("id") postId: Int,
        @Field("post[tag_string_diff]") tagStringDiff: String? = null,
        @Field("post[rating]") rating: String? = null,
        @Field("post[old_rating]") oldRating: String? = null,
        @Field("post[edit_reason]") editReason: String? = null
    ): Response<SinglePostResponse>
    
    /**
     * Validar credenciales de login
     * Usa el mismo endpoint de votar con score=0 (neutral vote)
     * Si las credenciales son correctas devuelve 200 o 422 (ya votó)
     * Si son incorrectas devuelve 401 o 403
     */
    @POST("posts/321786/votes.json")
    suspend fun validateCredentials(
        @Query("score") score: Int = 0,
        @Query("login") login: String,
        @Query("api_key") apiKey: String
    ): Response<VoteResponse>
    
    /**
     * Agregar a favoritos
     * Como la app original ei/e0.java: POST /favorites.json con body JSON {post_id: id}
     */
    @POST("favorites.json")
    suspend fun addFavorite(
        @Body body: Map<String, Int>
    ): Response<FavoriteResponse>
    
    /**
     * Eliminar de favoritos
     * Como la app original: DELETE /favorites/{post_id}.json
     */
    @DELETE("favorites/{post_id}.json")
    suspend fun removeFavorite(
        @Path("post_id") postId: Int
    ): Response<Unit>
    
    // ==================== POOLS ====================
    
    /**
     * Obtener lista de pools
     */
    @GET("pools.json")
    suspend fun getPools(
        @Query("search[name_matches]") nameMatches: String? = null,
        @Query("search[order]") order: String = "updated_at",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<Pool>>
    
    /**
     * Obtener un pool específico
     */
    @GET("pools/{id}.json")
    suspend fun getPool(
        @Path("id") id: Int
    ): Response<Pool>
    
    // ==================== COMMENTS ====================
    
    /**
     * Obtener comentarios de un post
     */
    @GET("comments.json")
    suspend fun getComments(
        @Query("search[post_id]") postId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<Comment>>
    
    /**
     * Obtener un comentario específico por ID
     */
    @GET("comments/{id}.json")
    suspend fun getComment(
        @Path("id") id: Int
    ): Response<Comment>
    
    /**
     * Crear comentario
     */
    @FormUrlEncoded
    @POST("comments.json")
    suspend fun createComment(
        @Field("comment[post_id]") postId: Int,
        @Field("comment[body]") body: String,
        @Field("comment[do_not_bump_post]") doNotBump: Boolean = false
    ): Response<Comment>
    
    /**
     * Editar comentario
     * PATCH /comments/{id}.json
     */
    @FormUrlEncoded
    @PATCH("comments/{id}.json")
    suspend fun editComment(
        @Path("id") commentId: Int,
        @Field("comment[body]") body: String
    ): Response<Comment>
    
    /**
     * Eliminar comentario (hide)
     * DELETE /comments/{id}.json
     */
    @DELETE("comments/{id}.json")
    suspend fun deleteComment(
        @Path("id") commentId: Int
    ): Response<Unit>
    
    /**
     * Votar comentario
     * POST /comments/{id}/votes.json
     */
    @POST("comments/{id}/votes.json")
    suspend fun voteComment(
        @Path("id") commentId: Int,
        @Query("score") score: Int,  // 1 = upvote, -1 = downvote
        @Query("no_unvote") noUnvote: Boolean = false
    ): Response<VoteResponse>
    
    // ==================== USERS ====================
    
    /**
     * Obtener información de usuario por ID
     */
    @GET("users/{id}.json")
    suspend fun getUser(
        @Path("id") id: Int
    ): Response<User>
    
    /**
     * Obtener usuario por nombre exacto
     * Usado en ProfileActivity para cargar el perfil
     * Similar a la app original: /users/{username}.json
     */
    @GET("users/{username}.json")
    suspend fun getUserByUsername(
        @Path("username") username: String
    ): Response<User>
    
    /**
     * Buscar usuarios por nombre (parcial)
     */
    @GET("users.json")
    suspend fun getUserByName(
        @Query("search[name_matches]") name: String
    ): Response<List<User>>
    
    // ==================== TAGS ====================
    
    /**
     * Autocompletar tags
     */
    @GET("tags/autocomplete.json")
    suspend fun autocompleteTags(
        @Query("search[name_matches]") query: String,
        @Query("limit") limit: Int = 10
    ): Response<List<TagAutocomplete>>
    
    // ==================== POPULAR ====================
    
    /**
     * Posts populares
     * La respuesta tiene el formato {"posts": [...]} igual que getPosts
     */
    @GET("popular.json")
    suspend fun getPopular(
        @Query("date") date: String? = null,
        @Query("scale") scale: String = "day"  // day, week, month
    ): Response<PostsResponse>

    /**
     * Obtener un post aleatorio
     * Usa el endpoint dedicado /posts/random.json
     * NOTA: Este endpoint devuelve el post directamente, no envuelto en {"posts": [...]}
     */
    @GET("posts/random.json")
    suspend fun getRandomPost(
        @Query("tags") tags: String? = null
    ): Response<Post>
    
    // ==================== NOTES ====================
    
    /**
     * Obtener notas de un post
     * Como la app original vi/y.java: /notes.json?search[post_id]=
     */
    @GET("notes.json")
    suspend fun getNotes(
        @Query("search[post_id]") postId: Int
    ): Response<List<Note>>
    
    // ==================== FAVORITES LIST ====================
    
    /**
     * Obtener lista de favoritos del usuario actual
     * Este endpoint devuelve los favoritos ordenados por fecha de favorito (más reciente primero)
     * A diferencia de usar "fav:username" en posts.json que ordena por ID del post.
     * Como la app original vi/a0.java: GET /favorites.json
     * 
     * NOTA: Según la documentación de e621, el parámetro user_id es necesario
     * para especificar de qué usuario obtener los favoritos.
     * Si el usuario tiene favoritos ocultos, debes ser ese usuario o Moderator+.
     */
    @GET("favorites.json")
    suspend fun getFavorites(
        @Query("user_id") userId: Int? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<PostsResponse>
    
    // ==================== WIKI ====================
    
    /**
     * Obtener una página wiki por su título
     * Como la app original vi/e0.java: GET /wiki_pages/{title}.json
     */
    @GET("wiki_pages/{title}.json")
    suspend fun getWikiPage(
        @Path("title") title: String
    ): Response<WikiPage>
    
    /**
     * Buscar páginas wiki
     */
    @GET("wiki_pages.json")
    suspend fun searchWikiPages(
        @Query("search[title]") title: String? = null,
        @Query("search[body_matches]") bodyMatches: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<WikiPage>>
    
    // ==================== POST SETS ====================
    
    /**
     * Buscar sets de posts
     * Como la app original: GET /post_sets.json
     */
    @GET("post_sets.json")
    suspend fun getPostSets(
        @Query("search[name]") name: String? = null,
        @Query("search[shortname]") shortname: String? = null,
        @Query("search[creator_name]") creatorName: String? = null,
        @Query("search[order]") order: String = "updated_at",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<PostSet>>
    
    /**
     * Obtener un set específico
     */
    @GET("post_sets/{id}.json")
    suspend fun getPostSet(
        @Path("id") id: Int
    ): Response<PostSet>
    
    /**
     * Añadir post a un set
     */
    @FormUrlEncoded
    @POST("post_sets/{id}/add_posts.json")
    suspend fun addPostToSet(
        @Path("id") setId: Int,
        @Field("post_ids[]") postIds: List<Int>
    ): Response<Unit>
    
    /**
     * Quitar post de un set
     */
    @FormUrlEncoded
    @POST("post_sets/{id}/remove_posts.json")
    suspend fun removePostFromSet(
        @Path("id") setId: Int,
        @Field("post_ids[]") postIds: List<Int>
    ): Response<Unit>
}

// Response classes adicionales
data class VoteResponse(
    val score: Int,
    val up: Int,
    val down: Int,
    val our_score: Int
)

data class FavoriteResponse(
    val post_id: Int
)

data class TagAutocomplete(
    val id: Int,
    val name: String,
    val post_count: Int,
    val category: Int,
    val antecedent_name: String?
)
