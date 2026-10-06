package com.flopster101.siliconplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repeatable UI-hitch benchmarks. Every test reports frame timing
 * (p50/p90/p95 + janky frames) so optimizations must move a metric.
 * Fixtures (/sdcard/Music/jank) and the target build are installed by
 * tools/jank_bench.sh before these run. Steps assert their preconditions
 * and throw instead of measuring idle taps on missing nodes.
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
        measureBlock = {
            try {
                journey()
            } catch (e: Throwable) {
                try {
                    device.dumpWindowHierarchy("/sdcard/Download/jank_fail.xml")
                } catch (_: Exception) {
                }
                throw e
            }
        }
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
        requireRow("Files")
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
        playFirstFixture()
        repeat(3) {
            requireTrackSwitch("Next track")
            requirePreviousTrack()
        }
        device.waitForIdle()
    }

    @Test
    fun cycleVisualizations() = measureJank {
        cycleVisualizationsJourney()
    }

    @Test
    fun playerTransportToggles() = measureJank {
        startActivityAndWait()
        device.waitForIdle()
        playFirstFixture()
        // Tap whatever transport state is showing; transitions expose
        // both nodes briefly, so only tap on a stable exclusive state.
        var flips = 0
        val toggleDeadline = System.currentTimeMillis() + 60_000
        while (flips < 8) {
            val play = device.findObject(By.desc("Play"))
            val pause = device.findObject(By.desc("Pause"))
            if (play != null && pause == null) {
                try {
                    play.click()
                    flips += 1
                } catch (_: Exception) {
                }
            } else if (pause != null && play == null) {
                try {
                    pause.click()
                    flips += 1
                } catch (_: Exception) {
                }
            }
            if (System.currentTimeMillis() > toggleDeadline) {
                throw AssertionError("Transport toggles stalled after $flips flips")
            }
            Thread.sleep(400)
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.cycleVisualizationsJourney() {
        startActivityAndWait()
        device.waitForIdle()
        playFirstFixture()
        // Visualization defaults to Off, so cycle modes through the picker
        // sheet. The surface only materializes once the sheet is gone, so
        // every selection closes the sheet; presence/absence of the surface
        // proves each tap took effect.
        // The sheet only lists modes enabled in settings, so discover
        // them instead of assuming the full set.
        openVisPicker()
        val modes = VIS_MODE_CANDIDATES.filter { device.findObject(By.text(it)) != null }
        if (!modes.contains("Bars") || modes.size < 3) {
            throw AssertionError("Too few visualization modes offered: $modes")
        }
        device.pressBack()
        device.waitForIdle()
        for (mode in modes + modes.reversed() + "Off") {
            openVisPicker()
            requireRow(mode)
            device.pressBack()
            device.waitForIdle()
            if (mode == "Off") {
                if (!device.wait(Until.gone(By.clazz("android.view.SurfaceView")), 8_000)) {
                    throw AssertionError("Visualization surface persisted after Off")
                }
            } else {
                device.wait(Until.findObject(By.clazz("android.view.SurfaceView")), 8_000)
                    ?: throw AssertionError("Visualization surface missing after selecting $mode")
            }
        }
    }

    private fun MacrobenchmarkScope.openVisPicker() {
        requireDesc("More options")
        Thread.sleep(500)
        requireRow("Visualizations")
        // Taps during the sheet-enter animation are swallowed.
        Thread.sleep(750)
    }

    private fun MacrobenchmarkScope.grantMediaPermissions() {
        for (permission in PERMISSIONS) {
            try {
                device.executeShellCommand("pm grant $PACKAGE $permission")
            } catch (_: Exception) {
            }
        }
        try {
            device.executeShellCommand("appops set $PACKAGE MANAGE_EXTERNAL_STORAGE allow")
        } catch (_: Exception) {
        }
        // A system permission dialog over the app eats taps and pollutes
        // frames; deny it outright if it is showing.
        try {
            device.findObject(By.text("DON’T ALLOW"))?.click()
            device.waitForIdle()
        } catch (_: Exception) {
        }
        // projectM's first-run preset prompt is modal; never let it linger.
        try {
            device.findObject(By.text("Not now"))?.click()
            device.waitForIdle()
        } catch (_: Exception) {
        }
    }

    private fun MacrobenchmarkScope.requireDesc(label: String) {
        // Transport state toggles asynchronously; the node may exist before
        // it accepts clicks, so retry briefly instead of failing at once.
        val deadline = System.currentTimeMillis() + 5_000
        var lastError: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val node = device.findObject(By.desc(label))
                    ?: throw AssertionError("Required node missing: desc='$label'")
                node.click()
                device.waitForIdle()
                return
            } catch (e: AssertionError) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(250)
            }
        }
        throw AssertionError("Required node not clickable: desc='$label'", lastError)
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

    private fun MacrobenchmarkScope.requireRow(label: String) {
        // Row labels are non-clickable text; tap the label center, exactly
        // like the finger taps that were verified manually.
        val text = device.wait(Until.findObject(By.text(label)), 5_000)
            ?: throw AssertionError("Required row missing: text='$label'")
        val bounds = text.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.flingScrollable(direction: Direction) {
        try {
            device.findObject(By.scrollable(true))?.fling(direction)
        } catch (_: Exception) {
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.playFirstFixture() {
        // The browser remembers its last directory across restarts, so only
        // drill down through levels that are actually on screen.
        if (device.findObject(By.text("File Browser")) == null) {
            requireRow("Files")
            device.wait(Until.findObject(By.text("File Browser")), 5_000)
                ?: throw AssertionError("File browser did not open")
            // Let the a11y tree settle; pre-navigation nodes linger as ghosts.
            Thread.sleep(750)
        }
        if (device.findObject(By.text("Internal storage")) != null) requireRow("Internal storage")
        if (device.findObject(By.text("Music")) != null &&
            device.findObject(By.textEndsWith(".mp3")) == null
        ) requireRow("Music")
        if (device.findObject(By.text("jank")) != null &&
            device.findObject(By.textEndsWith(".mp3")) == null
        ) requireRow("jank")
        openFirstAudioFile()
        // Player screen is up when its minimize affordance exists.
        device.wait(Until.findObject(By.desc("Minimize player")), 8_000)
            ?: throw AssertionError("Player did not open after tapping audio file")
    }

    private fun MacrobenchmarkScope.openFirstAudioFile() {
        // Taps the middle fixture so Next/Previous always have a sibling.
        var rows: List<UiObject2> = emptyList()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            rows = device.findObjects(By.textEndsWith(".mp3"))
            if (rows.size >= 2) break
            Thread.sleep(250)
        }
        val row = rows.getOrNull(rows.size / 2)
            ?: device.findObject(By.desc("Audio file"))
            ?: throw AssertionError("No audio rows in fixture folder")
        try {
            row.click()
        } catch (e: Exception) {
            throw AssertionError("Audio row not clickable", e)
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.playerSignature(): Set<String> {
        return device.findObjects(By.clazz("android.widget.TextView"))
            .mapNotNull { try { it.text } catch (_: Exception) { null } }
            .filter { it.isNotBlank() && !TIME_LIKE.matcher(it).matches() }
            .toSet()
    }

    private fun MacrobenchmarkScope.requirePreviousTrack() {
        // Previous restarts the current track past a playback-position
        // threshold instead of stepping back, so a tap that only restarts
        // just re-arms the next tap (now near zero) for the real switch.
        repeat(2) {
            val before = playerSignature()
            requireDesc("Previous track")
            val switchDeadline = System.currentTimeMillis() + 5_000
            do {
                Thread.sleep(250)
                if (playerSignature() != before) return
            } while (System.currentTimeMillis() < switchDeadline)
        }
        throw AssertionError("Track did not change after 'Previous track'")
    }

    private fun MacrobenchmarkScope.requireTrackSwitch(action: String) {
        val before = playerSignature()
        requireDesc(action)
        // The switch is async; the title must actually change.
        val switchDeadline = System.currentTimeMillis() + 5_000
        do {
            Thread.sleep(250)
            if (playerSignature() != before) return
        } while (System.currentTimeMillis() < switchDeadline)
        throw AssertionError("Track did not change after '$action'")
    }

    private companion object {
        val VIS_MODE_CANDIDATES = listOf("Bars", "Oscilloscope", "VU meters", "Channel scope", "Starfield")
        val TIME_LIKE = java.util.regex.Pattern.compile("\\d+:\\d+.*")
        const val PACKAGE = "com.flopster101.siliconplayer"
        val PERMISSIONS = listOf(
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS"
        )
    }
}
