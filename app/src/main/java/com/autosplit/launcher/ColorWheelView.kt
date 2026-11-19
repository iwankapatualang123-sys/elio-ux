package com.autosplit.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Roda warna bulat: sudut = hue, jarak dari tengah = saturasi. Kecerahan diatur dari luar. */
class ColorWheelView(ctx: Context) : View(ctx) {

    var onChange: ((Int) -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val hsv = floatArrayOf(0f, 1f, 1f)

    private val wheel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sat = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = Color.argb(200, 255, 255, 255)
    }
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        color = Color.WHITE
    }
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cx = 0f
    private var cy = 0f
    private var rad = 1f

    fun color(): Int = Color.HSVToColor(hsv)

    fun setColor(c: Int) {
        Color.colorToHSV(c, hsv)
        invalidate()
    }

    var brightness: Float
        get() = hsv[2]
        set(v) {
            hsv[2] = v.coerceIn(0.2f, 1f)
            invalidate()
            onChange?.invoke(color())
        }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val max = (260f * d).toInt()
        val w = MeasureSpec.getSize(widthSpec)
        val size = if (w > 0) min(w, max) else max
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        rad = min(w, h) / 2f - 14f * d
        wheel.shader = SweepGradient(
            cx, cy,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null
        )
        sat.shader = RadialGradient(cx, cy, rad, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        c.drawCircle(cx, cy, rad, wheel)
        c.drawCircle(cx, cy, rad, sat)
        shade.color = Color.argb(((1f - hsv[2]) * 255).toInt(), 0, 0, 0)
        c.drawCircle(cx, cy, rad, shade)
        c.drawCircle(cx, cy, rad, rim)

        val a = Math.toRadians(hsv[0].toDouble())
        val tx = cx + (cos(a) * hsv[1] * rad).toFloat()
        val ty = cy + (sin(a) * hsv[1] * rad).toFloat()
        thumbFill.color = color()
        c.drawCircle(tx, ty, 11f * d, thumbFill)
        c.drawCircle(tx, ty, 11f * d, thumbRing)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val dx = e.x - cx
        val dy = e.y - cy
        var h = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (h < 0f) h += 360f
        hsv[0] = h
        hsv[1] = min(1f, sqrt(dx * dx + dy * dy) / rad)
        invalidate()
        onChange?.invoke(color())
        return true
    }
}
