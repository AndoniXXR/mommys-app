package com.mommys.app.data.search

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.app.SearchManager
import android.provider.BaseColumns
import com.mommys.app.MommysApplication
import com.mommys.app.R
import com.mommys.app.data.api.ApiClient
import com.mommys.app.data.database.SearchHistoryEntity
import kotlinx.coroutines.*

/**
 * Helper para gestionar la búsqueda.
 * Combina las funcionalidades de:
 * - API autocomplete de e621
 * - RecentSearchesProvider (historial)
 * - SuggestionsManager (operadores)
 * 
 * Similar a la app original que usa /tags.json?search[name_matches]=
 */
class SearchHelper(private val context: Context) {
    
    companion object {
        private const val MAX_SUGGESTIONS = 50
        private const val MAX_API_RESULTS = 10
        private const val MAX_HISTORY = 10
        private const val MAX_SAVED = 5
        
        // Columnas para el cursor de sugerencias
        val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_QUERY
        )
    }
    
    private val suggestionsManager = SuggestionsManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val app: MommysApplication
        get() = context.applicationContext as MommysApplication
    
    private val apiService: com.mommys.app.data.api.ApiService
        get() = ApiClient.apiService

    /**
     * Obtiene sugerencias como cursor para el SearchView
     * Combina: historial, API autocomplete, y operadores
     */
    suspend fun getSuggestionsCursor(query: String): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        var id = 0L
        
        val currentInput = query.trim()
        val words = currentInput.split("\\s+".toRegex())
        val lastWord = words.lastOrNull()?.lowercase() ?: ""
        val prefixWords = if (words.size > 1) {
            words.dropLast(1).joinToString(" ") + " "
        } else {
            ""
        }
        
        // Si el query está vacío, mostrar historial reciente
        if (lastWord.isEmpty()) {
            return getEmptyQueryCursor()
        }
        
        // 1. Buscar en historial (prioridad alta)
        try {
            val historyMatches = app.database.searchHistoryDao().searchHistory(lastWord, 5)
            for (item in historyMatches) {
                cursor.addRow(arrayOf<Any?>(
                    id++,
                    item.displayText,
                    "Reciente",
                    R.drawable.ic_history,
                    item.query,
                    item.query
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 2. Si es un operador, buscar solo en operadores locales
        if (suggestionsManager.isOperator(lastWord) || lastWord.contains(":")) {
            val operators = suggestionsManager.getSuggestions(lastWord, MAX_SUGGESTIONS - cursor.count)
            for (suggestion in operators) {
                if (suggestion.type == SuggestionType.OPERATOR) {
                    val fullQuery = prefixWords + suggestion.text
                    cursor.addRow(arrayOf<Any?>(
                        id++,
                        suggestion.text,
                        "Operador",
                        R.drawable.ic_menu,
                        fullQuery,
                        fullQuery
                    ))
                }
            }
            return cursor
        }
        
        // 3. Buscar en API de e621 (autocomplete de tags)
        try {
            val response = apiService.autocompleteTags(
                query = "$lastWord*",  // Wildcard para prefijo
                limit = MAX_API_RESULTS
            )
            
            if (response.isSuccessful) {
                val tags = response.body() ?: emptyList()
                for (tag in tags) {
                    val fullQuery = prefixWords + tag.name
                    
                    // Categoría del tag para el subtexto
                    val categoryText = when (tag.category) {
                        0 -> "Tag general"
                        1 -> "Artista"
                        3 -> "Copyright"
                        4 -> "Personaje"
                        5 -> "Especie"
                        6 -> "Inválido"
                        7 -> "Meta"
                        8 -> "Lore"
                        else -> "Tag"
                    }
                    
                    // Mostrar conteo de posts
                    val subtitle = "$categoryText • ${formatPostCount(tag.post_count)}"
                    
                    cursor.addRow(arrayOf<Any?>(
                        id++,
                        tag.name,
                        subtitle,
                        R.drawable.ic_search,
                        fullQuery,
                        fullQuery
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 4. Si la API no retornó suficientes resultados, complementar con sugerencias locales
        if (cursor.count < 5) {
            val localSuggestions = suggestionsManager.getSuggestions(lastWord, MAX_SUGGESTIONS - cursor.count)
            for (suggestion in localSuggestions) {
                val fullQuery = prefixWords + suggestion.text
                val typeText = when (suggestion.type) {
                    SuggestionType.OPERATOR -> "Operador"
                    SuggestionType.TAG -> "Tag"
                    SuggestionType.HISTORY -> "Reciente"
                    SuggestionType.SAVED -> "Guardada"
                }
                
                val icon = when (suggestion.type) {
                    SuggestionType.OPERATOR -> R.drawable.ic_menu
                    SuggestionType.TAG -> R.drawable.ic_search
                    SuggestionType.HISTORY -> R.drawable.ic_history
                    SuggestionType.SAVED -> R.drawable.ic_favorite
                }
                
                cursor.addRow(arrayOf<Any?>(
                    id++,
                    suggestion.text,
                    typeText,
                    icon,
                    fullQuery,
                    fullQuery
                ))
            }
        }
        
        return cursor
    }
    
    /**
     * Formatea el conteo de posts para mostrar (1.2k, 5.6M, etc.)
     */
    private fun formatPostCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM posts", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK posts", count / 1_000.0)
            else -> "$count posts"
        }
    }
            
    /**
     * Cursor cuando el query está vacío - muestra historial reciente y guardadas
     */
    private suspend fun getEmptyQueryCursor(): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        var id = 0L
        
        try {
            val db = app.database
            
            // Historial reciente
            val history = db.searchHistoryDao().getRecentSearchesSync(MAX_HISTORY)
            for (item in history) {
                cursor.addRow(arrayOf<Any?>(
                    id++,
                    item.displayText,
                    "Reciente",
                    R.drawable.ic_history,
                    item.query,
                    item.query
                ))
            }
            
            // Búsquedas guardadas
            val saved = db.savedSearchDao().getAllSavedSearchesSync().take(MAX_SAVED)
            for (item in saved) {
                cursor.addRow(arrayOf<Any?>(
                    id++,
                    item.name,
                    "Guardada: ${item.query}",
                    R.drawable.ic_favorite,
                    item.query,
                    item.query
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return cursor
    }
    
    /**
     * Guarda una búsqueda en el historial
     */
    suspend fun saveToHistory(query: String) {
        if (query.isBlank()) return
        
        try {
            val dao = app.database.searchHistoryDao()
            
            // Verificar si ya existe
            val existing = dao.findByQuery(query)
            if (existing != null) {
                // Incrementar contador de uso
                dao.incrementUseCount(query)
            } else {
                // Crear nueva entrada
                dao.insert(SearchHistoryEntity(
                    query = query,
                    displayText = query
                ))
            }
            
            // Limpiar entradas antiguas
            dao.pruneOldEntries(100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Limpia todo el historial de búsquedas
     */
    suspend fun clearHistory() {
        try {
            app.database.searchHistoryDao().deleteAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Guarda una búsqueda como favorita
     */
    suspend fun saveSearch(name: String, query: String, page: Int = 1): Long {
        return try {
            app.database.savedSearchDao().insert(
                com.mommys.app.data.database.SavedSearchEntity(
                    name = name,
                    query = query,
                    page = page
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }
    
    /**
     * Elimina una búsqueda guardada
     */
    suspend fun deleteSavedSearch(id: Int) {
        try {
            app.database.savedSearchDao().delete(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        scope.cancel()
    }
}
