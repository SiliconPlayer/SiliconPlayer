package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.runWithNativeAudioSession
import com.flopster101.siliconplayer.ui.dialogs.PlayWithDialog
import java.io.File
import kotlinx.coroutines.launch

internal class ExternalPlayWithState {
    var dialogFile by mutableStateOf<File?>(null)
    var resolvedPath by mutableStateOf<String?>(null)
}

@Composable
internal fun AppNavigationPendingOpenEffects(
    currentView: MainView,
    settingsRoute: SettingsRoute,
    pendingFileToOpen: File?,
    pendingFileFromExternalIntent: Boolean,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    supportedExtensions: Set<String>,
    onRefreshCachedSourceFiles: () -> Unit,
    onSelectedFileChanged: (File?) -> Unit,
    onLoadSongVolumeForFile: (String) -> Unit,
    onApplyRepeatModeToNative: () -> Unit,
    onStartEngine: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsPlayerExpandedChanged: (Boolean) -> Unit,
    onIsPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPendingFileToOpenChanged: (File?) -> Unit,
    onPendingFileFromExternalIntentChanged: (Boolean) -> Unit,
    loadPlayableSiblingFiles: suspend (File) -> List<File>?
) {
    LaunchedEffect(currentView, settingsRoute) {
        if (currentView == MainView.Settings &&
            (settingsRoute == SettingsRoute.UrlCache || settingsRoute == SettingsRoute.CacheManager)
        ) {
            onRefreshCachedSourceFiles()
        }
    }

    // External opens stop here until the user picks a core; dismissing
    // clears the pending file so nothing is queued or played.
    val externalPlayWith = remember { ExternalPlayWithState() }
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val playWithFile = externalPlayWith.dialogFile
    if (playWithFile != null) {
        PlayWithDialog(
            file = playWithFile,
            prefs = prefs,
            showDontAskAgain = true,
            onPlay = {
                externalPlayWith.resolvedPath = playWithFile.absolutePath
                externalPlayWith.dialogFile = null
                onPendingFileToOpenChanged(playWithFile)
                onPendingFileFromExternalIntentChanged(true)
            },
            onDismiss = { externalPlayWith.dialogFile = null }
        )
    }

    // singleTop VIEW intents land in onNewIntent after composition; feed
    // them into the pending flow here.
    LaunchedEffect(MainActivity.externalFileSignal) {
        val warmFile = MainActivity.externalFileToOpen ?: return@LaunchedEffect
        MainActivity.externalFileToOpen = null
        onPendingFileToOpenChanged(warmFile)
        onPendingFileFromExternalIntentChanged(true)
    }

    LaunchedEffect(pendingFileToOpen) {
        pendingFileToOpen?.let { file ->
            val resolvedExternally = file.absolutePath == externalPlayWith.resolvedPath
            val askPlayWith = prefs.getBoolean(AppPreferenceKeys.PLAY_WITH_EXTERNAL_OPEN_DIALOG, true)
            if (file.exists() && pendingFileFromExternalIntent && !resolvedExternally &&
                !isSupportedPlaylistFile(file) && askPlayWith
            ) {
                externalPlayWith.dialogFile = file
                onPendingFileToOpenChanged(null)
                onPendingFileFromExternalIntentChanged(false)
                return@let
            }
            val openable = resolvedExternally || fileMatchesSupportedExtensions(file, supportedExtensions)
            if (file.exists() && openable) {
                onSelectedFileChanged(file)

                if (autoPlayOnTrackSelect) {
                    onLoadSongVolumeForFile(file.absolutePath)
                    runWithNativeAudioSession {
                        NativeBridge.replaceCurrentAudio(file.absolutePath)
                    }
                    onApplyRepeatModeToNative()
                    onStartEngine()
                    onIsPlayingChanged(true)
                }
                if (openPlayerOnTrackSelect) {
                    onIsPlayerExpandedChanged(true)
                }
                onIsPlayerSurfaceVisibleChanged(true)

                if (pendingFileFromExternalIntent) {
                    launch {
                        val playableFiles = loadPlayableSiblingFiles(file)
                        if (playableFiles != null) {
                            onVisiblePlayableFilesChanged(playableFiles)
                        }
                    }
                }

                externalPlayWith.resolvedPath = null
                onPendingFileToOpenChanged(null)
                onPendingFileFromExternalIntentChanged(false)
            }
        }
    }
}
