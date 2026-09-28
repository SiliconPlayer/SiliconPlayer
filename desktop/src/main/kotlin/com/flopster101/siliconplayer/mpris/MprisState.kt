package com.flopster101.siliconplayer.mpris

internal const val MPRIS_NO_TRACK_PATH = "/org/mpris/MediaPlayer2/TrackList/NoTrack"

internal fun mprisTrackPath(trackNumber: Long): String = "/org/mpris/MediaPlayer2/Track/$trackNumber"

internal enum class MprisPlaybackStatus(val wire: String) {
    Playing("Playing"),
    Paused("Paused"),
    Stopped("Stopped");

    companion object {
        fun fromWire(value: String): MprisPlaybackStatus? = entries.firstOrNull { it.wire == value }
    }
}

internal enum class MprisLoopStatus(val wire: String) {
    None("None"),
    Track("Track"),
    Playlist("Playlist");

    companion object {
        fun fromWire(value: String): MprisLoopStatus? = entries.firstOrNull { it.wire == value }
    }
}

// One snapshot of everything MPRIS publishes. Diffing consecutive snapshots is
// what drives PropertiesChanged, so it must stay cheap to compute and free of
// engine work (position and status are read, never pushed by the engine).
internal data class MprisState(
    val trackId: String = MPRIS_NO_TRACK_PATH,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val lengthMicroseconds: Long = 0L,
    val artUrl: String? = null,
    val playbackStatus: MprisPlaybackStatus = MprisPlaybackStatus.Stopped,
    val loopStatus: MprisLoopStatus = MprisLoopStatus.None,
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canSeek: Boolean = false,
    val volume: Double = 1.0,
    val positionMicroseconds: Long = 0L
) {
    fun hasTrack(): Boolean = trackId != MPRIS_NO_TRACK_PATH
}

internal class MprisCommands(
    val playPause: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val stop: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekToMicroseconds: (Long) -> Unit,
    val setVolume: (Double) -> Unit,
    val setLoopStatus: (MprisLoopStatus) -> Unit,
    val quit: () -> Unit,
    val raise: () -> Unit,
    val openUri: (String) -> Unit
)
