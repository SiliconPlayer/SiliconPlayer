package com.flopster101.siliconplayer

// A format name as shown in the player. `full` is the wording we want, and
// `short` is what the player line falls back to when the full one does not
// fit there. Labels stay short for the same reason.
internal data class FormatLabel(val short: String, val full: String)

// Format ids as the cores report them: cores with their own format name
// report it directly, and cores that report nothing fall back to the file
// extension. Adding or changing an entry is all it takes to reword what a
// core shows, including the names a core supplies itself.
private val FORMAT_LABELS: Map<String, FormatLabel> = mapOf(
    // Tracker module formats.
    "MOD" to FormatLabel("MOD", "ProTracker MOD"),
    "XM" to FormatLabel("XM", "FastTracker II XM"),
    "S3M" to FormatLabel("S3M", "Scream Tracker 3"),
    "IT" to FormatLabel("IT", "Impulse Tracker"),
    "FUR" to FormatLabel("FUR", "Furnace"),
    "DMF" to FormatLabel("DMF", "DefleMask Module"),
    "FTM" to FormatLabel("FTM", "FamiTracker Module"),
    "DNM" to FormatLabel("DNM", "Dn-FamiTracker Module"),
    "DNFT" to FormatLabel("DNFT", "Dn-FamiTracker Module"),
    "AHX" to FormatLabel("AHX", "Abyss' Highest eXperience"),
    "HVL" to FormatLabel("HVL", "HivelyTracker"),
    "KT" to FormatLabel("KT", "Klystrack"),
    // AY chip formats.
    "AY" to FormatLabel("AY", "ZX Spectrum AY"),
    "VTX" to FormatLabel("VTX", "Vortex Tracker II"),
    "YM" to FormatLabel("YM", "Atari ST YM"),
    "PT1" to FormatLabel("PT1", "Pro Tracker 1"),
    "PT2" to FormatLabel("PT2", "Pro Tracker 2"),
    "PT3" to FormatLabel("PT3", "Pro Tracker 3"),
    // Console sound formats.
    "NSF" to FormatLabel("NSF", "NES Sound Format"),
    "NSD" to FormatLabel("NSD", "NES Sound Data"),
    "GBS" to FormatLabel("GBS", "Game Boy Sound"),
    "GBR" to FormatLabel("GBR", "Game Boy Rip"),
    "HES" to FormatLabel("HES", "PC Engine HES"),
    "KSS" to FormatLabel("KSS", "MSX KSS"),
    "KSSX" to FormatLabel("KSS", "MSX KSS"),
    "KSCC" to FormatLabel("KSCC", "MSX KSS"),
    "SGC" to FormatLabel("SGC", "Sega 8-bit Sound"),
    "VGM" to FormatLabel("VGM", "Video Game Music"),
    "PSF" to FormatLabel("PSF", "PlayStation Sound Format"),
    "MINIPSF" to FormatLabel("MiniPSF", "PlayStation Sound Format"),
    "2SF" to FormatLabel("2SF", "Nintendo DS Sound Format"),
    "MINI2SF" to FormatLabel("Mini2SF", "Nintendo DS Sound Format"),
    "GSF" to FormatLabel("GSF", "Game Boy Advance Sound Format"),
    "MINIGSF" to FormatLabel("MiniGSF", "Game Boy Advance Sound Format"),
    "USF" to FormatLabel("USF", "Nintendo 64 Sound Format"),
    "MINIUSF" to FormatLabel("MiniUSF", "Nintendo 64 Sound Format")
)

// The System Dolby core still decodes metadata through FFmpeg, so the
// stream-level format name is the one that reaches the player.
internal fun readCurrentFormatLabel(extensionLabel: String): FormatLabel? {
    return try {
        val reported = NativeBridge.getCurrentFormatLabel().trim()
        resolveFormatLabel(reported.ifBlank { extensionLabel })
    } catch (_: Throwable) {
        null
    }
}

internal fun resolveFormatLabel(formatId: String): FormatLabel? {
    val id = formatId.trim()
    if (id.isEmpty()) return null
    return FORMAT_LABELS[id.uppercase()] ?: FormatLabel(short = id, full = id)
}
