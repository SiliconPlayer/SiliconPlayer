package com.flopster101.siliconplayer.mpris

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal const val DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER = 1
internal const val DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER = 4
internal const val DBUS_NAME_FLAG_DO_NOT_QUEUE = 0x4

internal data class DbusEndpoint(val path: String)

internal data class DbusConnectionResult(val connection: DbusConnection, val ownsName: Boolean)

// Minimal session-bus client: enough of the protocol to own a well-known name,
// answer method calls and emit signals, with no native D-Bus dependency.
internal class DbusConnection(
    val endpoint: String,
    private val channel: SocketChannel,
    private val input: BufferedInputStream,
    private val output: OutputStream,
    private val serialCounter: AtomicInteger
) : Closeable {
    @Volatile
    var uniqueName: String = ""
        private set

    private val pendingReplies = ConcurrentHashMap<Int, BlockingQueue<DbusMessage>>()
    private val writeLock = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var dispatcher: Executor? = null

    @Volatile
    private var onMethodCall: (DbusMessage) -> Unit = {}

    // Fires when the bus drops us, so the owner can register again.
    @Volatile
    var onClosed: (() -> Unit)? = null

    private val readerThread = Thread({ readLoop() }, "mpris-dbus-reader").apply {
        isDaemon = true
        start()
    }

    // The reader owns the socket from the start: the handshake itself needs the
    // Hello/RequestName replies, which arrive before the service registers.
    fun startReading(dispatcher: Executor, onMessage: (DbusMessage) -> Unit) {
        this.dispatcher = dispatcher
        this.onMethodCall = onMessage
    }

    fun send(message: DbusMessage) {
        val frame = message.encode()
        synchronized(writeLock) {
            if (closed) throw DbusException("connection closed")
            output.write(frame)
            output.flush()
        }
    }

    fun call(
        destination: String,
        path: String,
        interfaceName: String,
        member: String,
        body: List<DbusValue> = emptyList(),
        timeoutMs: Long = 5000
    ): List<DbusValue> {
        val serial = serialCounter.incrementAndGet()
        val queue = LinkedBlockingQueue<DbusMessage>()
        pendingReplies[serial] = queue
        try {
            send(
                DbusMessage(
                    type = DBUS_TYPE_METHOD_CALL,
                    serial = serial,
                    path = path,
                    interfaceName = interfaceName,
                    member = member,
                    destination = destination,
                    signature = body.signatureOrNull(),
                    body = body
                )
            )
            val reply = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw DbusException("no reply to $member within ${timeoutMs}ms")
            if (reply.type == DBUS_TYPE_ERROR) throw DbusException("bus error from $member: ${reply.errorName}")
            return reply.body
        } finally {
            pendingReplies.remove(serial)
        }
    }

    fun emit(path: String, interfaceName: String, member: String, body: List<DbusValue> = emptyList()) {
        send(
            DbusMessage(
                type = DBUS_TYPE_SIGNAL,
                serial = serialCounter.incrementAndGet(),
                path = path,
                interfaceName = interfaceName,
                member = member,
                signature = body.signatureOrNull(),
                body = body
            )
        )
    }

    fun replyTo(request: DbusMessage, body: List<DbusValue> = emptyList()) {
        if (request.flags and DBUS_FLAG_NO_REPLY_EXPECTED != 0) return
        send(
            DbusMessage(
                type = DBUS_TYPE_METHOD_RETURN,
                serial = serialCounter.incrementAndGet(),
                replySerial = request.serial,
                destination = request.sender,
                signature = body.signatureOrNull(),
                body = body
            )
        )
    }

    fun errorReplyTo(request: DbusMessage, errorName: String, text: String) {
        if (request.flags and DBUS_FLAG_NO_REPLY_EXPECTED != 0) return
        send(
            DbusMessage(
                type = DBUS_TYPE_ERROR,
                serial = serialCounter.incrementAndGet(),
                errorName = errorName,
                replySerial = request.serial,
                destination = request.sender,
                signature = "s",
                body = listOf(DbusValue.StringValue(text))
            )
        )
    }

    override fun close() {
        closed = true
        runCatching { channel.close() }
        readerThread.interrupt()
    }

    private fun readLoop() {
        try {
            while (!closed) {
                val frame = readFrame() ?: break
                val message = runCatching { parseDbusMessage(frame) }.getOrNull() ?: continue
                if (message.type == DBUS_TYPE_METHOD_CALL) {
                    val target = dispatcher
                    if (target != null) target.execute { runCatching { onMethodCall(message) } } else onMethodCall(message)
                } else {
                    val replySerial = message.replySerial
                    if (replySerial != null) pendingReplies[replySerial]?.offer(message)
                }
            }
        } catch (_: IOException) {
            // The socket closed or the bus dropped us; onClosed reports it.
        } finally {
            onClosed?.invoke()
        }
    }

    private fun readFrame(): ByteArray? {
        val fixed = ByteArray(DBUS_FIXED_HEADER_SIZE)
        if (!input.readFullyOrNull(fixed)) return null
        if (fixed[0] != 'l'.code.toByte()) throw DbusException("big-endian frames are not supported")
        val bodyLength = littleEndianUint32(fixed, 4)
        val fieldsLength = littleEndianUint32(fixed, 12)
        val padding = (8 - ((DBUS_FIXED_HEADER_SIZE + fieldsLength) % 8)) % 8
        val remaining = ByteArray((fieldsLength + padding + bodyLength).toInt())
        if (!input.readFullyOrNull(remaining)) return null
        return fixed + remaining
    }

    internal fun adoptUniqueName(name: String) {
        uniqueName = name
    }

    companion object {
        fun littleEndianUint32(source: ByteArray, offset: Int): Long {
            var value = 0L
            for (index in 0..3) value = value or ((source[offset + index].toLong() and 0xFF) shl (index * 8))
            return value
        }
    }
}

private fun List<DbusValue>.signatureOrNull(): String? = joinToString("") { it.signature() }.ifEmpty { null }

private fun InputStream.readFullyOrNull(destination: ByteArray): Boolean {
    var read = 0
    while (read < destination.size) {
        val count = read(destination, read, destination.size - read)
        if (count < 0) return false
        read += count
    }
    return true
}

internal object DbusSessionBus {
    fun connect(
        name: String,
        onFailure: (String) -> Unit = {},
        env: (String) -> String? = System::getenv
    ): DbusConnectionResult? {
        val endpoints = resolveEndpoints(env)
        if (endpoints.isEmpty()) {
            onFailure("no session bus address")
            return null
        }
        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            val channel = runCatching { openSocket(endpoint) }.getOrElse {
                lastError = it
                onFailure("cannot open ${endpoint.path}: ${it.message}")
                continue
            }
            val connection = runCatching { authenticateAndHandshake(endpoint, channel) }.getOrElse {
                lastError = it
                runCatching { channel.close() }
                onFailure("handshake failed: ${it.message}")
                continue
            }
            val ownsName = runCatching { requestName(connection, name) }.getOrElse {
                lastError = it
                onFailure("cannot own $name: ${it.message}")
                runCatching { connection.close() }
                continue
            }
            if (!ownsName) {
                // One media player per well-known name: a second instance stays off the bus.
                runCatching { connection.close() }
                onFailure("$name is already owned by another instance")
                return null
            }
            return DbusConnectionResult(connection, ownsName)
        }
        if (lastError != null) onFailure("last bus error: ${lastError.message}")
        return null
    }

    // Abstract-namespace addresses are skipped: UnixDomainSocketAddress.of only binds real paths.
    fun resolveEndpoints(env: (String) -> String? = System::getenv): List<DbusEndpoint> {
        val candidates = mutableListOf<DbusEndpoint>()
        val address = env("DBUS_SESSION_BUS_ADDRESS")
        if (!address.isNullOrBlank()) {
            address.split(';').forEach { entry ->
                val parts = entry.split(':', limit = 2)
                if (parts.size != 2 || parts[0] != "unix") return@forEach
                val values = parts[1].split(',').mapNotNull { field ->
                    val pair = field.split('=', limit = 2)
                    if (pair.size == 2) pair[0] to pair[1] else null
                }.toMap()
                values["path"]?.takeIf { it.isNotEmpty() }?.let { candidates += DbusEndpoint(it) }
            }
        }
        env("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let { candidates += DbusEndpoint("$it/bus") }
        return candidates.distinct()
    }

    private fun openSocket(endpoint: DbusEndpoint): SocketChannel =
        SocketChannel.open(StandardProtocolFamily.UNIX)
            .apply { connect(UnixDomainSocketAddress.of(Path.of(endpoint.path))) }

    private fun authenticateAndHandshake(endpoint: DbusEndpoint, channel: SocketChannel): DbusConnection {
        val input = BufferedInputStream(Channels.newInputStream(channel))
        val output = Channels.newOutputStream(channel)
        output.write(0)
        output.flush()
        val identity = currentUid()?.toString() ?: "siliconplayer"
        var line = sendAuth(input, output, "EXTERNAL", identity)
        if (!line.startsWith("OK")) line = sendAuth(input, output, "ANONYMOUS", "siliconplayer")
        if (!line.startsWith("OK")) throw DbusException("authentication rejected: $line")
        output.write("BEGIN\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.flush()
        val connection = DbusConnection(endpoint.path, channel, input, output, AtomicInteger(1))
        val hello = connection.call(DBUS_SERVICE, DBUS_PATH, DBUS_INTERFACE, "Hello")
        val uniqueName = (hello.firstOrNull() as? DbusValue.StringValue)?.value
            ?: throw DbusException("Hello returned no unique name")
        connection.adoptUniqueName(uniqueName)
        return connection
    }

    private fun sendAuth(input: InputStream, output: OutputStream, mechanism: String, identity: String): String {
        output.write("AUTH $mechanism ${toHex(identity)}\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.flush()
        return readAsciiLine(input) ?: throw DbusException("bus closed during authentication")
    }

    private fun readAsciiLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return null
            if (value == '\n'.code) return builder.toString().removeSuffix("\r")
            builder.append(value.toChar())
        }
    }

    private fun requestName(connection: DbusConnection, name: String): Boolean {
        val reply = connection.call(
            destination = DBUS_SERVICE,
            path = DBUS_PATH,
            interfaceName = DBUS_INTERFACE,
            member = "RequestName",
            body = listOf(
                DbusValue.StringValue(name),
                DbusValue.Uint32Value(DBUS_NAME_FLAG_DO_NOT_QUEUE.toLong())
            )
        )
        val code = (reply.firstOrNull() as? DbusValue.Uint32Value)?.value?.toInt() ?: 0
        return code == DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER || code == DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER
    }

    private fun currentUid(): Int? {
        val status = File("/proc/self/status")
        if (!status.isFile) return null
        val line = status.useLines { lines -> lines.firstOrNull { it.startsWith("Uid:") } } ?: return null
        return line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
    }

    private fun toHex(value: String): String {
        val digits = "0123456789abcdef"
        val builder = StringBuilder()
        value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            builder.append(digits[unsigned shr 4])
            builder.append(digits[unsigned and 0xF])
        }
        return builder.toString()
    }
}
