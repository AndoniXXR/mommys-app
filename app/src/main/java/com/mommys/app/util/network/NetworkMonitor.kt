package com.mommys.app.util.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NetworkMonitor - Monitor de conectividad de red en tiempo real
 * Basado en ff/b.java y a6/e.java de la app original (decompilada)
 * 
 * Características:
 * - Usa NetworkCallback (API 24+) con fallback a BroadcastReceiver para API < 24
 * - Detecta cambios de red en tiempo real (WiFi, Datos móviles, Sin conexión)
 * - Expone el estado de red como LiveData y StateFlow para compatibilidad
 * - Notifica a listeners cuando la red se conecta/desconecta
 * - Detecta modo avión
 * - Detecta tipo de red (2G, 3G, 4G, 5G, WiFi)
 */
class NetworkMonitor private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        
        @Volatile
        private var INSTANCE: NetworkMonitor? = null
        
        /**
         * Obtiene la instancia singleton del NetworkMonitor
         */
        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    // ConnectivityManager para detectar estado de red
    private val connectivityManager: ConnectivityManager? = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    
    // Handler para el hilo principal
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Estado de red actual (StateFlow para coroutines)
    private val _networkState = MutableStateFlow(NetworkState.DISCONNECTED)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    // Estado de red actual (LiveData para compatibilidad con ViewModels)
    private val _networkStateLiveData = MutableLiveData(NetworkState.DISCONNECTED)
    val networkStateLiveData: LiveData<NetworkState> = _networkStateLiveData
    
    // Estado de conexión simple (para uso rápido)
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Listeners para cambios de conectividad
    private val connectivityListeners = mutableListOf<NetworkConnectivityListener>()
    
    // Lock para sincronización
    private val lock = Any()
    
    // NetworkCallback para API 24+
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // BroadcastReceiver para API < 24 (fallback)
    private var connectivityReceiver: BroadcastReceiver? = null
    
    // Si el monitor está registrado
    private var isRegistered = false
    
    // Último estado de conexión conocido (para detectar cambios)
    private var wasConnected = false
    
    // Modo avión
    private var isAirplaneMode = false
    
    /**
     * Interface para listeners de conectividad
     */
    interface NetworkConnectivityListener {
        /**
         * Llamado cuando la red está disponible
         */
        fun onNetworkAvailable(state: NetworkState)
        
        /**
         * Llamado cuando se pierde la conexión
         */
        fun onNetworkLost()
        
        /**
         * Llamado cuando cambian las capacidades de red (WiFi <-> Móvil)
         */
        fun onNetworkCapabilitiesChanged(state: NetworkState)
    }
    
    init {
        // Obtener estado inicial
        updateNetworkState()
    }
    
    /**
     * Registra el monitor de red
     * Debe llamarse desde Application.onCreate() o Activity.onStart()
     */
    fun register() {
        synchronized(lock) {
            if (isRegistered) {
                Log.d(TAG, "NetworkMonitor already registered")
                return
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // API 24+: Usar NetworkCallback moderno
                    registerNetworkCallback()
                } else {
                    // API < 24: Usar BroadcastReceiver legacy
                    registerBroadcastReceiver()
                }
                
                // También registrar para modo avión
                registerAirplaneModeReceiver()
                
                isRegistered = true
                Log.d(TAG, "NetworkMonitor registered successfully")
                
                // Actualizar estado inicial
                updateNetworkState()
                
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException registering NetworkMonitor", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering NetworkMonitor", e)
            }
        }
    }
    
    /**
     * Desregistra el monitor de red
     * Debe llamarse desde Application.onTerminate() o cuando ya no se necesite
     */
    fun unregister() {
        synchronized(lock) {
            if (!isRegistered) {
                return
            }
            
            try {
                // Desregistrar NetworkCallback
                networkCallback?.let {
                    connectivityManager?.unregisterNetworkCallback(it)
                    networkCallback = null
                }
                
                // Desregistrar BroadcastReceiver
                connectivityReceiver?.let {
                    try {
                        context.unregisterReceiver(it)
                    } catch (e: IllegalArgumentException) {
                        // Receiver no estaba registrado
                    }
                    connectivityReceiver = null
                }
                
                isRegistered = false
                Log.d(TAG, "NetworkMonitor unregistered")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering NetworkMonitor", e)
            }
        }
    }
    
    /**
     * Registra un listener de conectividad
     */
    fun addConnectivityListener(listener: NetworkConnectivityListener) {
        synchronized(lock) {
            if (!connectivityListeners.contains(listener)) {
                connectivityListeners.add(listener)
            }
        }
    }
    
    /**
     * Remueve un listener de conectividad
     */
    fun removeConnectivityListener(listener: NetworkConnectivityListener) {
        synchronized(lock) {
            connectivityListeners.remove(listener)
        }
    }
    
    /**
     * Verifica si hay conexión a internet
     */
    fun isNetworkAvailable(): Boolean {
        return _isConnected.value
    }
    
    /**
     * Verifica si la conexión es WiFi
     */
    fun isWifiConnected(): Boolean {
        return _networkState.value.isWifi
    }
    
    /**
     * Verifica si la conexión es de datos móviles
     */
    fun isMobileConnected(): Boolean {
        return _networkState.value.isMobile
    }
    
    /**
     * Obtiene el estado de red actual
     */
    fun getCurrentState(): NetworkState {
        return _networkState.value
    }
    
    // ================= IMPLEMENTACIÓN PRIVADA =================
    
    /**
     * Registra NetworkCallback para API 24+
     * Basado en a6/e.java y ff/b.java de la app original
     */
    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            
            /**
             * Llamado cuando una red está disponible
             * Corresponde a onAvailable() en a6/e.java línea 26
             */
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                mainHandler.post {
                    updateNetworkState()
                    
                    // Notificar si pasamos de desconectado a conectado
                    if (!wasConnected) {
                        wasConnected = true
                        notifyNetworkAvailable()
                    }
                }
            }
            
            /**
             * Llamado cuando se pierde la red
             * Corresponde a onLost() en a6/e.java línea 91
             */
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                mainHandler.post {
                    // Verificar si realmente perdimos toda conectividad
                    // (podría haber otra red disponible)
                    updateNetworkState()
                    
                    if (!_isConnected.value && wasConnected) {
                        wasConnected = false
                        notifyNetworkLost()
                    }
                }
            }
            
            /**
             * Llamado cuando cambian las capacidades de red
             * Corresponde a onCapabilitiesChanged() en a6/e.java línea 62
             */
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(TAG, "Network capabilities changed")
                mainHandler.post {
                    val previousState = _networkState.value
                    updateNetworkStateFromCapabilities(networkCapabilities)
                    
                    // Notificar cambio de capacidades si cambió algo significativo
                    val newState = _networkState.value
                    if (previousState.networkType != newState.networkType ||
                        previousState.isMetered != newState.isMetered) {
                        notifyCapabilitiesChanged()
                    }
                }
            }
            
            /**
             * Llamado cuando cambia el estado de bloqueo de red
             * Corresponde a onBlockedStatusChanged() en a6/e.java línea 47
             */
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                Log.d(TAG, "Network blocked status changed: blocked=$blocked")
                if (!blocked) {
                    mainHandler.post {
                        updateNetworkState()
                        if (_isConnected.value && !wasConnected) {
                            wasConnected = true
                            notifyNetworkAvailable()
                        }
                    }
                }
            }
        }
        
        // Registrar callback para todas las redes
        // Basado en ff/b.java línea 90: registerDefaultNetworkCallback
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
            // Fallback a BroadcastReceiver si falla
            registerBroadcastReceiver()
        }
    }
    
    /**
     * Registra BroadcastReceiver para API < 24 (legacy)
     * Basado en ff/a.java de la app original
     */
    @Suppress("DEPRECATION")
    private fun registerBroadcastReceiver() {
        val receiver = object : BroadcastReceiver() {
            
            private var previouslyConnected = false
            
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) {
                    return
                }
                
                // Obtener estado de red actual
                val networkInfo = connectivityManager?.activeNetworkInfo
                val connected = networkInfo != null && networkInfo.isConnected
                
                Log.d(TAG, "Connectivity changed: connected=$connected")
                
                mainHandler.post {
                    updateNetworkState()
                    
                    // Notificar cambio de conectado a desconectado o viceversa
                    // Basado en ff/a.java líneas 30-37
                    if (connected && !previouslyConnected) {
                        previouslyConnected = true
                        wasConnected = true
                        notifyNetworkAvailable()
                    } else if (!connected && previouslyConnected) {
                        previouslyConnected = false
                        wasConnected = false
                        notifyNetworkLost()
                    }
                }
            }
        }
        
        // Registrar para CONNECTIVITY_ACTION
        @Suppress("DEPRECATION")
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiver(receiver, filter)
        connectivityReceiver = receiver
    }
    
    /**
     * Registra receiver para cambios de modo avión
     * Basado en Dispatcher.java NetworkBroadcastReceiver de Picasso
     */
    private fun registerAirplaneModeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                    val airplaneMode = intent.getBooleanExtra("state", false)
                    Log.d(TAG, "Airplane mode changed: $airplaneMode")
                    
                    mainHandler.post {
                        isAirplaneMode = airplaneMode
                        updateNetworkState()
                        
                        if (airplaneMode) {
                            notifyNetworkLost()
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)
    }
    
    /**
     * Actualiza el estado de red actual
     */
    @Suppress("DEPRECATION")
    private fun updateNetworkState() {
        val newState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getNetworkStateModern()
        } else {
            getNetworkStateLegacy()
        }
        
        _networkState.value = newState
        _networkStateLiveData.postValue(newState)
        _isConnected.value = newState.isConnected
        
        Log.d(TAG, "Network state updated: $newState")
    }
    
    /**
     * Actualiza estado desde NetworkCapabilities (para callback)
     */
    private fun updateNetworkStateFromCapabilities(capabilities: NetworkCapabilities) {
        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            isConnected
        }
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val isNotRoaming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        } else {
            true
        }
        
        val networkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
            else -> NetworkType.OTHER
        }
        
        val networkSubtype = if (networkType == NetworkType.MOBILE) {
            getMobileNetworkSubtype()
        } else {
            NetworkSubtype.UNKNOWN
        }
        
        val newState = NetworkState(
            isConnected = isConnected,
            isValidated = isValidated,
            isMetered = isMetered,
            isNotRoaming = isNotRoaming,
            networkType = networkType,
            networkSubtype = networkSubtype
        )
        
        _networkState.value = newState
        _networkStateLiveData.postValue(newState)
        _isConnected.value = isConnected && isValidated
    }
    
    /**
     * Obtiene el estado de red para API 23+ (moderno)
     */
    private fun getNetworkStateModern(): NetworkState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return NetworkState.DISCONNECTED
        }
        
        val network = connectivityManager?.activeNetwork
            ?: return NetworkState.DISCONNECTED
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkState.DISCONNECTED
        
        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val isNotRoaming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        } else {
            true
        }
        
        val networkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
            else -> NetworkType.OTHER
        }
        
        val networkSubtype = if (networkType == NetworkType.MOBILE) {
            getMobileNetworkSubtype()
        } else {
            NetworkSubtype.UNKNOWN
        }
        
        return NetworkState(
            isConnected = isConnected,
            isValidated = isValidated,
            isMetered = isMetered,
            isNotRoaming = isNotRoaming,
            networkType = networkType,
            networkSubtype = networkSubtype
        )
    }
    
    /**
     * Obtiene el estado de red para API < 23 (legacy)
     */
    @Suppress("DEPRECATION")
    private fun getNetworkStateLegacy(): NetworkState {
        val networkInfo = connectivityManager?.activeNetworkInfo
            ?: return NetworkState.DISCONNECTED
        
        val isConnected = networkInfo.isConnected
        val isValidated = networkInfo.isConnected // En API antigua, asumimos validado si conectado
        
        val networkType = when (networkInfo.type) {
            ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
            ConnectivityManager.TYPE_MOBILE -> NetworkType.MOBILE
            ConnectivityManager.TYPE_ETHERNET -> NetworkType.ETHERNET
            ConnectivityManager.TYPE_VPN -> NetworkType.VPN
            ConnectivityManager.TYPE_BLUETOOTH -> NetworkType.BLUETOOTH
            else -> NetworkType.OTHER
        }
        
        val isMetered = networkType == NetworkType.MOBILE
        val isNotRoaming = !networkInfo.isRoaming
        
        val networkSubtype = if (networkType == NetworkType.MOBILE) {
            getNetworkSubtypeFromTelephonyType(networkInfo.subtype)
        } else {
            NetworkSubtype.UNKNOWN
        }
        
        return NetworkState(
            isConnected = isConnected,
            isValidated = isValidated,
            isMetered = isMetered,
            isNotRoaming = isNotRoaming,
            networkType = networkType,
            networkSubtype = networkSubtype
        )
    }
    
    /**
     * Obtiene el subtipo de red móvil actual
     * Basado en PicassoExecutorService.java y di/k1.java de la app original
     */
    @Suppress("DEPRECATION")
    private fun getMobileNetworkSubtype(): NetworkSubtype {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            } else {
                telephonyManager?.networkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
            getNetworkSubtypeFromTelephonyType(networkType)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot get network subtype: missing READ_PHONE_STATE permission")
            NetworkSubtype.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network subtype", e)
            NetworkSubtype.UNKNOWN
        }
    }
    
    /**
     * Convierte TelephonyManager.NETWORK_TYPE_* a NetworkSubtype
     * Basado en PicassoExecutorService.adjustThreadCount() líneas 57-83
     */
    private fun getNetworkSubtypeFromTelephonyType(type: Int): NetworkSubtype {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> NetworkSubtype.GPRS
            TelephonyManager.NETWORK_TYPE_EDGE -> NetworkSubtype.EDGE
            TelephonyManager.NETWORK_TYPE_UMTS -> NetworkSubtype.UMTS
            TelephonyManager.NETWORK_TYPE_CDMA -> NetworkSubtype.CDMA
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> NetworkSubtype.EVDO_0
            TelephonyManager.NETWORK_TYPE_EVDO_A -> NetworkSubtype.EVDO_A
            TelephonyManager.NETWORK_TYPE_1xRTT -> NetworkSubtype.RTT
            TelephonyManager.NETWORK_TYPE_HSDPA -> NetworkSubtype.HSDPA
            TelephonyManager.NETWORK_TYPE_HSUPA -> NetworkSubtype.HSUPA
            TelephonyManager.NETWORK_TYPE_HSPA -> NetworkSubtype.HSPA
            TelephonyManager.NETWORK_TYPE_IDEN -> NetworkSubtype.IDEN
            TelephonyManager.NETWORK_TYPE_EVDO_B -> NetworkSubtype.EVDO_B
            TelephonyManager.NETWORK_TYPE_LTE -> NetworkSubtype.LTE
            TelephonyManager.NETWORK_TYPE_EHRPD -> NetworkSubtype.EHRPD
            TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkSubtype.HSPAP
            20 -> NetworkSubtype.NR // NETWORK_TYPE_NR (5G) = 20
            else -> NetworkSubtype.UNKNOWN
        }
    }
    
    // ================= NOTIFICACIONES A LISTENERS =================
    
    /**
     * Notifica a todos los listeners que la red está disponible
     */
    private fun notifyNetworkAvailable() {
        val state = _networkState.value
        Log.d(TAG, "Notifying network available: $state")
        
        synchronized(lock) {
            connectivityListeners.forEach { listener ->
                try {
                    listener.onNetworkAvailable(state)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener onNetworkAvailable", e)
                }
            }
        }
    }
    
    /**
     * Notifica a todos los listeners que se perdió la red
     */
    private fun notifyNetworkLost() {
        Log.d(TAG, "Notifying network lost")
        
        synchronized(lock) {
            connectivityListeners.forEach { listener ->
                try {
                    listener.onNetworkLost()
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener onNetworkLost", e)
                }
            }
        }
    }
    
    /**
     * Notifica a todos los listeners que cambiaron las capacidades
     */
    private fun notifyCapabilitiesChanged() {
        val state = _networkState.value
        Log.d(TAG, "Notifying capabilities changed: $state")
        
        synchronized(lock) {
            connectivityListeners.forEach { listener ->
                try {
                    listener.onNetworkCapabilitiesChanged(state)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener onCapabilitiesChanged", e)
                }
            }
        }
    }
}
