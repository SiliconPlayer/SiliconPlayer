package com.flopster101.siliconplayer.library

import com.flopster101.siliconplayer.NativeBridge
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

// One probed file: mirrors NativeBridge.TrackMetadataProbeResult without
// exposing the bridge type to the scanner.
internal data class IsolatedProbeResult(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationSeconds: Double?
)

// Probes run in a child JVM so a native decoder crash
// isolates to one file instead of killing the app.
internal class IsolatedLibraryProber(
    private val batchSize: Int = 64,
    private val batchTimeoutSeconds: Long = 180,
    private val singleTimeoutSeconds: Long = 60,
    private val runBatch: (paths: List<String>, timeoutSeconds: Long) -> BatchProbeOutcome? =
        ::runProbeBatchProcess
) {
    fun probeAll(
        paths: List<String>,
        onProgress: (probed: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<String, IsolatedProbeResult?> {
        val out = HashMap<String, IsolatedProbeResult?>(paths.size)
        probeRecursive(paths, out, paths.size, onProgress)
        return out
    }

    private fun probeRecursive(
        paths: List<String>,
        out: MutableMap<String, IsolatedProbeResult?>,
        total: Int,
        onProgress: (probed: Int, total: Int) -> Unit
    ) {
        if (paths.isEmpty()) return
        if (paths.size > batchSize) {
            // Split first so no batch run ever exceeds the chunk size.
            val mid = paths.size / 2
            probeRecursive(paths.subList(0, mid), out, total, onProgress)
            probeRecursive(paths.subList(mid, paths.size), out, total, onProgress)
            return
        }
        if (paths.size == 1) {
            val outcome = runBatch(paths, singleTimeoutSeconds)
            if (outcome != null) {
                out.putAll(outcome.parsed)
            } else {
                println("[SiliconPlayer] library probe crashed, skipping: ${paths[0]}")
            }
            onProgress(out.size, total)
            return
        }
        val outcome = runBatch(paths, batchTimeoutSeconds)
        if (outcome == null) {
            // Whole batch produced nothing (spawn failure); split blindly.
            val mid = paths.size / 2
            probeRecursive(paths.subList(0, mid), out, total, onProgress)
            probeRecursive(paths.subList(mid, paths.size), out, total, onProgress)
            return
        }
        out.putAll(outcome.parsed)
        onProgress(out.size, total)
        val failed = outcome.unparsed
        if (failed.isEmpty()) return
        val mid = failed.size / 2
        probeRecursive(failed.subList(0, mid), out, total, onProgress)
        probeRecursive(failed.subList(mid, failed.size), out, total, onProgress)
    }
}

// What a batch run produced: parsed results plus the request suffix the
// helper never printed (crashed or timed out mid-batch).
internal data class BatchProbeOutcome(
    val parsed: Map<String, IsolatedProbeResult?>,
    val unparsed: List<String>
)

// Spawns `LibraryProbeMain` in a child JVM inheriting this process's
// classpath and native library path. Never throws; null means the batch
// yielded nothing usable.
internal fun runProbeBatchProcess(paths: List<String>, timeoutSeconds: Long): BatchProbeOutcome? {
    if (paths.isEmpty()) return BatchProbeOutcome(emptyMap(), emptyList())
    val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
    val command = ArrayList<String>(paths.size + 6)
    command.add(javaBin)
    System.getProperty("java.library.path")?.takeIf { it.isNotBlank() }?.let { libraryPath ->
        command.add("-Djava.library.path=$libraryPath")
    }
    command.add("-cp")
    command.add(System.getProperty("java.class.path") ?: return null)
    command.add("com.flopster101.siliconplayer.library.LibraryProbeMain")
    command.addAll(paths)
    val process = runCatching {
        ProcessBuilder(command).redirectErrorStream(true).start()
    }.getOrNull() ?: return null
    val finished = try {
        process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
    if (!finished) {
        process.destroyForcibly()
    }
    val lines = runCatching {
        process.inputStream.bufferedReader().readLines()
    }.getOrElse { emptyList() }
    // Exit code only tells whether the tail survived; keep every line the
    // helper flushed before dying and subdivide the silent suffix.
    val parsed = HashMap<String, IsolatedProbeResult?>(paths.size)
    var parsedCount = 0
    for (line in lines) {
        val result = parseProbeLine(line, paths) ?: continue
        parsed[result.first] = result.second
        parsedCount++
    }
    if (parsedCount == 0 && lines.isNotEmpty()) {
        // Helper spoke but nothing parsed: protocol mismatch, not a crash.
        return BatchProbeOutcome(parsed, emptyList())
    }
    return BatchProbeOutcome(parsed, paths.drop(parsedCount))
}

// Child helper: prints one line per path in order, flushed eagerly.
object LibraryProbeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = System.out
        args.forEachIndexed { index, path ->
            val line = try {
                val probe = NativeBridge.probeMetadata(path, -1)
                if (probe == null) {
                    "$index FAIL"
                } else {
                    "$index OK ${encode(probe.title)} ${encode(probe.artist)} " +
                        "${encode(probe.album)} ${encode(probe.durationSeconds?.toString())}"
                }
            } catch (_: Throwable) {
                "$index FAIL"
            }
            out.println(line)
            out.flush()
        }
    }

    private fun encode(value: String?): String =
        if (value == null) {
            "-"
        } else {
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
        }
}

private fun parseProbeLine(
    line: String,
    paths: List<String>
): Pair<String, IsolatedProbeResult?>? {
    val parts = line.trim().split(' ')
    if (parts.size < 2) return null
    val index = parts[0].toIntOrNull() ?: return null
    if (index < 0 || index >= paths.size) return null
    if (parts[1] != "OK") {
        return paths[index] to null
    }
    if (parts.size != 6) return null
    val duration = if (parts[5] == "-") {
        null
    } else {
        decode(parts[5])?.toDoubleOrNull()
    }
    return paths[index] to IsolatedProbeResult(
        title = decode(parts[2]),
        artist = decode(parts[3]),
        album = decode(parts[4]),
        durationSeconds = duration
    )
}

private fun decode(value: String): String? =
    if (value == "-") {
        null
    } else {
        runCatching { Base64.getDecoder().decode(value).toString(Charsets.UTF_8) }.getOrNull()
    }
