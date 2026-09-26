package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.audio.SongVolumeStore
import org.json.JSONObject
import java.io.File

/**
 * JSON-file-backed [SongVolumeStore] living in the XDG config dir. Same
 * upsert semantics as Android's VolumeDatabase: setting one column preserves
 * the other, and absent paths read back as null/false.
 */
internal class DesktopSongVolumeStore(
    private val storeFile: File = File(DesktopPaths.configDir(), "song_volumes.json")
) : SongVolumeStore {
    companion object {
        @Volatile
        private var INSTANCE: DesktopSongVolumeStore? = null

        fun getInstance(): DesktopSongVolumeStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DesktopSongVolumeStore().also { INSTANCE = it }
            }
        }
    }

    private data class Entry(var volumeDb: Float?, var ignoreCoreVolume: Boolean)

    private val lock = Any()
    private var entries: MutableMap<String, Entry> = HashMap()

    init {
        load()
    }

    override fun getSongVolume(filePath: String): Float? = synchronized(lock) {
        entries[filePath]?.volumeDb
    }

    override fun setSongVolume(filePath: String, volumeDb: Float) = synchronized(lock) {
        val entry = entries.getOrPut(filePath) { Entry(null, false) }
        entry.volumeDb = volumeDb
        saveLocked()
    }

    override fun getSongIgnoreCoreVolume(filePath: String): Boolean = synchronized(lock) {
        entries[filePath]?.ignoreCoreVolume == true
    }

    override fun setSongIgnoreCoreVolume(filePath: String, ignoreCoreVolume: Boolean) = synchronized(lock) {
        val entry = entries.getOrPut(filePath) { Entry(null, false) }
        entry.ignoreCoreVolume = ignoreCoreVolume
        saveLocked()
    }

    override fun resetAllSongVolumes() = synchronized(lock) {
        entries = HashMap()
        saveLocked()
    }

    private fun load() = synchronized(lock) {
        val parsed = HashMap<String, Entry>()
        runCatching {
            if (!storeFile.exists()) return@runCatching
            val root = JSONObject(storeFile.readText())
            val volumes = root.optJSONObject("volumes") ?: return@runCatching
            for (key in volumes.keys()) {
                val node = volumes.optJSONObject(key) ?: continue
                parsed[key] = Entry(
                    volumeDb = if (node.has("db")) node.optDouble("db", 0.0).toFloat() else null,
                    ignoreCoreVolume = node.optBoolean("ignore", false)
                )
            }
        }
        entries = parsed
    }

    private fun saveLocked() {
        runCatching {
            storeFile.parentFile?.mkdirs()
            val volumes = JSONObject()
            for ((path, entry) in entries) {
                val node = JSONObject()
                if (entry.volumeDb != null) node.put("db", entry.volumeDb!!.toDouble())
                node.put("ignore", entry.ignoreCoreVolume)
                volumes.put(path, node)
            }
            storeFile.writeText(JSONObject().put("volumes", volumes).toString())
        }
    }
}
