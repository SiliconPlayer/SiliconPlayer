package com.flopster101.siliconplayer.library

import com.flopster101.siliconplayer.platform.LibrarySettingsSupport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Single-source settings side of DesktopLibraryRepository: the scanner row,
// folder management, and the extension/auto-scan overrides in library_scan.json.
internal class DesktopLibrarySettingsSupport(
    private val configDir: File,
    private val onStopPlayback: () -> Unit
) : LibrarySettingsSupport {
    override val supportsDeduplication: Boolean = false
    override val addFolderPlaceholder: String = "~/Music"

    override suspend fun sourceStatuses(): List<LibrarySourceStatus> = withContext(Dispatchers.IO) {
        val config = readLibraryScanConfig(configDir)
        val trackCount = if (config.scannerEnabled) readLibraryTracks(configDir).size.toLong() else 0L
        listOf(
            LibrarySourceStatus(
                id = LibraryContract.SOURCE_SCANNER,
                enabled = config.scannerEnabled,
                lastSyncMs = config.lastSyncMs,
                trackCount = trackCount
            )
        )
    }

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        if (sourceId != LibraryContract.SOURCE_SCANNER) return@withContext
        val config = readLibraryScanConfig(configDir)
        writeLibraryScanConfig(configDir, config.copy(scannerEnabled = enabled))
    }

    override fun sourceLabel(sourceId: String): String = "Storage scanner"

    override fun sourceDescription(sourceId: String): String =
        "Scans your folders directly into the library."

    override suspend fun scanRoots(): List<LibraryScanRoot> = withContext(Dispatchers.IO) {
        readLibraryScanConfig(configDir).roots
    }

    override suspend fun setScanRoots(roots: List<LibraryScanRoot>) = withContext(Dispatchers.IO) {
        val config = readLibraryScanConfig(configDir)
        writeLibraryScanConfig(configDir, config.copy(roots = roots))
    }

    override suspend fun scannerExtensions(): Set<String> = withContext(Dispatchers.IO) {
        readLibraryScanConfig(configDir).extensions ?: defaultScanExtensions()
    }

    override suspend fun setScannerExtensions(extensions: Set<String>) = withContext(Dispatchers.IO) {
        val config = readLibraryScanConfig(configDir)
        writeLibraryScanConfig(configDir, config.copy(extensions = extensions))
    }

    override suspend fun autoScanEnabled(): Boolean = withContext(Dispatchers.IO) {
        readLibraryScanConfig(configDir).autoScanEnabled
    }

    override suspend fun setAutoScanEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val config = readLibraryScanConfig(configDir)
        writeLibraryScanConfig(configDir, config.copy(autoScanEnabled = enabled))
    }

    override suspend fun deduplicateSources(): Boolean = false

    override suspend fun setDeduplicateSources(enabled: Boolean) {}

    override fun stopPlaybackForMetadataRefresh() {
        onStopPlayback()
    }
}
