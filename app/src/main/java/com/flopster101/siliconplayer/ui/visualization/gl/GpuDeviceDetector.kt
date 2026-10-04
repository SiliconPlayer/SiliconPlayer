package com.flopster101.siliconplayer.ui.visualization.gl

import android.os.Build

object GpuDeviceDetector {
    @Volatile
    private var cachedIsXclipse: Boolean? = null

    fun isXclipse(): Boolean {
        cachedIsXclipse?.let { return it }

        val deviceName = try {
            SiliconVisNativeBridge.nativeVulkanGetDeviceName()
        } catch (_: Throwable) {
            null
        }

        val isXclipse = if (!deviceName.isNullOrBlank()) {
            deviceName.contains("Xclipse", ignoreCase = true)
        } else {
            val hardware = Build.HARDWARE.lowercase()
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.lowercase()
            } else ""
            hardware.contains("s5e9925") || hardware.contains("s5e8845") ||
                hardware.contains("s5e9945") || soc.contains("2200") ||
                soc.contains("1480") || soc.contains("2400")
        }

        cachedIsXclipse = isXclipse
        return isXclipse
    }
}
