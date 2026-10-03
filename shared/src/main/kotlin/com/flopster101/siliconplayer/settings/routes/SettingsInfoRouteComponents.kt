package com.flopster101.siliconplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ColorFilter
import com.flopster101.siliconplayer.platform.LocalAppVersionInfo
import com.flopster101.siliconplayer.ui.icons.AboutAppIconColor
import com.flopster101.siliconplayer.ui.icons.AboutAppIconMonochrome
import com.flopster101.siliconplayer.ui.icons.BalanceIcon
import com.flopster101.siliconplayer.ui.icons.GithubIcon

@Composable
internal fun AboutSettingsBody(
    useMonet: Boolean
) {
    val uriHandler = LocalUriHandler.current
    val versionInfo = LocalAppVersionInfo.current
    val versionLabel = if (versionInfo.platform.isNotBlank()) {
        "v${versionInfo.versionName}-${versionInfo.platform}-${versionInfo.abiOrArch}-${versionInfo.gitSha}"
    } else {
        "v${versionInfo.versionName}-${versionInfo.abiOrArch}-${versionInfo.gitSha}"
    }
    val coreEntries = remember { AboutCatalog.cores }
    val libraryEntries = remember { AboutCatalog.libraries }
    var selectedAboutEntry by remember { mutableStateOf<AboutEntity?>(null) }
    var showAppLicense by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AboutAppIcon(useMonet = useMonet)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silicon Player",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(8.dp))

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Open-source, multi-format chiptune, tracker and modern music player.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AboutActionButton(
                        icon = GithubIcon,
                        label = "GitHub",
                        onClick = {
                            uriHandler.openUri("https://github.com/SiliconPlayer/SiliconPlayer")
                        }
                    )
                    AboutActionButton(
                        icon = BalanceIcon,
                        label = "GPL v3",
                        onClick = {
                            showAppLicense = true
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(8.dp))

        SettingsSectionLabel(text = "Audio cores")
        coreEntries.forEachIndexed { index, entry ->
            AboutEntityListItemCard(
                entity = entry,
                icon = Icons.Default.GraphicEq,
                onClick = { selectedAboutEntry = entry }
            )
            if (index != coreEntries.lastIndex) {
                SettingsRowSpacer()
            }
        }
        Spacer(modifier = Modifier.size(8.dp))

        SettingsSectionLabel(text = "Libraries")
        libraryEntries.forEachIndexed { index, entry ->
            AboutEntityListItemCard(
                entity = entry,
                icon = Icons.Default.Link,
                onClick = { selectedAboutEntry = entry }
            )
            if (index != libraryEntries.lastIndex) {
                SettingsRowSpacer()
            }
        }
    }

    selectedAboutEntry?.let { entity ->
        AboutEntityDialog(
            entity = entity,
            onDismiss = { selectedAboutEntry = null }
        )
    }

    if (showAppLicense) {
        val appLicenseText = remember {
            AboutCatalog.resolveLicenseText(AboutCatalog.applicationEntity.id) ?: ""
        }
        AboutLicenseTextDialog(
            entity = AboutCatalog.applicationEntity,
            text = appLicenseText,
            onDismiss = { showAppLicense = false }
        )
    }
}

@Composable
private fun AboutActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AboutAppIcon(
    useMonet: Boolean
) {
    Image(
        imageVector = if (useMonet) AboutAppIconMonochrome else AboutAppIconColor,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        colorFilter = if (useMonet) {
            ColorFilter.tint(MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    )
}


@Composable
private fun AboutEntityListItemCard(
    entity: AboutEntity,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    SettingsItemCard(
        title = entity.name,
        description = "${entity.description}\nAuthor: ${entity.author}\nLicense: ${entity.license}",
        icon = icon,
        onClick = onClick
    )
}

@Composable
internal fun ClearAudioParametersCard(
    onClearAll: () -> Unit,
    onClearPlugins: () -> Unit,
    onClearSongs: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    SettingsItemCard(
        title = "Clear saved parameters",
        description = "Reset volume settings for all, cores, or songs",
        icon = Icons.Default.Delete,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        SettingsActionListDialog(
            title = "Clear saved parameters",
            message = "Choose which audio parameters to reset:",
            actions = listOf(
                SettingsActionDialogItem("Clear all", onClearAll),
                SettingsActionDialogItem("Clear core volumes", onClearPlugins),
                SettingsActionDialogItem("Clear song volumes", onClearSongs)
            ),
            onDismiss = { showDialog = false }
        )
    }
}
