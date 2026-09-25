@file:JvmName("AndroidNetworkSourcesStore")
package com.flopster101.siliconplayer

import android.content.SharedPreferences
import com.flopster101.siliconplayer.platform.AndroidAppPreferences

internal fun readNetworkNodes(prefs: SharedPreferences): List<NetworkNode> =
    readNetworkNodes(AndroidAppPreferences(prefs))

internal fun writeNetworkNodes(prefs: SharedPreferences, nodes: List<NetworkNode>) =
    writeNetworkNodes(AndroidAppPreferences(prefs), nodes)
