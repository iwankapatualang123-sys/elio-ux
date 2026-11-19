package com.autosplit.launcher

import android.content.Context
import android.net.wifi.WifiManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager

/** Kekuatan sinyal seluler (0 sampai 4) dan Wi-Fi (0 sampai 4); -1 bila tidak tersedia. */
class LinkHub(ctx: Context) {

    private val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    var cellLevel = -1
        private set
    var wifiLevel = -1
        private set

    private val listener = object : PhoneStateListener() {
        @Deprecated("Dibutuhkan oleh Android lama")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            cellLevel = signalStrength?.level ?: -1
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        try {
            tm?.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        try {
            tm?.listen(listener, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    fun poll() {
        wifiLevel = try {
            val info = wifi?.connectionInfo
            if (info == null || info.networkId == -1 || info.rssi <= -126) -1
            else WifiManager.calculateSignalLevel(info.rssi, 5)
        } catch (e: Exception) {
            -1
        }
    }
}
