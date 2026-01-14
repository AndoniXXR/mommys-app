package com.mommys.app.ui.browse

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityBrowseTagsBinding
import com.mommys.app.ui.main.MainActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * BrowseTagsActivity - Browse and search tags
 * Like se/zepiwolf/tws/BrowseTagsActivity.java in the original app
 */
class BrowseTagsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowseTagsBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: TagsAdapter
    
    private val tags = mutableListOf<TagItem>()
    private var currentPage = 1
    private var searchQuery: String? = null
    private var sortOrder = 0 // 0=date, 1=name, 2=count
    private var isLoading = false
    private var hasMorePages = true
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowseTagsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = PreferencesManager(this)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Setup RecyclerView
        adapter = TagsAdapter(tags) { tag -> onTagClicked(tag) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        
        // Endless scroll
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                if (!isLoading && hasMorePages) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        loadTags()
                    }
                }
            }
        })
        
        // Initial load
        loadTags()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browse_tags, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search -> {
                showSearchDialog()
                true
            }
            R.id.order_by -> {
                showSortPopup()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSearchDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.browse_tags_search_hint)
            setSingleLine(true)
            setText(searchQuery ?: "")
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setMessage(R.string.browse_tags_search_message)
            .setView(editText)
            .setPositiveButton(R.string.main_btn_search) { _, _ ->
                val query = editText.text.toString().trim()
                searchQuery = if (query.isEmpty()) null else query
                resetAndLoad()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.clear) { _, _ ->
                searchQuery = null
                resetAndLoad()
            }
            .show()
    }
    
    private fun showSortPopup() {
        val anchor = findViewById<View>(R.id.order_by) ?: binding.toolbar
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_tags_sort, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_by_date -> {
                    sortOrder = 0
                    resetAndLoad()
                    true
                }
                R.id.sort_by_name -> {
                    sortOrder = 1
                    resetAndLoad()
                    true
                }
                R.id.sort_by_count -> {
                    sortOrder = 2
                    resetAndLoad()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    private fun resetAndLoad() {
        currentPage = 1
        hasMorePages = true
        tags.clear()
        adapter.notifyDataSetChanged()
        loadTags()
    }
    
    private fun loadTags() {
        if (isLoading) return
        isLoading = true
        
        binding.progressBar.visibility = View.VISIBLE
        
        scope.launch {
            try {
                val newTags = withContext(Dispatchers.IO) {
                    fetchTags()
                }
                
                if (newTags.isEmpty()) {
                    hasMorePages = false
                } else {
                    val startPos = tags.size
                    tags.addAll(newTags)
                    adapter.notifyItemRangeInserted(startPos, newTags.size)
                    currentPage++
                }
                
                binding.emptyText.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@BrowseTagsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun fetchTags(): List<TagItem> {
        val host = prefs.getHost()
        val order = when (sortOrder) {
            1 -> "name"
            2 -> "count"
            else -> "date"
        }
        
        var urlStr = "https://$host/tags.json?page=$currentPage&search[order]=$order&limit=75"
        searchQuery?.let {
            urlStr += "&search[name_matches]=${Uri.encode("*$it*")}"
        }
        
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MommysApp/1.0 (by Mommys)")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            // Add auth if logged in
            val username = prefs.username
            val apiKey = prefs.apiKey
            if (username.isNotEmpty() && apiKey.isNotEmpty()) {
                val credentials = "$username:$apiKey"
                val encoded = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)
            
            val result = mutableListOf<TagItem>()
            for (i in 0 until jsonArray.length()) {
                val tagJson = jsonArray.getJSONObject(i)
                result.add(TagItem(
                    id = tagJson.getInt("id"),
                    name = tagJson.getString("name"),
                    postCount = tagJson.getInt("post_count"),
                    category = tagJson.getInt("category"),
                    relatedTags = tagJson.optString("related_tags", "")
                ))
            }
            
            return result
        } finally {
            connection.disconnect()
        }
    }
    
    private fun onTagClicked(tag: TagItem) {
        // Show options dialog like original app
        val options = arrayOf(
            getString(R.string.tag_menu_search),
            getString(R.string.tag_menu_add_to_search),
            getString(R.string.tag_menu_add_to_blacklist),
            getString(R.string.tag_menu_copy)
        )
        
        AlertDialog.Builder(this)
            .setTitle(tag.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Search posts with this tag
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("search_query", tag.name)
                        }
                        startActivity(intent)
                        finish()
                    }
                    1 -> {
                        // Add to current search - return to MainActivity with tag
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("add_tag", tag.name)
                        }
                        startActivity(intent)
                        finish()
                    }
                    2 -> {
                        // Add to blacklist
                        addToBlacklist(tag.name)
                    }
                    3 -> {
                        // Copy to clipboard
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("tag", tag.name)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
    
    private fun addToBlacklist(tag: String) {
        val currentBlacklist = prefs.getBlacklistRaw()
        val newBlacklist = if (currentBlacklist.isBlank()) {
            tag
        } else {
            "$currentBlacklist\n$tag"
        }
        prefs.setBlacklistRaw(newBlacklist)
        Toast.makeText(this, getString(R.string.dialogs_tag_blacklisted, tag), Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    /**
     * Data class for tag item
     */
    data class TagItem(
        val id: Int,
        val name: String,
        val postCount: Int,
        val category: Int,
        val relatedTags: String
    ) {
        fun getCategoryName(): String {
            return when (category) {
                0 -> "general"
                1 -> "artist"
                3 -> "copyright"
                4 -> "character"
                5 -> "species"
                6 -> "invalid"
                7 -> "meta"
                8 -> "lore"
                else -> "unknown"
            }
        }
    }
    
    /**
     * Adapter for tags RecyclerView
     */
    inner class TagsAdapter(
        private val items: List<TagItem>,
        private val onClick: (TagItem) -> Unit
    ) : RecyclerView.Adapter<TagsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val txtTagName: android.widget.TextView = itemView.findViewById(R.id.txtTagName)
            val txtTagInfo: android.widget.TextView = itemView.findViewById(R.id.txtTagInfo)
            val txtRelatedTags: android.widget.TextView = itemView.findViewById(R.id.txtRelatedTags)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_browse_tag, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = items[position]
            holder.txtTagName.text = tag.name
            holder.txtTagInfo.text = getString(R.string.browse_tags_item_info, tag.postCount, tag.id, tag.getCategoryName())
            
            if (tag.relatedTags.isNotEmpty()) {
                holder.txtRelatedTags.text = getString(R.string.browse_tags_item_related_tags, tag.relatedTags.replace(" ", ", "))
                holder.txtRelatedTags.visibility = View.VISIBLE
            } else {
                holder.txtRelatedTags.visibility = View.GONE
            }
            
            holder.itemView.setOnClickListener { onClick(tag) }
        }
        
        override fun getItemCount() = items.size
    }
}
