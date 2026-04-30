package com.flopster101.siliconplayer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStateLogicTest {

    @Test
    fun nativeSubtuneCursorChangeDetectsCountOrIndexDifferences() {
        val cursor = NativeSubtuneCursor(count = 2, index = 1)

        assertFalse(
            hasNativeSubtuneCursorChanged(
                nativeCursor = cursor,
                currentSubtuneCount = 2,
                currentSubtuneIndex = 1
            )
        )
        assertTrue(
            hasNativeSubtuneCursorChanged(
                nativeCursor = cursor,
                currentSubtuneCount = 3,
                currentSubtuneIndex = 1
            )
        )
        assertTrue(
            hasNativeSubtuneCursorChanged(
                nativeCursor = cursor,
                currentSubtuneCount = 2,
                currentSubtuneIndex = 0
            )
        )
    }

    @Test
    fun nativeTrackSnapshotIsValidWhenAnyMetadataFieldIsPresent() {
        assertFalse(snapshotAppearsValid(emptySnapshot()))
        assertTrue(snapshotAppearsValid(emptySnapshot(sampleRateHz = 44100)))
        assertTrue(snapshotAppearsValid(emptySnapshot(durationSeconds = 1.0)))
        assertTrue(snapshotAppearsValid(emptySnapshot(title = "Title")))
        assertTrue(snapshotAppearsValid(emptySnapshot(artist = "Artist")))
    }

    @Test
    fun metadataPollingRunsAfterDelayOrWhenTextIsBlank() {
        assertFalse(
            shouldPollTrackMetadata(
                metadataPollElapsedMs = 539L,
                metadataTitle = "Title",
                metadataArtist = "Artist"
            )
        )
        assertTrue(
            shouldPollTrackMetadata(
                metadataPollElapsedMs = 540L,
                metadataTitle = "Title",
                metadataArtist = "Artist"
            )
        )
        assertTrue(
            shouldPollTrackMetadata(
                metadataPollElapsedMs = 0L,
                metadataTitle = "",
                metadataArtist = "Artist"
            )
        )
        assertTrue(
            shouldPollTrackMetadata(
                metadataPollElapsedMs = 0L,
                metadataTitle = "Title",
                metadataArtist = ""
            )
        )
    }

    private fun emptySnapshot(
        title: String = "",
        artist: String = "",
        sampleRateHz: Int = 0,
        durationSeconds: Double = 0.0
    ): NativeTrackSnapshot {
        return NativeTrackSnapshot(
            decoderName = null,
            title = title,
            artist = artist,
            sampleRateHz = sampleRateHz,
            channelCount = 0,
            bitDepthLabel = "",
            repeatModeCapabilitiesFlags = 0,
            playbackCapabilitiesFlags = 0,
            durationSeconds = durationSeconds
        )
    }
}
