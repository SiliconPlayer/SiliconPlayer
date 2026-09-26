package com.flopster101.siliconplayer.library

import com.flopster101.siliconplayer.NativeBridge
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// One probed file: mirrors NativeBridge.TrackMetadataProbeResult without
// exposing the bridge type to the scanner.
internal data class IsolatedProbeResult(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationSeconds: Double?
)

// Tag probes run in one long-lived child JVM (one spawn per scan, not per
// batch) so a native decoder crash can never take the app down. Lockstep
// request/reply pinpoints the killer: a dead or hung helper restarts and
// the in-flight file retries once — dying twice means killer, skip it.
internal class IsolatedLibraryProber(
    private val perFileTimeoutSeconds: Long = 30,
    private val transportProvider: () -> ProbeTransport = { ProcessProbeTransport() }
) {
    // Every path that died twice (confirmed killers), for session skip lists.
    val killedPaths = mutableSetOf<String>()

    fun probeAll(
        paths: List<String>,
        onProgress: (probed: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<String, IsolatedProbeResult> {
        val out = HashMap<String, IsolatedProbeResult>(paths.size)
        if (paths.isEmpty()) return out
        transportProvider().use { transport ->
            paths.forEachIndexed { index, path ->
                var result = transport.probe(path, perFileTimeoutSeconds)
                if (result === ProbeTransport.DEAD) {
                    result = transport.probe(path, perFileTimeoutSeconds)
                    if (result === ProbeTransport.DEAD) {
                        println("[SiliconPlayer] library probe crashed, skipping: $path")
                        killedPaths.add(path)
                        result = null
                    }
                }
                if (result != null) out[path] = result
                onProgress(index + 1, paths.size)
            }
        }
        return out
    }
}

// Lockstep probe channel. probe() returns the result, null for a failed
// open, or DEAD when the helper died/hung mid-file (already restarted).
internal interface ProbeTransport : AutoCloseable {
    fun probe(path: String, timeoutSeconds: Long): IsolatedProbeResult?

    companion object {
        val DEAD: IsolatedProbeResult? = IsolatedProbeResult("\u0000DEAD\u0000", null, null, null)
    }
}

// Child-JVM transport speaking the LibraryProbeMain line protocol.
internal class ProcessProbeTransport : ProbeTransport {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val readExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "library-probe-reader").apply { isDaemon = true }
    }

    override fun probe(path: String, timeoutSeconds: Long): IsolatedProbeResult? {
        if (!ensureStarted()) return ProbeTransport.DEAD
        val encoded = Base64.getEncoder().encodeToString(path.toByteArray(Charsets.UTF_8))
        if (!writeLine("PROBE $encoded")) return deadAndRestart()
        val reply = readReply(timeoutSeconds) ?: return deadAndRestart()
        return parseReply(reply)
    }

    override fun close() {
        runCatching { writeLine("QUIT") }
        process?.destroyForcibly()
        readExecutor.shutdownNow()
        process = null
    }

    private fun deadAndRestart(): IsolatedProbeResult? {
        restart()
        return ProbeTransport.DEAD
    }

    private fun restart() {
        process?.destroyForcibly()
        process = null
        ensureStarted()
    }

    private fun ensureStarted(): Boolean {
        if (process?.isAlive == true) return true
        val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val command = ArrayList<String>(6)
        command.add(javaBin)
        System.getProperty("java.library.path")?.takeIf { it.isNotBlank() }?.let { libraryPath ->
            command.add("-Djava.library.path=$libraryPath")
        }
        val classpath = System.getProperty("java.class.path")?.takeIf { it.isNotBlank() } ?: return false
        command.add("-cp")
        command.add(classpath)
        command.add("com.flopster101.siliconplayer.library.LibraryProbeMain")
        val started = runCatching {
            ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrNull() ?: return false
        process = started
        reader = started.inputStream.bufferedReader()
        writer = started.outputStream.bufferedWriter()
        return true
    }

    private fun writeLine(line: String): Boolean {
        val stream = writer ?: return false
        return try {
            stream.write(line)
            stream.newLine()
            stream.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readReply(timeoutSeconds: Long): String? {
        val stream = reader ?: return null
        val future = readExecutor.submit<String> { stream.readLine() }
        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            null
        }
    }

    private fun parseReply(line: String): IsolatedProbeResult? {
        val parts = line.trim().split(' ')
        if (parts.size != 5 || parts[0] != "OK") return null
        return IsolatedProbeResult(
            title = decode(parts[1]),
            artist = decode(parts[2]),
            album = decode(parts[3]),
            durationSeconds = decode(parts[4])?.toDoubleOrNull()
        )
    }

    private fun decode(value: String): String? =
        if (value == "-") {
            null
        } else {
            runCatching { Base64.getDecoder().decode(value).toString(Charsets.UTF_8) }.getOrNull()
        }
}

// Helper entry point. With file args it probes each and exits (debugging);
// with no args it serves PROBE <b64path> lines on stdin until QUIT/EOF,
// replying `OK <b64> <b64> <b64> <b64>` or `FAIL` per line, flushed eagerly.
object LibraryProbeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isNotEmpty()) {
            args.forEach { printResult(probePath(it)) }
            return
        }
        val input = System.`in`.bufferedReader()
        while (true) {
            val line = try {
                input.readLine() ?: break
            } catch (_: Exception) {
                break
            }
            if (line == "QUIT") break
            val path = line.removePrefix("PROBE ")
                .takeIf { it != line }?.let { decodePath(it) } ?: continue
            printResult(probePath(path))
        }
    }

    private fun probePath(path: String): String {
        return try {
            val probe = NativeBridge.probeMetadata(path, -1)
            if (probe == null) {
                "FAIL"
            } else {
                "OK ${encode(probe.title)} ${encode(probe.artist)} " +
                    "${encode(probe.album)} ${encode(probe.durationSeconds?.toString())}"
            }
        } catch (_: Throwable) {
            "FAIL"
        }
    }

    private fun printResult(line: String) {
        System.out.println(line)
        System.out.flush()
    }

    private fun encode(value: String?): String =
        if (value == null) {
            "-"
        } else {
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
        }

    private fun decodePath(value: String): String? =
        runCatching { Base64.getDecoder().decode(value).toString(Charsets.UTF_8) }.getOrNull()
}
