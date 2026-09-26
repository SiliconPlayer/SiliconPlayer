package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.library.BatchProbeOutcome
import com.flopster101.siliconplayer.library.DesktopLibraryRepository
import com.flopster101.siliconplayer.library.DesktopLibraryScanConfig
import com.flopster101.siliconplayer.library.DirectPathLister
import com.flopster101.siliconplayer.library.IsolatedLibraryProber
import com.flopster101.siliconplayer.library.IsolatedProbeResult
import com.flopster101.siliconplayer.library.LibraryContract
import com.flopster101.siliconplayer.library.LibraryScanRoot
import com.flopster101.siliconplayer.library.LibraryTrackEntity
import com.flopster101.siliconplayer.library.SCANNER_DEFAULT_EXTENSION_BLOCKLIST
import com.flopster101.siliconplayer.library.defaultScanExtensions
import com.flopster101.siliconplayer.library.libraryAlbumTracks
import com.flopster101.siliconplayer.library.libraryArtistAlbums
import com.flopster101.siliconplayer.library.libraryArtistTracks
import com.flopster101.siliconplayer.library.libraryCollections
import com.flopster101.siliconplayer.library.librarySearch
import com.flopster101.siliconplayer.library.readLibraryScanConfig
import com.flopster101.siliconplayer.library.readLibraryTracks
import com.flopster101.siliconplayer.library.writeLibraryScanConfig
import com.flopster101.siliconplayer.library.writeLibraryTracks
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopLibraryTest {

    private fun tempDir(): File =
        Files.createTempDirectory("desktop-library-test").toFile()

    // Hermetic 0.5 s 440 Hz sine WAV; never reference real user files here.
    private fun writeSineWav(file: File) {
        val rate = 22050
        val samples = rate / 2
        val data = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / rate) * 30000).toInt()
            data[i * 2] = (value and 0xFF).toByte()
            data[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        val header = ByteArray(44)
        fun putString(offset: Int, text: String) {
            text.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        }
        fun putInt(offset: Int, value: Int) {
            for (b in 0 until 4) header[offset + b] = ((value shr (8 * b)) and 0xFF).toByte()
        }
        fun putShort(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        putString(0, "RIFF")
        putInt(4, 36 + data.size)
        putString(8, "WAVE")
        putString(12, "fmt ")
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, 1)
        putInt(24, rate)
        putInt(28, rate * 2)
        putShort(32, 2)
        putShort(34, 16)
        putString(36, "data")
        putInt(40, data.size)
        file.writeBytes(header + data)
    }

    private fun resultFor(path: String) = IsolatedProbeResult(
        title = path.substringAfterLast('/').substringBeforeLast('.'),
        artist = null,
        album = null,
        durationSeconds = 1.0
    )

    private fun track(
        path: String,
        title: String = path.substringAfterLast('/').substringBeforeLast('.'),
        artist: String = "",
        albumArtist: String = "",
        album: String = "",
        trackNo: Int = 0,
        discNo: Int = 0,
        year: Int = 0,
        durationMs: Long = 0L
    ) = LibraryTrackEntity(
        path = path,
        sourceId = LibraryContract.SOURCE_SCANNER,
        dedupKey = path.lowercase(),
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        trackNo = trackNo,
        discNo = discNo,
        durationMs = durationMs,
        year = year,
        format = path.substringAfterLast('.', ""),
        sizeBytes = 1L,
        mtimeMs = 1L,
        addedAtMs = 1L
    )

    @Test
    fun collectionsGroupsAlbumsArtistsAndSorts() {
        val tracks = listOf(
            track("/m/b.mp3", title = "beta", artist = "Zebra", album = "Zulu"),
            track("/m/a.mp3", title = "alpha", artist = "apple", album = "Zulu"),
            track("/m/c.mp3", title = "solo", artist = "Solo", album = "Mix", albumArtist = "Other"),
            track("/m/e.mp3", title = "mid", artist = "Second", album = "Mix"),
            track("/m/d.mp3", title = "nope")
        )
        val collections = libraryCollections(tracks)
        assertEquals(5, collections.trackCount)
        assertEquals(listOf("Mix", "Unknown album", "Zulu"), collections.albums.map { it.name })
        assertEquals(LibraryContract.VARIOUS_ARTISTS, collections.albums[0].artist)
        assertEquals(LibraryContract.UNKNOWN_ARTIST, collections.albums[1].artist)
        assertEquals(2, collections.albums[2].trackCount)
        assertEquals(listOf("apple", "Second", "Solo", "Zebra"), collections.artists.map { it.name })
        assertEquals(
            listOf("alpha", "beta", "mid", "nope", "solo"),
            collections.tracks.map { it.title }
        )
    }

    @Test
    fun searchRespectsCapsAndBlankQuery() {
        val blank = librarySearch(listOf(track("/m/a.mp3")), "   ")
        assertTrue(blank.isEmpty)
        val many = (0 until 30).map { track("/m/a$it.mp3", title = "hit $it", album = "Album $it") }
        val results = librarySearch(many, "album")
        assertEquals(24, results.albums.size)
        assertTrue(results.artists.isEmpty())
        val tracks = (0 until 120).map { track("/m/t$it.mp3", title = "track $it") }
        assertEquals(100, librarySearch(tracks, "track").tracks.size)
    }

    @Test
    fun albumAndArtistTrackOrdering() {
        val tracks = listOf(
            track("/m/1.mp3", title = "b", album = "A", trackNo = 2),
            track("/m/2.mp3", title = "a", album = "A", trackNo = 1),
            track("/m/3.mp3", title = "x", artist = "Jane", album = "Second"),
            track("/m/4.mp3", title = "y", artist = "Jane", albumArtist = "Band", album = "First"),
            track("/m/5.mp3", title = "z", artist = "Band", albumArtist = "Band", album = "First")
        )
        assertEquals(listOf("a", "b"), libraryAlbumTracks(tracks, "A").map { it.title })
        assertEquals(listOf("y", "z"), libraryArtistTracks(tracks, "Band").map { it.title })
        assertEquals(listOf("x"), libraryArtistTracks(tracks, "Jane").map { it.title })
        val albums = libraryArtistAlbums(tracks, "Band")
        assertEquals(listOf("First"), albums.map { it.name })
        assertEquals("Band", albums[0].artist)
    }

    @Test
    fun storeRoundTripsTracksAndScanConfig() {
        val dir = tempDir()
        val tracks = listOf(
            track("/m/a.mp3", title = "A", artist = "X", album = "Y", durationMs = 1234L),
            track("/m/b.flac", title = "B")
        )
        writeLibraryTracks(dir, tracks)
        assertEquals(tracks, readLibraryTracks(dir))
        val config = DesktopLibraryScanConfig(
            roots = listOf(LibraryScanRoot("/music"), LibraryScanRoot("/other", enabled = false)),
            extensions = setOf("mp3", "flac"),
            autoScanEnabled = false,
            lastSyncMs = 42L
        )
        writeLibraryScanConfig(dir, config)
        assertEquals(config, readLibraryScanConfig(dir))
    }

    @Test
    fun corruptStoreReadsAsEmpty() {
        val dir = tempDir()
        File(dir, "library.json").writeText("{nope")
        assertTrue(readLibraryTracks(dir).isEmpty())
        writeLibraryTracks(dir, listOf(track("/m/a.mp3")))
        File(dir, "library.json").writeText("{nope")
        File(dir, "library.json.bak").writeText("[{bad]")
        assertTrue(readLibraryTracks(dir).isEmpty())
    }

    @Test
    fun defaultScanExtensionsBlocklist() {
        val extensions = defaultScanExtensions()
        assertTrue(extensions.contains("mp3"))
        assertTrue(extensions.contains("sid"))
        assertTrue(extensions.none { it in SCANNER_DEFAULT_EXTENSION_BLOCKLIST })
    }

    @Test
    fun listerSkipsHiddenEntriesWhenAsked() {
        val root = tempDir()
        File(root, ".hidden").mkdir()
        File(File(root, ".hidden"), "ghost.mp3").writeText("x")
        File(root, "real.mp3").writeText("x")
        val hidden = DirectPathLister.listFiles(LibraryScanRoot(root.absolutePath), includeHidden = false)
            .map { it.name }.toList()
        assertEquals(listOf("real.mp3"), hidden)
        assertEquals(
            2,
            DirectPathLister.listFiles(LibraryScanRoot(root.absolutePath), includeHidden = true).count()
        )
    }

    @Test
    fun proberSubdividesAroundCrashes() {
        val killer = "/m/killer.sid"
        val fakeRunner = { paths: List<String>, _: Long ->
            val killAt = paths.indexOf(killer)
            if (killAt < 0) {
                BatchProbeOutcome(paths.associateWith { resultFor(it) }, emptyList())
            } else {
                BatchProbeOutcome(
                    paths.subList(0, killAt).associateWith { resultFor(it) },
                    paths.subList(killAt, paths.size)
                )
            }
        }
        val prober = IsolatedLibraryProber(batchSize = 4, runBatch = fakeRunner)
        val paths = listOf("/m/a.mp3", "/m/b.mp3", killer, "/m/c.mp3", "/m/d.mp3")
        val results = prober.probeAll(paths)
        assertEquals(setOf("/m/a.mp3", "/m/b.mp3", "/m/c.mp3", "/m/d.mp3"), results.keys)
        assertEquals("a", results["/m/a.mp3"]?.title)
    }

    @Test
    fun proberSkipsEverythingOnSpawnFailure() {
        val prober = IsolatedLibraryProber(runBatch = { _, _ -> null })
        assertTrue(prober.probeAll(listOf("/m/a.mp3", "/m/b.mp3")).isEmpty())
    }

    @Test
    fun scanIndexesProbeAndPrunes() = runBlocking {
        val configDir = tempDir()
        val musicDir = File(tempDir(), "music").apply { mkdirs() }
        writeSineWav(File(musicDir, "tone.wav"))
        File(musicDir, "notes.md").writeText("# readme")
        File(musicDir, "doc.psc").writeText("plain text, not music")
        File(File(musicDir, ".cache").apply { mkdir() }, "cached.mp3").writeText("x")
        writeLibraryScanConfig(
            configDir,
            DesktopLibraryScanConfig(roots = listOf(LibraryScanRoot(musicDir.absolutePath)))
        )
        val repository = DesktopLibraryRepository(configDir)
        repository.requestScan()
        withTimeout(60_000) {
            while (repository.collections().trackCount != 1) delay(200)
        }
        val collections = repository.collections()
        val indexed = collections.tracks.single()
        assertEquals("tone.wav", indexed.path.substringAfterLast('/'))
        assertEquals("tone", indexed.title)
        assertEquals("WAV", indexed.format)
        assertTrue(indexed.durationMs in 400L..600L)
        assertTrue(repository.search("tone").tracks.isNotEmpty())
        File(musicDir, "tone.wav").delete()
        repository.requestScan()
        withTimeout(60_000) {
            while (repository.collections().trackCount != 0) delay(200)
        }
        assertTrue(readLibraryScanConfig(configDir).lastSyncMs > 0L)
    }
}
