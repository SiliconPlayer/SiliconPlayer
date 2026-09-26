package com.flopster101.siliconplayer.library

import com.flopster101.siliconplayer.firstParsableJson
import com.flopster101.siliconplayer.readCandidateTexts
import com.flopster101.siliconplayer.writeTextAtomic
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal const val LIBRARY_TRACKS_FILE_NAME = "library.json"
internal const val LIBRARY_SCAN_FILE_NAME = "library_scan.json"

// Null extensions means the default set (see defaultScanExtensions).
internal data class DesktopLibraryScanConfig(
    val roots: List<LibraryScanRoot>,
    val extensions: Set<String>? = null,
    val autoScanEnabled: Boolean = true,
    val lastSyncMs: Long = 0L
)

internal fun libraryTracksFile(configDir: File): File = File(configDir, LIBRARY_TRACKS_FILE_NAME)

internal fun libraryScanFile(configDir: File): File = File(configDir, LIBRARY_SCAN_FILE_NAME)

internal fun readLibraryTracks(configDir: File): List<LibraryTrackEntity> {
    val raw = firstParsableJson(readCandidateTexts(libraryTracksFile(configDir)), isObject = false)
        ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            decodeLibraryTrack(array.optJSONObject(index))
        }
    }.getOrElse { emptyList() }
}

internal fun writeLibraryTracks(configDir: File, tracks: List<LibraryTrackEntity>) {
    val array = JSONArray()
    tracks.forEach { track ->
        array.put(
            JSONObject()
                .put("path", track.path)
                .put("sourceId", track.sourceId)
                .put("dedupKey", track.dedupKey)
                .put("title", track.title)
                .put("artist", track.artist)
                .put("albumArtist", track.albumArtist)
                .put("album", track.album)
                .put("trackNo", track.trackNo)
                .put("discNo", track.discNo)
                .put("durationMs", track.durationMs)
                .put("year", track.year)
                .put("format", track.format)
                .put("sizeBytes", track.sizeBytes)
                .put("mtimeMs", track.mtimeMs)
                .put("addedAtMs", track.addedAtMs)
        )
    }
    writeTextAtomic(libraryTracksFile(configDir), array.toString())
}

internal fun readLibraryScanConfig(configDir: File): DesktopLibraryScanConfig {
    val raw = firstParsableJson(readCandidateTexts(libraryScanFile(configDir)), isObject = true)
    if (raw != null) {
        runCatching {
            val json = JSONObject(raw)
            val roots = json.optJSONArray("roots")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    val path = entry.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    LibraryScanRoot(path = path, enabled = entry.optBoolean("enabled", true))
                }
            } ?: emptyList()
            val extensions = json.optJSONArray("extensions")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).trim().lowercase().removePrefix(".")
                        .takeIf { it.isNotBlank() }
                }.toSet()
            }
            return DesktopLibraryScanConfig(
                roots = roots,
                extensions = extensions,
                autoScanEnabled = json.optBoolean("autoScanEnabled", true),
                lastSyncMs = json.optLong("lastSyncMs", 0L)
            )
        }
    }
    // First launch: preseed the XDG music directory so the library is useful
    // before the settings round adds folder management.
    val musicRoot = defaultMusicScanRoot()?.let { listOf(LibraryScanRoot(path = it)) } ?: emptyList()
    return DesktopLibraryScanConfig(roots = musicRoot)
}

internal fun writeLibraryScanConfig(configDir: File, config: DesktopLibraryScanConfig) {
    val json = JSONObject()
        .put("autoScanEnabled", config.autoScanEnabled)
        .put("lastSyncMs", config.lastSyncMs)
    val roots = JSONArray()
    config.roots.forEach { root ->
        roots.put(JSONObject().put("path", root.path).put("enabled", root.enabled))
    }
    json.put("roots", roots)
    if (config.extensions != null) {
        val extensions = JSONArray()
        config.extensions.forEach { extensions.put(it) }
        json.put("extensions", extensions)
    }
    writeTextAtomic(libraryScanFile(configDir), json.toString())
}

// Null means the conventional sampled set; module formats stay in the
// file browser. Users can widen it via the stored extensions override.
internal fun defaultScanExtensions(): Set<String> = SCANNER_CONVENTIONAL_EXTENSIONS

// XDG music dir with $HOME/Music fallback; never resolves to $HOME itself.
internal fun defaultMusicScanRoot(): String? {
    val home = System.getProperty("user.home")
    val candidates = listOfNotNull(
        System.getenv("XDG_MUSIC_DIR")?.takeIf { it.isNotBlank() },
        userDirsMusicDir(),
        home?.let { "$it/Music" }
    )
    for (candidate in candidates.map { File(it) }.filter { it.isDirectory }) {
        if (home != null && candidate.canonicalPath == File(home).canonicalPath) {
            // Some distros set XDG_MUSIC_DIR="$HOME[/]" with no music dir;
            // fall through to $HOME/Music instead of scanning all of home.
            val music = File(candidate, "Music")
            if (music.isDirectory) return music.absolutePath
            continue
        }
        return candidate.absolutePath
    }
    return null
}

private fun userDirsMusicDir(): String? {
    val home = System.getProperty("user.home") ?: return null
    val dirsFile = File(home, ".config/user-dirs.dirs")
    if (!dirsFile.isFile) return null
    val line = runCatching { dirsFile.readLines() }
        .getOrNull()?.firstOrNull { it.trimStart().startsWith("XDG_MUSIC_DIR=") } ?: return null
    val raw = line.substringAfter("=", "").trim().trim('"').trim('\'')
    if (raw.isBlank() || raw == "\$HOME") return null
    return raw.replace("\$HOME", home).replace("\${HOME}", home).takeIf { it.startsWith("/") }
}

private fun decodeLibraryTrack(json: JSONObject?): LibraryTrackEntity? {
    json ?: return null
    val path = json.optString("path").takeIf { it.isNotBlank() } ?: return null
    return LibraryTrackEntity(
        path = path,
        sourceId = json.optString("sourceId").takeIf { it.isNotBlank() } ?: LibraryContract.SOURCE_SCANNER,
        dedupKey = json.optString("dedupKey").takeIf { it.isNotBlank() } ?: libraryDedupKeyForPath(path),
        title = json.optString("title"),
        artist = json.optString("artist"),
        albumArtist = json.optString("albumArtist"),
        album = json.optString("album"),
        trackNo = json.optInt("trackNo", 0),
        discNo = json.optInt("discNo", 0),
        durationMs = json.optLong("durationMs", 0L),
        year = json.optInt("year", 0),
        format = json.optString("format"),
        sizeBytes = json.optLong("sizeBytes", 0L),
        mtimeMs = json.optLong("mtimeMs", 0L),
        addedAtMs = json.optLong("addedAtMs", 0L)
    )
}
