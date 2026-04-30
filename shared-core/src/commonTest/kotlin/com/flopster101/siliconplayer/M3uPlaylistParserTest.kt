package com.flopster101.siliconplayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class M3uPlaylistParserTest {

    @Test
    fun parseM3uPlaylistLinesUsesExtInfMetadataAndSubtuneFragment() {
        val document = parseM3uPlaylistLines(
            lines = listOf(
                "#EXTM3U",
                "#EXTINF:125,Artist - Song Title",
                "song.vgm#subtune=3"
            ),
            title = "List",
            format = PlaylistStoredFormat.M3u,
            sourceIdHint = "/music/list.m3u",
            resolveTarget = ::resolveAnyTarget,
            deriveFallbackTitle = ::fallbackTitle
        )

        val entry = assertNotNull(document).entries.single()
        assertEquals("List", document.title)
        assertEquals(PlaylistStoredFormat.M3u, document.format)
        assertEquals("/music/list.m3u", document.sourceIdHint)
        assertEquals("resolved/song.vgm", entry.source)
        assertEquals("Artist - Song Title", entry.title)
        assertEquals("Artist", entry.artist)
        assertEquals(2, entry.subtuneIndex)
        assertEquals(125.0, entry.durationSecondsOverride)
    }

    @Test
    fun parseM3uPlaylistLinesUsesEmbeddedMetadataAndHumanOneBasedModeForMultipleSubtunes() {
        val document = parseM3uPlaylistLines(
            lines = listOf(
                "multi.vgm::chip,1,Opening,1:05",
                "multi.vgm::chip,2,Ending,70"
            ),
            title = "Multi",
            format = PlaylistStoredFormat.M3u8,
            resolveTarget = ::resolveAnyTarget,
            deriveFallbackTitle = ::fallbackTitle
        )

        val entries = assertNotNull(document).entries
        assertEquals(listOf("Opening", "Ending"), entries.map { it.title })
        assertEquals(listOf(0, 1), entries.map { it.subtuneIndex })
        assertEquals(listOf(65.0, 70.0), entries.map { it.durationSecondsOverride })
    }

    @Test
    fun parseM3uPlaylistLinesLeavesUnrecognizedUrlFragmentsForResolver() {
        var resolvedRawEntry: String? = null

        val document = parseM3uPlaylistLines(
            lines = listOf("https://example.test/song.vgm#not-subtune"),
            title = "Remote",
            format = PlaylistStoredFormat.M3u,
            resolveTarget = { rawEntry ->
                resolvedRawEntry = rawEntry
                resolveAnyTarget(rawEntry)
            },
            deriveFallbackTitle = ::fallbackTitle
        )

        assertNotNull(document)
        assertEquals("https://example.test/song.vgm#not-subtune", resolvedRawEntry)
        assertNull(document.entries.single().subtuneIndex)
    }

    @Test
    fun parseM3uPlaylistLinesReturnsNullWhenTargetsDoNotResolve() {
        val document = parseM3uPlaylistLines(
            lines = listOf("#EXTINF:1,Missing", "missing.vgm"),
            title = "Missing",
            format = PlaylistStoredFormat.M3u,
            resolveTarget = { null },
            deriveFallbackTitle = ::fallbackTitle
        )

        assertNull(document)
    }

    private fun resolveAnyTarget(rawEntry: String): PlaylistTargetResolution {
        return PlaylistTargetResolution(
            source = "resolved/$rawEntry",
            requestUrlHint = null
        )
    }

    private fun fallbackTitle(source: String, lineIndex: Int): String {
        return "$source@$lineIndex"
    }
}
