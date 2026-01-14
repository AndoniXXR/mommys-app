package com.mommys.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mommys.app.service.FollowingJobService

/**
 * Receiver to reschedule jobs after device boot
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule following job
            FollowingJobService.schedule(context)
        }
    }
}
