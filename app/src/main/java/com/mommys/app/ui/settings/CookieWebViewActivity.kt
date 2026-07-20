package com.mommys.app.ui.settings

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.data.api.CloudflareCookieJar
import com.mommys.app.data.api.HttpConfig

/**
 * WebView Activity para manejar Cloudflare y Cookies.
 *
 * Mejoras respecto a la versión anterior (y alineadas con el comportamiento de
 * la app original Wolf's Stash):
 *
 * 1. **Auto-captura de cookies cuando termina el challenge**: antes dependíamos
 *    exclusivamente del botón "Done", y el usuario lo pulsaba a menudo ANTES de
 *    que Cloudflare seteara cf_clearance (que llega tras la recarga final del
 *    challenge JS). Ahora, cada onPageFinished captura las cookies y avisa al
 *    usuario cuando detecta que cf_clearance ya está disponible.
 *
 * 2. **Reload del CookieJar + invalidación de caches al cerrar**: tras guardar
 *    cookies nuevas se llama a CloudflareCookieJar.reload() y se limpia la
 *    cache de disco de Glide para que las imágenes bloqueadas se reintenten con
 *    las cookies nuevas en la próxima carga.
 *
 * El usuario mantiene el botón "Done" para cerrar manualmente; el comportamiento
 * automático es adicional, no sustitutivo.
 */
class CookieWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnDone: Button
    private lateinit var progressBar: ProgressBar

    private val prefs by lazy { MommysApplication.getInstance().preferencesManager }

    /** Host base según la preferencia useE621. */
    private val hostUrl: String
        get() = if (prefs.useE621()) "https://e621.net" else "https://e926.net"

    /** True si en algún momento del challenge se capturó cf_clearance. */
    private var capturedClearance = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cookie_webview)

        webView = findViewById(R.id.webView)
        btnDone = findViewById(R.id.btnDone)

        // ProgressBar opcional: si no existe en el layout, se ignora.
        progressBar = (findViewById<ProgressBar>(R.id.progressBar)) ?: ProgressBar(this)

        setupWebView()
        setupDoneButton()
        setupBackNavigation()

        webView.loadUrl(hostUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE

                // Capturar cookies en CADA carga terminada. Cloudflare completa
                // el challenge con una recarga final que es cuando setea
                // cf_clearance; hacerlo en cada onPageFinished asegura que no
                // nos lo perdamos.
                saveCookiesToPrefs(silent = true)

                // Avisar al usuario si ya tenemos cf_clearance (challenge resuelto).
                if (!capturedClearance && hasClearanceCookie()) {
                    capturedClearance = true
                    Toast.makeText(
                        this@CookieWebViewActivity,
                        getString(R.string.cookies_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Permitir que el WebView maneje sus propios redirects (necesario para
            // que Cloudflare complete el challenge internamente).
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // UA canónico de la app (debe coincidir con el de la API).
            // Es estable entre versiones (ver HttpConfig.userAgent()).
            userAgentString = HttpConfig.userAgent()
            cacheMode = WebSettings.LOAD_DEFAULT

            // Habilitar cookies (por defecto ya lo están, pero lo dejamos explícito).
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }
    }

    private fun setupDoneButton() {
        btnDone.setOnClickListener {
            saveCookiesToPrefs(silent = false)
            finishChallenge()
        }
    }

    /**
     * Guarda las cookies del WebView en preferencias, en el formato esperado
     * por [com.mommys.app.data.api.HttpConfig.cookies].
     *
     * @param silent si true, no muestra Toast (usado en onPageFinished).
     */
    private fun saveCookiesToPrefs(silent: Boolean) {
        try {
            val cookieManager = CookieManager.getInstance()
            var cookies = cookieManager.getCookie(hostUrl) ?: ""

            // Formatear como la app original (y como el código previo):
            // .replace(" ", "").replace("{", "").replace("}", "").replace(",", ";")
            cookies = cookies
                .replace(" ", "")
                .replace("{", "")
                .replace("}", "")
                .replace(",", ";")

            prefs.setCookies(cookies)
            cookieManager.flush()

            if (!silent) {
                Toast.makeText(this, R.string.cookies_saved, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (!silent) {
                Toast.makeText(this, R.string.cookies_save_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * ¿Tiene el WebView la cookie cf_clearance para el host actual?
     * Indica que el challenge de Cloudflare se completó.
     */
    private fun hasClearanceCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie(hostUrl) ?: ""
        return cookies.contains("cf_clearance=")
    }

    /**
     * Cierra la activity tras invalidar caches y recargar el CookieJar para que
     * la siguiente petición API use las cookies nuevas.
     */
    private fun finishChallenge() {
        // 1) Recargar el CookieJar para que las cookies nuevas estén disponibles
        //    inmediatamente en OkHttp sin esperar a reconstruir el cliente.
        CloudflareCookieJar.get().reload()

        // 2) Limpiar la cache de disco de Glide para que las imágenes que quedaron
        //    bloqueadas (403 cacheado o error) se reintenten con las cookies nuevas.
        try {
            Glide.get(applicationContext).clearDiskCache()
        } catch (e: Exception) {
            // No crítico
        }

        finish()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
