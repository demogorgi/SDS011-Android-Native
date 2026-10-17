package de.demogorgi.sds011native.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import de.demogorgi.sds011native.ui.state.GpsStatus
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.demogorgi.sds011native.ui.state.AqiLevel
import de.demogorgi.sds011native.ui.theme.AqiElevated
import de.demogorgi.sds011native.ui.theme.AqiGood
import de.demogorgi.sds011native.ui.theme.AqiHigh
import de.demogorgi.sds011native.ui.theme.BrandPurple
import de.demogorgi.sds011native.ui.theme.BrandTeal
import java.util.Locale

@Composable
fun LiveValuesCard(
    pm25: Double?,
    pm10: Double?,
    pm25Level: AqiLevel,
    pm10Level: AqiLevel,
    readingAgeSeconds: Long,
    sensorPauseSeconds: Long?,
    isConnected: Boolean,
    gpsStatus: GpsStatus,
    gpsLat: Double,
    gpsLon: Double,
    modifier: Modifier = Modifier
) {
    var showLevels by remember { mutableStateOf(false) }
    val tint by animateColorAsState(
        targetValue = worstLevelColor(pm25Level, pm10Level),
        label = "cardTint"
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(tint.copy(alpha = 0.07f))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                MetricTile(
                    title = "PM2.5",
                    value = pm25,
                    level = pm25Level,
                    accentColor = BrandTeal,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                MetricTile(
                    title = "PM10",
                    value = pm10,
                    level = pm10Level,
                    accentColor = BrandPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                LiveIndicatorDot(isActive = isConnected && sensorPauseSeconds == null && readingAgeSeconds in 0..10)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when {
                        !isConnected -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.no_sensor)
                        sensorPauseSeconds != null -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.sensor_paused, formatElapsed(sensorPauseSeconds))
                        readingAgeSeconds < 0 -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.waiting_first_reading)
                        readingAgeSeconds <= 2 -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.reading_age_live)
                        else -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.reading_age_seconds, readingAgeSeconds)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val gpsColor = if (gpsStatus == GpsStatus.FIX) AqiGood else MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.size(18.dp)) {
                    val c = center
                    drawCircle(gpsColor, radius = size.width * 0.3f, style = Stroke(2.dp.toPx()))
                    drawCircle(gpsColor, radius = size.width * 0.1f)
                    val r = size.width / 2f
                    drawLine(gpsColor, Offset(c.x, 0f), Offset(c.x, r * 0.3f), 2.dp.toPx())
                    drawLine(gpsColor, Offset(c.x, size.height), Offset(c.x, size.height - r * 0.3f), 2.dp.toPx())
                    drawLine(gpsColor, Offset(0f, c.y), Offset(r * 0.3f, c.y), 2.dp.toPx())
                    drawLine(gpsColor, Offset(size.width, c.y), Offset(size.width - r * 0.3f, c.y), 2.dp.toPx())
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (gpsStatus) {
                        GpsStatus.FIX -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.gps_active)
                        GpsStatus.SEARCHING -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.gps_searching)
                        GpsStatus.NONE -> androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.gps_off)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (gpsStatus == GpsStatus.FIX) {
                    Text(
                        text = String.format(Locale.US, "%.5f, %.5f", gpsLat, gpsLon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLevels = !showLevels }
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.levels_explanation),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showLevels) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = showLevels) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LevelRow(
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.levels_header_level),
                        "PM2.5",
                        "PM10",
                        header = true
                    )
                    LevelRow(
                        AqiLevel.GOOD,
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.level_good_desc_pm25),
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.level_good_desc_pm10)
                    )
                    LevelRow(
                        AqiLevel.ELEVATED,
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.level_elevated_desc_pm25),
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.level_elevated_desc_pm10)
                    )
                    LevelRow(
                        AqiLevel.HIGH,
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.level_high_desc_pm25),
                        androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.level_high_desc_pm10)
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(de.demogorgi.sds011native.R.string.levels_body_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

fun levelColor(level: AqiLevel): Color? = when (level) {
    AqiLevel.GOOD -> AqiGood
    AqiLevel.ELEVATED -> AqiElevated
    AqiLevel.HIGH -> AqiHigh
    AqiLevel.NONE -> null
}

@Composable
private fun worstLevelColor(a: AqiLevel, b: AqiLevel): Color {
    val worst = if (a.ordinal >= b.ordinal) a else b
    return levelColor(worst) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
}

@Composable
private fun LevelRow(level: AqiLevel, pm25: String, pm10: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(Modifier.weight(1f)) { AqiLevelBadge(level) }
        Text(pm25, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(pm10, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LevelRow(a: String, b: String, c: String, header: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        listOf(a, b, c).forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: Double?,
    level: AqiLevel,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "µg/m³",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 52.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        AqiLevelBadge(level = level)
    }
}

@Composable
fun AqiLevelBadge(
    level: AqiLevel,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeFg) = when (level) {
        AqiLevel.GOOD -> AqiGood.copy(alpha = 0.15f) to AqiGood
        AqiLevel.ELEVATED -> AqiElevated.copy(alpha = 0.15f) to AqiElevated
        AqiLevel.HIGH -> AqiHigh.copy(alpha = 0.15f) to AqiHigh
        AqiLevel.NONE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = badgeBg,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(badgeFg))
            Spacer(Modifier.width(6.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(level.labelRes),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = badgeFg
            )
        }
    }
}

@Composable
fun LiveIndicatorDot(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = AqiGood
) {
    if (!isActive) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .alpha(alpha)
            .background(color)
    )
}
