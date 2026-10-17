package de.demogorgi.sds011native.rec

import android.content.Context
import de.demogorgi.sds011native.R
import de.demogorgi.sds011native.model.HistoryPoint
import de.demogorgi.sds011native.model.MeasurementMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.floor

data class UploadInfo(val atMillis: Long, val ok: Boolean, val message: String)

/**
 * Schreibt Mittelwerte als CSV (alle Modi) und KML-Spuren (Messfahrt) und
 * lädt im stationären Modus zu sensor.community hoch. Dateiformat wie die
 * Python-Version.
 */
class Recorder(
    private val context: Context,
    private val outDir: File,
    private val onUpload: (UploadInfo) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        const val UPLOAD_URL = "https://api.luftdaten.info/v1/push-sensor-data/"
        const val GPS_MAX_AGE_MS = 10_000L
        private const val CSV_HEADER = "Zeit;PM2.5;PM10;Breite;Laenge"

        fun slugify(text: String, maxLen: Int = 40): String {
            var v = text.trim().lowercase(Locale.ROOT)
            listOf("ä" to "ae", "ö" to "oe", "ü" to "ue", "ß" to "ss").forEach { v = v.replace(it.first, it.second) }
            val sb = StringBuilder()
            for (c in v) {
                if (c in 'a'..'z' || c in '0'..'9') sb.append(c)
                else if (c == ' ' || c == '-' || c == '_' || c == '.') sb.append('_')
            }
            var slug = sb.toString()
            while (slug.contains("__")) slug = slug.replace("__", "_")
            return slug.trim('_').take(maxLen)
        }

        // KML-Farbe (aaBBGGRR): grün bis 25 µg/m³ über gelb nach rot ab 50
        fun kmlColor(value: Double): String {
            if (value >= 50.0) return "C80000FF"
            if (value < 0.0) return "C8FFFFFF"
            val idx = floor(value / (50.0 / 34.0)).toInt().coerceIn(0, 33)
            val r: Int
            val g: Int
            if (idx <= 16) {
                r = idx * 15; g = 255
            } else {
                r = 255; g = 255 - (idx - 17) * 15
            }
            return String.format(Locale.ROOT, "C800%02X%02X", g, r)
        }
    }

    @Volatile var sensorId: String = ""
    @Volatile var uploadEnabled: Boolean = true

    private val io = Executors.newSingleThreadExecutor()

    private var mode = MeasurementMode.INACTIVE
    private var csv: File? = null
    private var kml25: File? = null
    private var kml10: File? = null
    private var latOld: Double? = null
    private var lonOld: Double? = null
    private var pmOld25 = ""
    private var pmOld10 = ""

    @Volatile
    var currentFileName: String? = null
        private set

    @Synchronized
    fun start(newMode: MeasurementMode, name: String) {
        val stamp = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.ROOT).format(Date())
        val slug = slugify(name)
        val pre = "feinstaub_"
        outDir.mkdirs()
        mode = newMode
        latOld = null
        lonOld = null
        kml25 = null
        kml10 = null
        val csvName = when (newMode) {
            MeasurementMode.TRIP -> {
                val stem = if (slug.isNotEmpty()) "$pre${slug}_" else pre
                kml25 = File(outDir, "${stem}25_line_$stamp.kml")
                kml10 = File(outDir, "${stem}10_line_$stamp.kml")
                "$stem$stamp.csv"
            }
            MeasurementMode.LOCAL -> if (slug.isNotEmpty()) "$pre${slug}_$stamp.csv" else "${pre}lokal_$stamp.csv"
            else -> "${pre}stationaer_$stamp.csv"
        }
        csv = File(outDir, csvName)
        currentFileName = csvName
    }

    @Synchronized
    fun stop() {
        val a = kml25
        val b = kml10
        io.execute {
            listOf(a, b).forEach { f ->
                if (f != null && f.exists()) {
                    try {
                        f.appendText("  </Document>\n</kml>\n")
                    } catch (e: Exception) {
                        onError(context.getString(R.string.recorder_kml_error, e.message.orEmpty()))
                    }
                }
            }
        }
        mode = MeasurementMode.INACTIVE
        csv = null
        kml25 = null
        kml10 = null
    }

    /** lat/lon sind null ohne aktuellen GPS-Fix. */
    @Synchronized
    fun record(point: HistoryPoint, lat: Double?, lon: Double?) {
        val file = csv ?: return
        val currentMode = mode
        val t25 = String.format(Locale.ROOT, "%.1f", point.pm25)
        val t10 = String.format(Locale.ROOT, "%.1f", point.pm10)
        val k25 = kml25
        val k10 = kml10

        var oldLat = latOld
        var oldLon = lonOld
        var prev25 = pmOld25
        var prev10 = pmOld10
        val trackable = lat != null && lon != null && currentMode == MeasurementMode.TRIP
        if (lat == null || lon == null) {
            latOld = null
            lonOld = null
        } else if (trackable) {
            if (oldLat == null || oldLon == null) {
                oldLat = lat; oldLon = lon; prev25 = t25; prev10 = t10
            }
            latOld = lat; lonOld = lon; pmOld25 = t25; pmOld10 = t10
        }

        io.execute {
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(point.epochMillis))
                if (trackable && lat != null && lon != null && oldLat != null && oldLon != null && k25 != null && k10 != null) {
                    writePlacemark(k25, "25", t25, prev25, lat, lon, oldLat, oldLon, kmlColor(point.pm25))
                    writePlacemark(k10, "10", t10, prev10, lat, lon, oldLat, oldLon, kmlColor(point.pm10))
                }
                val isNew = !file.exists() || file.length() == 0L
                val line = "$time;$t25;$t10;${if (lat != null) coord(lat) else ""};${if (lon != null) coord(lon) else ""}"
                    .replace(".", ",")
                file.appendText((if (isNew) CSV_HEADER + "\n" else "") + line + "\n")
            } catch (e: Exception) {
                onError(context.getString(R.string.recorder_file_error, e.message.orEmpty()))
            }
        }
        if (currentMode == MeasurementMode.STATIONARY && uploadEnabled) {
            io.execute { upload(t25, t10) }
        }
    }

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)

    private fun writePlacemark(
        f: File, type: String, pm: String, pmOld: String,
        lat: Double, lon: Double, latOld: Double, lonOld: Double, color: String
    ) {
        val sb = StringBuilder()
        if (!f.exists()) {
            sb.append("<?xml version='1.0' encoding='UTF-8'?>\n")
            sb.append("<kml xmlns='http://earth.google.com/kml/2.1'>\n<Document>\n")
            sb.append("   <name> Feinstaub_Linie_${type}_${SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())}.kml </name>\n\n")
        }
        val here = "${coord(lon)},${coord(lat)},$pm"
        val before = "${coord(lonOld)},${coord(latOld)},$pmOld"
        sb.append("   <Placemark>\n   <name>$pm</name>\n    <description>$pm</description>\n")
        sb.append("    <Point>\n      <coordinates>$here</coordinates>\n    </Point>\n")
        sb.append("       <LineString>\n           <altitudeMode>relativeToGround</altitudeMode>\n")
        sb.append("           <coordinates>$here\n           $before</coordinates>\n       </LineString>\n")
        sb.append("       <Style>\n           <LineStyle>\n               <color>$color</color>\n")
        sb.append("               <width>8</width>\n           </LineStyle>\n       </Style>\n   </Placemark>\n")
        f.appendText(sb.toString())
    }

    private fun upload(t25: String, t10: String) {
        val body = JSONObject()
            .put("software_version", "SDS011-Android")
            .put(
                "sensordatavalues", JSONArray()
                    .put(JSONObject().put("value_type", "P1").put("value", t10))
                    .put(JSONObject().put("value_type", "P2").put("value", t25))
            ).toString()
        val info = try {
            val conn = URL(UPLOAD_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Pin", "1")
            conn.setRequestProperty("X-Sensor", sensorId)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            if (code == 201) UploadInfo(System.currentTimeMillis(), true, context.getString(R.string.upload_success, t25, t10))
            else UploadInfo(System.currentTimeMillis(), false, context.getString(R.string.upload_failed_response, code))
        } catch (e: Exception) {
            val errReason = when {
                e is java.net.UnknownHostException -> context.getString(R.string.upload_no_internet)
                e is java.net.SocketTimeoutException -> context.getString(R.string.upload_timeout)
                else -> context.getString(R.string.upload_server_unavailable)
            }
            UploadInfo(System.currentTimeMillis(), false, context.getString(R.string.upload_failed, errReason))
        }
        onUpload(info)
    }
}
