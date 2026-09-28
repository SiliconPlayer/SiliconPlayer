package com.flopster101.siliconplayer.mpris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Byte-level golden from an independent D-Bus implementation (dbus-next) for
// {"mpris:trackid": o, "xesam:title": s, "xesam:artist": as}. Any change to the
// array padding or the patched length offsets shows up here.
private val REFERENCE_METADATA_BODY = (
    "92000000000000000d0000006d707269733a747261636b696400016f000000001f0000002f6f72672f6d" +
        "707269732f4d65646961506c61796572322f547261636b2f3100000000000b000000786573616d3a74" +
        "69746c650001730000050000005469746c650000000c000000786573616d3a61727469737400026173" +
        "000000001600000008000000417274697374204100000000010000004200"
    ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class DbusCodecTest {

    private fun encode(value: DbusValue): ByteArray {
        val writer = DbusWriter(64)
        writer.writeValue(value)
        return writer.toByteArray()
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun metadataDictionaryMatchesTheReferenceEncoding() {
        val value = dbusMetadataDictionary(
            listOf(
                "mpris:trackid" to DbusValue.ObjectPathValue(mprisTrackPath(1)),
                "xesam:title" to DbusValue.StringValue("Title"),
                "xesam:artist" to dbusStringArray(listOf("Artist A", "B"))
            )
        )
        assertEquals(hex(REFERENCE_METADATA_BODY), hex(encode(value)))
    }

    @Test
    fun scalarValuesRoundTrip() {
        val values = listOf(
            DbusValue.ByteValue(7),
            DbusValue.BoolValue(true),
            DbusValue.BoolValue(false),
            DbusValue.Int16Value(-3),
            DbusValue.Uint16Value(65535),
            DbusValue.Int32Value(Int.MIN_VALUE),
            DbusValue.Uint32Value(4294967295L),
            DbusValue.Int64Value(Long.MIN_VALUE),
            DbusValue.DoubleValue(0.5),
            DbusValue.StringValue("héllo"),
            DbusValue.ObjectPathValue("/org/mpris/MediaPlayer2"),
            DbusValue.SignatureValue("a{sv}"),
            DbusValue.VariantValue(DbusValue.Int64Value(42L))
        )
        values.forEach { value ->
            val encoded = encode(value)
            assertEquals(value, DbusReader(encoded).readValue(value.signature()))
        }
    }

    @Test
    fun containersRoundTrip() {
        val values = listOf(
            dbusStringArray(listOf("file", "http", "", "smb")),
            dbusStringArray(emptyList()),
            DbusValue.ArrayValue("y", listOf(DbusValue.ByteValue(1), DbusValue.ByteValue(2))),
            DbusValue.StructValue(listOf(DbusValue.StringValue("a"), DbusValue.Int64Value(1L))),
            dbusMetadataDictionary(listOf("k" to DbusValue.StringValue("v"))),
            DbusValue.ArrayValue("a{sv}", listOf(dbusMetadataDictionary(listOf("n" to DbusValue.Int32Value(2)))))
        )
        values.forEach { value ->
            val encoded = encode(value)
            assertEquals(value, DbusReader(encoded).readValue(value.signature()))
        }
    }

    @Test
    fun byteArraysAreNotPaddedToTheStructAlignment() {
        // The array length ends 4-aligned but not 8-aligned here, so content padding
        // would show up as extra bytes before the element.
        val value = DbusValue.StructValue(
            listOf(
                DbusValue.Int32Value(1),
                DbusValue.Int32Value(2),
                DbusValue.ArrayValue("y", listOf(DbusValue.ByteValue(9)))
            )
        )
        assertEquals("0100000002000000" + "01000000" + "09", hex(encode(value)))
        assertEquals(value, DbusReader(encode(value)).readValue(value.signature()))
    }

    @Test
    fun messageRoundTripKeepsFieldsAndBody() {
        val message = DbusMessage(
            type = DBUS_TYPE_METHOD_CALL,
            serial = 12,
            path = MPRIS_PATH,
            interfaceName = MPRIS_PLAYER_INTERFACE,
            member = "SetPosition",
            destination = MPRIS_BUS_NAME,
            signature = "ox",
            body = listOf(DbusValue.ObjectPathValue(mprisTrackPath(3)), DbusValue.Int64Value(1_500_000L))
        )
        val parsed = parseDbusMessage(message.encode())
        assertEquals(DBUS_TYPE_METHOD_CALL, parsed.type)
        assertEquals(12, parsed.serial)
        assertEquals(MPRIS_PATH, parsed.path)
        assertEquals(MPRIS_PLAYER_INTERFACE, parsed.interfaceName)
        assertEquals("SetPosition", parsed.member)
        assertEquals(MPRIS_BUS_NAME, parsed.destination)
        assertEquals("ox", parsed.signature)
        assertEquals(message.body, parsed.body)
    }

    @Test
    fun aSignatureThatDisagreesWithTheBodyIsRejected() {
        val message = DbusMessage(
            type = DBUS_TYPE_METHOD_RETURN,
            serial = 1,
            signature = "s",
            body = listOf(DbusValue.StringValue("a"), DbusValue.StringValue("b"))
        )
        val failure = runCatching { message.encode() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun signaturesSplitOnCompleteTypes() {
        assertEquals(listOf("a{sv}"), "a{sv}".splitSignatures())
        assertEquals(listOf("s", "s"), "ss".splitSignatures())
        assertEquals(listOf("o", "x"), "ox".splitSignatures())
        assertEquals(listOf("a{sv}", "as"), "a{sv}as".splitSignatures())
    }

    @Test
    fun sessionBusAddressesResolveToSocketPaths() {
        val env = mapOf(
            "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/user/1000/bus,guid=abc;unix:abstract=/tmp/legacy",
            "XDG_RUNTIME_DIR" to "/run/user/1000"
        )
        val endpoints = DbusSessionBus.resolveEndpoints { env[it] }
        assertEquals(listOf(DbusEndpoint("/run/user/1000/bus")), endpoints)
    }

    @Test
    fun sessionBusFallsBackToTheRuntimeDirectory() {
        val env = mapOf("XDG_RUNTIME_DIR" to "/run/user/1000")
        assertEquals(listOf(DbusEndpoint("/run/user/1000/bus")), DbusSessionBus.resolveEndpoints { env[it] })
    }

    @Test
    fun sessionBusWithoutAnAddressHasNoEndpoints() {
        assertTrue(DbusSessionBus.resolveEndpoints { null }.isEmpty())
    }
}
