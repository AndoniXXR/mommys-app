package com.mommys.app

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.mommys.app.data.api.HttpConfig
import com.mommys.app.util.AdaptiveImageController
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Glide module con OkHttp integrado - como wolfstash CustomGlideModule.
 * Usa el mismo DNS IPv4-first y el mismo User-Agent + cookies de Cloudflare que
 * la API, para que las imágenes (también detrás de Cloudflare) pasen igual.
 *
 * Timeouts generosos (10s/30s) para no fallar en móvil inestable, y cache de
 * memoria + disco definidos explícitamente (la app original usaba ~1/7 del heap).
 */
@GlideModule
class CustomGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Memory cache: ~1/7 del heap disponible (igual que la app original con Picasso)
        val memoryCacheSize = Runtime.getRuntime().maxMemory() / 7
        builder.setMemoryCache(LruResourceCache(memoryCacheSize))
        // Disk cache de 250MB para imágenes (el cache de video de ExoPlayer es aparte, 100MB)
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 250L * 1024 * 1024))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)   // era 5s; 10s aguanta 3G/4G inestable
            .readTimeout(30, TimeUnit.SECONDS)      // era 15s
            .dns(HttpConfig.ipv4FirstDns)
            .addInterceptor(HttpConfig.cloudflareHeaderInterceptor())
            .build()

        glideClient = client

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )

        // Ahora que el cliente existe, aplicar la concurrencia adaptativa a la red actual
        AdaptiveImageController.onClientReady()
    }

    override fun isManifestParsingEnabled(): Boolean = false

    companion object {
        /** OkHttpClient de Glide, expuesto para que AdaptiveImageController ajuste su Dispatcher. */
        @Volatile
        var glideClient: OkHttpClient? = null
            private set
    }
}
