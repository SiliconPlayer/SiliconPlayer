@file:JvmName("AndroidPluginConfigurationStore")
package com.flopster101.siliconplayer

import android.content.SharedPreferences
import com.flopster101.siliconplayer.platform.AndroidAppPreferences

internal fun loadPluginConfigurations(prefs: SharedPreferences) =
    loadPluginConfigurations(AndroidAppPreferences(prefs))

internal fun savePluginConfiguration(prefs: SharedPreferences, decoderName: String) =
    savePluginConfiguration(AndroidAppPreferences(prefs), decoderName)

internal fun persistAllPluginConfigurations(prefs: SharedPreferences) =
    persistAllPluginConfigurations(AndroidAppPreferences(prefs))

internal fun applyDecoderPriorityOrder(orderedDecoderNames: List<String>, prefs: SharedPreferences) =
    applyDecoderPriorityOrder(orderedDecoderNames, AndroidAppPreferences(prefs))

internal fun readPluginVolumeForDecoder(prefs: SharedPreferences, decoderName: String?): Float =
    readPluginVolumeForDecoder(AndroidAppPreferences(prefs), decoderName)

internal fun writePluginVolumeForDecoder(prefs: SharedPreferences, decoderName: String?, valueDb: Float) =
    writePluginVolumeForDecoder(AndroidAppPreferences(prefs), decoderName, valueDb)

internal fun clearAllDecoderPluginVolumes(prefs: SharedPreferences) =
    clearAllDecoderPluginVolumes(AndroidAppPreferences(prefs))
