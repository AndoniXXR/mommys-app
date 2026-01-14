package com.mommys.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mommys.app.R
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Descargador de posts en batch con notificaciones individuales
 * Como la app original:
 * - Toast inicial: "Download started! Check the notification for progress."
 * - Cada descarga tiene su propia notificación con progreso
 * - Al completar cada una, la notificación permite abrir en galería
 * - Toast final: "Done - downloaded X posts!"
 * 
 * Similar a di/x.java case 0 en app original
 */
object BatchDownloader {
    
    private const val TAG = "BatchDownloader"
    private const val BATCH_CHANNEL_ID = "batch_downloads"
    private const val COMPLETE_CHANNEL_ID = "download_complete"
    private const val NOTIFICATION_ID_BASE = 1000
    
    /**
     * Iniciar descarga batch de posts
     * Similar a new Thread(new x(this, 0)).start() en MainActivity.I()
     */
    fun startBatchDownload(
        context: Context,
        posts: List<Post>,
        prefsManager: PreferencesManager
    ) {
        if (posts.isEmpty()) {
            Toast.makeText(context, R.string.no_posts_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Crear canal de notificación
        createBatchNotificationChannel(context)
        
        // Mostrar toast de inicio (como la app original: worker_download_start)
        Toast.makeText(
            context, 
            R.string.worker_download_start, 
            Toast.LENGTH_LONG
        ).show()
        
        // Iniciar descarga en background
        CoroutineScope(Dispatchers.IO).launch {
            downloadPosts(context, posts, prefsManager)
        }
    }
    
    /**
     * Crear canal de notificación para descargas batch
     */
    private fun createBatchNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para progreso de descarga (sin sonido)
            val progressName = context.getString(R.string.worker_download_notification_channel_name)
            val progressDescription = context.getString(R.string.worker_download_notification_channel_description)
            
            val progressChannel = NotificationChannel(BATCH_CHANNEL_ID, progressName, NotificationManager.IMPORTANCE_LOW).apply {
                description = progressDescription
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(progressChannel)
            
            // Canal para completado (con prioridad normal para que se vea)
            val completeChannel = NotificationChannel(COMPLETE_CHANNEL_ID, "Download Complete", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications when downloads are complete"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(completeChannel)
        }
    }
    
    /**
     * Proceso de descarga con notificación individual para cada post
     * Como la app original - cada descarga tiene su propia notificación
     */
    private suspend fun downloadPosts(
        context: Context,
        posts: List<Post>,
        prefsManager: PreferencesManager
    ) {
        var downloaded = 0
        
        for (post in posts) {
            // Usar módulo para mantener IDs de notificación en rango razonable
            val notificationId = NOTIFICATION_ID_BASE + (post.id % 10000)
            val fileName = PostDownloader.generateFileName(post, prefsManager) + 
                          PostDownloader.getFileExtension(post)
            
            Log.d(TAG, "Starting download for post ${post.id}, notificationId=$notificationId")
            
            try {
                // Mostrar notificación de progreso para este post
                showIndividualProgressNotification(context, notificationId, fileName, 0)
                
                // Descargar - sin callback, manejaremos la notificación directamente
                val result = PostDownloader.downloadPost(
                    context = context,
                    post = post,
                    prefsManager = prefsManager,
                    callback = object : PostDownloader.DownloadCallback {
                        override fun onStart() {}
                        
                        override fun onProgress(progress: Int) {
                            // Actualizar progreso en la notificación
                            showIndividualProgressNotification(context, notificationId, fileName, progress)
                        }
                        
                        override fun onSuccess(downloadedFile: PostDownloader.DownloadedFile) {
                            // No hacemos nada aquí, lo manejamos abajo con el result
                        }
                        
                        override fun onError(error: String) {
                            // No hacemos nada aquí, lo manejamos abajo
                        }
                    }
                )
                
                // Después de que downloadPost retorne, actualizar la notificación
                if (result != null) {
                    downloaded++
                    Log.d(TAG, "Post ${post.id} downloaded successfully, showing complete notification")
                    Log.d(TAG, "Result: fileName=${result.fileName}, uri=${result.uri}, mimeType=${result.mimeType}")
                    // Mostrar notificación de completado que abre en galería
                    showIndividualCompleteNotification(
                        context, 
                        notificationId, 
                        result.fileName,
                        result.uri,
                        result.mimeType
                    )
                    Log.d(TAG, "Complete notification shown for ${post.id}")
                } else {
                    Log.e(TAG, "Post ${post.id} download returned null result")
                    // Descarga falló
                    showIndividualErrorNotification(context, notificationId, fileName, "Download failed")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                showIndividualErrorNotification(context, notificationId, fileName, e.message ?: "Error")
            }
        }
        
        // Mostrar toast final (como la app original: worker_download_done)
        withContext(Dispatchers.Main) {
            val message = context.getString(R.string.worker_download_done, downloaded)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Mostrar notificación de progreso individual para un post
     */
    private fun showIndividualProgressNotification(
        context: Context,
        notificationId: Int,
        fileName: String,
        progress: Int
    ) {
        if (!DownloadNotificationHelper.hasNotificationPermission(context)) return
        
        val notification = NotificationCompat.Builder(context, BATCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0)
            .setAutoCancel(false)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Sin permiso
        }
    }
    
    /**
     * Mostrar notificación de descarga completada que permite abrir en galería
     * Como la app original - al tocar abre el archivo descargado
     */
    private fun showIndividualCompleteNotification(
        context: Context,
        notificationId: Int,
        fileName: String,
        fileUri: String,
        mimeType: String
    ) {
        Log.d(TAG, "showIndividualCompleteNotification called for notificationId=$notificationId, fileName=$fileName")
        
        if (!DownloadNotificationHelper.hasNotificationPermission(context)) {
            Log.e(TAG, "No notification permission!")
            return
        }
        
        val notificationManager = NotificationManagerCompat.from(context)
        
        // Cancelar la notificación de progreso primero
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled progress notification $notificationId")
        
        // Crear intent para abrir en galería
        val viewIntent = try {
            val uri = android.net.Uri.parse(fileUri)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating view intent: ${e.message}")
            null
        }
        
        val pendingIntent = viewIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        // Usar canal diferente y ID diferente para que se muestre como nueva notificación
        val completeNotificationId = notificationId + 100000
        
        val builder = NotificationCompat.Builder(context, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(context.getString(R.string.download_notification_complete))
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
        
        pendingIntent?.let { builder.setContentIntent(it) }
        
        try {
            NotificationManagerCompat.from(context).notify(completeNotificationId, builder.build())
            Log.d(TAG, "Complete notification posted successfully for $completeNotificationId")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException posting notification: ${e.message}")
        }
    }
    
    /**
     * Mostrar notificación de error para un post específico
     */
    private fun showIndividualErrorNotification(
        context: Context,
        notificationId: Int,
        fileName: String,
        error: String
    ) {
        if (!DownloadNotificationHelper.hasNotificationPermission(context)) return
        
        val notification = NotificationCompat.Builder(context, BATCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_error_outline)
            .setContentTitle(context.getString(R.string.download_notification_error))
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$fileName\n$error"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Sin permiso
        }
    }
}
