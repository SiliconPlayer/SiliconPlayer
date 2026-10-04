package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VulkanBarsTest {

    @Test
    fun testVulkanBarsOffscreenRenderAndReadback() {
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

        SiliconVisNativeBridge.nativeSetMode(handle, 1)
        SiliconVisNativeBridge.nativeSetBarsOptions(
            handle = handle,
            barCount = 32,
            smoothing = 0f,
            startColorArgb = -1,
            endColorArgb = -1,
            cornerRadiusPx = 4f,
            showFrequencyGuide = true,
            guideColorArgb = 0x40FFFFFF
        )

        // Strong spectrum: bars must reach the top half of the frame.
        val fft = FloatArray(128) { 0.9f }
        SiliconVisNativeBridge.nativePushFft(handle, fft, fft.size)
        repeat(3) {
            SiliconVisNativeBridge.nativeRenderVulkan(handle)
        }

        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback from Vulkan offscreen frame", readbackOk)

        var topHalfNonZero = 0
        for (y in 0 until height / 2) {
            for (x in 0 until width) {
                val base = (y * width + x) * 4
                if (readbackBuffer.get(base) != 0.toByte() ||
                    readbackBuffer.get(base + 1) != 0.toByte() ||
                    readbackBuffer.get(base + 2) != 0.toByte()
                ) {
                    topHalfNonZero++
                }
            }
        }
        println("Vulkan Bars frame rendered: $topHalfNonZero non-black pixels in top half")
        assertTrue("Expected tall bars reaching the top half", topHalfNonZero > width * 4)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }
}
