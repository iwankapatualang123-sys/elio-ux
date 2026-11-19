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
 * Kartu kaca mewah (Luxury HD) dengan 4 gaya bingkai yang dapat dipilih di Pengaturan:
 * 0 = MBUX Titanium + Ambient Light-Bar (default)
 * 1 = Lexus Sapphire Double-Bezel
 * 2 = BMW Precision Carbon + Siku HD
 * 3 = Minimalis Ambient Rim
 */
class GlassDrawable(
    private val density: Float,
    radiusDp: Float = 13f,
    private val accentFill: Boolean = false
) : Drawable() {

    private val radius = radiusDp * density
    private val thin = maxOf(1f, density * 1.0f)
    private val mid = maxOf(1.5f, density * 1.6f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thin
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val innerBevel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thin
        strokeJoin = Paint.Join.ROUND
    }
    private val accentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = mid
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()
    private val innerRect = RectF()
    private val rot = Matrix()
    private var sweep: SweepGradient? = null
    private var sweepKey = 0L

    override fun onBoundsChange(b: Rect) {
        val h = b.height().toFloat().coerceAtLeast(1f)
        val a = Pal.accent
        val top = if (accentFill) Color.argb(66, Color.red(a), Color.green(a), Color.blue(a))
        else Color.argb(225, 14, 21, 38)
        val bottom = if (accentFill) Color.argb(26, Color.red(a), Color.green(a), Color.blue(a))
        else Color.argb(240, 7, 11, 22)
        fill.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.top + h, top, bottom, Shader.TileMode.CLAMP)
        rect.set(b.left + thin, b.top + thin, b.right - thin, b.bottom - thin)
        innerRect.set(rect.left + thin * 1.4f, rect.top + thin * 1.4f, rect.right - thin * 1.4f, rect.bottom - thin * 1.4f)
        sweep = null
    }

    private fun activeC1(): Int = if (Pal.alertRed) Color.parseColor("#EF4444") else Pal.accent
    private fun activeC2(): Int = if (Pal.alertRed) Color.parseColor("#F97316") else Pal.accent2

    private fun makeSweep(): SweepGradient {
        val c1 = activeC1()
        val c2 = activeC2()
        val key = (c1.toLong() shl 32) or (c2.toLong() and 0xFFFFFFFFL)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val sg = sweep?.takeIf { sweepKey == key } ?: SweepGradient(
            cx, cy, Ambient.loopColors(c1, c2), null
        ).also {
            sweep = it
            sweepKey = key
        }
        rot.setRotate(Ambient.angle(), cx, cy)
        sg.setLocalMatrix(rot)
        return sg
    }

    override fun draw(c: Canvas) {
        val c1 = activeC1()
        val c2 = activeC2()
        c.drawRoundRect(rect, radius, radius, fill)

        when (Pal.frameStyle) {
            0 -> { // MBUX Titanium + Ambient Light-Bar
                border.shader = null
                border.strokeWidth = thin
                border.color = if (Pal.alertRed) Color.argb(160, 248, 113, 113)
                else if (accentFill) Color.argb(145, Color.red(c1), Color.green(c1), Color.blue(c1))
                else Color.argb(46, 255, 255, 255)
                c.drawRoundRect(rect, radius, radius, border)

                // Top specular glass highlight
                innerBevel.color = Color.argb(56, 255, 255, 255)
                c.drawLine(rect.left + radius, rect.top + thin, rect.right - radius, rect.top + thin, innerBevel)

                // Sleek top ambient light-bar
                val insetX = rect.width() * 0.14f
                if (rect.width() > density * 44f) {
                    accentBar.strokeWidth = mid
                    accentBar.shader = if (Ambient.running) makeSweep() else LinearGradient(
                        rect.left + insetX, rect.top, rect.right - insetX, rect.top,
                        intArrayOf(Color.TRANSPARENT, c1, c2, Color.TRANSPARENT),
                        floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP
                    )
                    c.drawLine(rect.left + insetX, rect.top, rect.right - insetX, rect.top, accentBar)
                }
            }
            1 -> { // Lexus Sapphire Double-Bezel
                border.strokeWidth = thin
                border.shader = if (Ambient.running) makeSweep() else LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(
                        Color.argb(115, 255, 255, 255),
                        Color.argb(145, Color.red(c1), Color.green(c1), Color.blue(c1)),
                        Color.argb(30, 255, 255, 255),
                        Color.argb(145, Color.red(c2), Color.green(c2), Color.blue(c2))
                    ),
                    floatArrayOf(0f, 0.28f, 0.65f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRoundRect(rect, radius, radius, border)
                innerBevel.color = Color.argb(34, 255, 255, 255)
                c.drawRoundRect(innerRect, (radius - thin).coerceAtLeast(2f), (radius - thin).coerceAtLeast(2f), innerBevel)
            }
            2 -> { // BMW Precision Carbon + Siku HD (L-Brackets)
                border.shader = null
                border.strokeWidth = thin
                border.color = Color.argb(48, 148, 163, 184)
                c.drawRoundRect(rect, radius, radius, border)

                val arm = minOf(density * 14f, rect.width() * 0.22f, rect.height() * 0.28f)
                accentBar.strokeWidth = mid
                accentBar.shader = if (Ambient.running) makeSweep() else null
                accentBar.color = c1
                // Top-left & Bottom-right in C1
                c.drawLine(rect.left + radius * 0.35f, rect.top, rect.left + radius * 0.35f + arm, rect.top, accentBar)
                c.drawLine(rect.left, rect.top + radius * 0.35f, rect.left, rect.top + radius * 0.35f + arm, accentBar)
                c.drawLine(rect.right - radius * 0.35f - arm, rect.bottom, rect.right - radius * 0.35f, rect.bottom, accentBar)
                c.drawLine(rect.right, rect.bottom - radius * 0.35f - arm, rect.right, rect.bottom - radius * 0.35f, accentBar)
                // Top-right & Bottom-left in C2
                if (!Ambient.running) accentBar.color = c2
                c.drawLine(rect.right - radius * 0.35f - arm, rect.top, rect.right - radius * 0.35f, rect.top, accentBar)
                c.drawLine(rect.right, rect.top + radius * 0.35f, rect.right, rect.top + radius * 0.35f + arm, accentBar)
                c.drawLine(rect.left + radius * 0.35f, rect.bottom, rect.left + radius * 0.35f + arm, rect.bottom, accentBar)
                c.drawLine(rect.left, rect.bottom - radius * 0.35f - arm, rect.left, rect.bottom - radius * 0.35f, accentBar)
            }
            else -> { // 3: Minimalis Ambient Rim
                border.shader = null
                border.strokeWidth = thin
                border.color = if (Pal.alertRed) Color.argb(145, 248, 113, 113)
                else if (accentFill) Color.argb(120, Color.red(c1), Color.green(c1), Color.blue(c1))
                else Color.argb(30, 255, 255, 255)
                c.drawRoundRect(rect, radius, radius, border)
            }
        }
    }

    override fun getOutline(o: Outline) {
        o.setRoundRect(bounds, radius)
        o.alpha = 0.45f
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        border.alpha = alpha
        innerBevel.alpha = alpha
        accentBar.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        border.colorFilter = colorFilter
        innerBevel.colorFilter = colorFilter
        accentBar.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
