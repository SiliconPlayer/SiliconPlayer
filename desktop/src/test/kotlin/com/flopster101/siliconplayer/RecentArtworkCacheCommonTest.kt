package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecentArtworkCacheCommonTest {

    @Test
    fun nullAndBlankSourcesHaveNoKey() {
        assertNull(recentArtworkCacheKeyForSource(null))
        assertNull(recentArtworkCacheKeyForSource("   "))
        assertNull(recentLargeArtworkCacheKeyForSource(null))
    }

    @Test
    fun keyIsStableSha1Filename() {
        val first = recentArtworkCacheKeyForSource("smb://nas/music/track.flac")
        val second = recentArtworkCacheKeyForSource("smb://nas/music/track.flac")
        assertEquals(first, second)
        val key = first!!
        assertTrue(key.endsWith(".jpg"))
        assertTrue(key.dropLast(4).all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun largeKeySharesThumbBase() {
        val thumb = recentArtworkCacheKeyForSource("smb://nas/music/track.flac")!!
        val large = recentLargeArtworkCacheKeyForSource("smb://nas/music/track.flac")!!
        assertEquals(thumb.substringBeforeLast('.') + "_large.jpg", large)
    }

    @Test
    fun distinctSourcesMapToDistinctKeys() {
        val local = recentArtworkCacheKeyForSource("/music/a.flac")
        val remote = recentArtworkCacheKeyForSource("smb://nas/music/a.flac")
        assertTrue(local != remote)
    }

    @Test
    fun cacheFileResolvesOnlyExistingFiles() {
        val dir = createTempDir("recent-artwork-test")
        try {
            assertNull(recentArtworkCacheFile(dir, null))
            assertNull(recentArtworkCacheFile(dir, "  "))
            assertNull(recentArtworkCacheFile(dir, "missing.jpg"))
            val present = File(dir, "present.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertEquals(present, recentArtworkCacheFile(dir, "present.jpg"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun mergeUpdatesOnlyMatchingEntry() {
        val current = listOf(
            RecentPathEntry(path = "smb://nas/music/a.flac", locationId = null),
            RecentPathEntry(path = "smb://nas/music/b.flac", locationId = null, artworkThumbnailCacheKey = "old.jpg")
        )
        val merged = mergeRecentPlayedTrackArtworkCacheKey(current, "smb://nas/music/b.flac", "new.jpg")
        assertEquals(null, merged[0].artworkThumbnailCacheKey)
        assertEquals("new.jpg", merged[1].artworkThumbnailCacheKey)
    }

    @Test
    fun mergeIgnoresBlankKeyAndUnknownPath() {
        val current = listOf(RecentPathEntry(path = "smb://nas/music/a.flac", locationId = null))
        assertTrue(mergeRecentPlayedTrackArtworkCacheKey(current, "smb://nas/music/a.flac", "  ") === current)
        assertTrue(
            mergeRecentPlayedTrackArtworkCacheKey(current, "smb://nas/music/z.flac", "new.jpg") === current
        )
    }
}
