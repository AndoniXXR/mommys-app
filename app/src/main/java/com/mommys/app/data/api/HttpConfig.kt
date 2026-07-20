package com.mommys.app.data.api

import com.mommys.app.MommysApplication
import okhttp3.Dns
import okhttp3.Interceptor
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Fuente única de verdad para el User-Agent y las cookies de Cloudflare.
 *
 * Centraliza el UA canónico y las cookies almacenadas en preferencias, de modo
 * que TODOS los clientes HTTP (API, Glide, ExoPlayer, suggestions) usen
 * exactamente el mismo UA y las mismas cookies.
 *
 * Esto es CRÍTICO para el bypass de Cloudflare: la cookie cf_clearance queda
 * ligada al User-Agent con el que se resolvió el challenge, así que el cliente
 * que resuelve el captcha (WebView) y los que consumen la API DEBEN coincidir.
 *
 * NOTA SOBRE EL UA: antes se derivaba de BuildConfig.VERSION_NAME, lo que hacía
 * que cada recompilación cambiara el UA e invalidara instantáneamente cualquier
 * cf_clearance guardada (Cloudflare liga cf_clearance a dominio+UA+IP). Ahora el
 * UA es una constante estable entre versiones, igual que en la app original
 * Wolf's Stash ("The Wolf's Stash v2 beta-4.16.4 (by ZepiWolf)" era fijo).
 */
object HttpConfig {

    /**
     * User-Agent canónico compartido por toda la app.
     * ESTABLE entre versiones para no invalidar cf_clearance entre recompilaciones.
     */
    fun userAgent(): String = STABLE_USER_AGENT

    private const val STABLE_USER_AGENT = "Mommys/1.x (by AndoniXXR)"

    /** Cookies guardadas tras resolver el challenge de Cloudflare (string "k=v; k=v"). */
    fun cookies(): String =
        MommysApplication.getInstance().preferencesManager.getCookies()

    /**
     * Interceptor OkHttp que inyecta el User-Agent y la cookie de Cloudflare en
     * cada petición. Lee las cookies de preferencias en CADA petición, así que
     * cuando el usuario resuelve un nuevo challenge, las cookies nuevas se usan
     * automáticamente sin reconstruir el cliente.
     */
    fun cloudflareHeaderInterceptor(): Interceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .header("User-Agent", userAgent())
        val cookieHeader = cookies()
        if (cookieHeader.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieHeader)
        }
        chain.proceed(requestBuilder.build())
    }

    /**
     * Variante de [cloudflareHeaderInterceptor] para clientes basados en
     * HttpURLConnection (Browse, Following). Inyecta el UA canónico y la cookie
     * de Cloudflare, igual que el interceptor hace en OkHttp.
     */
    fun applyCloudflareHeaders(connection: java.net.HttpURLConnection) {
        connection.setRequestProperty("User-Agent", userAgent())
        val cookieHeader = cookies()
        if (cookieHeader.isNotEmpty()) {
            connection.setRequestProperty("Cookie", cookieHeader)
        }
    }

    /**
     * DNS que prioriza IPv4 sobre IPv6. OkHttp 4.x no tiene Happy Eyeballs,
     * así que forzamos IPv4 primero para evitar el timeout de 30s cuando IPv6
     * está roto en el WiFi del usuario. Compartido por todos los clientes.
     */
    val ipv4FirstDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return Dns.SYSTEM.lookup(hostname)
                .sortedBy { if (it is Inet4Address) 0 else 1 }
        }
    }
}
