package de.demogorgi.sds011native.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.demogorgi.sds011native.R
import de.demogorgi.sds011native.ui.theme.AqiElevated

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val tabs = listOf(
        context.getString(R.string.about_tab_info),
        context.getString(R.string.about_tab_hardware),
        context.getString(R.string.about_tab_license)
    )
    val logoDescription = context.getString(R.string.app_logo_description)
    val aboutTitle = context.getString(R.string.about_title)
    val versionText = context.getString(R.string.app_version, appVersionName())
    val closeText = context.getString(R.string.action_close)
    val appDescription = context.getString(R.string.about_app_desc)
    val disclaimerTitle = context.getString(R.string.about_disclaimer_title)
    val disclaimerText = context.getString(R.string.about_disclaimer_text)
    val hardwareTitle = context.getString(R.string.hardware_wiring_title)
    val hardwareText = context.getString(R.string.hardware_wiring_text)
    val licenseText = context.getString(R.string.about_license_text)
    val openSourceTitle = context.getString(R.string.about_open_source)
    val creditsText = context.getString(R.string.about_credits)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0D0D0D),
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = logoDescription,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        aboutTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        versionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> GeneralInfoTab(appDescription, disclaimerTitle, disclaimerText)
                        1 -> HardwareWiringTab(hardwareTitle, hardwareText)
                        2 -> LicensesTab(licenseText, openSourceTitle, creditsText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(closeText)
            }
        }
    )
}

@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
}

@Composable
private fun GeneralInfoTab(
    appDescription: String,
    disclaimerTitle: String,
    disclaimerText: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            appDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AqiElevated.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    disclaimerTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    disclaimerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HardwareWiringTab(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun LicensesTab(licenseText: String, openSourceTitle: String, creditsText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            licenseText.substringBefore("\n\n"),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    licenseText.substringAfter("\n\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            openSourceTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            creditsText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
