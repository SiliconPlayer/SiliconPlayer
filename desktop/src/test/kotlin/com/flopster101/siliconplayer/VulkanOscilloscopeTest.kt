package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class VulkanOscilloscopeTest {

    @Test
    fun testVulkanOscilloscopeOffscreenRenderAndReadback() {
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

        SiliconVisNativeBridge.nativeSetMode(handle, 2)
        SiliconVisNativeBridge.nativeSetOscilloscopeOptions(
            handle = handle,
            stereo = false,
            windowMs = 30,
            triggerMode = 0,
            waveColorArgb = -1,
            lineWidthPx = 2f,
            gridColorArgb = 0x40FFFFFF,
            gridWidthPx = 1f,
            showCenterLine = true,
            showGrid = true
        )

        // Full-scale sine: the trace must cross the middle band of the frame.
        val frames = 512
        val pcm = FloatArray(frames) { i -> sin(2.0 * Math.PI * i / 64.0).toFloat() }
        SiliconVisNativeBridge.nativePushPcm(handle, pcm, frames, 1, 48000)
        repeat(3) {
            SiliconVisNativeBridge.nativeRenderVulkan(handle)
        }

        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback from Vulkan offscreen frame", readbackOk)

        var midBandNonZero = 0
        for (y in height / 4 until 3 * height / 4) {
            for (x in 0 until width) {
                val base = (y * width + x) * 4
                if (readbackBuffer.get(base) != 0.toByte() ||
                    readbackBuffer.get(base + 1) != 0.toByte() ||
                    readbackBuffer.get(base + 2) != 0.toByte()
                ) {
                    midBandNonZero++
                }
            }
        }
        println("Vulkan Oscilloscope frame rendered: $midBandNonZero non-black pixels in mid band")
        assertTrue("Expected wave trace crossing the mid band", midBandNonZero > width * 2)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }
}
