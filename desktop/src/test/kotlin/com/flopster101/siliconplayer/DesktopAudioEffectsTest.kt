package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.audio.EffectChain
import com.flopster101.siliconplayer.audio.EffectChainScopeTarget
import com.flopster101.siliconplayer.audio.EffectNode
import com.flopster101.siliconplayer.audio.EffectNodeIds
import com.flopster101.siliconplayer.audio.EffectPreset
import com.flopster101.siliconplayer.audio.NodeOptions
import com.flopster101.siliconplayer.audio.higherPriorityOverrideScope
import com.flopster101.siliconplayer.audio.queryEffectDescriptors
import com.flopster101.siliconplayer.audio.readBypassAllEffects
import com.flopster101.siliconplayer.audio.readEffectPresets
import com.flopster101.siliconplayer.audio.resolveInheritedChainForScope
import com.flopster101.siliconplayer.audio.resolveInitialDialogScope
import com.flopster101.siliconplayer.audio.writeBypassAllEffects
import com.flopster101.siliconplayer.audio.writeEffectPresets
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.applyEffectChainToNative
import com.flopster101.siliconplayer.audio.defaultDspSettings
import com.flopster101.siliconplayer.audio.hasCoreDspOverrides
import com.flopster101.siliconplayer.audio.normalizeBassDepthPref
import com.flopster101.siliconplayer.audio.normalizeBassRangePref
import com.flopster101.siliconplayer.audio.normalizeSurroundDelayMsPref
import com.flopster101.siliconplayer.audio.readCoreDspSettings
import com.flopster101.siliconplayer.audio.readCoreEffectChain
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.audio.readGlobalEffectChain
import com.flopster101.siliconplayer.audio.resolveEffectiveDspSettings
import com.flopster101.siliconplayer.audio.resolveEffectiveEffectChain
import com.flopster101.siliconplayer.audio.resolveEffectiveEffectChainForPrefs
import com.flopster101.siliconplayer.audio.writeCoreDspSettings
import com.flopster101.siliconplayer.audio.writeCoreEffectChain
import com.flopster101.siliconplayer.audio.ScopeKey
import com.flopster101.siliconplayer.audio.readPlaylistEffectChain
import com.flopster101.siliconplayer.audio.readScopeEffectChain
import com.flopster101.siliconplayer.audio.readSongEffectChain
import com.flopster101.siliconplayer.audio.writeGlobalDspSettings
import com.flopster101.siliconplayer.audio.writeGlobalEffectChain
import com.flopster101.siliconplayer.audio.writePlaylistEffectChain
import com.flopster101.siliconplayer.audio.writeSongEffectChain
import com.flopster101.siliconplayer.desktop.DesktopSongVolumeStore
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.settings.porting.ExportResult
import com.flopster101.siliconplayer.settings.porting.PortingPlatformFamily
import com.flopster101.siliconplayer.settings.porting.PortingSelection
import com.flopster101.siliconplayer.settings.porting.SettingsPortingEngine
import com.flopster101.siliconplayer.settings.porting.buildSettingsPortingCatalog
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) = values[key] as? Set<String> ?: defValues
    override fun contains(key: String) = values.containsKey(key)
    override fun allKeys(): Set<String> = values.keys
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
    fun effectChainJsonHandlesDefaultsDropsAndInvalidSchemas() {
        assertEquals(EffectChain.Empty, EffectChain.fromJson(null))
        assertEquals(EffectChain.Empty, EffectChain.fromJson(""))
        assertEquals(EffectChain.Empty, EffectChain.fromJson("not-json"))
        assertEquals(EffectChain.Empty, EffectChain.fromJson("""{"version":2,"nodes":[{"id":"gain"}]}"""))
        assertEquals(EffectChain.Empty, EffectChain.fromJson("""{"version":0,"nodes":[{"id":"gain"}]}"""))

        val messyJson = """
            {
              "extraTop": [1, 2, {"nested": true}],
              "master": { "mono": true, "futureFlag": 99 },
              "nodes": [
                { "id": "unknown.future_fx", "enabled": true, "params": { "foo": 1 } },
                { "id": "openmpt.surround", "options": { "mono": true, "unknownOpt": "x" }, "params": { "delayMs": 18, "bogus": 42 } },
                { "id": "gain", "enabled": false, "params": {} }
              ]
            }
        """.trimIndent()

        val parsed = EffectChain.fromJson(messyJson)
        assertFalse(parsed.input.mono)
        assertTrue(parsed.master.mono)
        assertEquals(2, parsed.nodes.size)
        assertEquals(EffectNodeIds.OPENMPT_SURROUND, parsed.nodes[0].id)
        assertTrue(parsed.nodes[0].enabled)
        assertTrue(parsed.nodes[0].options.mono)
        assertEquals(8f, parsed.nodes[0].params["depth"]!!, 0f)
        assertEquals(20f, parsed.nodes[0].params["delayMs"]!!, 0f)
        assertFalse(parsed.nodes[0].params.containsKey("bogus"))

        assertEquals(EffectNodeIds.GAIN, parsed.nodes[1].id)
        assertFalse(parsed.nodes[1].enabled)
        assertEquals(0f, parsed.nodes[1].params["gainDb"]!!, 0f)

        val roundTripped = EffectChain.fromJson(parsed.toJson())
        assertEquals(parsed, roundTripped)
    }

    @Test
    fun migratesLegacyPrefsAndResolvesMostSpecificFirst() {
        val prefs = InMemoryAppPreferences()
        assertEquals(EffectChain.Empty, readGlobalEffectChain(prefs))
        assertNull(readCoreEffectChain(prefs, "FFmpeg"))

        writeGlobalDspSettings(
            prefs.edit(),
            defaultDspSettings().copy(
                bassEnabled = true,
                bassDepth = 4,
                bassRange = 1,
                reverbEnabled = true,
                reverbDepth = 12,
                reverbPreset = 5
            )
        )
        prefs.edit().putBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, true).apply()

        val migratedGlobal = readGlobalEffectChain(prefs)
        assertTrue(migratedGlobal.master.mono)
        assertEquals(
            listOf(EffectNodeIds.OPENMPT_BASS, EffectNodeIds.OPENMPT_REVERB),
            migratedGlobal.nodes.map { it.id }
        )
        assertEquals(4f, migratedGlobal.nodes[0].params["depth"]!!, 0f)
        assertEquals(5f, migratedGlobal.nodes[1].params["preset"]!!, 0f)

        assertEquals(migratedGlobal, resolveEffectiveEffectChainForPrefs(prefs, "cRSID"))

        prefs.edit().putBoolean(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey("FFmpeg"), true).apply()
        val migratedCore = readCoreEffectChain(prefs, "FFmpeg")
        assertTrue(migratedCore != null && migratedCore.nodes.isEmpty())
        assertTrue(resolveEffectiveEffectChainForPrefs(prefs, "FFmpeg").nodes.isEmpty())

        writeCoreEffectChain(prefs.edit(), "FFmpeg", null)
        assertNull(readCoreEffectChain(prefs, "FFmpeg"))
        assertEquals(migratedGlobal, resolveEffectiveEffectChainForPrefs(prefs, "FFmpeg"))

        val gChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -1f))))
        val cChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -2f))))
        val pChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -3f))))
        val sChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -4f))))
        assertEquals(gChain, resolveEffectiveEffectChain(gChain, null, null, null))
        assertEquals(cChain, resolveEffectiveEffectChain(gChain, cChain, null, null))
        assertEquals(pChain, resolveEffectiveEffectChain(gChain, cChain, pChain, null))
        assertEquals(sChain, resolveEffectiveEffectChain(gChain, cChain, pChain, sChain))
    }

    @Test
    fun settingsPortingRoundTripsCustomOrderedGlobalAndCoreChains() {
        val srcPrefs = InMemoryAppPreferences()
        val customGlobal = EffectChain(
            input = NodeOptions(mono = false),
            master = NodeOptions(mono = true),
            nodes = listOf(
                EffectNode(
                    id = EffectNodeIds.OPENMPT_REVERB,
                    enabled = true,
                    options = NodeOptions(mono = true),
                    params = mapOf("depth" to 11f, "preset" to 13f)
                ),
                EffectNode(
                    id = EffectNodeIds.GAIN,
                    enabled = true,
                    params = mapOf("gainDb" to -3.5f)
                ),
                EffectNode(
                    id = EffectNodeIds.OPENMPT_BASS,
                    enabled = true,
                    params = mapOf("depth" to 3f, "range" to 1f)
                )
            )
        )
        val customCore = EffectChain(
            input = NodeOptions(mono = true),
            master = NodeOptions(mono = false),
            nodes = listOf(
                EffectNode(
                    id = EffectNodeIds.OPENMPT_BITCRUSH,
                    enabled = true,
                    params = mapOf("bits" to 8f)
                )
            )
        )
        writeGlobalEffectChain(srcPrefs.edit(), customGlobal)
        writeCoreEffectChain(srcPrefs.edit(), "cRSID", customCore)

        val srcCatalog = buildSettingsPortingCatalog(srcPrefs)
        val export = SettingsPortingEngine.buildExport(
            srcPrefs,
            srcCatalog,
            PortingSelection(setOf("audio.effects.global"))
        )
        assertTrue(export is ExportResult.Ok)
        val payload = (export as ExportResult.Ok).text

        val dstPrefs = InMemoryAppPreferences()
        val dstCatalog = buildSettingsPortingCatalog(dstPrefs)
        val skipped = SettingsPortingEngine.applyImport(
            dstPrefs,
            dstCatalog,
            payload,
            PortingSelection(setOf("audio.effects.global")),
            PortingPlatformFamily.Desktop
        )
        assertTrue("Expected zero skipped items on chain import, got ${skipped.map { it.key }}", skipped.isEmpty())
        assertEquals(customGlobal, readGlobalEffectChain(dstPrefs))
        assertEquals(customCore, readCoreEffectChain(dstPrefs, "cRSID"))
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

        val songChain = EffectChain(
            master = NodeOptions(mono = true),
            nodes = listOf(
                EffectNode(
                    id = EffectNodeIds.GAIN,
                    enabled = true,
                    params = mapOf("gainDb" to -5.0f)
                )
            )
        )
        writeSongEffectChain(store, "/music/a.mp3", songChain)
        assertEquals(songChain, readSongEffectChain(store, "/music/a.mp3"))
        assertEquals(-3.5f, store.getSongVolume("/music/a.mp3")!!, 0.0001f)
        assertTrue(store.getSongIgnoreCoreVolume("/music/a.mp3"))

        val reopened = DesktopSongVolumeStore(file)
        assertEquals(-3.5f, reopened.getSongVolume("/music/a.mp3")!!, 0.0001f)
        assertTrue(reopened.getSongIgnoreCoreVolume("/music/a.mp3"))
        assertEquals(songChain, readSongEffectChain(reopened, "/music/a.mp3"))

        writeSongEffectChain(reopened, "/music/a.mp3", null)
        assertNull(readSongEffectChain(reopened, "/music/a.mp3"))
        assertEquals(-3.5f, reopened.getSongVolume("/music/a.mp3")!!, 0.0001f)

        writeSongEffectChain(reopened, "/music/b.mp3", EffectChain.Empty)
        assertEquals(EffectChain.Empty, readSongEffectChain(reopened, "/music/b.mp3"))

        reopened.resetAllSongVolumes()
        assertNull(reopened.getSongVolume("/music/a.mp3"))
        assertFalse(reopened.getSongIgnoreCoreVolume("/music/a.mp3"))
        assertNull(readSongEffectChain(reopened, "/music/b.mp3"))
    }

    @Test
    fun songAndPlaylistScopesResolveWithReplacePrecedence() {
        val prefs = InMemoryAppPreferences()
        val dir = Files.createTempDirectory("siliconplayer-scope-resolve-").toFile()
        dir.deleteOnExit()
        val store = DesktopSongVolumeStore(File(dir, "song_volumes.json"))

        val gChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -1f))))
        val cChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -2f))))
        val pChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -3f))))
        val sChain = EffectChain(nodes = listOf(EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -4f))))

        writeGlobalEffectChain(prefs.edit(), gChain)
        writeCoreEffectChain(prefs.edit(), "OpenMPT", cChain)

        var playlistState = PlaylistLibraryState(
            playlists = listOf(
                StoredPlaylist(id = "pl-1", title = "Playlist 1")
            )
        )
        playlistState = writePlaylistEffectChain(playlistState, "pl-1", pChain)
        val activePlaylist = playlistState.playlists.first()
        assertEquals(pChain, readPlaylistEffectChain(activePlaylist))
        assertEquals(pChain, readScopeEffectChain(ScopeKey.Playlist("pl-1"), prefs, store, playlistState, activePlaylist))

        writeSongEffectChain(store, "/tracks/song1.mod", sChain)
        assertEquals(sChain, readScopeEffectChain(ScopeKey.Song("/tracks/song1.mod"), prefs, store, playlistState, activePlaylist))

        assertEquals(
            sChain,
            resolveEffectiveEffectChainForPrefs(
                prefs = prefs,
                coreName = "OpenMPT",
                playlist = activePlaylist,
                playlistState = playlistState,
                songVolumeStore = store,
                songPath = "/tracks/song1.mod"
            )
        )

        writeSongEffectChain(store, "/tracks/song1.mod", null)
        assertEquals(
            pChain,
            resolveEffectiveEffectChainForPrefs(
                prefs = prefs,
                coreName = "OpenMPT",
                playlist = activePlaylist,
                playlistState = playlistState,
                songVolumeStore = store,
                songPath = "/tracks/song1.mod"
            )
        )

        writeSongEffectChain(store, "/tracks/song1.mod", EffectChain.Empty)
        assertEquals(
            EffectChain.Empty,
            resolveEffectiveEffectChainForPrefs(
                prefs = prefs,
                coreName = "OpenMPT",
                playlist = activePlaylist,
                playlistState = playlistState,
                songVolumeStore = store,
                songPath = "/tracks/song1.mod"
            )
        )

        writeSongEffectChain(store, "/tracks/song1.mod", null)
        playlistState = writePlaylistEffectChain(playlistState, "pl-1", null)
        val clearedPlaylist = playlistState.playlists.first()
        assertEquals(
            cChain,
            resolveEffectiveEffectChainForPrefs(
                prefs = prefs,
                coreName = "OpenMPT",
                playlist = clearedPlaylist,
                playlistState = playlistState,
                songVolumeStore = store,
                songPath = "/tracks/song1.mod"
            )
        )
    }

    private fun writeStereoWav(file: File, sampleRate: Int, frames: Int) {
        val pcmBytes = ByteArray(frames * 4)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate.toDouble()
            val left = (kotlin.math.sin(2.0 * kotlin.math.PI * 110.0 * t) * 14000.0 +
                    kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * t) * 8000.0).toInt()
            val right = (kotlin.math.sin(2.0 * kotlin.math.PI * 110.0 * t) * 10000.0 -
                    kotlin.math.sin(2.0 * kotlin.math.PI * 660.0 * t) * 9000.0).toInt()
            val base = i * 4
            pcmBytes[base] = (left and 0xFF).toByte()
            pcmBytes[base + 1] = ((left shr 8) and 0xFF).toByte()
            pcmBytes[base + 2] = (right and 0xFF).toByte()
            pcmBytes[base + 3] = ((right shr 8) and 0xFF).toByte()
        }
        val header = ByteArray(44)
        fun putAscii(offset: Int, s: String) = s.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        fun putInt32Le(offset: Int, v: Int) {
            for (b in 0..3) header[offset + b] = ((v shr (8 * b)) and 0xFF).toByte()
        }
        fun putInt16Le(offset: Int, v: Int) {
            header[offset] = (v and 0xFF).toByte()
            header[offset + 1] = ((v shr 8) and 0xFF).toByte()
        }
        putAscii(0, "RIFF")
        putInt32Le(4, 36 + pcmBytes.size)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putInt32Le(16, 16)
        putInt16Le(20, 1)
        putInt16Le(22, 2)
        putInt32Le(24, sampleRate)
        putInt32Le(28, sampleRate * 4)
        putInt16Le(32, 4)
        putInt16Le(34, 16)
        putAscii(36, "data")
        putInt32Le(40, pcmBytes.size)
        file.writeBytes(header + pcmBytes)
    }

    @Test
    fun nativeEffectChainHonorsOrderMonoPlacementJsonAndLegacyShims() {
        useSilentTestAudioOutput()
        val dir = Files.createTempDirectory("siliconplayer-fx-chain-").toFile()
        dir.deleteOnExit()
        val wav = File(dir, "stereo.wav")
        writeStereoWav(wav, sampleRate = 48_000, frames = 8_192)

        fun renderChain(chain: EffectChain): FloatArray {
            NativeBridge.loadAudio(wav.absolutePath)
            applyEffectChainToNative(chain)
            val out = FloatArray(4096 * 2)
            val rendered = NativeBridge.renderOfflineAudioFrames(out, 4096, 48_000)
            assertEquals(4096, rendered)
            return out
        }

        try {
            val descriptors = JSONArray(NativeBridge.getEffectDescriptorsJson())
            val descriptorIds = (0 until descriptors.length())
                .map { descriptors.getJSONObject(it).getString("id") }
                .toSet()
            assertEquals(
                setOf(
                    EffectNodeIds.OPENMPT_BASS,
                    EffectNodeIds.OPENMPT_SURROUND,
                    EffectNodeIds.OPENMPT_REVERB,
                    EffectNodeIds.OPENMPT_BITCRUSH,
                    EffectNodeIds.GAIN
                ),
                descriptorIds
            )

            NativeBridge.setEffectChain(
                """{"version":1,"input":{"mono":true},"nodes":[{"id":"unknown.node"},{"id":"gain","params":{"gainDb":-4.5}}]}"""
            )
            val nativeRoundTrip = EffectChain.fromJson(NativeBridge.getEffectChain())
            assertTrue(nativeRoundTrip.input.mono)
            assertFalse(nativeRoundTrip.master.mono)
            assertEquals(1, nativeRoundTrip.nodes.size)
            assertEquals(EffectNodeIds.GAIN, nativeRoundTrip.nodes[0].id)
            assertEquals(-4.5f, nativeRoundTrip.nodes[0].params["gainDb"]!!, 0.001f)

            applyEffectChainToNative(EffectChain.Empty)
            NativeBridge.setForceMono(true)
            NativeBridge.setDspBassDepth(4)
            NativeBridge.setDspBassEnabled(true)
            assertTrue(NativeBridge.getForceMono())
            assertTrue(NativeBridge.getDspBassEnabled())
            assertEquals(4, NativeBridge.getDspBassDepth())
            val shimmedChain = EffectChain.fromJson(NativeBridge.getEffectChain())
            assertTrue(shimmedChain.master.mono)
            assertEquals(listOf(EffectNodeIds.OPENMPT_BASS), shimmedChain.nodes.map { it.id })

            NativeBridge.setOfflineRenderMode(true)
            NativeBridge.setMasterGain(0f)
            NativeBridge.setPluginGain(0f)
            NativeBridge.setSongGain(0f)
            NativeBridge.setEndFadeDurationMs(0)
            NativeBridge.setOutputLimiterEnabled(false)
            NativeBridge.setLookaheadClipperMode(0)

            val dry = renderChain(EffectChain.Empty)
            assertTrue((0 until 4096).any { f -> kotlin.math.abs(dry[f * 2] - dry[f * 2 + 1]) > 1e-3f })

            val surroundNode = EffectNode(
                id = EffectNodeIds.OPENMPT_SURROUND,
                params = mapOf("depth" to 14f, "delayMs" to 20f)
            )
            val preMonoOut = renderChain(
                EffectChain(input = NodeOptions(mono = true), nodes = listOf(surroundNode))
            )
            val postMonoOut = renderChain(
                EffectChain(master = NodeOptions(mono = true), nodes = listOf(surroundNode))
            )
            assertTrue(
                "input.mono before surround preserves surround stereo difference",
                (0 until 4096).any { f -> kotlin.math.abs(preMonoOut[f * 2] - preMonoOut[f * 2 + 1]) > 1e-3f }
            )
            for (f in 0 until 4096) {
                assertEquals(postMonoOut[f * 2], postMonoOut[f * 2 + 1], 0f)
            }

            val midMonoThenReverb = renderChain(
                EffectChain(
                    nodes = listOf(
                        surroundNode.copy(options = NodeOptions(mono = true)),
                        EffectNode(EffectNodeIds.OPENMPT_REVERB, params = mapOf("depth" to 12f, "preset" to 3f))
                    )
                )
            )
            val stereoSurroundThenReverb = renderChain(
                EffectChain(
                    nodes = listOf(
                        surroundNode,
                        EffectNode(EffectNodeIds.OPENMPT_REVERB, params = mapOf("depth" to 12f, "preset" to 3f))
                    )
                )
            )
            assertFalse(midMonoThenReverb.contentEquals(stereoSurroundThenReverb))

            val orderA = renderChain(
                EffectChain(
                    nodes = listOf(
                        EffectNode(EffectNodeIds.OPENMPT_BITCRUSH, params = mapOf("bits" to 4f)),
                        EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -6f)),
                        EffectNode(EffectNodeIds.OPENMPT_REVERB, params = mapOf("depth" to 12f, "preset" to 5f))
                    )
                )
            )
            val orderB = renderChain(
                EffectChain(
                    nodes = listOf(
                        EffectNode(EffectNodeIds.OPENMPT_REVERB, params = mapOf("depth" to 12f, "preset" to 5f)),
                        EffectNode(EffectNodeIds.GAIN, params = mapOf("gainDb" to -6f)),
                        EffectNode(EffectNodeIds.OPENMPT_BITCRUSH, params = mapOf("bits" to 4f))
                    )
                )
            )
            var orderDiffEnergy = 0.0
            for (i in orderA.indices) {
                val d = (orderA[i] - orderB[i]).toDouble()
                orderDiffEnergy += d * d
            }
            assertTrue("Node order must audibly change output (energy=$orderDiffEnergy)", orderDiffEnergy > 1e-2)
            assertNotEquals(orderA.toList(), orderB.toList())
        } finally {
            applyDspSettingsToNative(defaultDspSettings())
            NativeBridge.setForceMono(false)
            applyEffectChainToNative(EffectChain.Empty)
            NativeBridge.setLookaheadClipperMode(AppDefaults.AudioProcessing.lookaheadClipperMode.nativeValue)
            NativeBridge.setOfflineRenderMode(false)
            NativeBridge.releaseCurrentDecoder()
        }
    }

    @Test
    fun chainEditorMutationsAndScopeHelpersBehaveCorrectly() {
        val descriptors = queryEffectDescriptors()
        assertEquals(
            "OpenMPT Bass Expansion",
            descriptors.first { it.id == EffectNodeIds.OPENMPT_BASS }.displayName
        )
        assertEquals(
            "OpenMPT Surround",
            descriptors.first { it.id == EffectNodeIds.OPENMPT_SURROUND }.displayName
        )
        assertEquals(
            "OpenMPT Reverb",
            descriptors.first { it.id == EffectNodeIds.OPENMPT_REVERB }.displayName
        )
        assertEquals(
            "OpenMPT Bit Crush",
            descriptors.first { it.id == EffectNodeIds.OPENMPT_BITCRUSH }.displayName
        )
        assertEquals(
            "Gain",
            descriptors.first { it.id == EffectNodeIds.GAIN }.displayName
        )
        assertTrue(descriptors.any { it.id == EffectNodeIds.OPENMPT_BASS })
        assertTrue(descriptors.any { it.id == EffectNodeIds.OPENMPT_SURROUND })
        assertTrue(descriptors.any { it.id == EffectNodeIds.OPENMPT_REVERB })
        assertTrue(descriptors.any { it.id == EffectNodeIds.OPENMPT_BITCRUSH })
        assertTrue(descriptors.any { it.id == EffectNodeIds.GAIN })

        var chain = EffectChain.Empty
            .addNode(EffectNodeIds.OPENMPT_BASS, descriptors)
            .addNode(EffectNodeIds.GAIN, descriptors)
            .addNode(EffectNodeIds.OPENMPT_REVERB, descriptors)
        assertEquals(
            listOf(EffectNodeIds.OPENMPT_BASS, EffectNodeIds.GAIN, EffectNodeIds.OPENMPT_REVERB),
            chain.nodes.map { it.id }
        )

        val bassUid = chain.nodes[0].uid
        val gainUid = chain.nodes[1].uid
        val reverbUid = chain.nodes[2].uid
        assertNotEquals(bassUid, gainUid)
        assertNotEquals(gainUid, reverbUid)

        chain = chain.setNodeParamAt(0, "depth", 4f, descriptors)
            .setNodeMonoAt(1, true)
            .setNodeEnabledAt(2, false)
        assertEquals(4f, chain.nodes[0].params["depth"])
        assertTrue(chain.nodes[1].options.mono)
        assertFalse(chain.nodes[2].enabled)
        assertEquals(bassUid, chain.nodes[0].uid)
        assertEquals(gainUid, chain.nodes[1].uid)
        assertEquals(reverbUid, chain.nodes[2].uid)

        chain = chain.moveNode(2, 0)
        assertEquals(
            listOf(EffectNodeIds.OPENMPT_REVERB, EffectNodeIds.OPENMPT_BASS, EffectNodeIds.GAIN),
            chain.nodes.map { it.id }
        )
        assertEquals(reverbUid, chain.nodes[0].uid)

        chain = chain.removeNodeAt(1)
        assertEquals(listOf(EffectNodeIds.OPENMPT_REVERB, EffectNodeIds.GAIN), chain.nodes.map { it.id })

        val global = EffectChain.Empty.addNode(EffectNodeIds.OPENMPT_BASS, descriptors)
        val core = EffectChain.Empty.addNode(EffectNodeIds.OPENMPT_SURROUND, descriptors)
        val playlist = EffectChain.Empty.addNode(EffectNodeIds.OPENMPT_REVERB, descriptors)

        assertEquals(
            EffectChainScopeTarget.Playlist to playlist,
            resolveInheritedChainForScope(
                target = EffectChainScopeTarget.Song,
                global = global,
                core = core,
                playlist = playlist
            )
        )
        assertEquals(
            EffectChainScopeTarget.Playlist,
            higherPriorityOverrideScope(
                viewingScope = EffectChainScopeTarget.Core,
                core = core,
                playlist = playlist,
                song = null
            )
        )
        assertEquals(
            EffectChainScopeTarget.Playlist,
            resolveInitialDialogScope(
                savedScope = EffectChainScopeTarget.Global,
                hasActiveCore = true,
                hasActivePlaylist = true,
                hasActiveSong = true,
                coreChain = core,
                playlistChain = playlist,
                songChain = null
            )
        )

        val prefs = InMemoryAppPreferences()
        val presetA = EffectPreset(
            id = "preset_1",
            name = "Warm Bass",
            chain = global
        )
        val presetB = EffectPreset(
            id = "preset_2",
            name = "Surround Wide",
            chain = core
        )
        writeEffectPresets(prefs.edit(), listOf(presetA, presetB))
        assertEquals(listOf(presetA, presetB), readEffectPresets(prefs))

        writeGlobalEffectChain(prefs.edit(), global)
        assertEquals(global, resolveEffectiveEffectChainForPrefs(prefs))
        writeBypassAllEffects(prefs.edit(), true)
        assertTrue(readBypassAllEffects(prefs))
        assertEquals(EffectChain.Empty, resolveEffectiveEffectChainForPrefs(prefs))
    }
}
