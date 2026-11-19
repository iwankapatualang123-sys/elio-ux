package com.autosplit.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var hub: SensorHub
    private lateinit var link: LinkHub
    private lateinit var engine: PanelEngine

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var holder: FrameLayout
    private lateinit var gpsText: TextView
    private lateinit var nightBtn: TextView
    private lateinit var nightVeil: View
    private lateinit var indicator: StatusIndicator
    private lateinit var labelA: TextView
    private lateinit var labelB: TextView
    private lateinit var labelStart: TextView
    private var iconA: DockIconView? = null
    private var iconB: DockIconView? = null
    private lateinit var overlay: SlotOverlay

    private val weather = WeatherHub()
    private var gauge: SpeedoView? = null
    private val eq = EqHub { gauge?.setBands(it) }
    private var eqWarned = false
    private var lowBatWarned = 100
    private var lowBatDialog: AlertDialog? = null
    private var lastAlertRed = false
    private var lastBeepMs = 0L
    private val animViews = ArrayList<View>()
    private var lastFrameNs = 0L

    /** Menggambar ulang semua bingkai ~25 kali per detik selama mode Berjalan aktif. */
    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!Ambient.running) return
            if (frameTimeNanos - lastFrameNs >= if (Ambient.lite) 80_000_000L else 40_000_000L) {
                lastFrameNs = frameTimeNanos
                overlay.invalidate()
                for (v in animViews) v.invalidate()
            }
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private val tileValues = HashMap<String, TextView>()
    private val tileBoxes = HashMap<String, View>()

    private var panelsActive: Boolean
        get() = prefs.panelsActive
        set(v) {
            prefs.panelsActive = v
        }
    private var tick = 0
    private var lastBrightness = -2f

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Pal.accent = prefs.accent
        Pal.accent2 = prefs.accent2
        Pal.frameStyle = prefs.frameStyle
        Pal.alertRed = false
        applyOrientation()
        hub = SensorHub(this)
        link = LinkHub(this)
        engine = PanelEngine(this, prefs)
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
        }
        handleIntent(intent)
        handler.postDelayed({ askAdminOnce() }, 2500L)
    }

    /** Minta izin kunci layar sekali, supaya pop-up bisa mengunci otomatis saat timer habis. */
    private fun askAdminOnce() {
        if (!prefs.askOnUnplug || prefs.adminAsked || ShutdownActivity.isAdminActive(this) || isFinishing) return
        prefs.adminAsked = true
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Izinkan kunci layar")
            .setMessage("Agar tablet terkunci otomatis saat charger dicabut dan timer habis, AutoSplit butuh izin kunci layar. Izin ini hanya untuk mengunci layar.")
            .setPositiveButton("Izinkan") { _, _ -> ShutdownActivity.requestAdmin(this, REQ_ADMIN) }
            .setNegativeButton("Nanti", null)
            .showLeft()
    }

    private fun AlertDialog.Builder.showLeft(): AlertDialog {
        val d = show()
        place(d)
        return d
    }

    private fun applyOrientation() {
        requestedOrientation = when (prefs.orientation) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /** Dialog ditaruh di area speedometer agar tidak tertutup jendela Maps/YouTube (mendukung Lanskap & Potret). */
    @Suppress("DEPRECATION")
    private fun place(d: AlertDialog) {
        val w = d.window ?: return
        val real = Point()
        windowManager.defaultDisplay.getRealSize(real)
        val isPortrait = real.y > real.x
        val pct = prefs.leftPercent.coerceIn(25, 65)
        w.setGravity(Gravity.TOP or Gravity.START)
        val lp = w.attributes
        if (isPortrait) {
            val col = if (panelsActive && prefs.portraitTop2 && prefs.panelCount >= 3) {
                (real.x * pct / 100).coerceAtLeast(dp(300))
            } else {
                real.x
            }
            lp.width = (col - dp(24)).coerceAtLeast(dp(290)).coerceAtMost(real.x - dp(20))
            lp.x = if (prefs.speedoFlip && panelsActive && prefs.portraitTop2) (real.x - lp.width - dp(12)).coerceAtLeast(dp(10)) else dp(12)
            lp.y = dp(32)
        } else {
            val col = if (panelsActive && !isInMultiWindowMode) real.x * pct / 100 else real.x * 45 / 100
            lp.width = (col - dp(24)).coerceAtLeast(dp(300))
            lp.x = if (prefs.speedoFlip && panelsActive && !isInMultiWindowMode) (real.x - lp.width - dp(14)).coerceAtLeast(dp(14)) else dp(14)
            lp.y = dp(36)
        }
        w.attributes = lp
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_CHARGE, false) == true && prefs.autoStartPanels) {
            handler.postDelayed({ startPanels() }, 1500L)
        }
    }

    override fun onStart() {
        super.onStart()
        hub.start()
        link.start()
        gauge?.onResume()
        applyEffects()
        syncChargeService()
        updateSlotButtons()
        applyInsets()
        handler.post(poll)
    }

    override fun onStop() {
        eq.stop()
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        gauge?.onPause()
        handler.removeCallbacks(poll)
        hub.stop()
        link.stop()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        gauge?.destroy()
        gauge = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            hub.stop()
            hub.start()
        }
        if (requestCode == REQ_AUDIO) {
            if (hasAudioPermission()) {
                applyEffects()
            } else {
                prefs.eqMode = 0
                setGauge()
                toast("Equalizer butuh izin audio untuk membaca musik yang diputar")
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Launcher: tombol kembali tidak menutup layar utama.
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    // ---------- UI ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun glass(accent: Boolean = false) = GlassDrawable(resources.displayMetrics.density, 14f, accent)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun f(fmt: String, vararg a: Any): String = String.format(Locale.US, fmt, *a)

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = HudBackground(resources.displayMetrics.density)
        }

        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass()
            elevation = dp(6).toFloat()
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        animViews.add(status)
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 19f
            setTextColor(Pal.accent)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        gpsText = statusText(Gravity.CENTER).apply {
            textSize = 11.5f
        }
        nightBtn = TextView(this).apply {
            text = "🌙"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(3), dp(7), dp(3))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                prefs.nightMode = if (isNightActive()) 0 else 1
                applyNightVisuals()
                toast(if (isNightActive()) "Mode Malam Anti-Silau: AKTIF" else "Mode Malam: MATI")
            }
        }
        indicator = StatusIndicator(this)
        status.addView(clock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
        status.addView(gpsText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        status.addView(nightBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
        status.addView(indicator, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        holder = FrameLayout(this)

        val grid = GridLayout(this).apply { columnCount = 4 }
        addTile(grid, T_HEAD, "ARAH")
        addTile(grid, T_ALT, "KETINGGIAN")
        addTile(grid, T_G, "GAYA G")
        addTile(grid, T_BAT, "SUHU BATERAI")
        addTile(grid, T_TRIP, "JARAK TRIP")
        addTile(grid, T_MAX, "KEC. MAKS")
        addTile(grid, T_AVG, "RATA-RATA")
        addTile(grid, T_TIME, "WAKTU JALAN")
        addTile(grid, T_PRESS, "TEKANAN")
        addTile(grid, T_LUX, "CAHAYA")
        tileBoxes[T_TRIP]?.setOnLongClickListener {
            hub.resetTrip()
            toast("Trip direset")
            true
        }

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(6))
        }
        val (tileApps, _) = dockButton(DockIconView.APPS, "Aplikasi", false, { drawer() })
        val (tileA, lblA) = dockButton(DockIconView.MAPS, "Maps", false, { launchSlot(true) }, { pickApp(true); true })
        val (tileB, lblB) = dockButton(DockIconView.YOUTUBE, "YouTube", false, { launchSlot(false) }, { pickApp(false); true })
        val (tileStart, lblStart) = dockButton(DockIconView.START, "Mulai", true, { startPanels() }, { askStyle(); true })
        val (tileAtur, _) = dockButton(DockIconView.SETTINGS, "Atur", false, { showSettings() })
        labelA = lblA
        labelB = lblB
        labelStart = lblStart
        iconA = tileA.getChildAt(0) as? DockIconView
        iconB = tileB.getChildAt(0) as? DockIconView
        addDockTile(dock, tileApps)
        addDockTile(dock, tileA)
        addDockTile(dock, tileB)
        addDockTile(dock, tileStart)
        addDockTile(dock, tileAtur)

        val full = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        root.addView(status, LinearLayout.LayoutParams(full, wrap).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        root.addView(holder, LinearLayout.LayoutParams(full, 0, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        if (prefs.showTiles) root.addView(grid, LinearLayout.LayoutParams(full, wrap))
        root.addView(dock, LinearLayout.LayoutParams(full, wrap))
        overlay = SlotOverlay(this)
        nightVeil = View(this).apply {
            setBackgroundColor(Color.argb(54, 10, 6, 0))
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(full, full))
        frame.addView(overlay, FrameLayout.LayoutParams(full, full))
        frame.addView(nightVeil, FrameLayout.LayoutParams(full, full))
        setContentView(frame)

        applyNightVisuals()
        setGauge()
    }

    private fun statusText(g: Int) = TextView(this).apply {
        textSize = 13f
        setTextColor(Pal.MUTED)
        gravity = g
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun addTile(grid: GridLayout, key: String, label: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glass()
            elevation = dp(6).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        animViews.add(box)
        val l = TextView(this).apply {
            text = label
            setTextColor(Pal.MUTED)
            textSize = 10.5f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val v = TextView(this).apply {
            text = "—"
            setTextColor(Pal.TEXT)
            textSize = 17f
            gravity = Gravity.CENTER
            maxLines = 1
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        box.addView(l)
        box.addView(v)
        val lp = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        grid.addView(box, lp)
        tileValues[key] = v
        tileBoxes[key] = box
    }

    private fun dockButton(
        iconKind: Int,
        label: String,
        accent: Boolean = false,
        onClick: () -> Unit,
        onLongClick: (() -> Boolean)? = null
    ): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glass(accent)
            elevation = dp(6).toFloat()
            setPadding(dp(4), dp(7), dp(4), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick() }
            animViews.add(this)
        }
        val ico = DockIconView(this, iconKind)
        val tv = TextView(this).apply {
            text = label
            setTextColor(if (accent) Pal.accent else Pal.TEXT)
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(3), 0, 0)
        }
        box.addView(ico)
        box.addView(tv)
        return box to tv
    }

    private fun addDockTile(row: LinearLayout, v: View) {
        row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(2), dp(3), dp(2))
        })
    }

    private fun setGauge() {
        val g = gauge ?: SpeedoView(this)
        g.configure(
            prefs.gaugeStyle, prefs.accent, prefs.accent2, prefs.eco, prefs.eqMode,
            prefs.frameRunning, FRAME_SPEEDS[prefs.frameSpeed.coerceIn(0, FRAME_SPEEDS.size - 1)],
            prefs.ecoFrame, prefs.ecoEq, prefs.useMph
        )
        if (g.parent == null) holder.addView(g, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        gauge = g
    }

    private fun askStyle() {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        val listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        var dlgRef: AlertDialog? = null
        val activeIdx = prefs.gaugeStyle.coerceIn(0, STYLE_NAMES.size - 1)
        STYLE_NAMES.forEachIndexed { idx, name ->
            val isActive = idx == activeIdx
            val row = modernSettingRow(
                title = name,
                badgeText = if (isActive) "AKTIF" else "PILIH",
                badgeKind = if (isActive) 1 else 2
            ) {
                dlgRef?.dismiss()
                prefs.gaugeStyle = idx
                setGauge()
                toast("Tema: $name")
            }
            listBox.addView(row)
        }
        dlgRef = showModernCard("❖ PILIH TEMA SPEEDOMETER", "6 Tema HD Full-Press", listBox)
    }

    private fun iconKindFor(pkg: String, defaultKind: Int): Int {
        val p = pkg.lowercase(Locale.US)
        return when {
            p.contains("youtube") -> DockIconView.YOUTUBE
            p.contains("maps") || p.contains("waze") -> DockIconView.MAPS
            else -> defaultKind
        }
    }

    private fun updateSlotButtons() {
        labelStart.text = "Mulai ${prefs.panelCount}x"
        labelA.text = engine.labelOf(prefs.panelA)
        labelB.text = engine.labelOf(prefs.panelB)
        iconA?.setKind(iconKindFor(prefs.panelA, DockIconView.MAPS))
        iconB?.setKind(iconKindFor(prefs.panelB, DockIconView.YOUTUBE))
    }

    /**
     * Menyesuaikan posisi & ukuran area speedometer terhadap jendela split:
     * - Lanskap: Kiri atau Kanan (lebar leftPercent%)
     * - Potret Atas 2 + Bawah 1: Kiri-Atas atau Kanan-Atas (lebar leftPercent%, tinggi splitRatioA%)
     * - Potret 3 Susun: Atas atau Bawah penuh (tinggi leftPercent%)
     */
    @Suppress("DEPRECATION")
    private fun applyInsets() {
        val real = Point()
        windowManager.defaultDisplay.getRealSize(real)
        val reserve = panelsActive && !isInMultiWindowMode
        if (reserve) {
            val isPortrait = real.y > real.x
            val pct = prefs.leftPercent.coerceIn(25, 65)
            val ratioA = prefs.splitRatioA.coerceIn(25, 75)
            val topPad = dp(12) + dp(SlotOverlay.STRIP_DP.toInt()) - dp(3)
            val botPad = dp(12) + dp(SlotOverlay.PAD_DP.toInt()) - dp(3)
            when {
                isPortrait && prefs.portraitTop2 && prefs.panelCount >= 3 -> {
                    val sideOther = real.x * (100 - pct) / 100
                    val bottomOther = real.y - (real.y * ratioA / 100) + dp(4)
                    if (prefs.speedoFlip) {
                        root.setPadding(sideOther, topPad, dp(10), bottomOther)
                    } else {
                        root.setPadding(dp(10), topPad, sideOther, bottomOther)
                    }
                }
                isPortrait -> {
                    val otherH = real.y * (100 - pct) / 100
                    if (prefs.speedoFlip) {
                        root.setPadding(dp(10), otherH, dp(10), botPad)
                    } else {
                        root.setPadding(dp(10), topPad, dp(10), otherH)
                    }
                }
                else -> {
                    val otherW = real.x * (100 - pct) / 100
                    if (prefs.speedoFlip) {
                        root.setPadding(otherW, topPad, dp(11), botPad)
                    } else {
                        root.setPadding(dp(11), topPad, otherW, botPad)
                    }
                }
            }
        } else {
            root.setPadding(dp(12), dp(12), dp(12), dp(10))
        }
        updateOverlay(reserve)
    }

    private fun updateOverlay(show: Boolean) {
        if (!show || prefs.freeDragMode) {
            overlay.setSlots(emptyList())
            return
        }
        val list = ArrayList<SlotOverlay.Slot>()
        list.add(SlotOverlay.Slot(engine.boundsA(), "A  " + engine.labelOf(prefs.panelA).uppercase(Locale.US)))
        if (prefs.panelCount >= 3) {
            list.add(SlotOverlay.Slot(engine.boundsB(), "B  " + engine.labelOf(prefs.panelB).uppercase(Locale.US)))
        }
        overlay.setSlots(list)
    }

    // ---------- Pembaruan data ----------

    private fun moving(): Boolean = hub.currentSpeedMps() > 1.4f

    private fun cardinal(d: Float): String {
        val n = arrayOf("U", "TL", "T", "TG", "S", "BD", "B", "BL")
        return n[((d + 22.5f) / 45f).toInt() % 8]
    }

    private fun refresh() {
        val mph = prefs.useMph
        val k = if (mph) 2.23694f else 3.6f
        val unitName = if (mph) "mph" else "km/j"
        val spdKmh = hub.currentSpeedMps() * 3.6f
        gauge?.setSpeed(hub.currentSpeedMps() * k)
        gauge?.setG(hub.latG)

        val overSpeed = prefs.speedLimit > 0 && spdKmh > prefs.speedLimit
        val overheat = !hub.batteryTempC.isNaN() && hub.batteryTempC >= 43f
        val alertNow = overSpeed || overheat
        if (alertNow != lastAlertRed) {
            lastAlertRed = alertNow
            Pal.alertRed = alertNow
            overlay.invalidate()
            for (v in animViews) v.invalidate()
        }
        if (overSpeed) beepOverSpeed()

        tick++
        if (tick % 8 == 1) {
            hub.readBattery()
            checkBattery()
            link.poll()
            weather.updateIfNeeded(hub.lastLat, hub.lastLon)
            applyInsets()
            applyNightVisuals()
        }
        indicator.set(link.cellLevel, link.wifiLevel, hub.batteryPct, hub.charging, tick)
        updateGpsWarning(hub.gpsStatus(), overheat)

        val h = if (!hub.headingDeg.isNaN()) hub.headingDeg else hub.gpsBearing
        tileValues[T_HEAD]?.text = if (h.isNaN()) "—" else "${h.roundToInt() % 360}° ${cardinal(h)}"

        val alt = when {
            !hub.mslAltitude().isNaN() -> hub.mslAltitude()
            !hub.pressureHpa.isNaN() ->
                SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hub.pressureHpa).toDouble()
            !hub.altitudeM.isNaN() -> hub.altitudeM
            else -> Double.NaN
        }
        tileValues[T_ALT]?.text = when {
            alt.isNaN() -> "—"
            mph -> f("%.0f ft", alt * 3.28084)
            else -> f("%.0f mdpl", alt)
        }

        tileValues[T_G]?.text = if (hub.hasLinear || hub.hasAccel) f("%.2f G", hub.gForce) else "—"
        tileValues[T_BAT]?.text = if (hub.batteryTempC.isNaN()) "—" else f("%.0f°C", hub.batteryTempC)

        val dist = hub.tripMeters / 1000.0 * (if (mph) 0.621371 else 1.0)
        tileValues[T_TRIP]?.text = f("%.1f %s", dist, if (mph) "mi" else "km")
        tileValues[T_MAX]?.text = f("%.0f %s", hub.tripMaxMps * k, unitName)
        tileValues[T_AVG]?.text =
            if (hub.tripMovingSec > 5.0) f("%.0f %s", (hub.tripMeters / hub.tripMovingSec).toFloat() * k, unitName) else "—"
        val t = hub.tripMovingSec.toLong()
        tileValues[T_TIME]?.text = f("%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)

        tileBoxes[T_PRESS]?.visibility = if (hub.hasPressure) View.VISIBLE else View.GONE
        tileValues[T_PRESS]?.text = if (hub.pressureHpa.isNaN()) "—" else f("%.0f hPa", hub.pressureHpa)
        tileBoxes[T_LUX]?.visibility = if (hub.hasLight) View.VISIBLE else View.GONE
        tileValues[T_LUX]?.text = if (hub.lux < 0f) "—" else f("%.0f lx", hub.lux)

        if (tick % 4 == 1) pushLive(h, alt, overSpeed)
        autoBrightness()
    }

    private fun beepOverSpeed() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBeepMs < 12_000L) return
        lastBeepMs = now
        try {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 55)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 160)
            handler.postDelayed({ try { tg.release() } catch (_: Exception) {} }, 260L)
        } catch (_: Exception) {
        }
    }

    private fun isNightActive(): Boolean = when (prefs.nightMode) {
        1 -> true
        2 -> {
            val cal = java.util.Calendar.getInstance()
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = cal.get(java.util.Calendar.MINUTE)
            h >= 18 || h < 5 || (h == 5 && m < 30)
        }
        else -> false
    }

    private fun applyNightVisuals() {
        val on = isNightActive()
        nightVeil.visibility = if (on) View.VISIBLE else View.GONE
        nightBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(7).toFloat()
            setColor(if (on) Color.parseColor("#33F59E0B") else Color.parseColor("#18FFFFFF"))
            setStroke(dp(1), if (on) Color.parseColor("#99FBBF24") else Color.parseColor("#33FFFFFF"))
        }
    }

    private var lastGpsState = -1

    /** Tampilkan peringatan jelas di kartu status atas jika suhu panas >= 43°C atau tidak ada satelit. */
    private fun updateGpsWarning(raw: String, overheat: Boolean) {
        val fix = raw.startsWith("GPS ")
        val state: Int
        val displayText: String
        val fgColor: Int
        val bgColor: Int
        val strokeColor: Int

        when {
            overheat -> {
                state = 3
                displayText = "⚠ PANAS ${hub.batteryTempC.toInt()}°C"
                fgColor = Color.parseColor("#FECACA")
                bgColor = Color.parseColor("#44EF4444")
                strokeColor = Color.parseColor("#CCEF4444")
            }
            fix && hub.satsUsed > 0 -> {
                state = 2
                displayText = "● GPS ${hub.satsUsed}/${hub.satsVisible}"
                fgColor = Color.parseColor("#34D399")
                bgColor = Color.parseColor("#1A10B981")
                strokeColor = Color.parseColor("#5534D399")
            }
            !raw.startsWith("Mencari") && !fix -> {
                state = 0
                displayText = "⚠ GPS MATI / IZIN"
                fgColor = Color.parseColor("#FCA5A5")
                bgColor = Color.parseColor("#2EEF4444")
                strokeColor = Color.parseColor("#88F87171")
            }
            hub.satsUsed == 0 && hub.satsVisible == 0 -> {
                state = 0
                displayText = "⚠ NO SATELIT (0/0)"
                fgColor = Color.parseColor("#FCA5A5")
                bgColor = Color.parseColor("#2EEF4444")
                strokeColor = Color.parseColor("#88F87171")
            }
            else -> {
                state = 1
                displayText = "⚠ CARI SAT (${hub.satsUsed}/${hub.satsVisible})"
                fgColor = Color.parseColor("#FDE68A")
                bgColor = Color.parseColor("#2BF59E0B")
                strokeColor = Color.parseColor("#88FBBF24")
            }
        }

        gpsText.text = displayText
        if (state != lastGpsState) {
            lastGpsState = state
            gpsText.setTextColor(fgColor)
            gpsText.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gpsText.setPadding(dp(7), dp(2), dp(7), dp(2))
            gpsText.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(bgColor)
                setStroke(dp(1), strokeColor)
            }
        }
    }

    /** Data sensor, cuaca real-time, inclinometer & batas kecepatan untuk speedometer WebView. */
    private fun pushLive(heading: Float, alt: Double, overSpeed: Boolean) {
        val g = gauge ?: return
        val fix = hub.gpsStatus().startsWith("GPS ")
        val dot = if (fix) "\uD83D\uDFE2" else "\uD83D\uDFE1"
        val t = hub.tripMovingSec.toLong()
        val pitch = (hub.rawPitchDeg - prefs.pitchOffset).roundToInt()
        val roll = (hub.rawRollDeg - prefs.rollOffset).roundToInt()
        val pitchStr = if (pitch > 0) "+${pitch}°" else "${pitch}°"
        val rollStr = when {
            roll > 0 -> "${roll}°R"
            roll < 0 -> "${-roll}°L"
            else -> "0°"
        }

        val o = org.json.JSONObject()
        o.put("trip", f("%.1f", hub.tripMeters / 1000.0))
        o.put("alt", if (alt.isNaN()) "--" else f("%.0f", alt))
        o.put("head", if (heading.isNaN()) "--" else "${cardinalEn(heading)} ${heading.roundToInt() % 360}\u00B0")
        o.put("sats", if (hub.satsUsed > 0) "$dot ${hub.satsUsed}/${hub.satsVisible} SATS" else "⚠ NO SAT (${hub.satsUsed}/${hub.satsVisible})")
        o.put("gnss", if (fix && hub.satsUsed > 0) "$dot 3D FIX (${hub.satsUsed})" else "⚠ NO SATELIT")
        o.put("satsUsed", hub.satsUsed)
        o.put("satsVisible", hub.satsVisible)
        o.put("gpsFix", fix && hub.satsUsed > 0)
        o.put("max", f("%.0f", hub.tripMaxMps * 3.6f))
        o.put("avg", if (hub.tripMovingSec > 5.0) f("%.0f", hub.tripMeters / hub.tripMovingSec * 3.6) else "0")
        o.put("btemp", if (hub.batteryTempC.isNaN()) "--" else f("%.0f", hub.batteryTempC))
        o.put("time", f("%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60))
        o.put("acc", if (hub.charging) "ACTIVE" else "OFF")
        o.put("bat", if (hub.batteryPct >= 0) hub.batteryPct.toString() else "--")
        o.put("inc", "$pitchStr · $rollStr")
        o.put("limit", prefs.speedLimit)
        o.put("over", overSpeed)
        o.put("wxTemp", if (weather.hasData) "${weather.tempC}°C" else "--°")
        o.put("wxKind", weather.kind)
        o.put("wxLbl", if (weather.hasData) weather.label else "Offline")
        g.live(o.toString())
    }

    private fun cardinalEn(d: Float): String {
        val n = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return n[((d + 22.5f) / 45f).toInt() % 8]
    }

    private fun autoBrightness() {
        if (isNightActive()) {
            setBrightness(0.20f)
            return
        }
        if (!prefs.autoDim || hub.lux < 0f) {
            setBrightness(-1f)
            return
        }
        val b = when {
            hub.lux < 6f -> 0.18f
            hub.lux < 40f -> 0.4f
            else -> -1f
        }
        setBrightness(b)
    }

    private fun setBrightness(b: Float) {
        if (b == lastBrightness) return
        lastBrightness = b
        val lp = window.attributes
        lp.screenBrightness = b
        window.attributes = lp
    }

    // ---------- Panel ----------

    private fun launchSlot(isA: Boolean) {
        val pkg = if (isA) prefs.panelA else prefs.panelB
        if (!engine.isInstalled(pkg)) {
            toast("Aplikasi belum dipilih atau belum terpasang. Tekan lama tombol ini untuk memilih.")
            return
        }
        val bounds = if (isA) engine.boundsA() else engine.boundsB()
        val ok = engine.launch(pkg, if (engine.freeformUsable()) bounds else null, isInMultiWindowMode)
        if (!ok) toast("Gagal membuka aplikasi")
    }

    private fun startPanels() {
        val a = prefs.panelA
        val b = prefs.panelB
        if (!engine.isInstalled(a) && !engine.isInstalled(b)) {
            toast("Pilih aplikasi panel dulu: tekan lama tombol A atau B.")
            return
        }
        val first = if (engine.isInstalled(a)) a else b
        if (engine.freeformUsable()) {
            val wanted = if (prefs.panelCount >= 3) {
                listOf(b to engine.boundsB(), a to engine.boundsA())
            } else {
                listOf(first to engine.boundsA())
            }
            val targets = wanted.filter { engine.isInstalled(it.first) }
            panelsActive = true
            applyInsets()
            var delay = 0L
            for ((pkg, bounds) in targets) {
                handler.postDelayed({ engine.launch(pkg, bounds, false) }, delay)
                delay += 900L
            }
            toast("Menjalankan speedometer + ${targets.size} aplikasi")
            return
        }
        if (!isInMultiWindowMode) {
            toast("Masuk ke split-screen dulu: tekan lama tombol Recent lalu pilih AutoSplit, atau aktifkan \"Paksa mode freeform\" di ATUR.")
            return
        }
        engine.launch(first, null, true)
        if (prefs.panelCount >= 3) {
            toast("Tablet ini belum mendukung freeform, jadi baru 2 jendela. Coba aktifkan \"Paksa mode freeform\" di ATUR.")
        } else {
            toast("Mode 2 jendela: speedometer + 1 aplikasi. Ketuk A atau B untuk mengganti aplikasi di sisi kanan.")
        }
    }

    /** Tukar posisi aplikasi panel atas (A) dan panel bawah (B) dalam 1 ketukan. */
    private fun swapPanels() {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        val tmp = prefs.panelA
        prefs.panelA = prefs.panelB
        prefs.panelB = tmp
        updateSlotButtons()
        applyInsets()
        val aName = engine.labelOf(prefs.panelA)
        val bName = engine.labelOf(prefs.panelB)
        toast("Ditukar: Atas $aName ⇅ Bawah $bName")
        if (panelsActive) {
            startPanels()
        }
    }

    /** Peringatan baterai lemah di bawah 20% dan 10% saat tidak dicas; hilang sendiri saat dicas. */
    private fun checkBattery() {
        val pct = hub.batteryPct
        if (pct < 0) return
        if (hub.charging || pct >= 25) {
            lowBatWarned = 100
            lowBatDialog?.dismiss()
            lowBatDialog = null
            return
        }
        val threshold = when {
            pct < 10 -> 10
            pct < 20 -> 20
            else -> return
        }
        if (threshold >= lowBatWarned) return
        lowBatWarned = threshold
        lowBatDialog?.dismiss()
        lowBatDialog = AlertDialog.Builder(this, DIALOG)
            .setTitle("Baterai lemah: $pct%")
            .setMessage("Baterai tablet tinggal $pct%. Colokkan pengisi daya mobil agar unit tidak mati.")
            .setPositiveButton("Oke", null)
            .showLeft()
    }

    private fun hasAudioPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Bingkai layar berjalan/diam dan equalizer, mengikuti pengaturan dan Mode Hemat. */
    private fun applyEffects() {
        Ambient.periodMs = FRAME_SPEEDS[prefs.frameSpeed.coerceIn(0, FRAME_SPEEDS.size - 1)] * 1000L
        Ambient.running = prefs.frameRunning
        Ambient.lite = prefs.ecoFrame
        val choreo = android.view.Choreographer.getInstance()
        choreo.removeFrameCallback(frameCallback)
        if (Ambient.running) choreo.postFrameCallback(frameCallback)
        overlay.invalidate()
        for (v in animViews) v.invalidate()
        if (prefs.eqMode > 0 && hasAudioPermission()) {
            if (!eq.start(prefs.ecoEq) && !eqWarned) {
                eqWarned = true
                toast("Equalizer tidak didukung di perangkat ini")
            }
        } else {
            eq.stop()
        }
    }

    /** Petunjuk memberi izin Penghemat daya lewat ADB (sekali saja, butuh komputer). */
    private fun showAdbHelp() {
        val tv = TextView(this).apply {
            setTextIsSelectable(true)
            setTextColor(Pal.TEXT)
            textSize = 14f
            setPadding(dp(24), dp(12), dp(24), dp(4))
            text = "Android hanya mengizinkan Penghemat daya diatur aplikasi yang diberi izin khusus. " +
                "Lakukan sekali saja:\n\n" +
                "1. Aktifkan Opsi pengembang lalu Debugging USB di tablet.\n" +
                "2. Sambungkan tablet ke komputer yang sudah terpasang ADB (platform-tools).\n" +
                "3. Jalankan perintah ini di komputer:\n\n" + PowerSaver.ADB_COMMAND + "\n\n" +
                "Setelah itu buka lagi menu ini; tulisan \"perlu izin\" akan hilang."
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Izin Penghemat daya")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Oke", null)
            .showLeft()
    }

    private fun syncChargeService() {
        if (prefs.needsChargeService) ChargeService.start(this) else ChargeService.stop(this)
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            toast("Pengaturan tidak bisa dibuka")
        }
    }

    // ---------- Dialog Modern Floating Card ----------

    private class AppEntry(
        val pkg: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable
    )

    private fun launchablesWithIcons(): List<AppEntry> {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppEntry(
                    it.activityInfo.packageName,
                    it.loadLabel(pm).toString(),
                    it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase(Locale.US) }
    }

    /** Kartu melayang modern (dark-glass + bingkai neon) yang tetap di kolom kiri agar Maps/YouTube tidak tertutup. */
    @Suppress("DEPRECATION")
    private fun showModernCard(title: String, subtitle: String?, body: View): AlertDialog {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F5080D1B"))
                setStroke(dp(2), (Pal.accent and 0x00FFFFFF) or 0xCC000000.toInt())
            }
            elevation = dp(12).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(10))
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 14.5f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titleCol.addView(titleTv)
        if (!subtitle.isNullOrBlank()) {
            val subTv = TextView(this).apply {
                text = subtitle
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            titleCol.addView(subTv)
        }

        var dlgRef: AlertDialog? = null
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(9).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            setOnClickListener { dlgRef?.dismiss() }
        }
        header.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(30), dp(30)).apply { leftMargin = dp(8) })
        card.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#22324A"))
        }
        card.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        val real = Point()
        windowManager.defaultDisplay.getRealSize(real)
        val maxScrollH = (real.y * 0.72f).toInt().coerceAtLeast(dp(260))

        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(maxScrollH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply {
            isVerticalScrollBarEnabled = true
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val dlg = AlertDialog.Builder(this, DIALOG)
            .setView(card)
            .create()
        dlgRef = dlg
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.show()
        place(dlg)
        return dlg
    }

    /** Baris pengaturan bergaya kartu modern dengan pil status berwarna (0=MATI, 1=AKTIF, 2=NILAI/AKSI). */
    private fun modernSettingRow(
        title: String,
        badgeText: String,
        badgeKind: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(9), dp(10), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#11192D"))
                setStroke(dp(1), Color.parseColor("#21304C"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 12.2f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val (fg, bg, border) = when (badgeKind) {
            1 -> Triple(Color.parseColor("#34D399"), Color.parseColor("#13382D"), Color.parseColor("#2D7A5E"))
            2 -> Triple(Color.parseColor("#38BDF8"), Color.parseColor("#0D2A42"), Color.parseColor("#1E5A8A"))
            else -> Triple(Color.parseColor("#94A3B8"), Color.parseColor("#1A2336"), Color.parseColor("#2E3C56"))
        }
        val pill = TextView(this).apply {
            text = badgeText
            setTextColor(fg)
            textSize = 10.2f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7).toFloat()
                setColor(bg)
                setStroke(dp(1), border)
            }
        }
        row.addView(titleTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        row.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(5)
        }
        return row
    }

    /** Tombol pintasan cepat di bagian atas kartu Pengaturan. */
    private fun modernQuickBtn(iconText: String, label: String, highlight: Boolean, onClick: () -> Unit): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(11).toFloat()
                setColor(if (highlight) Color.parseColor("#0E2942") else Color.parseColor("#121B30"))
                setStroke(dp(1), if (highlight) Color.parseColor("#38BDF8") else Color.parseColor("#283755"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val ico = TextView(this).apply {
            text = iconText
            setTextColor(if (highlight) Color.parseColor("#38BDF8") else Color.parseColor("#E2E8F0"))
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val lbl = TextView(this).apply {
            text = label
            setTextColor(if (highlight) Color.parseColor("#E0F2FE") else Color.parseColor("#CBD5E1"))
            textSize = 10.2f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        }
        btn.addView(ico)
        btn.addView(lbl)
        return btn
    }

    /** Grid 3 kolom ikon aplikasi asli Android untuk laci aplikasi & pemilihan panel. */
    private fun buildAppIconGrid(list: List<AppEntry>, onPick: (AppEntry) -> Unit): GridLayout {
        val grid = GridLayout(this).apply {
            columnCount = 3
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        for (app in list) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#111A2E"))
                    setStroke(dp(1), Color.parseColor("#223250"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onPick(app) }
            }
            val iv = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val tv = TextView(this).apply {
                text = app.label
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 10.5f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(5), 0, 0)
            }
            tile.addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
            tile.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(tile, lp)
        }
        return grid
    }

    private fun pickApp(forA: Boolean) {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        val list = launchablesWithIcons()
        var dlgRef: AlertDialog? = null
        val grid = buildAppIconGrid(list) { app ->
            dlgRef?.dismiss()
            if (forA) prefs.panelA = app.pkg else prefs.panelB = app.pkg
            updateSlotButtons()
            applyInsets()
            toast(if (forA) "Panel Atas (A): ${app.label}" else "Panel Bawah (B): ${app.label}")
        }
        dlgRef = showModernCard(
            if (forA) "❖ PILIH PANEL ATAS (A)" else "❖ PILIH PANEL BAWAH (B)",
            "Ketuk ikon aplikasi yang ingin dipasang",
            grid
        )
    }

    private fun drawer() {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        val list = launchablesWithIcons()
        var dlgRef: AlertDialog? = null
        val grid = buildAppIconGrid(list) { app ->
            dlgRef?.dismiss()
            engine.launch(app.pkg, null, isInMultiWindowMode)
        }
        dlgRef = showModernCard(
            "❖ SEMUA APLIKASI",
            "${list.size} aplikasi terpasang · Ketuk untuk buka",
            grid
        )
    }

    private fun onOff(b: Boolean) = if (b) "AKTIF" else "MATI"

    private fun gradName(): String {
        for (i in GRAD_NAMES.indices) {
            if (GRAD_COLORS[i * 2] == prefs.accent && GRAD_COLORS[i * 2 + 1] == prefs.accent2) return GRAD_NAMES[i]
        }
        return "Kustom"
    }

    private fun askFrameStyle() {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        val listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }
        var dlgRef: AlertDialog? = null
        val cur = prefs.frameStyle.coerceIn(0, FRAME_STYLE_NAMES.size - 1)
        FRAME_STYLE_NAMES.forEachIndexed { idx, name ->
            val active = idx == cur
            listBox.addView(
                modernSettingRow(name, if (active) "AKTIF" else "PILIH", if (active) 1 else 2) {
                    dlgRef?.dismiss()
                    prefs.frameStyle = idx
                    Pal.frameStyle = idx
                    overlay.invalidate()
                    for (v in animViews) v.invalidate()
                    toast("Bingkai: $name")
                }
            )
        }
        dlgRef = showModernCard("❖ GAYA BINGKAI LUXURY HD", "4 Pilihan Bingkai Modern", listBox)
    }

    private fun applyPreset(entertainment: Boolean, fullScreen: Boolean) {
        if (fullScreen) {
            panelsActive = false
            applyInsets()
            toast("Preset: Fokus Speedometer (Layar Penuh)")
            return
        }
        prefs.panelCount = 3
        prefs.entertainmentLayout = entertainment
        updateSlotButtons()
        applyInsets()
        startPanels()
        toast(if (entertainment) "Preset: Mode Hiburan (Video Besar)" else "Preset: Mode Navigasi (Peta Besar)")
    }

    private fun showSettings() {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        var dlgRef: AlertDialog? = null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        // 1. Baris Preset & Pintasan Cepat 1-Ketukan (Tukar A/B, Navigasi, Hiburan, Fokus Speedo)
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnSwap = modernQuickBtn("⇅", "Tukar A/B", true) {
            dlgRef?.dismiss()
            swapPanels()
        }
        val btnNav = modernQuickBtn("🗺", "Navigasi", !prefs.entertainmentLayout && panelsActive) {
            dlgRef?.dismiss()
            applyPreset(entertainment = false, fullScreen = false)
        }
        val btnEnt = modernQuickBtn("▶", "Hiburan", prefs.entertainmentLayout && panelsActive) {
            dlgRef?.dismiss()
            applyPreset(entertainment = true, fullScreen = false)
        }
        val btnFull = modernQuickBtn("⛶", "Speedo", !panelsActive) {
            dlgRef?.dismiss()
            applyPreset(entertainment = false, fullScreen = true)
        }
        quickRow.addView(btnSwap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f).apply { rightMargin = dp(4) })
        quickRow.addView(btnNav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        quickRow.addView(btnEnt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        quickRow.addView(btnFull, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f))
        container.addView(quickRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(7)
        })

        // Indikator posisi panel Atas & Bawah saat ini
        val posChip = TextView(this).apply {
            text = "Atas (A): ${engine.labelOf(prefs.panelA)}   ⇅   Bawah (B): ${engine.labelOf(prefs.panelB)}"
            setTextColor(Color.parseColor("#7DD3FC"))
            textSize = 10.5f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#0B1F33"))
                setStroke(dp(1), Color.parseColor("#1A4366"))
            }
        }
        container.addView(posChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(9)
        })

        fun addRow(title: String, badge: String, kind: Int, action: () -> Unit) {
            container.addView(modernSettingRow(title, badge, kind) {
                dlgRef?.dismiss()
                action()
            })
        }

        // Semua fitur baru + seluruh fitur pengaturan bawaan tetap 100% utuh
        addRow("Tema / Gaya Speedometer", STYLE_NAMES[prefs.gaugeStyle.coerceIn(0, STYLE_NAMES.size - 1)].substringBefore(" ("), 2) { askStyle() }
        addRow("Gaya Bingkai Luxury HD", FRAME_STYLE_NAMES[prefs.frameStyle.coerceIn(0, FRAME_STYLE_NAMES.size - 1)], 2) { askFrameStyle() }
        addRow("Mode Malam Anti-Silau", NIGHT_MODE_NAMES[prefs.nightMode.coerceIn(0, NIGHT_MODE_NAMES.size - 1)], if (isNightActive()) 1 else 0) {
            prefs.nightMode = (prefs.nightMode + 1) % NIGHT_MODE_NAMES.size
            applyNightVisuals()
            autoBrightness()
            showSettings()
        }
        val limLabel = if (prefs.speedLimit > 0) "${prefs.speedLimit} KM/J" else "MATI"
        addRow("Batas Kecepatan (Speed Alert)", limLabel, if (prefs.speedLimit > 0) 2 else 0) {
            val idx = SPEED_LIMITS.indexOf(prefs.speedLimit).let { if (it < 0) 0 else it }
            prefs.speedLimit = SPEED_LIMITS[(idx + 1) % SPEED_LIMITS.size]
            showSettings()
        }
        addRow("Kalibrasi 0° Inclinometer (Tanjakan)", "KALIBRASI", 2) {
            prefs.pitchOffset = hub.rawPitchDeg
            prefs.rollOffset = hub.rawRollDeg
            toast("Inclinometer dikalibrasi ke 0° (jalan rata)")
        }
        addRow("Orientasi Layar", ORIENT_NAMES[prefs.orientation.coerceIn(0, ORIENT_NAMES.size - 1)], 2) {
            prefs.orientation = (prefs.orientation + 1) % ORIENT_NAMES.size
            applyOrientation()
            handler.postDelayed({
                applyInsets()
                if (panelsActive) startPanels()
            }, 350L)
            toast("Orientasi: ${ORIENT_NAMES[prefs.orientation]}")
        }
        addRow("Susunan Mode Potret", if (prefs.portraitTop2) "ATAS 2 + BAWAH 1" else "3 SUSUN", 2) {
            prefs.portraitTop2 = !prefs.portraitTop2
            applyInsets()
            if (panelsActive) startPanels()
            showSettings()
        }
        addRow("Posisi Speedometer", if (prefs.speedoFlip) "KANAN / BAWAH" else "KIRI / ATAS", 2) {
            prefs.speedoFlip = !prefs.speedoFlip
            applyInsets()
            if (panelsActive) startPanels()
            showSettings()
        }
        addRow("Atur Ukuran Semua Area", "${prefs.leftPercent}% · ${prefs.splitRatioA}/${100 - prefs.splitRatioA}", 2) { askRatio() }
        addRow("Mode Bebas Geser Jendela", onOff(prefs.freeDragMode), if (prefs.freeDragMode) 1 else 0) {
            prefs.freeDragMode = !prefs.freeDragMode
            applyInsets()
            toast(if (prefs.freeDragMode) "Mode Bebas Geser: seret & ubah ukuran jendela sesuka hati" else "Bingkai slot aktif kembali")
            showSettings()
        }
        addRow("Mulai ${prefs.panelCount} Jendela Split (Auto-Resize)", "MULAI", 1) { startPanels() }
        addRow("Tukar Posisi Aplikasi Atas ⇅ Bawah", "TUKAR", 2) { swapPanels() }
        addRow("Pilih Aplikasi Panel Atas (A)", engine.labelOf(prefs.panelA), 2) { pickApp(true) }
        addRow("Pilih Aplikasi Panel Bawah (B)", engine.labelOf(prefs.panelB), 2) { pickApp(false) }
        addRow("Sensor & Diagnostik Perangkat", "BUKA ›", 2) { showSensors() }
        addRow("Reset Trip (jarak, maks, waktu)", "RESET", 0) {
            hub.resetTrip()
            toast("Data trip direset")
        }
        addRow("Satuan Kecepatan", if (prefs.useMph) "MPH" else "KM/JAM", 2) {
            prefs.useMph = !prefs.useMph
            setGauge()
            showSettings()
        }
        addRow("Warna Gradasi Bingkai", gradName(), 2) { askAccent() }
        addRow("Hemat Speedometer (diam)", onOff(prefs.eco), if (prefs.eco) 1 else 0) {
            prefs.eco = !prefs.eco
            setGauge()
            applyEffects()
            showSettings()
        }
        addRow("Equalizer Musik", EQ_NAMES[prefs.eqMode.coerceIn(0, EQ_NAMES.size - 1)], if (prefs.eqMode > 0) 1 else 0) {
            prefs.eqMode = (prefs.eqMode + 1) % EQ_NAMES.size
            if (prefs.eqMode > 0 && !hasAudioPermission()) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            }
            setGauge()
            applyEffects()
            showSettings()
        }
        addRow("Bingkai Neon Berjalan", if (prefs.frameRunning) "JALAN" else "DIAM", if (prefs.frameRunning) 1 else 0) {
            prefs.frameRunning = !prefs.frameRunning
            setGauge()
            applyEffects()
            showSettings()
        }
        addRow("Hemat Bingkai (ringan)", onOff(prefs.ecoFrame), if (prefs.ecoFrame) 1 else 0) {
            prefs.ecoFrame = !prefs.ecoFrame
            setGauge()
            applyEffects()
            showSettings()
        }
        addRow("Hemat Equalizer (15 fps)", onOff(prefs.ecoEq), if (prefs.ecoEq) 1 else 0) {
            prefs.ecoEq = !prefs.ecoEq
            setGauge()
            applyEffects()
            showSettings()
        }
        addRow("Kecepatan Warna Bingkai", FRAME_SPEED_NAMES[prefs.frameSpeed.coerceIn(0, FRAME_SPEED_NAMES.size - 1)].substringBefore(" ("), 2) {
            prefs.frameSpeed = (prefs.frameSpeed + 1) % FRAME_SPEEDS.size
            setGauge()
            applyEffects()
            showSettings()
        }
        addRow("Kotak Sensor Bawah", onOff(prefs.showTiles), if (prefs.showTiles) 1 else 0) {
            prefs.showTiles = !prefs.showTiles
            recreate()
        }
        addRow("Lebar Panel Speedometer", "${prefs.leftPercent}%", 2) { askRatio() }
        addRow("Jumlah Jendela Split", "${prefs.panelCount} PANEL", 2) {
            prefs.panelCount = if (prefs.panelCount >= 3) 2 else 3
            updateSlotButtons()
            applyInsets()
            showSettings()
        }
        addRow("Susunan Jendela Kanan", if (prefs.entertainmentLayout) "HIBURAN" else "NAVIGASI", 2) {
            prefs.entertainmentLayout = !prefs.entertainmentLayout
            applyInsets()
            showSettings()
        }
        addRow("Aktif Otomatis Saat Dicas", onOff(prefs.autoOnCharge), if (prefs.autoOnCharge) 1 else 0) {
            prefs.autoOnCharge = !prefs.autoOnCharge
            syncChargeService()
            showSettings()
        }
        addRow("Mulai Panel Otomatis Saat Dicas", onOff(prefs.autoStartPanels), if (prefs.autoStartPanels) 1 else 0) {
            prefs.autoStartPanels = !prefs.autoStartPanels
            showSettings()
        }
        addRow("Pop-up Matikan Saat Charger Cabut", onOff(prefs.askOnUnplug), if (prefs.askOnUnplug) 1 else 0) {
            prefs.askOnUnplug = !prefs.askOnUnplug
            syncChargeService()
            showSettings()
        }
        addRow("Izin Kunci Layar Otomatis", if (ShutdownActivity.isAdminActive(this)) "AKTIF" else "IZINKAN", if (ShutdownActivity.isAdminActive(this)) 1 else 0) {
            if (ShutdownActivity.isAdminActive(this)) toast("Izin kunci layar sudah aktif")
            else ShutdownActivity.requestAdmin(this, REQ_ADMIN)
        }
        addRow("Coba Pop-up Matikan Unit", "TEST", 2) { ShutdownActivity.show(this) }
        val psBadge = if (prefs.powerSaveOnLock && !PowerSaver.canControl(this)) "IZIN ADB" else onOff(prefs.powerSaveOnLock)
        addRow("Hemat Daya Saat Terkunci", psBadge, if (prefs.powerSaveOnLock) 1 else 0) {
            if (prefs.powerSaveOnLock && !PowerSaver.canControl(this)) {
                showAdbHelp()
            } else {
                prefs.powerSaveOnLock = !prefs.powerSaveOnLock
                syncChargeService()
                if (prefs.powerSaveOnLock && !PowerSaver.canControl(this)) showAdbHelp() else showSettings()
            }
        }
        addRow("Bluetooth Nyala Saat Layar On", onOff(prefs.btOnScreenOn), if (prefs.btOnScreenOn) 1 else 0) {
            prefs.btOnScreenOn = !prefs.btOnScreenOn
            syncChargeService()
            showSettings()
        }
        addRow("Wi-Fi Nyala Saat Layar On", onOff(prefs.wifiOnScreenOn), if (prefs.wifiOnScreenOn) 1 else 0) {
            prefs.wifiOnScreenOn = !prefs.wifiOnScreenOn
            syncChargeService()
            showSettings()
        }
        addRow("Kecerahan Otomatis (Cahaya)", onOff(prefs.autoDim), if (prefs.autoDim) 1 else 0) {
            prefs.autoDim = !prefs.autoDim
            showSettings()
        }
        addRow("Layar Tetap Menyala", onOff(prefs.keepScreenOn), if (prefs.keepScreenOn) 1 else 0) {
            prefs.keepScreenOn = !prefs.keepScreenOn
            if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            showSettings()
        }
        addRow("Paksa Mode Freeform", onOff(prefs.forceFreeform), if (prefs.forceFreeform) 1 else 0) {
            prefs.forceFreeform = !prefs.forceFreeform
            showSettings()
        }
        addRow("Jadikan Launcher Utama", "ATUR ›", 2) { openSettings(Settings.ACTION_HOME_SETTINGS) }

        dlgRef = showModernCard("⚙ PENGATURAN ELIO UX", "Konfigurasi Cockpit & Split-Screen", container)
    }

    private fun askAccent() {
        val names = GRAD_NAMES + arrayOf("Roda warna: warna 1 (awal)", "Roda warna: warna 2 (akhir)")
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Warna gradasi")
            .setItems(names) { _, which ->
                when {
                    which < GRAD_NAMES.size -> {
                        prefs.accent = GRAD_COLORS[which * 2]
                        prefs.accent2 = GRAD_COLORS[which * 2 + 1]
                        recreate()
                    }
                    which == GRAD_NAMES.size -> askHue(false)
                    else -> askHue(true)
                }
            }
            .setNegativeButton("Batal", null)
            .showLeft()
    }

    private fun askHue(second: Boolean) {
        val start = if (second) prefs.accent2 else prefs.accent
        val wheel = ColorWheelView(this).apply { setColor(start) }
        val bar = View(this)
        fun paintBar(c: Int) {
            bar.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                if (second) intArrayOf(prefs.accent, c) else intArrayOf(c, prefs.accent2)
            ).apply { cornerRadius = dp(6).toFloat() }
        }
        paintBar(start)
        wheel.onChange = { paintBar(it) }
        val bright = SeekBar(this).apply {
            max = 80
            progress = ((wheel.brightness - 0.2f) * 100).toInt().coerceIn(0, 80)
        }
        bright.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) wheel.brightness = 0.2f + p / 100f
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val hint = TextView(this).apply {
            text = "Kecerahan"
            setTextColor(Pal.MUTED)
            textSize = 12f
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(wheel)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)).apply { topMargin = dp(14) })
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            addView(bright, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(if (second) "Warna 2 (akhir gradasi)" else "Warna 1 (awal gradasi)")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Simpan") { _, _ ->
                if (second) prefs.accent2 = wheel.color() else prefs.accent = wheel.color()
                recreate()
            }
            .setNegativeButton("Batal", null)
            .showLeft()
    }

    /** Kartu modern 2 slider untuk membesar-kecilkan Speedometer, Panel A, dan Panel B secara langsung. */
    private fun askRatio() {
        var dlgRef: AlertDialog? = null
        val lblSpd = TextView(this).apply {
            text = "Ukuran Area Speedometer: ${prefs.leftPercent}%"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        val sbSpd = SeekBar(this).apply {
            max = 40 // 25..65%
            progress = (prefs.leftPercent - 25).coerceIn(0, 40)
        }
        sbSpd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + 25
                lblSpd.text = "Ukuran Area Speedometer: $v%"
                if (fromUser) {
                    prefs.leftPercent = v
                    applyInsets()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val lblAB = TextView(this).apply {
            val a = prefs.splitRatioA.coerceIn(25, 75)
            text = "Proporsi Atas / Bawah (A vs B): $a% / ${100 - a}%"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        val sbAB = SeekBar(this).apply {
            max = 50 // 25..75%
            progress = (prefs.splitRatioA - 25).coerceIn(0, 50)
        }
        sbAB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val a = p + 25
                lblAB.text = "Proporsi Atas / Bawah (A vs B): $a% / ${100 - a}%"
                if (fromUser) {
                    prefs.splitRatioA = a
                    applyInsets()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val applyBtn = modernSettingRow("Terapkan & Sesuaikan Jendela Split", "TERAPKAN", 1) {
            dlgRef?.dismiss()
            applyInsets()
            if (panelsActive) startPanels()
            toast("Ukuran area diterapkan (${prefs.leftPercent}% · ${prefs.splitRatioA}/${100 - prefs.splitRatioA})")
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(lblSpd)
            addView(sbSpd, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
            addView(lblAB)
            addView(sbAB, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })
            addView(applyBtn)
        }
        dlgRef = showModernCard("⊞ ATUR UKURAN SEMUA AREA", "Geser slider untuk atur besar-kecil secara langsung", box)
    }

    private fun systemInfo(): String = buildString {
        append("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("RAM: bebas ${hub.ramFreeMb()} MB dari ${hub.ramTotalMb()} MB\n")
        append("Multi-window sekarang: ${if (isInMultiWindowMode) "ya" else "tidak"}\n")
        append("Fitur freeform sistem: ${if (engine.systemHasFreeform()) "ada" else "tidak ada"}\n")
        append("Paksa freeform: ${onOff(prefs.forceFreeform)}\n")
        append("GPS: ${hub.gpsStatus()}\n")
        append("Izin Penghemat daya (ADB): ${if (PowerSaver.canControl(this@MainActivity)) "sudah" else "belum"}\n")
        append("Ketinggian: ${when {
            !hub.mslAltitude().isNaN() -> f("%.0f mdpl (NMEA, di atas permukaan laut)", hub.mslAltitude())
            !hub.pressureHpa.isNaN() -> "dari barometer (perkiraan mdpl)"
            !hub.altitudeM.isNaN() -> f("%.0f m (GPS elipsoid, belum mdpl)", hub.altitudeM)
            else -> "belum ada"
        }}\n")
        append("Sinyal seluler: ${if (link.cellLevel < 0) "tidak terbaca" else "${link.cellLevel} dari 4"}\n")
        append("Sinyal Wi-Fi: ${if (link.wifiLevel < 0) "tidak terhubung" else "${link.wifiLevel} dari 4"}\n")
        val compass = when {
            hub.hasRotation -> "rotation vector"
            hub.hasCompassFallback -> "akselerometer + magnetometer"
            else -> "tidak ada"
        }
        append("Kompas: $compass\n")
        append("Gaya G: ${if (hub.hasLinear) "linear acceleration" else if (hub.hasAccel) "akselerometer" else "tidak ada"}\n")
        append("Sensor cahaya: ${if (hub.hasLight) "ada" else "tidak ada"}\n")
        append("Barometer: ${if (hub.hasPressure) "ada" else "tidak ada"}\n")
        append("Suhu ruang: ${if (hub.hasAmbientTemp) "ada" else "tidak ada"}\n")
        append("\n--- Semua sensor (nilai langsung) ---\n")
    }

    private fun showSensors() {
        if (moving()) {
            toast(LOCK_MSG)
            return
        }
        val live = SensorLive(this)
        live.start()
        val tv = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Pal.TEXT)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val sv = ScrollView(this).apply { addView(tv) }
        val loop = object : Runnable {
            override fun run() {
                tv.text = systemInfo() + live.render()
                handler.postDelayed(this, 500L)
            }
        }
        val dlg = AlertDialog.Builder(this, DIALOG)
            .setTitle("Sensor dan diagnostik")
            .setView(sv)
            .setPositiveButton("Tutup", null)
            .create()
        dlg.setOnDismissListener {
            handler.removeCallbacks(loop)
            live.stop()
        }
        dlg.show()
        place(dlg)
        handler.post(loop)
    }

    companion object {
        const val EXTRA_CHARGE = "charge"

        private const val REQ_LOCATION = 100
        private const val REQ_ADMIN = 101
        private const val REQ_AUDIO = 102
        private val EQ_NAMES = arrayOf("Mati", "Batang", "Gelombang")
        /** Detik per satu putaran warna berjalan. */
        private val FRAME_SPEEDS = intArrayOf(14, 9, 6, 3)
        private val FRAME_SPEED_NAMES = arrayOf("Sangat lambat (14 dtk)", "Lambat (9 dtk)", "Sedang (6 dtk)", "Cepat (3 dtk)")
        private const val DIALOG = android.R.style.Theme_Material_Dialog_Alert
        private const val LOCK_MSG = "Dikunci saat mobil bergerak"
        private val STYLE_NAMES = arrayOf(
            "Lexus F-Sport (HD)",
            "BMW G-Series (iDrive 8 HD)",
            "Mazda KODO (HD)",
            "Honda Civic Type R FL5 (HD)",
            "ELIO Neon (Innova Reborn)",
            "BMW M Sport (Innova Reborn)"
        )
        private val FRAME_STYLE_NAMES = arrayOf(
            "MBUX Titanium",
            "Lexus Sapphire",
            "BMW Precision",
            "Minimalis"
        )
        private val NIGHT_MODE_NAMES = arrayOf("MATI", "AKTIF", "OTOMATIS")
        private val ORIENT_NAMES = arrayOf("LANSKAP", "POTRET", "OTOMATIS")
        private val SPEED_LIMITS = intArrayOf(0, 80, 100, 120)
        private val GRAD_NAMES = arrayOf("Cyber Neon", "Aurora", "Plasma", "Sunset", "Lava GR", "Gold", "Ice", "Toxic")
        private val GRAD_COLORS = intArrayOf(
            Color.parseColor("#00E5FF"), Color.parseColor("#A855F7"),
            Color.parseColor("#00F5A0"), Color.parseColor("#00D9F5"),
            Color.parseColor("#F72585"), Color.parseColor("#4361EE"),
            Color.parseColor("#FF6A00"), Color.parseColor("#EE0979"),
            Color.parseColor("#FF1E56"), Color.parseColor("#FFAC41"),
            Color.parseColor("#F7971E"), Color.parseColor("#FFD200"),
            Color.parseColor("#74EBD5"), Color.parseColor("#9FACE6"),
            Color.parseColor("#B7FF00"), Color.parseColor("#00E5FF")
        )

        private const val T_HEAD = "head"
        private const val T_ALT = "alt"
        private const val T_G = "g"
        private const val T_BAT = "bat"
        private const val T_TRIP = "trip"
        private const val T_MAX = "max"
        private const val T_AVG = "avg"
        private const val T_TIME = "time"
        private const val T_PRESS = "press"
        private const val T_LUX = "lux"
    }
}
