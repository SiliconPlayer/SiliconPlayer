package com.flopster101.siliconplayer.settings.porting

import com.flopster101.siliconplayer.platform.AppPreferences

internal enum class PrefType(val tag: String) {
    Int("i"),
    Long("l"),
    Float("f"),
    Boolean("b"),
    String("s"),
    StringSet("ss");

    fun write(editor: AppPreferences.Editor, key: String, value: Any?) {
        when (this) {
            Int -> editor.putInt(key, (value as? Number)?.toInt() ?: 0)
            Long -> editor.putLong(key, (value as? Number)?.toLong() ?: 0L)
            Float -> editor.putFloat(key, (value as? Number)?.toFloat() ?: 0f)
            Boolean -> editor.putBoolean(key, value as? Boolean ?: false)
            String -> editor.putString(key, value as? String)
            StringSet -> editor.putStringSet(key, (value as? Set<*>)?.filterIsInstance<String>()?.toSet())
        }
    }

    fun remove(editor: AppPreferences.Editor, key: String) {
        editor.remove(key)
    }
}

/**
 * Internal availability checks run against this value; nothing about the
 * exporting platform leaks into the file content itself except the metadata
 * block, which is advisory only.
 */
internal enum class PortingPlatformFamily { Android, Desktop }

internal data class TypedPrefValue(
    val key: String,
    val type: PrefType,
    /** Compute the canonical default for this key on demand (constants, per-decoder defaults…). */
    val default: (AppPreferences) -> Any,
    /**
     * Keys that only exist when the user explicitly set them (per-backend
     * audio tuning, per-decoder overrides). They export only when present.
     */
    val onlyWhenSet: Boolean = false
)

/**
 * A selectable unit in the export/import tree. Keys do not need to exist in
 * the store; atoms whose current value equals the default are omitted from
 * exports, so freshly-installed configurations export nothing.
 */
internal class PortingEntry(
    val id: String,
    val label: String,
    val defaultSelected: Boolean = true,
    /** Null = every platform. */
    val platforms: Set<PortingPlatformFamily>? = null,
    val keys: List<TypedPrefValue>,
    /**
     * Per-key import validity for time-of-import context a static catalog
     * cannot express (enum values that do not exist on the importing
     * platform, decoder names absent from the registry, etc).
     * Returning false skips the key and reports it in the confirm dialog.
     */
    val isKeyImportable: (key: String, type: PrefType, value: Any?, platform: PortingPlatformFamily) -> Boolean = { _, _, _, _ -> true }
)

/** Non-selectable "(S)" grouping label. */
internal data class PortingGroupLabel(val label: String)

internal sealed class PortingNode {
    abstract val label: String

    class Label(override val label: String) : PortingNode()
    class EntryHolder(
        val entry: PortingEntry,
        val children: List<EntryHolder> = emptyList()
    ) : PortingNode() {
        override val label: String get() = entry.label
    }
}

internal class PortingSection(
    val id: String,
    val label: String,
    val platforms: Set<PortingPlatformFamily>? = null,
    val children: List<PortingNode>
)

internal interface PortingFileIo {
    /** Opens a save dialog; null means the user cancelled. */
    suspend fun pickExportTarget(defaultName: String): PortingFileTarget?
    suspend fun writeTarget(target: PortingFileTarget, data: ByteArray)
    suspend fun pickImportSource(): PortingFileSource?
    /** Platform-native share of the settings file when one exists. */
    suspend fun shareExport(portingData: PortingShareRequest)
    fun toast(message: String)
}

internal class PortingFileTarget(val pathOrUri: String)
internal class PortingFileSource(val name: String?, val bytes: ByteArray)
internal class PortingShareRequest(val bytes: ByteArray, val fileName: String)

internal object PortingPlatformInfo {
    val current: PortingPlatformFamily get() = runningPlatform
    val displayName: String get() = when (runningPlatform) {
        PortingPlatformFamily.Android -> "android"
        PortingPlatformFamily.Desktop -> "desktop"
    }
    var runningPlatform: PortingPlatformFamily = PortingPlatformFamily.Desktop
}
