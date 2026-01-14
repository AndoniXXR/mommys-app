package com.mommys.app.ui.main

import com.mommys.app.data.model.Post

/**
 * Interfaz para callbacks de selección múltiple
 * Similar a qi.k de la app original
 * 
 * Métodos:
 * - onPostSelected(post): llamado cuando se agrega un post a la selección
 * - onPostDeselected(post): llamado cuando se quita un post de la selección  
 * - getSelectedCount(): devuelve la cantidad de posts seleccionados
 */
interface SelectionCallback {
    /**
     * Llamado cuando un post es agregado a la selección
     * Similar a qi.k.p(mVar) de la app original
     */
    fun onPostSelected(post: Post)
    
    /**
     * Llamado cuando un post es removido de la selección
     * Similar a qi.k.n(mVar) de la app original
     */
    fun onPostDeselected(post: Post)
    
    /**
     * Devuelve la cantidad de posts actualmente seleccionados
     * Similar a qi.k.r() de la app original
     */
    fun getSelectedCount(): Int
}
