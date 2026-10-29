package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.mpris.DbusSessionBus
import com.flopster101.siliconplayer.mpris.DbusValue

/**
 * XDG desktop portal FileChooser client. Windows/macOS picks are explicitly
 * not planned; without a session bus or a portal this returns null and the
 * caller degrades to a toast.
 */
internal object DesktopPortingPortal {
    private const val SERVICE = "org.freedesktop.portal.Desktop"
    private const val PATH = "/org/freedesktop/portal/desktop"
    private const val FILE_CHOOSER = "org.freedesktop.portal.FileChooser"
    private const val REQUEST = "org.freedesktop.portal.Request"
    private const val RESPONSE_TIMEOUT_MS = 600_000L

    /** Opens the open-file dialog; returns the chosen path or null when unavailable/cancelled. */
    fun openFile(title: String): String? =
        runDialog("OpenFile", title, null, multipleOption())

    /** Opens the save dialog with a suggested name; returns the chosen path or null. */
    fun saveFile(title: String, suggestedName: String): String? =
        runDialog("SaveFile", title, suggestedName, emptyList())

    private fun runDialog(
        member: String,
        title: String,
        suggestedName: String?,
        extraOptions: List<Pair<String, DbusValue>>
    ): String? {
        val result = DbusSessionBus.connectForPortals() ?: return null
        val connection = result.connection
        try {
            val options = mutableListOf<Pair<String, DbusValue>>(
                "handle_token" to DbusValue.StringValue("siliconplayer_settings"),
                "modal" to DbusValue.BoolValue(true)
            )
            suggestedName?.let { options += "current_name" to DbusValue.StringValue(it) }
            options += extraOptions
            val body = listOf(
                DbusValue.StringValue(""),                     // parent window token
                DbusValue.StringValue(title),
                dbusSvArray(options)
            )
            connection.call(
                com.flopster101.siliconplayer.mpris.DBUS_SERVICE,
                com.flopster101.siliconplayer.mpris.DBUS_PATH,
                com.flopster101.siliconplayer.mpris.DBUS_INTERFACE,
                "AddMatch",
                listOf(
                    DbusValue.StringValue(
                        "type='signal',interface='org.freedesktop.portal.Request'," +
                            "member='Response'"
                    )
                )
            )
            val reply = connection.call(SERVICE, PATH, FILE_CHOOSER, member, body)
            val handle = (reply.firstOrNull() as? DbusValue.ObjectPathValue)?.value ?: return null
            val response = connection.waitForSignal(handle, REQUEST, "Response", RESPONSE_TIMEOUT_MS)
                ?: return null
            val code = response.body.getOrNull(0) as? DbusValue.Uint32Value ?: return null
            if (code.value.toInt() != 0) return null
            val results = response.body.getOrNull(1) as? DbusValue.ArrayValue ?: return null
            val uris = results.items
                .filterIsInstance<DbusValue.DictEntryValue>()
                .firstOrNull { it.key == "uris" }
                ?.value as? DbusValue.VariantValue ?: return null
            val uriArray = uris.value as? DbusValue.ArrayValue ?: return null
            val uri = uriArray.items.filterIsInstance<DbusValue.StringValue>().firstOrNull()?.value
                ?: return null
            return uri.removePrefix("file://")
        } finally {
            runCatching { connection.close() }
        }
    }

    private fun dbusSvArray(entries: List<Pair<String, DbusValue>>): DbusValue =
        DbusValue.ArrayValue(
            "{sv}",
            entries.map { (key, value) -> DbusValue.DictEntryValue(key, DbusValue.VariantValue(value)) }
        )

    private fun multipleOption(): List<Pair<String, DbusValue>> =
        listOf("multiple" to DbusValue.VariantValue(DbusValue.BoolValue(false)))
}

private fun multipleOption(): List<Pair<String, DbusValue>> =
    listOf("multiple" to DbusValue.BoolValue(false))
