package com.mommys.app.ui.sets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.api.ApiService
import com.mommys.app.data.model.PostSet
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Activity para explorar Sets de posts
 * Similar a BrowseSetsActivity.java de la app original
 */
class BrowseSetsActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_SELECT_MODE = "select_mode"
        const val EXTRA_MY_SETS = "my_sets"
        const val EXTRA_SET_ID = "set_id"
    }
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var txtEmptyMessage: TextView
    private lateinit var txtTitle: TextView
    
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: PostSetAdapter
    
    private var currentPage = 1
    private var isLoading = false
    private var hasMorePages = true
    
    // Search filters
    private var searchName: String? = null
    private var searchShortname: String? = null
    private var searchCreator: String? = null
    private var searchOrder: String = "updated_at"
    
    // Select mode for adding post to set
    private var postIdToAdd: Int = -1
    private var isSelectMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse_sets)
        
        prefs = PreferencesManager(this)
        
        // Handle deep links (e.g., https://e621.net/post_sets/12345)
        val deepLinkSetId = handleDeepLink()
        
        // Check if we're in select mode (adding post to set)
        intent.extras?.let {
            postIdToAdd = it.getInt(EXTRA_POST_ID, -1)
            isSelectMode = it.getBoolean(EXTRA_SELECT_MODE, false)
        }
        
        initViews()
        
        // If we have a deep link set ID, open that set directly
        if (deepLinkSetId != null) {
            openSetById(deepLinkSetId)
        } else {
            loadSets()
        }
    }
    
    /**
     * Handle deep links like https://e621.net/post_sets/12345
     */
    private fun handleDeepLink(): Int? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        
        // Parse set ID from path like /post_sets/12345
        val path = data.path ?: return null
        val setIdMatch = Regex("/post_sets/(\\d+)").find(path)
        return setIdMatch?.groupValues?.get(1)?.toIntOrNull()
    }
    
    /**
     * Open a specific set by ID - load posts for that set
     */
    private fun openSetById(setId: Int) {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().getPostSet(setId)
                if (response.isSuccessful) {
                    response.body()?.let { set ->
                        // Open the set in MainActivity with the set's posts
                        val intent = Intent(this@BrowseSetsActivity, MainActivity::class.java)
                        intent.putExtra("tags", "set:${set.shortname}")
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Toast.makeText(this@BrowseSetsActivity, getString(R.string.error_loading_sets), Toast.LENGTH_SHORT).show()
                    loadSets()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BrowseSetsActivity, e.message, Toast.LENGTH_SHORT).show()
                loadSets()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        if (isSelectMode && postIdToAdd > 0) {
            toolbar.title = getString(R.string.browse_sets_select_title)
        }
        
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyState = findViewById(R.id.emptyState)
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage)
        txtTitle = findViewById(R.id.txtTitle)
        
        adapter = PostSetAdapter(
            onSetClick = { set -> onSetClicked(set) },
            isSelectMode = isSelectMode
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Infinite scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                
                if (!isLoading && hasMorePages && lastVisibleItem >= totalItemCount - 5) {
                    loadMoreSets()
                }
            }
        })
    }
    
    private fun onSetClicked(set: PostSet) {
        if (isSelectMode && postIdToAdd > 0) {
            // Add post to this set
            addPostToSet(set, postIdToAdd)
        } else {
            // Open set in MainActivity with search
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("search_query", "set:${set.shortname}")
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }
    
    private fun addPostToSet(set: PostSet, postId: Int) {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // ApiClient ya incluye autenticación automática
                val apiService = ApiClient.apiService
                
                val response = apiService.addPostToSet(set.id, listOf(postId))
                
                progressBar.visibility = View.GONE
                
                if (response.isSuccessful) {
                    Toast.makeText(
                        this@BrowseSetsActivity,
                        getString(R.string.browse_sets_added_success, set.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMsg = when (response.code()) {
                        401, 403 -> getString(R.string.error_not_authorized)
                        422 -> getString(R.string.browse_sets_already_in_set)
                        else -> getString(R.string.error_generic)
                    }
                    Toast.makeText(this@BrowseSetsActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@BrowseSetsActivity,
                    getString(R.string.error_generic) + ": ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun loadSets() {
        currentPage = 1
        hasMorePages = true
        isLoading = true
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val baseUrl = if (prefs.useE621()) ApiService.BASE_URL_E621 else ApiService.BASE_URL_E926
                val apiService = ApiClient.getApiService(baseUrl)
                val response = apiService.getPostSets(
                    name = searchName,
                    shortname = searchShortname,
                    creatorName = searchCreator,
                    order = searchOrder,
                    page = currentPage,
                    limit = 50
                )
                
                progressBar.visibility = View.GONE
                isLoading = false
                
                if (response.isSuccessful && response.body() != null) {
                    val sets = response.body()!!
                    adapter.submitList(sets)
                    
                    if (sets.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                    }
                    
                    hasMorePages = sets.size >= 50
                    currentPage++
                } else {
                    showError(getString(R.string.error_loading_sets))
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                isLoading = false
                showError(getString(R.string.error_loading_sets) + "\n" + e.message)
            }
        }
    }
    
    private fun loadMoreSets() {
        if (isLoading || !hasMorePages) return
        
        isLoading = true
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val baseUrl = if (prefs.useE621()) ApiService.BASE_URL_E621 else ApiService.BASE_URL_E926
                val apiService = ApiClient.getApiService(baseUrl)
                val response = apiService.getPostSets(
                    name = searchName,
                    shortname = searchShortname,
                    creatorName = searchCreator,
                    order = searchOrder,
                    page = currentPage,
                    limit = 50
                )
                
                progressBar.visibility = View.GONE
                isLoading = false
                
                if (response.isSuccessful && response.body() != null) {
                    val newSets = response.body()!!
                    val currentList = adapter.currentList.toMutableList()
                    currentList.addAll(newSets)
                    adapter.submitList(currentList)
                    
                    hasMorePages = newSets.size >= 50
                    currentPage++
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                isLoading = false
            }
        }
    }
    
    private fun showError(message: String) {
        emptyState.visibility = View.VISIBLE
        txtEmptyMessage.text = message
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browse_sets, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.search -> {
                showSearchDialog()
                true
            }
            R.id.order_by -> {
                showOrderDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSearchDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_search, null)
        val etName = dialogView.findViewById<EditText>(R.id.eTName)
        val etShortname = dialogView.findViewById<EditText>(R.id.eTShortName)
        val etUsername = dialogView.findViewById<EditText>(R.id.eTUsername)
        
        // Fill with current values
        searchName?.let { etName.setText(it) }
        searchShortname?.let { etShortname.setText(it) }
        searchCreator?.let { etUsername.setText(it) }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_sets_search_title)
            .setMessage(R.string.browse_sets_search_message)
            .setView(dialogView)
            .setPositiveButton(R.string.search) { _, _ ->
                searchName = etName.text.toString().takeIf { it.isNotBlank() }
                searchShortname = etShortname.text.toString().takeIf { it.isNotBlank() }
                searchCreator = etUsername.text.toString().takeIf { it.isNotBlank() }
                loadSets()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.clear) { _, _ ->
                searchName = null
                searchShortname = null
                searchCreator = null
                loadSets()
            }
            .show()
    }
    
    private fun showOrderDialog() {
        val options = arrayOf(
            getString(R.string.browse_sets_order_updated),
            getString(R.string.browse_sets_order_created),
            getString(R.string.browse_sets_order_name),
            getString(R.string.browse_sets_order_shortname),
            getString(R.string.browse_sets_order_post_count)
        )
        val values = arrayOf("updated_at", "created_at", "name", "shortname", "post_count")
        
        val currentIndex = values.indexOf(searchOrder).takeIf { it >= 0 } ?: 0
        
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_sets_order_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                searchOrder = values[which]
                dialog.dismiss()
                loadSets()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
