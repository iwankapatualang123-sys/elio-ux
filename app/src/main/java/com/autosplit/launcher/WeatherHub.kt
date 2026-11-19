package com.autosplit.launcher

import android.os.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Mengambil cuaca real-time ringan dari Open-Meteo (tanpa API key) berdasarkan koordinat GPS asli.
 * Hanya memperbarui setiap 15 menit pada thread latar belakang agar tidak membebani UI.
 */
class WeatherHub {

    @Volatile var hasData: Boolean = false
        private set
    @Volatile var tempC: Int = 0
        private set
    @Volatile var kind: String = "partly" // sunny | partly | rain | storm
        private set
    @Volatile var label: String = "Cuaca --"
        private set

    private var lastFetchMs = 0L
    @Volatile private var fetching = false

    fun updateIfNeeded(lat: Double, lon: Double) {
        if (lat.isNaN() || lon.isNaN()) return
        val now = SystemClock.elapsedRealtime()
        val interval = if (hasData) 15 * 60_000L else 60_000L
        if (fetching || (lastFetchMs != 0L && now - lastFetchMs < interval)) return
        fetching = true
        lastFetchMs = now

        val urlStr = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
            lat, lon
        )
        Thread {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "ElioUX/2.0")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val cur = JSONObject(body).optJSONObject("current")
                    if (cur != null && cur.has("temperature_2m")) {
                        val t = cur.optDouble("temperature_2m", Double.NaN)
                        val code = cur.optInt("weather_code", 2)
                        if (!t.isNaN()) {
                            tempC = t.roundToInt()
                            val (k, lbl) = mapWmo(code)
                            kind = k
                            label = lbl
                            hasData = true
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Offline atau jaringan lambat: diam tanpa mengganggu launcher
            } finally {
                fetching = false
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun mapWmo(code: Int): Pair<String, String> = when (code) {
        0 -> "sunny" to "Cerah"
        1 -> "sunny" to "Cerah"
        2, 3 -> "partly" to "Berawan"
        45, 48 -> "partly" to "Berkabut"
        in 51..57 -> "rain" to "Gerimis"
        in 61..67, in 80..82 -> "rain" to "Hujan"
        in 95..99 -> "storm" to "Badai"
        else -> "partly" to "Berawan"
    }
}
