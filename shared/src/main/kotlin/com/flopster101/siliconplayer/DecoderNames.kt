package com.flopster101.siliconplayer

internal object DecoderNames {
    const val FFMPEG = "FFmpeg"
    const val PLATFORM_DOLBY = "System Dolby"
    const val LIB_OPEN_MPT = "libopenmpt"
    const val LIBXMP = "libxmp"
    const val UFMOD = "uFMOD-C"
    const val AYFLY = "ayfly"
    const val VGM_PLAY = "VGMPlay"
    const val GAME_MUSIC_EMU = "Game Music Emu"
    const val C_RSID = "cRSID"
    const val LIB_SID_PLAY_FP = "libsidplayfp"
    const val LAZY_USF2 = "lazyusf2"
    const val VIO2_SF = "vio2sf"
    const val LIB_UPSE = "libupse"
    const val VIOGSF = "viogsf"
    const val NEZPLUGPP = "NEZplug++"
    const val SC68 = "SC68"
    const val AD_PLUG = "AdPlug"
    const val UADE = "UADE"
    const val HIVELY_TRACKER = "HivelyTracker"
    const val KLYSTRACK = "klystrack-plus"
    const val FURNACE = "Furnace"
    const val LIB_DN_FAMITRACKER = "libdnfamitracker"

    val trackedFileDecoders: Set<String> = setOf(
        LIB_OPEN_MPT,
        LIBXMP,
        UFMOD,
        AYFLY,
        VGM_PLAY,
        C_RSID,
        LIB_SID_PLAY_FP,
        SC68,
        AD_PLUG,
        UADE,
        HIVELY_TRACKER,
        KLYSTRACK,
        FURNACE,
        LIB_DN_FAMITRACKER
    )

    val gameFileDecoders: Set<String> = setOf(
        GAME_MUSIC_EMU,
        NEZPLUGPP,
        LAZY_USF2,
        VIO2_SF,
        LIB_UPSE,
        VIOGSF
    )
}

internal fun canonicalDecoderNameForAlias(coreName: String?): String? {
    return when (coreName?.trim()?.lowercase()) {
        "ffmpeg" -> DecoderNames.FFMPEG
        "libopenmpt", "openmpt" -> DecoderNames.LIB_OPEN_MPT
        "libxmp", "xmp" -> DecoderNames.LIBXMP
        "ufmod-c", "ufmod", "u fmod" -> DecoderNames.UFMOD
        "ayfly", "libayfly" -> DecoderNames.AYFLY
        "vgmplay" -> DecoderNames.VGM_PLAY
        "game music emu", "libgme", "gme" -> DecoderNames.GAME_MUSIC_EMU
        "crsid", "c-rsid", "c rsid", "sid" -> DecoderNames.C_RSID
        "libsidplayfp", "sidplayfp" -> DecoderNames.LIB_SID_PLAY_FP
        "lazyusf2", "lazyusf", "usf" -> DecoderNames.LAZY_USF2
        "vio2sf", "2sf", "mini2sf" -> DecoderNames.VIO2_SF
        "libupse", "upse", "psf", "minipsf" -> DecoderNames.LIB_UPSE
        "viogsf", "gsf", "minigsf" -> DecoderNames.VIOGSF
        "nezplugpp", "nezplug++", "kss" -> DecoderNames.NEZPLUGPP
        "sc68", "sndh" -> DecoderNames.SC68
        "adplug", "opl" -> DecoderNames.AD_PLUG
        "hivelytracker", "hively", "hvl", "ahx" -> DecoderNames.HIVELY_TRACKER
        "klystrack-plus", "klystrack", "kly", "kt" -> DecoderNames.KLYSTRACK
        "furnace", "fur", "dmf" -> DecoderNames.FURNACE
        "libdnfamitracker", "dnfamitracker", "famitracker", "dn-famitracker", "dnft" -> DecoderNames.LIB_DN_FAMITRACKER
        "uade", "amiga" -> DecoderNames.UADE
        else -> null
    }
}

internal fun String?.matchesDecoderName(canonicalName: String): Boolean {
    return canonicalDecoderNameForAlias(this)?.equals(canonicalName, ignoreCase = true) == true
}
