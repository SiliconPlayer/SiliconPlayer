package com.flopster101.siliconplayer

import android.content.Context
import android.widget.Toast
import java.io.File

internal data class NativeTrackSnapshot(
    val decoderName: String?,
    val title: String,
    val artist: String,
    val album: String,
    val sampleRateHz: Int,
    val hasNativeSampleRate: Boolean,
    val channelCount: Int,
    val bitDepthLabel: String,
    val repeatModeCapabilitiesFlags: Int,
    val playbackCapabilitiesFlags: Int,
    val durationSeconds: Double,
    val subtuneCount: Int,
    val currentSubtuneIndex: Int
)

internal fun readNativeTrackSnapshot(): NativeTrackSnapshot {
    val decoder = readCurrentDecoderName()
    val fileTitle = NativeBridge.getTrackTitle()
    val fileAlbum = NativeBridge.getTrackAlbum()
    val subtuneCount = NativeBridge.getSubtuneCount()
    val currentSubtuneIndex = NativeBridge.getCurrentSubtuneIndex().coerceIn(0, (subtuneCount - 1).coerceAtLeast(0))
    val subtuneTitle = if (subtuneCount > 1) NativeBridge.getSubtuneTitle(currentSubtuneIndex) else ""
    val resolvedText = resolveSubtuneNowPlayingText(fileTitle, fileAlbum, subtuneCount, subtuneTitle)
    return NativeTrackSnapshot(
        decoderName = decoder,
        title = resolvedText.title,
        artist = NativeBridge.getTrackArtist(),
        album = resolvedText.album,
        sampleRateHz = NativeBridge.getTrackSampleRate(),
        hasNativeSampleRate = NativeBridge.hasNativeSampleRate(),
        channelCount = NativeBridge.getTrackChannelCount(),
        bitDepthLabel = NativeBridge.getTrackBitDepthLabel(),
        repeatModeCapabilitiesFlags = NativeBridge.getRepeatModeCapabilities(),
        playbackCapabilitiesFlags = NativeBridge.getPlaybackCapabilities(),
        durationSeconds = NativeBridge.getDuration(),
        subtuneCount = subtuneCount,
        currentSubtuneIndex = currentSubtuneIndex
    )
}

internal fun readCurrentDecoderName(): String? {
    return NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() }
}

internal fun syncPlaybackServiceForState(
    context: Context,
    selectedFile: File?,
    sourceId: String?,
    requestUrl: String?,
    metadataTitle: String,
    metadataArtist: String,
    durationSeconds: Double,
    positionSeconds: Double,
    isPlaying: Boolean,
    preferredRepeatMode: RepeatMode,
    activeRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int,
    playbackCapabilitiesFlags: Int
) {
    val sanitizedTitle = sanitizeRemoteCachedMetadataTitle(metadataTitle, selectedFile)
    PlaybackService.syncFromUi(
        context = context,
        path = sourceId ?: selectedFile?.absolutePath,
        requestUrl = requestUrl,
        title = sanitizedTitle.ifBlank {
            selectedFile?.name?.let(::inferredDisplayTitleForName).orEmpty()
        },
        artist = metadataArtist.ifBlank { "Unknown Artist" },
        durationSeconds = durationSeconds,
        positionSeconds = positionSeconds,
        isPlaying = isPlaying,
        preferredRepeatMode = preferredRepeatMode,
        activeRepeatMode = activeRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        playbackCapabilitiesFlags = playbackCapabilitiesFlags
    )
}

internal fun maybeShowCoreOptionRestartToast(
    context: Context,
    coreName: String,
    selectedFile: File?,
    isPlaying: Boolean,
    policy: CoreOptionApplyPolicy,
    optionLabel: String?
) {
    if (policy != CoreOptionApplyPolicy.RequiresPlaybackRestart) return
    if (!isPlaying || selectedFile == null) return
    val currentDecoderName = NativeBridge.getCurrentDecoderName()
    if (!currentDecoderName.equals(coreName, ignoreCase = true)) return
    val name = optionLabel?.ifBlank { null } ?: "This option"
    Toast.makeText(
        context,
        "$name will apply after restarting playback",
        Toast.LENGTH_SHORT
    ).show()
}

