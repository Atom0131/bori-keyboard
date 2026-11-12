package com.bori.keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.bori.keyboard.theme.KeyboardTheme

/**
 * Shown in place of the suggestion strip while typing a GIF or clipboard search: the keys
 * type into this bar, not into the app. Layout: [←] [🔍 query▌] [✕].
 */
class SearchBarView(context: Context) : View(context) {

    interface Listener {
        /** ← : leave the search without searching. */
        fun onSearchBack()
        /** ✕ : clear what was typed. */
        fun onSearchClear()
    }

    var listener: Listener? = null
    private var theme = KeyboardTheme.light()
    private var query = ""
    private var hint = ""
    private var caretOn = true

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint()
    /** False while an ambient effect runs behind us: InputRootView paints the background. */
    var paintBackground = true
    private val blink = Handler(Looper.getMainLooper())
    private val blinker = object : Runnable {
        override fun run() {
            caretOn = !caretOn
            invalidate()
            blink.postDelayed(this, 530)
        }
    }

    private val side get() = 48 * density

    fun setTheme(theme: KeyboardTheme) {
        this.theme = theme
        invalidate()
    }

    fun set(query: String, hint: String) {
        this.query = query
        this.hint = hint
        caretOn = true
        invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        blink.removeCallbacks(blinker)
        if (visibility == VISIBLE && isShown) blink.postDelayed(blinker, 530)
    }

    override fun onDetachedFromWindow() {
        blink.removeCallbacks(blinker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Only our own rectangle: drawColor fills the whole clip, and InputRootView turns
        // child clipping off (for press effects), so it would paint over the suggestion bar.
        val bg = theme.solidBackground
        if (paintBackground && android.graphics.Color.alpha(bg) != 0) {
            backgroundPaint.color = bg
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        }
        val h = height.toFloat()
        // The field.
        paint.color = theme.keyLetter
        val l = side
        val r = width - side
        canvas.drawRoundRect(l, 5 * density, r, h - 5 * density, h / 2, h / 2, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = h * 0.42f
        paint.color = theme.stripText
        val cy = h / 2 - (paint.descent() + paint.ascent()) / 2
        canvas.drawText("←", side / 2, cy, paint)
        if (query.isNotEmpty()) canvas.drawText("✕", width - side / 2, cy, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = h * 0.36f
        val textLeft = l + 14 * density
        val maxW = r - textLeft - 14 * density
        val cy2 = h / 2 - (paint.descent() + paint.ascent()) / 2
        if (query.isEmpty()) {
            paint.color = theme.hint
            canvas.drawText(fit("🔍  $hint", maxW), textLeft, cy2, paint)
            if (caretOn) drawCaret(canvas, textLeft + paint.measureText("🔍  ") - 2 * density)
        } else {
            paint.color = theme.text
            // Long queries show their end, where the typing is.
            var t = query
            while (t.length > 1 && paint.measureText(t) > maxW - 4 * density) t = t.substring(1)
            canvas.drawText(t, textLeft, cy2, paint)
            if (caretOn) drawCaret(canvas, textLeft + paint.measureText(t) + 1 * density)
        }
    }

    private fun drawCaret(canvas: Canvas, x: Float) {
        paint.color = theme.accent
        canvas.drawRect(x, height * 0.28f, x + 2 * density, height * 0.72f, paint)
    }

    private fun fit(t: String, maxW: Float): String {
        if (paint.measureText(t) <= maxW) return t
        val n = paint.breakText(t, true, maxW - paint.measureText("…"), null)
        return t.take(n.coerceAtLeast(1)) + "…"
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            when {
                e.x < side -> listener?.onSearchBack()
                e.x > width - side && query.isNotEmpty() -> listener?.onSearchClear()
            }
        }
        return true
    }
}
