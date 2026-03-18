package com.mommys.app.ui.pool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.db.downloads.AppDownloadsDatabase
import com.mommys.app.data.db.downloads.DownloadItem
import com.mommys.app.data.model.Pool
import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.databinding.ActivityPoolBinding
import com.mommys.app.service.DownloadQueueService
import com.mommys.app.ui.main.PostsAdapter
import com.mommys.app.ui.main.SelectionCallback
import com.mommys.app.ui.post.PostActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashSet

/**
 * PoolActivity - Display posts from a specific pool
 * Like se.zepiwolf.tws.PoolActivity in the original app
 *
 * Features:
 * - Grid view of pool posts
 * - Multi-select for batch download
 * - Follow/Unfollow pool
 * - Share pool
 * - Deep links support: /pools/{id}
 */
class PoolActivity : AppCompatActivity(), SelectionCallback {

    companion object {
        const val EXTRA_POOL_ID = "pool_id"
        
        // Static list of posts for PostActivity navigation
        var poolPosts: MutableList<Post> = mutableListOf()
        
        fun newIntent(context: Context, poolId: Int): Intent {
            return Intent(context, PoolActivity::class.java).apply {
                putExtra(EXTRA_POOL_ID, poolId)
            }
        }
    }

    private lateinit var binding: ActivityPoolBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: PostsAdapter
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var poolId: Int = -1
    private var pool: Pool? = null
    private val posts = mutableListOf<Post>()
    private var loadJob: Job? = null
    
    // Selection tracking (like LinkedHashSet K in original)
    private val selectedPosts = LinkedHashSet<Post>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoolBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = PreferencesManager(this)
        
        // Get pool ID from intent or deep link
        poolId = getPoolId()
        
        if (poolId <= 0) {
            Toast.makeText(this, "Not a valid URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        setupToolbar()
        setupRecyclerView()
        setupBottomNav()
        setupBackPress()
        
        // Load pool data
        loadPool()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newPoolId = getPoolId()
        if (newPoolId > 0 && newPoolId != poolId) {
            poolId = newPoolId
            supportActionBar?.title = getString(R.string.pool_title_id, poolId)
            clearSelection()
            val oldSize = posts.size
            posts.clear()
            adapter.notifyItemRangeRemoved(0, oldSize)
            poolPosts.clear()
            loadPool()
        }
    }
    
    private fun getPoolId(): Int {
        // First check extras
        val fromExtra = intent.getIntExtra(EXTRA_POOL_ID, -1)
        if (fromExtra > 0) return fromExtra
        
        // Try string extra
        val stringExtra = intent.getStringExtra(EXTRA_POOL_ID)
        stringExtra?.toIntOrNull()?.let { if (it > 0) return it }
        
        // Try deep link
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val pathSegments = uri.pathSegments
                for (segment in pathSegments) {
                    segment.toIntOrNull()?.let { if (it > 0) return it }
                }
            }
        }
        
        return -1
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.pool_title_id, poolId)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
    
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Like original app (di/m.java case 4):
                // If info overlay visible -> close it
                // If selection active -> clear selection
                // Otherwise -> finish
                val fLInfo = findViewById<FrameLayout>(R.id.fLInfo)
                if (fLInfo != null && fLInfo.childCount > 0) {
                    hidePostInfo()
                } else if (selectedPosts.isNotEmpty()) {
                    clearSelection()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    private fun setupRecyclerView() {
        val gridWidth = prefs.getGridWidth()
        
        adapter = PostsAdapter(
            onPostClick = { post -> openPost(post) },
            onInfoClick = { post -> showPostInfo(post) },
            selectionCallback = this
        ).apply {
            aspectRatio = prefs.gridHeight / 100.0
            showStats = prefs.isGridStatsEnabled()
            showInfoButton = prefs.isGridInfoEnabled()
            showStatusColors = prefs.isGridColoursEnabled()
        }
        
        binding.recyclerView.layoutManager = GridLayoutManager(this, gridWidth)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.select_download -> {
                    downloadSelected()
                    true
                }
                R.id.select_all -> {
                    selectAll()
                    true
                }
                R.id.select_clear -> {
                    clearSelection()
                    true
                }
                R.id.save -> {
                    savePool()
                    true
                }
                R.id.follow_pool -> {
                    toggleFollowPool()
                    true
                }
                else -> false
            }
        }
        
        // Initially hide selection group
        binding.bottomNav.menu.setGroupVisible(R.id.selected_group, false)
    }
    
    private fun loadPool() {
        // Cancel any in-progress load
        loadJob?.cancel()
        
        binding.progressBar.visibility = View.VISIBLE
        binding.txtTitle.text = getString(R.string.pool_subtitle_loading)
        binding.txtTitleStatus.visibility = View.VISIBLE
        binding.txtTitleStatus.text = getString(R.string.pool_subtitle_status_1)
        
        loadJob = scope.launch {
            try {
                // Step 1: Fetch pool info
                val api = ApiClient.getApiService()
                val poolResponse = withContext(Dispatchers.IO) {
                    api.getPool(poolId)
                }
                
                if (!poolResponse.isSuccessful) {
                    throw Exception("HTTP ${poolResponse.code()}")
                }
                
                pool = poolResponse.body()
                val poolData = pool ?: throw Exception("Pool not found")
                
                // Update UI with pool info
                supportActionBar?.title = getString(R.string.pool_title_id, poolId)
                binding.txtTitle.text = poolData.name.replace("_", " ")
                binding.txtTitleStatus.text = getString(R.string.pool_subtitle_status_2, poolData.postIds.size)
                
                // Step 2: Fetch posts using pool:{id} tag (like original app)
                if (poolData.postIds.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.txtTitleStatus.text = getString(R.string.pool_empty)
                    return@launch
                }
                
                val allPosts = mutableListOf<Post>()
                var page = 1
                
                while (true) {
                    binding.txtTitleStatus.text = getString(
                        R.string.pool_subtitle_status_3,
                        page,
                        ((poolData.postIds.size + 319) / 320).coerceAtLeast(1)
                    )
                    
                    val response = withContext(Dispatchers.IO) {
                        api.getPosts(tags = "pool:$poolId", limit = 320, page = page)
                    }
                    
                    if (response.isSuccessful) {
                        val fetchedPosts = response.body()?.posts
                        if (fetchedPosts != null) {
                            allPosts.addAll(fetchedPosts)
                            if (fetchedPosts.size < 320) break
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                    page++
                }
                
                // Apply blacklist and flash filtering like original app
                val filteredPosts = filterPosts(allPosts)
                
                // Reorder posts to match exact pool order
                val orderedPosts = poolData.postIds.mapNotNull { id ->
                    filteredPosts.find { it.id == id }
                }
                
                val oldSize = posts.size
                posts.clear()
                if (oldSize > 0) adapter.notifyItemRangeRemoved(0, oldSize)
                posts.addAll(orderedPosts)
                poolPosts.clear()
                poolPosts.addAll(orderedPosts)
                
                adapter.submitList(posts.toList())
                
                binding.progressBar.visibility = View.GONE
                binding.txtTitleStatus.visibility = View.GONE
                
                // Update follow button based on current state
                updateFollowButton()
                
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progressBar.visibility = View.GONE
                binding.txtTitle.text = getString(R.string.error)
                binding.txtTitleStatus.text = e.message ?: getString(R.string.error_generic)
                Toast.makeText(this@PoolActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun openPost(post: Post) {
        val position = posts.indexOf(post)
        if (position >= 0) {
            // Use PostActivity.createIntent which sets pendingPosts internally
            val intent = PostActivity.createIntent(this, poolPosts, position)
            startActivity(intent)
        }
    }
    
    // ==================== Selection Callbacks ====================
    
    override fun onPostSelected(post: Post) {
        selectedPosts.add(post)
        updateSelectionUI()
        performHapticFeedback()
    }
    
    override fun onPostDeselected(post: Post) {
        selectedPosts.remove(post)
        updateSelectionUI()
    }
    
    override fun getSelectedCount(): Int = selectedPosts.size
    
    private fun updateSelectionUI() {
        if (selectedPosts.isEmpty()) {
            binding.bottomNav.menu.setGroupVisible(R.id.selected_group, false)
        } else {
            binding.bottomNav.menu.setGroupVisible(R.id.selected_group, true)
            // Update badge on download button
            binding.bottomNav.getOrCreateBadge(R.id.select_download).apply {
                isVisible = true
                number = selectedPosts.size
            }
        }
    }
    
    private fun performHapticFeedback() {
        val vibrator = getSystemService<Vibrator>()
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(5L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(5L)
            }
        }
    }
    
    // ==================== Bottom Nav Actions ====================
    
    private fun downloadSelected() {
        if (selectedPosts.isEmpty()) {
            Toast.makeText(this, R.string.no_posts_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get posts to download and filter those without valid file URLs
        val postsToDownload = selectedPosts.toList().filter { post ->
            !post.file.url.isNullOrEmpty()
        }
        
        if (postsToDownload.isEmpty()) {
            Toast.makeText(this, R.string.no_downloadable_posts, Toast.LENGTH_SHORT).show()
            clearSelection()
            return
        }
        
        Toast.makeText(this, getString(R.string.downloading_posts, postsToDownload.size), Toast.LENGTH_SHORT).show()
        
        // Add posts to download queue using coroutines
        scope.launch {
            try {
                val database = AppDownloadsDatabase.getInstance(this@PoolActivity)
                val currentTime = System.currentTimeMillis()
                
                var addedCount = 0
                
                for ((index, post) in postsToDownload.withIndex()) {
                    // Create DownloadItem from Post (like original app)
                    val downloadItem = DownloadItem(
                        fileUrl = post.file.url ?: continue,
                        addedDate = currentTime + index, // Ensure order is preserved
                        postId = post.id,
                        fileExt = post.file.ext,
                        fileSize = post.file.size,
                        artists = post.tags.artist.joinToString("_"),
                        md5 = post.file.md5,
                        thumbUrl = post.preview.url ?: post.sample.url,
                        tags = (post.tags.general + post.tags.species).take(10).joinToString("_"),
                        characters = post.tags.character.joinToString("_"),
                        score = post.score.total,
                        favs = post.favCount,
                        apng = if (post.file.ext == "apng") 1 else 0,
                        rating = post.rating,
                        error = null,
                        isAi = if (post.tags.meta.contains("ai_generated")) 1 else 0
                    )
                    
                    // Insert (ignore if already exists)
                    withContext(Dispatchers.IO) {
                        val result = database.downloadDao().insert(downloadItem)
                        if (result != -1L) {
                            addedCount++
                        }
                    }
                }
                
                // Start download service
                DownloadQueueService.start(this@PoolActivity)
                
                Toast.makeText(
                    this@PoolActivity, 
                    getString(R.string.posts_added_to_queue, addedCount),
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@PoolActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        clearSelection()
    }
    
    private fun selectAll() {
        posts.forEach { post ->
            if (!selectedPosts.contains(post)) {
                selectedPosts.add(post)
            }
        }
        adapter.syncSelections(selectedPosts.map { it.id }.toSet())
        updateSelectionUI()
    }
    
    private fun clearSelection() {
        selectedPosts.clear()
        adapter.clearLocalSelections()
        updateSelectionUI()
        binding.bottomNav.removeBadge(R.id.select_download)
    }
    
    private fun savePool() {
        // Save pool to saved searches
        val poolTag = "pool:$poolId"
        val poolName = pool?.name?.replace("_", " ") ?: "Pool $poolId"
        
        prefs.addSavedSearch(poolName, poolTag)
        Toast.makeText(this, R.string.pool_saved, Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleFollowPool() {
        val followedTags = prefs.getFollowedTags().toMutableList()
        val poolTag = "pool:$poolId"
        
        if (followedTags.contains(poolTag)) {
            followedTags.remove(poolTag)
            prefs.setFollowedTags(followedTags)
            Toast.makeText(this, R.string.pool_unfollowed, Toast.LENGTH_SHORT).show()
        } else {
            followedTags.add(poolTag)
            prefs.setFollowedTags(followedTags)
            Toast.makeText(this, R.string.pool_followed, Toast.LENGTH_SHORT).show()
        }
        
        updateFollowButton()
    }
    
    private fun updateFollowButton() {
        val followedTags = prefs.getFollowedTags()
        val isFollowing = followedTags.contains("pool:$poolId")
        
        val followItem = binding.bottomNav.menu.findItem(R.id.follow_pool)
        if (isFollowing) {
            followItem?.setIcon(R.drawable.ic_following)
            followItem?.title = getString(R.string.pool_menu_unfollow)
        } else {
            followItem?.setIcon(R.drawable.ic_follow)
            followItem?.title = getString(R.string.pool_menu_follow)
        }
    }
    
    // ==================== Options Menu ====================
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pool, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.refresh -> {
                item.isEnabled = false
                // Clear list before reloading (like original app)
                val oldSize = posts.size
                posts.clear()
                if (oldSize > 0) adapter.notifyItemRangeRemoved(0, oldSize)
                poolPosts.clear()
                clearSelection()
                loadPool()
                binding.recyclerView.postDelayed({ item.isEnabled = true }, 1000)
                true
            }
            R.id.share_pool -> {
                sharePool()
                true
            }
            R.id.unfollow_pool -> {
                unfollowPool()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun sharePool() {
        if (prefs.isShareDisabled()) {
            Toast.makeText(this, R.string.post_share_disabled, Toast.LENGTH_SHORT).show()
            return
        }
        
        val host = if (prefs.isExplicitEnabled()) "e621" else "e926"
        val url = "https://$host.net/pools/$poolId"
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }
    
    private fun unfollowPool() {
        val followedTags = prefs.getFollowedTags().toMutableList()
        val poolTag = "pool:$poolId"
        
        if (followedTags.remove(poolTag)) {
            prefs.setFollowedTags(followedTags)
            Toast.makeText(this, R.string.pool_unfollowed, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.pool_not_following, Toast.LENGTH_SHORT).show()
        }
        
        updateFollowButton()
    }
    
    // ==================== Post Info Overlay ====================
    
    private fun showPostInfo(post: Post) {
        val fLInfo = findViewById<FrameLayout>(R.id.fLInfo) ?: return
        fLInfo.removeAllViews()
        
        val contentView = layoutInflater.inflate(R.layout.content_post_info, fLInfo, false)
        
        val txtPost = contentView.findViewById<TextView>(R.id.txtPost)
        val txtTags = contentView.findViewById<TextView>(R.id.txtTags)
        val txtArtist = contentView.findViewById<TextView>(R.id.txtArtist)
        val txtScore = contentView.findViewById<TextView>(R.id.txtScore)
        val txtFavourites = contentView.findViewById<TextView>(R.id.txtFavourites)
        val txtRating = contentView.findViewById<TextView>(R.id.txtRating)
        val txtComments = contentView.findViewById<TextView>(R.id.txtComments)
        val txtPool = contentView.findViewById<TextView>(R.id.txtPool)
        val txtParent = contentView.findViewById<TextView>(R.id.txtParent)
        val txtChildren = contentView.findViewById<TextView>(R.id.txtChildren)
        val imgPreview = contentView.findViewById<PhotoView>(R.id.imgPreview)
        val imgClose = contentView.findViewById<ImageView>(R.id.imgClose)
        
        txtPost.text = getString(R.string.post_info_title, post.id)
        
        val totalTags = post.tags.general.size + post.tags.species.size +
                        post.tags.character.size + post.tags.artist.size +
                        post.tags.copyright.size + post.tags.meta.size +
                        post.tags.lore.size + post.tags.invalid.size
        txtTags.text = getString(R.string.post_info_tags, totalTags)
        
        val artistName = if (post.tags.artist.isNotEmpty()) {
            post.tags.artist.joinToString(", ")
        } else {
            getString(R.string.unknown_artist)
        }
        txtArtist.text = getString(R.string.post_info_artist, artistName)
        txtScore.text = getString(R.string.post_info_score, post.score.total, post.score.up, kotlin.math.abs(post.score.down))
        txtFavourites.text = getString(R.string.post_info_favourites, post.favCount)
        txtRating.text = getString(R.string.post_info_rating, post.rating.uppercase())
        
        if (post.commentCount > 0) {
            txtComments.visibility = View.VISIBLE
            txtComments.text = getString(R.string.post_info_comments, post.commentCount)
        } else {
            txtComments.visibility = View.GONE
        }
        
        if (post.pools.isNotEmpty()) {
            txtPool.visibility = View.VISIBLE
            txtPool.text = getString(R.string.post_info_pool)
        } else {
            txtPool.visibility = View.GONE
        }
        
        if (post.relationships.parentId != null && post.relationships.parentId > 0) {
            txtParent.visibility = View.VISIBLE
            txtParent.text = getString(R.string.post_info_parent)
        } else {
            txtParent.visibility = View.GONE
        }
        
        if (post.relationships.hasChildren) {
            txtChildren.visibility = View.VISIBLE
            txtChildren.text = getString(R.string.post_info_children)
        } else {
            txtChildren.visibility = View.GONE
        }
        
        val previewUrl = post.preview.url ?: post.sample?.url ?: post.file.url
        Glide.with(this).load(previewUrl).into(imgPreview)
        
        imgPreview.setOnClickListener { hidePostInfo() }
        imgClose.setOnClickListener { hidePostInfo() }
        fLInfo.setOnClickListener { hidePostInfo() }
        
        fLInfo.addView(contentView)
        fLInfo.visibility = View.VISIBLE
    }
    
    private fun hidePostInfo() {
        val fLInfo = findViewById<FrameLayout>(R.id.fLInfo) ?: return
        fLInfo.visibility = View.GONE
        fLInfo.removeAllViews()
    }
    
    // ==================== Post Filtering ====================
    
    private fun filterPosts(allPosts: List<Post>): List<Post> {
        val applyBlacklist = prefs.blacklistEnabled && prefs.blacklistPoolPosts
        val blacklist = if (applyBlacklist) prefs.getBlacklist() else emptySet()
        val includeFlash = prefs.searchIncludeFlash
        
        return allPosts.filter { post ->
            // Filter flash/swf if disabled
            if (!includeFlash && post.file.ext.equals("swf", ignoreCase = true)) return@filter false
            
            // Filter blacklisted tags
            if (blacklist.isNotEmpty()) {
                val postTags = post.tags.general + post.tags.artist + post.tags.species +
                               post.tags.character + post.tags.copyright + post.tags.meta
                if (postTags.any { it in blacklist }) return@filter false
            }
            
            true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
        scope.cancel()
    }
}
