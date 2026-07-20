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
 *
 * ESTRATEGIA (alineada con la app original Wolf's Stash):
 * 1. Carga UNA vez `assets/suggestions.json` (150k tags con post_count y categoria)
 *    y lo cachea en memoria (`localTags`). Todas las búsquedas se filtran en local.
 * 2. Solo si la busqueda local da MENOS de 5 resultados, llama a la API como fallback
 *    (para tags muy nuevos o raros). Esa llamada se cachea en `apiCache`.
 *
 * Esto hace que las sugerencias sean instantáneas para el 99% de los casos
 * (como en la app original) sin depender de la red en cada tecla.
 */
class SearchSuggestionsProvider : SearchRecentSuggestionsProvider() {

    companion object {
        const val AUTHORITY = "com.mommys.app.search.provider"
        const val MODE = DATABASE_MODE_QUERIES or DATABASE_MODE_2LINES
        private const val TAG = "SearchSugProvider"
        private const val MAX_RESULTS = 25
        private const val MIN_LOCAL_RESULTS_BEFORE_API = 5

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

    /**
     * Tags locales cargados desde assets/suggestions.json.
     * Cache estático: sobrevive entre instancias del provider (que el framework
     * crea/destruye frecuentemente). Se carga UNA sola vez por sesión de app.
     */
    object LocalTagStore {
        @Volatile
        private var loaded = false
        // Cada entrada: [name, postCount, category]
        val tags: MutableList<TagEntry> = mutableListOf()

        @Synchronized
        fun ensureLoaded(context: android.content.Context) {
            if (loaded) return
            try {
                val start = System.currentTimeMillis()
                context.assets.open("suggestions.json").use { input ->
                    val bytes = input.readBytes()
                    val arr = JSONArray(String(bytes, Charsets.UTF_8))
                    for (i in 0 until arr.length()) {
                        val entry = arr.getJSONArray(i)
                        tags.add(
                            TagEntry(
                                name = entry.getString(0),
                                postCount = entry.optInt(1, 0),
                                category = entry.optInt(2, 0)
                            )
                        )
                    }
                }
                loaded = true
                val ms = System.currentTimeMillis() - start
                Log.d(TAG, "LocalTagStore loaded ${tags.size} tags in ${ms}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading suggestions.json", e)
                loaded = true  // no reintentar infinitamente
            }
        }
    }

    data class TagEntry(val name: String, val postCount: Int, val category: Int)
    private data class CachedTag(val name: String, val postCount: Int, val category: Int)

    // Cache de llamadas API (para tags raros que no estan en local)
    private val apiCache = ConcurrentHashMap<String, List<CachedTag>>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .addInterceptor(HttpConfig.cloudflareHeaderInterceptor())
            .build()
    }

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

        // Sugerencias de tags SOLO en e621 (NSFW).
        // En e926 (SFW) no se cargan sugerencias de tags, solo historial, porque el
        // JSON incluye tags con nombres explícitos (sex, penis, genitals, etc.) que
        // no deberían aparecer cuando el usuario eligió modo SFW.
        // Esto replica exactamente el comportamiento de la app original
        // (RecentSearchesProvider + MainActivity.o0 = useE621).
        val useE621 = try {
            (context?.applicationContext as? MommysApplication)?.preferencesManager?.useE621() ?: false
        } catch (e: Exception) {
            false
        }

        if (!useE621) {
            // e926: devolver solo el historial reciente
            return recentCursor ?: MatrixCursor(SUGGEST_COLUMNS)
        }

        // e621: asegurar que el store local este cargado y mostrar sugerencias
        context?.let { LocalTagStore.ensureLoaded(it) }

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

        // Operadores: solo local (no hay API para esto)
        if (manager.isOperator(lastWord) || lastWord.contains(":")) {
            return buildLocalCursor(lastWord, manager)
        }

        // 1. Filtrar en local (instantaneo)
        val localMatches = filterLocalTags(lastWord)

        // 2. Si hay suficientes resultados locales, devolverlos sin llamar a la API
        if (localMatches.size >= MIN_LOCAL_RESULTS_BEFORE_API) {
            return buildApiCursor(localMatches)
        }

        // 3. Pocos resultados locales: intentar API (cache primero)
        val cached = apiCache[lastWord]
        if (cached != null) {
            // Combinar locales + cacheados
            val combined = (localMatches + cached.map { CachedTag(it.name, it.postCount, it.category) })
                .distinctBy { it.name }
            return buildApiCursor(combined)
        }

        // 4. Llamar API (solo si local fallo)
        val apiResult = callApi(lastWord)
        if (apiResult != null && apiResult.isNotEmpty()) {
            apiCache[lastWord] = apiResult
            trimApiCache()
            val combined = (localMatches + apiResult)
                .distinctBy { it.name }
            return buildApiCursor(combined)
        }

        // 5. Fallback final: tags locales hardcoded (418 tags)
        return if (localMatches.isEmpty()) buildLocalCursor(lastWord, manager)
        else buildApiCursor(localMatches)
    }

    /**
     * Filtra los tags locales por coincidencia (startsWith primero, contains despues).
     * Instantáneo: pura memoria.
     */
    private fun filterLocalTags(term: String): List<CachedTag> {
        val termLower = term.lowercase()
        val startsWith = mutableListOf<CachedTag>()
        val contains = mutableListOf<CachedTag>()

        for (tag in LocalTagStore.tags) {
            val nameLower = tag.name.lowercase()
            when {
                nameLower.startsWith(termLower) -> startsWith.add(
                    CachedTag(tag.name, tag.postCount, tag.category)
                )
                nameLower.contains(termLower) -> contains.add(
                    CachedTag(tag.name, tag.postCount, tag.category)
                )
            }
            // Limitar para no devolver miles
            if (startsWith.size >= MAX_RESULTS) break
        }

        // Si startsWith no lleno el cupo, completar con contains
        val result = startsWith.toMutableList()
        if (result.size < MAX_RESULTS) {
            for (c in contains) {
                if (result.size >= MAX_RESULTS) break
                result.add(c)
            }
        }
        return result
    }

    /**
     * Llamada síncrona a la API (solo para tags raros). Segura porque query() corre en binder thread.
     */
    private fun callApi(searchTerm: String): List<CachedTag>? {
        try {
            val app = context?.applicationContext as? MommysApplication ?: return null
            val prefs = app.preferencesManager

            val baseUrl = if (prefs.useE621()) "https://e621.net" else "https://e926.net"
            val url = "$baseUrl/tags/autocomplete.json" +
                "?search[name_matches]=${Uri.encode(searchTerm)}*" +
                "&limit=$MAX_RESULTS"

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

    private fun trimApiCache() {
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
