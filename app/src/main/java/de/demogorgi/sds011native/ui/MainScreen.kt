package de.demogorgi.sds011native.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import de.demogorgi.sds011native.model.MeasurementMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.demogorgi.sds011native.ui.state.GpsStatus
import de.demogorgi.sds011native.ui.components.ChartCard
import de.demogorgi.sds011native.ui.components.LiveValuesCard
import de.demogorgi.sds011native.ui.components.MeasurementCard
import de.demogorgi.sds011native.ui.components.SensorCard
import de.demogorgi.sds011native.ui.components.TopBar

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadSettings(context)
        viewModel.loadDevices(context)
    }

    if (uiState.showSettings) {
        SettingsScreen(
            settings = uiState.settings,
            gpsLat = if (uiState.gpsStatus == GpsStatus.FIX) uiState.gpsLat else null,
            gpsLon = if (uiState.gpsStatus == GpsStatus.FIX) uiState.gpsLon else null,
            onSave = { viewModel.saveSettings(it) },
            onBack = { viewModel.showSettings(false) }
        )
        return
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopBar(
                statusText = uiState.connectionStatus,
                isConnected = uiState.isConnected,
                onSettingsClick = { viewModel.showSettings(true) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            SensorCard(
                availableDevices = uiState.availableDevices,
                selectedDevice = uiState.selectedDevice,
                isConnected = uiState.isConnected,
                connectionStatus = uiState.connectionStatus,
                onDeviceSelected = { viewModel.selectDevice(it) },
                onReloadDevices = { viewModel.loadDevices(context) },
                onConnect = { viewModel.connectSelectedDevice(context) },
                onDisconnect = { viewModel.disconnectSensor() }
            )

            LiveValuesCard(
                pm25 = uiState.currentPm25,
                pm10 = uiState.currentPm10,
                pm25Level = uiState.pm25Level,
                pm10Level = uiState.pm10Level,
                readingAgeSeconds = uiState.lastReadingAgeSeconds,
                sensorPauseSeconds = uiState.sensorPauseSeconds,
                isConnected = uiState.isConnected,
                gpsStatus = uiState.gpsStatus,
                gpsLat = uiState.gpsLat,
                gpsLon = uiState.gpsLon
            )

            ChartCard(
                historyPoints = uiState.historyPoints,
                rawChart = uiState.rawChart,
                chartRangeSeconds = uiState.chartRangeSeconds,
                intervalSeconds = uiState.intervalSeconds,
                onRawChartChange = { viewModel.setRawChart(it) },
                onRangeChange = { viewModel.setChartRange(it) },
                onClearPlot = { viewModel.clearPlot() }
            )

            MeasurementCard(
                currentMode = uiState.mode,
                pendingMode = uiState.pendingMode,
                intervalSeconds = uiState.intervalSeconds,
                intervalChoices = uiState.intervalChoices,
                sessionPoints = uiState.sessionPoints,
                sessionElapsedSeconds = uiState.sessionElapsedSeconds,
                sessionAvg25 = uiState.sessionAvg25,
                sessionAvg10 = uiState.sessionAvg10,
                sessionFile = uiState.sessionFile,
                uploadInfo = uiState.uploadInfo,
                lastSession = uiState.lastSession,
                outDirPath = uiState.outDirPath,
                isConnected = uiState.isConnected,
                tripName = uiState.tripName,
                placeName = uiState.placeName,
                onPendingModeChange = { viewModel.setPendingMode(it) },
                onTripNameChange = { viewModel.setTripName(it) },
                onPlaceNameChange = { viewModel.setPlaceName(it) },
                onIntervalChange = { viewModel.setInterval(it) },
                onStart = { viewModel.startMeasurement(context) },
                onStop = { viewModel.stopMeasurement() }
            )
        }
    }
}
