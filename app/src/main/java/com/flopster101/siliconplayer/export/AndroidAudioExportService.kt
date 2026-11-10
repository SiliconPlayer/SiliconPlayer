package com.flopster101.siliconplayer.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.ResultReceiver
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.CrsidOptionKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.EndFadeCurve
import com.flopster101.siliconplayer.GmeDefaults
import com.flopster101.siliconplayer.GmeOptionKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.REPEAT_CAP_LOOP_POINT
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.SidPlayFpOptionKeys
import com.flopster101.siliconplayer.UadeOptionKeys
import com.flopster101.siliconplayer.VolumeDatabase
import com.flopster101.siliconplayer.applyStoredPluginConfigurationsToNative
import com.flopster101.siliconplayer.audio.SongVolumeStore
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.defaultDspSettings
import com.flopster101.siliconplayer.audio.hasCoreDspOverrides
import com.flopster101.siliconplayer.audio.readCoreDspSettings
import com.flopster101.siliconplayer.audio.readCoreIgnoreGlobalDsp
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.audio.resolveEffectiveDspSettings
import com.flopster101.siliconplayer.hasReliableDuration
import com.flopster101.siliconplayer.platform.AndroidAppPreferences
import com.flopster101.siliconplayer.pushStoredCoreOptionsToNative
import com.flopster101.siliconplayer.pushStoredCorePreferencesToNative
import com.flopster101.siliconplayer.readPluginVolumeForDecoder
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

class AndroidAudioExportService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private var future: Future<*>? = null
    private var resultReceiver: ResultReceiver? = null
    @Volatile
    private var running = false
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NativeBridge.installContext(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Audio exports",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled.set(true)
                future?.cancel(true)
                updateNotification("Cancelling audio export…", -1)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (running) return START_NOT_STICKY
                val request = intent.readAudioExportRequest()
                resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
                cancelled.set(false)
                running = true
                startForeground(NOTIFICATION_ID, buildNotification("Exporting audio", 0))
                future = executor.submit { runExport(request) }
                return START_NOT_STICKY
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelled.set(true)
        future?.cancel(true)
        executor.shutdownNow()
        runCatching { NativeBridge.setOfflineRenderMode(false) }
        runCatching { NativeBridge.releaseCurrentDecoderNative() }
        super.onDestroy()
    }

    private fun runExport(request: AudioExportUiRequest) {
        var temporary: File? = null
        var createdMediaStoreUri: Uri? = null
        var createdLegacyFile: File? = null
        try {
            val input = request.source.filePath?.let(::File)
            require(input != null && input.isFile) { "The current track has no readable local file." }
            val prefs = AndroidAppPreferences(
                getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
            )
            NativeBridge.setOfflineRenderMode(true)
            applyStoredPluginConfigurationsToNative(prefs)
            pushStoredCoreOptionsToNative(prefs)
            pushStoredCorePreferencesToNative(prefs)
            val repeatCount = request.repeatCount.also { require(it > 0) { "Repeat count must be positive." } }
            val fadeExtensionFrames = if (
                request.renderOptions.endFadePlacement == AudioExportFadePlacement.NextLoop
            ) {
                audioExportFadeFrames(
                    request.renderOptions.endFadeDurationMs ?: 0,
                    request.renderOptions.sampleRateHz
                )
            } else {
                0L
            }
            var loopPassLimit = repeatCount
            var framesPerPass = 0L
            var metadata = AudioExportMetadata()
            fun applyRepeatMode(loopPoint: Boolean, counted: Boolean) {
                NativeBridge.setRepeatMode(
                    if (loopPoint) RepeatMode.LoopPoint.nativeValue else RepeatMode.None.nativeValue
                )
                NativeBridge.setOfflineLoopPointRepeatPasses(if (loopPoint && counted) loopPassLimit else 0)
            }
            fun loadPass(loopPoint: Boolean = false, counted: Boolean = false) {
                pushStoredAndroidTrackOptions(prefs, request.renderOptions)
                NativeBridge.stopEngineSyncNative()
                NativeBridge.loadAudio(input.absolutePath)
                if (request.source.subtuneCount > 1) {
                    check(NativeBridge.selectSubtune(request.source.subtuneIndex)) { "Subtune is unavailable." }
                }
                val decoderName = NativeBridge.getCurrentDecoderNameImpl()
                applyAndroidExportAudioSettings(
                    prefs = prefs,
                    sourcePath = input.absolutePath,
                    decoderName = decoderName,
                    volumeStore = VolumeDatabase.getInstance(this),
                    useActiveGain = request.renderOptions.useActiveGain,
                    useActiveEffects = request.renderOptions.useActiveEffects
                )
                applyRepeatMode(loopPoint, counted)
                val duration = if (request.renderOptions.durationCapSeconds != null) {
                    val cap = requireNotNull(request.renderOptions.durationCapSeconds)
                    val decodedDuration = NativeBridge.getDurationImpl()
                    if (hasReliableDuration(NativeBridge.getPlaybackCapabilities()) && decodedDuration > 0.0) {
                        minOf(cap, decodedDuration)
                    } else {
                        cap
                    }
                } else {
                    NativeBridge.getDurationImpl().takeIf {
                        hasReliableDuration(NativeBridge.getPlaybackCapabilities()) && it > 0.0
                    } ?: prefs.getInt(
                        AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS,
                        GmeDefaults.unknownDurationSeconds
                    ).toDouble()
                }
                require(duration.isFinite() && duration > 0.0) { "Could not determine track duration." }
                val frames = ceil(duration * request.renderOptions.sampleRateHz).toLong()
                if (framesPerPass == 0L) framesPerPass = frames else check(framesPerPass == frames)
                metadata = readAndroidExportMetadata(request.source)
            }
            loadPass()
            check(framesPerPass > 0L && framesPerPass <= Long.MAX_VALUE / repeatCount) { "Invalid track length." }
            val wholeTrackFrames = framesPerPass * repeatCount
            val loopPointSupported = NativeBridge.getRepeatModeCapabilities() and REPEAT_CAP_LOOP_POINT != 0
            var useLoopPoint = false
            var totalFrames = Math.addExact(wholeTrackFrames, fadeExtensionFrames)
            if (request.loopType == AudioExportLoopType.Duration) {
                val duration = requireNotNull(request.loopDurationSeconds)
                val rawFrames = ceil(duration * request.renderOptions.sampleRateHz)
                check(rawFrames > 0.0 && rawFrames <= Long.MAX_VALUE.toDouble()) { "Invalid loop duration." }
                totalFrames = Math.addExact(rawFrames.toLong(), fadeExtensionFrames)
                if (loopPointSupported && request.repeatMode == AudioExportRepeatMode.LoopPoint) {
                    useLoopPoint = true
                    applyRepeatMode(loopPoint = true, counted = false)
                }
            } else if ((repeatCount > 1 || fadeExtensionFrames > 0L) && loopPointSupported &&
                request.repeatMode == AudioExportRepeatMode.LoopPoint) {
                applyRepeatMode(loopPoint = true, counted = true)
                var probedFrames = 0L
                val samples = FloatArray(4096 * 2)
                while (probedFrames < wholeTrackFrames) {
                    if (cancelled.get() || Thread.currentThread().isInterrupted) {
                        throw CancellationException("Audio export cancelled")
                    }
                    val frames = minOf(4096L, wholeTrackFrames - probedFrames).toInt()
                    val rendered = NativeBridge.renderOfflineAudioFrames(
                        samples,
                        frames,
                        request.renderOptions.sampleRateHz
                    )
                    check(rendered >= 0) { "Offline rendering failed." }
                    if (rendered == 0) break
                    probedFrames += rendered
                    if (probedFrames >= framesPerPass && NativeBridge.getOfflineLoopPointWrapCount() == 0) break
                }
                if (probedFrames >= framesPerPass && NativeBridge.getOfflineLoopPointWrapCount() >= repeatCount) {
                    useLoopPoint = true
                    totalFrames = Math.addExact(probedFrames, fadeExtensionFrames)
                    if (fadeExtensionFrames > 0L) loopPassLimit = Math.addExact(repeatCount, 1)
                }
                loadPass(loopPoint = useLoopPoint, counted = useLoopPoint)
            }
            val outputFile = File.createTempFile("audio-export-", ".${request.container.extension}", cacheDir)
            temporary = outputFile
            val writer = when (request.encoder) {
                AudioExportEncoder.MediaCodec -> AndroidMediaCodecWriter(
                    file = outputFile,
                    sampleRateHz = request.renderOptions.sampleRateHz,
                    codecChoice = request.codec,
                    containerChoice = request.container,
                    bitrateBps = request.bitrateBps ?: 0,
                    metadata = if (request.includeMetadata) metadata else null
                )
                else -> {
                    val executable = when (request.encoder) {
                        AudioExportEncoder.SystemFfmpeg -> throw IllegalArgumentException("System FFmpeg is desktop-only")
                        AudioExportEncoder.ExternalFfmpeg -> requireNotNull(request.externalBinaryLocation)
                        AudioExportEncoder.MediaCodec -> error("Unreachable")
                    }
                    FfmpegExportWriter(
                        file = outputFile,
                        sampleRateHz = request.renderOptions.sampleRateHz,
                        executable = executable,
                        arguments = buildList {
                            if (request.includeMetadata) addAll(ffmpegMetadataArguments(metadata))
                            addAll(parseFfmpegArgumentList(request.ffmpegArguments))
                        }
                    )
                }
            }
            writer.use { output ->
                var remaining = totalFrames
                var framesLeftInPass = framesPerPass
                var lastProgressNs = 0L
                while (remaining > 0L) {
                    if (cancelled.get() || Thread.currentThread().isInterrupted) {
                        throw CancellationException("Audio export cancelled")
                    }
                    val frames = minOf(
                        4096L,
                        remaining,
                        if (useLoopPoint) remaining else framesLeftInPass
                    ).toInt()
                    val samples = FloatArray(frames * 2)
                    var rendered = NativeBridge.renderOfflineAudioFrames(
                        samples,
                        frames,
                        request.renderOptions.sampleRateHz
                    )
                    if (rendered == 0 && useLoopPoint && request.loopType == AudioExportLoopType.Duration) {
                        useLoopPoint = false
                        loadPass()
                        framesLeftInPass = framesPerPass
                        rendered = NativeBridge.renderOfflineAudioFrames(
                            samples,
                            frames,
                            request.renderOptions.sampleRateHz
                        )
                    }
                    check(rendered > 0) { "Offline rendering ended before the planned duration." }
                    applyAudioExportFade(
                        samples = samples,
                        frameOffset = totalFrames - remaining,
                        frameCount = rendered,
                        totalFrames = totalFrames,
                        fadeDurationMs = request.renderOptions.endFadeDurationMs ?: 0,
                        fadeCurve = request.renderOptions.endFadeCurve ?: EndFadeCurve.Linear,
                        sampleRateHz = request.renderOptions.sampleRateHz
                    )
                    output.write(samples, rendered)
                    remaining -= rendered
                    framesLeftInPass -= rendered
                    if (!useLoopPoint && framesLeftInPass == 0L && remaining > 0L) {
                        loadPass()
                        framesLeftInPass = framesPerPass
                    }
                    val now = System.nanoTime()
                    if (now - lastProgressNs >= 200_000_000L || remaining == 0L) {
                        reportProgress(totalFrames - remaining, totalFrames, request.renderOptions.sampleRateHz)
                        lastProgressNs = now
                    }
                }
                output.finish()
            }
            if (cancelled.get()) throw CancellationException("Audio export cancelled")
            fun writeToDestination(stream: OutputStream?) {
                if (stream == null) throw IllegalStateException("Could not open the destination document")
                stream.use { destination ->
                    outputFile.inputStream().use { source ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                                throw CancellationException("Audio export cancelled")
                            }
                            val count = source.read(buffer)
                            if (count < 0) break
                            destination.write(buffer, 0, count)
                        }
                    }
                }
            }
            val outputName = suggestedAudioExportFileName(request.source.title, request.container)
            when {
                request.destinationLocation == DESTINATION_MUSIC && Build.VERSION.SDK_INT >= 29 -> {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
                        put(MediaStore.MediaColumns.MIME_TYPE, androidExportMimeType(request.container))
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        values
                    ) ?: throw IllegalStateException("Could not create a file in Music")
                    createdMediaStoreUri = uri
                    writeToDestination(contentResolver.openOutputStream(uri, "wt"))
                    contentResolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null
                    )
                }
                request.destinationLocation == DESTINATION_MUSIC -> {
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    directory.mkdirs()
                    val file = uniqueAndroidExportFile(directory, outputName)
                    createdLegacyFile = file
                    writeToDestination(file.outputStream())
                }
                else -> {
                    writeToDestination(
                        contentResolver.openOutputStream(Uri.parse(request.destinationLocation), "wt")
                    )
                }
            }
            resultReceiver?.send(RESULT_COMPLETE, Bundle.EMPTY)
        } catch (error: Throwable) {
            createdMediaStoreUri?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }
            createdLegacyFile?.delete()
            if (error is CancellationException || cancelled.get()) {
                resultReceiver?.send(RESULT_CANCELLED, Bundle.EMPTY)
            } else {
                resultReceiver?.send(
                    RESULT_ERROR,
                    Bundle().apply { putString(EXTRA_ERROR, error.message ?: "Audio export failed") }
                )
            }
        } finally {
            temporary?.delete()
            running = false
            NativeBridge.setOfflineRenderMode(false)
            NativeBridge.releaseCurrentDecoderNative()
            stopForeground(true)
            stopSelf()
        }
    }

    private fun reportProgress(renderedFrames: Long, totalFrames: Long, sampleRateHz: Int) {
        val progress = AudioExportProgress(renderedFrames, totalFrames, sampleRateHz)
        val percent = (progress.fraction * 100.0).toInt()
        resultReceiver?.send(
            RESULT_PROGRESS,
            Bundle().apply {
                putLong(EXTRA_RENDERED_FRAMES, renderedFrames)
                putLong(EXTRA_TOTAL_FRAMES, totalFrames)
                putInt(EXTRA_SAMPLE_RATE, sampleRateHz)
            }
        )
        updateNotification("Exporting audio", percent)
    }

    private fun updateNotification(status: String, percent: Int) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status, percent))
    }

    private fun buildNotification(status: String, percent: Int): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AndroidAudioExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(status)
            .setContentText(if (percent >= 0) "$percent%" else null)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "silicon_player_audio_export"
        internal const val DESTINATION_MUSIC = "siliconplayer-music"
        private const val NOTIFICATION_ID = 1102
        private const val ACTION_START = "com.flopster101.siliconplayer.export.START"
        private const val ACTION_CANCEL = "com.flopster101.siliconplayer.export.CANCEL"
        private const val EXTRA_RESULT_RECEIVER = "resultReceiver"
        private const val EXTRA_ERROR = "error"
        private const val EXTRA_RENDERED_FRAMES = "renderedFrames"
        private const val EXTRA_TOTAL_FRAMES = "totalFrames"
        private const val EXTRA_SAMPLE_RATE = "sampleRate"
        const val RESULT_PROGRESS = 1
        const val RESULT_COMPLETE = 2
        const val RESULT_ERROR = 3
        const val RESULT_CANCELLED = 4

        internal fun start(context: Context, request: AudioExportUiRequest, receiver: ResultReceiver) {
            val intent = Intent(context, AndroidAudioExportService::class.java)
                .setAction(ACTION_START)
                .putExportRequest(request)
                .putExtra(EXTRA_RESULT_RECEIVER, receiver)
            ContextCompat.startForegroundService(context, intent)
        }

        internal fun cancel(context: Context) {
            context.startService(
                Intent(context, AndroidAudioExportService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}

internal fun uniqueAndroidExportFile(directory: File, fileName: String): File {
    val initial = File(directory, fileName)
    if (!initial.exists()) return initial
    val base = fileName.substringBeforeLast('.')
    val extension = fileName.substringAfterLast('.', "")
    var index = 2
    while (true) {
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        val candidate = File(directory, "$base ($index)$suffix")
        if (!candidate.exists()) return candidate
        index += 1
    }
}

private fun androidExportMimeType(container: AudioExportContainer): String = when (container) {
    AudioExportContainer.M4a -> "audio/mp4"
    AudioExportContainer.Adts -> "audio/aac"
    AudioExportContainer.Amr -> "audio/amr"
    AudioExportContainer.WebM -> "audio/webm"
    AudioExportContainer.ThreeGpp -> "audio/3gpp"
    AudioExportContainer.Mp3 -> "audio/mpeg"
    AudioExportContainer.Ogg -> "audio/ogg"
    AudioExportContainer.Flac -> "audio/flac"
    AudioExportContainer.Wav -> "audio/x-wav"
}

private fun pushStoredAndroidTrackOptions(
    prefs: AndroidAppPreferences,
    options: AudioExportRenderOptions
) {
    val unknownDuration = options.unknownDurationSeconds ?: prefs.getInt(
        AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS,
        GmeDefaults.unknownDurationSeconds
    )
    listOf(
        DecoderNames.GAME_MUSIC_EMU to GmeOptionKeys.UNKNOWN_DURATION_SECONDS,
        DecoderNames.LIB_SID_PLAY_FP to SidPlayFpOptionKeys.UNKNOWN_DURATION_SECONDS,
        DecoderNames.C_RSID to CrsidOptionKeys.UNKNOWN_DURATION_SECONDS,
        DecoderNames.UADE to UadeOptionKeys.UNKNOWN_DURATION_SECONDS
    ).forEach { (decoder, key) ->
        NativeBridge.setCoreOption(decoder, key, unknownDuration.toString())
    }
    NativeBridge.setEndFadeDurationMs(0)
}

private fun applyAndroidExportAudioSettings(
    prefs: AndroidAppPreferences,
    sourcePath: String,
    decoderName: String,
    volumeStore: SongVolumeStore,
    useActiveGain: Boolean,
    useActiveEffects: Boolean
) {
    NativeBridge.setMasterGain(
        if (useActiveGain) prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f) else 0f
    )
    NativeBridge.setPluginGain(
        if (useActiveGain && !volumeStore.getSongIgnoreCoreVolume(sourcePath)) {
            readPluginVolumeForDecoder(prefs, decoderName)
        } else {
            0f
        }
    )
    NativeBridge.setSongGain(if (useActiveGain) volumeStore.getSongVolume(sourcePath) ?: 0f else 0f)
    applyDspSettingsToNative(
        if (useActiveEffects) {
            resolveEffectiveDspSettings(
                coreName = decoderName,
                global = readGlobalDspSettings(prefs),
                core = readCoreDspSettings(prefs, decoderName),
                coreHasOverrides = hasCoreDspOverrides(prefs, decoderName),
                ignoreGlobalForCore = readCoreIgnoreGlobalDsp(prefs, decoderName)
            )
        } else {
            defaultDspSettings()
        }
    )
}

private fun readAndroidExportMetadata(source: AudioExportDialogSource): AudioExportMetadata {
    val subtuneTitle = if (source.subtuneCount > 1) {
        NativeBridge.getSubtuneTitle(source.subtuneIndex).trim().takeIf { it.isNotEmpty() }
    } else {
        null
    }
    val subtuneArtist = if (source.subtuneCount > 1) {
        NativeBridge.getSubtuneArtist(source.subtuneIndex).trim().takeIf { it.isNotEmpty() }
    } else {
        null
    }
    return AudioExportMetadata(
        title = subtuneTitle
            ?: source.title.trim().takeIf { it.isNotEmpty() }
            ?: NativeBridge.getTrackTitle().trim().takeIf { it.isNotEmpty() },
        artist = subtuneArtist ?: NativeBridge.getTrackArtist().trim().takeIf { it.isNotEmpty() },
        album = NativeBridge.getTrackAlbum().trim().takeIf { it.isNotEmpty() },
        composer = NativeBridge.getTrackComposer().trim().takeIf { it.isNotEmpty() },
        genre = NativeBridge.getTrackGenre().trim().takeIf { it.isNotEmpty() },
        date = NativeBridge.getTrackDate().trim().takeIf { it.isNotEmpty() }
            ?: NativeBridge.getTrackYear().trim().takeIf { it.isNotEmpty() },
        copyright = NativeBridge.getTrackCopyright().trim().takeIf { it.isNotEmpty() },
        comment = NativeBridge.getTrackComment().trim().takeIf { it.isNotEmpty() }
    )
}

private fun Intent.putExportRequest(request: AudioExportUiRequest): Intent = apply {
    putExtra("sourceId", request.source.sourceId)
    putExtra("filePath", request.source.filePath)
    putExtra("title", request.source.title)
    putExtra("artist", request.source.artist)
    putExtra("decoderName", request.source.decoderName)
    putExtra("subtuneIndex", request.source.subtuneIndex)
    putExtra("subtuneCount", request.source.subtuneCount)
    putExtra("sourceDuration", request.source.durationSeconds)
    putExtra("encoder", request.encoder.name)
    putExtra("container", request.container.name)
    putExtra("codec", request.codec.name)
    putExtra("bitrate", request.bitrateBps ?: 0)
    putExtra("ffmpegMode", request.ffmpegMode.name)
    putExtra("ffmpegArguments", request.ffmpegArguments)
    putExtra("externalBinary", request.externalBinaryLocation)
    putExtra("destination", request.destinationLocation)
    putExtra("includeMetadata", request.includeMetadata)
    putExtra("loopType", request.loopType.name)
    putExtra("repeatMode", request.repeatMode.storageValue)
    putExtra("repeat", request.repeatCount)
    putExtra("loopDuration", request.loopDurationSeconds ?: 0.0)
    putExtra("sampleRate", request.renderOptions.sampleRateHz)
    putExtra("sampleFormat", request.renderOptions.sampleFormat.storageValue)
    putExtra("durationCap", request.renderOptions.durationCapSeconds ?: 0.0)
    putExtra("unknownDuration", request.renderOptions.unknownDurationSeconds ?: 0)
    putExtra("fadeMs", request.renderOptions.endFadeDurationMs ?: -1)
    putExtra("fadeCurve", request.renderOptions.endFadeCurve?.storageValue)
    putExtra("fadePlacement", request.renderOptions.endFadePlacement.storageValue)
    putExtra("fadeAll", request.renderOptions.endFadeApplyToAllTracks ?: false)
    putExtra("hasFadeAll", request.renderOptions.endFadeApplyToAllTracks != null)
    putExtra("useGain", request.renderOptions.useActiveGain)
    putExtra("useEffects", request.renderOptions.useActiveEffects)
}

private fun Intent.readAudioExportRequest(): AudioExportUiRequest {
    return AudioExportUiRequest(
        source = AudioExportDialogSource(
            sourceId = getStringExtra("sourceId"),
            filePath = getStringExtra("filePath"),
            title = getStringExtra("title").orEmpty(),
            artist = getStringExtra("artist").orEmpty(),
            decoderName = getStringExtra("decoderName"),
            subtuneIndex = getIntExtra("subtuneIndex", 0),
            subtuneCount = getIntExtra("subtuneCount", 0),
            durationSeconds = getDoubleExtra("sourceDuration", 0.0)
        ),
        encoder = AudioExportEncoder.valueOf(getStringExtra("encoder")!!),
        container = AudioExportContainer.valueOf(getStringExtra("container")!!),
        codec = AudioExportCodec.valueOf(getStringExtra("codec")!!),
        bitrateBps = getIntExtra("bitrate", 0).takeIf { it > 0 },
        ffmpegMode = AudioExportFfmpegMode.valueOf(getStringExtra("ffmpegMode")!!),
        ffmpegArguments = getStringExtra("ffmpegArguments").orEmpty(),
        externalBinaryLocation = getStringExtra("externalBinary"),
        destinationLocation = getStringExtra("destination")!!,
        includeMetadata = getBooleanExtra("includeMetadata", true),
        loopType = AudioExportLoopType.valueOf(getStringExtra("loopType") ?: AudioExportLoopType.Repeat.name),
        repeatMode = getStringExtra("repeatMode")?.let { raw ->
            AudioExportRepeatMode.entries.first { it.storageValue == raw }
        } ?: AudioExportRepeatMode.LoopPoint,
        repeatCount = getIntExtra("repeat", 1),
        loopDurationSeconds = getDoubleExtra("loopDuration", 0.0).takeIf { it > 0.0 },
        renderOptions = AudioExportRenderOptions(
            sampleRateHz = getIntExtra("sampleRate", 48_000),
            sampleFormat = AudioExportSampleFormat.fromStorage(getStringExtra("sampleFormat"))
                ?: AudioExportSampleFormat.Pcm24,
            durationCapSeconds = getDoubleExtra("durationCap", 0.0).takeIf { it > 0.0 },
            unknownDurationSeconds = getIntExtra("unknownDuration", 0).takeIf { it > 0 },
            endFadeDurationMs = getIntExtra("fadeMs", -1).takeIf { it >= 0 },
            endFadeCurve = getStringExtra("fadeCurve")?.let(EndFadeCurve::fromStorage),
            endFadePlacement = getStringExtra("fadePlacement")?.let { raw ->
                AudioExportFadePlacement.entries.first { it.storageValue == raw }
            } ?: AudioExportFadePlacement.Tail,
            endFadeApplyToAllTracks = if (getBooleanExtra("hasFadeAll", false)) {
                getBooleanExtra("fadeAll", false)
            } else {
                null
            },
            useActiveGain = getBooleanExtra("useGain", true),
            useActiveEffects = getBooleanExtra("useEffects", true)
        )
    )
}
