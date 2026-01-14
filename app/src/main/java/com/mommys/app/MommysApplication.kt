package com.mommys.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.database.AppDatabase
import com.mommys.app.data.preferences.PreferencesManager

class MommysApplication : Application() {
    
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    
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
    }
    
    companion object {
        @Volatile
        private lateinit var _instance: MommysApplication
        
        @JvmStatic
        fun getInstance(): MommysApplication = _instance
    }
}
