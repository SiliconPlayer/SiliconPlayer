package com.flopster101.siliconplayer.playback

import android.media.audiofx.Visualizer
import android.util.Log
import com.flopster101.siliconplayer.NativeBridge

/**
 * Android Visualizer API tap for the platform Dolby core.
 *
 * When the shadow-render tap is disabled (or unavailable), this attaches the
 * system Visualizer to the platform player's audio session and pushes the
 * captured waveform into the native visualization ring buffers. The tap is
 * post-downmix stereo: stereo visualizers keep working, the multichannel
 * channel scope does not.
 *
 * Requires the RECORD_AUDIO permission (runtime-requested by the UI).
 */
internal object PlatformVisTap {
    private const val TAG = "PlatformVisTap"

    /**
     * The system Visualizer captures the post-Dolby summed downmix, which is
     * much hotter than the engine's per-channel capture; attenuate so both
     * tap sources drive visualizers at a comparable level.
     */
    private const val SYSTEM_TAP_GAIN = 0.45f

    private var visualizer: Visualizer? = null

    @JvmStatic
    fun start(audioSessionId: Int): Boolean {
        stop()
        if (audioSessionId <= 0) return false
        return try {
            val viz = Visualizer(audioSessionId)
            val rate = Visualizer.getMaxCaptureRate()
            viz.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRateHz: Int
                    ) {
                        val data = waveform ?: return
                        val count = data.size
                        if (count <= 0) return
                        // Byte (unsigned, 128-centered) -> float [-1, 1] mono mix.
                        // The system tap is a post-Dolby summed downmix and runs
                        // noticeably hotter than the in-app engine capture, so
                        // scale it down for comparable visualizer response.
                        val buffer = FloatArray(count)
                        var sumSq = 0.0
                        var i = 0
                        while (i < count) {
                            val v = ((data[i].toInt() and 0xFF) - 128) / 128.0f * SYSTEM_TAP_GAIN
                            buffer[i] = v
                            sumSq += (v * v).toDouble()
                            i++
                        }
                        val rms = kotlin.math.sqrt(sumSq / count).toFloat()
                        NativeBridge.pushExternalVisualizationSamples(buffer, count, rms)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRateHz: Int
                    ) {
                        // Bars derive from the waveform history in the engine.
                    }
                },
                rate,
                true,
                false
            )
            viz.enabled = true
            visualizer = viz
            Log.i(TAG, "started on session $audioSessionId (rate=$rate)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "start failed", e)
            visualizer = null
            false
        }
    }

    @JvmStatic
    fun stop() {
        val viz = visualizer
        visualizer = null
        if (viz != null) {
            try {
                viz.enabled = false
            } catch (ignored: IllegalStateException) {}
            try {
                viz.release()
            } catch (ignored: Exception) {}
            Log.i(TAG, "stopped")
        }
    }

    @JvmStatic
    fun isActive(): Boolean = visualizer != null
}
