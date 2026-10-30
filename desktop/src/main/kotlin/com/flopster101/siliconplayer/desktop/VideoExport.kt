package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.DomainStoreDirs
import com.flopster101.siliconplayer.GmeDefaults
import com.flopster101.siliconplayer.LookaheadClipperMode
import com.flopster101.siliconplayer.MultiChannelOutputMode
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeAntialiasMethod
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscTriggerMode
import com.flopster101.siliconplayer.isVisualizationModeSupported
import com.flopster101.siliconplayer.ui.visualization.OffscreenVisualizationRenderer
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.readPluginVolumeForDecoder
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val EXPORT_AUDIO_RATE = 48_000
private const val EXPORT_AUDIO_CHANNELS = 2
private const val DEFAULT_FPS = 30
private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 720

private data class ExportOptions(
    val song: File,
    val output: File,
    val fps: Int,
    val width: Int,
    val height: Int,
    val videoBitrate: String,
    val durationSeconds: Double?,
    val visualizationMode: VisualizationMode,
    val channelScopeAntialiasMethod: VisualizationChannelScopeAntialiasMethod,
    val channelScopeTriggerMode: VisualizationOscTriggerMode
)

fun main(args: Array<String>) {
    val options = parseExportOptions(args)
    require(options.song.isFile) { "Song does not exist: ${options.song}" }
    require(options.song.canonicalFile != options.output.canonicalFile) {
        "Output path must not overwrite the input song."
    }
    val overwriteOutput = options.output.exists()
    if (overwriteOutput) {
        require(options.output.isFile) { "Output path is not a file: ${options.output}" }
    }
    require(ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0) {
        "System ffmpeg was not found or failed to start."
    }
    if (overwriteOutput) {
        println("WARNING: ${options.output.absolutePath} will be overwritten in 3 seconds.")
        Thread.sleep(3_000)
    }

    DesktopPaths.install()
    DomainStoreDirs.configDir = DesktopPaths.configDir()
    val prefs = DesktopPreferencesProvider().getPreferences(AppPreferenceKeys.PREFS_NAME)
    val session = DesktopPlaybackSession()
    session.trackOptionsPrefs = prefs
    var frameRenderer: OffscreenVisualizationRenderer? = null
    val audioFile = File.createTempFile("siliconplayer-export-", ".f32le")

    try {
        NativeBridge.setMasterGain(prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f))
        NativeBridge.setForceMono(
            prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)
        )
        NativeBridge.setOutputLimiterEnabled(
            prefs.getBoolean(
                AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED,
                AppDefaults.AudioProcessing.outputLimiterEnabled
            )
        )
        NativeBridge.setLookaheadClipperMode(
            LookaheadClipperMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_LOOKAHEAD_CLIPPER_MODE,
                    AppDefaults.AudioProcessing.lookaheadClipperMode.storageValue
                )
            ).nativeValue
        )
        NativeBridge.setMultiChannelOutputMode(
            MultiChannelOutputMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.AUDIO_MULTI_CHANNEL_OUTPUT_MODE,
                    AppDefaults.OutputPipeline.multiChannelOutputMode.storageValue
                )
            ).nativeValue
        )
        pushAudioPipelineConfigToNative(prefs)
        applyDspSettingsToNative(readGlobalDspSettings(prefs))
        check(session.loadFile(options.song, autoStart = false)) {
            "Could not load song: ${options.song}"
        }
        val decoder = session.decoderName ?: error("Could not identify the selected decoder.")
        check(isVisualizationModeSupported(options.visualizationMode, decoder)) {
            "$decoder does not support ${options.visualizationMode.label} visualization."
        }
        val songVolumeStore = DesktopSongVolumeStore.getInstance()
        val ignoreCoreVolume = songVolumeStore.getSongIgnoreCoreVolume(options.song.absolutePath)
        NativeBridge.setPluginGain(if (ignoreCoreVolume) 0f else readPluginVolumeForDecoder(prefs, decoder))
        NativeBridge.setSongGain(songVolumeStore.getSongVolume(options.song.absolutePath) ?: 0f)
        NativeBridge.setRepeatMode(0)
        NativeBridge.setOfflineRenderMode(true)

        val trackDurationSeconds = if (session.hasReliableDuration && session.durationSeconds > 0.0) {
            session.durationSeconds
        } else {
            prefs.getInt(
                AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS,
                GmeDefaults.unknownDurationSeconds
            ).toDouble()
        }
        check(trackDurationSeconds.isFinite() && trackDurationSeconds > 0.0) {
            "Could not determine a positive track duration."
        }
        val durationSeconds = options.durationSeconds?.let { minOf(it, trackDurationSeconds) }
            ?: trackDurationSeconds
        val frameCount = kotlin.math.ceil(durationSeconds * options.fps).toLong()
        check(frameCount in 1..Int.MAX_VALUE.toLong()) { "Track duration is too long to export." }
        val audioFrameCount = (frameCount * EXPORT_AUDIO_RATE + options.fps - 1) / options.fps
        println("Exporting ${session.title} (${"%.2f".format(durationSeconds)}s, $frameCount frames)")

        writeAudioPass(audioFile, audioFrameCount)
        check(session.loadFile(options.song, autoStart = false)) {
            "Could not reopen song for visualization rendering."
        }
        NativeBridge.setRepeatMode(0)
        val pixelBytes = options.width * options.height * 4
        val pixels = ByteBuffer.allocateDirect(pixelBytes)
        frameRenderer = DesktopOffscreenVisualizationRendererFactory.create(
            options.visualizationMode, prefs, decoder, options.width, options.height,
            options.channelScopeAntialiasMethod,
            options.channelScopeTriggerMode
        )
        val ffmpeg = startFfmpeg(options, audioFile)
        try {
            val availableFrames = ArrayBlockingQueue<ByteArray>(3)
            repeat(3) { availableFrames.add(ByteArray(pixelBytes)) }
            val queuedFrames = ArrayBlockingQueue<ByteArray>(3)
            val endOfFrames = ByteArray(0)
            val writerFailure = AtomicReference<Throwable?>()
            val writer = Thread({
                try {
                    BufferedOutputStream(ffmpeg.outputStream, 1024 * 1024).use { video ->
                        while (true) {
                            val framePixels = queuedFrames.take()
                            if (framePixels === endOfFrames) break
                            video.write(framePixels)
                            availableFrames.put(framePixels)
                        }
                    }
                } catch (failure: Throwable) {
                    writerFailure.set(failure)
                }
            }, "video-export-ffmpeg-writer").apply {
                isDaemon = true
                start()
            }
            repeat(2) {
                check(frameRenderer!!.renderFrame(pixels, captureOutput = false)) {
                    "Could not prime offscreen frame readback."
                }
            }
            val renderStartNs = System.nanoTime()
            var intervalStartFrame = 0L
            var audioNs = 0L
            var renderNs = 0L
            var copyNs = 0L
            var pipeNs = 0L
            for (frame in 0 until frameCount.toInt()) {
                val audioStart = frame.toLong() * EXPORT_AUDIO_RATE / options.fps
                val audioEnd = (frame + 1L) * EXPORT_AUDIO_RATE / options.fps
                var stageStartNs = System.nanoTime()
                renderAudioDiscarding(audioEnd - audioStart)
                audioNs += System.nanoTime() - stageStartNs
                stageStartNs = System.nanoTime()
                check(
                    frameRenderer!!.renderFrame(pixels)
                ) { "Offscreen rendering failed at frame $frame." }
                renderNs += System.nanoTime() - stageStartNs
                stageStartNs = System.nanoTime()
                val framePixels = takeExportBuffer(availableFrames, writerFailure, ffmpeg)
                pipeNs += System.nanoTime() - stageStartNs
                stageStartNs = System.nanoTime()
                pixels.position(0)
                pixels.get(framePixels)
                copyNs += System.nanoTime() - stageStartNs
                stageStartNs = System.nanoTime()
                queueExportFrame(queuedFrames, framePixels, writerFailure, ffmpeg)
                pipeNs += System.nanoTime() - stageStartNs
                val renderedFrames = frame + 1
                if (renderedFrames % (options.fps * 5) == 0 || renderedFrames.toLong() == frameCount) {
                    printRenderProgress(
                        renderedFrames.toLong(), frameCount, renderStartNs,
                        renderedFrames - intervalStartFrame, audioNs, renderNs, copyNs, pipeNs
                    )
                    intervalStartFrame = renderedFrames.toLong()
                    audioNs = 0L
                    renderNs = 0L
                    copyNs = 0L
                    pipeNs = 0L
                }
            }
            println()
            queueExportFrame(queuedFrames, endOfFrames, writerFailure, ffmpeg)
            writer.join()
            writerFailure.get()?.let {
                throw IllegalStateException("Failed to send rendered frames to ffmpeg.", it)
            }
            check(ffmpeg.waitFor() == 0) { "ffmpeg failed while encoding the video." }
        } finally {
            if (ffmpeg.isAlive) {
                ffmpeg.destroyForcibly()
            }
        }
        println("Wrote ${options.output.absolutePath}")
    } finally {
        frameRenderer?.close()
        NativeBridge.setOfflineRenderMode(false)
        session.dispose()
        audioFile.delete()
    }
}

private fun takeExportBuffer(
    availableFrames: ArrayBlockingQueue<ByteArray>,
    writerFailure: AtomicReference<Throwable?>,
    ffmpeg: Process
): ByteArray {
    while (true) {
        writerFailure.get()?.let {
            throw IllegalStateException("Failed to send rendered frames to ffmpeg.", it)
        }
        availableFrames.poll(100, TimeUnit.MILLISECONDS)?.let { return it }
        check(ffmpeg.isAlive) { "ffmpeg exited while rendering video frames." }
    }
}

private fun queueExportFrame(
    queuedFrames: ArrayBlockingQueue<ByteArray>,
    framePixels: ByteArray,
    writerFailure: AtomicReference<Throwable?>,
    ffmpeg: Process
) {
    while (!queuedFrames.offer(framePixels, 100, TimeUnit.MILLISECONDS)) {
        writerFailure.get()?.let {
            throw IllegalStateException("Failed to send rendered frames to ffmpeg.", it)
        }
        check(ffmpeg.isAlive) { "ffmpeg exited while rendering video frames." }
    }
}

private fun printRenderProgress(
    renderedFrames: Long,
    totalFrames: Long,
    startNs: Long,
    intervalFrames: Long,
    audioNs: Long,
    renderNs: Long,
    copyNs: Long,
    pipeNs: Long
) {
    val elapsedSeconds = (System.nanoTime() - startNs) / 1_000_000_000.0
    val fps = renderedFrames / elapsedSeconds.coerceAtLeast(0.001)
    val percent = renderedFrames * 100 / totalFrames
    val intervalScale = 1.0 / (1_000_000.0 * intervalFrames.coerceAtLeast(1))
    print(
        "\rRendered $percent% ($renderedFrames/$totalFrames frames) " +
            String.format(
                Locale.ROOT,
                "%.1f FPS | audio %.1f ms, render/readback %.1f ms, copy %.1f ms, FFmpeg queue wait %.1f ms",
                fps,
                audioNs * intervalScale,
                renderNs * intervalScale,
                copyNs * intervalScale,
                pipeNs * intervalScale
            )
    )
    System.out.flush()
}

private fun writeAudioPass(file: File, frameCount: Long) {
    BufferedOutputStream(file.outputStream(), 1024 * 1024).use { output ->
        var remaining = frameCount
        while (remaining > 0) {
            val frames = minOf(4096L, remaining).toInt()
            val samples = FloatArray(frames * EXPORT_AUDIO_CHANNELS)
            check(NativeBridge.renderOfflineAudioFrames(samples, frames, EXPORT_AUDIO_RATE)) {
                "Offline audio rendering failed."
            }
            val bytes = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            bytes.asFloatBuffer().put(samples)
            output.write(bytes.array())
            remaining -= frames
        }
    }
}

private fun renderAudioDiscarding(frameCount: Long) {
    var remaining = frameCount
    while (remaining > 0) {
        val frames = minOf(4096L, remaining).toInt()
        check(
            NativeBridge.renderOfflineAudioFrames(
                FloatArray(frames * EXPORT_AUDIO_CHANNELS), frames, EXPORT_AUDIO_RATE
            )
        ) { "Offline audio rendering failed." }
        remaining -= frames
    }
}

private fun startFfmpeg(options: ExportOptions, audioFile: File): Process {
    options.output.absoluteFile.parentFile?.mkdirs()
    return ProcessBuilder(
        "ffmpeg", "-hide_banner", "-loglevel", "warning", "-y",
        "-f", "rawvideo", "-pixel_format", "rgba",
        "-video_size", "${options.width}x${options.height}",
        "-framerate", options.fps.toString(), "-i", "pipe:0",
        "-f", "f32le", "-ar", EXPORT_AUDIO_RATE.toString(), "-ac", EXPORT_AUDIO_CHANNELS.toString(),
        "-i", audioFile.absolutePath,
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "veryfast", "-b:v", options.videoBitrate,
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
        "-shortest", "-movflags", "+faststart", options.output.absolutePath
    ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
}

private fun parseExportOptions(args: Array<String>): ExportOptions {
    val values = args.toList().chunked(2).associate { pair ->
        require(pair.size == 2 && pair[0].startsWith("--")) { "Expected --option value arguments." }
        pair[0].removePrefix("--") to pair[1]
    }
    require(values.keys.all {
        it in setOf(
            "song", "output", "fps", "width", "height", "bitrate", "duration",
            "visualizer", "antialias", "trigger"
        )
    }) {
        "Unknown export option."
    }
    val song = values["song"]?.let(::expandUserPath) ?: error("Missing --song <path>.")
    val output = values["output"]?.let(::expandUserPath)
        ?: File(song.absoluteFile.parentFile, "${song.nameWithoutExtension}.mp4")
    val fps = values["fps"]?.toIntOrNull() ?: DEFAULT_FPS
    val width = values["width"]?.toIntOrNull() ?: DEFAULT_WIDTH
    val height = values["height"]?.toIntOrNull() ?: DEFAULT_HEIGHT
    val bitrate = values["bitrate"] ?: "6M"
    val duration = values["duration"]?.toDoubleOrNull()
    val visualizer = values["visualizer"]?.let(VisualizationMode::fromStorage)
        ?: VisualizationMode.ChannelScope
    val antialias = values["antialias"]?.let(VisualizationChannelScopeAntialiasMethod::fromStorage)
        ?: VisualizationChannelScopeAntialiasMethod.Fast
    val trigger = values["trigger"]?.let(VisualizationOscTriggerMode::fromStorage)
        ?: VisualizationOscTriggerMode.Rising
    require(duration == null || (duration.isFinite() && duration > 0.0)) {
        "--duration must be a positive number of seconds."
    }
    require(visualizer != VisualizationMode.Off) { "--visualizer must name a visualization mode." }
    require(fps in 1..120) { "--fps must be between 1 and 120." }
    require(width in 16..7680 && height in 16..4320) { "Invalid output dimensions." }
    require(bitrate.matches(Regex("[1-9][0-9]*[kKmM]?"))) { "Invalid --bitrate value." }
    return ExportOptions(song, output, fps, width, height, bitrate, duration, visualizer, antialias, trigger)
}

private fun expandUserPath(path: String): File {
    val home = System.getProperty("user.home")
    return when {
        path == "~" -> File(home)
        path.startsWith("~/") || path.startsWith("~\\") -> File(home, path.substring(2))
        else -> File(path)
    }
}
