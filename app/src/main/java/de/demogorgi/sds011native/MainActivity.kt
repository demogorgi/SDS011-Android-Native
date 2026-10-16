package de.demogorgi.sds011native

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.demogorgi.sds011native.settings.AppSettings
import de.demogorgi.sds011native.ui.MainScreen
import de.demogorgi.sds011native.ui.MainViewModel
import de.demogorgi.sds011native.ui.theme.SDS011Theme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadDevices(this)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startGps(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.loadSettings(this)
        checkBluetoothPermissions()
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when (state.settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            SDS011Theme(darkTheme = dark) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.loadDevices(this)
        }
    }
}
