package com.flopster101.siliconplayer

internal object XmpOptionKeys {
    const val INTERPOLATION = "xmp.interpolation"
    const val STEREO_SEPARATION = "xmp.stereo_separation"
    const val AMIGA_STEREO_SEPARATION = "xmp.amiga_stereo_separation"
    const val AMIGA_MIXING = "xmp.amiga_mixing"
}

internal object XmpConfig {
    val interpolationChoices = listOf(
        IntChoice(0, "Nearest"),
        IntChoice(1, "Linear"),
        IntChoice(2, "Spline")
    )

    fun interpolationOptionValue(mode: Int): String = when (mode) {
        0 -> "nearest"
        2 -> "spline"
        else -> "linear"
    }
}
