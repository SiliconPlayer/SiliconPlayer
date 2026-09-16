package com.flopster101.siliconplayer.ui.screens

import com.flopster101.siliconplayer.PlaylistEntrySortMode
import com.flopster101.siliconplayer.formatSourceIdForDisplay
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.resolveSmbDisplayHost
import com.flopster101.siliconplayer.sortPlaylistEntries
import com.flopster101.siliconplayer.isRoundScreenCompat
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.flopster101.siliconplayer.ui.dialogs.DialogSectionLabel
import com.flopster101.siliconplayer.ui.dialogs.DialogSelectableCard
import com.flopster101.siliconplayer.ui.dialogs.FloatingActionDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import android.content.pm.PackageManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.normalizeSourceIdentity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import com.flopster101.siliconplayer.playlistEntryMatchesPlayback
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.recentArtworkThumbnailFile
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.sourceLeafNameForDisplay
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.library.LibraryAlbum
import com.flopster101.siliconplayer.library.LibraryAlbumDetail
import com.flopster101.siliconplayer.library.LibraryArtist
import com.flopster101.siliconplayer.library.LibraryCollections
import com.flopster101.siliconplayer.library.LibraryContract
import com.flopster101.siliconplayer.library.LibraryRepository
import com.flopster101.siliconplayer.library.LibrarySearchResults
import com.flopster101.siliconplayer.library.LibrarySyncState
import com.flopster101.siliconplayer.library.LibraryTrackEntity
import com.flopster101.siliconplayer.loadArtworkForFile
import androidx.compose.ui.graphics.ImageBitmap
import com.flopster101.siliconplayer.NativeBridge
import java.io.File
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class PlaylistsSurfaceDestination {
    Library,
    Favorites,
    StoredPlaylist,
    AlbumDetail,
    ArtistDetail
}

private enum class LibrarySurfaceTab(val label: String) {
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Tracks("Tracks")
}

internal enum class ArtistContentMode(val label: String) {
    Albums("Albums"),
    Tracks("Tracks")
}

internal class LibrarySurfaceState {
    val destinationState = mutableStateOf(PlaylistsSurfaceDestination.Library)
    val selectedStoredPlaylistIdState = mutableStateOf<String?>(null)
    val selectedTabIndexState = mutableIntStateOf(0)
    val albumCollectionLayoutState = mutableStateOf(AlbumCollectionLayout.Grid)
    val searchActiveState = mutableStateOf(false)
    val searchQueryState = mutableStateOf("")
    val searchResultsState = mutableStateOf(LibrarySearchResults("", emptyList(), emptyList(), emptyList()))
    val albumOpenedFromArtistState = mutableStateOf(false)
    val artistOpenedFromAlbumState = mutableStateOf(false)
    val playlistsTabListState = LazyListState()
    val albumsGridState = LazyGridState()
    val albumsListState = LazyListState()
    val artistsListState = LazyListState()
    val tracksListState = LazyListState()
    val albumDetailListState = LazyListState()
    val artistDetailListState = LazyListState()
    val artistDetailTracksListState = LazyListState()
    val artistContentModeState = mutableStateOf(ArtistContentMode.Albums)
    val artistTracksState = mutableStateOf<List<LibraryTrackEntity>>(emptyList())
}

internal enum class AlbumCollectionLayout {
    Grid,
    List
}

private const val PLAYLISTS_PAGE_NAV_DURATION_MS = 280
private val PLAYLISTS_DETAIL_CONTENT_GUTTER = 8.dp
private val LocalPlaylistsTitleMarqueeClockState = compositionLocalOf<State<Long>> { mutableLongStateOf(0L) }

@Composable
private fun rememberPlaylistsTitleMarqueeClockState(resetKey: Any?): State<Long> {
    val clockState = remember { mutableLongStateOf(0L) }
    LaunchedEffect(resetKey) {
        val startTimeMs = withFrameMillis { it }
        clockState.longValue = 0L
        while (true) {
            clockState.longValue = withFrameMillis { it - startTimeMs }
        }
    }
    return clockState
}

private fun playlistsTitleMarqueeMotionFadeAlpha(
    elapsedMs: Int,
    segmentDurationMs: Int,
    fadeInMs: Int,
    fadeOutMs: Int
): Float {
    if (segmentDurationMs <= 0) return 0f
    val fadeInProgress = (elapsedMs.toFloat() / fadeInMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val fadeOutProgress = (
        (segmentDurationMs - elapsedMs).toFloat() / fadeOutMs.coerceAtLeast(1)
        ).coerceIn(0f, 1f)
    return minOf(fadeInProgress, fadeOutProgress)
}

@Composable
private fun PlaylistsTopBarMarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val measuredText = remember(text, style) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        val marqueeTrailingGap = 18.dp
        val marqueeEdgeFade = 14.dp
        val marqueeTrailingGapPx = with(density) { marqueeTrailingGap.roundToPx() }
        val marqueeEdgeFadePx = with(density) { marqueeEdgeFade.toPx() }
        val overflowPx = (measuredText.size.width - maxWidthPx).coerceAtLeast(0)
        val sharedTimeMs = LocalPlaylistsTitleMarqueeClockState.current.value
        val marqueeInstanceStartMs = remember(text, style) {
            mutableLongStateOf(Long.MIN_VALUE)
        }
        SideEffect {
            if (marqueeInstanceStartMs.longValue == Long.MIN_VALUE) {
                marqueeInstanceStartMs.longValue = sharedTimeMs
            }
        }
        val instanceElapsedMs = if (marqueeInstanceStartMs.longValue == Long.MIN_VALUE) {
            0L
        } else {
            (sharedTimeMs - marqueeInstanceStartMs.longValue).coerceAtLeast(0L)
        }
        val startPauseMs = 1450
        val turnaroundPauseMs = 1050
        val resetPauseMs = 1850
        val fadeInMs = 180
        val fadeOutMs = 260
        val travelDistancePx = (overflowPx + marqueeTrailingGapPx).coerceAtLeast(0)
        val marqueeSpeedPxPerSecond = with(density) { 56.dp.toPx() }.coerceAtLeast(1f)
        val travelDurationMs = if (travelDistancePx > 0) {
            ((travelDistancePx / marqueeSpeedPxPerSecond) * 1000f).toInt().coerceAtLeast(1)
        } else {
            0
        }
        val targetOffset = if (overflowPx > 0) -travelDistancePx.toFloat() else 0f
        val cycleDurationMs = startPauseMs + travelDurationMs + turnaroundPauseMs + travelDurationMs + resetPauseMs
        val cyclePositionMs = if (overflowPx > 0 && cycleDurationMs > 0) {
            (instanceElapsedMs % cycleDurationMs.toLong()).toInt()
        } else {
            0
        }
        val marqueeOffsetPx = when {
            overflowPx <= 0 -> 0f
            cyclePositionMs < startPauseMs -> 0f
            cyclePositionMs < startPauseMs + travelDurationMs -> {
                val progress = ((cyclePositionMs - startPauseMs).toFloat() / travelDurationMs).coerceIn(0f, 1f)
                targetOffset * progress
            }
            cyclePositionMs < startPauseMs + travelDurationMs + turnaroundPauseMs -> targetOffset
            cyclePositionMs < startPauseMs + travelDurationMs + turnaroundPauseMs + travelDurationMs -> {
                val elapsed = cyclePositionMs - startPauseMs - travelDurationMs - turnaroundPauseMs
                val progress = (elapsed.toFloat() / travelDurationMs).coerceIn(0f, 1f)
                targetOffset * (1f - progress)
            }
            else -> 0f
        }
        val marqueeFadeAlpha = when {
            overflowPx <= 0 -> 0f
            cyclePositionMs < startPauseMs -> 0f
            cyclePositionMs < startPauseMs + travelDurationMs -> {
                playlistsTitleMarqueeMotionFadeAlpha(
                    elapsedMs = cyclePositionMs - startPauseMs,
                    segmentDurationMs = travelDurationMs,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs
                )
            }
            cyclePositionMs < startPauseMs + travelDurationMs + turnaroundPauseMs -> 0f
            cyclePositionMs < startPauseMs + travelDurationMs + turnaroundPauseMs + travelDurationMs -> {
                playlistsTitleMarqueeMotionFadeAlpha(
                    elapsedMs = cyclePositionMs - startPauseMs - travelDurationMs - turnaroundPauseMs,
                    segmentDurationMs = travelDurationMs,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs
                )
            }
            else -> 0f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (overflowPx > 0 && marqueeFadeAlpha > 0f) {
                        Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val fadeWidthPx = marqueeEdgeFadePx.coerceAtMost(size.width / 2f)
                                if (fadeWidthPx > 0f) {
                                    val opaqueMaskAlpha = 1f - marqueeFadeAlpha
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = opaqueMaskAlpha),
                                                Color.Black
                                            ),
                                            startX = 0f,
                                            endX = fadeWidthPx
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(fadeWidthPx, size.height),
                                        blendMode = BlendMode.DstIn
                                    )
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black,
                                                Color.Black.copy(alpha = opaqueMaskAlpha)
                                            ),
                                            startX = size.width - fadeWidthPx,
                                            endX = size.width
                                        ),
                                        topLeft = Offset(size.width - fadeWidthPx, 0f),
                                        size = Size(fadeWidthPx, size.height),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (overflowPx > 0) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = marqueeOffsetPx }
                ) {
                    Text(
                        text = text,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.width(marqueeTrailingGap))
                }
            } else {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistsScreen(
    libraryState: PlaylistLibraryState,
    libraryCollections: LibraryCollections,
    libraryAlbumDetail: LibraryAlbumDetail?,
    libraryArtistAlbums: List<LibraryAlbum>?,
    selectedArtistName: String?,
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
    onOpenLibrarySettings: () -> Unit,
    activePlaylist: StoredPlaylist?,
    currentPlaybackSourceId: String?,
    currentSubtuneIndex: Int,
    bottomContentPadding: Dp,
    favoritesSortMode: PlaylistEntrySortMode,
    networkNodes: List<NetworkNode> = emptyList(),
    backHandlingEnabled: Boolean = true,
    onBack: () -> Unit,
    onFavoritesSortModeChange: (PlaylistEntrySortMode) -> Unit,
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
    onOpenFavoriteTrackLocation: (PlaylistTrackEntry) -> Unit,
    onShareFavoriteTrack: (PlaylistTrackEntry) -> Unit,
    onCopyFavoriteTrackSource: (PlaylistTrackEntry) -> Unit,
    onOpenFavoriteTrackInfo: (PlaylistTrackEntry) -> Unit
) {
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val configuration = LocalConfiguration.current
    val isRound = configuration.isRoundScreenCompat || configuration.screenWidthDp == configuration.screenHeightDp

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var destination by surfaceState.destinationState
    var selectedStoredPlaylistId by surfaceState.selectedStoredPlaylistIdState
    var selectedTabIndex by surfaceState.selectedTabIndexState
    var albumCollectionLayout by surfaceState.albumCollectionLayoutState
    val librarySyncState by LibraryRepository.scanState.collectAsState()
    var librarySearchActive by surfaceState.searchActiveState
    var librarySearchQuery by surfaceState.searchQueryState
    var librarySearchResults by surfaceState.searchResultsState
    var artistContentMode by surfaceState.artistContentModeState
    LaunchedEffect(selectedArtistName, destination) {
        val artistName = selectedArtistName
        if (destination == PlaylistsSurfaceDestination.ArtistDetail && artistName != null) {
            surfaceState.artistTracksState.value = LibraryRepository.artistTracks(context, artistName)
        }
    }
    var libraryContextTracks by remember { mutableStateOf<List<LibraryTrackEntity>?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val libraryFavoriteKeySet = remember(libraryState.favorites) {
        libraryState.favorites.asSequence()
            .mapNotNull { normalizeSourceIdentity(it.source) }
            .toSet()
    }
    val libraryPinnedFolderKeySet = remember(pinnedHomeEntries) {
        pinnedHomeEntries.asSequence()
            .filter { it.isFolder }
            .mapNotNull { normalizeSourceIdentity(it.path) }
            .toSet()
    }
    val libraryPinnedFileKeySet = remember(pinnedHomeEntries) {
        pinnedHomeEntries.asSequence()
            .filterNot { it.isFolder }
            .mapNotNull { normalizeSourceIdentity(it.path) }
            .toSet()
    }
    fun libraryPinnedEntriesFor(tracks: List<LibraryTrackEntity>): List<HomePinnedEntry> =
        tracks.map { track ->
            HomePinnedEntry(
                path = track.path,
                isFolder = false,
                title = track.title,
                artist = track.artist.takeUnless {
                    it.isBlank() || it.equals("Unknown artist", ignoreCase = true)
                }
            )
        }
    fun libraryFolderPinsFor(tracks: List<LibraryTrackEntity>, titleOverride: String?): List<HomePinnedEntry> =
        tracks.map { it.path.substringBeforeLast('/') }.distinct().map { folder ->
            HomePinnedEntry(
                path = folder,
                isFolder = true,
                title = titleOverride ?: folder.substringAfterLast('/')
            )
        }
    fun libraryRowActions(tracks: List<LibraryTrackEntity>): LibraryRowContextActions {
        val keys = tracks.mapNotNull { normalizeSourceIdentity(it.path) }
        val isFavorite = keys.isNotEmpty() && keys.all { it in libraryFavoriteKeySet }
        val pins = if (tracks.size == 1) {
            libraryPinnedEntriesFor(tracks)
        } else {
            libraryFolderPinsFor(tracks, null)
        }
        val isPinned = pins.isNotEmpty() && pins.all { pin ->
            val key = normalizeSourceIdentity(pin.path)
            key != null && if (pin.isFolder) {
                key in libraryPinnedFolderKeySet
            } else {
                key in libraryPinnedFileKeySet
            }
        }
        return LibraryRowContextActions(
            isFavorite = isFavorite,
            isPinned = isPinned,
            onToggleFavorite = {
                if (isFavorite) {
                    onRemoveLibraryTracksFromFavorites(tracks)
                } else {
                    onAddLibraryTracksToFavorites(tracks)
                }
            },
            onTogglePin = {
                if (isPinned) {
                    onUnpinLibraryPaths(pins.map { it.path })
                } else {
                    onPinLibraryEntries(pins)
                }
            },
            onAddToPlaylist = { libraryContextTracks = tracks }
        )
    }
    fun libraryAlbumContextMenu(album: LibraryAlbum): LibraryCollectionContextMenu {
        return LibraryCollectionContextMenu(
            noun = "album",
            onAddToFavorites = {
                coroutineScope.launch {
                    onAddLibraryTracksToFavorites(LibraryRepository.albumTracks(context, album.rawName))
                }
            },
            onPin = {
                coroutineScope.launch {
                    val tracks = LibraryRepository.albumTracks(context, album.rawName)
                    val folders = libraryFolderPinsFor(tracks, album.rawName)
                    if (folders.size == 1) {
                        onPinLibraryEntries(folders)
                    } else {
                        onPinLibraryEntries(libraryPinnedEntriesFor(tracks))
                    }
                }
            },
            onAddToPlaylist = {
                coroutineScope.launch { libraryContextTracks = LibraryRepository.albumTracks(context, album.rawName) }
            }
        )
    }
    fun libraryArtistContextMenu(artist: LibraryArtist): LibraryCollectionContextMenu {
        return LibraryCollectionContextMenu(
            noun = "artist",
            onAddToFavorites = {
                coroutineScope.launch {
                    onAddLibraryTracksToFavorites(LibraryRepository.artistTracks(context, artist.name))
                }
            },
            onPin = {
                coroutineScope.launch {
                    val tracks = LibraryRepository.artistTracks(context, artist.name)
                    val folders = libraryFolderPinsFor(tracks, artist.name)
                    if (folders.size == 1) {
                        onPinLibraryEntries(folders)
                    } else {
                        onPinLibraryEntries(libraryPinnedEntriesFor(tracks))
                    }
                }
            },
            onAddToPlaylist = {
                coroutineScope.launch { libraryContextTracks = LibraryRepository.artistTracks(context, artist.name) }
            }
        )
    }
    LaunchedEffect(librarySearchQuery, librarySearchActive) {
        if (!librarySearchActive || librarySearchQuery.isBlank()) {
            librarySearchResults = LibrarySearchResults("", emptyList(), emptyList(), emptyList())
            return@LaunchedEffect
        }
        delay(220)
        librarySearchResults = LibraryRepository.search(context, librarySearchQuery)
    }
    var libraryCollectionsOverride by remember { mutableStateOf<LibraryCollections?>(null) }
    val effectiveLibraryCollections = libraryCollectionsOverride ?: libraryCollections
    // Refresh the visible collections whenever a scan completes, regardless
    // of where it was started from.
    LaunchedEffect(Unit) {
        LibraryRepository.scanState.collect { state ->
            if (!state.isScanning && state.lastSyncedAtMs > 0L) {
                libraryCollectionsOverride = LibraryRepository.collections(context)
            }
        }
    }
    val libraryTabs = rememberLibraryTabs()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { libraryTabs.size }
    )
    val showingFavoritesDetail = destination == PlaylistsSurfaceDestination.Favorites
    val showingAlbumDetail = destination == PlaylistsSurfaceDestination.AlbumDetail && libraryAlbumDetail != null
    val showingArtistDetail = destination == PlaylistsSurfaceDestination.ArtistDetail && selectedArtistName != null && libraryArtistAlbums != null
    val selectedStoredPlaylist = selectedStoredPlaylistId?.let { playlistId ->
        libraryState.playlists.firstOrNull { playlist -> playlist.id == playlistId }
    }
    val showingStoredPlaylistDetail =
        destination == PlaylistsSurfaceDestination.StoredPlaylist && selectedStoredPlaylist != null
    val showingPlaylistDetail = showingFavoritesDetail || showingStoredPlaylistDetail ||
            showingAlbumDetail || showingArtistDetail
    var storedPlaylistSortMode by rememberSaveable(selectedStoredPlaylistId) {
        mutableStateOf(PlaylistEntrySortMode.Custom)
    }
    var favoritesEditModeEnabled by rememberSaveable { mutableStateOf(false) }
    var favoritesDraggingEntryId by remember { mutableStateOf<String?>(null) }
    var showDeleteAllFavoritesConfirm by rememberSaveable { mutableStateOf(false) }
    var trackInfoDialogState by remember {
        mutableStateOf<PlaylistTrackInfoDialogState?>(null)
    }
    val detailSubtitle = when {
        showingFavoritesDetail -> "Favorites"
        showingStoredPlaylistDetail -> selectedStoredPlaylist?.title
        else -> null
    }
    var albumOpenedFromArtist by surfaceState.albumOpenedFromArtistState
    var artistOpenedFromAlbum by surfaceState.artistOpenedFromAlbumState
    val detailCollapseFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val showCollapsedDetailSubtitle = detailSubtitle != null &&
        scrollBehavior.state.collapsedFraction >= 0.999f
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTabIndex != pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }
    }
    LaunchedEffect(destination, selectedStoredPlaylistId, libraryState.playlists) {
        if (
            destination == PlaylistsSurfaceDestination.StoredPlaylist &&
            selectedStoredPlaylist == null
        ) {
            destination = PlaylistsSurfaceDestination.Library
            selectedStoredPlaylistId = null
        }
    }
    BackHandler(enabled = backHandlingEnabled && librarySearchActive) {
        librarySearchActive = false
        librarySearchQuery = ""
        keyboardController?.hide()
        librarySearchResults = LibrarySearchResults("", emptyList(), emptyList(), emptyList())
    }
    BackHandler(enabled = backHandlingEnabled && showingPlaylistDetail) {
        favoritesEditModeEnabled = false
        favoritesDraggingEntryId = null
        selectedStoredPlaylistId = null
        if (destination == PlaylistsSurfaceDestination.AlbumDetail && albumOpenedFromArtist) {
            albumOpenedFromArtist = false
            destination = PlaylistsSurfaceDestination.ArtistDetail
        } else if (destination == PlaylistsSurfaceDestination.ArtistDetail && artistOpenedFromAlbum) {
            artistOpenedFromAlbum = false
            destination = PlaylistsSurfaceDestination.AlbumDetail
        } else {
            albumOpenedFromArtist = false
            artistOpenedFromAlbum = false
            destination = PlaylistsSurfaceDestination.Library
        }
    }
    LaunchedEffect(favoritesEditModeEnabled, favoritesSortMode, libraryState.favorites) {
        val isCustomSort = favoritesSortMode == PlaylistEntrySortMode.Custom
        val missingDraggedEntry = favoritesDraggingEntryId != null &&
            libraryState.favorites.none { it.id == favoritesDraggingEntryId }
        if (!favoritesEditModeEnabled || !isCustomSort || missingDraggedEntry) {
            favoritesDraggingEntryId = null
        }
    }
    val sortedFavoriteEntries = remember(libraryState.favorites, favoritesSortMode) {
        sortPlaylistEntries(
            entries = libraryState.favorites,
            sortMode = favoritesSortMode
        )
    }
    val playlistsTitleMarqueeClockState = rememberPlaylistsTitleMarqueeClockState(detailSubtitle)
    val watchDetailContentPadding = if (isWatch) {
        PaddingValues(
            start = if (isRound) 14.dp else 10.dp,
            top = if (isRound) 24.dp else 12.dp,
            end = if (isRound) 14.dp else 10.dp,
            bottom = if (isRound) 56.dp else 16.dp
        )
    } else {
        PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomContentPadding + 16.dp
        )
    }

    CompositionLocalProvider(LocalPlaylistsTitleMarqueeClockState provides playlistsTitleMarqueeClockState) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isWatch) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                ),
            topBar = {
                if (!isWatch) {
                    LargeTopAppBar(
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                if (showingPlaylistDetail && detailSubtitle != null) {
                                    val detailTitleAlpha = ((detailCollapseFraction - 0.58f) / 0.42f)
                                        .coerceIn(0f, 1f)
                                    val playlistsTitleAlpha = 1f - detailTitleAlpha
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "Playlists",
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.graphicsLayer(alpha = playlistsTitleAlpha)
                                        )
                                        PlaylistsTopBarMarqueeText(
                                            text = detailSubtitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.graphicsLayer(alpha = detailTitleAlpha)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.height(18.dp),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showCollapsedDetailSubtitle,
                                            enter = fadeIn(
                                                animationSpec = tween(
                                                    durationMillis = 160,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            ),
                                            exit = fadeOut(
                                                animationSpec = tween(
                                                    durationMillis = 90,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                        ) {
                                            Text(
                                                text = "Playlist",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = when {
                                            showingAlbumDetail || showingArtistDetail -> "Library"
                                            showingPlaylistDetail -> "Playlists"
                                            else -> "Library"
                                        }
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            AnimatedContent(
                                targetState = librarySearchActive && !showingPlaylistDetail,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                                        fadeOut(animationSpec = tween(durationMillis = 150))
                                },
                                label = "librarySearchNavigationIcon"
                            ) { searching ->
                                if (searching) {
                                    IconButton(
                                        onClick = {
                                            librarySearchActive = false
                                            librarySearchQuery = ""
                                            keyboardController?.hide()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close search"
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                    if (showingPlaylistDetail) {
                                        favoritesEditModeEnabled = false
                                        favoritesDraggingEntryId = null
                                        selectedStoredPlaylistId = null
                                        if (destination == PlaylistsSurfaceDestination.AlbumDetail &&
                                            albumOpenedFromArtist
                                        ) {
                                            albumOpenedFromArtist = false
                                            destination = PlaylistsSurfaceDestination.ArtistDetail
                                        } else if (destination == PlaylistsSurfaceDestination.ArtistDetail &&
                                            artistOpenedFromAlbum
                                        ) {
                                            artistOpenedFromAlbum = false
                                            destination = PlaylistsSurfaceDestination.AlbumDetail
                                        } else {
                                            albumOpenedFromArtist = false
                                            destination = PlaylistsSurfaceDestination.Library
                                        }
                                    } else {
                                        onBack()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go back"
                                )
                            }
                        }
                    }
                },
                        actions = {
                            if (!showingPlaylistDetail && !isWatch) {
                                AnimatedVisibility(
                                    visible = !librarySearchActive,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 150))
                                ) {
                                    IconButton(
                                        onClick = { librarySearchActive = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search library"
                                        )
                                    }
                                }
                                if (librarySyncState.isScanning) {
                                    Text(
                                        text = "${librarySyncState.indexedTracks} new",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        LibraryRepository.requestScan(context)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scan library now"
                                    )
                                }
                                IconButton(onClick = onOpenLibrarySettings) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Library sources"
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        ) { innerPadding ->
            val actualInnerPadding = if (isWatch) PaddingValues(0.dp) else innerPadding
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val forward = playlistsSurfaceDestinationOrder(targetState) >=
                        playlistsSurfaceDestinationOrder(initialState)
                    val enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PLAYLISTS_PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 210,
                            delayMillis = 40,
                            easing = LinearOutSlowInEasing
                        )
                    )
                    val exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                        animationSpec = tween(
                            durationMillis = PLAYLISTS_PAGE_NAV_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 110,
                            easing = FastOutLinearInEasing
                        )
                    )
                    enter togetherWith exit
                },
                label = "playlistsSurfaceTransition",
                modifier = Modifier.fillMaxSize()
            ) { currentDestination ->
                if (currentDestination == PlaylistsSurfaceDestination.AlbumDetail &&
                    libraryAlbumDetail != null
                ) {
                    LibraryAlbumDetailPage(
                        detail = libraryAlbumDetail,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(actualInnerPadding),
                        activeSourceId = currentPlaybackSourceId,
                        bottomContentPadding = bottomContentPadding,
                        listState = surfaceState.albumDetailListState,
                        artistKnown = libraryAlbumDetail.album.artist.isNotBlank() &&
                                !libraryAlbumDetail.album.artist.equals(LibraryContract.UNKNOWN_ARTIST, ignoreCase = true) &&
                                !libraryAlbumDetail.album.artist.equals(LibraryContract.VARIOUS_ARTISTS, ignoreCase = true),
                        onPlay = { index ->
                            onPlayLibraryTracks(
                                libraryAlbumDetail.tracks,
                                index,
                                libraryAlbumDetail.album.name
                            )
                        },
                        onShuffle = {
                            onShuffleLibraryTracks(
                                libraryAlbumDetail.tracks,
                                libraryAlbumDetail.album.name
                            )
                        },
                        onOpenArtist = {
                            onOpenLibraryArtist(libraryAlbumDetail.album.artist)
                            artistOpenedFromAlbum = true
                            destination = PlaylistsSurfaceDestination.ArtistDetail
                        },
                        trackActions = { track -> libraryRowActions(listOf(track)) },
                        albumActions = libraryRowActions(libraryAlbumDetail.tracks)
                    )
                } else if (currentDestination == PlaylistsSurfaceDestination.ArtistDetail &&
                    selectedArtistName != null &&
                    libraryArtistAlbums != null
                ) {
                    LibraryArtistDetailPage(
                        artist = selectedArtistName,
                        albums = libraryArtistAlbums,
                        tracks = surfaceState.artistTracksState.value,
                        activeSourceId = currentPlaybackSourceId,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(actualInnerPadding),
                        bottomContentPadding = bottomContentPadding,
                        albumsListState = surfaceState.artistDetailListState,
                        tracksListState = surfaceState.artistDetailTracksListState,
                        mode = artistContentMode,
                        onModeChanged = { artistContentMode = it },
                        onOpenAlbum = { album ->
                            onOpenLibraryAlbum(album.rawName, album.rawName)
                            albumOpenedFromArtist = true
                            destination = PlaylistsSurfaceDestination.AlbumDetail
                        },
                        onPlayTracks = onPlayLibraryTracks,
                        trackActions = if (isWatch) {
                            null
                        } else {
                            { track -> libraryRowActions(listOf(track)) }
                        }
                    )
                } else if (currentDestination == PlaylistsSurfaceDestination.Favorites) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(actualInnerPadding),
                        contentPadding = watchDetailContentPadding,
                        verticalArrangement = if (isWatch) Arrangement.spacedBy(6.dp) else Arrangement.spacedBy(0.dp)
                    ) {
                        playlistDetailContent(
                            title = "Favorites",
                            entries = sortedFavoriteEntries,
                            heroIcon = Icons.Default.Star,
                            emptyBody = "Your favorites will show up here.",
                            selectedSortMode = favoritesSortMode,
                            onSortModeSelected = onFavoritesSortModeChange,
                            isEditMode = favoritesEditModeEnabled,
                            onEditModeChanged = { enabled ->
                                favoritesEditModeEnabled = enabled
                                if (!enabled) {
                                    favoritesDraggingEntryId = null
                                }
                            },
                            canReorderEntries = favoritesSortMode == PlaylistEntrySortMode.Custom,
                            draggingEntryId = favoritesDraggingEntryId,
                            onDraggingEntryIdChange = { favoritesDraggingEntryId = it },
                            activeSourceId = currentPlaybackSourceId,
                            currentSubtuneIndex = currentSubtuneIndex,
                            onEntryClick = onOpenFavorite,
                            onPlayPlaylist = onPlayFavoritePlaylist,
                            onShufflePlaylist = onShuffleFavoritePlaylist,
                            onDeletePlaylist = {},
                            canDeletePlaylist = false,
                            onDeleteAllEntries = { showDeleteAllFavoritesConfirm = true },
                            onPlayEntry = onOpenFavorite,
                            onPlayEntryAsCached = onPlayFavoriteTrackAsCached,
                            onDeleteEntry = onDeleteFavoriteTrack,
                            onMoveEntry = onMoveFavoriteTrack,
                            onOpenEntryLocation = onOpenFavoriteTrackLocation,
                            onShareEntry = onShareFavoriteTrack,
                            onCopyEntrySource = onCopyFavoriteTrackSource,
                            onOpenEntryInfo = { entry ->
                                trackInfoDialogState = buildPlaylistTrackInfoDialogState(
                                    playlistTitle = "Favorites",
                                    entry = entry,
                                    networkNodes = networkNodes
                                )
                            },
                            isWatch = isWatch,
                            onBack = {
                                favoritesEditModeEnabled = false
                                favoritesDraggingEntryId = null
                                destination = PlaylistsSurfaceDestination.Library
                            }
                        )
                    }
                } else if (
                    currentDestination == PlaylistsSurfaceDestination.StoredPlaylist &&
                    selectedStoredPlaylist != null
                ) {
                    val sortedStoredPlaylistEntries = sortPlaylistEntries(
                        entries = selectedStoredPlaylist.entries,
                        sortMode = storedPlaylistSortMode
                    )
                    val sortedStoredPlaylist = selectedStoredPlaylist.copy(entries = sortedStoredPlaylistEntries)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(actualInnerPadding),
                        contentPadding = watchDetailContentPadding,
                        verticalArrangement = if (isWatch) Arrangement.spacedBy(6.dp) else Arrangement.spacedBy(0.dp)
                    ) {
                        playlistDetailContent(
                            title = selectedStoredPlaylist.title,
                            entries = sortedStoredPlaylistEntries,
                            heroIcon = null,
                            emptyBody = "This playlist has no tracks.",
                            selectedSortMode = storedPlaylistSortMode,
                            onSortModeSelected = { storedPlaylistSortMode = it },
                            isEditMode = false,
                            onEditModeChanged = {},
                            showAddAction = false,
                            showEditAction = false,
                            showDeleteAllEntriesAction = false,
                            canReorderEntries = false,
                            draggingEntryId = null,
                            onDraggingEntryIdChange = {},
                            activeSourceId = currentPlaybackSourceId,
                            currentSubtuneIndex = currentSubtuneIndex,
                            onEntryClick = { entry -> onOpenStoredPlaylistEntry(entry, sortedStoredPlaylist) },
                            onPlayPlaylist = { onPlayStoredPlaylist(sortedStoredPlaylist) },
                            onShufflePlaylist = { onShuffleStoredPlaylist(sortedStoredPlaylist) },
                            onDeletePlaylist = {},
                            canDeletePlaylist = false,
                            onDeleteAllEntries = {},
                            canDeleteEntries = false,
                            onPlayEntry = { entry -> onOpenStoredPlaylistEntry(entry, sortedStoredPlaylist) },
                            onPlayEntryAsCached = {},
                            onDeleteEntry = {},
                            onMoveEntry = { _, _ -> },
                            onOpenEntryLocation = {},
                            onShareEntry = {},
                            onCopyEntrySource = {},
                            onOpenEntryInfo = { entry ->
                                trackInfoDialogState = buildPlaylistTrackInfoDialogState(
                                    playlistTitle = selectedStoredPlaylist.title,
                                    entry = entry,
                                    networkNodes = networkNodes
                                )
                            },
                            showPlayAsCachedAction = false,
                            showLocationAction = false,
                            showShareAction = false,
                            showCopySourceAction = false,
                            showInfoAction = true,
                            isWatch = isWatch,
                            onBack = {
                                selectedStoredPlaylistId = null
                                destination = PlaylistsSurfaceDestination.Library
                            }
                        )
                    }
                } else {
                    if (isWatch) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(actualInnerPadding),
                            contentPadding = watchDetailContentPadding,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                Text(
                                    text = "Library",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    libraryTabs.forEachIndexed { index, tab ->
                                        val isSelected = selectedTabIndex == index
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedTabIndex = index
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = when (tab) {
                                                        LibrarySurfaceTab.Playlists -> "Playlists"
                                                        LibrarySurfaceTab.Albums -> "Albums"
                                                        LibrarySurfaceTab.Artists -> "Artists"
                                                        LibrarySurfaceTab.Tracks -> "Tracks"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            when (libraryTabs[selectedTabIndex]) {
                                LibrarySurfaceTab.Playlists -> {
                                    item {
                                        FavoritesCollectionRow(
                                            favoriteCount = libraryState.favorites.size,
                                            onClick = { destination = PlaylistsSurfaceDestination.Favorites },
                                            isWatch = true
                                        )
                                    }
                                    if (libraryState.playlists.isEmpty()) {
                                        item {
                                            EmptySectionCard(
                                                title = "No playlists yet",
                                                body = "Playlists will appear here."
                                            )
                                        }
                                    } else {
                                        items(
                                            items = libraryState.playlists,
                                            key = { it.id }
                                        ) { playlist ->
                                            PlaylistCollectionRow(
                                                playlist = playlist,
                                                onClick = {
                                                    selectedStoredPlaylistId = playlist.id
                                                    destination = PlaylistsSurfaceDestination.StoredPlaylist
                                                },
                                                isWatch = true
                                            )
                                        }
                                    }
                                }
                                LibrarySurfaceTab.Albums -> {
                                    if (effectiveLibraryCollections.albums.isEmpty()) {
                                        item {
                                            LibraryPlaceholderRow(
                                                title = if (librarySyncState.isScanning) "Scanning library…" else "No albums yet",
                                                body = if (librarySyncState.isScanning) "Indexing MediaStore tracks" else "Albums from your media library will appear here",
                                                isWatch = true
                                            )
                                        }
                                    } else {
                                        items(
                                            items = effectiveLibraryCollections.albums,
                                            key = { it.name }
                                        ) { album ->
                                            LibraryAlbumCompactRow(
                                                album = album,
                                                onClick = {
                                                    onOpenLibraryAlbum(album.rawName, album.rawName)
                                                    destination = PlaylistsSurfaceDestination.AlbumDetail
                                                }
                                            )
                                        }
                                    }
                                }
                                LibrarySurfaceTab.Artists -> {
                                    if (effectiveLibraryCollections.artists.isEmpty()) {
                                        item {
                                            LibraryPlaceholderRow(
                                                title = if (librarySyncState.isScanning) "Scanning library…" else "No artists yet",
                                                body = if (librarySyncState.isScanning) "Indexing MediaStore tracks" else "Library tracks will appear here",
                                                isWatch = true
                                            )
                                        }
                                    } else {
                                        items(
                                            items = effectiveLibraryCollections.artists,
                                            key = { it.name }
                                        ) { artist ->
                                            LibraryArtistCompactRow(
                                                artist = artist,
                                                onClick = {
                                                    onOpenLibraryArtist(artist.name)
                                                    destination = PlaylistsSurfaceDestination.ArtistDetail
                                                }
                                            )
                                        }
                                    }
                                }
                                LibrarySurfaceTab.Tracks -> {
                                    if (effectiveLibraryCollections.tracks.isEmpty()) {
                                        item {
                                            LibraryPlaceholderRow(
                                                title = if (librarySyncState.isScanning) "Scanning library…" else "No tracks yet",
                                                body = if (librarySyncState.isScanning) "Indexing MediaStore tracks" else "Tracks from your media library will appear here",
                                                isWatch = true
                                            )
                                        }
                                    } else {
                                        itemsIndexed(
                                            items = effectiveLibraryCollections.tracks,
                                            key = { _, track -> track.path }
                                        ) { index, track ->
                                            LibraryTrackListRow(
                                                position = index + 1,
                                                title = track.title,
                                                subtitleArtist = track.artist,
                                                durationMs = track.durationMs,
                                                isActive = currentPlaybackSourceId != null && currentPlaybackSourceId == track.path,
                                                onClick = {
                                                    onPlayLibraryTracks(
                                                        effectiveLibraryCollections.tracks,
                                                        index,
                                                        "All tracks"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        AnimatedContent(
                            targetState = librarySearchActive,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 160))
                            },
                            label = "librarySearchTransition"
                        ) { searching ->
                            if (searching) {
                                LibrarySearchOverlay(
                            query = librarySearchQuery,
                            results = librarySearchResults,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(actualInnerPadding),
                            bottomContentPadding = bottomContentPadding,
                            activeSourceId = currentPlaybackSourceId,
                            onQueryChanged = { librarySearchQuery = it },
                            onOpenAlbum = { album ->
                                keyboardController?.hide()
                                onOpenLibraryAlbum(album.rawName, album.rawName)
                                destination = PlaylistsSurfaceDestination.AlbumDetail
                            },
                            onOpenArtist = { artist ->
                                keyboardController?.hide()
                                onOpenLibraryArtist(artist.name)
                                destination = PlaylistsSurfaceDestination.ArtistDetail
                            },
                            onPlayTrack = { _, index ->
                                keyboardController?.hide()
                                onPlayLibraryTracks(
                                    librarySearchResults.tracks,
                                    index,
                                    librarySearchQuery.ifBlank { "Search" }
                                )
                            },
                            trackActions = { track -> libraryRowActions(listOf(track)) }
                                )
                            } else {
                                Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(actualInnerPadding)
                        ) {
                            LibraryTabRow(
                                selectedTabIndex = selectedTabIndex,
                                tabs = libraryTabs,
                                onTabSelected = { tabIndex ->
                                    if (selectedTabIndex == tabIndex) return@LibraryTabRow
                                    selectedTabIndex = tabIndex
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(tabIndex)
                                    }
                                }
                            )
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (libraryTabs[page]) {
                                    LibrarySurfaceTab.Playlists -> {
                                        PlaylistsLibraryTabPage(
                                            libraryState = libraryState,
                                            bottomContentPadding = bottomContentPadding,
                                            listState = surfaceState.playlistsTabListState,
                                            onOpenFavorites = { destination = PlaylistsSurfaceDestination.Favorites },
                                            onOpenPlaylist = { playlist ->
                                                selectedStoredPlaylistId = playlist.id
                                                destination = PlaylistsSurfaceDestination.StoredPlaylist
                                            }
                                        )
                                    }
                                    LibrarySurfaceTab.Albums -> {
                                        AlbumsLibraryPage(
                                            albums = effectiveLibraryCollections.albums,
                                            bottomContentPadding = bottomContentPadding,
                                            listState = surfaceState.albumsListState,
                                            gridState = surfaceState.albumsGridState,
                                            layout = albumCollectionLayout,
                                            onLayoutChanged = { albumCollectionLayout = it },
                                            isSyncing = librarySyncState.isScanning,
                                            syncState = librarySyncState,
                                            onOpenAlbum = { album ->
                                                onOpenLibraryAlbum(album.rawName, album.rawName)
                                                destination = PlaylistsSurfaceDestination.AlbumDetail
                                            },
                                            contextMenuFor = ::libraryAlbumContextMenu
                                        )
                                    }
                                    LibrarySurfaceTab.Artists -> {
                                        ArtistsLibraryPage(
                                            artists = effectiveLibraryCollections.artists,
                                            bottomContentPadding = bottomContentPadding,
                                            listState = surfaceState.artistsListState,
                                            isSyncing = librarySyncState.isScanning,
                                            syncState = librarySyncState,
                                            onOpenArtist = { artist ->
                                                onOpenLibraryArtist(artist.name)
                                                destination = PlaylistsSurfaceDestination.ArtistDetail
                                            },
                                            contextMenuFor = ::libraryArtistContextMenu
                                        )
                                    }
                                    LibrarySurfaceTab.Tracks -> {
                                        TracksLibraryPage(
                                            tracks = effectiveLibraryCollections.tracks,
                                            bottomContentPadding = bottomContentPadding,
                                            listState = surfaceState.tracksListState,
                                            isSyncing = librarySyncState.isScanning,
                                            syncState = librarySyncState,
                                            activeSourceId = currentPlaybackSourceId,
                                            onPlayTracks = onPlayLibraryTracks,
                                            contextMenuFor = { track -> libraryRowActions(listOf(track)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                    }
                }
            }
        }
    }
    if (showDeleteAllFavoritesConfirm) {
        if (isWatch) {
            WatchDialogContainer(
                title = "Delete all favorites?",
                onDismissRequest = { showDeleteAllFavoritesConfirm = false }
            ) {
                Text(
                    text = "This will remove every track from Favorites.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        showDeleteAllFavoritesConfirm = false
                        onDeleteAllFavorites()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Delete all")
                }
                TextButton(
                    onClick = { showDeleteAllFavoritesConfirm = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteAllFavoritesConfirm = false },
                title = { Text("Delete all favorites?") },
                text = { Text("This will remove every track from Favorites.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAllFavoritesConfirm = false
                            onDeleteAllFavorites()
                        }
                    ) {
                        Text("Delete all")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllFavoritesConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    trackInfoDialogState?.let { dialogState ->
        BrowserInfoDialog(
            title = "Track and decoder info",
            fields = dialogState.fields,
            onDismiss = { trackInfoDialogState = null }
        )
    }
    libraryContextTracks?.let { contextTracks ->
        var newPlaylistTitle by remember(contextTracks) { mutableStateOf("") }
        FloatingActionDialog(
            title = "Add to playlist",
            onDismiss = { libraryContextTracks = null },
            confirmText = "Create",
            confirmEnabled = newPlaylistTitle.isNotBlank(),
            onConfirm = {
                onAddLibraryTracksToPlaylist(contextTracks, null, newPlaylistTitle.trim())
                libraryContextTracks = null
            }
        ) {
            DialogSectionLabel("Create new playlist")
            OutlinedTextField(
                value = newPlaylistTitle,
                onValueChange = { newPlaylistTitle = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                modifier = Modifier.fillMaxWidth()
            )
            if (libraryState.playlists.isNotEmpty()) {
                DialogSectionLabel("Your playlists", modifier = Modifier.padding(top = 12.dp))
                libraryState.playlists.forEach { playlist ->
                    DialogSelectableCard(
                        label = playlist.title,
                        icon = Icons.Default.PlayArrow,
                        isSelected = false,
                        isEnabled = true,
                        subtitle = "${playlist.entries.size} tracks",
                        onClick = {
                            onAddLibraryTracksToPlaylist(contextTracks, playlist.id, "")
                            libraryContextTracks = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsLibraryTabPage(
    libraryState: PlaylistLibraryState,
    bottomContentPadding: Dp,
    listState: LazyListState,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit,
    isWatch: Boolean = false
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (isWatch) {
            PaddingValues(0.dp)
        } else {
            PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomContentPadding + 16.dp
            )
        },
        verticalArrangement = if (isWatch) Arrangement.spacedBy(6.dp) else Arrangement.spacedBy(0.dp)
    ) {
        item {
            FavoritesCollectionRow(
                favoriteCount = libraryState.favorites.size,
                onClick = onOpenFavorites,
                isWatch = isWatch
            )
        }
        if (!isWatch) {
            item {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
        if (libraryState.playlists.isEmpty()) {
            item {
                if (!isWatch) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                EmptySectionCard(
                    title = "No playlists yet",
                    body = "More playlist options will show up here later."
                )
            }
        } else {
            items(
                items = libraryState.playlists,
                key = { it.id }
            ) { playlist ->
                PlaylistCollectionRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) },
                    isWatch = isWatch
                )
                if (!isWatch) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionPlaceholderPage(
    title: String,
    body: String,
    bottomContentPadding: Dp,
    isWatch: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (isWatch) {
            PaddingValues(0.dp)
        } else {
            PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = bottomContentPadding + 16.dp
            )
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        items(
            items = listOf(0, 1, 2, 3)
        ) {
            LibraryPlaceholderRow(
                title = "$title will appear here",
                body = "Add library folders later.",
                isWatch = isWatch
            )
        }
    }
}

@Composable
private fun AlbumsLibraryPage(
    albums: List<LibraryAlbum>,
    bottomContentPadding: Dp,
    listState: LazyListState,
    gridState: LazyGridState,
    layout: AlbumCollectionLayout,
    onLayoutChanged: (AlbumCollectionLayout) -> Unit,
    isSyncing: Boolean,
    syncState: LibrarySyncState,
    onOpenAlbum: (LibraryAlbum) -> Unit,
    contextMenuFor: (LibraryAlbum) -> LibraryCollectionContextMenu,
    isWatch: Boolean = false
) {
    if (albums.isEmpty()) {
        LibraryCollectionPlaceholderPage(
            title = if (isSyncing) "Scanning library…" else "No albums yet",
            body = if (isSyncing) "Indexing MediaStore tracks" else "Albums from your media library will appear here.",
            bottomContentPadding = bottomContentPadding,
            isWatch = isWatch
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (syncState.isScanning) {
            LibraryScanProgressRow(
                syncState = syncState,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )
        }
        AnimatedContent(
            targetState = layout,
            transitionSpec = {
                val enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 160,
                        easing = LinearOutSlowInEasing
                    )
                )
                val exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutLinearInEasing
                    )
                )
                enter togetherWith exit
            },
            label = "albumLayoutModeTransition",
            modifier = Modifier.fillMaxSize()
        ) { currentLayout ->
            if (currentLayout == AlbumCollectionLayout.List) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 10.dp,
                        end = 16.dp,
                        bottom = bottomContentPadding + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        AlbumLayoutToggleRow(
                            layout = currentLayout,
                            onLayoutChanged = onLayoutChanged
                        )
                    }
                    items(albums, key = { it.name }) { album ->
                        AlbumLibraryListRow(
                            album = album,
                            onClick = { onOpenAlbum(album) },
                            contextMenu = contextMenuFor(album)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 10.dp,
                        end = 16.dp,
                        bottom = bottomContentPadding + 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AlbumLayoutToggleRow(
                            layout = currentLayout,
                            onLayoutChanged = onLayoutChanged
                        )
                    }
                    gridItems(albums, key = { it.name }) { album ->
                        AlbumLibraryGridCard(
                            album = album,
                            onClick = { onOpenAlbum(album) },
                            contextMenu = contextMenuFor(album)
                        )
                    }
                }
            }
        }
    }
    }

@Composable
private fun TracksLibraryPage(
    tracks: List<LibraryTrackEntity>,
    bottomContentPadding: Dp,
    listState: LazyListState,
    isSyncing: Boolean,
    syncState: LibrarySyncState,
    activeSourceId: String?,
    onPlayTracks: (List<LibraryTrackEntity>, Int, String) -> Unit,
    contextMenuFor: (LibraryTrackEntity) -> LibraryRowContextActions
) {
    if (tracks.isEmpty()) {
        LibraryCollectionPlaceholderPage(
            title = if (isSyncing) "Scanning library…" else "No tracks yet",
            body = if (isSyncing) "Indexing MediaStore tracks" else "Tracks from your media library will appear here.",
            bottomContentPadding = bottomContentPadding,
            isWatch = false
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (syncState.isScanning) {
            LibraryScanProgressRow(
                syncState = syncState,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 10.dp,
                end = 16.dp,
                bottom = bottomContentPadding + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(tracks, key = { _, track -> track.path }) { index, track ->
                LibraryTrackListRow(
                    position = index + 1,
                    title = track.title,
                    subtitleArtist = track.artist,
                    durationMs = track.durationMs,
                    isActive = activeSourceId != null && activeSourceId == track.path,
                    onClick = { onPlayTracks(tracks, index, "All tracks") },
                    actions = contextMenuFor(track),
                    artworkPath = track.path
                )
            }
        }
    }
}

@Composable
private fun ArtistsLibraryPage(
    artists: List<LibraryArtist>,
    bottomContentPadding: Dp,
    listState: LazyListState,
    isSyncing: Boolean,
    syncState: LibrarySyncState,
    onOpenArtist: (LibraryArtist) -> Unit,
    contextMenuFor: (LibraryArtist) -> LibraryCollectionContextMenu,
    isWatch: Boolean = false
) {
    if (artists.isEmpty()) {
        LibraryCollectionPlaceholderPage(
            title = if (isSyncing) "Scanning library…" else "No artists yet",
            body = if (isSyncing) "Indexing MediaStore tracks" else "Artists from your media library will appear here.",
            bottomContentPadding = bottomContentPadding,
            isWatch = isWatch
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (syncState.isScanning) {
            LibraryScanProgressRow(
                syncState = syncState,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )
        }
        LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 10.dp,
            end = 16.dp,
            bottom = bottomContentPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            ArtistLibraryRow(
                artist = artist,
                onClick = { onOpenArtist(artist) },
                contextMenu = contextMenuFor(artist)
            )
        }
    }
    }
}

@Composable
private fun LibraryScanProgressRow(
    syncState: LibrarySyncState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Scanning library…",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${syncState.scannedFiles} files checked · ${syncState.indexedTracks} tracks indexed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumLayoutToggleRow(
    layout: AlbumCollectionLayout,
    onLayoutChanged: (AlbumCollectionLayout) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LibraryLayoutToggleButton(
                selected = layout == AlbumCollectionLayout.Grid,
                icon = Icons.Default.GridView,
                contentDescription = "Grid albums",
                onClick = { onLayoutChanged(AlbumCollectionLayout.Grid) }
            )
            LibraryLayoutToggleButton(
                selected = layout == AlbumCollectionLayout.List,
                icon = Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "List albums",
                onClick = { onLayoutChanged(AlbumCollectionLayout.List) }
            )
        }
    }
}

@Composable
private fun LibraryLayoutToggleButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    ) {
        Box(
            modifier = Modifier
                .size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AlbumLibraryGridCard(
    album: LibraryAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contextMenu: LibraryCollectionContextMenu? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = contextMenu?.let { { menuExpanded = true } }
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (contextMenu != null) {
            LibraryCollectionActionsMenu(
                actions = contextMenu,
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                AlbumArtworkBox(artworkPath = album.artworkPath)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (album.artist.isBlank()) "${album.trackCount} tracks" else "${album.artist} · ${album.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibraryAlbumDetailPage(
    detail: LibraryAlbumDetail,
    modifier: Modifier = Modifier,
    activeSourceId: String?,
    bottomContentPadding: Dp,
    listState: LazyListState,
    artistKnown: Boolean,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onOpenArtist: () -> Unit,
    trackActions: (LibraryTrackEntity) -> LibraryRowContextActions,
    albumActions: LibraryRowContextActions
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomContentPadding + 16.dp
        )
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    AlbumArtworkBox(
                        artworkPath = detail.album.artworkPath,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = detail.album.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            if (artistKnown) append(detail.album.artist)
                            if (detail.album.year > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(detail.album.year)
                            }
                            if (isNotEmpty()) append(" · ")
                            append(playlistTrackCountLabel(detail.album.trackCount))
                            if (detail.album.durationMs > 0L) {
                                append(" · ")
                                append(formatPlaylistInfoDuration(detail.album.durationMs / 1000.0))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Go to artist", style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                enabled = artistKnown,
                                onClick = {
                                    menuExpanded = false
                                    onOpenArtist()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (albumActions.isFavorite) "Remove album from favorites" else "Add album to favorites",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (albumActions.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
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
                                    albumActions.onToggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (albumActions.isPinned) "Unpin album from home" else "Pin album to home",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
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
                                    albumActions.onTogglePin()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add album to playlist…", style = MaterialTheme.typography.bodyLarge) },
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
                                    albumActions.onAddToPlaylist()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onShuffle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle album"
                        )
                    }
                    FilledIconButton(
                        onClick = { onPlay(0) },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play album",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
        itemsIndexed(
            items = detail.tracks,
            key = { _, track -> track.path }
        ) { index, track ->
            LibraryTrackListRow(
                position = if (track.trackNo > 0) track.trackNo else index + 1,
                title = track.title,
                subtitleArtist = track.artist,
                durationMs = track.durationMs,
                isActive = activeSourceId != null && activeSourceId == track.path,
                onClick = { onPlay(index) },
                actions = trackActions(track)
            )
            if (index < detail.tracks.lastIndex) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(start = 58.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun LibraryCollectionActionsMenu(
    actions: LibraryCollectionContextMenu,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Add ${actions.noun} to favorites", style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.StarBorder,
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
                onDismiss()
                actions.onAddToFavorites()
            }
        )
        DropdownMenuItem(
            text = { Text("Pin ${actions.noun} to home", style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PushPin,
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
                onDismiss()
                actions.onPin()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to playlist…", style = MaterialTheme.typography.bodyLarge) },
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
                onDismiss()
                actions.onAddToPlaylist()
            }
        )
    }
}

@Composable
private fun LibraryTrackListRow(
    position: Int,
    title: String,
    subtitleArtist: String,
    durationMs: Long,
    isActive: Boolean,
    onClick: () -> Unit,
    actions: LibraryRowContextActions? = null,
    artworkPath: String? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = actions?.let { { menuExpanded = true } }
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (actions != null) {
            LibraryItemActionsMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                actions = actions
            )
        }
        if (artworkPath != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AlbumArtworkBox(artworkPath = artworkPath)
            }
        } else {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                if (durationMs > 0L) {
                    append(formatPlaylistInfoDuration(durationMs / 1000.0))
                }
                val artist = subtitleArtist.takeUnless {
                    it.isBlank() || it.equals("Unknown artist", ignoreCase = true)
                }
                if (artist != null) {
                    if (isNotEmpty()) append(" · ")
                    append(artist)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibraryArtistDetailPage(
    artist: String,
    albums: List<LibraryAlbum>,
    tracks: List<LibraryTrackEntity>,
    activeSourceId: String?,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp,
    albumsListState: LazyListState,
    tracksListState: LazyListState,
    mode: ArtistContentMode,
    onModeChanged: (ArtistContentMode) -> Unit,
    onOpenAlbum: (LibraryAlbum) -> Unit,
    onPlayTracks: (List<LibraryTrackEntity>, Int, String) -> Unit,
    trackActions: ((LibraryTrackEntity) -> LibraryRowContextActions)? = null
) {
    val pagerState = rememberPagerState(initialPage = mode.ordinal) { ArtistContentMode.entries.size }
    val pagerScope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) {
        val entry = ArtistContentMode.entries.getOrNull(pagerState.currentPage)
        if (entry != null && entry != mode) {
            onModeChanged(entry)
        }
    }
    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = artist,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val totalTracks = albums.sumOf { it.trackCount }
            Text(
                text = "${albums.size} albums · $totalTracks tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TabRow(
            selectedTabIndex = mode.ordinal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            ArtistContentMode.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = mode == entry,
                    onClick = {
                        pagerScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = entry.label) }
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (ArtistContentMode.entries[page]) {
                ArtistContentMode.Albums -> {
                    LazyColumn(
                        state = albumsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 4.dp,
                            end = 16.dp,
                            bottom = bottomContentPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = albums,
                            key = { "${it.name}|${it.artist}" }
                        ) { album ->
                            AlbumLibraryListRow(album = album, onClick = { onOpenAlbum(album) })
                        }
                    }
                }
                ArtistContentMode.Tracks -> {
                    LazyColumn(
                        state = tracksListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 4.dp,
                            end = 16.dp,
                            bottom = bottomContentPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (tracks.isEmpty()) {
                            item {
                                Text(
                                    text = "No tracks indexed for this artist.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = tracks,
                                key = { _, track -> track.path }
                            ) { index, track ->
                                LibraryTrackListRow(
                                    position = index + 1,
                                    title = track.title,
                                    subtitleArtist = track.artist,
                                    durationMs = track.durationMs,
                                    isActive = activeSourceId != null && activeSourceId == track.path,
                                    onClick = { onPlayTracks(tracks, index, artist) },
                                    actions = trackActions?.invoke(track),
                                    artworkPath = track.path
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryAlbumCompactRow(
    album: LibraryAlbum,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            AlbumArtworkBox(artworkPath = album.artworkPath)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (album.artist.isBlank()) "${album.trackCount} tracks" else "${album.artist} · ${album.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryArtistCompactRow(
    artist: LibraryArtist,
    onClick: () -> Unit,
    contextMenu: LibraryCollectionContextMenu? = null,
    flat: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flat) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = contextMenu?.let { { menuExpanded = true } }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (contextMenu != null) {
            LibraryCollectionActionsMenu(
                actions = contextMenu,
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false }
            )
        }
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            AlbumArtworkBox(artworkPath = artist.artworkPath)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.albumCount} albums · ${artist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumArtworkBox(
    artworkPath: String?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(artworkPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artworkPath) {
        val path = artworkPath ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            loadArtworkForFile(File(path))
        }
    }
    val artwork = bitmap
    if (artwork != null) {
        Image(
            bitmap = artwork,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            LibraryFallbackArtworkIcon(
                fileName = File(artworkPath.orEmpty()).name,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun LibraryFallbackArtworkIcon(
    fileName: String,
    modifier: Modifier = Modifier
) {
    val supportedExtensions = remember {
        NativeBridge.getSupportedExtensions()
            .asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
    }
    val decoderArtworkHints = rememberBrowserDecoderArtworkHints()
    val visualKind = remember(fileName, supportedExtensions, decoderArtworkHints) {
        browserRemoteEntryVisualKind(
            name = fileName,
            isDirectory = false,
            supportedExtensions = supportedExtensions,
            decoderExtensionArtworkHints = decoderArtworkHints
        )
    }
    BrowserRemoteEntryIcon(
        visualKind = visualKind,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun AlbumLibraryListRow(
    album: LibraryAlbum,
    onClick: () -> Unit,
    contextMenu: LibraryCollectionContextMenu? = null,
    flat: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flat) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = contextMenu?.let { { menuExpanded = true } }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (contextMenu != null) {
            LibraryCollectionActionsMenu(
                actions = contextMenu,
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false }
            )
        }
        Surface(
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            AlbumArtworkBox(artworkPath = album.artworkPath)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (album.artist.isBlank()) "${album.trackCount} tracks" else "${album.artist} · ${album.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistLibraryRow(
    artist: LibraryArtist,
    onClick: () -> Unit,
    contextMenu: LibraryCollectionContextMenu
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCollectionActionsMenu(
            actions = contextMenu,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false }
        )
        Surface(
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            AlbumArtworkBox(artworkPath = artist.artworkPath)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.albumCount} albums · ${artist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryPlaceholderRow(
    title: String,
    body: String,
    isWatch: Boolean = false
) {
    if (isWatch) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun LibraryTabRow(
    selectedTabIndex: Int,
    tabs: List<LibrarySurfaceTab>,
    onTabSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = when (tab) {
                            LibrarySurfaceTab.Playlists -> "Playlists"
                            LibrarySurfaceTab.Albums -> "Albums"
                            LibrarySurfaceTab.Artists -> "Artists"
                            LibrarySurfaceTab.Tracks -> "Tracks"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun rememberLibraryTabs(): List<LibrarySurfaceTab> {
    return listOf(
        LibrarySurfaceTab.Playlists,
        LibrarySurfaceTab.Albums,
        LibrarySurfaceTab.Artists,
        LibrarySurfaceTab.Tracks
    )
}

private fun LazyListScope.playlistDetailContent(
    title: String,
    entries: List<PlaylistTrackEntry>,
    heroIcon: ImageVector?,
    emptyBody: String,
    selectedSortMode: PlaylistEntrySortMode,
    onSortModeSelected: (PlaylistEntrySortMode) -> Unit,
    isEditMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    showAddAction: Boolean = true,
    showEditAction: Boolean = true,
    showDeleteAllEntriesAction: Boolean = true,
    canReorderEntries: Boolean,
    draggingEntryId: String?,
    onDraggingEntryIdChange: (String?) -> Unit,
    activeSourceId: String?,
    currentSubtuneIndex: Int,
    onEntryClick: (PlaylistTrackEntry) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onDeleteAllEntries: () -> Unit,
    canDeleteEntries: Boolean = true,
    onPlayEntry: (PlaylistTrackEntry) -> Unit,
    onPlayEntryAsCached: (PlaylistTrackEntry) -> Unit,
    onDeleteEntry: (PlaylistTrackEntry) -> Unit,
    onMoveEntry: (PlaylistTrackEntry, Int) -> Unit,
    onOpenEntryLocation: (PlaylistTrackEntry) -> Unit,
    onShareEntry: (PlaylistTrackEntry) -> Unit,
    onCopyEntrySource: (PlaylistTrackEntry) -> Unit,
    onOpenEntryInfo: (PlaylistTrackEntry) -> Unit,
    showPlayAsCachedAction: Boolean = true,
    showLocationAction: Boolean = true,
    showShareAction: Boolean = true,
    showCopySourceAction: Boolean = true,
    showInfoAction: Boolean = true,
    isWatch: Boolean = false,
    onBack: () -> Unit = {}
) {
    item {
        if (isWatch) {
            WearPlaylistHeroHeader(
                title = title,
                trackCountLabel = playlistTrackCountLabel(entries.size),
                heroIcon = heroIcon,
                entries = entries,
                selectedSortMode = selectedSortMode,
                onSortModeSelected = onSortModeSelected,
                showDeleteAllEntriesAction = showDeleteAllEntriesAction,
                onPlayPlaylist = onPlayPlaylist,
                onShufflePlaylist = onShufflePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                canDeletePlaylist = canDeletePlaylist,
                onDeleteAllEntries = onDeleteAllEntries,
                onBack = onBack
            )
        } else {
            PlaylistHeroCard(
                title = title,
                trackCountLabel = playlistTrackCountLabel(entries.size),
                heroIcon = heroIcon,
                entries = entries,
                selectedSortMode = selectedSortMode,
                onSortModeSelected = onSortModeSelected,
                isEditMode = isEditMode,
                onEditModeChanged = onEditModeChanged,
                showAddAction = showAddAction,
                showEditAction = showEditAction,
                showDeleteAllEntriesAction = showDeleteAllEntriesAction,
                onPlayPlaylist = onPlayPlaylist,
                onShufflePlaylist = onShufflePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                canDeletePlaylist = canDeletePlaylist,
                onDeleteAllEntries = onDeleteAllEntries
            )
        }
    }
    if (entries.isEmpty()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isWatch) 4.dp else (PLAYLISTS_DETAIL_CONTENT_GUTTER + 6.dp),
                        top = 8.dp,
                        end = if (isWatch) 4.dp else (PLAYLISTS_DETAIL_CONTENT_GUTTER + 6.dp)
                    ),
                horizontalAlignment = if (isWatch) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Nothing here yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = if (isWatch) TextAlign.Center else TextAlign.Start
                )
                Text(
                    text = emptyBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (isWatch) TextAlign.Center else TextAlign.Start
                )
            }
        }
    } else {
        itemsIndexed(
            items = entries,
            key = { _, entry -> entry.id }
        ) { index, entry ->
            val isActive = playlistEntryMatchesPlayback(
                entry = entry,
                activeSourceId = activeSourceId,
                currentSubtuneIndex = currentSubtuneIndex
            )
            val canMoveUp = index > 0
            val canMoveDown = index < entries.lastIndex
            PlaylistTrackRow(
                entry = entry,
                isActive = isActive,
                isDragged = draggingEntryId == entry.id,
                editModeEnabled = isEditMode,
                canReorder = canReorderEntries,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onClick = { onEntryClick(entry) },
                onPlay = { onPlayEntry(entry) },
                onPlayAsCached = { onPlayEntryAsCached(entry) },
                canDelete = canDeleteEntries,
                onDelete = { onDeleteEntry(entry) },
                onMoveUp = { onMoveEntry(entry, -1) },
                onMoveDown = { onMoveEntry(entry, 1) },
                onDragStart = {
                    if (canReorderEntries) {
                        onDraggingEntryIdChange(entry.id)
                    }
                },
                onDragStep = { direction ->
                    if (canReorderEntries) {
                        if (direction > 0) {
                            onMoveEntry(entry, 1)
                        } else if (direction < 0) {
                            onMoveEntry(entry, -1)
                        }
                    }
                },
                onDragEnd = {
                    onDraggingEntryIdChange(null)
                },
                onOpenLocation = { onOpenEntryLocation(entry) },
                onShare = { onShareEntry(entry) },
                onCopySource = { onCopyEntrySource(entry) },
                onOpenInfo = { onOpenEntryInfo(entry) },
                showPlayAsCachedAction = showPlayAsCachedAction,
                showLocationAction = showLocationAction,
                showShareAction = showShareAction,
                showCopySourceAction = showCopySourceAction,
                showInfoAction = showInfoAction,
                isWatch = isWatch
            )
        }
    }
}

@Composable
private fun WearPlaylistHeroHeader(
    title: String,
    trackCountLabel: String,
    heroIcon: ImageVector?,
    entries: List<PlaylistTrackEntry>,
    selectedSortMode: PlaylistEntrySortMode,
    onSortModeSelected: (PlaylistEntrySortMode) -> Unit,
    showDeleteAllEntriesAction: Boolean,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onDeleteAllEntries: () -> Unit,
    onBack: () -> Unit
) {
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreActionsDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Back to Library",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        PlaylistCoverArt(
            entries = entries,
            heroIcon = heroIcon,
            modifier = Modifier.size(48.dp),
            iconSize = 24.dp
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = trackCountLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            FilledIconButton(
                onClick = onPlayPlaylist,
                modifier = Modifier.size(42.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(24.dp)
                )
            }
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onShufflePlaylist),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { showSortDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canDeletePlaylist || showDeleteAllEntriesAction) {
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { showMoreActionsDialog = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        WatchDialogContainer(
            title = "Sort tracks",
            onDismissRequest = { showSortDialog = false }
        ) {
            PlaylistEntrySortMode.entries.forEach { mode ->
                val isSelected = mode == selectedSortMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickable {
                            onSortModeSelected(mode)
                            showSortDialog = false
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { showSortDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }

    if (showMoreActionsDialog) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = { showMoreActionsDialog = false }
        ) {
            if (canDeletePlaylist) {
                Button(
                    onClick = {
                        showMoreActionsDialog = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Delete playlist")
                }
            }
            if (showDeleteAllEntriesAction) {
                Button(
                    onClick = {
                        showMoreActionsDialog = false
                        onDeleteAllEntries()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Delete all entries")
                }
            }
            TextButton(
                onClick = { showMoreActionsDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun PlaylistHeroCard(
    title: String,
    trackCountLabel: String,
    heroIcon: ImageVector?,
    entries: List<PlaylistTrackEntry>,
    selectedSortMode: PlaylistEntrySortMode,
    onSortModeSelected: (PlaylistEntrySortMode) -> Unit,
    isEditMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    showAddAction: Boolean,
    showEditAction: Boolean,
    showDeleteAllEntriesAction: Boolean,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onDeleteAllEntries: () -> Unit
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PLAYLISTS_DETAIL_CONTENT_GUTTER,
                top = 8.dp,
                end = PLAYLISTS_DETAIL_CONTENT_GUTTER,
                bottom = 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PlaylistCoverArt(
            entries = entries,
            heroIcon = heroIcon,
            modifier = Modifier.size(220.dp),
            iconSize = 68.dp
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = trackCountLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete playlist",
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
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        enabled = canDeletePlaylist,
                        onClick = {
                            menuExpanded = false
                            onDeletePlaylist()
                        }
                    )
                    if (showDeleteAllEntriesAction) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete all entries",
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
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                menuExpanded = false
                                onDeleteAllEntries()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onShufflePlaylist) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle"
                )
            }
            FilledIconButton(
                onClick = onPlayPlaylist,
                modifier = Modifier.size(64.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play playlist",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showAddAction) {
                PlaylistActionPill(
                    label = "Add",
                    icon = Icons.Default.Add,
                    onClick = {}
                )
            }
            if (showEditAction) {
                PlaylistActionPill(
                    label = if (isEditMode) "Done" else "Edit",
                    icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    onClick = { onEditModeChanged(!isEditMode) }
                )
            }
            Box {
                PlaylistActionPill(
                    label = "Sort",
                    icon = Icons.Default.SwapVert,
                    onClick = { sortMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    PlaylistEntrySortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = if (mode == selectedSortMode) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else null,
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            onClick = {
                                sortMenuExpanded = false
                                onSortModeSelected(mode)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.clip(pillShape).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = pillShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PlaylistCoverArt(
    entries: List<PlaylistTrackEntry>,
    heroIcon: ImageVector?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (heroIcon != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(iconSize * 1.65f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = heroIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        } else {
            PlaylistIconGrid(entries = entries)
        }
    }
}

@Composable
private fun PlaylistIconGrid(
    entries: List<PlaylistTrackEntry>
) {
    val coverSources = playlistCoverSources(entries)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlaylistCoverCell(
                source = coverSources[0],
                modifier = Modifier.weight(1f)
            )
            PlaylistCoverCell(
                source = coverSources[1],
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlaylistCoverCell(
                source = coverSources[2],
                modifier = Modifier.weight(1f)
            )
            PlaylistCoverCell(
                source = coverSources[3],
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlaylistCoverCell(
    source: String?,
    modifier: Modifier = Modifier
) {
    val icon = placeholderArtworkIconForFile(
        file = source?.let(::File),
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun FavoritesCollectionRow(
    favoriteCount: Int,
    onClick: () -> Unit,
    isWatch: Boolean = false
) {
    PlaylistLibraryFlatRow(
        modifier = Modifier
            .fillMaxWidth(),
        title = "Favorites",
        subtitle = when (favoriteCount) {
            0 -> "No tracks yet"
            1 -> "1 track"
            else -> "$favoriteCount tracks"
        },
        icon = Icons.Default.Star,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
        isWatch = isWatch
    )
}

@Composable
private fun EmptySectionCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun playlistsSurfaceDestinationOrder(destination: PlaylistsSurfaceDestination): Int =
    when (destination) {
        PlaylistsSurfaceDestination.Library -> 0
        PlaylistsSurfaceDestination.Favorites -> 1
        PlaylistsSurfaceDestination.StoredPlaylist -> 1
        PlaylistsSurfaceDestination.AlbumDetail -> 1
        PlaylistsSurfaceDestination.ArtistDetail -> 1
    }

private fun playlistTrackCountLabel(trackCount: Int): String =
    when (trackCount) {
        0 -> "No tracks yet"
        1 -> "1 track"
        else -> "$trackCount tracks"
    }

private fun playlistCoverSources(entries: List<PlaylistTrackEntry>): List<String?> {
    val distinctSources = entries
        .asSequence()
        .mapNotNull { entry -> entry.source.takeIf { it.isNotBlank() } }
        .distinctBy { source -> playlistCoverSourceKey(source) }
        .take(4)
        .toMutableList()
    if (distinctSources.isEmpty()) {
        distinctSources += ""
    }
    while (distinctSources.size < 4) {
        distinctSources += distinctSources.last()
    }
    return distinctSources
}

private fun playlistCoverSourceKey(source: String): String {
    val fileName = source.substringAfterLast('/').substringAfterLast('\\')
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
    return extension.ifBlank {
        fileName.lowercase(Locale.ROOT)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRow(
    entry: PlaylistTrackEntry,
    isActive: Boolean,
    isDragged: Boolean,
    editModeEnabled: Boolean,
    canReorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPlayAsCached: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onOpenLocation: () -> Unit,
    onShare: () -> Unit,
    onCopySource: () -> Unit,
    onOpenInfo: () -> Unit,
    showPlayAsCachedAction: Boolean,
    showLocationAction: Boolean,
    showShareAction: Boolean,
    showCopySourceAction: Boolean,
    showInfoAction: Boolean,
    isWatch: Boolean = false
) {
    var menuExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var wearActionSheetOpen by rememberSaveable(entry.id) { mutableStateOf(false) }
    val isRemoteSource = remember(entry.source) { isRemotePlaylistSource(entry.source) }
    val localEntryFile = remember(entry.source) { resolvePlaylistEntryLocalFile(entry.source) }
    val canOpenLocalLocation = localEntryFile?.exists() == true

    if (isWatch) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { wearActionSheetOpen = true }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistTrackArtworkChip(
                entry = entry,
                isActive = isActive,
                isWatch = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlistPageTrackSubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { wearActionSheetOpen = true },
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (wearActionSheetOpen) {
            WatchDialogContainer(
                title = entry.title,
                onDismissRequest = { wearActionSheetOpen = false }
            ) {
                Text(
                    text = playlistPageTrackSubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                Button(
                    onClick = {
                        wearActionSheetOpen = false
                        onPlay()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Play")
                    }
                }
                if (showPlayAsCachedAction && isRemoteSource) {
                    FilledTonalButton(
                        onClick = {
                            wearActionSheetOpen = false
                            onPlayAsCached()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Play as cached")
                    }
                }
                if (showInfoAction) {
                    FilledTonalButton(
                        onClick = {
                            wearActionSheetOpen = false
                            onOpenInfo()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Track info")
                        }
                    }
                }
                if (showLocationAction && canOpenLocalLocation) {
                    FilledTonalButton(
                        onClick = {
                            wearActionSheetOpen = false
                            onOpenLocation()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Open location")
                        }
                    }
                }
                if (canReorder) {
                    if (canMoveUp) {
                        FilledTonalButton(
                            onClick = {
                                wearActionSheetOpen = false
                                onMoveUp()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Move up")
                            }
                        }
                    }
                    if (canMoveDown) {
                        FilledTonalButton(
                            onClick = {
                                wearActionSheetOpen = false
                                onMoveDown()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Move down")
                            }
                        }
                    }
                }
                if (canDelete) {
                    Button(
                        onClick = {
                            wearActionSheetOpen = false
                            onDelete()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Remove from playlist")
                        }
                    }
                }
                TextButton(
                    onClick = { wearActionSheetOpen = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    } else {
        val draggedHighlightColor by animateColorAsState(
            targetValue = if (isDragged) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
            } else {
                Color.Transparent
            },
            animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
            label = "playlistRowDraggedHighlight"
        )
        val draggedScale by animateFloatAsState(
            targetValue = if (isDragged) 1.014f else 1f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "playlistRowDraggedScale"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PLAYLISTS_DETAIL_CONTENT_GUTTER)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = draggedScale
                        scaleY = draggedScale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(draggedHighlightColor)
                    .let { base ->
                        if (editModeEnabled) {
                            base
                        } else {
                            base.clickable(onClick = onClick)
                        }
                    }
                    .padding(start = 6.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistTrackArtworkChip(
                    entry = entry,
                    isActive = isActive,
                    isWatch = false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playlistPageTrackSubtitle(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (editModeEnabled) {
                    PlaylistTrackReorderHandle(
                        reorderEnabled = canReorder,
                        isDragged = isDragged,
                        onDragStart = onDragStart,
                        onDragStep = onDragStep,
                        onDragEnd = onDragEnd
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = { menuExpanded = true }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Track options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Play",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
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
                                onPlay()
                            }
                        )
                        if (showPlayAsCachedAction && isRemoteSource) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Play as cached",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
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
                                    onPlayAsCached()
                                }
                            )
                        }
                        if (canDelete) {
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
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                        if (showLocationAction && canOpenLocalLocation) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Open location",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
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
                                    onOpenLocation()
                                }
                            )
                        }
                        if (showCopySourceAction && isRemoteSource) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Copy URL",
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
                                    menuExpanded = false
                                    onCopySource()
                                }
                            )
                        } else if (showShareAction && canOpenLocalLocation) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Share",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
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
                                    onShare()
                                }
                            )
                        }
                        if (showInfoAction) {
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
                                    menuExpanded = false
                                    onOpenInfo()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Move up",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            enabled = canReorder && canMoveUp,
                            onClick = {
                                menuExpanded = false
                                onMoveUp()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Move down",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            ),
                            enabled = canReorder && canMoveDown,
                            onClick = {
                                menuExpanded = false
                                onMoveDown()
                            }
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun PlaylistTrackReorderHandle(
    reorderEnabled: Boolean,
    isDragged: Boolean,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit
) {
    var dragSwapRemainderPx by remember { mutableFloatStateOf(0f) }
    val dragSwapThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val handleTint by animateColorAsState(
        targetValue = when {
            !reorderEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
            isDragged -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        },
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "playlistDragHandleTint"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .let { base ->
                if (reorderEnabled) {
                    base.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragSwapRemainderPx = 0f
                                onDragStart()
                            },
                            onDragEnd = {
                                dragSwapRemainderPx = 0f
                                onDragEnd()
                            },
                            onDragCancel = {
                                dragSwapRemainderPx = 0f
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragSwapRemainderPx += dragAmount.y
                                while (dragSwapRemainderPx >= dragSwapThresholdPx) {
                                    onDragStep(1)
                                    dragSwapRemainderPx -= dragSwapThresholdPx
                                }
                                while (dragSwapRemainderPx <= -dragSwapThresholdPx) {
                                    onDragStep(-1)
                                    dragSwapRemainderPx += dragSwapThresholdPx
                                }
                            }
                        )
                    }
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = handleTint,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun isRemotePlaylistSource(sourceId: String): Boolean {
    val normalized = sourceId.trim()
    if (normalized.isEmpty()) return false
    val scheme = Uri.parse(normalized).scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https" || scheme == "smb") return true
    if (scheme == "archive") {
        val parsed = parseArchiveSourceId(normalized) ?: return false
        return parseHttpSourceSpecFromInput(parsed.archivePath) != null ||
            parseSmbSourceSpecFromInput(parsed.archivePath) != null
    }
    return parseHttpSourceSpecFromInput(normalized) != null ||
        parseSmbSourceSpecFromInput(normalized) != null
}

@Composable
private fun PlaylistTrackArtworkChip(
    entry: PlaylistTrackEntry,
    isActive: Boolean,
    isWatch: Boolean = false
) {
    val context = LocalContext.current
    val fallbackIcon = placeholderArtworkIconForFile(
        file = resolvePlaylistEntryLocalFile(entry.source),
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    val artworkThumbnailCacheKey = androidx.compose.runtime.produceState<String?>(
        initialValue = entry.artworkThumbnailCacheKey,
        key1 = entry.id,
        key2 = entry.source,
        key3 = entry.artworkThumbnailCacheKey
    ) {
        if (!entry.artworkThumbnailCacheKey.isNullOrBlank()) {
            value = entry.artworkThumbnailCacheKey
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            ensureRecentArtworkThumbnailCached(
                context = context,
                sourceId = entry.source
            )
        }
    }.value
    val artwork = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = artworkThumbnailCacheKey
    ) {
        value = withContext(Dispatchers.IO) {
            val artworkFile = recentArtworkThumbnailFile(context, artworkThumbnailCacheKey)
                ?: return@withContext null
            BitmapFactory.decodeFile(artworkFile.absolutePath)?.asImageBitmap()
        }
    }.value
    val chipSize = if (isWatch) 32.dp else 46.dp
    val iconSize = if (isWatch) 18.dp else 28.dp
    Surface(
        modifier = Modifier.size(chipSize),
        shape = if (isWatch) RoundedCornerShape(10.dp) else MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(iconSize)
            )
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlaylistCollectionRow(
    playlist: StoredPlaylist,
    onClick: () -> Unit,
    isWatch: Boolean = false
) {
    PlaylistLibraryFlatRow(
        modifier = Modifier
            .fillMaxWidth(),
        title = playlist.title,
        subtitle = "${playlist.entries.size} tracks • ${playlist.format.label}",
        icon = Icons.Default.LibraryMusic,
        iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
        isWatch = isWatch
    )
}

@Composable
private fun PlaylistLibraryFlatRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    isWatch: Boolean = false
) {
    if (isWatch) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconContainerColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .clickable(onClick = onClick)
                .padding(start = 6.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = iconContainerColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun playlistPageTrackSubtitle(entry: PlaylistTrackEntry): String {
    return entry.artist?.trim()?.takeIf { it.isNotBlank() } ?: "No metadata yet"
}

private data class PlaylistTrackInfoDialogState(
    val fields: List<BrowserInfoField>
)

private fun buildPlaylistTrackInfoDialogState(
    playlistTitle: String,
    entry: PlaylistTrackEntry,
    networkNodes: List<NetworkNode> = emptyList()
): PlaylistTrackInfoDialogState {
    val sourceId = entry.source.trim()
    val localFile = resolvePlaylistEntryLocalFile(sourceId)?.takeIf { it.exists() && it.isFile }
    val archiveSource = parseArchiveSourceId(sourceId)
    val httpSource = parseHttpSourceSpecFromInput(sourceId)
    val smbSource = parseSmbSourceSpecFromInput(sourceId)
    val sourceFileName = localFile?.name
        ?: sourceLeafNameForDisplay(sourceId)
        ?: entry.title
    val sourceSizeBytes = localFile?.length()
    val storageOrHostLabel = when {
        httpSource != null || smbSource != null -> "Host"
        archiveSource != null -> "Archive"
        else -> "Storage"
    }
    val storageOrHostValue = when {
        httpSource != null -> httpSource.host
        smbSource != null -> resolveSmbDisplayHost(smbSource.host, networkNodes)
        archiveSource != null -> decodePercentEncodedForDisplay(archiveSource.archivePath) ?: archiveSource.archivePath
        localFile?.parentFile != null -> localFile.parentFile?.absolutePath.orEmpty()
        else -> sourceId
    }
    val fields = buildBrowserInfoFields(
        entries = listOf(
            BrowserInfoEntry(
                name = sourceFileName,
                isDirectory = false,
                sizeBytes = sourceSizeBytes
            )
        ),
        path = formatSourceIdForDisplay(sourceId, networkNodes),
        storageOrHostLabel = storageOrHostLabel,
        storageOrHost = storageOrHostValue
    ).toMutableList()
    fields += BrowserInfoField("Playlist", playlistTitle)
    if (entry.title.isNotBlank()) {
        fields += BrowserInfoField("Track title", entry.title)
    }
    entry.artist?.trim()?.takeIf { it.isNotBlank() }?.let { artist ->
        fields += BrowserInfoField("Artist", artist)
    }
    entry.album?.trim()?.takeIf { it.isNotBlank() }?.let { album ->
        fields += BrowserInfoField("Album", album)
    }
    entry.subtuneIndex?.let { subtuneIndex ->
        fields += BrowserInfoField("Subtune", "${subtuneIndex + 1}")
    }
    entry.durationSecondsOverride
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { seconds ->
            fields += BrowserInfoField("Duration", formatPlaylistInfoDuration(seconds))
        }
    return PlaylistTrackInfoDialogState(fields = fields)
}

private fun formatPlaylistInfoDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}

@Composable
private fun LibrarySearchOverlay(
    query: String,
    results: LibrarySearchResults,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp,
    activeSourceId: String?,
    onQueryChanged: (String) -> Unit,
    onOpenAlbum: (LibraryAlbum) -> Unit,
    onOpenArtist: (LibraryArtist) -> Unit,
    onPlayTrack: (LibraryTrackEntity, Int) -> Unit,
    trackActions: (LibraryTrackEntity) -> LibraryRowContextActions
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            placeholder = { Text("Search library") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear query"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
        )
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        if (query.isNotBlank() && results.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptySectionCard(
                    title = "No results",
                    body = "Nothing matches \"$query\" in the indexed library."
                )
            }
        } else if (query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 4.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 16.dp
                )
            ) {
                if (results.albums.isNotEmpty()) {
                    item(key = "section-albums") { LibrarySearchSectionHeader("Albums") }
                    items(
                        items = results.albums,
                        key = { "album:${it.rawName}" }
                    ) { album ->
                        AlbumLibraryListRow(
                            album = album,
                            onClick = { onOpenAlbum(album) },
                            flat = true
                        )
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item(key = "section-artists") { LibrarySearchSectionHeader("Artists") }
                    items(
                        items = results.artists,
                        key = { "artist:${it.name}" }
                    ) { artist ->
                        LibraryArtistCompactRow(
                            artist = artist,
                            onClick = { onOpenArtist(artist) },
                            flat = true
                        )
                    }
                }
                if (results.tracks.isNotEmpty()) {
                    item(key = "section-tracks") { LibrarySearchSectionHeader("Tracks") }
                    itemsIndexed(
                        items = results.tracks,
                        key = { _, track -> "track:${track.path}" }
                    ) { index, track ->
                        LibraryTrackListRow(
                            position = index + 1,
                            title = track.title,
                            subtitleArtist = track.artist,
                            durationMs = track.durationMs,
                            isActive = activeSourceId != null && activeSourceId == track.path,
                            onClick = { onPlayTrack(track, index) },
                            actions = trackActions(track)
                        )
                    }
                }
            }
        }
    }
}

private data class LibraryRowContextActions(
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val onToggleFavorite: () -> Unit,
    val onTogglePin: () -> Unit,
    val onAddToPlaylist: () -> Unit
)

private data class LibraryCollectionContextMenu(
    val noun: String,
    val onAddToFavorites: () -> Unit,
    val onPin: () -> Unit,
    val onAddToPlaylist: () -> Unit
)

@Composable
private fun LibraryItemActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: LibraryRowContextActions
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    if (actions.isFavorite) "Remove from favorites" else "Add to favorites",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (actions.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
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
                onDismiss()
                actions.onToggleFavorite()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (actions.isPinned) "Unpin from home" else "Pin to home",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PushPin,
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
                onDismiss()
                actions.onTogglePin()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to playlist…", style = MaterialTheme.typography.bodyLarge) },
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
                onDismiss()
                actions.onAddToPlaylist()
            }
        )
    }
}

@Composable
private fun LibrarySearchSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp)
    )
}
