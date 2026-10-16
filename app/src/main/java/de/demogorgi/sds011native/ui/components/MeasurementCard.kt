package de.demogorgi.sds011native.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.demogorgi.sds011native.model.MeasurementMode
import de.demogorgi.sds011native.rec.UploadInfo
import de.demogorgi.sds011native.ui.state.LastSession
import de.demogorgi.sds011native.ui.theme.AqiElevated
import de.demogorgi.sds011native.ui.theme.AqiHigh
import de.demogorgi.sds011native.util.FileSharing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementCard(
    currentMode: MeasurementMode,
    pendingMode: MeasurementMode,
    intervalSeconds: Int,
    intervalChoices: List<Int>,
    sessionPoints: Int,
    sessionElapsedSeconds: Long,
    sessionAvg25: Double,
    sessionAvg10: Double,
    sessionFile: String?,
    uploadInfo: UploadInfo?,
    lastSession: LastSession?,
    outDirPath: String,
    isConnected: Boolean,
    tripName: String,
    placeName: String,
    onPendingModeChange: (MeasurementMode) -> Unit,
    onTripNameChange: (String) -> Unit,
    onPlaceNameChange: (String) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = currentMode != MeasurementMode.INACTIVE
    val shownMode = if (isRecording) currentMode else pendingMode
    var showFilesDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Messauftrag",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedButton(
                    onClick = { showFilesDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dateien", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (isRecording) {
                RunningPanel(currentMode, sessionElapsedSeconds, sessionPoints, sessionAvg25, sessionAvg10, sessionFile, uploadInfo)
            } else {
                if (lastSession != null) {
                    LastSessionPanel(lastSession, outDirPath)
                    Spacer(Modifier.height(12.dp))
                }
                ModeCard(
                    title = "Beobachten",
                    description = "Nur anzeigen und zeichnen, nichts wird gespeichert",
                    icon = Icons.Default.Info,
                    selected = pendingMode == MeasurementMode.INACTIVE,
                    onClick = { onPendingModeChange(MeasurementMode.INACTIVE) }
                ) {}
                Spacer(Modifier.height(8.dp))
                ModeCard(
                    title = "Messfahrt",
                    description = "Unterwegs: GPS-Spur (KML) und CSV",
                    icon = Icons.Default.Send,
                    selected = pendingMode == MeasurementMode.TRIP,
                    onClick = { onPendingModeChange(MeasurementMode.TRIP) }
                ) {
                    NameField("Name der Messfahrt", "z. B. Von Essen nach Duisburg", tripName, onTripNameChange,
                        "Erscheint im Dateinamen. Ohne Name heißen die Dateien nur nach Zeitstempel.")
                }
                Spacer(Modifier.height(8.dp))
                ModeCard(
                    title = "Lokal",
                    description = "An einem Ort: CSV mit Ortsname",
                    icon = Icons.Default.Place,
                    selected = pendingMode == MeasurementMode.LOCAL,
                    onClick = { onPendingModeChange(MeasurementMode.LOCAL) }
                ) {
                    NameField("Ort", "z. B. Kletterhalle", placeName, onPlaceNameChange,
                        "Erscheint im Dateinamen. Ohne Ort heißt die Datei „lokal“.")
                }
                Spacer(Modifier.height(8.dp))
                ModeCard(
                    title = "Stationär",
                    badge = "öffentlich",
                    description = "Upload zu sensor.community mit jedem neuen Mittelwert, dazu CSV",
                    icon = Icons.Default.Home,
                    selected = pendingMode == MeasurementMode.STATIONARY,
                    onClick = { onPendingModeChange(MeasurementMode.STATIONARY) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AqiElevated.copy(alpha = 0.15f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Öffentlicher Upload",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Jeder neue Mittelwert geht an sensor.community und erscheint dort. " +
                                    "Nur am vereinbarten Standort starten. Dafür sind nur 5 oder 15 min wählbar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Mittelwert-Takt",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            SegmentedChoice(
                options = intervalChoices.map { it to (if (it >= 60) "${it / 60}m" else "${it}s") },
                selected = intervalSeconds,
                onSelected = onIntervalChange
            )
            Text(
                text = when (shownMode) {
                    MeasurementMode.INACTIVE -> "Alle ${formatInterval(intervalSeconds)} wird gemittelt und gezeichnet."
                    MeasurementMode.STATIONARY -> "Alle ${formatInterval(intervalSeconds)} wird gemittelt, gezeichnet, gespeichert und gesendet."
                    else -> "Alle ${formatInterval(intervalSeconds)} wird gemittelt, gezeichnet und gespeichert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))
            if (isRecording) {
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AqiHigh, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                    Spacer(Modifier.width(10.dp))
                    Text("Stoppen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else if (shownMode == MeasurementMode.INACTIVE) {
                Text(
                    text = if (isConnected) "Läuft automatisch, solange der Sensor verbunden ist. Für eine Aufzeichnung oben einen Auftrag wählen."
                    else "Zuerst oben den Sensor verbinden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Button(
                    onClick = onStart,
                    enabled = isConnected,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${shownMode.label} starten",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isConnected) {
                    Text(
                        text = "Zuerst oben den Sensor verbinden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    if (showFilesDialog) {
        SavedFilesDialog(
            outDirPath = outDirPath,
            onDismiss = { showFilesDialog = false }
        )
    }
}

@Composable
private fun RunningPanel(
    mode: MeasurementMode,
    elapsedSeconds: Long,
    points: Int,
    avg25: Double,
    avg10: Double,
    file: String?,
    upload: UploadInfo?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AqiHigh.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveIndicatorDot(isActive = true, color = AqiHigh)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${mode.label} aktiv",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = formatElapsed(elapsedSeconds),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 48.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(Modifier.fillMaxWidth()) {
                Stat("Punkte", points.toString(), Modifier.weight(1f))
                Stat("Ø PM2.5", if (points > 0) fmt1(avg25) else "–", Modifier.weight(1f))
                Stat("Ø PM10", if (points > 0) fmt1(avg10) else "–", Modifier.weight(1f))
            }
            if (mode == MeasurementMode.STATIONARY) {
                Text(
                    text = upload?.let { (if (it.ok) "Upload ok: " else "") + it.message } ?: "Noch kein Upload",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (upload != null && !upload.ok) AqiHigh else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (file != null) {
                Text(
                    text = "Datei $file",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NameField(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    help: String
) {
    Column(Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    details: @Composable () -> Unit
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = AqiElevated.copy(alpha = 0.2f)) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RadioButton(selected = selected, onClick = onClick)
            }
            AnimatedVisibility(visible = selected) { details() }
        }
    }
}

private fun fmt1(v: Double) = String.format(java.util.Locale.GERMANY, "%.1f", v)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LastSessionPanel(last: LastSession, outDirPath: String) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Letzte Messung · ${last.mode.label}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (last.fileName != null) {
                    val file = File(outDirPath, last.fileName)
                    if (file.exists()) {
                        OutlinedButton(
                            onClick = { FileSharing.shareFile(context, file) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Teilen", fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Dauer", formatElapsed(last.durationSeconds), Modifier.weight(1.3f))
                Stat("Punkte", last.points.toString(), Modifier.weight(1f))
                Stat("Ø 2.5", if (last.points > 0) fmt1(last.avg25) else "–", Modifier.weight(1f))
                Stat("Ø 10", if (last.points > 0) fmt1(last.avg10) else "–", Modifier.weight(1f))
            }
            if (last.fileName != null) {
                Text(
                    "${last.fileName}\n$outDirPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedFilesDialog(
    outDirPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dir = File(outDirPath)
    var filesList by remember {
        mutableStateOf(
            dir.listFiles { _, name -> name.endsWith(".csv", true) || name.endsWith(".kml", true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gespeicherte Messdateien", fontWeight = FontWeight.Bold) },
        text = {
            if (filesList.isEmpty()) {
                Text("Bisher keine CSV- oder KML-Dateien gespeichert.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(filesList) { file ->
                        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(file.lastModified()))
                        val sizeKb = String.format(Locale.GERMANY, "%.1f kB", file.length() / 1024.0)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1
                                    )
                                    Text(
                                        "$dateStr · $sizeKb",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { FileSharing.shareFile(context, file) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Teilen",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            file.delete()
                                            filesList = dir.listFiles { _, name -> name.endsWith(".csv", true) || name.endsWith(".kml", true) }
                                                ?.sortedByDescending { it.lastModified() }
                                                ?: emptyList()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (filesList.isNotEmpty()) {
                Button(onClick = { FileSharing.shareFiles(context, filesList) }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Alle teilen")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}
