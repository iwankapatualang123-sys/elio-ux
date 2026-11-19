package com.autosplit.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable

/**
 * Kartu transparan melayang: isi kaca gelap tembus pandang dan satu garis tepi tebal dengan sudut
 * membulat. Garis polos warna aksen, atau gradasi berputar saat Ambient.running (mode Berjalan).
 * getOutline() memberi bentuk untuk bayangan elevation.
 */
class GlassDrawable(
    density: Float,
    radiusDp: Float = 14f,
    private val accentFill: Boolean = false
) : Drawable() {

    private val radius = radiusDp * density
    private val line = maxOf(3f, density * 3f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = line
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = line * 0.42f
        strokeJoin = Paint.Join.ROUND
    }

    private val rect = RectF()
    private val rot = Matrix()
    private var sweep: SweepGradient? = null
    private var sweepKey = 0L

    override fun onBoundsChange(b: Rect) {
        val h = b.height().toFloat()
        val a = Pal.accent
        val top = if (accentFill) Color.argb(80, Color.red(a), Color.green(a), Color.blue(a)) else Color.argb(90, 20, 28, 40)
        val bottom = if (accentFill) Color.argb(30, Color.red(a), Color.green(a), Color.blue(a)) else Color.argb(50, 6, 10, 16)
        fill.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        rect.set(b.left + line / 2f, b.top + line / 2f, b.right - line / 2f, b.bottom - line / 2f)
        gloss.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            intArrayOf(Color.argb(220, 255, 255, 255), Color.argb(110, 255, 255, 255), Color.argb(60, 255, 255, 255)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        sweep = null
    }

    override fun draw(c: Canvas) {
        c.drawRoundRect(rect, radius, radius, fill)
        if (Ambient.running) {
            val key = Pal.accent.toLong() shl 32 or (Pal.accent2.toLong() and 0xFFFFFFFFL)
            val cx = rect.centerX()
            val cy = rect.centerY()
            val sg = sweep?.takeIf { sweepKey == key } ?: SweepGradient(
                cx, cy,
                Ambient.loopColors(Pal.accent, Pal.accent2),
                null
            ).also {
                sweep = it
                sweepKey = key
            }
            rot.setRotate(Ambient.angle(), cx, cy)
            sg.setLocalMatrix(rot)
            outline.shader = sg
        } else {
            outline.shader = null
            outline.color = Pal.accent
        }
        c.drawRoundRect(rect, radius, radius, outline)
        c.drawRoundRect(rect, radius, radius, gloss)
    }

    override fun getOutline(o: Outline) {
        o.setRoundRect(bounds, radius)
        o.alpha = 0.45f
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        outline.alpha = alpha
        gloss.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        outline.colorFilter = colorFilter
        gloss.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
