package com.flopster101.siliconplayer.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal class AndroidMediaCodecWriter(
    private val file: File,
    private val sampleRateHz: Int,
    private val codecChoice: AudioExportCodec,
    containerChoice: AudioExportContainer,
    bitrateBps: Int,
    metadata: AudioExportMetadata?
) : AudioExportWriter {
    private var codec: MediaCodec? = null
    private var sink: AndroidEncodedAudioPacketSink? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private val outputChannels = if (codecChoice == AudioExportCodec.AmrNb) 1 else CHANNEL_COUNT
    private val bytesPerFrame = outputChannels * 2
    private var framesQueued = 0L
    private val queue = ArrayBlockingQueue<FloatArray>(QUEUE_CAPACITY)
    private val workerFailure = AtomicReference<Throwable?>(null)
    private lateinit var worker: Thread
    @Volatile
    private var codecStarted = false
    private var formatStarted = false
    @Volatile
    private var finished = false
    @Volatile
    private var closed = false
    @Volatile
    private var resourcesReleased = false

    init {
        require(sampleRateHz > 0) { "Sample rate must be positive." }
        require(bitrateBps > 0 || codecChoice == AudioExportCodec.Flac) {
            "Bitrate must be positive for this codec."
        }
        try {
            val newCodec = createAndroidMediaCodecEncoder(codecChoice)
            codec = newCodec
            val format = MediaFormat.createAudioFormat(
                androidMediaCodecMime(codecChoice),
                sampleRateHz,
                outputChannels
            ).apply {
                if (codecChoice == AudioExportCodec.Aac) {
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    )
                }
                if (codecChoice != AudioExportCodec.Flac) {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                }
            }
            newCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            sink = createAndroidEncodedAudioPacketSink(
                file = file,
                container = containerChoice,
                codec = codecChoice,
                sampleRateHz = sampleRateHz,
                metadata = metadata
            )
            newCodec.start()
            codecStarted = true
            worker = Thread(::encodeLoop, "audio-export-encoder").apply {
                isDaemon = true
                start()
            }
        } catch (error: Exception) {
            releaseResources()
            throw error
        }
    }

    override fun write(samples: FloatArray, frameCount: Int) {
        check(!closed && !finished) { "Cannot write after the MediaCodec writer is finalized." }
        require(frameCount >= 0) { "Frame count must not be negative." }
        require(frameCount <= samples.size / CHANNEL_COUNT) {
            "Stereo input must contain at least frameCount * 2 samples."
        }
        if (frameCount == 0) return
        throwIfWorkerFailed()
        val chunk = samples.copyOf(frameCount * CHANNEL_COUNT)
        while (!queue.offer(chunk, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throwIfWorkerFailed()
            check(!closed) { "MediaCodec writer was closed during encoding." }
        }
    }

    /**
     * Encodes queued chunks on its own thread so native rendering overlaps with
     * MediaCodec work instead of running strictly one after the other.
     */
    private fun encodeLoop() {
        try {
            while (true) {
                val chunk = queue.take()
                if (chunk.isEmpty()) break
                encodeChunk(chunk, chunk.size / CHANNEL_COUNT)
            }
            finalizeEncoder()
        } catch (error: Throwable) {
            workerFailure.compareAndSet(null, error)
        }
    }

    private fun encodeChunk(samples: FloatArray, frameCount: Int) {
        var frameOffset = 0
        while (frameOffset < frameCount) {
            val codec = checkNotNull(codec)
            val inputIndex = dequeueInputBuffer()
            val inputBuffer = checkNotNull(codec.getInputBuffer(inputIndex))
            val maxFrames = minOf(
                PCM_BUFFER_BYTES / bytesPerFrame,
                inputBuffer.capacity() / bytesPerFrame
            )
            check(maxFrames > 0) { "MediaCodec input buffer is too small for a stereo frame." }
            val chunkFrames = minOf(frameCount - frameOffset, maxFrames)
            val chunkBytes = chunkFrames * bytesPerFrame
            inputBuffer.clear()
            if (outputChannels == 1) {
                writeFloatStereoToMonoPcm16Le(inputBuffer, samples, frameOffset, chunkFrames)
            } else {
                writeFloatsToPcm16Le(
                    inputBuffer,
                    samples,
                    frameOffset * CHANNEL_COUNT,
                    chunkFrames * CHANNEL_COUNT
                )
            }
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
        check(!closed) { "Cannot finalize a closed MediaCodec writer." }
        finished = true
        try {
            queue.put(STOP_CHUNK)
            worker.join()
            throwIfWorkerFailed()
        } finally {
            releaseResources()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (::worker.isInitialized && worker.isAlive) {
            queue.clear()
            queue.offer(STOP_CHUNK)
            worker.join(CLOSE_JOIN_TIMEOUT_MS)
        }
        releaseResources()
    }

    private fun finalizeEncoder() {
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
        sink?.finish(framesQueued)
    }

    private fun throwIfWorkerFailed() {
        workerFailure.get()?.let { failure ->
            throw IllegalStateException("MediaCodec encoding failed: ${failure.message}", failure)
        }
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
                        "Timed out waiting for the MediaCodec encoder to finish."
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!formatStarted) { "MediaCodec output format changed after the sink started." }
                    checkNotNull(sink).onFormatChanged(codec.outputFormat)
                    formatStarted = true
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
                            check(formatStarted) { "MediaCodec output arrived before the output format." }
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            checkNotNull(sink).writePacket(outputBuffer, bufferInfo)
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
        runCatching { sink?.close() }
        if (codecStarted) {
            codecStarted = false
            runCatching { codec?.stop() }
        }
        runCatching { codec?.release() }
    }

    private companion object {
        const val CHANNEL_COUNT = 2
        const val PCM_BUFFER_BYTES = 64 * 1024
        const val QUEUE_CAPACITY = 4
        const val QUEUE_OFFER_TIMEOUT_MS = 50L
        const val CLOSE_JOIN_TIMEOUT_MS = 2_000L
        const val DEQUEUE_TIMEOUT_US = 1_000L
        const val MAX_EOS_EMPTY_POLLS = 500
        val STOP_CHUNK = FloatArray(0)
    }
}

internal fun writeFloatStereoToMonoPcm16Le(
    output: ByteBuffer,
    samples: FloatArray,
    frameOffset: Int,
    frameCount: Int
) {
    for (frame in 0 until frameCount) {
        val sampleIndex = (frameOffset + frame) * 2
        val mixed = (samples[sampleIndex] + samples[sampleIndex + 1]) * 0.5f
        output.putShort(pcm16Le(mixed))
    }
}

internal fun writeFloatsToPcm16Le(
    output: ByteBuffer,
    samples: FloatArray,
    sampleOffset: Int,
    sampleCount: Int
) {
    for (index in 0 until sampleCount) {
        output.putShort(pcm16Le(samples[sampleOffset + index]))
    }
}

private fun pcm16Le(sample: Float): Short {
    val clamped = sample.coerceIn(-1.0f, 1.0f)
    val scale = if (clamped < 0.0f) PCM16_NEGATIVE_FULL_SCALE else PCM16_FULL_SCALE
    return (clamped * scale).roundToInt().toShort()
}

private const val PCM16_FULL_SCALE = 32_767.0f
private const val PCM16_NEGATIVE_FULL_SCALE = 32_768.0f
