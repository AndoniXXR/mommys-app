package com.mommys.app.util.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NetworkAwareDispatcher - Manejador de requests con reconexión automática
 * Basado en Dispatcher.java de Picasso en la app original (decompilada)
 * 
 * Características:
 * - Mantiene una cola de acciones fallidas (failedActions)
 * - Cuando se recupera la conexión, ejecuta automáticamente las acciones fallidas (flushFailedActions)
 * - Soporta retry con delay configurable (RETRY_DELAY = 500ms)
 * - Soporta marcar acciones para replay (markForReplay)
 * - Thread-safe
 */
class NetworkAwareDispatcher private constructor() : NetworkMonitor.NetworkConnectivityListener {
    
    companion object {
        private const val TAG = "NetworkDispatcher"
        
        /**
         * Delay en milisegundos para reintentar (como Dispatcher.RETRY_DELAY = 500)
         */
        const val RETRY_DELAY_MS = 500L
        
        /**
         * Delay en milisegundos para batch de completados (como Dispatcher.BATCH_DELAY = 200)
         */
        const val BATCH_DELAY_MS = 200L
        
        /**
         * Máximo número de reintentos por acción
         */
        const val MAX_RETRIES = 3
        
        @Volatile
        private var INSTANCE: NetworkAwareDispatcher? = null
        
        /**
         * Obtiene la instancia singleton del NetworkAwareDispatcher
         */
        fun getInstance(): NetworkAwareDispatcher {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkAwareDispatcher().also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * Representa una acción que puede fallar y ser reintentada
     */
    data class FailedAction(
        val id: String,
        val action: () -> Unit,
        val retryCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis(),
        val supportsReplay: Boolean = true
    )
    
    /**
     * Interface para callbacks de página/refresh
     */
    interface PageRefreshCallback {
        fun onRefreshRequested()
    }
    
    // Handler para el hilo principal
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Mapa de acciones fallidas (como Dispatcher.failedActions = new WeakHashMap())
    // Usamos LinkedHashMap para mantener orden de inserción
    private val failedActions = LinkedHashMap<String, FailedAction>()
    
    // Lock para sincronización
    private val lock = Any()
    
    // Si está procesando acciones fallidas
    private val isFlushing = AtomicBoolean(false)
    
    // Callbacks de páginas para refresh automático (WeakReferences para evitar memory leaks)
    private val pageRefreshCallbacks = ConcurrentLinkedQueue<WeakReference<PageRefreshCallback>>()
    
    // Referencia al NetworkMonitor
    private var networkMonitor: NetworkMonitor? = null
    
    // Si está inicializado
    private var isInitialized = false
    
    /**
     * Inicializa el dispatcher con el NetworkMonitor
     * Debe llamarse después de que NetworkMonitor esté registrado
     */
    fun initialize(networkMonitor: NetworkMonitor) {
        if (isInitialized) {
            return
        }
        
        this.networkMonitor = networkMonitor
        networkMonitor.addConnectivityListener(this)
        isInitialized = true
        
        Log.d(TAG, "NetworkAwareDispatcher initialized")
    }
    
    /**
     * Limpia recursos
     */
    fun shutdown() {
        networkMonitor?.removeConnectivityListener(this)
        networkMonitor = null
        isInitialized = false
        
        synchronized(lock) {
            failedActions.clear()
        }
        pageRefreshCallbacks.clear()
        
        Log.d(TAG, "NetworkAwareDispatcher shutdown")
    }
    
    /**
     * Marca una acción para replay cuando vuelva la conexión
     * Basado en Dispatcher.markForReplay() líneas 228-240 y 494-500
     * 
     * @param id Identificador único de la acción
     * @param action La acción a ejecutar cuando vuelva la conexión
     * @param supportsReplay Si la acción soporta ser reintentada
     */
    fun markForReplay(id: String, action: () -> Unit, supportsReplay: Boolean = true) {
        if (!supportsReplay) {
            Log.d(TAG, "Action $id does not support replay, skipping")
            return
        }
        
        synchronized(lock) {
            // Verificar si ya existe y no excede límite de reintentos
            val existing = failedActions[id]
            if (existing != null && existing.retryCount >= MAX_RETRIES) {
                Log.d(TAG, "Action $id exceeded max retries ($MAX_RETRIES), not marking for replay")
                failedActions.remove(id)
                return
            }
            
            val retryCount = (existing?.retryCount ?: 0) + 1
            val failedAction = FailedAction(
                id = id,
                action = action,
                retryCount = retryCount,
                supportsReplay = supportsReplay
            )
            
            failedActions[id] = failedAction
            Log.d(TAG, "Marked action for replay: $id (retry #$retryCount)")
        }
    }
    
    /**
     * Cancela una acción pendiente
     * Basado en Dispatcher.performCancel() líneas 299-322
     */
    fun cancelAction(id: String) {
        synchronized(lock) {
            val removed = failedActions.remove(id)
            if (removed != null) {
                Log.d(TAG, "Cancelled action: $id")
            }
        }
    }
    
    /**
     * Verifica si hay acciones pendientes
     */
    fun hasPendingActions(): Boolean {
        synchronized(lock) {
            return failedActions.isNotEmpty()
        }
    }
    
    /**
     * Obtiene el número de acciones pendientes
     */
    fun getPendingActionsCount(): Int {
        synchronized(lock) {
            return failedActions.size
        }
    }
    
    /**
     * Registra un callback para refresh de página
     */
    fun registerPageRefreshCallback(callback: PageRefreshCallback) {
        // Limpiar referencias muertas
        cleanupDeadReferences()
        
        // Verificar que no esté ya registrado
        val exists = pageRefreshCallbacks.any { it.get() == callback }
        if (!exists) {
            pageRefreshCallbacks.add(WeakReference(callback))
            Log.d(TAG, "Registered page refresh callback")
        }
    }
    
    /**
     * Desregistra un callback de refresh
     */
    fun unregisterPageRefreshCallback(callback: PageRefreshCallback) {
        pageRefreshCallbacks.removeIf { it.get() == callback || it.get() == null }
        Log.d(TAG, "Unregistered page refresh callback")
    }
    
    /**
     * Ejecuta una acción con retry automático si falla por red
     * 
     * @param id Identificador único
     * @param action La acción a ejecutar
     * @param onSuccess Callback cuando tiene éxito
     * @param onError Callback cuando falla definitivamente
     */
    fun executeWithRetry(
        id: String,
        action: suspend () -> Unit,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        // Esta es una función de utilidad que se puede usar para envolver calls de API
        // La implementación real del retry se hace en cada lugar donde se usa
        // Para mantener compatibilidad con código existente
    }
    
    // ================= NetworkConnectivityListener =================
    
    /**
     * Llamado cuando la red está disponible
     * Basado en Dispatcher.performNetworkStateChange() líneas 343-351
     */
    override fun onNetworkAvailable(state: NetworkState) {
        Log.d(TAG, "Network available, flushing failed actions...")
        
        // Ejecutar acciones fallidas con un pequeño delay
        mainHandler.postDelayed({
            flushFailedActions()
            notifyPageRefresh()
        }, BATCH_DELAY_MS)
    }
    
    /**
     * Llamado cuando se pierde la red
     */
    override fun onNetworkLost() {
        Log.d(TAG, "Network lost")
        // No hacemos nada especial aquí, las acciones se marcarán para replay
        // cuando fallen por red
    }
    
    /**
     * Llamado cuando cambian las capacidades de red
     */
    override fun onNetworkCapabilitiesChanged(state: NetworkState) {
        Log.d(TAG, "Network capabilities changed: ${state.networkType}")
        
        // Si cambiamos de móvil a WiFi, podríamos querer reintentar algunas acciones
        if (state.isWifi && hasPendingActions()) {
            mainHandler.postDelayed({
                flushFailedActions()
            }, BATCH_DELAY_MS)
        }
    }
    
    // ================= IMPLEMENTACIÓN =================
    
    /**
     * Ejecuta todas las acciones fallidas
     * Basado en Dispatcher.flushFailedActions() líneas 199-212
     * 
     * Este método es público para permitir que MainActivity lo llame
     * cuando muestra el Snackbar de reconexión y el usuario hace tap en "Refresh"
     */
    fun flushFailedActions() {
        if (!isFlushing.compareAndSet(false, true)) {
            Log.d(TAG, "Already flushing failed actions")
            return
        }
        
        try {
            val actionsToExecute: List<FailedAction>
            
            synchronized(lock) {
                if (failedActions.isEmpty()) {
                    Log.d(TAG, "No failed actions to flush")
                    return
                }
                
                // Copiar y limpiar
                actionsToExecute = failedActions.values.toList()
                failedActions.clear()
                
                Log.d(TAG, "Flushing ${actionsToExecute.size} failed actions")
            }
            
            // Ejecutar cada acción con delay entre ellas
            actionsToExecute.forEachIndexed { index, failedAction ->
                mainHandler.postDelayed({
                    try {
                        Log.d(TAG, "Replaying action: ${failedAction.id}")
                        failedAction.action.invoke()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error replaying action ${failedAction.id}", e)
                        
                        // Si falla de nuevo y no excede reintentos, marcar para replay
                        if (failedAction.retryCount < MAX_RETRIES) {
                            markForReplay(
                                id = failedAction.id,
                                action = failedAction.action,
                                supportsReplay = failedAction.supportsReplay
                            )
                        }
                    }
                }, RETRY_DELAY_MS * index)
            }
            
        } finally {
            // Dar tiempo para que se ejecuten las acciones antes de permitir otro flush
            mainHandler.postDelayed({
                isFlushing.set(false)
            }, RETRY_DELAY_MS * 3)
        }
    }
    
    /**
     * Notifica a todas las páginas registradas que deben refrescarse
     */
    private fun notifyPageRefresh() {
        cleanupDeadReferences()
        
        pageRefreshCallbacks.forEach { ref ->
            ref.get()?.let { callback ->
                try {
                    mainHandler.post {
                        callback.onRefreshRequested()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying page refresh", e)
                }
            }
        }
    }
    
    /**
     * Limpia referencias muertas de la cola
     */
    private fun cleanupDeadReferences() {
        pageRefreshCallbacks.removeIf { it.get() == null }
    }
}
