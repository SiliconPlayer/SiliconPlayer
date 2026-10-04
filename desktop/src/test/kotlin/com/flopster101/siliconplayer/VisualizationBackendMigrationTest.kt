package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryPrefs : AppPreferences {
    val map = mutableMapOf<String, Any?>()
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = null
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun allKeys(): Set<String> = map.keys
    override fun edit(): AppPreferences.Editor = object : AppPreferences.Editor {
        override fun putString(key: String, value: String?): AppPreferences.Editor {
            map[key] = value
            return this
        }
        override fun putInt(key: String, value: Int): AppPreferences.Editor {
            map[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): AppPreferences.Editor {
            map[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): AppPreferences.Editor {
            map[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): AppPreferences.Editor {
            map[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): AppPreferences.Editor = this
        override fun remove(key: String): AppPreferences.Editor {
            map.remove(key)
            return this
        }
        override fun clear(): AppPreferences.Editor {
            map.clear()
            return this
        }
        override fun apply() {}
        override fun commit(): Boolean = true
    }
    override fun addListener(listener: AppPreferences.OnChangeListener) {}
    override fun removeListener(listener: AppPreferences.OnChangeListener) {}
}

class VisualizationBackendMigrationTest {

    @Test
    fun testMigratesAllBackendsOnce() {
        val prefs = InMemoryPrefs()
        migrateVisualizationBackendsToVulkan(prefs)

        // Vulkan SurfaceView where supported, OpenGL SurfaceView otherwise.
        val expected = if (supportsVulkanRendering()) {
            VisualizationRenderBackend.VulkanSurface.storageValue
        } else {
            VisualizationRenderBackend.OpenGlSurface.storageValue
        }
        val keys = listOf(
            AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND,
            AppPreferenceKeys.VISUALIZATION_OSC_RENDER_BACKEND,
            AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND,
            AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND,
            AppPreferenceKeys.VISUALIZATION_STARFIELD_RENDER_BACKEND
        )
        for (key in keys) {
            assertEquals(expected, prefs.getString(key, null))
        }

        // A later user choice must survive: second run is a no-op.
        prefs.edit().putString(AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND, "opengl_texture").apply()
        migrateVisualizationBackendsToVulkan(prefs)
        assertEquals("opengl_texture", prefs.getString(AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND, null))
        assertTrue(prefs.getBoolean("visualization_vulkan_backend_migration_done_v1", false))
    }
}
