package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.playback.ClearedPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMetadataStateTest {

    @Test
    fun clearedPlaybackState_hasEmptyMetadataAlbumByDefault() {
        val cleared = ClearedPlaybackState()
        assertEquals("", cleared.metadataAlbum)
    }

    @Test
    fun clearPlaybackMetadataStateAction_invokesOnMetadataAlbumChangedWithEmptyString() {
        var observedAlbum = "Previous Album"
        clearPlaybackMetadataStateAction(
            onSelectedFileChanged = {},
            onCurrentPlaybackSourceIdChanged = {},
            onDurationChanged = {},
            onPositionChanged = {},
            onIsPlayingChanged = {},
            onSeekInProgressChanged = {},
            onSeekUiBusyChanged = {},
            onSeekStartedAtMsChanged = {},
            onSeekRequestedAtMsChanged = {},
            onMetadataTitleChanged = {},
            onMetadataArtistChanged = {},
            onMetadataAlbumChanged = { observedAlbum = it },
            onMetadataSampleRateChanged = {},
            onMetadataChannelCountChanged = {},
            onMetadataBitDepthLabelChanged = {},
            onSubtuneCountChanged = {},
            onCurrentSubtuneIndexChanged = {},
            onSubtuneEntriesCleared = {},
            onShowSubtuneSelectorDialogChanged = {},
            onRepeatModeCapabilitiesFlagsChanged = {},
            onPlaybackCapabilitiesFlagsChanged = {},
            onArtworkBitmapCleared = {},
            onIgnoreCoreVolumeForSongChanged = {}
        )
        assertEquals("", observedAlbum)
    }
}
