package com.flopster101.siliconplayer

internal object XmpOptionKeys {
    const val INTERPOLATION = "xmp.interpolation"
    const val STEREO_SEPARATION = "xmp.stereo_separation"
    const val AMIGA_STEREO_SEPARATION = "xmp.amiga_stereo_separation"
    const val AMIGA_MODEL = "xmp.amiga_model"
}

internal object XmpConfig {
    val interpolationChoices = listOf(
        IntChoice(0, "Nearest"),
        IntChoice(1, "Linear"),
        IntChoice(2, "Spline")
    )

    val amigaModelChoices = listOf(
        IntChoice(0, "Off"),
        IntChoice(1, "Amiga 500"),
        IntChoice(2, "Amiga 1200")
    )

    fun interpolationOptionValue(mode: Int): String = when (mode) {
        0 -> "nearest"
        2 -> "spline"
        else -> "linear"
    }
}
