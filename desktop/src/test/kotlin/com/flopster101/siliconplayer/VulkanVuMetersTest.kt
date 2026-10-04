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
}
