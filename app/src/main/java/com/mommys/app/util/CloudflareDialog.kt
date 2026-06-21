package com.mommys.app.util

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mommys.app.R
import com.mommys.app.ui.settings.CookieWebViewActivity
import kotlinx.coroutines.launch

/**
 * Muestra automáticamente un diálogo cuando Cloudflare bloquea la API,
 * ofreciendo abrir [CookieWebViewActivity] para resolver el captcha.
 *
 * Reutiliza los strings `page_error_cloudflare_dialog_*` ya existentes.
 * Garantiza un solo diálogo a la vez por activity y respeta el ciclo de vida.
 */
fun AppCompatActivity.observeCloudflareBlocks() {
    var dialogShown = false
    lifecycleScope.launch {
        CloudflareBlocker.events.collect {
            if (dialogShown || isFinishing || isDestroyed) return@collect
            dialogShown = true
            AlertDialog.Builder(this@observeCloudflareBlocks)
                .setTitle(R.string.page_error_cloudflare_dialog_title)
                .setMessage(R.string.page_error_cloudflare_dialog_message)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(this@observeCloudflareBlocks, CookieWebViewActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener { dialogShown = false }
                .show()
        }
    }
}
