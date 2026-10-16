package de.demogorgi.sds011native.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.demogorgi.sds011native.model.HistoryPoint
import de.demogorgi.sds011native.ui.theme.AqiHigh
import de.demogorgi.sds011native.ui.theme.BrandPurple
import de.demogorgi.sds011native.ui.theme.BrandTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                label = { Text(label, maxLines = 1, softWrap = false, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun ChartCard(
    historyPoints: List<HistoryPoint>,
    rawChart: Boolean,
    chartRangeSeconds: Long,
    intervalSeconds: Int,
    onRawChartChange: (Boolean) -> Unit,
    onRangeChange: (Long) -> Unit,
    onClearPlot: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "Verlauf",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClearPlot) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Plot leeren",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SegmentedChoice(
                options = listOf(false to "Mittelwerte", true to "Rohwerte"),
                selected = rawChart,
                onSelected = onRawChartChange
            )
            Spacer(Modifier.height(8.dp))
            SegmentedChoice(
                options = listOf(600L to "10 min", 3600L to "1 h", 21600L to "6 h", 0L to "Alles"),
                selected = chartRangeSeconds,
                onSelected = onRangeChange
            )

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "µg/m³",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                LegendItem("PM2.5", BrandTeal)
                Spacer(Modifier.width(16.dp))
                LegendItem("PM10", BrandPurple)
            }

            Spacer(Modifier.height(8.dp))

            DustChartCanvas(
                points = historyPoints,
                dots = !rawChart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (rawChart) "Rohwerte" else "Mittelwerte je ${formatInterval(intervalSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatInterval(seconds: Int): String =
    if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} min" else "$seconds s"

private fun niceStep(maxValue: Double): Double {
    val steps = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 500.0)
    return steps.firstOrNull { maxValue / it <= 4.0 } ?: 1000.0
}

private val timeSteps = longArrayOf(
    10, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 21600, 43200, 86400
)

@Composable
fun DustChartCanvas(
    points: List<HistoryPoint>,
    dots: Boolean,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val plotBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 12.sp, color = muted)
    val endStyle = TextStyle(fontSize = 14.sp, color = onSurface, fontWeight = FontWeight.SemiBold)

    if (points.size < 2) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(plotBg)
        ) {
            Text(
                text = "Noch nicht genügend Datenpunkte",
                style = MaterialTheme.typography.bodyMedium,
                color = muted
            )
        }
        return
    }

    var selectedT by remember { mutableStateOf<Long?>(null) }
    val tipBg = MaterialTheme.colorScheme.surface
    val tipStyle = TextStyle(fontSize = 13.sp, color = onSurface)

    Canvas(
        modifier = modifier.pointerInput(points.size) {
            fun pick(x: Float) {
                val left = 36.dp.toPx()
                val plotW = size.width - left - 44.dp.toPx()
                val minT = points.first().epochMillis
                val maxT = max(points.last().epochMillis, minT + 1000L)
                val t = minT + ((x - left) / plotW * (maxT - minT)).toLong()
                selectedT = points.minByOrNull { abs(it.epochMillis - t) }?.epochMillis
            }
            detectTapGestures(onTap = { o -> pick(o.x) })
        }.pointerInput(points.size) {
            detectHorizontalDragGestures { change, _ ->
                val left = 36.dp.toPx()
                val plotW = size.width - left - 44.dp.toPx()
                val minT = points.first().epochMillis
                val maxT = max(points.last().epochMillis, minT + 1000L)
                val t = minT + ((change.position.x - left) / plotW * (maxT - minT)).toLong()
                selectedT = points.minByOrNull { abs(it.epochMillis - t) }?.epochMillis
            }
        }
    ) {
        val left = 36.dp.toPx()
        val right = 44.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 26.dp.toPx()
        val plotW = size.width - left - right
        val plotH = size.height - top - bottom

        val minT = points.first().epochMillis
        val maxT = max(points.last().epochMillis, minT + 1000L)
        val dataMax = points.maxOf { max(it.pm25, it.pm10) }
        val step = niceStep(max(dataMax * 1.1, 5.0))
        val yMax = ceil(max(dataMax, 1.0) * 1.05 / step) * step

        fun xOf(t: Long) = left + plotW * (t - minT).toFloat() / (maxT - minT).toFloat()
        fun yOf(v: Double) = top + plotH * (1f - (v / yMax).toFloat().coerceIn(0f, 1f))

        drawRect(plotBg, Offset(left, top), Size(plotW, plotH))

        var v = 0.0
        while (v <= yMax + 1e-6) {
            val y = yOf(v)
            drawLine(gridColor, Offset(left, y), Offset(left + plotW, y), 1.dp.toPx())
            val label = measurer.measure(String.format(Locale.GERMANY, "%.0f", v), axisStyle)
            drawText(label, topLeft = Offset(left - 6.dp.toPx() - label.size.width, y - label.size.height / 2f))
            v += step
        }

        val thresholds = listOf(
            Triple(25.0, "PM2.5 erhöht", BrandTeal),
            Triple(40.0, "PM10 erhöht", BrandPurple),
            Triple(50.0, "hoch", AqiHigh)
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        for ((value, text, color) in thresholds) {
            if (value >= yMax) continue
            val y = yOf(value)
            drawLine(color.copy(alpha = 0.7f), Offset(left, y), Offset(left + plotW, y), 1.dp.toPx(), pathEffect = dash)
            val label = measurer.measure(text, axisStyle)
            drawText(label, topLeft = Offset(left + plotW - label.size.width - 4.dp.toPx(), y - label.size.height - 1.dp.toPx()))
        }

        val spanSec = (maxT - minT) / 1000L
        val tickSec = timeSteps.firstOrNull { spanSec / it <= 4 } ?: 86400L
        val fmt = SimpleDateFormat(if (tickSec < 60) "HH:mm:ss" else "HH:mm", Locale.GERMANY)
        val tickMs = tickSec * 1000L
        val offsetMs = TimeZone.getDefault().getOffset(minT).toLong()
        var t = ((minT + offsetMs) / tickMs + 1) * tickMs - offsetMs
        while (t <= maxT) {
            val x = xOf(t)
            drawLine(gridColor, Offset(x, top + plotH), Offset(x, top + plotH + 5.dp.toPx()), 1.dp.toPx())
            val label = measurer.measure(fmt.format(Date(t)), axisStyle)
            val lx = (x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(lx, top + plotH + 7.dp.toPx()))
            t += tickMs
        }

        fun series(selector: (HistoryPoint) -> Double, color: Color) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = xOf(p.epochMillis)
                val y = yOf(selector(p))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (dots) {
                // Rohwerte: nur Punkte, keine Verbindungslinie
                points.forEach { p ->
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(xOf(p.epochMillis), yOf(selector(p))))
                }
            } else {
                // Mittelwerte: linespoints (Linie + Punkte)
                drawPath(path, color, style = Stroke(1.5.dp.toPx()))
                points.forEach { p ->
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(xOf(p.epochMillis), yOf(selector(p))))
                }
            }
            val last = points.last()
            val c = Offset(xOf(last.epochMillis), yOf(selector(last)))
            drawCircle(color, radius = 5.dp.toPx(), center = c)
            val text = measurer.measure(String.format(Locale.GERMANY, "%.1f", selector(last)), endStyle)
            drawText(text, topLeft = Offset(c.x + 8.dp.toPx(), c.y - text.size.height / 2f))
        }

        series({ it.pm10 }, BrandPurple)
        series({ it.pm25 }, BrandTeal)

        val sel = selectedT?.let { st -> points.firstOrNull { it.epochMillis == st } }
        if (sel != null) {
            val x = xOf(sel.epochMillis)
            drawLine(onSurface.copy(alpha = 0.5f), Offset(x, top), Offset(x, top + plotH), 1.dp.toPx())
            drawCircle(BrandPurple, 5.dp.toPx(), Offset(x, yOf(sel.pm10)))
            drawCircle(BrandTeal, 5.dp.toPx(), Offset(x, yOf(sel.pm25)))
            val l1 = measurer.measure(SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date(sel.epochMillis)), tipStyle.copy(fontWeight = FontWeight.Bold))
            val l2 = measurer.measure(String.format(Locale.GERMANY, "PM2.5  %.1f", sel.pm25), tipStyle.copy(color = BrandTeal))
            val l3 = measurer.measure(String.format(Locale.GERMANY, "PM10  %.1f", sel.pm10), tipStyle.copy(color = BrandPurple))
            val pad = 8.dp.toPx()
            val w = maxOf(l1.size.width, l2.size.width, l3.size.width) + 2 * pad
            val h = l1.size.height + l2.size.height + l3.size.height + 2 * pad
            val bx = (if (x + 12.dp.toPx() + w <= size.width) x + 12.dp.toPx() else x - 12.dp.toPx() - w).coerceAtLeast(0f)
            val by = top + 4.dp.toPx()
            drawRoundRect(tipBg, Offset(bx, by), Size(w, h), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            drawRoundRect(gridColor, Offset(bx, by), Size(w, h), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawText(l1, topLeft = Offset(bx + pad, by + pad))
            drawText(l2, topLeft = Offset(bx + pad, by + pad + l1.size.height))
            drawText(l3, topLeft = Offset(bx + pad, by + pad + l1.size.height + l2.size.height))
        }
    }
}

@Composable
fun LegendItem(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
