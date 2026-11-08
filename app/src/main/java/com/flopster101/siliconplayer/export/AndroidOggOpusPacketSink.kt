package com.flopster101.siliconplayer.export

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

internal class AndroidOggOpusPacketSink(
    file: File,
    private val sampleRateHz: Int,
    private val metadata: AudioExportMetadata?
) : AndroidEncodedAudioPacketSink {
    init {
        require(sampleRateHz > 0) { "Sample rate must be positive." }
    }

    private val output = file.outputStream().buffered()
    private var headersWritten = false
    private var preSkip = 0
    private var nextPageSequence = 0
    private var granulePosition = 0L
    private var pendingPacket: ByteArray? = null
    private var pendingGranulePosition = 0L
    private var finished = false
    private var closed = false

    override fun onFormatChanged(format: MediaFormat) {
        check(!closed && !finished) { "Cannot set the format after the Ogg sink is finalized." }
        check(!headersWritten) { "Ogg Opus output format cannot change after the stream starts." }
        val csd = format.getByteBuffer("csd-2")?.duplicate()
        val formatPreSkip = if (csd == null) {
            0
        } else {
            require(csd.remaining() >= 2) { "Opus csd-2 must contain a little-endian pre-skip." }
            (csd.get().toInt() and 0xff) or ((csd.get().toInt() and 0xff) shl 8)
        }
        writeHeaders(formatPreSkip)
    }

    override fun writePacket(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        check(!closed && !finished) { "Cannot write after the Ogg sink is finalized." }
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        require(info.offset >= 0 && info.size >= 0) { "Opus packet bounds must not be negative." }
        val end = info.offset.toLong() + info.size.toLong()
        require(end <= buffer.capacity().toLong()) { "Opus packet exceeds the output buffer." }
        if (info.size == 0) return
        check(headersWritten) { "Opus audio arrived before the output format." }

        val packet = ByteArray(info.size)
        val source = buffer.duplicate()
        source.clear()
        source.position(info.offset)
        source.limit(info.offset + info.size)
        source.get(packet)
        val durationSamples = opusPacketDurationSamples(packet)

        flushPendingPacket(endOfStream = false)
        pendingPacket = packet
        pendingGranulePosition = advanceOpusGranulePosition(granulePosition, durationSamples)
    }

    override fun finish(totalFrames: Long) {
        if (finished || closed) return
        try {
            require(totalFrames >= 0L) { "Frame count must not be negative." }
            if (!headersWritten) writeHeaders(preSkip = 0)
            if (pendingPacket != null) {
                val renderedSamples = Math.multiplyExact(totalFrames, OGG_OPUS_SAMPLE_RATE) / sampleRateHz
                pendingGranulePosition = Math.addExact(preSkip.toLong(), renderedSamples)
            }
            flushPendingPacket(endOfStream = true)
            output.flush()
        } finally {
            finished = true
            close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingPacket = null
        output.close()
    }

    private fun writeHeaders(preSkip: Int) {
        writePage(
            headerType = OGG_BOS,
            granulePosition = 0L,
            packets = listOf(buildOpusHead(sampleRateHz, preSkip))
        )
        writePage(
            headerType = 0,
            granulePosition = 0L,
            packets = listOf(buildOpusTags(metadata))
        )
        this.preSkip = preSkip
        granulePosition = preSkip.toLong()
        headersWritten = true
    }

    private fun flushPendingPacket(endOfStream: Boolean) {
        val packet = pendingPacket
        if (packet == null) {
            if (endOfStream) {
                writePage(OGG_EOS, granulePosition, emptyList())
            }
            return
        }
        writePage(
            headerType = if (endOfStream) OGG_EOS else 0,
            granulePosition = pendingGranulePosition,
            packets = listOf(packet)
        )
        granulePosition = pendingGranulePosition
        pendingPacket = null
    }

    private fun writePage(headerType: Int, granulePosition: Long, packets: List<ByteArray>) {
        output.write(
            buildOggPage(
                headerType = headerType,
                granulePosition = granulePosition,
                serialNumber = OGG_STREAM_SERIAL,
                pageSequence = nextPageSequence,
                packets = packets
            )
        )
        nextPageSequence++
    }
}

internal fun buildOpusHead(sampleRateHz: Int, preSkip: Int): ByteArray {
    require(sampleRateHz > 0) { "Sample rate must be positive." }
    require(preSkip in 0..0xffff) { "Opus pre-skip must fit in 16 bits." }
    val packet = ByteArray(19)
    OPUS_HEAD_MAGIC.copyInto(packet)
    packet[8] = 1
    packet[9] = 2
    writeLittleEndian16(packet, 10, preSkip)
    writeLittleEndian32(packet, 12, sampleRateHz)
    return packet
}

internal fun opusVorbisComments(metadata: AudioExportMetadata?): List<ByteArray> =
    metadata?.let(::audioExportMetadataEntries).orEmpty().map { (name, value) ->
        "$name=$value".toByteArray(Charsets.UTF_8)
    }

internal fun buildOpusTags(
    metadata: AudioExportMetadata?,
    vendor: String = "SiliconPlayer"
): ByteArray {
    val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
    val comments = opusVorbisComments(metadata)
    val output = ByteArrayOutputStream()
    output.write(OPUS_TAGS_MAGIC)
    output.writeLittleEndian32(vendorBytes.size)
    output.write(vendorBytes)
    output.writeLittleEndian32(comments.size)
    comments.forEach { comment ->
        output.writeLittleEndian32(comment.size)
        output.write(comment)
    }
    return output.toByteArray()
}

internal fun opusPacketDurationSamples(packet: ByteArray): Int {
    require(packet.isNotEmpty()) { "Opus packet must contain a TOC byte." }
    val toc = packet[0].toInt() and 0xff
    val config = toc ushr 3
    val frameSamples = when (config) {
        in 0..11 -> when (config and 3) {
            0 -> 480
            1 -> 960
            2 -> 1_920
            else -> 2_880
        }
        in 12..15 -> if (config and 1 == 0) 480 else 960
        else -> when (config and 3) {
            0 -> 120
            1 -> 240
            2 -> 480
            else -> 960
        }
    }
    val frameCount = when (toc and 3) {
        0 -> {
            require(packet.size - 1 <= MAX_OPUS_FRAME_BYTES) { "Opus frame is too large." }
            1
        }
        1 -> {
            val payloadBytes = packet.size - 1
            require(payloadBytes % 2 == 0) { "Opus CBR frames have unequal sizes." }
            require(payloadBytes / 2 <= MAX_OPUS_FRAME_BYTES) { "Opus frame is too large." }
            2
        }
        2 -> {
            val first = readOpusFrameLength(packet, 1)
            val remaining = packet.size - first.dataOffset
            require(first.length <= remaining) { "Truncated first Opus frame." }
            require(remaining - first.length <= MAX_OPUS_FRAME_BYTES) { "Opus frame is too large." }
            2
        }
        else -> {
            require(packet.size >= 2) { "Truncated Opus frame count." }
            val control = packet[1].toInt() and 0xff
            val count = control and 0x3f
            require(count in 1..48) { "Invalid Opus frame count." }
            require(frameSamples * count <= MAX_OPUS_PACKET_SAMPLES) {
                "Opus packet duration exceeds 120 ms."
            }
            var offset = 2
            if (control and 0x80 != 0) {
                var paddingLength: Int
                do {
                    require(offset < packet.size) { "Truncated Opus padding." }
                    paddingLength = packet[offset++].toInt() and 0xff
                    val paddingBytes = if (paddingLength == 255) 254 else paddingLength
                    require(paddingBytes <= packet.size - offset) { "Truncated Opus padding." }
                    offset += paddingBytes
                } while (paddingLength == 255)
            }
            if (control and 0x40 == 0) {
                val payloadBytes = packet.size - offset
                require(payloadBytes % count == 0) { "Opus CBR frames have unequal sizes." }
                require(payloadBytes / count <= MAX_OPUS_FRAME_BYTES) { "Opus frame is too large." }
            } else {
                repeat(count - 1) {
                    val frame = readOpusFrameLength(packet, offset)
                    offset = frame.dataOffset
                    require(frame.length <= packet.size - offset) { "Truncated Opus frame." }
                    offset += frame.length
                }
                require(packet.size - offset <= MAX_OPUS_FRAME_BYTES) { "Opus frame is too large." }
            }
            count
        }
    }
    return frameSamples * frameCount
}

internal fun advanceOpusGranulePosition(current: Long, packetDurationSamples: Int): Long {
    require(current >= 0L) { "Opus granule position must not be negative." }
    require(packetDurationSamples in 1..MAX_OPUS_PACKET_SAMPLES) {
        "Invalid Opus packet duration."
    }
    return Math.addExact(current, packetDurationSamples.toLong())
}

internal fun oggPacketLacing(packetSize: Int): ByteArray {
    require(packetSize >= 0) { "Ogg packet size must not be negative." }
    val fullSegments = packetSize / 255
    return ByteArray(fullSegments + 1) { index ->
        if (index < fullSegments) 255.toByte() else (packetSize % 255).toByte()
    }
}

internal fun buildOggPage(
    headerType: Int,
    granulePosition: Long,
    serialNumber: Int,
    pageSequence: Int,
    packets: List<ByteArray>
): ByteArray {
    require(headerType in 0..7) { "Invalid Ogg page header type." }
    require(pageSequence >= 0) { "Ogg page sequence must not be negative." }
    val lacings = packets.map { oggPacketLacing(it.size) }
    val segmentCount = lacings.sumOf { it.size }
    require(segmentCount <= 255) { "Ogg page requires more than 255 segments." }
    val bodySize = packets.sumOf { it.size.toLong() }
    require(bodySize <= Int.MAX_VALUE - 27L - segmentCount) { "Ogg page is too large." }
    val bodyOffset = 27 + segmentCount
    val page = ByteArray(bodyOffset + bodySize.toInt())
    OGG_CAPTURE_PATTERN.copyInto(page)
    page[5] = headerType.toByte()
    writeLittleEndian64(page, 6, granulePosition)
    writeLittleEndian32(page, 14, serialNumber)
    writeLittleEndian32(page, 18, pageSequence)
    page[26] = segmentCount.toByte()
    var lacingOffset = 27
    lacings.forEach { lacing ->
        lacing.copyInto(page, lacingOffset)
        lacingOffset += lacing.size
    }
    var packetOffset = bodyOffset
    packets.forEach { packet ->
        packet.copyInto(page, packetOffset)
        packetOffset += packet.size
    }
    writeLittleEndian32(page, 22, oggCrc32(page))
    return page
}

internal fun oggCrc32(bytes: ByteArray): Int {
    var crc = 0
    bytes.forEach { byte ->
        val tableIndex = ((crc ushr 24) xor (byte.toInt() and 0xff)) and 0xff
        crc = (crc shl 8) xor OGG_CRC_TABLE[tableIndex]
    }
    return crc
}

private data class OpusFrameLength(val length: Int, val dataOffset: Int)

private fun readOpusFrameLength(packet: ByteArray, offset: Int): OpusFrameLength {
    require(offset >= 0 && offset < packet.size) { "Truncated Opus frame length." }
    val first = packet[offset].toInt() and 0xff
    if (first < 252) return OpusFrameLength(first, offset + 1)
    require(offset + 1 < packet.size) { "Truncated Opus frame length." }
    val second = packet[offset + 1].toInt() and 0xff
    return OpusFrameLength(first + 4 * second, offset + 2)
}

private fun writeLittleEndian16(target: ByteArray, offset: Int, value: Int) {
    target[offset] = value.toByte()
    target[offset + 1] = (value ushr 8).toByte()
}

private fun writeLittleEndian32(target: ByteArray, offset: Int, value: Int) {
    repeat(4) { index ->
        target[offset + index] = (value ushr (index * 8)).toByte()
    }
}

private fun writeLittleEndian64(target: ByteArray, offset: Int, value: Long) {
    repeat(8) { index ->
        target[offset + index] = (value ushr (index * 8)).toByte()
    }
}

private fun ByteArrayOutputStream.writeLittleEndian32(value: Int) {
    repeat(4) { index ->
        write(value ushr (index * 8))
    }
}

private const val OGG_BOS = 0x02
private const val OGG_EOS = 0x04
private const val OGG_OPUS_SAMPLE_RATE = 48_000L
private const val OGG_STREAM_SERIAL = 0x4f707573
private const val MAX_OPUS_FRAME_BYTES = 1_275
private const val MAX_OPUS_PACKET_SAMPLES = 5_760
private val OGG_CAPTURE_PATTERN = byteArrayOf(0x4f, 0x67, 0x67, 0x53)
private val OPUS_HEAD_MAGIC = "OpusHead".toByteArray(Charsets.US_ASCII)
private val OPUS_TAGS_MAGIC = "OpusTags".toByteArray(Charsets.US_ASCII)
private val OGG_CRC_TABLE = IntArray(256) { index ->
    var crc = index shl 24
    repeat(8) {
        crc = if (crc and 0x80000000.toInt() != 0) {
            (crc shl 1) xor 0x04c11db7
        } else {
            crc shl 1
        }
    }
    crc
}
