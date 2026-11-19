package com.autosplit.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder

/**
 * Menunggu pengisi daya dicolok lalu membuka launcher otomatis. ACTION_POWER_CONNECTED tidak
 * bisa didaftarkan di manifest sejak Android 8, jadi receiver didaftarkan saat layanan latar depan
 * ini berjalan (dengan notifikasi kecil yang tidak berbunyi).
 */
class ChargeService : Service() {

    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_POWER_CONNECTED -> openLauncher(c)
                    Intent.ACTION_POWER_DISCONNECTED -> if (Prefs(c).askOnUnplug) ShutdownActivity.show(c)
                    Intent.ACTION_SCREEN_OFF -> PowerSaver.onLock(c)
                    Intent.ACTION_SCREEN_ON -> {
                        PowerSaver.onUnlock(c)
                        PowerSaver.bluetoothOn(c)
                        PowerSaver.wifiOn(c)
                    }
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(r, f)
        }
        receiver = r
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent?.getBooleanExtra(EXTRA_BOOT, false) == true && isPlugged()) openLauncher(this)
        return START_STICKY
    }

    override fun onDestroy() {
        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        receiver = null
        super.onDestroy()
    }

    private fun isPlugged(): Boolean {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun openLauncher(c: Context) {
        if (!Prefs(c).autoOnCharge) return
        val i = Intent(c, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(MainActivity.EXTRA_CHARGE, true)
        try {
            c.startActivity(i)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Elio UX", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, CHANNEL)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Elio UX aktif")
            .setContentText("Memantau pengisi daya, layar, Bluetooth, dan Wi-Fi")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "autosplit"
        private const val NOTIF_ID = 1
        private const val EXTRA_BOOT = "boot"

        fun start(ctx: Context, fromBoot: Boolean = false) {
            val i = Intent(ctx, ChargeService::class.java).putExtra(EXTRA_BOOT, fromBoot)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ChargeService::class.java))
        }
    }
}
