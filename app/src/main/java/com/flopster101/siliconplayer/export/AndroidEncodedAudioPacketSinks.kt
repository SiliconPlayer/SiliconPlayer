package com.flopster101.siliconplayer.export

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

internal fun createAndroidEncodedAudioPacketSink(
    file: File,
    container: AudioExportContainer,
    codec: AudioExportCodec,
    sampleRateHz: Int,
    metadata: AudioExportMetadata?
): AndroidEncodedAudioPacketSink = when (container) {
    AudioExportContainer.M4a, AudioExportContainer.ThreeGpp, AudioExportContainer.WebM ->
        AndroidMediaMuxerPacketSink(file, container, metadata)
    AudioExportContainer.Adts -> {
        require(codec == AudioExportCodec.Aac) { "ADTS requires AAC." }
        AndroidAdtsPacketSink(file, sampleRateHz, metadata)
    }
    AudioExportContainer.Ogg -> {
        require(codec == AudioExportCodec.Opus) { "Ogg export currently requires Opus." }
        AndroidOggOpusPacketSink(file, sampleRateHz, metadata)
    }
    AudioExportContainer.Flac -> {
        require(codec == AudioExportCodec.Flac) { "Raw FLAC requires the FLAC codec." }
        AndroidFlacPacketSink(file, sampleRateHz, metadata)
    }
    AudioExportContainer.Amr -> {
        require(codec == AudioExportCodec.AmrNb) { "Raw AMR requires AMR-NB." }
        AndroidAmrPacketSink(file, metadata)
    }
    else -> throw IllegalArgumentException("Unsupported MediaCodec container: $container")
}

private class AndroidMediaMuxerPacketSink(
    private val file: File,
    private val container: AudioExportContainer,
    private val metadata: AudioExportMetadata?
) : AndroidEncodedAudioPacketSink {
    private val muxer = MediaMuxer(file.absolutePath, muxerFormat(container))
    private var trackIndex = -1
    private var started = false
    private var closed = false

    override fun onFormatChanged(format: MediaFormat) {
        check(!closed && !started) { "MediaMuxer output format changed after it started." }
        trackIndex = muxer.addTrack(format)
        muxer.start()
        started = true
    }

    override fun writePacket(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        check(started) { "MediaCodec output arrived before the output format." }
        muxer.writeSampleData(trackIndex, buffer, info)
    }

    override fun finish(totalFrames: Long) {
        if (closed) return
        try {
            if (started) {
                muxer.stop()
                started = false
            }
            muxer.release()
            closed = true
            metadata?.let {
                if (container == AudioExportContainer.WebM) {
                    writeAndroidWebmMetadata(file, it)
                } else {
                    writeAndroidMp4Metadata(file, it)
                }
            }
        } finally {
            if (!closed) close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (started) {
            started = false
            runCatching { muxer.stop() }
        }
        muxer.release()
    }

    private companion object {
        fun muxerFormat(container: AudioExportContainer): Int = when (container) {
            AudioExportContainer.ThreeGpp -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
            AudioExportContainer.WebM -> {
                require(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            }
            else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
    }
}

private abstract class AndroidStreamingPacketSink(file: File) : AndroidEncodedAudioPacketSink {
    protected val output = DataOutputStream(BufferedOutputStream(file.outputStream()))
    private var formatReceived = false
    protected var closed = false
        private set

    final override fun onFormatChanged(format: MediaFormat) {
        check(!closed && !formatReceived) { "Output format changed after the stream started." }
        writeHeader(format)
        formatReceived = true
    }

    final override fun writePacket(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        check(formatReceived) { "MediaCodec output arrived before the output format." }
        writePayload(buffer, info)
    }

    protected abstract fun writeHeader(format: MediaFormat)
    protected abstract fun writePayload(buffer: ByteBuffer, info: MediaCodec.BufferInfo)

    override fun finish(totalFrames: Long) {
        close()
        onFinished(totalFrames)
    }

    protected open fun onFinished(totalFrames: Long) = Unit

    override fun close() {
        if (closed) return
        closed = true
        output.close()
    }

    protected fun ByteBuffer.readBytes(): ByteArray {
        val bytes = ByteArray(remaining())
        get(bytes)
        return bytes
    }
}

private class AndroidAdtsPacketSink(
    file: File,
    private val sampleRateHz: Int,
    private val metadata: AudioExportMetadata?
) : AndroidStreamingPacketSink(file) {
    override fun writeHeader(format: MediaFormat) {
        buildId3Tag(metadata)?.let(output::write)
    }

    override fun writePayload(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val payload = buffer.readBytes()
        output.write(buildAdtsHeader(payload.size, sampleRateHz, CHANNEL_COUNT))
        output.write(payload)
    }
}

private class AndroidAmrPacketSink(
    file: File,
    metadata: AudioExportMetadata?
) : AndroidStreamingPacketSink(file) {
    init {
        require(metadata == null || audioExportMetadataEntries(metadata).isEmpty()) {
            "Raw AMR does not support metadata."
        }
    }

    override fun writeHeader(format: MediaFormat) {
        output.write(AMR_FILE_HEADER)
    }

    override fun writePayload(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        output.write(buffer.readBytes())
    }
}

private class AndroidFlacPacketSink(
    private val file: File,
    private val sampleRateHz: Int,
    private val metadata: AudioExportMetadata?
) : AndroidStreamingPacketSink(file) {
    override fun writeHeader(format: MediaFormat) {
        output.write(buildFlacStreamHeader(format, sampleRateHz, metadata))
    }

    override fun writePayload(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        output.write(buffer.readBytes())
    }

    override fun onFinished(totalFrames: Long) {
        updateFlacTotalSamples(file, totalFrames)
    }
}

internal fun buildAdtsHeader(payloadSize: Int, sampleRateHz: Int, channelCount: Int): ByteArray {
    val sampleRateIndex = AAC_SAMPLE_RATES.indexOf(sampleRateHz)
    require(sampleRateIndex >= 0) { "AAC ADTS does not support $sampleRateHz Hz." }
    require(channelCount in 1..7) { "ADTS channel count must be between 1 and 7." }
    val frameSize = payloadSize + ADTS_HEADER_BYTES
    require(frameSize < 8_192) { "AAC frame is too large for ADTS." }
    return byteArrayOf(
        0xff.toByte(),
        0xf1.toByte(),
        ((AAC_LC_PROFILE - 1) shl 6 or (sampleRateIndex shl 2) or (channelCount ushr 2)).toByte(),
        (((channelCount and 3) shl 6) or (frameSize ushr 11)).toByte(),
        (frameSize ushr 3).toByte(),
        (((frameSize and 7) shl 5) or 0x1f).toByte(),
        0xfc.toByte()
    )
}

internal fun buildId3Tag(metadata: AudioExportMetadata?): ByteArray? {
    if (metadata == null) return null
    val frames = audioExportMetadataEntries(metadata).map { (name, value) ->
        val id = when (name) {
            "DATE" -> "TDRC"
            "COMMENT" -> "COMM"
            else -> ID3_TEXT_FRAMES.getValue(name)
        }
        val text = value.toByteArray(Charsets.UTF_8)
        val content = if (id == "COMM") {
            byteArrayOf(ID3_UTF8, 0x75, 0x6e, 0x64, 0) + text
        } else {
            byteArrayOf(ID3_UTF8) + text
        }
        val frame = id.toByteArray(Charsets.US_ASCII) + id3SyncSafe(content.size) +
            byteArrayOf(0, 0) + content
        frame
    }
    if (frames.isEmpty()) return null
    val payload = frames.fold(ByteArray(0)) { result, frame -> result + frame }
    return "ID3".toByteArray(Charsets.US_ASCII) + byteArrayOf(4, 0, 0) +
        id3SyncSafe(payload.size) + payload
}

internal fun audioExportMetadataEntries(metadata: AudioExportMetadata): List<Pair<String, String>> =
    listOf(
        "TITLE" to metadata.title,
        "ARTIST" to metadata.artist,
        "ALBUM" to metadata.album,
        "COMPOSER" to metadata.composer,
        "GENRE" to metadata.genre,
        "DATE" to metadata.date,
        "COPYRIGHT" to metadata.copyright,
        "COMMENT" to metadata.comment
    ).mapNotNull { (name, value) ->
        value?.takeUnless { it.isBlank() }?.let { name to it }
    }

internal fun buildFlacStreamHeader(
    format: MediaFormat,
    sampleRateHz: Int,
    metadata: AudioExportMetadata?
): ByteArray = buildFlacStreamHeader(
    codecData = (0..2).mapNotNull { index ->
        format.getByteBuffer("csd-$index")?.duplicate()?.let { buffer ->
            ByteArray(buffer.remaining()).also(buffer::get)
        }
    },
    sampleRateHz = sampleRateHz,
    metadata = metadata
)

internal fun buildFlacStreamHeader(
    codecData: List<ByteArray>,
    sampleRateHz: Int,
    metadata: AudioExportMetadata?
): ByteArray {
    val marker = codecData.indexOfFirst {
        it.containsBytes(FLAC_MARKER) || it.containsBytes(FLAC_MP4_CONFIG_MARKER)
    }
    val existing = if (marker >= 0) {
        val data = codecData[marker]
        val flacOffset = data.indexOfBytes(FLAC_MARKER)
        if (flacOffset >= 0) {
            data.copyOfRange(flacOffset, data.size)
        } else {
            val configOffset = data.indexOfBytes(FLAC_MP4_CONFIG_MARKER)
            require(configOffset + FLAC_MP4_CONFIG_HEADER_BYTES <= data.size) {
                "Truncated FLAC codec configuration."
            }
            FLAC_MARKER + data.copyOfRange(configOffset + FLAC_MP4_CONFIG_HEADER_BYTES, data.size)
        }
    } else {
        FLAC_MARKER + buildFlacStreamInfo(sampleRateHz, CHANNEL_COUNT)
    }
    require(existing.size >= FLAC_STREAM_INFO_END_OFFSET) { "FLAC STREAMINFO is missing." }
    require(existing[FLAC_MARKER.size].toInt() and 0x7f == 0) {
        "FLAC STREAMINFO must be the first metadata block."
    }
    require(existing.readUnsigned24(FLAC_MARKER.size + 1) == FLAC_STREAM_INFO_BYTES) {
        "FLAC STREAMINFO must contain 34 bytes."
    }
    val comment = buildFlacVorbisComment(metadata)
    val output = java.io.ByteArrayOutputStream(existing.size + comment.size)
    output.write(FLAC_MARKER)
    var offset = FLAC_MARKER.size
    var foundLastBlock = false
    while (offset + FLAC_BLOCK_HEADER_BYTES <= existing.size && !foundLastBlock) {
        val header = existing[offset].toInt() and 0xff
        val last = header and 0x80 != 0
        val length = existing.readUnsigned24(offset + 1)
        require(length <= existing.size - offset - FLAC_BLOCK_HEADER_BYTES) {
            "Truncated FLAC metadata block."
        }
        val blockSize = FLAC_BLOCK_HEADER_BYTES + length
        val block = existing.copyOfRange(offset, offset + blockSize)
        block[0] = (block[0].toInt() and 0x7f).toByte()
        output.write(block)
        offset += blockSize
        foundLastBlock = last
    }
    require(offset == existing.size) { "Malformed FLAC stream metadata." }
    return output.toByteArray() + comment
}

internal fun updateFlacTotalSamples(file: File, totalFrames: Long) {
    require(totalFrames in 0..FLAC_MAX_TOTAL_SAMPLES) { "FLAC total sample count is out of range." }
    RandomAccessFile(file, "rw").use { output ->
        require(output.length() >= FLAC_STREAM_INFO_END_OFFSET) { "FLAC STREAMINFO is missing." }
        val header = ByteArray(FLAC_STREAM_INFO_END_OFFSET)
        output.readFully(header)
        require(header.copyOfRange(0, 4).contentEquals(FLAC_MARKER)) { "FLAC marker is missing." }
        require(header[4].toInt() and 0x7f == 0 && header.readUnsigned24(5) == FLAC_STREAM_INFO_BYTES) {
            "FLAC STREAMINFO must be the first metadata block."
        }
        val packed = (FLAC_PACKED_INFO_OFFSET until FLAC_PACKED_INFO_OFFSET + 8)
            .fold(0L) { value, index -> (value shl 8) or (header[index].toInt() and 0xff).toLong() }
        val updated = (packed and (0x0fff_ffffL shl 36)) or totalFrames
        output.seek(FLAC_PACKED_INFO_OFFSET.toLong())
        repeat(8) { index ->
            output.write((updated ushr ((7 - index) * 8)).toInt() and 0xff)
        }
    }
}

internal fun buildFlacVorbisComment(metadata: AudioExportMetadata?): ByteArray {
    val vendor = "SiliconPlayer".toByteArray(Charsets.UTF_8)
    val entries = metadata?.let(::audioExportMetadataEntries).orEmpty().map { (name, value) ->
        "$name=$value".toByteArray(Charsets.UTF_8)
    }
    val output = java.io.ByteArrayOutputStream()
    output.writeLittleEndian32(vendor.size)
    output.write(vendor)
    output.writeLittleEndian32(entries.size)
    entries.forEach { entry ->
        output.writeLittleEndian32(entry.size)
        output.write(entry)
    }
    val payload = output.toByteArray()
    require(payload.size < 16_777_216) { "FLAC metadata is too large." }
    return byteArrayOf(
        0x84.toByte(),
        (payload.size ushr 16).toByte(),
        (payload.size ushr 8).toByte(),
        payload.size.toByte()
    ) + payload
}

private fun buildFlacStreamInfo(sampleRateHz: Int, channelCount: Int): ByteArray {
    require(sampleRateHz in 1..1_048_575) { "FLAC sample rate is out of range." }
    val blockSize = 4_096
    val packed = (sampleRateHz.toLong() shl 44) or
        ((channelCount - 1).toLong() shl 41) or
        (15L shl 36)
    val output = java.io.ByteArrayOutputStream(46)
    output.write(
        byteArrayOf(
            0, 0, 0, 34,
            (blockSize ushr 8).toByte(), blockSize.toByte(),
            (blockSize ushr 8).toByte(), blockSize.toByte(),
            0, 0, 0, 0, 0, 0
        )
    )
    output.write(ByteArray(8) { index -> (packed ushr ((7 - index) * 8)).toByte() })
    output.write(ByteArray(16))
    return output.toByteArray()
}

private fun id3SyncSafe(size: Int): ByteArray {
    require(size in 0..0x0fff_ffff) { "ID3 frame is too large." }
    return byteArrayOf(
        (size ushr 21).toByte(),
        (size ushr 14).toByte(),
        (size ushr 7).toByte(),
        size.toByte()
    ).also { bytes -> bytes.forEach { require(it.toInt() and 0x80 == 0) } }
}

private fun java.io.ByteArrayOutputStream.writeLittleEndian32(value: Int) {
    write(value and 0xff)
    write(value ushr 8 and 0xff)
    write(value ushr 16 and 0xff)
    write(value ushr 24 and 0xff)
}

private fun ByteArray.readUnsigned24(offset: Int): Int =
    (this[offset].toInt() and 0xff shl 16) or
        (this[offset + 1].toInt() and 0xff shl 8) or
        (this[offset + 2].toInt() and 0xff)

private fun ByteArray.containsBytes(pattern: ByteArray): Boolean = indexOfBytes(pattern) >= 0

private fun ByteArray.indexOfBytes(pattern: ByteArray): Int {
    for (index in 0..size - pattern.size) {
        if (pattern.indices.all { this[index + it] == pattern[it] }) return index
    }
    return -1
}

private const val CHANNEL_COUNT = 2
private const val ADTS_HEADER_BYTES = 7
private const val AAC_LC_PROFILE = 2
private const val FLAC_BLOCK_HEADER_BYTES = 4
private const val FLAC_STREAM_INFO_BYTES = 34
private const val FLAC_STREAM_INFO_END_OFFSET = 42
private const val FLAC_PACKED_INFO_OFFSET = 18
private const val FLAC_MAX_TOTAL_SAMPLES = (1L shl 36) - 1L
private const val FLAC_MP4_CONFIG_HEADER_BYTES = 8
private const val ID3_UTF8: Byte = 3
private val AAC_SAMPLE_RATES = intArrayOf(
    96_000, 88_200, 64_000, 48_000, 44_100, 32_000,
    24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350
)
private val ID3_TEXT_FRAMES = mapOf(
    "TITLE" to "TIT2",
    "ARTIST" to "TPE1",
    "ALBUM" to "TALB",
    "COMPOSER" to "TCOM",
    "GENRE" to "TCON",
    "COPYRIGHT" to "TCOP"
)
private val FLAC_MARKER = "fLaC".toByteArray()
private val FLAC_MP4_CONFIG_MARKER = "dfLa".toByteArray()
internal val AMR_FILE_HEADER = "#!AMR\n".toByteArray()
