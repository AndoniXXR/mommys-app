package com.mommys.app.data.api

import com.mommys.app.MommysApplication
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * CookieJar compartido para TODOS los clientes OkHttp (API, Glide, ExoPlayer).
 *
 * PROBLEMA QUE RESUELVE:
 * OkHttp por defecto no persiste cookies entre redirects. Cuando Cloudflare desafía,
 * responde con una cadena de HTTP 30x redirects en los que va seteando cookies
 * intermedias (cf_chl_*, __cf_bm, ...) mediante cabeceras Set-Cookie. El cliente
 * DEBE conservar todas esas cookies y reenviarlas en cada hop para llegar al
 * cf_clearance final. Sin CookieJar, OkHttp pierde las intermedias y el bypass falla.
 *
 * La app original Wolf's Stash usa jsoup, que internamente mantiene un
 * java.net.CookieManager que SÍ preserva cookies Set-Cookie entre redirects.
 * Este CookieJar replica ese comportamiento usando OkHttp puro, sin reescribir
 * la capa de red.
 *
 * FUNCIONAMIENTO:
 * - loadForRequest(url): devuelve todas las cookies aplicables a esa URL
 *   (las estáticas guardadas desde el WebView + las dinámicas capturadas en redirects).
 * - saveFromResponse(url, cookies): captura cualquier Set-Cookie que llegue en la
 *   respuesta (incluidas las de hops intermedios de CF) y las guarda en memoria.
 *   Si la cookie es cf_clearance, también la persiste a preferencias para que
 *   sobreviva a reinicios y la usen los HttpURLConnection que no pasan por aquí.
 */
class CloudflareCookieJar : CookieJar {

    companion object {
        @Volatile
        private var instance: CloudflareCookieJar? = null

        fun get(): CloudflareCookieJar =
            instance ?: synchronized(this) {
                instance ?: CloudflareCookieJar().also { instance = it }
            }
    }

    // Cookies dinámicas capturadas durante redirects/respuestas.
    // Key = dominio de la cookie (sin leading dot), Value = mapa nombre->cookie.
    private val store: MutableMap<String, MutableMap<String, Cookie>> = ConcurrentHashMap()

    init {
        // Precargar cualquier cookie guardada previamente desde el WebView
        loadPersistedCookies()
    }

    /**
     * Devuelve las cookies aplicables a la URL dada para enviar en la petición.
     * Incluye cookies que coincidan por dominio exacto o por sufijo de dominio.
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val result = mutableListOf<Cookie>()
        val host = url.host

        // 1) Cookies dinámicas capturadas en redirects previos
        for ((domain, cookies) in store) {
            if (matchesDomain(host, domain)) {
                val now = System.currentTimeMillis()
                for ((_, cookie) in cookies) {
                    if (cookie.expiresAt >= now && cookie.matches(url)) {
                        result.add(cookie)
                    }
                }
            }
        }

        // 2) Cookies estáticas guardadas por el WebView (formato "k=v; k=v")
        //    Se inyectan como fallback para asegurar que cf_clearance persistida
        //    sobreviva incluso si la cache dinámica se vació.
        val persisted = try {
            MommysApplication.getInstance().preferencesManager.getCookies()
        } catch (e: Exception) {
            ""
        }
        if (persisted.isNotEmpty()) {
            for (pair in persisted.split(";")) {
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val name = pair.substring(0, eq).trim()
                    val value = pair.substring(eq + 1).trim()
                    if (name.isNotEmpty()) {
                        try {
                            result.add(
                                Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(host)
                                    .build()
                            )
                        } catch (e: IllegalArgumentException) {
                            // Cookie inválida (valor con chars no permitidos), ignorar
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Captura las cookies Set-Cookie de una respuesta.
     * Crítico: esto se llama EN CADA redirect, así que capturamos todas las
     * intermedias de Cloudflare, no solo la final.
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            val domain = cookie.domain
            val bucket = store.getOrPut(domain) { ConcurrentHashMap() }
            // Descartar expiradas al guardar
            if (cookie.expiresAt < now) {
                bucket.remove(cookie.name)
            } else {
                bucket[cookie.name] = cookie
            }

            // Persistir cf_clearance a preferencias (la usa para sobrevivir reinicios
            // y para HttpURLConnection que no pasan por este CookieJar).
            if (cookie.name == "cf_clearance") {
                persistAllCookiesForHost(url.host)
            }
        }
    }

    /**
     * Vacía la cache dinámica y recarga desde preferencias.
     * Llamar tras resolver el captcha en el WebView para que la cookie nueva
     * esté disponible inmediatamente.
     */
    fun reload() {
        store.clear()
        loadPersistedCookies()
    }

    /**
     * Vacía todo (cache + preferencias).
     */
    fun clearAll() {
        store.clear()
        try {
            MommysApplication.getInstance().preferencesManager.clearCookies()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun loadPersistedCookies() {
        val persisted = try {
            MommysApplication.getInstance().preferencesManager.getCookies()
        } catch (e: Exception) {
            return
        }
        if (persisted.isEmpty()) return

        // Intentar asignar las cookies persistidas a ambos hosts posibles
        for (host in listOf("e621.net", "e926.net")) {
            for (pair in persisted.split(";")) {
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val name = pair.substring(0, eq).trim()
                    val value = pair.substring(eq + 1).trim()
                    if (name.isEmpty()) continue
                    try {
                        val cookie = Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(host)
                            .build()
                        store.getOrPut(host) { ConcurrentHashMap() }[name] = cookie
                    } catch (e: IllegalArgumentException) {
                        // ignorar cookie inválida
                    }
                }
            }
        }
    }

    private fun persistAllCookiesForHost(host: String) {
        // Reconstruir el string "k=v; k=v" con las cookies del host y guardarlo
        val sb = StringBuilder()
        for ((domain, cookies) in store) {
            if (!matchesDomain(host, domain)) continue
            for ((name, cookie) in cookies) {
                if (sb.isNotEmpty()) sb.append("; ")
                sb.append(name).append('=').append(cookie.value)
            }
        }
        try {
            MommysApplication.getInstance().preferencesManager.setCookies(sb.toString())
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun matchesDomain(host: String, cookieDomain: String): Boolean {
        if (host == cookieDomain) return true
        if (host.endsWith(".$cookieDomain")) return true
        // cookieDomain puede venir con o sin leading dot
        val clean = cookieDomain.removePrefix(".")
        return host == clean || host.endsWith(".$clean")
    }
}
