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
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ContentProvider para sugerencias de búsqueda.
 * Sistema instantáneo: resultados locales al instante, API en background.
 *
 * Flujo:
 * 1. query() retorna INMEDIATAMENTE con tags locales + historial (0ms)
 * 2. En paralelo, lanza la API /tags/autocomplete.json en un hilo
 * 3. Cuando la API responde, guarda en cache y llama notifyChange()
 * 4. El SearchView re-consulta automáticamente y ahora incluye resultados API del cache
 */
class SearchSuggestionsProvider : SearchRecentSuggestionsProvider() {

    companion object {
        const val AUTHORITY = "com.mommys.app.search.provider"
        const val MODE = DATABASE_MODE_QUERIES or DATABASE_MODE_2LINES
        private const val TAG = "SearchSugProvider"
        private const val API_TIMEOUT_MS = 2000L
        private const val MAX_API_RESULTS = 20
        private const val DEBOUNCE_MS = 250L

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

    // Cache de resultados API por prefijo
    private val apiCache = ConcurrentHashMap<String, List<ApiTag>>()

    // Request en curso (para cancelar si el usuario sigue escribiendo)
    private val pendingCall = AtomicReference<Call?>(null)

    // Último término buscado (para debounce)
    @Volatile private var lastSearchTerm = ""
    @Volatile private var lastQueryTime = 0L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    // Modelo ligero para cache
    private data class ApiTag(
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

        // 2. Sugerencias de tags (local + cache API)
        val tagsCursor = getTagSuggestionsCursor(query, uri)

        // 3. Combinar
        return if (recentCursor != null && tagsCursor.count > 0) {
            MergeCursor(arrayOf(recentCursor, tagsCursor))
        } else if (tagsCursor.count > 0) {
            tagsCursor
        } else {
            recentCursor ?: MatrixCursor(SUGGEST_COLUMNS)
        }
    }

    /**
     * Retorna sugerencias de tags instantáneamente.
     * Si hay cache API → usa cache. Si no → local + lanza API en background.
     */
    private fun getTagSuggestionsCursor(fullQuery: String, uri: Uri): MatrixCursor {
        val cursor = MatrixCursor(SUGGEST_COLUMNS)
        val manager = suggestionsManager ?: return cursor

        val currentInput = fullQuery.trim()
        val words = currentInput.split("\\s+".toRegex())
        val lastWord = words.lastOrNull() ?: ""

        if (lastWord.isEmpty()) return cursor

        // Operadores: solo local, sin API
        if (manager.isOperator(lastWord) || lastWord.contains(":")) {
            return getLocalSuggestionsCursor(lastWord, manager)
        }

        // ¿Hay resultados API en cache para este término?
        val cached = findCachedResults(lastWord)
        if (cached != null) {
            // Cache hit → retornar resultados API inmediatamente
            return buildApiCursor(cached)
        }

        // Cache miss → retornar local al instante + lanzar API en background
        val localCursor = getLocalSuggestionsCursor(lastWord, manager)
        launchApiInBackground(lastWord, uri)
        return localCursor
    }

    /**
     * Busca en cache: coincidencia exacta o prefijo compatible
     */
    private fun findCachedResults(term: String): List<ApiTag>? {
        // Coincidencia exacta primero
        apiCache[term]?.let { return it }

        // Buscar cache de prefijo más corto y filtrar localmente
        for (i in term.length - 1 downTo 1) {
            val prefix = term.substring(0, i)
            val cached = apiCache[prefix]
            if (cached != null) {
                // Filtrar el cache existente con el término más largo
                val filtered = cached.filter { it.name.contains(term, ignoreCase = true) }
                if (filtered.isNotEmpty()) return filtered
            }
        }
        return null
    }

    /**
     * Lanza la llamada API en un hilo background.
     * Cuando responde, guarda en cache y notifica al ContentResolver
     * para que el SearchView re-consulte automáticamente.
     */
    private fun launchApiInBackground(searchTerm: String, @Suppress("UNUSED_PARAMETER") uri: Uri) {
        val now = System.currentTimeMillis()

        // Debounce: no lanzar si ya se buscó hace poco
        if (searchTerm == lastSearchTerm && now - lastQueryTime < DEBOUNCE_MS) {
            return
        }
        lastSearchTerm = searchTerm
        lastQueryTime = now

        // Cancelar request anterior si existe
        pendingCall.getAndSet(null)?.cancel()

        Thread {
            try {
                val app = context?.applicationContext as? MommysApplication ?: return@Thread
                val prefs = app.preferencesManager

                val baseUrl = if (prefs.useE621()) "https://e621.net" else "https://e926.net"
                val url = "$baseUrl/tags/autocomplete.json" +
                    "?search[name_matches]=${Uri.encode(searchTerm)}*" +
                    "&limit=$MAX_API_RESULTS"

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mommys/1.4.6 (by AndoniXXR)")
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

                val call = httpClient.newCall(requestBuilder.build())
                pendingCall.set(call)

                val response = call.execute()
                pendingCall.set(null)

                if (!response.isSuccessful) {
                    response.close()
                    return@Thread
                }

                val body = response.body?.string() ?: return@Thread
                response.close()

                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) return@Thread

                // Guardar en cache
                val tags = mutableListOf<ApiTag>()
                for (i in 0 until jsonArray.length()) {
                    val tag = jsonArray.getJSONObject(i)
                    tags.add(ApiTag(
                        name = tag.getString("name"),
                        postCount = tag.optInt("post_count", 0),
                        category = tag.optInt("category", 0)
                    ))
                }
                apiCache[searchTerm] = tags

                // Limpiar cache viejo si crece mucho
                if (apiCache.size > 50) {
                    val keysToRemove = apiCache.keys().toList()
                        .take(apiCache.size - 30)
                    keysToRemove.forEach { apiCache.remove(it) }
                }

                // Notificar al ContentResolver para que SearchView re-consulte
                // Solo si el usuario sigue escribiendo el mismo término
                if (lastSearchTerm == searchTerm) {
                    context?.contentResolver?.notifyChange(
                        Uri.parse("content://$AUTHORITY"), null
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "API background call failed: ${e.message}")
            }
        }.start()
    }

    /**
     * Construye cursor desde resultados API cacheados
     */
    private fun buildApiCursor(tags: List<ApiTag>): MatrixCursor {
        val cursor = MatrixCursor(SUGGEST_COLUMNS)
        var id = 20000L

        for (tag in tags) {
            val categoryText = when (tag.category) {
                0 -> "General"
                1 -> "Artista"
                3 -> "Copyright"
                4 -> "Personaje"
                5 -> "Especie"
                6 -> "Inválido"
                7 -> "Meta"
                8 -> "Lore"
                else -> "Tag"
            }
            val subtitle = "$categoryText • ${formatPostCount(tag.postCount)}"

            cursor.addRow(arrayOf(
                0,
                android.R.drawable.ic_menu_search,
                tag.name,
                subtitle,
                tag.name,
                id++
            ))
        }
        return cursor
    }

    /**
     * Genera cursor con sugerencias locales estáticas (instantáneo)
     */
    private fun getLocalSuggestionsCursor(lastWord: String, manager: SuggestionsManager): MatrixCursor {
        val cursor = MatrixCursor(SUGGEST_COLUMNS)
        val suggestions = manager.getSuggestions(lastWord, 50)
        var id = 10000L

        for (suggestion in suggestions) {
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

            cursor.addRow(arrayOf(
                0,
                icon,
                suggestion.text,
                typeText,
                suggestion.text,
                id++
            ))
        }
        return cursor
    }

    private fun formatPostCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM posts", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK posts", count / 1_000.0)
            else -> "$count posts"
        }
    }
}
