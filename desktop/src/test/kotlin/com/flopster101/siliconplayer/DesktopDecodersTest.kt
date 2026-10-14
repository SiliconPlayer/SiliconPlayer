package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge

class DesktopDecodersTest {

    private val allExpectedDecoders = setOf(
        "FFmpeg",
        "libopenmpt",
        "libxmp",
        "uFMOD-C",
        "ayfly",
        "VGMPlay",
        "Game Music Emu",
        "cRSID",
        "libsidplayfp",
        "lazyusf2",
        "vio2sf",
        "libupse",
        "viogsf",
        "NEZplug++",
        "SC68",
        "AdPlug",
        "UADE",
        "HivelyTracker",
        "klystrack-plus",
        "Furnace",
        "libdnfamitracker"
    )

    @Test
    fun testAllDecodersRegistered() {
        val registered = NativeBridge.getRegisteredDecoderNames().toSet()
        println("Registered decoders (${registered.size}): $registered")
        for (expected in allExpectedDecoders) {
            assertTrue("Expected decoder $expected to be registered", registered.contains(expected))
        }
        assertEquals(allExpectedDecoders.size, registered.size)
    }

    private fun testSingleDecoder(decoderName: String, testFilePath: String) {
        useSilentTestAudioOutput()
        val file = File(testFilePath)
        assertTrue("Test file must exist: $testFilePath", file.exists())

        try {
            NativeBridge.loadAudioWithDecoder(file.absolutePath, decoderName)
            val currentDecoder = NativeBridge.getCurrentDecoderName()
            assertEquals("Expected active decoder $decoderName", decoderName, currentDecoder)
            val duration = NativeBridge.getDuration()
            println("[$decoderName] successfully loaded ${file.name}, duration: $duration s")
            assertTrue("Duration should be finite and >= 0.0", !duration.isNaN() && duration >= 0.0)

            NativeBridge.startEngineNative()
            val deadline = System.currentTimeMillis() + 2000
            while (!NativeBridge.isEnginePlaying() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            assertTrue("Engine should report playing", NativeBridge.isEnginePlaying())
            NativeBridge.stopEngineNative()
        } finally {
            NativeBridge.releaseCurrentDecoder()
        }
    }

    @Test
    fun testFFmpegDecoder() {
        testSingleDecoder(
            "FFmpeg",
            "/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3"
        )
    }

    @Test
    fun testLibOpenMptDecoder() {
        testSingleDecoder(
            "libopenmpt",
            "/home/flopster101/Music/SyncedMusic/TrackerMusic/FORMAT-Modtracker/spin_me_round.xm"
        )
    }

    @Test
    fun testLibXmpDecoder() {
        testSingleDecoder(
            "libxmp",
            "/home/flopster101/Music/SyncedMusic/TrackerMusic/FORMAT-Modtracker/spin_me_round.xm"
        )
    }

    @Test
    fun testUfmodDecoder() {
        testSingleDecoder(
            "uFMOD-C",
            "/home/flopster101/Music/SyncedMusic/TrackerMusic/FORMAT-Modtracker/spin_me_round.xm"
        )
    }

    @Test
    fun testAyflyDecoder() {
        testSingleDecoder(
            "ayfly",
            "/home/flopster101/Music/SyncedMusic/Chips/VGM/ZXSPECTRUM/Ben Daglish - Dark Fusion - Title (Beeper) (1988).ay"
        )
    }

    @Test
    fun testVgmDecoder() {
        testSingleDecoder(
            "VGMPlay",
            "/home/flopster101/Music/SyncedMusic/Chips/VGM/Megadrive/Sonic the Hedgehog (EMU).zophar/02 - Green Hill Zone.vgm"
        )
    }

    @Test
    fun testGameMusicEmuDecoder() {
        testSingleDecoder(
            "Game Music Emu",
            "/home/flopster101/Music/SyncedMusic/MIDIs/OldMIDIs/smb3-world6.spc"
        )
    }

    @Test
    fun testCrsidDecoder() {
        testSingleDecoder(
            "cRSID",
            "/home/flopster101/Music/SyncedMusic/Chips/C64/Mathematica_tune_1_8580.sid"
        )
    }

    @Test
    fun testLibSidPlayFpDecoder() {
        testSingleDecoder(
            "libsidplayfp",
            "/home/flopster101/Music/SyncedMusic/Chips/C64/Mathematica_tune_1_8580.sid"
        )
    }

    @Test
    fun testLazyUsf2Decoder() {
        testSingleDecoder(
            "lazyusf2",
            "/home/flopster101/Music/SyncedMusic/Chips/VGM/N64/Diddy Kong Racing (EMU).zophar/35 Gets Balloon from Genie.miniusf"
        )
    }

    @Test
    fun testVio2sfDecoder() {
        testSingleDecoder(
            "vio2sf",
            "/home/flopster101/Music/SyncedMusic/Chips/VGM/DS/Kirby Super Star Ultra (EMU).zophar/050 Vs. Meta Knight.mini2sf"
        )
    }

    @Test
    fun testSc68Decoder() {
        testSingleDecoder(
            "SC68",
            "/home/flopster101/Music/SyncedMusic/Chips/DSE Examples/Atari/Chaos.sndh"
        )
    }

    @Test
    fun testAdPlugDecoder() {
        testSingleDecoder(
            "AdPlug",
            "/home/flopster101/Music/SyncedMusic/Chips/DSE Examples/Other/Siren - Tyrian Asteroid Dance Part 1.lds"
        )
    }

    @Test
    fun testAdPlugChannelScope() {
        useSilentTestAudioOutput()
        val visHandle = SiliconVisNativeBridge.nativeCreate()
        assertTrue("Expected non-zero visHandle", visHandle != 0L)
        NativeBridge.attachAudioEngineToVisualizer(visHandle)

        val testFiles = listOf(
            "/home/flopster101/Music/SyncedMusic/Chips/VGM/PC/LIGHT5.A2M"
        )
        for (filePath in testFiles) {
            val file = File(filePath)
            if (!file.exists()) continue
            for (repeatMode in listOf(0)) {
                for (engine in listOf(0, 2)) {
                    println("=== Testing file: ${file.name} with engine $engine, repeatMode $repeatMode ===")
                    try {
                        NativeBridge.setCoreOption("AdPlug", "adplug.opl_engine", engine.toString())
                        NativeBridge.setRepeatMode(repeatMode)
                        NativeBridge.loadAudioWithDecoder(file.absolutePath, "AdPlug")
                        val duration = NativeBridge.getDuration()
                        println("DURATION: $duration for ${file.name}")
                        val vulkanSupported = SiliconVisNativeBridge.nativeVulkanIsSupported()
                        if (vulkanSupported) {
                            SiliconVisNativeBridge.nativeInitVulkan(visHandle, 320, 180, null)
                        }

                        // Step 1: Start playing MINIMIZED (Channel Scope inactive)
                        NativeBridge.setChannelScopeVisualizerActive(false)
                        NativeBridge.setCoreOption("AdPlug", "visualization.channel_scope_active", "false")
                        SiliconVisNativeBridge.nativeSetMode(visHandle, 0)
                        NativeBridge.startEngineNative()

                        println("--- Phase 1: Minimized (500ms) ---")
                        var t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 500) {
                            Thread.sleep(30)
                            val vu = NativeBridge.getVisualizationVuLevels().toList()
                            val pos = NativeBridge.getPosition()
                            val isPlaying = NativeBridge.isEnginePlaying()
                            println("MINIMIZED: POS: $pos, vu: $vu, isPlaying: $isPlaying")
                        }

                        // Step 2: EXPAND Player (Channel Scope active)
                        println("--- Phase 2: Expanded with Channel Scope (1500ms) ---")
                        NativeBridge.setCoreOption("AdPlug", "visualization.channel_scope_active", "true")
                        NativeBridge.setChannelScopeVisualizerActive(true)
                        SiliconVisNativeBridge.nativeSetMode(visHandle, 4)
                        val names = NativeBridge.getDecoderToggleChannelNames()
                        println("TOGGLE CHANNELS: ${names.toList()}")

                        t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 8000) {
                            Thread.sleep(30)
                            if (vulkanSupported) {
                                SiliconVisNativeBridge.nativeRenderVulkan(visHandle)
                            }
                            val textState = NativeBridge.getChannelScopeTextState(64)
                            val scopeSamples = NativeBridge.getChannelScopeSamples(64)
                            val scopeMax = if (scopeSamples.isNotEmpty()) scopeSamples.maxOrNull() ?: 0f else 0f
                            val vu = NativeBridge.getVisualizationVuLevels().toList()
                            val pos = NativeBridge.getPosition()
                            val isPlaying = NativeBridge.isEnginePlaying()
                            println("EXPANDED: POS: $pos, vu: $vu, scopeMax: $scopeMax, isPlaying: $isPlaying")
                        }

                        // Step 3: MINIMIZE again
                        println("--- Phase 3: Minimized again (500ms) ---")
                        NativeBridge.setChannelScopeVisualizerActive(false)
                        NativeBridge.setCoreOption("AdPlug", "visualization.channel_scope_active", "false")
                        SiliconVisNativeBridge.nativeSetMode(visHandle, 0)

                        t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 500) {
                            Thread.sleep(30)
                            val vu = NativeBridge.getVisualizationVuLevels().toList()
                            val pos = NativeBridge.getPosition()
                            val isPlaying = NativeBridge.isEnginePlaying()
                            println("MINIMIZED_AGAIN: POS: $pos, vu: $vu, isPlaying: $isPlaying")
                        }

                        assertTrue("Engine should still be playing for ${file.name}", NativeBridge.isEnginePlaying())
                        NativeBridge.stopEngineNative()
                    } finally {
                        NativeBridge.setChannelScopeVisualizerActive(false)
                        NativeBridge.releaseCurrentDecoder()
                    }
                }
            }
        }
        if (SiliconVisNativeBridge.nativeVulkanIsSupported()) {
            try { SiliconVisNativeBridge.nativeReleaseVulkan(visHandle) } catch (_: Throwable) {}
        }
        SiliconVisNativeBridge.nativeDestroy(visHandle)
    }

    @Test
    fun testUadeDecoder() {
        testSingleDecoder(
            "UADE",
            "/home/flopster101/Music/SyncedMusic/Chips/DSE Examples/Amiga/David Whittaker - Speedball.DW"
        )
    }

    @Test
    fun testHivelyTrackerDecoder() {
        testSingleDecoder(
            "HivelyTracker",
            "/home/flopster101/Music/SyncedMusic/TrackerMusic/FORMAT-Modtracker/DSE_Amiga/Monk - Galactic Emeralds.hvl"
        )
    }

    @Test
    fun testKlystrackDecoder() {
        testSingleDecoder(
            "klystrack-plus",
            "/home/flopster101/Music/SyncedMusic/TrackerMusic/FORMAT-Klystrack/smp-dpintro.kt"
        )
    }

    @Test
    fun testFurnaceDecoder() {
        testSingleDecoder(
            "Furnace",
            "/home/flopster101/Music/SyncedMusic/Mine/Furnace/A642_test.fur"
        )
    }

    @Test
    fun testDnfamitrackerDecoder() {
        val file = File("/home/flopster101/Music/SyncedMusic/Mine/Famistuff/240 bits_v2.dnm")
        assertTrue("Test file must exist: ${file.absolutePath}", file.exists())
        useSilentTestAudioOutput()
        NativeBridge.loadAudioWithDecoder(file.absolutePath, "libdnfamitracker")
        val currentDecoder = NativeBridge.getCurrentDecoderName()
        assertEquals("Expected active decoder libdnfamitracker", "libdnfamitracker", currentDecoder)
        NativeBridge.startEngineNative()
        Thread.sleep(4000)
        NativeBridge.getVisualizationWaveformScope(-1, 50, 0)
        Thread.sleep(300)
        val waveform = NativeBridge.getVisualizationWaveformScope(-1, 50, 0)
        println("Waveform size: ${waveform.size}")
        for (i in 0 until minOf(20, waveform.size)) {
            println("sample[$i] = ${waveform[i]}")
        }
        var minVal = 1e9f
        var maxVal = -1e9f
        var jumps = 0
        for (i in waveform.indices) {
            minVal = minOf(minVal, waveform[i])
            maxVal = maxOf(maxVal, waveform[i])
            if (i > 0 && Math.abs(waveform[i] - waveform[i-1]) > 0.5f) {
                jumps++
            }
        }
        println("Scope stats: min=$minVal, max=$maxVal, jumps=$jumps")
        NativeBridge.stopEngineNative()
        NativeBridge.releaseCurrentDecoder()
    }
}
