package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.AppVersionInfo
import com.flopster101.siliconplayer.platform.ArtworkThumbnailLoader
import com.flopster101.siliconplayer.platform.AudioOutputRouteInfo
import com.flopster101.siliconplayer.platform.AudioOutputRouteType
import com.flopster101.siliconplayer.platform.AudioRouteManager
import com.flopster101.siliconplayer.platform.FileExportHandler
import com.flopster101.siliconplayer.platform.LocalAppCacheDir
import com.flopster101.siliconplayer.platform.LocalAppConfigDir
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.LocalAppVersionInfo
import com.flopster101.siliconplayer.platform.LocalArtworkCacheSupport
import com.flopster101.siliconplayer.platform.LocalArtworkThumbnailLoader
import com.flopster101.siliconplayer.platform.LocalAudioInspectorSupport
import com.flopster101.siliconplayer.platform.LocalAudioRouteManager
import com.flopster101.siliconplayer.platform.LocalFileExportHandler
import com.flopster101.siliconplayer.platform.LocalIsWatchDevice
import com.flopster101.siliconplayer.platform.LocalLibraryRepository
import com.flopster101.siliconplayer.platform.LocalLibrarySettingsSupport
import com.flopster101.siliconplayer.platform.LocalPlatformBackHandler
import com.flopster101.siliconplayer.platform.LocalPlaylistPlatformSupport
import com.flopster101.siliconplayer.platform.LocalPlaylistRefreshNotifier
import com.flopster101.siliconplayer.platform.LocalPreferencesProvider
import com.flopster101.siliconplayer.platform.LocalProjectMOptionsProvider
import com.flopster101.siliconplayer.platform.LocalRemoteSourceExportSupport
import com.flopster101.siliconplayer.platform.LocalSettingsPlatformContent
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.LocalTrackProbeSupport
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo
import com.flopster101.siliconplayer.platform.PlaylistRefreshNotifier
import com.flopster101.siliconplayer.platform.PreferencesProvider
import com.flopster101.siliconplayer.platform.ProjectMOptionsProvider
import com.flopster101.siliconplayer.platform.SettingsPlatformContent
import com.flopster101.siliconplayer.platform.ToastHandler
import com.flopster101.siliconplayer.platform.WindowSizeInfo
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopAppPreferences(private val nodeName: String) : AppPreferences {
    private val prefs: Preferences = Preferences.userRoot().node("com/flopster101/siliconplayer/$nodeName")
    private val listeners = CopyOnWriteArrayList<AppPreferences.OnChangeListener>()

    init {
        prefs.addPreferenceChangeListener { evt ->
            val key = evt.key
            if (key != null) {
                listeners.forEach { it.onPreferenceChanged(this, key) }
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = prefs.get(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = prefs.getFloat(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val raw = prefs.get(key, null) ?: return defValues
        return raw.split("\n").filter { it.isNotEmpty() }.toSet()
    }

    override fun contains(key: String): Boolean = prefs.get(key, null) != null
    override fun allKeys(): Set<String> = runCatching { prefs.keys().toSet() }.getOrDefault(emptySet())

    override fun edit(): AppPreferences.Editor = Editor()

    override fun addListener(listener: AppPreferences.OnChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AppPreferences.OnChangeListener) {
        listeners.remove(listener)
    }

    private inner class Editor : AppPreferences.Editor {
        private val modifications = mutableListOf<(Preferences) -> Unit>()

        override fun putString(key: String, value: String?): AppPreferences.Editor {
            modifications.add { p ->
                if (value != null) p.put(key, value) else p.remove(key)
            }
            return this
        }

        override fun putInt(key: String, value: Int): AppPreferences.Editor {
            modifications.add { p -> p.putInt(key, value) }
            return this
        }

        override fun putBoolean(key: String, value: Boolean): AppPreferences.Editor {
            modifications.add { p -> p.putBoolean(key, value) }
            return this
        }

        override fun putFloat(key: String, value: Float): AppPreferences.Editor {
            modifications.add { p -> p.putFloat(key, value) }
            return this
        }

        override fun putLong(key: String, value: Long): AppPreferences.Editor {
            modifications.add { p -> p.putLong(key, value) }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): AppPreferences.Editor {
            modifications.add { p ->
                if (values != null) p.put(key, values.joinToString("\n")) else p.remove(key)
            }
            return this
        }

        override fun remove(key: String): AppPreferences.Editor {
            modifications.add { p -> p.remove(key) }
            return this
        }

        override fun clear(): AppPreferences.Editor {
            modifications.add { p -> p.clear() }
            return this
        }

        override fun apply() {
            for (mod in modifications) {
                mod(prefs)
            }
            try {
                prefs.flush()
            } catch (_: Throwable) {}
        }

        override fun commit(): Boolean {
            apply()
            return true
        }
    }
}

class DesktopPreferencesProvider : PreferencesProvider {
    private val cache = mutableMapOf<String, AppPreferences>()

    override fun getPreferences(name: String): AppPreferences {
        return synchronized(cache) {
            cache.getOrPut(name) { DesktopAppPreferences(name) }
        }
    }
}

class DesktopAudioRouteManager(
    private val openAudioSettings: () -> Unit = {}
) : AudioRouteManager {
    // Device enumeration spins a transient miniaudio context; re-query at
    // most every few seconds so chip recompositions stay allocation-free.
    private var cachedName: String? = null
    private var cachedAtMs: Long = 0L

    @Composable
    override fun rememberCurrentRoute(): AudioOutputRouteInfo {
        val now = System.currentTimeMillis()
        val name = synchronized(this) {
            if (cachedName == null || now - cachedAtMs > 5000L) {
                cachedName = runCatching { NativeBridge.getAudioOutputRouteName() }
                    .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                cachedAtMs = now
            }
            cachedName
        } ?: "System Output"
        return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, name)
    }

    // Desktop has no system output switcher; the dialog's action button
    // opens the in-app audio settings page instead.
    override fun openAudioOutputSwitcher() = openAudioSettings()

    override fun formatUsbAudioName(rawName: String): String = rawName
}

@Composable
fun ProvideDesktopPlatformAdapters(
    windowWidthDp: Int = 1100,
    windowHeightDp: Int = 750,
    backDispatcher: DesktopBackDispatcher = remember { DesktopBackDispatcher() },
    stopPlaybackForRefresh: () -> Unit = {},
    openAudioSettings: () -> Unit = {},
    toastHandler: ToastHandler = remember { ToastHandler { msg -> println("[SiliconPlayer] $msg") } },
    textInputTracker: com.flopster101.siliconplayer.TextInputTracker = remember { com.flopster101.siliconplayer.TextInputTracker() },
    content: @Composable () -> Unit
) {
    val prefsProvider = remember { DesktopPreferencesProvider() }
    val prefs = remember { prefsProvider.getPreferences(com.flopster101.siliconplayer.AppPreferenceKeys.PREFS_NAME) }
    remember(prefs) {
        com.flopster101.siliconplayer.NetworkCredentialStore.preferencesProvider = { prefs }
    }
    val audioRouteManager = remember(openAudioSettings) { DesktopAudioRouteManager(openAudioSettings) }
    val windowSizeInfo = remember(windowWidthDp, windowHeightDp) {
        WindowSizeInfo(
            screenWidthDp = windowWidthDp,
            screenHeightDp = windowHeightDp,
            smallestScreenWidthDp = minOf(windowWidthDp, windowHeightDp),
            isRound = false
        )
    }
    val projectMOptionsProvider = remember(prefs) {
        object : ProjectMOptionsProvider {
            override fun getEnabledPresetLabels(): Map<String, String> {
                return DesktopProjectMPresetSets.enabledSets(prefs).associate { it.id to it.label }
            }
        }
    }

    val cacheDir = remember { DesktopPaths.cacheDir() }
    val configDir = remember { DesktopPaths.configDir() }
    remember(configDir) {
        com.flopster101.siliconplayer.NetworkCredentialStore.configDirProvider = { configDir }
    }
    val artworkThumbnailLoader = remember(cacheDir) {
        val recentArtworkCacheDir = desktopRecentArtworkCacheDir(cacheDir)
        object : ArtworkThumbnailLoader {
            override fun peek(cacheKey: String?) = DesktopArtworkSupport.peekMemoryArtwork(cacheKey)
            override suspend fun load(cacheKey: String?): androidx.compose.ui.graphics.ImageBitmap? {
                if (cacheKey == null) return null
                DesktopArtworkSupport.peekMemoryArtwork(cacheKey)?.let { return it }
                val trimmed = cacheKey.trim()
                if (trimmed.isEmpty()) return null
                desktopRecentArtworkCacheFile(recentArtworkCacheDir, trimmed, false)?.let { file ->
                    return decodeRecentArtworkFile(trimmed, file)
                }
                if (looksLikeRemoteSourceId(trimmed)) {
                    return withContext(Dispatchers.IO) {
                        ensureDesktopRecentArtworkCached(recentArtworkCacheDir, trimmed, null, false)?.let { key ->
                            DesktopArtworkSupport.peekMemoryArtwork(key)
                                ?: desktopRecentArtworkCacheFile(recentArtworkCacheDir, key, false)?.let { file ->
                                    decodeRecentArtworkFile(key, file)
                                }
                        }
                    }
                }
                val file = File(trimmed).takeIf { it.exists() && it.isFile } ?: return null
                return withContext(Dispatchers.IO) { DesktopArtworkSupport.loadArtworkForFile(file) }
            }
            override val revision: kotlinx.coroutines.flow.StateFlow<Long> = desktopRecentArtworkCacheRevision
        }
    }

    val appVersionInfo = remember {
        AppVersionInfo(
            versionName = DesktopBuildConfig.VERSION_NAME,
            abiOrArch = System.getProperty("os.arch") ?: "desktop",
            gitSha = DesktopBuildConfig.GIT_SHA
        )
    }

    val remoteSourceExportSupport = rememberDesktopRemoteSourceExportSupport(cacheDir)
    val artworkCacheSupport = rememberDesktopArtworkCacheSupport(cacheDir)
    val playlistPlatformSupport = rememberDesktopPlaylistPlatformSupport(cacheDir)
    val libraryRepository = remember(configDir) {
        com.flopster101.siliconplayer.library.DesktopLibraryRepository(configDir)
    }
    LaunchedEffect(libraryRepository) { libraryRepository.maybeStartAutoScan() }
    val librarySettingsSupport = remember(configDir, stopPlaybackForRefresh) {
        com.flopster101.siliconplayer.library.DesktopLibrarySettingsSupport(configDir, stopPlaybackForRefresh)
    }
    val playlistRefreshNotifier = remember(toastHandler) {
        PlaylistRefreshNotifier { current, total, _ ->
            when {
                total <= 0 -> Unit
                current <= 0 -> toastHandler.showToast("Refreshing metadata for $total tracks…")
                current >= total -> toastHandler.showToast("Metadata refresh finished")
            }
        }
    }

    CompositionLocalProvider(
        com.flopster101.siliconplayer.LocalTextInputTracker provides textInputTracker,
        LocalAppPreferences provides prefs,
        LocalPreferencesProvider provides prefsProvider,
        LocalIsWatchDevice provides false,
        LocalAudioRouteManager provides audioRouteManager,
        LocalAudioInspectorSupport provides DesktopAudioInspectorSupport,
        LocalToastHandler provides toastHandler,
        LocalArtworkThumbnailLoader provides artworkThumbnailLoader,
        LocalDesktopBackDispatcher provides backDispatcher,
        LocalPlatformBackHandler provides { enabled, onBack ->
            DesktopBackHandler(dispatcher = backDispatcher, enabled = enabled, onBack = onBack)
        },
        LocalWindowSizeInfo provides windowSizeInfo,
        LocalProjectMOptionsProvider provides projectMOptionsProvider,
        LocalAppVersionInfo provides appVersionInfo,
        LocalSettingsPlatformContent provides object : SettingsPlatformContent {
            @Composable
            override fun LibrarySettingsContent(onOpenScanner: () -> Unit) {
                com.flopster101.siliconplayer.LibrarySettingsRouteContent(
                    onOpenScanner = onOpenScanner
                )
            }

            @Composable
            override fun LibraryScannerContent() {
                com.flopster101.siliconplayer.LibraryScannerRouteContent()
            }
        },
        LocalAppCacheDir provides cacheDir,
        LocalAppConfigDir provides configDir,
        LocalArtworkCacheSupport provides artworkCacheSupport,
        LocalPlaylistPlatformSupport provides playlistPlatformSupport,
        LocalLibraryRepository provides libraryRepository,
        LocalLibrarySettingsSupport provides librarySettingsSupport,
        LocalTrackProbeSupport provides com.flopster101.siliconplayer.library.DesktopTrackProbeSupport,
        LocalPlaylistRefreshNotifier provides playlistRefreshNotifier,
        LocalRemoteSourceExportSupport provides { remoteSourceExportSupport },
        LocalFileExportHandler provides FileExportHandler { files ->
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Destination Folder"
            }
            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val destDir = chooser.selectedFile
                files.forEach { file ->
                    file.copyTo(destDir.resolve(file.name), overwrite = true)
                }
                toastHandler.showToast("Saved ${files.size} file(s)")
            }
        },
        content = content
    )
}

