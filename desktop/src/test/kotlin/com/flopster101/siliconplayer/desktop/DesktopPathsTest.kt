package com.flopster101.siliconplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopPathsTest {

    private fun tempDirectory(name: String): File =
        Files.createTempDirectory("siliconplayer-$name-").toFile().also { it.deleteOnExit() }

    @Test
    fun cacheDirFallsBackUnderHome() {
        val home = tempDirectory("home")
        val previousHome = System.getProperty("user.home")
        try {
            if (System.getenv("XDG_CACHE_HOME") != null) return
            System.setProperty("user.home", home.absolutePath)
            assertEquals(File(home, ".cache/siliconplayer"), DesktopPaths.cacheDir())
        } finally {
            System.setProperty("user.home", previousHome)
            home.deleteRecursively()
        }
    }

    @Test
    fun configDirFallsBackUnderHome() {
        val home = tempDirectory("home")
        val previousHome = System.getProperty("user.home")
        try {
            if (System.getenv("XDG_CONFIG_HOME") != null) return
            System.setProperty("user.home", home.absolutePath)
            assertEquals(File(home, ".config/siliconplayer"), DesktopPaths.configDir())
        } finally {
            System.setProperty("user.home", previousHome)
            home.deleteRecursively()
        }
    }

    @Test
    fun migratesLegacyCacheDirectory() {
        val home = tempDirectory("legacy-home")
        val legacyDir = File(home, ".siliconplayer/cache")
        val targetDir = tempDirectory("cache")
        val track = File(legacyDir, "progressive_remote_sources/track.bin")
        track.parentFile.mkdirs()
        track.writeText("cached-bytes")
        File(legacyDir, "artwork").mkdirs()

        migrateLegacyCacheDir(legacyDir, targetDir)

        assertEquals("cached-bytes", File(targetDir, "progressive_remote_sources/track.bin").readText())
        assertTrue(File(targetDir, "artwork").isDirectory)
        assertFalse(legacyDir.exists())
        assertFalse(File(home, ".siliconplayer").exists())
        home.deleteRecursively()
        targetDir.deleteRecursively()
    }

    @Test
    fun keepsAlreadyMigratedCacheItems() {
        val legacyDir = tempDirectory("legacy-cache")
        val targetDir = tempDirectory("cache")
        File(legacyDir, "artwork").mkdirs()
        File(targetDir, "artwork").mkdirs()
        File(targetDir, "artwork/keep.png").writeText("original")

        migrateLegacyCacheDir(legacyDir, targetDir)

        assertEquals("original", File(targetDir, "artwork/keep.png").readText())
        targetDir.deleteRecursively()
    }

    @Test
    fun migratesLegacyPreferencesSubtree() {
        val legacyNode = tempDirectory("legacy-prefs")
        val settingsDir = File(legacyNode, "silicon_player_settings")
        settingsDir.mkdirs()
        File(settingsDir, "prefs.xml").writeText("<prefs/>")
        File(settingsDir, ".lock").writeText("stale-lock")
        val targetNode = File(tempDirectory("config"), ".java/.userPrefs/com/flopster101/siliconplayer")

        migrateLegacyPreferencesDir(legacyNode, targetNode)

        assertEquals("<prefs/>", File(targetNode, "silicon_player_settings/prefs.xml").readText())
        assertFalse(File(targetNode, "silicon_player_settings/.lock").exists())
    }

    @Test
    fun keepsExistingPreferencesWhenTargetHasState() {
        val legacyNode = tempDirectory("legacy-prefs")
        val settingsDir = File(legacyNode, "silicon_player_settings")
        settingsDir.mkdirs()
        File(settingsDir, "prefs.xml").writeText("<legacy/>")
        val targetNode = File(tempDirectory("config"), ".java/.userPrefs/com/flopster101/siliconplayer")
        File(targetNode, "silicon_player_settings").mkdirs()
        File(targetNode, "silicon_player_settings/prefs.xml").writeText("<current/>")

        migrateLegacyPreferencesDir(legacyNode, targetNode)

        assertEquals("<current/>", File(targetNode, "silicon_player_settings/prefs.xml").readText())
    }
}
