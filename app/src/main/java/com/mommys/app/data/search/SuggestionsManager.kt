package com.mommys.app.data.search

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/**
 * Tipo de sugerencia
 */
enum class SuggestionType {
    TAG,        // Tag normal de búsqueda
    OPERATOR,   // Operador de búsqueda (order:, rating:, etc.)
    HISTORY,    // Del historial de búsquedas
    SAVED       // Búsqueda guardada
}

/**
 * Modelo de sugerencia
 */
data class Suggestion(
    val text: String,
    val type: SuggestionType,
    val score: Int = 0  // Para ordenar por relevancia
)

/**
 * Gestor de sugerencias de búsqueda.
 * Similar a la combinación de RecentSearchesProvider + suggestions.json de la app original.
 * 
 * Carga las sugerencias predefinidas desde assets y las combina con el historial.
 */
class SuggestionsManager(private val context: Context) {
    
    companion object {
        private const val SUGGESTIONS_FILE = "suggestions.json"
        private const val MAX_CACHE_SIZE = 5000
    }
    
    // Cache de sugerencias predefinidas
    private val predefinedSuggestions: List<String> by lazy {
        loadSuggestionsFromAssets()
    }
    
    // Operadores de búsqueda conocidos
    private val operators = listOf(
        // Score
        "score:100", "score:>=100", "score:>100", "score:<=100", "score:<100",
        "score:>=50", "score:>=200", "score:>=500", "score:>=1000",
        // Favcount
        "favcount:100", "favcount:>=100", "favcount:>100", "favcount:<=100", "favcount:<100",
        "favcount:>=50", "favcount:>=200", "favcount:>=500",
        // Comments
        "commentcount:>10", "commentcount:>20", "commentcount:>50",
        // Rating
        "rating:safe", "rating:questionable", "rating:explicit",
        // Type
        "type:jpg", "type:png", "type:gif", "type:webm", "type:mp4",
        // Parent/Child
        "ischild:true", "ischild:false", "isparent:true", "isparent:false",
        "parent:none",
        // Dimensions
        "width:>=1920", "width:>=1080", "height:>=1920", "height:>=1080",
        "mpixels:>=2",
        // Ratio
        "ratio:1.33", "ratio:1.78", "ratio:0.56",
        // Date
        "date:today", "date:yesterday", "date:week", "date:month", "date:year",
        // Duration (videos)
        "duration:>30", "duration:>60", "duration:>120",
        // Status
        "status:active", "status:pending", "status:flagged", "status:deleted",
        // Pool
        "inpool:true", "inpool:false",
        // Order
        "order:id", "order:id_asc", "order:random",
        "order:score", "order:score_asc",
        "order:favcount", "order:favcount_asc",
        "order:tagcount", "order:tagcount_asc",
        "order:comments", "order:comments_asc",
        "order:mpixels", "order:mpixels_asc",
        "order:filesize", "order:filesize_asc",
        "order:landscape", "order:portrait",
        "order:change",
        "order:duration", "order:duration_asc",
        // Negation examples
        "-male", "-female", "-comic", "-animated"
    )
    
    /**
     * Carga las sugerencias desde el archivo JSON en assets
     */
    private fun loadSuggestionsFromAssets(): List<String> {
        return try {
            val inputStream = context.assets.open(SUGGESTIONS_FILE)
            val json = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(json)
            
            val list = mutableListOf<String>()
            for (i in 0 until minOf(jsonArray.length(), MAX_CACHE_SIZE)) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            // Si falla, retornar lista vacía
            emptyList()
        }
    }
    
    /**
     * Obtiene sugerencias que coincidan con el query
     * @param query El texto a buscar (generalmente el último tag que se está escribiendo)
     * @param limit Máximo de sugerencias a retornar
     */
    fun getSuggestions(query: String, limit: Int = 50): List<Suggestion> {
        if (query.isBlank()) return emptyList()
        
        val queryLower = query.lowercase(Locale.getDefault())
        val results = mutableListOf<Suggestion>()
        
        // 1. Buscar en operadores (alta prioridad)
        val matchingOperators = operators.filter { op ->
            op.lowercase().startsWith(queryLower) || 
            op.lowercase().contains(queryLower)
        }.take(limit / 4)
        
        for (op in matchingOperators) {
            val score = if (op.lowercase().startsWith(queryLower)) 100 else 50
            results.add(Suggestion(op, SuggestionType.OPERATOR, score))
        }
        
        // 2. Buscar en sugerencias predefinidas
        val matchingTags = predefinedSuggestions.asSequence()
            .filter { tag ->
                val tagLower = tag.lowercase()
                tagLower.startsWith(queryLower) || tagLower.contains(queryLower)
            }
            .take(limit)
            .map { tag ->
                val tagLower = tag.lowercase()
                val score = when {
                    tagLower == queryLower -> 200          // Coincidencia exacta
                    tagLower.startsWith(queryLower) -> 100 // Empieza con
                    else -> 25                              // Contiene
                }
                Suggestion(tag, SuggestionType.TAG, score)
            }
            .toList()
        
        results.addAll(matchingTags)
        
        // 3. Ordenar por score y limitar
        return results
            .sortedByDescending { it.score }
            .distinctBy { it.text.lowercase() }
            .take(limit)
    }
    
    /**
     * Verifica si un texto es un operador de búsqueda
     */
    fun isOperator(text: String): Boolean {
        val prefixes = listOf(
            "score:", "favcount:", "commentcount:", "rating:", "type:",
            "ischild:", "isparent:", "parent:", "width:", "height:",
            "mpixels:", "ratio:", "date:", "duration:", "status:",
            "inpool:", "order:", "fav:", "pool:", "set:", "user:",
            "uploaderid:", "approverid:", "commenter:", "noter:",
            "noteupdater:", "artcomm:", "delreason:", "source:",
            "description:", "note:", "id:", "tagcount:", "gentags:",
            "arttags:", "chartags:", "copytags:", "spectags:", "metatags:",
            "invtags:", "lortags:", "-"
        )
        val lower = text.lowercase()
        return prefixes.any { lower.startsWith(it) }
    }
    
    /**
     * Obtiene sugerencias populares/recomendadas
     */
    fun getPopularSuggestions(limit: Int = 20): List<Suggestion> {
        val popular = listOf(
            "solo", "female", "male", "duo", "anthro", "feral",
            "order:score", "order:favcount", "rating:safe",
            "hi_res", "absurd_res", "animated"
        )
        
        return popular.take(limit).mapIndexed { index, text ->
            val type = if (isOperator(text)) SuggestionType.OPERATOR else SuggestionType.TAG
            Suggestion(text, type, 100 - index)
        }
    }
    
    /**
     * Obtiene la cantidad total de sugerencias disponibles
     */
    fun getTotalSuggestionsCount(): Int {
        return predefinedSuggestions.size + operators.size
    }
}
