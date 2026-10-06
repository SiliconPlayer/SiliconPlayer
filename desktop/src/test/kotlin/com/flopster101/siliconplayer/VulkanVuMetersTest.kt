package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VulkanVuMetersTest {

    @Test
    fun testVulkanVuMetersOffscreenRenderAndReadback() {
        if (!SiliconVisNativeBridge.nativeVulkanIsSupported()) {
            println("Vulkan not supported on this platform, skipping.")
            return
        }

        val handle = SiliconVisNativeBridge.nativeCreate()
        assertTrue("Expected non-zero visualizer handle", handle != 0L)

        val width = 320
        val height = 180
        val initOk = SiliconVisNativeBridge.nativeInitVulkan(handle, width, height, null)
        assertTrue("Vulkan pipeline initialization failed", initOk)

        SiliconVisNativeBridge.nativeSetMode(handle, 3)
        SiliconVisNativeBridge.nativeSetVuMetersOptions(
            handle = handle,
            stereo = false,
            anchor = 1,
            smoothing = 0f,
            fillColorArgb = -1,
            trackColorArgb = 0x40FFFFFF,
            labelColorArgb = -1
        )

        // Full-scale mono level: the fill must cover most of the centered row.
        SiliconVisNativeBridge.nativeSetVuLevels(handle, 1f, 1f)
        repeat(3) {
            SiliconVisNativeBridge.nativeRenderVulkan(handle)
        }

        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback from Vulkan offscreen frame", readbackOk)

        var midRowNonZero = 0
        for (x in width / 4 until 3 * width / 4) {
            val base = ((height / 2) * width + x) * 4
            if (readbackBuffer.get(base) != 0.toByte() ||
                readbackBuffer.get(base + 1) != 0.toByte() ||
                readbackBuffer.get(base + 2) != 0.toByte()
            ) {
                midRowNonZero++
            }
        }
        println("Vulkan VU frame rendered: $midRowNonZero non-black pixels on the mid row")
        assertTrue("Expected meter fill across the mid row", midRowNonZero > width / 4)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }

    @Test
    fun testVulkanVuMetersResizeAppliesDensity() {
        if (!SiliconVisNativeBridge.nativeVulkanIsSupported()) {
            println("Vulkan not supported on this platform, skipping.")
            return
        }

        val handle = SiliconVisNativeBridge.nativeCreate()
        assertTrue("Expected non-zero visualizer handle", handle != 0L)

        val width = 320
        val height = 180
        val initOk = SiliconVisNativeBridge.nativeInitVulkan(handle, width, height, null)
        assertTrue("Vulkan pipeline initialization failed", initOk)

        // Init only knows the default 1x density; the resize that follows
        // must reach the renderer or the meters keep their 1x geometry.
        SiliconVisNativeBridge.nativeResizeVulkan(handle, width, height, 3.0f)
        SiliconVisNativeBridge.nativeSetMode(handle, 3)
        SiliconVisNativeBridge.nativeSetVuMetersOptions(
            handle = handle,
            stereo = false,
            anchor = 0,
            smoothing = 0f,
            fillColorArgb = 0xFF00FF00.toInt(),
            trackColorArgb = 0x40FFFFFF,
            labelColorArgb = -1
        )

        // Full-scale mono level on a top-anchored row, filled pure green
        // so the count ignores whatever background sits behind the meters.
        SiliconVisNativeBridge.nativeSetVuLevels(handle, 1f, 1f)
        repeat(3) {
            SiliconVisNativeBridge.nativeRenderVulkan(handle)
        }

        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback from Vulkan offscreen frame", readbackOk)

        // At 3x density the row is 42 px tall; at the stale 1x default
        // it would be 14 px. Column height survives readback orientation.
        var bandPx = 0
        val x = 240
        for (y in 0 until height) {
            val base = (y * width + x) * 4
            if (readbackBuffer.get(base) == 0.toByte() &&
                readbackBuffer.get(base + 1) != 0.toByte() &&
                readbackBuffer.get(base + 2) == 0.toByte()
            ) {
                bandPx++
            }
        }
        println("Vulkan VU density band: $bandPx px")
        assertTrue("Expected 3x-density row height, got $bandPx px", bandPx >= 30)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }
}
