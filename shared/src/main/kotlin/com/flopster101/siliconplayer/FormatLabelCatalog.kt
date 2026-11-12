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
    "EFT" to FormatLabel("EFT", "FamiTracker Module"),
    "FC" to FormatLabel("FC", "Future Composer"),
    "TFM" to FormatLabel("TFM", "TFM Music Maker"),
    "TFE" to FormatLabel("TFE", "TFM Music Maker"),
    "AHX" to FormatLabel("AHX", "Abyss' Highest eXperience"),
    "HVL" to FormatLabel("HVL", "HivelyTracker"),
    "KT" to FormatLabel("KT", "Klystrack"),
    "SNDH" to FormatLabel("SNDH", "Atari ST SNDH"),
    // AY chip formats.
    "AY" to FormatLabel("AY", "ZX Spectrum AY"),
    "VTX" to FormatLabel("VTX", "Vortex Tracker II"),
    "YM" to FormatLabel("YM", "Atari ST YM"),
    "PT1" to FormatLabel("PT1", "Pro Tracker 1"),
    "PT2" to FormatLabel("PT2", "Pro Tracker 2"),
    "PT3" to FormatLabel("PT3", "Pro Tracker 3"),
    "ASC" to FormatLabel("ASC", "ASC Sound Master"),
    "STC" to FormatLabel("STC", "Sound Tracker"),
    "STP" to FormatLabel("STP", "Sound Tracker Pro"),
    "SQT" to FormatLabel("SQT", "SQ-Tracker"),
    "PSC" to FormatLabel("PSC", "Pro Sound Creator"),
    "PSG" to FormatLabel("PSG", "PSG Register Dump"),
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
    "MINIUSF" to FormatLabel("MiniUSF", "Nintendo 64 Sound Format"),
    // FFmpeg names a bare file by its demuxer and one inside a container by
    // its codec, so the same format shows up under either spelling.
    "MP2/3 (MPEG audio layer 2/3)" to FormatLabel("MPEG Audio", "MPEG Audio Layer 2/3"),
    "MP1 (MPEG audio layer 1)" to FormatLabel("MP1", "MPEG Audio Layer 1"),
    "MP2 (MPEG audio layer 2)" to FormatLabel("MP2", "MPEG Audio Layer 2"),
    "MP3 (MPEG audio layer 3)" to FormatLabel("MP3", "MPEG Audio Layer 3"),
    "raw ADTS AAC (Advanced Audio Coding)" to FormatLabel("AAC", "Advanced Audio Coding"),
    "AAC (Advanced Audio Coding)" to FormatLabel("AAC", "Advanced Audio Coding"),
    "raw FLAC" to FormatLabel("FLAC", "Free Lossless Audio Codec"),
    "FLAC (Free Lossless Audio Codec)" to FormatLabel("FLAC", "Free Lossless Audio Codec"),
    "ALAC (Apple Lossless Audio Codec)" to FormatLabel("ALAC", "Apple Lossless Audio Codec"),
    "raw AC-3" to FormatLabel("AC-3", "Dolby Digital (AC-3)"),
    "ATSC A/52A (AC-3)" to FormatLabel("AC-3", "Dolby Digital (AC-3)"),
    "raw E-AC-3" to FormatLabel("E-AC-3", "Dolby Digital Plus (E-AC-3)"),
    "ATSC A/52B (AC-3, E-AC-3)" to FormatLabel("E-AC-3", "Dolby Digital Plus (E-AC-3)"),
    "raw AC-4" to FormatLabel("AC-4", "Dolby AC-4"),
    "raw TrueHD" to FormatLabel("TrueHD", "Dolby TrueHD"),
    "TrueHD" to FormatLabel("TrueHD", "Dolby TrueHD"),
    "raw MLP" to FormatLabel("MLP", "Meridian Lossless Packing"),
    "raw DTS" to FormatLabel("DTS", "DTS Coherent Acoustics"),
    "raw DTS-HD" to FormatLabel("DTS-HD", "DTS-HD"),
    "DCA (DTS Coherent Acoustics)" to FormatLabel("DTS", "DTS Coherent Acoustics"),
    "raw SBC (low-complexity subband codec)" to FormatLabel("SBC", "Low Complexity Subband Codec"),
    "raw TAK" to FormatLabel("TAK", "Tom's Lossless Audio Kompressor"),
    "TAK (Tom's lossless Audio Kompressor)" to FormatLabel("TAK", "Tom's Lossless Audio Kompressor"),
    "raw Shorten" to FormatLabel("SHN", "Shorten"),
    "Shorten" to FormatLabel("SHN", "Shorten"),
    "TTA (True Audio)" to FormatLabel("TTA", "True Audio"),
    "QOA (Quite OK Audio)" to FormatLabel("QOA", "Quite OK Audio"),
    "Cook / Cooker / Gecko (RealAudio G2)" to FormatLabel("Cook", "RealAudio G2"),
    "WAV / WAVE (Waveform Audio)" to FormatLabel("WAV", "Waveform Audio"),
    "Audio IFF" to FormatLabel("AIFF", "Audio Interchange File Format"),
    "Apple CAF (Core Audio Format)" to FormatLabel("CAF", "Core Audio Format"),
    "Sony Wave64" to FormatLabel("W64", "Sony Wave64"),
    "3GPP AMR" to FormatLabel("AMR", "Adaptive Multi-Rate"),
    "DSD Stream File (DSF)" to FormatLabel("DSF", "DSD Stream File"),
    "Apple HTTP Live Streaming" to FormatLabel("HLS", "HTTP Live Streaming"),
    "raw MPEG video" to FormatLabel("MPEG", "MPEG Video"),
    "Sun AU" to FormatLabel("AU", "Sun Audio"),
    "Asterisk raw pcm" to FormatLabel("SLN", "Asterisk RAW PCM"),
    "Raw Immersive Audio Model and Formats" to FormatLabel("IAMF", "Immersive Audio Model and Formats"),
    "Audible AA format files" to FormatLabel("AA", "Audible Audiobook"),
    "codec2 .c2 demuxer" to FormatLabel("C2", "Codec2"),
    "NIST SPeech HEader REsources" to FormatLabel("NIST", "NIST Speech Header Resources")
)

// A core's own casing carries no meaning, so ids are matched without it.
private val FORMAT_LABELS_BY_ID: Map<String, FormatLabel> =
    FORMAT_LABELS.mapKeys { (id, _) -> id.uppercase() }

// The Dolby MediaCodec core still decodes metadata through FFmpeg, so the
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
    return FORMAT_LABELS_BY_ID[id.uppercase()] ?: FormatLabel(short = id, full = id)
}
