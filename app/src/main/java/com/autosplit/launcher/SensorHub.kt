package com.autosplit.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Membaca semua sensor bawaan perangkat. Semua callback berjalan di thread utama.
 * Dipanggil dari onStart/onStop (bukan onResume/onPause) supaya tetap hidup saat
 * launcher berada di jendela split yang tidak sedang fokus.
 */
class SensorHub(private val ctx: Context) : SensorEventListener, LocationListener {

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val main = Handler(Looper.getMainLooper())

    private var started = false

    // GPS
    var hasGpsHardware = false
        private set
    var permissionGranted = false
        private set
    var accuracyM = -1f
        private set
    var altitudeM = Double.NaN
        private set
    var lastLat = Double.NaN
        private set
    var lastLon = Double.NaN
        private set
    var gpsBearing = Float.NaN
        private set
    var satsUsed = 0
        private set
    var satsVisible = 0
        private set
    private var mslAlt = Double.NaN
    private var mslTime = 0L
    private val nmea = android.location.OnNmeaMessageListener { msg, _ -> parseNmea(msg) }
    private var speedMps = 0f
    private var lastFixElapsed = 0L
    private var lastLoc: Location? = null
    private var declination = 0f

    // Trip
    var tripMeters = 0.0
        private set
    var tripMaxMps = 0f
        private set
    var tripMovingSec = 0.0
        private set

    // Sensor lain
    var headingDeg = Float.NaN
        private set
    var gForce = 0f
        private set
    var latG = 0f
        private set
    var longG = 0f
        private set
    var rawPitchDeg = 0f
        private set
    var rawRollDeg = 0f
        private set
    var pressureHpa = Float.NaN
        private set
    var lux = -1f
        private set
    var cabinTempC = Float.NaN
        private set
    var hasRotation = false
        private set
    var hasCompassFallback = false
        private set
    var hasLinear = false
        private set
    var hasAccel = false
        private set
    var hasLight = false
        private set
    var hasPressure = false
        private set
    var hasAmbientTemp = false
        private set

    // Baterai
    var batteryPct = -1
        private set
    var batteryTempC = Float.NaN
        private set
    var charging = false
        private set

    private val rot = FloatArray(9)
    private val remap = FloatArray(9)
    private val orient = FloatArray(3)
    private val acc = FloatArray(3)
    private val mag = FloatArray(3)
    private val gravity = FloatArray(3)
    private var hasAccSample = false
    private var hasMagSample = false
    private var hx = 0.0
    private var hy = 0.0
    private var headingInit = false

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val n = status.satelliteCount
            for (i in 0 until n) if (status.usedInFix(i)) used++
            satsUsed = used
            satsVisible = n
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        hasGpsHardware = lm.allProviders.contains(LocationManager.GPS_PROVIDER)
        permissionGranted =
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasGpsHardware && permissionGranted) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            } catch (e: Exception) {
            }
            // Ikut menerima posisi yang sudah didapat aplikasi lain (mis. Google Maps) tanpa biaya baterai.
            try {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            } catch (e: Exception) {
            }
            try {
                lm.registerGnssStatusCallback(gnss, main)
            } catch (e: Exception) {
            }
            try {
                lm.addNmeaListener(nmea, main)
            } catch (e: Exception) {
            }
        }

        val rotation = reg(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_UI)
        val linear = reg(Sensor.TYPE_LINEAR_ACCELERATION, SensorManager.SENSOR_DELAY_UI)
        hasRotation = rotation != null
        hasLinear = linear != null
        if (rotation == null || linear == null) {
            hasAccel = reg(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_UI) != null
        }
        if (rotation == null) {
            hasCompassFallback = hasAccel && reg(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_UI) != null
        }
        hasLight = reg(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL) != null
        hasPressure = reg(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL) != null
        hasAmbientTemp = reg(Sensor.TYPE_AMBIENT_TEMPERATURE, SensorManager.SENSOR_DELAY_NORMAL) != null
        readBattery()
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            sm.unregisterListener(this)
        } catch (e: Exception) {
        }
        try {
            lm.removeUpdates(this)
        } catch (e: Exception) {
        }
        try {
            lm.unregisterGnssStatusCallback(gnss)
        } catch (e: Exception) {
        }
        try {
            lm.removeNmeaListener(nmea)
        } catch (e: Exception) {
        }
        hasAccSample = false
        hasMagSample = false
    }

    private fun reg(type: Int, delay: Int): Sensor? {
        val s = sm.getDefaultSensor(type) ?: return null
        return try {
            if (sm.registerListener(this, s, delay)) s else null
        } catch (e: Exception) {
            null
        }
    }

    /** Kecepatan saat ini; 0 bila GPS belum ada atau sinyal hilang lebih dari 4 detik. */
    fun currentSpeedMps(): Float {
        if (lastFixElapsed == 0L) return 0f
        return if (SystemClock.elapsedRealtime() - lastFixElapsed > 4000L) 0f else speedMps
    }

    /** Kalimat GGA: kolom 6 = kualitas fix, kolom 9 = ketinggian di atas permukaan laut (meter). */
    private fun parseNmea(m: String) {
        if (m.length < 7 || m[0] != '$' || !m.regionMatches(3, "GGA", 0, 3)) return
        val f = m.split(',')
        if (f.size < 10) return
        if ((f[6].toIntOrNull() ?: 0) == 0) return
        val alt = f[9].toDoubleOrNull() ?: return
        mslAlt = alt
        mslTime = SystemClock.elapsedRealtime()
    }

    /** Ketinggian mdpl dari NMEA bila masih baru (10 detik), selain itu NaN. */
    fun mslAltitude(): Double =
        if (mslTime > 0 && SystemClock.elapsedRealtime() - mslTime < 10000L) mslAlt else Double.NaN

    fun gpsStatus(): String {
        if (!hasGpsHardware) return "GPS tidak tersedia di perangkat ini"
        if (!permissionGranted) return "Izin lokasi belum diberikan"
        val enabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
        if (!enabled) return "GPS mati, nyalakan di pengaturan lokasi"
        val age = SystemClock.elapsedRealtime() - lastFixElapsed
        if (lastFixElapsed == 0L || age > 4000L) {
            return if (satsVisible == 0) "Mencari satelit: belum ada sinyal, butuh langit terbuka"
            else "Mencari satelit (${satsUsed} dipakai dari ${satsVisible} terlihat)"
        }
        val acc = if (accuracyM >= 0f) "±${accuracyM.toInt()} m · " else ""
        return "GPS $acc${satsUsed}/${satsVisible} satelit"
    }

    override fun onLocationChanged(loc: Location) {
        // Posisi dari jaringan (Wi-Fi/seluler) terlalu kasar untuk kecepatan; hanya GPS atau fused yang akurat.
        if (loc.provider != LocationManager.GPS_PROVIDER && (!loc.hasAccuracy() || loc.accuracy > 25f)) return
        val prev = lastLoc
        if (prev != null && loc.time <= prev.time) return
        accuracyM = if (loc.hasAccuracy()) loc.accuracy else -1f
        altitudeM = if (loc.hasAltitude()) loc.altitude else Double.NaN
        lastLat = loc.latitude
        lastLon = loc.longitude

        var v = if (loc.hasSpeed()) loc.speed else -1f
        var dist = 0f
        var dt = 0.0
        if (prev != null) {
            dt = (loc.time - prev.time) / 1000.0
            dist = prev.distanceTo(loc)
            if (v < 0f && dt > 0.2) v = (dist / dt).toFloat()
        }
        if (v < 0.5f) v = 0f
        speedMps = v
        lastFixElapsed = SystemClock.elapsedRealtime()

        if (loc.hasBearing() && v > 2f) gpsBearing = loc.bearing

        val okAcc = accuracyM < 0f || accuracyM <= 30f
        if (v >= 0.8f && okAcc && prev != null && dt > 0.0 && dt <= 10.0) {
            tripMeters += dist
            tripMovingSec += dt
        }
        if (v > tripMaxMps) tripMaxMps = v

        try {
            declination = android.hardware.GeomagneticField(
                loc.latitude.toFloat(), loc.longitude.toFloat(),
                (if (loc.hasAltitude()) loc.altitude else 0.0).toFloat(), loc.time
            ).declination
        } catch (e: Exception) {
        }
        lastLoc = Location(loc)
    }

    @Deprecated("Dibutuhkan oleh Android lama")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                applyHeading()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                acc[0] = e.values[0]; acc[1] = e.values[1]; acc[2] = e.values[2]
                hasAccSample = true
                val a = 0.9f
                for (i in 0..2) gravity[i] = a * gravity[i] + (1f - a) * e.values[i]
                updateInclinometer(gravity[0], gravity[1], gravity[2])
                if (!hasLinear) {
                    val lx = e.values[0] - gravity[0]
                    val ly = e.values[1] - gravity[1]
                    val lz = e.values[2] - gravity[2]
                    updateG(lx, ly, lz)
                }
                if (!hasRotation && hasCompassFallback && hasMagSample) {
                    if (SensorManager.getRotationMatrix(rot, null, acc, mag)) applyHeading()
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                mag[0] = e.values[0]; mag[1] = e.values[1]; mag[2] = e.values[2]
                hasMagSample = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                updateG(x, y, z)
            }
            Sensor.TYPE_LIGHT -> {
                val v = e.values[0]
                lux = if (lux < 0f) v else lux * 0.9f + v * 0.1f
            }
            Sensor.TYPE_PRESSURE -> pressureHpa = e.values[0]
            Sensor.TYPE_AMBIENT_TEMPERATURE -> cabinTempC = e.values[0]
        }
    }

    /** Sudut tanjakan (pitch) & kemiringan bodi (roll) dari vektor gravitasi akselerometer. */
    @Suppress("DEPRECATION")
    private fun updateInclinometer(gx: Float, gy: Float, gz: Float) {
        val sx: Float
        val sy: Float
        when (wm.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> { sx = gy; sy = -gx }
            Surface.ROTATION_180 -> { sx = -gx; sy = -gy }
            Surface.ROTATION_270 -> { sx = -gy; sy = gx }
            else -> { sx = gx; sy = gy }
        }
        val roll = Math.toDegrees(atan2(sx.toDouble(), sqrt((sy * sy + gz * gz).toDouble()))).toFloat()
        val pitch = Math.toDegrees(atan2(gz.toDouble(), sqrt((sx * sx + sy * sy).toDouble()))).toFloat()
        rawRollDeg = rawRollDeg * 0.85f + roll * 0.15f
        rawPitchDeg = rawPitchDeg * 0.85f + pitch * 0.15f
    }

    /**
     * Gaya G dari percepatan linear (tanpa gravitasi) pada sumbu perangkat. Sumbu layar
     * ditentukan dari rotasi tampilan. Tablet tegak di dasbor: maju = arah kamera belakang (-Z).
     * Tablet datar: maju = atas layar.
     */
    @Suppress("DEPRECATION")
    private fun updateG(x: Float, y: Float, z: Float) {
        val g = SensorManager.GRAVITY_EARTH
        gForce = gForce * 0.85f + (sqrt(x * x + y * y + z * z) / g) * 0.15f
        val sx: Float
        val sy: Float
        when (wm.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> { sx = y; sy = -x }
            Surface.ROTATION_180 -> { sx = -x; sy = -y }
            Surface.ROTATION_270 -> { sx = -y; sy = x }
            else -> { sx = x; sy = y }
        }
        val upright = abs(rot[8]) <= 0.75f
        val lat = sx / g
        val lon = (if (upright) -z else sy) / g
        latG = latG * 0.8f + lat * 0.2f
        longG = longG * 0.8f + lon * 0.2f
    }

    /**
     * Tablet yang dipasang tegak di dasbor: arah mobil = arah kamera belakang (sumbu -Z).
     * Tablet yang diletakkan datar: pakai arah atas layar sesuai rotasi tampilan.
     */
    @Suppress("DEPRECATION")
    private fun applyHeading() {
        val flat = abs(rot[8]) > 0.75f
        val ok = if (flat) {
            val (ax, ay) = when (wm.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rot, ax, ay, remap)
        } else {
            SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_MINUS_Z, remap)
        }
        if (!ok) return
        SensorManager.getOrientation(remap, orient)
        val az = Math.toRadians(Math.toDegrees(orient[0].toDouble()) + declination)
        if (!headingInit) {
            hx = sin(az); hy = cos(az); headingInit = true
        } else {
            hx = hx * 0.85 + sin(az) * 0.15
            hy = hy * 0.85 + cos(az) * 0.15
        }
        headingDeg = ((Math.toDegrees(atan2(hx, hy)) + 360.0) % 360.0).toFloat()
    }

    fun readBattery() {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        batteryTempC = if (t == Int.MIN_VALUE) Float.NaN else t / 10f
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    fun resetTrip() {
        tripMeters = 0.0
        tripMaxMps = 0f
        tripMovingSec = 0.0
    }

    fun ramFreeMb(): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024L * 1024L)
    }

    fun ramTotalMb(): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024L * 1024L)
    }

    fun allSensors(): List<Sensor> = sm.getSensorList(Sensor.TYPE_ALL)
}

/** Pembaca langsung semua sensor untuk dialog "Sensor". Aktif hanya selama dialog terbuka. */
class SensorLive(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val values = LinkedHashMap<Sensor, FloatArray>()
    private val order = ArrayList<Sensor>()

    fun start() {
        for (s in sm.getSensorList(Sensor.TYPE_ALL)) {
            if (s.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) continue
            order.add(s)
            try {
                sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
            } catch (e: Exception) {
            }
        }
    }

    fun stop() {
        try {
            sm.unregisterListener(this)
        } catch (e: Exception) {
        }
    }

    fun render(): String {
        val sb = StringBuilder()
        for (s in order) {
            sb.append(s.name).append('\n')
            val v = values[s]
            if (v == null) {
                sb.append("   (menunggu data)\n")
            } else {
                sb.append("   ")
                val n = minOf(v.size, 3)
                for (i in 0 until n) {
                    sb.append(String.format(java.util.Locale.US, "%.2f", v[i]))
                    if (i < n - 1) sb.append(", ")
                }
                sb.append('\n')
            }
        }
        if (order.isEmpty()) sb.append("Tidak ada sensor yang terbaca.\n")
        return sb.toString()
    }

    override fun onSensorChanged(e: SensorEvent) {
        values[e.sensor] = e.values.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
