package com.mommys.app.util

import com.mommys.app.data.model.Post
import com.mommys.app.data.preferences.PreferencesManager
import org.json.JSONObject

/**
 * Helper class for blacklist functionality
 * Matches the original app's ii/m.java method k() and m() logic
 * 
 * Blacklist entry format:
 * - Each line is a separate entry
 * - Multiple tags on same line = AND logic (all must match)
 * - Tag starting with "-" = negative match (if present, entry doesn't apply)
 * - Supports rating:e, rating:q, rating:s
 * - Supports score:<=N, score:<N, score:>=N, score:>N
 * - Supports favcount:<=N, favcount:<N, favcount:>=N, favcount:>N
 */
object BlacklistHelper {
    
    /**
     * Data class for parsed blacklist entry
     * Like ii.a in the original app
     */
    data class BlacklistEntry(
        val positiveTags: List<String>,  // Tags that must match (AND logic)
        val negativeTags: List<String>   // Tags that prevent match
    )
    
    /**
     * Parse the raw blacklist text into structured entries
     * Like ii.a.a() in the original app
     */
    fun parseBlacklist(rawText: String): List<BlacklistEntry> {
        if (rawText.isBlank()) return emptyList()
        
        return rawText.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") } // Skip empty lines and comments
            .map { line ->
                val parts = line.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                val positive = parts.filter { !it.startsWith("-") }
                val negative = parts.filter { it.startsWith("-") }.map { it.removePrefix("-") }
                BlacklistEntry(positive, negative)
            }
    }
    
    /**
     * Check if a Post is blacklisted
     * Like ii.m.k() in the original app
     * 
     * @param post The post to check
     * @param prefs PreferencesManager to get blacklist settings
     * @return true if post is blacklisted, false otherwise
     */
    fun isPostBlacklisted(post: Post, prefs: PreferencesManager): Boolean {
        // Check if blacklist is enabled
        if (!prefs.blacklistEnabled) return false
        
        val rawBlacklist = prefs.getBlacklistRaw()
        if (rawBlacklist.isBlank()) return false
        
        val entries = parseBlacklist(rawBlacklist)
        if (entries.isEmpty()) return false
        
        // Collect all tags from the post
        val postTags = collectPostTags(post)
        val rating = post.rating.lowercase()
        val score = post.score.total
        val favCount = post.favCount
        
        // Check each blacklist entry
        for (entry in entries) {
            val positiveMatch = checkPositiveMatch(entry.positiveTags, postTags, rating, score, favCount)
            val negativeMatch = checkNegativeMatch(entry.negativeTags, postTags, rating, score, favCount)
            
            // Entry matches if all positive tags match AND no negative tags match
            if (positiveMatch && !negativeMatch) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Check if a Post (from JSON) is blacklisted
     * For use in FollowingJobService
     */
    fun isPostBlacklistedFromJson(postJson: JSONObject, prefs: PreferencesManager): Boolean {
        // Check if blacklist is enabled
        if (!prefs.blacklistEnabled) return false
        
        val rawBlacklist = prefs.getBlacklistRaw()
        if (rawBlacklist.isBlank()) return false
        
        val entries = parseBlacklist(rawBlacklist)
        if (entries.isEmpty()) return false
        
        // Collect all tags from JSON
        val postTags = collectTagsFromJson(postJson)
        val rating = postJson.optString("rating", "s").lowercase()
        val scoreObj = postJson.optJSONObject("score")
        val score = scoreObj?.optInt("total", 0) ?: 0
        val favCount = postJson.optInt("fav_count", 0)
        
        // Check each blacklist entry
        for (entry in entries) {
            val positiveMatch = checkPositiveMatch(entry.positiveTags, postTags, rating, score, favCount)
            val negativeMatch = checkNegativeMatch(entry.negativeTags, postTags, rating, score, favCount)
            
            if (positiveMatch && !negativeMatch) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get the list of blacklist entries that match a post
     * Used for showing dialog with matching tags
     */
    fun getMatchingBlacklistEntries(post: Post, prefs: PreferencesManager): List<String> {
        if (!prefs.blacklistEnabled) return emptyList()
        
        val rawBlacklist = prefs.getBlacklistRaw()
        if (rawBlacklist.isBlank()) return emptyList()
        
        val entries = parseBlacklist(rawBlacklist)
        if (entries.isEmpty()) return emptyList()
        
        val postTags = collectPostTags(post)
        val rating = post.rating.lowercase()
        val score = post.score.total
        val favCount = post.favCount
        
        val matchingEntries = mutableListOf<String>()
        
        for (entry in entries) {
            val positiveMatch = checkPositiveMatch(entry.positiveTags, postTags, rating, score, favCount)
            val negativeMatch = checkNegativeMatch(entry.negativeTags, postTags, rating, score, favCount)
            
            if (positiveMatch && !negativeMatch) {
                // Format the entry as readable string
                val entryStr = entry.positiveTags.joinToString(" ")
                matchingEntries.add(entryStr)
            }
        }
        
        return matchingEntries
    }
    
    /**
     * Collect all tags from a Post into a set
     */
    private fun collectPostTags(post: Post): Set<String> {
        val tags = mutableSetOf<String>()
        
        post.tags.general.forEach { tags.add(it.lowercase()) }
        post.tags.species.forEach { tags.add(it.lowercase()) }
        post.tags.character.forEach { tags.add(it.lowercase()) }
        post.tags.artist.forEach { tags.add(it.lowercase()) }
        post.tags.copyright.forEach { tags.add(it.lowercase()) }
        post.tags.lore.forEach { tags.add(it.lowercase()) }
        post.tags.meta.forEach { tags.add(it.lowercase()) }
        
        return tags
    }
    
    /**
     * Collect all tags from JSON
     */
    private fun collectTagsFromJson(postJson: JSONObject): Set<String> {
        val tags = mutableSetOf<String>()
        val tagsObj = postJson.optJSONObject("tags") ?: return tags
        
        val categories = arrayOf("general", "species", "character", "artist", "copyright", "lore", "meta")
        for (category in categories) {
            val tagArray = tagsObj.optJSONArray(category) ?: continue
            for (i in 0 until tagArray.length()) {
                val tagName = tagArray.optString(i, "")
                if (tagName.isNotEmpty()) {
                    tags.add(tagName.lowercase())
                }
            }
        }
        
        return tags
    }
    
    /**
     * Check if all positive tags in an entry match the post
     * Like ii.m.m() in the original app
     */
    private fun checkPositiveMatch(
        positiveTags: List<String>,
        postTags: Set<String>,
        rating: String,
        score: Int,
        favCount: Int
    ): Boolean {
        if (positiveTags.isEmpty()) return false
        
        var matchCount = 0
        
        for (tag in positiveTags) {
            val tagLower = tag.lowercase()
            
            when {
                // Rating checks
                tagLower == "rating:e" || tagLower == "rating:explicit" -> {
                    if (rating == "e") matchCount++
                }
                tagLower == "rating:q" || tagLower == "rating:questionable" -> {
                    if (rating == "q") matchCount++
                }
                tagLower == "rating:s" || tagLower == "rating:safe" -> {
                    if (rating == "s") matchCount++
                }
                
                // Score checks
                tagLower.startsWith("score:<=") -> {
                    val value = tagLower.removePrefix("score:<=").toIntOrNull()
                    if (value != null && score <= value) matchCount++
                }
                tagLower.startsWith("score:<") -> {
                    val value = tagLower.removePrefix("score:<").toIntOrNull()
                    if (value != null && score < value) matchCount++
                }
                tagLower.startsWith("score:>=") -> {
                    val value = tagLower.removePrefix("score:>=").toIntOrNull()
                    if (value != null && score >= value) matchCount++
                }
                tagLower.startsWith("score:>") -> {
                    val value = tagLower.removePrefix("score:>").toIntOrNull()
                    if (value != null && score > value) matchCount++
                }
                
                // Favcount checks
                tagLower.startsWith("favcount:<=") -> {
                    val value = tagLower.removePrefix("favcount:<=").toIntOrNull()
                    if (value != null && favCount <= value) matchCount++
                }
                tagLower.startsWith("favcount:<") -> {
                    val value = tagLower.removePrefix("favcount:<").toIntOrNull()
                    if (value != null && favCount < value) matchCount++
                }
                tagLower.startsWith("favcount:>=") -> {
                    val value = tagLower.removePrefix("favcount:>=").toIntOrNull()
                    if (value != null && favCount >= value) matchCount++
                }
                tagLower.startsWith("favcount:>") -> {
                    val value = tagLower.removePrefix("favcount:>").toIntOrNull()
                    if (value != null && favCount > value) matchCount++
                }
                
                // Regular tag check
                else -> {
                    if (postTags.contains(tagLower)) matchCount++
                }
            }
        }
        
        // All positive tags must match
        return matchCount >= positiveTags.size
    }
    
    /**
     * Check if any negative tag matches
     */
    private fun checkNegativeMatch(
        negativeTags: List<String>,
        postTags: Set<String>,
        rating: String,
        score: Int,
        favCount: Int
    ): Boolean {
        if (negativeTags.isEmpty()) return false
        
        for (tag in negativeTags) {
            val tagLower = tag.lowercase()
            
            when {
                // Rating checks
                tagLower == "rating:e" || tagLower == "rating:explicit" -> {
                    if (rating == "e") return true
                }
                tagLower == "rating:q" || tagLower == "rating:questionable" -> {
                    if (rating == "q") return true
                }
                tagLower == "rating:s" || tagLower == "rating:safe" -> {
                    if (rating == "s") return true
                }
                
                // Regular tag check
                else -> {
                    if (postTags.contains(tagLower)) return true
                }
            }
        }
        
        return false
    }
}
