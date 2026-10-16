package de.demogorgi.sds011native.settings

import android.content.Context
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AppSettings(
    val sensorId: String = "raspi-00000000a5c85ba8",
    val sensorLat: Double = 51.439947,
    val sensorLon: Double = 6.781089,
    val maxDistanceM: Int = 500,
    val uploadEnabled: Boolean = true,
    val themeMode: Int = THEME_SYSTEM
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }

    fun distanceTo(lat: Double, lon: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(sensorLat)
        val p2 = Math.toRadians(lat)
        val dp = p2 - p1
        val dl = Math.toRadians(lon - sensorLon)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sensorId = prefs.getString("sensorId", d.sensorId) ?: d.sensorId,
            sensorLat = prefs.getString("lat", null)?.toDoubleOrNull() ?: d.sensorLat,
            sensorLon = prefs.getString("lon", null)?.toDoubleOrNull() ?: d.sensorLon,
            maxDistanceM = prefs.getInt("maxDistanceM", d.maxDistanceM),
            uploadEnabled = prefs.getBoolean("uploadEnabled", d.uploadEnabled),
            themeMode = prefs.getInt("themeMode", d.themeMode)
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("sensorId", s.sensorId)
            .putString("lat", s.sensorLat.toString())
            .putString("lon", s.sensorLon.toString())
            .putInt("maxDistanceM", s.maxDistanceM)
            .putBoolean("uploadEnabled", s.uploadEnabled)
            .putInt("themeMode", s.themeMode)
            .apply()
    }
}
