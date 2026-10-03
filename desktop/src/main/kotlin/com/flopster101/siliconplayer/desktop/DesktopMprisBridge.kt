package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.RECENT_ARTWORK_CACHE_DIR
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.mpris.DbusException
import com.flopster101.siliconplayer.mpris.MPRIS_NO_TRACK_PATH
import com.flopster101.siliconplayer.mpris.MprisCommands
import com.flopster101.siliconplayer.mpris.MprisLoopStatus
import com.flopster101.siliconplayer.mpris.MprisPlaybackStatus
import com.flopster101.siliconplayer.mpris.MprisService
import com.flopster101.siliconplayer.mpris.MprisState
import com.flopster101.siliconplayer.mpris.mprisTrackPath
import java.io.File
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.math.log10
import kotlin.math.pow

private const val MICROSECONDS_PER_SECOND = 1_000_000.0
// A failed cover load is retried, not pinned: the fetch races the player's
// own artwork load (and a sleepy NAS). Artless tracks stop after the cap
// instead of hammering the server every poll.
private const val MAX_COVER_LOAD_ATTEMPTS = 6
private const val COVER_LOAD_RETRY_INTERVAL_MS = 5_000L
private const val MPRIS_VOLUME_MIN_DB = -20f
private const val MPRIS_VOLUME_MAX_DB = 20f

// MPRIS OpenUri: a local file:// URI goes through the normal file path (and the
// Play-with gate), anything else is handled as a remote source.
internal fun resolveDesktopMprisOpenUriFile(uri: String): File? {
    val trimmed = uri.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.contains("://") && !trimmed.startsWith("file://", ignoreCase = true)) return null
    val path = runCatching { java.net.URI(trimmed).path }.getOrNull() ?: trimmed.removePrefix("file://")
    return File(path ?: return null).takeIf { it.isFile }
}

// MPRIS Raise has no Compose Desktop API; go through AWT on the event thread.
internal fun bringDesktopWindowToFront() {
    SwingUtilities.invokeLater {
        runCatching {
            java.awt.Window.getWindows().firstOrNull { it.isShowing }?.let { window ->
                window.toFront()
                window.requestFocus()
            }
        }
    }
}

// MPRIS speaks linear gain, the app's master control is in dB; these two are the
// only place that conversion happens.
internal fun masterGainDbToMprisVolume(decibels: Float, muted: Boolean): Double {
    if (muted) return 0.0
    return 10.0.pow((decibels / 20.0).toDouble()).coerceIn(0.0, 1.0)
}

internal fun mprisVolumeToMasterGainDb(volume: Double): Float {
    val linear = volume.coerceIn(0.0, 1.0)
    if (linear <= 0.0) return MPRIS_VOLUME_MIN_DB
    return (20.0 * log10(linear)).toFloat().coerceIn(MPRIS_VOLUME_MIN_DB, MPRIS_VOLUME_MAX_DB)
}

internal fun mprisLoopStatusForRepeatMode(repeatMode: RepeatMode): MprisLoopStatus = when (repeatMode) {
    RepeatMode.None -> MprisLoopStatus.None
    RepeatMode.Playlist -> MprisLoopStatus.Playlist
    RepeatMode.Track, RepeatMode.Subtune, RepeatMode.LoopPoint -> MprisLoopStatus.Track
}

internal fun repeatModeForMprisLoopStatus(loopStatus: MprisLoopStatus): RepeatMode = when (loopStatus) {
    MprisLoopStatus.None -> RepeatMode.None
    MprisLoopStatus.Track -> RepeatMode.Track
    MprisLoopStatus.Playlist -> RepeatMode.Playlist
}

// Reads the live session for everything MPRIS publishes and keeps one D-Bus
// service alive for the window's lifetime. Actions come from the composition,
// which owns the app's play/next/seek entry points.
internal class DesktopMprisBridge(private val session: DesktopPlaybackSession) {
    @Volatile
    private var actions: MprisCommands? = null

    @Volatile
    private var volumeProvider: () -> Double = { 1.0 }

    private var service: MprisService? = null
    private var trackIdentity: String? = null
    private var trackNumber = 0L
    private val coverExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "mpris-cover").apply { isDaemon = true }
    }

    @Volatile
    private var coverIdentity: String? = null

    @Volatile
    private var coverLoadInflight = false

    @Volatile
    private var coverAttempts = 0

    @Volatile
    private var coverLastAttemptAt = 0L

    @Volatile
    private var coverUrl: String? = null

    fun bind(commands: MprisCommands, volume: () -> Double) {
        actions = commands
        volumeProvider = volume
    }

    fun start() {
        if (service != null) return
        val created = MprisService(
            stateProvider = { buildState() },
            commandsProvider = { actions ?: throw DbusException("playback actions are not wired yet") },
            onLog = { message -> println("[SiliconPlayer] $message") }
        )
        service = created
        created.start()
    }

    fun stop() {
        service?.stop()
        service = null
    }

    @Synchronized
    private fun buildState(): MprisState {
        val currentFile = session.currentFile
        val identity = session.currentSourceId ?: currentFile?.absolutePath
        if (identity != trackIdentity) {
            trackIdentity = identity
            if (identity != null) trackNumber += 1L
        }
        val hasTrack = currentFile != null && identity != null
        refreshCover(identity)
        val lengthMicros = if (session.hasReliableDuration) {
            (session.durationSeconds * MICROSECONDS_PER_SECOND).toLong().coerceAtLeast(0L)
        } else {
            0L
        }
        return MprisState(
            trackId = if (hasTrack) mprisTrackPath(trackNumber) else MPRIS_NO_TRACK_PATH,
            title = session.title,
            artist = session.artist,
            album = session.album,
            lengthMicroseconds = lengthMicros,
            artUrl = coverUrl,
            playbackStatus = when {
                !session.canResume() -> MprisPlaybackStatus.Stopped
                session.isPlaying -> MprisPlaybackStatus.Playing
                else -> MprisPlaybackStatus.Paused
            },
            loopStatus = mprisLoopStatusForRepeatMode(session.repeatMode),
            canGoNext = hasTrack,
            canGoPrevious = hasTrack,
            // Clients skip unplayable players; a loaded track stays playable.
            canPlay = session.canResume(),
            canPause = session.isPlaying,
            canSeek = session.canSeek,
            volume = volumeProvider(),
            positionMicroseconds = (session.positionSeconds * MICROSECONDS_PER_SECOND).toLong().coerceAtLeast(0L)
        )
    }

    // Cover lookup is filesystem/network work (embedded-art decode, folder-cover
    // scan, JPEG encode), so it runs on its own thread: the D-Bus poll and every
    // method reply must stay IO-free or clients time out and drop the player.
    private fun refreshCover(identity: String?) {
        if (identity != coverIdentity) {
            coverIdentity = identity
            coverUrl = null
            coverAttempts = 0
            coverLoadInflight = false
            if (identity == null) return
            requestCoverLoad(identity)
            return
        }
        if (identity == null || coverUrl != null || coverLoadInflight) return
        if (coverAttempts >= MAX_COVER_LOAD_ATTEMPTS) return
        if (System.currentTimeMillis() - coverLastAttemptAt < COVER_LOAD_RETRY_INTERVAL_MS) return
        requestCoverLoad(identity)
    }

    private fun requestCoverLoad(identity: String) {
        coverLoadInflight = true
        coverAttempts += 1
        coverLastAttemptAt = System.currentTimeMillis()
        coverExecutor.execute {
            try {
                val url = loadCoverUrl(identity)
                if (url != null && coverIdentity == identity) {
                    coverUrl = url
                }
            } finally {
                // A newer track already owns the flag with its own queued task.
                if (coverIdentity == identity) {
                    coverLoadInflight = false
                }
            }
        }
    }

    // The recent-artwork cache already holds a JPEG per source, so the media
    // widget gets a file URL without a second encoder.
    private fun loadCoverUrl(identity: String): String? = runCatching {
        val cacheRoot = File(DesktopPaths.cacheDir(), RECENT_ARTWORK_CACHE_DIR)
        val cacheKey = ensureDesktopRecentArtworkCached(
            cacheRoot = cacheRoot,
            sourceId = identity,
            requestUrlHint = session.currentRequestUrl,
            requireLarge = true
        ) ?: return null
        desktopRecentArtworkCacheFile(cacheRoot, cacheKey, preferLarge = true)
            ?.takeIf { it.isFile && it.length() > 0L }
            ?.toURI()
            ?.toString()
    }.getOrNull()
}
