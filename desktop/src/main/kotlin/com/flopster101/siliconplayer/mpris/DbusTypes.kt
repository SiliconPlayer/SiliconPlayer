package com.flopster101.siliconplayer.mpris

// The subset of the D-Bus type system MPRIS needs. Keeping it explicit avoids
// reflection-based marshalling and keeps the wire format testable headless.
sealed interface DbusValue {
    data class ByteValue(val value: Int) : DbusValue
    data class BoolValue(val value: Boolean) : DbusValue
    data class Int16Value(val value: Short) : DbusValue
    data class Uint16Value(val value: Int) : DbusValue
    data class Int32Value(val value: Int) : DbusValue
    data class Uint32Value(val value: Long) : DbusValue
    data class Int64Value(val value: Long) : DbusValue
    data class DoubleValue(val value: Double) : DbusValue
    data class StringValue(val value: String) : DbusValue
    data class ObjectPathValue(val value: String) : DbusValue
    data class SignatureValue(val value: String) : DbusValue
    data class VariantValue(val value: DbusValue) : DbusValue
    data class ArrayValue(val elementSignature: String, val items: List<DbusValue>) : DbusValue
    data class StructValue(val items: List<DbusValue>) : DbusValue
    data class DictEntryValue(val key: String, val value: DbusValue) : DbusValue
}

fun DbusValue.signature(): String = when (this) {
    is DbusValue.ByteValue -> "y"
    is DbusValue.BoolValue -> "b"
    is DbusValue.Int16Value -> "n"
    is DbusValue.Uint16Value -> "q"
    is DbusValue.Int32Value -> "i"
    is DbusValue.Uint32Value -> "u"
    is DbusValue.Int64Value -> "x"
    is DbusValue.DoubleValue -> "d"
    is DbusValue.StringValue -> "s"
    is DbusValue.ObjectPathValue -> "o"
    is DbusValue.SignatureValue -> "g"
    is DbusValue.VariantValue -> "v"
    is DbusValue.ArrayValue -> "a" + elementSignature
    is DbusValue.StructValue -> items.joinToString(separator = "", prefix = "(", postfix = ")") { it.signature() }
    is DbusValue.DictEntryValue -> "{${DbusValue.StringValue(key).signature()}${value.signature()}}"
}

fun dbusString(value: String): DbusValue = DbusValue.StringValue(value)

fun dbusStringArray(values: List<String>): DbusValue =
    DbusValue.ArrayValue("s", values.map { DbusValue.StringValue(it) })

fun dbusMetadataDictionary(entries: List<Pair<String, DbusValue>>): DbusValue =
    DbusValue.ArrayValue(
        "{sv}",
        entries.map { (key, value) -> DbusValue.DictEntryValue(key, DbusValue.VariantValue(value)) }
    )

internal const val DBUS_TYPE_METHOD_CALL = 1
internal const val DBUS_TYPE_METHOD_RETURN = 2
internal const val DBUS_TYPE_ERROR = 3
internal const val DBUS_TYPE_SIGNAL = 4

internal const val DBUS_FLAG_NO_REPLY_EXPECTED = 0x1

internal const val DBUS_SERVICE = "org.freedesktop.DBus"
internal const val DBUS_PATH = "/org/freedesktop/DBus"
internal const val DBUS_INTERFACE = "org.freedesktop.DBus"
internal const val DBUS_PROPERTIES_INTERFACE = "org.freedesktop.DBus.Properties"
internal const val DBUS_INTROSPECTABLE_INTERFACE = "org.freedesktop.DBus.Introspectable"
internal const val DBUS_PEER_INTERFACE = "org.freedesktop.DBus.Peer"

internal const val DBUS_ERROR_UNKNOWN_METHOD = "org.freedesktop.DBus.Error.UnknownMethod"
internal const val DBUS_ERROR_UNKNOWN_OBJECT = "org.freedesktop.DBus.Error.UnknownObject"
internal const val DBUS_ERROR_UNKNOWN_INTERFACE = "org.freedesktop.DBus.Error.UnknownInterface"
internal const val DBUS_ERROR_UNKNOWN_PROPERTY = "org.freedesktop.DBus.Error.UnknownProperty"
internal const val DBUS_ERROR_INVALID_ARGS = "org.freedesktop.DBus.Error.InvalidArgs"
internal const val DBUS_ERROR_PROPERTY_READ_ONLY = "org.freedesktop.DBus.Error.PropertyReadOnly"
