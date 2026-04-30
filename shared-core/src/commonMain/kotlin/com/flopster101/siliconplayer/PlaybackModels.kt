package com.flopster101.siliconplayer

data class NativeTrackSnapshot(
    val decoderName: String?,
    val title: String,
    val artist: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepthLabel: String,
    val repeatModeCapabilitiesFlags: Int,
    val playbackCapabilitiesFlags: Int,
    val durationSeconds: Double
)

data class SubtuneState(
    val count: Int,
    val currentIndex: Int
)

data class NativeSubtuneCursor(
    val count: Int,
    val index: Int
)

data class DeferredPlaybackSeek(
    val sourceId: String,
    val positionSeconds: Double
)

data class SnapshotApplicationResult(
    val decoderName: String?,
    val pluginVolumeDb: Float?,
    val title: String,
    val artist: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepthLabel: String,
    val repeatModeCapabilitiesFlags: Int,
    val playbackCapabilitiesFlags: Int,
    val durationSeconds: Double
)

data class SubtuneSelectionResult(
    val success: Boolean,
    val snapshot: NativeTrackSnapshot? = null,
    val durationSeconds: Double = 0.0,
    val isPlaying: Boolean = false,
    val sourceId: String? = null
)

data class LoadedTrackSelectionState(
    val snapshot: NativeTrackSnapshot,
    val initialSubtuneApplied: Boolean
)
