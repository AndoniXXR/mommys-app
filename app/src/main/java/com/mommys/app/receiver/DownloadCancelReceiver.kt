package com.mommys.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mommys.app.service.DownloadQueueService

/**
 * BroadcastReceiver para manejar la cancelación de descargas
 * cuando el usuario descarta la notificación
 */
class DownloadCancelReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.mommys.app.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_POST_ID = "post_id"
        private const val TAG = "DownloadCancelReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        when (intent.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                val postId = intent.getIntExtra(EXTRA_POST_ID, -1)
                if (postId != -1) {
                    Log.d(TAG, "Usuario canceló descarga de post $postId")
                    
                    // Detener el servicio de descargas si está corriendo
                    // En una implementación más compleja, marcaríamos solo esta descarga como cancelada
                    DownloadQueueService.stop()
                    
                    // TODO: Si implementas cancelación granular, aquí marcarías el post específico
                    // como cancelado en la base de datos
                }
            }
        }
    }
}
