package com.autosplit.launcher

import android.os.SystemClock

/** Jam bersama untuk animasi bingkai berjalan: sudut dihitung dari waktu, jadi gerakan tetap halus. */
object Ambient {
    @Volatile
    var running = false

    @Volatile
    var periodMs = 9000L

    /** Hemat bingkai: digambar lebih jarang dan tanpa pendar. */
    @Volatile
    var lite = false

    /** Lingkaran gradasi tanpa sambungan: warna 1 -> campuran -> warna 2 -> campuran -> warna 1. */
    fun loopColors(c1: Int, c2: Int): IntArray {
        val mid = android.graphics.Color.rgb(
            (android.graphics.Color.red(c1) + android.graphics.Color.red(c2)) / 2,
            (android.graphics.Color.green(c1) + android.graphics.Color.green(c2)) / 2,
            (android.graphics.Color.blue(c1) + android.graphics.Color.blue(c2)) / 2
        )
        return intArrayOf(c1, mid, c2, mid, c1)
    }

    fun angle(): Float = (SystemClock.uptimeMillis() % periodMs) * 360f / periodMs
}
