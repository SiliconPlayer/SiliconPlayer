package com.flopster101.siliconplayer.platform

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.DocumentsContract
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
import com.flopster101.siliconplayer.export.AndroidAudioExportService
import com.flopster101.siliconplayer.export.AudioExportCodec
import com.flopster101.siliconplayer.export.AudioExportEncoder
import com.flopster101.siliconplayer.export.AudioExportExecutionState
import com.flopster101.siliconplayer.export.AudioExportPlatformAdapter
import com.flopster101.siliconplayer.export.AudioExportProgress
import com.flopster101.siliconplayer.export.AudioExportUiRequest
import com.flopster101.siliconplayer.export.detectAndroidMediaCodecEncoders
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
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG, null)
                ?.takeIf { File(it).isFile && File(it).canExecute() }
        )
    }
    var externalBinaryName by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG_NAME, null))
    }
    var destinationLocationState by remember {
        mutableStateOf<String?>(AndroidAudioExportService.DESTINATION_MUSIC)
    }
    var destinationLabelState by remember { mutableStateOf<String?>(null) }
    var destinationDescriptionState by remember { mutableStateOf("Music") }
    var executionStateState by remember { mutableStateOf(AudioExportExecutionState()) }
    val supportedCodecs = remember {
        detectAndroidMediaCodecEncoders().ifEmpty { setOf(AudioExportCodec.Aac) }
    }
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
        if (uri != null) {
            destinationLocationState = uri.toString()
            destinationLabelState = readAndroidDocumentName(context, uri)
            destinationDescriptionState = formatAndroidDocumentUri(context, uri)
        }
    }
    return remember(binaryLauncher, destinationLauncher) {
        object : AudioExportPlatformAdapter {
            override val encoders = listOf(
                AudioExportEncoder.MediaCodec,
                AudioExportEncoder.ExternalFfmpeg
            )
            override val supportedMediaCodecs: Set<AudioExportCodec>
                get() = supportedCodecs
            override val mediaCodecSupportsWebM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            override val executionState: AudioExportExecutionState
                get() = executionStateState
            override val externalBinaryLocation: String?
                get() = externalBinaryLocationState
            override val externalBinaryLabel: String?
                get() = if (externalBinaryLocationState == null) {
                    null
                } else {
                    externalBinaryName ?: externalBinaryLocationState?.let(::File)?.name
                }
            override val externalBinaryActionLabel = "Import"
            override val destinationLocation: String?
                get() = destinationLocationState
            override val destinationLabel: String?
                get() = destinationLabelState
            override val destinationDescription: String?
                get() = destinationDescriptionState

            override fun chooseExternalBinary() {
                binaryLauncher.launch(arrayOf("*/*"))
            }

            override fun chooseDestination(suggestedFileName: String) {
                destinationLauncher.launch(suggestedFileName)
            }

            override fun resetDestination(suggestedFileName: String) {
                destinationLocationState = AndroidAudioExportService.DESTINATION_MUSIC
                destinationLabelState = null
                destinationDescriptionState = "Music"
            }

            override fun startExport(request: AudioExportUiRequest) {
                if (executionStateState.running) return
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                        when (resultCode) {
                            AndroidAudioExportService.RESULT_PROGRESS -> {
                                executionStateState = executionStateState.copy(
                                    status = "Encoding audio",
                                    progress = AudioExportProgress(
                                        renderedFrames = resultData.getLong("renderedFrames"),
                                        totalFrames = resultData.getLong("totalFrames"),
                                        sampleRateHz = resultData.getInt("sampleRate")
                                    )
                                )
                            }
                            AndroidAudioExportService.RESULT_COMPLETE -> {
                                executionStateState = AudioExportExecutionState()
                                toastHandler.showToast("Audio export complete")
                            }
                            AndroidAudioExportService.RESULT_CANCELLED -> {
                                executionStateState = AudioExportExecutionState()
                                toastHandler.showToast("Audio export cancelled")
                            }
                            AndroidAudioExportService.RESULT_ERROR -> {
                                executionStateState = AudioExportExecutionState()
                                toastHandler.showToast(
                                    resultData.getString("error") ?: "Audio export failed"
                                )
                            }
                        }
                    }
                }
                executionStateState = AudioExportExecutionState(
                    running = true,
                    status = "Starting export…"
                )
                try {
                    AndroidAudioExportService.start(context, request, receiver)
                } catch (error: Exception) {
                    executionStateState = AudioExportExecutionState()
                    toastHandler.showToast(error.message ?: "Could not start audio export")
                }
            }

            override fun cancelExport() {
                if (!executionStateState.running) return
                executionStateState = executionStateState.copy(status = "Cancelling…")
                AndroidAudioExportService.cancel(context)
            }
        }
    }
}

private fun readAndroidDocumentName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun formatAndroidDocumentUri(context: Context, uri: Uri): String {
    val documentId = runCatching {
        when {
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.getDocumentId(uri)
            DocumentsContract.isTreeUri(uri) -> DocumentsContract.getTreeDocumentId(uri)
            else -> null
        }
    }.getOrNull()
    val decodedId = documentId?.let(Uri::decode)?.substringAfter(':')?.removePrefix("/")
    return decodedId?.takeIf { it.isNotBlank() }
        ?: readAndroidDocumentName(context, uri)
        ?: uri.lastPathSegment?.let(Uri::decode)
        ?: "Selected document"
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
