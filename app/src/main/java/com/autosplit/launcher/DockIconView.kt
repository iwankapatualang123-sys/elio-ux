package com.autosplit.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ikon vektor modern ringan untuk 5 tombol dock bawah Elio UX (Aplikasi, Maps, YouTube, Mulai/Tema, Atur).
 * Digambar murni dengan Canvas Android tanpa library luar sehingga sangat ringan di tablet lama.
 */
class DockIconView(ctx: Context, private var kind: Int) : View(ctx) {

    fun setKind(newKind: Int) {
        if (kind == newKind) return
        kind = newKind
        invalidate()
    }

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val sz = (26f * d).toInt()
        setMeasuredDimension(sz, sz)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        when (kind) {
            APPS -> drawApps(c, w, h)
            MAPS -> drawMaps(c, cx, cy, w, h)
            YOUTUBE -> drawYouTube(c, cx, cy, w, h)
            START -> drawStart(c, cx, cy, w, h)
            SETTINGS -> drawSettings(c, cx, cy, w * 0.42f)
        }
    }

    private fun drawApps(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        val pad = w * 0.14f
        val gap = w * 0.10f
        val cell = (w - pad * 2f - gap) / 2f
        val rad = cell * 0.28f
        val cols = intArrayOf(
            Color.parseColor("#E2E8F0"),
            Color.parseColor("#C4B5FD"),
            Color.parseColor("#93C5FD"),
            Color.parseColor("#E2E8F0")
        )
        for (i in 0..3) {
            p.color = cols[i]
            val x = pad + (i % 2) * (cell + gap)
            val y = pad + (i / 2) * (cell + gap)
            r.set(x, y, x + cell, y + cell)
            c.drawRoundRect(r, rad, rad, p)
        }
    }

    private fun drawMaps(c: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        val rad = w * 0.30f
        val py = h * 0.40f
        p.style = Paint.Style.FILL
        // Pin body (green bottom + blue right + red top + yellow left)
        path.reset()
        path.moveTo(cx, h * 0.90f)
        path.cubicTo(cx - rad * 1.15f, py + rad * 0.85f, cx - rad, py, cx - rad, py)
        path.arcTo(RectF(cx - rad, py - rad, cx + rad, py + rad), 180f, 180f, false)
        path.cubicTo(cx + rad, py, cx + rad * 1.15f, py + rad * 0.85f, cx, h * 0.90f)
        path.close()
        p.color = Color.parseColor("#34A853")
        c.drawPath(path, p)

        // Red top-left arc
        r.set(cx - rad, py - rad, cx + rad, py + rad)
        p.color = Color.parseColor("#EA4335")
        c.drawArc(r, 200f, 110f, true, p)
        // Blue top-right arc
        p.color = Color.parseColor("#4285F4")
        c.drawArc(r, 310f, 80f, true, p)
        // Yellow left arc
        p.color = Color.parseColor("#FBBC04")
        c.drawArc(r, 120f, 80f, true, p)
        // White center dot
        p.color = Color.WHITE
        c.drawCircle(cx, py, rad * 0.44f, p)
    }

    private fun drawYouTube(c: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#EF0023")
        r.set(w * 0.06f, h * 0.18f, w * 0.94f, h * 0.82f)
        c.drawRoundRect(r, w * 0.18f, w * 0.18f, p)
        p.color = Color.WHITE
        path.reset()
        path.moveTo(cx - w * 0.10f, cy - h * 0.16f)
        path.lineTo(cx + w * 0.18f, cy)
        path.lineTo(cx - w * 0.10f, cy + h * 0.16f)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawStart(c: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        // Split-screen launcher icon in current accent color
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.0f * d
        p.color = Pal.accent
        r.set(w * 0.10f, h * 0.16f, w * 0.90f, h * 0.84f)
        c.drawRoundRect(r, 4f * d, 4f * d, p)
        // Vertical split divider + right horizontal split
        c.drawLine(cx - w * 0.04f, h * 0.16f, cx - w * 0.04f, h * 0.84f, p)
        c.drawLine(cx - w * 0.04f, cy, w * 0.90f, cy, p)
        // Small play triangle on left pane
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        path.reset()
        path.moveTo(w * 0.22f, cy - h * 0.12f)
        path.lineTo(w * 0.36f, cy)
        path.lineTo(w * 0.22f, cy + h * 0.12f)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawSettings(c: Canvas, cx: Float, cy: Float, rad: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#CBD5E1")
        val teeth = 8
        path.reset()
        for (i in 0 until teeth * 2) {
            val a = i * Math.PI / teeth
            val rr = if (i % 2 == 0) rad else rad * 0.72f
            val x = cx + (cos(a) * rr).toFloat()
            val y = cy + (sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
        p.color = Color.parseColor("#0B1020")
        c.drawCircle(cx, cy, rad * 0.42f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * d
        p.color = Pal.accent
        c.drawCircle(cx, cy, rad * 0.42f, p)
    }

    companion object {
        const val APPS = 0
        const val MAPS = 1
        const val YOUTUBE = 2
        const val START = 3
        const val SETTINGS = 4
    }
}

