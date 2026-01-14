package com.mommys.app.data.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.mommys.app.MommysApplication
import kotlinx.coroutines.runBlocking

/**
 * ContentProvider para sugerencias de búsqueda.
 * Similar a se.zepiwolf.tws.search.RecentSearchesProvider
 * 
 * Combina:
 * - Historial de búsquedas recientes
 * - Sugerencias predefinidas desde assets/suggestions.json
 * - Búsquedas guardadas
 */
class SearchSuggestionsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.mommys.app.search.provider"
        const val MODE = 3 // DATABASE_MODE_QUERIES | DATABASE_MODE_2LINES
        
        private const val SUGGESTIONS = 1
        private const val SEARCH_SUGGEST = 2
        
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "suggestions", SUGGESTIONS)
            addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST)
            addURI(AUTHORITY, "${SearchManager.SUGGEST_URI_PATH_QUERY}/*", SEARCH_SUGGEST)
        }
        
        // Columnas estándar para sugerencias de búsqueda
        private val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,      // Texto principal
            SearchManager.SUGGEST_COLUMN_TEXT_2,      // Texto secundario (tipo/categoría)
            SearchManager.SUGGEST_COLUMN_ICON_1,      // Icono izquierdo
            SearchManager.SUGGEST_COLUMN_INTENT_DATA, // Datos para el intent
            SearchManager.SUGGEST_COLUMN_QUERY        // Query a insertar
        )
        
        // Límite de sugerencias
        private const val MAX_SUGGESTIONS = 50
        
        // Tipos de sugerencias
        const val TYPE_HISTORY = "history"
        const val TYPE_SAVED = "saved"
        const val TYPE_TAG = "tag"
        const val TYPE_OPERATOR = "operator"
    }
    
    private lateinit var suggestionsManager: SuggestionsManager
    
    override fun onCreate(): Boolean {
        context?.let { ctx ->
            suggestionsManager = SuggestionsManager(ctx)
        }
        return true
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return when (uriMatcher.match(uri)) {
            SUGGESTIONS, SEARCH_SUGGEST -> {
                // Obtener el query de búsqueda
                val query = uri.lastPathSegment?.takeIf { 
                    it != SearchManager.SUGGEST_URI_PATH_QUERY 
                } ?: selectionArgs?.firstOrNull() ?: ""
                
                getSuggestionsCursor(query)
            }
            else -> MatrixCursor(COLUMNS)
        }
    }
    
    /**
     * Genera el cursor con sugerencias basadas en el query actual
     */
    private fun getSuggestionsCursor(query: String): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        var id = 0L
        
        val ctx = context ?: return cursor
        val app = ctx.applicationContext as? MommysApplication ?: return cursor
        
        // Obtener el último tag que se está escribiendo
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
            runBlocking {
                try {
                    val db = app.database
                    
                    // Historial reciente
                    val history = db.searchHistoryDao().getRecentSearchesSync(10)
                    for (item in history) {
                        cursor.addRow(arrayOf(
                            id++,
                            item.displayText,
                            "Reciente",
                            android.R.drawable.ic_menu_recent_history,
                            item.query,
                            item.query
                        ))
                    }
                    
                    // Búsquedas guardadas
                    val saved = db.savedSearchDao().getAllSavedSearchesSync().take(5)
                    for (item in saved) {
                        cursor.addRow(arrayOf(
                            id++,
                            item.name,
                            "Guardada: ${item.query}",
                            android.R.drawable.ic_menu_save,
                            item.query,
                            item.query
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return cursor
        }
        
        // Buscar sugerencias que coincidan
        val suggestions = suggestionsManager.getSuggestions(lastWord, MAX_SUGGESTIONS)
        
        // Añadir sugerencias del historial primero
        runBlocking {
            try {
                val db = app.database
                val historyMatches = db.searchHistoryDao().searchHistory(lastWord, 5)
                for (item in historyMatches) {
                    cursor.addRow(arrayOf(
                        id++,
                        item.displayText,
                        "Reciente",
                        android.R.drawable.ic_menu_recent_history,
                        item.query,
                        item.query
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Añadir sugerencias de tags predefinidos
        for (suggestion in suggestions) {
            val fullQuery = prefixWords + suggestion.text
            val typeText = when (suggestion.type) {
                SuggestionType.OPERATOR -> "Operador"
                SuggestionType.TAG -> "Tag"
                SuggestionType.HISTORY -> "Reciente"
                SuggestionType.SAVED -> "Guardada"
            }
            
            val icon = when (suggestion.type) {
                SuggestionType.OPERATOR -> android.R.drawable.ic_menu_sort_by_size
                SuggestionType.TAG -> android.R.drawable.ic_menu_search
                SuggestionType.HISTORY -> android.R.drawable.ic_menu_recent_history
                SuggestionType.SAVED -> android.R.drawable.ic_menu_save
            }
            
            cursor.addRow(arrayOf(
                id++,
                suggestion.text,
                typeText,
                icon,
                fullQuery,
                fullQuery
            ))
        }
        
        return cursor
    }
    
    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            SUGGESTIONS, SEARCH_SUGGEST -> SearchManager.SUGGEST_MIME_TYPE
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
