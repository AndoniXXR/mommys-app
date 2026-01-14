package com.mommys.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mommys.app.R

/**
 * Helper para manejar notificaciones de descarga
 * Similar a la implementación de la app original
 */
object DownloadNotificationHelper {
    
    private const val CHANNEL_ID = "Downloads"
    private const val NOTIFICATION_ID_BASE = 1000
    
    /**
     * Crear el canal de notificaciones (requerido para Android 8+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.worker_download_notification_channel_name)
            val description = context.getString(R.string.worker_download_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Verificar si tenemos permiso para mostrar notificaciones
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Mostrar notificación de inicio de descarga
     */
    fun showDownloadStartNotification(context: Context, postId: Int, fileName: String): Int {
        if (!hasNotificationPermission(context)) return -1
        
        createNotificationChannel(context)
        
        val notificationId = NOTIFICATION_ID_BASE + postId
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true) // Indeterminate progress
            .setAutoCancel(false)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Sin permiso
            return -1
        }
        
        return notificationId
    }
    
    /**
     * Actualizar progreso de la notificación
     */
    fun updateDownloadProgress(context: Context, notificationId: Int, progress: Int, fileName: String) {
        if (!hasNotificationPermission(context) || notificationId < 0) return
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setAutoCancel(false)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Sin permiso
        }
    }
    
    /**
     * Mostrar notificación de descarga completada
     * @param fileUri URI del archivo descargado para abrir en la galería
     * @param mimeType Tipo MIME del archivo
     */
    fun showDownloadCompleteNotification(context: Context, notificationId: Int, fileName: String, fileUri: String? = null, mimeType: String? = null) {
        Log.d("DownloadNotification", "showDownloadCompleteNotification called: id=$notificationId, fileName=$fileName, uri=$fileUri")
        
        val actualNotificationId = if (notificationId >= 0) notificationId else NOTIFICATION_ID_BASE
        
        // Primero cancelar la notificación de progreso existente
        try {
            NotificationManagerCompat.from(context).cancel(actualNotificationId)
            Log.d("DownloadNotification", "Cancelled previous notification")
        } catch (e: Exception) {
            Log.w("DownloadNotification", "Failed to cancel: ${e.message}")
        }
        
        if (!hasNotificationPermission(context)) {
            Log.w("DownloadNotification", "No notification permission")
            return
        }
        
        Log.d("DownloadNotification", "Using actualNotificationId=$actualNotificationId")
        
        // Crear PendingIntent para abrir el archivo en la galería
        val contentIntent = if (fileUri != null && mimeType != null) {
            try {
                val uri = android.net.Uri.parse(fileUri)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                PendingIntent.getActivity(
                    context,
                    actualNotificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } catch (e: Exception) {
                Log.w("DownloadNotification", "Failed to create view intent: ${e.message}")
                null
            }
        } else {
            null
        }
        
        // Crear nueva notificación con mayor prioridad
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(context.getString(R.string.download_notification_complete))
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
        
        // Agregar intent si existe
        contentIntent?.let { builder.setContentIntent(it) }
        
        val notification = builder.build()
        
        try {
            NotificationManagerCompat.from(context).notify(actualNotificationId, notification)
            Log.d("DownloadNotification", "Notification sent successfully")
        } catch (e: SecurityException) {
            Log.e("DownloadNotification", "SecurityException: ${e.message}")
        }
    }
    
    /**
     * Mostrar notificación de error en la descarga
     */
    fun showDownloadErrorNotification(context: Context, notificationId: Int, fileName: String, error: String) {
        if (!hasNotificationPermission(context)) {
            // Si no hay permiso, al menos cancelar la notificación anterior si existe
            if (notificationId >= 0) {
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
            return
        }
        
        val actualNotificationId = if (notificationId >= 0) notificationId else NOTIFICATION_ID_BASE
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_error_outline)
            .setContentTitle(context.getString(R.string.download_notification_error))
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$fileName\n$error"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(actualNotificationId, notification)
        } catch (e: SecurityException) {
            // Sin permiso
        }
    }
    
    /**
     * Cancelar una notificación
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId >= 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
