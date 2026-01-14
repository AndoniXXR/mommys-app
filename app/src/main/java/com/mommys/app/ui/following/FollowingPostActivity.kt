package com.mommys.app.ui.following

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.mommys.app.R
import com.mommys.app.data.db.downloads.AppDownloadsDatabase
import com.mommys.app.data.db.downloads.DownloadItem
import com.mommys.app.data.db.following.AppFollowingPostDatabase
import com.mommys.app.data.db.following.FollowingPost
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.ui.post.PostActivity
import com.mommys.app.ui.settings.SettingsActivity
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Activity que muestra los posts de tags seguidos
 * Como FollowingPostActivity en la app original
 */
class FollowingPostActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtSubtitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    
    private lateinit var prefs: PreferencesManager
    private lateinit var followingDb: AppFollowingPostDatabase
    private lateinit var downloadsDb: AppDownloadsDatabase
    private lateinit var adapter: FollowingPostAdapter
    
    private val posts = mutableListOf<FollowingPostItem>()
    private val selectedPosts = mutableSetOf<FollowingPostItem>()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Preference key for order: true = oldest first, false = newest first
    private val PREF_ORDER_OLDEST_FIRST = "fob"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following_post)
        
        // Init prefs and databases
        prefs = PreferencesManager(this)
        followingDb = AppFollowingPostDatabase.getInstance(applicationContext)
        downloadsDb = AppDownloadsDatabase.getInstance(applicationContext)
        
        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        // Init views
        recyclerView = findViewById(R.id.recyclerView)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)
        
        // Setup RecyclerView
        val gridWidth = prefs.getGridColumns()
        recyclerView.layoutManager = GridLayoutManager(this, gridWidth)
        
        adapter = FollowingPostAdapter(
            items = posts,
            prefsManager = prefs,
            onItemClick = { position -> openPost(position) },
            onItemLongClick = { position -> showPostInfo(position) },
            onInfoClick = { position -> showPostInfo(position) },
            onSelectionChanged = { item, selected ->
                if (selected) {
                    selectedPosts.add(item)
                    adapter.vibrateOnSelect(this)
                } else {
                    selectedPosts.remove(item)
                }
            }
        )
        recyclerView.adapter = adapter
        
        // Load posts
        loadPosts()
    }
    
    private fun loadPosts() {
        progressBar.visibility = View.VISIBLE
        
        scope.launch {
            val dbPosts = withContext(Dispatchers.IO) {
                val oldestFirst = prefs.getSharedPreferences().getBoolean(PREF_ORDER_OLDEST_FIRST, true)
                if (oldestFirst) {
                    followingDb.followingPostDao().getAllPostsOldestFirst()
                } else {
                    followingDb.followingPostDao().getAllPostsNewestFirst()
                }
            }
            
            // Convertir a items para el adapter
            val items = dbPosts.mapNotNull { fp ->
                try {
                    val json = JSONObject(fp.json)
                    FollowingPostItem.fromJson(json, fp.queryString, fp.addedDate)
                } catch (e: Exception) {
                    null
                }
            }
            
            posts.clear()
            posts.addAll(items)
            adapter.notifyDataSetChanged()
            
            txtSubtitle.text = getString(R.string.following_post_subtitle, posts.size)
            progressBar.visibility = View.GONE
            
            updateEmptyState()
        }
    }
    
    private fun updateEmptyState() {
        if (posts.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun openPost(position: Int) {
        if (position < 0 || position >= posts.size) return
        
        val item = posts[position]
        
        // Abrir PostActivity con el ID del post
        val intent = Intent(this, PostActivity::class.java).apply {
            putExtra(PostActivity.EXTRA_POST_ID, item.postId)
        }
        startActivityForResult(intent, 52)
    }
    
    private fun showPostInfo(position: Int) {
        if (position < 0 || position >= posts.size) return
        val item = posts[position]
        
        val message = buildString {
            append("Post #${item.postId}\n")
            if (!item.queryString.isNullOrEmpty()) {
                append("Found by: ${item.queryString}\n")
            }
            append("Score: ${item.score}\n")
            append("Favorites: ${item.favCount}\n")
            if (!item.artists.isNullOrEmpty()) {
                append("Artists: ${item.artists}\n")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Post Info")
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_following_post, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.help -> {
                showHelpDialog()
                true
            }
            R.id.download_selected -> {
                downloadSelectedPosts()
                true
            }
            R.id.order_by_oldest -> {
                prefs.getSharedPreferences().edit().putBoolean(PREF_ORDER_OLDEST_FIRST, true).apply()
                loadPosts()
                true
            }
            R.id.order_by_newest -> {
                prefs.getSharedPreferences().edit().putBoolean(PREF_ORDER_OLDEST_FIRST, false).apply()
                loadPosts()
                true
            }
            R.id.clear_scrolled_past -> {
                showClearScrolledPastDialog()
                true
            }
            R.id.clear_all -> {
                showClearAllDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.following_post_title)
            .setMessage(R.string.following_post_help)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.settings) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }
    
    private fun downloadSelectedPosts() {
        if (selectedPosts.isEmpty()) {
            Toast.makeText(this, R.string.no_posts_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        scope.launch {
            var addedCount = 0
            withContext(Dispatchers.IO) {
                for (post in selectedPosts) {
                    val downloadItem = DownloadItem(
                        fileUrl = post.fileUrl ?: continue,
                        addedDate = System.currentTimeMillis(),
                        postId = post.postId,
                        fileExt = post.fileExt,
                        fileSize = post.fileSize,
                        artists = post.artists,
                        md5 = post.md5,
                        thumbUrl = post.previewUrl,
                        tags = post.tags,
                        characters = post.characters,
                        score = post.score,
                        favs = post.favCount,
                        rating = post.rating
                    )
                    val result = downloadsDb.downloadDao().insert(downloadItem)
                    if (result > 0) addedCount++
                }
            }
            
            selectedPosts.clear()
            adapter.clearSelections()
            Toast.makeText(
                this@FollowingPostActivity,
                getString(R.string.added_to_download_queue, addedCount),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun showClearScrolledPastDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.following_post_dialog_clear_scrolled_past_title)
            .setMessage(R.string.following_post_dialog_clear_scrolled_past_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                clearScrolledPastPosts()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
    
    private fun clearScrolledPastPosts() {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        
        if (lastVisiblePosition < 0 || lastVisiblePosition >= posts.size) return
        
        val lastSeenPost = posts[lastVisiblePosition]
        val removedCount = lastVisiblePosition + 1
        
        scope.launch {
            withContext(Dispatchers.IO) {
                val oldestFirst = prefs.getSharedPreferences().getBoolean(PREF_ORDER_OLDEST_FIRST, true)
                if (oldestFirst) {
                    followingDb.followingPostDao().deleteOlderThanOrEqual(lastSeenPost.addedDate)
                } else {
                    followingDb.followingPostDao().deleteNewerThanOrEqual(lastSeenPost.addedDate)
                }
            }
            
            // Remove from list
            for (i in 0 until removedCount) {
                if (posts.isNotEmpty()) posts.removeAt(0)
            }
            adapter.notifyItemRangeRemoved(0, removedCount)
            txtSubtitle.text = getString(R.string.following_post_subtitle, posts.size)
            updateEmptyState()
            
            Toast.makeText(this@FollowingPostActivity, 
                getString(R.string.cleared_count, removedCount), 
                Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.following_post_dialog_clear_title)
            .setMessage(R.string.following_post_dialog_clear_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                clearAllPosts()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
    
    private fun clearAllPosts() {
        scope.launch {
            withContext(Dispatchers.IO) {
                followingDb.followingPostDao().deleteAll()
            }
            
            posts.clear()
            adapter.notifyDataSetChanged()
            txtSubtitle.text = getString(R.string.following_post_subtitle, 0)
            updateEmptyState()
            
            Toast.makeText(this@FollowingPostActivity, R.string.cleared, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * Data class para representar un post de following
 */
data class FollowingPostItem(
    val postId: Int,
    val previewUrl: String?,
    val fileUrl: String?,
    val fileExt: String?,
    val fileSize: Long,
    val md5: String?,
    val score: Int,
    val favCount: Int,
    val rating: String?,
    val artists: String?,
    val characters: String?,
    val tags: String?,
    val queryString: String?,
    val addedDate: Long
) {
    companion object {
        fun fromJson(json: JSONObject, queryString: String?, addedDate: Long): FollowingPostItem {
            val preview = json.optJSONObject("preview")
            val file = json.optJSONObject("file")
            val scoreObj = json.optJSONObject("score")
            val tagsObj = json.optJSONObject("tags")
            
            val artistsList = tagsObj?.optJSONArray("artist")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            
            val charactersList = tagsObj?.optJSONArray("character")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            
            val allTags = mutableListOf<String>()
            tagsObj?.keys()?.forEach { key ->
                tagsObj.optJSONArray(key)?.let { arr ->
                    for (i in 0 until arr.length()) {
                        allTags.add(arr.getString(i))
                    }
                }
            }
            
            return FollowingPostItem(
                postId = json.optInt("id"),
                previewUrl = preview?.optString("url"),
                fileUrl = file?.optString("url"),
                fileExt = file?.optString("ext"),
                fileSize = file?.optLong("size") ?: 0,
                md5 = file?.optString("md5"),
                score = scoreObj?.optInt("total") ?: 0,
                favCount = json.optInt("fav_count"),
                rating = json.optString("rating"),
                artists = artistsList.joinToString(", "),
                characters = charactersList.joinToString(", "),
                tags = allTags.joinToString(" "),
                queryString = queryString,
                addedDate = addedDate
            )
        }
    }
}
