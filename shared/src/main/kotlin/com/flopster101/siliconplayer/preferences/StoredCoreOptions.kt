package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences

internal fun storedUfmodQuirks(prefs: AppPreferences): Int =
    prefs.getInt(CorePreferenceKeys.UFMOD_QUIRKS, 0)

internal fun storedLibupseReverb(prefs: AppPreferences): Boolean =
    prefs.getBoolean(CorePreferenceKeys.LIBUPSE_REVERB, LibupseDefaults.reverb)

internal fun storedViogsfInterpolation(prefs: AppPreferences): Boolean =
    prefs.getBoolean(CorePreferenceKeys.VIOGSF_INTERPOLATION, ViogsfDefaults.interpolation)

internal fun storedNezplugppFilter(prefs: AppPreferences): Int =
    prefs.getInt(CorePreferenceKeys.NEZPLUGPP_FILTER, NezplugppDefaults.filter)

internal fun storedNezplugppVolumeDb(prefs: AppPreferences, key: String, defaultDb: Int): Int =
    prefs.getInt(key, defaultDb)

internal fun storedViogsfFilteringPercent(prefs: AppPreferences): Int =
    prefs.getInt(CorePreferenceKeys.VIOGSF_FILTERING, ViogsfDefaults.filteringPercent)

// Core options are stored for the next open of their core, so they must reach
// the engine before the first load of a session.
internal fun pushStoredCoreOptionsToNative(prefs: AppPreferences) {
    NativeBridge.setCoreOption(DecoderNames.UFMOD, UfmodOptionKeys.QUIRKS, storedUfmodQuirks(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.LIB_UPSE, LibupseOptionKeys.REVERB, storedLibupseReverb(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.VIOGSF, ViogsfOptionKeys.INTERPOLATION, storedViogsfInterpolation(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.VIOGSF, ViogsfOptionKeys.FILTERING, (storedViogsfFilteringPercent(prefs) / 100.0).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.FILTER, storedNezplugppFilter(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_KSS, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_KSS_DB, NezplugppDefaults.volumeKssDb).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_NSF, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_NSF_DB, NezplugppDefaults.volumeNsfDb).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_GBS, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_GBS_DB, NezplugppDefaults.volumeGbsDb).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_HES, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_HES_DB, NezplugppDefaults.volumeHesDb).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_SGC, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_SGC_DB, NezplugppDefaults.volumeSgcDb).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_NSD, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_NSD_DB, NezplugppDefaults.volumeNsdDb).toString())
    NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.VOLUME_AY, storedNezplugppVolumeDb(prefs, CorePreferenceKeys.NEZPLUGPP_VOLUME_AY_DB, NezplugppDefaults.volumeAyDb).toString())
}
