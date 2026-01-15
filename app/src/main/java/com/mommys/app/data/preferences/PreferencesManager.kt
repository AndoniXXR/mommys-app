package com.mommys.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manager para SharedPreferences
 * Basado en la estructura de la app original que usa múltiples archivos de preferencias
 */
class PreferencesManager(context: Context) {
    
    companion object {
        // Preference file names
        private const val PREF_USER_INFO = "user_info"
        private const val PREF_USER_PREFERENCES = "user_preferences"
        private const val PREF_CONSENT = "consent"
        private const val PREF_PIN = "pin_settings"
        
        // User info keys
        private const val KEY_ACCEPTED = "accepted"
        private const val KEY_ACCEPTED_WHEN = "accepted_when"
        private const val KEY_USERNAME = "username"
        private const val KEY_API_KEY = "api_key"
        
        // General preferences keys
        private const val KEY_THEME = "theme"
        private const val KEY_USE_E621 = "use_e621"
        private const val KEY_GRID_COLUMNS = "grid_width"
        private const val KEY_AUTOPLAY_VIDEO = "autoplay_video"
        private const val KEY_MUTE_VIDEO = "mute_video"
        private const val KEY_SHOW_POST_INFO = "show_post_info"
        private const val KEY_BLACKLIST = "blacklist"
        private const val KEY_LANGUAGE = "language"
        
        // Filter keys (como la app original usa filter_rating, filter_order, filter_type)
        private const val KEY_FILTER_RATING = "filter_rating"
        private const val KEY_FILTER_ORDER = "filter_order"
        private const val KEY_FILTER_TYPE = "filter_type"
        private const val KEY_LAST_SEARCH = "last_search"
        private const val KEY_LAST_SEARCH_PAGE = "last_search_page"
        private const val KEY_SAVED_ORDER = "saved_order"
        
        // PIN keys (matching original app)
        private const val KEY_PIN = "pin"
        private const val KEY_PIN_UNLOCK = "consent_pin_unlock"
        private const val KEY_PIN_APP_LINK = "consent_pin_app_link"
        private const val KEY_PIN_BIOMETRICS = "consent_biometrics"
        private const val KEY_PIN_AUTO_LOCK = "consent_pin_auto_lock"
        private const val KEY_PIN_AUTO_LOCK_INSTANTLY = "consent_pin_auto_lock_instantly"
        
        // Consent keys
        private const val KEY_CONSENT_ABOVE_18 = "consent_above_18"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
    }
    
    private val userInfoPrefs: SharedPreferences = 
        context.getSharedPreferences(PREF_USER_INFO, Context.MODE_PRIVATE)
    
    private val userPrefs: SharedPreferences = 
        context.getSharedPreferences(PREF_USER_PREFERENCES, Context.MODE_PRIVATE)
    
    private val consentPrefs: SharedPreferences = 
        context.getSharedPreferences(PREF_CONSENT, Context.MODE_PRIVATE)
    
    private val pinPrefs: SharedPreferences = 
        context.getSharedPreferences(PREF_PIN, Context.MODE_PRIVATE)
    
    /**
     * Obtener acceso a userPrefs para operaciones especiales como last_ad
     * Como se usa en la app original para ads
     */
    fun getSharedPreferences(): SharedPreferences = userPrefs
    
    // ==================== USER INFO ====================
    
    fun hasAccepted(): Boolean = userInfoPrefs.getBoolean(KEY_ACCEPTED, false)
    
    fun setAccepted(accepted: Boolean) {
        userInfoPrefs.edit {
            putBoolean(KEY_ACCEPTED, accepted)
            putLong(KEY_ACCEPTED_WHEN, System.currentTimeMillis())
        }
    }
    
    fun getUsername(): String? = userInfoPrefs.getString(KEY_USERNAME, null)
    
    fun getApiKey(): String? = userInfoPrefs.getString(KEY_API_KEY, null)
    
    fun setCredentials(username: String?, apiKey: String?) {
        userInfoPrefs.edit {
            putString(KEY_USERNAME, username)
            putString(KEY_API_KEY, apiKey)
        }
    }
    
    fun isLoggedIn(): Boolean = getUsername() != null && getApiKey() != null
    
    fun logout() {
        userInfoPrefs.edit {
            remove(KEY_USERNAME)
            remove(KEY_API_KEY)
        }
    }
    
    // ==================== PREFERENCES ====================
    
    // Note: getTheme/setTheme removed - use getThemeMode/setThemeMode instead (INT, not STRING)
    
    // useE621 reads from general_change_host like original app
    fun useE621(): Boolean = getHost() == "e621.net"
    
    fun setUseE621(use: Boolean) {
        // This updates the host preference to match
        setHost(if (use) "e621.net" else "e926.net")
    }
    
    fun getGridColumns(): Int = userPrefs.getInt(KEY_GRID_COLUMNS, 3)
    
    fun setGridColumns(columns: Int) {
        userPrefs.edit { putInt(KEY_GRID_COLUMNS, columns) }
    }
    
    fun getAutoplayVideo(): Boolean = userPrefs.getBoolean(KEY_AUTOPLAY_VIDEO, true)
    
    fun setAutoplayVideo(autoplay: Boolean) {
        userPrefs.edit { putBoolean(KEY_AUTOPLAY_VIDEO, autoplay) }
    }
    
    fun getMuteVideo(): Boolean = userPrefs.getBoolean(KEY_MUTE_VIDEO, true)
    
    fun setMuteVideo(mute: Boolean) {
        userPrefs.edit { putBoolean(KEY_MUTE_VIDEO, mute) }
    }
    
    fun getShowPostInfo(): Boolean = userPrefs.getBoolean(KEY_SHOW_POST_INFO, true)
    
    fun setShowPostInfo(show: Boolean) {
        userPrefs.edit { putBoolean(KEY_SHOW_POST_INFO, show) }
    }
    
    fun getLanguage(): String = userPrefs.getString(KEY_LANGUAGE, "en") ?: "en"
    
    fun setLanguage(language: String) {
        userPrefs.edit { putString(KEY_LANGUAGE, language) }
    }
    
    // ==================== FILTERS (como la app original h7/d.java) ====================
    
    /**
     * Obtiene el filtro de rating (bitwise: 1=e, 2=q, 4=s, 7=all)
     * Default 7 = mostrar todo
     */
    fun getFilterRating(): Int {
        val value = userPrefs.getInt(KEY_FILTER_RATING, 7)
        return if (value < 1 || value > 7) 7 else value
    }
    
    /**
     * Guarda el filtro de rating
     * @param showSafe mostrar safe (s)
     * @param showQuestionable mostrar questionable (q)
     * @param showExplicit mostrar explicit (e)
     */
    fun setFilterRating(showSafe: Boolean, showQuestionable: Boolean, showExplicit: Boolean) {
        var value = 0
        if (showSafe) value += 4
        if (showQuestionable) value += 2
        if (showExplicit) value += 1
        if (value < 1) value = 7 // Si nada seleccionado, mostrar todo
        userPrefs.edit { putInt(KEY_FILTER_RATING, value) }
    }
    
    /**
     * Obtiene el tipo de orden (0=newest, 1=oldest, 2=score, 3=favs)
     */
    fun getFilterOrder(): Int {
        val value = userPrefs.getInt(KEY_FILTER_ORDER, 0)
        return if (value < 0 || value > 3) 0 else value
    }
    
    fun setFilterOrder(order: Int) {
        userPrefs.edit { putInt(KEY_FILTER_ORDER, order.coerceIn(0, 3)) }
    }
    
    /**
     * Obtiene el filtro de tipo (0=all, 1=images, 2=videos, 3=gifs)
     */
    fun getFilterType(): Int {
        val value = userPrefs.getInt(KEY_FILTER_TYPE, 0)
        return if (value < 0 || value > 3) 0 else value
    }
    
    fun setFilterType(type: Int) {
        userPrefs.edit { putInt(KEY_FILTER_TYPE, type.coerceIn(0, 3)) }
    }
    
    /**
     * Guarda/obtiene la última búsqueda
     */
    fun getLastSearch(): String = userPrefs.getString(KEY_LAST_SEARCH, "") ?: ""
    
    fun setLastSearch(query: String) {
        userPrefs.edit { putString(KEY_LAST_SEARCH, query) }
    }
    
    fun getLastSearchPage(): Int = userPrefs.getInt(KEY_LAST_SEARCH_PAGE, 1)
    
    fun setLastSearchPage(page: Int) {
        userPrefs.edit { putInt(KEY_LAST_SEARCH_PAGE, page) }
    }
    
    /**
     * Orden de búsquedas guardadas (0=date_used, 1=date_created, 2=name, 3=tags)
     */
    fun getSavedOrder(): Int {
        val value = userPrefs.getInt(KEY_SAVED_ORDER, 0)
        return if (value < 0 || value > 3) 0 else value
    }
    
    fun setSavedOrder(order: Int) {
        userPrefs.edit { putInt(KEY_SAVED_ORDER, order.coerceIn(0, 3)) }
    }
    
    // ==================== BLACKLIST ====================
    
    fun getBlacklist(): Set<String> {
        return userPrefs.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()
    }
    
    fun setBlacklist(tags: Set<String>) {
        userPrefs.edit { putStringSet(KEY_BLACKLIST, tags) }
    }
    
    fun addToBlacklist(tag: String) {
        val current = getBlacklist().toMutableSet()
        current.add(tag)
        setBlacklist(current)
    }
    
    fun removeFromBlacklist(tag: String) {
        val current = getBlacklist().toMutableSet()
        current.remove(tag)
        setBlacklist(current)
    }
    
    // ==================== PIN (matching original app - uses userPrefs) ====================
    
    fun useBiometric(): Boolean = userPrefs.getBoolean(KEY_PIN_BIOMETRICS, true)
    
    fun setUseBiometric(use: Boolean) {
        userPrefs.edit { putBoolean(KEY_PIN_BIOMETRICS, use) }
    }
    
    fun getAutoLock(): Boolean = userPrefs.getBoolean(KEY_PIN_AUTO_LOCK, false)
    
    fun setAutoLock(autoLock: Boolean) {
        userPrefs.edit { putBoolean(KEY_PIN_AUTO_LOCK, autoLock) }
    }
    
    // PIN App Link (requires PIN for e621 links)
    var pinAppLink: Boolean
        get() = userPrefs.getBoolean(KEY_PIN_APP_LINK, true)
        set(value) { userPrefs.edit { putBoolean(KEY_PIN_APP_LINK, value) } }
    
    // PIN Auto Lock
    var pinAutoLock: Boolean
        get() = userPrefs.getBoolean(KEY_PIN_AUTO_LOCK, false)
        set(value) { userPrefs.edit { putBoolean(KEY_PIN_AUTO_LOCK, value) } }
    
    // PIN Auto Lock Instantly
    var pinAutoLockInstantly: Boolean
        get() = userPrefs.getBoolean(KEY_PIN_AUTO_LOCK_INSTANTLY, false)
        set(value) { userPrefs.edit { putBoolean(KEY_PIN_AUTO_LOCK_INSTANTLY, value) } }

    // ==================== CONSENT ====================
    // Note: consent_above_18 is stored in user_info like the original app
    
    fun isAbove18(): Boolean = userInfoPrefs.getBoolean(KEY_CONSENT_ABOVE_18, false)
    
    fun setAbove18(above: Boolean) {
        userInfoPrefs.edit { putBoolean(KEY_CONSENT_ABOVE_18, above) }
    }
    
    fun isAnalyticsEnabled(): Boolean = consentPrefs.getBoolean(KEY_ANALYTICS_ENABLED, false)
    
    fun setAnalyticsEnabled(enabled: Boolean) {
        consentPrefs.edit { putBoolean(KEY_ANALYTICS_ENABLED, enabled) }
    }
    
    // ==================== PROPERTY-STYLE ACCESSORS ====================
    // For compatibility with Activities that use property syntax
    
    @get:JvmName("getUsernameValue")
    @set:JvmName("setUsernameValue")
    var username: String
        get() = userInfoPrefs.getString(KEY_USERNAME, null) ?: ""
        set(value) { userInfoPrefs.edit { putString(KEY_USERNAME, value) } }
    
    @get:JvmName("getApiKeyValue")
    @set:JvmName("setApiKeyValue")
    var apiKey: String
        get() = userInfoPrefs.getString(KEY_API_KEY, null) ?: ""
        set(value) { userInfoPrefs.edit { putString(KEY_API_KEY, value) } }
    
    @get:JvmName("isUserLoggedIn")
    var isLoggedIn: Boolean
        get() = getUsername() != null && getApiKey() != null
        set(value) { if (!value) logout() }
    
    var darkMode: Boolean
        get() = getThemeMode() == 2 // 2 = dark mode
        set(value) = setThemeMode(if (value) 2 else 1) // 2=dark, 1=light
    
    @get:JvmName("getGridColumnsValue")
    @set:JvmName("setGridColumnsValue")
    var gridColumns: Int
        get() = userPrefs.getInt(KEY_GRID_COLUMNS, 3)
        set(value) { userPrefs.edit { putInt(KEY_GRID_COLUMNS, value) } }
    
    var safeMode: Boolean
        get() = !useE621()
        set(value) = setUseE621(!value)
    
    // Autoplay videos en Post view - key: post_autoplay_videos
    var autoPlayVideos: Boolean
        get() = userPrefs.getBoolean("post_autoplay_videos", false)
        set(value) { userPrefs.edit { putBoolean("post_autoplay_videos", value) } }
    
    // Posición del botón back en PostActivity: "left" o "right"
    var backButtonPosition: String
        get() = userPrefs.getString("back_button_position", "left") ?: "left"
        set(value) { userPrefs.edit { putString("back_button_position", value) } }
    
    // PIN stored as INT (0-9999), -1 means disabled (matching original app)
    var pinValue: Int
        get() = userPrefs.getInt(KEY_PIN, -1)
        set(value) { userPrefs.edit { putInt(KEY_PIN, value) } }
    
    fun isPinSet(): Boolean {
        val pin = pinValue
        return pin >= 0 && pin <= 9999
    }
    
    fun clearPin() {
        userPrefs.edit { putInt(KEY_PIN, -1) }
    }
    
    private fun formatPin(pin: Int): String {
        val sb = StringBuilder(pin.toString())
        while (sb.length < 4) {
            sb.insert(0, "0")
        }
        return sb.toString()
    }
    
    // PIN code as formatted string (read-only for display)
    val pinCode: String
        get() {
            val pin = pinValue
            return if (pin >= 0 && pin <= 9999) formatPin(pin) else ""
        }
    
    // ==================== GRID PREFERENCES ====================
    
    var gridHeight: Int
        get() = userPrefs.getInt("grid_height", 110)
        set(value) { userPrefs.edit { putInt("grid_height", value) } }
    
    var postsPerPage: Int
        get() = userPrefs.getInt("search_posts_count", 75)
        set(value) { userPrefs.edit { putInt("search_posts_count", value) } }
    
    // Calcula el ratio de aspecto para las imágenes del grid (grid_height / 100.0)
    val gridAspectRatio: Double
        get() = gridHeight / 100.0
    
    var gridStats: Boolean
        get() = userPrefs.getBoolean("grid_stats", true)
        set(value) { userPrefs.edit { putBoolean("grid_stats", value) } }
    
    var gridInfo: Boolean
        get() = userPrefs.getBoolean("grid_info", true)
        set(value) { userPrefs.edit { putBoolean("grid_info", value) } }
    
    var gridColours: Boolean
        get() = userPrefs.getBoolean("grid_colours", true)
        set(value) { userPrefs.edit { putBoolean("grid_colours", value) } }
    
    var gridGifs: Boolean
        get() = userPrefs.getBoolean("grid_gifs", false)
        set(value) { userPrefs.edit { putBoolean("grid_gifs", value) } }
    
    var gridDarkenSeen: Boolean
        get() = userPrefs.getBoolean("grid_darken_seen", true)
        set(value) { userPrefs.edit { putBoolean("grid_darken_seen", value) } }
    
    var gridHideSeen: Boolean
        get() = userPrefs.getBoolean("grid_hide_seen", false)
        set(value) { userPrefs.edit { putBoolean("grid_hide_seen", value) } }
    
    var gridNewLabel: Boolean
        get() = userPrefs.getBoolean("grid_new_label", true)
        set(value) { userPrefs.edit { putBoolean("grid_new_label", value) } }
    
    var gridRefresh: Boolean
        get() = userPrefs.getBoolean("grid_refresh", false)
        set(value) { userPrefs.edit { putBoolean("grid_refresh", value) } }
    
    var gridNavigate: Boolean
        get() = userPrefs.getBoolean("grid_navigate", false)
        set(value) { userPrefs.edit { putBoolean("grid_navigate", value) } }
    
    // ==================== POST PREFERENCES ====================
    
    var postExpandTags: Boolean
        get() = userPrefs.getBoolean("post_expand_tags", true)
        set(value) { userPrefs.edit { putBoolean("post_expand_tags", value) } }
    
    var postExpandDescription: Boolean
        get() = userPrefs.getBoolean("post_expand_description", true)
        set(value) { userPrefs.edit { putBoolean("post_expand_description", value) } }
    
    var postHideComments: Boolean
        get() = userPrefs.getBoolean("post_hide_comments", false)
        set(value) { userPrefs.edit { putBoolean("post_hide_comments", value) } }
    
    var postHideScore: Boolean
        get() = userPrefs.getBoolean("post_hide_score", false)
        set(value) { userPrefs.edit { putBoolean("post_hide_score", value) } }
    
    var postBackButton: Boolean
        get() = userPrefs.getBoolean("post_back_button", true)
        set(value) { userPrefs.edit { putBoolean("post_back_button", value) } }
    
    // Back button location: "0"=Left (default), "1"=Right
    var postBackButtonLocation: Int
        get() = userPrefs.getString("post_back_button_location", "0")?.toIntOrNull() ?: 0
        set(value) { userPrefs.edit { putString("post_back_button_location", value.toString()) } }
    
    var postEdgeNavigation: Boolean
        get() = userPrefs.getBoolean("post_edge_navigation", true)
        set(value) { userPrefs.edit { putBoolean("post_edge_navigation", value) } }
    
    var postPullToClose: Boolean
        get() = userPrefs.getBoolean("post_pull_to_close", true)
        set(value) { userPrefs.edit { putBoolean("post_pull_to_close", value) } }
    
    // Video Quality: "0"=Original, "1"=720p (default), "2"=480p
    // La app original mapea: 0→1 (original), 1→2 (720p), 2→3 (480p)
    var postVideoQuality: Int
        get() = userPrefs.getString("post_default_video_quality", "1")?.toIntOrNull() ?: 1
        set(value) { userPrefs.edit { putString("post_default_video_quality", value.toString()) } }
    
    var postMuteVideos: Boolean
        get() = userPrefs.getBoolean("post_mute_videos", false)
        set(value) { userPrefs.edit { putBoolean("post_mute_videos", value) } }
    
    var postFullscreenVideos: Boolean
        get() = userPrefs.getBoolean("post_fullscreen_videos", false)
        set(value) { userPrefs.edit { putBoolean("post_fullscreen_videos", value) } }
    
    var postKeepScreenAwake: Boolean
        get() = userPrefs.getBoolean("post_keep_screen_awake", false)
        set(value) { userPrefs.edit { putBoolean("post_keep_screen_awake", value) } }
    
    var postLoadHq: Boolean
        get() = userPrefs.getBoolean("post_load_hq", true)
        set(value) { userPrefs.edit { putBoolean("post_load_hq", value) } }
    
    var postDataSaver: Boolean
        get() = userPrefs.getBoolean("post_data_saver", false)
        set(value) { userPrefs.edit { putBoolean("post_data_saver", value) } }
    
    var postUpvoteOnFav: Boolean
        get() = userPrefs.getBoolean("post_action_upvote_on_fav", false)
        set(value) { userPrefs.edit { putBoolean("post_action_upvote_on_fav", value) } }
    
    var postUpvoteOnDownload: Boolean
        get() = userPrefs.getBoolean("post_action_upvote_on_download", false)
        set(value) { userPrefs.edit { putBoolean("post_action_upvote_on_download", value) } }
    
    var postFavOnDownload: Boolean
        get() = userPrefs.getBoolean("post_action_fav_on_download", false)
        set(value) { userPrefs.edit { putBoolean("post_action_fav_on_download", value) } }
    
    var postHideStatusBar: Boolean
        get() = userPrefs.getBoolean("post_hide_status_bar", false)
        set(value) { userPrefs.edit { putBoolean("post_hide_status_bar", value) } }
    
    var postHideNavBar: Boolean
        get() = userPrefs.getBoolean("post_hide_nav_bar", false)
        set(value) { userPrefs.edit { putBoolean("post_hide_nav_bar", value) } }
    
    // Video Format: "0"=WebM (default), "1"=MP4
    // La app original mapea: 0→1 (webm), 1→2 (mp4)
    var postVideoFormat: Int
        get() = userPrefs.getString("post_default_video_format", "0")?.toIntOrNull() ?: 0
        set(value) { userPrefs.edit { putString("post_default_video_format", value.toString()) } }
    
    var postLandscapeVideos: Boolean
        get() = userPrefs.getBoolean("post_landscape_videos", false)
        set(value) { userPrefs.edit { putBoolean("post_landscape_videos", value) } }
    
    var postLongClickToUnfav: Boolean
        get() = userPrefs.getBoolean("post_long_click_to_unfav", false)
        set(value) { userPrefs.edit { putBoolean("post_long_click_to_unfav", value) } }
    
    var postTopPreview: Boolean
        get() = userPrefs.getBoolean("post_top_preview", true)
        set(value) { userPrefs.edit { putBoolean("post_top_preview", value) } }
    
    var postControlsFullscreen: Boolean
        get() = userPrefs.getBoolean("post_controls_fullscreen", true)
        set(value) { userPrefs.edit { putBoolean("post_controls_fullscreen", value) } }
    
    var postDisableShare: Boolean
        get() = userPrefs.getBoolean("post_disable_share", false)
        set(value) { userPrefs.edit { putBoolean("post_disable_share", value) } }
    
    // ==================== SEARCH PREFERENCES ====================
    
    var searchHistory: Boolean
        get() = userPrefs.getBoolean("search_history", true)
        set(value) { userPrefs.edit { putBoolean("search_history", value) } }
    
    var searchSuggestions: Boolean
        get() = userPrefs.getBoolean("search_suggestions", true)
        set(value) { userPrefs.edit { putBoolean("search_suggestions", value) } }
    
    var searchSavedNewWindow: Boolean
        get() = userPrefs.getBoolean("search_saved_new_window", true)
        set(value) { userPrefs.edit { putBoolean("search_saved_new_window", value) } }
    
    var searchLastOnStart: Boolean
        get() = userPrefs.getBoolean("search_last_on_start", true)
        set(value) { userPrefs.edit { putBoolean("search_last_on_start", value) } }
    
    var searchNewestFirst: Boolean
        get() = userPrefs.getBoolean("search_newest_first", false)
        set(value) { userPrefs.edit { putBoolean("search_newest_first", value) } }
    
    var searchInNewTask: Boolean
        get() = userPrefs.getBoolean("search_in_new_task", true)
        set(value) { userPrefs.edit { putBoolean("search_in_new_task", value) } }
    
    var gridFavOrder: Boolean
        get() = userPrefs.getBoolean("grid_fav_order", true)
        set(value) { userPrefs.edit { putBoolean("grid_fav_order", value) } }
    
    var searchIncludeFlash: Boolean
        get() = userPrefs.getBoolean("search_include_flash", false)
        set(value) { userPrefs.edit { putBoolean("search_include_flash", value) } }
    
    fun clearSearchHistory() {
        userPrefs.edit { 
            remove("search_history_list")
            remove(KEY_LAST_SEARCH)
        }
    }
    
    // ==================== STORAGE PREFERENCES ====================
    
    // Default false: usar Download Manager por defecto (como app original)
    var storageCustomFolderEnabled: Boolean
        get() = userPrefs.getBoolean("storage_custom_folder_enabled", false)
        set(value) { userPrefs.edit { putBoolean("storage_custom_folder_enabled", value) } }
    
    var storageCustomFolder: String
        get() = userPrefs.getString("storage_custom_folder", "") ?: ""
        set(value) { userPrefs.edit { putString("storage_custom_folder", value) } }
    
    var storageFileNameMask: String
        get() = userPrefs.getString("storage_file_name_mask", "%artist%-%id%") ?: "%artist%-%id%"
        set(value) { userPrefs.edit { putString("storage_file_name_mask", value) } }
    
    var storageOverwrite: Boolean
        get() = userPrefs.getBoolean("storage_overwrite", true)
        set(value) { userPrefs.edit { putBoolean("storage_overwrite", value) } }
    
    var storageHide: Boolean
        get() = userPrefs.getBoolean("storage_hide", false)
        set(value) { userPrefs.edit { putBoolean("storage_hide", value) } }
    
    // Default true: cache limitado habilitado por defecto (como app original)
    var storageMaxCache: Boolean
        get() = userPrefs.getBoolean("storage_max_cache", true)
        set(value) { userPrefs.edit { putBoolean("storage_max_cache", value) } }
    
    // Usar la key del XML: storage_max_cache_slider (como en preferences_storage.xml)
    var storageMaxCacheSlider: Int
        get() = userPrefs.getInt("storage_max_cache_slider", 500)
        set(value) { userPrefs.edit { putInt("storage_max_cache_slider", value) } }
    
    // ==================== GENERAL EXTRA PREFERENCES ====================
    
    var blacklistEnabled: Boolean
        get() = userPrefs.getBoolean("general_blacklist_enabled", true)
        set(value) { userPrefs.edit { putBoolean("general_blacklist_enabled", value) } }
    
    var blacklistPoolPosts: Boolean
        get() = userPrefs.getBoolean("general_blacklist_pool_posts", false)
        set(value) { userPrefs.edit { putBoolean("general_blacklist_pool_posts", value) } }
    
    var hideInTasks: Boolean
        get() = userPrefs.getBoolean("general_hide_in_tasks", false)
        set(value) { userPrefs.edit { putBoolean("general_hide_in_tasks", value) } }
    
    var disguiseEnabled: Boolean
        get() = userPrefs.getBoolean("general_disguise", false)
        set(value) { userPrefs.edit { putBoolean("general_disguise", value) } }
    
    var startInSavedSearches: Boolean
        get() = userPrefs.getBoolean("general_start_in_saved", false)
        set(value) { userPrefs.edit { putBoolean("general_start_in_saved", value) } }

    // Host configuration
    fun getHost(): String = userPrefs.getString("general_change_host", "e926.net") ?: "e926.net"
    
    fun setHost(host: String) {
        userPrefs.edit { putString("general_change_host", host) }
    }
    
    // Theme mode (0=system, 1=light, 2=dark, 3=battery) - uses INT like original app
    fun getThemeMode(): Int = userPrefs.getInt("theme", 0)
    
    fun setThemeMode(mode: Int) {
        userPrefs.edit { putInt("theme", mode) }
    }
    
    // Post quality (0=preview, 1=sample, 2=original)
    fun getPostQuality(): Int {
        val value = userPrefs.getString("general_post_quality", "1") ?: "1"
        return value.toIntOrNull() ?: 1
    }
    
    fun setPostQuality(quality: Int) {
        userPrefs.edit { putString("general_post_quality", quality.toString()) }
    }
    
    // Thumbnail quality (0=preview, 1=sample, 2=original)
    fun getThumbQuality(): Int {
        val value = userPrefs.getString("general_thumb_quality", "0") ?: "0"
        return value.toIntOrNull() ?: 0
    }
    
    fun setThumbQuality(quality: Int) {
        userPrefs.edit { putString("general_thumb_quality", quality.toString()) }
    }
    
    // ==================== FOLLOWING PREFERENCES ====================
    
    var followingEnabled: Boolean
        get() = userPrefs.getBoolean("following_enabled", true)  // Default true
        set(value) { userPrefs.edit { putBoolean("following_enabled", value) } }
    
    // Track if we've asked for notification permission
    var notificationPermissionAsked: Boolean
        get() = userPrefs.getBoolean("notification_permission_asked", false)
        set(value) { userPrefs.edit { putBoolean("notification_permission_asked", value) } }
    
    var followingOnlyWifi: Boolean
        get() = userPrefs.getBoolean("following_only_wifi", false) // default false like original
        set(value) { userPrefs.edit { putBoolean("following_only_wifi", value) } }
    
    // Original uses STRING that is parsed to INT
    var followingPeriod: Int
        get() = (userPrefs.getString("following_period", "1") ?: "1").toIntOrNull() ?: 1
        set(value) { userPrefs.edit { putString("following_period", value.toString()) } }
    
    var followingDisplayTag: Boolean
        get() = userPrefs.getBoolean("following_display_tag", true)
        set(value) { userPrefs.edit { putBoolean("following_display_tag", value) } }
    
    var followingDisplayInSavedSearch: Boolean
        get() = userPrefs.getBoolean("following_display_in_saved_search", true)
        set(value) { userPrefs.edit { putBoolean("following_display_in_saved_search", value) } }
    
    // Tags being followed (stored as newline-separated string)
    var followingTags: String
        get() = userPrefs.getString("following_tags", "") ?: ""
        set(value) { userPrefs.edit { putString("following_tags", value) } }
    
    fun getFollowingTagsList(): List<String> {
        return followingTags.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    fun setFollowingTagsList(tags: List<String>) {
        followingTags = tags.joinToString("\n")
    }
    
    /**
     * Check if a tag is currently being followed
     */
    fun isTagFollowed(tag: String): Boolean {
        val normalizedTag = tag.trim().lowercase()
        return getFollowingTagsList().any { it.lowercase() == normalizedTag }
    }
    
    /**
     * Add a tag to the following list
     * @return true if tag was added, false if already followed
     */
    fun addFollowingTag(tag: String): Boolean {
        val normalizedTag = tag.trim()
        if (normalizedTag.isEmpty()) return false
        
        val currentTags = getFollowingTagsList().toMutableList()
        
        // Check if already followed (case-insensitive)
        if (currentTags.any { it.lowercase() == normalizedTag.lowercase() }) {
            return false
        }
        
        currentTags.add(normalizedTag)
        setFollowingTagsList(currentTags)
        return true
    }
    
    /**
     * Remove a tag from the following list
     * @return true if tag was removed, false if not found
     */
    fun removeFollowingTag(tag: String): Boolean {
        val normalizedTag = tag.trim().lowercase()
        val currentTags = getFollowingTagsList().toMutableList()
        
        val removed = currentTags.removeAll { it.lowercase() == normalizedTag }
        if (removed) {
            setFollowingTagsList(currentTags)
        }
        return removed
    }
    
    // Last update timestamp for following (flu = following last update)
    var followingLastUpdate: Long
        get() = userPrefs.getLong("flu", 0L)
        set(value) { userPrefs.edit { putLong("flu", value) } }
    
    // ==================== ADS PREFERENCES ====================
    // Keys con "3" al final como en la app original
    
    var adsBannerGrid: Boolean
        get() = userPrefs.getBoolean("ads_banner_grid3", true)
        set(value) { userPrefs.edit { putBoolean("ads_banner_grid3", value) } }
    
    var adsBannerPost: Boolean
        get() = userPrefs.getBoolean("ads_banner_post3", true)
        set(value) { userPrefs.edit { putBoolean("ads_banner_post3", value) } }
    
    // Default false como en la app original
    var adsInterstitial: Boolean
        get() = userPrefs.getBoolean("ads_interstitial3", false)
        set(value) { userPrefs.edit { putBoolean("ads_interstitial3", value) } }
    
    // ==================== PRIVACY PREFERENCES ====================
    // En la app original estas se guardan en un archivo de prefs separado (userInfo)
    // pero aquí usamos el mismo para simplificar
    
    var privacyCrashReports: Boolean
        get() = userPrefs.getBoolean("privacy_crash_reports", true)
        set(value) { userPrefs.edit { putBoolean("privacy_crash_reports", value) } }
    
    var privacyAnalytics: Boolean
        get() = userPrefs.getBoolean("privacy_analytics", true)
        set(value) { userPrefs.edit { putBoolean("privacy_analytics", value) } }
    
    // ==================== NETWORK/PROXY PREFERENCES ====================
    
    /**
     * Obtiene la configuración del proxy
     * @return ProxyConfig si está configurado, null si no
     */
    fun getProxyConfig(): ProxyConfig? {
        val host = userPrefs.getString("proxy_host", null)
        val port = userPrefs.getInt("proxy_port", -1)
        val username = userPrefs.getString("proxy_username", null)
        val password = userPrefs.getString("proxy_password", null)
        
        // Validar configuración
        if (host.isNullOrEmpty() || port < 0 || port > 65535 || !host.contains(".")) {
            return null
        }
        
        return ProxyConfig(host, port, username, password)
    }
    
    /**
     * Guarda la configuración del proxy
     * @param config ProxyConfig o null para deshabilitar
     */
    fun setProxyConfig(config: ProxyConfig?) {
        userPrefs.edit {
            if (config == null || config.host.isEmpty() || config.port < 0 || config.port > 65535 || !config.host.contains(".")) {
                putString("proxy_host", null)
                putInt("proxy_port", -1)
                putString("proxy_username", null)
                putString("proxy_password", null)
            } else {
                putString("proxy_host", config.host)
                putInt("proxy_port", config.port)
                putString("proxy_username", config.username)
                putString("proxy_password", config.password)
            }
        }
    }
    
    /**
     * Verifica si hay un proxy configurado
     */
    fun hasProxy(): Boolean = getProxyConfig() != null
    
    /**
     * Data class para configuración de proxy
     */
    data class ProxyConfig(
        val host: String,
        val port: Int,
        val username: String?,
        val password: String?
    )
    
    // ==================== VIEWED POSTS ====================
    
    private val viewedPostsKey = "viewed_posts"
    
    fun getViewedPosts(): Set<String> {
        return userPrefs.getStringSet(viewedPostsKey, emptySet()) ?: emptySet()
    }
    
    fun addViewedPost(postId: Int) {
        val current = getViewedPosts().toMutableSet()
        current.add(postId.toString())
        userPrefs.edit { putStringSet(viewedPostsKey, current) }
    }
    
    fun isPostViewed(postId: Int): Boolean {
        return getViewedPosts().contains(postId.toString())
    }
    
    fun clearViewedPosts() {
        userPrefs.edit { remove(viewedPostsKey) }
    }
    
    // ==================== BLACKLIST RAW (like original app) ====================
    
    /**
     * Get blacklist as raw text (each line is an entry, like original app)
     */
    fun getBlacklistRaw(): String {
        return userPrefs.getString("blacklist_raw", "") ?: ""
    }
    
    /**
     * Set blacklist from raw text
     */
    fun setBlacklistRaw(text: String) {
        userPrefs.edit { putString("blacklist_raw", text) }
        // Also update the Set version for compatibility
        val entries = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        setBlacklist(entries)
    }
    
    // ==================== COOKIES ====================
    
    /**
     * Guarda las cookies (usando la misma key "cookies" que la app original)
     * Se usan para pasar el challenge de Cloudflare
     * Como WebViewActivity.java -> case 5 en el OnClickListener
     */
    fun setCookies(cookies: String) {
        userPrefs.edit {
            putString("cookies", cookies)
        }
    }
    
    /**
     * Obtiene las cookies guardadas
     */
    fun getCookies(): String {
        return userPrefs.getString("cookies", "") ?: ""
    }
    
    /**
     * Verifica si hay cookies guardadas
     */
    fun hasCookies(): Boolean {
        return getCookies().isNotEmpty()
    }
    
    /**
     * Limpia las cookies
     */
    fun clearCookies() {
        userPrefs.edit {
            remove("cookies")
        }
    }
    
    // ==================== HELPER METHODS FOR POOL ACTIVITY ====================
    
    /**
     * Get grid width (columns)
     */
    fun getGridWidth(): Int = userPrefs.getInt(KEY_GRID_COLUMNS, 3)
    
    /**
     * Check if grid stats are enabled
     */
    fun isGridStatsEnabled(): Boolean = userPrefs.getBoolean("grid_stats", true)
    
    /**
     * Check if grid info button is enabled
     */
    fun isGridInfoEnabled(): Boolean = userPrefs.getBoolean("grid_info", true)
    
    /**
     * Check if grid rating colors are enabled
     */
    fun isGridColoursEnabled(): Boolean = userPrefs.getBoolean("grid_colours", true)
    
    /**
     * Check if share is disabled in settings
     */
    fun isShareDisabled(): Boolean = userPrefs.getBoolean("post_disable_share", false)
    
    /**
     * Check if explicit content (e621) is enabled
     */
    fun isExplicitEnabled(): Boolean = getHost() == "e621.net"
    
    /**
     * Get followed tags list (same as getFollowingTagsList but different name)
     */
    fun getFollowedTags(): List<String> = getFollowingTagsList()
    
    /**
     * Set followed tags list
     */
    fun setFollowedTags(tags: List<String>) = setFollowingTagsList(tags)
    
    // ==================== SAVED SEARCHES ====================
    
    private val savedSearchesKey = "saved_searches"
    
    /**
     * Add a saved search
     * Format: name|tags
     */
    fun addSavedSearch(name: String, tags: String) {
        val searches = getSavedSearches().toMutableList()
        searches.add(SavedSearch(name, tags, System.currentTimeMillis()))
        saveSavedSearches(searches)
    }
    
    /**
     * Get all saved searches
     */
    fun getSavedSearches(): List<SavedSearch> {
        val raw = userPrefs.getString(savedSearchesKey, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 2) {
                SavedSearch(
                    name = parts[0],
                    tags = parts[1],
                    lastUsed = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                )
            } else null
        }
    }
    
    /**
     * Save all saved searches
     */
    private fun saveSavedSearches(searches: List<SavedSearch>) {
        val raw = searches.joinToString("\n") { "${it.name}|${it.tags}|${it.lastUsed}" }
        userPrefs.edit { putString(savedSearchesKey, raw) }
    }
    
    data class SavedSearch(
        val name: String,
        val tags: String,
        val lastUsed: Long
    )
}
