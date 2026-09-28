package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences

internal fun storedUfmodQuirks(prefs: AppPreferences): Int =
    prefs.getInt(CorePreferenceKeys.UFMOD_QUIRKS, 0)

// Core options are stored for the next open of their core, so they must reach
// the engine before the first load of a session.
internal fun pushStoredCoreOptionsToNative(prefs: AppPreferences) {
    NativeBridge.setCoreOption(DecoderNames.UFMOD, UfmodOptionKeys.QUIRKS, storedUfmodQuirks(prefs).toString())
}
