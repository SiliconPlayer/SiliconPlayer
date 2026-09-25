@file:JvmName("AndroidFileRepository")

package com.flopster101.siliconplayer.data

import android.content.SharedPreferences
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.platform.AndroidAppPreferences
import java.io.File

fun FileRepository(
    supportedExtensions: Set<String>,
    prefs: SharedPreferences,
    sortArchivesBeforeFiles: Boolean,
    nameSortMode: BrowserNameSortMode,
    rootDirectoryProvider: () -> File = { android.os.Environment.getExternalStorageDirectory() }
): FileRepository {
    return FileRepository(
        supportedExtensions = supportedExtensions,
        prefs = AndroidAppPreferences(prefs),
        sortArchivesBeforeFiles = sortArchivesBeforeFiles,
        nameSortMode = nameSortMode,
        rootDirectoryProvider = rootDirectoryProvider
    )
}
