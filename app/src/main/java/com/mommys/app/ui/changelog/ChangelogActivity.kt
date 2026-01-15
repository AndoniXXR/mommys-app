package com.mommys.app.ui.changelog

import android.os.Bundle
import android.text.Html
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R

class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val txtContent = findViewById<TextView>(R.id.txtContent)
        
        // Load changelog content from strings
        val changelogText = getString(R.string.changelog_content)
        
        // Parse HTML formatting (bold tags, etc.)
        txtContent.text = Html.fromHtml(changelogText, Html.FROM_HTML_MODE_COMPACT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
