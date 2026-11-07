package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.DomainStoreDirs
import com.flopster101.siliconplayer.EndFadeCurve
import com.flopster101.siliconplayer.GmeDefaults
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.applyStoredPluginConfigurationsToNative
import com.flopster101.siliconplayer.export.AudioExportMetadata
import com.flopster101.siliconplayer.export.AudioExportProgress
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.AudioExportSampleFormat
import com.flopster101.siliconplayer.pushStoredCoreOptionsToNative
import java.io.Closeable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.system.exitProcess

private const val EXPORT_CHANNELS = 2
private const val EXPORT_CHUNK_FRAMES = 256
private const val PROGRESS_INTERVAL_NS = 200_000_000L

internal interface AudioExportWriter : Closeable {
    fun write(samples: FloatArray, frameCount: Int)
    fun finish()
}

internal data class AudioExportOptions(
    val input: File,
    val output: File,
    val subtuneIndex: Int?,
    val renderOptions: AudioExportRenderOptions,
    val ffmpegPath: File?,
    val ffmpegArguments: List<String>,
    val force: Boolean
)

internal data class AudioExportResult(
    val output: File,
    val frameCount: Long,
    val elapsedSeconds: Double
)

fun main(args: Array<String>) {
    if (args.isEmpty() || args.any { it == "--help" || it == "-h" }) {
        printAudioExportHelp()
        if (args.isEmpty()) exitProcess(2) else return
    }
    try {
        val options = parseAudioExportOptions(args)
        val progressStartNs = System.nanoTime()
        val result = runAudioExport(options) { progress ->
            printAudioExportProgress(progress, options.renderOptions, System.nanoTime() - progressStartNs)
        }
        println(
            "Wrote ${result.output.absolutePath} " +
                "(${formatSeconds(result.frameCount.toDouble() / options.renderOptions.sampleRateHz)}, " +
                "${result.output.length()} bytes, ${formatSeconds(result.elapsedSeconds)} elapsed)"
        )
    } catch (e: IllegalArgumentException) {
        System.err.println("Audio export configuration error: ${e.message}")
        exitProcess(2)
    } catch (t: Throwable) {
        System.err.println("Audio export failed: ${t.message ?: t.javaClass.simpleName}")
        exitProcess(1)
    }
}

internal fun runAudioExport(
    options: AudioExportOptions,
    onProgress: (AudioExportProgress) -> Unit = {}
): AudioExportResult {
    require(options.input.isFile) { "Input does not exist or is not a file: ${options.input}" }
    val input = options.input.canonicalFile
    val output = options.output.absoluteFile
    require(input != output.canonicalFile) { "Output path must not overwrite the input file." }
    if (output.exists()) {
        require(output.isFile) { "Output path is not a file: $output" }
        require(options.force) { "Output exists; use --force to replace it: $output" }
    }
    val outputDir = output.parentFile ?: error("Output has no parent directory: $output")
    require((outputDir.isDirectory || outputDir.mkdirs()) && outputDir.isDirectory) {
        "Could not create output directory: $outputDir"
    }
    val ffmpegExecutable = resolveFfmpegExecutable(options, output)

    DesktopPaths.install()
    DomainStoreDirs.configDir = DesktopPaths.configDir()
    val prefs = DesktopPreferencesProvider().getPreferences(AppPreferenceKeys.PREFS_NAME)
    val session = DesktopPlaybackSession().apply {
        trackOptionsPrefs = prefs
        audioExportOptions = options.renderOptions
        persistLoadCrashGuard = false
    }
    val temporaryFile = AtomicReference<File?>(null)
    val shutdownHook = Thread({ temporaryFile.get()?.delete() }, "audio-export-cleanup")
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    var completed = false
    val startNs = System.nanoTime()
    try {
        NativeBridge.setOfflineRenderMode(true)
        applyStoredPluginConfigurationsToNative(prefs)
        pushStoredCoreOptionsToNative(prefs)
        pushStoredDesktopCorePreferencesToNative(prefs)
        applyOfflineAudioPreferencesToNative(prefs)
        check(session.loadFile(input, autoStart = false, initialSubtuneIndex = options.subtuneIndex)) {
            "Could not load input: $input"
        }
        if (options.subtuneIndex != null) {
            check(session.subtuneIndex == options.subtuneIndex) {
                "Subtune ${options.subtuneIndex + 1} is not available; input has ${session.subtuneCount} subtunes."
            }
        }
        val decoderName = session.decoderName ?: error("Could not identify the selected decoder.")
        applyOfflineTrackGainToNative(prefs, input.absolutePath, decoderName)
        val metadata = readDecoderMetadata(session)
        NativeBridge.setRepeatMode(RepeatMode.None.nativeValue)

        val unknownDurationSeconds = options.renderOptions.unknownDurationSeconds
            ?: prefs.getInt(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, GmeDefaults.unknownDurationSeconds)
        val entryDurationSeconds = if (session.hasReliableDuration && session.durationSeconds > 0.0) {
            session.durationSeconds
        } else {
            unknownDurationSeconds.toDouble()
        }
        val durationSeconds = options.renderOptions.durationCapSeconds?.let { cap ->
            minOf(cap, entryDurationSeconds)
        } ?: entryDurationSeconds
        check(durationSeconds.isFinite() && durationSeconds > 0.0) {
            "Could not determine a positive track duration."
        }
        val rawFrameCount = ceil(durationSeconds * options.renderOptions.sampleRateHz)
        check(rawFrameCount <= Long.MAX_VALUE.toDouble()) { "Track is too long to export." }
        val totalFrames = rawFrameCount.toLong()
        check(totalFrames > 0L) { "Track is too short to export." }
        if (ffmpegExecutable == null) {
            val bytesPerFrame = EXPORT_CHANNELS * options.renderOptions.sampleFormat.bytesPerSample
            require(totalFrames <= (0xffffffffL - 64L) / bytesPerFrame) {
                "Output exceeds the standard WAV size limit; use a shorter duration."
            }
        }
        val encoderLabel = if (ffmpegExecutable == null) {
            options.renderOptions.sampleFormat.storageValue
        } else {
            "FFmpeg ${output.extension.ifBlank { "unknown" }}"
        }
        println(
            "Exporting ${session.title} with $decoderName " +
                "(${formatSeconds(durationSeconds)}, ${options.renderOptions.sampleRateHz} Hz, " +
                "$encoderLabel, stereo)"
        )

        val tempSuffix = if (ffmpegExecutable == null) ".part" else ".part.${output.extension}"
        val temp = File.createTempFile(".siliconplayer-audio-", tempSuffix, outputDir)
        temporaryFile.set(temp)
        val exportWriter = if (ffmpegExecutable == null) {
            WavExportWriter(
                temp,
                options.renderOptions.sampleRateHz,
                options.renderOptions.sampleFormat,
                metadata
            )
        } else {
            FfmpegExportWriter(
                file = temp,
                sampleRateHz = options.renderOptions.sampleRateHz,
                executable = ffmpegExecutable,
                arguments = ffmpegOutputArguments(options, output, metadata)
            )
        }
        exportWriter.use { writer ->
            var remaining = totalFrames
            var lastProgressNs = 0L
            while (remaining > 0L) {
                if (Thread.currentThread().isInterrupted) throw CancellationException("Audio export cancelled.")
                val frames = minOf(EXPORT_CHUNK_FRAMES.toLong(), remaining).toInt()
                val samples = FloatArray(frames * EXPORT_CHANNELS)
                check(NativeBridge.renderOfflineAudioFrames(samples, frames, options.renderOptions.sampleRateHz)) {
                    "Offline audio rendering failed at frame ${totalFrames - remaining}."
                }
                writer.write(samples, frames)
                remaining -= frames
                val now = System.nanoTime()
                if (now - lastProgressNs >= PROGRESS_INTERVAL_NS || remaining == 0L) {
                    onProgress(AudioExportProgress(totalFrames - remaining, totalFrames))
                    lastProgressNs = now
                }
            }
            writer.finish()
        }
        moveCompletedExport(temp, output, options.force)
        completed = true
        return AudioExportResult(
            output = output,
            frameCount = totalFrames,
            elapsedSeconds = (System.nanoTime() - startNs) / 1_000_000_000.0
        )
    } finally {
        NativeBridge.setOfflineRenderMode(false)
        session.dispose()
        if (!completed) temporaryFile.get()?.delete()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }
}

internal fun isWavOutput(output: File): Boolean = output.extension.equals("wav", ignoreCase = true)

internal fun resolveFfmpegExecutable(
    options: AudioExportOptions,
    output: File,
    systemFfmpegAvailable: Boolean = isSystemFfmpegAvailable()
): String? {
    options.ffmpegPath?.let { executable ->
        require(executable.isFile && executable.canExecute()) {
            "FFmpeg executable is not runnable: $executable"
        }
        return executable.absolutePath
    }
    val required = !isWavOutput(output) || options.ffmpegArguments.isNotEmpty()
    require(!required || systemFfmpegAvailable) {
        "FFmpeg is required for this output; install it or use --ffmpeg <path>."
    }
    return if (systemFfmpegAvailable) "ffmpeg" else null
}

private fun isSystemFfmpegAvailable(): Boolean = runCatching {
    ProcessBuilder("ffmpeg", "-version")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor() == 0
}.getOrDefault(false)

private fun ffmpegOutputArguments(
    options: AudioExportOptions,
    output: File,
    metadata: AudioExportMetadata
): List<String> = buildList {
    if (isWavOutput(output)) {
        add("-c:a")
        add(
            when (options.renderOptions.sampleFormat) {
                AudioExportSampleFormat.Pcm16 -> "pcm_s16le"
                AudioExportSampleFormat.Pcm24 -> "pcm_s24le"
                AudioExportSampleFormat.Float32 -> "pcm_f32le"
            }
        )
        add("-rf64")
        add("auto")
    }
    addAll(ffmpegMetadataArguments(metadata))
    addAll(options.ffmpegArguments)
}

internal fun ffmpegMetadataArguments(metadata: AudioExportMetadata): List<String> = buildList {
    fun add(name: String, value: String?) {
        if (!value.isNullOrBlank()) {
            add("-metadata")
            add("$name=$value")
        }
    }
    add("title", metadata.title)
    add("artist", metadata.artist)
    add("album", metadata.album)
    add("composer", metadata.composer)
    add("genre", metadata.genre)
    add("date", metadata.date)
    add("copyright", metadata.copyright)
    add("comment", metadata.comment)
}

private fun readDecoderMetadata(session: DesktopPlaybackSession): AudioExportMetadata {
    val subtuneArtist = if (session.subtuneCount > 1) {
        NativeBridge.getSubtuneArtist(session.subtuneIndex).trim().takeIf { it.isNotEmpty() }
    } else {
        null
    }
    return AudioExportMetadata(
        title = session.title.trim().takeIf { it.isNotEmpty() },
        artist = subtuneArtist
            ?: NativeBridge.getTrackArtist().trim().takeIf { it.isNotEmpty() },
        album = session.album.trim().takeIf { it.isNotEmpty() },
        composer = NativeBridge.getTrackComposer().trim().takeIf { it.isNotEmpty() },
        genre = NativeBridge.getTrackGenre().trim().takeIf { it.isNotEmpty() },
        date = NativeBridge.getTrackDate().trim().takeIf { it.isNotEmpty() }
            ?: NativeBridge.getTrackYear().trim().takeIf { it.isNotEmpty() },
        copyright = NativeBridge.getTrackCopyright().trim().takeIf { it.isNotEmpty() },
        comment = NativeBridge.getTrackComment().trim().takeIf { it.isNotEmpty() }
    )
}

internal fun parseFfmpegArgumentList(raw: String): List<String> {
    val arguments = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false
    raw.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' && quote != '\'' -> escaped = true
            quote != null && char == quote -> quote = null
            quote != null -> current.append(char)
            char == '\'' || char == '"' -> {
                quote = char
                started = true
            }
            char.isWhitespace() -> {
                if (started || current.isNotEmpty()) {
                    arguments.add(current.toString())
                    current.clear()
                    started = false
                }
            }
            else -> current.append(char)
        }
    }
    require(!escaped && quote == null) { "--ffmpegArgs contains an unterminated quote or escape." }
    if (started || current.isNotEmpty()) arguments.add(current.toString())
    return arguments
}

internal fun moveCompletedExport(temp: File, output: File, force: Boolean) {
    if (!force) {
        try {
            Files.createLink(output.toPath(), temp.toPath())
            Files.delete(temp.toPath())
        } catch (_: UnsupportedOperationException) {
            Files.move(temp.toPath(), output.toPath())
        }
        return
    }
    try {
        Files.move(
            temp.toPath(), output.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun parseAudioExportOptions(args: Array<String>): AudioExportOptions {
    val values = mutableMapOf<String, String>()
    var force = false
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        require(argument.startsWith("--")) { "Unexpected argument: $argument" }
        val name = argument.removePrefix("--")
        if (name == "force") {
            force = true
            index++
            continue
        }
        require(name in setOf(
            "input", "output", "subtune", "sampleRate", "sampleFormat", "duration",
            "unknownDuration", "fadeMs", "fadeCurve", "fadeAll", "ffmpeg", "ffmpegArgs"
        )) { "Unknown option: --$name" }
        require(index + 1 < args.size) { "Missing value for --$name" }
        require(values.put(name, args[index + 1]) == null) { "Duplicate option: --$name" }
        index += 2
    }
    fun requiredPath(name: String): File {
        return requireNotNull(values[name]) { "Missing --$name <path>." }.let(::expandAudioExportPath)
    }
    fun intValue(name: String): Int? = values[name]?.let { raw ->
        requireNotNull(raw.toIntOrNull()) { "--$name must be an integer." }
    }
    val input = requiredPath("input")
    val output = requiredPath("output")
    val subtuneIndex = intValue("subtune")?.let { subtune ->
        require(subtune >= 1) { "--subtune must be a positive one-based index." }
        subtune - 1
    }
    val sampleFormat = values["sampleFormat"]?.let { raw ->
        requireNotNull(AudioExportSampleFormat.fromStorage(raw)) {
            "--sampleFormat must be pcm16, pcm24, or float32."
        }
    } ?: AudioExportSampleFormat.Pcm24
    val fadeAll = values["fadeAll"]?.let { raw ->
        require(raw == "true" || raw == "false") { "--fadeAll must be true or false." }
        raw.toBoolean()
    }
    val fadeCurve = values["fadeCurve"]?.let { raw ->
        requireNotNull(EndFadeCurve.entries.firstOrNull { it.storageValue == raw }) {
            "--fadeCurve must be linear, ease_in, or ease_out."
        }
    }
    val duration = values["duration"]?.let { raw ->
        requireNotNull(raw.toDoubleOrNull()) { "--duration must be a number." }
    }
    return AudioExportOptions(
        input = input,
        output = output,
        subtuneIndex = subtuneIndex,
        ffmpegPath = values["ffmpeg"]?.let(::expandAudioExportPath),
        ffmpegArguments = values["ffmpegArgs"]?.let(::parseFfmpegArgumentList).orEmpty(),
        renderOptions = AudioExportRenderOptions(
            sampleRateHz = intValue("sampleRate") ?: 48_000,
            sampleFormat = sampleFormat,
            durationCapSeconds = duration,
            unknownDurationSeconds = intValue("unknownDuration"),
            endFadeDurationMs = intValue("fadeMs"),
            endFadeCurve = fadeCurve,
            endFadeApplyToAllTracks = fadeAll
        ),
        force = force
    )
}

private fun expandAudioExportPath(path: String): File {
    val home = System.getProperty("user.home")
    return when {
        path == "~" -> File(home)
        path.startsWith("~/") || path.startsWith("~\\") -> File(home, path.substring(2))
        else -> File(path)
    }
}

private fun printAudioExportProgress(
    progress: AudioExportProgress,
    options: AudioExportRenderOptions,
    elapsedNs: Long
) {
    val renderedSeconds = progress.renderedFrames.toDouble() / options.sampleRateHz
    val totalSeconds = progress.totalFrames.toDouble() / options.sampleRateHz
    val elapsedSeconds = elapsedNs / 1_000_000_000.0
    val speed = renderedSeconds / elapsedSeconds.coerceAtLeast(0.001)
    val etaSeconds = if (speed > 0.0) (totalSeconds - renderedSeconds) / speed else 0.0
    print(
        "\rExported ${String.format(Locale.ROOT, "%6.2f", progress.fraction * 100.0)}% " +
            "(${formatSeconds(renderedSeconds)} / ${formatSeconds(totalSeconds)}), " +
            "${String.format(Locale.ROOT, "%.1f", speed)}x realtime, ETA ${formatSeconds(etaSeconds)}"
    )
    System.out.flush()
    if (progress.renderedFrames == progress.totalFrames) println()
}

private fun formatSeconds(seconds: Double): String {
    return String.format(Locale.ROOT, "%.2fs", seconds)
}

private fun printAudioExportHelp() {
    println(
        """
        Usage: ./gradlew :desktop:exportAudio -Pinput=<file> -Poutput=<wav> [options]

        Options:
          -Psubtune=<1-based index>       Export one subtune instead of the default
          -PsampleRate=<8000..192000>     Output sample rate (default 48000)
          -PsampleFormat=<format>         pcm16, pcm24 (default), or float32
          -Pduration=<seconds>            Optional positive duration cap
          -PunknownDuration=<1..86400>    Export-only unknown-duration fallback
          -PfadeMs=<0|100..120000>       Export-only fade duration; 0 disables
          -PfadeCurve=<curve>             linear, ease_in, or ease_out
          -PfadeAll=<true|false>          Apply fade to reliable-duration tracks
          -Pffmpeg=<path>                 Use a specific FFmpeg executable
          -PffmpegArgs="<arguments>"      Output codec/options, e.g. "-c:a flac"
          -Pforce=true                    Replace an existing output file
        """.trimIndent()
    )
}
