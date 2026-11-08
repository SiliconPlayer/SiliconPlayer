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

internal fun createAndroidMediaCodecEncoder(codec: AudioExportCodec): MediaCodec =
    MediaCodec.createEncoderByType(androidMediaCodecMime(codec))
