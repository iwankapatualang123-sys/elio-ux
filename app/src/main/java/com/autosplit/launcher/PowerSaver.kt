package com.autosplit.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Menyalakan Penghemat daya sistem saat layar terkunci dan mematikannya lagi saat dibuka.
 * Butuh izin WRITE_SECURE_SETTINGS yang diberikan sekali lewat ADB:
 *   adb shell pm grant com.autosplit.launcher android.permission.WRITE_SECURE_SETTINGS
 * Hanya mematikan penghemat daya yang dinyalakan oleh AutoSplit sendiri.
 */
object PowerSaver {
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val LOW_POWER = "low_power"
    const val ADB_COMMAND = "adb shell pm grant com.elioux.launcher android.permission.WRITE_SECURE_SETTINGS"

    fun canControl(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun isOn(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, LOW_POWER, 0) == 1

    private fun set(ctx: Context, on: Boolean): Boolean = try {
        Settings.Global.putInt(ctx.contentResolver, LOW_POWER, if (on) 1 else 0)
    } catch (e: Exception) {
        false
    }

    fun onLock(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.powerSaveOnLock || !canControl(ctx) || isOn(ctx)) return
        if (set(ctx, true)) p.saverByUs = true
    }

    /** Bluetooth dinyalakan saat layar menyala (Android 12 ke bawah; dari Android 13 dilarang sistem). */
    @Suppress("DEPRECATION", "MissingPermission")
    fun bluetoothOn(ctx: Context) {
        if (!Prefs(ctx).btOnScreenOn || android.os.Build.VERSION.SDK_INT >= 33) return
        try {
            val bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return
            if (!bt.isEnabled) bt.enable()
        } catch (e: Exception) {
        }
    }

    /** Wi-Fi dinyalakan saat layar menyala (Android 9 ke bawah; dari Android 10 dilarang sistem). */
    @Suppress("DEPRECATION")
    fun wifiOn(ctx: Context) {
        if (!Prefs(ctx).wifiOnScreenOn || android.os.Build.VERSION.SDK_INT >= 29) return
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
            if (!wm.isWifiEnabled) wm.isWifiEnabled = true
        } catch (e: Exception) {
        }
    }

    fun onUnlock(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.saverByUs) return
        set(ctx, false)
        p.saverByUs = false
    }
}
