package com.flopster101.siliconplayer.desktop

import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class FfmpegExportWriter(
    file: File,
    sampleRateHz: Int,
    executable: String,
    arguments: List<String>
) : AudioExportWriter {
    var frameCount = 0L
        private set

    private var finished = false
    private var closed = false
    private val byteBuffer = ByteBuffer
        .allocate(SAMPLES_PER_CHUNK * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    private val process = ProcessBuilder(
        buildList {
            add(executable)
            add("-hide_banner")
            add("-loglevel")
            add("warning")
            add("-nostdin")
            add("-y")
            add("-f")
            add("f32le")
            add("-ar")
            add(sampleRateHz.toString())
            add("-ac")
            add("2")
            add("-i")
            add("pipe:0")
            addAll(arguments)
            add(file.absolutePath)
        }
    )
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    private val input = BufferedOutputStream(process.outputStream, 64 * 1024)

    override fun write(samples: FloatArray, frameCount: Int) {
        check(!closed && !finished) { "Cannot write after the FFmpeg writer is finalized." }
        require(frameCount >= 0 && frameCount <= samples.size / 2) {
            "Stereo input must contain at least frameCount * 2 samples."
        }
        check(process.isAlive) { "FFmpeg exited before accepting all audio." }
        var sampleOffset = 0
        val sampleCount = frameCount * 2
        while (sampleOffset < sampleCount) {
            val count = minOf(SAMPLES_PER_CHUNK, sampleCount - sampleOffset)
            byteBuffer.clear()
            byteBuffer.asFloatBuffer().put(samples, sampleOffset, count)
            input.write(byteBuffer.array(), 0, count * Float.SIZE_BYTES)
            sampleOffset += count
        }
        this.frameCount += frameCount.toLong()
    }

    override fun finish() {
        if (finished) return
        check(!closed) { "Cannot finalize a closed FFmpeg writer." }
        input.close()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "FFmpeg failed with exit code $exitCode." }
        finished = true
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { input.close() }
        if (!finished) process.destroyForcibly()
    }

    private companion object {
        const val SAMPLES_PER_CHUNK = 4_096
    }
}
