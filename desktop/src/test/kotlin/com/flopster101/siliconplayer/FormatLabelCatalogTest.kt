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
