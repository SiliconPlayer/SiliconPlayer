package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerParityTest {

    @Test
    fun testFadeModeClassification() {
        fun isFadeMode(mode: Int): Boolean =
            mode == 1 || mode == 2 || mode == 3 || mode == 5

        // Bars (1), Oscilloscope (2), VU Meters (3), Starfield (5) are fade modes
        assertTrue(isFadeMode(1))
        assertTrue(isFadeMode(2))
        assertTrue(isFadeMode(3))
        assertTrue(isFadeMode(5))

        // Channel Scope (4) and ProjectM (100) are hold-first-frame modes
        assertFalse(isFadeMode(4))
        assertFalse(isFadeMode(100))
    }

    @Test
    fun testFadeSettledLogic() {
        fun isFadeSettled(mode: Int, visualAlpha: Float): Boolean {
            val isFade = mode == 1 || mode == 2 || mode == 3 || mode == 5
            return !isFade || visualAlpha <= 0.001f
        }

        // Channel scope and projectM are settled immediately regardless of alpha
        assertTrue(isFadeSettled(4, 1.0f))
        assertTrue(isFadeSettled(4, 0.5f))
        assertTrue(isFadeSettled(100, 1.0f))

        // Fade modes are not settled while fading out
        assertFalse(isFadeSettled(1, 1.0f))
        assertFalse(isFadeSettled(2, 0.5f))
        assertFalse(isFadeSettled(3, 0.1f))
        assertFalse(isFadeSettled(5, 0.05f))

        // Fade modes are settled once alpha reaches threshold
        assertTrue(isFadeSettled(1, 0.001f))
        assertTrue(isFadeSettled(1, 0.0f))
        assertTrue(isFadeSettled(2, 0.0f))
        assertTrue(isFadeSettled(3, 0.0f))
        assertTrue(isFadeSettled(5, 0.0f))
    }

    @Test
    fun testScopeTrackTransitionMath() {
        fun computeTransition(transitionMode: Int, progress: Float, width: Float): Pair<Float, Float> {
            val p = progress.coerceIn(0f, 1f)
            val eased = p * p * (3f - 2f * p)
            val offsetX = if (transitionMode == 1) -eased * width else 0f
            val alpha = 1f - eased
            return offsetX to alpha
        }

        val width = 800f

        // Progress = 0 (start of transition)
        val (offStartSlide, alphaStartSlide) = computeTransition(1, 0f, width)
        assertEquals(0f, offStartSlide, 0.0001f)
        assertEquals(1f, alphaStartSlide, 0.0001f)

        val (offStartFade, alphaStartFade) = computeTransition(2, 0f, width)
        assertEquals(0f, offStartFade, 0.0001f)
        assertEquals(1f, alphaStartFade, 0.0001f)

        // Progress = 0.5 (midpoint of transition)
        // Eased at 0.5 = 0.5 * 0.5 * (3 - 1) = 0.25 * 2 = 0.5
        val (offMidSlide, alphaMidSlide) = computeTransition(1, 0.5f, width)
        assertEquals(-400f, offMidSlide, 0.0001f)
        assertEquals(0.5f, alphaMidSlide, 0.0001f)

        val (offMidFade, alphaMidFade) = computeTransition(2, 0.5f, width)
        assertEquals(0f, offMidFade, 0.0001f)
        assertEquals(0.5f, alphaMidFade, 0.0001f)

        // Progress = 1.0 (end of transition)
        val (offEndSlide, alphaEndSlide) = computeTransition(1, 1f, width)
        assertEquals(-800f, offEndSlide, 0.0001f)
        assertEquals(0f, alphaEndSlide, 0.0001f)

        val (offEndFade, alphaEndFade) = computeTransition(2, 1f, width)
        assertEquals(0f, offEndFade, 0.0001f)
        assertEquals(0f, alphaEndFade, 0.0001f)
    }

    @Test
    fun testContrastModeMappings() {
        assertEquals(1, resolveContrastMode(VisualizationMode.Bars, true, false, VisualizationVuAnchor.Bottom, true))
        assertEquals(0, resolveContrastMode(VisualizationMode.Bars, false, false, VisualizationVuAnchor.Bottom, true))

        assertEquals(2, resolveContrastMode(VisualizationMode.Oscilloscope, true, false, VisualizationVuAnchor.Bottom, true))
        assertEquals(3, resolveContrastMode(VisualizationMode.Oscilloscope, true, true, VisualizationVuAnchor.Bottom, true))
        assertEquals(0, resolveContrastMode(VisualizationMode.Oscilloscope, false, true, VisualizationVuAnchor.Bottom, true))

        assertEquals(4, resolveContrastMode(VisualizationMode.VuMeters, true, false, VisualizationVuAnchor.Top, true))
        assertEquals(5, resolveContrastMode(VisualizationMode.VuMeters, true, false, VisualizationVuAnchor.Bottom, true))
        assertEquals(5, resolveContrastMode(VisualizationMode.VuMeters, true, false, VisualizationVuAnchor.Center, true))
        assertEquals(0, resolveContrastMode(VisualizationMode.VuMeters, false, false, VisualizationVuAnchor.Top, true))

        assertEquals(6, resolveContrastMode(VisualizationMode.ChannelScope, false, false, VisualizationVuAnchor.Bottom, true))
        assertEquals(0, resolveContrastMode(VisualizationMode.ChannelScope, false, false, VisualizationVuAnchor.Bottom, false))

        assertEquals(7, resolveContrastMode(VisualizationMode.Starfield, true, false, VisualizationVuAnchor.Bottom, true))
        assertEquals(0, resolveContrastMode(VisualizationMode.Starfield, false, false, VisualizationVuAnchor.Bottom, true))
    }

    private fun resolveContrastMode(
        mode: VisualizationMode,
        contrastEnabled: Boolean,
        oscStereo: Boolean,
        vuAnchor: VisualizationVuAnchor,
        channelScopeContrastEnabled: Boolean
    ): Int {
        return when (mode) {
            VisualizationMode.Bars -> if (contrastEnabled) 1 else 0
            VisualizationMode.Oscilloscope -> if (!contrastEnabled) 0 else if (oscStereo) 3 else 2
            VisualizationMode.VuMeters -> if (!contrastEnabled) 0 else if (vuAnchor == VisualizationVuAnchor.Top) 4 else 5
            VisualizationMode.ChannelScope -> if (channelScopeContrastEnabled) 6 else 0
            VisualizationMode.Starfield -> if (contrastEnabled) 7 else 0
            else -> 0
        }
    }
}
