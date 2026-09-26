@file:JvmName("AndroidNetworkSourcesStore")
package com.flopster101.siliconplayer

import android.content.SharedPreferences
import com.flopster101.siliconplayer.platform.AndroidAppPreferences

internal fun readNetworkNodes(prefs: SharedPreferences): List<NetworkNode> {
    val dir = DomainStoreDirs.configDir
    return if (dir != null) {
        readNetworkNodes(dir, AndroidAppPreferences(prefs))
    } else {
        readNetworkNodes(AndroidAppPreferences(prefs))
    }
}

internal fun writeNetworkNodes(prefs: SharedPreferences, nodes: List<NetworkNode>) {
    val dir = DomainStoreDirs.configDir
    if (dir != null) {
        writeNetworkNodes(dir, nodes)
    } else {
        writeNetworkNodes(AndroidAppPreferences(prefs), nodes)
    }
}
