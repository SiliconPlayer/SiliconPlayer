package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.LookaheadClipperMode
import com.flopster101.siliconplayer.MultiChannelOutputMode
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.readPluginVolumeForDecoder

internal fun applyOfflineAudioPreferencesToNative(prefs: AppPreferences) {
    NativeBridge.setMasterGain(prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f))
    NativeBridge.setForceMono(prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false))
    NativeBridge.setOutputLimiterEnabled(
        prefs.getBoolean(
            AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED,
            AppDefaults.AudioProcessing.outputLimiterEnabled
        )
    )
    NativeBridge.setLookaheadClipperMode(
        LookaheadClipperMode.fromStorage(
            prefs.getString(
                AppPreferenceKeys.AUDIO_LOOKAHEAD_CLIPPER_MODE,
                AppDefaults.AudioProcessing.lookaheadClipperMode.storageValue
            )
        ).nativeValue
    )
    NativeBridge.setMultiChannelOutputMode(
        MultiChannelOutputMode.fromStorage(
            prefs.getString(
                AppPreferenceKeys.AUDIO_MULTI_CHANNEL_OUTPUT_MODE,
                AppDefaults.OutputPipeline.multiChannelOutputMode.storageValue
            )
        ).nativeValue
    )
    pushAudioPipelineConfigToNative(prefs)
    applyDspSettingsToNative(readGlobalDspSettings(prefs))
}

internal fun applyOfflineTrackGainToNative(
    prefs: AppPreferences,
    sourcePath: String,
    decoderName: String
) {
    val songVolumeStore = DesktopSongVolumeStore.getInstance()
    val ignoreCoreVolume = songVolumeStore.getSongIgnoreCoreVolume(sourcePath)
    NativeBridge.setPluginGain(if (ignoreCoreVolume) 0f else readPluginVolumeForDecoder(prefs, decoderName))
    NativeBridge.setSongGain(songVolumeStore.getSongVolume(sourcePath) ?: 0f)
}
