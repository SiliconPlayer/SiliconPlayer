package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.export.AudioExportMetadata
import com.flopster101.siliconplayer.export.AudioExportSampleFormat
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

internal class WavExportWriter(
    file: File,
    private val sampleRateHz: Int,
    private val sampleFormat: AudioExportSampleFormat,
    metadata: AudioExportMetadata = AudioExportMetadata()
) : AudioExportWriter {
    var frameCount: Long = 0L
        private set

    private var finished = false
    private var closed = false
    private val bytesPerFrame = sampleFormat.bytesPerSample * CHANNEL_COUNT
    private val byteRate = sampleRateHz.toLong() * bytesPerFrame
    private val riffSizeWithoutData = if (sampleFormat == AudioExportSampleFormat.Float32) {
        FLOAT_RIFF_SIZE_WITHOUT_DATA
    } else {
        PCM_RIFF_SIZE_WITHOUT_DATA
    }
    private val dataSizeOffset = if (sampleFormat == AudioExportSampleFormat.Float32) {
        FLOAT_DATA_SIZE_OFFSET
    } else {
        PCM_DATA_SIZE_OFFSET
    }
    private val maxFrameCount = (UINT32_MAX - riffSizeWithoutData) / bytesPerFrame
    private val buffer = ByteArray(16 * 1024)
    private val infoMetadata = listOfNotNull(
        riffInfoField("INAM", metadata.title),
        riffInfoField("IART", metadata.artist),
        riffInfoField("IPRD", metadata.album),
        riffInfoField("IMUS", metadata.composer),
        riffInfoField("IGNR", metadata.genre),
        riffInfoField("ICRD", metadata.date),
        riffInfoField("ICOP", metadata.copyright),
        riffInfoField("ICMT", metadata.comment)
    )
    private val infoMetadataSize = if (infoMetadata.isEmpty()) {
        0L
    } else {
        12L + infoMetadata.sumOf { (_, value) -> 8L + value.size + (value.size % 2) }
    }
    private val output = run {
        require(sampleRateHz > 0) { "Sample rate must be positive." }
        require(byteRate <= UINT32_MAX) { "Sample rate is too high for a WAV byte-rate field." }
        RandomAccessFile(file, "rw")
    }

    init {
        try {
            output.setLength(0L)
            writePlaceholderHeader()
        } catch (error: Exception) {
            output.close()
            throw error
        }
    }

    override fun write(samples: FloatArray, frameCount: Int) {
        check(!closed && !finished) { "Cannot write after the WAV writer is finalized." }
        require(frameCount >= 0) { "Frame count must not be negative." }
        require(frameCount <= samples.size / CHANNEL_COUNT) {
            "Stereo input must contain at least frameCount * 2 samples."
        }
        require(frameCount.toLong() <= maxFrameCount - this.frameCount) {
            "Audio data exceeds standard WAV size limits."
        }

        var frameOffset = 0
        while (frameOffset < frameCount) {
            val chunkFrames = minOf(frameCount - frameOffset, buffer.size / bytesPerFrame)
            val chunkSamples = chunkFrames * CHANNEL_COUNT
            val chunkBytes = chunkSamples * sampleFormat.bytesPerSample
            for (sampleIndex in 0 until chunkSamples) {
                val sample = samples[frameOffset * CHANNEL_COUNT + sampleIndex]
                val offset = sampleIndex * sampleFormat.bytesPerSample
                when (sampleFormat) {
                    AudioExportSampleFormat.Pcm16 -> {
                        val normalized = sample.coerceIn(-1.0f, 1.0f)
                        val scale = if (normalized < 0.0f) PCM16_NEGATIVE_FULL_SCALE else PCM16_FULL_SCALE
                        val value = (normalized * scale).roundToInt()
                        buffer[offset] = value.toByte()
                        buffer[offset + 1] = (value ushr 8).toByte()
                    }
                    AudioExportSampleFormat.Pcm24 -> {
                        val normalized = sample.coerceIn(-1.0f, 1.0f)
                        val scale = if (normalized < 0.0f) PCM24_NEGATIVE_FULL_SCALE else PCM24_FULL_SCALE
                        val value = (normalized * scale).roundToInt()
                        buffer[offset] = value.toByte()
                        buffer[offset + 1] = (value ushr 8).toByte()
                        buffer[offset + 2] = (value ushr 16).toByte()
                    }
                    AudioExportSampleFormat.Float32 -> {
                        val bits = sample.toRawBits()
                        buffer[offset] = bits.toByte()
                        buffer[offset + 1] = (bits ushr 8).toByte()
                        buffer[offset + 2] = (bits ushr 16).toByte()
                        buffer[offset + 3] = (bits ushr 24).toByte()
                    }
                }
            }
            output.write(buffer, 0, chunkBytes)
            frameOffset += chunkFrames
            this.frameCount += chunkFrames.toLong()
        }
    }

    override fun finish() {
        if (finished) return
        check(!closed) { "Cannot finalize a closed WAV writer." }
        val dataBytes = frameCount * bytesPerFrame
        val riffSize = riffSizeWithoutData + dataBytes + infoMetadataSize
        require(riffSize <= UINT32_MAX) { "WAV metadata exceeds standard RIFF size limits." }
        output.seek(output.length())
        writeInfoMetadata()
        output.seek(RIFF_SIZE_OFFSET)
        writeUint32(riffSize)
        if (sampleFormat == AudioExportSampleFormat.Float32) {
            output.seek(FACT_SAMPLE_LENGTH_OFFSET)
            writeUint32(frameCount)
        }
        output.seek(dataSizeOffset)
        writeUint32(dataBytes)
        output.seek(output.length())
        finished = true
    }

    override fun close() {
        if (closed) return
        try {
            finish()
        } finally {
            closed = true
            output.close()
        }
    }

    private fun writeInfoMetadata() {
        if (infoMetadata.isEmpty()) return
        writeAscii("LIST")
        writeUint32(infoMetadataSize - 8L)
        writeAscii("INFO")
        infoMetadata.forEach { (id, value) ->
            writeAscii(id)
            writeUint32(value.size.toLong())
            output.write(value)
            if (value.size % 2 != 0) output.write(0)
        }
    }

    private fun writePlaceholderHeader() {
        output.seek(0L)
        writeAscii("RIFF")
        writeUint32(riffSizeWithoutData)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeUint32(FMT_CHUNK_SIZE)
        writeUint16(if (sampleFormat == AudioExportSampleFormat.Float32) IEEE_FLOAT_FORMAT_TAG else PCM_FORMAT_TAG)
        writeUint16(CHANNEL_COUNT)
        writeUint32(sampleRateHz.toLong())
        writeUint32(byteRate)
        writeUint16(bytesPerFrame)
        writeUint16(sampleFormat.bytesPerSample * 8)
        if (sampleFormat == AudioExportSampleFormat.Float32) {
            writeAscii("fact")
            writeUint32(FACT_CHUNK_SIZE)
            writeUint32(0L)
        }
        writeAscii("data")
        writeUint32(0L)
    }

    private fun writeAscii(value: String) {
        output.write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun writeUint16(value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    private fun writeUint32(value: Long) {
        for (shift in 0 until 32 step 8) {
            output.write(((value ushr shift) and 0xFF).toInt())
        }
    }

    private companion object {
        const val CHANNEL_COUNT = 2
        const val PCM_FORMAT_TAG = 1
        const val IEEE_FLOAT_FORMAT_TAG = 3
        const val FMT_CHUNK_SIZE = 16L
        const val FACT_CHUNK_SIZE = 4L
        const val PCM_RIFF_SIZE_WITHOUT_DATA = 36L
        const val FLOAT_RIFF_SIZE_WITHOUT_DATA = 48L
        const val PCM_DATA_SIZE_OFFSET = 40L
        const val FLOAT_DATA_SIZE_OFFSET = 52L
        const val RIFF_SIZE_OFFSET = 4L
        const val FACT_SAMPLE_LENGTH_OFFSET = 44L
        const val PCM16_FULL_SCALE = 32_767.0f
        const val PCM16_NEGATIVE_FULL_SCALE = 32_768.0f
        const val PCM24_FULL_SCALE = 8_388_607.0f
        const val PCM24_NEGATIVE_FULL_SCALE = 8_388_608.0f
        const val UINT32_MAX = 0xFFFF_FFFFL
    }
}

private fun riffInfoField(id: String, value: String?): Pair<String, ByteArray>? {
    val normalized = value?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return id to (normalized + "\u0000").toByteArray(Charsets.UTF_8)
}
