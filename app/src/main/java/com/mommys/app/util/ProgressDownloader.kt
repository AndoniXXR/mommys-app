package com.mommys.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Helper para descargar contenido con tracking de progreso y caché local
 * Similar a la implementación original ri/a.java con caché MD5
 */
object ProgressDownloader {
    
    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(originalResponse.body?.let { body ->
                    ProgressResponseBody(body, progressListeners[chain.request().url.toString()])
                })
                .build()
        }
        .build()
    
    private val progressListeners = mutableMapOf<String, ProgressListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cacheDir: File? = null

    /**
     * Inicializar el directorio de caché. Llamar una vez desde Application o Activity.
     */
    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "img_cache").apply { mkdirs() }
    }
    
    interface ProgressListener {
        fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean)
        fun onComplete(data: ByteArray?)
        fun onError(exception: Exception)
    }

    /**
     * Genera nombre de archivo de caché basado en MD5 de la URL (como ri/a.c() original)
     */
    private fun getCacheFile(url: String): File? {
        val dir = cacheDir ?: return null
        val md5 = MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, md5)
    }

    /**
     * Descarga contenido con tracking de progreso. Sirve desde caché si existe.
     * @param url URL del contenido a descargar
     * @param listener Callback para reportar progreso y completado
     * @return Call que puede cancelarse con call.cancel() (null si se sirvió desde caché)
     */
    fun download(url: String, listener: ProgressListener): Call? {
        // Verificar caché local primero (como ri/a.c() original)
        val cacheFile = getCacheFile(url)
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            mainHandler.post {
                try {
                    val data = cacheFile.readBytes()
                    listener.onComplete(data)
                } catch (e: Exception) {
                    // Caché corrupta, eliminar y descargar
                    cacheFile.delete()
                    download(url, listener)
                }
            }
            return null
        }

        progressListeners[url] = listener
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                progressListeners.remove(url)
                if (call.isCanceled()) return
                mainHandler.post {
                    listener.onError(e)
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                progressListeners.remove(url)
                if (call.isCanceled()) {
                    response.close()
                    return
                }
                if (!response.isSuccessful) {
                    mainHandler.post {
                        listener.onError(IOException("Unexpected response code: ${response.code}"))
                    }
                    return
                }
                
                val data = response.body?.bytes()
                if (call.isCanceled()) return

                // Guardar en caché local
                if (data != null) {
                    try {
                        cacheFile?.writeBytes(data)
                    } catch (_: Exception) { /* Si falla el caché, no es crítico */ }
                }

                mainHandler.post {
                    listener.onComplete(data)
                }
            }
        })
        return call
    }
    
    /**
     * ResponseBody que intercepta y reporta progreso de lectura
     */
    private class ProgressResponseBody(
        private val responseBody: ResponseBody,
        private val progressListener: ProgressListener?
    ) : ResponseBody() {
        
        private var bufferedSource: BufferedSource? = null
        
        override fun contentType(): MediaType? = responseBody.contentType()
        
        override fun contentLength(): Long = responseBody.contentLength()
        
        override fun source(): BufferedSource {
            if (bufferedSource == null) {
                bufferedSource = ProgressSource(responseBody.source(), responseBody.contentLength()).buffer()
            }
            return bufferedSource!!
        }
        
        private inner class ProgressSource(
            delegate: Source,
            private val contentLength: Long
        ) : ForwardingSource(delegate) {
            
            private var totalBytesRead = 0L
            private val mainHandler = Handler(Looper.getMainLooper())
            
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                
                if (bytesRead != -1L) {
                    totalBytesRead += bytesRead
                }
                
                val done = bytesRead == -1L
                
                // Reportar progreso en el hilo principal
                mainHandler.post {
                    progressListener?.onProgress(totalBytesRead, contentLength, done)
                }
                
                return bytesRead
            }
        }
    }
}
