package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.audio.defaultDspSettings
import com.flopster101.siliconplayer.audio.hasCoreDspOverrides
import com.flopster101.siliconplayer.audio.normalizeBassDepthPref
import com.flopster101.siliconplayer.audio.normalizeBassRangePref
import com.flopster101.siliconplayer.audio.normalizeSurroundDelayMsPref
import com.flopster101.siliconplayer.audio.readCoreDspSettings
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.audio.resolveEffectiveDspSettings
import com.flopster101.siliconplayer.audio.writeCoreDspSettings
import com.flopster101.siliconplayer.audio.writeGlobalDspSettings
import com.flopster101.siliconplayer.desktop.DesktopSongVolumeStore
import com.flopster101.siliconplayer.platform.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

private class InMemoryAppPreferences : AppPreferences {
    private val values = HashMap<String, Any?>()
    private inner class FakeEditor : AppPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { this@InMemoryAppPreferences.values[key] = values }
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

class DesktopAudioEffectsTest {

    @Test
    fun normalizesLegacyDspRanges() {
        assertEquals(2, normalizeBassDepthPref(2))
        assertEquals(0, normalizeBassDepthPref(8))
        assertEquals(3, normalizeBassRangePref(3))
        assertEquals(0, normalizeBassRangePref(21))
        assertEquals(25, normalizeSurroundDelayMsPref(25))
        assertEquals(5, normalizeSurroundDelayMsPref(7))
        assertEquals(10, normalizeSurroundDelayMsPref(8))
    }

    @Test
    fun resolvesEffectiveDspSettings() {
        val global = defaultDspSettings()
        val core = global.copy(bassDepth = (global.bassDepth + 1).coerceIn(0, 4))
        assertEquals(global, resolveEffectiveDspSettings(null, global, core, true, true))
        assertEquals(core, resolveEffectiveDspSettings("Core", global, core, true, false))
        assertEquals(core, resolveEffectiveDspSettings("Core", global, core, false, true))
        assertEquals(global, resolveEffectiveDspSettings("Core", global, core, false, false))
    }

    @Test
    fun roundTripsGlobalAndCoreDspSettings() {
        val prefs = InMemoryAppPreferences()
        assertEquals(defaultDspSettings(), readGlobalDspSettings(prefs))
        assertFalse(hasCoreDspOverrides(prefs, "FFmpeg"))

        val edited = defaultDspSettings().copy(bassEnabled = !defaultDspSettings().bassEnabled, reverbPreset = 7)
        writeGlobalDspSettings(prefs.edit(), edited)
        assertEquals(edited, readGlobalDspSettings(prefs))

        writeCoreDspSettings(prefs.edit(), "FFmpeg", edited)
        assertTrue(hasCoreDspOverrides(prefs, "FFmpeg"))
        assertEquals(edited, readCoreDspSettings(prefs, "FFmpeg"))
        assertEquals(defaultDspSettings(), readCoreDspSettings(prefs, null))
    }

    @Test
    fun songVolumeStoreRoundTripsAndPersists() {
        val dir = Files.createTempDirectory("siliconplayer-song-volumes-").toFile()
        dir.deleteOnExit()
        val file = File(dir, "song_volumes.json")
        val store = DesktopSongVolumeStore(file)

        assertNull(store.getSongVolume("/music/a.mp3"))
        assertFalse(store.getSongIgnoreCoreVolume("/music/a.mp3"))

        store.setSongVolume("/music/a.mp3", -3.5f)
        assertEquals(-3.5f, store.getSongVolume("/music/a.mp3")!!, 0.0001f)
        assertFalse(store.getSongIgnoreCoreVolume("/music/a.mp3"))

        store.setSongIgnoreCoreVolume("/music/a.mp3", true)
        assertTrue(store.getSongIgnoreCoreVolume("/music/a.mp3"))
        assertEquals(-3.5f, store.getSongVolume("/music/a.mp3")!!, 0.0001f)

        val reopened = DesktopSongVolumeStore(file)
        assertEquals(-3.5f, reopened.getSongVolume("/music/a.mp3")!!, 0.0001f)
        assertTrue(reopened.getSongIgnoreCoreVolume("/music/a.mp3"))

        reopened.resetAllSongVolumes()
        assertNull(reopened.getSongVolume("/music/a.mp3"))
        assertFalse(reopened.getSongIgnoreCoreVolume("/music/a.mp3"))
    }
}
