package com.flopster101.siliconplayer

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

// Crash-safe small-file IO for per-domain config files. Writes go to a temp
// file followed by an atomic same-directory rename; the previous version is
// kept as `.bak` so a torn write can never lose more than one save.
internal fun domainFileNameForKey(key: String): String = when (key) {
    AppPreferenceKeys.RECENT_FOLDERS -> "recent-folders.json"
    AppPreferenceKeys.RECENT_PLAYED_FILES -> "recent-files.json"
    AppPreferenceKeys.PINNED_HOME_ENTRIES -> "pinned.json"
    else -> "$key.json"
}

internal fun domainFileForKey(configDir: File, key: String): File =
    File(configDir, domainFileNameForKey(key))

internal fun sanitizeDomainFileName(name: String): String {
    val sanitized = name.map { char ->
        if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_'
    }.joinToString("").trim('_', '.', ' ')
    return sanitized.ifBlank { "unnamed" }
}

internal fun writeTextAtomic(file: File, text: String) {
    try {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        val backup = File(file.parentFile, "${file.name}.bak")
        if (file.exists()) {
            runCatching {
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            runCatching { tmp.delete() }
        }
    } catch (_: Throwable) {}
}

// Process-wide config-dir handle for per-domain files, set once at startup
// (MainActivity.onCreate on Android, main() on desktop). Compat delegates
// route through it; when unset they fall back to legacy prefs behavior.
internal object DomainStoreDirs {
    @Volatile var configDir: File? = null
}

// Deletes a domain file and its backup/tmp siblings, best-effort.
internal fun deleteStoreFile(file: File) {
    runCatching {
        file.delete()
        file.parentFile?.let { parent ->
            File(parent, "${file.name}.bak").delete()
            File(parent, "${file.name}.tmp").delete()
        }
    }
}

internal fun deleteDomainFile(configDir: File, key: String) {
    deleteStoreFile(domainFileForKey(configDir, key))
}

// Wipes every per-domain file (recents, playlists, favorites, network,
// credentials). Reserved for the future nuclear-reset option; the regular
// settings reset must not touch domain files.
internal fun clearAllDomainFiles(configDir: File) {
    deleteDomainFile(configDir, AppPreferenceKeys.RECENT_FOLDERS)
    deleteDomainFile(configDir, AppPreferenceKeys.RECENT_PLAYED_FILES)
    deleteDomainFile(configDir, AppPreferenceKeys.PINNED_HOME_ENTRIES)
    deleteStoreFile(networkNodesFile(configDir))
    deleteStoreFile(networkCredentialsFile(configDir))
    deleteStoreFile(playlistFavoritesFile(configDir))
    runCatching { playlistLibraryDir(configDir).deleteRecursively() }
}

// Non-blank candidate texts, newest first (main, then backup).
internal fun readCandidateTexts(file: File): List<String> {
    val candidates = mutableListOf<String>()
    if (file.isFile) {
        runCatching { file.readText() }.getOrNull()
            ?.takeUnless { it.isBlank() }
            ?.let(candidates::add)
    }
    val backup = file.parentFile?.let { File(it, "${file.name}.bak") }
    if (backup != null && backup.isFile) {
        runCatching { backup.readText() }.getOrNull()
            ?.takeUnless { it.isBlank() }
            ?.let(candidates::add)
    }
    return candidates
}

// First candidate that parses as a top-level JSON array/object, else null.
// Gates tolerant decoders so corrupt mains fall through to the backup.
internal fun firstParsableJson(candidates: List<String>, isObject: Boolean): String? {
    for (raw in candidates) {
        val parsable = runCatching {
            if (isObject) JSONObject(raw) else JSONArray(raw)
        }.isSuccess
        if (parsable) return raw
    }
    return null
}
