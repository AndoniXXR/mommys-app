package com.mommys.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus que avisa cuando una llamada API es bloqueada por Cloudflare
 * (HTTP 403/503). Lo emite el interceptor de detección en
 * [com.mommys.app.data.api.ApiClient] y lo observan las activities principales
 * para mostrar el diálogo de "resolver captcha ahora".
 *
 * El throttle evita inundar al usuario de diálogos cuando muchas peticiones
 * fallan a la vez (ej: el grid dispara decenas de llamadas).
 */
object CloudflareBlocker {

    private const val THROTTLE_MS = 10_000L

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    @Volatile
    private var lastEmitMs = 0L

    /** Llamar desde el interceptor cuando se detecte un 403/503 de Cloudflare. */
    fun notifyBlocked() {
        val now = System.currentTimeMillis()
        if (now - lastEmitMs > THROTTLE_MS) {
            lastEmitMs = now
            _events.tryEmit(Unit)
        }
    }
}
