package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.artworkNeedsBlurFill
import com.flopster101.siliconplayer.ui.visualization.computeBlurThumbArgb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkBlurFillTest {

    @Test
    fun solidColorSurvivesDownscaleAndSmoothing() {
        val src = IntArray(96 * 48) { 0xFFFF0000.toInt() }
        val thumb = computeBlurThumbArgb(src, 96, 48)!!
        assertEquals(48, thumb.width)
        assertEquals(24, thumb.height)
        assertArrayEquals(IntArray(48 * 24) { 0xFFFF0000.toInt() }, thumb.argb)
    }

    @Test
    fun twoByTwoCornersAverageWithTruncation() {
        // 2x2 keeps its size; each pixel's clipped 3x3 neighborhood is all
        // four corners, so every output is the truncated channel average.
        val src = intArrayOf(
            0xFF000000.toInt(), 0xFFFF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt()
        )
        val thumb = computeBlurThumbArgb(src, 2, 2)!!
        assertEquals(2, thumb.width)
        assertEquals(2, thumb.height)
        // (0+255+0+0)/4 == 63 per light channel, alpha untouched.
        assertArrayEquals(IntArray(4) { 0xFF3F3F3F.toInt() }, thumb.argb)
    }

    @Test
    fun thumbnailDimsFollowLongEdge() {
        assertEquals(48, computeBlurThumbArgb(IntArray(1000 * 500) { -1 }, 1000, 500)!!.width)
        assertEquals(24, computeBlurThumbArgb(IntArray(1000 * 500) { -1 }, 1000, 500)!!.height)
        assertEquals(48, computeBlurThumbArgb(IntArray(100 * 100) { -1 }, 100, 100)!!.width)
        assertEquals(48, computeBlurThumbArgb(IntArray(100 * 100) { -1 }, 100, 100)!!.height)
        // Smaller than the cap: kept as-is, smoothing only.
        val tiny = computeBlurThumbArgb(IntArray(30 * 10) { -1 }, 30, 10)!!
        assertEquals(30, tiny.width)
        assertEquals(10, tiny.height)
    }

    @Test
    fun badInputReturnsNull() {
        assertNull(computeBlurThumbArgb(IntArray(0), 0, 0))
        assertNull(computeBlurThumbArgb(IntArray(4), 4, 4))
        assertNull(computeBlurThumbArgb(IntArray(4), 0, 2))
        assertNull(computeBlurThumbArgb(IntArray(4), -2, 2))
    }

    @Test
    fun fillOnlyWhenAspectsDiffer() {
        assertFalse(artworkNeedsBlurFill(500, 500, 1000f, 1000f))
        assertFalse(artworkNeedsBlurFill(640, 480, 800f, 600f))
        assertTrue(artworkNeedsBlurFill(999, 1000, 1000f, 1000f))
        assertFalse(artworkNeedsBlurFill(0, 100, 100f, 100f))
        assertFalse(artworkNeedsBlurFill(100, 100, 0f, 100f))
    }
}
