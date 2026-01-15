package com.mommys.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.database.AppDatabase
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.network.NetworkAwareDispatcher
import com.mommys.app.util.network.NetworkMonitor

class MommysApplication : Application() {
    
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    
    // NetworkMonitor para detectar cambios de conectividad en tiempo real
    // Basado en ff/b.java de la app original
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor.getInstance(this) }
    
    // NetworkAwareDispatcher para manejar reconexión automática
    // Basado en Dispatcher.java de Picasso en la app original
    val networkDispatcher: NetworkAwareDispatcher by lazy { NetworkAwareDispatcher.getInstance() }
    
    override fun onCreate() {
        super.onCreate()
        _instance = this
        
        // Configurar ApiClient para usar el host correcto (e621 o e926)
        try {
            val useE621 = preferencesManager.useE621()
            ApiClient.setUseE621(useE621)
        } catch (e: Exception) {
            // Default to e926 (safe mode)
            ApiClient.setUseE621(false)
        }
        
        // Apply theme according to preferences (0=system, 1=light, 2=dark, 3=battery)
        try {
            val nightMode = when (preferencesManager.getThemeMode()) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                3 -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            // Default to system mode on error
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        
        // Apply saved language at app startup
        // This ensures the locale is restored when the app starts
        try {
            val savedLanguage = preferencesManager.getLanguage()
            if (savedLanguage.isNotEmpty()) {
                val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(savedLanguage)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        } catch (e: Exception) {
            // If language restore fails, use system default
            android.util.Log.e("MommysApplication", "Error restoring language", e)
        }
        
        // Inicializar NetworkMonitor y NetworkAwareDispatcher
        // Basado en ff/b.java constructor que llama a x() para registrar callbacks
        initializeNetworkMonitoring()
    }
    
    /**
     * Inicializa el sistema de monitoreo de red
     * Basado en ff/b.java línea 41: x() para registrar callbacks
     */
    private fun initializeNetworkMonitoring() {
        try {
            // Registrar el NetworkMonitor para recibir callbacks de cambios de red
            networkMonitor.register()
            
            // Inicializar el dispatcher con el monitor
            networkDispatcher.initialize(networkMonitor)
            
        } catch (e: Exception) {
            // Si falla el registro, la app sigue funcionando pero sin reconexión automática
            android.util.Log.e("MommysApplication", "Error initializing network monitoring", e)
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // Limpiar recursos de red
        try {
            networkDispatcher.shutdown()
            networkMonitor.unregister()
        } catch (e: Exception) {
            android.util.Log.e("MommysApplication", "Error shutting down network monitoring", e)
        }
    }
    
    companion object {
        @Volatile
        private lateinit var _instance: MommysApplication
        
        @JvmStatic
        fun getInstance(): MommysApplication = _instance
    }
}
