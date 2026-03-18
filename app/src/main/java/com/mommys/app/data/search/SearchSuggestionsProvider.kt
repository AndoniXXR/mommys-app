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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ContentProvider para sugerencias de búsqueda.
 * Sistema híbrido: API autocomplete + fallback estático.
 *
 * Extiende SearchRecentSuggestionsProvider (framework Android) que gestiona
 * automáticamente el historial reciente en SQLite interno.
 *
 * Sobreescribe query() para combinar:
 * 1. Historial de búsquedas recientes (del framework)
 * 2. Tags dinámicos de la API /tags/autocomplete.json (artistas, personajes, etc.)
 * 3. Fallback: tags estáticos de SuggestionsManager si la API falla
 */
class SearchSuggestionsProvider : SearchRecentSuggestionsProvider() {

    companion object {
        const val AUTHORITY = "com.mommys.app.search.provider"
        const val MODE = DATABASE_MODE_QUERIES or DATABASE_MODE_2LINES
        private const val TAG = "SearchSugProvider"
        private const val API_TIMEOUT_MS = 3000L
        private const val MAX_API_RESULTS = 20

        // Columnas EXACTAS que SearchRecentSuggestionsProvider retorna
        // con DATABASE_MODE_QUERIES | DATABASE_MODE_2LINES.
        // El orden DEBE coincidir para que MergeCursor funcione.
        private val SUGGEST_COLUMNS = arrayOf(
            SearchManager.SUGGEST_COLUMN_FORMAT,     // 0
            SearchManager.SUGGEST_COLUMN_ICON_1,     // 1
            SearchManager.SUGGEST_COLUMN_TEXT_1,      // 2
            SearchManager.SUGGEST_COLUMN_TEXT_2,      // 3
            SearchManager.SUGGEST_COLUMN_QUERY,       // 4
            BaseColumns._ID                           // 5
        )
    }

    private var suggestionsManager: SuggestionsManager? = null

    // OkHttpClient dedicado a autocomplete con timeout corto
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
        // Usar SuggestionsManager (datos estáticos, sin I/O)
        if (suggestionsManager == null) {
            suggestionsManager = SuggestionsManager()
        }

        // Obtener el query actual del usuario
        val query = selectionArgs?.firstOrNull()
            ?: uri.lastPathSegment?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: ""

        // 1. Obtener historial reciente del framework
        val recentCursor: Cursor? = try {
            super.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: Exception) {
            null
        }

        // 2. Obtener sugerencias de tags
        val tagsCursor = getTagSuggestionsCursor(query)

        // 3. Combinar: historial primero, luego tags
        return if (recentCursor != null && tagsCursor.count > 0) {
            MergeCursor(arrayOf(recentCursor, tagsCursor))
        } else if (tagsCursor.count > 0) {
            tagsCursor
        } else {
            recentCursor ?: MatrixCursor(SUGGEST_COLUMNS)
        }
    }

    /**
     * Genera cursor con sugerencias de tags.
     * Estrategia híbrida:
     * - Primero intenta API /tags/autocomplete.json (tags dinámicos: artistas, personajes, etc.)
     * - Si la API falla (sin red, timeout), usa SuggestionsManager (tags estáticos de suggestions.json)
     * - Si es un operador (contiene ":"), solo busca localmente
     */
    private fun getTagSuggestionsCursor(fullQuery: String): MatrixCursor {
        val cursor = MatrixCursor(SUGGEST_COLUMNS)
        val manager = suggestionsManager ?: return cursor

        val currentInput = fullQuery.trim()
        val words = currentInput.split("\\s+".toRegex())
        val lastWord = words.lastOrNull() ?: ""

        if (lastWord.isEmpty()) return cursor

        // Si es un operador, solo buscar localmente (la API no maneja operadores)
        if (manager.isOperator(lastWord) || lastWord.contains(":")) {
            return getLocalSuggestionsCursor(lastWord, manager)
        }

        // Intentar API primero
        val apiCursor = tryApiAutocomplete(lastWord)
        if (apiCursor != null && apiCursor.count > 0) {
            return apiCursor
        }

        // Fallback: sugerencias estáticas locales
        return getLocalSuggestionsCursor(lastWord, manager)
    }

    /**
     * Intenta obtener sugerencias de la API /tags/autocomplete.json
     * Llamada HTTP síncrona (ContentProvider.query() corre en binder thread, no main thread)
     */
    private fun tryApiAutocomplete(searchTerm: String): MatrixCursor? {
        try {
            val app = context?.applicationContext as? MommysApplication ?: return null
            val prefs = app.preferencesManager

            // Determinar URL base según configuración
            val baseUrl = if (prefs.useE621()) {
                "https://e621.net"
            } else {
                "https://e926.net"
            }

            // Construir URL: /tags/autocomplete.json?search[name_matches]=fox*&limit=20
            val url = "$baseUrl/tags/autocomplete.json" +
                "?search[name_matches]=${Uri.encode(searchTerm)}*" +
                "&limit=$MAX_API_RESULTS"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mommys/1.4.6 (by AndoniXXR)")
                .header("Accept", "application/json")
                .get()

            // Agregar auth si hay credenciales
            val username = prefs.getUsername()
            val apiKey = prefs.getApiKey()
            if (!username.isNullOrEmpty() && !apiKey.isNullOrEmpty() && !username.contains(":")) {
                val credentials = "$username:$apiKey"
                val basicAuth = "Basic " + Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", basicAuth)
            }

            // Ejecutar request síncrono
            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            response.close()

            // Parsear respuesta JSON
            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) return null

            val cursor = MatrixCursor(SUGGEST_COLUMNS)
            var id = 20000L

            for (i in 0 until jsonArray.length()) {
                val tag = jsonArray.getJSONObject(i)
                val name = tag.getString("name")
                val postCount = tag.optInt("post_count", 0)
                val category = tag.optInt("category", 0)

                // Categoría del tag para subtexto
                val categoryText = when (category) {
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

                val subtitle = "$categoryText • ${formatPostCount(postCount)}"

                cursor.addRow(arrayOf(
                    0,                                          // suggest_format
                    android.R.drawable.ic_menu_search,           // suggest_icon_1
                    name,                                       // suggest_text_1
                    subtitle,                                   // suggest_text_2
                    name,                                       // suggest_query
                    id++                                        // _id
                ))
            }

            return cursor
        } catch (e: Exception) {
            Log.d(TAG, "API autocomplete failed, using fallback: ${e.message}")
            return null
        }
    }

    /**
     * Genera cursor con sugerencias locales estáticas (fallback offline)
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
                0,                  // suggest_format
                icon,               // suggest_icon_1
                suggestion.text,    // suggest_text_1
                typeText,           // suggest_text_2
                suggestion.text,    // suggest_query
                id++                // _id
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
