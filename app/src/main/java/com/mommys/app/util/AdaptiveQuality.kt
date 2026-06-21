package com.mommys.app.util

import com.mommys.app.MommysApplication
import com.mommys.app.data.model.Post

/**
 * Adaptive loading (Fase 2): decide la calidad/URL de imagen según la red.
 *
 * - **Grid/thumbnails**: siempre `preview` (o `sample`), nunca `file` full-res.
 * - **Vista del post**: en WiFi carga full-res; en red móvil (o con Data Saver)
 *   carga `sample` (mucho menos dato y más rápido).
 *
 * Las DESCARGAS explícitas (botón descargar) NO pasan por aquí: siempre son
 * full-res vía [com.mommys.app.util.PostDownloader] (decisión del usuario).
 */
object AdaptiveQuality {

    /**
     * URL para thumbnails del grid: preview, con fallback a sample.
     * Nunca devuelve `file.url` (full-res) — un thumbnail no necesita 4K.
     */
    fun thumbUrl(post: Post): String? = post.preview.url ?: post.sample?.url

    /**
     * Si la vista del post debe cargar la imagen full-res (`file.url`).
     *
     * Devuelve `false` (usar `sample`) cuando:
     * - el usuario activó Data Saver (`postDataSaver`), o
     * - la red actual es móvil (`isMetered`) o lenta (`isSlow`, 2G/EDGE).
     *
     * En WiFi/rápida devuelve `postLoadHq` (por defecto `true`).
     * La descarga explícita no se ve afectada.
     */
    fun shouldLoadFullRes(): Boolean {
        val app = MommysApplication.getInstance()
        val state = app.networkMonitor.getCurrentState()
        // Data Saver manual del usuario → forzar modo ahorro
        if (app.preferencesManager.postDataSaver) return false
        // Red móvil o lenta → no full-res (usar sample)
        if (state.isMetered || state.isSlow) return false
        // WiFi/rápida → full-res si el usuario quiere HQ
        return app.preferencesManager.postLoadHq
    }
}
