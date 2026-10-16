package de.demogorgi.sds011native

import android.Manifest
import android.content.pm.PackageManager
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

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (btGranted) {
            viewModel.loadDevices(this)
        }
        if (locGranted) {
            viewModel.startGps(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.loadSettings(this)
        requestAppPermissions()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when (state.settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            SDS011Theme(darkTheme = dark) {
                MainScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { requestAppPermissions() }
                )
            }
        }
    }

    fun requestAppPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.loadDevices(this)
            viewModel.startGps(this)
        }
    }
}
