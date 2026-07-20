package com.mommys.app.util.network

/**
 * Eventos de TRANSICIÓN REAL de red, emitidos por [NetworkMonitor].
 *
 * A diferencia de [NetworkState] (que es el estado actual y se reemite a nuevos collectors),
 * estos eventos representan cambios confirmados offline<->online y se entregan via un
 * SharedFlow con replay = 0. Esto significa que:
 *   - Cuando la Activity vuelve de segundo plano, NO recibe un valor antiguo.
 *   - Solo recibe eventos producidos DESPUÉS de suscribirse.
 *
 * Esto es lo que evita el falso "conexión restablecida" al volver a la app:
 * el auto-refresh (y el banner) reaccionan solo a transiciones reales, no a la
 * reemisión del último estado conocido.
 *
 * Diseno basado en el análisis comparativo con la app original Wolf's Stash
 * (que no muestra feedback de reconexión) + corrección del bug de StateFlow.
 */
sealed class NetworkEvent {
    /**
     * La app pasó de tener conexión a NO tenerla (pérdida real verificada).
     */
    object BecameOffline : NetworkEvent()

    /**
     * La app pasó de NO tener conexión a tenerla (recuperación real verificada).
     * Es el momento apropiado para reintentar cargas fallidas (auto-refresh silencioso).
     */
    object BecameOnline : NetworkEvent()
}
