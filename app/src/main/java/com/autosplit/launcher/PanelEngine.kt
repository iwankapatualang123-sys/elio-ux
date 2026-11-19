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
     * Susunan jendela di sisi kanan layar. Semua jendela punya tepi kiri, kanan, atas, dan bawah
     * yang sejajar dengan panel speedometer, dengan jarak tetap supaya bingkai neon (SlotOverlay)
     * pas mengelilinginya. Navigasi: A besar di atas, B kecil di bawah. Hiburan: sebaliknya.
     */
    private fun layout(): Pair<Rect, Rect> {
        val s = screenSize()
        val edge = px(14f)
        val strip = px(SlotOverlay.STRIP_DP)
        val pad = px(SlotOverlay.PAD_DP)
        val x0 = s.x * prefs.leftPercent / 100 + edge
        val x1 = s.x - edge
        val top = edge + strip
        val bottom = s.y - edge - pad

        if (prefs.panelCount < 3) {
            val r = Rect(x0, top, x1, bottom)
            return r to r
        }
        val gap = pad + strip + px(8f)
        val total = bottom - top
        val bigH = ((total - gap) * 0.6f).toInt()
        val upper = Rect(x0, top, x1, top + bigH)
        val lower = Rect(x0, top + bigH + gap, x1, bottom)
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
