package com.mommys.app.util

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

/**
 * Helper para descargar contenido con tracking de progreso
 * Similar a la implementación original de la app decompilada
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
    
    interface ProgressListener {
        fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean)
        fun onComplete(data: ByteArray?)
        fun onError(exception: Exception)
    }
    
    /**
     * Descarga contenido con tracking de progreso
     * @param url URL del contenido a descargar
     * @param listener Callback para reportar progreso y completado
     */
    fun download(url: String, listener: ProgressListener) {
        progressListeners[url] = listener
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                progressListeners.remove(url)
                mainHandler.post {
                    listener.onError(e)
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                progressListeners.remove(url)
                if (!response.isSuccessful) {
                    mainHandler.post {
                        listener.onError(IOException("Unexpected response code: ${response.code}"))
                    }
                    return
                }
                
                val data = response.body?.bytes()
                mainHandler.post {
                    listener.onComplete(data)
                }
            }
        })
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
