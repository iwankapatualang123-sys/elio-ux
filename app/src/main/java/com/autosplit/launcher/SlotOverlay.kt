package com.autosplit.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View

/**
 * Bingkai tebal berwarna aksen: satu bingkai penuh di tepi layar, dan bingkai utuh di sekeliling
 * tiap jendela aplikasi beserta labelnya. Bingkai bisa Diam (warna tetap) atau Berjalan
 * (gradasi soft berputar mengikuti jam Ambient, seperti ambient light). Tidak menangkap sentuhan.
 */
class SlotOverlay(ctx: Context) : View(ctx) {

    class Slot(val rect: Rect, val label: String, val frame: Boolean = true)

    private var slots: List<Slot> = emptyList()
    private val d = ctx.resources.displayMetrics.density
    private val loc = IntArray(2)
    private val box = RectF()
    private val rot = Matrix()
    private var sweep: SweepGradient? = null

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(3.5f, d * 3.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(7f, d * 7f)
        strokeJoin = Paint.Join.ROUND
    }
    private val glowWide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d * 22f
        alpha = 38
    }
    private val glowMid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d * 13f
        alpha = 80
    }
    private val screenGloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(2.5f, d * 2.6f)
        strokeJoin = Paint.Join.ROUND
    }
    private val frameGloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.5f, d * 1.4f)
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * d
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.12f
    }

    fun setSlots(list: List<Slot>) {
        slots = list
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sweep = null
        val g = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(120, 255, 255, 255), Color.argb(70, 255, 255, 255)),
            floatArrayOf(0f, 0.4f, 1f), android.graphics.Shader.TileMode.CLAMP
        )
        screenGloss.shader = g
        frameGloss.shader = g
    }

    override fun onDraw(c: Canvas) {
        framePaint.color = Pal.accent
        text.color = Pal.accent

        val sw = screenPaint.strokeWidth
        box.set(sw / 2f + d, sw / 2f + d, width - sw / 2f - d, height - sw / 2f - d)
        val radius = 20f * d
        val sg: SweepGradient? = if (Ambient.running) (sweep ?: SweepGradient(
                width / 2f, height / 2f,
                Ambient.loopColors(Pal.accent, Pal.accent2),
                null
            ).also { sweep = it }) else null
        if (sg != null) {
            rot.setRotate(Ambient.angle(), width / 2f, height / 2f)
            sg.setLocalMatrix(rot)
            screenPaint.shader = sg
            if (!Ambient.lite) {
                glowWide.shader = sg
                glowMid.shader = sg
                c.drawRoundRect(box, radius, radius, glowWide)
                c.drawRoundRect(box, radius, radius, glowMid)
            }
        } else {
            screenPaint.shader = null
            screenPaint.color = Pal.accent
        }
        c.drawRoundRect(box, radius, radius, screenPaint)
        c.drawRoundRect(box, radius, radius, screenGloss)

        if (slots.isEmpty()) return
        getLocationOnScreen(loc)
        val pad = PAD_DP * d
        val strip = STRIP_DP * d
        val r = 14f * d
        for (s in slots) {
            if (s.frame) {
                box.set(
                    s.rect.left - loc[0] - pad,
                    s.rect.top - loc[1] - strip,
                    s.rect.right - loc[0] + pad,
                    s.rect.bottom - loc[1] + pad
                )
                if (sg != null) framePaint.shader = sg else { framePaint.shader = null; framePaint.color = Pal.accent }
                c.drawRoundRect(box, r, r, framePaint)
                c.drawRoundRect(box, r, r, frameGloss)
            }
            c.drawText(s.label, s.rect.left - loc[0] + pad, s.rect.top - loc[1] - strip * 0.3f, text)
        }
    }

    companion object {
        const val PAD_DP = 6f
        const val STRIP_DP = 22f
    }
}
