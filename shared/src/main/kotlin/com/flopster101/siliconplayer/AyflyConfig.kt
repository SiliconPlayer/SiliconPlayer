package com.flopster101.siliconplayer

internal object AyflyOptionKeys {
    const val OVERSAMPLE = "ayfly.oversample"
    const val CHIP_TYPE = "ayfly.chip_type"
    const val MIX_TYPE = "ayfly.mix_type"
    const val INT_FREQ = "ayfly.int_freq"
}

internal object AyflyConfig {
    val oversampleChoices = listOf(
        IntChoice(1, "1x"),
        IntChoice(2, "2x"),
        IntChoice(4, "4x"),
        IntChoice(8, "8x")
    )

    val chipChoices = listOf(
        IntChoice(-1, "Auto"),
        IntChoice(0, "AY-3-8910"),
        IntChoice(1, "YM2149")
    )

    val mixChoices = listOf(
        IntChoice(-1, "Auto"),
        IntChoice(0, "ABC"),
        IntChoice(1, "ACB"),
        IntChoice(2, "BAC"),
        IntChoice(3, "BCA"),
        IntChoice(4, "CAB"),
        IntChoice(5, "CBA")
    )

    val intFreqChoices = listOf(
        IntChoice(0, "Auto"),
        IntChoice(50, "50 Hz"),
        IntChoice(60, "60 Hz"),
        IntChoice(100, "100 Hz"),
        IntChoice(120, "120 Hz")
    )
}
