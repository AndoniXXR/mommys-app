package com.mommys.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Custom SwipeRefreshLayout que no intercepta gestos horizontales.
 * 
 * Esto permite que el ViewPager2 reciba los swipes horizontales para cambiar de página,
 * mientras que los swipes verticales hacia abajo siguen activando el refresh.
 * 
 * Basado en la implementación original de la app (CustomSwipeRefreshLayout.java)
 */
class CustomSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var declined: Boolean = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                declined = false
            }
            MotionEvent.ACTION_MOVE -> {
                // Si ya decidimos no interceptar, seguir sin interceptar
                if (declined) {
                    return false
                }
                
                val diffX = abs(event.x - initialX)
                val diffY = abs(event.y - initialY)
                
                // Si el movimiento horizontal es mayor que el touchSlop y es mayor que el vertical,
                // es un swipe horizontal - no interceptar para que el ViewPager lo reciba
                if (diffX > touchSlop && diffX > diffY) {
                    declined = true
                    return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                declined = false
            }
        }
        
        return super.onInterceptTouchEvent(event)
    }
}
