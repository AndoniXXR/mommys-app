package com.mommys.app.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mommys.app.MommysApplication
import com.mommys.app.R

/**
 * WebView Activity para manejar Cloudflare y Cookies
 * Basado en WebViewActivity.java de la app original
 * 
 * Esta actividad abre el sitio web e926/e621 para:
 * 1. Resolver captchas de Cloudflare
 * 2. Obtener cookies necesarias para la API
 * 
 * El usuario puede navegar y resolver captchas, y al presionar "Done",
 * las cookies se guardan para uso posterior en las llamadas API.
 */
class CookieWebViewActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var btnDone: Button
    
    private val prefs by lazy { MommysApplication.getInstance().preferencesManager }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cookie_webview)
        
        webView = findViewById(R.id.webView)
        btnDone = findViewById(R.id.btnDone)
        
        setupWebView()
        setupDoneButton()
        
        // Cargar el sitio apropiado basado en la configuración del host
        val url = if (prefs.useE621()) "https://e621.net" else "https://e926.net"
        webView.loadUrl(url)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = WebViewClient()
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // User-Agent exacto de la app original
            userAgentString = "The Wolf's Stash v2 beta-4.15.2 (by ZepiWolf)"
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // Habilitar cookies
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }
    }
    
    private fun setupDoneButton() {
        btnDone.setOnClickListener {
            saveCookiesAndFinish()
        }
    }
    
    /**
     * Guarda las cookies del WebView para uso en las llamadas API
     * Como la app original en com/applovin/mediation/nativeAds/a.java case 5
     */
    private fun saveCookiesAndFinish() {
        try {
            val cookieManager = CookieManager.getInstance()
            
            // Obtener URL del host actual
            val hostUrl = if (prefs.useE621()) "https://e621.net" else "https://e926.net"
            
            // Obtener cookies para el host actual
            var cookies = cookieManager.getCookie(hostUrl) ?: ""
            
            // Formatear cookies como en la app original
            // .replace(" ", "").replace("{", "").replace("}", "").replace(",", ";")
            cookies = cookies
                .replace(" ", "")
                .replace("{", "")
                .replace("}", "")
                .replace(",", ";")
            
            // Guardar cookies en preferencias (usando la misma key que la app original)
            prefs.setCookies(cookies)
            
            // Sincronizar cookies
            cookieManager.flush()
            
            Toast.makeText(this, R.string.cookies_saved, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cookies_save_error, Toast.LENGTH_SHORT).show()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
