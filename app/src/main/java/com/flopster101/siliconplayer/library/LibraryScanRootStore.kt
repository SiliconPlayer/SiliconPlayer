package com.flopster101.siliconplayer.library

import android.content.Context
import org.json.JSONArray

data class LibraryScanRoot(
    val path: String,
    val enabled: Boolean = true
)

internal object LibraryScanRootStore {

    private const val PREFS_NAME = "library_prefs"
    private const val KEY_SCAN_ROOTS = "scan_roots_json"
    private const val KEY_SCANNER_EXTENSIONS = "scanner_extensions"
    private const val KEY_AUTO_SCAN_ENABLED = "auto_scan_enabled"

    val DEFAULT_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac",
        "wma", "mka", "ape", "wv", "aiff", "aif", "alac"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadRoots(context: Context): List<LibraryScanRoot> {
        val raw = prefs(context).getString(KEY_SCAN_ROOTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val path = entry.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LibraryScanRoot(path = path, enabled = entry.optBoolean("enabled", true))
            }
        }.getOrElse { emptyList() }
    }

    fun saveRoots(context: Context, roots: List<LibraryScanRoot>) {
        val array = JSONArray()
        roots.forEach { root ->
            array.put(
                org.json.JSONObject()
                    .put("path", root.path)
                    .put("enabled", root.enabled)
            )
        }
        prefs(context).edit().putString(KEY_SCAN_ROOTS, array.toString()).apply()
    }

    fun loadExtensions(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_SCANNER_EXTENSIONS, null) ?: return DEFAULT_EXTENSIONS
        val parsed = raw.split(',', ' ', ';').map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotBlank() }.toSet()
        return if (parsed.isEmpty()) DEFAULT_EXTENSIONS else parsed
    }

    fun saveExtensions(context: Context, extensions: Set<String>) {
        prefs(context).edit().putString(KEY_SCANNER_EXTENSIONS, extensions.joinToString(",")).apply()
    }

    fun autoScanEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SCAN_ENABLED, true)

    fun setAutoScanEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SCAN_ENABLED, enabled).apply()
    }
}
