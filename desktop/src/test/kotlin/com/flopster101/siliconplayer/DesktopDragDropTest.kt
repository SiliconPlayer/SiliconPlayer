package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DroppedItemsAction
import com.flopster101.siliconplayer.desktop.resolveDroppedItemsAction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopDragDropTest {

    private val supportedExtensions = setOf("flac", "mp3")

    private fun tempDir(prefix: String): File {
        val dir = Files.createTempDirectory(prefix).toFile()
        dir.deleteOnExit()
        return dir
    }

    @Test
    fun supportedAudioFileDropsAsPlayAction() {
        val directory = tempDir("siliconplayer-drop-audio-")
        val audio = File(directory, "track.flac").apply { writeBytes(ByteArray(1)) }
        val text = File(directory, "notes.txt").apply { writeText("x") }
        assertEquals(
            DroppedItemsAction.PlayAudio(audio),
            resolveDroppedItemsAction(listOf(text.absolutePath, audio.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun audioWinsOverFolderInMixedDrop() {
        val directory = tempDir("siliconplayer-drop-mixed-")
        val folder = File(directory, "album").apply { mkdirs() }
        val audio = File(directory, "track.mp3").apply { writeBytes(ByteArray(1)) }
        assertEquals(
            DroppedItemsAction.PlayAudio(audio),
            resolveDroppedItemsAction(listOf(folder.absolutePath, audio.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun folderDropBrowsesDirectory() {
        val directory = tempDir("siliconplayer-drop-folder-")
        val folder = File(directory, "album").apply { mkdirs() }
        assertEquals(
            DroppedItemsAction.BrowseDirectory(folder),
            resolveDroppedItemsAction(listOf(folder.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun audioExtensionMatchIgnoresCase() {
        val directory = tempDir("siliconplayer-drop-case-")
        val audio = File(directory, "TRACK.FLAC").apply { writeBytes(ByteArray(1)) }
        assertEquals(
            DroppedItemsAction.PlayAudio(audio),
            resolveDroppedItemsAction(listOf(audio.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun directoryNamedLikeAudioStillBrowses() {
        val directory = tempDir("siliconplayer-drop-dirname-")
        val folder = File(directory, "album.flac").apply { mkdirs() }
        assertEquals(
            DroppedItemsAction.BrowseDirectory(folder),
            resolveDroppedItemsAction(listOf(folder.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun fileUriDropResolvesToPlayAction() {
        val directory = tempDir("siliconplayer-drop-uri-")
        val audio = File(directory, "a b track.flac").apply { writeBytes(ByteArray(1)) }
        assertEquals(
            DroppedItemsAction.PlayAudio(audio),
            resolveDroppedItemsAction(listOf(audio.toURI().toString()), supportedExtensions)
        )
    }

    @Test
    fun fileUriFolderDropBrowsesDirectory() {
        val directory = tempDir("siliconplayer-drop-uridir-")
        val folder = File(directory, "my album").apply { mkdirs() }
        assertEquals(
            DroppedItemsAction.BrowseDirectory(folder),
            resolveDroppedItemsAction(listOf(folder.toURI().toString()), supportedExtensions)
        )
    }

    @Test
    fun unsupportedFileDropsAsUnsupported() {
        val directory = tempDir("siliconplayer-drop-unsupported-")
        val text = File(directory, "notes.txt").apply { writeText("x") }
        assertEquals(
            DroppedItemsAction.Unsupported,
            resolveDroppedItemsAction(listOf(text.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun missingPathDropsAsUnsupported() {
        val directory = tempDir("siliconplayer-drop-missing-")
        val missing = File(directory, "gone.flac")
        assertEquals(
            DroppedItemsAction.Unsupported,
            resolveDroppedItemsAction(listOf(missing.absolutePath), supportedExtensions)
        )
    }

    @Test
    fun emptyDropIsUnsupported() {
        assertEquals(
            DroppedItemsAction.Unsupported,
            resolveDroppedItemsAction(emptyList(), supportedExtensions)
        )
    }
}
