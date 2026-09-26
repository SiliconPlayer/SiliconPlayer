package com.flopster101.siliconplayer.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.flopster101.siliconplayer.CacheExportResult
import com.flopster101.siliconplayer.ExportConflictDecision
import com.flopster101.siliconplayer.ExportFileItem
import com.flopster101.siliconplayer.ExportNameConflict
import com.flopster101.siliconplayer.ParsedPlaylistDocument
import com.flopster101.siliconplayer.PlaylistExportFormat
import com.flopster101.siliconplayer.RemoteExportRequest
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.library.LibraryCollections
import com.flopster101.siliconplayer.library.LibrarySearchResults
import com.flopster101.siliconplayer.library.LibrarySyncState
import com.flopster101.siliconplayer.library.LibraryTrackEntity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AppPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getFloat(key: String, defValue: Float): Float
    fun getLong(key: String, defValue: Long): Long
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putLong(key: String, value: Long): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }

    fun edit(): Editor

    fun interface OnChangeListener {
        fun onPreferenceChanged(prefs: AppPreferences, key: String)
    }

    fun addListener(listener: OnChangeListener)
    fun removeListener(listener: OnChangeListener)
}

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("No AppPreferences provided")
}

interface PreferencesProvider {
    fun getPreferences(name: String): AppPreferences
}

val LocalPreferencesProvider = staticCompositionLocalOf<PreferencesProvider> {
    error("No PreferencesProvider provided")
}

val LocalIsWatchDevice = staticCompositionLocalOf { false }
val LocalIsRoundScreen = staticCompositionLocalOf { false }

@Composable
fun isWatchDevice(): Boolean = LocalIsWatchDevice.current

@Composable
fun isRoundScreen(): Boolean = LocalIsRoundScreen.current

interface ArtworkThumbnailLoader {
    fun peek(cacheKey: String?): androidx.compose.ui.graphics.ImageBitmap?
    suspend fun load(cacheKey: String?): androidx.compose.ui.graphics.ImageBitmap?
    val revision: kotlinx.coroutines.flow.StateFlow<Long>
}

val LocalArtworkThumbnailLoader = staticCompositionLocalOf<ArtworkThumbnailLoader> {
    object : ArtworkThumbnailLoader {
        override fun peek(cacheKey: String?): androidx.compose.ui.graphics.ImageBitmap? = null
        override suspend fun load(cacheKey: String?): androidx.compose.ui.graphics.ImageBitmap? = null
        override val revision: kotlinx.coroutines.flow.StateFlow<Long> = kotlinx.coroutines.flow.MutableStateFlow(0L)
    }
}

enum class AudioOutputRouteType {
    Speaker,
    Headphones,
    Usb,
    Bluetooth
}

data class AudioOutputRouteInfo(
    val type: AudioOutputRouteType,
    val name: String
)

interface AudioRouteManager {
    @Composable
    fun rememberCurrentRoute(): AudioOutputRouteInfo
    fun openAudioOutputSwitcher()
    fun formatUsbAudioName(rawName: String): String
}

val LocalAudioRouteManager = staticCompositionLocalOf<AudioRouteManager> {
    object : AudioRouteManager {
        @Composable
        override fun rememberCurrentRoute(): AudioOutputRouteInfo {
            return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Default Output")
        }
        override fun openAudioOutputSwitcher() {}
        override fun formatUsbAudioName(rawName: String): String = rawName
    }
}

fun interface ToastHandler {
    fun showToast(message: String)
}

val LocalToastHandler = staticCompositionLocalOf<ToastHandler> {
    ToastHandler { message -> println("[Toast] $message") }
}

typealias BackHandlerCallback = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

val LocalPlatformBackHandler = staticCompositionLocalOf<BackHandlerCallback> {
    { _, _ -> }
}

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    LocalPlatformBackHandler.current(enabled, onBack)
}

data class WindowSizeInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestScreenWidthDp: Int,
    val isRound: Boolean = false
)

val LocalWindowSizeInfo = staticCompositionLocalOf {
    WindowSizeInfo(screenWidthDp = 360, screenHeightDp = 640, smallestScreenWidthDp = 360, isRound = false)
}

interface ProjectMOptionsProvider {
    fun getEnabledPresetLabels(): Map<String, String>
}

val LocalProjectMOptionsProvider = staticCompositionLocalOf<ProjectMOptionsProvider> {
    object : ProjectMOptionsProvider {
        override fun getEnabledPresetLabels(): Map<String, String> = emptyMap()
    }
}

data class AppVersionInfo(
    val versionName: String,
    val abiOrArch: String,
    val gitSha: String
)

val LocalAppVersionInfo = staticCompositionLocalOf {
    AppVersionInfo(
        versionName = "1.0.0",
        abiOrArch = System.getProperty("os.arch") ?: "unknown",
        gitSha = "dev"
    )
}

interface SettingsPlatformContent {
    @Composable
    fun LibrarySettingsContent(onOpenScanner: () -> Unit) {}

    @Composable
    fun LibraryScannerContent() {}

    @Composable
    fun PlatformAudioOptions(
        bitPerfectUsbAudio: Boolean,
        onBitPerfectUsbAudioChanged: (Boolean) -> Unit
    ) {}

    @Composable
    fun PlatformDolbyDetailContent() {}

    @Composable
    fun ProjectMRouteContent(onOpenPresetPacks: () -> Unit) {}

    @Composable
    fun ProjectMSetsRouteContent() {}
}

val LocalSettingsPlatformContent = staticCompositionLocalOf<SettingsPlatformContent> {
    object : SettingsPlatformContent {}
}

data class PlatformStorageLocation(
    val id: String,
    val kind: StorageLocationKind,
    val typeLabel: String,
    val name: String,
    val directory: java.io.File
)

enum class StorageLocationKind {
    ROOT,
    INTERNAL,
    SD,
    USB
}

val LocalStorageLocationsProvider = staticCompositionLocalOf<() -> List<PlatformStorageLocation>> {
    {
        val results = mutableListOf<PlatformStorageLocation>()
        val seen = mutableSetOf<String>()
        val userHome = java.io.File(System.getProperty("user.home") ?: "/")
        if (userHome.exists() && userHome.isDirectory) {
            results += PlatformStorageLocation(
                id = userHome.absolutePath,
                kind = StorageLocationKind.INTERNAL,
                typeLabel = "Home",
                name = userHome.name.ifBlank { "Home" },
                directory = userHome
            )
            seen += userHome.absolutePath
        }
        java.io.File.listRoots()?.forEach { root ->
            if (root.exists() && root.isDirectory && root.absolutePath !in seen) {
                results += PlatformStorageLocation(
                    id = root.absolutePath,
                    kind = StorageLocationKind.ROOT,
                    typeLabel = if (root.absolutePath == "/") "Root" else root.absolutePath,
                    name = root.name.ifBlank { root.absolutePath },
                    directory = root
                )
                seen += root.absolutePath
            }
        }
        val mediaDir = java.io.File("/media")
        if (mediaDir.exists() && mediaDir.isDirectory && mediaDir.absolutePath !in seen) {
            results += PlatformStorageLocation(
                id = mediaDir.absolutePath,
                kind = StorageLocationKind.USB,
                typeLabel = "Media",
                name = "media",
                directory = mediaDir
            )
        }
        val mntDir = java.io.File("/mnt")
        if (mntDir.exists() && mntDir.isDirectory && mntDir.absolutePath !in seen) {
            results += PlatformStorageLocation(
                id = mntDir.absolutePath,
                kind = StorageLocationKind.SD,
                typeLabel = "Mounts",
                name = "mnt",
                directory = mntDir
            )
        }
        results
    }
}

fun interface FileExportHandler {
    fun exportFiles(files: List<java.io.File>)
}

val LocalFileExportHandler = staticCompositionLocalOf<FileExportHandler> {
    FileExportHandler { _ -> }
}

internal interface RemoteSourceExportSupport {
    suspend fun prepareRemoteExportFile(
        request: RemoteExportRequest,
        onStatus: suspend (RemoteLoadUiState) -> Unit = {}
    ): Result<ExportFileItem>

    suspend fun exportFiles(
        exportItems: List<ExportFileItem>,
        onNameConflict: (suspend (ExportNameConflict) -> ExportConflictDecision)? = null
    ): CacheExportResult
}

internal val LocalRemoteSourceExportSupport = staticCompositionLocalOf<() -> RemoteSourceExportSupport> {
    { error("No RemoteSourceExportSupport provided") }
}

@Composable
internal fun rememberRemoteSourceExportSupport(): RemoteSourceExportSupport =
    LocalRemoteSourceExportSupport.current()

val LocalAppCacheDir = staticCompositionLocalOf<java.io.File> {
    java.io.File(System.getProperty("java.io.tmpdir"), "siliconplayer_cache").also { it.mkdirs() }
}

/**
 * Media library queries backing the albums/artists/tracks surfaces. Platforms without an
 * indexed library report [isAvailable] as `false` and return empty collections.
 */
interface LibraryRepositorySupport {
    val isAvailable: Boolean
    val scanState: StateFlow<LibrarySyncState>
    suspend fun collections(): LibraryCollections
    suspend fun search(rawQuery: String): LibrarySearchResults
    suspend fun albumTracks(albumName: String): List<LibraryTrackEntity>
    suspend fun artistTracks(artist: String): List<LibraryTrackEntity>
    fun requestScan()
}

/** No-op repository used when the platform has no media library (e.g. desktop). */
private val EmptyLibraryRepository = object : LibraryRepositorySupport {
    override val isAvailable: Boolean = false
    override val scanState: StateFlow<LibrarySyncState> = MutableStateFlow(LibrarySyncState())
    override suspend fun collections(): LibraryCollections = LibraryCollections.Empty
    override suspend fun search(rawQuery: String) = LibrarySearchResults(rawQuery, emptyList(), emptyList(), emptyList())
    override suspend fun albumTracks(albumName: String): List<LibraryTrackEntity> = emptyList()
    override suspend fun artistTracks(artist: String): List<LibraryTrackEntity> = emptyList()
    override fun requestScan() {}
}

val LocalLibraryRepository = staticCompositionLocalOf<LibraryRepositorySupport> {
    EmptyLibraryRepository
}

/**
 * Generated artwork and ad-hoc image decoding for playlist covers and library thumbnails.
 */
interface ArtworkCacheSupport {
    suspend fun ensureThumbnailCached(sourceId: String, requestUrlHint: String?): String?
    suspend fun ensureArtworkCached(sourceId: String, requireLarge: Boolean): String?
    fun peekGeneratedKey(sourceId: String): String?
    fun cacheFile(cacheKey: String, preferLarge: Boolean): File?
    suspend fun loadImageFile(file: File): ImageBitmap?
    suspend fun loadArtworkForSource(sourceId: String, requestUrl: String?): ImageBitmap?
    fun peekLibraryArtwork(path: String?): ImageBitmap?
    suspend fun loadLibraryArtwork(path: String): ImageBitmap?
}

val LocalArtworkCacheSupport = staticCompositionLocalOf<ArtworkCacheSupport> {
    object : ArtworkCacheSupport {
        override suspend fun ensureThumbnailCached(sourceId: String, requestUrlHint: String?): String? = null
        override suspend fun ensureArtworkCached(sourceId: String, requireLarge: Boolean): String? = null
        override fun peekGeneratedKey(sourceId: String): String? = null
        override fun cacheFile(cacheKey: String, preferLarge: Boolean): File? = null
        override suspend fun loadImageFile(file: File): ImageBitmap? = null
        override suspend fun loadArtworkForSource(sourceId: String, requestUrl: String?): ImageBitmap? = null
        override fun peekLibraryArtwork(path: String?): ImageBitmap? = null
        override suspend fun loadLibraryArtwork(path: String): ImageBitmap? = null
    }
}

/**
 * Playlist file/cover pickers plus playlist export and sharing, all backed by the platform's
 * own document picker or share affordance.
 */
internal interface PlaylistPlatformSupport {
    /**
     * Whether the platform routes playlist import/export through its own document picker. When
     * `false`, the shared UI shows its in-app file picker instead.
     */
    val supportsSystemPicker: Boolean
    val coversDirectory: File
    suspend fun pickCoverImage(destFile: File): Boolean
    suspend fun rotateCoverFile(file: File, degrees: Float): Boolean
    suspend fun pickPlaylistDocument(): ParsedPlaylistDocument?
    suspend fun exportPlaylist(playlist: StoredPlaylist, format: PlaylistExportFormat): Boolean
    suspend fun sharePlaylist(playlist: StoredPlaylist, format: PlaylistExportFormat): Boolean
}

internal val LocalPlaylistPlatformSupport = staticCompositionLocalOf<PlaylistPlatformSupport> {
    error("No PlaylistPlatformSupport provided")
}

@Composable
internal fun rememberPlaylistPlatformSupport(): PlaylistPlatformSupport =
    LocalPlaylistPlatformSupport.current

fun interface PlaylistRefreshNotifier {
    fun update(current: Int, total: Int, title: String?)
}

val LocalPlaylistRefreshNotifier = staticCompositionLocalOf<PlaylistRefreshNotifier> {
    PlaylistRefreshNotifier { _, _, _ -> }
}


