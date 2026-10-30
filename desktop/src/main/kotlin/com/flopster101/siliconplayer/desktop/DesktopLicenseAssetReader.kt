package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.platform.LicenseAssetReader
import java.io.IOException

// Bundled license texts are generated into the desktop resources by
// tools/generate-about.py, so they resolve off the classpath.
internal object DesktopLicenseAssetReader : LicenseAssetReader {
    override fun read(assetPath: String): String? {
        return try {
            val stream = DesktopLicenseAssetReader::class.java.classLoader
                ?.getResourceAsStream(assetPath)
                ?: return null
            stream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null
        }
    }
}
