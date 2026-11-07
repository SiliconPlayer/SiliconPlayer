package com.flopster101.siliconplayer.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.roundToInt

internal class AndroidMediaCodecAacWriter(
    file: File,
    private val sampleRateHz: Int,
    bitrateKbps: Int
) : AudioExportWriter {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private val pcmBuffer = ByteArray(PCM_BUFFER_BYTES)
    private var trackIndex = -1
    private var framesQueued = 0L
    private var codecStarted = false
    private var muxerStarted = false
    private var finished = false
    private var closed = false
    private var resourcesReleased = false

    init {
        require(sampleRateHz > 0) { "Sample rate must be positive." }
        require(bitrateKbps > 0) { "Bitrate must be positive." }
        try {
            val newCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = newCodec
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRateHz,
                CHANNEL_COUNT
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
            }
            newCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            newCodec.start()
            codecStarted = true
        } catch (error: Exception) {
            releaseResources()
            throw error
        }
    }

    override fun write(samples: FloatArray, frameCount: Int) {
        check(!closed && !finished) { "Cannot write after the AAC writer is finalized." }
        require(frameCount >= 0) { "Frame count must not be negative." }
        require(frameCount <= samples.size / CHANNEL_COUNT) {
            "Stereo input must contain at least frameCount * 2 samples."
        }

        var frameOffset = 0
        while (frameOffset < frameCount) {
            val codec = checkNotNull(codec)
            val inputIndex = dequeueInputBuffer()
            val inputBuffer = checkNotNull(codec.getInputBuffer(inputIndex))
            val maxFrames = minOf(
                PCM_BUFFER_BYTES / BYTES_PER_FRAME,
                inputBuffer.capacity() / BYTES_PER_FRAME
            )
            check(maxFrames > 0) { "AAC input buffer is too small for a stereo frame." }
            val chunkFrames = minOf(frameCount - frameOffset, maxFrames)
            val chunkSamples = chunkFrames * CHANNEL_COUNT
            val chunkBytes = chunkFrames * BYTES_PER_FRAME
            convertFloatsToPcm16Le(
                samples,
                frameOffset * CHANNEL_COUNT,
                chunkSamples,
                pcmBuffer
            )
            inputBuffer.clear()
            inputBuffer.put(pcmBuffer, 0, chunkBytes)
            codec.queueInputBuffer(
                inputIndex,
                0,
                chunkBytes,
                presentationTimeUs(framesQueued),
                0
            )
            framesQueued += chunkFrames
            frameOffset += chunkFrames
            drainEncoder(endOfStream = false)
        }
    }

    override fun finish() {
        if (finished) return
        check(!closed) { "Cannot finalize a closed AAC writer." }
        try {
            val codec = checkNotNull(codec)
            val inputIndex = dequeueInputBuffer()
            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs(framesQueued),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            drainEncoder(endOfStream = true)
            if (muxerStarted) {
                muxerStarted = false
                checkNotNull(muxer).stop()
            }
        } finally {
            finished = true
            releaseResources()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        releaseResources()
    }

    private fun dequeueInputBuffer(): Int {
        val codec = checkNotNull(codec)
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) return inputIndex
            drainEncoder(endOfStream = false)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = checkNotNull(codec)
        var emptyPolls = 0
        while (true) {
            val timeoutUs = if (endOfStream) DEQUEUE_TIMEOUT_US else 0L
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    emptyPolls++
                    check(emptyPolls < MAX_EOS_EMPTY_POLLS) {
                        "Timed out waiting for the AAC encoder to finish."
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "AAC output format changed after the muxer started." }
                    val muxer = checkNotNull(muxer)
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                    emptyPolls = 0
                }
                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> emptyPolls = 0
                outputIndex >= 0 -> {
                    emptyPolls = 0
                    val outputBuffer = checkNotNull(codec.getOutputBuffer(outputIndex))
                    try {
                        val isCodecConfig =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (bufferInfo.size > 0 && !isCodecConfig) {
                            check(muxerStarted) { "AAC output arrived before the output format." }
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            checkNotNull(muxer).writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun presentationTimeUs(frameOffset: Long): Long =
        frameOffset * 1_000_000L / sampleRateHz

    private fun releaseResources() {
        if (resourcesReleased) return
        resourcesReleased = true
        if (muxerStarted) {
            muxerStarted = false
            runCatching { muxer?.stop() }
        }
        runCatching { muxer?.release() }
        if (codecStarted) {
            codecStarted = false
            runCatching { codec?.stop() }
        }
        runCatching { codec?.release() }
    }

    private companion object {
        const val CHANNEL_COUNT = 2
        const val BYTES_PER_FRAME = CHANNEL_COUNT * 2
        const val PCM_BUFFER_BYTES = 16 * 1024
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val MAX_EOS_EMPTY_POLLS = 500
    }
}

internal fun convertFloatsToPcm16Le(
    samples: FloatArray,
    sampleOffset: Int,
    sampleCount: Int,
    output: ByteArray
) {
    for (index in 0 until sampleCount) {
        val sample = samples[sampleOffset + index].coerceIn(-1.0f, 1.0f)
        val scale = if (sample < 0.0f) PCM16_NEGATIVE_FULL_SCALE else PCM16_FULL_SCALE
        val value = (sample * scale).roundToInt()
        output[index * 2] = value.toByte()
        output[index * 2 + 1] = (value ushr 8).toByte()
    }
}

private const val PCM16_FULL_SCALE = 32_767.0f
private const val PCM16_NEGATIVE_FULL_SCALE = 32_768.0f
