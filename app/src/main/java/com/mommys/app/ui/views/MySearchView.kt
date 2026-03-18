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
 * Handles query refinement when selecting suggestions.
 *
 * Replica exacta del método p(CharSequence) de la app decompilada:
 * - Si query vacío: reemplaza todo con la sugerencia
 * - Si termina en espacio: agrega la sugerencia al final
 * - Si contiene espacios: reemplaza el último tag con la sugerencia
 * - Si es una sola palabra: reemplaza con la sugerencia
 */
class MySearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SearchView(context, attrs) {
    
    // Listener for query refinement (como l0 k0 en el original)
    var onQueryRefineListener: OnQueryRefineListener? = null
    
    interface OnQueryRefineListener {
        fun onQueryRefine(text: CharSequence)
    }
    
    init {
        applyTextColors()
    }
    
    private fun applyTextColors() {
        try {
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
                Color.WHITE
            }
            
            val searchEditText = findViewById<EditText>(
                androidx.appcompat.R.id.search_src_text
            )
            
            searchEditText?.apply {
                setTextColor(textColor)
                setHintTextColor(textColor)
                highlightColor = textColor and 0x80FFFFFF.toInt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Réplica exacta del método p(CharSequence) de la app original.
     * Llamado por setQueryRefinementEnabled(true) cuando se pulsa la flecha
     * de edición en una sugerencia.
     *
     * Lógica idéntica a MySearchView.p() decompilada:
     * 1. Si el query está vacío → pone solo la sugerencia
     * 2. Si termina en espacio → agrega la sugerencia (append)
     * 3. Si contiene espacios → reemplaza el último tag
     * 4. Caso contrario → reemplaza todo
     */
    fun refineQuery(suggestion: CharSequence) {
        val suggestionText = suggestion.toString().trim()
        val currentQuery = query.toString()
        
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
            else -> {
                val trimmed = currentQuery.trim()
                if (trimmed.contains(" ")) {
                    newQuery.append(trimmed.substring(0, trimmed.lastIndexOf(" ")))
                    newQuery.append(" ")
                    newQuery.append(suggestionText)
                } else {
                    newQuery.append(suggestionText)
                }
            }
        }
        
        setQuery(newQuery.toString(), false)
    }
}
