package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import com.flopster101.siliconplayer.library.LibraryAlbum
import com.flopster101.siliconplayer.library.LibraryAlbumDetail
import com.flopster101.siliconplayer.library.LibraryTrackEntity
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.flopster101.siliconplayer.ui.screens.LibrarySurfaceState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.ExperimentalComposeUiApi
import java.io.File

@Composable
internal fun AppNavigationHomeRouteSection(
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
    playlists: List<StoredPlaylist> = emptyList(),
    favoriteSourcePaths: List<String> = emptyList(),
    onToggleFavoriteSource: ((String, String) -> Unit)? = null,
    onAddSourceToPlaylist: ((String, String, String?, String) -> Unit)? = null,
    onRemoveSourceFromPlaylist: ((String, String) -> Unit)? = null,
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
    MainHomeRouteHost(
        mainPadding = mainPadding,
        currentTrackPath = currentTrackPath,
        currentTrackTitle = currentTrackTitle,
        currentTrackArtist = currentTrackArtist,
        pinnedHomeEntries = pinnedHomeEntries,
        recentFolders = recentFolders,
        recentPlayedFiles = recentPlayedFiles,
        storagePresentationForEntry = storagePresentationForEntry,
        storagePresentationForPinnedEntry = storagePresentationForPinnedEntry,
        bottomContentPadding = bottomContentPadding,
        playlists = playlists,
        favoriteSourcePaths = favoriteSourcePaths,
        onToggleFavoriteSource = onToggleFavoriteSource,
        onAddSourceToPlaylist = onAddSourceToPlaylist,
        onRemoveSourceFromPlaylist = onRemoveSourceFromPlaylist,
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

@Composable
internal fun AppNavigationPlaylistsRouteSection(
    mainPadding: PaddingValues,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    backHandlingEnabled: Boolean,
    libraryState: PlaylistLibraryState,
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
    pinnedHomeEntries: List<HomePinnedEntry>,
    surfaceState: LibrarySurfaceState,
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
    onAppendStoredPlaylistEntries: (String, List<PlaylistTrackEntry>) -> Unit = { _, _ -> },
    onTogglePinStoredPlaylist: (String) -> Unit = {},
    onDeleteFavoriteTracks: (Set<String>) -> Unit = {},
    onDeleteStoredPlaylistEntries: (String, Set<String>) -> Unit = { _, _ -> },
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit = {}
) {
    MainPlaylistsRouteHost(
        mainPadding = mainPadding,
        bottomContentPadding = bottomContentPadding,
        backHandlingEnabled = backHandlingEnabled,
        libraryState = libraryState,
        activePlaylist = activePlaylist,
        activePlaylistEntryId = activePlaylistEntryId,
        currentPlaybackSourceId = currentPlaybackSourceId,
        currentPlaybackTitle = currentPlaybackTitle,
        currentPlaybackArtist = currentPlaybackArtist,
        currentSubtuneIndex = currentSubtuneIndex,
        favoritesSortMode = favoritesSortMode,
        networkNodes = networkNodes,
        libraryAlbumDetail = libraryAlbumDetail,
        libraryArtistAlbums = libraryArtistAlbums,
        selectedArtistName = selectedArtistName,
        onExitPlaylists = onExitPlaylists,
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
        onAppendStoredPlaylistEntries = onAppendStoredPlaylistEntries,
        onTogglePinStoredPlaylist = onTogglePinStoredPlaylist,
        onDeleteFavoriteTracks = onDeleteFavoriteTracks,
        onDeleteStoredPlaylistEntries = onDeleteStoredPlaylistEntries,
        onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
    )
}

@Composable
internal fun AppNavigationNetworkRouteSection(
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
    MainNetworkRouteHost(
        mainPadding = mainPadding,
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

@Composable
internal fun AppNavigationBrowserRouteSection(
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
    MainBrowserRouteHost(
        mainPadding = mainPadding,
        repository = repository,
        decoderExtensionArtworkHints = decoderExtensionArtworkHints,
        initialLocationId = initialLocationId,
        initialDirectoryPath = initialDirectoryPath,
        initialSmbSourceNodeId = initialSmbSourceNodeId,
        initialSmbAllowHostShareNavigation = initialSmbAllowHostShareNavigation,
        initialHttpSourceNodeId = initialHttpSourceNodeId,
        initialHttpRootPath = initialHttpRootPath,
        restoreFocusedItemRequestToken = restoreFocusedItemRequestToken,
        bottomContentPadding = bottomContentPadding,
        showParentDirectoryEntry = showParentDirectoryEntry,
        showFileIconChipBackground = showFileIconChipBackground,
        backHandlingEnabled = backHandlingEnabled,
        playingFile = playingFile,
        playingPlaylistFile = playingPlaylistFile,
        favoriteSourcePaths = favoriteSourcePaths,
        networkNodes = networkNodes,
        onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
        onExitBrowser = onExitBrowser,
        onBrowserLocationChanged = onBrowserLocationChanged,
        onFileSelected = onFileSelected,
        onPlaylistFileSelected = onPlaylistFileSelected,
        onToggleFavoriteFile = onToggleFavoriteFile,
        playlists = playlists,
        favoriteSourceIds = favoriteSourceIds,
        onToggleFavoriteSource = onToggleFavoriteSource,
        onAddSourceToPlaylist = onAddSourceToPlaylist,
        onRemoveSourceFromPlaylist = onRemoveSourceFromPlaylist,
        onOpenRemoteSource = onOpenRemoteSource,
        onOpenRemoteSourceAsCached = onOpenRemoteSourceAsCached,
        onRememberSmbCredentials = onRememberSmbCredentials,
        onRememberHttpCredentials = onRememberHttpCredentials,
        pinnedHomeEntries = pinnedHomeEntries,
        onPinHomeEntry = onPinHomeEntry
    )
}

@Composable
internal fun AppNavigationSettingsRouteSection(
    mainPadding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.padding(mainPadding)) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppNavigationMainScaffoldSection(
    currentView: MainView,
    mainContentFocusRequester: FocusRequester,
    canFocusMiniPlayer: Boolean,
    requestMiniPlayerFocus: () -> Unit,
    onHardwareNavigationInput: () -> Unit,
    onTouchInteraction: () -> Unit,
    onOpenPlayerSurface: () -> Unit,
    onHomeRequested: () -> Unit,
    onOpenUrlOrPathRequested: () -> Unit,
    onSettingsRequested: () -> Unit,
    homeContent: @Composable (PaddingValues) -> Unit,
    playlistsContent: @Composable (PaddingValues) -> Unit,
    networkContent: @Composable (PaddingValues) -> Unit,
    browserContent: @Composable (PaddingValues) -> Unit,
    settingsContent: @Composable (PaddingValues) -> Unit
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            onTouchInteraction()
                            focusManager.clearFocus(force = true)
                        }
                    }
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                if (
                    keyEvent.key == Key.DirectionLeft ||
                    keyEvent.key == Key.DirectionRight ||
                    keyEvent.key == Key.DirectionUp ||
                    keyEvent.key == Key.DirectionDown ||
                    keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                ) {
                    onHardwareNavigationInput()
                }
                if (!canFocusMiniPlayer) {
                    return@onPreviewKeyEvent false
                }
                val moveDirection = when (keyEvent.key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                }
                if (moveDirection == null) {
                    return@onPreviewKeyEvent false
                }
                val movedWithinMainContent = focusManager.moveFocus(moveDirection)
                if (movedWithinMainContent) {
                    true
                } else {
                    mainContentFocusRequester.saveFocusedChild()
                    requestMiniPlayerFocus()
                    true
                }
            }
    ) {
        MainNavigationScaffold(
            currentView = currentView,
            onOpenPlayerSurface = onOpenPlayerSurface,
            onHomeRequested = onHomeRequested,
            onOpenUrlOrPathRequested = onOpenUrlOrPathRequested,
            onSettingsRequested = onSettingsRequested,
            mainContentModifier = Modifier
                .focusRequester(mainContentFocusRequester)
                .focusRestorer()
            ) { mainPadding, targetView ->
            when (targetView) {
                MainView.Home -> homeContent(mainPadding)
                MainView.Playlists -> playlistsContent(mainPadding)
                MainView.Network -> networkContent(mainPadding)
                MainView.Browser -> browserContent(mainPadding)
                MainView.Settings -> settingsContent(mainPadding)
            }
        }
    }
}
