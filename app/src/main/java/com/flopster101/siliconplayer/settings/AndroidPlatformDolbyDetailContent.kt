package com.flopster101.siliconplayer.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.IntChoice
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.SettingsItemCard
import com.flopster101.siliconplayer.SettingsRowSpacer
import com.flopster101.siliconplayer.SettingsSectionLabel

@Composable
fun AndroidPlatformDolbyDetailContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    var useForMultichannel by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.PLATFORM_DOLBY_DECODER, AppDefaults.OutputPipeline.platformDolbyDecoder)
        )
    }
    var visSource by remember {
        androidx.compose.runtime.mutableIntStateOf(
            com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.visSourceStorageValue()
        )
    }
    val hasRecordAudioPermission = remember {
        mutableStateOf(com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.hasRecordAudioPermission())
    }
    val recordAudioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordAudioPermission.value = granted
        if (!granted && visSource == com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_SYSTEM) {
            visSource = com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_SHADOW
            com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.setVisSourceStorageValue(visSource)
        }
    }
    var claimedFormats by remember {
        mutableStateOf(com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.claimedFormats())
    }
    LaunchedEffect(Unit) {
        while (true) {
            claimedFormats = com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.claimedFormats()
            kotlinx.coroutines.delay(2000)
        }
    }

    Column {
        SettingsSectionLabel("Core status")
        PlayerSettingToggleCard(
            title = "Use for Dolby formats",
            description = "Hand E-AC-3 / AC-3 playback to the system decoder so the device's Dolby processing (Atmos / spatializer) applies. Falls back to the FFmpeg core automatically when unsupported or on error.",
            checked = useForMultichannel,
            onCheckedChange = { enabled ->
                useForMultichannel = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.PLATFORM_DOLBY_DECODER, enabled).apply()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Handled formats")
        claimedFormats.forEach { (label, component) ->
            SettingsItemCard(
                title = label,
                description = if (component.isNotBlank()) {
                    "System codec: $component"
                } else {
                    "No system decoder available; the FFmpeg core handles this format"
                },
                icon = Icons.Default.MusicNote,
                onClick = {}
            )
            if (label != claimedFormats.last().first) {
                SettingsRowSpacer()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Visualizer tap")
        val visSourceDescription = when (visSource) {
            com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_SYSTEM ->
                if (hasRecordAudioPermission.value) {
                    "Taps the system output mix. Stereo visualizers only."
                } else {
                    "Requires microphone access to capture the output mix."
                }
            com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_NONE ->
                "Visualizers freeze while this core is active."
            else ->
                "Renders the track silently in-app. All visualizers keep working."
        }
        CoreChoiceSelectorCard(
            title = "Visualizer tap source",
            description = visSourceDescription,
            selectedValue = visSource,
            options = listOf(
                IntChoice(
                    com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_SHADOW,
                    "Shadow decoder (full)"
                ),
                IntChoice(
                    com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_SYSTEM,
                    "Android system tap (stereo)"
                ),
                IntChoice(
                    com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_NONE,
                    "None (frozen)"
                )
            ),
            onSelected = { selected ->
                if (selected == com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.VIS_SOURCE_SYSTEM &&
                    !hasRecordAudioPermission.value
                ) {
                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    return@CoreChoiceSelectorCard
                }
                visSource = selected
                com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.setVisSourceStorageValue(selected)
                com.flopster101.siliconplayer.playback.PlatformDolbyPlayer.refreshShadowMute()
            }
        )
    }
}
