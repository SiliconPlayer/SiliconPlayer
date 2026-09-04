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

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var player: MediaPlayer? = null

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
        releasePlayer()
        if (hadActive) {
            Log.i(TAG, "deactivated")
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

    private fun shouldUsePlatform(path: String): Boolean {
        // Local files only (SMB/HTTP keeps the FFmpeg path for now).
        if (!path.startsWith("/") || !File(path).exists()) return false
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
        val available = candidateMimes.any { mime -> hasPlatformDolbyDecoder(mime) }
        if (!available) {
            Log.i(TAG, "codec=$codec but no platform Dolby decoder; staying on FFmpeg")
        }
        return available
    }

    private fun hasPlatformDolbyDecoder(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                (info.name.startsWith("c2.dolby") || info.name.startsWith("OMX.dolby")) &&
                try {
                    info.supportedTypes.contains(mime)
                } catch (t: Throwable) {
                    false
                }
        }
    } catch (t: Throwable) {
        false
    }

    private fun activate(path: String, pendingStart: Boolean) {
        // Claim synchronously so transport calls that arrive before the
        // player thread finishes preparing are redirected, not leaked to
        // the FFmpeg engine.
        releasePlayer()
        naturalEnd = false
        prepared = false
        this.pendingStart = pendingStart
        lastKnownPlaying = pendingStart
        active = true
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
                p.setDataSource(path)
                p.setOnPreparedListener { mp ->
                    prepared = true
                    Log.i(TAG, "prepared (system decoder active)")
                    if (this@PlatformDolbyPlayer.pendingStart) {
                        this@PlatformDolbyPlayer.pendingStart = false
                        try {
                            mp.start()
                            lastKnownPlaying = true
                        } catch (e: Exception) {
                            Log.w(TAG, "start after prepare failed", e)
                        }
                    }
                }
                p.setOnCompletionListener {
                    lastKnownPlaying = false
                    naturalEnd = true
                    Log.i(TAG, "completed")
                }
                p.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "platform decode error what=$what extra=$extra; falling back to FFmpeg")
                    fallbackToNative()
                    true
                }
                p.prepareAsync()
                player = p
            } catch (e: Exception) {
                Log.w(TAG, "activation failed; falling back to FFmpeg", e)
                fallbackToNative()
            }
        }
    }

    private fun fallbackToNative() {
        val wasPlaying = pendingStart || lastKnownPlaying
        val resumePosition = positionSeconds()
        active = false
        prepared = false
        pendingStart = false
        lastKnownPlaying = false
        releasePlayer()
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
