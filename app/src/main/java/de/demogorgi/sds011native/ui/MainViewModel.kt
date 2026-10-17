package de.demogorgi.sds011native.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.demogorgi.sds011native.model.AppModel
import de.demogorgi.sds011native.model.MeasurementMode
import de.demogorgi.sds011native.sds.Reading
import de.demogorgi.sds011native.sds.Sds011Protocol
import de.demogorgi.sds011native.sds.SensorConnection
import de.demogorgi.sds011native.rec.Recorder
import de.demogorgi.sds011native.settings.AppSettings
import de.demogorgi.sds011native.settings.SettingsStore
import de.demogorgi.sds011native.ui.state.LastSession
import de.demogorgi.sds011native.ui.state.MainUiState
import de.demogorgi.sds011native.ui.state.GpsStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel(), SensorConnection.Listener {

    private val appModel = AppModel()
    private var connection: SensorConnection = SensorConnection(this)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var recorder: Recorder? = null
    private var settingsStore: SettingsStore? = null
    @Volatile private var lastFixAt = 0L

    private val gps = GpsTracker { status, lat, lon ->
        if (status == GpsStatus.FIX) lastFixAt = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(gpsStatus = status, gpsLat = lat, gpsLon = lon) }
    }

    private var powerSaveActive = false
    private var sensorSleeping = false
    private var cycleStartedAt = 0L
    private var nextWakeAt = 0L

    init {
        appModel.setMeanListener { point ->
            val s = _uiState.value
            val fresh = s.gpsStatus == GpsStatus.FIX &&
                SystemClock.elapsedRealtime() - lastFixAt <= Recorder.GPS_MAX_AGE_MS
            recorder?.record(point, if (fresh) s.gpsLat else null, if (fresh) s.gpsLon else null)
        }
        viewModelScope.launch {
            while (true) {
                drivePowerSave()
                updateUiState()
                delay(1000L)
            }
        }
    }

    private var simulationJob: kotlinx.coroutines.Job? = null

    fun loadDevices(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val bondedDevices = if (adapter != null &&
            (Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        ) {
            try {
                adapter.bondedDevices?.map {
                    de.demogorgi.sds011native.ui.state.SensorDevice(
                        name = it.name ?: "Unbenanntes Gerät",
                        address = it.address,
                        isSimulated = false,
                        bluetoothDevice = it
                    )
                } ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val allDevices = bondedDevices + de.demogorgi.sds011native.ui.state.SensorDevice.SIMULATED
        _uiState.update { state ->
            state.copy(
                availableDevices = allDevices,
                selectedDevice = state.selectedDevice ?: allDevices.first(),
                errorMessage = null
            )
        }
    }

    fun selectDevice(device: de.demogorgi.sds011native.ui.state.SensorDevice) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun connectSelectedDevice(context: Context) {
        val device = _uiState.value.selectedDevice
        if (device == null) {
            _uiState.update { it.copy(errorMessage = "Bitte zuerst ein Gerät auswählen") }
            return
        }

        if (device.isSimulated) {
            connection.disconnect()
            simulationJob?.cancel()
            appModel.isConnected = true
            _uiState.update { it.copy(connectionStatus = "verbunden (simuliert)", isConnected = true, errorMessage = null) }
            applySensorInterval()

            simulationJob = viewModelScope.launch {
                var base25 = 12.4
                var base10 = 24.8
                while (isActive) {
                    if (!powerSaveActive || !sensorSleeping) {
                        base25 = (base25 + (Math.random() - 0.49) * 0.8).coerceIn(2.0, 85.0)
                        base10 = (base10 + (Math.random() - 0.49) * 1.4).coerceIn(5.0, 140.0)
                        handleReading(Reading(base25, base10, 0x5349))
                    }
                    delay(1000L)
                }
            }
            updateUiState()
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val btDev = device.bluetoothDevice
        if (btDev == null) {
            _uiState.update { it.copy(errorMessage = "Bluetooth-Gerät nicht verfügbar") }
            return
        }

        simulationJob?.cancel()
        simulationJob = null
        _uiState.update { it.copy(connectionStatus = "verbinde ...", errorMessage = null) }
        connection.connect(adapter, btDev)
    }

    fun disconnectSensor() {
        simulationJob?.cancel()
        simulationJob = null
        connection.write(Sds011Protocol.buildSleepCommand(false))
        connection.write(Sds011Protocol.buildWorkPeriodCommand(0))
        connection.disconnect()
        appModel.isConnected = false
        resetPowerSave()
        _uiState.update { it.copy(connectionStatus = "getrennt", isConnected = false) }
        updateUiState()
    }

    fun setRawChart(raw: Boolean) {
        _uiState.update { it.copy(rawChart = raw) }
        updateUiState()
    }

    fun setChartRange(rangeSeconds: Long) {
        _uiState.update { it.copy(chartRangeSeconds = rangeSeconds) }
        updateUiState()
    }

    fun clearPlot() {
        appModel.clearPlot()
        updateUiState()
    }

    fun setPendingMode(mode: MeasurementMode) {
        _uiState.update { it.copy(pendingMode = mode) }
        if (mode == MeasurementMode.STATIONARY && appModel.intervalSeconds < AppModel.POWER_SAVE_MIN_INTERVAL) {
            appModel.intervalSeconds = AppModel.POWER_SAVE_MIN_INTERVAL
            applySensorInterval()
            updateUiState()
        }
    }

    fun setTripName(name: String) {
        _uiState.update { it.copy(tripName = name.take(40)) }
    }

    fun setPlaceName(name: String) {
        _uiState.update { it.copy(placeName = name.take(40)) }
    }

    private var appContext: Context? = null

    fun loadSettings(context: Context) {
        appContext = context.applicationContext
        if (settingsStore != null) return
        val store = SettingsStore(context)
        settingsStore = store
        _uiState.update { it.copy(settings = store.load()) }
    }

    fun saveSettings(settings: AppSettings) {
        settingsStore?.save(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun startGps(context: Context) {
        gps.start(context)
    }

    fun setInterval(seconds: Int) {
        val effective = if (_uiState.value.mode != MeasurementMode.INACTIVE) _uiState.value.mode else _uiState.value.pendingMode
        if (effective == MeasurementMode.STATIONARY && seconds < AppModel.POWER_SAVE_MIN_INTERVAL) {
            _uiState.update { it.copy(errorMessage = "Stationär nur 5 oder 15 min") }
            return
        }
        appModel.intervalSeconds = seconds
        applySensorInterval()
        updateUiState()
    }

    fun startMeasurement(context: Context) {
        if (!appModel.isConnected) {
            _uiState.update { it.copy(errorMessage = "Erst Sensor verbinden") }
            return
        }
        val targetMode = _uiState.value.pendingMode
        if (targetMode == MeasurementMode.STATIONARY && appModel.intervalSeconds < AppModel.POWER_SAVE_MIN_INTERVAL) {
            appModel.intervalSeconds = AppModel.POWER_SAVE_MIN_INTERVAL
        }
        val settings = _uiState.value.settings
        if (targetMode == MeasurementMode.STATIONARY && settings.uploadEnabled) {
            val s = _uiState.value
            val fresh = s.gpsStatus == GpsStatus.FIX &&
                SystemClock.elapsedRealtime() - lastFixAt <= Recorder.GPS_MAX_AGE_MS
            if (fresh && s.gpsLat != null && s.gpsLon != null) {
                val dist = settings.distanceTo(s.gpsLat, s.gpsLon)
                if (dist > settings.maxDistanceM) {
                    _uiState.update {
                        it.copy(errorMessage = "Stationär nur am Sensorstandort: ${dist.toInt()} m entfernt (max. ${settings.maxDistanceM} m)")
                    }
                    return
                }
            }
        }
        val rec = recorder ?: run {
            val dir = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "Feinstaub")
            Recorder(
                dir,
                onUpload = { info -> _uiState.update { it.copy(uploadInfo = info) } },
                onError = { msg -> _uiState.update { it.copy(errorMessage = msg) } }
            ).also {
                recorder = it
                _uiState.update { s -> s.copy(outDirPath = dir.absolutePath) }
            }
        }
        val name = when (targetMode) {
            MeasurementMode.TRIP -> _uiState.value.tripName
            MeasurementMode.LOCAL -> _uiState.value.placeName
            else -> ""
        }
        val isSimulated = _uiState.value.selectedDevice?.isSimulated == true
        if (targetMode == MeasurementMode.STATIONARY && isSimulated) {
            _uiState.update { it.copy(errorMessage = "Simulation: Öffentlicher Upload zu sensor.community deaktiviert") }
        }
        rec.sensorId = settings.sensorId
        rec.uploadEnabled = settings.uploadEnabled && !isSimulated
        rec.start(targetMode, name)
        _uiState.update { it.copy(uploadInfo = null) }
        appModel.mode = targetMode
        applySensorInterval()
        updateUiState()
    }

    fun stopMeasurement() {
        val previous = appModel.mode
        if (previous != MeasurementMode.INACTIVE) {
            val now = System.currentTimeMillis()
            val summary = LastSession(
                mode = previous,
                endedAtMillis = now,
                durationSeconds = appModel.getSessionElapsedSeconds(now),
                points = appModel.sessionPoints,
                avg25 = appModel.sessionAvg25,
                avg10 = appModel.sessionAvg10,
                fileName = recorder?.currentFileName
            )
            _uiState.update { it.copy(lastSession = summary) }
        }
        recorder?.stop()
        appModel.mode = MeasurementMode.INACTIVE
        updateUiState()
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onConnected() {
        viewModelScope.launch {
            appModel.isConnected = true
            _uiState.update { it.copy(connectionStatus = "verbunden", isConnected = true) }
            applySensorInterval()
            updateUiState()
        }
    }

    override fun onReading(reading: Reading) {
        viewModelScope.launch {
            handleReading(reading)
        }
    }

    override fun onDisconnected(message: String?) {
        viewModelScope.launch {
            val activeMode = appModel.mode
            val wasRecording = activeMode != MeasurementMode.INACTIVE
            appModel.isConnected = false
            resetPowerSave()
            val failed = message != null && message != "getrennt"

            if (wasRecording && failed) {
                appContext?.let { ctx ->
                    de.demogorgi.sds011native.util.NotificationHelper.showConnectionLostAlert(ctx, activeMode.label)
                }
            }

            _uiState.update {
                it.copy(
                    connectionStatus = if (failed) "Verbindung verloren" else "getrennt",
                    isConnected = false,
                    errorMessage = if (failed) message else it.errorMessage
                )
            }
            updateUiState()
        }
    }

    private fun handleReading(reading: Reading) {
        val now = SystemClock.elapsedRealtime()
        if (powerSaveActive && cycleStartedAt == 0L) {
            cycleStartedAt = now
        }
        val usable = !powerSaveActive || (cycleStartedAt > 0L && now - cycleStartedAt >= AppModel.POWER_SAVE_SETTLE_MS)
        appModel.addReading(reading.pm25, reading.pm10, usable)

        if (powerSaveActive && cycleStartedAt > 0L && now - cycleStartedAt >= AppModel.POWER_SAVE_SETTLE_MS + AppModel.POWER_SAVE_SAMPLE_WINDOW_MS) {
            sensorSleeping = true
            nextWakeAt = cycleStartedAt + appModel.intervalSeconds * 1000L
            cycleStartedAt = 0L
            connection.write(Sds011Protocol.buildSleepCommand(true))
        }
        updateUiState()
    }

    private fun applySensorInterval() {
        if (!appModel.isConnected) {
            resetPowerSave()
            return
        }
        val interval = appModel.intervalSeconds
        if (interval >= AppModel.POWER_SAVE_MIN_INTERVAL) {
            if (!powerSaveActive) {
                powerSaveActive = true
                sensorSleeping = false
                cycleStartedAt = 0L
                nextWakeAt = 0L
            }
            connection.write(Sds011Protocol.buildWorkPeriodCommand(0))
            connection.write(Sds011Protocol.buildSleepCommand(false))
        } else {
            resetPowerSave()
            connection.write(Sds011Protocol.buildSleepCommand(false))
            connection.write(Sds011Protocol.buildWorkPeriodCommand(0))
        }
    }

    private fun drivePowerSave() {
        if (!powerSaveActive || !sensorSleeping || !appModel.isConnected) return
        val now = SystemClock.elapsedRealtime()
        if (nextWakeAt > 0L && now >= nextWakeAt) {
            sensorSleeping = false
            cycleStartedAt = 0L
            nextWakeAt = 0L
            connection.write(Sds011Protocol.buildSleepCommand(false))
        }
    }

    private fun resetPowerSave() {
        powerSaveActive = false
        sensorSleeping = false
        cycleStartedAt = 0L
        nextWakeAt = 0L
    }

    private fun updateUiState() {
        val now = System.currentTimeMillis()
        val history = appModel.history(_uiState.value.rawChart, _uiState.value.chartRangeSeconds)

        _uiState.update { state ->
            state.copy(
                isConnected = appModel.isConnected,
                currentPm25 = if (appModel.currentPm25 > 0.0) appModel.currentPm25 else null,
                currentPm10 = if (appModel.currentPm10 > 0.0) appModel.currentPm10 else null,
                lastReadingAgeSeconds = appModel.getCurrentAgeSeconds(now),
                sensorPauseSeconds = if (powerSaveActive && sensorSleeping && nextWakeAt > 0L)
                    maxOf(0L, (nextWakeAt - SystemClock.elapsedRealtime()) / 1000L) else null,
                historyPoints = history,
                mode = appModel.mode,
                intervalSeconds = appModel.intervalSeconds,
                sessionPoints = appModel.sessionPoints,
                sessionElapsedSeconds = appModel.getSessionElapsedSeconds(now),
                sessionAvg25 = appModel.sessionAvg25,
                sessionAvg10 = appModel.sessionAvg10,
                sessionFile = recorder?.currentFileName
            )
        }
    }

    override fun onCleared() {
        disconnectSensor()
        gps.stop()
        super.onCleared()
    }
}
