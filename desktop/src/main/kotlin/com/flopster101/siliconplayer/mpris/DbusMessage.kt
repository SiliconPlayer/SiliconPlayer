package com.flopster101.siliconplayer.mpris

internal const val DBUS_FIELD_PATH = 1
internal const val DBUS_FIELD_INTERFACE = 2
internal const val DBUS_FIELD_MEMBER = 3
internal const val DBUS_FIELD_ERROR_NAME = 4
internal const val DBUS_FIELD_REPLY_SERIAL = 5
internal const val DBUS_FIELD_DESTINATION = 6
internal const val DBUS_FIELD_SENDER = 7
internal const val DBUS_FIELD_SIGNATURE = 8
internal const val DBUS_FIELD_UNIX_FDS = 9

internal const val DBUS_FIXED_HEADER_SIZE = 16
internal const val DBUS_PROTOCOL_VERSION = 1

internal data class DbusMessage(
    val type: Int,
    val serial: Int,
    val flags: Int = 0,
    val path: String? = null,
    val interfaceName: String? = null,
    val member: String? = null,
    val errorName: String? = null,
    val replySerial: Int? = null,
    val destination: String? = null,
    val sender: String? = null,
    val signature: String? = null,
    val body: List<DbusValue> = emptyList()
) {
    fun encode(): ByteArray {
        val bodySignature = body.joinToString("") { it.signature() }.ifEmpty { null }
        check(bodySignature == signature) { "signature '$signature' does not match body '$bodySignature'" }
        val fields = mutableListOf<Pair<Int, DbusValue>>()
        path?.let { fields += DBUS_FIELD_PATH to DbusValue.ObjectPathValue(it) }
        interfaceName?.let { fields += DBUS_FIELD_INTERFACE to DbusValue.StringValue(it) }
        member?.let { fields += DBUS_FIELD_MEMBER to DbusValue.StringValue(it) }
        errorName?.let { fields += DBUS_FIELD_ERROR_NAME to DbusValue.StringValue(it) }
        replySerial?.let { fields += DBUS_FIELD_REPLY_SERIAL to DbusValue.Uint32Value(it.toLong()) }
        destination?.let { fields += DBUS_FIELD_DESTINATION to DbusValue.StringValue(it) }
        sender?.let { fields += DBUS_FIELD_SENDER to DbusValue.StringValue(it) }
        signature?.let { fields += DBUS_FIELD_SIGNATURE to DbusValue.SignatureValue(it) }

        val bodyWriter = DbusWriter(256)
        body.forEach { bodyWriter.writeValue(it) }
        val bodyBytes = bodyWriter.toByteArray()

        val writer = DbusWriter(DBUS_FIXED_HEADER_SIZE + 256 + bodyBytes.size)
        writer.writeByte('l'.code)
        writer.writeByte(type)
        writer.writeByte(flags)
        writer.writeByte(DBUS_PROTOCOL_VERSION)
        writer.writeUint32(bodyBytes.size.toLong())
        writer.writeUint32(serial.toLong() and 0xFFFFFFFFL)
        val lengthOffset = writer.position()
        writer.writeUint32(0L)
        writer.align(8)
        val contentStart = writer.position()
        // a(yv): each field is a struct, so every entry starts 8-aligned.
        fields.forEach { (code, value) ->
            writer.writeValue(
                DbusValue.StructValue(
                    listOf(DbusValue.ByteValue(code), DbusValue.VariantValue(value))
                )
            )
        }
        writer.patchUint32(lengthOffset, (writer.position() - contentStart).toLong())
        writer.align(8)
        bodyBytes.forEach { writer.writeByte(it.toInt() and 0xFF) }
        return writer.toByteArray()
    }
}

internal fun parseDbusMessage(frame: ByteArray): DbusMessage {
    val reader = DbusReader(frame)
    val endian = reader.readByte()
    if (endian != 'l'.code) throw DbusException("big-endian frames are not supported")
    val type = reader.readByte()
    val flags = reader.readByte()
    val version = reader.readByte()
    if (version != DBUS_PROTOCOL_VERSION) throw DbusException("unsupported protocol version $version")
    val bodyLength = reader.readUint32()
    val serial = reader.readUint32().toInt()
    val fields = (reader.readValue("a(yv)") as DbusValue.ArrayValue).items
    var path: String? = null
    var interfaceName: String? = null
    var member: String? = null
    var errorName: String? = null
    var replySerial: Int? = null
    var destination: String? = null
    var sender: String? = null
    var signature: String? = null
    fields.forEach { entry ->
        val struct = entry as DbusValue.StructValue
        val code = (struct.items[0] as DbusValue.ByteValue).value
        val value = (struct.items[1] as DbusValue.VariantValue).value
        when (code) {
            DBUS_FIELD_PATH -> path = (value as DbusValue.ObjectPathValue).value
            DBUS_FIELD_INTERFACE -> interfaceName = (value as DbusValue.StringValue).value
            DBUS_FIELD_MEMBER -> member = (value as DbusValue.StringValue).value
            DBUS_FIELD_ERROR_NAME -> errorName = (value as DbusValue.StringValue).value
            DBUS_FIELD_REPLY_SERIAL -> replySerial = (value as DbusValue.Uint32Value).value.toInt()
            DBUS_FIELD_DESTINATION -> destination = (value as DbusValue.StringValue).value
            DBUS_FIELD_SENDER -> sender = (value as DbusValue.StringValue).value
            DBUS_FIELD_SIGNATURE -> signature = (value as DbusValue.SignatureValue).value
        }
    }
    reader.align(8)
    val bodyReader = DbusReader(frame, reader.position())
    val body = if (signature.isNullOrEmpty() || bodyLength == 0L) {
        emptyList()
    } else {
        signature.splitSignatures().map { bodyReader.readValue(it) }
    }
    return DbusMessage(
        type = type,
        serial = serial,
        flags = flags,
        path = path,
        interfaceName = interfaceName,
        member = member,
        errorName = errorName,
        replySerial = replySerial,
        destination = destination,
        sender = sender,
        signature = signature,
        body = body
    )
}
