package com.mommys.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Note (nota en una imagen)
 * Basado en ii/f.java de la app original
 * 
 * Las notas son áreas rectangulares sobre la imagen con texto
 */
data class Note(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("post_id")
    val postId: Int,
    
    @SerializedName("x")
    val x: Int,
    
    @SerializedName("y")
    val y: Int,
    
    @SerializedName("width")
    val width: Int,
    
    @SerializedName("height")
    val height: Int,
    
    @SerializedName("is_active")
    val isActive: Boolean,
    
    @SerializedName("body")
    val body: String
) {
    override fun toString(): String {
        return "Note{x=$x, y=$y, width=$width, height=$height, isActive=$isActive, body='$body'}"
    }
}
