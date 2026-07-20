package com.mommys.app.data.api

import android.util.Base64
import com.mommys.app.MommysApplication
import com.mommys.app.util.CloudflareBlocker
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente de red singleton
 * Configurado como wolfstash: timeout corto + IPv4 primero para evitar bloqueo IPv6
 */
object ApiClient {

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val RW_TIMEOUT_SECONDS = 30L

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
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(RW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(RW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .dns(HttpConfig.ipv4FirstDns)
            // CookieJar compartido: captura Set-Cookie de cada redirect de Cloudflare
            // (cf_chl_*, __cf_bm, cf_clearance) y las reenvía en el siguiente hop.
            // Esto replica lo que jsoup hace por defecto en la app original.
            .cookieJar(CloudflareCookieJar.get())
            // Seguir redirects (incluidos cross-protocol http<->https) como hace jsoup.
            // OkHttp por defecto ya sigue redirects, pero lo dejamos explícito y además
            // habilitamos los de SSL para no perder saltos en el challenge de Cloudflare.
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(createHeaderInterceptor())
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createCloudflareInterceptor())
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
            val requestBuilder = chain.request().newBuilder()
                .header("User-Agent", HttpConfig.userAgent())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")

            // Enviar Cookie header para pasar Cloudflare (como h7/d.java método q())
            val cookies = HttpConfig.cookies()
            if (cookies.isNotEmpty()) {
                requestBuilder.header("Cookie", cookies)
            }

            chain.proceed(requestBuilder.build())
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
     * Interceptor que detecta bloqueos de Cloudflare (HTTP 403/503) y notifica
     * a [CloudflareBlocker] para que se ofrezca al usuario resolver el captcha.
     * Como la app original qa2.java líneas 206-237.
     */
    private fun createCloudflareInterceptor(): Interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 403 || response.code == 503) {
            CloudflareBlocker.notifyBlocked()
        }
        response
    }

    /**
     * Interceptor para logging
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // En release no loguear headers (expondría Authorization: Basic ...).
            // En debug sí, para diagnosticar red.
            level = if (com.mommys.app.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }
    }
}
