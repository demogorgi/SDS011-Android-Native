package de.demogorgi.sds011native.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.demogorgi.sds011native.ui.state.SensorDevice
import de.demogorgi.sds011native.ui.theme.AqiGood
import de.demogorgi.sds011native.ui.theme.BrandTeal

@Composable
fun SensorCard(
    availableDevices: List<SensorDevice>,
    selectedDevice: SensorDevice?,
    isConnected: Boolean,
    connectionStatus: String,
    onDeviceSelected: (SensorDevice) -> Unit,
    onReloadDevices: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connecting = connectionStatus.contains("verbinde", ignoreCase = true)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isConnected || connecting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BluetoothBadge(connected = isConnected, connecting = connecting)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedDevice?.name ?: "SDS011 Sensor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = if (isConnected) androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.status_connected)
                            else androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.status_connecting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isConnected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.action_disconnect))
                        }
                    }
                }
            } else {
                var expanded by remember { mutableStateOf(false) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedDevice?.name ?: "SDS011 simuliert",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.label_select_device)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Gerät wählen",
                                    modifier = Modifier.clickable { expanded = true }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            availableDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = device.name,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        onDeviceSelected(device)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onReloadDevices) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Geräte neu laden"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onConnect,
                    enabled = selectedDevice != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.action_connect),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BluetoothBadge(connected: Boolean, connecting: Boolean, modifier: Modifier = Modifier) {
    val tint = if (connected) AqiGood else MaterialTheme.colorScheme.onSurfaceVariant
    val transition = rememberInfiniteTransition(label = "btSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "btAngle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "btPulse"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (connected) AqiGood.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        BluetoothGlyph(
            color = tint,
            modifier = Modifier
                .size(24.dp)
                .alpha(if (connected && !connecting) pulse.coerceAtLeast(0.7f) else 1f)
        )
        if (connecting) {
            Canvas(modifier = Modifier.size(44.dp).rotate(angle)) {
                drawArc(
                    color = BrandTeal,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun BluetoothGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val k = size.width / 24f
        val path = Path().apply {
            moveTo(7f * k, 7f * k)
            lineTo(17f * k, 17f * k)
            lineTo(12f * k, 22f * k)
            lineTo(12f * k, 2f * k)
            lineTo(17f * k, 7f * k)
            lineTo(7f * k, 17f * k)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
