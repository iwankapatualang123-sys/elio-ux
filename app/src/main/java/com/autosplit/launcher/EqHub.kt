package com.autosplit.launcher

import android.media.audiofx.Visualizer
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Membaca spektrum suara yang sedang diputar tablet (sesi audio global 0) lewat Visualizer,
 * lalu meringkasnya menjadi 32 pita 0..1 berskala logaritmik. Saat hening, pengiriman berhenti
 * setelah beberapa frame agar tidak membebani CPU. Butuh izin RECORD_AUDIO.
 */
class EqHub(private val onBands: (FloatArray) -> Unit) {

    private var vis: Visualizer? = null
    private val bands = FloatArray(BANDS)
    private var silentFrames = 0

    val running: Boolean get() = vis != null

    private var liteMode = false

    /** [lite] = Hemat equalizer: data diambil 10 kali per detik, bukan 20. */
    fun start(lite: Boolean): Boolean {
        if (vis != null && lite == liteMode) return true
        stop()
        liteMode = lite
        var v: Visualizer? = null
        return try {
            v = Visualizer(0)
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = 512.coerceIn(range[0], range[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, waveform: ByteArray?, rate: Int) {}

                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft != null) process(fft)
                }
            }, minOf(Visualizer.getMaxCaptureRate(), if (lite) 10000 else 20000), false, true)
            v.enabled = true
            vis = v
            true
        } catch (e: Throwable) {
            try {
                v?.release()
            } catch (ignored: Throwable) {
            }
            vis = null
            false
        }
    }

    fun stop() {
        val v = vis ?: return
        vis = null
        try {
            v.enabled = false
            v.release()
        } catch (e: Throwable) {
        }
    }

    // Susunan FFT Visualizer: [0]=Re f0, [1]=Re f(n/2), lalu (Re fk, Im fk) untuk k = 1 .. n/2-1.
    private fun process(fft: ByteArray) {
        val bins = fft.size / 2
        var any = false
        for (b in 0 until BANDS) {
            val lo = binAt(b, bins)
            val hi = maxOf(lo + 1, binAt(b + 1, bins))
            var m = 0f
            for (k in lo until minOf(hi, bins)) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                m = maxOf(m, sqrt(re * re + im * im))
            }
            val tilt = 0.85f + 0.5f * b / BANDS
            val v = (ln(1f + m) / LOG_MAX * tilt).coerceIn(0f, 1f)
            bands[b] = v
            if (v > 0.04f) any = true
        }
        if (any) silentFrames = 0 else silentFrames++
        if (silentFrames < 15) onBands(bands)
    }

    private fun binAt(b: Int, bins: Int): Int {
        val f = b.toDouble() / BANDS
        return (1 + (bins - 2) * (2.0.pow(f * 10.0) - 1) / 1023.0).toInt().coerceIn(1, bins - 1)
    }

    companion object {
        const val BANDS = 32
        private val LOG_MAX = ln(1f + 181f)
    }
}
