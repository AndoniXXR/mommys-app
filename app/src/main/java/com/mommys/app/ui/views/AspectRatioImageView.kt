package com.mommys.app.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView que mantiene una proporción de aspecto basada en el ancho.
 * Similar a PressableHigherImageView de la app original.
 * 
 * Por defecto usa ratio 1.1 (height = width * 1.1)
 */
class AspectRatioImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    
    /**
     * Ratio de aspecto (height/width).
     * Valores típicos:
     * - 1.0 = cuadrado
     * - 1.1 = ligeramente más alto (default de la app original)
     * - 1.33 = 4:3
     */
    var aspectRatio: Double = 1.1
        set(value) {
            field = value.coerceIn(1.0, 2.0)
            requestLayout()
        }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = (width * aspectRatio).toInt()
        setMeasuredDimension(width, height)
    }
}
