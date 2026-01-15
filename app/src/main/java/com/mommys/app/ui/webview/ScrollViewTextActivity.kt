package com.mommys.app.ui.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ScrollViewTextActivity - Shows Terms of Service or Privacy Policy in a WebView
 * Based on se/zepiwolf/tws/ScrollViewTextActivity.java from original app
 */
class ScrollViewTextActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "e"
        const val TYPE_TERMS_OF_SERVICE = 1
        const val TYPE_PRIVACY_POLICY = 2
        const val TYPE_LICENSES = 3
        
        private const val TERMS_URL = "https://e621.net/static/terms_of_service"
        private const val PRIVACY_URL = "https://e621.net/static/privacy"
        private const val LICENSES_URL = "https://e621.net/static/site_map"  // Placeholder - shows site info
        
        private const val PREFS_USER_INFO = "user_info"
        private const val KEY_ACCEPTED_WHEN = "accepted_when"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var webView: WebView
    private lateinit var txtAcceptedDate: TextView
    private lateinit var divider: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scroll_view_text)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)
        txtAcceptedDate = findViewById(R.id.txtAcceptedDate)
        divider = findViewById(R.id.divider)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup WebView
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true

        // Get extras
        val extras = intent.extras
        if (extras == null) {
            finish()
            return
        }

        // Determine type and load appropriate URL
        val type = extras.getInt(EXTRA_TYPE, TYPE_TERMS_OF_SERVICE)
        val title: String
        val url: String

        when (type) {
            TYPE_TERMS_OF_SERVICE -> {
                title = getString(R.string.terms_of_service)
                url = TERMS_URL
            }
            TYPE_PRIVACY_POLICY -> {
                title = getString(R.string.privacy_policy)
                url = PRIVACY_URL
            }
            TYPE_LICENSES -> {
                title = getString(R.string.licenses_title)
                url = LICENSES_URL
                // Hide accepted date for licenses
                txtAcceptedDate.visibility = View.GONE
                divider.visibility = View.GONE
            }
            else -> {
                title = getString(R.string.terms_of_service)
                url = TERMS_URL
            }
        }

        supportActionBar?.title = title
        webView.loadUrl(url)

        // Show accepted date if available
        val acceptedWhen = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
            .getLong(KEY_ACCEPTED_WHEN, -1L)

        if (acceptedWhen > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
            val formattedDate = dateFormat.format(Date(acceptedWhen))
            txtAcceptedDate.text = getString(R.string.scroll_date_accepted, formattedDate)
        } else {
            txtAcceptedDate.visibility = View.GONE
            divider.visibility = View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
