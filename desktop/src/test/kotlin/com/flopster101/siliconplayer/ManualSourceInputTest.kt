package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ManualSourceInputTest {

    private fun tempTrack(): Pair<File, File> {
        val directory = Files.createTempDirectory("siliconplayer-manual-input-").toFile()
        directory.deleteOnExit()
        val file = File(directory, "track.mp3")
        file.writeBytes(byteArrayOf(1, 2, 3))
        file.deleteOnExit()
        return directory to file
    }

    @Test
    fun resolvesLocalFilePath() {
        val (_, file) = tempTrack()
        val resolved = resolveManualSourceInput(file.absolutePath)
        assertEquals(ManualSourceType.LocalFile, resolved?.type)
        assertEquals(file.absolutePath, resolved?.localFile?.absolutePath)
    }

    @Test
    fun resolvesLocalDirectoryPath() {
        val (directory, _) = tempTrack()
        val resolved = resolveManualSourceInput(directory.absolutePath)
        assertEquals(ManualSourceType.LocalDirectory, resolved?.type)
        assertEquals(directory.absolutePath, resolved?.directoryPath)
    }

    @Test
    fun resolvesFileUriPath() {
        val (_, file) = tempTrack()
        val resolved = resolveManualSourceInput("file://${file.absolutePath}")
        assertEquals(ManualSourceType.LocalFile, resolved?.type)
        assertEquals(file.absolutePath, resolved?.localFile?.absolutePath)
    }

    @Test
    fun expandsHomeDirectoryPrefix() {
        val home = System.getProperty("user.home") ?: return
        Assume.assumeTrue(File(home).isDirectory)
        val resolved = resolveManualSourceInput("~")
        assertEquals(ManualSourceType.LocalDirectory, resolved?.type)
        assertEquals(File(home).absolutePath, resolved?.directoryPath)
    }

    @Test
    fun rejectsMissingAndBlankInput() {
        assertNull(resolveManualSourceInput(""))
        assertNull(resolveManualSourceInput("   "))
        assertNull(resolveManualSourceInput("/definitely/missing-${System.nanoTime()}.mp3"))
        assertNull(resolveManualSourceInput("file:///definitely/missing-${System.nanoTime()}.mp3"))
    }
}
