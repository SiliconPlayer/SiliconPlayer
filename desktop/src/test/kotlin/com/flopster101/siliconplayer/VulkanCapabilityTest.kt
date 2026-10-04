package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanCapabilityTest {
    @Test
    fun testVulkanHostCapabilityProbe() {
        val supported = SiliconVisNativeBridge.nativeVulkanIsSupported()
        println("Vulkan supported on host: $supported")
        assertTrue("Vulkan should be supported on this host system", supported)

        val tier = SiliconVisNativeBridge.nativeVulkanGetTier()
        println("Detected Vulkan capability tier: $tier")
        assertTrue("Expected tier to be >= 1 (Tier1_Base11)", tier >= 1)

        val apiVersion = SiliconVisNativeBridge.nativeVulkanGetApiVersion()
        val major = (apiVersion shr 22) and 0x7F
        val minor = (apiVersion shr 12) and 0x3FF
        val patch = apiVersion and 0xFFF
        println("Reported Vulkan API version: $major.$minor.$patch (0x${Integer.toHexString(apiVersion)})")
        assertEquals(1, major)
        assertTrue(minor >= 1)
    }
}
