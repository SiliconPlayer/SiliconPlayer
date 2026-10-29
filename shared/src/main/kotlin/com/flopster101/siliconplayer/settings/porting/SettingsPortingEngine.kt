package com.flopster101.siliconplayer.settings.porting

import com.flopster101.siliconplayer.platform.AppPreferences

internal data class PortingSelection(
    val selectedEntryIds: Set<String>
)

internal sealed class ExportResult {
    class Ok(val text: String, val keyCount: Int) : ExportResult()
    class Failed(val message: String) : ExportResult()
}

internal class ImportSkippedItem(
    val sectionLabel: String,
    val entryLabel: String,
    val sectionId: String,
    val entryId: String,
    val key: String,
    val reason: String
)

internal class ImportSectionPreview(
    val sectionId: String,
    val sectionLabel: String,
    val entryId: String,
    val entryLabel: String,
    /** Keys the file carries values for that will be written when confirmed. */
    val toApply: List<Pair<String, PrefType>>,
    /** Keys in this entry that will be reset to default because the file has no value for them. */
    val resetToDefault: List<Pair<String, PrefType>>
)

internal sealed class ImportPreview {
    abstract val meta: PortingFileMeta

    class Ok(
        override val meta: PortingFileMeta,
        val sections: List<ImportSectionPreview>,
        val skipped: List<ImportSkippedItem>
    ) : ImportPreview()

    class Unreadable(override val meta: PortingFileMeta) : ImportPreview()
}

internal object SettingsPortingEngine {
    internal val version get() = PortingFileMeta(
        configVersion = SETTINGS_PORTING_CONFIG_VERSION,
        appVersion = PortingVersionProvider.appVersion,
        appPlatform = PortingPlatformInfo.displayName,
        generatedAt = System.currentTimeMillis()
    )

    fun buildExport(
        prefs: AppPreferences,
        catalog: List<PortingSection>,
        selection: PortingSelection
    ): ExportResult {
        val entryMap = flattenEntries(catalog).associateBy { it.entry.id }

        val payload = linkedMapOf<String, LinkedHashMap<String, Pair<PrefType, Any?>>>()
        var keyCount = 0
        for (entryId in selection.selectedEntryIds) {
            val entry = entryMap[entryId]?.entry ?: continue
            val entryKeys = payload.getOrPut(entry.id) { linkedMapOf() }
            for (kv in entry.keys) {
                val current = readTyped(prefs, kv.type, kv.key, kv.default(prefs))
                    ?: continue
                val default = kv.default(prefs)
                if (valuesEqual(current, default)) continue
                entryKeys[kv.key] = kv.type to current
                keyCount++
            }
        }
        return ExportResult.Ok(
            text = SettingsPortingJson.write(version, payload),
            keyCount = keyCount
        )
    }

    fun prepareImport(
        prefs: AppPreferences,
        catalog: List<PortingSection>,
        text: String,
        platform: PortingPlatformFamily
    ): ImportPreview {
        val meta = SettingsPortingJson.readMeta(text)
            ?: return ImportPreview.Unreadable(PortingFileMeta(-1, "", "", 0))
        val sectionByEntry = buildEntryMap(catalog)
        val previews = LinkedHashMap<String, MutableMap<String, MutablePair<MutableList<Pair<String, PrefType>>, MutableList<Pair<String, PrefType>>>>>()
        val skipped = mutableListOf<ImportSkippedItem>()
        val seen = mutableSetOf<Pair<String, String>>()

        val parsed = SettingsPortingJson.iterateEntries(text) { entryId, key, raw ->
            val located = sectionByEntry[entryId]
            if (located == null) {
                skipped += ImportSkippedItem("(unknown)", entryId, "", entryId, key, "This version has no such entry")
                return@iterateEntries
            }
            val knownKey = located.second.keys.firstOrNull { it.key == key }
            if (knownKey == null) {
                skipped += ImportSkippedItem(located.first.label, located.second.label, located.first.id, entryId, key, "This version has no such key")
                return@iterateEntries
            }
            when (raw) {
                is SettingsPortingJson.RawValue.Malformed -> {
                    skipped += ImportSkippedItem(located.first.label, located.second.label, located.first.id, entryId, key, "Unsupported value: ${raw.reason}")
                    return@iterateEntries
                }
                is SettingsPortingJson.RawValue.Known -> {
                    if (!located.second.isKeyImportable(key, raw.type, raw.value, platform)) {
                        skipped += ImportSkippedItem(located.first.label, located.second.label, located.first.id, entryId, key, "Not available on ${platform.name.lowercase()}")
                        return@iterateEntries
                    }
                    val perEntry = previews.getOrPut(located.first.id) { linkedMapOf() }
                        .getOrPut(entryId) { MutablePair(mutableListOf(), mutableListOf()) }
                    if (perEntry.first.none { it.first == key }) perEntry.first.add(key to raw.type)
                    seen.add(entryId to key)
                }
            }
        }
        return ImportPreview.Ok(meta, previewsToSections(catalog, previews, seen), skipped)
    }

    fun applyImport(
        prefs: AppPreferences,
        catalog: List<PortingSection>,
        text: String,
        selection: PortingSelection,
        platform: PortingPlatformFamily
    ): List<ImportSkippedItem> {
        val skipped = mutableListOf<ImportSkippedItem>()
        val editor = prefs.edit()
        val sectionByEntry = buildEntryMap(catalog)
        val skippedKeysByEntry = mutableMapOf<String, MutableSet<String>>()
        val writtenKeysByEntry = mutableMapOf<String, MutableSet<String>>()
        SettingsPortingJson.iterateEntries(text) { entryId, key, raw ->
            // Only keys of entries the user selected are written.
            if (entryId !in selection.selectedEntryIds) return@iterateEntries
            val located = sectionByEntry[entryId] ?: return@iterateEntries
            val knownKey = located.second.keys.firstOrNull { it.key == key }
            if (knownKey == null) {
                if (raw is SettingsPortingJson.RawValue.Known) {
                    skipped += ImportSkippedItem(located.first.label, located.second.label, located.first.id, entryId, key, "This version has no such key")
                }
                return@iterateEntries
            }
            when (raw) {
                is SettingsPortingJson.RawValue.Known -> {
                    if (!located.second.isKeyImportable(key, raw.type, raw.value, platform)) {
                        skipped += ImportSkippedItem(located.first.label, located.second.label, located.first.id, entryId, key, "Not available on ${platform.name.lowercase()}")
                        skippedKeysByEntry.getOrPut(entryId) { mutableSetOf() }.add(key)
                        return@iterateEntries
                    }
                    knownKey.type.write(editor, key, raw.value)
                    writtenKeysByEntry.getOrPut(entryId) { mutableSetOf() }.add(key)
                }
                is SettingsPortingJson.RawValue.Malformed -> {
                    skipped += ImportSkippedItem(located.first.label, located.second.label, located.first.id, entryId, key, "Unsupported value: ${raw.reason}")
                }
            }
        }

        // Selected entries with no value in the file reset to defaults.
        for (entryId in selection.selectedEntryIds) {
            val entry = sectionByEntry[entryId]?.second ?: continue
            val skip = skippedKeysByEntry[entryId] ?: emptySet()
            for (kv in entry.keys) {
                if (kv.key !in skip && kv.key !in (writtenKeysByEntry[entryId] ?: emptySet())) {
                    editor.remove(kv.key)
                }
            }
        }
        editor.apply()
        return skipped
    }

    private fun previewsToSections(
        catalog: List<PortingSection>,
        previews: Map<String, Map<String, MutablePair<MutableList<Pair<String, PrefType>>, MutableList<Pair<String, PrefType>>>>>,
        seen: Set<Pair<String, String>>
    ): List<ImportSectionPreview> {
        val out = mutableListOf<ImportSectionPreview>()
        for (section in catalog) {
            val entryMap = previews[section.id] ?: continue
            for (child in section.children) {
                val entry = (child as? PortingNode.EntryHolder)?.entry ?: continue
                val info = entryMap[entry.id] ?: continue
                out += ImportSectionPreview(
                    sectionId = section.id,
                    sectionLabel = section.label,
                    entryId = entry.id,
                    entryLabel = entry.label,
                    toApply = info.first.toList(),
                    resetToDefault = entry.keys.filter { it.key !in info.first.map { p -> p.first } }.map { it.key to it.type }
                )
            }
        }
        return out
    }

    private data class MutablePair<A, B>(val first: A, val second: B)

    private fun buildEntryMap(
        catalog: List<PortingSection>
    ): Map<String, Pair<PortingSection, PortingEntry>> {
        val map = LinkedHashMap<String, Pair<PortingSection, PortingEntry>>()
        for (section in catalog) {
            flattenNodes(section.children).forEach { holder ->
                map[holder.entry.id] = section to holder.entry
            }
        }
        return map
    }

    private fun flattenEntries(catalog: List<PortingSection>): List<PortingNode.EntryHolder> =
        catalog.flatMap { flattenNodes(it.children) }

    private fun flattenNodes(
        nodes: List<PortingNode>
    ): List<PortingNode.EntryHolder> =
        nodes.flatMap { node ->
            when (node) {
                is PortingNode.Label -> emptyList()
                is PortingNode.EntryHolder -> listOf(node) + flattenNodes(node.children)
            }
        }

    private fun readTyped(prefs: AppPreferences, type: PrefType, key: String, default: Any): Any? = when (type) {
        PrefType.Int -> prefs.getInt(key, default as Int)
        PrefType.Long -> prefs.getLong(key, default as Long)
        PrefType.Float -> prefs.getFloat(key, default as Float)
        PrefType.Boolean -> prefs.getBoolean(key, default as Boolean)
        PrefType.String -> prefs.getString(key, default as? String)
        PrefType.StringSet -> prefs.getStringSet(key, (default as? Set<*>)?.filterIsInstance<String>()?.toSet())
    }

    private fun valuesEqual(a: Any?, b: Any?): Boolean {
        if (a == null || b == null) return a == b
        if (a is Number && b is Number) {
            return when {
                a is Float || b is Float -> a.toFloat() == b.toFloat()
                a is Double || b is Double -> a.toDouble() == b.toDouble()
                a is Long || b is Long -> a.toLong() == b.toLong()
                else -> a.toInt() == b.toInt()
            }
        }
        if (a is Set<*> && b is Set<*>) return a.toSet() == b.toSet()
        return a == b
    }
}

internal object PortingVersionProvider {
    var appVersion: String = "dev"
}
