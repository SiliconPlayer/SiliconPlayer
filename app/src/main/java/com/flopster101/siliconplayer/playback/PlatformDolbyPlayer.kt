package com.flopster101.siliconplayer.playback

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.NativeBridge
import java.io.File

/**
 * Platform Dolby playback core.
 *
 * Routes audible playback of DD-family tracks (E-AC-3 / AC-3) through the
 * system MediaPlayer (NuPlayer in mediaserver) instead of the in-app FFmpeg
 * engine. The platform decoder registers itself with AudioFlinger as a Dolby
 * codec (CCodec -> registerDsCallback -> registerDlbCodecCallback), which is
 * what arms the Dolby Stage content processor / spatializer for the track.
 *
 * The native decoder is still loaded for metadata and state; only transport
 * (play/pause/seek/position) is redirected by [NativeBridge] while active.
 * Any failure falls back to the FFmpeg engine transparently.
 */
internal object PlatformDolbyPlayer {
    private const val TAG = "PlatformDolby"

    // Claimed synchronously during activate() so transport redirection is
    // deterministic; cleared on deactivate/fallback.
    @Volatile
    private var active = false

    @Volatile
    private var prepared = false

    @Volatile
    private var pendingStart = false

    @Volatile
    private var lastKnownPlaying = false

    @Volatile
    private var naturalEnd = false

    // MediaCodec component name backing the active platform playback
    // (e.g. c2.dolby.eac3.decoder.eac3-joc), resolved from the same codec
    // list the platform uses for decoder selection.
    @Volatile
    private var codecName: String? = null

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var player: MediaPlayer? = null

    // Prepare-ahead for gapless track advance: a MediaPlayer created for the
    // next DD-family track, attached to the current one via setNextMediaPlayer
    // (framework handoff with no audible gap). Promoted on completion; the
    // app's advance path adopts it via consumeHandoffIfMatches and reloads
    // only the native decoder.
    @Volatile
    private var nextPath: String? = null
    private var nextPlayer: MediaPlayer? = null
    private var nextPlayerPrepared = false

    // Path of a track the framework already handed off to (set on completion
    // promotion), awaiting adoption by the app's advance path.
    @Volatile
    private var handoffPath: String? = null

    // SMB upgrade: an smb:// DD source whose progressive cache has not fully
    // downloaded yet. FFmpeg keeps audible playback; when the background
    // prefetch completes, the platform core switches to the cached local
    // copy and resumes at the engine's position.
    @Volatile
    private var smbUpgradeRequestPath: String? = null
    private var smbUpgradeGeneration = 0
    @Volatile
    private var pendingResumePosition: Double = -1.0
    @Volatile
    private var pendingResumePath: String? = null

    @JvmStatic
    fun isActive(): Boolean = active

    /**
     * Called after the native decoder loaded [path]. Decides whether audible
     * playback is routed to the platform decoder, based on the probed FFmpeg
     * codec and the availability of a matching platform (c2.dolby/OMX.dolby)
     * decoder. Any previous platform playback is torn down first.
     */
    @JvmStatic
    fun onNativeTrackLoaded(path: String) {
        // Gapless handoff: the framework already advanced audible playback to
        // this track; keep the promoted player and let the native decoder
        // reload underneath it for visualizers/metadata only.
        if (active && handoffPath != null && handoffPath == path) {
            handoffPath = null
            naturalEnd = false
            Log.i(TAG, "adopted seamless handoff for $path")
            return
        }
        deactivate()
        if (!shouldUsePlatform(path)) return
        activate(path, pendingStart = false)
    }

    /**
     * Re-arms the platform core for [path] when transport starts without a
     * fresh load (e.g. replay after stop, or recovery from a lost race with
     * the player thread). Returns true when playback was routed here.
     */
    @JvmStatic
    fun activateIfEligibleAndPlay(path: String?): Boolean {
        if (path == null || active) return false
        if (!shouldUsePlatform(path)) return false
        activate(path, pendingStart = true)
        return true
    }

    @JvmStatic
    fun onNativeTrackUnloaded() {
        deactivate()
    }

    @JvmStatic
    fun deactivate() {
        val hadActive = active
        active = false
        prepared = false
        pendingStart = false
        lastKnownPlaying = false
        naturalEnd = false
        handoffPath = null
        codecName = null
        smbUpgradeRequestPath = null
        smbUpgradeGeneration++
        pendingResumePosition = -1.0
        pendingResumePath = null
        try {
            NativeBridge.onPlatformCoreInactive()
            NativeBridge.setOutputShadowMuted(false)
        } catch (ignored: Throwable) {}
        PlatformVisTap.stop()
        releasePlayer()
        releaseNextPlayer()
        if (hadActive) {
            Log.i(TAG, "deactivated")
        }
    }

    /** MediaCodec component backing the active core, e.g. c2.dolby.eac3.decoder. */
    @JvmStatic
    fun codecName(): String = codecName.orEmpty()

    /**
     * Formats this core claims, each mapped to the platform decoder
     * component that would handle it (may be empty when none exists on
     * this device). Used by the core settings UI.
     */
    @JvmStatic
    fun claimedFormats(): List<Pair<String, String>> = listOf(
        "E-AC-3 (DD+)" to resolvePlatformDolbyDecoder("audio/eac3").orEmpty(),
        "E-AC-3 JOC (Atmos)" to resolvePlatformDolbyDecoder("audio/eac3-joc").orEmpty(),
        "AC-3 (DD)" to resolvePlatformDolbyDecoder("audio/ac3").orEmpty()
    )

    /**
     * Re-evaluate the shadow-render mute and vis tap immediately (settings
     * changed while a track is playing).
     */
    @JvmStatic
    fun refreshShadowMute() {
        NativeBridge.refreshShadowRenderForVisSourceChange()
        if (visSource() == VIS_SOURCE_SYSTEM && hasRecordAudioPermission()) {
            val p = player
            if (p != null) {
                PlatformVisTap.start(p.audioSessionId)
            }
        } else {
            PlatformVisTap.stop()
        }
    }

    /**
     * Visualizer tap source for the platform core:
     * 0 = shadow decoder (parallel muted render, full visualizers),
     * 1 = Android system Visualizer (stereo, battery-friendly),
     * 2 = none (frozen visualizers).
     */
    const val VIS_SOURCE_SHADOW = 0
    const val VIS_SOURCE_SYSTEM = 1
    const val VIS_SOURCE_NONE = 2

    @JvmStatic
    fun visSource(): Int = visSourceStorageValue()

    @JvmStatic
    fun setVisSourceStorageValue(value: Int) {
        try {
            NativeBridge.requireAppContext().getSharedPreferences(
                AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE
            ).edit().putInt(AppPreferenceKeys.PLATFORM_DOLBY_VIS_SOURCE, value).apply()
        } catch (ignored: Throwable) {}
    }

    @JvmStatic
    fun visSourceStorageValue(): Int = try {
        NativeBridge.requireAppContext().getSharedPreferences(
            AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE
        ).getInt(AppPreferenceKeys.PLATFORM_DOLBY_VIS_SOURCE, VIS_SOURCE_SHADOW)
    } catch (t: Throwable) {
        VIS_SOURCE_SHADOW
    }

    @JvmStatic
    fun hasRecordAudioPermission(): Boolean = try {
        androidx.core.content.ContextCompat.checkSelfPermission(
            NativeBridge.requireAppContext(),
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /**
     * True when the native engine should shadow-render muted so the
     * visualization pipeline keeps receiving data while this core owns
     * audible playback. Only the shadow source uses the engine; the system
     * Visualizer taps the player session instead, and "none" freezes vis.
     */
    @JvmStatic
    fun isParallelVisEnabled(): Boolean {
        if (visSource() != VIS_SOURCE_SHADOW) return false
        return try {
            NativeBridge.requireAppContext().getSharedPreferences(
                AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE
            ).getBoolean(AppPreferenceKeys.PLATFORM_DOLBY_PARALLEL_VIS, true)
        } catch (t: Throwable) {
            true
        }
    }

    /**
     * Start the system Visualizer tap when the configured source is the
     * Android Visualizer and the permission is granted; otherwise stop it.
     */
    @JvmStatic
    fun maybeStartFor(audioSessionId: Int) {
        val source = visSource()
        if (source == VIS_SOURCE_SYSTEM && hasRecordAudioPermission()) {
            PlatformVisTap.start(audioSessionId)
        } else {
            PlatformVisTap.stop()
        }
    }

    @JvmStatic
    fun redirectPlay(): Boolean {
        if (!active) return false
        val h = handler ?: return false
        val p = player
        if (p != null && prepared) {
            h.post {
                try {
                    p.start()
                    lastKnownPlaying = true
                } catch (e: Exception) {
                    Log.w(TAG, "start failed", e)
                }
            }
        } else {
            pendingStart = true
            lastKnownPlaying = true
        }
        return true
    }

    @JvmStatic
    fun redirectPause(): Boolean {
        if (!active) return false
        val h = handler ?: return false
        pendingStart = false
        lastKnownPlaying = false
        val p = player
        if (p != null && prepared) {
            h.post {
                try {
                    p.pause()
                } catch (ignored: IllegalStateException) {}
            }
        }
        return true
    }

    @JvmStatic
    fun isPlaying(): Boolean = active && lastKnownPlaying

    @JvmStatic
    fun positionSeconds(): Double {
        val p = player ?: return 0.0
        return try {
            p.currentPosition / 1000.0
        } catch (e: Exception) {
            0.0
        }
    }

    @JvmStatic
    fun durationSeconds(): Double {
        val p = player ?: return 0.0
        return try {
            p.duration / 1000.0
        } catch (e: Exception) {
            0.0
        }
    }

    @JvmStatic
    fun seekTo(seconds: Double) {
        val h = handler ?: return
        val p = player ?: return
        val targetMs = ((seconds.coerceAtLeast(0.0) * 1000.0).toLong()).toInt()
        h.post {
            try {
                p.seekTo(targetMs)
            } catch (e: Exception) {
                Log.w(TAG, "seek failed", e)
            }
        }
    }

    @JvmStatic
    fun consumeNaturalEnd(): Boolean {
        val end = naturalEnd
        naturalEnd = false
        return end
    }

    // ---- internals ----

    private fun ensureHandler(): Handler {
        handler?.let { return it }
        val thread = HandlerThread("PlatformDolbyPlayer")
        thread.start()
        val h = Handler(thread.looper)
        handlerThread = thread
        handler = h
        return h
    }

    private fun releasePlayer() {
        val p = player
        player = null
        val h = handler
        if (p != null && h != null) {
            h.post {
                try {
                    p.stop()
                } catch (ignored: IllegalStateException) {}
                try {
                    p.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Resolution for remote sources: either the URL itself is streamable
     * directly (HTTP — NuPlayer handles those natively) or a fully cached
     * local copy exists (SMB progressive cache). Null means the source is
     * not usable by the platform core.
     */
    private sealed interface RemoteResolution {
        data class Direct(val url: String) : RemoteResolution
        data class Cached(val path: String) : RemoteResolution
    }

    /**
     * Resolve a remote (http/https/smb) source for platform playback.
     * SMB has no direct URL support in MediaPlayer, but its progressive
     * cache prefetches the entire file — once fully downloaded, the cached
     * local copy can be played with the position preserved.
     */
    private fun resolveRemoteSource(rawPath: String): RemoteResolution? {
        val lowercase = rawPath.lowercase()
        return when {
            lowercase.startsWith("http://") || lowercase.startsWith("https://") ->
                RemoteResolution.Direct(rawPath)
            lowercase.startsWith("smb://") -> {
                val cached = cachedFileForSmbSource(rawPath)
                if (cached != null && isProgressiveCacheComplete(cached)) {
                    RemoteResolution.Cached(cached.absolutePath)
                } else null
            }
            else -> null
        }
    }

    /**
     * A progressive cache is complete when its meta header matches, every
     * chunk slot in the chunk map is marked cached, and the data file spans
     * the full remote size.
     */
    private fun isProgressiveCacheComplete(dataFile: File): Boolean = runCatching {
        val metaFile = File(dataFile.absolutePath + ".meta")
        val chunkMapFile = File(dataFile.absolutePath + ".chunks")
        if (!metaFile.isFile || !chunkMapFile.isFile) return@runCatching false
        var sizeBytes = -1L
        var chunkSizeBytes = -1
        metaFile.readText().lineSequence().forEach { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size != 2) return@forEach
            when (parts[0]) {
                "sizeBytes" -> sizeBytes = parts[1].toLongOrNull() ?: -1L
                "chunkSizeBytes" -> chunkSizeBytes = parts[1].toIntOrNull() ?: -1
            }
        }
        if (sizeBytes <= 0L || chunkSizeBytes <= 0) return@runCatching false
        val chunkCount = ((sizeBytes + chunkSizeBytes - 1L) / chunkSizeBytes).toInt()
        if (chunkCount <= 0) return@runCatching false
        val states = chunkMapFile.readBytes()
        if (states.size < chunkCount) return@runCatching false
        for (i in 0 until chunkCount) {
            if (states[i].toInt() == 0) return@runCatching false
        }
        dataFile.length() == sizeBytes
    }.getOrDefault(false)

    private fun cachedFileForSmbSource(requestUri: String): File? = try {
        val spec = com.flopster101.siliconplayer.resolveCredentialedSmbSpec(requestUri)
            ?: return null
        val sourceId = com.flopster101.siliconplayer.buildSmbSourceId(spec)
        val context = NativeBridge.requireAppContext()
        val cacheRoot = java.io.File(
            context.cacheDir,
            com.flopster101.siliconplayer.PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR
        )
        com.flopster101.siliconplayer.remoteCacheFileForSource(cacheRoot, sourceId)
    } catch (t: Throwable) {
        Log.d(TAG, "SMB cache lookup failed", t)
        null
    }

    private fun isRemoteSource(path: String): Boolean {
        val lowercase = path.lowercase()
        return lowercase.startsWith("http://") ||
            lowercase.startsWith("https://") ||
            lowercase.startsWith("smb://")
    }

    private fun shouldUsePlatform(path: String): Boolean {
        val context = try {
            NativeBridge.requireAppContext()
        } catch (t: Throwable) {
            return false
        }
        val prefs = context.getSharedPreferences(
            AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean(AppPreferenceKeys.PLATFORM_DOLBY_DECODER, true)) return false
        // Bit-perfect mode owns the output exclusively; platform decode cannot
        // honor it, so it always wins over this core.
        if (prefs.getBoolean(AppPreferenceKeys.BIT_PERFECT_USB_AUDIO, false)) return false
        if (!isRemoteSource(path)) {
            // Local files only unless it is a streamable remote URL; SMB is
            // playable once its progressive cache holds the whole file.
            if (!path.startsWith("/") || !File(path).exists()) return false
        }
        val codec = try {
            NativeBridge.getFfmpegCodecName().trim().lowercase()
        } catch (t: Throwable) {
            return false
        }
        val candidateMimes = when {
            codec.startsWith("eac3") || codec.startsWith("e-ac") ->
                arrayOf("audio/eac3", "audio/eac3-joc")
            codec.startsWith("ac3") -> arrayOf("audio/ac3")
            else -> return false
        }
        val component = candidateMimes.firstNotNullOfOrNull { mime ->
            resolvePlatformDolbyDecoder(mime)
        }
        if (component == null) {
            Log.i(TAG, "codec=$codec but no platform Dolby decoder; staying on FFmpeg")
            return false
        }
        codecName = component
        return true
    }

    private fun resolvePlatformDolbyDecoder(mime: String): String? = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            !info.isEncoder &&
                (info.name.startsWith("c2.dolby") || info.name.startsWith("OMX.dolby")) &&
                try {
                    info.supportedTypes.contains(mime)
                } catch (t: Throwable) {
                    false
                }
        }?.name
    } catch (t: Throwable) {
        null
    }

    private fun activate(path: String, pendingStart: Boolean) {
        // Claim synchronously so transport calls that arrive before the
        // player thread finishes preparing are redirected, not leaked to
        // the FFmpeg engine.
        releasePlayer()
        releaseNextPlayer()
        handoffPath = null
        naturalEnd = false
        // Remote sources resolve to a playable locator up front (direct URL
        // or cached file). Resolution happens before the async prepare so a
        // null result can bail out synchronously into the FFmpeg fallback.
        val dataSource = when {
            !isRemoteSource(path) -> path
            else -> when (val remote = resolveRemoteSource(path)) {
                is RemoteResolution.Direct -> remote.url
                is RemoteResolution.Cached -> remote.path
                null -> {
                    // Nothing claimed yet: keep the FFmpeg engine audible and
                    // let the SMB upgrade checker re-evaluate once the cache
                    // has the whole file.
                    Log.i(TAG, "remote source not yet playable by platform core: $path")
                    if (path.startsWith("smb://", true)) {
                        armSmbUpgrade(path)
                    }
                    return
                }
            }
        }
        prepared = false
        this.pendingStart = pendingStart
        // Claim-time capture of a pending resume position (SMB upgrade), so
        // a track switch racing the takeover cannot inherit a stale seek.
        val resumePosition = if (pendingResumePath == path) pendingResumePosition else -1.0
        pendingResumePosition = -1.0
        pendingResumePath = null
        lastKnownPlaying = pendingStart
        active = true
        NativeBridge.setOutputShadowMuted(isParallelVisEnabled())
        Log.i(TAG, "activated for $path")
        ensureHandler().post {
            try {
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                try {
                    if (dataSource.startsWith("http://", true) ||
                        dataSource.startsWith("https://", true)
                    ) {
                        // MediaUri variant routes through the framework HTTP
                        // stack (proper UA/cookie handling for NuPlayer).
                        p.setDataSource(
                            NativeBridge.requireAppContext(),
                            android.net.Uri.parse(dataSource)
                        )
                    } else {
                        p.setDataSource(dataSource)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "setDataSource failed for $dataSource", e)
                    throw e
                }
                p.setOnPreparedListener { mp ->
                    prepared = true
                    Log.i(TAG, "prepared (system decoder active)")
                    maybeStartFor(mp.audioSessionId)
                    if (this@PlatformDolbyPlayer.pendingStart) {
                        this@PlatformDolbyPlayer.pendingStart = false
                        try {
                            if (resumePosition > 0.5) {
                                @Suppress("DEPRECATION")
                                mp.seekTo((resumePosition * 1000).toInt())
                            }
                            mp.start()
                            lastKnownPlaying = true
                        } catch (e: Exception) {
                            Log.w(TAG, "start after prepare failed", e)
                        }
                    }
                }
                p.setOnCompletionListener {
                    // With a prepared next player attached, the framework has
                    // already handed off audibly (it started the successor at
                    // completion); promote it so this core keeps redirecting
                    // transport to the new track. The app's advance path then
                    // adopts the handoff via consumeHandoffIfMatches.
                    val promoted = nextPlayer
                    if (nextPlayerPrepared && promoted != null) {
                        val old = player
                        player = promoted
                        prepared = true
                        this@PlatformDolbyPlayer.pendingStart = false
                        lastKnownPlaying = true
                        handoffPath = nextPath
                        nextPath = null
                        nextPlayer = null
                        nextPlayerPrepared = false
                        naturalEnd = true
                        try { promoted.start() } catch (ignored: Exception) {}
                        old?.release()
                        Log.i(TAG, "gapless handoff to $handoffPath")
                    } else {
                        lastKnownPlaying = false
                        naturalEnd = true
                        Log.i(TAG, "completed")
                    }
                }
                p.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "platform decode error what=$what extra=$extra; falling back to FFmpeg")
                    fallbackToNative()
                    true
                }
                p.prepareAsync()
                player = p
                // A next-track hint may have arrived while the player thread
                // was preparing; attach it once the current player exists.
                val queued = nextPath
                if (queued != null) {
                    handler?.post { prepareNextPlayerLocked() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "activation failed; falling back to FFmpeg", e)
                fallbackToNative()
            }
        }
    }

    /**
     * Watch the SMB progressive cache until it holds the whole file, then
     * switch the platform core onto the cached local copy (position resume;
     * the engine keeps running underneath for visualizers).
     */
    private fun armSmbUpgrade(requestPath: String) {
        smbUpgradeRequestPath = requestPath
        val gen = ++smbUpgradeGeneration
        // The watcher can be armed before the player handler exists (the
        // source resolves during the synchronous claim, before activate
        // creates the thread), so create it instead of bailing out.
        val h = ensureHandler()
        Log.i(TAG, "SMB source is Dolby; watching cache for full download")
        fun postCheck() {
            h.postDelayed({
                if (gen != smbUpgradeGeneration || smbUpgradeRequestPath != requestPath) {
                    return@postDelayed
                }
                val cached = resolveRemoteSource(requestPath) as? RemoteResolution.Cached
                if (cached == null) {
                    postCheck()
                    return@postDelayed
                }
                smbUpgradeRequestPath = null
                // The takeover drives a full decoder load; keep it off the
                // player handler thread (prepare callbacks live there).
                Thread {
                    try {
                        takeOverFromEngine(cached.path)
                    } catch (t: Throwable) {
                        Log.w(TAG, "SMB cache takeover failed", t)
                    }
                }.start()
            }, 2000L)
        }
        postCheck()
    }

    private fun takeOverFromEngine(cachedPath: String) {
        val resume = try {
            NativeBridge.getPositionImpl()
        } catch (t: Throwable) {
            0.0
        }
        val wasPlaying = try {
            NativeBridge.isEnginePlayingImpl()
        } catch (t: Throwable) {
            false
        }
        if (resume > 0.5) {
            pendingResumePosition = resume
            pendingResumePath = cachedPath
        }
        Log.i(TAG, "SMB cache complete; platform core adopts cached copy (pos=$resume)")
        NativeBridge.replaceCurrentAudio(cachedPath)
        if (wasPlaying) {
            NativeBridge.startEngine()
        }
    }

    /**
     * App-side playlist hint: prepare [path] ahead so the framework can
     * hand off to it when the current track completes (gapless for DD
     * playlists). Safe to call repeatedly; stale preparations are released.
     * Without an active platform playback the hint is dropped and the
     * normal advance transition applies.
     */
    @JvmStatic
    fun setNextTrackHint(path: String?) {
        if (path == nextPath) return
        nextPath = path
        val h = handler ?: return
        if (!active) return
        h.post { prepareNextPlayerLocked() }
    }

    /** Must run on the player handler thread. */
    private fun prepareNextPlayerLocked() {
        val path = nextPath ?: run { releaseNextPlayer(); return }
        val cur = player
        if (!active || cur == null || !prepared) {
            releaseNextPlayer()
            return
        }
        if (nextPlayer != null && nextPlayerPrepared) return
        releaseNextPlayer()
        if (!shouldUsePlatform(path)) return
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // Same audio session keeps the Visualizer tap (and any session
            // effects) continuous across the handoff.
            try { p.setAudioSessionId(cur.audioSessionId) } catch (ignored: Exception) {}
            if (isRemoteSource(path)) {
                p.setDataSource(
                    NativeBridge.requireAppContext(),
                    android.net.Uri.parse(path)
                )
            } else {
                p.setDataSource(path)
            }
            p.setOnPreparedListener { mp ->
                nextPlayerPrepared = true
                try {
                    player?.setNextMediaPlayer(mp)
                } catch (e: Exception) {
                    Log.w(TAG, "setNextMediaPlayer failed", e)
                }
            }
            p.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "next-track prepare error what=$what extra=$extra; handoff disarmed")
                releaseNextPlayer()
                true
            }
            p.prepareAsync()
            nextPlayer = p
        } catch (e: Exception) {
            Log.w(TAG, "next-track prepare failed", e)
            releaseNextPlayer()
        }
    }

    private fun releaseNextPlayer() {
        nextPlayerPrepared = false
        nextPlayer?.release()
        nextPlayer = null
    }

    /**
     * True when the platform core just completed a seamless framework
     * handoff into [path] (the app's advance path resolved the same track).
     * The caller then reloads only the native decoder for visualizers and
     * skips the audible teardown/rebuild.
     */
    @JvmStatic
    fun consumeHandoffIfMatches(path: String?): Boolean {
        val pending = handoffPath
        if (pending != null && path != null && pending == path) {
            handoffPath = null
            return true
        }
        return false
    }

    private fun fallbackToNative() {
        val wasPlaying = pendingStart || lastKnownPlaying
        val resumePosition = positionSeconds()
        active = false
        prepared = false
        pendingStart = false
        lastKnownPlaying = false
        handoffPath = null
        smbUpgradeRequestPath = null
        smbUpgradeGeneration++
        pendingResumePosition = -1.0
        pendingResumePath = null
        try {
            NativeBridge.onPlatformCoreInactive()
            NativeBridge.setOutputShadowMuted(false)
        } catch (ignored: Throwable) {}
        PlatformVisTap.stop()
        releasePlayer()
        releaseNextPlayer()
        if (!wasPlaying) return
        // Native decoder is already loaded with the same path; resume there.
        // Native start (not NativeBridge.startEngine) so the routing layer
        // does not re-arm this player and loop.
        try {
            NativeBridge.startEngineNative()
            if (resumePosition > 0.5) {
                NativeBridge.seekToImpl(resumePosition)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fallback resume failed", e)
        }
    }
}
