package com.autosplit.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Pop-up "Apakah unit dimatikan?" saat pengisi daya dicabut. Ya = kunci layar (device admin).
 * Tanpa jawaban, layar dikunci otomatis setelah hitung mundur. Dicolok lagi = pop-up ditutup.
 */
class ShutdownActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var remaining = COUNTDOWN_SEC
    private lateinit var countText: TextView
    private var receiver: BroadcastReceiver? = null

    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val admin by lazy { ComponentName(this, AdminReceiver::class.java) }

    private val countdown = object : Runnable {
        override fun run() {
            remaining--
            if (remaining <= 0) {
                confirm(false)
            } else {
                countText.text = remaining.toString()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Pal.accent = Prefs(this).accent
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())

        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_POWER_CONNECTED) finish()
            }
        }
        val f = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(r, f)
        receiver = r
        handler.postDelayed(countdown, 1000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(countdown)
        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        receiver = null
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun card(accent: Boolean): Drawable = LayerDrawable(
        arrayOf(
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(if (accent) 0 else 235, 8, 12, 18))
            },
            GlassDrawable(resources.displayMetrics.density, 14f, accent)
        )
    )

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.argb(120, 0, 0, 0)) }
        val real = android.graphics.Point()
        windowManager.defaultDisplay.getRealSize(real)
        val col = (real.x * Prefs(this).leftPercent / 100 - dp(28)).coerceAtLeast(dp(340))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = card(false)
            elevation = dp(10).toFloat()
            setPadding(dp(36), dp(28), dp(36), dp(24))
        }
        box.addView(TextView(this).apply {
            text = "PENGISI DAYA DICABUT"
            setTextColor(Pal.accent)
            textSize = 13f
            letterSpacing = 0.2f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        box.addView(TextView(this).apply {
            text = "Apakah unit dimatikan?"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })
        countText = TextView(this).apply {
            text = remaining.toString()
            setTextColor(Pal.accent)
            textSize = 54f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        }
        box.addView(countText)
        box.addView(TextView(this).apply {
            text = "Layar dikunci otomatis saat hitungan habis"
            setTextColor(Pal.MUTED)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(if (dpm.isAdminActive(admin)) 20 else 8))
        })
        if (!dpm.isAdminActive(admin)) {
            box.addView(TextView(this).apply {
                text = "Izin kunci layar belum aktif. Ketuk di sini untuk mengaktifkan."
                setTextColor(Pal.RED)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
                setOnClickListener {
                    handler.removeCallbacks(countdown)
                    requestAdmin(this@ShutdownActivity, REQ_ADMIN_ONLY)
                }
            })
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("TIDAK", false) { finish() }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        row.addView(button("YA, MATIKAN", true) { confirm(true) }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(6), 0, dp(6), 0) })
        box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(box, FrameLayout.LayoutParams(col, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(14)
            topMargin = dp(40)
        })
        return root
    }

    private fun button(label: String, accent: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(if (accent) Color.WHITE else Pal.TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = card(accent)
        setOnClickListener { onClick() }
    }

    private fun confirm(fromUser: Boolean) {
        handler.removeCallbacks(countdown)
        if (dpm.isAdminActive(admin)) {
            lockAndFinish()
        } else if (fromUser) {
            requestAdmin(this, REQ_ADMIN)
        } else {
            finish()
        }
    }

    private fun lockAndFinish() {
        try {
            dpm.lockNow()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengunci layar", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    @Deprecated("Dibutuhkan untuk Activity biasa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when {
            requestCode == REQ_ADMIN && dpm.isAdminActive(admin) -> lockAndFinish()
            requestCode == REQ_ADMIN_ONLY -> {
                setContentView(buildUi())
                remaining = COUNTDOWN_SEC
                countText.text = remaining.toString()
                handler.postDelayed(countdown, 1000L)
            }
            else -> finish()
        }
    }

    companion object {
        private const val COUNTDOWN_SEC = 20
        private const val REQ_ADMIN = 7
        private const val REQ_ADMIN_ONLY = 8

        fun show(ctx: Context) {
            val i = Intent(ctx, ShutdownActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
            } catch (e: Exception) {
            }
        }

        fun isAdminActive(ctx: Context): Boolean {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
        }

        @Suppress("DEPRECATION")
        fun requestAdmin(act: Activity, requestCode: Int) {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(act, AdminReceiver::class.java))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Dipakai hanya untuk mengunci layar saat unit dimatikan. AutoSplit tidak bisa menghapus data atau mengubah kata sandi."
                )
            act.startActivityForResult(i, requestCode)
        }
    }
}
