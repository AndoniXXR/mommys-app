package com.mommys.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mommys.app.R
import com.mommys.app.data.db.following.AppFollowingPostDatabase
import com.mommys.app.data.db.following.FollowingPost
import com.mommys.app.data.db.logs.AppLogDatabase
import com.mommys.app.data.preferences.PreferencesManager
import com.mommys.app.ui.post.PostActivity
import com.mommys.app.util.BlacklistHelper
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JobService for checking followed tags and sending notifications.
 * Matches the original app's se.zepiwolf.tws.service.FollowingJobService
 */
class FollowingJobService : JobService() {
    
    companion object {
        const val JOB_ID = 94174434 // Same as original app
        private const val CHANNEL_ID = "Following" // Same as original app
        private const val NOTIFICATION_ID = 1001
        
        // Period values mapped exactly like original app's m12677j() method
        // 0 -> 30 min, 1 -> 60 min, 2 -> 180 min, 3 -> 360 min, 4 -> 720 min, 5 -> 1440 min
        private val PERIOD_MINUTES = intArrayOf(
            30,   // 0: 30 minutes
            60,   // 1: 1 hour (default)
            180,  // 2: 3 hours
            360,  // 3: 6 hours
            720,  // 4: 12 hours
            1440  // 5: 24 hours
        )
        
        /**
         * Get period in minutes from preference index
         * Like original app's m12677j() method
         */
        fun getPeriodMinutes(index: Int): Int {
            return when (index) {
                0 -> 30
                1 -> 60
                2 -> 180
                3 -> 360
                4 -> 720
                else -> 1440
            }
        }
        
        /**
         * Schedule the following job
         * Like original app's AbstractC1043e.m6372x()
         */
        fun schedule(context: Context, forceReschedule: Boolean = false) {
            val prefs = PreferencesManager(context)
            if (!prefs.followingEnabled) {
                cancel(context)
                return
            }
            
            val periodIndex = prefs.followingPeriod
            val periodMinutes = getPeriodMinutes(periodIndex)
            val periodMillis = periodMinutes * 60000L
            
            val componentName = ComponentName(context, FollowingJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPersisted(true) // Survive reboot
                .setPeriodic(periodMillis)
            
            // WiFi only constraint (NETWORK_TYPE_UNMETERED = 2, NETWORK_TYPE_ANY = 1)
            if (prefs.followingOnlyWifi) {
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            } else {
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            }
            
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            // Check if already scheduled (skip if not forcing reschedule)
            if (!forceReschedule) {
                val pendingJobs = jobScheduler.allPendingJobs
                for (job in pendingJobs) {
                    if (job.id == JOB_ID) {
                        log(context, "Job already scheduled, skipping")
                        return
                    }
                }
            }
            
            // Cancel existing job if rescheduling
            if (forceReschedule) {
                jobScheduler.cancel(JOB_ID)
            }
            
            val result = jobScheduler.schedule(builder.build())
            if (result == JobScheduler.RESULT_SUCCESS) {
                log(context, "Job scheduled. Every $periodMinutes minutes. WiFi only: ${prefs.followingOnlyWifi}")
            } else {
                log(context, "Job scheduling failed with result: $result")
            }
        }
        
        /**
         * Cancel the following job
         */
        fun cancel(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            log(context, "Job cancelled")
        }
        
        /**
         * Log a message to the database
         */
        private fun log(context: Context, message: String) {
            AppLogDatabase.log(context, message)
        }
    }
    
    @Volatile
    private var isCancelled = false
    
    override fun onStartJob(params: JobParameters): Boolean {
        isCancelled = false
        
        // Create notification channel for Android 8+
        createNotificationChannel()
        
        // Run in background thread (like original app)
        Thread {
            try {
                doWork(params)
            } catch (e: Exception) {
                log(this, "Error: ${e.message}")
                e.printStackTrace()
            } finally {
                // Job finished
                jobFinished(params, false)
            }
        }.start()
        
        // Return true because we're doing async work
        return true
    }
    
    override fun onStopJob(params: JobParameters): Boolean {
        isCancelled = true
        // Return false - don't reschedule
        return false
    }
    
    private fun doWork(params: JobParameters) {
        val prefs = PreferencesManager(this)
        
        // Check if enabled
        if (!prefs.followingEnabled) {
            log(this, "Following disabled, skipping")
            return
        }
        
        // Check network
        if (!isNetworkAvailable()) {
            log(this, "No network available")
            return
        }
        
        // Check WiFi requirement
        if (prefs.followingOnlyWifi && !isWifiConnected()) {
            log(this, "WiFi required but not connected")
            return
        }
        
        val lastUpdate = prefs.followingLastUpdate
        val currentTime = System.currentTimeMillis()
        val displayTag = prefs.followingDisplayTag
        val useE621 = prefs.useE621()  // Get host preference
        
        // Get tags to check
        val tagsString = prefs.followingTags
        if (tagsString.isBlank()) {
            log(this, "No tags to follow")
            return
        }
        
        val tags = tagsString.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (tags.isEmpty()) {
            log(this, "No valid tags found")
            return
        }
        
        log(this, "Checking ${tags.size} tags... lastUpdate=$lastUpdate")
        
        // Get database for following posts
        val followingDb = AppFollowingPostDatabase.getInstance(this)
        
        var allSuccess = true
        
        for (tag in tags) {
            if (isCancelled) {
                log(this, "Job cancelled")
                break
            }
            
            try {
                val newPostsCount = checkTagForNewPosts(tag, prefs, useE621, lastUpdate, displayTag, followingDb)
                if (newPostsCount > 0) {
                    log(this, "Found $newPostsCount new posts for: $tag")
                }
            } catch (e: Exception) {
                log(this, "Error checking $tag: ${e.message}")
                e.printStackTrace()
                allSuccess = false
            }
            
            // Small delay between requests (like original app)
            Thread.sleep(1000)
        }
        
        // Update last check time if successful
        if (allSuccess) {
            prefs.followingLastUpdate = currentTime
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        log(this, "Check complete at ${dateFormat.format(Date())}")
    }
    
    /**
     * Check a tag for new posts
     * Like original app's m16088a() method
     * Returns number of new posts found
     */
    private fun checkTagForNewPosts(
        tag: String,
        prefs: PreferencesManager,
        useE621: Boolean,
        lastUpdate: Long,
        displayTag: Boolean,
        followingDb: AppFollowingPostDatabase
    ): Int {
        // Build API URL for the tag - use correct host based on preferences
        val host = if (useE621) "e621.net" else "e926.net"
        val encodedTag = Uri.encode(tag)
        val urlString = "https://$host/posts.json?tags=$encodedTag"
        
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MommysApp/1.0 (by Mommys)")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            // Add authorization if logged in
            val username = prefs.username
            val apiKey = prefs.apiKey
            if (username.isNotEmpty() && apiKey.isNotEmpty()) {
                val credentials = "$username:$apiKey"
                val encoded = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                log(this, "HTTP error ${connection.responseCode} for tag: $tag")
                return 0
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val postsArray = json.getJSONArray("posts")
            
            // Parse posts and check for new ones
            val posts = mutableListOf<Pair<Int, JSONObject>>()
            for (i in 0 until postsArray.length()) {
                try {
                    val postJson = postsArray.getJSONObject(i)
                    val postId = postJson.getInt("id")
                    posts.add(Pair(postId, postJson))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Reverse to process oldest first (like original app)
            posts.reverse()
            
            // Check if tag is a pool query
            val isPoolQuery = tag.startsWith("pool:") || tag.contains(" pool:")
            
            var newPostsCount = 0
            
            for ((postId, postJson) in posts) {
                // Get post creation timestamp
                val createdAtStr = postJson.optString("created_at", "")
                val postTimestamp = parseTimestamp(createdAtStr)
                
                // Skip if post is older than last update
                if (postTimestamp <= lastUpdate) {
                    continue
                }
                
                // For non-pool queries, check if post is in blacklist using centralized BlacklistHelper
                if (!isPoolQuery) {
                    if (BlacklistHelper.isPostBlacklistedFromJson(postJson, prefs)) {
                        continue
                    }
                }
                
                // Check if post already exists in database
                val existingPost = runBlocking {
                    followingDb.followingPostDao().getPostById(postId)
                }
                
                if (existingPost != null) {
                    // Post already processed
                    continue
                }
                
                // New post found! Insert into database
                val followingPost = FollowingPost(
                    postId = postId,
                    json = postJson.toString(),
                    addedDate = System.currentTimeMillis(),
                    queryString = tag,
                    isAi = 0
                )
                
                runBlocking {
                    followingDb.followingPostDao().insert(followingPost)
                }
                
                // Send individual notification with thumbnail for each new post
                sendNotificationForPost(postId, postJson, tag, displayTag, isPoolQuery)
                
                newPostsCount++
            }
            
            return newPostsCount
            
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parse ISO timestamp to milliseconds
     */
    private fun parseTimestamp(timestampStr: String): Long {
        if (timestampStr.isEmpty()) return 0
        
        return try {
            // Format: 2024-01-15T12:30:45.123-05:00
            val formats = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    val date = sdf.parse(timestampStr)
                    if (date != null) return date.time
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Send notification for a single new post with thumbnail
     * Like original app's notification with BigPictureStyle
     */
    private fun sendNotificationForPost(
        postId: Int,
        postJson: JSONObject,
        tag: String,
        displayTag: Boolean,
        isPool: Boolean
    ) {
        val title = getString(R.string.following_notification_title)
        val message = if (displayTag) {
            "New post for: $tag"
        } else {
            "New post available!"
        }
        
        // Get preview URL from post JSON
        val previewUrl = getPreviewUrl(postJson)
        
        // Download preview image
        var previewBitmap: Bitmap? = null
        if (previewUrl != null) {
            previewBitmap = downloadImage(previewUrl)
        }
        
        // Create intent to open the specific post
        val intent = Intent(this, PostActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PostActivity.EXTRA_POST_ID, postId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, postId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_active)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        
        // Add BigPictureStyle with thumbnail if available
        if (previewBitmap != null) {
            val bigPictureStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(previewBitmap)
                .setBigContentTitle(title)
                .setSummaryText(message)
            
            // Set large icon (small preview shown when collapsed)
            builder.setLargeIcon(previewBitmap)
            builder.setStyle(bigPictureStyle)
        }
        
        val notification = builder.build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        // Use postId as notification ID so each post gets its own notification
        notificationManager?.notify(postId, notification)
        
        log(this, "Notification sent for post #$postId ($tag)")
    }
    
    /**
     * Get preview URL from post JSON
     */
    private fun getPreviewUrl(postJson: JSONObject): String? {
        try {
            val previewObj = postJson.optJSONObject("preview")
            if (previewObj != null) {
                val url = previewObj.optString("url", "")
                if (url.isNotEmpty()) return url
            }
            
            // Fallback to sample
            val sampleObj = postJson.optJSONObject("sample")
            if (sampleObj != null) {
                val url = sampleObj.optString("url", "")
                if (url.isNotEmpty()) return url
            }
            
            // Fallback to file URL
            val fileObj = postJson.optJSONObject("file")
            if (fileObj != null) {
                val url = fileObj.optString("url", "")
                if (url.isNotEmpty()) return url
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Download image from URL and return as Bitmap
     */
    private fun downloadImage(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "MommysApp/1.0 (by Mommys)")
            connection.connect()
            
            val inputStream = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            connection.disconnect()
            
            bitmap
        } catch (e: Exception) {
            log(this, "Failed to download image: ${e.message}")
            null
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.following_notification_channel_title)
            val description = getString(R.string.following_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            
            // Use "Following" as channel ID like original app
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun log(context: Context, message: String) {
        AppLogDatabase.log(context, message)
    }
}
