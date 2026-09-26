package com.flopster101.siliconplayer.audio

/**
 * Per-song audio overrides keyed by file path: song volume in dB and whether
 * the core volume is ignored for that song. Android backs this with SQLite,
 * desktop with a JSON file; both sides share the audio-effects dialog and
 * wiring against this interface.
 */
internal interface SongVolumeStore {
    fun getSongVolume(filePath: String): Float?
    fun setSongVolume(filePath: String, volumeDb: Float)
    fun getSongIgnoreCoreVolume(filePath: String): Boolean
    fun setSongIgnoreCoreVolume(filePath: String, ignoreCoreVolume: Boolean)
    fun resetAllSongVolumes()
}
