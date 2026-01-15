package com.mommys.app.ui.browse

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mommys.app.R
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityBrowsePoolsBinding
import com.mommys.app.ui.main.MainActivity
import com.mommys.app.ui.pool.PoolActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * BrowsePoolsActivity - Browse and search pools
 * Like se/zepiwolf/tws/BrowsePoolsActivity.java in the original app
 */
class BrowsePoolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowsePoolsBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: PoolsAdapter
    
    private val pools = mutableListOf<PoolItem>()
    private var currentPage = 1
    private var searchQuery: String? = null
    private var isLoading = false
    private var hasMorePages = true
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowsePoolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = PreferencesManager(this)
        
        // Handle deep links (e.g., https://e621.net/pools/12345)
        val deepLinkPoolId = handleDeepLink()
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Setup RecyclerView
        adapter = PoolsAdapter(pools) { pool -> onPoolClicked(pool) }
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
                        loadPools()
                    }
                }
            }
        })
        
        // If we have a deep link pool ID, open that pool directly
        if (deepLinkPoolId != null) {
            openPoolById(deepLinkPoolId)
        } else {
            // Initial load
            loadPools()
        }
    }
    
    /**
     * Handle deep links like https://e621.net/pools/12345
     */
    private fun handleDeepLink(): Int? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        
        // Parse pool ID from path like /pools/12345
        val path = data.path ?: return null
        val poolIdMatch = Regex("/pools/(\\d+)").find(path)
        return poolIdMatch?.groupValues?.get(1)?.toIntOrNull()
    }
    
    /**
     * Open a specific pool by ID - navigate to PoolActivity
     */
    private fun openPoolById(poolId: Int) {
        val intent = PoolActivity.newIntent(this, poolId)
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browse_pools, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search -> {
                showSearchDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSearchDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.browse_pools_search_hint)
            setSingleLine(true)
            setText(searchQuery ?: "")
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setMessage(R.string.browse_pools_search_message)
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
    
    private fun resetAndLoad() {
        currentPage = 1
        hasMorePages = true
        pools.clear()
        adapter.notifyDataSetChanged()
        loadPools()
    }
    
    private fun loadPools() {
        if (isLoading) return
        isLoading = true
        
        binding.progressBar.visibility = View.VISIBLE
        
        scope.launch {
            try {
                val newPools = withContext(Dispatchers.IO) {
                    fetchPools()
                }
                
                if (newPools.isEmpty()) {
                    hasMorePages = false
                } else {
                    val startPos = pools.size
                    pools.addAll(newPools)
                    adapter.notifyItemRangeInserted(startPos, newPools.size)
                    currentPage++
                }
                
                binding.emptyText.visibility = if (pools.isEmpty()) View.VISIBLE else View.GONE
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@BrowsePoolsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun fetchPools(): List<PoolItem> {
        val host = prefs.getHost()
        
        var urlStr = "https://$host/pools.json?page=$currentPage&search[order]=updated_at&limit=75"
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
            
            val result = mutableListOf<PoolItem>()
            for (i in 0 until jsonArray.length()) {
                val poolJson = jsonArray.getJSONObject(i)
                result.add(PoolItem(
                    id = poolJson.getInt("id"),
                    name = poolJson.getString("name"),
                    postCount = poolJson.getInt("post_count"),
                    creatorName = poolJson.optString("creator_name", ""),
                    description = poolJson.optString("description", ""),
                    createdAt = poolJson.optString("created_at", ""),
                    updatedAt = poolJson.optString("updated_at", "")
                ))
            }
            
            return result
        } finally {
            connection.disconnect()
        }
    }
    
    private fun onPoolClicked(pool: PoolItem) {
        // Show options dialog like original app
        val options = arrayOf(
            getString(R.string.pool_menu_open),
            getString(R.string.pool_menu_add_to_search)
        )
        
        AlertDialog.Builder(this)
            .setTitle(pool.name.replace("_", " "))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Open pool in PoolActivity
                        val intent = PoolActivity.newIntent(this, pool.id)
                        startActivity(intent)
                    }
                    1 -> {
                        // Add pool:id to search
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("add_tag", "pool:${pool.id}")
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    /**
     * Data class for pool item
     */
    data class PoolItem(
        val id: Int,
        val name: String,
        val postCount: Int,
        val creatorName: String,
        val description: String,
        val createdAt: String,
        val updatedAt: String
    ) {
        fun getFormattedDates(): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                
                val createdDate = inputFormat.parse(createdAt.take(19))
                val updatedDate = inputFormat.parse(updatedAt.take(19))
                
                val createdStr = createdDate?.let { outputFormat.format(it) } ?: "?"
                val updatedStr = updatedDate?.let { outputFormat.format(it) } ?: "?"
                
                "Created: $createdStr, updated: $updatedStr"
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    /**
     * Adapter for pools RecyclerView
     */
    inner class PoolsAdapter(
        private val items: List<PoolItem>,
        private val onClick: (PoolItem) -> Unit
    ) : RecyclerView.Adapter<PoolsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val txtPoolName: android.widget.TextView = itemView.findViewById(R.id.txtPoolName)
            val txtPoolInfo: android.widget.TextView = itemView.findViewById(R.id.txtPoolInfo)
            val txtPoolDate: android.widget.TextView = itemView.findViewById(R.id.txtPoolDate)
            val txtPoolDescription: android.widget.TextView = itemView.findViewById(R.id.txtPoolDescription)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_browse_pool, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pool = items[position]
            holder.txtPoolName.text = pool.name.replace("_", " ")
            holder.txtPoolInfo.text = getString(R.string.browse_pools_item_info, pool.postCount, pool.id, pool.creatorName)
            holder.txtPoolDate.text = pool.getFormattedDates()
            
            if (pool.description.isNotEmpty()) {
                holder.txtPoolDescription.text = getString(R.string.browse_pools_item_description, pool.description)
                holder.txtPoolDescription.visibility = View.VISIBLE
            } else {
                holder.txtPoolDescription.visibility = View.GONE
            }
            
            holder.itemView.setOnClickListener { onClick(pool) }
        }
        
        override fun getItemCount() = items.size
    }
}
