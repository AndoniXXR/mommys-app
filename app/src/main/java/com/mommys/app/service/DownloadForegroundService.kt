package com.mommys.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mommys.app.R
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.util.DownloadNotificationHelper
import com.mommys.app.util.PostDownloader
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * ForegroundService para descargas que sobrevive cuando la app va a segundo plano.
 * Android no puede matar este servicio mientras muestra notificación persistente.
 * Soporta descargas simultáneas (máx 3) y cola ilimitada.
 */
class DownloadForegroundService : Service() {

    companion object {
        private const val TAG = "DownloadFgService"
        private const val SUMMARY_NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "download_service"
        private const val MAX_CONCURRENT = 3

        private val pendingQueue = ConcurrentLinkedQueue<Post>()
        private val activeCount = AtomicInteger(0)
        private val completedCount = AtomicInteger(0)
        private val failedCount = AtomicInteger(0)
        private var totalEnqueued = AtomicInteger(0)
        @Volatile
        private var isProcessing = false

        /**
         * Encolar un post para descarga y arrancar el servicio
         */
        fun enqueueDownload(context: Context, post: Post) {
            pendingQueue.add(post)
            totalEnqueued.incrementAndGet()
            startService(context)
        }

        /**
         * Encolar múltiples posts para descarga batch
         */
        fun enqueueBatch(context: Context, posts: List<Post>) {
            pendingQueue.addAll(posts)
            totalEnqueued.addAndGet(posts.size)
            startService(context)
        }

        private fun startService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun resetCounters() {
            completedCount.set(0)
            failedCount.set(0)
            totalEnqueued.set(0)
            activeCount.set(0)
            isProcessing = false
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(SUMMARY_NOTIFICATION_ID, buildSummaryNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isProcessing) {
            isProcessing = true
            processQueue()
        } else {
            // Service ya corriendo, procesar nuevas items
            processQueue()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        resetCounters()
        super.onDestroy()
    }

    private fun processQueue() {
        serviceScope.launch {
            while (pendingQueue.isNotEmpty() || activeCount.get() > 0) {
                // Lanzar descargas hasta el límite de concurrencia
                while (activeCount.get() < MAX_CONCURRENT) {
                    val post = pendingQueue.poll() ?: break
                    activeCount.incrementAndGet()
                    updateSummaryNotification()

                    launch {
                        try {
                            downloadSingle(post)
                            completedCount.incrementAndGet()
                        } catch (e: Exception) {
                            Log.e(TAG, "Download failed for post ${post.id}", e)
                            failedCount.incrementAndGet()
                        } finally {
                            activeCount.decrementAndGet()
                            updateSummaryNotification()
                        }
                    }
                }

                // Esperar antes de verificar de nuevo
                delay(500)
            }

            // Todas las descargas completadas
            Log.d(TAG, "All downloads finished: ${completedCount.get()} ok, ${failedCount.get()} failed")
            withContext(Dispatchers.Main) {
                stopSelf()
            }
        }
    }

    private suspend fun downloadSingle(post: Post) {
        val prefs = PreferencesManager(applicationContext)
        val fileName = "Post #${post.id}"

        val notificationId = DownloadNotificationHelper.showDownloadStartNotification(
            applicationContext, post.id, fileName
        )

        val result = PostDownloader.downloadPost(
            context = applicationContext,
            post = post,
            prefsManager = prefs,
            callback = object : PostDownloader.DownloadCallback {
                override fun onStart() {
                    Log.d(TAG, "Download started: ${post.id}")
                }

                override fun onProgress(progress: Int) {
                    DownloadNotificationHelper.updateDownloadProgress(
                        applicationContext, notificationId, progress, fileName
                    )
                }

                override fun onSuccess(downloadedFile: PostDownloader.DownloadedFile) {
                    Log.d(TAG, "Download success: ${post.id}")
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Download error: ${post.id} - $error")
                }
            }
        )

        withContext(Dispatchers.Main) {
            if (result != null) {
                DownloadNotificationHelper.showDownloadCompleteNotification(
                    applicationContext, notificationId, result.fileName,
                    result.uri, result.mimeType
                )
            } else {
                DownloadNotificationHelper.showDownloadErrorNotification(
                    applicationContext, notificationId, fileName,
                    getString(R.string.action_download_failed)
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.worker_download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.worker_download_notification_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildSummaryNotification(): Notification {
        val active = activeCount.get()
        val completed = completedCount.get()
        val total = totalEnqueued.get()
        val pending = pendingQueue.size

        val text = when {
            active > 0 -> "Downloading $active file(s)... ($completed/$total done)"
            pending > 0 -> "$pending download(s) pending..."
            else -> "Downloads complete ($completed/$total)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateSummaryNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SUMMARY_NOTIFICATION_ID, buildSummaryNotification())
    }
}
