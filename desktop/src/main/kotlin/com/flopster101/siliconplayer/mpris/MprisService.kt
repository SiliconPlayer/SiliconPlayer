package com.flopster101.siliconplayer.mpris

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal const val MPRIS_PATH = "/org/mpris/MediaPlayer2"
internal const val MPRIS_ROOT_INTERFACE = "org.mpris.MediaPlayer2"
internal const val MPRIS_PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
internal const val MPRIS_BUS_NAME = "org.mpris.MediaPlayer2.siliconplayer"
internal const val MPRIS_IDENTITY = "SiliconPlayer"
internal const val MPRIS_DESKTOP_ENTRY = "siliconplayer"
internal const val MPRIS_POLL_INTERVAL_MS = 400L
internal const val MPRIS_RECONNECT_INTERVAL_MS = 2000L
internal val MPRIS_SUPPORTED_URI_SCHEMES = listOf("file", "http", "https", "smb")

// Serves the MPRIS contract on the session bus: root + Player interfaces,
// PropertiesChanged diffing, and transport methods routed to the app's own
// playback actions, so a headset button and the on-screen button do the same thing.
// Property reads never need the actions, so discovery succeeds even when the
// actions are wired a moment after the bus name becomes visible.
// Owning the name is not permanent: a bus restart or a dropped socket releases it,
// so the service re-registers itself until it is stopped.
internal class MprisService(
    private val stateProvider: () -> MprisState,
    private val commandsProvider: () -> MprisCommands,
    private val onLog: (String) -> Unit = {},
    private val env: (String) -> String? = System::getenv
) {
    private val lock = Any()
    private var connection: DbusConnection? = null
    private var poller: ScheduledExecutorService? = null
    private var supervisor: ScheduledExecutorService? = null
    private var lastState: MprisState? = null
    private var stopped = true
    private var loggedUnavailable = false

    // Sampled on the poll thread only: a slow stateProvider must never delay a
    // method reply, or the client times out and drops the player.
    @Volatile
    private var snapshot: MprisState = MprisState()

    fun start(): Boolean {
        synchronized(lock) {
            if (!stopped) return true
            stopped = false
        }
        connectOnce()
        synchronized(lock) {
            if (stopped) return isRunning()
            supervisor = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "mpris-supervisor").apply { isDaemon = true }
            }.also { scheduler ->
                scheduler.scheduleWithFixedDelay(
                    { connectOnce() },
                    MPRIS_RECONNECT_INTERVAL_MS,
                    MPRIS_RECONNECT_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )
            }
        }
        return isRunning()
    }

    fun stop() {
        val bus: DbusConnection?
        val scheduler: ScheduledExecutorService?
        val ticker: ScheduledExecutorService?
        synchronized(lock) {
            stopped = true
            bus = connection.also { connection = null }
            scheduler = supervisor.also { supervisor = null }
            ticker = poller.also { poller = null }
            lastState = null
        }
        scheduler?.shutdownNow()
        ticker?.shutdownNow()
        bus?.close()
    }

    fun isRunning(): Boolean = synchronized(lock) { connection != null }

    // Idempotent: connects when there is no live bus, does nothing otherwise.
    private fun connectOnce() {
        synchronized(lock) {
            if (stopped || connection != null) return
        }
        val result = DbusSessionBus.connect(
            MPRIS_BUS_NAME,
            onFailure = { reason ->
                // One log per outage; the supervisor keeps retrying silently.
                val firstFailure = synchronized(lock) {
                    if (loggedUnavailable) false else { loggedUnavailable = true; true }
                }
                if (firstFailure) onLog("MPRIS unavailable: $reason")
            },
            env = env
        ) ?: return
        val bus = result.connection
        val dispatch = Executors.newSingleThreadExecutor { task ->
            Thread(task, "mpris-dbus-dispatch").apply { isDaemon = true }
        }
        bus.onClosed = { onBusClosed(bus) }
        bus.startReading(dispatch) { message -> handleMessage(message) }
        val ticker = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "mpris-poll").apply { isDaemon = true }
        }
        synchronized(lock) {
            if (stopped) {
                bus.close()
                ticker.shutdownNow()
                return
            }
            connection = bus
            poller = ticker
            lastState = null
            loggedUnavailable = false
        }
        // Sample synchronously before announcing: a client that discovers us in
        // the first moments must already see real state, not the empty default.
        pollOnce()
        ticker.scheduleWithFixedDelay(
            { pollOnce() },
            MPRIS_POLL_INTERVAL_MS,
            MPRIS_POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
        onLog("MPRIS registered as $MPRIS_BUS_NAME (bus ${bus.endpoint}, unique ${bus.uniqueName})")
    }

    private fun onBusClosed(bus: DbusConnection) {
        val lost = synchronized(lock) {
            if (connection !== bus) {
                false
            } else {
                connection = null
                poller?.shutdownNow()
                poller = null
                lastState = null
                true
            }
        }
        if (lost) onLog("MPRIS connection to ${bus.endpoint} closed, re-registering")
    }

    private fun pollOnce() {
        val bus = connection ?: return
        val previous = lastState
        val current = runCatching { stateProvider() }.getOrNull() ?: return
        snapshot = current
        lastState = current
        if (previous == null) return
        val changed = changedPlayerProperties(previous, current)
        if (changed.isEmpty()) return
        runCatching {
            bus.emit(
                path = MPRIS_PATH,
                interfaceName = DBUS_PROPERTIES_INTERFACE,
                member = "PropertiesChanged",
                body = listOf(
                    DbusValue.StringValue(MPRIS_PLAYER_INTERFACE),
                    dbusMetadataDictionary(changed.toList()),
                    dbusStringArray(emptyList())
                )
            )
        }.onFailure { dropBus(bus) }
    }

    private fun dropBus(bus: DbusConnection) {
        onBusClosed(bus)
        runCatching { bus.close() }
    }

    private fun handleMessage(message: DbusMessage) {
        val bus = connection ?: return
        if (message.path != null && message.path != MPRIS_PATH) {
            bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_OBJECT, "no object at ${message.path}")
            return
        }
        val member = message.member
        if (member == null) {
            bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_METHOD, "memberless call")
            return
        }
        val state = snapshot
        val commands = runCatching { commandsProvider() }.getOrNull()
        val outcome = runCatching { dispatchCall(bus, message, state, commands, member) }
        outcome.onFailure { error ->
            bus.errorReplyTo(message, DBUS_ERROR_INVALID_ARGS, error.message ?: "invalid arguments")
        }
    }

    private fun dispatchCall(
        bus: DbusConnection,
        message: DbusMessage,
        state: MprisState,
        commands: MprisCommands?,
        member: String
    ) {
        when (message.interfaceName) {
            DBUS_INTROSPECTABLE_INTERFACE -> bus.replyTo(message, listOf(DbusValue.StringValue(MPRIS_INTROSPECTION_XML)))
            DBUS_PEER_INTERFACE -> when (member) {
                "Ping" -> bus.replyTo(message)
                "GetMachineId" -> bus.replyTo(message, listOf(DbusValue.StringValue("")))
                else -> bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_METHOD, "unknown $member")
            }
            DBUS_PROPERTIES_INTERFACE -> handlePropertiesCall(bus, message, state, commands, member)
            MPRIS_ROOT_INTERFACE -> when (member) {
                "Quit" -> {
                    val actions = requireActions(bus, message, commands) ?: return
                    actions.quit()
                    bus.replyTo(message)
                }
                "Raise" -> {
                    val actions = requireActions(bus, message, commands) ?: return
                    actions.raise()
                    bus.replyTo(message)
                }
                else -> bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_METHOD, "unknown $member")
            }
            MPRIS_PLAYER_INTERFACE -> handlePlayerCall(bus, message, state, commands, member)
            else -> bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_INTERFACE, "unknown ${message.interfaceName}")
        }
    }

    private fun requireActions(
        bus: DbusConnection,
        message: DbusMessage,
        commands: MprisCommands?
    ): MprisCommands? {
        if (commands != null) return commands
        bus.errorReplyTo(message, DBUS_ERROR_INVALID_ARGS, "playback actions are not wired yet")
        return null
    }

    private fun handlePropertiesCall(
        bus: DbusConnection,
        message: DbusMessage,
        state: MprisState,
        commands: MprisCommands?,
        member: String
    ) {
        when (member) {
            "Get" -> {
                val interfaceName = message.stringArg(0)
                val propertyName = message.stringArg(1)
                val value = propertyValue(interfaceName, propertyName, state)
                    ?: return bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_PROPERTY, "no property $propertyName")
                bus.replyTo(message, listOf(DbusValue.VariantValue(value)))
            }
            "GetAll" -> {
                val interfaceName = message.stringArg(0)
                bus.replyTo(message, listOf(getAllProperties(interfaceName, state)))
            }
            "Set" -> {
                val interfaceName = message.stringArg(0)
                val propertyName = message.stringArg(1)
                val value = message.variantArg(2)
                if (interfaceName == MPRIS_PLAYER_INTERFACE && propertyName == "Volume") {
                    val actions = requireActions(bus, message, commands) ?: return
                    val volume = (value as? DbusValue.DoubleValue)?.value
                        ?: throw DbusException("Volume must be a double")
                    actions.setVolume(volume.coerceIn(0.0, 1.0))
                    return bus.replyTo(message)
                }
                if (interfaceName == MPRIS_PLAYER_INTERFACE && propertyName == "LoopStatus") {
                    val actions = requireActions(bus, message, commands) ?: return
                    val requested = (value as? DbusValue.StringValue)?.value
                        ?: throw DbusException("LoopStatus must be a string")
                    actions.setLoopStatus(
                        MprisLoopStatus.fromWire(requested) ?: throw DbusException("invalid LoopStatus $requested")
                    )
                    return bus.replyTo(message)
                }
                bus.errorReplyTo(message, DBUS_ERROR_PROPERTY_READ_ONLY, "$propertyName is read-only")
            }
            else -> bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_METHOD, "unknown $member")
        }
    }

    private fun handlePlayerCall(
        bus: DbusConnection,
        message: DbusMessage,
        state: MprisState,
        commands: MprisCommands?,
        member: String
    ) {
        val actions = requireActions(bus, message, commands) ?: return
        when (member) {
            "Next" -> {
                actions.next()
                bus.replyTo(message)
            }
            "Previous" -> {
                actions.previous()
                bus.replyTo(message)
            }
            "Play" -> {
                actions.play()
                bus.replyTo(message)
            }
            "Pause" -> {
                actions.pause()
                bus.replyTo(message)
            }
            "PlayPause" -> {
                actions.playPause()
                bus.replyTo(message)
            }
            "Stop" -> {
                actions.stop()
                bus.replyTo(message)
            }
            "Seek" -> {
                val offsetMicros = message.int64Arg(0)
                val target = (state.positionMicroseconds + offsetMicros).coerceAtLeast(0L)
                actions.seekToMicroseconds(target)
                bus.replyTo(message)
            }
            "SetPosition" -> {
                val trackId = message.objectPathArg(0)
                if (trackId != state.trackId) {
                    return bus.errorReplyTo(message, DBUS_ERROR_INVALID_ARGS, "unknown track $trackId")
                }
                actions.seekToMicroseconds(message.int64Arg(1).coerceAtLeast(0L))
                bus.replyTo(message)
            }
            "OpenUri" -> {
                actions.openUri(message.stringArg(0))
                bus.replyTo(message)
            }
            else -> bus.errorReplyTo(message, DBUS_ERROR_UNKNOWN_METHOD, "unknown $member")
        }
    }

    private fun propertyValue(interfaceName: String, propertyName: String, state: MprisState): DbusValue? = when (interfaceName) {
        MPRIS_ROOT_INTERFACE -> rootProperties()[propertyName]
        MPRIS_PLAYER_INTERFACE -> playerProperties(state)[propertyName]
        else -> null
    }

    private fun getAllProperties(interfaceName: String, state: MprisState): DbusValue = dbusMetadataDictionary(
        when (interfaceName) {
            MPRIS_ROOT_INTERFACE -> rootProperties().toList()
            MPRIS_PLAYER_INTERFACE -> playerProperties(state).toList()
            else -> emptyList()
        }
    )

    private fun rootProperties(): Map<String, DbusValue> = mapOf(
        "CanQuit" to DbusValue.BoolValue(true),
        "CanRaise" to DbusValue.BoolValue(true),
        "HasTrackList" to DbusValue.BoolValue(false),
        "Identity" to DbusValue.StringValue(MPRIS_IDENTITY),
        "DesktopEntry" to DbusValue.StringValue(MPRIS_DESKTOP_ENTRY),
        "SupportedUriSchemes" to dbusStringArray(MPRIS_SUPPORTED_URI_SCHEMES),
        "SupportedMimeTypes" to dbusStringArray(emptyList()),
        "Fullscreen" to DbusValue.BoolValue(false),
        "CanSetFullscreen" to DbusValue.BoolValue(false)
    )

    private fun playerProperties(state: MprisState): Map<String, DbusValue> = mapOf(
        "PlaybackStatus" to DbusValue.StringValue(state.playbackStatus.wire),
        "LoopStatus" to DbusValue.StringValue(state.loopStatus.wire),
        "Rate" to DbusValue.DoubleValue(1.0),
        "Shuffle" to DbusValue.BoolValue(false),
        "Metadata" to buildMetadata(state),
        "Volume" to DbusValue.DoubleValue(state.volume.coerceIn(0.0, 1.0)),
        "Position" to DbusValue.Int64Value(state.positionMicroseconds.coerceAtLeast(0L)),
        "MinimumRate" to DbusValue.DoubleValue(1.0),
        "MaximumRate" to DbusValue.DoubleValue(1.0),
        "CanGoNext" to DbusValue.BoolValue(state.canGoNext),
        "CanGoPrevious" to DbusValue.BoolValue(state.canGoPrevious),
        "CanPlay" to DbusValue.BoolValue(state.canPlay),
        "CanPause" to DbusValue.BoolValue(state.canPause),
        "CanSeek" to DbusValue.BoolValue(state.canSeek),
        "CanControl" to DbusValue.BoolValue(true)
    )

    private fun buildMetadata(state: MprisState): DbusValue {
        val entries = mutableListOf<Pair<String, DbusValue>>(
            "mpris:trackid" to DbusValue.ObjectPathValue(state.trackId)
        )
        if (state.lengthMicroseconds > 0L) {
            entries += "mpris:length" to DbusValue.Int64Value(state.lengthMicroseconds)
        }
        state.artUrl?.let { entries += "mpris:artUrl" to DbusValue.StringValue(it) }
        entries += "xesam:title" to DbusValue.StringValue(state.title.ifBlank { MPRIS_IDENTITY })
        if (state.artist.isNotBlank()) entries += "xesam:artist" to dbusStringArray(listOf(state.artist))
        if (state.album.isNotBlank()) entries += "xesam:album" to DbusValue.StringValue(state.album)
        return dbusMetadataDictionary(entries)
    }

    // Position is polled by clients through Properties.Get; the spec keeps it out of the signal.
    private fun changedPlayerProperties(previous: MprisState, current: MprisState): Map<String, DbusValue> {
        val before = playerProperties(previous)
        val after = playerProperties(current)
        val changed = linkedMapOf<String, DbusValue>()
        for (key in before.keys + after.keys) {
            if (key == "Position" || before[key] == after[key]) continue
            after[key]?.let { changed[key] = it }
        }
        return changed
    }
}

private fun DbusMessage.stringArg(index: Int): String = when (val value = body.getOrNull(index)) {
    is DbusValue.StringValue -> value.value
    is DbusValue.ObjectPathValue -> value.value
    else -> throw DbusException("argument $index is not a string")
}

private fun DbusMessage.objectPathArg(index: Int): String = when (val value = body.getOrNull(index)) {
    is DbusValue.ObjectPathValue -> value.value
    is DbusValue.StringValue -> value.value
    else -> throw DbusException("argument $index is not an object path")
}

private fun DbusMessage.int64Arg(index: Int): Long = when (val value = body.getOrNull(index)) {
    is DbusValue.Int64Value -> value.value
    else -> throw DbusException("argument $index is not an int64")
}

private fun DbusMessage.variantArg(index: Int): DbusValue = when (val value = body.getOrNull(index)) {
    is DbusValue.VariantValue -> value.value
    else -> throw DbusException("argument $index is not a variant")
}
