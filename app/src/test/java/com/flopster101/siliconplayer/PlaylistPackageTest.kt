package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class PlaylistPackageTest {

    @Test
    fun `playlist package round trips playlist formats metadata and cover`() {
        val cover = File.createTempFile("playlist-cover", ".bin")
        cover.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            val entry = PlaylistTrackEntry(
                id = "entry-1",
                source = "file:///music/track.mod",
                requestUrlHint = "https://example.test/track.mod",
                title = "Track",
                customTitle = "Custom track",
                artist = "Artist",
                album = "Album",
                subtuneIndex = 2,
                durationSecondsOverride = 12.5,
                addedAtMs = 1234L
            )
            val state = PlaylistLibraryState(
                playlists = listOf(
                    StoredPlaylist(
                        id = "internal",
                        title = "Internal playlist",
                        format = PlaylistStoredFormat.Internal,
                        sourceIdHint = "source",
                        entries = listOf(entry),
                        updatedAtMs = 1000L,
                        isPinned = true,
                        folderId = "folder",
                        customArtworkUri = cover.absolutePath,
                        iconTintArgb = 0xFF00FF00,
                        autoGenerateCover = false
                    ),
                    StoredPlaylist(
                        id = "m3u",
                        title = "M3U playlist",
                        format = PlaylistStoredFormat.M3u,
                        entries = listOf(entry.copy(id = "entry-2")),
                        updatedAtMs = 2000L
                    ),
                    StoredPlaylist(
                        id = "m3u8",
                        title = "M3U8 playlist",
                        format = PlaylistStoredFormat.M3u8,
                        entries = listOf(entry.copy(id = "entry-3")),
                        updatedAtMs = 3000L
                    )
                ),
                folders = listOf(
                    PlaylistFolder(
                        id = "folder",
                        title = "Backups",
                        createdAtMs = 4000L,
                        isPinned = true
                    )
                )
            )

            val restored = parsePlaylistPackage(createPlaylistPackage(state))

            assertNotNull(restored)
            assertEquals(state, restored?.state)
            assertEquals(cover.readBytes().toList(), restored?.covers?.get("internal")?.toList())
        } finally {
            cover.delete()
        }
    }
}
