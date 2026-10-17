package de.demogorgi.sds011native.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.demogorgi.sds011native.R
import de.demogorgi.sds011native.settings.AppSettings
import de.demogorgi.sds011native.ui.components.SegmentedChoice
import java.util.Locale

private fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
private fun fmt(v: Double) = String.format(Locale.ROOT, "%.6f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    gpsLat: Double?,
    gpsLon: Double?,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var sensorId by remember { mutableStateOf(settings.sensorId) }
    var lat by remember { mutableStateOf(fmt(settings.sensorLat)) }
    var lon by remember { mutableStateOf(fmt(settings.sensorLon)) }
    var maxDist by remember { mutableStateOf(settings.maxDistanceM.toString()) }
    var upload by remember { mutableStateOf(settings.uploadEnabled) }

    val latV = parse(lat)
    val lonV = parse(lon)
    val distV = maxDist.trim().toIntOrNull()
    val latOk = latV != null && latV in -90.0..90.0
    val lonOk = lonV != null && lonV in -180.0..180.0
    val distOk = distV != null && distV in 10..100_000
    val idOk = sensorId.isNotBlank()
    val valid = latOk && lonOk && distOk && idOk

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    SegmentedChoice(
                        options = listOf(
                            AppSettings.THEME_SYSTEM to stringResource(R.string.theme_system),
                            AppSettings.THEME_LIGHT to stringResource(R.string.theme_light),
                            AppSettings.THEME_DARK to stringResource(R.string.theme_dark)
                        ),
                        selected = settings.themeMode,
                        onSelected = { onSave(settings.copy(themeMode = it)) }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    SegmentedChoice(
                        options = listOf(
                            AppSettings.LANG_SYSTEM to stringResource(R.string.lang_system),
                            AppSettings.LANG_DE to stringResource(R.string.lang_de),
                            AppSettings.LANG_EN to stringResource(R.string.lang_en)
                        ),
                        selected = settings.languageMode,
                        onSelected = { onSave(settings.copy(languageMode = it)) }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_stationary_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        stringResource(R.string.settings_stationary_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_upload_label), fontWeight = FontWeight.Medium)
                            Text(
                                if (upload) stringResource(R.string.settings_upload_enabled_desc) else stringResource(R.string.settings_upload_disabled_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = upload, onCheckedChange = { upload = it })
                    }

                    OutlinedTextField(
                        value = sensorId,
                        onValueChange = { sensorId = it.trim().take(60) },
                        label = { Text(stringResource(R.string.settings_sensor_id)) },
                        isError = !idOk,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(stringResource(R.string.settings_sensor_location), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lat,
                            onValueChange = { lat = it },
                            label = { Text(stringResource(R.string.settings_latitude)) },
                            isError = !latOk,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lon,
                            onValueChange = { lon = it },
                            label = { Text(stringResource(R.string.settings_longitude)) },
                            isError = !lonOk,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (gpsLat != null && gpsLon != null) {
                                lat = fmt(gpsLat)
                                lon = fmt(gpsLon)
                            }
                        },
                        enabled = gpsLat != null && gpsLon != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (gpsLat != null) stringResource(R.string.settings_use_current_gps) else stringResource(R.string.settings_waiting_gps))
                    }

                    OutlinedTextField(
                        value = maxDist,
                        onValueChange = { maxDist = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.settings_max_dist_label)) },
                        supportingText = { Text(stringResource(R.string.settings_max_dist_help)) },
                        isError = !distOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    onSave(settings.copy(sensorId = sensorId.trim(), sensorLat = latV!!, sensorLon = lonV!!, maxDistanceM = distV!!, uploadEnabled = upload))
                    onBack()
                },
                enabled = valid,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.settings_action_save), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
