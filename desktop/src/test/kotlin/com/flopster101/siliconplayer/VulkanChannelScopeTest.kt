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

    @Test
    fun testVulkanDesktopSkiaReadbackAndBackendSwitch() {
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
        SiliconVisNativeBridge.nativeResizeVulkan(handle, width, height, 1.0f)

        val sampleCount = 256
        val channel0 = FloatArray(sampleCount) { i -> sin(i * 0.05).toFloat() * 0.8f }
        SiliconVisNativeBridge.nativePushChannelScopeHistory(handle, 0, channel0, sampleCount)

        SiliconVisNativeBridge.nativeRenderVulkan(handle)

        val bufferSize = width * height * 4
        val directBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, directBuffer)
        assertTrue("Expected successful readback", readbackOk)

        val pixelByteArray = ByteArray(bufferSize)
        directBuffer.position(0)
        directBuffer.get(pixelByteArray)

        val info = org.jetbrains.skia.ImageInfo(
            org.jetbrains.skia.ColorInfo(
                org.jetbrains.skia.ColorType.RGBA_8888,
                org.jetbrains.skia.ColorAlphaType.PREMUL,
                org.jetbrains.skia.ColorSpace.sRGB
            ),
            width,
            height
        )
        val bitmap = org.jetbrains.skia.Bitmap().also { it.allocPixels(info) }
        org.jetbrains.skia.Image.makeRaster(info, pixelByteArray, width * 4).use { raster ->
            raster.readPixels(bitmap)
        }
        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)

        val hostHandle = com.flopster101.siliconplayer.ui.visualization.gl.DesktopGlSurface.nativeInit(handle)
        if (hostHandle != 0L) {
            val glOk = com.flopster101.siliconplayer.ui.visualization.gl.DesktopGlSurface.nativeRenderFrame(
                hostHandle,
                handle,
                width,
                height,
                1.0f,
                directBuffer
            )
            assertTrue("Expected successful GL render frame", glOk)
            com.flopster101.siliconplayer.ui.visualization.gl.DesktopGlSurface.nativeDestroy(hostHandle, handle)
        }

        SiliconVisNativeBridge.nativeDestroy(handle)
    }

    @Test
    fun testVulkanTransitionSnapshotAndDrawing() {
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

        // Render first frame with channel 0 data
        val sampleCount = 512
        val channel0 = FloatArray(sampleCount) { i -> sin(i * 0.05).toFloat() * 0.8f }
        SiliconVisNativeBridge.nativePushChannelScopeHistory(handle, 0, channel0, sampleCount)
        SiliconVisNativeBridge.nativeRenderVulkan(handle)

        // Take transition snapshot of frame 1
        val snapshotOk = SiliconVisNativeBridge.nativeTakeTransitionSnapshotVulkan(handle)
        assertTrue("Expected successful transition snapshot capture", snapshotOk)

        // Render frame 2 with transition active (50% slide & fade)
        val channel1 = FloatArray(sampleCount) { i -> sin(i * 0.10).toFloat() * 0.8f }
        SiliconVisNativeBridge.nativePushChannelScopeHistory(handle, 1, channel1, sampleCount)
        SiliconVisNativeBridge.nativeSetTransitionVulkan(handle, -40.0f, 0.5f)
        SiliconVisNativeBridge.nativeRenderVulkan(handle)

        val bufferSize = width * height * 4
        val directBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, directBuffer)
        assertTrue("Expected successful readback of frame with transition quad", readbackOk)

        var nonZeroBytes = 0
        directBuffer.rewind()
        for (i in 0 until bufferSize) {
            if (directBuffer.get(i) != 0.toByte()) {
                nonZeroBytes++
            }
        }
        println("Vulkan transition frame rendered: $nonZeroBytes non-zero bytes")
        assertTrue("Expected non-zero pixels in transition composite frame", nonZeroBytes > 0)

        // Release snapshot
        SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(handle)
        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }

    @Test
    fun testVulkanArtworkBackdropAndContrastScrim() {
        if (!SiliconVisNativeBridge.nativeVulkanIsSupported()) {
            println("Vulkan not supported on this platform, skipping.")
            return
        }

        val handle = SiliconVisNativeBridge.nativeCreate()
        assertTrue("Expected valid visualizer handle", handle != 0L)

        val width = 320
        val height = 240
        val initOk = SiliconVisNativeBridge.nativeInitVulkan(handle, width, height, null)
        assertTrue("Vulkan pipeline initialization failed", initOk)

        SiliconVisNativeBridge.nativeSetMode(handle, 1) // SILICON_VIS_MODE_CHANNEL_SCOPE
        SiliconVisNativeBridge.nativeSetArtworkTheme(handle, 0xFF4A90E2.toInt(), 0xFF1E1E1E.toInt(), 1)
        SiliconVisNativeBridge.nativeSetContrastMode(handle, 6) // SILICON_VIS_CONTRAST_CHANNEL_SCOPE
        SiliconVisNativeBridge.nativeSetContrastScrim(handle, 0xFF000000.toInt())
        SiliconVisNativeBridge.nativeSetShowArtworkBackground(handle, true)

        // 1. Upload non-square artwork (e.g. 100x60) to test aspect-fit and blur fill
        val artW = 100
        val artH = 60
        val artBuf = ByteBuffer.allocateDirect(artW * artH * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until artW * artH) {
            artBuf.put(200.toByte()) // R
            artBuf.put(100.toByte()) // G
            artBuf.put(50.toByte())  // B
            artBuf.put(255.toByte()) // A
        }
        artBuf.flip()
        SiliconVisNativeBridge.nativeSetArtworkPixels(handle, artBuf, artW, artH)

        // Push test channel history
        val sampleCount = 256
        val channel0 = FloatArray(sampleCount) { i -> sin(i * 0.05).toFloat() * 0.8f }
        SiliconVisNativeBridge.nativePushChannelScopeHistory(handle, 0, channel0, sampleCount)

        // Render frame with artwork and contrast scrim
        SiliconVisNativeBridge.nativeRenderVulkan(handle)

        val bufferSize = width * height * 4
        val directBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val readbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, directBuffer)
        assertTrue("Expected successful readback of frame with artwork backdrop", readbackOk)

        var nonZeroBytes = 0
        directBuffer.rewind()
        for (i in 0 until bufferSize) {
            if (directBuffer.get(i) != 0.toByte()) {
                nonZeroBytes++
            }
        }
        println("Vulkan artwork backdrop frame rendered: $nonZeroBytes non-zero bytes")
        assertTrue("Expected non-zero pixels in artwork backdrop frame", nonZeroBytes > (bufferSize / 2))

        // 2. Clear artwork and upload placeholder icon
        val iconSize = 48
        val iconBuf = ByteBuffer.allocateDirect(iconSize * iconSize * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until iconSize * iconSize) {
            iconBuf.put(255.toByte())
            iconBuf.put(255.toByte())
            iconBuf.put(255.toByte())
            iconBuf.put(255.toByte())
        }
        iconBuf.flip()
        SiliconVisNativeBridge.nativeSetArtworkPixels(handle, null, 0, 0)
        SiliconVisNativeBridge.nativeSetIconPixels(handle, iconBuf, iconSize, iconSize)

        SiliconVisNativeBridge.nativeRenderVulkan(handle)
        val readbackFallbackOk = SiliconVisNativeBridge.nativeReadbackVulkan(handle, directBuffer)
        assertTrue("Expected successful readback of fallback icon frame", readbackFallbackOk)

        SiliconVisNativeBridge.nativeReleaseVulkan(handle)
        SiliconVisNativeBridge.nativeDestroy(handle)
    }
}
