package com.flopster101.siliconplayer.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.flopster101.siliconplayer.settings.porting.PortingFileIo
import com.flopster101.siliconplayer.settings.porting.PortingFileSource
import com.flopster101.siliconplayer.settings.porting.PortingFileTarget
import com.flopster101.siliconplayer.settings.porting.PortingShareRequest
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val PORTING_MIME_TYPE = "application/x-siliconplayer-settings"

/**
 * SAF-backed settings porting IO: system save/open pickers plus the standard
 * FileProvider share sheet for cross-device exports.
 */
@Composable
internal fun rememberAndroidPortingFileIo(): PortingFileIo {
    val context = LocalContext.current
    val openDocumentPicker = rememberOpenPortingSourcePicker()
    val createDocumentPicker = rememberCreatePortingTargetPicker()
    return remember(context, openDocumentPicker, createDocumentPicker) {
        object : PortingFileIo {
            override suspend fun pickExportTarget(defaultName: String): PortingFileTarget? {
                val uri = createDocumentPicker(defaultName) ?: return null
                return PortingFileTarget(uri.toString())
            }

            override suspend fun writeTarget(target: PortingFileTarget, data: ByteArray) {
                withContext(Dispatchers.IO) {
                    writePortingBytes(context, Uri.parse(target.pathOrUri), data)
                }
            }

            override suspend fun pickImportSource(): PortingFileSource? {
                val uri = openDocumentPicker(arrayOf(PORTING_MIME_TYPE, "application/octet-stream", "text/plain"))
                    ?: return null
                val name = queryPortingDisplayName(context, uri)
                val bytes = withContext(Dispatchers.IO) { readPortingBytes(context, uri) }
                if (bytes == null) {
                    toast(context, "Cannot read that file")
                    return null
                }
                return PortingFileSource(name, bytes)
            }

            override suspend fun shareExport(portingData: PortingShareRequest) {
                val contentUri = withContext(Dispatchers.IO) {
                    val shareDir = File(context.cacheDir, "shared_settings").apply { mkdirs() }
                    val shareFile = File(shareDir, portingData.fileName)
                    shareFile.writeBytes(portingData.bytes)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        shareFile
                    )
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = PORTING_MIME_TYPE
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, portingData.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share settings"))
                }
            }

            override fun toast(message: String) = toast(context, message)
        }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Suspends until the user picks a settings file, or resumes with `null` when dismissed. */
@Composable
private fun rememberOpenPortingSourcePicker(): suspend (Array<String>) -> Uri? {
    val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Uri?>?>(null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pending.getAndSet(null)?.takeIf { it.isActive }?.resume(uri)
    }
    return remember(launcher) {
        { mimeTypes ->
            suspendCancellableCoroutine { continuation ->
                pending.set(continuation)
                launcher.launch(mimeTypes)
                continuation.invokeOnCancellation { pending.compareAndSet(continuation, null) }
            }
        }
    }
}

/** Suspends until the user picks an export destination, or `null` when dismissed. */
@Composable
private fun rememberCreatePortingTargetPicker(): suspend (String) -> Uri? {
    val pending = AtomicReference<kotlinx.coroutines.CancellableContinuation<Uri?>?>(null)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PORTING_MIME_TYPE)
    ) { uri ->
        pending.getAndSet(null)?.takeIf { it.isActive }?.resume(uri)
    }
    return remember(launcher) {
        { suggestedName ->
            suspendCancellableCoroutine { continuation ->
                pending.set(continuation)
                launcher.launch(suggestedName)
                continuation.invokeOnCancellation { pending.compareAndSet(continuation, null) }
            }
        }
    }
}

private fun writePortingBytes(context: Context, uri: Uri, data: ByteArray) {
    val stream = context.contentResolver.openOutputStream(uri, "wt")
    requireNotNull(stream) { "No writable stream for $uri" }.use { it.write(data) }
}

private fun readPortingBytes(context: Context, uri: Uri): ByteArray? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

private fun queryPortingDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
