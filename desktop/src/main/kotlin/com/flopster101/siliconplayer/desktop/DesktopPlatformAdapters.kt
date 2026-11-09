package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.flopster101.siliconplayer.DecoderPlatform
import com.flopster101.siliconplayer.LocalDecoderPlatform
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.export.AudioExportEncoder
import com.flopster101.siliconplayer.export.AudioExportExecutionState
import com.flopster101.siliconplayer.export.AudioExportPlatformAdapter
import com.flopster101.siliconplayer.export.AudioExportProgress
import com.flopster101.siliconplayer.export.AudioExportUiRequest
import com.flopster101.siliconplayer.export.LocalAudioExportPlatformAdapter
import com.flopster101.siliconplayer.library.defaultMusicScanRoot
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
import com.flopster101.siliconplayer.platform.LocalLicenseAssetReader
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var cachedType: AudioOutputRouteType? = null
    private var cachedAtMs: Long = 0L

    @Composable
    override fun rememberCurrentRoute(): AudioOutputRouteInfo {
        val now = System.currentTimeMillis()
        val (name, type) = synchronized(this) {
            if (cachedName == null || now - cachedAtMs > 5000L) {
                cachedName = runCatching { NativeBridge.getAudioOutputRouteName() }
                    .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                cachedType = runCatching { NativeBridge.getAudioOutputRouteClass() }
                    .getOrNull()?.let { AudioOutputRouteType.entries.getOrNull(it) }
                cachedAtMs = now
            }
            (cachedName ?: "System Output") to (cachedType ?: AudioOutputRouteType.Speaker)
        }
        return AudioOutputRouteInfo(type, name)
    }

    // Desktop has no system output switcher; the dialog's action button
    // opens the in-app audio settings page instead.
    override fun openAudioOutputSwitcher() = openAudioSettings()

    override fun formatUsbAudioName(rawName: String): String = rawName
}

private class DesktopAudioExportPlatformAdapter(
    private val prefs: AppPreferences,
    private val toastHandler: ToastHandler
) : AudioExportPlatformAdapter {
    override val encoders = listOf(AudioExportEncoder.SystemFfmpeg, AudioExportEncoder.ExternalFfmpeg)
    override var externalBinaryLocation by mutableStateOf(
        prefs.getString(com.flopster101.siliconplayer.AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG, null)
            ?.takeIf { File(it).isFile && File(it).canExecute() }
    )
        private set
    override var destinationLocation by mutableStateOf<String?>(null)
        private set
    override var executionState by mutableStateOf(AudioExportExecutionState())
        private set
    override val externalBinaryLabel: String?
        get() = externalBinaryLocation?.let(::File)?.name
    override val externalBinaryActionLabel = "Choose"
    override val destinationLabel: String?
        get() = destinationLocation?.let(::File)?.name
    override val destinationDescription: String?
        get() = destinationLocation

    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var worker: Process? = null
    @Volatile
    private var cancelRequested = false

    override fun chooseExternalBinary() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "Choose External FFmpeg"
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val path = chooser.selectedFile.absolutePath
        externalBinaryLocation = path
        prefs.edit()
            .putString(com.flopster101.siliconplayer.AppPreferenceKeys.AUDIO_EXPORT_EXTERNAL_FFMPEG, path)
            .apply()
    }

    override fun chooseDestination(suggestedFileName: String) {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "Export Audio"
            selectedFile = File(suggestedFileName)
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            destinationLocation = chooser.selectedFile.absolutePath
        }
    }

    override fun resetDestination(suggestedFileName: String) {
        val home = System.getProperty("user.home")
        val directory = defaultMusicScanRoot()?.let(::File)
            ?: home?.let { File(it, "Music") }
            ?: DesktopPaths.cacheDir().resolve("Music")
        directory.mkdirs()
        destinationLocation = File(directory, suggestedFileName).absolutePath
    }

    override fun startExport(request: AudioExportUiRequest) {
        if (executionState.running) return
        val arguments = try {
            buildDesktopAudioExportArguments(request)
        } catch (error: IllegalArgumentException) {
            toastHandler.showToast(error.message ?: "Invalid audio export configuration")
            return
        }
        cancelRequested = false
        executionState = AudioExportExecutionState(running = true, status = "Starting export…")
        scope.launch { runExportWorker(arguments, request) }
    }

    override fun cancelExport() {
        cancelRequested = true
        if (executionState.running) {
            executionState = executionState.copy(status = "Cancelling…")
        }
        worker?.destroyForcibly()
    }

    private fun runExportWorker(arguments: List<String>, request: AudioExportUiRequest) {
        val errors = mutableListOf<String>()
        try {
            val javaHome = System.getProperty("java.home")
            val executable = File(javaHome, "bin/java").takeIf { it.isFile }?.absolutePath ?: "java"
            val command = mutableListOf(executable)
            System.getProperty("java.library.path")?.takeIf { it.isNotBlank() }?.let {
                command += "-Djava.library.path=$it"
            }
            command += listOf("-cp", System.getProperty("java.class.path"), "com.flopster101.siliconplayer.desktop.AudioExportKt")
            command += arguments
            val process = ProcessBuilder(command).start()
            worker = process
            if (cancelRequested) process.destroyForcibly()
            val errorThread = Thread {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.forEach {
                        synchronized(errors) {
                            errors += it
                            if (errors.size > 8) errors.removeAt(0)
                        }
                    }
                }
            }.apply { start() }
            val token = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val buffer = CharArray(1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                for (index in 0 until count) {
                    val char = buffer[index]
                    if (char == '\r' || char == '\n') {
                        updateWorkerProgress(token.toString(), request)
                        token.clear()
                    } else {
                        token.append(char)
                    }
                }
            }
            updateWorkerProgress(token.toString(), request)
            val exitCode = process.waitFor()
            errorThread.join()
            when {
                cancelRequested -> toastHandler.showToast("Audio export cancelled")
                exitCode == 0 -> toastHandler.showToast("Exported ${File(request.destinationLocation).name}")
                else -> {
                    val detail = synchronized(errors) { errors.lastOrNull() }
                    toastHandler.showToast(detail ?: "Audio export failed with exit code $exitCode")
                }
            }
        } catch (error: Exception) {
            if (!cancelRequested) {
                toastHandler.showToast(error.message ?: "Audio export failed")
            }
        } finally {
            worker = null
            executionState = AudioExportExecutionState()
        }
    }

    private fun updateWorkerProgress(token: String, request: AudioExportUiRequest) {
        val match = Regex("Exported\\s+([0-9.]+)%").find(token) ?: return
        val fraction = match.groupValues[1].toDoubleOrNull()?.div(100.0)?.coerceIn(0.0, 1.0) ?: return
        val totalSeconds = (
            request.renderOptions.durationCapSeconds ?: request.source.durationSeconds
        ).coerceAtLeast(0.0) * request.repeatCount
        val totalFrames = (totalSeconds * request.renderOptions.sampleRateHz).toLong()
        executionState = executionState.copy(
            status = "Encoding audio",
            progress = if (totalFrames > 0L) {
                AudioExportProgress(
                    renderedFrames = (totalFrames * fraction).toLong(),
                    totalFrames = totalFrames,
                    sampleRateHz = request.renderOptions.sampleRateHz
                )
            } else {
                null
            }
        )
    }
}

internal fun buildDesktopAudioExportArguments(request: AudioExportUiRequest): List<String> = buildList {
    require(request.encoder != AudioExportEncoder.MediaCodec) { "MediaCodec is unavailable on desktop." }
    val input = request.source.filePath?.let(::File)?.takeIf { it.isFile }
    requireNotNull(input) { "The current track has no readable local file." }
    add("--input")
    add(input.absolutePath)
    add("--output")
    add(request.destinationLocation)
    if (request.source.subtuneCount > 1) {
        add("--subtune")
        add((request.source.subtuneIndex + 1).toString())
    }
    add("--sampleRate")
    add(request.renderOptions.sampleRateHz.toString())
    add("--sampleFormat")
    add(request.renderOptions.sampleFormat.storageValue)
    request.renderOptions.durationCapSeconds?.let {
        add("--duration")
        add(it.toString())
    }
    request.renderOptions.unknownDurationSeconds?.let {
        add("--unknownDuration")
        add(it.toString())
    }
    request.renderOptions.endFadeDurationMs?.let {
        add("--fadeMs")
        add(it.toString())
    }
    request.renderOptions.endFadeCurve?.let {
        add("--fadeCurve")
        add(it.storageValue)
    }
    request.renderOptions.endFadeApplyToAllTracks?.let {
        add("--fadeAll")
        add(it.toString())
    }
    add("--repeatCount")
    add(request.repeatCount.toString())
    add("--useGain")
    add(request.renderOptions.useActiveGain.toString())
    add("--useEffects")
    add(request.renderOptions.useActiveEffects.toString())
    if (request.encoder == AudioExportEncoder.ExternalFfmpeg) {
        add("--ffmpeg")
        add(requireNotNull(request.externalBinaryLocation) { "Import an external FFmpeg binary first." })
    }
    add("--ffmpegArgs")
    add(request.ffmpegArguments)
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
                    // Disk read plus image decode: never on the row.
                    return withContext(Dispatchers.IO) { decodeRecentArtworkFile(trimmed, file) }
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

    com.flopster101.siliconplayer.settings.porting.PortingVersionProvider.appVersion = DesktopBuildConfig.VERSION_NAME

    val appVersionInfo = remember {
        val os = System.getProperty("os.name")?.lowercase()?.let {
            if (it.contains("linux")) "linux" else if (it.contains("mac") || it.contains("darwin")) "macos" else if (it.contains("win")) "windows" else it
        } ?: "linux"
        AppVersionInfo(
            versionName = DesktopBuildConfig.VERSION_NAME,
            platform = os,
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

    com.flopster101.siliconplayer.settings.porting.PortingPlatformInfo.runningPlatform =
        com.flopster101.siliconplayer.settings.porting.PortingPlatformFamily.Desktop
    val portingFileIo = rememberDesktopPortingFileIo()
    val audioExportPlatformAdapter = remember(prefs, toastHandler) {
        DesktopAudioExportPlatformAdapter(prefs, toastHandler)
    }

    CompositionLocalProvider(
        com.flopster101.siliconplayer.settings.porting.LocalPortingFileIo provides portingFileIo,
        com.flopster101.siliconplayer.LocalTextInputTracker provides textInputTracker,
        LocalAppPreferences provides prefs,
        LocalPreferencesProvider provides prefsProvider,
        LocalIsWatchDevice provides false,
        LocalAudioRouteManager provides audioRouteManager,
        LocalLicenseAssetReader provides DesktopLicenseAssetReader,
        LocalAudioInspectorSupport provides DesktopAudioInspectorSupport,
        LocalToastHandler provides toastHandler,
        LocalAudioExportPlatformAdapter provides audioExportPlatformAdapter,
        LocalArtworkThumbnailLoader provides artworkThumbnailLoader,
        LocalDesktopBackDispatcher provides backDispatcher,
        LocalPlatformBackHandler provides { enabled, onBack ->
            DesktopBackHandler(dispatcher = backDispatcher, enabled = enabled, onBack = onBack)
        },
        LocalWindowSizeInfo provides windowSizeInfo,
        LocalProjectMOptionsProvider provides projectMOptionsProvider,
        LocalAppVersionInfo provides appVersionInfo,
        LocalDecoderPlatform provides DecoderPlatform.Desktop,
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

