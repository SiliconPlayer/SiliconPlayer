package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.flopster101.siliconplayer.export.AudioExportFadePlacement
import com.flopster101.siliconplayer.export.AudioExportFfmpegMode
import com.flopster101.siliconplayer.export.AudioExportLoopType
import com.flopster101.siliconplayer.export.AudioExportPlatformAdapter
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.AudioExportRepeatMode
import com.flopster101.siliconplayer.export.AudioExportSampleFormat
import com.flopster101.siliconplayer.export.AudioExportUiRequest
import com.flopster101.siliconplayer.export.audioExportBitrates
import com.flopster101.siliconplayer.export.audioExportEstimatedRemainingSeconds
import com.flopster101.siliconplayer.export.audioExportSubtuneLabel
import com.flopster101.siliconplayer.export.defaultAudioExportBitrate
import com.flopster101.siliconplayer.export.easyFfmpegOutputArguments
import com.flopster101.siliconplayer.export.formatAudioExportBitrate
import com.flopster101.siliconplayer.export.formatAudioExportByteRate
import com.flopster101.siliconplayer.export.formatAudioExportSizeEstimate
import com.flopster101.siliconplayer.export.parseAudioExportTime
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
    val completion = executionState.completion
    val showingProgress = executionState.running || exportStarted || observedRunning
    LaunchedEffect(executionState.running, completion) {
        if (executionState.running) {
            observedRunning = true
        } else if (completion != null) {
            exportStarted = false
        } else if (exportStarted || observedRunning) {
            exportStarted = false
            observedRunning = false
            onDismiss()
        }
    }
    if (completion != null) {
        AudioExportCompletionDialog(
            completion = completion,
            supportedActions = platform.completionActions,
            onShare = { platform.shareCompletion(completion) },
            onOpenFolder = { platform.openCompletionFolder(completion) },
            onPlayNow = { platform.playCompletion(completion) },
            onClose = {
                platform.clearCompletion()
                onDismiss()
            }
        )
        return
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
    var loopType by rememberSaveable(source.sourceId) {
        mutableStateOf(
            if (source.durationReliable) {
                AudioExportLoopType.Repeat
            } else {
                AudioExportLoopType.Duration
            }
        )
    }
    var repeatMode by rememberSaveable { mutableStateOf(AudioExportRepeatMode.LoopPoint) }
    var repeatCountText by rememberSaveable { mutableStateOf("1") }
    var loopDurationText by rememberSaveable(source.sourceId) { mutableStateOf(formatTime(source.durationSeconds)) }
    var fadeDurationMs by rememberSaveable { mutableIntStateOf(0) }
    var fadeCurve by rememberSaveable { mutableStateOf(EndFadeCurve.EaseOut) }
    var fadePlacement by rememberSaveable { mutableStateOf(AudioExportFadePlacement.Tail) }
    var timingExpanded by rememberSaveable { mutableStateOf(false) }
    var processingExpanded by rememberSaveable { mutableStateOf(false) }

    val durationCapSeconds = if (customDuration) durationSecondsText.toDoubleOrNull() else null
    val invalidDuration = customDuration && (durationCapSeconds == null || durationCapSeconds <= 0.0)
    val repeatCount = repeatCountText.toIntOrNull()
    val invalidRepeat = loopType == AudioExportLoopType.Repeat && (repeatCount == null || repeatCount <= 0)
    val loopDurationSeconds = parseAudioExportTime(loopDurationText)
    val invalidLoopDuration = loopType == AudioExportLoopType.Duration &&
        (loopDurationSeconds == null || loopDurationSeconds <= 0.0)
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
    val canExport = destinationLocation != null && !externalBinaryMissing && !invalidDuration && !invalidRepeat &&
        !invalidLoopDuration &&
        (encoder != AudioExportEncoder.MediaCodec || platform.supportedMediaCodecs.contains(codec)) &&
        container in containersFor(codec)
    val isLossless = codec == AudioExportCodec.Flac || codec == AudioExportCodec.Pcm24
    val suggestedFileName = suggestedAudioExportFileName(source.title, container)
    LaunchedEffect(source.sourceId) {
        platform.resetDestination(suggestedFileName)
    }
    val plannedAudioSeconds = if (loopType == AudioExportLoopType.Duration) {
        loopDurationSeconds ?: source.durationSeconds
    } else {
        (durationCapSeconds ?: source.durationSeconds) * (repeatCount ?: 1)
    }
    val fadeExtensionSeconds = if (fadePlacement == AudioExportFadePlacement.NextLoop) {
        fadeDurationMs / 1_000.0
    } else {
        0.0
    }
    val timingSummary = buildString {
        append(formatTime(plannedAudioSeconds))
        if (fadeDurationMs == 0) {
            append(" · no fade")
        } else if (fadeExtensionSeconds > 0.0) {
            append(" + ${fadeDurationMs / 1000} s fade")
        } else {
            append(" · ${fadeDurationMs / 1000} s fade")
        }
    }
    val sizeEstimate = formatAudioExportSizeEstimate(
        durationSeconds = plannedAudioSeconds + fadeExtensionSeconds,
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

    if (showingProgress) {
        AudioExportProgressDialog(source, platform, executionState, onDismiss)
    } else {
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
                                            }
                                            container = containersFor(codec).first()
                                            bitrateBps = defaultAudioExportBitrate(codec) ?: 256_000
                                            if (codec == AudioExportCodec.AmrNb) sampleRateHz = 8_000
                                            if (codec == AudioExportCodec.Opus) sampleRateHz = 48_000
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
                                            container = containersFor(it).first()
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
                                    subtitle = if (container == AudioExportContainer.Amr) {
                                        "Raw AMR does not support metadata"
                                    } else {
                                        "Title, artist, album, and other decoder tags"
                                    },
                                    checked = includeMetadata,
                                    enabled = container != AudioExportContainer.Amr,
                                    onCheckedChange = { includeMetadata = it }
                                )
                                AudioExportCollapsibleSection(
                                    title = "Timing and fades",
                                    summary = timingSummary,
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
                                            label = "Loop type",
                                            value = loopType,
                                            values = AudioExportLoopType.entries,
                                            valueLabel = { it.label },
                                            onValueChange = { loopType = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AudioExportInputField(
                                            label = if (loopType == AudioExportLoopType.Repeat) "Repeats" else "Loop duration",
                                            value = if (loopType == AudioExportLoopType.Repeat) repeatCountText else loopDurationText,
                                            onValueChange = { value ->
                                                if (loopType == AudioExportLoopType.Repeat) {
                                                    if (value.all(Char::isDigit)) repeatCountText = value
                                                } else if (value.all { it.isDigit() || it == ':' || it == '.' }) {
                                                    loopDurationText = value
                                                }
                                            },
                                            isError = invalidRepeat || invalidLoopDuration,
                                            keyboardType = if (loopType == AudioExportLoopType.Repeat) {
                                                KeyboardType.Number
                                            } else {
                                                KeyboardType.Text
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        AudioExportDropdown(
                                            label = "Repeat mode",
                                            value = repeatMode,
                                            values = AudioExportRepeatMode.entries,
                                            valueLabel = { it.label },
                                            onValueChange = { repeatMode = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (loopType == AudioExportLoopType.Repeat && !source.durationReliable) {
                                        Text(
                                            text = "This decoder does not report a track length, " +
                                                "so repeat passes may not line up with the track.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    if (fadeDurationMs > 0) {
                                        AudioExportDropdown(
                                            label = "Fade placement",
                                            value = fadePlacement,
                                            values = AudioExportFadePlacement.entries,
                                            valueLabel = { it.label },
                                            onValueChange = { fadePlacement = it },
                                            modifier = Modifier.fillMaxWidth()
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
                                            includeMetadata = includeMetadata && container != AudioExportContainer.Amr,
                                            loopType = loopType,
                                            repeatMode = repeatMode,
                                            repeatCount = if (loopType == AudioExportLoopType.Repeat) {
                                                requireNotNull(repeatCount)
                                            } else {
                                                1
                                            },
                                            loopDurationSeconds = if (loopType == AudioExportLoopType.Duration) {
                                                requireNotNull(loopDurationSeconds)
                                            } else {
                                                null
                                            },
                                            renderOptions = AudioExportRenderOptions(
                                                sampleRateHz = sampleRateHz,
                                                sampleFormat = AudioExportSampleFormat.Pcm24,
                                                durationCapSeconds = durationCapSeconds,
                                                endFadeDurationMs = fadeDurationMs,
                                                endFadeCurve = fadeCurve,
                                                endFadePlacement = fadePlacement,
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
}

@Composable
private fun AudioExportProgressDialog(
    source: AudioExportDialogSource,
    platform: AudioExportPlatformAdapter,
    state: AudioExportExecutionState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = adaptiveDialogProperties()) {
        val fade = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            fade.animateTo(1f, animationSpec = tween(durationMillis = 120))
        }
        Surface(
            modifier = adaptiveDialogModifier().graphicsLayer(alpha = fade.value),
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
                        text = "Exporting audio",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                AudioExportProgressContent(source, state)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = platform::cancelExport, shape = Radius.ControlCompact) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss, shape = Radius.ControlCompact) {
                        Text("Run in background")
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
            Row(modifier = Modifier.fillMaxWidth()) {
                AudioExportProgressStat(
                    label = "Elapsed",
                    value = formatTime(progress.elapsedSeconds),
                    modifier = Modifier.weight(1f)
                )
                AudioExportProgressStat(
                    label = "Est. remaining",
                    value = audioExportEstimatedRemainingSeconds(progress)?.let(::formatTime) ?: "—",
                    modifier = Modifier.weight(1f)
                )
                AudioExportProgressStat(
                    label = "Speed",
                    value = formatAudioExportByteRate(progress.writtenBytes, progress.elapsedSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AudioExportProgressStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
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

@Composable
private fun AudioExportInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    Column(modifier = modifier) {
        AudioExportFieldLabel(label)
        Surface(
            modifier = Modifier.fillMaxWidth().clip(Radius.Control),
            shape = Radius.Control,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        innerTextField()
                    }
                }
            )
        }
    }
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
