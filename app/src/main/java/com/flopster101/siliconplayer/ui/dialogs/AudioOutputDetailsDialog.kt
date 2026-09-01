package com.flopster101.siliconplayer.ui.dialogs

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.isWatchDevice
import com.flopster101.siliconplayer.buildDecoderExtensionArtworkHintMap
import com.flopster101.siliconplayer.decoderArtworkHintForName
import com.flopster101.siliconplayer.resolveDecoderArtworkHintForFileName
import com.flopster101.siliconplayer.AudioBackendPreference
import com.flopster101.siliconplayer.AudioBufferPreset
import com.flopster101.siliconplayer.AudioResamplerPreference
import com.flopster101.siliconplayer.ui.icons.ConversionPathIcon
import com.flopster101.siliconplayer.ui.screens.AudioOutputRouteInfo
import com.flopster101.siliconplayer.ui.screens.AudioOutputRouteType
import java.io.File
import java.util.Locale

/**
 * Material Design 3 Audio Output & Signal Chain Inspector Dialog.
 * Displays real-time audio pipeline topology:
 * (Source File) -> (Decoder) -> (Optional Resampler) -> (Audio HAL / Output Sink)
 */
@Composable
internal fun AudioOutputDetailsDialog(
    routeInfo: AudioOutputRouteInfo,
    displayFile: File?,
    sourceId: String?,
    requestUrl: String?,
    decoderName: String?,
    trackSampleRateHz: Int,
    decoderRenderRateHz: Int = NativeBridge.getDecoderRenderSampleRateHz(),
    outputStreamRateHz: Int = NativeBridge.getOutputStreamSampleRateHz(),
    channelCount: Int,
    bitDepthLabel: String,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    onOpenAudioSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    val selectedBackend = remember(prefs) {
        AudioBackendPreference.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE, null))
    }
    val bufferPresetKey = remember(selectedBackend) {
        AppPreferenceKeys.audioBufferPresetForBackend(selectedBackend)
    }
    val activeBufferPreset = remember(prefs, bufferPresetKey) {
        if (prefs.contains(bufferPresetKey)) {
            AudioBufferPreset.fromStorage(prefs.getString(bufferPresetKey, null))
        } else {
            AudioBufferPreset.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_BUFFER_PRESET, null))
        }
    }
    val activeResamplerPref = remember(prefs) {
        AudioResamplerPreference.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE, null))
    }

    val effectiveDecoderName = decoderName?.takeIf { it.isNotBlank() } ?: "Unknown"
    val effectiveFileName = displayFile?.name ?: sourceId?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Unknown audio"
    val effectiveTrackRate = if (trackSampleRateHz > 0) trackSampleRateHz else (decoderRenderRateHz.takeIf { it > 0 } ?: 48000)
    val effectiveDecoderRate = if (decoderRenderRateHz > 0) decoderRenderRateHz else effectiveTrackRate
    val effectiveOutputRate = if (outputStreamRateHz > 0) outputStreamRateHz else 48000
    val effectiveChannels = if (channelCount > 0) channelCount else 2

    val burstFrames = remember { NativeBridge.getStreamBurstFrames() }
    val effectiveBurstFrames = if (burstFrames > 0) burstFrames else 480
    val latencyMs = if (effectiveBurstFrames > 0 && effectiveOutputRate > 0) {
        effectiveBurstFrames * 1000.0 / effectiveOutputRate
    } else {
        10.0
    }

    val isResamplingActive = effectiveDecoderRate > 0 && effectiveOutputRate > 0 && effectiveDecoderRate != effectiveOutputRate

    val storageSourceLabel = when {
        sourceId?.startsWith("smb://", ignoreCase = true) == true || requestUrl?.startsWith("smb://", ignoreCase = true) == true -> "SMB share"
        sourceId?.startsWith("http://", ignoreCase = true) == true || sourceId?.startsWith("https://", ignoreCase = true) == true ||
        requestUrl?.startsWith("http://", ignoreCase = true) == true || requestUrl?.startsWith("https://", ignoreCase = true) == true -> "HTTP stream"
        else -> "Local storage"
    }

    val fileFormatBadge = remember(effectiveFileName) {
        val ext = effectiveFileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
        if (ext.isNotBlank() && ext.length <= 5) ext else "AUDIO"
    }

    val fileArtworkHint = remember(effectiveFileName, effectiveDecoderName, decoderExtensionArtworkHints) {
        decoderArtworkHintForName(effectiveDecoderName)
            ?: resolveDecoderArtworkHintForFileName(effectiveFileName, decoderExtensionArtworkHints)
            ?: resolveDecoderArtworkHintForFileName(effectiveFileName, buildDecoderExtensionArtworkHintMap())
    }

    val backendLabel = remember {
        val nativeBackend = NativeBridge.getAudioBackendLabel()
        if (nativeBackend.isNotBlank() && nativeBackend != "(inactive)" && nativeBackend != "Unknown") {
            nativeBackend
        } else {
            "AAudio"
        }
    }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Audio output",
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = routeInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Backend: $backendLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Rate: ${formatSampleRateForInspector(effectiveOutputRate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Decoder: $effectiveDecoderName (${formatSampleRateForInspector(effectiveDecoderRate)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isResamplingActive) {
                    Text(
                        text = "Resampling: ${formatSampleRateForInspector(effectiveDecoderRate)} -> ${formatSampleRateForInspector(effectiveOutputRate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD54F)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done")
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = adaptiveDialogProperties()
    ) {
        Surface(
            modifier = adaptiveDialogModifier(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // 1. Dialog Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (routeInfo.type) {
                                AudioOutputRouteType.Bluetooth -> Icons.Default.Bluetooth
                                AudioOutputRouteType.Headphones -> Icons.Default.Headphones
                                AudioOutputRouteType.Usb -> Icons.Default.Usb
                                AudioOutputRouteType.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio output details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = routeInfo.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // Section: Signal Chain Graph
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogSectionLabel(
                            text = "Signal chain",
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {

                                // Node 1: Source File
                                SignalChainNodeCard(
                                    icon = {
                                        when (fileArtworkHint) {
                                            DecoderArtworkHint.TrackedFile -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_file_tracked),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            DecoderArtworkHint.GameFile -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_file_game),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    iconBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    title = effectiveFileName,
                                    badgeText = fileFormatBadge,
                                    badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    badgeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    details = listOfNotNull(
                                        "Rate" to formatSampleRateForInspector(effectiveTrackRate),
                                        "Channels" to "$effectiveChannels ch",
                                        bitDepthLabel.takeIf { it.isNotBlank() }?.let { "Bit depth" to it },
                                        "Source" to storageSourceLabel
                                    )
                                )

                                // Connector: Stream Feed
                                SignalChainConnector(label = "Stream feed")

                                // Node 2: Decoder
                                SignalChainNodeCard(
                                    icon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_placeholder_tracker_chip),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    },
                                    iconBackgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                    title = effectiveDecoderName,
                                    badgeText = "Decoder",
                                    badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    badgeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    details = listOf(
                                        "Rate" to formatSampleRateForInspector(effectiveDecoderRate),
                                        "Format" to "Float32"
                                    )
                                )

                                // Optional Node 3: Resampler
                                if (isResamplingActive) {
                                    SignalChainConnector(label = "PCM stream")

                                    val resamplerTitle = when (activeResamplerPref) {
                                        AudioResamplerPreference.Sox -> "SoX resampler"
                                        AudioResamplerPreference.BuiltIn -> "Built-in resampler"
                                    }
                                    val resamplerEngine = when (activeResamplerPref) {
                                        AudioResamplerPreference.Sox -> "SoX (libsoxr)"
                                        AudioResamplerPreference.BuiltIn -> "miniaudio linear"
                                    }

                                    SignalChainNodeCard(
                                        icon = {
                                            Icon(
                                                imageVector = ConversionPathIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color(0xFFFFD54F)
                                            )
                                        },
                                        iconBackgroundColor = Color(0xFFFFD54F).copy(alpha = 0.15f),
                                        title = resamplerTitle,
                                        badgeText = "Resampler",
                                        badgeColor = Color(0xFFFFD54F),
                                        badgeBackgroundColor = Color(0xFFFFD54F).copy(alpha = 0.15f),
                                        details = listOf(
                                            "Ratio" to "${formatSampleRateForInspector(effectiveDecoderRate)} → ${formatSampleRateForInspector(effectiveOutputRate)}",
                                            "Engine" to resamplerEngine
                                        )
                                    )
                                }

                                // Connector to Output Sink
                                val outputConnectorLabel = when (routeInfo.type) {
                                    AudioOutputRouteType.Bluetooth -> "$backendLabel → Bluetooth A2DP"
                                    AudioOutputRouteType.Usb -> "$backendLabel → AudioFlinger"
                                    else -> "$backendLabel → AudioFlinger"
                                }
                                SignalChainConnector(label = outputConnectorLabel)

                                // Node 4 (or 3): Output Endpoint
                                val sinkBadgeText = when (routeInfo.type) {
                                    AudioOutputRouteType.Speaker -> "Internal"
                                    AudioOutputRouteType.Headphones -> "Wired"
                                    AudioOutputRouteType.Usb -> "USB"
                                    AudioOutputRouteType.Bluetooth -> "A2DP"
                                }
                                val sinkExtra = when (routeInfo.type) {
                                    AudioOutputRouteType.Usb -> "Routing" to "AudioFlinger"
                                    else -> null
                                }
                                SignalChainNodeCard(
                                    icon = {
                                        Icon(
                                            imageVector = when (routeInfo.type) {
                                                AudioOutputRouteType.Bluetooth -> Icons.Default.Bluetooth
                                                AudioOutputRouteType.Headphones -> Icons.Default.Headphones
                                                AudioOutputRouteType.Usb -> Icons.Default.Usb
                                                AudioOutputRouteType.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    iconBackgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    title = routeInfo.name,
                                    badgeText = sinkBadgeText,
                                    badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    badgeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    details = listOfNotNull(
                                        "Rate" to formatSampleRateForInspector(effectiveOutputRate),
                                        "Format" to "16-bit PCM",
                                        sinkExtra
                                    )
                                )
                            }
                        }
                    }

                    // Section: Hardware & Stream Metrics (Key-Value Row List Card)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogSectionLabel(
                            text = "Hardware & stream metrics",
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        val deviceTypeSub = when (routeInfo.type) {
                            AudioOutputRouteType.Speaker -> "TYPE_BUILTIN_SPEAKER"
                            AudioOutputRouteType.Headphones -> "TYPE_WIRED_HEADSET"
                            AudioOutputRouteType.Usb -> "TYPE_USB_DEVICE"
                            AudioOutputRouteType.Bluetooth -> "TYPE_BLUETOOTH_A2DP"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricListRow(
                                    label = "Active backend",
                                    value = "$backendLabel (Shared)",
                                    sub = "miniaudio"
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                val presetTargetFrames = when (activeBufferPreset) {
                                    AudioBufferPreset.VerySmall -> "2048 frames"
                                    AudioBufferPreset.Small -> "4096 frames"
                                    AudioBufferPreset.Medium -> "8192 frames"
                                    AudioBufferPreset.Large -> "16384 frames"
                                    AudioBufferPreset.VeryLarge -> "32768 frames"
                                }
                                MetricListRow(
                                    label = "Buffer & latency",
                                    value = "$effectiveBurstFrames frames (${String.format(Locale.US, "%.1f ms", latencyMs)})",
                                    sub = "Preset: ${activeBufferPreset.label} ($presetTargetFrames)"
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                MetricListRow(
                                    label = "Output device",
                                    value = routeInfo.name,
                                    sub = deviceTypeSub
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                MetricListRow(
                                    label = "Output format",
                                    value = "${formatSampleRateForInspector(effectiveOutputRate)} • 16-bit",
                                    sub = null
                                )
                            }
                        }
                    }

                    // Section: USB Supported Sample Rates (if USB route)
                    if (routeInfo.type == AudioOutputRouteType.Usb) {
                        UsbSupportedRatesSection(
                            context = context,
                            activeRateHz = effectiveOutputRate
                        )
                    }
                }

                // 3. Dialog Action Buttons Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenAudioSettings) {
                        Text(
                            text = "Audio settings",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChainNodeCard(
    icon: @Composable () -> Unit,
    iconBackgroundColor: Color,
    title: String,
    badgeText: String,
    badgeColor: Color,
    badgeBackgroundColor: Color,
    details: List<Pair<String, String>>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBackgroundColor)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = badgeColor,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                BulletSeparatedDetails(
                    details = details,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BulletSeparatedDetails(
    details: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    if (details.isEmpty()) return

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val separatorColor = MaterialTheme.colorScheme.outlineVariant

    Layout(
        content = {
            // Measurables 0 until N: the detail items
            details.forEach { (label, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$label:",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        fontSize = 11.sp
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor,
                        fontSize = 11.sp
                    )
                }
            }
            // Measurables N until 2N - 1: distinct bullet separators
            repeat((details.size - 1).coerceAtLeast(0)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = separatorColor,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val itemCount = details.size
        val itemPlaceables = (0 until itemCount).map {
            measurables[it].measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val bulletPlaceables = (itemCount until measurables.size).map {
            measurables[it].measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val singleBulletWidth = bulletPlaceables.firstOrNull()?.width ?: 0
        val singleBulletHeight = bulletPlaceables.firstOrNull()?.height ?: 0

        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val lines = mutableListOf<MutableList<Placeable>>()
        val lineItemPositions = mutableListOf<MutableList<Int>>()
        val lineBulletPositions = mutableListOf<MutableList<Int>>()

        var currentLine = mutableListOf<Placeable>()
        var currentItemXs = mutableListOf<Int>()
        var currentBulletXs = mutableListOf<Int>()
        var currentLineWidth = 0

        itemPlaceables.forEach { item ->
            val neededWidth = if (currentLine.isEmpty()) item.width else singleBulletWidth + item.width
            if (currentLine.isNotEmpty() && currentLineWidth + neededWidth > maxWidth) {
                // Line wrap
                lines.add(currentLine)
                lineItemPositions.add(currentItemXs)
                lineBulletPositions.add(currentBulletXs)

                currentLine = mutableListOf()
                currentItemXs = mutableListOf()
                currentBulletXs = mutableListOf()
                currentLineWidth = 0
            }

            if (currentLine.isNotEmpty()) {
                currentBulletXs.add(currentLineWidth)
                currentLineWidth += singleBulletWidth
            }
            currentItemXs.add(currentLineWidth)
            currentLine.add(item)
            currentLineWidth += item.width
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
            lineItemPositions.add(currentItemXs)
            lineBulletPositions.add(currentBulletXs)
        }

        val lineSpacing = 3.dp.roundToPx()
        val lineHeight = (itemPlaceables.maxOfOrNull { it.height } ?: 0).coerceAtLeast(singleBulletHeight)
        val totalHeight = if (lines.isEmpty()) 0 else lines.size * lineHeight + (lines.size - 1).coerceAtLeast(0) * lineSpacing
        val resolvedWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else (lines.maxOfOrNull { line -> line.sumOf { it.width } } ?: 0)

        layout(resolvedWidth, totalHeight) {
            var y = 0
            var bulletIndex = 0
            lines.forEachIndexed { lineIdx, line ->
                val itemXs = lineItemPositions[lineIdx]
                val bXs = lineBulletPositions[lineIdx]

                bXs.forEach { bx ->
                    if (bulletIndex < bulletPlaceables.size) {
                        val bulletPlaceable = bulletPlaceables[bulletIndex++]
                        val bulletY = y + (lineHeight - bulletPlaceable.height) / 2
                        bulletPlaceable.placeRelative(bx, bulletY)
                    }
                }

                line.forEachIndexed { itemIdx, placeable ->
                    val itemX = itemXs[itemIdx]
                    val itemY = y + (lineHeight - placeable.height) / 2
                    placeable.placeRelative(itemX, itemY)
                }

                y += lineHeight + lineSpacing
            }
        }
    }
}

@Composable
private fun SignalChainConnector(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Vertical Connector Line on the left (aligned with icon centers)
        Box(
            modifier = Modifier
                .padding(start = 27.dp)
                .width(2.dp)
                .height(26.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        )

        // Centered Pill
        Surface(
            modifier = Modifier.padding(start = 42.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MetricListRow(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UsbSupportedRatesSection(
    context: Context,
    activeRateHz: Int
) {
    val supportedRates = remember(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rates = mutableSetOf<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevices = devices.filter {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            for (dev in usbDevices) {
                dev.sampleRates.forEach { if (it > 0) rates.add(it) }
            }
        }
        if (rates.isEmpty()) {
            listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)
        } else {
            rates.sorted()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Supported sample rates",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Hardware query",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                supportedRates.forEach { rate ->
                    val isActive = rate == activeRateHz
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            text = formatSampleRateForInspector(rate),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSampleRateForInspector(rateHz: Int): String {
    if (rateHz <= 0) return "Unknown"
    return if (rateHz % 1000 == 0) {
        "${rateHz / 1000}.0 kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", rateHz / 1000.0)
    }
}
