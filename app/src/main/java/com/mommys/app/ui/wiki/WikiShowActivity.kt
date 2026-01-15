package com.mommys.app.ui.wiki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.api.ApiService
import com.mommys.app.data.preferences.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Activity para mostrar una página Wiki de e621
 * Similar a WikiShowActivity.java de la app original
 */
class WikiShowActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_WIKI_TITLE = "wiki_title"
        const val EXTRA_TAG = "tag"  // Alias for EXTRA_WIKI_TITLE
    }
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var txtTitle: TextView
    private lateinit var txtBody: TextView
    private lateinit var txtOtherNames: TextView
    private lateinit var txtMeta: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var txtEmptyMessage: TextView
    
    private lateinit var prefs: PreferencesManager
    private var wikiTitle: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wiki_show)
        
        prefs = PreferencesManager(this)
        
        initViews()
        
        // Obtener título del wiki desde intent o deep link
        wikiTitle = getWikiTitleFromIntent()
        
        if (wikiTitle.isNullOrEmpty()) {
            showError(getString(R.string.wiki_error_no_title))
            return
        }
        
        toolbar.title = getString(R.string.wiki_show_title, wikiTitle)
        
        loadWikiPage(wikiTitle!!)
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        progressBar = findViewById(R.id.progressBar)
        txtTitle = findViewById(R.id.txtTitle)
        txtBody = findViewById(R.id.txtBody)
        txtOtherNames = findViewById(R.id.txtOtherNames)
        txtMeta = findViewById(R.id.txtMeta)
        emptyState = findViewById(R.id.emptyState)
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage)
        
        // Enable clickable links
        txtBody.movementMethod = LinkMovementMethod.getInstance()
    }
    
    private fun getWikiTitleFromIntent(): String? {
        // First check extras (when launched from inside the app)
        intent.getStringExtra(EXTRA_WIKI_TITLE)?.let {
            return it
        }
        
        // Also check EXTRA_TAG (alias)
        intent.getStringExtra(EXTRA_TAG)?.let {
            return it
        }
        
        // Check for deep link (e621.net/wiki_pages/title)
        val action = intent.action
        val data: Uri? = intent.data
        
        if (Intent.ACTION_VIEW == action && data != null) {
            // Get the last path segment as wiki title
            return data.lastPathSegment
        }
        
        return null
    }
    
    private fun loadWikiPage(title: String) {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val baseUrl = if (prefs.useE621()) ApiService.BASE_URL_E621 else ApiService.BASE_URL_E926
                val apiService = ApiClient.getApiService(baseUrl)
                val response = apiService.getWikiPage(title)
                
                progressBar.visibility = View.GONE
                
                if (response.isSuccessful && response.body() != null) {
                    val wiki = response.body()!!
                    displayWikiPage(wiki)
                } else {
                    val errorMessage = when (response.code()) {
                        404 -> getString(R.string.wiki_not_found, title)
                        else -> getString(R.string.wiki_error_loading)
                    }
                    showError(errorMessage)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showError(getString(R.string.wiki_error_loading) + "\n" + e.message)
            }
        }
    }
    
    private fun displayWikiPage(wiki: com.mommys.app.data.model.WikiPage) {
        txtTitle.text = wiki.title.replace("_", " ")
        
        // Parse and display body with DText formatting
        val formattedBody = parseDText(wiki.body)
        txtBody.text = formattedBody
        
        // Show other names if available
        if (!wiki.otherNames.isNullOrEmpty()) {
            txtOtherNames.visibility = View.VISIBLE
            txtOtherNames.text = getString(R.string.wiki_other_names, wiki.otherNames.joinToString(", "))
        }
        
        // Show metadata
        val metaBuilder = StringBuilder()
        wiki.creatorName?.let {
            metaBuilder.append(getString(R.string.wiki_created_by, it))
        }
        wiki.updatedAt?.let {
            if (metaBuilder.isNotEmpty()) metaBuilder.append("\n")
            metaBuilder.append(getString(R.string.wiki_updated, it.substringBefore("T")))
        }
        if (wiki.isLocked == true) {
            if (metaBuilder.isNotEmpty()) metaBuilder.append("\n")
            metaBuilder.append(getString(R.string.wiki_locked))
        }
        
        if (metaBuilder.isNotEmpty()) {
            txtMeta.text = metaBuilder.toString()
        }
    }
    
    /**
     * Parse DText (e621's markup language) to displayable text
     * Basic implementation - handles common patterns
     */
    private fun parseDText(input: String): CharSequence {
        var text = input
        
        // Convert wiki links [[tag]] to readable text
        text = text.replace(Regex("""\[\[([^\]|]+)\|([^\]]+)\]\]""")) { match ->
            match.groupValues[2] // Use display name
        }
        text = text.replace(Regex("""\[\[([^\]]+)\]\]""")) { match ->
            match.groupValues[1].replace("_", " ")
        }
        
        // Convert external links "text":url
        text = text.replace(Regex(""""([^"]+)":(\S+)""")) { match ->
            "${match.groupValues[1]} (${match.groupValues[2]})"
        }
        
        // Remove header formatting h1. h2. etc
        text = text.replace(Regex("""h[1-6]\.\s*"""), "")
        
        // Convert bold [b]text[/b]
        text = text.replace(Regex("""\[b\](.*?)\[/b\]""", RegexOption.DOT_MATCHES_ALL)) { match ->
            match.groupValues[1]
        }
        
        // Convert italic [i]text[/i]
        text = text.replace(Regex("""\[i\](.*?)\[/i\]""", RegexOption.DOT_MATCHES_ALL)) { match ->
            match.groupValues[1]
        }
        
        // Convert spoiler [spoiler]text[/spoiler]
        text = text.replace(Regex("""\[spoiler\](.*?)\[/spoiler\]""", RegexOption.DOT_MATCHES_ALL)) { match ->
            "[SPOILER: ${match.groupValues[1]}]"
        }
        
        // Remove quote blocks [quote] [/quote]
        text = text.replace("[quote]", "\"")
        text = text.replace("[/quote]", "\"")
        
        // Remove section blocks
        text = text.replace(Regex("""\[section(,[^\]]+)?\]"""), "")
        text = text.replace("[/section]", "")
        
        // Remove code blocks
        text = text.replace(Regex("""\[code\](.*?)\[/code\]""", RegexOption.DOT_MATCHES_ALL)) { match ->
            match.groupValues[1]
        }
        
        // Convert list items
        text = text.replace(Regex("""^\*\s+""", RegexOption.MULTILINE), "• ")
        
        return text
    }
    
    private fun showError(message: String) {
        emptyState.visibility = View.VISIBLE
        txtEmptyMessage.text = message
        txtTitle.visibility = View.GONE
        txtBody.visibility = View.GONE
        txtOtherNames.visibility = View.GONE
        txtMeta.visibility = View.GONE
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
