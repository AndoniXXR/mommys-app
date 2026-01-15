package com.mommys.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * SlidingPanelLayout - Panel deslizable para pull-to-close
 * 
 * Implementado exactamente como df/c.java en la app original:
 * - Permite cerrar la actividad arrastrando hacia abajo
 * - Solo responde a gestos en el 45% superior de la pantalla
 * - Usa ViewDragHelper para el arrastre suave
 * - Dibuja overlay oscuro detrás mientras se arrastra
 */
class SlidingPanelLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        // Direcciones de slide (como bf.a.f2333c en la app original)
        const val DIRECTION_LEFT = 0
        const val DIRECTION_RIGHT = 1
        const val DIRECTION_TOP = 2
        const val DIRECTION_BOTTOM = 3  // Este es el que usa PostActivity
        const val DIRECTION_VERTICAL = 4
        const val DIRECTION_HORIZONTAL = 5
        
        // Threshold para activar el cierre (25% del alto)
        private const val CLOSE_THRESHOLD = 0.25f
        
        // Velocidad mínima para considerar un fling
        private const val MIN_FLING_VELOCITY = 5.0f
    }

    /**
     * Configuración del panel (como bf.a en la app original)
     */
    data class PanelConfig(
        val touchEnabled: Boolean = true,      // f2331a - si responde a touch
        val touchThreshold: Float = 0.45f,     // f2332b - zona sensible (45% superior)
        val direction: Int = DIRECTION_BOTTOM  // f2333c - dirección de slide
    )

    /**
     * Interface para escuchar eventos del panel
     * Como df.b en la app original
     */
    interface OnPanelSlideListener {
        fun onPanelSlide(slideOffset: Float)
        fun onPanelOpened()
        fun onPanelClosed()
        fun onDragStateChanged(state: Int)
    }

    private var config = PanelConfig()
    private var slideListener: OnPanelSlideListener? = null
    
    // Vista que se arrastra (el contenido)
    private var contentView: View? = null
    
    // Dimensiones
    private var screenWidth = 0
    private var screenHeight = 0
    
    // ViewDragHelper para manejar el arrastre
    private val dragHelper: ViewDragHelper
    
    // Paint para el overlay oscuro
    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 204  // 80% opacidad (como la app original: 0.8f * 255)
    }
    
    // Rectángulo para invalidación
    private val invalidateRect = Rect()
    
    // OverScroller para animaciones suaves
    private val scroller = OverScroller(context)
    
    // Estado de arrastre
    private var isDragging = false
    private var isInTouchZone = false
    
    // Velocidad mínima escalada
    private val minFlingVelocity: Float
    
    init {
        setWillNotDraw(false)
        
        screenWidth = resources.displayMetrics.widthPixels
        minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
        
        // Crear ViewDragHelper
        dragHelper = ViewDragHelper.create(this, 1.0f, DragCallback())
        dragHelper.minVelocity = resources.displayMetrics.density * 400.0f
    }

    /**
     * Configurar el panel con los parámetros dados
     */
    fun configure(config: PanelConfig) {
        this.config = config
    }

    /**
     * Establecer el listener de eventos
     */
    fun setOnPanelSlideListener(listener: OnPanelSlideListener?) {
        this.slideListener = listener
    }

    /**
     * Establecer la vista de contenido que se arrastrará
     */
    fun setContentView(view: View) {
        contentView = view
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        screenHeight = measuredHeight
        screenWidth = measuredWidth
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Dibujar overlay oscuro detrás del contenido
        // Como df/c.java onDraw - case 3 (DIRECTION_BOTTOM)
        val content = contentView ?: return
        
        when (config.direction) {
            DIRECTION_BOTTOM -> {
                // Dibujar rect desde bottom del contenido hasta bottom de la pantalla
                canvas.drawRect(
                    0f,
                    content.bottom.toFloat(),
                    measuredWidth.toFloat(),
                    measuredHeight.toFloat(),
                    overlayPaint
                )
            }
            DIRECTION_TOP -> {
                canvas.drawRect(
                    0f,
                    0f,
                    measuredWidth.toFloat(),
                    content.top.toFloat(),
                    overlayPaint
                )
            }
            // Otros casos si se necesitan
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!config.touchEnabled) {
            return false
        }
        
        val action = ev.actionMasked
        val y = ev.y
        
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // Verificar si el touch está en la zona sensible (45% superior)
                // Como df/c.java onInterceptTouchEvent líneas 230-265
                isInTouchZone = when (config.direction) {
                    DIRECTION_BOTTOM -> y < config.touchThreshold * height
                    DIRECTION_TOP -> y > height - config.touchThreshold * height
                    else -> y < config.touchThreshold * height
                }
                
                if (!isInTouchZone) {
                    return false
                }
            }
        }
        
        return try {
            dragHelper.shouldInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!config.touchEnabled) {
            return false
        }
        
        return try {
            dragHelper.processTouchEvent(event)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Actualizar opacidad del overlay basado en el offset de slide
     * Como df/c.b() en la app original
     */
    private fun updateOverlayAlpha(slideOffset: Float) {
        // slideOffset: 1.0 = cerrado (en posición original), 0.0 = abierto (arrastrado completamente)
        val alpha = ((0.8f * slideOffset) + 0.0f) * 255.0f
        overlayPaint.alpha = alpha.toInt()
        
        // Calcular rect para invalidar
        val content = contentView ?: return
        when (config.direction) {
            DIRECTION_BOTTOM -> {
                invalidateRect.set(0, content.bottom, measuredWidth, measuredHeight)
            }
            DIRECTION_TOP -> {
                invalidateRect.set(0, 0, measuredWidth, content.top)
            }
        }
        invalidate(invalidateRect)
    }

    /**
     * Callback para ViewDragHelper
     * Como df/a.java en la app original
     */
    private inner class DragCallback : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            // Solo capturar si es la vista de contenido y estamos en la zona sensible
            if (child.id != contentView?.id) {
                return false
            }
            
            if (config.touchEnabled && !isInTouchZone) {
                return false
            }
            
            return true
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            // Limitar el movimiento vertical según la dirección
            // Como df/a.java e() método
            return when (config.direction) {
                DIRECTION_BOTTOM -> {
                    // Solo permitir arrastrar hacia abajo (top >= 0)
                    clamp(top, 0, screenHeight)
                }
                DIRECTION_TOP -> {
                    // Solo permitir arrastrar hacia arriba (top <= 0)
                    clamp(top, -screenHeight, 0)
                }
                DIRECTION_VERTICAL -> {
                    // Permitir ambas direcciones
                    clamp(top, -screenHeight, screenHeight)
                }
                else -> 0
            }
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            // No permitir movimiento horizontal para DIRECTION_BOTTOM
            return when (config.direction) {
                DIRECTION_LEFT, DIRECTION_RIGHT, DIRECTION_HORIZONTAL -> {
                    clamp(left, -screenWidth, screenWidth)
                }
                else -> 0
            }
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return when (config.direction) {
                DIRECTION_BOTTOM, DIRECTION_TOP, DIRECTION_VERTICAL -> screenHeight
                else -> 0
            }
        }

        override fun getViewHorizontalDragRange(child: View): Int {
            return when (config.direction) {
                DIRECTION_LEFT, DIRECTION_RIGHT, DIRECTION_HORIZONTAL -> screenWidth
                else -> 0
            }
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            
            // Calcular offset de slide (1.0 = posición original, 0.0 = completamente arrastrado)
            // Como df/a.java v() método
            val slideOffset = when (config.direction) {
                DIRECTION_BOTTOM -> 1.0f - (abs(top).toFloat() / screenHeight)
                DIRECTION_TOP -> 1.0f - (abs(top).toFloat() / screenHeight)
                else -> 1.0f - (abs(top).toFloat() / screenHeight)
            }
            
            slideListener?.onPanelSlide(slideOffset)
            updateOverlayAlpha(slideOffset)
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            
            // Decidir si cerrar o abrir basado en velocidad y posición
            // Como df/a.java w() método - case 3 (DIRECTION_BOTTOM)
            val top = releasedChild.top
            val threshold = (height * CLOSE_THRESHOLD).toInt()
            val isHorizontalFling = abs(xvel) > MIN_FLING_VELOCITY
            var targetTop = 0
            
            when (config.direction) {
                DIRECTION_BOTTOM -> {
                    if (yvel > 0) {
                        // Arrastrando hacia abajo
                        if (abs(yvel) > MIN_FLING_VELOCITY && !isHorizontalFling) {
                            // Fling hacia abajo - cerrar
                            targetTop = screenHeight
                        } else if (top > threshold) {
                            // Pasó el threshold - cerrar
                            targetTop = screenHeight
                        }
                    } else if (yvel == 0f && top > threshold) {
                        // Sin velocidad pero pasó el threshold - cerrar
                        targetTop = screenHeight
                    }
                }
                DIRECTION_TOP -> {
                    if (yvel < 0) {
                        if (abs(yvel) > MIN_FLING_VELOCITY && !isHorizontalFling) {
                            targetTop = -screenHeight
                        } else if (top < -threshold) {
                            targetTop = -screenHeight
                        }
                    } else if (yvel == 0f && top < -threshold) {
                        targetTop = -screenHeight
                    }
                }
            }
            
            // Animar a la posición objetivo
            dragHelper.settleCapturedViewAt(releasedChild.left, targetTop)
            invalidate()
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            
            // Notificar cambio de estado
            // Como df/a.java u() método
            slideListener?.onDragStateChanged(state)
            
            if (state == ViewDragHelper.STATE_IDLE) {
                val content = contentView ?: return
                
                when (config.direction) {
                    DIRECTION_BOTTOM -> {
                        if (content.top != 0) {
                            // Panel cerrado (arrastrado hacia abajo)
                            slideListener?.onPanelClosed()
                        } else {
                            // Panel en posición original
                            slideListener?.onPanelOpened()
                        }
                    }
                    DIRECTION_TOP -> {
                        if (content.top != 0) {
                            slideListener?.onPanelClosed()
                        } else {
                            slideListener?.onPanelOpened()
                        }
                    }
                }
            }
        }
    }

    /**
     * Función helper para limitar un valor entre min y max
     * Como df/c.a() en la app original
     */
    private fun clamp(value: Int, min: Int, max: Int): Int {
        return max(min, min(max, value))
    }
}
