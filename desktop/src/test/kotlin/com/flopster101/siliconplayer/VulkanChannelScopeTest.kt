package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class VulkanChannelScopeTest {

    @Test
    fun testVulkanChannelScopeOffscreenRenderAndReadback() {
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

        // Set mode to Channel Scope (mode = 4)
        SiliconVisNativeBridge.nativeSetMode(handle, 4)

        // Push sine wave into channel 0 and 1
        val sampleCount = 512
        val channel0 = FloatArray(sampleCount) { i -> sin(i * 0.05).toFloat() * 0.8f }
        val channel1 = FloatArray(sampleCount) { i -> sin(i * 0.10).toFloat() * 0.8f }
        SiliconVisNativeBridge.nativePushChannelScopeHistory(handle, 0, channel0, sampleCount)
        SiliconVisNativeBridge.nativePushChannelScopeHistory(handle, 1, channel1, sampleCount)

        // Render frame
        SiliconVisNativeBridge.nativeRenderVulkan(handle)

        // Read back offscreen rendered frame
        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback from Vulkan offscreen frame", readbackOk)

        // Verify non-empty pixels rendered (waveforms and grid lines)
        var nonZeroBytes = 0
        readbackBuffer.rewind()
        for (i in 0 until bufferSize) {
            if (readbackBuffer.get(i) != 0.toByte()) {
                nonZeroBytes++
            }
        }
        println("Vulkan Channel Scope frame rendered: $nonZeroBytes non-zero bytes out of $bufferSize")
        assertTrue("Expected rendered geometry in framebuffer", nonZeroBytes > 0)

        // Clean up
        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }

    @Test
    fun testVulkanChannelScopeWithTextQuads() {
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

        SiliconVisNativeBridge.nativeSetMode(handle, 4)

        val quads = floatArrayOf(
            10f, 10f, 0f, 0f, 1f, 1f, 1f, 1f,
            50f, 10f, 1f, 0f, 1f, 1f, 1f, 1f,
            10f, 30f, 0f, 1f, 1f, 1f, 1f, 1f,
            10f, 30f, 0f, 1f, 1f, 1f, 1f, 1f,
            50f, 10f, 1f, 0f, 1f, 1f, 1f, 1f,
            50f, 30f, 1f, 1f, 1f, 1f, 1f, 1f
        )
        val textBuf = ByteBuffer.allocateDirect(quads.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        textBuf.put(quads).flip()

        SiliconVisNativeBridge.nativeSetTextQuads(handle, textBuf, 6)
        SiliconVisNativeBridge.nativeRenderVulkan(handle)

        val bufferSize = width * height * 4
        val readbackBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, readbackBuffer)
        assertTrue("Expected successful readback with text quads", readbackOk)

        SiliconVisNativeBridge.nativeSetTextQuads(handle, null, 0)
        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }
}
