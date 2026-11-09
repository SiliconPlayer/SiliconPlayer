package com.flopster101.siliconplayer.export

import com.flopster101.siliconplayer.EndFadeCurve

internal fun applyAudioExportFade(
    samples: FloatArray,
    frameOffset: Long,
    frameCount: Int,
    totalFrames: Long,
    fadeDurationMs: Int,
    fadeCurve: EndFadeCurve,
    sampleRateHz: Int
) {
    require(frameOffset >= 0L && totalFrames >= 0L) { "Frame offsets must not be negative." }
    require(frameCount >= 0 && frameCount <= samples.size / EXPORT_STEREO_CHANNELS) {
        "Stereo output must contain frameCount frames."
    }
    require(frameOffset <= totalFrames - frameCount) { "Frames exceed the export duration." }
    if (fadeDurationMs <= 0 || totalFrames == 0L) return
    require(sampleRateHz > 0) { "Sample rate must be positive." }
    val fadeFrames = minOf(
        totalFrames,
        (fadeDurationMs.toLong() * sampleRateHz + 999L) / 1_000L
    )
    if (fadeFrames <= 0L) return
    val fadeStart = totalFrames - fadeFrames
    val denominator = (fadeFrames - 1L).coerceAtLeast(1L).toDouble()
    for (frame in 0 until frameCount) {
        val position = frameOffset + frame
        if (position < fadeStart) continue
        val progress = ((position - fadeStart).toDouble() / denominator).coerceIn(0.0, 1.0)
        val remainingGain = 1.0 - progress
        val gain = when (fadeCurve) {
            EndFadeCurve.Linear -> remainingGain
            EndFadeCurve.EaseIn -> 1.0 - progress * progress
            EndFadeCurve.EaseOut -> remainingGain * remainingGain
        }.toFloat()
        samples[frame * 2] *= gain
        samples[frame * 2 + 1] *= gain
    }
}

private const val EXPORT_STEREO_CHANNELS = 2
