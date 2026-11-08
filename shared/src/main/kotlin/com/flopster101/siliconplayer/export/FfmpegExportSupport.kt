package com.flopster101.siliconplayer.export

internal fun ffmpegMetadataArguments(metadata: AudioExportMetadata): List<String> = buildList {
    fun add(name: String, value: String?) {
        if (!value.isNullOrBlank()) {
            add("-metadata")
            add("$name=$value")
        }
    }
    add("title", metadata.title)
    add("artist", metadata.artist)
    add("album", metadata.album)
    add("composer", metadata.composer)
    add("genre", metadata.genre)
    add("date", metadata.date)
    add("copyright", metadata.copyright)
    add("comment", metadata.comment)
}

internal fun parseFfmpegArgumentList(raw: String): List<String> {
    val arguments = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false
    raw.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' && quote != '\'' -> escaped = true
            quote != null && char == quote -> quote = null
            quote != null -> current.append(char)
            char == '\'' || char == '"' -> {
                quote = char
                started = true
            }
            char.isWhitespace() -> {
                if (started || current.isNotEmpty()) {
                    arguments.add(current.toString())
                    current.clear()
                    started = false
                }
            }
            else -> current.append(char)
        }
    }
    require(!escaped && quote == null) { "--ffmpegArgs contains an unterminated quote or escape." }
    if (started || current.isNotEmpty()) arguments.add(current.toString())
    return arguments
}

internal fun easyFfmpegOutputArguments(codec: AudioExportCodec, bitrateBps: Int?): List<String> = buildList {
    add("-c:a")
    add(
        when (codec) {
            AudioExportCodec.Aac -> "aac"
            AudioExportCodec.AmrNb -> "amr_nb"
            AudioExportCodec.Flac -> "flac"
            AudioExportCodec.Mp3 -> "libmp3lame"
            AudioExportCodec.Opus -> "libopus"
            AudioExportCodec.Pcm24 -> "pcm_s24le"
        }
    )
    if (bitrateBps != null && codec !in listOf(AudioExportCodec.Flac, AudioExportCodec.Pcm24)) {
        add("-b:a")
        add(bitrateBps.toString())
    }
}
