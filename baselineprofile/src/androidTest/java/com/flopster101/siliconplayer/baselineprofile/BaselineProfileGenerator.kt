package com.flopster101.siliconplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.flopster101.siliconplayer"
    ) {
        // Journey: cold start -> home -> file browser -> player -> back.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Scroll the home screen (recents + quick rows). Re-find the scrollable
        // each time because Compose rebuilds nodes and stale references throw.
        flingScrollable(Direction.DOWN)
        flingScrollable(Direction.DOWN)
        flingScrollable(Direction.UP)

        // Open the file browser tab and scroll a directory.
        val filesTab = device.wait(Until.findObject(By.text("Files")), 5_000)
            ?: device.findObject(By.desc("Files"))
        filesTab?.click()
        device.waitForIdle()
        flingScrollable(Direction.DOWN)
        flingScrollable(Direction.UP)

        // Back to home and open the player (mini player "Play" affordance).
        device.pressBack()
        device.waitForIdle()
        device.findObject(By.desc("Play"))?.click()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.flingScrollable(direction: Direction) {
        try {
            device.findObject(By.scrollable(true))?.fling(direction)
        } catch (_: Exception) {
            // Ignore stale/absent nodes; profile generation should continue.
        }
        device.waitForIdle()
    }
}
