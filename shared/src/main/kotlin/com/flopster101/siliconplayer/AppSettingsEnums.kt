package com.flopster101.siliconplayer

enum class LookaheadClipperMode(val storageValue: String, val label: String, val nativeValue: Int) {
    Off("off", "Off", 0),
    Soft("soft", "Soft", 1),
    Hard("hard", "Hard", 2);

    companion object {
        fun fromStorage(value: String?): LookaheadClipperMode {
            return entries.firstOrNull { it.storageValue == value } ?: Soft
        }
    }
}

enum class MultiChannelOutputMode(val storageValue: String, val label: String, val nativeValue: Int) {
    FfmpegOnly("ffmpeg_only", "FFmpeg only", 0),
    AllDecoders("all_decoders", "All decoders", 1),
    Disabled("disabled", "Disabled", 2);

    companion object {
        fun fromStorage(value: String?): MultiChannelOutputMode {
            return entries.firstOrNull { it.storageValue == value } ?: FfmpegOnly
        }
    }
}

enum class FilenameDisplayMode(val storageValue: String, val label: String) {
    Always("always", "Always"),
    Never("never", "Never"),
    TrackerOnly("tracker_only", "Tracker/Chiptune formats only");

    companion object {
        fun fromStorage(value: String?): FilenameDisplayMode {
            return entries.firstOrNull { it.storageValue == value } ?: TrackerOnly
        }
    }
}

enum class EndFadeCurve(val storageValue: String, val label: String, val nativeValue: Int) {
    Linear("linear", "Linear", 0),
    EaseIn("ease_in", "Ease-in", 1),
    EaseOut("ease_out", "Ease-out", 2);

    companion object {
        fun fromStorage(value: String?): EndFadeCurve {
            return entries.firstOrNull { it.storageValue == value } ?: Linear
        }
    }
}

enum class AudioBackendPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    Auto("auto", "Auto (Default)", 0),
    AAudio("aaudio", "AAudio", 1),
    OpenSLES("opensl", "OpenSL ES", 2),
    WASAPI("wasapi", "WASAPI", 3),
    DirectSound("dsound", "DirectSound", 4),
    WinMM("winmm", "WinMM", 5),
    CoreAudio("coreaudio", "Core Audio", 6),
    ALSA("alsa", "ALSA", 7),
    PulseAudio("pulseaudio", "PulseAudio", 8),
    JACK("jack", "JACK", 9),
    Sndio("sndio", "sndio", 10),
    Audio4("audio4", "audio(4)", 11),
    OSS("oss", "OSS", 12),
    NullAudio("null", "Null Audio", 13);

    companion object {
        fun fromStorage(value: String?): AudioBackendPreference {
            if (value == "audiotrack") {
                return AAudio
            }
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}
