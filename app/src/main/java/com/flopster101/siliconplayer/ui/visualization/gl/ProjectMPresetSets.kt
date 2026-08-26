package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.content.SharedPreferences
import com.flopster101.siliconplayer.AppPreferenceKeys
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * A named collection of MilkDrop presets rooted at a single directory. The id is
 * a stable machine key; the label is what gets shown to the user.
 */
data class ProjectMPresetSet(
    val id: String,
    val label: String,
    val dir: String,
    val isInternal: Boolean
)

/**
 * Builds and persists the set of projectM preset sets. Bundled (internal) sets
 * are always enumerable; user sets are paths the user typed in. Enabling is
 * tracked separately so at least one set can always stay on.
 */
object ProjectMPresetSets {
    const val INTERNAL_TEST_ID = "internal_projectm_tests"
    private const val INTERNAL_TEST_LABEL = "Internal — projectM test presets"
    private const val INTERNAL_TEST_ASSET_DIR = "projectm"
    private const val INTERNAL_TEST_FILES_DIR = "projectm_presets"

    // A preset key is "<setId>\x1F<relativePath>"; this mirrors the native separator.
    private const val KEY_SEPARATOR = "\u001F"

    fun internalTestDir(context: Context): String? = ensureExtracted(context)

    fun userSets(prefs: SharedPreferences): List<ProjectMPresetSet> {
        val raw = prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_USER_PRESET_PATHS, null)
            ?: return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val path = line.trim()
            if (path.isEmpty()) return@mapNotNull null
            ProjectMPresetSet(
                id = "user_${UUID.nameUUIDFromBytes(path.toByteArray()).toString()}",
                label = userLabelFor(path),
                dir = path,
                isInternal = false
            )
        }
    }

    fun internalSets(context: Context): List<ProjectMPresetSet> {
        val dir = internalTestDir(context) ?: return emptyList()
        return listOf(
            ProjectMPresetSet(
                id = INTERNAL_TEST_ID,
                label = INTERNAL_TEST_LABEL,
                dir = dir,
                isInternal = true
            )
        )
    }

    /** All known sets (internal + user). */
    fun allSets(context: Context, prefs: SharedPreferences): List<ProjectMPresetSet> =
        internalSets(context) + userSets(prefs)

    /** The enabled set ids. Defaults to the internal test set on first run. */
    fun enabledSetIds(prefs: SharedPreferences): Set<String> {
        val stored = prefs.getStringSet(AppPreferenceKeys.VISUALIZATION_PROJECTM_ENABLED_SET_IDS, null)
        if (stored != null) return stored.toSet()
        // First run: enable the internal test set.
        return setOf(INTERNAL_TEST_ID)
    }

    /** The sets currently enabled, in a stable order. */
    fun enabledSets(context: Context, prefs: SharedPreferences): List<ProjectMPresetSet> {
        val enabled = enabledSetIds(prefs)
        return allSets(context, prefs).filter { it.id in enabled }
    }

    /**
     * Toggles a set on/off. Refuses to disable the last enabled set so a set
     * always exists; returns true if the change was applied.
     */
    fun setEnabled(prefs: SharedPreferences, setId: String, enabled: Boolean): Boolean {
        val current = enabledSetIds(prefs).toMutableSet()
        if (enabled) {
            current.add(setId)
        } else {
            if (setId !in current) return true
            if (current.size <= 1) return false
            current.remove(setId)
        }
        prefs.edit().putStringSet(AppPreferenceKeys.VISUALIZATION_PROJECTM_ENABLED_SET_IDS, current).apply()
        return true
    }

    /** Adds a user preset folder path. Returns the set id it maps to. */
    fun addUserSet(prefs: SharedPreferences, path: String): String {
        val trimmed = path.trim()
        val existing = userSets(prefs).map { it.dir }.toSet()
        val paths = userSets(prefs).map { it.dir }.toMutableList()
        if (existing.none { it == trimmed }) {
            paths.add(trimmed)
        }
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_USER_PRESET_PATHS, paths.joinToString("\n"))
            .apply()
        val id = "user_${UUID.nameUUIDFromBytes(trimmed.toByteArray()).toString()}"
        prefs.edit().putStringSet(
            AppPreferenceKeys.VISUALIZATION_PROJECTM_ENABLED_SET_IDS,
            (enabledSetIds(prefs) + id).toSet()
        ).apply()
        return id
    }

    /** Removes a user preset folder path. */
    fun removeUserSet(prefs: SharedPreferences, setId: String) {
        val path = userSets(prefs).firstOrNull { it.id == setId }?.dir ?: return
        val paths = userSets(prefs).map { it.dir }.filter { it != path }
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_USER_PRESET_PATHS, paths.joinToString("\n"))
            .apply()
        val enabled = enabledSetIds(prefs).toMutableSet()
        enabled.remove(setId)
        if (enabled.isEmpty()) enabled.add(INTERNAL_TEST_ID)
        prefs.edit().putStringSet(AppPreferenceKeys.VISUALIZATION_PROJECTM_ENABLED_SET_IDS, enabled).apply()
    }

    private const val INDEX_FILE_NAME = "projectm_index.json"
    private const val INDEX_VERSION = 1

    private data class IndexEntry(val dir: String, val dirMtime: Long, val presets: List<String>)

    private fun indexFile(context: Context): File = File(context.filesDir, INDEX_FILE_NAME)

    private fun dirMtime(dir: String): Long = try { File(dir).lastModified() } catch (_: Throwable) { 0L }

    @Volatile private var memoryCache: MutableMap<String, IndexEntry>? = null
    private val cacheLock = Any()

    private fun cachedIndex(context: Context): MutableMap<String, IndexEntry> {
        synchronized(cacheLock) {
            memoryCache?.let { return it }
            val loaded = loadIndex(context)
            memoryCache = loaded
            return loaded
        }
    }

    private fun saveCachedIndex(context: Context, map: Map<String, IndexEntry>) {
        synchronized(cacheLock) {
            memoryCache = map.toMutableMap()
            saveIndex(context, map)
        }
    }

    fun preloadIndex(context: Context) {
        try { cachedIndex(context) } catch (_: Throwable) {}
    }

    private fun scanRelativeMilk(root: File): List<String> {
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<String>()
        val stack = ArrayDeque<Pair<File, String>>()
        stack.add(root to "")
        while (stack.isNotEmpty()) {
            val (dir, prefix) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (child.isDirectory) {
                    stack.add(child to rel)
                } else if (child.isFile && child.name.endsWith(".milk", ignoreCase = true)) {
                    out.add(rel)
                }
            }
        }
        out.sort()
        return out
    }

    private fun loadIndex(context: Context): MutableMap<String, IndexEntry> {
        val out = mutableMapOf<String, IndexEntry>()
        try {
            val f = indexFile(context)
            if (!f.isFile) return out
            val obj = JSONObject(f.readText())
            if (obj.optInt("version", 0) != INDEX_VERSION) return out
            val sets = obj.optJSONObject("sets") ?: return out
            val keys = sets.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val e = sets.optJSONObject(id) ?: continue
                val dir = e.optString("dir", "")
                val mtime = e.optLong("dirMtime", 0L)
                val arr = e.optJSONArray("presets") ?: JSONArray()
                val presets = mutableListOf<String>()
                for (i in 0 until arr.length()) presets.add(arr.optString(i))
                out[id] = IndexEntry(dir, mtime, presets)
            }
        } catch (_: Throwable) { }
        return out
    }

    private fun saveIndex(context: Context, map: Map<String, IndexEntry>) {
        try {
            val obj = JSONObject()
            obj.put("version", INDEX_VERSION)
            val sets = JSONObject()
            for ((id, e) in map) {
                val o = JSONObject()
                o.put("dir", e.dir)
                o.put("dirMtime", e.dirMtime)
                val arr = JSONArray()
                for (p in e.presets) arr.put(p)
                o.put("presets", arr)
                sets.put(id, o)
            }
            obj.put("sets", sets)
            indexFile(context).writeText(obj.toString())
        } catch (_: Throwable) { }
    }

    fun indexedPresetsForSet(context: Context, set: ProjectMPresetSet): List<String> {
        val map = cachedIndex(context)
        val cached = map[set.id]
        if (cached != null && cached.dir == set.dir) return cached.presets
        val scanned = scanRelativeMilk(File(set.dir))
        map[set.id] = IndexEntry(set.dir, dirMtime(set.dir), scanned)
        saveCachedIndex(context, map)
        return scanned
    }

    fun indexedPresetKeys(context: Context, prefs: SharedPreferences): Pair<List<String>, List<String>> {
        val sets = enabledSets(context, prefs).sortedBy { it.id }
        if (sets.isEmpty()) return emptyList<String>() to emptyList()
        val map = cachedIndex(context)
        var dirty = false
        val allKeys = ArrayList<String>(4096)
        val allSetIds = ArrayList<String>(4096)
        for (set in sets) {
            val cached = map[set.id]
            val presets = if (cached != null && cached.dir == set.dir) cached.presets else {
                val scanned = scanRelativeMilk(File(set.dir))
                map[set.id] = IndexEntry(set.dir, dirMtime(set.dir), scanned)
                dirty = true
                scanned
            }
            for (rel in presets) {
                allKeys.add("${set.id}$KEY_SEPARATOR$rel")
                allSetIds.add(set.id)
            }
        }
        if (dirty) saveCachedIndex(context, map)
        return allKeys to allSetIds
    }

    fun reindex(context: Context, prefs: SharedPreferences): Int {
        val sets = enabledSets(context, prefs)
        val map = cachedIndex(context)
        var total = 0
        for (set in sets) {
            val scanned = scanRelativeMilk(File(set.dir))
            map[set.id] = IndexEntry(set.dir, dirMtime(set.dir), scanned)
            total += scanned.size
        }
        val validIds = allSets(context, prefs).map { it.id }.toSet()
        val pruned = map.filterKeys { it in validIds }
        saveCachedIndex(context, pruned)
        return total
    }

    /** Splits a preset key into its (setId, relativePath) pair. */
    fun splitKey(key: String): Pair<String, String> {
        val idx = key.indexOf(KEY_SEPARATOR)
        if (idx <= 0) return key to ""
        return key.substring(0, idx) to key.substring(idx + KEY_SEPARATOR.length)
    }

    /**
     * Counts .milk presets under a set's directory (recursively).
     * Returns 0 if the directory is missing. Used only for the settings-page
     * summary label; the native side does its own scan at attach.
     */
    fun presetCountFor(set: ProjectMPresetSet): Int {
        val root = File(set.dir)
        if (!root.isDirectory) return 0
        var count = 0
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.add(child)
                } else if (child.isFile && child.name.endsWith(".milk", ignoreCase = true)) {
                    count++
                }
            }
        }
        return count
    }

    fun presetCountForIndexed(context: Context, set: ProjectMPresetSet): Int {
        return try { indexedPresetsForSet(context, set).size } catch (_: Throwable) { presetCountFor(set) }
    }

    private fun userLabelFor(path: String): String {
        val name = path.trimEnd('/').substringAfterLast('/')
        return if (name.isEmpty()) path else name
    }

    private fun ensureExtracted(context: Context): String? {
        return try {
            val outDir = File(context.filesDir, INTERNAL_TEST_FILES_DIR)
            val assetNames = context.assets.list(INTERNAL_TEST_ASSET_DIR).orEmpty()
                .filter { it.endsWith(".milk") }
            if (assetNames.isEmpty()) {
                return if (outDir.isDirectory) outDir.absolutePath else null
            }
            if (!outDir.isDirectory) outDir.mkdirs()
            assetNames.forEach { name ->
                val outFile = File(outDir, name)
                if (!outFile.isFile || outFile.length() == 0L) {
                    context.assets.open("$INTERNAL_TEST_ASSET_DIR/$name").use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            outDir.absolutePath
        } catch (t: Throwable) {
            android.util.Log.w("SiliconVis", "projectM preset extraction failed", t)
            null
        }
    }
}
