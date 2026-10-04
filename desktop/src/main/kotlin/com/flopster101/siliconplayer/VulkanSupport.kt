package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge

private const val VK_API_VERSION_1_1 = (1 shl 22) or (1 shl 12)

private var cachedVulkanRenderingSupport: Boolean? = null

// Software rasterizers and pre-1.1 drivers stay on OpenGL.
fun supportsVulkanRendering(): Boolean {
    cachedVulkanRenderingSupport?.let { return it }
    val supported = try {
        if (!SiliconVisNativeBridge.nativeVulkanIsSupported()) {
            false
        } else if (SiliconVisNativeBridge.nativeVulkanGetApiVersion() < VK_API_VERSION_1_1) {
            false
        } else {
            val deviceName = SiliconVisNativeBridge.nativeVulkanGetDeviceName().orEmpty()
            !deviceName.contains("llvmpipe", ignoreCase = true) &&
                !deviceName.contains("lavapipe", ignoreCase = true)
        }
    } catch (_: Throwable) {
        false
    }
    cachedVulkanRenderingSupport = supported
    return supported
}
