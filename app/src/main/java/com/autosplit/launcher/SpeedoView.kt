package com.autosplit.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Menampilkan template ELIO Speedo (assets/speedo) dan mengirim data sensor ke API
 * ElioSpeedo/ElioBridge. Panggilan JS hanya dikirim saat nilainya berubah.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class SpeedoView(ctx: Context) : WebView(ctx) {

    private var ready = false
    private var style = "elio"
    private var c1 = "#00E5FF"
    private var c2 = "#A855F7"
    private var eco = false
    private var eq = "off"
    private var frameRun = false
    private var frameSec = 9
    private var frameLite = false
    private var eqLite = false
    private var unit = "km/h"
    private val sb = StringBuilder(256)

    private var lastSpeed = -1
    private var lastG = Int.MIN_VALUE
    private var lastLive = ""

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                applyAll()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        loadUrl("file:///android_asset/speedo/index.html")
    }

    fun configure(
        styleIndex: Int, color1: Int, color2: Int, ecoMode: Boolean, eqMode: Int,
        frameRunning: Boolean, frameSeconds: Int, frameLiteMode: Boolean, eqLiteMode: Boolean,
        useMph: Boolean = false
    ) {
        frameSec = frameSeconds
        frameLite = frameLiteMode
        eqLite = eqLiteMode
        unit = if (useMph) "mph" else "km/h"
        style = STYLES[styleIndex.coerceIn(0, STYLES.size - 1)]
        c1 = hex(color1)
        c2 = hex(color2)
        eco = ecoMode
        eq = EQ_MODES[eqMode.coerceIn(0, EQ_MODES.size - 1)]
        frameRun = frameRunning
        applyAll()
    }

    /** Pita equalizer 0..1 dari EqHub (dipanggil di thread utama). */
    fun setBands(bands: FloatArray) {
        if (!ready || eq == "off") return
        sb.setLength(0)
        sb.append("ElioEq.setBands([")
        for (i in bands.indices) {
            if (i > 0) sb.append(',')
            sb.append((bands[i] * 100f).roundToInt() / 100f)
        }
        sb.append("])")
        evaluateJavascript(sb.toString(), null)
    }

    fun setSpeed(kmh: Float) {
        val v = kmh.roundToInt().coerceIn(0, 260)
        if (v == lastSpeed) return
        lastSpeed = v
        js("ElioSpeedo.setSpeed($v)")
    }

    fun setG(g: Float) {
        val q = (g * 100f).roundToInt()
        if (q == lastG) return
        lastG = q
        js("ElioSpeedo.setGForce(${String.format(Locale.US, "%.2f", q / 100f)})")
    }

    /** [json] dibuat dengan org.json sehingga sudah ter-escape. */
    fun live(json: String) {
        if (json == lastLive) return
        lastLive = json
        js("ElioBridge.live($json)")
    }

    private fun applyAll() {
        if (!ready) return
        js(
            "ElioSpeedo.setStyle('$style');ElioSpeedo.setGradient('$c1','$c2');ElioSpeedo.setEco($eco);" +
                "if(ElioSpeedo.setUnit)ElioSpeedo.setUnit('$unit');" +
                "ElioEq.setMode('$eq');document.querySelector('.elio-speedo').classList.toggle('as-frame-run',$frameRun);" +
                "AutoSplitFx.setFrameLite($frameLite);AutoSplitFx.setFrameSpeed($frameSec);ElioEq.setLite($eqLite);"
        )
        lastSpeed = -1
        lastG = Int.MIN_VALUE
        lastLive = ""
    }

    private fun js(code: String) {
        if (ready) evaluateJavascript(code, null)
    }

    private fun hex(c: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and c)

    companion object {
        val STYLES = arrayOf("lfa", "m3", "mazda", "honda", "elio", "bmw_m")
        val EQ_MODES = arrayOf("off", "bars", "wave")
    }
}
