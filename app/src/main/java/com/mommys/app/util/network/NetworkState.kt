package com.mommys.app.util.network

/**
 * NetworkState - Estado de la conexión de red
 * Basado en a6/k.java de la app original (decompilada)
 * 
 * Contiene información sobre el estado actual de la conexión:
 * - isConnected: Si hay conexión a internet
 * - isValidated: Si la conexión ha sido validada (puede acceder a internet)
 * - isMetered: Si la conexión es de pago (datos móviles)
 * - isNotRoaming: Si NO está en roaming
 * - networkType: Tipo de red (WiFi, Mobile, etc.)
 * - networkSubtype: Subtipo de red móvil (2G, 3G, 4G, 5G)
 */
data class NetworkState(
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = true,
    val isNotRoaming: Boolean = true,
    val networkType: NetworkType = NetworkType.NONE,
    val networkSubtype: NetworkSubtype = NetworkSubtype.UNKNOWN
) {
    
    /**
     * Indica si la conexión es WiFi
     */
    val isWifi: Boolean
        get() = networkType == NetworkType.WIFI
    
    /**
     * Indica si la conexión es de datos móviles
     */
    val isMobile: Boolean
        get() = networkType == NetworkType.MOBILE
    
    /**
     * Indica si la conexión es ethernet
     */
    val isEthernet: Boolean
        get() = networkType == NetworkType.ETHERNET
    
    /**
     * Indica si hay una conexión válida y funcional
     */
    val isOnline: Boolean
        get() = isConnected && isValidated
    
    /**
     * Indica si la conexión es rápida (WiFi, Ethernet, 4G, 5G)
     */
    val isFast: Boolean
        get() = when {
            networkType == NetworkType.WIFI -> true
            networkType == NetworkType.ETHERNET -> true
            networkType == NetworkType.MOBILE -> when (networkSubtype) {
                NetworkSubtype.LTE,
                NetworkSubtype.HSPAP,
                NetworkSubtype.HSPA,
                NetworkSubtype.NR -> true // 4G, 4G+, 5G
                else -> false
            }
            else -> false
        }
    
    /**
     * Indica si la conexión es lenta (2G, EDGE)
     */
    val isSlow: Boolean
        get() = networkType == NetworkType.MOBILE && when (networkSubtype) {
            NetworkSubtype.GPRS,
            NetworkSubtype.EDGE,
            NetworkSubtype.CDMA,
            NetworkSubtype.IDEN -> true
            else -> false
        }
    
    /**
     * Número de threads recomendados para descargas según la velocidad de red
     * Basado en PicassoExecutorService.java de la app original
     */
    val recommendedThreadCount: Int
        get() = when {
            networkType == NetworkType.WIFI -> 4
            networkType == NetworkType.ETHERNET -> 4
            networkType == NetworkType.MOBILE -> when (networkSubtype) {
                NetworkSubtype.GPRS, NetworkSubtype.EDGE -> 1  // 2G
                NetworkSubtype.UMTS, NetworkSubtype.CDMA, 
                NetworkSubtype.EVDO_0, NetworkSubtype.EVDO_A,
                NetworkSubtype.EVDO_B, NetworkSubtype.RTT -> 2  // 3G
                NetworkSubtype.HSDPA, NetworkSubtype.HSUPA,
                NetworkSubtype.HSPA, NetworkSubtype.HSPAP -> 3  // 3G+
                NetworkSubtype.LTE, NetworkSubtype.NR -> 3      // 4G, 5G
                else -> 3
            }
            else -> 3
        }
    
    companion object {
        /**
         * Estado por defecto: sin conexión
         */
        val DISCONNECTED = NetworkState(
            isConnected = false,
            isValidated = false,
            isMetered = true,
            isNotRoaming = true,
            networkType = NetworkType.NONE,
            networkSubtype = NetworkSubtype.UNKNOWN
        )
        
        /**
         * Estado conectado a WiFi
         */
        val WIFI_CONNECTED = NetworkState(
            isConnected = true,
            isValidated = true,
            isMetered = false,
            isNotRoaming = true,
            networkType = NetworkType.WIFI,
            networkSubtype = NetworkSubtype.UNKNOWN
        )
    }
}

/**
 * Tipos de red
 */
enum class NetworkType {
    NONE,
    WIFI,
    MOBILE,
    ETHERNET,
    VPN,
    BLUETOOTH,
    OTHER
}

/**
 * Subtipos de red móvil
 * Basado en TelephonyManager.NETWORK_TYPE_* constants
 */
enum class NetworkSubtype {
    UNKNOWN,
    
    // 2G
    GPRS,       // NETWORK_TYPE_GPRS = 1
    EDGE,       // NETWORK_TYPE_EDGE = 2
    CDMA,       // NETWORK_TYPE_CDMA = 4
    IDEN,       // NETWORK_TYPE_IDEN = 11
    
    // 3G
    UMTS,       // NETWORK_TYPE_UMTS = 3
    EVDO_0,     // NETWORK_TYPE_EVDO_0 = 5
    EVDO_A,     // NETWORK_TYPE_EVDO_A = 6
    RTT,        // NETWORK_TYPE_1xRTT = 7
    EVDO_B,     // NETWORK_TYPE_EVDO_B = 12
    EHRPD,      // NETWORK_TYPE_EHRPD = 14
    
    // 3G+ (HSPA)
    HSDPA,      // NETWORK_TYPE_HSDPA = 8
    HSUPA,      // NETWORK_TYPE_HSUPA = 9
    HSPA,       // NETWORK_TYPE_HSPA = 10
    HSPAP,      // NETWORK_TYPE_HSPAP = 15
    
    // 4G
    LTE,        // NETWORK_TYPE_LTE = 13
    
    // 5G
    NR          // NETWORK_TYPE_NR = 20
}
