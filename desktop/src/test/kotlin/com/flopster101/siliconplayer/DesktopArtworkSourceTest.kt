package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopArtworkSupport
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers desktop artwork metadata parsing and the shared playback source label.
 *
 * Image decoding is deliberately left out: initializing Skia in the test JVM breaks
 * the native decoder tests that run in the same process.
 */
class DesktopArtworkSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val artworkBytes: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEklEQVR4nGM4ISd3Qk6OAUIBAB8mBBG4glMAAAAAAElFTkSuQmCC"
    )

    private fun flacBytes(bitDepth: Int, artwork: ByteArray?): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write("fLaC".toByteArray(Charsets.US_ASCII))
        val streamInfo = ByteArray(34)
        streamInfo[12] = ((bitDepth - 1) shr 4).toByte()
        streamInfo[13] = (((bitDepth - 1) and 0x0F) shl 4).toByte()
        stream.write(0x00)
        stream.write(byteArrayOf(0, 0, streamInfo.size.toByte()))
        stream.write(streamInfo)
        if (artwork != null) {
            val mime = "image/png".toByteArray(Charsets.US_ASCII)
            val picture = ByteArrayOutputStream()
            picture.write(ByteArray(4)) // picture type
            picture.write(byteArrayOf(0, 0, 0, mime.size.toByte()))
            picture.write(mime)
            picture.write(ByteArray(4)) // description length
            picture.write(ByteArray(16)) // width, height, color depth, colors used
            picture.write(
                byteArrayOf(
                    (artwork.size ushr 24).toByte(),
                    (artwork.size ushr 16).toByte(),
                    (artwork.size ushr 8).toByte(),
                    artwork.size.toByte()
                )
            )
            picture.write(artwork)
            val block = picture.toByteArray()
            stream.write(0x86) // last block, PICTURE
            stream.write(byteArrayOf((block.size shr 16).toByte(), (block.size shr 8).toByte(), block.size.toByte()))
            stream.write(block)
        }
        return stream.toByteArray()
    }

    /** ID3v2.3 tag holding one APIC frame with [artwork]. */
    private fun id3Bytes(artwork: ByteArray): ByteArray {
        val mime = "image/png".toByteArray(Charsets.US_ASCII)
        val payload = ByteArrayOutputStream()
        payload.write(0x00) // ISO-8859-1 text encoding
        payload.write(mime)
        payload.write(0x00)
        payload.write(0x03) // front cover
        payload.write(0x00) // empty description
        payload.write(artwork)
        val frame = payload.toByteArray()
        val stream = ByteArrayOutputStream()
        stream.write("ID3".toByteArray(Charsets.US_ASCII))
        stream.write(byteArrayOf(0x03, 0x00, 0x00))
        val tagSize = frame.size + 10
        stream.write(
            byteArrayOf(
                ((tagSize shr 21) and 0x7F).toByte(),
                ((tagSize shr 14) and 0x7F).toByte(),
                ((tagSize shr 7) and 0x7F).toByte(),
                (tagSize and 0x7F).toByte()
            )
        )
        stream.write("APIC".toByteArray(Charsets.US_ASCII))
        stream.write(
            byteArrayOf(
                (frame.size shr 24).toByte(),
                (frame.size shr 16).toByte(),
                (frame.size shr 8).toByte(),
                frame.size.toByte()
            )
        )
        stream.write(byteArrayOf(0x00, 0x00)) // flags
        stream.write(frame)
        return stream.toByteArray()
    }

    @Test
    fun readsFlacArtworkAndBitDepth() {
        val file = File(tempFolder.newFolder("flac"), "track.flac")
        file.writeBytes(flacBytes(bitDepth = 24, artwork = artworkBytes))

        assertArrayEquals(artworkBytes, DesktopArtworkSupport.extractEmbeddedArtworkBytes(file))
        assertEquals(24, DesktopArtworkSupport.extractFlacBitDepth(file))
    }

    @Test
    fun returnsNullForFlacWithoutArtwork() {
        val file = File(tempFolder.newFolder("bare"), "track.flac")
        file.writeBytes(flacBytes(bitDepth = 16, artwork = null))

        assertNull(DesktopArtworkSupport.extractEmbeddedArtworkBytes(file))
        assertEquals(16, DesktopArtworkSupport.extractFlacBitDepth(file))
    }

    @Test
    fun readsId3Artwork() {
        val file = File(tempFolder.newFolder("id3"), "track.mp3")
        file.writeBytes(id3Bytes(artworkBytes))

        assertArrayEquals(artworkBytes, DesktopArtworkSupport.extractEmbeddedArtworkBytes(file))
        assertNull(DesktopArtworkSupport.extractFlacBitDepth(file))
    }

    @Test
    fun skipsMissingAndNonAudioFiles() {
        assertNull(DesktopArtworkSupport.extractEmbeddedArtworkBytes(null))
        val text = File(tempFolder.newFolder("text"), "notes.txt")
        text.writeText("not audio")
        assertNull(DesktopArtworkSupport.extractEmbeddedArtworkBytes(text))
        assertEquals(null, DesktopArtworkSupport.extractFlacBitDepth(text))
    }

    @Test
    fun resolvesSmbLabelFromSourceId() {
        val displayFile = File("/virtual/remote/track.flac")
        assertEquals(
            "SMB (media.local/music)",
            resolvePlaybackSourceLabel(displayFile, "smb://media.local/music/track.flac")
        )
    }

    @Test
    fun marksCachedRemoteLabels() {
        val cachedFile = File(tempFolder.newFolder(REMOTE_SOURCE_CACHE_DIR), "abc_track.flac")
        assertEquals(
            "SMB (media.local/music) (cached)",
            resolvePlaybackSourceLabel(cachedFile, "smb://media.local/music/track.flac")
        )
        assertEquals(
            "Streamed (cached)",
            resolvePlaybackSourceLabel(cachedFile, "https://media.local/music/track.flac")
        )
    }

    @Test
    fun resolvesLocalLabelForPlainFiles() {
        val file = File(tempFolder.newFolder("plain"), "track.flac")
        assertEquals("Local", resolvePlaybackSourceLabel(file, file.absolutePath))
        assertNull(resolvePlaybackSourceLabel(null, file.absolutePath))
    }
}
