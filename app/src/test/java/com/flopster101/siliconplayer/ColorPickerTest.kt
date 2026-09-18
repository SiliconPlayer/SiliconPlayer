package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.dialogs.formatHexRgb
import com.flopster101.siliconplayer.ui.dialogs.hsvToRgb
import com.flopster101.siliconplayer.ui.dialogs.parseHexColor
import com.flopster101.siliconplayer.ui.dialogs.rgbToHsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class ColorPickerTest {

    @Test
    fun `parseHexColor correctly parses various valid hex formats`() {
        assertEquals(0xFFFF0000.toInt(), parseHexColor("#FF0000"))
        assertEquals(0xFFFF0000.toInt(), parseHexColor("FF0000"))
        assertEquals(0xFF00FF00.toInt(), parseHexColor("#00FF00"))
        assertEquals(0xFF0000FF.toInt(), parseHexColor("#0000FF"))
        assertEquals(0xFF112233.toInt(), parseHexColor("#123"))
        assertEquals(0xFF112233.toInt(), parseHexColor("123"))
        assertEquals(0xFF4285F4.toInt(), parseHexColor("#4285F4"))
        assertEquals(0xFF4285F4.toInt(), parseHexColor("#FF4285F4"))
        assertEquals(0xFF4285F4.toInt(), parseHexColor("804285F4"))
    }

    @Test
    fun `parseHexColor returns null on invalid hex strings`() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#"))
        assertNull(parseHexColor("XYZ"))
        assertNull(parseHexColor("#12"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#1234567"))
        assertNull(parseHexColor("#123456789"))
    }

    @Test
    fun `formatHexRgb outputs standard uppercase 6 digit hex`() {
        assertEquals("#FF0000", formatHexRgb(255, 0, 0))
        assertEquals("#00FF00", formatHexRgb(0, 255, 0))
        assertEquals("#0000FF", formatHexRgb(0, 0, 255))
        assertEquals("#FFFFFF", formatHexRgb(255, 255, 255))
        assertEquals("#000000", formatHexRgb(0, 0, 0))
        assertEquals("#4285F4", formatHexRgb(0x42, 0x85, 0xF4))
    }

    @Test
    fun `rgbToHsv and hsvToRgb roundtrip accurately for key colors`() {
        val testColors = listOf(
            Triple(255, 0, 0),
            Triple(0, 255, 0),
            Triple(0, 0, 255),
            Triple(255, 255, 0),
            Triple(0, 255, 255),
            Triple(255, 0, 255),
            Triple(255, 255, 255),
            Triple(0, 0, 0),
            Triple(128, 128, 128),
            Triple(66, 133, 244),
            Triple(234, 67, 53)
        )

        for ((r, g, b) in testColors) {
            val hsv = rgbToHsv(r, g, b)
            val (r2, g2, b2) = hsvToRgb(hsv.hue, hsv.saturation, hsv.value)
            assertEquals("R mismatch for ($r, $g, $b)", r, r2)
            assertEquals("G mismatch for ($r, $g, $b)", g, g2)
            assertEquals("B mismatch for ($r, $g, $b)", b, b2)
        }
    }
}
