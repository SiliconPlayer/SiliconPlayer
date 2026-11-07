package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.AdPlugOptionKeys
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.AyflyOptionKeys
import com.flopster101.siliconplayer.CoreOptionApplyPolicy
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.CrsidOptionKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.DnfamitrackerOptionKeys
import com.flopster101.siliconplayer.FfmpegOptionKeys
import com.flopster101.siliconplayer.FurnaceOptionKeys
import com.flopster101.siliconplayer.GmeOptionKeys
import com.flopster101.siliconplayer.HivelyTrackerOptionKeys
import com.flopster101.siliconplayer.KlystrackOptionKeys
import com.flopster101.siliconplayer.LazyUsf2OptionKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.Sc68OptionKeys
import com.flopster101.siliconplayer.SidPlayFpOptionKeys
import com.flopster101.siliconplayer.UadeOptionKeys
import com.flopster101.siliconplayer.VgmPlayConfig
import com.flopster101.siliconplayer.VgmPlayOptionKeys
import com.flopster101.siliconplayer.Vio2sfOptionKeys
import com.flopster101.siliconplayer.XmpConfig
import com.flopster101.siliconplayer.XmpOptionKeys
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.ToastHandler
import java.util.Locale

// CORE-OPTION PUSH table. Normalization mirrors Android AppNavigationCoreEffects;
// restart policy resolves from the engine (1 = RequiresPlaybackRestart).
private fun pushDesktopCoreRate(coreName: String, rateHz: Int) {
    runCatching { NativeBridge.setCoreOutputSampleRate(coreName, rateHz) }
}

private fun applyDesktopCoreOption(
    coreName: String,
    optionName: String,
    optionValue: String,
    policy: CoreOptionApplyPolicy,
    optionLabel: String?,
    toast: ToastHandler?,
    isPlaying: Boolean,
    hasCurrentTrack: Boolean,
    activeDecoderName: String?,
    silent: Boolean = false
) {
    runCatching { NativeBridge.setCoreOption(coreName, optionName, optionValue) }
    // Startup sweep only fills the stored map each open reads; no policy
    // probe or toast while the session has not played anything yet.
    if (silent) return
    val resolved = runCatching {
        if (NativeBridge.getCoreOptionApplyPolicy(coreName, optionName) == 1) {
            CoreOptionApplyPolicy.RequiresPlaybackRestart
        } else {
            CoreOptionApplyPolicy.Live
        }
    }.getOrDefault(policy)
    if (resolved != CoreOptionApplyPolicy.RequiresPlaybackRestart) return
    if (!isPlaying || !hasCurrentTrack) return
    if (!activeDecoderName.equals(coreName, ignoreCase = true)) return
    runCatching { toast?.showToast("${optionLabel ?: "This option"} will apply after restarting playback") }
}

internal fun pushDesktopCorePrefToNative(
    prefs: AppPreferences,
    key: String,
    toast: ToastHandler?,
    isPlaying: Boolean,
    hasCurrentTrack: Boolean,
    activeDecoderName: String?,
    silent: Boolean = false
) {
    fun opt(
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String?
    ) = applyDesktopCoreOption(coreName, optionName, optionValue, policy, optionLabel, toast, isPlaying, hasCurrentTrack, activeDecoderName, silent)
    // 0 = auto/native; coerce window matches Android (8000..192000).
    fun clampedRate(raw: Int): Int = if (raw <= 0) 0 else raw.coerceIn(8000, 192000)
    fun plainRate(raw: Int): Int = if (raw <= 0) 0 else raw
    fun percent2(raw: Int): String = String.format(Locale.US, "%.2f", raw / 100.0)
    when (key) {
        CorePreferenceKeys.CORE_RATE_FFMPEG -> pushDesktopCoreRate(DecoderNames.FFMPEG, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK ->
            opt(DecoderNames.FFMPEG, FfmpegOptionKeys.GAPLESS_REPEAT_TRACK, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Gapless repeat track")
        CorePreferenceKeys.CORE_RATE_OPENMPT -> pushDesktopCoreRate(DecoderNames.LIB_OPEN_MPT, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_VGMPLAY -> pushDesktopCoreRate(DecoderNames.VGM_PLAY, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_GME -> pushDesktopCoreRate(DecoderNames.GAME_MUSIC_EMU, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_CRSID -> pushDesktopCoreRate(DecoderNames.C_RSID, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_SIDPLAYFP -> pushDesktopCoreRate(DecoderNames.LIB_SID_PLAY_FP, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_LAZYUSF2 -> pushDesktopCoreRate(DecoderNames.LAZY_USF2, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_ADPLUG -> pushDesktopCoreRate(DecoderNames.AD_PLUG, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_XMP -> pushDesktopCoreRate(DecoderNames.LIBXMP, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_UFMOD -> pushDesktopCoreRate(DecoderNames.UFMOD, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_AYFLY -> pushDesktopCoreRate(DecoderNames.AYFLY, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_HIVELYTRACKER -> pushDesktopCoreRate(DecoderNames.HIVELY_TRACKER, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_KLYSTRACK -> pushDesktopCoreRate(DecoderNames.KLYSTRACK, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_DNFAMITRACKER -> pushDesktopCoreRate(DecoderNames.LIB_DN_FAMITRACKER, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_FURNACE -> pushDesktopCoreRate(DecoderNames.FURNACE, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_UADE -> pushDesktopCoreRate(DecoderNames.UADE, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_SC68 -> pushDesktopCoreRate(DecoderNames.SC68, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.XMP_INTERPOLATION -> {
            val v = prefs.getInt(key, 0).coerceIn(0, 2)
            opt(DecoderNames.LIBXMP, XmpOptionKeys.INTERPOLATION, XmpConfig.interpolationOptionValue(v), CoreOptionApplyPolicy.Live, "Interpolation")
        }
        CorePreferenceKeys.XMP_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.LIBXMP, XmpOptionKeys.STEREO_SEPARATION, prefs.getInt(key, 100).coerceIn(-100, 100).toString(), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.XMP_AMIGA_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.LIBXMP, XmpOptionKeys.AMIGA_STEREO_SEPARATION, prefs.getInt(key, 100).coerceIn(-100, 100).toString(), CoreOptionApplyPolicy.Live, "Amiga stereo separation")
        CorePreferenceKeys.XMP_AMIGA_MODEL ->
            opt(DecoderNames.LIBXMP, XmpOptionKeys.AMIGA_MODEL, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.Live, "Amiga mixing")
        CorePreferenceKeys.AYFLY_OVERSAMPLE ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.OVERSAMPLE, prefs.getInt(key, 0).coerceIn(1, 8).toString(), CoreOptionApplyPolicy.Live, "Oversampling")
        CorePreferenceKeys.AYFLY_CHIP_TYPE ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.CHIP_TYPE, prefs.getInt(key, 0).coerceIn(-1, 1).toString(), CoreOptionApplyPolicy.Live, "Chip model")
        CorePreferenceKeys.AYFLY_MIX_TYPE ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.MIX_TYPE, prefs.getInt(key, 0).coerceIn(-1, 5).toString(), CoreOptionApplyPolicy.Live, "Stereo mix order")
        CorePreferenceKeys.AYFLY_INT_FREQ ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.INT_FREQ, prefs.getInt(key, 0).coerceIn(0, 1000).toString(), CoreOptionApplyPolicy.Live, "Interrupt frequency")
        CorePreferenceKeys.AYFLY_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.STEREO_SEPARATION, prefs.getInt(key, 67).coerceIn(0, 100).toString(), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.ADPLUG_OPL_ENGINE ->
            opt(DecoderNames.AD_PLUG, AdPlugOptionKeys.OPL_ENGINE, prefs.getInt(key, 0).coerceIn(0, 3).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Adlib core")
        CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO ->
            opt(DecoderNames.LAZY_USF2, LazyUsf2OptionKeys.USE_HLE_AUDIO, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Use HLE audio")
        CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY ->
            opt(DecoderNames.VIO2_SF, Vio2sfOptionKeys.INTERPOLATION_QUALITY, prefs.getInt(key, 0).coerceIn(0, 4).toString(), CoreOptionApplyPolicy.Live, "Interpolation quality")
        CorePreferenceKeys.SC68_ASID ->
            opt(DecoderNames.SC68, Sc68OptionKeys.ASID, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "aSID filter")
        CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS ->
            opt(DecoderNames.SC68, Sc68OptionKeys.DEFAULT_TIME_SECONDS, prefs.getInt(key, 0).coerceIn(0, 24 * 60 * 60 - 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Default track time")
        CorePreferenceKeys.SC68_YM_ENGINE ->
            opt(DecoderNames.SC68, Sc68OptionKeys.YM_ENGINE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "YM engine")
        CorePreferenceKeys.SC68_YM_VOLMODEL ->
            opt(DecoderNames.SC68, Sc68OptionKeys.YM_VOLMODEL, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "YM volume model")
        CorePreferenceKeys.SC68_AMIGA_FILTER ->
            opt(DecoderNames.SC68, Sc68OptionKeys.AMIGA_FILTER, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Amiga filter")
        CorePreferenceKeys.SC68_AMIGA_BLEND ->
            opt(DecoderNames.SC68, Sc68OptionKeys.AMIGA_BLEND, prefs.getInt(key, 0).coerceIn(0, 255).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Amiga blend")
        CorePreferenceKeys.SC68_AMIGA_CLOCK ->
            opt(DecoderNames.SC68, Sc68OptionKeys.AMIGA_CLOCK, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Amiga clock")
        CorePreferenceKeys.UADE_FILTER_ENABLED ->
            opt(DecoderNames.UADE, UadeOptionKeys.FILTER_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Paula filter")
        CorePreferenceKeys.UADE_NTSC_MODE ->
            opt(DecoderNames.UADE, UadeOptionKeys.NTSC_MODE, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "NTSC mode")
        CorePreferenceKeys.UADE_PANNING_MODE ->
            opt(DecoderNames.UADE, UadeOptionKeys.PANNING_MODE, prefs.getInt(key, 0).coerceIn(0, 4).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Panning")
        CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE ->
            opt(DecoderNames.HIVELY_TRACKER, HivelyTrackerOptionKeys.PANNING_MODE, prefs.getInt(key, 0).coerceIn(-1, 4).toString(), CoreOptionApplyPolicy.Live, "Stereo panning")
        CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT -> {
            val raw = prefs.getInt(key, 100)
            val v = if (raw < 0) -1 else raw.coerceIn(25, 300)
            opt(DecoderNames.HIVELY_TRACKER, HivelyTrackerOptionKeys.MIX_GAIN_PERCENT, v.toString(), CoreOptionApplyPolicy.Live, "Replay mix gain")
        }
        CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY ->
            opt(DecoderNames.KLYSTRACK, KlystrackOptionKeys.PLAYER_QUALITY, prefs.getInt(key, 0).coerceIn(0, 4).toString(), CoreOptionApplyPolicy.Live, "Replay quality")
        CorePreferenceKeys.DNFAMITRACKER_SEEK_EXACT ->
            opt(DecoderNames.LIB_DN_FAMITRACKER, DnfamitrackerOptionKeys.SEEK_EXACT, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Exact seeking")
        CorePreferenceKeys.DNFAMITRACKER_SCOPE_DC_BLOCK ->
            opt(DecoderNames.LIB_DN_FAMITRACKER, DnfamitrackerOptionKeys.SCOPE_DC_BLOCK, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "Scope DC blocker")
        CorePreferenceKeys.FURNACE_SCOPE_DC_BLOCK ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.SCOPE_DC_BLOCK, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "Scope DC blocker")
        CorePreferenceKeys.FURNACE_YM2612_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.YM2612_CORE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "YM2612 core")
        CorePreferenceKeys.FURNACE_SN_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.SN_CORE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SN76489 core")
        CorePreferenceKeys.FURNACE_NES_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.NES_CORE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "NES core")
        CorePreferenceKeys.FURNACE_C64_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.C64_CORE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "C64 core")
        CorePreferenceKeys.FURNACE_GB_QUALITY ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.GB_QUALITY, prefs.getInt(key, 0).coerceIn(0, 5).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Game Boy quality")
        CorePreferenceKeys.FURNACE_DSID_QUALITY ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.DSID_QUALITY, prefs.getInt(key, 0).coerceIn(0, 5).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "dSID quality")
        CorePreferenceKeys.FURNACE_AY_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.AY_CORE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "AY core")
        CorePreferenceKeys.CRSID_CLOCK_MODE ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.CLOCK_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Timing standard")
        CorePreferenceKeys.CRSID_SID_MODEL_MODE ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.SID_MODEL_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SID model")
        CorePreferenceKeys.CRSID_QUALITY_MODE ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.QUALITY_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Emulation quality")
        CorePreferenceKeys.CRSID_FILTER_6581_PRESET ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.FILTER_6581_PRESET, prefs.getInt(key, 0).coerceIn(0, 3).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "6581 filter preset")
        CorePreferenceKeys.SIDPLAYFP_BACKEND -> {
            val v = prefs.getInt(key, 0).coerceIn(0, 2)
            val engine = when (v) { 1 -> "sidlite"; 2 -> "resid"; else -> "residfp" }
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.BACKEND, engine, CoreOptionApplyPolicy.RequiresPlaybackRestart, "Engine")
        }
        CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.CLOCK_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Timing standard")
        CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.SID_MODEL_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SID model")
        CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_6581_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "Filter for MOS6581")
        CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_8580_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "Filter for MOS8580")
        CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.DIGI_BOOST_8580, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Digi boost (8580)")
        CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_CURVE_6581, percent2(prefs.getInt(key, 50).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Filter curve 6581")
        CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_RANGE_6581, percent2(prefs.getInt(key, 50).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Filter range 6581")
        CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_CURVE_8580, percent2(prefs.getInt(key, 50).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Filter curve 8580")
        CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.RESIDFP_FAST_SAMPLING, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Fast sampling")
        CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.RESIDFP_COMBINED_WAVEFORMS_STRENGTH, prefs.getInt(key, 50).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.Live, "Combined waveforms")
        CorePreferenceKeys.GME_TEMPO_PERCENT ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.TEMPO, percent2(prefs.getInt(key, 100).coerceIn(50, 200)), CoreOptionApplyPolicy.Live, "Tempo")
        CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.STEREO_SEPARATION, percent2(prefs.getInt(key, 100).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.GME_ECHO_ENABLED ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.ECHO_ENABLED, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "SPC echo")
        CorePreferenceKeys.GME_ACCURACY_ENABLED ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.ACCURACY_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "High accuracy emulation")
        CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.EQ_TREBLE_DB, prefs.getInt(key, 0).coerceIn(-50, 5).toString(), CoreOptionApplyPolicy.Live, "EQ treble")
        CorePreferenceKeys.GME_EQ_BASS_HZ ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.EQ_BASS_HZ, prefs.getInt(key, 0).coerceIn(1, 1000).toString(), CoreOptionApplyPolicy.Live, "EQ bass")
        CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.SPC_USE_BUILTIN_FADE, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SPC built-in fade")
        CorePreferenceKeys.GME_SPC_INTERPOLATION ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.SPC_INTERPOLATION, prefs.getInt(key, 0).coerceIn(-2, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SPC interpolation")
        CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.SPC_USE_NATIVE_SAMPLE_RATE, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Use native SPC sample rate")
        AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS -> {
            val v = prefs.getInt(key, 0).coerceIn(1, 86400).toString()
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
            opt(DecoderNames.C_RSID, CrsidOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
            opt(DecoderNames.UADE, UadeOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
        }
        CorePreferenceKeys.VGMPLAY_LOOP_COUNT ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.LOOP_COUNT, prefs.getInt(key, 2).coerceIn(1, 99).toString(), CoreOptionApplyPolicy.Live, "Loop count")
        CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.ALLOW_NON_LOOPING_LOOP, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Allow non-looping loop")
        CorePreferenceKeys.VGMPLAY_VSYNC_RATE -> {
            val raw = prefs.getInt(key, 60)
            val v = if (raw == 50 || raw == 60) raw else 0
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.VSYNC_RATE_HZ, v.toString(), CoreOptionApplyPolicy.Live, "VSync mode")
        }
        CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.RESAMPLE_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Resampling mode")
        CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.CHIP_SAMPLE_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Chip sample mode")
        CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.CHIP_SAMPLE_RATE_HZ, prefs.getInt(key, 0).coerceIn(8000, 192000).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Chip sample rate")
        CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.stereo_separation_percent", prefs.getInt(key, 100).toString(), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.stereo_separation_amiga_percent", prefs.getInt(key, 100).toString(), CoreOptionApplyPolicy.Live, "Amiga stereo separation")
        CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.interpolation_filter_length", prefs.getInt(key, 8).toString(), CoreOptionApplyPolicy.Live, "Interpolation filter")
        CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.amiga_resampler_mode", prefs.getInt(key, 0).toString(), CoreOptionApplyPolicy.Live, "Amiga resampler")
        CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.amiga_resampler_apply_all_modules", prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Apply Amiga resampler to all modules")
        CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.volume_ramping_strength", prefs.getInt(key, -1).toString(), CoreOptionApplyPolicy.Live, "Volume ramping strength")
        CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.ft2_xm_volume_ramping", prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "FT2 5ms XM ramping")
        CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.master_gain_millibel", prefs.getInt(key, 0).toString(), CoreOptionApplyPolicy.Live, "Master gain")
        CorePreferenceKeys.OPENMPT_SURROUND_ENABLED ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.surround_enabled", prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Enable surround sound")
        else -> {
            // VGMPlay per-chip emulator cores: pref vgmplay_chip_core_<chip>.
            val prefix = CorePreferenceKeys.vgmPlayChipCoreKey("")
            if (key.startsWith(prefix) && key.length > prefix.length) {
                val chipKey = key.removePrefix(prefix)
                val default = VgmPlayConfig.defaultChipCoreSelections()[chipKey] ?: 0
                opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.CHIP_CORE_PREFIX + chipKey, prefs.getInt(key, default).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "$chipKey emulator core")
            }
        }
    }
}

internal fun pushStoredDesktopCorePreferencesToNative(prefs: AppPreferences) {
    prefs.allKeys().forEach { key ->
        runCatching {
            pushDesktopCorePrefToNative(
                prefs = prefs,
                key = key,
                toast = null,
                isPlaying = false,
                hasCurrentTrack = false,
                activeDecoderName = null,
                silent = true
            )
        }
    }
}
