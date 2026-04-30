package com.flopster101.siliconplayer

data class PlaylistTargetResolution(
    val source: String,
    val requestUrlHint: String?,
    val subtuneIndex: Int? = null
)

private data class PendingParsedM3uEntry(
    val source: String,
    val requestUrlHint: String?,
    val title: String,
    val artist: String?,
    val embeddedRawSubtuneNumber: Int?,
    val fragmentSubtuneIndex: Int?,
    val durationSecondsOverride: Double?
)

private data class PendingM3uMetadata(
    val title: String,
    val artist: String?,
    val durationSecondsOverride: Double?
)

private data class EmbeddedM3uEntryMetadata(
    val rawTarget: String,
    val rawSubtuneNumber: Int?,
    val title: String?,
    val artist: String?,
    val durationSecondsOverride: Double?
)

fun parseM3uPlaylistLines(
    lines: List<String>,
    title: String,
    format: PlaylistStoredFormat,
    sourceIdHint: String? = null,
    resolveTarget: (rawEntry: String) -> PlaylistTargetResolution?,
    deriveFallbackTitle: (source: String, lineIndex: Int) -> String
): ParsedPlaylistDocument? {
    if (lines.isEmpty()) return null
    var pendingMetadata: PendingM3uMetadata? = null
    val pendingEntries = buildList {
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingMetadata = parseExtInfMetadata(line)
                return@forEachIndexed
            }
            if (line.startsWith("#")) return@forEachIndexed

            val embeddedMetadata = parseEmbeddedM3uEntryMetadata(line)
            val playlistTarget = embeddedMetadata?.rawTarget ?: line
            val stripped = stripRecognizedPlaylistFragment(playlistTarget)
            val normalized = stripped.first.trim()
            if (normalized.isBlank()) return@forEachIndexed

            val resolved = resolveTarget(normalized) ?: return@forEachIndexed
            val metadata = pendingMetadata
            pendingMetadata = null
            val fallbackTitle = deriveFallbackTitle(
                resolved.source,
                index
            )
            add(
                PendingParsedM3uEntry(
                    source = resolved.source,
                    requestUrlHint = resolved.requestUrlHint,
                    title = metadata?.title?.takeIf { it.isNotBlank() }
                        ?: embeddedMetadata?.title?.takeIf { it.isNotBlank() }
                        ?: fallbackTitle,
                    artist = metadata?.artist ?: embeddedMetadata?.artist,
                    embeddedRawSubtuneNumber = embeddedMetadata?.rawSubtuneNumber,
                    fragmentSubtuneIndex = resolved.subtuneIndex ?: stripped.second,
                    durationSecondsOverride = metadata?.durationSecondsOverride ?: embeddedMetadata?.durationSecondsOverride
                )
            )
        }
    }
    if (pendingEntries.isEmpty()) return null
    val embeddedSubtuneMode = determineEmbeddedM3uSubtuneMode(pendingEntries)
    val entries = pendingEntries.map { entry ->
        PlaylistTrackEntry(
            source = entry.source,
            requestUrlHint = entry.requestUrlHint,
            title = entry.title,
            artist = entry.artist,
            subtuneIndex = entry.embeddedRawSubtuneNumber?.let { rawSubtuneNumber ->
                normalizeEmbeddedM3uSubtuneNumber(rawSubtuneNumber, embeddedSubtuneMode)
            } ?: entry.fragmentSubtuneIndex,
            durationSecondsOverride = entry.durationSecondsOverride
        )
    }
    return ParsedPlaylistDocument(
        title = title,
        format = format,
        sourceIdHint = sourceIdHint,
        entries = entries
    )
}

private enum class EmbeddedM3uSubtuneMode {
    ZeroBased,
    HumanOneBased
}

private fun parseExtInfMetadata(line: String): PendingM3uMetadata? {
    val extInfPayload = line.substringAfter(':', missingDelimiterValue = "")
    val durationToken = extInfPayload.substringBefore(',', missingDelimiterValue = "").trim()
    val payload = extInfPayload
        .substringAfter(',', missingDelimiterValue = "")
        .trim()
    val durationSecondsOverride = parsePlaylistDurationSeconds(durationToken)
    if (payload.isBlank() && durationSecondsOverride == null) return null
    val artist = payload.substringBefore(" - ", missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotBlank() }
    return PendingM3uMetadata(
        title = payload,
        artist = artist,
        durationSecondsOverride = durationSecondsOverride
    )
}

private fun parseEmbeddedM3uEntryMetadata(rawEntry: String): EmbeddedM3uEntryMetadata? {
    val separatorIndex = rawEntry.indexOf("::")
    if (separatorIndex <= 0 || separatorIndex >= rawEntry.lastIndex) return null
    val rawTarget = rawEntry.substring(0, separatorIndex).trim()
    if (rawTarget.isBlank()) return null
    val payload = rawEntry.substring(separatorIndex + 2).trim()
    if (payload.isBlank()) return null
    val fields = splitEscapedPlaylistFields(payload)
    if (fields.size < 2) return null
    val rawSubtuneNumber = fields.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    return EmbeddedM3uEntryMetadata(
        rawTarget = rawTarget,
        rawSubtuneNumber = rawSubtuneNumber.coerceAtLeast(0),
        title = fields.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
        artist = null,
        durationSecondsOverride = parsePlaylistDurationSeconds(fields.getOrNull(3))
    )
}

private fun determineEmbeddedM3uSubtuneMode(entries: List<PendingParsedM3uEntry>): EmbeddedM3uSubtuneMode {
    val embeddedSubtuneNumbers = entries.mapNotNull { it.embeddedRawSubtuneNumber }
    if (embeddedSubtuneNumbers.size <= 1) {
        return EmbeddedM3uSubtuneMode.ZeroBased
    }
    return if (embeddedSubtuneNumbers.any { it == 0 }) {
        EmbeddedM3uSubtuneMode.ZeroBased
    } else {
        EmbeddedM3uSubtuneMode.HumanOneBased
    }
}

private fun normalizeEmbeddedM3uSubtuneNumber(
    rawSubtuneNumber: Int,
    mode: EmbeddedM3uSubtuneMode
): Int {
    return when (mode) {
        EmbeddedM3uSubtuneMode.ZeroBased -> rawSubtuneNumber.coerceAtLeast(0)
        EmbeddedM3uSubtuneMode.HumanOneBased -> normalizeHumanPlaylistSubtuneIndex(rawSubtuneNumber) ?: 0
    }
}

private fun splitEscapedPlaylistFields(payload: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var escaping = false
    payload.forEach { char ->
        when {
            escaping -> {
                current.append(char)
                escaping = false
            }
            char == '\\' -> escaping = true
            char == ',' -> {
                fields += current.toString()
                current.setLength(0)
            }
            else -> current.append(char)
        }
    }
    if (escaping) {
        current.append('\\')
    }
    fields += current.toString()
    return fields
}

private fun stripRecognizedPlaylistFragment(rawEntry: String): Pair<String, Int?> {
    val fragmentIndex = rawEntry.lastIndexOf('#')
    if (fragmentIndex < 0 || fragmentIndex == rawEntry.lastIndex) {
        return rawEntry to null
    }
    val fragment = rawEntry.substring(fragmentIndex + 1).trim()
    val subtuneIndex = parsePlaylistSubtuneFragment(fragment) ?: return rawEntry to null
    return rawEntry.substring(0, fragmentIndex) to subtuneIndex
}

private fun parsePlaylistSubtuneFragment(fragment: String): Int? {
    val normalized = fragment.trim()
    if (normalized.isBlank()) return null
    if (normalized.all { it.isDigit() }) {
        return normalizeHumanPlaylistSubtuneIndex(normalized.toIntOrNull())
    }
    val key = normalized.substringBefore('=').trim().lowercase()
    val value = normalized.substringAfter('=', missingDelimiterValue = "").trim()
    if (value.isBlank()) return null
    return when (key) {
        "subtune", "tune", "track" -> normalizeHumanPlaylistSubtuneIndex(value.toIntOrNull())
        "subtune_index", "subtuneindex" -> value.toIntOrNull()?.coerceAtLeast(0)
        else -> null
    }
}

private fun normalizeHumanPlaylistSubtuneIndex(value: Int?): Int? {
    val parsed = value ?: return null
    return if (parsed <= 0) 0 else parsed - 1
}

private fun parsePlaylistDurationSeconds(rawValue: String?): Double? {
    val normalized = rawValue?.trim().orEmpty()
    if (normalized.isBlank()) return null
    if (normalized.contains(':')) {
        val parts = normalized
            .split(':')
            .map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() || it.any { ch -> !ch.isDigit() } }) {
            return null
        }
        var totalSeconds = 0L
        parts.forEach { part ->
            totalSeconds = (totalSeconds * 60L) + part.toLong()
        }
        return totalSeconds.toDouble().takeIf { it > 0.0 }
    }
    val numeric = normalized.toDoubleOrNull() ?: return null
    return numeric.takeIf { it > 0.0 }
}
