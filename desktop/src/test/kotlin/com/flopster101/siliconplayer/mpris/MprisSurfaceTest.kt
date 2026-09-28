package com.flopster101.siliconplayer.mpris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MprisSurfaceTest {

    @Test
    fun introspectionDeclaresTheStandardInterfaces() {
        listOf(
            "org.freedesktop.DBus.Introspectable",
            "org.freedesktop.DBus.Properties",
            "org.mpris.MediaPlayer2",
            "org.mpris.MediaPlayer2.Player"
        ).forEach { assertTrue(it, MPRIS_INTROSPECTION_XML.contains("<interface name=\"$it\">")) }
    }

    @Test
    fun introspectionDeclaresTheTransportMethods() {
        listOf("Next", "Previous", "Play", "Pause", "PlayPause", "Stop", "Seek", "SetPosition", "OpenUri", "Quit", "Raise")
            .forEach { assertTrue(it, MPRIS_INTROSPECTION_XML.contains("<method name=\"$it\"")) }
    }

    @Test
    fun introspectionMarksPositionReadOnlyAndVolumeWritable() {
        assertTrue(MPRIS_INTROSPECTION_XML.contains("<property name=\"Position\" type=\"x\" access=\"read\"/>"))
        assertTrue(MPRIS_INTROSPECTION_XML.contains("<property name=\"Volume\" type=\"d\" access=\"readwrite\"/>"))
        assertTrue(MPRIS_INTROSPECTION_XML.contains("<property name=\"LoopStatus\" type=\"s\" access=\"readwrite\"/>"))
        assertTrue(MPRIS_INTROSPECTION_XML.contains("<property name=\"Shuffle\" type=\"b\" access=\"read\"/>"))
    }

    @Test
    fun busNameAndPathFollowTheSpec() {
        assertEquals("/org/mpris/MediaPlayer2", MPRIS_PATH)
        assertTrue(MPRIS_BUS_NAME.startsWith("$MPRIS_ROOT_INTERFACE."))
    }

    @Test
    fun trackPathsAreUniqueObjectPaths() {
        assertEquals("/org/mpris/MediaPlayer2/Track/1", mprisTrackPath(1))
        assertEquals("/org/mpris/MediaPlayer2/Track/2", mprisTrackPath(2))
        assertTrue(MprisState().hasTrack().not())
        assertTrue(MprisState(trackId = mprisTrackPath(1)).hasTrack())
    }

    @Test
    fun playbackAndLoopStatusRoundTripThroughTheWireNames() {
        MprisPlaybackStatus.entries.forEach { assertEquals(it, MprisPlaybackStatus.fromWire(it.wire)) }
        MprisLoopStatus.entries.forEach { assertEquals(it, MprisLoopStatus.fromWire(it.wire)) }
        assertNull(MprisPlaybackStatus.fromWire("Bogus"))
        assertNull(MprisLoopStatus.fromWire("Bogus"))
    }

    @Test
    fun supportedUriSchemesCoverTheBrowsers() {
        assertEquals(listOf("file", "http", "https", "smb"), MPRIS_SUPPORTED_URI_SCHEMES)
        assertFalse(MPRIS_SUPPORTED_URI_SCHEMES.contains("archive"))
    }
}
