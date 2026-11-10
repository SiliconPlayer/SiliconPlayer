package com.flopster101.siliconplayer.export

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat

internal fun detectAndroidMediaCodecEncoders(): Set<AudioExportCodec> {
    val codecs = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList() }
        .getOrNull().orEmpty()
    return listOf(
        AudioExportCodec.Aac,
        AudioExportCodec.AmrNb,
        AudioExportCodec.Opus,
        AudioExportCodec.Flac
    ).filterTo(mutableSetOf()) { choice ->
        val mime = androidMediaCodecMime(choice)
        codecs.any { info ->
            info.isEncoder && runCatching {
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }.getOrDefault(false)
        }
    }
}

internal fun androidMediaCodecMime(codec: AudioExportCodec): String = when (codec) {
    AudioExportCodec.Aac -> MediaFormat.MIMETYPE_AUDIO_AAC
    AudioExportCodec.AmrNb -> MediaFormat.MIMETYPE_AUDIO_AMR_NB
    AudioExportCodec.Opus -> MediaFormat.MIMETYPE_AUDIO_OPUS
    AudioExportCodec.Flac -> MediaFormat.MIMETYPE_AUDIO_FLAC
    else -> throw IllegalArgumentException("$codec is not a MediaCodec output")
}

/**
 * Creates an encoder for [codec], preferring a vendor implementation over a
 * software one. MediaCodec's default pick is often the generic `c2.android.*`
 * encoder even when a faster vendor encoder is installed.
 */
internal fun createAndroidMediaCodecEncoder(codec: AudioExportCodec): MediaCodec {
    val mime = androidMediaCodecMime(codec)
    val candidates = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { info ->
                info.isEncoder && runCatching {
                    info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                }.getOrDefault(false)
            }
            .sortedBy { it.isSoftwareOnly }
    }.getOrDefault(emptyList())
    candidates.forEach { info ->
        runCatching { return MediaCodec.createByCodecName(info.name) }
    }
    return MediaCodec.createEncoderByType(mime)
}
