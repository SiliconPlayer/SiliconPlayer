package com.flopster101.siliconplayer.mpris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class MprisBindingTest {

    // A client that discovers us before the transport actions are wired must still get
    // working property reads; only transport calls may fail. Otherwise the client drops
    // the player on startup and never retries, with no error on either side.
    @Test
    fun discoveryReadsSucceedBeforeActionsAreWired() {
        withPrivateBus { env ->
            val service = MprisService(
                stateProvider = { playingState() },
                commandsProvider = { throw DbusException("playback actions are not wired yet") },
                env = env
            )
            assertTrue(service.start())
            try {
                val client = DbusSessionBus.connect("org.mpris.MediaPlayer2.bindingprobe", env = env)
                    ?: throw AssertionError("private bus refused the probe client")
                try {
                    val status = client.connection.call(
                        MPRIS_BUS_NAME,
                        MPRIS_PATH,
                        DBUS_PROPERTIES_INTERFACE,
                        "Get",
                        listOf(
                            DbusValue.StringValue(MPRIS_PLAYER_INTERFACE),
                            DbusValue.StringValue("PlaybackStatus")
                        )
                    )
                    assertEquals(DbusValue.VariantValue(DbusValue.StringValue("Playing")), status.single())
                    val playerEntries = getAll(client, MPRIS_PLAYER_INTERFACE)
                    assertEquals("Playing", stringProperty(playerEntries, "PlaybackStatus"))
                    assertEquals("Probe Song", stringProperty(metadataEntries(playerEntries), "xesam:title"))
                    val rootEntries = getAll(client, MPRIS_ROOT_INTERFACE)
                    assertEquals(MPRIS_IDENTITY, stringProperty(rootEntries, "Identity"))
                    try {
                        client.connection.call(MPRIS_BUS_NAME, MPRIS_PATH, MPRIS_PLAYER_INTERFACE, "PlayPause")
                        fail("transport before wiring must fail")
                    } catch (error: DbusException) {
                        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("InvalidArgs"))
                    }
                } finally {
                    client.connection.close()
                }
            } finally {
                service.stop()
            }
        }
    }

    private fun playingState(): MprisState = MprisState(
        trackId = mprisTrackPath(1L),
        title = "Probe Song",
        artist = "Probe Artist",
        lengthMicroseconds = 200000000L,
        playbackStatus = MprisPlaybackStatus.Playing,
        canPlay = false,
        canPause = true
    )

    private fun getAll(client: DbusConnectionResult, iface: String): List<DbusValue.DictEntryValue> {
        val reply = client.connection.call(
            MPRIS_BUS_NAME,
            MPRIS_PATH,
            DBUS_PROPERTIES_INTERFACE,
            "GetAll",
            listOf(DbusValue.StringValue(iface))
        )
        return (reply.single() as DbusValue.ArrayValue).items.filterIsInstance<DbusValue.DictEntryValue>()
    }

    private fun stringProperty(entries: List<DbusValue.DictEntryValue>, key: String): String {
        val value = entries.single { it.key == key }.value as DbusValue.VariantValue
        return (value.value as DbusValue.StringValue).value
    }

    private fun metadataEntries(entries: List<DbusValue.DictEntryValue>): List<DbusValue.DictEntryValue> {
        val value = entries.single { it.key == "Metadata" }.value as DbusValue.VariantValue
        return (value.value as DbusValue.ArrayValue).items.filterIsInstance<DbusValue.DictEntryValue>()
    }

    private fun withPrivateBus(block: ((String) -> String?) -> Unit) {
        val dir = Files.createTempDirectory("mpris-binding-test")
        val socket = dir.resolve("bus")
        dir.resolve("session.conf").toFile().writeText(
            "<!DOCTYPE busconfig PUBLIC \"-//freedesktop//DTD D-BUS Bus Configuration 1.0//EN\"\n" +
                " \"http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd\">\n" +
                "<busconfig>\n" +
                "  <type>session</type>\n" +
                "  <listen>unix:path=$socket</listen>\n" +
                "  <policy context=\"default\">\n" +
                "    <allow own=\"*\"/>\n" +
                "    <allow send_destination=\"*\" eavesdrop=\"true\"/>\n" +
                "    <allow eavesdrop=\"true\"/>\n" +
                "  </policy>\n" +
                "</busconfig>\n"
        )
        val daemon = ProcessBuilder("dbus-daemon", "--config-file=${dir.resolve("session.conf")}", "--print-address=1")
            .redirectErrorStream(true)
            .start()
        try {
            val address = daemon.inputStream.bufferedReader().readLine()
            require(!address.isNullOrBlank()) { "dbus-daemon printed no address" }
            block { key -> if (key == "DBUS_SESSION_BUS_ADDRESS") address else System.getenv(key) }
        } finally {
            daemon.destroy()
            daemon.waitFor(10L, TimeUnit.SECONDS)
            dir.toFile().deleteRecursively()
        }
    }
}
