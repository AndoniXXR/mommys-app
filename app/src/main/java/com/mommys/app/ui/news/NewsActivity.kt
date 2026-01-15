package com.mommys.app.ui.news

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R

/**
 * NewsActivity - Shows news from app developer
 * Based on se/zepiwolf/tws/NewsActivity.java from original app
 * 
 * Note: The original app uses Firebase Remote Config to fetch news.
 * This implementation shows a placeholder or can be extended to fetch news from a remote source.
 */
class NewsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_USER = "user_preferences"
        private const val KEY_NEWS_VERSION = "news_v"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var txtNews: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        txtNews = findViewById(R.id.txtNews)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Load and display news
        loadNews()
    }

    private fun loadNews() {
        // In the original app, news is fetched from Firebase Remote Config:
        // c.a().b("news") and c.a().b("news_version")
        // 
        // For now, we show a placeholder message.
        // This can be extended to:
        // 1. Use Firebase Remote Config
        // 2. Fetch from a custom API endpoint
        // 3. Load from SharedPreferences if cached
        
        val newsText = """
            Welcome to Mommys App!
            
            This is where app news and announcements will appear.
            
            Stay tuned for updates!
        """.trimIndent()

        txtNews.text = newsText

        // Save news version to preferences (original app behavior)
        // This is used to track if user has seen the latest news
        val prefs = getSharedPreferences(PREFS_USER, MODE_PRIVATE)
        prefs.edit().putInt(KEY_NEWS_VERSION, 1).apply()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
