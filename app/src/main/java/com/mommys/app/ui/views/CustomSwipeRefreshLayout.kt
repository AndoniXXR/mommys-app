package com.mommys.app.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.mommys.app.R
import kotlin.math.abs

/**
 * Custom SwipeRefreshLayout que no intercepta gestos horizontales.
 * 
 * Esto permite que el ViewPager2 reciba los swipes horizontales para cambiar de página,
 * mientras que los swipes verticales hacia abajo siguen activando el refresh.
 * 
 * También sobreescribe canChildScrollUp() para buscar el RecyclerView del grid
 * dentro del ViewPager2, ya que ViewPager2 horizontal siempre retorna
 * canScrollVertically(-1) = false.
 * 
 * Basado en la implementación original de la app (CustomSwipeRefreshLayout.java + k5/l.java)
 */
class CustomSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var declined: Boolean = false

    /**
     * Busca el RecyclerView del grid dentro del ViewPager2 actual.
     * Como k5/l.java g() en la app original, pero adaptado porque
     * el hijo directo es ViewPager2 (horizontal) que siempre retorna false.
     */
    override fun canChildScrollUp(): Boolean {
        val viewPager = findViewPager2() ?: return super.canChildScrollUp()
        val innerRecycler = viewPager.getChildAt(0) as? RecyclerView ?: return super.canChildScrollUp()
        val currentHolder = innerRecycler.findViewHolderForAdapterPosition(viewPager.currentItem)
        val gridRecycler = currentHolder?.itemView?.findViewById<RecyclerView>(R.id.recyclerView)
        return gridRecycler?.canScrollVertically(-1) ?: false
    }

    private fun findViewPager2(): ViewPager2? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is ViewPager2) return child
        }
        return null
    }

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
