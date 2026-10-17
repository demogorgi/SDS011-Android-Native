package de.demogorgi.sds011native.ui.state

import android.bluetooth.BluetoothDevice
import de.demogorgi.sds011native.model.HistoryPoint
import de.demogorgi.sds011native.model.MeasurementMode
import de.demogorgi.sds011native.rec.UploadInfo
import de.demogorgi.sds011native.settings.AppSettings

data class SensorDevice(
    val name: String,
    val address: String,
    val isSimulated: Boolean = false,
    val bluetoothDevice: BluetoothDevice? = null
) {
    val displayLabel: String
        get() = name

    companion object {
        val SIMULATED = SensorDevice(
            name = "SDS011 simuliert",
            address = "00:00:00:00:00:00",
            isSimulated = true
        )
    }
}

enum class AqiLevel(val label: String) {
    NONE("kein Wert"),
    GOOD("gut"),
    ELEVATED("erhöht"),
    HIGH("hoch");

    companion object {
        fun fromPm25(value: Double?): AqiLevel {
            if (value == null) return NONE
            return when {
                value < 25.0 -> GOOD
                value <= 50.0 -> ELEVATED
                else -> HIGH
            }
        }

        fun fromPm10(value: Double?): AqiLevel {
            if (value == null) return NONE
            return when {
                value < 40.0 -> GOOD
                value <= 50.0 -> ELEVATED
                else -> HIGH
            }
        }
    }
}

data class LastSession(
    val mode: MeasurementMode,
    val endedAtMillis: Long,
    val durationSeconds: Long,
    val points: Int,
    val avg25: Double,
    val avg10: Double,
    val fileName: String?
)

enum class GpsStatus { NONE, SEARCHING, FIX }

data class MainUiState(
    val connectionStatus: String = "getrennt",
    val isConnected: Boolean = false,
    val selectedDevice: SensorDevice? = SensorDevice.SIMULATED,
    val availableDevices: List<SensorDevice> = listOf(SensorDevice.SIMULATED),

    val currentPm25: Double? = null,
    val currentPm10: Double? = null,
    val lastReadingAgeSeconds: Long = -1L,
    val sensorPauseSeconds: Long? = null,

    val historyPoints: List<HistoryPoint> = emptyList(),
    val rawChart: Boolean = false,
    val chartRangeSeconds: Long = 0L, // 0 = Alles

    val mode: MeasurementMode = MeasurementMode.INACTIVE,
    val pendingMode: MeasurementMode = MeasurementMode.INACTIVE,
    val intervalSeconds: Int = 5,
    val sessionPoints: Int = 0,
    val sessionElapsedSeconds: Long = 0L,

    val sessionAvg25: Double = 0.0,
    val sessionAvg10: Double = 0.0,
    val sessionFile: String? = null,
    val uploadInfo: UploadInfo? = null,
    val lastSession: LastSession? = null,
    val outDirPath: String = "",

    val settings: AppSettings = AppSettings(),
    val showSettings: Boolean = false,
    val showAbout: Boolean = false,

    val tripName: String = "",
    val placeName: String = "",
    val gpsStatus: GpsStatus = GpsStatus.NONE,
    val gpsLat: Double = 0.0,
    val gpsLon: Double = 0.0,

    val errorMessage: String? = null
) {
    val pm25Level: AqiLevel
        get() = AqiLevel.fromPm25(currentPm25)

    val intervalChoices: List<Int>
        get() {
            val effective = if (mode != MeasurementMode.INACTIVE) mode else pendingMode
            return if (effective == MeasurementMode.STATIONARY) listOf(300, 900)
            else listOf(1, 5, 10, 30, 60, 300, 900)
        }

    val pm10Level: AqiLevel
        get() = AqiLevel.fromPm10(currentPm10)
}
