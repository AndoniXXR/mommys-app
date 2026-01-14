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
     * Crear comentario
     */
    @FormUrlEncoded
    @POST("comments.json")
    suspend fun createComment(
        @Field("comment[post_id]") postId: Int,
        @Field("comment[body]") body: String,
        @Field("comment[do_not_bump_post]") doNotBump: Boolean = false
    ): Response<Comment>
    
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
