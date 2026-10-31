package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatLabelCatalogTest {

    @Test
    fun namesReportedFormatIds() {
        assertEquals("NES Sound Format", resolveFormatLabel("NSF")?.full)
        assertEquals("Abyss' Highest eXperience", resolveFormatLabel("AHX")?.full)
        assertEquals("Klystrack", resolveFormatLabel("kt")?.full)
    }

    @Test
    fun keepsShortFormForTightLines() {
        assertEquals("AHX", resolveFormatLabel("AHX")?.short)
        assertEquals("MiniGSF", resolveFormatLabel("MINIGSF")?.short)
    }

    @Test
    fun namesFormatsFfmpegReportsByDemuxer() {
        assertEquals("Free Lossless Audio Codec", resolveFormatLabel("raw FLAC")?.full)
        assertEquals("Dolby Digital (AC-3)", resolveFormatLabel("raw AC-3")?.full)
        assertEquals(
            "Low Complexity Subband Codec",
            resolveFormatLabel("raw SBC (low-complexity subband codec)")?.full
        )
        assertEquals("MPEG Audio Layer 2/3", resolveFormatLabel("MP2/3 (MPEG audio layer 2/3)")?.full)
    }

    @Test
    fun namesFormatsFfmpegReportsByCodec() {
        assertEquals("Advanced Audio Coding", resolveFormatLabel("AAC (Advanced Audio Coding)")?.full)
        assertEquals("AC-3", resolveFormatLabel("ATSC A/52A (AC-3)")?.short)
        assertEquals("DTS Coherent Acoustics", resolveFormatLabel("DCA (DTS Coherent Acoustics)")?.full)
        assertEquals("FLAC", resolveFormatLabel("FLAC (Free Lossless Audio Codec)")?.short)
    }

    @Test
    fun passesNamesCoresComposeThemselves() {
        assertEquals(
            "ProTracker MOD (M.K.)",
            resolveFormatLabel("ProTracker MOD (M.K.)")?.full
        )
    }

    @Test
    fun reportsNothingWithoutAFormatId() {
        assertNull(resolveFormatLabel(""))
        assertNull(resolveFormatLabel("   "))
    }
}
