package com.mommys.app.data.api

import android.util.Base64
import com.mommys.app.MommysApplication
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente de red singleton
 */
object ApiClient {
    
    private const val TIMEOUT_SECONDS = 30L
    // User-Agent similar a la app original
    private const val USER_AGENT = "Mommys/1.0 (Android app)"
    
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ApiService.BASE_URL_E926
    
    /**
     * Instancia del servicio de API (propiedad para acceso directo)
     */
    val apiService: ApiService
        get() = getApiService()
    
    /**
     * Obtiene la instancia de Retrofit
     */
    fun getClient(baseUrl: String = currentBaseUrl): Retrofit {
        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            retrofit = createRetrofit(baseUrl)
        }
        return retrofit!!
    }
    
    /**
     * Obtiene el servicio de API
     */
    fun getApiService(baseUrl: String = currentBaseUrl): ApiService {
        return getClient(baseUrl).create(ApiService::class.java)
    }
    
    /**
     * Cambia entre e621 y e926
     */
    fun setUseE621(useE621: Boolean) {
        val newUrl = if (useE621) ApiService.BASE_URL_E621 else ApiService.BASE_URL_E926
        if (currentBaseUrl != newUrl) {
            currentBaseUrl = newUrl
            retrofit = null // Force recreation
        }
    }
    
    private fun createRetrofit(baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(createHeaderInterceptor())
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Interceptor para headers comunes
     * Como la app original gi/l.java líneas 68-77
     */
    private fun createHeaderInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
    }
    
    /**
     * Interceptor para autenticación
     * La app original usa Basic Auth con header Authorization
     * Como h7/d.java método r(): "Basic " + Base64(username:apikey)
     */
    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val prefs = MommysApplication.getInstance().preferencesManager
            val username = prefs.getUsername()
            val apiKey = prefs.getApiKey()
            
            val requestBuilder = chain.request().newBuilder()
            
            if (username != null && apiKey != null && username.isNotEmpty() && apiKey.isNotEmpty()) {
                // Verificar que username no contenga ":" (como la app original)
                if (!username.contains(":")) {
                    // Basic Auth: Base64(username:apikey)
                    val credentials = "$username:$apiKey"
                    val basicAuth = "Basic " + Base64.encodeToString(
                        credentials.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    requestBuilder.header("Authorization", basicAuth)
                }
                
                // También agregar como query params (algunos endpoints lo requieren)
                val url = chain.request().url.newBuilder()
                    .addQueryParameter("login", username)
                    .addQueryParameter("api_key", apiKey)
                    .build()
                requestBuilder.url(url)
            }
            
            // Para peticiones POST de votos/favoritos, agregar X-Requested-With
            // Como la app original gi/l.java línea 75
            if (chain.request().method == "POST" || chain.request().method == "DELETE") {
                requestBuilder.header("X-Requested-With", "XMLHttpRequest")
            }
            
            chain.proceed(requestBuilder.build())
        }
    }
    
    /**
     * Interceptor para logging (debug)
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
}
