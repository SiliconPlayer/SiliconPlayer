package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopDecodersTest {

    private val allExpectedDecoders = setOf(
        "FFmpeg",
        "LibOpenMPT",
        "libxmp",
        "uFMOD",
        "ayfly",
        "VGMPlay",
        "Game Music Emu",
        "cRSID",
        "LibSIDPlayFP",
        "LazyUSF2",
        "Vio2SF",
        "SC68",
        "AdPlug",
        "UADE",
        "HivelyTracker",
        "Klystrack-plus",
        "Furnace"
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
            "LibOpenMPT",
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
            "uFMOD",
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
            "LibSIDPlayFP",
            "/home/flopster101/Music/SyncedMusic/Chips/C64/Mathematica_tune_1_8580.sid"
        )
    }

    @Test
    fun testLazyUsf2Decoder() {
        testSingleDecoder(
            "LazyUSF2",
            "/home/flopster101/Music/SyncedMusic/Chips/VGM/N64/Diddy Kong Racing (EMU).zophar/35 Gets Balloon from Genie.miniusf"
        )
    }

    @Test
    fun testVio2sfDecoder() {
        testSingleDecoder(
            "Vio2SF",
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
            "Klystrack-plus",
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
}
