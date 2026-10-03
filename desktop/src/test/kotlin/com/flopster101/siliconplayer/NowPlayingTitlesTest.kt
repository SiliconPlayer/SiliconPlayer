package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTitlesTest {

    @Test
    fun namedSubtunePromotesTitleAndDemotesFileTitleToAlbum() {
        val resolved = resolveSubtuneNowPlayingText(
            fileTitle = "chippy_dippy",
            fileAlbum = "",
            subtuneCount = 3,
            subtuneTitle = "Chippy Dippy Song 2"
        )
        assertEquals("Chippy Dippy Song 2", resolved.title)
        assertEquals("chippy_dippy", resolved.album)
    }

    @Test
    fun singleSubtunePassesThrough() {
        val resolved = resolveSubtuneNowPlayingText(
            fileTitle = "tms",
            fileAlbum = "Famistuff",
            subtuneCount = 1,
            subtuneTitle = "tms"
        )
        assertEquals("tms", resolved.title)
        assertEquals("Famistuff", resolved.album)
    }

    @Test
    fun subtuneEchoingFileTitleKeepsTodayDisplay() {
        val resolved = resolveSubtuneNowPlayingText(
            fileTitle = "Comic Bakery",
            fileAlbum = "",
            subtuneCount = 20,
            subtuneTitle = "Comic Bakery"
        )
        assertEquals("Comic Bakery", resolved.title)
        assertEquals("", resolved.album)
    }

    @Test
    fun blankSubtuneTitleKeepsTodayDisplay() {
        val resolved = resolveSubtuneNowPlayingText(
            fileTitle = "Level Music",
            fileAlbum = "",
            subtuneCount = 4,
            subtuneTitle = "  "
        )
        assertEquals("Level Music", resolved.title)
        assertEquals("", resolved.album)
    }

    @Test
    fun badgeShowsOneBasedPosition() {
        assertEquals("[2/3]", subtuneBadgeText(1, 3))
    }

    @Test
    fun artistAlbumLineCombinesBoth() {
        assertEquals("Artist • Album", formatArtistAlbumLine("Artist", "Album"))
    }

    @Test
    fun artistAlbumLineHidesWhenBothBlank() {
        assertEquals("", formatArtistAlbumLine("", ""))
    }

    @Test
    fun artistAlbumLineKeepsSoleSurvivor() {
        assertEquals("Album", formatArtistAlbumLine("", "Album"))
        assertEquals("Artist", formatArtistAlbumLine("Artist", ""))
    }

    @Test
    fun badgedTitleAppendsBadge() {
        assertEquals("Song [2/3]", formatBadgedTitle("Song", "[2/3]"))
        assertEquals("Song", formatBadgedTitle("Song", null))
    }

    @Test
    fun badgeHiddenWithoutSubtunes() {
        assertEquals(null, subtuneBadgeText(0, 1))
        assertEquals(null, subtuneBadgeText(0, 0))
    }

    @Test
    fun unknownMarkerOnlyOnEstimates() {
        assertEquals("03:00", formatDurationWithUnknown(180.0, true))
        assertEquals("03:00?", formatDurationWithUnknown(180.0, false))
    }

    @Test
    fun blankFileTitleFallsBackToFileAlbum() {
        val resolved = resolveSubtuneNowPlayingText(
            fileTitle = "  ",
            fileAlbum = "Demozoo Entry",
            subtuneCount = 2,
            subtuneTitle = "Second Song"
        )
        assertEquals("Second Song", resolved.title)
        assertEquals("Demozoo Entry", resolved.album)
    }
}
