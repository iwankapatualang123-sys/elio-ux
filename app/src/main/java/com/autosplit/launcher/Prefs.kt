package com.autosplit.launcher

import android.content.Context
import android.graphics.Color
import android.os.Build

object Pal {
    /** Warna aksen yang dipilih pengguna; diisi dari Prefs saat aplikasi dibuka. */
    var accent: Int = Color.parseColor("#00E5FF")
    var accent2: Int = Color.parseColor("#A855F7")

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

    companion object {
        const val DEFAULT_A = "com.google.android.apps.maps"
        const val DEFAULT_B = "com.google.android.youtube"
    }
}
