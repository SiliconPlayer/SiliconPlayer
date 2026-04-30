package com.flopster101.siliconplayer

fun hasNativeSubtuneCursorChanged(
    nativeCursor: NativeSubtuneCursor,
    currentSubtuneCount: Int,
    currentSubtuneIndex: Int
): Boolean {
    return nativeCursor.count != currentSubtuneCount || nativeCursor.index != currentSubtuneIndex
}

fun snapshotAppearsValid(snapshot: NativeTrackSnapshot): Boolean {
    return snapshot.sampleRateHz > 0 ||
        snapshot.durationSeconds > 0.0 ||
        snapshot.title.isNotBlank() ||
        snapshot.artist.isNotBlank()
}

fun shouldPollTrackMetadata(
    metadataPollElapsedMs: Long,
    metadataTitle: String,
    metadataArtist: String
): Boolean {
    return metadataPollElapsedMs >= 540L || metadataTitle.isBlank() || metadataArtist.isBlank()
}
