package com.flopster101.siliconplayer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceArtworkCacheTest {

    private fun createSourceCacheRoot(): File {
        val cacheDir = createTempDir("source-artwork-test")
        return File(cacheDir, REMOTE_SOURCE_CACHE_DIR)
    }

    private fun File.cleanUp() {
        parentFile?.deleteRecursively() ?: deleteRecursively()
    }

    @Test
    fun nullAndBlankSourcesHaveNoKey() {
        assertNull(remoteSourceArtworkCacheKey(null))
        assertNull(remoteSourceArtworkCacheKey("   "))
    }

    @Test
    fun keyIsStableSha1Filename() {
        val first = remoteSourceArtworkCacheKey("smb://nas/music/track.flac")
        assertEquals(first, remoteSourceArtworkCacheKey("smb://nas/music/track.flac"))
        val key = first!!
        assertTrue(key.endsWith(".artwork"))
        assertTrue(key.dropLast(".artwork".length).all { it.isDigit() || it in 'a'..'f' })
        assertNull(cachedSourceArtworkFile(createSourceCacheRoot(), null))
    }

    @Test
    fun subtuneSuffixSharesTheSourceKey() {
        val plain = remoteSourceArtworkCacheKey("smb://nas/music/track.flac")
        val subtune = remoteSourceArtworkCacheKey("smb://nas/music/track.flac#subtune=3")
        assertEquals(plain, subtune)
    }

    @Test
    fun artworkDirectorySitsBesideTheSourceCache() {
        val cacheDir = createTempDir("source-artwork-dir-test")
        try {
            val artworkDir = remoteSourceArtworkCacheDir(File(cacheDir, REMOTE_SOURCE_CACHE_DIR))
            assertEquals(cacheDir, artworkDir.parentFile)
            assertEquals(REMOTE_SOURCE_ARTWORK_CACHE_DIR, artworkDir.name)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun writeThenReadRoundTripsBytes() {
        val sourceRoot = createSourceCacheRoot()
        try {
            val sourceId = "smb://nas/music/track.flac"
            assertNull(cachedSourceArtworkFile(sourceRoot, sourceId))
            val bytes = byteArrayOf(1, 2, 3, 4)
            writeCachedSourceArtwork(sourceRoot, sourceId, bytes)
            val cached = cachedSourceArtworkFile(sourceRoot, sourceId)
            assertNotNull(cached)
            assertArrayEquals(bytes, cached!!.readBytes())
        } finally {
            sourceRoot.cleanUp()
        }
    }

    @Test
    fun writeReplacesExistingArtwork() {
        val sourceRoot = createSourceCacheRoot()
        try {
            val sourceId = "smb://nas/music/track.flac"
            writeCachedSourceArtwork(sourceRoot, sourceId, byteArrayOf(1, 2, 3))
            writeCachedSourceArtwork(sourceRoot, sourceId, byteArrayOf(9, 9))
            assertArrayEquals(byteArrayOf(9, 9), cachedSourceArtworkFile(sourceRoot, sourceId)!!.readBytes())
        } finally {
            sourceRoot.cleanUp()
        }
    }

    @Test
    fun emptyAndOversizedArtworkIsRejected() {
        val sourceRoot = createSourceCacheRoot()
        try {
            val sourceId = "smb://nas/music/track.flac"
            assertNull(writeCachedSourceArtwork(sourceRoot, sourceId, null))
            assertNull(writeCachedSourceArtwork(sourceRoot, sourceId, ByteArray(0)))
            assertNull(
                writeCachedSourceArtwork(
                    sourceRoot,
                    sourceId,
                    ByteArray(MAX_CACHED_SOURCE_ARTWORK_BYTES + 1)
                )
            )
            assertNull(cachedSourceArtworkFile(sourceRoot, sourceId))
        } finally {
            sourceRoot.cleanUp()
        }
    }

    @Test
    fun deleteRemovesArtworkAndReportsFreedBytes() {
        val sourceRoot = createSourceCacheRoot()
        try {
            val sourceId = "smb://nas/music/track.flac"
            assertEquals(0L, deleteCachedSourceArtwork(sourceRoot, sourceId))
            writeCachedSourceArtwork(sourceRoot, sourceId, ByteArray(512) { 7 })
            assertEquals(512L, deleteCachedSourceArtwork(sourceRoot, sourceId))
            assertNull(cachedSourceArtworkFile(sourceRoot, sourceId))
        } finally {
            sourceRoot.cleanUp()
        }
    }

    @Test
    fun distinctSourcesDoNotShareArtwork() {
        val sourceRoot = createSourceCacheRoot()
        try {
            writeCachedSourceArtwork(sourceRoot, "smb://nas/music/a.flac", byteArrayOf(1))
            assertNull(cachedSourceArtworkFile(sourceRoot, "smb://nas/music/b.flac"))
            assertNull(cachedSourceArtworkFile(sourceRoot, "/music/a.flac"))
        } finally {
            sourceRoot.cleanUp()
        }
    }
}
