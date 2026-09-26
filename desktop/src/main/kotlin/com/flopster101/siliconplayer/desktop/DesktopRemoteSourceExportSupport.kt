package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.CacheExportResult
import com.flopster101.siliconplayer.ExportConflictAction
import com.flopster101.siliconplayer.ExportConflictDecision
import com.flopster101.siliconplayer.ExportFileItem
import com.flopster101.siliconplayer.ExportNameConflict
import com.flopster101.siliconplayer.HttpRemoteExportRequest
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.RemoteDownloadResult
import com.flopster101.siliconplayer.RemoteExportCancelledException
import com.flopster101.siliconplayer.RemoteExportRequest
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.SmbRemoteExportRequest
import com.flopster101.siliconplayer.SmbSourceSpec
import com.flopster101.siliconplayer.buildHttpRequestUri
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource
import com.flopster101.siliconplayer.data.sha1Hex
import com.flopster101.siliconplayer.filenameFromContentDisposition
import com.flopster101.siliconplayer.httpBasicAuthorizationHeader
import com.flopster101.siliconplayer.platform.RemoteSourceExportSupport
import com.flopster101.siliconplayer.resolveCredentialedHttpSpec
import com.flopster101.siliconplayer.sanitizeRemoteLeafName
import com.flopster101.siliconplayer.stripRemoteCacheHashPrefix
import com.flopster101.siliconplayer.stripUrlFragment
import com.flopster101.siliconplayer.withAppSmbSession
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import javax.swing.JFileChooser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Desktop implementation of the shared remote browse/export contract.
 *
 * Remote sources are downloaded into the desktop cache directory before they are opened as
 * archives, playlists, or previews, and exported to a folder chosen through a Swing directory
 * chooser.
 */
@Composable
internal fun rememberDesktopRemoteSourceExportSupport(cacheDir: File): RemoteSourceExportSupport {
    return remember(cacheDir) {
        DesktopRemoteSourceExportSupport(cacheDir)
    }
}

private class DesktopRemoteSourceExportSupport(
    private val cacheDir: File
) : RemoteSourceExportSupport {

    private val cacheRoot: File
        get() = File(cacheDir, REMOTE_SOURCE_CACHE_DIR)

    override suspend fun prepareRemoteExportFile(
        request: RemoteExportRequest,
        onStatus: suspend (RemoteLoadUiState) -> Unit
    ): Result<ExportFileItem> {
        val existing = findExistingCachedFileForSource(cacheRoot, request.sourceId)
        if (existing != null) {
            existing.setLastModified(System.currentTimeMillis())
            return Result.success(existing.toExportFileItem(request.preferredFileName))
        }

        val downloaded = when (request) {
            is HttpRemoteExportRequest -> downloadHttpToCache(
                sourceId = request.sourceId,
                requestUrl = request.requestUrl,
                onStatus = onStatus
            )

            is SmbRemoteExportRequest -> downloadSmbToCache(
                sourceId = request.sourceId,
                spec = request.smbSpec,
                onStatus = onStatus
            )
        }

        val downloadedFile = downloaded.file
            ?: return Result.failure(
                if (downloaded.cancelled) {
                    RemoteExportCancelledException(downloaded.errorMessage ?: "Cancelled")
                } else {
                    IllegalStateException(downloaded.errorMessage ?: "Failed to cache remote file")
                }
            )
        downloadedFile.setLastModified(System.currentTimeMillis())
        return Result.success(downloadedFile.toExportFileItem(request.preferredFileName))
    }

    override suspend fun exportFiles(
        exportItems: List<ExportFileItem>,
        onNameConflict: (suspend (ExportNameConflict) -> ExportConflictDecision)?
    ): CacheExportResult {
        val distinctItems = exportItems.distinctBy { it.sourceFile.absolutePath }
        if (distinctItems.isEmpty()) {
            return CacheExportResult(exportedCount = 0, failedCount = 0, cancelled = true)
        }
        val destinationDirectory = withContext(Dispatchers.Main.immediate) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Destination Folder"
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else {
                null
            }
        } ?: return CacheExportResult(exportedCount = 0, failedCount = 0, cancelled = true)

        return withContext(Dispatchers.IO) {
            copyExportItems(
                destinationDirectory = destinationDirectory,
                exportItems = distinctItems,
                onNameConflict = onNameConflict
            )
        }
    }

    private suspend fun copyExportItems(
        destinationDirectory: File,
        exportItems: List<ExportFileItem>,
        onNameConflict: (suspend (ExportNameConflict) -> ExportConflictDecision)?
    ): CacheExportResult {
        if (!destinationDirectory.isDirectory) {
            return CacheExportResult(
                exportedCount = 0,
                failedCount = exportItems.size,
                invalidDestination = true
            )
        }
        var exported = 0
        var failed = 0
        var skipped = 0
        var rememberedConflictAction: ExportConflictAction? = null
        val usedNames = destinationDirectory.listFiles().orEmpty().map { it.name }.toMutableSet()

        exportItems.forEach { exportItem ->
            val sourceFile = exportItem.sourceFile
            if (!sourceFile.exists() || !sourceFile.isFile) {
                failed++
                return@forEach
            }

            val requestedName = exportItem.displayNameOverride
                ?.trim()
                .takeUnless { it.isNullOrBlank() }
                ?: sourceFile.name
            val baseName = sanitizeRemoteLeafName(requestedName)
                ?.takeUnless { it.isBlank() }
                ?: sourceFile.name
            var destinationFile = File(destinationDirectory, baseName)

            if (destinationFile.exists()) {
                if (onNameConflict == null) {
                    val autoRenameName = nextAvailableName(baseName, usedNames) ?: run {
                        failed++
                        return@forEach
                    }
                    destinationFile = File(destinationDirectory, autoRenameName)
                } else {
                    val action = rememberedConflictAction ?: run {
                        val decision = onNameConflict(ExportNameConflict(fileName = baseName))
                        if (decision.applyToAll) {
                            rememberedConflictAction = decision.action
                        }
                        decision.action
                    }
                    when (action) {
                        ExportConflictAction.Overwrite -> Unit
                        ExportConflictAction.Skip -> {
                            skipped++
                            return@forEach
                        }

                        ExportConflictAction.Cancel -> {
                            return CacheExportResult(
                                exportedCount = exported,
                                failedCount = failed,
                                skippedCount = skipped,
                                cancelled = true
                            )
                        }
                    }
                }
            }

            val copied = runCatching {
                currentCoroutineContext().ensureActive()
                destinationFile.parentFile?.mkdirs()
                sourceFile.copyTo(destinationFile, overwrite = true)
            }.isSuccess
            if (copied) {
                exported++
                usedNames += destinationFile.name
            } else {
                failed++
            }
        }

        return CacheExportResult(
            exportedCount = exported,
            failedCount = failed,
            skippedCount = skipped,
            cancelled = false
        )
    }

    private suspend fun downloadHttpToCache(
        sourceId: String,
        requestUrl: String,
        onStatus: suspend (RemoteLoadUiState) -> Unit
    ): RemoteDownloadResult {
        val resolvedSpec = resolveCredentialedHttpSpec(input = sourceId, credentialHint = requestUrl)
            ?: resolveCredentialedHttpSpec(input = requestUrl, credentialHint = requestUrl)
        val effectiveRequestUrl = resolvedSpec
            ?.let(::buildHttpRequestUri)
            ?.let(::stripUrlFragment)
            ?: requestUrl
        val safeLeaf = remoteFilenameHintForUrl(sourceId) ?: "remote"
        var target = File(cacheRoot, "${sha1Hex(sourceId)}_$safeLeaf")
        var temp = File(target.absolutePath + ".part")
        var connection: HttpURLConnection? = null

        emitRemoteStatus(
            onStatus = onStatus,
            state = RemoteLoadUiState(
                sourceId = sourceId,
                phase = RemoteLoadPhase.Connecting,
                indeterminate = true
            )
        )

        try {
            val opened = openHttpConnectionWithRedirects(effectiveRequestUrl, sourceId, requestUrl)
            connection = opened.first
            if (connection == null) {
                return RemoteDownloadResult(
                    file = null,
                    errorMessage = opened.second ?: "Connection failed"
                )
            }
            val contentDispositionName = filenameFromContentDisposition(
                connection.getHeaderField("Content-Disposition")
            )
            if (!contentDispositionName.isNullOrBlank()) {
                val resolvedTarget = File(cacheRoot, "${sha1Hex(sourceId)}_$contentDispositionName")
                if (resolvedTarget.absolutePath != target.absolutePath) {
                    target = resolvedTarget
                    temp = File(target.absolutePath + ".part")
                }
            }

            val expectedBytes = connection.contentLengthLong
            var totalBytes = 0L
            var latestBytesPerSecond: Long? = null
            val startedAtMs = System.currentTimeMillis()
            var lastSpeedSampleBytes = 0L
            var lastSpeedSampleMs = startedAtMs
            var lastUiUpdateMs = 0L

            suspend fun publishDownloadStatus(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastUiUpdateMs < 120L) return
                lastUiUpdateMs = now
                val hasKnownTotal = expectedBytes > 0L
                emitRemoteStatus(
                    onStatus = onStatus,
                    state = RemoteLoadUiState(
                        sourceId = sourceId,
                        phase = RemoteLoadPhase.Downloading,
                        downloadedBytes = totalBytes,
                        totalBytes = expectedBytes.takeIf { it > 0L },
                        bytesPerSecond = latestBytesPerSecond,
                        percent = if (hasKnownTotal) {
                            ((totalBytes * 100L) / expectedBytes).toInt().coerceIn(0, 100)
                        } else {
                            null
                        },
                        indeterminate = !hasKnownTotal
                    )
                )
            }

            publishDownloadStatus(force = true)
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalBytes += read
                        val now = System.currentTimeMillis()
                        val deltaMs = now - lastSpeedSampleMs
                        if (deltaMs >= 350L) {
                            val deltaBytes = totalBytes - lastSpeedSampleBytes
                            latestBytesPerSecond = ((deltaBytes * 1000L) / deltaMs).coerceAtLeast(0L)
                            lastSpeedSampleBytes = totalBytes
                            lastSpeedSampleMs = now
                        }
                        publishDownloadStatus()
                    }
                    output.flush()
                }
            }
            publishDownloadStatus(force = true)

            if (totalBytes <= 0L) {
                return RemoteDownloadResult(file = null, errorMessage = "Downloaded 0 bytes")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
            val avgSpeed = (totalBytes * 1000L) / elapsedMs
            emitRemoteStatus(
                onStatus = onStatus,
                state = RemoteLoadUiState(
                    sourceId = sourceId,
                    phase = RemoteLoadPhase.Downloading,
                    downloadedBytes = totalBytes,
                    totalBytes = expectedBytes.takeIf { it > 0L },
                    bytesPerSecond = latestBytesPerSecond ?: avgSpeed,
                    percent = if (expectedBytes > 0L) 100 else null,
                    indeterminate = expectedBytes <= 0L
                )
            )
            return RemoteDownloadResult(file = target)
        } catch (_: CancellationException) {
            return RemoteDownloadResult(file = null, errorMessage = "Cancelled", cancelled = true)
        } catch (t: Throwable) {
            return RemoteDownloadResult(
                file = null,
                errorMessage = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
            )
        } finally {
            connection?.disconnect()
            if (temp.exists() && target.length() <= 0L) {
                temp.delete()
            }
        }
    }

    private suspend fun openHttpConnectionWithRedirects(
        initialUrl: String,
        sourceId: String,
        credentialHint: String,
        maxRedirects: Int = 6
    ): Pair<HttpURLConnection?, String?> {
        var currentUrl = initialUrl
        repeat(maxRedirects + 1) {
            currentCoroutineContext().ensureActive()
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Desktop)")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
                setRequestProperty("Icy-MetaData", "1")
                resolveCredentialedHttpSpec(currentUrl, credentialHint)?.let { spec ->
                    httpBasicAuthorizationHeader(
                        username = spec.username,
                        password = spec.password
                    )?.let { header ->
                        setRequestProperty("Authorization", header)
                    }
                }
            }
            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    return Pair(null, "Redirect missing Location header (HTTP $responseCode)")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else if (responseCode !in 200..299) {
                connection.disconnect()
                return Pair(null, "HTTP $responseCode")
            } else {
                return Pair(connection, null)
            }
        }
        return Pair(null, "Too many redirects for $sourceId")
    }

    private suspend fun downloadSmbToCache(
        sourceId: String,
        spec: SmbSourceSpec,
        onStatus: suspend (RemoteLoadUiState) -> Unit
    ): RemoteDownloadResult {
        val remotePath = spec.path?.trim().orEmpty()
        if (remotePath.isBlank()) {
            return RemoteDownloadResult(
                file = null,
                errorMessage = "SMB share must point to a file path inside the share"
            )
        }
        val safeLeaf = sanitizeRemoteLeafName(remotePath)
            ?: sanitizeRemoteLeafName(spec.host)
            ?: "remote"
        val target = File(cacheRoot, "${sha1Hex(sourceId)}_$safeLeaf")
        val temp = File(target.absolutePath + ".part")
        emitRemoteStatus(
            onStatus = onStatus,
            state = RemoteLoadUiState(
                sourceId = sourceId,
                phase = RemoteLoadPhase.Connecting,
                indeterminate = true
            )
        )
        return try {
            withAppSmbSession(spec) { session ->
                val share = session.connectShare(spec.share)
                if (share !is DiskShare) {
                    share.close()
                    throw IllegalStateException("SMB share is not a disk share")
                }
                share.openFile(
                    remotePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                ).use { smbFile ->
                    val expectedBytes = runCatching {
                        smbFile.getFileInformation(FileStandardInformation::class.java)
                            .getEndOfFile()
                            .coerceAtLeast(0L)
                    }.getOrNull()?.takeIf { it > 0L }
                    BufferedInputStream(smbFile.inputStream).use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var totalBytes = 0L
                            var latestBytesPerSecond: Long? = null
                            val startedAtMs = System.currentTimeMillis()
                            var lastSpeedSampleBytes = 0L
                            var lastSpeedSampleMs = startedAtMs
                            var lastUiUpdateMs = 0L

                            suspend fun publishDownloadStatus(force: Boolean = false) {
                                val now = System.currentTimeMillis()
                                if (!force && now - lastUiUpdateMs < 120L) return
                                lastUiUpdateMs = now
                                emitRemoteStatus(
                                    onStatus = onStatus,
                                    state = RemoteLoadUiState(
                                        sourceId = sourceId,
                                        phase = RemoteLoadPhase.Downloading,
                                        downloadedBytes = totalBytes,
                                        totalBytes = expectedBytes,
                                        bytesPerSecond = latestBytesPerSecond,
                                        percent = expectedBytes?.let { expected ->
                                            ((totalBytes * 100L) / expected).toInt().coerceIn(0, 100)
                                        },
                                        indeterminate = expectedBytes == null
                                    )
                                )
                            }

                            publishDownloadStatus(force = true)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                totalBytes += read
                                val now = System.currentTimeMillis()
                                val deltaMs = now - lastSpeedSampleMs
                                if (deltaMs >= 350L) {
                                    val deltaBytes = totalBytes - lastSpeedSampleBytes
                                    latestBytesPerSecond = ((deltaBytes * 1000L) / deltaMs).coerceAtLeast(0L)
                                    lastSpeedSampleBytes = totalBytes
                                    lastSpeedSampleMs = now
                                }
                                publishDownloadStatus()
                            }
                            output.flush()
                            publishDownloadStatus(force = true)
                        }
                    }
                }
            }
            if (temp.length() <= 0L) {
                temp.delete()
                return RemoteDownloadResult(
                    file = null,
                    errorMessage = "Downloaded 0 bytes from SMB share"
                )
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            RemoteDownloadResult(file = target)
        } catch (_: CancellationException) {
            RemoteDownloadResult(file = null, errorMessage = "Cancelled", cancelled = true)
        } catch (t: Throwable) {
            RemoteDownloadResult(
                file = null,
                errorMessage = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
            )
        } finally {
            if (temp.exists() && target.length() <= 0L) {
                temp.delete()
            }
        }
    }

    private fun File.toExportFileItem(preferredFileName: String): ExportFileItem {
        return ExportFileItem(
            sourceFile = this,
            displayNameOverride = sanitizeRemoteLeafName(preferredFileName)
                ?: stripRemoteCacheHashPrefix(name)
        )
    }

    private suspend fun emitRemoteStatus(
        onStatus: suspend (RemoteLoadUiState) -> Unit,
        state: RemoteLoadUiState
    ) {
        withContext(Dispatchers.Main.immediate) {
            onStatus(state)
        }
    }
}

private fun nextAvailableName(
    baseName: String,
    existingNames: Collection<String>
): String? {
    if (!existingNames.contains(baseName)) return baseName
    val dotIndex = baseName.lastIndexOf('.')
    val stem = if (dotIndex > 0) baseName.substring(0, dotIndex) else baseName
    val ext = if (dotIndex > 0) baseName.substring(dotIndex) else ""
    for (attempt in 1..999) {
        val candidate = "$stem ($attempt)$ext"
        if (!existingNames.contains(candidate)) {
            return candidate
        }
    }
    return null
}

private fun remoteFilenameHintForUrl(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull()
    val fragmentHint = sanitizeRemoteLeafName(uri?.fragment)
        ?.takeIf { it.contains('.') }
    if (fragmentHint != null) return fragmentHint

    val queryHint = uri?.rawQuery
        ?.split('&')
        ?.mapNotNull { pair ->
            val key = pair.substringBefore('=')
            if (key == "filename" || key == "file" || key == "name") {
                val value = pair.substringAfter('=', "")
                runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()
            } else {
                null
            }
        }
        ?.firstNotNullOfOrNull { candidate ->
            sanitizeRemoteLeafName(candidate)?.takeIf { it.contains('.') }
        }
    if (queryHint != null) return queryHint

    val path = uri?.path?.trimEnd('/').orEmpty()
    return sanitizeRemoteLeafName(path.substringAfterLast('/', ""))
}
