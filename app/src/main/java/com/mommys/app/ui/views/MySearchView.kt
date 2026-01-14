package com.mommys.app.ui.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat

/**
 * Custom SearchView exactly like the original MySearchView from se.zepiwolf.tws
 * Handles query refinement when selecting suggestions
 */
class MySearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SearchView(context, attrs) {
    
    // Listener for query refinement
    private var onQueryRefineListener: OnQueryRefineListener? = null
    
    interface OnQueryRefineListener {
        fun onQueryRefine(text: CharSequence)
    }
    
    init {
        // Configurar colores del texto programáticamente
        // porque los atributos XML no funcionan bien con Material3
        applyTextColors()
    }
    
    /**
     * Aplica los colores correctos al EditText interno del SearchView
     */
    private fun applyTextColors() {
        try {
            // Obtener el color del tema (mySearchHintTextColor)
            val typedValue = TypedValue()
            val resolved = context.theme.resolveAttribute(
                com.mommys.app.R.attr.mySearchHintTextColor, 
                typedValue, 
                true
            )
            
            val textColor = if (resolved && typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else if (resolved) {
                typedValue.data
            } else {
                // Fallback: blanco para tema oscuro
                Color.WHITE
            }
            
            // Buscar el EditText interno del SearchView
            val searchEditText = findViewById<EditText>(
                androidx.appcompat.R.id.search_src_text
            )
            
            searchEditText?.apply {
                setTextColor(textColor)
                setHintTextColor(textColor)
                // También configurar color del cursor si es posible
                highlightColor = textColor and 0x80FFFFFF.toInt() // Semi-transparente
            }
        } catch (e: Exception) {
            // Ignorar errores silenciosamente
            e.printStackTrace()
        }
    }
    
    /**
     * Called when a suggestion is selected/refined
     * Handles appending the suggestion to existing query intelligently
     */
    fun onSuggestionClick(suggestion: CharSequence) {
        onQueryRefineListener?.let { listener ->
            val currentQuery = query.toString()
            val suggestionText = suggestion.toString().trim()
            
            val newQuery = StringBuilder()
            
            when {
                currentQuery.trim().isEmpty() -> {
                    newQuery.append(suggestionText)
                }
                currentQuery.endsWith(" ") -> {
                    newQuery.append(currentQuery.trim())
                    newQuery.append(" ")
                    newQuery.append(suggestionText)
                }
                currentQuery.trim().contains(" ") -> {
                    val trimmed = currentQuery.trim()
                    newQuery.append(trimmed.substring(0, trimmed.lastIndexOf(" ")))
                    newQuery.append(" ")
                    newQuery.append(suggestionText)
                }
                else -> {
                    newQuery.append(suggestionText)
                }
            }
            
            setQuery(newQuery.toString(), false)
        }
    }
    
    /**
     * Set query with option to submit
     */
    fun setQueryText(query: String) {
        setQuery(query, false)
    }
    
    fun setOnQueryRefineListener(listener: OnQueryRefineListener?) {
        this.onQueryRefineListener = listener
    }
}
