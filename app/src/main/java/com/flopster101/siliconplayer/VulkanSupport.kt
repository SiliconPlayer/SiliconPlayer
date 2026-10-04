package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge

private var cachedVulkanRenderingSupport: Boolean? = null

fun supportsVulkanRendering(): Boolean {
    cachedVulkanRenderingSupport?.let { return it }
    val supported = try {
        SiliconVisNativeBridge.nativeVulkanIsSupported()
    } catch (_: Throwable) {
        false
    }
    cachedVulkanRenderingSupport = supported
    return supported
}
