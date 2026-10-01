package com.flopster101.siliconplayer.ui.screens

import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.inferredDisplayTitleForName
import com.flopster101.siliconplayer.isSupportedPlaylistFileName
import com.flopster101.siliconplayer.ui.dialogs.AddToPlaylistChooserDialog
import com.flopster101.siliconplayer.ui.dialogs.DirectoryTreeSheet
import com.flopster101.siliconplayer.ui.dialogs.PlayWithDialog
import java.net.URLConnection
import java.net.URLEncoder
import java.net.URLDecoder
import com.flopster101.siliconplayer.platform.PlatformBackHandler
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.LocalIsWatchDevice
import com.flopster101.siliconplayer.platform.LocalIsRoundScreen
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo
import com.flopster101.siliconplayer.platform.PlatformStorageLocation
import com.flopster101.siliconplayer.platform.StorageLocationKind
import com.flopster101.siliconplayer.platform.LocalStorageLocationsProvider
import com.flopster101.siliconplayer.platform.LocalFileExportHandler
import com.flopster101.siliconplayer.platform.LocalAppCacheDir
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.ui.icons.FileGameIcon
import com.flopster101.siliconplayer.ui.icons.FileTrackedIcon
import com.flopster101.siliconplayer.ui.icons.FileUnsupportedIcon
import com.flopster101.siliconplayer.ui.icons.FolderZipIcon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.extensionCandidatesForName
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.FilePreviewKind
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.BrowserLaunchState
import com.flopster101.siliconplayer.BrowserLocationModel
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.resolveDecoderArtworkHintForFileName
import com.flopster101.siliconplayer.resolveBrowserLocationModel
import com.flopster101.siliconplayer.buildHttpDisplayUri
import com.flopster101.siliconplayer.buildSmbDisplayUri
import com.flopster101.siliconplayer.platform.LocalArtworkThumbnailLoader
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.previewPinnedHomeEntryInsertion
import com.flopster101.siliconplayer.PINNED_HOME_ENTRIES_LIMIT
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import com.flopster101.siliconplayer.folderTitleForDisplay
import com.flopster101.siliconplayer.isSupportedPlaylistFile
import com.flopster101.siliconplayer.contextMenuSecondaryClick
import com.flopster101.siliconplayer.tvKeyLongPress
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.LocalBrowserListLocation
import com.flopster101.siliconplayer.LocalBrowserListingAdapter
import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.data.ensureArchiveMounted
import com.flopster101.siliconplayer.data.resolveArchiveLogicalDirectory
import com.flopster101.siliconplayer.data.resolveArchiveLocationToFile
import com.flopster101.siliconplayer.data.readZipEntrySizesForDirectory
import com.flopster101.siliconplayer.ExportFileItem
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.min

private const val BROWSER_PAGE_DURATION_MS = 280
private const val MIN_LOADING_SPINNER_MS = 220L
private const val FILE_ENTRY_ANIM_DURATION_MS = 280
private const val DIRECTORY_DIRECT_PUBLISH_MAX_ITEMS = 3000
private const val DIRECTORY_ENTRY_ANIM_MAX_ITEMS = 180
private const val LOCAL_BROWSER_THUMBNAIL_STAGGER_BASE_MS = 36L
private const val LOCAL_BROWSER_THUMBNAIL_STAGGER_RANGE_MS = 120L
private const val LOCAL_BROWSER_THUMBNAIL_PREVIEW_MAX_ITEMS = 180
private const val LOCAL_BROWSER_THUMBNAIL_PREVIEW_CONSTRAINED_MAX_ITEMS = 72
private val FILE_ICON_BOX_SIZE = 38.dp
private val FILE_ICON_GLYPH_SIZE = 26.dp
@OptIn(ExperimentalCoroutinesApi::class)
private val LOCAL_BROWSER_THUMBNAIL_LOADER_DISPATCHER = Dispatchers.IO.limitedParallelism(1)
private val FALLBACK_VIDEO_EXTENSIONS = setOf(
    "3g2", "3gp", "asf", "avi", "divx", "f4v", "flv", "m2ts", "m2v", "m4v",
    "mkv", "mov", "mp4", "mpeg", "mpg", "mts", "ogm", "ogv", "rm", "rmvb",
    "ts", "vob", "webm", "wmv"
)

private data class BrowserContentState(
    val pane: BrowserPane,
    val selectedLocationId: String?,
    val currentDirectoryPath: String?
)

private enum class BrowserPane {
    StorageLocations,
    LoadingDirectory,
    DirectoryEntries
}

private data class ArchiveMountInfo(
    val archivePath: String,
    val parentPath: String,
    val returnTargetPath: String? = null,
    val logicalArchivePath: String? = null,
    val smbSourceNodeId: Long? = null,
    val httpSourceNodeId: Long? = null,
    val httpRootPath: String? = null
)

private data class ArchiveToolbarContext(
    val subtitle: String,
    val isRemote: Boolean,
    val sourceLabel: String,
    val sourceTypeLabel: String,
    val sourceIcon: ImageVector
)

private fun encodeSavedFileItem(item: FileItem): String {
    return listOf(
        URLEncoder.encode(item.file.absolutePath, "UTF-8"),
        URLEncoder.encode(item.name, "UTF-8"),
        if (item.isDirectory) "1" else "0",
        item.size.toString(),
        item.kind.name
    ).joinToString("\u001f")
}

private fun decodeSavedFileItem(encoded: String): FileItem? {
    val parts = encoded.split("\u001f")
    if (parts.size != 5) return null
    return runCatching {
        FileItem(
            file = File(URLDecoder.decode(parts[0], "UTF-8")),
            name = URLDecoder.decode(parts[1], "UTF-8"),
            isDirectory = parts[2] == "1",
            size = parts[3].toLong(),
            kind = FileItem.Kind.valueOf(parts[4])
        )
    }.getOrNull()
}

private val LocalBrowserFileListSaver = listSaver<List<FileItem>, String>(
    save = { items -> items.map(::encodeSavedFileItem) },
    restore = { encodedItems -> encodedItems.mapNotNull(::decodeSavedFileItem) }
)

private const val LOCAL_BROWSER_SHOW_INTERMEDIARY_LOADING_PAGE = false

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
internal fun FileBrowserScreen(
    repository: FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    initialLocationId: String? = null,
    initialDirectoryPath: String? = null,
    initialSmbSourceNodeId: Long? = null,
    initialHttpSourceNodeId: Long? = null,
    initialHttpRootPath: String? = null,
    restoreFocusedItemRequestToken: Int = 0,
    onFileSelected: (File, String?) -> Unit,
    onPlaylistFileSelected: (File, String?) -> Unit = { _, _ -> },
    onVisiblePlayableFilesChanged: (List<File>) -> Unit = {},
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    showParentDirectoryEntry: Boolean = true,
    showFileIconChipBackground: Boolean = true,
    backHandlingEnabled: Boolean = true,
    onExitBrowser: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    showPrimaryTopBar: Boolean = true,
    playingFile: File? = null,
    playingPlaylistFile: File? = null,
    favoriteSourcePaths: List<String> = emptyList(),
    onToggleFavoriteFile: (File) -> Unit = {},
    playlists: List<StoredPlaylist> = emptyList(),
    onAddSourceToPlaylist: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    onRemoveSourceFromPlaylist: (String, String) -> Unit = { _, _ -> },
    pinnedHomeEntries: List<HomePinnedEntry> = emptyList(),
    onPinHomeEntry: (RecentPathEntry, Boolean) -> Unit = { _, _ -> }
) {
    val prefs = LocalAppPreferences.current
    val toastHandler = LocalToastHandler.current
    val isWatch = LocalIsWatchDevice.current
    val isRound = LocalIsRoundScreen.current
    val windowSize = LocalWindowSizeInfo.current
    val storageLocationsProvider = LocalStorageLocationsProvider.current
    val fileExportHandler = LocalFileExportHandler.current
    val cacheDir = LocalAppCacheDir.current
    val clipboardManager = LocalClipboardManager.current
    val isTablet = windowSize.smallestScreenWidthDp >= 600

    var showLocalThumbnailPreviews by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_LOCAL_THUMBNAIL_PREVIEWS,
                AppDefaults.Browser.showLocalThumbnailPreviews
            )
        )
    }
    DisposableEffect(prefs) {
        val listener = AppPreferences.OnChangeListener { preferences, key ->
            if (key == AppPreferenceKeys.BROWSER_SHOW_LOCAL_THUMBNAIL_PREVIEWS) {
                showLocalThumbnailPreviews = preferences.getBoolean(
                    AppPreferenceKeys.BROWSER_SHOW_LOCAL_THUMBNAIL_PREVIEWS,
                    AppDefaults.Browser.showLocalThumbnailPreviews
                )
            }
        }
        prefs.addListener(listener)
        onDispose {
            prefs.removeListener(listener)
        }
    }
    val localListingAdapter = remember(repository) { LocalBrowserListingAdapter(repository) }
    var storageLocationsRefreshToken by remember { mutableIntStateOf(0) }
    val storageLocations = remember(storageLocationsProvider, storageLocationsRefreshToken) { storageLocationsProvider() }
    var selectedLocationId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentDirectoryPath by rememberSaveable { mutableStateOf<String?>(null) }
    val currentDirectory = currentDirectoryPath?.let(::File)
    var fileList by rememberSaveable(stateSaver = LocalBrowserFileListSaver) { mutableStateOf(emptyList<FileItem>()) }
    var selectorExpanded by remember { mutableStateOf(false) }
    var currentFolderMenuExpanded by remember { mutableStateOf(false) }
    var directoryTreeOpen by remember { mutableStateOf(false) }
    var browserNavDirection by remember { mutableStateOf(BrowserPageNavDirection.Forward) }
    var isLoadingDirectory by remember { mutableStateOf(false) }
    var lastCompletedDirectoryPath by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAppliedInitialNavigationKey by rememberSaveable { mutableStateOf<String?>(null) }
    val directoryListState = rememberLazyListState()
    var launchAutoScrollTargetKey by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var directoryLoadJob by remember { mutableStateOf<Job?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var directoryAnimationEpoch by remember { mutableIntStateOf(0) }
    val loadingLogLines = remember { mutableStateListOf<String>() }
    val archiveMountRoots = remember { mutableStateMapOf<String, ArchiveMountInfo>() }
    val selectorButtonFocusRequester = remember { FocusRequester() }
    var browserFocusedEntryKey by remember { mutableStateOf<String?>(null) }
    val browserEntryFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val browserSearchController = rememberBrowserSearchController()
    val browserSelectionController = rememberBrowserSelectionController<String>()
    var browserInfoFields by remember { mutableStateOf<List<BrowserInfoField>>(emptyList()) }
    var showBrowserInfoDialog by remember { mutableStateOf(false) }
    var textPreviewDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var imagePreviewDialogState by remember { mutableStateOf<Pair<String, File>?>(null) }
    var pendingDeleteFilePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingPinConfirmation by remember { mutableStateOf<Pair<RecentPathEntry, Boolean>?>(null) }
    var pendingPinEvictionCandidate by remember { mutableStateOf<HomePinnedEntry?>(null) }
    var watchActionTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var pendingPlaylistAddSource by remember { mutableStateOf<Pair<String, String>?>(null) }
    val folderSummaryCache = remember { mutableStateMapOf<String, String>() }
    val isConstrainedBrowserDevice = remember(isWatch) {
        isWatch || Runtime.getRuntime().availableProcessors().coerceAtLeast(1) <= 4
    }

    val selectedLocation = storageLocations.firstOrNull { it.id == selectedLocationId }
    val hasActiveDirectory = currentDirectory != null
    val initialNavigationKey = remember(
        initialLocationId,
        initialDirectoryPath,
        initialSmbSourceNodeId,
        initialHttpSourceNodeId,
        initialHttpRootPath
    ) {
        buildString {
            append(initialLocationId.orEmpty())
            append('|')
            append(initialDirectoryPath.orEmpty())
            append('|')
            append(initialSmbSourceNodeId ?: -1L)
            append('|')
            append(initialHttpSourceNodeId ?: -1L)
            append('|')
            append(initialHttpRootPath.orEmpty())
        }
    }
    val hasRequestedInitialNavigation =
        initialLocationId != null || !initialDirectoryPath.isNullOrBlank()
    val hasPendingInitialNavigation =
        selectedLocationId == null &&
            !hasActiveDirectory &&
            hasRequestedInitialNavigation &&
            lastAppliedInitialNavigationKey != initialNavigationKey
    val browserContentState = remember(
        selectedLocationId,
        currentDirectory?.absolutePath,
        isLoadingDirectory,
        hasPendingInitialNavigation
    ) {
        BrowserContentState(
            pane = when {
                selectedLocationId == null && !hasActiveDirectory && hasPendingInitialNavigation ->
                    BrowserPane.LoadingDirectory
                selectedLocationId == null && !hasActiveDirectory -> BrowserPane.StorageLocations
                LOCAL_BROWSER_SHOW_INTERMEDIARY_LOADING_PAGE && isLoadingDirectory -> BrowserPane.LoadingDirectory
                else -> BrowserPane.DirectoryEntries
            },
            selectedLocationId = selectedLocationId,
            currentDirectoryPath = currentDirectory?.absolutePath
        )
    }

    fun appendLoadingLog(message: String) {
        val lineNumber = loadingLogLines.size + 1
        loadingLogLines += "[${lineNumber.toString().padStart(2, '0')}] $message"
        val maxLines = 80
        if (loadingLogLines.size > maxLines) {
            repeat(loadingLogLines.size - maxLines) {
                loadingLogLines.removeAt(0)
            }
        }
    }

    fun cancelDirectoryLoad() {
        directoryLoadJob?.cancel()
        directoryLoadJob = null
        isLoadingDirectory = false
    }

    fun loadDirectoryAsync(directory: File) {
        directoryLoadJob?.cancel()
        isLoadingDirectory = true
        lastCompletedDirectoryPath = null
        directoryAnimationEpoch += 1
        fileList = emptyList()
        folderSummaryCache.clear()
        loadingLogLines.clear()
        appendLoadingLog("Opening ${directory.absolutePath}")
        appendLoadingLog("Listing directory entries")

        val targetPath = directory.absolutePath
        val loadingStartedAt = System.currentTimeMillis()
        val normalizedTargetPathForMountLookup = runCatching { File(targetPath).canonicalPath }
            .getOrElse { File(targetPath).absolutePath }
            .replace('\\', '/')
            .trimEnd('/')
            .ifBlank { "/" }
        val mountedForTarget = archiveMountRoots.entries
            .asSequence()
            .filter { (mountRoot, _) ->
                normalizedTargetPathForMountLookup == mountRoot ||
                    normalizedTargetPathForMountLookup.startsWith("$mountRoot/")
            }
            .maxByOrNull { (mountRoot, _) -> mountRoot.length }
            ?.toPair()
        directoryLoadJob = coroutineScope.launch {
            try {
                // Ensure at least one frame is rendered with the spinner before parsing starts.
                withFrameNanos { }
                delay(16)
                val loadedFiles = withContext(Dispatchers.IO) {
                    val baseFiles = localListingAdapter
                        .list(LocalBrowserListLocation(directory))
                        .getOrThrow()
                        .entries
                    if (mountedForTarget == null) {
                        baseFiles
                    } else {
                        val mountRoot = mountedForTarget.first
                        val mountInfo = mountedForTarget.second
                        val relativeDirectory = if (normalizedTargetPathForMountLookup == mountRoot) {
                            ""
                        } else {
                            normalizedTargetPathForMountLookup.removePrefix("$mountRoot/")
                                .replace('\\', '/')
                                .trim('/')
                        }
                        val zipSizes = readZipEntrySizesForDirectory(
                            cacheDir = cacheDir,
                            archivePath = mountInfo.archivePath,
                            relativeDirectory = relativeDirectory
                        )
                        if (zipSizes.isEmpty()) {
                            baseFiles
                        } else {
                            baseFiles.map { item ->
                                if (!item.isDirectory) {
                                    zipSizes[item.name]?.let { resolvedSize ->
                                        item.copy(size = resolvedSize)
                                    } ?: item
                                } else {
                                    item
                                }
                            }
                        }
                    }
                }
                onVisiblePlayableFilesChanged(
                    loadedFiles
                        .asSequence()
                        .filter { item -> repository.isPlayableFile(item.file) }
                        .map { it.file }
                        .toList()
                )
                val stillOnSameDirectory = currentDirectoryPath == targetPath
                if (stillOnSameDirectory) {
                    val folders = loadedFiles.count { it.isDirectory }
                    val files = loadedFiles.size - folders
                    appendLoadingLog("Found ${loadedFiles.size} entries")
                    appendLoadingLog("$folders folders, $files files")
                    appendLoadingLog("Load finished")
                    // Publish all items at once for normal folders. For very large folders,
                    // chunk without artificial delays to avoid blocking the main thread.
                    fileList = emptyList()
                    if (shouldPublishDirectoryAllAtOnce(loadedFiles.size)) {
                        fileList = loadedFiles
                    } else {
                        val publishBatchSize = directoryPublishBatchSize(loadedFiles.size)
                        var index = 0
                        while (index < loadedFiles.size) {
                            ensureActive()
                            if (currentDirectoryPath != targetPath) break

                            val end = min(index + publishBatchSize, loadedFiles.size)
                            val chunk = loadedFiles.subList(index, end)
                            fileList = fileList + chunk
                            index = end
                            if (index < loadedFiles.size) {
                                // Yield once per chunk to avoid UI stalls for huge folders.
                                withFrameNanos { }
                            }
                        }
                    }
                    lastCompletedDirectoryPath = targetPath
                }
            } catch (_: CancellationException) {
                // Directory load was superseded by another navigation action.
            } finally {
                if (currentDirectoryPath == targetPath) {
                    val elapsed = System.currentTimeMillis() - loadingStartedAt
                    if (elapsed < MIN_LOADING_SPINNER_MS) {
                        delay(MIN_LOADING_SPINNER_MS - elapsed)
                    }
                    isLoadingDirectory = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelDirectoryLoad()
        }
    }

    fun relativeDepth(directory: File?, root: File?): Int {
        if (directory == null || root == null) return 0
        val rootPath = root.absolutePath.trimEnd('/')
        val dirPath = directory.absolutePath
        if (dirPath == rootPath) return 0
        return dirPath.removePrefix("$rootPath/").split("/").count { it.isNotBlank() }
    }

    fun openBrowserHome() {
        cancelDirectoryLoad()
        launchAutoScrollTargetKey = null
        browserNavDirection = BrowserPageNavDirection.Backward
        selectedLocationId = null
        currentDirectoryPath = null
        fileList = emptyList()
        lastCompletedDirectoryPath = null
        loadingLogLines.clear()
        onVisiblePlayableFilesChanged(emptyList())
        onBrowserLocationChanged(BrowserLaunchState())
    }

    fun openLocation(location: StorageLocation) {
        launchAutoScrollTargetKey = null
        browserNavDirection = BrowserPageNavDirection.Forward
        selectedLocationId = location.id
        currentDirectoryPath = location.directory.absolutePath
        loadDirectoryAsync(location.directory)
        onBrowserLocationChanged(
            BrowserLaunchState(
                locationId = location.id,
                directoryPath = location.directory.absolutePath
            )
        )
    }

    fun normalizePathForArchiveMountLookup(path: String): String {
        val normalized = runCatching { File(path).canonicalPath }
            .getOrElse { File(path).absolutePath }
            .replace('\\', '/')
        return normalized.trimEnd('/').ifBlank { "/" }
    }

    fun findArchiveMount(path: String): Pair<String, ArchiveMountInfo>? {
        val normalizedPath = normalizePathForArchiveMountLookup(path)
        return archiveMountRoots
            .asSequence()
            .filter { (mountRoot, _) ->
                normalizedPath == mountRoot || normalizedPath.startsWith("$mountRoot/")
            }
            .maxByOrNull { (mountRoot, _) -> mountRoot.length }
            ?.toPair()
    }

    fun resolveLogicalArchivePath(directory: File?): String? {
        val dir = directory ?: return null
        val mounted = findArchiveMount(dir.absolutePath) ?: return null
        val mountRoot = mounted.first
        val archivePath = mounted.second.logicalArchivePath ?: mounted.second.archivePath
        val normalizedDirectoryPath = normalizePathForArchiveMountLookup(dir.absolutePath)
        if (normalizedDirectoryPath == mountRoot) {
            return buildArchiveDirectoryPath(archivePath)
        }
        val relative = normalizedDirectoryPath.removePrefix("$mountRoot/").replace('\\', '/').trimStart('/')
        return buildArchiveDirectoryPath(archivePath, relative)
    }

    fun resolveArchiveRelativePath(absolutePath: String, mountRoot: String): String? {
        val normalizedFilePath = normalizePathForArchiveMountLookup(absolutePath)
        if (normalizedFilePath == mountRoot || !normalizedFilePath.startsWith("$mountRoot/")) return null
        return normalizedFilePath.removePrefix("$mountRoot/").trim('/').ifBlank { null }
    }

    fun emitBrowserLocationChange(directory: File) {
        val mounted = findArchiveMount(directory.absolutePath)?.second
        val logicalArchivePath = resolveLogicalArchivePath(directory)
        val archiveOriginPath = logicalArchivePath?.let { path -> parseArchiveLogicalPath(path)?.first }
        val isArchiveSmb = archiveOriginPath != null &&
            parseSmbSourceSpecFromInput(archiveOriginPath) != null
        val isArchiveHttp = archiveOriginPath != null &&
            parseHttpSourceSpecFromInput(archiveOriginPath) != null
        onBrowserLocationChanged(
            BrowserLaunchState(
                locationId = selectedLocationId,
                directoryPath = logicalArchivePath ?: directory.absolutePath,
                smbSourceNodeId = if (isArchiveSmb) {
                    mounted?.smbSourceNodeId ?: initialSmbSourceNodeId
                } else {
                    null
                },
                httpSourceNodeId = if (isArchiveHttp) {
                    mounted?.httpSourceNodeId ?: initialHttpSourceNodeId
                } else {
                    null
                },
                httpRootPath = if (isArchiveHttp) {
                    mounted?.httpRootPath ?: initialHttpRootPath
                } else {
                    null
                }
            )
        )
    }

    fun navigateTo(directory: File) {
        launchAutoScrollTargetKey = null
        val root = selectedLocation?.directory
        val previousDepth = relativeDepth(currentDirectory, root)
        val nextDepth = relativeDepth(directory, root)
        browserNavDirection = if (nextDepth >= previousDepth) {
            BrowserPageNavDirection.Forward
        } else {
            BrowserPageNavDirection.Backward
        }
        currentDirectoryPath = directory.absolutePath
        loadDirectoryAsync(directory)
        emitBrowserLocationChange(directory)
    }

    fun openArchive(item: FileItem) {
        val archiveFile = item.file
        val activeDirectory = currentDirectory
        if (!item.isArchive || activeDirectory == null) {
            return
        }
        val parentMountInfo = findArchiveMount(activeDirectory.absolutePath)?.second
        directoryLoadJob?.cancel()
        isLoadingDirectory = true
        directoryLoadJob = coroutineScope.launch {
            try {
                val mountDirectory = withContext(Dispatchers.IO) {
                    ensureArchiveMounted(cacheDir, archiveFile)
                }
                archiveFile.parentFile?.absolutePath?.let { parentPath ->
                    val mountRootKey = normalizePathForArchiveMountLookup(mountDirectory.absolutePath)
                    val existing = archiveMountRoots[mountRootKey]
                    archiveMountRoots[mountRootKey] = ArchiveMountInfo(
                        archivePath = archiveFile.absolutePath,
                        parentPath = parentPath,
                        returnTargetPath = existing?.returnTargetPath ?: parentPath,
                        logicalArchivePath = existing?.logicalArchivePath ?: archiveFile.absolutePath,
                        smbSourceNodeId = existing?.smbSourceNodeId
                            ?: parentMountInfo?.smbSourceNodeId
                            ?: initialSmbSourceNodeId,
                        httpSourceNodeId = existing?.httpSourceNodeId
                            ?: parentMountInfo?.httpSourceNodeId
                            ?: initialHttpSourceNodeId,
                        httpRootPath = existing?.httpRootPath
                            ?: parentMountInfo?.httpRootPath
                            ?: initialHttpRootPath
                    )
                }
                navigateTo(mountDirectory)
            } catch (_: CancellationException) {
                // Archive open was superseded by another action.
            } finally {
                isLoadingDirectory = false
            }
        }
    }

    fun navigateUpWithinLocation() {
        val directory = currentDirectory ?: return openBrowserHome()

        findArchiveMount(directory.absolutePath)?.let { (mountRoot, mountInfo) ->
            val normalizedDirectoryPath = normalizePathForArchiveMountLookup(directory.absolutePath)
            if (normalizedDirectoryPath != mountRoot) {
                val archiveParent = directory.parentFile
                if (archiveParent != null && archiveParent.exists() && archiveParent.isDirectory) {
                    browserNavDirection = BrowserPageNavDirection.Backward
                    navigateTo(archiveParent)
                    return
                }
            }
            val returnTarget = mountInfo.returnTargetPath ?: mountInfo.parentPath
            val returnModel = resolveBrowserLocationModel(
                initialLocationId = null,
                initialDirectoryPath = returnTarget,
                initialSmbSourceNodeId = null,
                initialHttpSourceNodeId = null,
                initialHttpRootPath = null
            )
            when (returnModel) {
                is BrowserLocationModel.Local -> {
                    val parentDirectory = returnTarget
                        ?.let(::File)
                        ?.takeIf { it.exists() && it.isDirectory }
                    if (parentDirectory != null) {
                        browserNavDirection = BrowserPageNavDirection.Backward
                        navigateTo(parentDirectory)
                    } else {
                        openBrowserHome()
                    }
                }
                is BrowserLocationModel.Smb -> {
                    onBrowserLocationChanged(
                        BrowserLaunchState(
                            directoryPath = returnTarget,
                            smbSourceNodeId = mountInfo.smbSourceNodeId ?: initialSmbSourceNodeId
                        )
                    )
                }
                is BrowserLocationModel.Http -> {
                    onBrowserLocationChanged(
                        BrowserLaunchState(
                            directoryPath = returnTarget,
                            httpSourceNodeId = mountInfo.httpSourceNodeId ?: initialHttpSourceNodeId,
                            httpRootPath = mountInfo.httpRootPath ?: initialHttpRootPath
                        )
                    )
                }
                else -> openBrowserHome()
            }
            return
        }

        val location = selectedLocation ?: return openBrowserHome()
        val root = location.directory
        if (directory.absolutePath == root.absolutePath) {
            openBrowserHome()
            return
        }

        val parent = directory.parentFile
        if (parent == null || !isWithinRoot(parent, root)) {
            navigateTo(root)
        } else {
            navigateTo(parent)
        }
    }

    fun handleBack() {
        if (browserSelectionController.isSelectionMode) {
            browserSelectionController.exitSelectionMode()
            return
        }
        if (currentDirectory != null) {
            navigateUpWithinLocation()
        } else {
            onExitBrowser?.invoke()
        }
    }

    fun showSelectionInfoDialog() {
        val selectedItems = fileList.filter { item ->
            browserSelectionController.selectedKeys.contains(item.file.absolutePath)
        }
        if (selectedItems.isEmpty()) return
        val infoEntries = selectedItems.map { item ->
            BrowserInfoEntry(
                name = item.name,
                isDirectory = item.isDirectory,
                sizeBytes = if (item.isDirectory) null else item.size
            )
        }
        val pathLabel = currentDirectory?.absolutePath
            ?: selectedLocation?.directory?.absolutePath
            ?: "/"
        val storageLabel = selectedLocation?.let { location ->
            "${location.typeLabel} (${location.name})"
        } ?: "Unknown"
        browserInfoFields = buildBrowserInfoFields(
            entries = infoEntries,
            path = pathLabel,
            storageOrHostLabel = "Storage",
            storageOrHost = storageLabel
        )
        showBrowserInfoDialog = true
    }

    fun selectedRegularFileItems(): List<FileItem> {
        return fileList.filter { item ->
            browserSelectionController.selectedKeys.contains(item.file.absolutePath) &&
                !item.isDirectory &&
                item.file.exists() &&
                item.file.isFile
        }
    }

    fun selectedAnyItems(): List<FileItem> {
        return fileList.filter { item ->
            browserSelectionController.selectedKeys.contains(item.file.absolutePath)
        }
    }

    fun resolveCurrentFolderPathForActions(): String? {
        return resolveLogicalArchivePath(currentDirectory)
            ?: currentDirectory?.absolutePath
            ?: selectedLocation?.directory?.absolutePath
    }

    fun showCurrentFolderInfoDialog() {
        val folderPath = resolveCurrentFolderPathForActions() ?: return
        val folderTitle = folderTitleForDisplay(folderPath)
        browserInfoFields = buildBrowserInfoFields(
            entries = listOf(
                BrowserInfoEntry(
                    name = folderTitle,
                    isDirectory = true,
                    sizeBytes = null
                )
            ),
            path = folderPath,
            storageOrHostLabel = "Storage",
            storageOrHost = selectedLocation?.let { location ->
                "${location.typeLabel} (${location.name})"
            } ?: "Unknown"
        )
        showBrowserInfoDialog = true
    }

    fun showItemInfoDialog(item: FileItem) {
        val storageLabel = selectedLocation?.let { location ->
            "${location.typeLabel} (${location.name})"
        } ?: "Unknown"
        browserInfoFields = buildBrowserInfoFields(
            entries = listOf(
                BrowserInfoEntry(
                    name = item.name,
                    isDirectory = item.isDirectory,
                    sizeBytes = if (item.isDirectory) null else item.size
                )
            ),
            path = item.file.absolutePath,
            storageOrHostLabel = "Storage",
            storageOrHost = storageLabel
        )
        showBrowserInfoDialog = true
    }

    fun pinItemToHome(item: FileItem) {
        val isFolder = item.isDirectory
        val recentEntry = RecentPathEntry(
            path = item.file.absolutePath,
            locationId = selectedLocationId,
            title = if (isFolder) item.name else null
        )
        val preview = previewPinnedHomeEntryInsertion(
            current = pinnedHomeEntries,
            candidate = HomePinnedEntry(
                path = recentEntry.path,
                isFolder = isFolder,
                locationId = recentEntry.locationId,
                title = recentEntry.title
            ),
            maxItems = PINNED_HOME_ENTRIES_LIMIT
        )
        if (preview.requiresConfirmation) {
            pendingPinEvictionCandidate = preview.evictionCandidate
            pendingPinConfirmation = recentEntry to isFolder
        } else {
            onPinHomeEntry(recentEntry, isFolder)
            toastHandler.showToast(if (isFolder) "Pinned folder to home" else "Pinned file to home")
        }
    }

    fun openFileItem(item: FileItem) {
        if (item.isArchive) {
            openArchive(item)
            return
        }
        if (item.isDirectory) {
            navigateTo(item.file)
            return
        }
        val mounted = findArchiveMount(item.file.absolutePath)
        val sourceIdOverride = if (mounted != null) {
            val mountRoot = mounted.first
            val archivePath = mounted.second.logicalArchivePath ?: mounted.second.archivePath
            val relativePath = resolveArchiveRelativePath(item.file.absolutePath, mountRoot)
            if (relativePath.isNullOrBlank()) {
                null
            } else {
                buildArchiveSourceId(archivePath, relativePath)
            }
        } else {
            null
        }
        if (isSupportedPlaylistFile(item.file)) {
            onPlaylistFileSelected(item.file, sourceIdOverride)
            return
        }
        when (browserPreviewKindForName(item.name)) {
            FilePreviewKind.Text -> {
                val textPreviewContent = readTextPreviewContent(item.file)
                if (textPreviewContent != null) {
                    textPreviewDialogState = item.name to textPreviewContent
                } else {
                    toastHandler.showToast("Unable to preview text file")
                }
                return
            }
            FilePreviewKind.Image -> {
                imagePreviewDialogState = item.name to item.file
                return
            }
            null -> Unit
        }
        onFileSelected(item.file, sourceIdOverride)
    }

    LaunchedEffect(
        storageLocations,
        initialLocationId,
        initialDirectoryPath,
        initialSmbSourceNodeId,
        initialHttpSourceNodeId,
        initialHttpRootPath
    ) {
        val initialLocation = initialLocationId?.let { id ->
            storageLocations.firstOrNull { it.id == id }
        }
        if (lastAppliedInitialNavigationKey == initialNavigationKey) return@LaunchedEffect
        lastAppliedInitialNavigationKey = initialNavigationKey

        val rawInitialDirectory = initialDirectoryPath?.trim().takeUnless { it.isNullOrBlank() }
        val resolvedArchive = rawInitialDirectory?.let { path ->
            withContext(Dispatchers.IO) { resolveArchiveLogicalDirectory(cacheDir, path) }
        }
        val restoredDirectory = when {
            resolvedArchive != null -> {
                val archiveLocation = resolvedArchive.archivePath
                val archiveIsSmb = parseSmbSourceSpecFromInput(archiveLocation) != null
                val archiveIsHttp = parseHttpSourceSpecFromInput(archiveLocation) != null
                archiveMountRoots[normalizePathForArchiveMountLookup(resolvedArchive.mountDirectory.absolutePath)] =
                    ArchiveMountInfo(
                        archivePath = archiveLocation,
                        parentPath = resolvedArchive.parentPath,
                        returnTargetPath = resolvedArchive.parentPath,
                        logicalArchivePath = archiveLocation,
                        smbSourceNodeId = if (archiveIsSmb) initialSmbSourceNodeId else null,
                        httpSourceNodeId = if (archiveIsHttp) initialHttpSourceNodeId else null,
                        httpRootPath = if (archiveIsHttp) initialHttpRootPath else null
                    )
                resolvedArchive.targetDirectory
            }
            rawInitialDirectory != null -> File(rawInitialDirectory).takeIf { it.exists() && it.isDirectory }
            else -> initialLocation?.directory
        } ?: initialLocation?.directory ?: run {
            openBrowserHome()
            return@LaunchedEffect
        }

        val resolvedLocationId = initialLocation?.id
            ?: storageLocations.firstOrNull { isWithinRoot(restoredDirectory, it.directory) }?.id

        browserNavDirection = BrowserPageNavDirection.Forward
        selectedLocationId = resolvedLocationId
        currentDirectoryPath = restoredDirectory.absolutePath
        loadDirectoryAsync(restoredDirectory)
        val restoredMountInfo = findArchiveMount(restoredDirectory.absolutePath)?.second
        val restoredLogicalArchivePath = resolveLogicalArchivePath(restoredDirectory)
        val restoredArchiveOriginPath = restoredLogicalArchivePath?.let { path -> parseArchiveLogicalPath(path)?.first }
        val restoredIsArchiveSmb = restoredArchiveOriginPath != null &&
            parseSmbSourceSpecFromInput(restoredArchiveOriginPath) != null
        val restoredIsArchiveHttp = restoredArchiveOriginPath != null &&
            parseHttpSourceSpecFromInput(restoredArchiveOriginPath) != null
        onBrowserLocationChanged(
            BrowserLaunchState(
                locationId = resolvedLocationId,
                directoryPath = restoredLogicalArchivePath ?: restoredDirectory.absolutePath,
                smbSourceNodeId = if (restoredIsArchiveSmb) {
                    restoredMountInfo?.smbSourceNodeId ?: initialSmbSourceNodeId
                } else {
                    null
                },
                httpSourceNodeId = if (restoredIsArchiveHttp) {
                    restoredMountInfo?.httpSourceNodeId ?: initialHttpSourceNodeId
                } else {
                    null
                },
                httpRootPath = if (restoredIsArchiveHttp) {
                    restoredMountInfo?.httpRootPath ?: initialHttpRootPath
                } else {
                    null
                }
            )
        )

        val browserPlayingFile = playingPlaylistFile ?: playingFile
        val playingPath = browserPlayingFile?.absolutePath
        val playingParentPath = browserPlayingFile?.parentFile?.absolutePath
        launchAutoScrollTargetKey = if (
            playingPath != null &&
            playingParentPath == restoredDirectory.absolutePath
        ) {
            "${restoredDirectory.absolutePath}|$playingPath"
        } else {
            null
        }
    }

    LaunchedEffect(currentDirectory?.absolutePath, isLoadingDirectory, lastCompletedDirectoryPath) {
        val activeDirectory = currentDirectory ?: return@LaunchedEffect
        val activePath = activeDirectory.absolutePath
        if (!isLoadingDirectory && activePath != lastCompletedDirectoryPath) {
            loadDirectoryAsync(activeDirectory)
        }
    }

    LaunchedEffect(selectedLocationId, currentDirectory?.absolutePath) {
        browserSelectionController.exitSelectionMode()
        showBrowserInfoDialog = false
        pendingDeleteFilePaths = emptyList()
    }

    PlatformBackHandler(
        enabled = backHandlingEnabled && (currentDirectory != null || onExitBrowser != null),
        onBack = { handleBack() }
    )

    LaunchedEffect(launchAutoScrollTargetKey, fileList.size) {
        val targetKey = launchAutoScrollTargetKey ?: return@LaunchedEffect
        val targetPath = targetKey.substringAfter('|', missingDelimiterValue = "")
        if (targetPath.isBlank()) {
            launchAutoScrollTargetKey = null
            return@LaunchedEffect
        }
        val targetIndex = fileList.indexOfFirst { !it.isDirectory && it.file.absolutePath == targetPath }
        if (targetIndex < 0) return@LaunchedEffect

        val visible = directoryListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!visible) {
            // Keep a little context above the playing row when jumping.
            directoryListState.animateScrollToItem((targetIndex - 2).coerceAtLeast(0))
        }
        launchAutoScrollTargetKey = null
    }

    LaunchedEffect(
        restoreFocusedItemRequestToken,
        selectedLocationId,
        currentDirectory?.absolutePath,
        fileList.size
    ) {
        if (restoreFocusedItemRequestToken <= 0 || selectedLocationId == null) return@LaunchedEffect
        val focusedKey = browserFocusedEntryKey ?: return@LaunchedEffect
        val requester = browserEntryFocusRequesters[focusedKey] ?: return@LaunchedEffect
        val targetIndex = when {
            focusedKey.startsWith("parent:") -> if (showParentDirectoryEntry) 0 else -1
            else -> {
                val fileIndex = fileList.indexOfFirst { it.file.absolutePath == focusedKey }
                if (fileIndex < 0) -1 else fileIndex + if (showParentDirectoryEntry) 1 else 0
            }
        }
        if (targetIndex >= 0) {
            val isVisible = directoryListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            if (!isVisible) {
                directoryListState.animateScrollToItem((targetIndex - 2).coerceAtLeast(0))
                withFrameNanos { }
            }
        }
        requester.requestFocus()
    }

    val logicalArchivePath = resolveLogicalArchivePath(currentDirectory)
    val archiveToolbarContext = remember(logicalArchivePath) {
        logicalArchivePath?.let(::buildArchiveToolbarContext)
    }
    val showLocalStorageSelector = archiveToolbarContext?.isRemote != true
    val subtitleIcon = archiveToolbarContext?.sourceIcon
        ?: if (archiveToolbarContext != null) FolderZipIcon else null
        ?: selectedLocation?.let { iconForStorageKind(it.kind, isTablet) }
        ?: Icons.Default.Home
    val subtitle = archiveToolbarContext?.subtitle ?: if (selectedLocation == null && currentDirectory == null) {
        "Storage locations"
    } else {
        currentDirectory?.absolutePath
            ?: selectedLocation?.name
            ?: "Storage locations"
    }
    val filteredFileList by remember(browserSearchController.debouncedQuery) {
        derivedStateOf {
            if (browserSearchController.debouncedQuery.isBlank()) {
                fileList
            } else {
                fileList.filter { item ->
                    matchesBrowserSearchQuery(item.name, browserSearchController.debouncedQuery)
                }
            }
        }
    }
    val shouldAnimateDirectoryEntries = !isConstrainedBrowserDevice &&
        filteredFileList.size <= DIRECTORY_ENTRY_ANIM_MAX_ITEMS
    val allowThumbnailPreviewLoads = showLocalThumbnailPreviews &&
        !directoryListState.isScrollInProgress &&
        filteredFileList.size <= (
            if (isConstrainedBrowserDevice) {
                LOCAL_BROWSER_THUMBNAIL_PREVIEW_CONSTRAINED_MAX_ITEMS
            } else {
                LOCAL_BROWSER_THUMBNAIL_PREVIEW_MAX_ITEMS
            }
        )
    // Pre-hash the favorite paths so the per-row "is this favorited?" check is
    // an O(1) HashSet lookup instead of an O(N) scan calling samePath() (which
    // resolves canonical paths via blocking File.canonicalFile syscalls). With
    // hundreds of visible rows recomposing on scroll start/stop this previously
    // ran thousands of stat() calls on the UI thread.
    val favoriteSourcePathSet = remember(favoriteSourcePaths) {
        favoriteSourcePaths.toHashSet()
    }

    Scaffold(
        topBar = {
            if (!isWatch) {
                Column {
                    if (showPrimaryTopBar) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Silicon Player",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.background
                            ),
                            actions = {
                                onExitBrowser?.let { exitBrowser ->
                                    IconButton(onClick = exitBrowser) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Go to app home"
                                        )
                                    }
                                }
                                onOpenSettings?.let { openSettings ->
                                    IconButton(onClick = openSettings) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Open settings"
                                        )
                                    }
                                }
                            }
                        )
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .contextMenuSecondaryClick {
                                        if (resolveCurrentFolderPathForActions() != null) {
                                            currentFolderMenuExpanded = true
                                        }
                                    }
                                    .combinedClickable(
                                        onClick = {
                                            if (showLocalStorageSelector) {
                                                selectorExpanded = true
                                            }
                                        },
                                        onLongClick = {
                                            if (resolveCurrentFolderPathForActions() != null) {
                                                currentFolderMenuExpanded = true
                                            }
                                        }
                                    )
                                    .padding(horizontal = 2.dp)
                            ) {
                                Box {
                                    BrowserToolbarSelectorLabel(
                                        expanded = selectorExpanded,
                                        onClick = {
                                            if (showLocalStorageSelector) {
                                                selectorExpanded = true
                                            }
                                        },
                                        onLongClick = {
                                            if (resolveCurrentFolderPathForActions() != null) {
                                                currentFolderMenuExpanded = true
                                            }
                                        },
                                        modifier = Modifier.padding(start = 6.dp),
                                        enabled = showLocalStorageSelector,
                                        focusRequester = selectorButtonFocusRequester
                                    )
                                    DropdownMenu(
                                        expanded = selectorExpanded,
                                        onDismissRequest = { selectorExpanded = false }
                                    ) {
                                        if (showLocalStorageSelector) {
                                            Text(
                                                text = "Storage locations",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("Home")
                                                        Text(
                                                            "Storage locations",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Home,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    selectorExpanded = false
                                                    openBrowserHome()
                                                }
                                            )
                                            storageLocations.forEach { location ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(location.typeLabel)
                                                            Text(
                                                                location.name,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = iconForStorageKind(location.kind, isTablet),
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        selectorExpanded = false
                                                        openLocation(location)
                                                    }
                                                )
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = "Directory tree",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Browse directory tree…") },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null
                                                    )
                                                },
                                                enabled = currentDirectory != null,
                                                onClick = {
                                                    selectorExpanded = false
                                                    directoryTreeOpen = true
                                                }
                                            )
                                        } else {
                                            val contextLabel = archiveToolbarContext
                                            Text(
                                                text = "Archive source",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(contextLabel?.sourceTypeLabel ?: "Archive")
                                                        Text(
                                                            contextLabel?.sourceLabel ?: "Remote source",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = contextLabel?.sourceIcon ?: Icons.Default.Folder,
                                                        contentDescription = null
                                                    )
                                                },
                                                enabled = false,
                                                onClick = {}
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = currentFolderMenuExpanded,
                                        onDismissRequest = { currentFolderMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Pin folder to home",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                                            colors = MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.onSurface,
                                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            onClick = {
                                                val folderPath = resolveCurrentFolderPathForActions()
                                                if (folderPath == null) {
                                                    currentFolderMenuExpanded = false
                                                    return@DropdownMenuItem
                                                }
                                                val recentEntry = RecentPathEntry(
                                                    path = folderPath,
                                                    locationId = selectedLocationId,
                                                    title = folderTitleForDisplay(folderPath)
                                                )
                                                val preview = previewPinnedHomeEntryInsertion(
                                                    current = pinnedHomeEntries,
                                                    candidate = HomePinnedEntry(
                                                        path = recentEntry.path,
                                                        isFolder = true,
                                                        locationId = recentEntry.locationId,
                                                        title = recentEntry.title
                                                    ),
                                                    maxItems = PINNED_HOME_ENTRIES_LIMIT
                                                )
                                                if (preview.requiresConfirmation) {
                                                    pendingPinEvictionCandidate = preview.evictionCandidate
                                                    pendingPinConfirmation = recentEntry to true
                                                } else {
                                                    onPinHomeEntry(recentEntry, true)
                                                    toastHandler.showToast("Pinned folder to home")
                                                }
                                                currentFolderMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Copy path",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                                            colors = MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.onSurface,
                                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            onClick = {
                                                val folderPath = resolveCurrentFolderPathForActions()
                                                if (folderPath != null) {
                                                    clipboardManager.setText(AnnotatedString(folderPath))
                                                    toastHandler.showToast("Copied path")
                                                }
                                                currentFolderMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Info",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                                            colors = MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.onSurface,
                                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            onClick = {
                                                showCurrentFolderInfoDialog()
                                                currentFolderMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                                BrowserToolbarPathRow(
                                    icon = subtitleIcon,
                                    subtitle = subtitle,
                                    contentStartPadding = 12.dp
                                )
                            }
                            if (selectedLocationId != null) {
                                BrowserSelectionToolbarControls(
                                    visible = browserSelectionController.isSelectionMode,
                                    canSelectAny = filteredFileList.isNotEmpty(),
                                    onSelectAll = {
                                        browserSelectionController.selectAll(
                                            filteredFileList.map { item -> item.file.absolutePath }
                                        )
                                    },
                                    onDeselectAll = { browserSelectionController.deselectAll() },
                                    actionItems = listOf(
                                        BrowserSelectionActionItem(
                                            label = "Play",
                                            icon = Icons.Default.PlayArrow,
                                            enabled = selectedRegularFileItems().size == 1,
                                            onClick = {
                                                val selectedItem = selectedRegularFileItems().singleOrNull()
                                                if (selectedItem != null) {
                                                    browserSelectionController.exitSelectionMode()
                                                    openFileItem(selectedItem)
                                                }
                                            }
                                        ),
                                        BrowserSelectionActionItem(
                                            label = if (selectedRegularFileItems().size == 1) {
                                                "Save file"
                                            } else {
                                                "Save files"
                                            },
                                            icon = Icons.Default.Save,
                                            enabled = selectedRegularFileItems().isNotEmpty(),
                                            onClick = {
                                                val exportFiles = selectedRegularFileItems().map { it.file }
                                                if (exportFiles.isNotEmpty()) {
                                                    fileExportHandler.exportFiles(exportFiles)
                                                    browserSelectionController.exitSelectionMode()
                                                }
                                            }
                                        ),
                                        BrowserSelectionActionItem(
                                            label = selectedAnyItems().singleOrNull()?.let { item ->
                                                if (item.isDirectory) "Pin folder to home" else "Pin file to home"
                                            } ?: "Pin to home",
                                            icon = Icons.Default.Home,
                                            enabled = selectedAnyItems().size == 1,
                                            onClick = {
                                                val selectedItem = selectedAnyItems().singleOrNull() ?: return@BrowserSelectionActionItem
                                                val recentEntry = RecentPathEntry(
                                                    path = selectedItem.file.absolutePath,
                                                    locationId = selectedLocationId,
                                                    title = if (selectedItem.isDirectory) selectedItem.name else null
                                                )
                                                val isFolder = selectedItem.isDirectory
                                                val preview = previewPinnedHomeEntryInsertion(
                                                    current = pinnedHomeEntries,
                                                    candidate = HomePinnedEntry(
                                                        path = recentEntry.path,
                                                        isFolder = isFolder,
                                                        locationId = recentEntry.locationId,
                                                        title = recentEntry.title
                                                    ),
                                                    maxItems = PINNED_HOME_ENTRIES_LIMIT
                                                )
                                                if (preview.requiresConfirmation) {
                                                    pendingPinEvictionCandidate = preview.evictionCandidate
                                                    pendingPinConfirmation = recentEntry to isFolder
                                                } else {
                                                    onPinHomeEntry(recentEntry, isFolder)
                                                    toastHandler.showToast(if (isFolder) "Pinned folder to home" else "Pinned file to home")
                                                }
                                            }
                                        ),
                                        BrowserSelectionActionItem(
                                            label = "Delete",
                                            icon = Icons.Default.Delete,
                                            enabled = selectedRegularFileItems().isNotEmpty(),
                                            onClick = {
                                                pendingDeleteFilePaths = selectedRegularFileItems()
                                                    .map { it.file.absolutePath }
                                            }
                                        ),
                                        BrowserSelectionActionItem(
                                            label = "Info",
                                            icon = Icons.Default.Info,
                                            enabled = browserSelectionController.selectedKeys.isNotEmpty(),
                                            onClick = { showSelectionInfoDialog() }
                                        )
                                    ),
                                    onCancel = { browserSelectionController.exitSelectionMode() }
                                )
                                BrowserToolbarSearchButton(
                                    onClick = {
                                        if (browserSearchController.isVisible) {
                                            browserSearchController.hide()
                                        } else {
                                            browserSearchController.show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    BrowserSearchToolbarRow(
                        visible = currentDirectory != null && browserSearchController.isVisible,
                        queryInput = browserSearchController.input,
                        onQueryInputChanged = browserSearchController::onInputChange,
                        onClose = browserSearchController::hide
                    )
                    HorizontalDivider()
                }
            }
        }
    ) { paddingValues ->
        LaunchedEffect(selectedLocationId, currentDirectory?.absolutePath) {
            if (currentDirectory == null) {
                browserSearchController.hide()
            }
        }
        fun triggerPullRefresh() {
            if (isPullRefreshing) return
            coroutineScope.launch {
                isPullRefreshing = true
                if (currentDirectory == null) {
                    storageLocationsRefreshToken += 1
                    delay(240)
                } else {
                    val refreshDir = currentDirectory ?: selectedLocation?.directory
                    if (refreshDir != null) {
                        loadDirectoryAsync(refreshDir)
                    }
                    val waitDeadline = System.currentTimeMillis() + 2200L
                    while (isLoadingDirectory && System.currentTimeMillis() < waitDeadline) {
                        delay(24)
                    }
                }
                isPullRefreshing = false
            }
        }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isPullRefreshing,
            onRefresh = { triggerPullRefresh() }
        )
        val directoryScrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
            enabled = browserContentState.pane == BrowserPane.DirectoryEntries,
            listState = directoryListState,
            flashKey = "${browserContentState.currentDirectoryPath}|${filteredFileList.size}|${browserSearchController.debouncedQuery}",
            label = "fileBrowserDirectoryScrollbarAlpha"
        )
        var directoryScrollbarHeld by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            AnimatedContent(
                targetState = browserContentState.pane,
                transitionSpec = {
                    val loadingTransition =
                        initialState == BrowserPane.LoadingDirectory ||
                            targetState == BrowserPane.LoadingDirectory
                    browserContentTransform(
                        navDirection = browserNavDirection,
                        loadingTransition = loadingTransition,
                        loadingPageEnabled = LOCAL_BROWSER_SHOW_INTERMEDIARY_LOADING_PAGE
                    )
                },
                label = "browserContentTransition",
                modifier = Modifier.fillMaxSize()
            ) { pane ->
                if (pane == BrowserPane.StorageLocations) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (isRound) 14.dp else 12.dp,
                            end = if (isRound) 14.dp else 12.dp,
                            top = if (isWatch) (if (isRound) 24.dp else 12.dp) else 16.dp,
                            bottom = bottomContentPadding + if (isRound) 56.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (isWatch) 8.dp else 10.dp)
                    ) {
                        item(key = "storage_locations_header") {
                            Text(
                                text = "Storage Locations",
                                style = if (isWatch) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleMedium,
                                fontWeight = if (isWatch) FontWeight.Bold else FontWeight.SemiBold,
                                textAlign = if (isWatch) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = if (isWatch) 0.dp else 4.dp,
                                        bottom = if (isWatch) 4.dp else 0.dp
                                    )
                            )
                        }
                        onExitBrowser?.let { exitBrowser ->
                            item(key = "storage_app_home") {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = if (isWatch) RoundedCornerShape(16.dp) else CardDefaults.elevatedShape,
                                    onClick = exitBrowser
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                vertical = if (isWatch) 8.dp else 12.dp,
                                                horizontal = if (isWatch) 12.dp else 16.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(if (isWatch) 20.dp else 24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(if (isWatch) 10.dp else 16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Home screen",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Return to player home",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        items(storageLocations) { location ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = if (isWatch) RoundedCornerShape(16.dp) else CardDefaults.elevatedShape,
                                onClick = { openLocation(location) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = if (isWatch) 8.dp else 12.dp,
                                            horizontal = if (isWatch) 12.dp else 16.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = iconForStorageKind(location.kind, isTablet),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(if (isWatch) 20.dp else 24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(if (isWatch) 10.dp else 16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = location.typeLabel,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = location.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (pane == BrowserPane.LoadingDirectory) {
                    BrowserLoadingCard(
                        icon = Icons.Default.Folder,
                        title = "Loading directory...",
                        subtitle = currentDirectory?.absolutePath ?: "Fetching folder entries",
                        logLines = loadingLogLines,
                        waitingLine = "[00] Waiting for filesystem response..."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = directoryListState,
                        contentPadding = PaddingValues(
                            start = if (isWatch) (if (isRound) 12.dp else 8.dp) else (if (isRound) 10.dp else 0.dp),
                            end = if (isWatch) (if (isRound) 12.dp else 8.dp) else (if (isRound) 10.dp else 0.dp),
                            top = if (isWatch) (if (isRound) 24.dp else 12.dp) else (if (isRound) 6.dp else 0.dp),
                            bottom = bottomContentPadding + if (isRound) 56.dp else 0.dp
                        ),
                        verticalArrangement = if (isWatch) Arrangement.spacedBy(4.dp) else Arrangement.Top
                    ) {
                        if (isWatch) {
                            item(key = "watch_directory_header") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .contextMenuSecondaryClick {
                                            if (resolveCurrentFolderPathForActions() != null) {
                                                currentFolderMenuExpanded = true
                                            }
                                        }
                                        .combinedClickable(
                                            onClick = {
                                                if (showLocalStorageSelector) {
                                                    selectorExpanded = true
                                                }
                                            },
                                            onLongClick = {
                                                if (resolveCurrentFolderPathForActions() != null) {
                                                    currentFolderMenuExpanded = true
                                                }
                                            }
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = currentDirectory?.name ?: selectedLocation?.name ?: "Files",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val itemCount = filteredFileList.size
                                    Text(
                                        text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (showParentDirectoryEntry) {
                            val parentEntryKey = "parent:${browserContentState.currentDirectoryPath}"
                            item(key = parentEntryKey) {
                                AnimatedFileBrowserEntry(
                                    itemKey = parentEntryKey,
                                    animationEpoch = directoryAnimationEpoch,
                                    animateOnFirstComposition = isLoadingDirectory,
                                    enabled = shouldAnimateDirectoryEntries
                                ) {
                                    val rowFocusRequester = remember(parentEntryKey) { FocusRequester() }
                                    DisposableEffect(parentEntryKey) {
                                        browserEntryFocusRequesters[parentEntryKey] = rowFocusRequester
                                        onDispose { browserEntryFocusRequesters.remove(parentEntryKey) }
                                    }
                                    ParentDirectoryItemRow(
                                        onClick = { navigateUpWithinLocation() },
                                        isWatch = isWatch,
                                        rightFocusRequester = selectorButtonFocusRequester,
                                        rowFocusRequester = rowFocusRequester,
                                        onFocused = { browserFocusedEntryKey = parentEntryKey }
                                    )
                                }
                            }
                        }
                        if (filteredFileList.isEmpty() && browserSearchController.debouncedQuery.isNotBlank()) {
                            item(key = "search-empty") {
                                BrowserSearchNoResultsCard(query = browserSearchController.debouncedQuery)
                            }
                        } else {
                            itemsIndexed(
                                items = filteredFileList,
                                key = { _, item -> item.file.absolutePath }
                            ) { index, item ->
                                val entryKey = item.file.absolutePath
                                val isSelected = browserSelectionController.selectedKeys.contains(entryKey)
                                val hasSelectedAbove = if (index > 0) {
                                    val aboveKey = filteredFileList[index - 1].file.absolutePath
                                    browserSelectionController.selectedKeys.contains(aboveKey)
                                } else {
                                    false
                                }
                                val hasSelectedBelow = if (index < filteredFileList.lastIndex) {
                                    val belowKey = filteredFileList[index + 1].file.absolutePath
                                    browserSelectionController.selectedKeys.contains(belowKey)
                                } else {
                                    false
                                }
                                AnimatedFileBrowserEntry(
                                    itemKey = entryKey,
                                    animationEpoch = directoryAnimationEpoch,
                                    animateOnFirstComposition = isLoadingDirectory,
                                    enabled = shouldAnimateDirectoryEntries
                                ) {
                                    val rowFocusRequester = remember(entryKey) { FocusRequester() }
                                    DisposableEffect(entryKey) {
                                        browserEntryFocusRequesters[entryKey] = rowFocusRequester
                                        onDispose { browserEntryFocusRequesters.remove(entryKey) }
                                    }
                                    FileItemRow(
                                        item = item,
                                        isPlaying = item.file == playingFile,
                                        isPlayingPlaylist = item.file == playingPlaylistFile,
                                        isFavorited = favoriteSourcePathSet.contains(entryKey),
                                        isSelected = isSelected,
                                        hasSelectedAbove = hasSelectedAbove,
                                        hasSelectedBelow = hasSelectedBelow,
                                        showFavoriteToggle = browserSelectionController.isSelectionMode,
                                        showFileIconChipBackground = showFileIconChipBackground,
                                        showLocalThumbnailPreviews = showLocalThumbnailPreviews,
                                        allowThumbnailPreviewLoads = allowThumbnailPreviewLoads,
                                        folderSummaryCache = folderSummaryCache,
                                        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                                        rightFocusRequester = selectorButtonFocusRequester,
                                        rowFocusRequester = rowFocusRequester,
                                        onFocused = { browserFocusedEntryKey = entryKey },
                                        onLongClick = {
                                            if (isWatch) {
                                                watchActionTargetItem = item
                                            } else {
                                                if (browserSelectionController.isSelectionMode) {
                                                    val didSelectRange =
                                                        browserSelectionController.selectedKeys.size == 1 &&
                                                            browserSelectionController.selectRangeTo(
                                                                key = entryKey,
                                                                orderedKeys = filteredFileList.map { it.file.absolutePath }
                                                            )
                                                    if (!didSelectRange) {
                                                        browserSelectionController.toggleSelection(entryKey)
                                                    }
                                                } else {
                                                    browserSelectionController.enterSelectionWith(entryKey)
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (browserSelectionController.isSelectionMode) {
                                                browserSelectionController.toggleSelection(entryKey)
                                                return@FileItemRow
                                            }
                                            openFileItem(item)
                                        },
                                        onToggleFavorite = { onToggleFavoriteFile(item.file) },
                                        onAddToPlaylist = {
                                            pendingPlaylistAddSource = item.file.absolutePath to
                                                inferredDisplayTitleForName(item.file.name)
                                        },
                                        onPinToHome = { pinItemToHome(item) },
                                        onShowInfo = { showItemInfoDialog(item) },
                                        onSelect = { browserSelectionController.enterSelectionWith(entryKey) },
                                        onDelete = if (!item.isDirectory && !item.isArchive) {
                                            { pendingDeleteFilePaths = listOf(item.file.absolutePath) }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = isPullRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            )
            if (browserContentState.pane == BrowserPane.DirectoryEntries) {
                BrowserLazyListScrollbar(
                    listState = directoryListState,
                    onDragActiveChanged = { isActive -> directoryScrollbarHeld = isActive },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = if (isRound) 24.dp else 8.dp,
                            end = if (isRound) 6.dp else 2.dp,
                            bottom = bottomContentPadding + if (isRound) 40.dp else 8.dp
                        )
                        .fillMaxHeight()
                        .width(if (isWatch) 16.dp else 24.dp)
                        .graphicsLayer(alpha = if (directoryScrollbarHeld) 1f else directoryScrollbarAlpha)
                )
            }
        }
    }

    if (isWatch && selectorExpanded) {
        Dialog(
            onDismissRequest = { selectorExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (isRound) 14.dp else 10.dp,
                        end = if (isRound) 14.dp else 10.dp,
                        top = if (isRound) 24.dp else 12.dp,
                        bottom = if (isRound) 28.dp else 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item(key = "watch_selector_title") {
                        Text(
                            text = "Storage Locations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                    onExitBrowser?.let { exitBrowser ->
                        item(key = "watch_selector_app_home") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .clickable {
                                        selectorExpanded = false
                                        exitBrowser()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Home screen",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Return to player home",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item(key = "watch_selector_home") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    selectorExpanded = false
                                    openBrowserHome()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage locations",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Top-level folders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(
                        items = storageLocations,
                        key = { it.id }
                    ) { location ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    selectorExpanded = false
                                    openLocation(location)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = iconForStorageKind(location.kind, isTablet),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = location.typeLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = location.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isWatch && currentFolderMenuExpanded) {
        Dialog(
            onDismissRequest = { currentFolderMenuExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (isRound) 14.dp else 10.dp,
                        end = if (isRound) 14.dp else 10.dp,
                        top = if (isRound) 24.dp else 12.dp,
                        bottom = if (isRound) 28.dp else 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item(key = "watch_folder_options_title") {
                        Text(
                            text = currentDirectory?.name ?: "Folder Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                    item(key = "watch_pin_folder") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    val folderPath = resolveCurrentFolderPathForActions()
                                    if (folderPath != null) {
                                        val recentEntry = RecentPathEntry(
                                            path = folderPath,
                                            locationId = selectedLocationId,
                                            title = folderTitleForDisplay(folderPath)
                                        )
                                        val preview = previewPinnedHomeEntryInsertion(
                                            current = pinnedHomeEntries,
                                            candidate = HomePinnedEntry(
                                                path = recentEntry.path,
                                                isFolder = true,
                                                locationId = recentEntry.locationId,
                                                title = recentEntry.title
                                            ),
                                            maxItems = PINNED_HOME_ENTRIES_LIMIT
                                        )
                                        if (preview.requiresConfirmation) {
                                            pendingPinEvictionCandidate = preview.evictionCandidate
                                            pendingPinConfirmation = recentEntry to true
                                        } else {
                                            onPinHomeEntry(recentEntry, true)
                                            toastHandler.showToast("Pinned folder to home")
                                        }
                                    }
                                    currentFolderMenuExpanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pin folder to home",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    item(key = "watch_folder_info") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    showCurrentFolderInfoDialog()
                                    currentFolderMenuExpanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Info",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPlaylistAddSource?.let { (source, title) ->
        AddToPlaylistChooserDialog(
            playlists = playlists,
            pendingSources = setOf(source),
            onConfirm = { playlistId, newTitle ->
                onAddSourceToPlaylist(source, title, playlistId, newTitle)
            },
            onRemoveFromPlaylist = { playlistId ->
                onRemoveSourceFromPlaylist(source, playlistId)
            },
            onDismiss = { pendingPlaylistAddSource = null }
        )
    }
    watchActionTargetItem?.let { targetItem ->
        val isFolder = targetItem.isDirectory
        Dialog(
            onDismissRequest = { watchActionTargetItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (isRound) 14.dp else 10.dp,
                        end = if (isRound) 14.dp else 10.dp,
                        top = if (isRound) 24.dp else 12.dp,
                        bottom = if (isRound) 28.dp else 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item(key = "target_title") {
                        Text(
                            text = targetItem.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                    if (!isFolder) {
                        item(key = "action_play") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                    .clickable {
                                        watchActionTargetItem = null
                                        openFileItem(targetItem)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Play",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    item(key = "action_pin") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    val recentEntry = RecentPathEntry(
                                        path = targetItem.file.absolutePath,
                                        locationId = selectedLocationId,
                                        title = if (isFolder) targetItem.name else null
                                    )
                                    val preview = previewPinnedHomeEntryInsertion(
                                        current = pinnedHomeEntries,
                                        candidate = HomePinnedEntry(
                                            path = recentEntry.path,
                                            isFolder = isFolder,
                                            locationId = recentEntry.locationId,
                                            title = recentEntry.title
                                        ),
                                        maxItems = PINNED_HOME_ENTRIES_LIMIT
                                    )
                                    if (preview.requiresConfirmation) {
                                        pendingPinEvictionCandidate = preview.evictionCandidate
                                        pendingPinConfirmation = recentEntry to isFolder
                                    } else {
                                        onPinHomeEntry(recentEntry, isFolder)
                                        toastHandler.showToast(if (isFolder) "Pinned folder to home" else "Pinned file to home")
                                    }
                                    watchActionTargetItem = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isFolder) "Pin folder to home" else "Pin file to home",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    item(key = "action_info") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    browserInfoFields = buildBrowserInfoFields(
                                        entries = listOf(
                                            BrowserInfoEntry(
                                                name = targetItem.name,
                                                isDirectory = targetItem.isDirectory,
                                                sizeBytes = if (targetItem.isDirectory) null else targetItem.size
                                            )
                                        ),
                                        path = targetItem.file.absolutePath,
                                        storageOrHostLabel = "Storage",
                                        storageOrHost = selectedLocation?.let { location ->
                                            "${location.typeLabel} (${location.name})"
                                        } ?: "Unknown"
                                    )
                                    showBrowserInfoDialog = true
                                    watchActionTargetItem = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Info",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    if (!targetItem.file.name.isEmpty()) {
                        item(key = "action_delete") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable {
                                        pendingDeleteFilePaths = listOf(targetItem.file.absolutePath)
                                        watchActionTargetItem = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBrowserInfoDialog) {
        BrowserInfoDialog(
            title = "Info",
            fields = browserInfoFields,
            onDismiss = { showBrowserInfoDialog = false }
        )
    }
    val treeRoot = selectedLocation?.directory
    val treeCurrent = currentDirectory
    if (directoryTreeOpen && treeRoot != null && treeCurrent != null) {
        DirectoryTreeSheet(
            root = treeRoot,
            currentDirectory = treeCurrent,
            onNavigate = { target -> navigateTo(target) },
            onDismiss = { directoryTreeOpen = false }
        )
    }
    textPreviewDialogState?.let { (fileName, textContent) ->
        BrowserTextPreviewDialog(
            fileName = fileName,
            textContent = textContent,
            onDismiss = { textPreviewDialogState = null }
        )
    }
    imagePreviewDialogState?.let { (fileName, imageFile) ->
        BrowserImagePreviewDialog(
            fileName = fileName,
            imageFile = imageFile,
            onDismiss = { imagePreviewDialogState = null }
        )
    }
    if (pendingDeleteFilePaths.isNotEmpty()) {
        val performDelete = {
            val pathsToDelete = pendingDeleteFilePaths
            pendingDeleteFilePaths = emptyList()
            coroutineScope.launch {
                val deletedCount = withContext(Dispatchers.IO) {
                    pathsToDelete.count { path ->
                        runCatching {
                            File(path).takeIf { it.exists() && it.isFile }?.delete() == true
                        }.getOrDefault(false)
                    }
                }
                val failedCount = pathsToDelete.size - deletedCount
                toastHandler.showToast(
                    "Deleted $deletedCount file(s)" + if (failedCount > 0) " ($failedCount failed)" else ""
                )
                browserSelectionController.exitSelectionMode()
                currentDirectory?.let { loadDirectoryAsync(it) }
            }
        }

        if (isWatch) {
            WatchDialogContainer(
                title = "Delete files",
                onDismissRequest = { pendingDeleteFilePaths = emptyList() }
            ) {
                Text(
                    text = "Delete ${pendingDeleteFilePaths.size} selected file(s)?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Button(
                    onClick = { performDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = { pendingDeleteFilePaths = emptyList() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { pendingDeleteFilePaths = emptyList() },
                title = { Text("Delete files") },
                text = {
                    Text(
                        text = "Delete ${pendingDeleteFilePaths.size} selected file(s)?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { performDelete() }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteFilePaths = emptyList() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    pendingPinConfirmation?.let { (entry, isFolder) ->
        val evictionCandidate = pendingPinEvictionCandidate
        val messageText = buildString {
            append("You can pin up to $PINNED_HOME_ENTRIES_LIMIT entries. ")
            if (evictionCandidate != null) {
                append("The oldest pinned ")
                append(if (evictionCandidate.isFolder) "folder" else "file")
                append(" will be removed to make space.")
            } else {
                append("The oldest pinned entry will be removed to make space.")
            }
        }
        val onContinue = {
            onPinHomeEntry(entry, isFolder)
            pendingPinConfirmation = null
            pendingPinEvictionCandidate = null
            toastHandler.showToast(if (isFolder) "Pinned folder to home" else "Pinned file to home")
        }
        val onCancel = {
            pendingPinConfirmation = null
            pendingPinEvictionCandidate = null
        }

        if (isWatch) {
            WatchDialogContainer(
                title = "Pin limit reached",
                onDismissRequest = onCancel
            ) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Continue")
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text("Pin limit reached") },
                text = {
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = onContinue) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ParentDirectoryItemRow(
    onClick: () -> Unit,
    isWatch: Boolean = false,
    rightFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val iconBoxSize = if (isWatch) 32.dp else FILE_ICON_BOX_SIZE
    val iconGlyphSize = if (isWatch) 16.dp else FILE_ICON_GLYPH_SIZE
    val chipCorner = if (isWatch) 8.dp else 11.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isWatch) 14.dp else 0.dp))
            .background(
                if (isWatch) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
            .focusProperties {
                if (rightFocusRequester != null) {
                    right = rightFocusRequester
                }
            }
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onFocused?.invoke() }
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = if (isWatch) 10.dp else 16.dp,
                vertical = if (isWatch) 7.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(iconBoxSize),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(chipCorner)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Parent directory",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(iconGlyphSize)
                )
            }
        }
        Spacer(modifier = Modifier.width(if (isWatch) 10.dp else 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "..",
                style = if (isWatch) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = "Parent directory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AnimatedFileBrowserEntry(
    itemKey: String,
    animationEpoch: Int,
    animateOnFirstComposition: Boolean,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    var visible by remember(itemKey, animationEpoch) {
        mutableStateOf(!animateOnFirstComposition)
    }
    LaunchedEffect(itemKey, animationEpoch, animateOnFirstComposition) {
        if (!animateOnFirstComposition) {
            visible = true
            return@LaunchedEffect
        }
        withFrameNanos { }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = FILE_ENTRY_ANIM_DURATION_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "fileEntryAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 10.dp,
        animationSpec = tween(
            durationMillis = FILE_ENTRY_ANIM_DURATION_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "fileEntryOffset"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = offsetY.toPx()
                this.alpha = alpha
            }
    ) {
        content()
    }
}

private fun shouldPublishDirectoryAllAtOnce(totalItems: Int): Boolean {
    return totalItems <= DIRECTORY_DIRECT_PUBLISH_MAX_ITEMS
}

private fun buildArchiveToolbarContext(logicalPath: String): ArchiveToolbarContext? {
    val parsed = parseArchiveLogicalPath(logicalPath) ?: return null
    val archiveLocation = parsed.first
    val inArchivePath = parsed.second
    val archiveSource = parseArchiveSourceId(archiveLocation)
    val archiveContainerLocation = archiveSource?.archivePath ?: archiveLocation
    val archiveContainerDisplay = when {
        parseSmbSourceSpecFromInput(archiveContainerLocation) != null -> {
            val spec = parseSmbSourceSpecFromInput(archiveContainerLocation) ?: return null
            decodePercentEncodedForDisplay(buildSmbDisplayUri(spec)) ?: buildSmbDisplayUri(spec)
        }
        parseHttpSourceSpecFromInput(archiveContainerLocation) != null -> {
            val spec = parseHttpSourceSpecFromInput(archiveContainerLocation) ?: return null
            decodePercentEncodedForDisplay(buildHttpDisplayUri(spec)) ?: buildHttpDisplayUri(spec)
        }
        else -> decodePercentEncodedForDisplay(archiveContainerLocation) ?: archiveContainerLocation
    }
    val archiveEntryDisplay = archiveSource
        ?.entryPath
        ?.split('/')
        ?.filter { it.isNotBlank() }
        ?.joinToString("/") { segment -> decodePercentEncodedForDisplay(segment) ?: segment }
        ?.takeIf { it.isNotBlank() }
    val inArchiveDisplay = inArchivePath
        ?.split('/')
        ?.filter { it.isNotBlank() }
        ?.joinToString("/") { segment -> decodePercentEncodedForDisplay(segment) ?: segment }
        ?.takeIf { it.isNotBlank() }
    val subtitle = buildString {
        append(archiveContainerDisplay)
        if (!archiveEntryDisplay.isNullOrBlank()) {
            append('/')
            append(archiveEntryDisplay)
        }
        if (!inArchiveDisplay.isNullOrBlank()) {
            append('/')
            append(inArchiveDisplay)
        }
    }
    return when {
        parseSmbSourceSpecFromInput(archiveContainerLocation) != null -> ArchiveToolbarContext(
            subtitle = subtitle,
            isRemote = true,
            sourceLabel = archiveContainerDisplay,
            sourceTypeLabel = "SMB archive",
            sourceIcon = NetworkIcons.SmbShare
        )
        parseHttpSourceSpecFromInput(archiveContainerLocation) != null -> ArchiveToolbarContext(
            subtitle = subtitle,
            isRemote = true,
            sourceLabel = archiveContainerDisplay,
            sourceTypeLabel = "HTTP archive",
            sourceIcon = NetworkIcons.WorldCode
        )
        else -> ArchiveToolbarContext(
            subtitle = subtitle,
            isRemote = false,
            sourceLabel = archiveContainerDisplay,
            sourceTypeLabel = "Local archive",
            sourceIcon = Icons.Default.Folder
        )
    }
}

private fun directoryPublishBatchSize(totalItems: Int): Int = when {
    totalItems <= 5000 -> 256
    totalItems <= 10000 -> 512
    else -> 1024
}
private typealias StorageLocation = PlatformStorageLocation
private typealias StorageKind = StorageLocationKind

private fun iconForStorageKind(kind: StorageKind, isTablet: Boolean = false): ImageVector {
    return when (kind) {
        StorageKind.ROOT -> Icons.Default.Folder
        StorageKind.INTERNAL -> {
            if (isTablet) Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid
        }
        StorageKind.SD -> Icons.Default.SdCard
        StorageKind.USB -> Icons.Default.Usb
    }
}


private fun isWithinRoot(file: File, root: File): Boolean {
    val filePath = file.absolutePath
    val rootPath = root.absolutePath.trimEnd('/')
    if (rootPath.isEmpty()) {
        // "/" should contain any absolute path.
        return filePath.startsWith("/")
    }
    return filePath == rootPath || filePath.startsWith("$rootPath/")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    isPlaying: Boolean,
    isPlayingPlaylist: Boolean,
    isFavorited: Boolean = false,
    isSelected: Boolean = false,
    hasSelectedAbove: Boolean = false,
    hasSelectedBelow: Boolean = false,
    showFavoriteToggle: Boolean = false,
    showFileIconChipBackground: Boolean,
    showLocalThumbnailPreviews: Boolean,
    allowThumbnailPreviewLoads: Boolean,
    folderSummaryCache: MutableMap<String, String>,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint> = emptyMap(),
    rightFocusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    onPinToHome: (() -> Unit)? = null,
    onShowInfo: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val isWatch = LocalIsWatchDevice.current
    val iconBoxSize = if (isWatch) 32.dp else FILE_ICON_BOX_SIZE
    val iconGlyphSize = if (isWatch) 16.dp else FILE_ICON_GLYPH_SIZE
    val chipCorner = if (isWatch) 8.dp else 11.dp
    var showPlayWith by remember { mutableStateOf(false) }
    val playWithPrefs = LocalAppPreferences.current
    val thumbnailLoader = LocalArtworkThumbnailLoader.current
    if (showPlayWith) {
        PlayWithDialog(
            file = item.file,
            prefs = playWithPrefs,
            onPlay = {
                showPlayWith = false
                onClick()
            },
            onDismiss = { showPlayWith = false }
        )
    }
    // Cache per-row derived values that depend only on the FileItem identity.
    // Without these `remember`s, every row recomposition (e.g. when scroll
    // start/stop flips allowThumbnailPreviewLoads) re-ran filename parsing,
    // MimeTypeMap lookups and shape allocation across every visible row.
    val selectionShape = remember(hasSelectedAbove, hasSelectedBelow, isWatch) {
        val cornerRadius = if (isWatch) 14.dp else 18.dp
        RoundedCornerShape(
            topStart = if (hasSelectedAbove && !isWatch) 0.dp else cornerRadius,
            topEnd = if (hasSelectedAbove && !isWatch) 0.dp else cornerRadius,
            bottomStart = if (hasSelectedBelow && !isWatch) 0.dp else cornerRadius,
            bottomEnd = if (hasSelectedBelow && !isWatch) 0.dp else cornerRadius
        )
    }
    val isVideoFile = remember(item.file.absolutePath, item.isDirectory) {
        !item.isDirectory && isLikelyVideoFile(item.file)
    }
    val previewKind = remember(item.name, item.isDirectory) {
        if (item.isDirectory) null else browserPreviewKindForName(item.name)
    }
    val decoderArtworkHint = remember(
        item.file.absolutePath,
        item.isDirectory,
        isVideoFile,
        decoderExtensionArtworkHints
    ) {
        if (item.isDirectory || isVideoFile) {
            null
        } else {
            resolveDecoderArtworkHintForFileName(item.file.name, decoderExtensionArtworkHints)
        }
    }
    val shouldShowThumbnailPreview = showLocalThumbnailPreviews &&
        item.kind == FileItem.Kind.AudioFile &&
        !item.isDirectory &&
        !isVideoFile &&
        previewKind == null
    // Cache the lastModified syscall for the lifetime of this row composition
    // (until the file path changes). Reading File.lastModified() directly as a
    // produceState key forced a stat() call on the UI thread on every
    // recomposition, which spiked frame times when scroll start/stop or playback
    // poll updates rippled through the visible rows.
    val itemLastModified = remember(item.file.absolutePath) {
        item.file.lastModified()
    }
    val thumbnailPreview by produceState<ImageBitmap?>(
        initialValue = null,
        shouldShowThumbnailPreview,
        allowThumbnailPreviewLoads,
        item.file.absolutePath,
        itemLastModified,
        item.size
    ) {
        if (!shouldShowThumbnailPreview) {
            value = null
            return@produceState
        }
        if (!allowThumbnailPreviewLoads) {
            return@produceState
        }
        delay(
            LOCAL_BROWSER_THUMBNAIL_STAGGER_BASE_MS +
                ((item.file.absolutePath.hashCode().toLong() and Long.MAX_VALUE) %
                    LOCAL_BROWSER_THUMBNAIL_STAGGER_RANGE_MS)
        )
        value = thumbnailLoader.load(item.file.absolutePath)
    }
    val subtitle by produceState(
        initialValue = if (item.isDirectory && !item.isArchive) {
            folderSummaryCache[item.file.absolutePath] ?: "Loading..."
        } else {
            val format = inferredPrimaryExtensionForName(item.file.name)?.uppercase(Locale.ROOT) ?: "UNKNOWN"
            "$format • ${formatFileSizeHumanReadable(item.size)}"
        },
        key1 = item.file.absolutePath,
        key2 = item.kind,
        key3 = item.size
    ) {
        if (item.isArchive) {
            value = withContext(Dispatchers.IO) {
                "ZIP archive • ${formatFileSizeHumanReadable(item.size)}"
            }
            return@produceState
        }
        if (item.isDirectory) {
            val cacheKey = item.file.absolutePath
            val cached = folderSummaryCache[cacheKey]
            if (cached != null) {
                value = cached
                return@produceState
            }
            val resolved = withContext(Dispatchers.IO) {
                buildFolderSummary(item.file)
            }
            folderSummaryCache[cacheKey] = resolved
            value = resolved
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val format = inferredPrimaryExtensionForName(item.file.name)?.uppercase(Locale.ROOT) ?: "UNKNOWN"
            "$format • ${formatFileSizeHumanReadable(item.size)}"
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(selectionShape)
            .background(
                if (isSelected || (isWatch && (isPlaying || isPlayingPlaylist))) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else if (isWatch) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                }
            )
            .focusProperties {
                if (rightFocusRequester != null) {
                    right = rightFocusRequester
                }
            }
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused) onFocused?.invoke() }
            .tvKeyLongPress(onLongClick)
            .contextMenuSecondaryClick { menuExpanded = true }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable()
            .padding(
                horizontal = if (isWatch) 10.dp else 16.dp,
                vertical = if (isWatch) 7.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isCurrentlyPlaying = isPlaying || isPlayingPlaylist
        val showIconChipBackground = item.isDirectory || showFileIconChipBackground
        val chipShape = RoundedCornerShape(chipCorner)
        val chipContainerColor = if (item.isDirectory) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val chipContentColor = if (item.isDirectory) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        val iconTint = if (showIconChipBackground) {
            chipContentColor
        } else {
            MaterialTheme.colorScheme.primary
        }
        Box(
            modifier = Modifier.size(iconBoxSize),
            contentAlignment = Alignment.Center
        ) {
            val previewBitmap = thumbnailPreview
            val previewAlpha by animateFloatAsState(
                targetValue = if (previewBitmap != null) 1f else 0f,
                animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
                label = "browserThumbnailPreviewAlpha"
            )
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "Track thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(chipShape)
                        .graphicsLayer(alpha = previewAlpha)
                )
            } else {
                val iconContent: @Composable () -> Unit = {
                    if (item.isDirectory) {
                        if (item.isArchive) {
                            Icon(
                                imageVector = FolderZipIcon,
                                contentDescription = "ZIP archive",
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Directory",
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    } else {
                        val contentDescription = when {
                            isVideoFile -> "Video file"
                            decoderArtworkHint == DecoderArtworkHint.TrackedFile -> "Tracked file"
                            decoderArtworkHint == DecoderArtworkHint.GameFile -> "Game file"
                            previewKind == FilePreviewKind.Text -> "Text file"
                            previewKind == FilePreviewKind.Image -> "Image file"
                            item.kind == FileItem.Kind.UnsupportedFile -> "File"
                            else -> "Audio file"
                        }
                        if (isVideoFile) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else if (decoderArtworkHint == DecoderArtworkHint.TrackedFile) {
                            Icon(
                                imageVector = FileTrackedIcon,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else if (decoderArtworkHint == DecoderArtworkHint.GameFile) {
                            Icon(
                                imageVector = FileGameIcon,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else if (previewKind == FilePreviewKind.Text) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else if (previewKind == FilePreviewKind.Image) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else if (item.kind == FileItem.Kind.UnsupportedFile) {
                            Icon(
                                imageVector = FileUnsupportedIcon,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = contentDescription,
                                tint = iconTint,
                                modifier = Modifier.size(iconGlyphSize)
                            )
                        }
                    }
                }
                if (showIconChipBackground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = chipContainerColor,
                                shape = chipShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        iconContent()
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        iconContent()
                    }
                }
            }
            if (isCurrentlyPlaying) {
                val badgeSize = if (isWatch) 14.dp else 18.dp
                val badgeIconSize = if (isWatch) 9.dp else 11.dp
                val badgeOffset = if (isWatch) 2.dp else 3.dp
                val badgeCutoutColor = if (isSelected || (isWatch && isCurrentlyPlaying)) {
                    MaterialTheme.colorScheme.primaryContainer
                } else if (isWatch) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = badgeOffset, y = badgeOffset)
                        .size(badgeSize)
                        .background(color = badgeCutoutColor, shape = CircleShape)
                        .padding(1.5.dp)
                        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Currently playing",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(badgeIconSize)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(if (isWatch) 10.dp else 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = if (isWatch) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        val canShowFavoriteToggle = showFavoriteToggle &&
            !item.isDirectory &&
            item.kind == FileItem.Kind.AudioFile
        if (canShowFavoriteToggle) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorited) {
                        "Remove from favorites"
                    } else {
                        "Add to favorites"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (!isWatch && !showFavoriteToggle) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = { menuExpanded = true }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (item.isDirectory) "Open" else "Play",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.FolderOpen else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    if (!item.isDirectory &&
                        (item.kind == FileItem.Kind.AudioFile || isSupportedPlaylistFileName(item.name))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Play with...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                menuExpanded = false
                                showPlayWith = true
                            }
                        )
                    }
                    if (onAddToPlaylist != null && !item.isDirectory && item.kind == FileItem.Kind.AudioFile) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to playlist...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    if (!item.isDirectory && item.kind == FileItem.Kind.AudioFile) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite()
                            }
                        )
                    }
                    if (onPinToHome != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (item.isDirectory) "Pin folder to home" else "Pin file to home",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                menuExpanded = false
                                onPinToHome()
                            }
                        )
                    }
                    if (onShowInfo != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                menuExpanded = false
                                onShowInfo()
                            }
                        )
                    }
                    if (onSelect != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Select",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                menuExpanded = false
                                onSelect()
                            }
                        )
                    }
                    if (onDelete != null && !item.isDirectory) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error
                            ),
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun isLikelyVideoFile(file: File): Boolean {
    val candidates = extensionCandidatesForName(file.name)
    if (candidates.isEmpty()) return false
    return candidates.any { extension ->
        val mimeType = URLConnection.guessContentTypeFromName("file.$extension")
        mimeType?.startsWith("video/") == true || extension in FALLBACK_VIDEO_EXTENSIONS
    }
}

private fun formatFileSizeHumanReadable(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble().coerceAtLeast(0.0)
    var unitIndex = 0

    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        String.format(Locale.US, "%.0f %s", size, units[unitIndex])
    } else {
        String.format(Locale.US, "%.1f %s", size, units[unitIndex])
    }
}

private fun buildFolderSummary(folder: File): String {
    val children = folder.listFiles().orEmpty()
    val folderCount = children.count { it.isDirectory }
    val fileCount = children.count { it.isFile }

    val parts = mutableListOf<String>()
    if (folderCount > 0) {
        parts += pluralize(folderCount, "folder")
    }
    // Only show file count if there are files OR if there are no folders
    if (fileCount > 0 || folderCount == 0) {
        parts += pluralize(fileCount, "file")
    }
    return parts.joinToString(" • ")
}

private fun pluralize(count: Int, singular: String): String {
    val word = if (count == 1) singular else "${singular}s"
    return "$count $word"
}

private fun readTextPreviewContent(
    file: File,
    maxBytes: Int = 512 * 1024
): String? {
    if (!file.exists() || !file.isFile) return null
    return runCatching {
        val bytes = file.inputStream().use { input ->
            val buffer = ByteArray(maxBytes + 1)
            var totalRead = 0
            while (totalRead < buffer.size) {
                val read = input.read(buffer, totalRead, buffer.size - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            buffer.copyOf(totalRead)
        }
        val wasTruncated = bytes.size > maxBytes
        val previewBytes = if (wasTruncated) bytes.copyOf(maxBytes) else bytes
        val text = previewBytes.toString(Charsets.UTF_8)
        if (wasTruncated) {
            "$text\n\n[Preview truncated at ${maxBytes / 1024} KB]"
        } else {
            text
        }
    }.getOrNull()
}
