package com.autosplit.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.ceil

/**
 * Indikator atas: batang sinyal seluler, busur Wi-Fi, dan baterai bersegmen berwarna.
 * Saat mengisi daya, ikon petir muncul dan satu segmen berdenyut. Digambar hanya saat nilainya berubah.
 */
class StatusIndicator(ctx: Context) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density

    private var cell = -1
    private var wifi = -1
    private var pct = -1
    private var charging = false
    private var tick = 0

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * d
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = Color.argb(235, 255, 255, 255)
    }
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 255, 255, 255)
    }
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * d
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boltPath = Path()
    private val rect = RectF()
    private val arcRect = RectF()

    fun set(cellLevel: Int, wifiLevel: Int, percent: Int, isCharging: Boolean, t: Int) {
        val changed = cellLevel != cell || wifiLevel != wifi || percent != pct || isCharging != charging
        cell = cellLevel
        wifi = wifiLevel
        pct = percent
        charging = isCharging
        tick = t
        if (changed || isCharging) invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension((196f * d).toInt(), (30f * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val base = h / 2f + 9f * d
        val accent = Pal.accent
        val dim = Color.argb(70, 255, 255, 255)

        // Sinyal seluler: empat batang naik
        var x = 2f * d
        for (i in 0..3) {
            val bh = (5f + i * 4f) * d
            bar.color = if (i < cell) Color.WHITE else dim
            rect.set(x, base - bh, x + 4f * d, base)
            c.drawRoundRect(rect, d, d, bar)
            x += 6.5f * d
        }

        // Wi-Fi: tiga busur dan satu titik
        val wx = x + 14f * d
        val lit = if (wifi < 0) 0 else ceil(wifi * 3 / 4.0).toInt().coerceIn(0, 3)
        for (i in 0..2) {
            val rad = (5f + i * 4.5f) * d
            arcRect.set(wx - rad, base - rad, wx + rad, base + rad)
            arcP.color = if (i < lit || (wifi >= 0 && i == 0)) accent else dim
            c.drawArc(arcRect, -135f, 90f, false, arcP)
        }
        dot.color = if (wifi >= 0) accent else dim
        c.drawCircle(wx, base - 0.5f * d, 1.8f * d, dot)

        // Baterai bersegmen
        val label = if (pct >= 0) "$pct%" else "--"
        val textW = text.measureText(label)
        val batW = 42f * d
        val batH = 20f * d
        val tipW = 3f * d
        val right = w - 2f * d - textW - 8f * d - tipW
        val left = right - batW
        val top = h / 2f - batH / 2f
        rect.set(left, top, right, top + batH)
        c.drawRoundRect(rect, 3f * d, 3f * d, body)
        rect.set(right + d, top + batH * 0.3f, right + d + tipW, top + batH * 0.7f)
        c.drawRoundRect(rect, d, d, tip)

        val segs = 8
        val padIn = 3.2f * d
        val gap = 1.4f * d
        val sw = (batW - 2f * padIn - (segs - 1) * gap) / segs
        val litSegs = if (pct < 0) 0 else ceil(pct / 12.5).toInt().coerceIn(0, segs)
        val color = when {
            pct in 0..19 -> Pal.RED
            pct in 20..49 -> Color.parseColor("#FFB020")
            else -> Color.parseColor("#35E08A")
        }
        for (i in 0 until segs) {
            val sl = left + padIn + i * (sw + gap)
            rect.set(sl, top + padIn, sl + sw, top + batH - padIn)
            seg.color = if (i < litSegs) color else dim
            if (charging && i == litSegs - 1 && tick % 2 == 0) seg.color = Color.WHITE
            c.drawRect(rect, seg)
        }
        c.drawText(label, right + tipW + 8f * d, h / 2f - (text.descent() + text.ascent()) / 2f, text)

        if (charging) {
            val bx = left - 14f * d
            val by = h / 2f - 8f * d
            boltPath.reset()
            boltPath.moveTo(bx + 5f * d, by)
            boltPath.lineTo(bx, by + 9f * d)
            boltPath.lineTo(bx + 4f * d, by + 9f * d)
            boltPath.lineTo(bx + 2.5f * d, by + 16f * d)
            boltPath.lineTo(bx + 9f * d, by + 6f * d)
            boltPath.lineTo(bx + 5f * d, by + 6f * d)
            boltPath.lineTo(bx + 7.5f * d, by)
            boltPath.close()
            bolt.color = accent
            c.drawPath(boltPath, bolt)
        }
    }
}
