package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * UADE also claims .ftm (Amiga FaceTheMusic), so a FamiTracker module fed
 * to it grinds inside the probe instead of failing. Stopping then must
 * return promptly and later loads must proceed: the stuck probe parks
 * only its own loader thread and never the engine.
 */
class DesktopUadeStuckProbeTest {
    private val ftm = File("/home/flopster101/Music/SyncedMusic/Chips/FamiCommodore.ftm")
    private val localFile = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")

    @Test
    fun stopDuringUadeProbeReturnsAndEngineStaysUsable() {
        Assume.assumeTrue("FTM fixture is available", ftm.exists())
        Assume.assumeTrue("Music fixture is available", localFile.exists())
        useSilentTestAudioOutput()
        val session = DesktopPlaybackSession()
        try {
            val loaderDone = CountDownLatch(1)
            Thread {
                try {
                    NativeBridge.loadAudioWithDecoder(ftm.absolutePath, "UADE")
                } catch (_: Throwable) {
                } finally {
                    loaderDone.countDown()
                }
            }.also { it.isDaemon = true; it.start() }

            // Give the probe a chance to grind; whether it sticks or lands,
            // stopping now must be prompt and must not wedge later loads.
            Thread.sleep(5_000)
            val t0 = System.currentTimeMillis()
            session.stop()
            val stopMs = System.currentTimeMillis() - t0
            assertTrue("Stop during the UADE probe returns promptly (ms=$stopMs)",
                stopMs < 12_000)

            val t1 = System.currentTimeMillis()
            val loaded = session.loadFile(localFile, autoStart = false)
            val loadMs = System.currentTimeMillis() - t1
            assertTrue("Follow-up load completes after stop", loaded)
            assertTrue("Follow-up load is not stuck behind the probe (ms=$loadMs)",
                loadMs < 15_000)
            assertTrue(
                "Local track is installed, not the probed URL",
                NativeBridge.getTrackTitle().isNotBlank()
            )
            loaderDone.await(5, TimeUnit.SECONDS)
        } finally {
            try { session.stop() } catch (_: Throwable) {}
        }
    }
}
