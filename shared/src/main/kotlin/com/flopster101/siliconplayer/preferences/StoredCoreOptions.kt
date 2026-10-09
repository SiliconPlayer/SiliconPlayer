package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences

internal fun storedUfmodQuirks(prefs: AppPreferences): Int =
    prefs.getInt(CorePreferenceKeys.UFMOD_QUIRKS, 0)

internal fun storedLibupseReverb(prefs: AppPreferences): Boolean =
    prefs.getBoolean(CorePreferenceKeys.LIBUPSE_REVERB, LibupseDefaults.reverb)

internal fun storedViogsfInterpolation(prefs: AppPreferences): Boolean =
    prefs.getBoolean(CorePreferenceKeys.VIOGSF_INTERPOLATION, ViogsfDefaults.interpolation)

internal fun storedViogsfFilteringPercent(prefs: AppPreferences): Int =
    prefs.getInt(CorePreferenceKeys.VIOGSF_FILTERING, ViogsfDefaults.filteringPercent)

// Core options are stored for the next open of their core, so they must reach
// the engine before the first load of a session.
internal fun pushStoredCoreOptionsToNative(prefs: AppPreferences) {
    NativeBridge.setCoreOption(DecoderNames.UFMOD, UfmodOptionKeys.QUIRKS, storedUfmodQuirks(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.LIB_UPSE, LibupseOptionKeys.REVERB, storedLibupseReverb(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.VIOGSF, ViogsfOptionKeys.INTERPOLATION, storedViogsfInterpolation(prefs).toString())
    NativeBridge.setCoreOption(DecoderNames.VIOGSF, ViogsfOptionKeys.FILTERING, (storedViogsfFilteringPercent(prefs) / 100.0).toString())
}
