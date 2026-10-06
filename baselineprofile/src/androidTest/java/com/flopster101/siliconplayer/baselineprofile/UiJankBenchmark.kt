package com.flopster101.siliconplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repeatable UI-hitch benchmarks. Every test reports frame timing
 * (p50/p90/p95 + janky frames) so optimizations must move a metric.
 * Fixtures (/sdcard/Music) and the target build are installed by
 * tools/jank_bench.sh before these run; selectors degrade gracefully
 * so a missing node skips its step instead of failing the run.
 */
@RunWith(AndroidJUnit4::class)
class UiJankBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private fun measureJank(
        startupMode: StartupMode = StartupMode.WARM,
        iterations: Int = 5,
        journey: MacrobenchmarkScope.() -> Unit
    ) = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = startupMode,
        iterations = iterations,
        setupBlock = {
            pressHome()
            grantMediaPermissions()
        },
        measureBlock = journey
    )

    @Test
    fun coldStartToHome() = measureJank(
        startupMode = StartupMode.COLD,
        iterations = 5
    ) {
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun scrollFileBrowser() = measureJank {
        startActivityAndWait()
        device.waitForIdle()
        tapTextOrDesc("Files")
        device.waitForIdle()
        repeat(3) {
            flingScrollable(Direction.DOWN)
            flingScrollable(Direction.UP)
        }
    }

    @Test
    fun switchTracks() = measureJank {
        startActivityAndWait()
        device.waitForIdle()
        tapTextOrDesc("Files")
        device.waitForIdle()
        openFirstAudioFile()
        repeat(6) {
            tapDesc("Next track")
        }
        device.waitForIdle()
    }

    @Test
    fun cycleVisualizations() = measureJank {
        startActivityAndWait()
        device.waitForIdle()
        openPlayer()
        repeat(8) {
            tapDesc("Next visualization")
        }
        device.waitForIdle()
    }

    @Test
    fun playerTransportToggles() = measureJank {
        startActivityAndWait()
        device.waitForIdle()
        openPlayer()
        repeat(4) {
            tapDesc("Next track")
            tapDesc("Previous")
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.grantMediaPermissions() {
        for (permission in PERMISSIONS) {
            try {
                device.executeShellCommand("pm grant $PACKAGE $permission")
            } catch (_: Exception) {
            }
        }
        // Keep the screen on so long journeys are never interrupted.
        try {
            device.executeShellCommand("svc power stayon true")
        } catch (_: Exception) {
        }
    }

    private fun MacrobenchmarkScope.tapTextOrDesc(label: String): Boolean {
        val node = device.wait(Until.findObject(By.text(label)), 2_000)
            ?: device.findObject(By.desc(label))
            ?: return false
        return try {
            node.click()
            device.waitForIdle()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun MacrobenchmarkScope.tapDesc(label: String): Boolean {
        val node = device.wait(Until.findObject(By.desc(label)), 2_000) ?: return false
        return try {
            node.click()
            device.waitForIdle()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun MacrobenchmarkScope.flingScrollable(direction: Direction) {
        try {
            device.findObject(By.scrollable(true))?.fling(direction)
        } catch (_: Exception) {
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openPlayer() {
        // Prefer the mini-player artwork, fall back to any Play affordance.
        if (tapDesc("Mini player artwork")) return
        tapTextOrDesc("Play")
    }

    private fun MacrobenchmarkScope.openFirstAudioFile() {
        // Assumes the harness pushed fixtures under /sdcard/Music and the
        // browser lists it. Falls back to the first audio-looking row.
        tapTextOrDesc("Music")
        val audio = device.wait(
            Until.findObject(By.textEndsWith(".mp3")),
            3_000
        ) ?: device.findObject(By.textEndsWith(".flac"))
            ?: device.findObject(By.desc("Audio file"))
            ?: return
        try {
            audio.click()
        } catch (_: Exception) {
        }
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.flopster101.siliconplayer"
        val PERMISSIONS = listOf(
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE"
        )
    }
}
