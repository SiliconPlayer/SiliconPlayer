package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VulkanStarfieldTest {

    @Test
    fun testVulkanStarfieldOffscreenRenderAndReadback() {
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

        SiliconVisNativeBridge.nativeSetMode(handle, 5)
        SiliconVisNativeBridge.nativeSetStarfieldOptions(
            handle = handle,
            starCount = 256,
            speed = 0.3f,
            fov = 1.0f,
            nearPlane = 0.06f,
            starColorArgb = -1,
            baseSizePx = 2.4f,
            sizeGrowth = 1.2f,
            farDim = 0.6f,
            softness = 0.25f,
            beatGlow = 0.0f,
            glowSize = 3.0f,
            trailPersistence = 0.55f,
            streaks = false,
            streakLength = 1.0f,
            centerX = 0f,
            centerY = 0f,
            autoDrift = false,
            beatFollow = false,
            reactSpeed = 0.0f,
            flash = 0.0f,
            squareStars = false
        )

        // Several frames: trails accumulate across frames through the
        // LOAD render pass, persistence included.
        repeat(4) {
            SiliconVisNativeBridge.nativeRenderVulkan(handle)
        }

        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback from Vulkan offscreen frame", readbackOk)

        var nonZeroBytes = 0
        readbackBuffer.rewind()
        for (i in 0 until bufferSize) {
            if (readbackBuffer.get(i) != 0.toByte()) {
                nonZeroBytes++
            }
        }
        println("Vulkan Starfield frame rendered: $nonZeroBytes non-zero bytes out of $bufferSize")
        assertTrue("Expected rendered stars in framebuffer", nonZeroBytes > 0)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }
}
