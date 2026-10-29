package com.flopster101.siliconplayer.settings.porting

import com.flopster101.siliconplayer.platform.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPortingEngineTest {

    private class FakePrefs : AppPreferences {
        val values = mutableMapOf<String, Any>()
        val strings = mutableMapOf<String, String?>()
        val sets = mutableMapOf<String, Set<String>?>()

        override fun getString(key: String, defValue: String?): String? = strings.getOrDefault(key, defValue)
        override fun getInt(key: String, defValue: Int): Int = values.getOrDefault(key, defValue) as Int
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values.getOrDefault(key, defValue) as Boolean
        override fun getFloat(key: String, defValue: Float): Float = values.getOrDefault(key, defValue) as Float
        override fun getLong(key: String, defValue: Long): Long = values.getOrDefault(key, defValue) as Long
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = sets.getOrDefault(key, defValues)
        override fun contains(key: String): Boolean =
            values.containsKey(key) || strings.containsKey(key) || sets.containsKey(key)
        override fun allKeys(): Set<String> = values.keys + strings.keys + sets.keys
        override fun edit(): AppPreferences.Editor = FakeEditor()
        override fun addListener(listener: AppPreferences.OnChangeListener) {}
        override fun removeListener(listener: AppPreferences.OnChangeListener) {}

        private inner class FakeEditor : AppPreferences.Editor {
            override fun putString(key: String, value: String?): AppPreferences.Editor {
                if (value == null) strings.remove(key) else strings[key] = value
                return this
            }
            override fun putInt(key: String, value: Int): AppPreferences.Editor { values[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): AppPreferences.Editor { values[key] = value; return this }
            override fun putFloat(key: String, value: Float): AppPreferences.Editor { values[key] = value; return this }
            override fun putLong(key: String, value: Long): AppPreferences.Editor { values[key] = value; return this }
            override fun putStringSet(key: String, value: Set<String>?): AppPreferences.Editor { sets[key] = value; return this }
            override fun remove(key: String): AppPreferences.Editor {
                values.remove(key); strings.remove(key); sets.remove(key)
                return this
            }
            override fun clear(): AppPreferences.Editor { values.clear(); strings.clear(); sets.clear(); return this }
            override fun apply() {}
            override fun commit(): Boolean = true
        }
    }

    private fun holder(id: String, keys: List<TypedPrefValue>, defaultSelected: Boolean = true) =
        PortingNode.EntryHolder(PortingEntry(id, id, defaultSelected, keys = keys))

    private val catalog = listOf(
        PortingSection(
            "ui",
            "UI",
            null,
            listOf(
                holder(
                    "ui.theme",
                    listOf(
                        TypedPrefValue("theme_mode", PrefType.String, { "auto" }),
                        TypedPrefValue("theme_use_monet", PrefType.Boolean, { false })
                    )
                )
            )
        ),
        PortingSection(
            "audioCores",
            "Audio cores",
            null,
            listOf(
                PortingNode.EntryHolder(
                    PortingEntry(
                        "cores.enabled",
                        "cores.enabled",
                        keys = listOf(
                            TypedPrefValue("decoder_ftp_enabled", PrefType.Boolean, { true }),
                            TypedPrefValue("decoder_windows_only_enabled", PrefType.Boolean, { true })
                        ),
                        isKeyImportable = { key: String, _: PrefType, _: Any?, platform: PortingPlatformFamily ->
                            key == "decoder_ftp_enabled" || platform == PortingPlatformFamily.Android
                        }
                    )
                )
            )
        )
    )

    private fun selection(vararg ids: String) = PortingSelection(ids.toSet())

    @Test
    fun exportOmitsValuesAtDefault() {
        val prefs = FakePrefs()
        prefs.values["decoder_ftp_enabled"] = false
        val result = SettingsPortingEngine.buildExport(prefs, catalog, selection("ui.theme", "cores.enabled"))
        assertTrue(result is ExportResult.Ok)
        val text = (result as ExportResult.Ok).text
        assertTrue(text.contains("decoder_ftp_enabled"))
        assertFalse(text.contains("theme_use_monet"))
        assertFalse(text.contains("theme_mode"))
        assertEquals(1, result.keyCount)
    }

    @Test
    fun roundTripAppliesAndResets() {
        val source = FakePrefs()
        source.values["theme_use_monet"] = true
        source.values["decoder_ftp_enabled"] = false
        val text = (SettingsPortingEngine.buildExport(source, catalog, selection("ui.theme", "cores.enabled"))
            as ExportResult.Ok).text

        val target = FakePrefs()
        target.strings["theme_mode"] = "dark"         // will be reset (not in file)
        target.values["theme_use_monet"] = false      // will be applied
        val skipped = SettingsPortingEngine.applyImport(
            target, catalog, text, selection("ui.theme", "cores.enabled"), PortingPlatformFamily.Desktop
        )
        assertEquals(emptyList<ImportSkippedItem>(), skipped)
        assertEquals(true, target.values["theme_use_monet"])
        assertTrue(target.strings["theme_mode"] == null && !target.strings.containsKey("theme_mode"))
    }

    @Test
    fun importSkipsPlatformUnavailableKeys() {
        val source = FakePrefs()
        source.values["theme_use_monet"] = true
        source.values["decoder_ftp_enabled"] = false
        source.values["decoder_windows_only_enabled"] = false
        val text = (SettingsPortingEngine.buildExport(source, catalog, selection("ui.theme", "cores.enabled"))
            as ExportResult.Ok).text

        val target = FakePrefs()
        val preview = SettingsPortingEngine.prepareImport(
            target, catalog, text, PortingPlatformFamily.Desktop
        ) as ImportPreview.Ok
        assertEquals(1, preview.skipped.size)
        assertEquals("decoder_windows_only_enabled", preview.skipped.first().key)

        val skipped = SettingsPortingEngine.applyImport(
            target, catalog, text, selection("ui.theme", "cores.enabled"), PortingPlatformFamily.Desktop
        )
        assertEquals(1, skipped.size)
        // Skipped key is untouched; other keys applied.
        assertEquals(false, target.values["decoder_ftp_enabled"])
        assertFalse(target.values.containsKey("decoder_windows_only_enabled"))
    }

    @Test
    fun unknownEntriesAndKeysAreReportedNotWritten() {
        val text = """
            {
              "format": "siliconplayer.settings",
              "configVersion": 1,
              "appVersion": "1.0",
              "appPlatform": "android",
              "generatedAt": 1,
              "entries": {
                "ghost.entry": {"some_key": {"t": "b", "v": true}},
                "ui.theme": {"unknown_key": {"t": "b", "v": true}}
              }
            }
        """.trimIndent()
        val prefs = FakePrefs()
        val preview = SettingsPortingEngine.prepareImport(
            prefs, catalog, text, PortingPlatformFamily.Desktop
        ) as ImportPreview.Ok
        assertEquals(2, preview.skipped.size)
        assertFalse(preview.skipped.any { it.reason.startsWith("Not available") })

        val skipped = SettingsPortingEngine.applyImport(
            prefs, catalog, text, selection("ui.theme"), PortingPlatformFamily.Desktop
        )
        assertEquals(1, skipped.size)
        assertTrue(prefs.values.isEmpty() && prefs.strings.isEmpty())
    }

    @Test
    fun corruptFileIsUnreadable() {
        val prefs = FakePrefs()
        val preview = SettingsPortingEngine.prepareImport(prefs, catalog, "{ not json", PortingPlatformFamily.Desktop)
        assertTrue(preview is ImportPreview.Unreadable)
    }
}
