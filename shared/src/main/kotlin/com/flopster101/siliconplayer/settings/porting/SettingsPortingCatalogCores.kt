package com.flopster101.siliconplayer.settings.porting

import com.flopster101.siliconplayer.AdPlugDefaults
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AyflyDefaults
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.CrsidDefaults
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.DnfamitrackerDefaults
import com.flopster101.siliconplayer.FfmpegDefaults
import com.flopster101.siliconplayer.FurnaceDefaults
import com.flopster101.siliconplayer.GmeDefaults
import com.flopster101.siliconplayer.HivelyTrackerDefaults
import com.flopster101.siliconplayer.KlystrackDefaults
import com.flopster101.siliconplayer.LazyUsf2Defaults
import com.flopster101.siliconplayer.LibupseDefaults
import com.flopster101.siliconplayer.NezplugppDefaults
import com.flopster101.siliconplayer.OpenMptDefaults
import com.flopster101.siliconplayer.Sc68Defaults
import com.flopster101.siliconplayer.SidPlayFpDefaults
import com.flopster101.siliconplayer.UadeDefaults
import com.flopster101.siliconplayer.UfmodDefaults
import com.flopster101.siliconplayer.VgmPlayDefaults
import com.flopster101.siliconplayer.Vio2sfDefaults
import com.flopster101.siliconplayer.ViogsfDefaults
import com.flopster101.siliconplayer.XmpDefaults
import com.flopster101.siliconplayer.platform.AppPreferences

internal fun boolV(key: String, default: Boolean, onlyWhenSet: Boolean = false) =
    TypedPrefValue(key, PrefType.Boolean, { default }, onlyWhenSet)
internal fun intV(key: String, default: Int, onlyWhenSet: Boolean = false) =
    TypedPrefValue(key, PrefType.Int, { default }, onlyWhenSet)
internal fun floatV(key: String, default: Float, onlyWhenSet: Boolean = false) =
    TypedPrefValue(key, PrefType.Float, { default }, onlyWhenSet)
internal fun stringV(key: String, default: String, onlyWhenSet: Boolean = false) =
    TypedPrefValue(key, PrefType.String, { default }, onlyWhenSet)
internal fun stringSetV(key: String, default: Set<String> = emptySet(), onlyWhenSet: Boolean = false) =
    TypedPrefValue(key, PrefType.StringSet, { default }, onlyWhenSet)

internal fun holder(
    id: String,
    label: String,
    keys: List<TypedPrefValue>,
    defaultSelected: Boolean = true,
    isKeyImportable: (String, PrefType, Any?, PortingPlatformFamily) -> Boolean = { _, _, _, _ -> true },
    children: List<PortingNode.EntryHolder> = emptyList()
) = PortingNode.EntryHolder(
    PortingEntry(id, label, defaultSelected, null, keys, isKeyImportable),
    children
)

internal fun sanitizeDecoderToken(name: String): String = buildString {
    name.forEach { c -> if (c.isLetterOrDigit()) append(c.lowercaseChar()) }
}.ifEmpty { "unknown" }

internal fun isDecoderSupportedOnPortingPlatform(
    decoder: String,
    platform: PortingPlatformFamily
): Boolean {
    val decoderPlatform = when (platform) {
        PortingPlatformFamily.Android -> com.flopster101.siliconplayer.DecoderPlatform.Android
        PortingPlatformFamily.Desktop -> com.flopster101.siliconplayer.DecoderPlatform.Desktop
    }
    return com.flopster101.siliconplayer.isDecoderSupportedOnPlatform(decoder, decoderPlatform)
}

internal fun coresPriorityPortingEntries(decoders: List<String>): List<PortingNode> = listOf(
    holder(
        "cores.order",
        "Audio cores order",
        decoders.map { intV("decoder_${it}_priority", PRIORITY_FALLBACK_DEFAULT, onlyWhenSet = true) }
    ),
    holder(
        "cores.extensions",
        "Audio cores extensions",
        decoders.map { stringSetV("decoder_${it}_enabled_extensions", onlyWhenSet = true) }
    )
)

internal fun coresEnabledStateEntry(decoders: List<String>): PortingNode = holder(
    "cores.enabled",
    "Enabled state",
    decoders.map { boolV("decoder_${it}_enabled", true) },
    isKeyImportable = { key, _, _, platform ->
        val decoder = key.removePrefix("decoder_").removeSuffix("_enabled")
        isDecoderSupportedOnPortingPlatform(decoder, platform)
    }
)

// Priority keys are always explicit once priority editing has been used at
// least once; absent keys mean the engine's own registration order.
private const val PRIORITY_FALLBACK_DEFAULT = 0

internal fun coreSettingsPortingDecoders(prefs: AppPreferences): Map<String, List<TypedPrefValue>> {
    val map = LinkedHashMap<String, List<TypedPrefValue>>()
    for ((name, keys) in KnownCoreSettings) map[name] = keys + dynamicKeysFor(name, prefs)
    com.flopster101.siliconplayer.NativeBridge.getRegisteredDecoderNames().forEach { name ->
        map.getOrPut(name) { dynamicKeysFor(name, prefs) }
    }
    return map
}

internal fun perCoreDspPortingEntries(prefs: AppPreferences): List<PortingNode> {
    val nodes = mutableListOf<PortingNode>()
    // Group per-core DSP tokens into one entry each with their keys.
    val byToken = linkedMapOf<String, MutableList<TypedPrefValue>>()
    for (key in prefs.allKeys()) {
        if (!key.startsWith("audio_dsp_core_")) continue
        val rest = key.removePrefix("audio_dsp_core_")
        val end = rest.indexOf('_')
        if (end <= 0) continue
        val token = rest.substring(0, end)
        val suffix = rest.substring(end + 1)
        val typed = dspTypedValue(key, suffix) ?: continue
        byToken.getOrPut(token) { mutableListOf() }.add(typed)
    }
    byToken.forEach { (token, keys) ->
        nodes.add(holder("audio.dsp.${sanitizeDecoderToken(token)}", token, keys))
    }
    return nodes
}

internal fun perCoreVolumePortingEntries(decoders: List<String>): List<PortingNode> = listOf(
    holder(
        "audio.corevolumes",
        "Core volume offsets",
        listOf(
            floatV("audio_plugin_volume_db", 0f, onlyWhenSet = true)
        ) + decoders.map { floatV("decoder_${it}_plugin_volume_db", 0f, onlyWhenSet = true) }
    ),
    holder(
        "audio.dspGA",
        "Per-song DSP sensitivity",
        listOf(boolV("audio_dsp_ignore", false, onlyWhenSet = true)),
        defaultSelected = false
    )
)

private fun dynamicKeysFor(decoder: String, prefs: AppPreferences): List<TypedPrefValue> {
    if (!decoder.equals(DecoderNames.VGM_PLAY, ignoreCase = true)) return emptyList()
    return prefs.allKeys()
        .filter { it.startsWith("vgmplay_chip_core_") }
        .map { intV(it, -1, onlyWhenSet = true) }
}

private fun dspTypedValue(key: String, suffix: String): TypedPrefValue? = when (suffix) {
    "bass_enabled", "surround_enabled", "reverb_enabled", "bitcrush_enabled", "ignore_global" ->
        boolV(key, false, onlyWhenSet = true)
    "bass_depth", "bass_range", "surround_depth", "surround_delay_ms", "reverb_depth",
    "reverb_preset", "bitcrush_bits" -> intV(key, 0, onlyWhenSet = true)
    else -> null
}

private val KnownCoreSettings: Map<String, List<TypedPrefValue>> = mapOf(
    DecoderNames.FFMPEG to listOf(
        intV(CorePreferenceKeys.CORE_RATE_FFMPEG, FfmpegDefaults.coreSampleRateHz, onlyWhenSet = true),
        boolV(CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK, FfmpegDefaults.gaplessRepeatTrack)
    ),
    DecoderNames.LIB_OPEN_MPT to listOf(
        intV(CorePreferenceKeys.CORE_RATE_OPENMPT, OpenMptDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT, OpenMptDefaults.stereoSeparationPercent),
        intV(CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT, OpenMptDefaults.stereoSeparationAmigaPercent),
        intV(CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH, OpenMptDefaults.interpolationFilterLength),
        intV(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE, OpenMptDefaults.amigaResamplerMode),
        boolV(CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES, OpenMptDefaults.amigaResamplerApplyAllModules),
        intV(CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH, OpenMptDefaults.volumeRampingStrength),
        boolV(CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING, OpenMptDefaults.ft2XmVolumeRamping),
        intV(CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL, OpenMptDefaults.masterGainMilliBel),
        boolV(CorePreferenceKeys.OPENMPT_SURROUND_ENABLED, OpenMptDefaults.surroundEnabled)
    ),
    DecoderNames.VGM_PLAY to listOf(
        intV(CorePreferenceKeys.CORE_RATE_VGMPLAY, VgmPlayDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, VgmPlayDefaults.loopCount),
        boolV(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP, VgmPlayDefaults.allowNonLoopingLoop),
        intV(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, VgmPlayDefaults.vsyncRate),
        intV(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, VgmPlayDefaults.resampleMode),
        intV(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, VgmPlayDefaults.chipSampleMode),
        intV(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, VgmPlayDefaults.chipSampleRate)
    ),
    DecoderNames.GAME_MUSIC_EMU to listOf(
        intV(CorePreferenceKeys.CORE_RATE_GME, GmeDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.GME_TEMPO_PERCENT, GmeDefaults.tempoPercent),
        intV(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT, GmeDefaults.stereoSeparationPercent),
        boolV(CorePreferenceKeys.GME_ECHO_ENABLED, GmeDefaults.echoEnabled),
        boolV(CorePreferenceKeys.GME_ACCURACY_ENABLED, GmeDefaults.accuracyEnabled),
        intV(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, GmeDefaults.eqTrebleDecibel),
        intV(CorePreferenceKeys.GME_EQ_BASS_HZ, GmeDefaults.eqBassHz),
        boolV(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, GmeDefaults.spcUseBuiltInFade),
        intV(CorePreferenceKeys.GME_SPC_INTERPOLATION, GmeDefaults.spcInterpolation),
        boolV(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE, GmeDefaults.spcUseNativeSampleRate)
    ),
    DecoderNames.C_RSID to listOf(
        intV(CorePreferenceKeys.CORE_RATE_CRSID, CrsidDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.CRSID_CLOCK_MODE, CrsidDefaults.clockMode),
        intV(CorePreferenceKeys.CRSID_SID_MODEL_MODE, CrsidDefaults.sidModelMode),
        intV(CorePreferenceKeys.CRSID_QUALITY_MODE, CrsidDefaults.qualityMode),
        intV(CorePreferenceKeys.CRSID_FILTER_6581_PRESET, CrsidDefaults.filter6581Preset)
    ),
    DecoderNames.LIB_SID_PLAY_FP to listOf(
        intV(CorePreferenceKeys.CORE_RATE_SIDPLAYFP, SidPlayFpDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.SIDPLAYFP_BACKEND, SidPlayFpDefaults.backend),
        intV(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE, SidPlayFpDefaults.clockMode),
        intV(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE, SidPlayFpDefaults.sidModelMode),
        boolV(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED, SidPlayFpDefaults.filter6581Enabled),
        boolV(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED, SidPlayFpDefaults.filter8580Enabled),
        boolV(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580, SidPlayFpDefaults.digiBoost8580),
        intV(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581, SidPlayFpDefaults.filterCurve6581Percent),
        intV(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581, SidPlayFpDefaults.filterRange6581Percent),
        intV(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580, SidPlayFpDefaults.filterCurve8580Percent),
        boolV(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING, SidPlayFpDefaults.reSidFpFastSampling),
        intV(CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH, SidPlayFpDefaults.reSidFpCombinedWaveformsStrength)
    ),
    DecoderNames.LAZY_USF2 to listOf(
        intV(CorePreferenceKeys.CORE_RATE_LAZYUSF2, LazyUsf2Defaults.coreSampleRateHz, onlyWhenSet = true),
        boolV(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO, LazyUsf2Defaults.useHleAudio)
    ),
    DecoderNames.AD_PLUG to listOf(
        intV(CorePreferenceKeys.CORE_RATE_ADPLUG, AdPlugDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.ADPLUG_OPL_ENGINE, AdPlugDefaults.oplEngine)
    ),
    DecoderNames.LIBXMP to listOf(
        intV(CorePreferenceKeys.CORE_RATE_XMP, XmpDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.CORE_RATE_UFMOD, UfmodDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.UFMOD_QUIRKS, 0, onlyWhenSet = true),
        intV(CorePreferenceKeys.XMP_INTERPOLATION, XmpDefaults.interpolation),
        intV(CorePreferenceKeys.XMP_STEREO_SEPARATION_PERCENT, XmpDefaults.stereoSeparationPercent),
        intV(CorePreferenceKeys.XMP_AMIGA_STEREO_SEPARATION_PERCENT, XmpDefaults.amigaStereoSeparationPercent),
        intV(CorePreferenceKeys.XMP_AMIGA_MODEL, XmpDefaults.amigaModel)
    ),
    DecoderNames.AYFLY to listOf(
        intV(CorePreferenceKeys.CORE_RATE_AYFLY, AyflyDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.AYFLY_OVERSAMPLE, AyflyDefaults.oversample),
        intV(CorePreferenceKeys.AYFLY_CHIP_TYPE, AyflyDefaults.chipType),
        intV(CorePreferenceKeys.AYFLY_MIX_TYPE, AyflyDefaults.mixType),
        intV(CorePreferenceKeys.AYFLY_INT_FREQ, AyflyDefaults.intFreq)
    ),
    DecoderNames.VIO2_SF to listOf(
        intV(CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY, Vio2sfDefaults.interpolationQuality)
    ),
    DecoderNames.SC68 to listOf(
        intV(CorePreferenceKeys.CORE_RATE_SC68, Sc68Defaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.SC68_ASID, Sc68Defaults.asid),
        intV(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS, Sc68Defaults.defaultTimeSeconds),
        intV(CorePreferenceKeys.SC68_YM_ENGINE, Sc68Defaults.ymEngine),
        intV(CorePreferenceKeys.SC68_YM_VOLMODEL, Sc68Defaults.ymVolModel),
        boolV(CorePreferenceKeys.SC68_AMIGA_FILTER, Sc68Defaults.amigaFilter),
        intV(CorePreferenceKeys.SC68_AMIGA_BLEND, Sc68Defaults.amigaBlend),
        intV(CorePreferenceKeys.SC68_AMIGA_CLOCK, Sc68Defaults.amigaClock)
    ),
    DecoderNames.UADE to listOf(
        intV(CorePreferenceKeys.CORE_RATE_UADE, UadeDefaults.coreSampleRateHz, onlyWhenSet = true),
        boolV(CorePreferenceKeys.UADE_FILTER_ENABLED, UadeDefaults.filterEnabled),
        boolV(CorePreferenceKeys.UADE_NTSC_MODE, UadeDefaults.ntscMode),
        intV(CorePreferenceKeys.UADE_PANNING_MODE, UadeDefaults.panningMode)
    ),
    DecoderNames.HIVELY_TRACKER to listOf(
        intV(CorePreferenceKeys.CORE_RATE_HIVELYTRACKER, HivelyTrackerDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE, HivelyTrackerDefaults.panningMode),
        intV(CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT, HivelyTrackerDefaults.mixGainPercent)
    ),
    DecoderNames.KLYSTRACK to listOf(
        intV(CorePreferenceKeys.CORE_RATE_KLYSTRACK, KlystrackDefaults.coreSampleRateHz, onlyWhenSet = true),
        intV(CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY, KlystrackDefaults.playerQuality)
    ),
    DecoderNames.LIB_DN_FAMITRACKER to listOf(
        intV(CorePreferenceKeys.CORE_RATE_DNFAMITRACKER, DnfamitrackerDefaults.coreSampleRateHz, onlyWhenSet = true),
        boolV(CorePreferenceKeys.DNFAMITRACKER_SEEK_EXACT, DnfamitrackerDefaults.seekExact),
        boolV(CorePreferenceKeys.DNFAMITRACKER_SCOPE_DC_BLOCK, DnfamitrackerDefaults.scopeDcBlock)
    ),
    DecoderNames.FURNACE to listOf(
        intV(CorePreferenceKeys.CORE_RATE_FURNACE, FurnaceDefaults.coreSampleRateHz, onlyWhenSet = true),
        boolV(CorePreferenceKeys.FURNACE_SCOPE_DC_BLOCK, FurnaceDefaults.scopeDcBlock),
        intV(CorePreferenceKeys.FURNACE_YM2612_CORE, FurnaceDefaults.ym2612Core),
        intV(CorePreferenceKeys.FURNACE_SN_CORE, FurnaceDefaults.snCore),
        intV(CorePreferenceKeys.FURNACE_NES_CORE, FurnaceDefaults.nesCore),
        intV(CorePreferenceKeys.FURNACE_C64_CORE, FurnaceDefaults.c64Core),
        intV(CorePreferenceKeys.FURNACE_GB_QUALITY, FurnaceDefaults.gbQuality),
        intV(CorePreferenceKeys.FURNACE_DSID_QUALITY, FurnaceDefaults.dsidQuality),
        intV(CorePreferenceKeys.FURNACE_AY_CORE, FurnaceDefaults.ayCore)
    ),
    DecoderNames.NEZPLUGPP to listOf(
        intV(CorePreferenceKeys.NEZPLUGPP_FILTER, NezplugppDefaults.filter),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_KSS_DB, NezplugppDefaults.volumeKssDb),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_NSF_DB, NezplugppDefaults.volumeNsfDb),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_GBS_DB, NezplugppDefaults.volumeGbsDb),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_HES_DB, NezplugppDefaults.volumeHesDb),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_SGC_DB, NezplugppDefaults.volumeSgcDb),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_NSD_DB, NezplugppDefaults.volumeNsdDb),
        intV(CorePreferenceKeys.NEZPLUGPP_VOLUME_AY_DB, NezplugppDefaults.volumeAyDb)
    ),
    DecoderNames.LIB_UPSE to listOf(
        boolV(CorePreferenceKeys.LIBUPSE_REVERB, LibupseDefaults.reverb)
    ),
    DecoderNames.VIOGSF to listOf(
        boolV(CorePreferenceKeys.VIOGSF_INTERPOLATION, ViogsfDefaults.interpolation),
        intV(CorePreferenceKeys.VIOGSF_FILTERING, ViogsfDefaults.filteringPercent)
    ),
    "System Dolby" to listOf(
        intV("platform_dolby_decoder", 0, onlyWhenSet = true)
    )
)
