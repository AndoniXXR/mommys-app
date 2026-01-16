package com.mommys.app.util

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Manager para el cache de ExoPlayer
 * Basado en ViewOnClickListenerC1887s.java líneas 95-109 de la app original
 * 
 * Maneja:
 * - Cache de videos de 100MB
 * - DataSource.Factory para ExoPlayer con cache
 */
object ExoPlayerCacheManager {
    
    private const val CACHE_SIZE = 100L * 1024L * 1024L // 100MB como la app original
    private const val CACHE_FOLDER_NAME = "video_cache"
    
    @Volatile
    private var simpleCache: SimpleCache? = null
    
    @Volatile
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    
    /**
     * Obtiene la instancia del SimpleCache (singleton)
     * Como la app original (líneas 95-102)
     */
    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, CACHE_FOLDER_NAME)
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
        }
        return simpleCache!!
    }
    
    /**
     * Obtiene el CacheDataSource.Factory configurado
     * Como la app original (líneas 103-109)
     */
    @Synchronized
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        if (cacheDataSourceFactory == null) {
            val cache = getSimpleCache(context)
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
            
            cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setCacheWriteDataSinkFactory(null) // null = usar default
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return cacheDataSourceFactory!!
    }
    
    /**
     * Libera los recursos del cache
     * Llamar cuando la app se cierra
     */
    @Synchronized
    fun release() {
        simpleCache?.release()
        simpleCache = null
        cacheDataSourceFactory = null
    }
}
