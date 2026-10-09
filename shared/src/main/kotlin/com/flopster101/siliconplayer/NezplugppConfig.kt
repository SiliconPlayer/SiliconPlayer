package com.flopster101.siliconplayer

internal object NezplugppOptionKeys {
    const val FILTER = "nezplugpp.filter"
    const val VOLUME_KSS = "nezplugpp.volume_kss"
    const val VOLUME_NSF = "nezplugpp.volume_nsf"
    const val VOLUME_GBS = "nezplugpp.volume_gbs"
    const val VOLUME_HES = "nezplugpp.volume_hes"
    const val VOLUME_SGC = "nezplugpp.volume_sgc"
    const val VOLUME_NSD = "nezplugpp.volume_nsd"
    const val VOLUME_AY = "nezplugpp.volume_ay"
}

internal object NezplugppConfig {
    val filterChoices = listOf(
        IntChoice(0, "None"),
        IntChoice(1, "Low-pass"),
        IntChoice(2, "Weighted"),
        IntChoice(3, "Low-pass + weighted")
    )
}
