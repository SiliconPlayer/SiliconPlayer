package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flopster101.siliconplayer.EndFadeCurve
import com.flopster101.siliconplayer.TrackTextInputActive
import com.flopster101.siliconplayer.VerticalScrollbarTrack
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.export.AudioExportCodec
import com.flopster101.siliconplayer.export.AudioExportContainer
import com.flopster101.siliconplayer.export.AudioExportDialogSource
import com.flopster101.siliconplayer.export.AudioExportEncoder
import com.flopster101.siliconplayer.export.AudioExportExecutionState
import com.flopster101.siliconplayer.export.AudioExportFfmpegMode
import com.flopster101.siliconplayer.export.AudioExportPlatformAdapter
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.AudioExportRepeatChoice
import com.flopster101.siliconplayer.export.AudioExportSampleFormat
import com.flopster101.siliconplayer.export.AudioExportUiRequest
import com.flopster101.siliconplayer.export.audioExportBitrates
import com.flopster101.siliconplayer.export.audioExportSubtuneLabel
import com.flopster101.siliconplayer.export.defaultAudioExportBitrate
import com.flopster101.siliconplayer.export.easyFfmpegOutputArguments
import com.flopster101.siliconplayer.export.formatAudioExportBitrate
import com.flopster101.siliconplayer.export.formatAudioExportSizeEstimate
import com.flopster101.siliconplayer.export.mediaCodecContainer
import com.flopster101.siliconplayer.export.suggestedAudioExportFileName
import com.flopster101.siliconplayer.export.supportedAudioExportContainers
import com.flopster101.siliconplayer.formatTime
import com.flopster101.siliconplayer.onSizeChangedDeferred
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import com.flopster101.siliconplayer.rememberScrollStateScrollbarDragHandler
import com.flopster101.siliconplayer.ui.theme.Radius

@Composable
internal fun AudioExportDialog(
    source: AudioExportDialogSource,
    platform: AudioExportPlatformAdapter,
    onDismiss: () -> Unit
) {
    TrackTextInputActive()
    var exportStarted by rememberSaveable { mutableStateOf(false) }
    var observedRunning by rememberSaveable { mutableStateOf(false) }
    val executionState = platform.executionState
    val showingProgress = executionState.running || exportStarted || observedRunning
    LaunchedEffect(executionState.running) {
        if (executionState.running) {
            observedRunning = true
        } else if (exportStarted || observedRunning) {
            exportStarted = false
            observedRunning = false
            onDismiss()
        }
    }
    var encoder by rememberSaveable { mutableStateOf(platform.encoders.first()) }
    var container by rememberSaveable { mutableStateOf(AudioExportContainer.M4a) }
    var codec by rememberSaveable { mutableStateOf(AudioExportCodec.Aac) }
    var sampleRateHz by rememberSaveable { mutableIntStateOf(48_000) }
    var bitrateBps by rememberSaveable { mutableIntStateOf(256_000) }
    var ffmpegMode by rememberSaveable { mutableStateOf(AudioExportFfmpegMode.Easy) }
    var ffmpegArguments by rememberSaveable { mutableStateOf("-c:a aac -b:a 256k") }
    var includeMetadata by rememberSaveable { mutableStateOf(true) }
    var useActiveGain by rememberSaveable { mutableStateOf(true) }
    var useActiveEffects by rememberSaveable { mutableStateOf(true) }
    var customDuration by rememberSaveable { mutableStateOf(false) }
    var durationSecondsText by rememberSaveable { mutableStateOf("120") }
    var repeatChoice by rememberSaveable { mutableStateOf(AudioExportRepeatChoice.None) }
    var fadeDurationMs by rememberSaveable { mutableIntStateOf(0) }
    var fadeCurve by rememberSaveable { mutableStateOf(EndFadeCurve.EaseOut) }
    var timingExpanded by rememberSaveable { mutableStateOf(false) }
    var processingExpanded by rememberSaveable { mutableStateOf(false) }

    val durationCapSeconds = if (customDuration) durationSecondsText.toDoubleOrNull() else null
    val invalidDuration = customDuration && (durationCapSeconds == null || durationCapSeconds <= 0.0)
    val externalBinaryMissing = encoder == AudioExportEncoder.ExternalFfmpeg &&
        platform.externalBinaryLocation == null
    val destinationLocation = platform.destinationLocation
    val usesFfmpeg = encoder != AudioExportEncoder.MediaCodec
    fun containersFor(choice: AudioExportCodec): List<AudioExportContainer> {
        return supportedAudioExportContainers(choice, encoder).filter {
            it != AudioExportContainer.WebM || encoder != AudioExportEncoder.MediaCodec ||
                platform.mediaCodecSupportsWebM
        }
    }
    val canExport = destinationLocation != null && !externalBinaryMissing && !invalidDuration &&
        (encoder != AudioExportEncoder.MediaCodec || platform.supportedMediaCodecs.contains(codec)) &&
        container in containersFor(codec)
    val isLossless = codec == AudioExportCodec.Flac || codec == AudioExportCodec.Pcm24
    val suggestedFileName = suggestedAudioExportFileName(source.title, container)
    LaunchedEffect(source.sourceId) {
        platform.resetDestination(suggestedFileName)
    }
    val sizeEstimate = formatAudioExportSizeEstimate(
        durationSeconds = durationCapSeconds ?: source.durationSeconds,
        container = container,
        codec = codec,
        sampleRateHz = sampleRateHz,
        bitrateBps = bitrateBps
    )
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val thumbFraction = remember(scrollState.maxValue, viewportHeightPx) {
        if (viewportHeightPx <= 0f) 1f else {
            val contentHeight = viewportHeightPx + scrollState.maxValue.toFloat()
            (viewportHeightPx / contentHeight).coerceIn(0.08f, 1f)
        }
    }
    val offsetFraction = remember(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = scrollState,
        label = "audioExportDialogScrollbarAlpha"
    )
    val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)

    Dialog(onDismissRequest = onDismiss, properties = adaptiveDialogProperties()) {
        Surface(
            modifier = adaptiveDialogModifier().heightIn(max = 560.dp),
            shape = Radius.Dialog,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showingProgress) "Exporting audio" else "Export",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (showingProgress) Modifier else Modifier.weight(1f, fill = false)
                        )
                        .onSizeChangedDeferred { viewportHeightPx = it.height.toFloat() }
                ) {
                    if (showingProgress) {
                        AudioExportProgressContent(source, executionState)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp)
                                .verticalScroll(scrollState)
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AudioExportSourceCard(source)
                            Column {
                                AudioExportSectionLabel("Output")
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = true,
                                        onClick = {},
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        icon = {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    ) {
                                        Text("Audio")
                                    }
                                    SegmentedButton(
                                        selected = false,
                                        onClick = {},
                                        enabled = false,
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        icon = {
                                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    ) {
                                        Text("Video")
                                    }
                                }
                            }

                            Column {
                                AudioExportDropdown(
                                    label = "Encoder",
                                    value = encoder,
                                    values = platform.encoders,
                                    valueLabel = { it.label },
                                    onValueChange = {
                                        encoder = it
                                        platform.resetDestination(suggestedFileName)
                                        if (it == AudioExportEncoder.MediaCodec) {
                                            codec = platform.supportedMediaCodecs.minByOrNull { codec ->
                                                codec.ordinal
                                            } ?: AudioExportCodec.Aac
                                            container = codec.mediaCodecContainer()
                                            bitrateBps = defaultAudioExportBitrate(codec) ?: 256_000
                                            if (codec == AudioExportCodec.AmrNb) sampleRateHz = 8_000
                                            if (codec == AudioExportCodec.Opus) sampleRateHz = 48_000
                                        }
                                    }
                                )
                                if (encoder == AudioExportEncoder.ExternalFfmpeg) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            AudioExportFieldLabel("FFmpeg binary")
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                                shape = Radius.Control,
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = platform.externalBinaryLabel ?: "No binary imported",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = platform::chooseExternalBinary,
                                            shape = Radius.ControlCompact
                                        ) {
                                            Text(platform.externalBinaryActionLabel)
                                        }
                                    }
                                }
                                if (usesFfmpeg) {
                                    SingleChoiceSegmentedButtonRow(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                    ) {
                                        AudioExportFfmpegMode.entries.forEachIndexed { index, mode ->
                                            SegmentedButton(
                                                selected = ffmpegMode == mode,
                                                onClick = { ffmpegMode = mode },
                                                shape = SegmentedButtonDefaults.itemShape(
                                                    index = index,
                                                    count = AudioExportFfmpegMode.entries.size
                                                )
                                            ) {
                                                Text(mode.label)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AudioExportDropdown(
                                    label = "Codec",
                                    value = codec,
                                    values = if (usesFfmpeg) {
                                        AudioExportCodec.entries
                                    } else {
                                        platform.supportedMediaCodecs.sortedBy { it.ordinal }
                                    },
                                    valueLabel = { it.label },
                                    onValueChange = {
                                        codec = it
                                        val containers = containersFor(it)
                                        if (container !in containers) container = containers.first()
                                        bitrateBps = defaultAudioExportBitrate(it) ?: 256_000
                                        if (it == AudioExportCodec.AmrNb) sampleRateHz = 8_000
                                        if (it == AudioExportCodec.Opus) sampleRateHz = 48_000
                                        platform.resetDestination(suggestedFileName)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                AudioExportDropdown(
                                    label = "Container",
                                    value = container,
                                    values = containersFor(codec),
                                    valueLabel = { it.label },
                                    onValueChange = {
                                        container = it
                                        platform.resetDestination(suggestedFileName)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!isLossless) {
                                    AudioExportDropdown(
                                        label = "Quality",
                                        value = bitrateBps,
                                        values = audioExportBitrates(codec),
                                        valueLabel = ::formatAudioExportBitrate,
                                        onValueChange = { bitrateBps = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                AudioExportDropdown(
                                    label = "Sample rate",
                                    value = sampleRateHz,
                                    values = when (codec) {
                                        AudioExportCodec.AmrNb -> listOf(8_000)
                                        AudioExportCodec.Opus -> listOf(48_000)
                                        else -> listOf(44_100, 48_000, 96_000)
                                    },
                                    valueLabel = { "${it / 1000.0} kHz".replace(".0 ", " ") },
                                    onValueChange = { sampleRateHz = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (usesFfmpeg && ffmpegMode == AudioExportFfmpegMode.Advanced) {
                                Column {
                                    AudioExportFieldLabel("Output arguments")
                                    OutlinedTextField(
                                        value = ffmpegArguments,
                                        onValueChange = { ffmpegArguments = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        singleLine = true,
                                        shape = Radius.Control
                                    )
                                }
                            }

                            AudioExportSwitchRow(
                                title = "Include metadata",
                                subtitle = "Title, artist, album, and other decoder tags",
                                checked = includeMetadata,
                                onCheckedChange = { includeMetadata = it }
                            )
                            AudioExportCollapsibleSection(
                                title = "Timing and fades",
                                summary = "${formatTime(durationCapSeconds ?: source.durationSeconds)} · " +
                                    if (fadeDurationMs == 0) "no fade" else "${fadeDurationMs / 1000} s fade",
                                expanded = timingExpanded,
                                onToggle = { timingExpanded = !timingExpanded }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AudioExportDropdown(
                                        label = "Duration",
                                        value = customDuration,
                                        values = listOf(false, true),
                                        valueLabel = { if (it) "Custom" else "Full track" },
                                        onValueChange = { customDuration = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    AudioExportDropdown(
                                        label = "Repeats",
                                        value = repeatChoice,
                                        values = listOf(
                                            AudioExportRepeatChoice.None,
                                            AudioExportRepeatChoice.TrackTwice
                                        ),
                                        valueLabel = { it.label },
                                        onValueChange = { repeatChoice = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (customDuration) {
                                    OutlinedTextField(
                                        value = durationSecondsText,
                                        onValueChange = { durationSecondsText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Duration in seconds") },
                                        isError = invalidDuration,
                                        singleLine = true,
                                        shape = Radius.Control
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AudioExportDropdown(
                                        label = "End fade",
                                        value = fadeDurationMs,
                                        values = listOf(0, 3_000, 5_000, 10_000),
                                        valueLabel = { if (it == 0) "None" else "${it / 1000} seconds" },
                                        onValueChange = { fadeDurationMs = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    AudioExportDropdown(
                                        label = "Fade curve",
                                        value = fadeCurve,
                                        values = EndFadeCurve.entries,
                                        valueLabel = { it.label },
                                        onValueChange = { fadeCurve = it },
                                        modifier = Modifier.weight(1f),
                                        enabled = fadeDurationMs > 0
                                    )
                                }
                            }
                            AudioExportCollapsibleSection(
                                title = "Audio processing",
                                summary = "Gain ${if (useActiveGain) "on" else "off"} · effects " +
                                    if (useActiveEffects) "on" else "off",
                                expanded = processingExpanded,
                                onToggle = { processingExpanded = !processingExpanded }
                            ) {
                                AudioExportSwitchRow(
                                    title = "Use active gain",
                                    checked = useActiveGain,
                                    onCheckedChange = { useActiveGain = it }
                                )
                                AudioExportSwitchRow(
                                    title = "Use active effects",
                                    checked = useActiveEffects,
                                    onCheckedChange = { useActiveEffects = it }
                                )
                            }
                            Column {
                                AudioExportSectionLabel("Destination")
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = Radius.Card,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = platform.destinationLabel ?: suggestedFileName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (destinationLocation == null) {
                                                    "Choose destination"
                                                } else {
                                                    platform.destinationDescription ?: destinationLocation
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { platform.chooseDestination(suggestedFileName) }) {
                                            Icon(Icons.Default.Folder, contentDescription = "Choose destination")
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Estimated size",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = sizeEstimate,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (scrollState.maxValue > 0 && viewportHeightPx > 0f) {
                        VerticalScrollbarTrack(
                            thumbFraction = thumbFraction,
                            offsetFraction = offsetFraction,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(4.dp)
                                .fillMaxHeight()
                                .graphicsLayer(alpha = scrollbarAlpha),
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                            onDragFractionChanged = dragToFraction
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showingProgress) {
                        TextButton(onClick = platform::cancelExport, shape = Radius.ControlCompact) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onDismiss, shape = Radius.ControlCompact) {
                            Text("Run in background")
                        }
                    } else {
                        TextButton(onClick = onDismiss, shape = Radius.ControlCompact) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val destination = destinationLocation ?: return@Button
                                exportStarted = true
                                platform.startExport(
                                    AudioExportUiRequest(
                                        source = source,
                                        encoder = encoder,
                                        container = container,
                                        codec = codec,
                                        bitrateBps = bitrateBps.takeUnless { isLossless },
                                        ffmpegMode = ffmpegMode,
                                        ffmpegArguments = if (!usesFfmpeg) {
                                            ""
                                        } else if (ffmpegMode == AudioExportFfmpegMode.Advanced) {
                                            ffmpegArguments
                                        } else {
                                            easyFfmpegOutputArguments(codec, bitrateBps.takeUnless { isLossless })
                                                .joinToString(" ")
                                        },
                                        externalBinaryLocation = platform.externalBinaryLocation,
                                        destinationLocation = destination,
                                        includeMetadata = includeMetadata,
                                        repeatChoice = repeatChoice,
                                        renderOptions = AudioExportRenderOptions(
                                            sampleRateHz = sampleRateHz,
                                            sampleFormat = AudioExportSampleFormat.Pcm24,
                                            durationCapSeconds = durationCapSeconds,
                                            endFadeDurationMs = fadeDurationMs,
                                            endFadeCurve = fadeCurve,
                                            useActiveGain = useActiveGain,
                                            useActiveEffects = useActiveEffects
                                        )
                                    )
                                )
                                if (!platform.executionState.running) exportStarted = false
                            },
                            enabled = canExport,
                            shape = Radius.ControlCompact
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export audio")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioExportProgressContent(
    source: AudioExportDialogSource,
    state: AudioExportExecutionState
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AudioExportSourceCard(source)
        Text(
            text = state.status.ifBlank { "Preparing export…" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${(progress.fraction * 100.0).toInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${formatTime(progress.renderedSeconds)} / ${formatTime(progress.totalSeconds)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioExportSourceCard(source: AudioExportDialogSource) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Active track",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = source.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(
                    audioExportSubtuneLabel(source.subtuneIndex, source.subtuneCount),
                    source.decoderName?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (source.durationSeconds > 0.0 && source.durationSeconds.isFinite()) {
                Text(
                    text = formatTime(source.durationSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioExportSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun AudioExportFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> AudioExportDropdown(
    label: String,
    value: T,
    values: List<T>,
    valueLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        AudioExportFieldLabel(label)
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .audioExportMenuAnchor(this, enabled)
                    .clip(Radius.Control)
                    .alpha(if (enabled) 1f else 0.38f),
                shape = Radius.Control,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = valueLabel(value),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                }
            }
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.clip(Radius.Control)
            ) {
                values.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(valueLabel(item), style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onValueChange(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioExportSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AudioExportCollapsibleSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}
