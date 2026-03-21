package com.haidianfirstteam.nostalgiaai.ui.tutorial

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.haidianfirstteam.nostalgiaai.R

class SpotlightOverlay(context: Context) : android.widget.FrameLayout(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt()
        style = Paint.Style.FILL
    }
    private var scrimAlphaMul: Float = 1.0f
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }
    private var hole: RectF? = null
    private var holeAnim: ValueAnimator? = null
    private var bubbleLaidOut: Boolean = false
    private var bubbleW: Int = 0
    private var bubbleH: Int = 0

    private val bubbleCard = MaterialCardView(context).apply {
        radius = dp(12).toFloat()
        cardElevation = dp(6).toFloat()
        setCardBackgroundColor(0xFFFFFFFF.toInt())
        useCompatPadding = true
    }
    private val tvText = TextView(context).apply {
        setTextColor(0xFF111111.toInt())
        textSize = 14f
    }
    private val tvProgress = TextView(context).apply {
        setTextColor(0xFF666666.toInt())
        textSize = 12f
        gravity = Gravity.END
    }
    private val btnSkip = TextView(context).apply {
        text = "跳过"
        setTextColor(0xFF444444.toInt())
        textSize = 12f
        setPadding(dp(6), dp(4), dp(6), dp(4))
    }
    private val btnSkipAll = TextView(context).apply {
        text = "全部跳过"
        setTextColor(0xFF444444.toInt())
        textSize = 12f
        setPadding(dp(6), dp(4), dp(6), dp(4))
    }
    private val btnNext = TextView(context).apply {
        text = "下一步"
        setTextColor(0xFF0B57D0.toInt())
        textSize = 13f
        setPadding(dp(10), dp(8), dp(10), dp(8))
        gravity = Gravity.END
    }

    var onNext: (() -> Unit)? = null
    var onSkip: (() -> Unit)? = null
    var onSkipAll: (() -> Unit)? = null

    init {
        setWillNotDraw(false)
        // Required for CLEAR xfermode on API 19
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val left = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                addView(btnSkip)
                addView(btnSkipAll)
            }
            addView(left, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(tvProgress, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
            addView(topRow)
            addView(tvText, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
            addView(btnNext, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }
        bubbleCard.addView(wrap)
        addView(bubbleCard, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        btnNext.setOnClickListener { onNext?.invoke() }
        btnSkip.setOnClickListener { onSkip?.invoke() }
        btnSkipAll.setOnClickListener { onSkipAll?.invoke() }
    }

    fun setStep(text: String, holeRect: RectF?, stepIndex1: Int, stepTotal: Int, isLast: Boolean) {
        tvText.text = text
        tvProgress.text = "${stepIndex1}/${stepTotal}"
        btnNext.text = if (isLast) "完成" else "下一步"

        // Animate hole transition.
        val prev = hole
        holeAnim?.cancel()
        if (prev != null && holeRect != null) {
            val from = RectF(prev)
            val to = RectF(holeRect)
            val anim = ValueAnimator.ofFloat(0f, 1f)
            anim.duration = 200
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener {
                val t = it.animatedValue as Float
                hole = RectF(
                    from.left + (to.left - from.left) * t,
                    from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t,
                    from.bottom + (to.bottom - from.bottom) * t,
                )
                invalidate()
            }
            anim.start()
            holeAnim = anim
        } else {
            hole = holeRect
        }

        // Light breathing effect on step change.
        ValueAnimator.ofFloat(1.0f, 0.92f, 1.0f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrimAlphaMul = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        post { layoutBubble() }
        invalidate()
    }

    // Backward-compatible overload
    fun setStep(text: String, holeRect: RectF?, isLast: Boolean) {
        setStep(text, holeRect, stepIndex1 = 1, stepTotal = 1, isLast = isLast)
    }

    private fun layoutBubble() {
        val h = hole
        val maxW = (width - dp(24)).coerceAtLeast(dp(200))
        bubbleCard.layoutParams = (bubbleCard.layoutParams as LayoutParams).apply {
            width = maxW
        }
        bubbleCard.measure(
            MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
        val bw = bubbleCard.measuredWidth
        val bh = bubbleCard.measuredHeight

        val x = dp(12)
        val y = if (h == null) {
            dp(90)
        } else {
            val centerY = (h.top + h.bottom) / 2f
            if (centerY < height / 2f) {
                // place below
                (h.bottom + dp(14)).toInt().coerceAtMost(height - bh - dp(12))
            } else {
                // place above
                (h.top - bh - dp(14)).toInt().coerceAtLeast(dp(12))
            }
        }
        if (!bubbleLaidOut || bubbleW != bw || bubbleH != bh) {
            bubbleCard.layout(0, 0, bw, bh)
            bubbleW = bw
            bubbleH = bh
            bubbleLaidOut = true
        }

        val nx = x.toFloat()
        val ny = y.toFloat()
        bubbleCard.animate()
            .x(nx)
            .y(ny)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        @Suppress("DEPRECATION")
        val save = if (Build.VERSION.SDK_INT >= 21) {
            canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        } else {
            // API 19: only the 6-arg overload exists.
            canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null, Canvas.ALL_SAVE_FLAG)
        }
        val baseAlpha = (0xCC * scrimAlphaMul).toInt().coerceIn(0, 255)
        scrimPaint.alpha = baseAlpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        val h = hole
        if (h != null) {
            val w = h.width()
            val hh = h.height()
            val isCircle = kotlin.math.abs(w - hh) <= dp(10) && kotlin.math.max(w, hh) <= dp(120)
            if (isCircle) {
                val cx = (h.left + h.right) / 2f
                val cy = (h.top + h.bottom) / 2f
                val r = kotlin.math.max(w, hh) / 2f
                canvas.drawCircle(cx, cy, r, clearPaint)
            } else {
                val r = dp(16).toFloat()
                canvas.drawRoundRect(h, r, r, clearPaint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Tap anywhere outside bubble -> next
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val x = event.x.toInt()
            val y = event.y.toInt()
            if (!(x >= bubbleCard.left && x <= bubbleCard.right && y >= bubbleCard.top && y <= bubbleCard.bottom)) {
                onNext?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
