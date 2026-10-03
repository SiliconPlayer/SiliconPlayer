package com.flopster101.siliconplayer.mpris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DbusNameClaimTest {

    @Test
    fun primaryAndAlreadyOwnedMeanOwned() {
        assertTrue(nameRequestOwnsName(DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER))
        assertTrue(nameRequestOwnsName(DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER))
        assertFalse(nameRequestOwnsName(DBUS_REQUEST_NAME_REPLY_EXISTS))
        assertFalse(nameRequestOwnsName(0))
        assertFalse(nameRequestOwnsName(2))
    }

    @Test
    fun staleInstanceMatchesOurOwnLaunchShapes() {
        assertTrue(isSiliconPlayerCmdline("/opt/SiliconPlayer/bin/SiliconPlayer"))
        assertTrue(isSiliconPlayerCmdline("SiliconPlayer-0.1.0-x86_64.AppImage"))
        assertTrue(
            isSiliconPlayerCmdline(
                "java -cp /mnt/nvme_build/projects/drdsnd_alt/SiliconPlayer/desktop/build/classes"
            )
        )
        assertFalse(isSiliconPlayerCmdline("firefox"))
        assertFalse(isSiliconPlayerCmdline(""))
    }

    @Test
    fun replacementFlagsStealTheNameFromACooperativeHolder() {
        assumeTrue(DbusSessionBus.resolveEndpoints().isNotEmpty())
        val holder = DbusSessionBus.connect(MPRIS_BUS_NAME + "-takeover-probe")
        assumeTrue(holder != null)
        try {
            val taker = DbusSessionBus.connect(MPRIS_BUS_NAME + "-takeover-probe", onFailure = {})
            assertNotNull("a new instance must steal the name", taker)
            taker?.connection?.close()
        } finally {
            holder!!.connection.close()
        }
    }

    @Test
    fun nameOwnerPidRoundTripsToOurOwnPid() {
        assumeTrue(DbusSessionBus.resolveEndpoints().isNotEmpty())
        val holder = DbusSessionBus.connect(MPRIS_BUS_NAME + "-owner-probe")
        assumeTrue(holder != null)
        val bus = holder!!.connection
        try {
            val owner = bus.call(
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "GetNameOwner",
                body = listOf(DbusValue.StringValue(MPRIS_BUS_NAME + "-owner-probe"))
            ).firstOrNull() as? DbusValue.StringValue
            assertNotNull(owner)
            val pid = bus.call(
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "GetConnectionUnixProcessID",
                body = listOf(DbusValue.StringValue(owner!!.value))
            ).firstOrNull() as? DbusValue.Uint32Value
            assertEquals(ProcessHandle.current().pid(), pid?.value?.toLong())
        } finally {
            bus.close()
        }
    }
}
