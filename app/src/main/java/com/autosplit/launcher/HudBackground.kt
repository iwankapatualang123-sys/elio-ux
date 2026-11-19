package com.autosplit.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Latar HUD gelap bergradien radial dengan kisi garis sangat tipis. Digambar ulang hanya saat perlu. */
class HudBackground(private val density: Float) : Drawable() {

    private val base = Paint()
    private val grid = Paint().apply {
        color = Color.argb(14, 0, 229, 255)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    override fun onBoundsChange(b: Rect) {
        base.shader = RadialGradient(
            b.exactCenterX(), b.exactCenterY(), max(b.width(), b.height()) * 0.7f,
            intArrayOf(Color.parseColor("#0C1824"), Color.parseColor("#04060A")),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, base)
        val step = 48f * density
        var x = b.left + step
        while (x < b.right) {
            c.drawLine(x, b.top.toFloat(), x, b.bottom.toFloat(), grid)
            x += step
        }
        var y = b.top + step
        while (y < b.bottom) {
            c.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, grid)
            y += step
        }
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
