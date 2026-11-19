package com.autosplit.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.view.WindowManager

/**
 * Meluncurkan aplikasi ke panel. Dua jalur:
 * - freeform / pop-up: tiap aplikasi diluncurkan dengan batas ukuran (launch bounds).
 * - split: aplikasi diluncurkan ke sisi seberang launcher (FLAG_ACTIVITY_LAUNCH_ADJACENT).
 */
class PanelEngine(private val ctx: Context, private val prefs: Prefs) {

    fun systemHasFreeform(): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    fun freeformUsable(): Boolean = prefs.forceFreeform || systemHasFreeform()

    fun isInstalled(pkg: String): Boolean = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun labelOf(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "Pilih app"
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val p = Point()
        wm.defaultDisplay.getRealSize(p)
        return p
    }

    private fun px(dp: Float): Int = (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /**
     * Susunan jendela adaptif:
     * - Lanskap: Speedometer Kiri/Kanan (leftPercent%), panel A & B di sisi satunya.
     * - Potret: Speedometer Atas/Bawah (leftPercent%), panel A & B di ruang tersisa.
     * - Proporsi Panel A vs B mengikuti prefs.splitRatioA (25%..75%).
     */
    private fun layout(): Pair<Rect, Rect> {
        val s = screenSize()
        val isPortrait = s.y > s.x
        val edge = px(12f)
        val strip = px(SlotOverlay.STRIP_DP)
        val pad = px(SlotOverlay.PAD_DP)
        val pct = prefs.leftPercent.coerceIn(25, 65)
        val ratioA = prefs.splitRatioA.coerceIn(25, 75) / 100f

        val x0: Int
        val x1: Int
        val top: Int
        val bottom: Int

        if (isPortrait && prefs.portraitTop2 && prefs.panelCount >= 3) {
            // Mode Potret: Atas 2 (Speedometer + Panel A) & Bawah 1 lebar penuh (Panel B, cocok untuk YouTube!)
            val speedoW = s.x * pct / 100
            val topH = (s.y * ratioA).toInt()
            val gapY = pad + strip + px(6f)
            val panelA = if (prefs.speedoFlip) {
                Rect(edge, edge + strip, s.x - speedoW - edge, topH - pad)
            } else {
                Rect(speedoW + edge, edge + strip, s.x - edge, topH - pad)
            }
            val panelB = Rect(edge, topH + gapY - pad, s.x - edge, s.y - edge - pad)
            return panelA to panelB
        }

        if (isPortrait) {
            x0 = edge
            x1 = s.x - edge
            val speedoH = s.y * pct / 100
            if (prefs.speedoFlip) {
                // Speedometer di Bawah, panel A & B di Atas
                top = edge + strip
                bottom = s.y - speedoH - pad
            } else {
                // Speedometer di Atas, panel A & B di Bawah
                top = speedoH + strip
                bottom = s.y - edge - pad
            }
        } else {
            top = edge + strip
            bottom = s.y - edge - pad
            val speedoW = s.x * pct / 100
            if (prefs.speedoFlip) {
                // Speedometer di Kanan, panel A & B di Kiri
                x0 = edge
                x1 = s.x - speedoW - edge
            } else {
                // Speedometer di Kiri, panel A & B di Kanan
                x0 = speedoW + edge
                x1 = s.x - edge
            }
        }

        if (prefs.panelCount < 3) {
            val r = Rect(x0, top, x1, bottom)
            return r to r
        }
        val gap = pad + strip + px(6f)
        val total = (bottom - top).coerceAtLeast(px(120f))
        val firstRatio = if (prefs.entertainmentLayout) (1f - ratioA) else ratioA
        val upperH = ((total - gap) * firstRatio).toInt()
        val upper = Rect(x0, top, x1, top + upperH)
        val lower = Rect(x0, top + upperH + gap, x1, bottom)
        return if (prefs.entertainmentLayout) lower to upper else upper to lower
    }

    fun boundsA(): Rect = layout().first

    fun boundsB(): Rect = layout().second

    fun launch(pkg: String, bounds: Rect?, adjacent: Boolean): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (adjacent) flags = flags or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        intent.addFlags(flags)
        return try {
            val options = ActivityOptions.makeBasic()
            if (bounds != null) options.setLaunchBounds(bounds)
            ctx.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            false
        }
    }
}
