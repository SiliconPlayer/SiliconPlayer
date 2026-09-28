package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class UfmodStoredOptionsTest {

    private class FakePrefs : AppPreferences {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): AppPreferences.Editor = object : AppPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { values[key] = value ?: "" }
            override fun putInt(key: String, value: Int) = apply { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
            override fun putFloat(key: String, value: Float) = apply { values[key] = value }
            override fun putLong(key: String, value: Long) = apply { values[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { this@FakePrefs.values[key] = values.orEmpty() }
            override fun remove(key: String) = apply { this@FakePrefs.values.remove(key) }
            override fun clear() = apply { this@FakePrefs.values.clear() }
            override fun apply() {}
            override fun commit(): Boolean = true
        }
        override fun addListener(listener: AppPreferences.OnChangeListener) {}
        override fun removeListener(listener: AppPreferences.OnChangeListener) {}
    }

    @Test
    fun quirksFallBackToZeroWhenUnset() {
        assertEquals(0, storedUfmodQuirks(FakePrefs()))
    }

    @Test
    fun quirksComeFromTheStoredPref() {
        val prefs = FakePrefs()
        prefs.edit().putInt(CorePreferenceKeys.UFMOD_QUIRKS, 3).apply()
        assertEquals(3, storedUfmodQuirks(prefs))
    }

    // Pinned to the option name UfmodDecoder::setOption matches.
    @Test
    fun optionKeyMatchesTheNativeDecoder() {
        assertEquals("ufmod.quirks", UfmodOptionKeys.QUIRKS)
    }
}
