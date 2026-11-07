package com.flopster101.siliconplayer.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.export.AudioExportEncoder
import com.flopster101.siliconplayer.export.AudioExportPlatformAdapter
import com.flopster101.siliconplayer.export.AudioExportUiRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberAndroidAudioExportPlatformAdapter(
    context: Context,
    prefs: AppPreferences,
    toastHandler: ToastHandler
): AudioExportPlatformAdapter {
    var externalBinaryLocationState by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG, null))
    }
    var externalBinaryName by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG_NAME, null))
    }
    var destinationLocationState by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val binaryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching { importAndroidFfmpegBinary(context, uri) }
            result.onSuccess { (file, name) ->
                externalBinaryLocationState = file.absolutePath
                externalBinaryName = name
                prefs.edit()
                    .putString(AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG, file.absolutePath)
                    .putString(AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG_NAME, name)
                    .apply()
                toastHandler.showToast("Imported $name")
            }.onFailure { error ->
                toastHandler.showToast(error.message ?: "Could not import FFmpeg")
            }
        }
    }
    val destinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        destinationLocationState = uri?.toString()
    }
    return remember(binaryLauncher, destinationLauncher) {
        object : AudioExportPlatformAdapter {
            override val encoders = listOf(
                AudioExportEncoder.MediaCodec,
                AudioExportEncoder.ExternalFfmpeg
            )
            override val externalBinaryLocation: String?
                get() = externalBinaryLocationState
            override val externalBinaryLabel: String?
                get() = externalBinaryName ?: externalBinaryLocationState?.let(::File)?.name
            override val externalBinaryActionLabel = "Import"
            override val destinationLocation: String?
                get() = destinationLocationState
            override val destinationLabel: String?
                get() = destinationLocationState?.let { location ->
                    Uri.parse(location).lastPathSegment?.substringAfterLast('/') ?: location
                }

            override fun chooseExternalBinary() {
                binaryLauncher.launch(arrayOf("*/*"))
            }

            override fun chooseDestination(suggestedFileName: String) {
                destinationLauncher.launch(suggestedFileName)
            }

            override fun startExport(request: AudioExportUiRequest) {
                toastHandler.showToast("In-app audio export is not connected yet")
            }
        }
    }
}

private suspend fun importAndroidFfmpegBinary(context: Context, uri: Uri): Pair<File, String> =
    withContext(Dispatchers.IO) {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "ffmpeg"
        val directory = File(context.filesDir, "audio-export").apply { mkdirs() }
        val destination = File(directory, "ffmpeg")
        val temporary = File(directory, "ffmpeg.importing")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("Could not open the selected binary")
            if (temporary.length() == 0L) throw IllegalArgumentException("The selected binary is empty")
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            if (!destination.setExecutable(true, false) && !destination.canExecute()) {
                throw IllegalStateException("Could not make the imported binary executable")
            }
            destination to displayName
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }
