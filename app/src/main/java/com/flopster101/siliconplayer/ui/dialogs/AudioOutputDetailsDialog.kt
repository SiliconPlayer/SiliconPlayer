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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.flopster101.siliconplayer.ChoiceDialogOption
import com.flopster101.siliconplayer.SettingsSingleChoiceDialog
import com.flopster101.siliconplayer.usb.DirectUacVolumeMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.isCachedRemoteSourceFile
import com.flopster101.siliconplayer.sourceIdForCachedFileName
import com.flopster101.siliconplayer.stripRemoteCacheHashPrefix
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.AudioBackendPreference
import com.flopster101.siliconplayer.AudioBufferPreset
import com.flopster101.siliconplayer.AudioResamplerPreference
import com.flopster101.siliconplayer.BitPerfectCoordinator
import com.flopster101.siliconplayer.BitPerfectDriverMethod
import com.flopster101.siliconplayer.BitPerfectSupportStatus
import com.flopster101.siliconplayer.supportsLiveSampleRateChange
import com.flopster101.siliconplayer.ui.icons.ConversionPathIcon
import com.flopster101.siliconplayer.ui.screens.AudioOutputRouteInfo
import com.flopster101.siliconplayer.ui.screens.AudioOutputRouteType
import com.flopster101.siliconplayer.usb.UacDriverCoordinator
import kotlinx.coroutines.launch
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
    isPlaying: Boolean = false,
    playbackCapabilitiesFlags: Int = 0,
    bitPerfectEnabled: Boolean = false,
    onBitPerfectToggled: (Boolean) -> Unit = {},
    onRestartTrack: () -> Unit = {},
    onOpenAudioSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    val driverMethod = remember(prefs) {
        BitPerfectDriverMethod.fromStorage(prefs.getString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, null))
    }
    val bitPerfectSupportStatus = remember(routeInfo, driverMethod) {
        BitPerfectCoordinator.checkBitPerfectSupport(context, driverMethod)
    }
    val isBitPerfectSupported = bitPerfectSupportStatus == BitPerfectSupportStatus.Supported
    val isUacDriverOpen by UacDriverCoordinator.isOpen.collectAsState()
    val isUacStreaming by UacDriverCoordinator.isStreaming.collectAsState()
    val uacLastError by UacDriverCoordinator.lastErrorMessage.collectAsState()
    val uacDiagnostics = remember(isUacStreaming, isUacDriverOpen) {
        if (isUacDriverOpen) UacDriverCoordinator.getDiagnostics() else null
    }
    val uacVolumeMode by UacDriverCoordinator.volumeMode.collectAsState()
    val uacManualVolume by UacDriverCoordinator.manualVolume.collectAsState()
    val uacEffectiveVolumeScale by UacDriverCoordinator.effectiveVolumeScale.collectAsState()
    var showVolumeModeDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var showRestartConfirmDialog by remember { mutableStateOf(false) }
    var pendingBitPerfectState by remember { mutableStateOf(false) }

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

    val cacheRoot = remember(context) { File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR) }
    val resolvedSourceId = remember(displayFile, sourceId, requestUrl) {
        val direct = sourceId?.trim()?.takeIf { it.isNotBlank() } ?: requestUrl?.trim()?.takeIf { it.isNotBlank() }
        if (direct != null && (direct.startsWith("smb://", ignoreCase = true) || direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true))) {
            direct
        } else if (displayFile != null && isCachedRemoteSourceFile(displayFile)) {
            sourceIdForCachedFileName(cacheRoot, displayFile.name) ?: direct
        } else {
            direct
        }
    }

    val effectiveDecoderName = decoderName?.takeIf { it.isNotBlank() } ?: "Unknown"
    val effectiveFileName = remember(displayFile, resolvedSourceId, sourceId, requestUrl) {
        val candidate = when {
            resolvedSourceId != null && (resolvedSourceId.startsWith("smb://", ignoreCase = true) || resolvedSourceId.startsWith("http://", ignoreCase = true) || resolvedSourceId.startsWith("https://", ignoreCase = true)) -> {
                val leaf = resolvedSourceId.substringAfterLast('/').trim()
                decodePercentEncodedForDisplay(leaf) ?: leaf
            }
            displayFile != null -> {
                val stripped = stripRemoteCacheHashPrefix(displayFile.name)
                decodePercentEncodedForDisplay(stripped) ?: stripped
            }
            sourceId != null -> {
                val leaf = stripRemoteCacheHashPrefix(sourceId.substringAfterLast('/').trim())
                decodePercentEncodedForDisplay(leaf) ?: leaf
            }
            requestUrl != null -> {
                val leaf = stripRemoteCacheHashPrefix(requestUrl.substringAfterLast('/').trim())
                decodePercentEncodedForDisplay(leaf) ?: leaf
            }
            else -> "Unknown audio"
        }
        if (candidate.isBlank() || candidate == "remote") {
            val fallbackLeaf = resolvedSourceId?.substringAfterLast('/')?.trim()
            fallbackLeaf?.let { decodePercentEncodedForDisplay(it) ?: it }?.takeIf { it.isNotBlank() && it != "remote" } ?: candidate
        } else {
            candidate
        }
    }
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

    val isBitPerfectActive = remember(bitPerfectEnabled, routeInfo, isUacDriverOpen, isBitPerfectSupported) {
        routeInfo.type == AudioOutputRouteType.Usb && bitPerfectEnabled && (isUacDriverOpen || BitPerfectCoordinator.isBitPerfectActive(context) || isBitPerfectSupported)
    }

    var showReplugNoticeDialog by remember { mutableStateOf(false) }

    val onToggleBitPerfect: (Boolean) -> Unit = { targetEnabled ->
        val canLiveChange = supportsLiveSampleRateChange(playbackCapabilitiesFlags)
        if (isPlaying && !canLiveChange) {
            pendingBitPerfectState = targetEnabled
            showRestartConfirmDialog = true
        } else {
            onBitPerfectToggled(targetEnabled)
            if (targetEnabled) {
                if (driverMethod == BitPerfectDriverMethod.DirectUac) {
                    val rawUsb = UacDriverCoordinator.findUsbAudioDevice(context)
                    if (rawUsb != null) {
                        coroutineScope.launch {
                            val granted = UacDriverCoordinator.requestPermission(context, rawUsb)
                            if (granted) {
                                UacDriverCoordinator.open(context, rawUsb)
                                if (isPlaying) {
                                    val targetRate = effectiveDecoderRate
                                    val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                    val ok = UacDriverCoordinator.start(targetRate, targetBitDepth, 2)
                                    NativeBridge.setBitPerfectMode(ok)
                                    if (!ok) {
                                        onBitPerfectToggled(false)
                                    }
                                } else {
                                    NativeBridge.setBitPerfectMode(true)
                                }
                            } else {
                                onBitPerfectToggled(false)
                            }
                        }
                    } else {
                        onBitPerfectToggled(false)
                    }
                } else if (BitPerfectCoordinator.isBitPerfectPlatformSupported()) {
                    val usbAudioDevice = BitPerfectCoordinator.findConnectedUsbAudioDevice(context)
                    if (usbAudioDevice != null) {
                        if (isPlaying) {
                            val targetRate = effectiveDecoderRate
                            BitPerfectCoordinator.setPreferredBitPerfectMixer(context, usbAudioDevice, targetRate, effectiveChannels)
                        }
                        NativeBridge.setBitPerfectMode(true)
                    } else {
                        BitPerfectCoordinator.clearBitPerfectMixer(context)
                        NativeBridge.setBitPerfectMode(false)
                        onBitPerfectToggled(false)
                    }
                }
            } else {
                UacDriverCoordinator.close()
                BitPerfectCoordinator.clearBitPerfectMixer(context)
                NativeBridge.setBitPerfectMode(false)
                showReplugNoticeDialog = true
            }
        }
    }

    val isResamplingActive = effectiveDecoderRate > 0 && effectiveOutputRate > 0 && effectiveDecoderRate != effectiveOutputRate

    val storageSourceLabel = when {
        resolvedSourceId?.startsWith("smb://", ignoreCase = true) == true ||
        sourceId?.startsWith("smb://", ignoreCase = true) == true ||
        requestUrl?.startsWith("smb://", ignoreCase = true) == true -> "SMB share"

        resolvedSourceId?.startsWith("http://", ignoreCase = true) == true ||
        resolvedSourceId?.startsWith("https://", ignoreCase = true) == true ||
        sourceId?.startsWith("http://", ignoreCase = true) == true ||
        sourceId?.startsWith("https://", ignoreCase = true) == true ||
        requestUrl?.startsWith("http://", ignoreCase = true) == true ||
        requestUrl?.startsWith("https://", ignoreCase = true) == true -> "HTTP stream"

        displayFile != null && isCachedRemoteSourceFile(displayFile) -> {
            val cachedSource = sourceIdForCachedFileName(cacheRoot, displayFile.name)
            if (cachedSource?.startsWith("smb://", ignoreCase = true) == true) "SMB share"
            else if (cachedSource?.startsWith("http://", ignoreCase = true) == true || cachedSource?.startsWith("https://", ignoreCase = true) == true) "HTTP stream"
            else "Local storage"
        }

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
                                val hasNativeRate = remember { NativeBridge.hasNativeSampleRate() }
                                val trackBitrate = remember { NativeBridge.getTrackBitrate() }
                                val isTrackVbr = remember { NativeBridge.isTrackVBR() }
                                val fileSubtitleItems = remember(hasNativeRate, trackSampleRateHz, effectiveChannels, bitDepthLabel) {
                                    val parts = mutableListOf<String>()
                                    if (hasNativeRate && trackSampleRateHz > 0) {
                                        parts.add(formatSampleRateForInspector(trackSampleRateHz))
                                    } else {
                                        parts.add("N/A")
                                    }
                                    if (effectiveChannels > 0) {
                                        parts.add("$effectiveChannels ch")
                                    }
                                    val cleanBitDepth = bitDepthLabel.trim().takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                                    if (cleanBitDepth != null) {
                                        parts.add(if (cleanBitDepth.endsWith("-bit", ignoreCase = true) || cleanBitDepth.endsWith("bit", ignoreCase = true)) cleanBitDepth else "$cleanBitDepth-bit")
                                    }
                                    if (hasNativeRate && trackBitrate > 0L) {
                                        val formattedBitrate = formatBitrateForInspector(trackBitrate, isTrackVbr)
                                        if (formattedBitrate.isNotBlank()) {
                                            parts.add(formattedBitrate)
                                        }
                                    }
                                    if (storageSourceLabel != "Local storage") {
                                        parts.add(storageSourceLabel)
                                    }
                                    parts
                                }

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
                                                    imageVector = Icons.Default.AudioFile,
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
                                    subtitleItems = fileSubtitleItems
                                )

                                // Connector: File -> Decoder
                                SignalChainConnector()

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
                                    subtitleItems = listOf(
                                        formatSampleRateForInspector(effectiveDecoderRate),
                                        "32-bit Float"
                                    )
                                )

                                // Optional Node 3: Resampler
                                if (isResamplingActive) {
                                    val resamplerTitle = when (activeResamplerPref) {
                                        AudioResamplerPreference.Sox -> "SoX resampler"
                                        AudioResamplerPreference.BuiltIn -> "Built-in resampler"
                                    }
                                    val resamplerEngine = when (activeResamplerPref) {
                                        AudioResamplerPreference.Sox -> "SoX (libsoxr)"
                                        AudioResamplerPreference.BuiltIn -> "miniaudio linear"
                                    }

                                    SignalChainConnector(
                                        label = "Resampling",
                                        highlightColor = Color(0xFFFFD54F)
                                    )

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
                                        subtitleItems = listOf(
                                            "${formatSampleRateForInspector(effectiveDecoderRate)} → ${formatSampleRateForInspector(effectiveOutputRate)}",
                                            resamplerEngine
                                        )
                                    )
                                }

                                // Connector to Output Sink
                                val bitPerfectHighlightColor = Color(0xFF4DD0E1)
                                SignalChainConnector(
                                    label = if (isBitPerfectActive) "Direct USB" else null,
                                    highlightColor = if (isBitPerfectActive) bitPerfectHighlightColor else null
                                )

                                // Node 4 (or 3): Output Endpoint
                                val sinkBadgeText = when (routeInfo.type) {
                                    AudioOutputRouteType.Speaker -> "Internal"
                                    AudioOutputRouteType.Headphones -> "Wired"
                                    AudioOutputRouteType.Usb -> if (isBitPerfectActive) "Bit-Perfect" else "USB"
                                    AudioOutputRouteType.Bluetooth -> "A2DP"
                                }
                                val sinkIconTint = if (isBitPerfectActive) bitPerfectHighlightColor else MaterialTheme.colorScheme.secondary
                                val sinkIconBg = if (isBitPerfectActive) bitPerfectHighlightColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                val sinkBadgeColor = if (isBitPerfectActive) bitPerfectHighlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                                val sinkBadgeBg = if (isBitPerfectActive) bitPerfectHighlightColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest

                                val sinkFormatText = if (isUacStreaming) {
                                    val bits = uacDiagnostics?.bitsPerSample?.takeIf { it > 0 } ?: 16
                                    "$bits-bit PCM"
                                } else {
                                    "16-bit PCM"
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
                                            tint = sinkIconTint
                                        )
                                    },
                                    iconBackgroundColor = sinkIconBg,
                                    title = routeInfo.name,
                                    badgeText = sinkBadgeText,
                                    badgeColor = sinkBadgeColor,
                                    badgeBackgroundColor = sinkBadgeBg,
                                    subtitleItems = listOf(
                                        formatSampleRateForInspector(effectiveOutputRate),
                                        sinkFormatText
                                    )
                                )
                            }
                        }
                    }

                    // Section: Stream Metrics
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialogSectionLabel(
                            text = "Stream metrics",
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
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricListRow(
                                    label = "Active backend",
                                    value = backendLabel,
                                    sub = "miniaudio"
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                MetricListRow(
                                    label = "Buffer & latency",
                                    value = String.format(Locale.US, "%.1f ms", latencyMs),
                                    sub = "$effectiveBurstFrames frames"
                                )
                            }
                        }
                    }

                    // Section: Bit-Perfect Routing (if USB route)
                    if (routeInfo.type == AudioOutputRouteType.Usb) {
                        val supportedRates = remember(context) {
                            BitPerfectCoordinator.getUsbDeviceSupportedSampleRates(context)
                        }
                        val isUac1 = remember(context) {
                            BitPerfectCoordinator.isConnectedUsbAudioUac1(context)
                        }
                        val uacLabel = if (isUac1) "UAC 1.0" else "UAC 2.0"

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DialogSectionLabel(
                                text = "Bit-perfect routing",
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
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Lossless USB direct access",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val (statusText, statusColor) = when (bitPerfectSupportStatus) {
                                                BitPerfectSupportStatus.Supported -> Pair("Bypasses Android audio mixer for bit-perfect output.", MaterialTheme.colorScheme.onSurfaceVariant)
                                                BitPerfectSupportStatus.UnsupportedAudioHal -> Pair("Platform bit-perfect API is not supported by this device's audio HAL.", MaterialTheme.colorScheme.error)
                                                BitPerfectSupportStatus.UnsupportedApiLevel -> Pair("Platform bit-perfect USB routing requires Android 14 or higher.", MaterialTheme.colorScheme.error)
                                                BitPerfectSupportStatus.NoUsbDeviceConnected -> Pair("No compatible USB audio device connected.", MaterialTheme.colorScheme.error)
                                            }
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = statusColor
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            val driverBadge = "Driver: ${driverMethod.displayName}"
                                            Text(
                                                text = driverBadge,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isBitPerfectSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (driverMethod == BitPerfectDriverMethod.DirectUac && uacLastError != null) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = "Error: $uacLastError",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Switch(
                                            checked = bitPerfectEnabled && isBitPerfectSupported,
                                            onCheckedChange = onToggleBitPerfect,
                                            enabled = isBitPerfectSupported
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Supported Rates ($uacLabel)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            supportedRates.forEach { rate ->
                                                val isActive = rate == effectiveOutputRate
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                    )
                                                ) {
                                                    Text(
                                                        text = formatSampleRateForInspector(rate),
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (bitPerfectEnabled && isBitPerfectSupported && driverMethod == BitPerfectDriverMethod.DirectUac) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "DAC Volume Scaling",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = when (uacVolumeMode) {
                                                            DirectUacVolumeMode.None -> "None (0 dBFS unity gain)"
                                                            DirectUacVolumeMode.System -> "Match Android system volume"
                                                            DirectUacVolumeMode.Manual -> "Manual slider"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                FilledTonalButton(
                                                    onClick = { showVolumeModeDialog = true },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = uacVolumeMode.displayName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            if (uacVolumeMode == DirectUacVolumeMode.Manual) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Slider(
                                                        value = uacManualVolume,
                                                        onValueChange = { newVol ->
                                                            UacDriverCoordinator.setManualVolume(context, newVol)
                                                        },
                                                        valueRange = 0f..1f,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "${(uacManualVolume * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.width(36.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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

    if (showRestartConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmDialog = false },
            title = {
                Text(
                    text = "Restart playback?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Changing bit-perfect audio for $effectiveDecoderName won't take effect until playback is restarted. Would you like to restart playback now?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartConfirmDialog = false
                        if (pendingBitPerfectState) {
                            if (driverMethod == BitPerfectDriverMethod.DirectUac) {
                                val rawUsb = UacDriverCoordinator.findUsbAudioDevice(context)
                                if (rawUsb != null) {
                                    coroutineScope.launch {
                                        val granted = UacDriverCoordinator.requestPermission(context, rawUsb)
                                        if (granted) {
                                            UacDriverCoordinator.open(context, rawUsb)
                                            onBitPerfectToggled(true)
                                            val targetRate = effectiveDecoderRate
                                            val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                            val ok = UacDriverCoordinator.start(targetRate, targetBitDepth, 2)
                                            NativeBridge.setBitPerfectMode(ok)
                                            if (ok) {
                                                onRestartTrack()
                                            } else {
                                                onBitPerfectToggled(false)
                                            }
                                        } else {
                                            onBitPerfectToggled(false)
                                        }
                                    }
                                } else {
                                    onBitPerfectToggled(false)
                                }
                            } else {
                                onBitPerfectToggled(true)
                                val usbDevice = BitPerfectCoordinator.findConnectedUsbAudioDevice(context)
                                if (usbDevice != null) {
                                    BitPerfectCoordinator.setPreferredBitPerfectMixer(context, usbDevice, effectiveDecoderRate, effectiveChannels)
                                    NativeBridge.setBitPerfectMode(true)
                                }
                                onRestartTrack()
                            }
                        } else {
                            UacDriverCoordinator.close()
                            BitPerfectCoordinator.clearBitPerfectMixer(context)
                            NativeBridge.setBitPerfectMode(false)
                            onBitPerfectToggled(false)
                            showReplugNoticeDialog = true
                            onRestartTrack()
                        }
                    }
                ) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestartConfirmDialog = false
                        onBitPerfectToggled(pendingBitPerfectState)
                        if (!pendingBitPerfectState) {
                            UacDriverCoordinator.close()
                            BitPerfectCoordinator.clearBitPerfectMixer(context)
                            NativeBridge.setBitPerfectMode(false)
                            showReplugNoticeDialog = true
                        }
                    }
                ) {
                    Text("Keep playing")
                }
            }
        )
    }

    if (showReplugNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showReplugNoticeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Device reconnect notice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Disabling direct USB audio while a device is active releases exclusive hardware control. To route audio through Android's standard audio mixer again, you may need to unplug and reconnect your USB audio device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showReplugNoticeDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    if (showVolumeModeDialog) {
        SettingsSingleChoiceDialog(
            title = "DAC Volume Scaling",
            selectedValue = uacVolumeMode.storageValue,
            options = DirectUacVolumeMode.entries.map {
                ChoiceDialogOption(
                    value = it.storageValue,
                    label = it.displayName
                )
            },
            onSelected = { selectedStorage ->
                val selected = DirectUacVolumeMode.fromStorage(selectedStorage)
                UacDriverCoordinator.setVolumeMode(context, selected)
                showVolumeModeDialog = false
            },
            onDismiss = { showVolumeModeDialog = false }
        )
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
    subtitleItems: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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

                BulletWrappingRow(
                    items = subtitleItems,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BulletWrappingRow(
    items: List<String>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val itemColor = MaterialTheme.colorScheme.onSurfaceVariant
    val separatorColor = MaterialTheme.colorScheme.outlineVariant

    Layout(
        content = {
            // 0 until N: item texts
            items.forEach { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = itemColor,
                    fontSize = 12.sp
                )
            }
            // N until 2N - 1: distinct bullet separators
            repeat((items.size - 1).coerceAtLeast(0)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = separatorColor,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val itemCount = items.size
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
                // Line wrap: save line without trailing bullet
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

        val lineSpacing = 2.dp.roundToPx()
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
private fun SignalChainConnector(
    label: String? = null,
    highlightColor: Color? = null
) {
    val lineColor = highlightColor?.copy(alpha = 0.5f) ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Vertical Connector Line on the left (aligned with icon centers)
        Box(
            modifier = Modifier
                .padding(start = 27.dp)
                .width(2.dp)
                .height(if (label != null) 24.dp else 16.dp)
                .background(lineColor)
        )

        // Optional Centered Pill
        if (!label.isNullOrBlank()) {
            val pillBg = highlightColor?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surfaceContainerHighest
            val pillBorder = highlightColor?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            val contentColor = highlightColor ?: MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                modifier = Modifier.padding(start = 40.dp),
                shape = RoundedCornerShape(6.dp),
                color = pillBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, pillBorder)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    fontSize = 10.sp
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

private fun formatBitrateForInspector(bitrateBps: Long, isVbr: Boolean): String {
    if (bitrateBps <= 0L) return ""
    val kbps = bitrateBps / 1000.0
    val prefix = if (isVbr) "~" else ""
    return when {
        kbps >= 1000 -> String.format(Locale.US, "%s%.1f Mbps", prefix, kbps / 1000.0)
        else -> String.format(Locale.US, "%s%.0f kbps", prefix, kbps)
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
