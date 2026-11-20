package com.wsclient

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class Glyph { POWER, DOWNLOAD, LAYERS, CHEVRON, GAUGE, REFRESH, MORE, SERVER, GLOBE, HOME, LIST, CHART, GEAR, PLUS }

object Ui {
    /** Applies [percent] (0..100) opacity to [color]. */
    fun withAlpha(color: Int, percent: Int): Int {
        val a = (percent.coerceIn(0, 100) * 255 / 100)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)),
            ch(Color.blue(a), Color.blue(b))
        )
    }

    @Suppress("DEPRECATION")
    fun prepareWindow(a: Activity, p: Customization.Palette) {
        val w = a.window
        WindowCompat.setDecorFitsSystemWindows(w, false)
        w.setBackgroundDrawable(ColorDrawable(p.background))
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        val ctl = WindowCompat.getInsetsController(w, w.decorView)
        ctl.isAppearanceLightStatusBars = p.isLight
        ctl.isAppearanceLightNavigationBars = p.isLight
    }

    fun onInsets(view: View, block: (Insets) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            block(
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun padWithInsets(view: View) {
        val l = view.paddingLeft
        val t = view.paddingTop
        val r = view.paddingRight
        val b = view.paddingBottom
        onInsets(view) { i -> view.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom) }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.72f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
    }
}

/** Линейные иконки в сетке 24x24, ресурсы не нужны. */
object Glyphs {
    private val path = Path()

    fun draw(c: Canvas, g: Glyph, size: Float, color: Int, p: Paint) {
        val u = size / 24f
        c.save()
        c.scale(u, u)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = color

        when (g) {
            Glyph.POWER -> {
                c.drawArc(RectF(3f, 4f, 21f, 22f), -45f, 270f, false, p)
                c.drawLine(12f, 2f, 12f, 12f, p)
            }
            Glyph.DOWNLOAD -> {
                path.reset()
                path.moveTo(3f, 15f); path.lineTo(3f, 19f); path.quadTo(3f, 21f, 5f, 21f)
                path.lineTo(19f, 21f); path.quadTo(21f, 21f, 21f, 19f); path.lineTo(21f, 15f)
                c.drawPath(path, p)
                c.drawLine(12f, 3f, 12f, 15f, p)
                poly(c, p, false, 7f, 10f, 12f, 15f, 17f, 10f)
            }
            Glyph.LAYERS -> {
                poly(c, p, true, 12f, 2f, 2f, 7f, 12f, 12f, 22f, 7f)
                poly(c, p, false, 2f, 12f, 12f, 17f, 22f, 12f)
                poly(c, p, false, 2f, 17f, 12f, 22f, 22f, 17f)
            }
            Glyph.CHEVRON -> poly(c, p, false, 9f, 6f, 15f, 12f, 9f, 18f)
            Glyph.GAUGE -> {
                c.drawArc(RectF(3f, 3f, 21f, 21f), 150f, 240f, false, p)
                c.drawLine(12f, 13f, 16.5f, 8.5f, p)
                dot(c, p, 12f, 13f, 1.4f)
            }
            Glyph.REFRESH -> {
                c.drawArc(RectF(3.5f, 3.5f, 20.5f, 20.5f), 30f, 280f, false, p)
                val a = Math.toRadians(310.0)
                val tx = (12 + 8.5 * cos(a)).toFloat()
                val ty = (12 + 8.5 * sin(a)).toFloat()
                val dx = (-sin(a)).toFloat()
                val dy = cos(a).toFloat()
                val nx = -dy
                val ny = dx
                poly(
                    c, p, false,
                    tx - 4.5f * dx + 3.2f * nx, ty - 4.5f * dy + 3.2f * ny,
                    tx, ty,
                    tx - 4.5f * dx - 3.2f * nx, ty - 4.5f * dy - 3.2f * ny
                )
            }
            Glyph.MORE -> {
                dot(c, p, 12f, 5f, 1.8f); dot(c, p, 12f, 12f, 1.8f); dot(c, p, 12f, 19f, 1.8f)
            }
            Glyph.SERVER -> {
                c.drawRoundRect(RectF(2f, 3f, 22f, 10.5f), 2.5f, 2.5f, p)
                c.drawRoundRect(RectF(2f, 13.5f, 22f, 21f), 2.5f, 2.5f, p)
                dot(c, p, 6.5f, 6.75f, 1.1f)
                dot(c, p, 6.5f, 17.25f, 1.1f)
            }
            Glyph.GLOBE -> {
                c.drawCircle(12f, 12f, 10f, p)
                c.drawLine(2f, 12f, 22f, 12f, p)
                c.drawOval(RectF(7.5f, 2f, 16.5f, 22f), p)
            }
            Glyph.HOME -> {
                path.reset()
                path.moveTo(3f, 10.5f); path.lineTo(12f, 3f); path.lineTo(21f, 10.5f)
                path.lineTo(21f, 19f); path.quadTo(21f, 21f, 19f, 21f)
                path.lineTo(5f, 21f); path.quadTo(3f, 21f, 3f, 19f); path.close()
                c.drawPath(path, p)
                poly(c, p, false, 9f, 21f, 9f, 13f, 15f, 13f, 15f, 21f)
            }
            Glyph.LIST -> {
                c.drawLine(9f, 6f, 21f, 6f, p)
                c.drawLine(9f, 12f, 21f, 12f, p)
                c.drawLine(9f, 18f, 21f, 18f, p)
                dot(c, p, 4.5f, 6f, 1.3f); dot(c, p, 4.5f, 12f, 1.3f); dot(c, p, 4.5f, 18f, 1.3f)
            }
            Glyph.CHART -> {
                c.drawLine(6f, 20f, 6f, 13f, p)
                c.drawLine(12f, 20f, 12f, 5f, p)
                c.drawLine(18f, 20f, 18f, 10f, p)
            }
            Glyph.GEAR -> {
                c.drawCircle(12f, 12f, 3.2f, p)
                c.drawCircle(12f, 12f, 6.8f, p)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    c.drawLine(
                        (12 + 6.8 * cos(a)).toFloat(), (12 + 6.8 * sin(a)).toFloat(),
                        (12 + 9.8 * cos(a)).toFloat(), (12 + 9.8 * sin(a)).toFloat(), p
                    )
                }
            }
            Glyph.PLUS -> {
                c.drawLine(12f, 5f, 12f, 19f, p)
                c.drawLine(5f, 12f, 19f, 12f, p)
            }
        }
        c.restore()
    }

    private fun poly(c: Canvas, p: Paint, close: Boolean, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        if (close) path.close()
        c.drawPath(path, p)
    }

    private fun dot(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
    }
}

class GlyphView(context: Context, private val glyph: Glyph, private var color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun tint(c: Int) { color = c; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        Glyphs.draw(canvas, glyph, s, color, paint)
        canvas.restore()
    }
}

/** Круглая кнопка питания; при подключении вокруг вращается дуга. */
class PowerButtonView(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private var ringColor = Color.WHITE
    private var fillColor = Color.TRANSPARENT
    private var spinning = false
    private var spin = 0f
    private var anim: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        Ui.pressable(this)
    }

    fun update(ring: Int, fill: Int, spinning: Boolean) {
        ringColor = ring
        fillColor = fill
        this.spinning = spinning
        if (spinning) startSpin() else stopSpin()
        invalidate()
    }

    private fun startSpin() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { spin = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopSpin() { anim?.cancel(); anim = null }

    override fun onDetachedFromWindow() { stopSpin(); super.onDetachedFromWindow() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (spinning) startSpin()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val stroke = 3f * d
        val r = min(cx, cy) - stroke

        fill.color = fillColor
        canvas.drawCircle(cx, cy, r, fill)

        ring.strokeWidth = stroke
        ring.strokeCap = Paint.Cap.ROUND
        ring.color = ringColor
        if (spinning) {
            ring.alpha = 70
            canvas.drawCircle(cx, cy, r, ring)
            ring.alpha = 255
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), spin, 100f, false, ring)
        } else {
            canvas.drawCircle(cx, cy, r, ring)
        }

        val g = 40f * d
        canvas.save()
        canvas.translate(cx - g / 2f, cy - g / 2f)
        Glyphs.draw(canvas, Glyph.POWER, g, ringColor, glyph)
        canvas.restore()
    }
}
