package de.demogorgi.sds011native.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.demogorgi.sds011native.R
import de.demogorgi.sds011native.ui.theme.AqiGood

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    statusText: String,
    isConnected: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0D0D0D),
                    modifier = Modifier.size(40.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Feinstaub",
                    maxLines = 1,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            StatusPill(statusText = statusText, isConnected = isConnected)
            IconButton(onClick = onSettingsClick) {
                GearIcon(MaterialTheme.colorScheme.onSurface)
            }
        },
        modifier = modifier
    )
}

@Composable
fun StatusPill(
    statusText: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = when {
            isConnected -> AqiGood
            statusText.contains("verbinde", ignoreCase = true) -> Color(0xFFFFB300)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        label = "statusDotColor"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.padding(end = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun formatElapsed(seconds: Long): String =
    String.format(java.util.Locale.GERMANY, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)

@Composable
private fun GearIcon(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val c = center
        val outer = size.minDimension / 2f
        val body = outer * 0.78f
        for (i in 0 until 8) {
            rotate(i * 45f, c) {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(c.x - outer * 0.17f, c.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 0.34f, outer * 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            }
        }
        drawCircle(color, radius = body, center = c)
        drawCircle(Color.Transparent, radius = outer * 0.32f, center = c, blendMode = BlendMode.Clear)
    }
}
