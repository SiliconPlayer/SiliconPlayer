package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.export.applyAudioExportFade
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AudioExportFadeTest {

    @Test
    fun linearFadeEndsAtZeroAndAffectsBothChannels() {
        val samples = FloatArray(20) { 1.0f }

        applyAudioExportFade(
            samples = samples,
            frameOffset = 0,
            frameCount = 10,
            totalFrames = 10,
            fadeDurationMs = 500,
            fadeCurve = EndFadeCurve.Linear,
            sampleRateHz = 10
        )

        val gains = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 0.75f, 0.5f, 0.25f, 0f)
        gains.forEachIndexed { frame, gain ->
            assertArrayEquals(floatArrayOf(gain, gain), samples.copyOfRange(frame * 2, frame * 2 + 2), 0.0001f)
        }
    }

    @Test
    fun chunkedFadeMatchesSinglePass() {
        val expected = FloatArray(20) { 1.0f }
        val actual = FloatArray(20) { 1.0f }
        applyFade(expected, 0, 10, EndFadeCurve.EaseOut)
        applyFade(actual, 0, 3, EndFadeCurve.EaseOut)
        applyFade(actual, 3, 4, EndFadeCurve.EaseOut)
        applyFade(actual, 7, 3, EndFadeCurve.EaseOut)

        assertArrayEquals(expected, actual, 0.0001f)
    }

    @Test
    fun easeInUsesSelectedCurve() {
        val samples = FloatArray(8) { 1.0f }
        applyAudioExportFade(samples, 6, 4, 10, 500, EndFadeCurve.EaseIn, 10)

        assertArrayEquals(
            floatArrayOf(0.9375f, 0.9375f, 0.75f, 0.75f, 0.4375f, 0.4375f, 0f, 0f),
            samples,
            0.0001f
        )
    }

    @Test
    fun fadeLongerThanTrackCoversWholeExport() {
        val samples = FloatArray(8) { 1.0f }

        applyAudioExportFade(
            samples = samples,
            frameOffset = 0,
            frameCount = 4,
            totalFrames = 4,
            fadeDurationMs = 10_000,
            fadeCurve = EndFadeCurve.Linear,
            sampleRateHz = 48_000
        )

        assertArrayEquals(floatArrayOf(1f, 1f, 0.6667f, 0.6667f, 0.3333f, 0.3333f, 0f, 0f), samples, 0.0001f)
    }

    @Test
    fun zeroDurationLeavesSamplesUntouched() {
        val samples = FloatArray(8) { index -> index.toFloat() }
        val expected = samples.copyOf()

        applyAudioExportFade(samples, 0, 4, 4, 0, EndFadeCurve.Linear, 48_000)

        assertArrayEquals(expected, samples, 0f)
    }

    private fun applyFade(samples: FloatArray, offset: Long, count: Int, curve: EndFadeCurve) {
        val chunk = samples.copyOfRange(offset.toInt() * 2, (offset.toInt() + count) * 2)
        applyAudioExportFade(
            samples = chunk,
            frameOffset = offset,
            frameCount = count,
            totalFrames = 10,
            fadeDurationMs = 500,
            fadeCurve = curve,
            sampleRateHz = 10
        )
        chunk.copyInto(samples, offset.toInt() * 2)
    }
}
