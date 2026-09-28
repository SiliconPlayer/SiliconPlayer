package com.flopster101.siliconplayer.mpris

import java.nio.charset.StandardCharsets

// Little-endian D-Bus marshalling. Only the types MPRIS exchanges are supported;
// anything else throws instead of writing a corrupt frame.
internal class DbusWriter(initialCapacity: Int = 512) {
    private var buffer = ByteArray(initialCapacity)
    private var size = 0

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    fun align(alignment: Int) {
        val padding = (alignment - (size % alignment)) % alignment
        if (padding == 0) return
        ensure(padding)
        java.util.Arrays.fill(buffer, size, size + padding, 0)
        size += padding
    }

    fun position(): Int = size

    fun writeByte(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    fun writeUint16(value: Int) {
        align(2)
        ensure(2)
        buffer[size++] = (value and 0xFF).toByte()
        buffer[size++] = ((value ushr 8) and 0xFF).toByte()
    }

    fun writeUint32(value: Long) {
        align(4)
        ensure(4)
        for (shift in 0..3) buffer[size++] = ((value ushr (shift * 8)) and 0xFF).toByte()
    }

    fun writeInt64(value: Long) {
        align(8)
        ensure(8)
        for (shift in 0..7) buffer[size++] = ((value ushr (shift * 8)) and 0xFF).toByte()
    }

    fun writeDouble(value: Double) {
        writeInt64(java.lang.Double.doubleToRawLongBits(value))
    }

    fun writeStringBody(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeUint32(bytes.size.toLong())
        ensure(bytes.size + 1)
        System.arraycopy(bytes, 0, buffer, size, bytes.size)
        size += bytes.size
        buffer[size++] = 0
    }

    fun writeSignature(value: String) {
        val bytes = value.toByteArray(StandardCharsets.US_ASCII)
        ensure(bytes.size + 1)
        buffer[size++] = bytes.size.toByte()
        System.arraycopy(bytes, 0, buffer, size, bytes.size)
        size += bytes.size
        buffer[size++] = 0
    }

    fun patchUint32(offset: Int, value: Long) {
        for (index in 0..3) buffer[offset + index] = ((value ushr (index * 8)) and 0xFF).toByte()
    }

    fun writeValue(value: DbusValue) {
        when (value) {
            is DbusValue.ByteValue -> writeByte(value.value)
            is DbusValue.BoolValue -> writeUint32(if (value.value) 1L else 0L)
            is DbusValue.Int16Value -> writeUint16(value.value.toInt() and 0xFFFF)
            is DbusValue.Uint16Value -> writeUint16(value.value)
            is DbusValue.Int32Value -> writeUint32(value.value.toLong() and 0xFFFFFFFFL)
            is DbusValue.Uint32Value -> writeUint32(value.value)
            is DbusValue.Int64Value -> writeInt64(value.value)
            is DbusValue.DoubleValue -> writeDouble(value.value)
            is DbusValue.StringValue -> writeStringBody(value.value)
            is DbusValue.ObjectPathValue -> writeStringBody(value.value)
            is DbusValue.SignatureValue -> writeSignature(value.value)
            is DbusValue.VariantValue -> {
                writeSignature(value.value.signature())
                writeValue(value.value)
            }
            is DbusValue.ArrayValue -> {
                writeUint32(0L)
                val lengthOffset = position() - 4
                align(alignmentOfSignature(value.elementSignature))
                val contentStart = position()
                value.items.forEach {
                    align(alignmentOf(it))
                    writeValue(it)
                }
                patchUint32(lengthOffset, (position() - contentStart).toLong())
            }
            is DbusValue.StructValue -> {
                align(8)
                value.items.forEach { writeValue(it) }
            }
            is DbusValue.DictEntryValue -> {
                align(8)
                writeStringBody(value.key)
                writeValue(value.value)
            }
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

// D-Bus alignment per type; array elements are aligned individually, which is
// what a{sv} bodies depend on (and why a byte array must not be padded).
private fun alignmentOf(value: DbusValue): Int = when (value) {
    is DbusValue.ByteValue, is DbusValue.SignatureValue -> 1
    is DbusValue.Int16Value, is DbusValue.Uint16Value -> 2
    is DbusValue.BoolValue, is DbusValue.Int32Value, is DbusValue.Uint32Value,
    is DbusValue.StringValue, is DbusValue.ObjectPathValue, is DbusValue.ArrayValue,
    is DbusValue.VariantValue -> 4
    is DbusValue.Int64Value, is DbusValue.DoubleValue,
    is DbusValue.StructValue, is DbusValue.DictEntryValue -> 8
}

private fun alignmentOfSignature(signature: String): Int = when (signature.firstOrNull()) {
    'n', 'q' -> 2
    'b', 'i', 'u', 's', 'o', 'a', 'v' -> 4
    'x', 't', 'd', '(', '{', 'r', 'e' -> 8
    else -> 1
}

internal class DbusReader(private val data: ByteArray, private var offset: Int = 0) {
    fun position(): Int = offset

    fun align(alignment: Int) {
        offset += (alignment - (offset % alignment)) % alignment
    }

    fun readByte(): Int {
        if (offset >= data.size) throw DbusException("truncated byte at $offset")
        return data[offset++].toInt() and 0xFF
    }

    fun readUint16(): Int {
        align(2)
        return readByte() or (readByte() shl 8)
    }

    fun readUint32(): Long {
        align(4)
        var value = 0L
        for (shift in 0..3) value = value or (readByte().toLong() shl (shift * 8))
        return value
    }

    fun readInt32(): Int = readUint32().toInt()

    fun readInt64(): Long {
        align(8)
        var value = 0L
        for (shift in 0 until 64 step 8) value = value or (readByte().toLong() shl shift)
        return value
    }

    fun readDouble(): Double = java.lang.Double.longBitsToDouble(readInt64())

    fun readStringBody(): String {
        val length = readUint32().toInt()
        if (length < 0 || offset + length > data.size) throw DbusException("string length $length out of bounds")
        val value = String(data, offset, length, StandardCharsets.UTF_8)
        offset += length + 1
        return value
    }

    fun readSignature(): String {
        val length = readByte()
        if (offset + length > data.size) throw DbusException("signature length $length out of bounds")
        val value = String(data, offset, length, StandardCharsets.US_ASCII)
        offset += length + 1
        return value
    }

    fun readValue(signature: String): DbusValue = when (val type = signature.firstOrNull()) {
        'y' -> DbusValue.ByteValue(readByte())
        'b' -> DbusValue.BoolValue(readUint32() != 0L)
        'n' -> DbusValue.Int16Value(readUint16().toShort())
        'q' -> DbusValue.Uint16Value(readUint16())
        'i' -> DbusValue.Int32Value(readInt32())
        'u' -> DbusValue.Uint32Value(readUint32())
        'x' -> DbusValue.Int64Value(readInt64())
        'd' -> DbusValue.DoubleValue(readDouble())
        's' -> DbusValue.StringValue(readStringBody())
        'o' -> DbusValue.ObjectPathValue(readStringBody())
        'g' -> DbusValue.SignatureValue(readSignature())
        'v' -> DbusValue.VariantValue(readValue(readSignature()))
        'a' -> {
            val elementSignature = signature.substring(1)
            val byteLength = readUint32().toInt()
            align(alignmentOfSignature(elementSignature))
            val end = offset + byteLength
            val items = mutableListOf<DbusValue>()
            while (offset < end) {
                align(alignmentOfSignature(elementSignature))
                items += readValue(elementSignature)
            }
            DbusValue.ArrayValue(elementSignature, items)
        }
        '(' -> {
            align(8)
            val inner = signature.substring(1, signature.length - 1)
            DbusValue.StructValue(inner.splitSignatures().map { readValue(it) })
        }
        '{' -> {
            align(8)
            val inner = signature.substring(1, signature.length - 1)
            val parts = inner.splitSignatures()
            val key = readValue(parts[0]) as DbusValue.StringValue
            DbusValue.DictEntryValue(key.value, readValue(parts[1]))
        }
        else -> throw DbusException("unsupported signature '$signature'")
    }
}

class DbusException(message: String) : RuntimeException(message)

// Splits a signature into complete types, keeping the array prefix and nested
// "a{sv}" / "(...)" with their element type.
internal fun String.splitSignatures(): List<String> {
    val parts = mutableListOf<String>()
    var index = 0
    while (index < length) {
        val start = index
        index = endOfSignatureType(index)
        parts += substring(start, index)
    }
    return parts
}

private fun String.endOfSignatureType(start: Int): Int {
    var index = start + 1
    return when (this[start]) {
        'a' -> endOfSignatureType(index)
        '(', '{' -> {
            val closer = if (this[start] == '(') ')' else '}'
            var depth = 1
            while (index < length && depth > 0) {
                when (this[index]) {
                    '(', '{' -> depth++
                    ')', '}' -> depth--
                }
                index++
            }
            if (depth != 0) throw DbusException("unterminated '${this[start]}' in signature $this")
            if (this[index - 1] != closer) throw DbusException("mismatched '${this[start]}' in signature $this")
            index
        }
        else -> index
    }
}
