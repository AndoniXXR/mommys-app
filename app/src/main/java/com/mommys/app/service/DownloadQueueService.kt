package com.mommys.app.service

import android.content.Context
import android.util.Log
import com.mommys.app.data.db.downloads.AppDownloadsDatabase
import com.mommys.app.data.db.downloads.DownloadItem
import com.mommys.app.data.model.*
import kotlinx.coroutines.*

/**
 * Servicio para procesar la cola de descargas en segundo plano
 * Como si/C4044c.java (m16090s) + j/RunnableC2519l en la app original
 * 
 * IMPORTANTE: Usa Thread estático como el original para mantener
 * consistencia con isRunning() check en DownloadManagerActivity
 */
object DownloadQueueService {
    private const val TAG = "DownloadQueueService"
    
    @Volatile
    private var downloadThread: Thread? = null
    
    /**
     * Verifica si el servicio está corriendo
     * Como C4044c.f26271d.isAlive() en el original
     */
    fun isRunning(): Boolean = downloadThread?.isAlive == true
    
    /**
     * Inicia el servicio de descargas
     * Como C4044c.m16090s() - synchronized para evitar race conditions
     */
    @Synchronized
    fun start(context: Context) {
        val thread = downloadThread
        // Si ya está corriendo, no hacer nada
        if (thread != null && thread.isAlive) {
            Log.d(TAG, "Download service already running")
            return
        }
        
        // Crear y arrancar nuevo thread
        downloadThread = Thread {
            processQueue(context.applicationContext)
        }.apply { 
            name = "DownloadQueueThread"
            start() 
        }
        
        Log.d(TAG, "Download service started")
    }
    
    /**
     * Detiene el servicio de descargas
     * Como interrumpir C4044c.f26271d en el original
     */
    fun stop() {
        downloadThread?.interrupt()
        downloadThread = null
        Log.d(TAG, "Download service stopped")
    }
    
    /**
     * Procesa la cola de descargas
     * Lee items pendientes del Room DB y los encola en el ForegroundService
     */
    private fun processQueue(context: Context) {
        val database = AppDownloadsDatabase.getInstance(context)
        
        Log.d(TAG, "Starting download queue processing")
        
        while (!Thread.currentThread().isInterrupted) {
            try {
                val nextDownload = runBlocking {
                    database.downloadDao().getNextPendingDownload()
                }
                
                if (nextDownload == null) {
                    Log.d(TAG, "No more pending downloads, stopping")
                    break
                }
                
                Log.d(TAG, "Processing download: ${nextDownload.postId}")
                
                val tempPost = createTempPost(nextDownload)
                
                // Encolar en el ForegroundService (sobrevive en segundo plano)
                DownloadForegroundService.enqueueDownload(context, tempPost)
                
                // Eliminar de la cola de Room DB ya que el ForegroundService se encarga
                runBlocking {
                    database.downloadDao().deleteByFileUrl(nextDownload.fileUrl)
                }
                
                Log.d(TAG, "Enqueued download for post ${nextDownload.postId}")
                
                Thread.sleep(200)
                
            } catch (e: InterruptedException) {
                Log.d(TAG, "Download thread interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in download queue", e)
            }
        }
        
        Log.d(TAG, "Download queue processing finished")
    }
    
    /**
     * Crea un Post temporal a partir de DownloadItem para usar PostDownloader
     * Esto permite reutilizar toda la lógica de descarga con MediaStore
     */
    private fun createTempPost(item: DownloadItem): Post {
        return Post(
            id = item.postId,
            createdAt = "",
            updatedAt = null,
            file = FileInfo(
                width = 0,
                height = 0,
                ext = item.fileExt ?: "",
                size = item.fileSize,
                md5 = item.md5,
                url = item.fileUrl
            ),
            preview = PreviewInfo(0, 0, item.thumbUrl),
            sample = SampleInfo(false, null, null, item.thumbUrl),
            score = Score(0, 0, item.score),
            tags = Tags(
                general = emptyList(),
                species = emptyList(),
                character = item.characters?.replace("-", " ")?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                copyright = emptyList(),
                artist = item.artists?.split("_")?.filter { it.isNotBlank() } ?: emptyList(),
                invalid = emptyList(),
                lore = emptyList(),
                meta = emptyList()
            ),
            lockedTags = emptyList(),
            changeSeq = 0L,
            flags = Flags(false, false, false, false, false, false),
            rating = item.rating ?: "q",
            favCount = item.favs,
            sources = emptyList(),
            pools = emptyList(),
            relationships = Relationships(null, false, false, emptyList()),
            approverId = null,
            uploaderId = null,
            description = null,
            commentCount = 0,
            isFavorited = false,
            hasNotes = false
        )
    }
}
