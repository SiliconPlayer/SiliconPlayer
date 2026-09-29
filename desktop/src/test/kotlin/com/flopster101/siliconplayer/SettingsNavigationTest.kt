package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun testCanOpenCoreSettingsForKnownDecodersAndAliases() {
        assertTrue(canOpenCoreSettingsForDecoder("uFMOD-C"))
        assertTrue(canOpenCoreSettingsForDecoder("ufmod"))
        assertTrue(canOpenCoreSettingsForDecoder("LibOpenMPT"))
        assertTrue(canOpenCoreSettingsForDecoder("openmpt"))
        assertTrue(canOpenCoreSettingsForDecoder("FFmpeg"))
        assertTrue(canOpenCoreSettingsForDecoder("ffmpeg"))
        assertTrue(canOpenCoreSettingsForDecoder("Game Music Emu"))
        assertTrue(canOpenCoreSettingsForDecoder("gme"))
        assertTrue(canOpenCoreSettingsForDecoder("cRSID"))
        assertTrue(canOpenCoreSettingsForDecoder("sid"))
        assertTrue(canOpenCoreSettingsForDecoder("Furnace"))
        assertTrue(canOpenCoreSettingsForDecoder("dmf"))
        assertTrue(canOpenCoreSettingsForDecoder("AdPlug"))
        assertTrue(canOpenCoreSettingsForDecoder("opl"))
        assertTrue(canOpenCoreSettingsForDecoder("HivelyTracker"))
        assertTrue(canOpenCoreSettingsForDecoder("hvl"))
        assertTrue(canOpenCoreSettingsForDecoder("Klystrack"))
        assertTrue(canOpenCoreSettingsForDecoder("kt"))
        assertTrue(canOpenCoreSettingsForDecoder("UADE"))
        assertTrue(canOpenCoreSettingsForDecoder("amiga"))
    }

    @Test
    fun testCanOpenCoreSettingsForInvalidOrNullDecoders() {
        assertFalse(canOpenCoreSettingsForDecoder(null))
        assertFalse(canOpenCoreSettingsForDecoder(""))
        assertFalse(canOpenCoreSettingsForDecoder("   "))
        assertFalse(canOpenCoreSettingsForDecoder("UnknownNonExistentCore"))
    }

    @Test
    fun testOpenCurrentCoreSettingsNavigatesAndCollapsesPlayer() {
        var currentView = MainView.Home
        var settingsRoute = SettingsRoute.Root
        var settingsRouteHistory = listOf(SettingsRoute.GeneralAudio)
        var settingsReturnView = MainView.Home
        var selectedPluginName: String? = null
        var isPlayerExpanded = true

        val coordinator = buildSettingsNavigationCoordinator(
            currentView = currentView,
            settingsRoute = settingsRoute,
            settingsRouteHistory = settingsRouteHistory,
            settingsReturnView = settingsReturnView,
            lastUsedCoreName = "uFMOD-C",
            setSettingsRoute = { settingsRoute = it },
            setSettingsRouteHistory = { settingsRouteHistory = it },
            setSettingsReturnView = { settingsReturnView = it },
            setCurrentView = { currentView = it },
            setSelectedPluginName = { selectedPluginName = it },
            setPlayerExpanded = { isPlayerExpanded = it }
        )

        coordinator.openCurrentCoreSettings()

        assertEquals(DecoderNames.UFMOD, selectedPluginName)
        assertEquals(SettingsRoute.PluginDetail, settingsRoute)
        assertEquals(emptyList<SettingsRoute>(), settingsRouteHistory)
        assertEquals(MainView.Settings, currentView)
        assertEquals(MainView.Home, settingsReturnView)
        assertFalse(isPlayerExpanded)

        // Exit settings returns to the view from before.
        coordinator.exitSettingsToReturnView()
        assertEquals(MainView.Home, currentView)
        assertEquals(SettingsRoute.Root, settingsRoute)
    }

    @Test
    fun testOpenCurrentCoreSettingsWithNullCoreDoesNothing() {
        var currentView = MainView.Home
        var settingsRoute = SettingsRoute.Root
        var settingsRouteHistory = emptyList<SettingsRoute>()
        var selectedPluginName: String? = null
        var isPlayerExpanded = true

        val coordinator = buildSettingsNavigationCoordinator(
            currentView = currentView,
            settingsRoute = settingsRoute,
            settingsRouteHistory = settingsRouteHistory,
            settingsReturnView = MainView.Home,
            lastUsedCoreName = null,
            setSettingsRoute = { settingsRoute = it },
            setSettingsRouteHistory = { settingsRouteHistory = it },
            setSettingsReturnView = { },
            setCurrentView = { currentView = it },
            setSelectedPluginName = { selectedPluginName = it },
            setPlayerExpanded = { isPlayerExpanded = it }
        )

        coordinator.openCurrentCoreSettings()

        assertEquals(null, selectedPluginName)
        assertEquals(SettingsRoute.Root, settingsRoute)
        assertEquals(MainView.Home, currentView)
        assertTrue(isPlayerExpanded)
    }
}
