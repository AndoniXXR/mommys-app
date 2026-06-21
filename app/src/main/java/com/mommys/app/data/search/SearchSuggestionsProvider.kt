package com.mommys.app.data.search

import android.app.SearchManager
import android.content.SearchRecentSuggestionsProvider
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Base64
import android.util.Log
import com.mommys.app.MommysApplication
import com.mommys.app.data.api.HttpConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ContentProvider para sugerencias de búsqueda.
 * Llamada directa a la API (query() corre en binder thread, no bloquea el UI).
 * Cache en memoria para que consultas repetidas sean instantáneas.
 * Fallback a tags locales si la API falla.
 */
class SearchSuggestionsProvider : SearchRecentSuggestionsProvider() {

    companion object {
        const val AUTHORITY = "com.mommys.app.search.provider"
        const val MODE = DATABASE_MODE_QUERIES or DATABASE_MODE_2LINES
        private const val TAG = "SearchSugProvider"
        private const val MAX_API_RESULTS = 20

        private val SUGGEST_COLUMNS = arrayOf(
            SearchManager.SUGGEST_COLUMN_FORMAT,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_QUERY,
            BaseColumns._ID
        )
    }

    private var suggestionsManager: SuggestionsManager? = null

    // Cache en memoria: término → resultados API
    private val apiCache = ConcurrentHashMap<String, List<CachedTag>>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .addInterceptor(HttpConfig.cloudflareHeaderInterceptor())
            .build()
    }

    private data class CachedTag(
        val name: String,
        val postCount: Int,
        val category: Int
    )

    init {
        setupSuggestions(AUTHORITY, MODE)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        if (suggestionsManager == null) {
            suggestionsManager = SuggestionsManager()
        }

        val query = selectionArgs?.firstOrNull()
            ?: uri.lastPathSegment?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: ""

        // 1. Historial reciente (framework SQLite)
        val recentCursor: Cursor? = try {
            super.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: Exception) {
            null
        }

        // 2. Sugerencias de tags
        val tagsCursor = getTagSuggestionsCursor(query)

        // 3. Combinar
        return if (recentCursor != null && tagsCursor.count > 0) {
            MergeCursor(arrayOf(recentCursor, tagsCursor))
        } else if (tagsCursor.count > 0) {
            tagsCursor
        } else {
            recentCursor ?: MatrixCursor(SUGGEST_COLUMNS)
        }
    }

    private fun getTagSuggestionsCursor(fullQuery: String): MatrixCursor {
        val manager = suggestionsManager ?: return MatrixCursor(SUGGEST_COLUMNS)

        val lastWord = fullQuery.trim().split("\\s+".toRegex()).lastOrNull() ?: ""
        if (lastWord.isEmpty()) return MatrixCursor(SUGGEST_COLUMNS)

        // Operadores: solo local
        if (manager.isOperator(lastWord) || lastWord.contains(":")) {
            return buildLocalCursor(lastWord, manager)
        }

        // 1. Cache hit → instantáneo
        val cached = findCached(lastWord)
        if (cached != null) return buildApiCursor(cached)

        // 2. Llamar API directamente (estamos en binder thread, no UI thread)
        val apiResult = callApi(lastWord)
        if (apiResult != null && apiResult.isNotEmpty()) {
            apiCache[lastWord] = apiResult
            trimCache()
            return buildApiCursor(apiResult)
        }

        // 3. Fallback: tags locales
        return buildLocalCursor(lastWord, manager)
    }

    /**
     * Busca en cache: exacto primero, luego filtra desde un prefijo cacheado
     */
    private fun findCached(term: String): List<CachedTag>? {
        apiCache[term]?.let { return it }

        for (i in term.length - 1 downTo 1) {
            val prefix = term.substring(0, i)
            val cached = apiCache[prefix]
            if (cached != null) {
                val filtered = cached.filter { it.name.contains(term, ignoreCase = true) }
                if (filtered.isNotEmpty()) return filtered
            }
        }
        return null
    }

    /**
     * Llamada síncrona a la API. Seguro porque query() corre en binder thread.
     */
    private fun callApi(searchTerm: String): List<CachedTag>? {
        try {
            val app = context?.applicationContext as? MommysApplication ?: return null
            val prefs = app.preferencesManager

            val baseUrl = if (prefs.useE621()) "https://e621.net" else "https://e926.net"
            val url = "$baseUrl/tags/autocomplete.json" +
                "?search[name_matches]=${Uri.encode(searchTerm)}*" +
                "&limit=$MAX_API_RESULTS"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()

            val username = prefs.getUsername()
            val apiKey = prefs.getApiKey()
            if (!username.isNullOrEmpty() && !apiKey.isNullOrEmpty() && !username.contains(":")) {
                val credentials = "$username:$apiKey"
                val basicAuth = "Basic " + Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", basicAuth)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            response.close()

            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) return null

            val tags = mutableListOf<CachedTag>()
            for (i in 0 until jsonArray.length()) {
                val tag = jsonArray.getJSONObject(i)
                tags.add(CachedTag(
                    name = tag.getString("name"),
                    postCount = tag.optInt("post_count", 0),
                    category = tag.optInt("category", 0)
                ))
            }
            return tags
        } catch (e: Exception) {
            Log.d(TAG, "API call failed: ${e.message}")
            return null
        }
    }

    private fun buildApiCursor(tags: List<CachedTag>): MatrixCursor {
        val cursor = MatrixCursor(SUGGEST_COLUMNS)
        var id = 20000L
        for (tag in tags) {
            val categoryText = when (tag.category) {
                0 -> "General"; 1 -> "Artista"; 3 -> "Copyright"
                4 -> "Personaje"; 5 -> "Especie"; 6 -> "Inválido"
                7 -> "Meta"; 8 -> "Lore"; else -> "Tag"
            }
            val subtitle = "$categoryText • ${formatPostCount(tag.postCount)}"
            cursor.addRow(arrayOf(0, android.R.drawable.ic_menu_search, tag.name, subtitle, tag.name, id++))
        }
        return cursor
    }

    private fun buildLocalCursor(lastWord: String, manager: SuggestionsManager): MatrixCursor {
        val cursor = MatrixCursor(SUGGEST_COLUMNS)
        var id = 10000L
        for (suggestion in manager.getSuggestions(lastWord, 50)) {
            val typeText = when (suggestion.type) {
                SuggestionType.OPERATOR -> "Operador"
                SuggestionType.TAG -> "Tag"
                SuggestionType.HISTORY -> "Reciente"
                SuggestionType.SAVED -> "Guardada"
            }
            val icon = when (suggestion.type) {
                SuggestionType.OPERATOR -> android.R.drawable.ic_menu_sort_by_size
                SuggestionType.TAG -> android.R.drawable.ic_menu_search
                else -> android.R.drawable.ic_menu_recent_history
            }
            cursor.addRow(arrayOf(0, icon, suggestion.text, typeText, suggestion.text, id++))
        }
        return cursor
    }

    private fun trimCache() {
        if (apiCache.size > 50) {
            apiCache.keys().toList().take(apiCache.size - 30).forEach { apiCache.remove(it) }
        }
    }

    private fun formatPostCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM posts", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK posts", count / 1_000.0)
            else -> "$count posts"
        }
    }
}
