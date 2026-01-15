package com.mommys.app.ui.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager

/**
 * WebViewActivity - Generic WebView to display e621/e926
 * Based on se/zepiwolf/tws/WebViewActivity.java from original app
 */
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnDone: Button
    private lateinit var titleText: TextView
    private var baseUrl: String = ""

    companion object {
        private const val USER_AGENT = "Mommys App v1.0.0"
        const val EXTRA_URL = "url"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        // Initialize views
        webView = findViewById(R.id.webView)
        btnDone = findViewById(R.id.btnDone)
        titleText = findViewById(R.id.title)

        // Setup WebView
        webView.webViewClient = WebViewClient()
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.userAgentString = USER_AGENT

        // Determine base URL based on SFW mode
        val prefs = PreferencesManager(this)
        baseUrl = if (prefs.safeMode) {
            "https://e926.net"
        } else {
            "https://e621.net"
        }

        // Check for URL in intent, otherwise use base URL
        val intentUrl = intent.getStringExtra(EXTRA_URL)
        val urlToLoad = intentUrl ?: baseUrl
        
        // Load the URL
        webView.loadUrl(urlToLoad)

        // Done button closes the activity
        btnDone.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
