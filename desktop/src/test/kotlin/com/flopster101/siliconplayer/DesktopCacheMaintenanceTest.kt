package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.clearArchiveMountCache
import com.flopster101.siliconplayer.data.enforceArchiveMountCacheLimits
import com.flopster101.siliconplayer.platform.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

private class CacheTestPrefs : AppPreferences {
    private val values = HashMap<String, Any?>()
    private inner class FakeEditor : AppPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { this@CacheTestPrefs.values[key] = values }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun apply() {}
        override fun commit() = true
    }

    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = values[key] as? Set<String> ?: defValues
    override fun contains(key: String) = values.containsKey(key)
    override fun edit(): AppPreferences.Editor = FakeEditor()
    override fun addListener(listener: AppPreferences.OnChangeListener) {}
    override fun removeListener(listener: AppPreferences.OnChangeListener) {}
}

class DesktopCacheMaintenanceTest {

    private fun tempDir(prefix: String): File {
        val dir = Files.createTempDirectory(prefix).toFile()
        dir.deleteOnExit()
        return dir
    }

    @Test
    fun listsEnforcesAndClearsRemoteCache() {
        val cacheRoot = tempDir("siliconplayer-remote-cache-")
        val first = File(cacheRoot, "aaa_track.mp3").apply { writeBytes(ByteArray(100)) }
        Thread.sleep(15)
        File(cacheRoot, "bbb_track.mp3").apply { writeBytes(ByteArray(100)) }
        first.setLastModified(System.currentTimeMillis() - 60_000)

        val listed = listCachedSourceFiles(cacheRoot)
        assertEquals(2, listed.size)
        assertEquals("bbb_track.mp3", listed.first().fileName)

        val pruned = enforceRemoteCacheLimits(cacheRoot, maxTracks = 1, maxBytes = Long.MAX_VALUE)
        assertEquals(1, pruned.deletedFiles)
        assertEquals(100L, pruned.freedBytes)
        assertTrue(File(cacheRoot, "bbb_track.mp3").exists())

        rememberSourceForCachedFile(cacheRoot, "bbb_track.mp3", "https://example.com/b.mp3")
        assertEquals("https://example.com/b.mp3", sourceIdForCachedFileName(cacheRoot, "bbb_track.mp3"))

        val cleared = clearRemoteCacheFiles(cacheRoot)
        assertEquals(1, cleared.deletedFiles)
        assertEquals(0, cleared.skippedFiles)
        assertTrue(listCachedSourceFiles(cacheRoot).isEmpty())
    }

    @Test
    fun deletesSpecificCacheFilesWithProtection() {
        val cacheRoot = tempDir("siliconplayer-remote-delete-")
        File(cacheRoot, "keep.mp3").apply { writeBytes(ByteArray(10)) }
        File(cacheRoot, "drop.mp3").apply { writeBytes(ByteArray(10)) }
        val keepPath = File(cacheRoot, "keep.mp3").absolutePath

        val result = deleteSpecificRemoteCacheFiles(
            cacheRoot,
            setOf(keepPath, File(cacheRoot, "drop.mp3").absolutePath, File(cacheRoot, "missing.mp3").absolutePath),
            protectedPaths = setOf(keepPath)
        )
        assertEquals(1, result.deletedFiles)
        assertEquals(1, result.skippedFiles)
        assertEquals(1, result.missingFiles)
        assertTrue(File(cacheRoot, "keep.mp3").exists())
    }

    @Test
    fun clearsAndEnforcesArchiveMountCache() {
        val cacheDir = tempDir("siliconplayer-archive-cache-")
        val mountRoot = File(cacheDir, "archive_mounts").apply { mkdirs() }
        val oldMount = File(mountRoot, "old").apply { mkdirs() }
        File(oldMount, ".ready").writeText("stamp")
        File(oldMount, "track.mod").writeBytes(ByteArray(50))
        oldMount.setLastModified(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        File(oldMount, ".ready").setLastModified(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)

        val pruned = enforceArchiveMountCacheLimits(cacheDir, maxMounts = 10, maxBytes = Long.MAX_VALUE, maxAgeDays = 7)
        assertEquals(1, pruned.deletedMounts)
        assertTrue(pruned.freedBytes >= 50L)

        val freshMount = File(mountRoot, "fresh").apply { mkdirs() }
        File(freshMount, ".ready").writeText("stamp")
        val cleared = clearArchiveMountCache(cacheDir)
        assertEquals(1, cleared.deletedMounts)
    }

    @Test
    fun appliesRemoteSourceCachePolicyOnLaunch() {
        val prefs = CacheTestPrefs()
        val cacheRoot = tempDir("siliconplayer-remote-policy-")
        val older = File(cacheRoot, "first.mp3").apply { writeBytes(ByteArray(100)) }
        older.setLastModified(System.currentTimeMillis() - 60_000)
        val newer = File(cacheRoot, "second.mp3").apply { writeBytes(ByteArray(100)) }

        prefs.edit()
            .putInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, 1)
            .putLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, Long.MAX_VALUE)
            .apply()
        val pruned = applyRemoteSourceCachePolicy(prefs, cacheRoot)
        assertFalse(pruned.clearedOnLaunch)
        assertEquals(1, pruned.deletedFiles)
        assertTrue(newer.exists())

        prefs.edit().putBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, true).apply()
        val cleared = applyRemoteSourceCachePolicy(prefs, cacheRoot, protectedPaths = setOf(newer.absolutePath))
        assertTrue(cleared.clearedOnLaunch)
        assertEquals(1, cleared.skippedFiles)
        assertTrue(newer.exists())
    }

    @Test
    fun appliesArchiveMountCachePolicyOnLaunch() {
        val prefs = CacheTestPrefs()
        val cacheDir = tempDir("siliconplayer-archive-policy-")
        val mountRoot = File(cacheDir, "archive_mounts").apply { mkdirs() }
        val olderMount = File(mountRoot, "older").apply { mkdirs() }
        File(olderMount, ".ready").writeText("stamp")
        File(olderMount, "track.mod").writeBytes(ByteArray(64))
        olderMount.setLastModified(System.currentTimeMillis() - 60_000)
        File(olderMount, ".ready").setLastModified(System.currentTimeMillis() - 60_000)
        val newerMount = File(mountRoot, "newer").apply { mkdirs() }
        File(newerMount, ".ready").writeText("stamp")

        prefs.edit().putInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, 1).apply()
        val pruned = applyArchiveMountCachePolicy(prefs, cacheDir)
        assertFalse(pruned.clearedOnLaunch)
        assertEquals(1, pruned.deletedMounts)

        prefs.edit().putBoolean(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, true).apply()
        val cleared = applyArchiveMountCachePolicy(prefs, cacheDir)
        assertTrue(cleared.clearedOnLaunch)
        assertEquals(1, cleared.deletedMounts)
    }

    @Test
    fun enforcesCacheLimitsFromPrefs() {
        val prefs = CacheTestPrefs()
        val cacheRoot = tempDir("siliconplayer-remote-prefs-")
        File(cacheRoot, "a.mp3").apply { writeBytes(ByteArray(100)) }
        File(cacheRoot, "b.mp3").apply { writeBytes(ByteArray(100)) }
        rememberSourceForCachedFile(cacheRoot, "a.mp3", "https://example.com/a.mp3")
        rememberSourceForCachedFile(cacheRoot, "b.mp3", "https://example.com/b.mp3")
        // The index is old enough to be pruned first; it must never be treated as a track.
        File(cacheRoot, ".source_index.json").setLastModified(System.currentTimeMillis() - 120_000)
        prefs.edit().putInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, 1).apply()
        val pruned = enforceRemoteCacheLimitsFromPrefs(prefs, cacheRoot)
        assertEquals(1, pruned.deletedFiles)
        assertTrue(File(cacheRoot, ".source_index.json").exists())

        val cacheDir = tempDir("siliconplayer-archive-prefs-")
        val mountRoot = File(cacheDir, "archive_mounts").apply { mkdirs() }
        File(mountRoot, "mount").apply {
            mkdirs()
            File(this, ".ready").writeText("stamp")
        }
        prefs.edit().putInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, 1).apply()
        val archivePruned = enforceArchiveMountCacheLimitsFromPrefs(prefs, cacheDir)
        assertEquals(0, archivePruned.deletedMounts)
    }

    @Test
    fun resetsVisualizationSettingsToDefaults() {
        val prefs = CacheTestPrefs()
        prefs.edit()
            .putInt(AppPreferenceKeys.VISUALIZATION_BAR_COUNT, 99)
            .putBoolean(AppPreferenceKeys.VISUALIZATION_OSC_STEREO, true)
            .putString(AppPreferenceKeys.VISUALIZATION_VU_ANCHOR, "top")
            .apply()

        resetVisualizationBarsSettings(prefs)
        resetVisualizationOscilloscopeSettings(prefs)
        resetVisualizationVuSettings(prefs)

        assertEquals(AppDefaults.Visualization.Bars.count, prefs.getInt(AppPreferenceKeys.VISUALIZATION_BAR_COUNT, -1))
        assertEquals(AppDefaults.Visualization.Oscilloscope.stereo, prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_OSC_STEREO, false))
        assertEquals(
            AppDefaults.Visualization.Vu.anchor.storageValue,
            prefs.getString(AppPreferenceKeys.VISUALIZATION_VU_ANCHOR, null)
        )
    }

    @Test
    fun clearsAllAudioParameterPrefs() {
        val prefs = CacheTestPrefs()
        prefs.edit()
            .putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, -6f)
            .putBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, true)
            .putBoolean(AppPreferenceKeys.audioDspCoreBassEnabledKey("FFmpeg"), true)
            .apply()

        clearAllAudioParameterPrefs(prefs)

        assertTrue(prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f) == 0f)
        assertEquals(
            AppDefaults.AudioProcessing.Dsp.bassEnabled,
            prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, AppDefaults.AudioProcessing.Dsp.bassEnabled)
        )
        assertTrue(!prefs.contains(AppPreferenceKeys.audioDspCoreBassEnabledKey("FFmpeg")))
    }
}
