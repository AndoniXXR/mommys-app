package com.mommys.app.util

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.mommys.app.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manager para el cache de la aplicación
 * Basado en ri/a.java de la app original
 * 
 * Maneja:
 * - Cálculo del tamaño del cache
 * - Limpieza del cache
 * - Verificación automática contra el límite configurado
 * - Cache de Glide
 */
object CacheManager {
    
    private const val TAG = "CacheManager"
    
    /**
     * Calcula el tamaño total de un directorio recursivamente
     * Equivalente a ri.a.b() de la app original
     * 
     * @param directory Directorio a calcular
     * @return Tamaño en bytes
     */
    fun getDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
    
    /**
     * Calcula el tamaño del cache de la aplicación en bytes
     * Incluye cacheDir y el cache de Glide
     */
    fun getCacheSize(context: Context): Long {
        return getDirectorySize(context.cacheDir)
    }
    
    /**
     * Calcula el tamaño del cache en MB
     */
    fun getCacheSizeMB(context: Context): Long {
        return getCacheSize(context) / (1024 * 1024)
    }
    
    /**
     * Formatea el tamaño en bytes a un string legible
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    /**
     * Elimina recursivamente todos los archivos de un directorio
     * Equivalente a ri.a.a() de la app original
     * 
     * @param directory Directorio a limpiar
     */
    fun clearDirectory(directory: File) {
        if (!directory.exists()) return
        
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearDirectory(file)
            }
            file.delete()
        }
    }
    
    /**
     * Limpia todo el cache de la aplicación
     * Incluye cacheDir y el cache de Glide
     */
    fun clearAllCache(context: Context) {
        try {
            // Limpiar cache general
            clearDirectory(context.cacheDir)
            Log.d(TAG, "Cache directory cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache directory", e)
        }
    }
    
    /**
     * Limpia el cache de Glide (debe llamarse en background thread)
     */
    suspend fun clearGlideCache(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                Glide.get(context).clearDiskCache()
                Log.d(TAG, "Glide disk cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing Glide disk cache", e)
            }
        }
        
        withContext(Dispatchers.Main) {
            try {
                Glide.get(context).clearMemory()
                Log.d(TAG, "Glide memory cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing Glide memory cache", e)
            }
        }
    }
    
    /**
     * Limpia completamente todo el cache (cacheDir + Glide)
     * Debe llamarse desde una coroutine
     */
    suspend fun clearAllCacheComplete(context: Context) {
        clearGlideCache(context)
        withContext(Dispatchers.IO) {
            clearAllCache(context)
        }
    }
    
    /**
     * Verifica si el cache excede el límite y lo limpia si es necesario
     * Equivalente al código en di/x.java de la app original
     * 
     * Esta función debe llamarse en background thread (ej: onResume de MainActivity)
     * 
     * @param context Contexto de la aplicación
     * @param preferencesManager Manager de preferencias
     * @return true si se limpió el cache, false si no era necesario
     */
    fun checkAndCleanCacheIfNeeded(context: Context, preferencesManager: PreferencesManager): Boolean {
        // Verificar si el límite de cache está habilitado
        if (!preferencesManager.storageMaxCache) {
            Log.d(TAG, "Cache limit not enabled, skipping check")
            return false
        }
        
        val maxCacheSizeMB = preferencesManager.storageMaxCacheSlider
        val currentCacheSizeMB = getCacheSizeMB(context)
        
        Log.d(TAG, "Cache check: current=${currentCacheSizeMB}MB, max=${maxCacheSizeMB}MB")
        
        if (currentCacheSizeMB > maxCacheSizeMB) {
            Log.d(TAG, "Cache exceeds limit, clearing...")
            clearAllCache(context)
            return true
        }
        
        return false
    }
    
    /**
     * Versión suspendida que también limpia Glide
     */
    suspend fun checkAndCleanCacheIfNeededSuspend(context: Context, preferencesManager: PreferencesManager): Boolean {
        if (!preferencesManager.storageMaxCache) {
            return false
        }
        
        val maxCacheSizeMB = preferencesManager.storageMaxCacheSlider
        val currentCacheSizeMB = getCacheSizeMB(context)
        
        if (currentCacheSizeMB > maxCacheSizeMB) {
            clearAllCacheComplete(context)
            return true
        }
        
        return false
    }
    
    /**
     * Obtiene información detallada del cache para debug/display
     */
    fun getCacheInfo(context: Context): CacheInfo {
        val cacheDir = context.cacheDir
        val totalSize = getCacheSize(context)
        
        // Contar subdirectorios principales
        val subDirs = mutableMapOf<String, Long>()
        cacheDir.listFiles()?.forEach { file ->
            val size = if (file.isDirectory) getDirectorySize(file) else file.length()
            subDirs[file.name] = size
        }
        
        return CacheInfo(
            totalBytes = totalSize,
            totalFormatted = formatSize(totalSize),
            subDirectories = subDirs
        )
    }
    
    data class CacheInfo(
        val totalBytes: Long,
        val totalFormatted: String,
        val subDirectories: Map<String, Long>
    )
}
