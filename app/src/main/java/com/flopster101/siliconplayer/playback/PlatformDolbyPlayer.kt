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

    // Gapless advance: next track pre-prepared via setNextMediaPlayer.
    @Volatile
    private var nextPath: String? = null
    private var nextPlayer: MediaPlayer? = null
    private var nextPlayerPrepared = false

    // Set on framework handoff promotion, consumed by the advance path.
    @Volatile
    private var handoffPath: String? = null

    // Direct-SMB source whose cache is still downloading; FFmpeg stays
    // audible until the cached copy can take over at the engine position.
    @Volatile
    private var smbUpgradeRequestPath: String? = null
    private var smbUpgradeGeneration = 0
    @Volatile
    private var pendingResumePosition: Double = -1.0
    @Volatile
    private var pendingResumePath: String? = null

    @JvmStatic
    fun isActive(): Boolean = active

    /** Route [path] to the platform core when it qualifies; else drop it. */
    @JvmStatic
    fun onNativeTrackLoaded(path: String) {
        if (active && handoffPath != null && handoffPath == path) {
            // Framework handoff already plays this; only the native
            // decoder needs reloading underneath it.
            handoffPath = null
            naturalEnd = false
            Log.i(TAG, "adopted seamless handoff for $path")
            return
        }
        deactivate()
        if (!shouldUsePlatform(path)) return
        activate(path, pendingStart = false)
    }

    /** Re-arm for [path] when transport starts without a fresh load. */
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

    /** Direct URL or fully cached local copy for a remote source. */
    private sealed interface RemoteResolution {
        data class Direct(val url: String) : RemoteResolution
        data class Cached(val path: String) : RemoteResolution
    }

    /** HTTP streams directly; SMB only once its cache holds the whole file. */
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

    /** Complete cache: meta header matches, all chunks marked, size spans. */
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
        // Bit-perfect owns the output only while it actually drives it; the
        // toggle alone must not suppress the platform core on other routes.
        if (isBitPerfectInUse(prefs, context)) return false
        if (!isRemoteSource(path)) {
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

    private fun isBitPerfectInUse(prefs: android.content.SharedPreferences, context: Context): Boolean {
        if (!prefs.getBoolean(AppPreferenceKeys.BIT_PERFECT_USB_AUDIO, false)) return false
        return com.flopster101.siliconplayer.usb.UacDriverCoordinator.isStreaming.value ||
            com.flopster101.siliconplayer.usb.UacDriverCoordinator.findUsbAudioDevice(context) != null
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
        // Claim synchronously so transport calls arriving before prepare
        // finishes are redirected, not leaked to the FFmpeg engine.
        releasePlayer()
        releaseNextPlayer()
        handoffPath = null
        naturalEnd = false
        val dataSource = when {
            !isRemoteSource(path) -> path
            else -> when (val remote = resolveRemoteSource(path)) {
                is RemoteResolution.Direct -> remote.url
                is RemoteResolution.Cached -> remote.path
                null -> {
                    // Unclaimed: FFmpeg stays audible until the SMB cache
                    // completes and the upgrade watcher retries.
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
                        // Uri variant gets framework UA/cookie handling.
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
                    // A prepared successor means the framework already handed
                    // off audibly; promote it so the advance path can adopt.
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
     * Watch the SMB progressive cache until complete, then take over from
     * the engine at its position.
     */
    private fun armSmbUpgrade(requestPath: String) {
        smbUpgradeRequestPath = requestPath
        val gen = ++smbUpgradeGeneration
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
                // Decoder load must not block player callbacks.
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
     * Prepare [path] ahead for a gapless framework handoff at completion.
     * Dropped when no platform playback is active.
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
     * Consumed by the advance path when it resolves the handed-off track;
     * the native decoder then reloads without audible teardown.
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
