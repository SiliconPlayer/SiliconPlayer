package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences

private const val VULKAN_BACKEND_MIGRATION_DONE_KEY = "visualization_vulkan_backend_migration_done_v1"

// One-time migration to Vulkan SurfaceView backends. Runs before any
// backend pref is read so migrated values apply on the first frame.
fun migrateVisualizationBackendsToVulkan(prefs: AppPreferences) {
    if (prefs.getBoolean(VULKAN_BACKEND_MIGRATION_DONE_KEY, false)) return
    val vulkanSurface = VisualizationRenderBackend.VulkanSurface.storageValue
    prefs.edit()
        .putString(AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND, vulkanSurface)
        .putString(AppPreferenceKeys.VISUALIZATION_OSC_RENDER_BACKEND, vulkanSurface)
        .putString(AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND, vulkanSurface)
        .putString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND, vulkanSurface)
        .putString(AppPreferenceKeys.VISUALIZATION_STARFIELD_RENDER_BACKEND, vulkanSurface)
        .putBoolean(VULKAN_BACKEND_MIGRATION_DONE_KEY, true)
        .apply()
}
