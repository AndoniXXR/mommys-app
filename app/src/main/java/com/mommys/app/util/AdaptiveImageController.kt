package com.mommys.app.util

import android.util.Log
import com.mommys.app.CustomGlideModule
import com.mommys.app.MommysApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Adaptive loading: ajusta dinámicamente la concurrencia de red de la carga de
 * imágenes (Glide) según el tipo de red, igual que hacía la app original con
 * PicassoExecutorService.adjustThreadCount() (1 hilo en 2G → 4 en WiFi).
 *
 * En red móvil lenta, limitar las descargas simultáneas evita ahogar la red
 * (que es la causa principal de que el grid tarde tanto en cargar con datos).
 *
 * Observa [com.mommys.app.util.network.NetworkMonitor.networkState] y aplica
 * `recommendedThreadCount` al `Dispatcher` del OkHttp de Glide.
 */
object AdaptiveImageController {

    private const val TAG = "AdaptiveImage"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var initialized = false

    /**
     * Arranca el controlador. Llamar desde [MommysApplication.onCreate].
     * Aplica la concurrencia del estado actual y se mantiene reactivo a cambios de red.
     */
    fun init(app: MommysApplication) {
        if (initialized) return
        initialized = true

        applyConcurrency(app.networkMonitor.getCurrentState().recommendedThreadCount)

        scope.launch {
            app.networkMonitor.networkState.collect { state ->
                applyConcurrency(state.recommendedThreadCount)
            }
        }
    }

    /**
     * Llamar cuando Glide cree su OkHttpClient ([CustomGlideModule.registerComponents]).
     * El cliente se crea de forma perezosa, así que aquí aplicamos la concurrencia
     * actual en cuanto está disponible.
     */
    fun onClientReady() {
        applyConcurrency(MommysApplication.getInstance().networkMonitor.getCurrentState().recommendedThreadCount)
    }

    private fun applyConcurrency(maxPerHost: Int) {
        val client = CustomGlideModule.glideClient ?: return
        try {
            // Limitar descargas simultáneas al host de imágenes según la red:
            // 2G=1, 3G=2, 4G=3, WiFi=4 (de NetworkState.recommendedThreadCount).
            client.dispatcher.maxRequestsPerHost = maxPerHost
            client.dispatcher.maxRequests = maxOf(maxPerHost * 2, 6)
            Log.d(TAG, "concurrency set: maxRequestsPerHost=$maxPerHost, maxRequests=${client.dispatcher.maxRequests}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust concurrency", e)
        }
    }
}
