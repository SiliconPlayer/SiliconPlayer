package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.LibrarySurfaceState
import com.flopster101.siliconplayer.ui.screens.HttpFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.ui.screens.PlaylistsScreen
import com.flopster101.siliconplayer.library.LibraryAlbumDetail
import com.flopster101.siliconplayer.library.LibraryAlbum
import com.flopster101.siliconplayer.library.LibraryCollections
import com.flopster101.siliconplayer.library.LibraryRepository
import com.flopster101.siliconplayer.library.LibraryTrackEntity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import java.io.File

@Composable
internal fun MainHomeRouteHost(
    mainPadding: PaddingValues,
    currentTrackPath: String?,
    currentTrackTitle: String,
    currentTrackArtist: String,
    pinnedHomeEntries: List<HomePinnedEntry>,
    recentFolders: List<RecentPathEntry>,
    recentPlayedFiles: List<RecentPathEntry>,
    storagePresentationForEntry: (RecentPathEntry) -> StoragePresentation,
    storagePresentationForPinnedEntry: (HomePinnedEntry) -> StoragePresentation,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onOpenLibrary: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenPinnedFolder: (HomePinnedEntry) -> Unit,
    onPlayPinnedFile: (HomePinnedEntry) -> Unit,
    onOpenRecentFolder: (RecentPathEntry) -> Unit,
    onPlayRecentFile: (RecentPathEntry) -> Unit,
    onPinRecentFolder: (RecentPathEntry) -> Unit,
    onPinRecentFile: (RecentPathEntry) -> Unit,
    onPinnedFolderAction: (HomePinnedEntry, FolderEntryAction) -> Unit,
    onPinnedFileAction: (HomePinnedEntry, SourceEntryAction) -> Unit,
    onPersistRecentFileMetadata: (RecentPathEntry, String, String) -> Unit,
    onRecentFolderAction: (RecentPathEntry, FolderEntryAction) -> Unit,
    onRecentFileAction: (RecentPathEntry, SourceEntryAction) -> Unit,
    onClearPinnedEntries: () -> Unit,
    onClearRecentFolders: () -> Unit,
    onClearRecentPlayed: () -> Unit,
    canShareRecentFile: (RecentPathEntry) -> Boolean,
    canSharePinnedFile: (HomePinnedEntry) -> Boolean,
    onOpenPlayerSurface: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onOpenUrlOrPath: (() -> Unit)? = null
) {
    Box(modifier = Modifier.padding(mainPadding)) {
        HomeScreen(
            currentTrackPath = currentTrackPath,
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentTrackArtist,
            pinnedHomeEntries = pinnedHomeEntries,
            recentFolders = recentFolders,
            recentPlayedFiles = recentPlayedFiles,
            storagePresentationForEntry = storagePresentationForEntry,
            storagePresentationForPinnedEntry = storagePresentationForPinnedEntry,
            bottomContentPadding = bottomContentPadding,
            onOpenLibrary = onOpenLibrary,
            onOpenPlaylists = onOpenPlaylists,
            onOpenNetwork = onOpenNetwork,
            onOpenPinnedFolder = onOpenPinnedFolder,
            onPlayPinnedFile = onPlayPinnedFile,
            onOpenRecentFolder = onOpenRecentFolder,
            onPlayRecentFile = onPlayRecentFile,
            onPinRecentFolder = onPinRecentFolder,
            onPinRecentFile = onPinRecentFile,
            onPinnedFolderAction = onPinnedFolderAction,
            onPinnedFileAction = onPinnedFileAction,
            onPersistRecentFileMetadata = onPersistRecentFileMetadata,
            onRecentFolderAction = onRecentFolderAction,
            onRecentFileAction = onRecentFileAction,
            onClearPinnedEntries = onClearPinnedEntries,
            onClearRecentFolders = onClearRecentFolders,
            onClearRecentPlayed = onClearRecentPlayed,
            canShareRecentFile = canShareRecentFile,
            canSharePinnedFile = canSharePinnedFile,
            onOpenPlayerSurface = onOpenPlayerSurface,
            onOpenSettings = onOpenSettings,
            onOpenUrlOrPath = onOpenUrlOrPath
        )
    }
}

@Composable
internal fun MainNetworkRouteHost(
    mainPadding: PaddingValues,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    backHandlingEnabled: Boolean,
    nodes: List<NetworkNode>,
    currentFolderId: Long?,
    onExitNetwork: () -> Unit,
    onCurrentFolderIdChanged: (Long?) -> Unit,
    onNodesChanged: (List<NetworkNode>) -> Unit,
    onResolveRemoteSourceMetadata: (String, () -> Unit) -> Unit,
    onCancelPendingMetadataBackfill: () -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onBrowseSmbSource: (String, Long?) -> Unit,
    onBrowseHttpSource: (String, Long?, String?) -> Unit,
    pinnedHomeEntries: List<HomePinnedEntry>,
    onPinHomeEntry: (RecentPathEntry, Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(mainPadding)) {
        NetworkBrowserScreen(
            bottomContentPadding = bottomContentPadding,
            backHandlingEnabled = backHandlingEnabled,
            nodes = nodes,
            currentFolderId = currentFolderId,
            onExitNetwork = onExitNetwork,
            onCurrentFolderIdChanged = onCurrentFolderIdChanged,
            onNodesChanged = onNodesChanged,
            onResolveRemoteSourceMetadata = onResolveRemoteSourceMetadata,
            onCancelPendingMetadataBackfill = onCancelPendingMetadataBackfill,
            onOpenRemoteSource = onOpenRemoteSource,
            onBrowseSmbSource = onBrowseSmbSource,
            onBrowseHttpSource = onBrowseHttpSource,
            pinnedHomeEntries = pinnedHomeEntries,
            onPinHomeEntry = onPinHomeEntry
        )
    }
}

@Composable
internal fun MainPlaylistsRouteHost(
    mainPadding: PaddingValues,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    backHandlingEnabled: Boolean,
    libraryState: PlaylistLibraryState,
    pinnedHomeEntries: List<HomePinnedEntry>,
    surfaceState: LibrarySurfaceState,
    activePlaylist: StoredPlaylist?,
    activePlaylistEntryId: String? = null,
    currentPlaybackSourceId: String?,
    currentPlaybackTitle: String? = null,
    currentPlaybackArtist: String? = null,
    currentSubtuneIndex: Int,
    favoritesSortMode: PlaylistEntrySortMode,
    networkNodes: List<NetworkNode> = emptyList(),
    libraryAlbumDetail: LibraryAlbumDetail?,
    libraryArtistAlbums: List<LibraryAlbum>?,
    selectedArtistName: String?,
    onExitPlaylists: () -> Unit,
    onOpenLibraryAlbum: (String, String) -> Unit,
    onOpenLibraryArtist: (String) -> Unit,
    onPlayLibraryTracks: (List<LibraryTrackEntity>, Int, String) -> Unit,
    onShuffleLibraryTracks: (List<LibraryTrackEntity>, String) -> Unit,
    onAddLibraryTracksToFavorites: (List<LibraryTrackEntity>) -> Unit,
    onRemoveLibraryTracksFromFavorites: (List<LibraryTrackEntity>) -> Unit,
    onAddLibraryTracksToPlaylist: (List<LibraryTrackEntity>, String?, String) -> Unit,
    onPinLibraryEntries: (List<HomePinnedEntry>) -> Unit,
    onUnpinLibraryPaths: (List<String>) -> Unit,
    onFavoritesSortModeChange: (PlaylistEntrySortMode) -> Unit,
    onOpenLibrarySettings: () -> Unit,
    onOpenFavorite: (PlaylistTrackEntry) -> Unit,
    onPlayStoredPlaylist: (StoredPlaylist) -> Unit,
    onShuffleStoredPlaylist: (StoredPlaylist) -> Unit,
    onOpenStoredPlaylistEntry: (PlaylistTrackEntry, StoredPlaylist) -> Unit,
    onPlayFavoritePlaylist: () -> Unit,
    onShuffleFavoritePlaylist: () -> Unit,
    onDeleteAllFavorites: () -> Unit,
    onDeleteFavoriteTrack: (PlaylistTrackEntry) -> Unit,
    onMoveFavoriteTrack: (PlaylistTrackEntry, Int) -> Unit,
    onPlayFavoriteTrackAsCached: (PlaylistTrackEntry) -> Unit,
    onCreatePlaylist: (String) -> String,
    onDeleteStoredPlaylistEntry: (PlaylistTrackEntry, String) -> Unit,
    onMoveStoredPlaylistEntry: (PlaylistTrackEntry, String, Int) -> Unit,
    onDeleteAllStoredPlaylistEntries: (String) -> Unit,
    onPlayStoredPlaylistTrackAsCached: (PlaylistTrackEntry, StoredPlaylist) -> Unit,
    onRemoveSourceFromPlaylist: (String, String) -> Unit,
    onOpenFavoriteTrackLocation: (PlaylistTrackEntry) -> Unit,
    onShareFavoriteTrack: (PlaylistTrackEntry) -> Unit,
    onCopyFavoriteTrackSource: (PlaylistTrackEntry) -> Unit,
    onOpenFavoriteTrackInfo: (PlaylistTrackEntry) -> Unit,
    onDeleteStoredPlaylist: (String) -> Unit = {},
    onRenameStoredPlaylist: (String, String) -> Unit = { _, _ -> },
    onOpenBrowser: () -> Unit = {},
    onAppendStoredPlaylistEntries: (String, List<PlaylistTrackEntry>) -> Unit = { _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var libraryCollections by remember { mutableStateOf(LibraryCollections.Empty) }
    LaunchedEffect(Unit) {
        libraryCollections = LibraryRepository.collections(context)
        LibraryRepository.maybeStartAutoScan(context)
    }
    Box(modifier = Modifier.fillMaxSize().padding(mainPadding)) {
        PlaylistsScreen(
            libraryState = libraryState,
            libraryCollections = libraryCollections,
            libraryAlbumDetail = libraryAlbumDetail,
            libraryArtistAlbums = libraryArtistAlbums,
            selectedArtistName = selectedArtistName,
            activePlaylist = activePlaylist,
            activePlaylistEntryId = activePlaylistEntryId,
            currentPlaybackSourceId = currentPlaybackSourceId,
            currentPlaybackTitle = currentPlaybackTitle,
            currentPlaybackArtist = currentPlaybackArtist,
            currentSubtuneIndex = currentSubtuneIndex,
            bottomContentPadding = bottomContentPadding,
            favoritesSortMode = favoritesSortMode,
            networkNodes = networkNodes,
            onOpenLibraryAlbum = onOpenLibraryAlbum,
            onOpenLibraryArtist = onOpenLibraryArtist,
            onPlayLibraryTracks = onPlayLibraryTracks,
        onShuffleLibraryTracks = onShuffleLibraryTracks,
        onAddLibraryTracksToFavorites = onAddLibraryTracksToFavorites,
        onRemoveLibraryTracksFromFavorites = onRemoveLibraryTracksFromFavorites,
        onAddLibraryTracksToPlaylist = onAddLibraryTracksToPlaylist,
        onPinLibraryEntries = onPinLibraryEntries,
        onUnpinLibraryPaths = onUnpinLibraryPaths,
        pinnedHomeEntries = pinnedHomeEntries,
        surfaceState = surfaceState,
            backHandlingEnabled = backHandlingEnabled,
            onBack = onExitPlaylists,
            onFavoritesSortModeChange = onFavoritesSortModeChange,
            onOpenLibrarySettings = onOpenLibrarySettings,
            onOpenFavorite = onOpenFavorite,
            onPlayStoredPlaylist = onPlayStoredPlaylist,
            onShuffleStoredPlaylist = onShuffleStoredPlaylist,
            onOpenStoredPlaylistEntry = onOpenStoredPlaylistEntry,
            onPlayFavoritePlaylist = onPlayFavoritePlaylist,
            onShuffleFavoritePlaylist = onShuffleFavoritePlaylist,
            onDeleteAllFavorites = onDeleteAllFavorites,
            onDeleteFavoriteTrack = onDeleteFavoriteTrack,
            onMoveFavoriteTrack = onMoveFavoriteTrack,
            onPlayFavoriteTrackAsCached = onPlayFavoriteTrackAsCached,
            onCreatePlaylist = onCreatePlaylist,
            onDeleteStoredPlaylistEntry = onDeleteStoredPlaylistEntry,
            onMoveStoredPlaylistEntry = onMoveStoredPlaylistEntry,
            onDeleteAllStoredPlaylistEntries = onDeleteAllStoredPlaylistEntries,
            onPlayStoredPlaylistTrackAsCached = onPlayStoredPlaylistTrackAsCached,
            onRemoveSourceFromPlaylist = onRemoveSourceFromPlaylist,
            onOpenFavoriteTrackLocation = onOpenFavoriteTrackLocation,
            onShareFavoriteTrack = onShareFavoriteTrack,
            onCopyFavoriteTrackSource = onCopyFavoriteTrackSource,
            onOpenFavoriteTrackInfo = onOpenFavoriteTrackInfo,
            onDeleteStoredPlaylist = onDeleteStoredPlaylist,
            onRenameStoredPlaylist = onRenameStoredPlaylist,
            onOpenBrowser = onOpenBrowser,
            onAppendStoredPlaylistEntries = onAppendStoredPlaylistEntries
        )
    }
}

@Composable
internal fun MainBrowserRouteHost(
    mainPadding: PaddingValues,
    repository: com.flopster101.siliconplayer.data.FileRepository,
    decoderExtensionArtworkHints: Map<String, DecoderArtworkHint>,
    initialLocationId: String?,
    initialDirectoryPath: String?,
    initialSmbSourceNodeId: Long?,
    initialSmbAllowHostShareNavigation: Boolean,
    initialHttpSourceNodeId: Long?,
    initialHttpRootPath: String?,
    restoreFocusedItemRequestToken: Int,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    backHandlingEnabled: Boolean,
    playingFile: File?,
    playingPlaylistFile: File?,
    favoriteSourcePaths: List<String>,
    networkNodes: List<NetworkNode> = emptyList(),
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onExitBrowser: () -> Unit,
    onBrowserLocationChanged: (BrowserLaunchState) -> Unit,
    onFileSelected: (File, String?) -> Unit,
    onPlaylistFileSelected: (File, String?) -> Unit,
    onToggleFavoriteFile: (File) -> Unit,
    playlists: List<StoredPlaylist>,
    favoriteSourceIds: Set<String>,
    onToggleFavoriteSource: (String, String) -> Unit,
    onAddSourceToPlaylist: (String, String, String?, String) -> Unit,
    onRemoveSourceFromPlaylist: (String, String) -> Unit,
    onOpenRemoteSource: (String) -> Unit,
    onOpenRemoteSourceAsCached: (String) -> Unit,
    onRememberSmbCredentials: (Long?, String, String?, String?) -> Unit,
    onRememberHttpCredentials: (Long?, String, String?, String?) -> Unit,
    pinnedHomeEntries: List<HomePinnedEntry>,
    onPinHomeEntry: (RecentPathEntry, Boolean) -> Unit
) {
    val routeResolution = remember(
        initialLocationId,
        initialDirectoryPath,
        initialSmbSourceNodeId,
        initialHttpSourceNodeId,
        initialHttpRootPath
    ) {
        resolveBrowserRouteResolution(
            initialLocationId = initialLocationId,
            initialDirectoryPath = initialDirectoryPath,
            initialSmbSourceNodeId = initialSmbSourceNodeId,
            initialHttpSourceNodeId = initialHttpSourceNodeId,
            initialHttpRootPath = initialHttpRootPath
        )
    }
    val renderState = rememberBrowserRouteRenderState(routeResolution)

    Box(modifier = Modifier.padding(mainPadding)) {
        if (renderState.renderMode == BrowserRouteMode.Smb && renderState.renderSmbSpec != null) {
            val smbSpec = requireNotNull(renderState.renderSmbSpec)
            LaunchedEffect(renderState.renderSmbSessionKey) {
                onVisiblePlayableFilesChanged(emptyList())
            }
            SmbFileBrowserScreen(
                sourceSpec = smbSpec,
                bottomContentPadding = bottomContentPadding,
                backHandlingEnabled = backHandlingEnabled,
                allowHostShareNavigation = initialSmbAllowHostShareNavigation,
                onExitBrowser = onExitBrowser,
                onOpenRemoteSource = onOpenRemoteSource,
                onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
                playlists = playlists,
                favoriteSourceIds = favoriteSourceIds,
                onToggleFavoriteSource = onToggleFavoriteSource,
                onAddSourceToPlaylist = onAddSourceToPlaylist,
                onRemoveSourceFromPlaylist = onRemoveSourceFromPlaylist,
                onRememberSmbCredentials = onRememberSmbCredentials,
                sourceNodeId = routeResolution.requestedSmbSourceNodeId,
                onBrowserLocationChanged = onBrowserLocationChanged,
                onPlaylistFileSelected = onPlaylistFileSelected,
                pinnedHomeEntries = pinnedHomeEntries,
                onPinHomeEntry = onPinHomeEntry,
                networkNodes = networkNodes
            )
        } else if (renderState.renderMode == BrowserRouteMode.Http && renderState.renderHttpSpec != null) {
            val httpSpec = requireNotNull(renderState.renderHttpSpec)
            LaunchedEffect(renderState.renderHttpSessionKey) {
                onVisiblePlayableFilesChanged(emptyList())
            }
            HttpFileBrowserScreen(
                sourceSpec = httpSpec,
                browserRootPath = routeResolution.requestedHttpRootPath,
                bottomContentPadding = bottomContentPadding,
                backHandlingEnabled = backHandlingEnabled,
                onExitBrowser = onExitBrowser,
                onOpenRemoteSource = onOpenRemoteSource,
                onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
                onRememberHttpCredentials = onRememberHttpCredentials,
                sourceNodeId = routeResolution.requestedHttpSourceNodeId,
                onBrowserLocationChanged = onBrowserLocationChanged,
                onPlaylistFileSelected = onPlaylistFileSelected,
                pinnedHomeEntries = pinnedHomeEntries,
                onPinHomeEntry = onPinHomeEntry
            )
        } else {
            LaunchedEffect(routeResolution.requestedLocalLocationId, routeResolution.requestedLocalDirectoryPath) {
                RemotePlayableSourceIdsHolder.current = emptyList()
            }
            FileBrowserScreen(
                repository = repository,
                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
                initialLocationId = routeResolution.requestedLocalLocationId,
                initialDirectoryPath = routeResolution.requestedLocalDirectoryPath,
                initialSmbSourceNodeId = routeResolution.requestedSmbSourceNodeId,
                initialHttpSourceNodeId = routeResolution.requestedHttpSourceNodeId,
                initialHttpRootPath = routeResolution.requestedHttpRootPath,
                restoreFocusedItemRequestToken = restoreFocusedItemRequestToken,
                onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
                bottomContentPadding = bottomContentPadding,
                showParentDirectoryEntry = showParentDirectoryEntry,
                showFileIconChipBackground = showFileIconChipBackground,
                backHandlingEnabled = backHandlingEnabled,
                onExitBrowser = onExitBrowser,
                onOpenSettings = null,
                showPrimaryTopBar = false,
                playingFile = playingFile,
                playingPlaylistFile = playingPlaylistFile,
                favoriteSourcePaths = favoriteSourcePaths,
                onBrowserLocationChanged = onBrowserLocationChanged,
                onFileSelected = onFileSelected,
                onPlaylistFileSelected = onPlaylistFileSelected,
                onToggleFavoriteFile = onToggleFavoriteFile,
                playlists = playlists,
                onAddSourceToPlaylist = onAddSourceToPlaylist,
                onRemoveSourceFromPlaylist = onRemoveSourceFromPlaylist,
                pinnedHomeEntries = pinnedHomeEntries,
                onPinHomeEntry = onPinHomeEntry
            )
        }
    }
}
