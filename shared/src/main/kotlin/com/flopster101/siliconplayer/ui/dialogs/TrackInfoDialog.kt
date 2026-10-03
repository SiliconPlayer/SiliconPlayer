package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.VerticalScrollbarTrack
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.onSizeChangedDeferred
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.PlatformBackHandler
import com.flopster101.siliconplayer.platform.isWatchDevice
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import com.flopster101.siliconplayer.rememberScrollStateScrollbarDragHandler
import com.flopster101.siliconplayer.ui.screens.TrackInfoCoreSections
import com.flopster101.siliconplayer.ui.screens.TrackInfoDetailsRow
import com.flopster101.siliconplayer.ui.screens.appendCoreTrackInfoCopyRows
import com.flopster101.siliconplayer.ui.screens.formatBitrate
import com.flopster101.siliconplayer.ui.screens.formatFileSize
import com.flopster101.siliconplayer.ui.screens.formatSampleRateForDetails
import com.flopster101.siliconplayer.formatDurationWithUnknown
import com.flopster101.siliconplayer.formatTime
import com.flopster101.siliconplayer.ui.screens.rememberTrackInfoLiveMetadata
import java.io.File

@Composable
internal fun TrackInfoDialog(
    file: File?,
    title: String,
    artist: String,
    decoderName: String?,
    isDialogVisible: Boolean = true,
    playbackSourceLabel: String? = null,
    pathOrUrl: String? = null,
    playlistTitle: String? = null,
    playlistFormatLabel: String? = null,
    playlistTrackCount: Int = 0,
    playlistPathOrUrl: String? = null,
    sampleRateHz: Int = 0,
    channelCount: Int = 0,
    bitDepthLabel: String = "",
    durationSeconds: Double = 0.0,
    hasReliableDuration: Boolean = true,
    onDismiss: () -> Unit
) {
    val toastHandler = LocalToastHandler.current
    val clipboardManager = LocalClipboardManager.current

    PlatformBackHandler(enabled = isDialogVisible) {
        onDismiss()
    }
    val liveMetadata = rememberTrackInfoLiveMetadata(
        filePath = file?.absolutePath,
        decoderName = decoderName,
        isDialogVisible = isDialogVisible
    )
    val detailsScrollState = rememberScrollState()
    val detailsFocusRequester = remember { FocusRequester() }
    val closeButtonFocusRequester = remember { FocusRequester() }
    val copyButtonFocusRequester = remember { FocusRequester() }
    var detailsViewportHeightPx by remember { mutableIntStateOf(0) }
    val detailsScrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = detailsScrollState,
        label = "trackInfoDetailsScrollbarAlpha"
    )
    val fileSizeBytes = file?.length() ?: 0L
    val filename = file?.name ?: "No file loaded"
    val extension = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "UNKNOWN"
    val decoderLabel = decoderName?.ifBlank { "Unknown" } ?: "Unknown"
    val bitrateLabel = if (liveMetadata.bitrate > 0L) {
        "${formatBitrate(liveMetadata.bitrate, liveMetadata.isVbr)} (${if (liveMetadata.isVbr) "VBR" else "CBR"})"
    } else {
        "Unavailable"
    }
    val audioBackendLabel = liveMetadata.audioBackendLabel.ifBlank { "(inactive)" }
    val lengthLabel = if (durationSeconds > 0.0) {
        formatDurationWithUnknown(durationSeconds, hasReliableDuration)
    } else {
        "Unavailable"
    }
    val channelsLabel = if (channelCount > 0) "$channelCount channels" else "Unknown"
    val depthLabel = bitDepthLabel.trim().takeIf { it.isNotBlank() && it != "-bit" } ?: "Unknown"
    val playlistCountLabel = when {
        playlistTrackCount <= 0 -> null
        playlistTrackCount == 1 -> "1 track"
        else -> "$playlistTrackCount tracks"
    }
    val trackRateLabel = if (liveMetadata.hasNativeSampleRate) {
        formatSampleRateForDetails(sampleRateHz)
    } else {
        "N/A"
    }
    val sampleRateChain =
        "$trackRateLabel -> " +
            "${formatSampleRateForDetails(liveMetadata.renderRateHz)} -> " +
            formatSampleRateForDetails(liveMetadata.outputRateHz)
    val pathOrUrlLabel = pathOrUrl?.ifBlank { "Unavailable" } ?: "Unavailable"
    val isWatch = isWatchDevice()
    LaunchedEffect(isDialogVisible, isWatch) {
        if (isDialogVisible && !isWatch) {
            runCatching { detailsFocusRequester.requestFocus() }
        }
    }
    val copyAllText = buildString {
        fun row(label: String, value: String) {
            append(label).append(": ").append(value).append('\n')
        }

        row("Filename", filename)
        row("Title", title)
        row("Artist", artist)
        if (liveMetadata.composer.isNotBlank()) row("Composer", liveMetadata.composer)
        if (liveMetadata.genre.isNotBlank()) row("Genre", liveMetadata.genre)
        if (liveMetadata.album.isNotBlank()) row("Album", liveMetadata.album)
        if (liveMetadata.year.isNotBlank()) row("Year", liveMetadata.year)
        if (liveMetadata.date.isNotBlank()) row("Date", liveMetadata.date)
        if (liveMetadata.copyrightText.isNotBlank()) row("Copyright", liveMetadata.copyrightText)
        if (liveMetadata.comment.isNotBlank()) row("Comment", liveMetadata.comment)
        row("Format", extension)
        row("Decoder", decoderLabel)
        playbackSourceLabel?.takeIf { it.isNotBlank() }?.let { row("Playback source", it) }
        row("File size", if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable")
        row("Sample rate chain", sampleRateChain)
        row("Bitrate", bitrateLabel)
        row("Length", lengthLabel)
        row("Audio channels", channelsLabel)
        row("Bit depth", depthLabel)
        row("Audio backend", audioBackendLabel)
        row("Path / URL", pathOrUrlLabel)
        playlistTitle?.takeIf { it.isNotBlank() }?.let { row("Playlist", it) }
        playlistFormatLabel?.takeIf { it.isNotBlank() }?.let { row("Playlist format", it) }
        playlistCountLabel?.let { row("Playlist tracks", it) }
        playlistPathOrUrl?.takeIf { it.isNotBlank() }?.let { row("Playlist path / URL", it) }
        appendCoreTrackInfoCopyRows(
            builder = this,
            decoderName = decoderName,
            sampleRateHz = sampleRateHz,
            metadata = liveMetadata
        )
    }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Track and decoder info",
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackInfoDetailsRow("Filename", filename)
                TrackInfoDetailsRow("Title", title)
                TrackInfoDetailsRow("Artist", artist)
                if (liveMetadata.composer.isNotBlank()) {
                    TrackInfoDetailsRow("Composer", liveMetadata.composer)
                }
                if (liveMetadata.genre.isNotBlank()) {
                    TrackInfoDetailsRow("Genre", liveMetadata.genre)
                }
                if (liveMetadata.album.isNotBlank()) {
                    TrackInfoDetailsRow("Album", liveMetadata.album)
                }
                if (liveMetadata.year.isNotBlank()) {
                    TrackInfoDetailsRow("Year", liveMetadata.year)
                }
                if (liveMetadata.date.isNotBlank()) {
                    TrackInfoDetailsRow("Date", liveMetadata.date)
                }
                if (liveMetadata.copyrightText.isNotBlank()) {
                    TrackInfoDetailsRow("Copyright", liveMetadata.copyrightText)
                }
                if (liveMetadata.comment.isNotBlank()) {
                    TrackInfoDetailsRow("Comment", liveMetadata.comment)
                }
                TrackInfoDetailsRow("Format", extension)
                TrackInfoDetailsRow("Decoder", decoderLabel)
                playbackSourceLabel?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playback source", it)
                }
                TrackInfoDetailsRow(
                    "File size",
                    if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable"
                )
                TrackInfoDetailsRow("Sample rate chain", sampleRateChain)
                TrackInfoDetailsRow("Bitrate", bitrateLabel)
                TrackInfoDetailsRow("Length", lengthLabel)
                TrackInfoDetailsRow("Audio channels", channelsLabel)
                TrackInfoDetailsRow("Bit depth", depthLabel)
                TrackInfoDetailsRow("Audio backend", audioBackendLabel)
                TrackInfoDetailsRow("Path / URL", pathOrUrlLabel)
                playlistTitle?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playlist", it)
                }
                playlistFormatLabel?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playlist format", it)
                }
                playlistCountLabel?.let {
                    TrackInfoDetailsRow("Playlist tracks", it)
                }
                playlistPathOrUrl?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playlist path / URL", it)
                }
                TrackInfoCoreSections(
                    decoderName = decoderName,
                    sampleRateHz = sampleRateHz,
                    metadata = liveMetadata
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(copyAllText.trim()))
                    toastHandler.showToast("Copied track and decoder info")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Copy all")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text("Track and decoder info") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChangedDeferred { detailsViewportHeightPx = it.height }
                            .dialogScrollableContentNavigation(
                                scrollState = detailsScrollState,
                                focusRequester = detailsFocusRequester,
                                viewportHeightPx = detailsViewportHeightPx,
                                actionFocusRequester = closeButtonFocusRequester
                            )
                            .padding(end = 10.dp)
                            .verticalScroll(detailsScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TrackInfoDetailsRow("Filename", filename)
                                TrackInfoDetailsRow("Title", title)
                                TrackInfoDetailsRow("Artist", artist)
                                if (liveMetadata.composer.isNotBlank()) {
                                    TrackInfoDetailsRow("Composer", liveMetadata.composer)
                                }
                                if (liveMetadata.genre.isNotBlank()) {
                                    TrackInfoDetailsRow("Genre", liveMetadata.genre)
                                }
                                if (liveMetadata.album.isNotBlank()) {
                                    TrackInfoDetailsRow("Album", liveMetadata.album)
                                }
                                if (liveMetadata.year.isNotBlank()) {
                                    TrackInfoDetailsRow("Year", liveMetadata.year)
                                }
                                if (liveMetadata.date.isNotBlank()) {
                                    TrackInfoDetailsRow("Date", liveMetadata.date)
                                }
                                if (liveMetadata.copyrightText.isNotBlank()) {
                                    TrackInfoDetailsRow("Copyright", liveMetadata.copyrightText)
                                }
                                if (liveMetadata.comment.isNotBlank()) {
                                    TrackInfoDetailsRow("Comment", liveMetadata.comment)
                                }
                                TrackInfoDetailsRow("Format", extension)
                                TrackInfoDetailsRow("Decoder", decoderLabel)
                                playbackSourceLabel?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playback source", it)
                                }
                                TrackInfoDetailsRow(
                                    "File size",
                                    if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable"
                                )
                                TrackInfoDetailsRow("Sample rate chain", sampleRateChain)
                                TrackInfoDetailsRow("Bitrate", bitrateLabel)
                                TrackInfoDetailsRow("Length", lengthLabel)
                                TrackInfoDetailsRow("Audio channels", channelsLabel)
                                TrackInfoDetailsRow("Bit depth", depthLabel)
                                TrackInfoDetailsRow("Audio backend", audioBackendLabel)
                                TrackInfoDetailsRow("Path / URL", pathOrUrlLabel)
                                playlistTitle?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playlist", it)
                                }
                                playlistFormatLabel?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playlist format", it)
                                }
                                playlistCountLabel?.let {
                                    TrackInfoDetailsRow("Playlist tracks", it)
                                }
                                playlistPathOrUrl?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playlist path / URL", it)
                                }
                                TrackInfoCoreSections(
                                    decoderName = decoderName,
                                    sampleRateHz = sampleRateHz,
                                    metadata = liveMetadata
                                )
                            }
                        }
                    }
                    if (detailsScrollState.maxValue > 0 && detailsViewportHeightPx > 0) {
                        TrackInfoDetailsScrollbar(
                            scrollState = detailsScrollState,
                            viewportHeightPx = detailsViewportHeightPx,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(6.dp)
                                .graphicsLayer(alpha = detailsScrollbarAlpha)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier
                        .focusRequester(copyButtonFocusRequester)
                        .focusProperties {
                            up = detailsFocusRequester
                            left = closeButtonFocusRequester
                        },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(copyAllText.trim()))
                        toastHandler.showToast("Copied track and decoder info")
                    }
                ) {
                    Text("Copy all")
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier
                        .focusRequester(closeButtonFocusRequester)
                        .focusProperties {
                            up = detailsFocusRequester
                            right = copyButtonFocusRequester
                        },
                    onClick = onDismiss
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
internal fun TrackInfoDetailsDialog(
    file: File?,
    title: String,
    artist: String,
    decoderName: String?,
    isDialogVisible: Boolean = true,
    playbackSourceLabel: String? = null,
    pathOrUrl: String? = null,
    playlistTitle: String? = null,
    playlistFormatLabel: String? = null,
    playlistTrackCount: Int = 0,
    playlistPathOrUrl: String? = null,
    sampleRateHz: Int = 0,
    channelCount: Int = 0,
    bitDepthLabel: String = "",
    durationSeconds: Double = 0.0,
    hasReliableDuration: Boolean = true,
    onDismiss: () -> Unit
) = TrackInfoDialog(
    file = file,
    title = title,
    artist = artist,
    decoderName = decoderName,
    isDialogVisible = isDialogVisible,
    playbackSourceLabel = playbackSourceLabel,
    pathOrUrl = pathOrUrl,
    playlistTitle = playlistTitle,
    playlistFormatLabel = playlistFormatLabel,
    playlistTrackCount = playlistTrackCount,
    playlistPathOrUrl = playlistPathOrUrl,
    sampleRateHz = sampleRateHz,
    channelCount = channelCount,
    bitDepthLabel = bitDepthLabel,
    durationSeconds = durationSeconds,
    hasReliableDuration = hasReliableDuration,
    onDismiss = onDismiss
)

@Composable
internal fun TrackInfoDetailsScrollbar(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0 || viewportHeightPx <= 0) return

    val viewport = viewportHeightPx.toFloat()
    val content = viewport + maxScroll.toFloat()
    val thumbFraction = (viewport / content).coerceIn(0f, 1f)
    val offsetFraction = if (maxScroll > 0) {
        scrollState.value.toFloat() / maxScroll.toFloat()
    } else {
        0f
    }
    val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)

    VerticalScrollbarTrack(
        thumbFraction = thumbFraction,
        offsetFraction = offsetFraction,
        modifier = modifier,
        minThumbHeight = 24.dp,
        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
        thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
        onDragFractionChanged = dragToFraction
    )
}
