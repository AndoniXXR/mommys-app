package com.mommys.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.mommys.app.data.db.downloads.AppDownloadsDatabase
import com.mommys.app.data.db.downloads.DownloadItem
import com.mommys.app.data.model.*
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.DownloadNotificationHelper
import com.mommys.app.util.PostDownloader
import kotlinx.coroutines.*

/**
 * Servicio para procesar la cola de descargas en segundo plano.
 * Refactorizado para ser un Foreground Service oficial de Android
 * y evitar que el sistema lo mate al salir de la app.
 */
class DownloadQueueService : Service() {
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DownloadQueueService started")
        running = true
        
        // Iniciar en primer plano inmediatamente
        startForegroundService()
        
        // Iniciar procesamiento de cola
        serviceScope.launch {
            processQueue(applicationContext)
            // Al terminar la cola, detener el servicio
            stopSelf()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DownloadQueueService destroyed")
        running = false
        serviceJob.cancel()
    }
    
    private fun startForegroundService() {
        val notification = DownloadNotificationHelper.getForegroundServiceNotification(this)
        val notificationId = 9999 // ID fijo para el servicio
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
    }

    /**
     * Procesa la cola de descargas
     */
    private suspend fun processQueue(context: Context) = coroutineScope {
        val database = AppDownloadsDatabase.getInstance(context)
        val prefs = PreferencesManager(context)
        
        Log.d(TAG, "Starting download queue processing")
        
        while (isActive) {
            try {
                // Obtener siguiente descarga pendiente
                val nextDownload = database.downloadDao().getNextPendingDownload()
                
                if (nextDownload == null) {
                    Log.d(TAG, "No more pending downloads, stopping")
                    break
                }
                
                Log.d(TAG, "Processing download: " + nextDownload.postId)
                
                // Crear Post temporal para usar PostDownloader
                val tempPost = createTempPost(nextDownload)
                
                // Crear notificaci�n para esta descarga
                val notificationId = 2000 + (nextDownload.postId % 10000)
                val fileName = "Post #" + nextDownload.postId
                
                // Descargar usando PostDownloader con callback para progreso
                var success = false
                
                // Usar PostDownloader
                val result = PostDownloader.downloadPost(
                    context,
                    tempPost,
                    prefs,
                    object : PostDownloader.DownloadCallback {
                        override fun onStart() {
                            DownloadNotificationHelper.showDownloadStartNotification(
                                context, 
                                nextDownload.postId, 
                                fileName
                            )
                        }

                        override fun onProgress(progress: Int) {
                            DownloadNotificationHelper.updateDownloadProgress(
                                context, 
                                notificationId, 
                                progress, 
                                fileName
                            )
                        }

                        override fun onSuccess(downloadedFile: PostDownloader.DownloadedFile) {
                            success = true
                            DownloadNotificationHelper.showDownloadCompleteNotification(
                                context, 
                                notificationId, 
                                fileName,
                                downloadedFile.uri,
                                downloadedFile.mimeType
                            )
                        }

                        override fun onError(error: String) {
                            DownloadNotificationHelper.showDownloadErrorNotification(
                                context, 
                                notificationId, 
                                fileName, 
                                error
                            )
                        }
                    }
                )
                
                if (result != null) success = true;

                if (success) {
                    // Eliminar de la cola despu�s de descarga exitosa
                    database.downloadDao().deleteByFileUrl(nextDownload.fileUrl)
                    Log.d(TAG, "Download completed: " + nextDownload.postId)
                } else {
                    // Marcar con error
                    database.downloadDao().update(
                        nextDownload.copy(error = "Download failed")
                    )
                    Log.e(TAG, "Download failed: " + nextDownload.postId)
                }
                
                // Peque�a pausa entre descargas
                delay(500)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in download loop", e)
                delay(1000)
            }
        }
    }
    
    private fun createTempPost(item: DownloadItem): Post {
        // Crear un objeto Post b�sico con la info disponible en DownloadItem
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

    companion object {
        private const val TAG = "DownloadQueueService"
        
        @Volatile
        var running = false
            internal set
            
        fun isRunning(): Boolean = running
        
        /**
         * Inicia el servicio de descargas en primer plano
         */
        fun start(context: Context) {
            val intent = Intent(context, DownloadQueueService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * Detiene el servicio de descargas
         */
        fun stop(context: Context) {
            val intent = Intent(context, DownloadQueueService::class.java)
            context.stopService(intent)
        }
    }
}
