package com.autosplit.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View

/**
 * Bingkai layar & jendela split bergaya Luxury HD (4 gaya mengikuti Pal.frameStyle).
 * Strip label 22dp lama sudah dirapatkan menjadi 6dp agar Google Maps & YouTube jauh lebih luas.
 */
class SlotOverlay(ctx: Context) : View(ctx) {

    class Slot(val rect: Rect, val label: String, val frame: Boolean = true)

    private var slots: List<Slot> = emptyList()
    private val d = ctx.resources.displayMetrics.density
    private val loc = IntArray(2)
    private val box = RectF()
    private val innerBox = RectF()
    private val rot = Matrix()
    private var sweep: SweepGradient? = null
    private var sweepKey = 0L

    private val thin = maxOf(1.2f, d * 1.25f)
    private val mid = maxOf(2.1f, d * 2.2f)

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thin
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(5.5f, d * 6f)
        strokeJoin = Paint.Join.ROUND
        alpha = 52
    }
    private val accentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = mid
        strokeCap = Paint.Cap.ROUND
    }
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thin
        strokeJoin = Paint.Join.ROUND
    }

    fun setSlots(list: List<Slot>) {
        slots = list
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sweep = null
    }

    private fun getSweep(c1: Int, c2: Int): SweepGradient? {
        if (!Ambient.running) return null
        val key = (c1.toLong() shl 32) or (c2.toLong() and 0xFFFFFFFFL)
        val sg = sweep?.takeIf { sweepKey == key } ?: SweepGradient(
            width / 2f, height / 2f, Ambient.loopColors(c1, c2), null
        ).also {
            sweep = it
            sweepKey = key
        }
        rot.setRotate(Ambient.angle(), width / 2f, height / 2f)
        sg.setLocalMatrix(rot)
        return sg
    }

    private fun drawLuxuryRect(c: Canvas, r: RectF, radius: Float, c1: Int, c2: Int, sg: SweepGradient?) {
        when (Pal.frameStyle) {
            0 -> { // MBUX Titanium + Ambient Light-Bar
                framePaint.shader = null
                framePaint.strokeWidth = thin
                framePaint.color = if (Pal.alertRed) Color.argb(170, 248, 113, 113) else Color.argb(56, 255, 255, 255)
                c.drawRoundRect(r, radius, radius, framePaint)

                val insetX = r.width() * 0.12f
                accentBar.strokeWidth = mid
                accentBar.shader = sg ?: LinearGradient(
                    r.left + insetX, r.top, r.right - insetX, r.top,
                    intArrayOf(Color.TRANSPARENT, c1, c2, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP
                )
                c.drawLine(r.left + insetX, r.top, r.right - insetX, r.top, accentBar)
            }
            1 -> { // Lexus Sapphire Double-Bezel
                framePaint.strokeWidth = thin
                framePaint.shader = sg ?: LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    intArrayOf(
                        Color.argb(135, 255, 255, 255),
                        Color.argb(165, Color.red(c1), Color.green(c1), Color.blue(c1)),
                        Color.argb(35, 255, 255, 255),
                        Color.argb(165, Color.red(c2), Color.green(c2), Color.blue(c2))
                    ),
                    floatArrayOf(0f, 0.28f, 0.65f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRoundRect(r, radius, radius, framePaint)
                innerBox.set(r.left + thin * 1.5f, r.top + thin * 1.5f, r.right - thin * 1.5f, r.bottom - thin * 1.5f)
                bevelPaint.color = Color.argb(36, 255, 255, 255)
                c.drawRoundRect(innerBox, (radius - thin).coerceAtLeast(2f), (radius - thin).coerceAtLeast(2f), bevelPaint)
            }
            2 -> { // BMW Precision Carbon + Siku HD
                framePaint.shader = null
                framePaint.strokeWidth = thin
                framePaint.color = Color.argb(50, 148, 163, 184)
                c.drawRoundRect(r, radius, radius, framePaint)

                val arm = minOf(d * 18f, r.width() * 0.16f, r.height() * 0.22f)
                accentBar.strokeWidth = mid
                accentBar.shader = sg
                accentBar.color = c1
                c.drawLine(r.left + radius * 0.3f, r.top, r.left + radius * 0.3f + arm, r.top, accentBar)
                c.drawLine(r.left, r.top + radius * 0.3f, r.left, r.top + radius * 0.3f + arm, accentBar)
                c.drawLine(r.right - radius * 0.3f - arm, r.bottom, r.right - radius * 0.3f, r.bottom, accentBar)
                c.drawLine(r.right, r.bottom - radius * 0.3f - arm, r.right, r.bottom - radius * 0.3f, accentBar)
                if (sg == null) accentBar.color = c2
                c.drawLine(r.right - radius * 0.3f - arm, r.top, r.right - radius * 0.3f, r.top, accentBar)
                c.drawLine(r.right, r.top + radius * 0.3f, r.right, r.top + radius * 0.3f + arm, accentBar)
                c.drawLine(r.left + radius * 0.3f, r.bottom, r.left + radius * 0.3f + arm, r.bottom, accentBar)
                c.drawLine(r.left, r.bottom - radius * 0.3f - arm, r.left, r.bottom - radius * 0.3f, accentBar)
            }
            else -> { // 3: Minimalis Ambient Rim
                framePaint.shader = null
                framePaint.strokeWidth = thin
                framePaint.color = if (Pal.alertRed) Color.argb(150, 248, 113, 113) else Color.argb(38, 255, 255, 255)
                c.drawRoundRect(r, radius, radius, framePaint)
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val c1 = if (Pal.alertRed) Color.parseColor("#EF4444") else Pal.accent
        val c2 = if (Pal.alertRed) Color.parseColor("#F97316") else Pal.accent2
        val sg = getSweep(c1, c2)

        // Outer Luxury HD Line Frame: wraps OUTSIDE the top status/signal bar & BELOW the bottom dock!
        val inset = d * 2.5f
        box.set(inset, inset, width - inset, height - inset)
        val outerR = 16f * d
        val outerShader = sg ?: LinearGradient(
            box.left, box.top, box.right, box.bottom,
            intArrayOf(c1, c2, c1),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        if (Pal.frameStyle != 3 && !Ambient.lite) {
            outerGlow.shader = outerShader
            outerGlow.alpha = if (Pal.frameStyle == 2) 38 else 56
            c.drawRoundRect(box, outerR, outerR, outerGlow)
        }
        accentBar.shader = outerShader
        accentBar.strokeWidth = when (Pal.frameStyle) {
            3 -> thin
            2 -> thin * 1.35f
            else -> mid
        }
        c.drawRoundRect(box, outerR, outerR, accentBar)

        if (slots.isEmpty()) return
        getLocationOnScreen(loc)
        val pad = PAD_DP * d
        val strip = STRIP_DP * d
        val r = 12f * d
        for (s in slots) {
            if (s.frame) {
                box.set(
                    s.rect.left - loc[0] - pad,
                    s.rect.top - loc[1] - strip,
                    s.rect.right - loc[0] + pad,
                    s.rect.bottom - loc[1] + pad
                )
                drawLuxuryRect(c, box, r, c1, c2, sg)
            }
        }
    }

    companion object {
        const val PAD_DP = 4f
        const val STRIP_DP = 6f
    }
}
