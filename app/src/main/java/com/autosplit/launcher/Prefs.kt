package com.autosplit.launcher

import android.content.Context
import android.graphics.Color
import android.os.Build

object Pal {
    /** Warna aksen yang dipilih pengguna; diisi dari Prefs saat aplikasi dibuka. */
    var accent: Int = Color.parseColor("#00E5FF")
    var accent2: Int = Color.parseColor("#A855F7")
    /** 0 = MBUX Titanium, 1 = Lexus Sapphire, 2 = BMW Precision, 3 = Minimalis */
    var frameStyle: Int = 0
    /** True saat kecepatan melebihi batas atau suhu tablet >= 43°C. */
    var alertRed: Boolean = false

    val BG = Color.parseColor("#05070D")
    val CYAN: Int get() = accent
    val TEXT = Color.parseColor("#E8F1FA")
    val MUTED = Color.parseColor("#8A9BAE")
    val RED = Color.parseColor("#FF3B4A")
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("autosplit", Context.MODE_PRIVATE)

    var gaugeStyle: Int
        get() = sp.getInt("style", 4)
        set(v) = sp.edit().putInt("style", v).apply()

    var accent: Int
        get() = sp.getInt("accent", Color.parseColor("#00E5FF"))
        set(v) = sp.edit().putInt("accent", v).apply()

    var accent2: Int
        get() = sp.getInt("accent2", Color.parseColor("#A855F7"))
        set(v) = sp.edit().putInt("accent2", v).apply()

    var eco: Boolean
        get() = sp.getBoolean("eco", false)
        set(v) = sp.edit().putBoolean("eco", v).apply()

    /** 0 = mati, 1 = batang, 2 = gelombang. */
    var eqMode: Int
        get() = sp.getInt("eqmode", 0)
        set(v) = sp.edit().putInt("eqmode", v).apply()

    var frameRunning: Boolean
        get() = sp.getBoolean("framerun", false)
        set(v) = sp.edit().putBoolean("framerun", v).apply()

    /** Indeks kecepatan warna berjalan, lihat MainActivity.FRAME_SPEEDS. */
    var frameSpeed: Int
        get() = sp.getInt("framespeed", 1)
        set(v) = sp.edit().putInt("framespeed", v).apply()

    var ecoFrame: Boolean
        get() = sp.getBoolean("ecoframe", false)
        set(v) = sp.edit().putBoolean("ecoframe", v).apply()

    var ecoEq: Boolean
        get() = sp.getBoolean("ecoeq", false)
        set(v) = sp.edit().putBoolean("ecoeq", v).apply()

    var showTiles: Boolean
        get() = sp.getBoolean("tiles", false)
        set(v) = sp.edit().putBoolean("tiles", v).apply()

    var useMph: Boolean
        get() = sp.getBoolean("mph", false)
        set(v) = sp.edit().putBoolean("mph", v).apply()

    var panelA: String
        get() = sp.getString("panelA", DEFAULT_A) ?: DEFAULT_A
        set(v) = sp.edit().putString("panelA", v).apply()

    var panelB: String
        get() = sp.getString("panelB", DEFAULT_B) ?: DEFAULT_B
        set(v) = sp.edit().putString("panelB", v).apply()

    var leftPercent: Int
        get() = sp.getInt("left", 40)
        set(v) = sp.edit().putInt("left", v).apply()

    var panelCount: Int
        get() = sp.getInt("count", 3)
        set(v) = sp.edit().putInt("count", v).apply()

    var entertainmentLayout: Boolean
        get() = sp.getBoolean("hiburan", false)
        set(v) = sp.edit().putBoolean("hiburan", v).apply()

    var panelsActive: Boolean
        get() = sp.getBoolean("panelsactive", false)
        set(v) = sp.edit().putBoolean("panelsactive", v).apply()

    var autoDim: Boolean
        get() = sp.getBoolean("autodim", true)
        set(v) = sp.edit().putBoolean("autodim", v).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keepon", true)
        set(v) = sp.edit().putBoolean("keepon", v).apply()

    var forceFreeform: Boolean
        get() = sp.getBoolean("forcefree", Build.MANUFACTURER.equals("samsung", ignoreCase = true))
        set(v) = sp.edit().putBoolean("forcefree", v).apply()

    var autoOnCharge: Boolean
        get() = sp.getBoolean("autocharge", true)
        set(v) = sp.edit().putBoolean("autocharge", v).apply()

    var adminAsked: Boolean
        get() = sp.getBoolean("adminasked", false)
        set(v) = sp.edit().putBoolean("adminasked", v).apply()

    var askOnUnplug: Boolean
        get() = sp.getBoolean("askunplug", true)
        set(v) = sp.edit().putBoolean("askunplug", v).apply()

    var powerSaveOnLock: Boolean
        get() = sp.getBoolean("savelock", false)
        set(v) = sp.edit().putBoolean("savelock", v).apply()

    /** True bila Penghemat daya yang sedang menyala dinyalakan oleh AutoSplit. */
    var saverByUs: Boolean
        get() = sp.getBoolean("saverbyus", false)
        set(v) = sp.edit().putBoolean("saverbyus", v).apply()

    var btOnScreenOn: Boolean
        get() = sp.getBoolean("btscreen", true)
        set(v) = sp.edit().putBoolean("btscreen", v).apply()

    var wifiOnScreenOn: Boolean
        get() = sp.getBoolean("wifiscreen", true)
        set(v) = sp.edit().putBoolean("wifiscreen", v).apply()

    /** Layanan latar depan diperlukan untuk fitur charger, hemat daya saat terkunci, Bluetooth, dan Wi-Fi. */
    val needsChargeService: Boolean
        get() = autoOnCharge || askOnUnplug || powerSaveOnLock || btOnScreenOn || wifiOnScreenOn

    var autoStartPanels: Boolean
        get() = sp.getBoolean("autopanels", true)
        set(v) = sp.edit().putBoolean("autopanels", v).apply()

    /** 0 = MBUX Titanium, 1 = Lexus Sapphire, 2 = BMW Precision, 3 = Minimalis */
    var frameStyle: Int
        get() = sp.getInt("framestyle", 0)
        set(v) = sp.edit().putInt("framestyle", v).apply()

    /** 0 = Mati, 1 = Aktif, 2 = Otomatis (18:00-05:30) */
    var nightMode: Int
        get() = sp.getInt("nightmode", 2)
        set(v) = sp.edit().putInt("nightmode", v).apply()

    /** Batas kecepatan (km/j): 0 = Mati, 80, 100, 120 */
    var speedLimit: Int
        get() = sp.getInt("speedlimit", 100)
        set(v) = sp.edit().putInt("speedlimit", v).apply()

    var pitchOffset: Float
        get() = sp.getFloat("pitchoffset", 0f)
        set(v) = sp.edit().putFloat("pitchoffset", v).apply()

    var rollOffset: Float
        get() = sp.getFloat("rolloffset", 0f)
        set(v) = sp.edit().putFloat("rolloffset", v).apply()

    /** 0 = Lanskap, 1 = Potret, 2 = Otomatis */
    var orientation: Int
        get() = sp.getInt("orient", 0)
        set(v) = sp.edit().putInt("orient", v).apply()

    /** false = Kiri (Lanskap) / Atas (Potret); true = Kanan (Lanskap) / Bawah (Potret) */
    var speedoFlip: Boolean
        get() = sp.getBoolean("speedoflip", false)
        set(v) = sp.edit().putBoolean("speedoflip", v).apply()

    /** Proporsi ukuran Panel A dibanding Panel B (25..75%, default 60%) */
    var splitRatioA: Int
        get() = sp.getInt("splitratioa", 60)
        set(v) = sp.edit().putInt("splitratioa", v).apply()

    /** True = sembunyikan bingkai slot agar Maps & YouTube bebas diseret/diubah ukuran manual */
    var freeDragMode: Boolean
        get() = sp.getBoolean("freedrag", false)
        set(v) = sp.edit().putBoolean("freedrag", v).apply()

    /** Mode Potret: true = Atas 2 (Speedo + Panel A) & Bawah 1 penuh (Panel B); false = 3 susun vertikal */
    var portraitTop2: Boolean
        get() = sp.getBoolean("portraittop2", true)
        set(v) = sp.edit().putBoolean("portraittop2", v).apply()

    companion object {
        const val DEFAULT_A = "com.google.android.apps.maps"
        const val DEFAULT_B = "com.google.android.youtube"
    }
}
