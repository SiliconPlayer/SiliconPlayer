package com.flopster101.siliconplayer.audio

/**
 * Per-song audio overrides keyed by file path: song volume in dB, whether
 * the core volume is ignored for that song, and an optional serialized
 * effect chain override. Android backs this with SQLite, desktop with a
 * JSON file; both sides share the audio-effects dialog and wiring against
 * this interface.
 */
internal interface SongVolumeStore {
    fun getSongVolume(filePath: String): Float?
    fun setSongVolume(filePath: String, volumeDb: Float)
    fun getSongIgnoreCoreVolume(filePath: String): Boolean
    fun setSongIgnoreCoreVolume(filePath: String, ignoreCoreVolume: Boolean)
    fun getSongEffectChain(filePath: String): String? = null
    fun setSongEffectChain(filePath: String, chainJson: String?) {}
    fun resetAllSongVolumes()
}
