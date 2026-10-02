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
    return NativeTrackSnapshot(
        decoderName = decoder,
        title = NativeBridge.getTrackTitle(),
        artist = NativeBridge.getTrackArtist(),
        album = NativeBridge.getTrackAlbum(),
        sampleRateHz = NativeBridge.getTrackSampleRate(),
        hasNativeSampleRate = NativeBridge.hasNativeSampleRate(),
        channelCount = NativeBridge.getTrackChannelCount(),
        bitDepthLabel = NativeBridge.getTrackBitDepthLabel(),
        repeatModeCapabilitiesFlags = NativeBridge.getRepeatModeCapabilities(),
        playbackCapabilitiesFlags = NativeBridge.getPlaybackCapabilities(),
        durationSeconds = NativeBridge.getDuration(),
        subtuneCount = NativeBridge.getSubtuneCount(),
        currentSubtuneIndex = NativeBridge.getCurrentSubtuneIndex()
    )
}

internal fun readCurrentDecoderName(): String? {
    return NativeBridge.getCurrentDecoderName().trim().takeIf { it.isNotEmpty() }
}

internal fun readCurrentFormatName(decoderName: String?): String? {
    if (decoderName.isNullOrBlank()) return null
    return try {
        when {
            decoderName.equals(DecoderNames.PLATFORM_DOLBY, ignoreCase = true) -> {
                // The player-facing format line stays stream-level (what
                // FFmpeg reports for the file); the platform codec component
                // is only surfaced in the track info dialog.
                val container = NativeBridge.getFfmpegContainerName().trim()
                val codec = NativeBridge.getFfmpegCodecName().trim()
                when {
                    container.isNotEmpty() -> container
                    codec.isNotEmpty() -> codec
                    else -> null
                }
            }
            decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true) -> {
                val container = NativeBridge.getFfmpegContainerName().trim()
                val codec = NativeBridge.getFfmpegCodecName().trim()
                when {
                    container.isNotEmpty() -> container
                    codec.isNotEmpty() -> codec
                    else -> null
                }
            }
            decoderName.equals(DecoderNames.LIB_OPEN_MPT, ignoreCase = true) -> {
                NativeBridge.getOpenMptModuleTypeLong().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.VGM_PLAY, ignoreCase = true) -> {
                val system = NativeBridge.getVgmSystemName().trim()
                if (system.isNotEmpty()) "VGM ($system)" else null
            }
            decoderName.equals(DecoderNames.GAME_MUSIC_EMU, ignoreCase = true) -> {
                NativeBridge.getGmeSystemName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.C_RSID, ignoreCase = true) ||
            decoderName.equals(DecoderNames.LIB_SID_PLAY_FP, ignoreCase = true) -> {
                NativeBridge.getSidFormatName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.SC68, ignoreCase = true) -> {
                NativeBridge.getSc68FormatName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.HIVELY_TRACKER, ignoreCase = true) -> {
                NativeBridge.getHivelyFormatName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.matchesDecoderName(DecoderNames.KLYSTRACK) -> {
                NativeBridge.getKlystrackFormatName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.LIB_DN_FAMITRACKER, ignoreCase = true) -> {
                NativeBridge.getDnfamitrackerFormatName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.FURNACE, ignoreCase = true) -> {
                NativeBridge.getFurnaceFormatName().trim().takeIf { it.isNotEmpty() }
            }
            decoderName.equals(DecoderNames.UADE, ignoreCase = true) -> {
                val detected = NativeBridge.getUadeDetectedFormatName().trim()
                val format = NativeBridge.getUadeFormatName().trim()
                when {
                    detected.isNotEmpty() -> detected
                    format.isNotEmpty() -> format
                    else -> null
                }
            }
            else -> null
        }
    } catch (e: Throwable) {
        null
    }
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

