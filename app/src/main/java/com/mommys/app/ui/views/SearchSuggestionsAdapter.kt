package com.mommys.app.ui.views

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cursoradapter.widget.CursorAdapter
import com.mommys.app.R
import android.app.SearchManager
import android.provider.BaseColumns

/**
 * Adaptador para las sugerencias del SearchView.
 * Similar al adaptador de sugerencias de la app original.
 * 
 * Muestra sugerencias con icono, texto principal y subtexto (categoría/post count).
 */
class SearchSuggestionsAdapter(
    context: Context,
    private val onSuggestionClick: (String) -> Unit,
    private val onInsertClick: (String) -> Unit
) : CursorAdapter(context, null, 0) {

    private val inflater = LayoutInflater.from(context)
    
    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return inflater.inflate(R.layout.item_search_suggestion, parent, false)
    }
    
    override fun bindView(view: View, context: Context, cursor: Cursor) {
        // Usar IDs exactos del layout original
        val txtSuggestion = view.findViewById<TextView>(android.R.id.text1)
        val txtType = view.findViewById<TextView>(android.R.id.text2)
        val imgIcon = view.findViewById<ImageView>(android.R.id.icon1)
        val btnInsert = view.findViewById<ImageView>(R.id.edit_query)
        
        // Obtener datos del cursor
        val text1Index = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1)
        val text2Index = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2)
        val iconIndex = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_1)
        val queryIndex = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY)
        
        val text1 = if (text1Index >= 0) cursor.getString(text1Index) else ""
        val text2 = if (text2Index >= 0) cursor.getString(text2Index) else null
        val iconResId = if (iconIndex >= 0) cursor.getInt(iconIndex) else R.drawable.ic_search
        val query = if (queryIndex >= 0) cursor.getString(queryIndex) else text1
        
        // Configurar texto principal
        txtSuggestion.text = text1
        
        // Configurar subtexto (categoría o tipo)
        if (!text2.isNullOrEmpty()) {
            txtType.text = text2
            txtType.visibility = View.VISIBLE
        } else {
            txtType.visibility = View.GONE
        }
        
        // Icono - usar el icono del cursor o determinar por tipo
        try {
            imgIcon.setImageResource(iconResId)
            imgIcon.visibility = View.VISIBLE
        } catch (e: Exception) {
            // Fallback si el recurso no existe
            imgIcon.setImageResource(R.drawable.ic_search)
            imgIcon.visibility = View.VISIBLE
        }
        
        // Botón de insertar (flecha para añadir al campo sin ejecutar)
        btnInsert.visibility = View.VISIBLE
        
        // Click en la sugerencia completa - ejecuta la búsqueda
        view.setOnClickListener {
            onSuggestionClick(query)
        }
        
        // Click en el botón de insertar - añade al campo sin ejecutar
        btnInsert.setOnClickListener {
            onInsertClick(text1)
        }
    }
    
    /**
     * Obtiene el query de la sugerencia en la posición dada
     */
    fun getSuggestionQuery(position: Int): String? {
        val cursor = cursor ?: return null
        if (!cursor.moveToPosition(position)) return null
        
        val queryIndex = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY)
        return if (queryIndex >= 0) cursor.getString(queryIndex) else null
    }
    
    /**
     * Obtiene el texto de la sugerencia en la posición dada
     */
    fun getSuggestionText(position: Int): String? {
        val cursor = cursor ?: return null
        if (!cursor.moveToPosition(position)) return null
        
        val textIndex = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1)
        return if (textIndex >= 0) cursor.getString(textIndex) else null
    }
}
