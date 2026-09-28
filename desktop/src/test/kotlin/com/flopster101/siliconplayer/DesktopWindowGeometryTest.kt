package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopWindowGeometry
import com.flopster101.siliconplayer.desktop.DesktopWindowStateFileName
import com.flopster101.siliconplayer.desktop.loadDesktopWindowGeometry
import com.flopster101.siliconplayer.desktop.saveDesktopWindowGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DesktopWindowGeometryTest {

    @Test
    fun roundTripPreservesSizeAndPosition() {
        val dir = createTempDir("window-geometry-test")
        try {
            saveDesktopWindowGeometry(
                dir,
                DesktopWindowGeometry(widthDp = 1280f, heightDp = 800f, xDp = 64f, yDp = 32f)
            )
            val loaded = loadDesktopWindowGeometry(dir)!!
            assertEquals(1280f, loaded.widthDp)
            assertEquals(800f, loaded.heightDp)
            assertEquals(64f, loaded.xDp)
            assertEquals(32f, loaded.yDp)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingFileLoadsNull() {
        val dir = createTempDir("window-geometry-test")
        try {
            assertNull(loadDesktopWindowGeometry(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptMainFallsBackToBackup() {
        val dir = createTempDir("window-geometry-test")
        try {
            File(dir, DesktopWindowStateFileName).writeText("{not json")
            File(dir, "$DesktopWindowStateFileName.bak").writeText(
                """{"widthDp":900.0,"heightDp":600.0}"""
            )
            val loaded = loadDesktopWindowGeometry(dir)!!
            assertEquals(900f, loaded.widthDp)
            assertEquals(600f, loaded.heightDp)
            assertNull(loaded.xDp)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun outOfRangeSizesAreClamped() {
        val dir = createTempDir("window-geometry-test")
        try {
            File(dir, DesktopWindowStateFileName).writeText(
                """{"widthDp":40.0,"heightDp":20000.0,"xDp":10.0,"yDp":20.0}"""
            )
            val loaded = loadDesktopWindowGeometry(dir)!!
            assertEquals(480f, loaded.widthDp)
            assertEquals(4320f, loaded.heightDp)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun absentPositionRestoresPlatformDefault() {
        val dir = createTempDir("window-geometry-test")
        try {
            saveDesktopWindowGeometry(
                dir,
                DesktopWindowGeometry(widthDp = 1100f, heightDp = 750f, xDp = null, yDp = null)
            )
            val loaded = loadDesktopWindowGeometry(dir)!!
            assertNull(loaded.xDp)
            assertNull(loaded.yDp)
        } finally {
            dir.deleteRecursively()
        }
    }
}
