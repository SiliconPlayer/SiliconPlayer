package com.flopster101.siliconplayer.ui.screens

import android.content.Context
import com.flopster101.siliconplayer.PlaylistEntrySortMode
import com.flopster101.siliconplayer.PlaylistSortMode
import com.flopster101.siliconplayer.formatSourceIdForDisplay
import com.flopster101.siliconplayer.moveStoredPlaylist
import com.flopster101.siliconplayer.AppPreferenceKeys
import androidx.compose.material3.Switch
import com.flopster101.siliconplayer.PlaylistCoverGenerationMode
import com.flopster101.siliconplayer.readPlaylistCoverGenerationMode
import com.flopster101.siliconplayer.sortStoredPlaylists
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.resolveSmbDisplayHost
import com.flopster101.siliconplayer.sortPlaylistEntries
import com.flopster101.siliconplayer.isRoundScreenCompat
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.flopster101.siliconplayer.exportPlaylistToUri
import com.flopster101.siliconplayer.favoritesAsStoredPlaylist
import com.flopster101.siliconplayer.sharePlaylist
import com.flopster101.siliconplayer.suggestedPlaylistExportFileName
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.flopster101.siliconplayer.parsePlaylistDocumentFromUri
import com.flopster101.siliconplayer.parsePlaylistDocument
import com.flopster101.siliconplayer.duplicateStoredPlaylist
import com.flopster101.siliconplayer.saveNormalizedPlaylistCover
import com.flopster101.siliconplayer.rotatePlaylistCoverFile
import com.flopster101.siliconplayer.ui.dialogs.ColorPickerDialog
import com.flopster101.siliconplayer.isSupportedPlaylistFile
import com.flopster101.siliconplayer.ParsedPlaylistDocument
import com.flopster101.siliconplayer.ui.dialogs.FilePickerChoiceSheet
import com.flopster101.siliconplayer.ui.dialogs.StorageFilePickerSheet
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
import com.flopster101.siliconplayer.FAVORITES_PLAYLIST_ID
import com.flopster101.siliconplayer.PlaylistMetadataRefresher
import com.flopster101.siliconplayer.isRemotePlaylistSource
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.FilterQuality
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
import com.flopster101.siliconplayer.PlaylistStoredFormat
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.ui.dialogs.AddToPlaylistChooserDialog
import com.flopster101.siliconplayer.ui.dialogs.AddTracksSourceSheet
import com.flopster101.siliconplayer.ui.dialogs.AddDirectUrlDialog
import com.flopster101.siliconplayer.ui.dialogs.AddFromLibraryPickerSheet
import com.flopster101.siliconplayer.ui.dialogs.AddFromStoragePickerSheet
import com.flopster101.siliconplayer.ui.dialogs.AddFromNetworkPickerSheet
import com.flopster101.siliconplayer.ui.dialogs.NewPlaylistDialog
import com.flopster101.siliconplayer.ui.dialogs.RenamePlaylistDialog
import com.flopster101.siliconplayer.ui.dialogs.NewFolderDialog
import com.flopster101.siliconplayer.ui.dialogs.RenameFolderDialog
import com.flopster101.siliconplayer.ui.dialogs.DeleteFolderDialog
import com.flopster101.siliconplayer.ui.dialogs.MoveToFolderDialog
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import com.flopster101.siliconplayer.PlaylistFolder
import com.flopster101.siliconplayer.createPlaylistFolder
import com.flopster101.siliconplayer.renamePlaylistFolder
import com.flopster101.siliconplayer.togglePinPlaylistFolder
import com.flopster101.siliconplayer.movePlaylistFolder
import com.flopster101.siliconplayer.movePlaylistToFolder
import com.flopster101.siliconplayer.deletePlaylistFolder
import com.flopster101.siliconplayer.resolveFolderPath
import com.flopster101.siliconplayer.getDescendantFolderIds
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.appendStoredPlaylistEntries
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.ensureRecentArtworkCached
import com.flopster101.siliconplayer.inferredDisplayTitleForName
import com.flopster101.siliconplayer.miniPlayerFabLift
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import com.flopster101.siliconplayer.playlistContainsTrack
import com.flopster101.siliconplayer.playlistEntryMatchesPlayback
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.peekCachedArtworkBitmapForSource
import com.flopster101.siliconplayer.recentArtworkThumbnailFile
import com.flopster101.siliconplayer.recentArtworkFile
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
import com.flopster101.siliconplayer.loadLibraryThumbnail
import com.flopster101.siliconplayer.peekLibraryThumbnail
import androidx.compose.ui.graphics.ImageBitmap
import com.flopster101.siliconplayer.NativeBridge
import java.io.File
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.luminance
import com.flopster101.siliconplayer.RECENT_ARTWORK_CACHE_DIR
import com.flopster101.siliconplayer.sha1Hex
import com.flopster101.siliconplayer.updateStoredPlaylistCover
import java.io.FileOutputStream

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
    val selectedPlaylistFolderIdState = mutableStateOf<String?>(null)
    val selectedTabIndexState = mutableIntStateOf(0)
    val albumCollectionLayoutState = mutableStateOf(AlbumCollectionLayout.Grid)
    val searchActiveState = mutableStateOf(false)
    val searchQueryState = mutableStateOf("")
    val searchResultsState = mutableStateOf(LibrarySearchResults("", emptyList(), emptyList(), emptyList()))
    val albumOpenedFromArtistState = mutableStateOf(false)
    val artistOpenedFromAlbumState = mutableStateOf(false)
    // Navigation history for transition direction. Tracks the page stack so a
    // forward push (list -> artist -> album) differs from a back pop.
    val destinationBackStackState = mutableStateOf(listOf(PlaylistsSurfaceDestination.Library))
    val playlistsTabListState = LazyListState()
    val albumsGridState = LazyGridState()
    val albumsListState = LazyListState()
    val artistsListState = LazyListState()
    val tracksListState = LazyListState()
    val albumDetailListState = LazyListState()
    val artistDetailListState = LazyListState()
    val artistDetailTracksListState = LazyListState()
    val artistAlbumsGridState = LazyGridState()
    val artistAlbumLayoutState = mutableStateOf(AlbumCollectionLayout.Grid)
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
    activePlaylistEntryId: String? = null,
    currentPlaybackSourceId: String?,
    currentPlaybackTitle: String? = null,
    currentPlaybackArtist: String? = null,
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
    onTogglePinStoredPlaylist: (String) -> Unit = {},
    onRenameStoredPlaylist: (String, String) -> Unit = { _, _ -> },
    onOpenBrowser: () -> Unit = {},
    onAppendStoredPlaylistEntries: (String, List<PlaylistTrackEntry>) -> Unit = { _, _ -> },
    onDeleteFavoriteTracks: (Set<String>) -> Unit = {},
    onDeleteStoredPlaylistEntries: (String, Set<String>) -> Unit = { _, _ -> },
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit = {}
) {
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val configuration = LocalConfiguration.current
    val isRound = configuration.isRoundScreenCompat || configuration.screenWidthDp == configuration.screenHeightDp

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var destination by surfaceState.destinationState
    var selectedStoredPlaylistId by surfaceState.selectedStoredPlaylistIdState
    var selectedPlaylistFolderId by surfaceState.selectedPlaylistFolderIdState
    var selectedTabIndex by surfaceState.selectedTabIndexState
    var albumCollectionLayout by surfaceState.albumCollectionLayoutState
    val librarySyncState by LibraryRepository.scanState.collectAsState()
    var librarySearchActive by surfaceState.searchActiveState
    var librarySearchQuery by surfaceState.searchQueryState
    var librarySearchResults by surfaceState.searchResultsState
    var artistContentMode by surfaceState.artistContentModeState
    var artistAlbumLayout by surfaceState.artistAlbumLayoutState
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var libraryPlaylistSortMode by remember {
        mutableStateOf(
            PlaylistSortMode.fromStorage(
                prefs.getString(AppPreferenceKeys.LIBRARY_PLAYLIST_SORT_MODE, PlaylistSortMode.RecentlyUpdated.storageValue)
            )
        )
    }
    var coverGenerationMode by remember {
        mutableStateOf(readPlaylistCoverGenerationMode(prefs))
    }
    val autoGenerateMosaics = coverGenerationMode != PlaylistCoverGenerationMode.Never
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferenceKeys.PLAYLIST_COVER_GENERATION_MODE || key == AppPreferenceKeys.PLAYLIST_AUTO_MOSAIC) {
                coverGenerationMode = readPlaylistCoverGenerationMode(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    var playlistsEditModeEnabled by rememberSaveable { mutableStateOf(false) }
    var playlistsDraggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedArtistName, destination) {
        val artistName = selectedArtistName
        if (destination == PlaylistsSurfaceDestination.ArtistDetail && artistName != null) {
            surfaceState.artistTracksState.value = LibraryRepository.artistTracks(context, artistName)
        }
    }
    var libraryContextTracks by remember { mutableStateOf<List<LibraryTrackEntity>?>(null) }
    var showAddTracksSourceSheet by remember { mutableStateOf(false) }
    var showAddFromLibrarySheet by remember { mutableStateOf(false) }
    var showAddFromStorageSheet by remember { mutableStateOf(false) }
    var showAddFromNetworkSheet by remember { mutableStateOf(false) }
    var showAddDirectUrlDialog by remember { mutableStateOf(false) }
    var duplicateTrackPromptState by remember { mutableStateOf<DuplicateTrackPromptState?>(null) }
    val currentQueuedTrack = remember(currentPlaybackSourceId, currentPlaybackTitle, currentPlaybackArtist) {
        currentPlaybackSourceId?.trim()?.takeIf { it.isNotEmpty() }?.let { source ->
            PlaylistTrackEntry(
                id = java.util.UUID.randomUUID().toString(),
                source = source,
                title = currentPlaybackTitle?.trim()?.takeIf { it.isNotEmpty() }
                    ?: inferredDisplayTitleForName(source.substringAfterLast('/')),
                artist = currentPlaybackArtist?.trim()?.takeIf { it.isNotEmpty() },
                album = null,
                subtuneIndex = currentSubtuneIndex.takeIf { it >= 0 }
            )
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val refreshPlaylistMetadataAction: (String) -> Unit = { plId ->
        coroutineScope.launch {
            Toast.makeText(context, "Refreshing metadata…", Toast.LENGTH_SHORT).show()
            val (succeeded, total) = PlaylistMetadataRefresher.refreshPlaylistTracks(
                context = context,
                playlistId = plId,
                targetEntryIds = null,
                playlistLibraryStateProvider = { libraryState },
                onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
            )
            val msg = when {
                total == 0 -> "No tracks to refresh"
                succeeded == total -> "Refreshed metadata for $total tracks"
                succeeded > 0 -> "Refreshed $succeeded of $total tracks"
                else -> "Failed to refresh metadata"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    val refreshTrackMetadataAction: (PlaylistTrackEntry) -> Unit = { entry ->
        coroutineScope.launch {
            val success = PlaylistMetadataRefresher.refreshSingleTrack(
                context = context,
                entry = entry,
                playlistLibraryState = libraryState,
                onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
            )
            val msg = if (success) "Metadata refreshed" else "Could not refresh metadata"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
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
    fun isPlaylistPinnedToHome(playlistId: String): Boolean {
        val targetPath = "playlist://$playlistId"
        return pinnedHomeEntries.any { samePath(it.path, targetPath) }
    }
    fun togglePlaylistHomePin(playlistId: String, title: String) {
        val targetPath = "playlist://$playlistId"
        if (isPlaylistPinnedToHome(playlistId)) {
            onUnpinLibraryPaths(listOf(targetPath))
        } else {
            onPinLibraryEntries(
                listOf(
                    HomePinnedEntry(
                        path = targetPath,
                        isFolder = true,
                        title = title,
                        artist = null
                    )
                )
            )
        }
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
    var favoritesSelectedEntryIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var favoritesDraggingEntryId by remember { mutableStateOf<String?>(null) }
    var showDeleteAllFavoritesConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteSelectedFavoritesConfirm by rememberSaveable { mutableStateOf(false) }
    var storedPlaylistEditModeEnabled by rememberSaveable(selectedStoredPlaylistId) {
        mutableStateOf(false)
    }
    var storedPlaylistSelectedEntryIds by rememberSaveable(selectedStoredPlaylistId) {
        mutableStateOf(setOf<String>())
    }
    var storedPlaylistDraggingEntryId by remember(selectedStoredPlaylistId) {
        mutableStateOf<String?>(null)
    }
    var showDeleteAllStoredPlaylistEntriesConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    var showDeleteSelectedStoredPlaylistEntriesConfirm by rememberSaveable {
        mutableStateOf(false)
    }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<StoredPlaylist?>(null) }
    var playlistPendingRename by remember { mutableStateOf<StoredPlaylist?>(null) }
    var playlistPendingDuplicate by remember { mutableStateOf<StoredPlaylist?>(null) }
    var playlistPendingMove by remember { mutableStateOf<StoredPlaylist?>(null) }
    var folderPendingRename by remember { mutableStateOf<PlaylistFolder?>(null) }
    var folderPendingDelete by remember { mutableStateOf<PlaylistFolder?>(null) }
    var folderPendingMove by remember { mutableStateOf<PlaylistFolder?>(null) }
    var playlistPendingCoverCustomization by remember { mutableStateOf<StoredPlaylist?>(null) }
    val coverImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { targetUri ->
        val playlist = playlistPendingCoverCustomization ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            val coversDir = File(context.filesDir, "playlist_covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val targetFile = File(coversDir, "${playlist.id}.jpg")
            val success = saveNormalizedPlaylistCover(context, targetUri, targetFile)
            if (success) {
                val updatedState = updateStoredPlaylistCover(
                    state = libraryState,
                    playlistId = playlist.id,
                    customArtworkUri = targetFile.absolutePath,
                    iconTintArgb = playlist.iconTintArgb
                )
                onPlaylistLibraryStateChanged(updatedState)
                playlistPendingCoverCustomization = updatedState.playlists.firstOrNull { it.id == playlist.id }
            } else {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var trackInfoDialogState by remember {
        mutableStateOf<PlaylistTrackInfoDialogState?>(null)
    }
    var pendingExportPlaylist by remember { mutableStateOf<StoredPlaylist?>(null) }
    val exportPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { targetUri ->
        val playlist = pendingExportPlaylist
        pendingExportPlaylist = null
        if (targetUri != null && playlist != null) {
            val success = exportPlaylistToUri(context, targetUri, playlist)
            if (success) {
                Toast.makeText(context, "Exported ${playlist.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to export playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onExportPlaylistAction: (StoredPlaylist) -> Unit = { playlist ->
        pendingExportPlaylist = playlist
        exportPlaylistLauncher.launch(suggestedPlaylistExportFileName(playlist))
    }
    val onSharePlaylistAction: (StoredPlaylist) -> Unit = { playlist ->
        sharePlaylist(context, playlist)
    }
    var playlistFabExpanded by remember { mutableStateOf(false) }
    var pendingImportPlaylistDocument by remember { mutableStateOf<ParsedPlaylistDocument?>(null) }
    var showImportPickerChoiceSheet by remember { mutableStateOf(false) }
    var showBuiltInPlaylistPicker by remember { mutableStateOf(false) }
    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { targetUri ->
        if (targetUri != null) {
            val doc = parsePlaylistDocumentFromUri(context, targetUri)
            if (doc != null && doc.entries.isNotEmpty()) {
                pendingImportPlaylistDocument = doc
            } else {
                Toast.makeText(context, "No valid tracks found in playlist", Toast.LENGTH_SHORT).show()
            }
        }
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
        playlistFabExpanded = false
    }
    LaunchedEffect(destination, selectedStoredPlaylistId, libraryState.playlists) {
        if (
            destination == PlaylistsSurfaceDestination.StoredPlaylist &&
            selectedStoredPlaylistId != null &&
            selectedStoredPlaylist == null
        ) {
            destination = PlaylistsSurfaceDestination.Library
            selectedStoredPlaylistId = null
        }
    }
    LaunchedEffect(destination) {
        playlistFabExpanded = false
        if (destination == PlaylistsSurfaceDestination.Library) {
            selectedStoredPlaylistId = null
        }
        // Sync the nav history: pop back to an already-open page, else push.
        val stack = surfaceState.destinationBackStackState.value
        val existingIndex = stack.indexOf(destination)
        surfaceState.destinationBackStackState.value = if (existingIndex >= 0) {
            stack.subList(0, existingIndex + 1)
        } else {
            stack + destination
        }
    }
    BackHandler(enabled = backHandlingEnabled && playlistFabExpanded) {
        playlistFabExpanded = false
    }
    BackHandler(enabled = backHandlingEnabled && librarySearchActive) {
        librarySearchActive = false
        librarySearchQuery = ""
        keyboardController?.hide()
        librarySearchResults = LibrarySearchResults("", emptyList(), emptyList(), emptyList())
    }
    BackHandler(
        enabled = backHandlingEnabled &&
            destination == PlaylistsSurfaceDestination.Library &&
            playlistsEditModeEnabled &&
            !playlistFabExpanded &&
            !librarySearchActive
    ) {
        playlistsEditModeEnabled = false
        playlistsDraggingId = null
    }
    BackHandler(
        enabled = backHandlingEnabled &&
            destination == PlaylistsSurfaceDestination.Library &&
            selectedPlaylistFolderId != null &&
            !playlistFabExpanded &&
            !librarySearchActive
    ) {
        val currentFolder = libraryState.folders.firstOrNull { it.id == selectedPlaylistFolderId }
        selectedPlaylistFolderId = currentFolder?.parentFolderId
    }
    BackHandler(enabled = backHandlingEnabled && showingPlaylistDetail) {
        if (destination == PlaylistsSurfaceDestination.Favorites && favoritesEditModeEnabled) {
            favoritesEditModeEnabled = false
            favoritesSelectedEntryIds = emptySet()
            favoritesDraggingEntryId = null
            return@BackHandler
        }
        if (destination == PlaylistsSurfaceDestination.StoredPlaylist && storedPlaylistEditModeEnabled) {
            storedPlaylistEditModeEnabled = false
            storedPlaylistSelectedEntryIds = emptySet()
            storedPlaylistDraggingEntryId = null
            return@BackHandler
        }
        favoritesEditModeEnabled = false
        favoritesSelectedEntryIds = emptySet()
        favoritesDraggingEntryId = null
        storedPlaylistEditModeEnabled = false
        storedPlaylistSelectedEntryIds = emptySet()
        storedPlaylistDraggingEntryId = null
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
        if (!favoritesEditModeEnabled) {
            favoritesSelectedEntryIds = emptySet()
        } else {
            val currentIds = libraryState.favorites.map { it.id }.toSet()
            if (favoritesSelectedEntryIds.any { it !in currentIds }) {
                favoritesSelectedEntryIds = favoritesSelectedEntryIds.filter { it in currentIds }.toSet()
            }
        }
    }
    LaunchedEffect(storedPlaylistEditModeEnabled, storedPlaylistSortMode, selectedStoredPlaylist?.entries) {
        val isCustomSort = storedPlaylistSortMode == PlaylistEntrySortMode.Custom
        val entries = selectedStoredPlaylist?.entries.orEmpty()
        val missingDraggedEntry = storedPlaylistDraggingEntryId != null &&
            entries.none { it.id == storedPlaylistDraggingEntryId }
        if (!storedPlaylistEditModeEnabled || !isCustomSort || missingDraggedEntry) {
            storedPlaylistDraggingEntryId = null
        }
        if (!storedPlaylistEditModeEnabled) {
            storedPlaylistSelectedEntryIds = emptySet()
        } else {
            val currentIds = entries.map { it.id }.toSet()
            if (storedPlaylistSelectedEntryIds.any { it !in currentIds }) {
                storedPlaylistSelectedEntryIds = storedPlaylistSelectedEntryIds.filter { it in currentIds }.toSet()
            }
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
                                                if (destination == PlaylistsSurfaceDestination.Favorites && favoritesEditModeEnabled) {
                                                    favoritesEditModeEnabled = false
                                                    favoritesSelectedEntryIds = emptySet()
                                                    favoritesDraggingEntryId = null
                                                    return@IconButton
                                                }
                                                if (destination == PlaylistsSurfaceDestination.StoredPlaylist && storedPlaylistEditModeEnabled) {
                                                    storedPlaylistEditModeEnabled = false
                                                    storedPlaylistSelectedEntryIds = emptySet()
                                                    storedPlaylistDraggingEntryId = null
                                                    return@IconButton
                                                }
                                                favoritesEditModeEnabled = false
                                                favoritesSelectedEntryIds = emptySet()
                                                favoritesDraggingEntryId = null
                                                storedPlaylistEditModeEnabled = false
                                                storedPlaylistSelectedEntryIds = emptySet()
                                                storedPlaylistDraggingEntryId = null
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
                                                    artistOpenedFromAlbum = false
                                                    destination = PlaylistsSurfaceDestination.Library
                                                }
                                            } else if (selectedPlaylistFolderId != null) {
                                                val currentFolder = libraryState.folders.firstOrNull { it.id == selectedPlaylistFolderId }
                                                selectedPlaylistFolderId = currentFolder?.parentFolderId
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
                    // Forward = pushing a page not already open; backward = a
                    // pop back to an earlier page (the target is already in the
                    // history at this point, since the sync effect has not run
                    // yet for the new destination).
                    val stack = surfaceState.destinationBackStackState.value
                    val forward = !stack.contains(targetState)
                    val enter = if (forward) {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
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
                    } else {
                        // Returning to a page: fade back in place; it does not
                        // slide, so its appearance is not animated.
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 210,
                                delayMillis = 40,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    }
                    // The page behind never travels: it only fades, so a back
                    // navigation does not replay that page's entrance.
                    val exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth },
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
                when (currentDestination) {
                    PlaylistsSurfaceDestination.AlbumDetail -> {
                        if (libraryAlbumDetail != null) {
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
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                    PlaylistsSurfaceDestination.ArtistDetail -> {
                        if (selectedArtistName != null && libraryArtistAlbums != null) {
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
                                albumsGridState = surfaceState.artistAlbumsGridState,
                                albumLayout = artistAlbumLayout,
                                onAlbumLayoutChanged = { artistAlbumLayout = it },
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
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                    PlaylistsSurfaceDestination.Favorites -> {
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                            favoritesSelectedEntryIds = emptySet()
                                        }
                                    },
                                    selectedEntryIds = favoritesSelectedEntryIds,
                                    onToggleSelectEntry = { entryId ->
                                        favoritesSelectedEntryIds = if (favoritesSelectedEntryIds.contains(entryId)) {
                                            favoritesSelectedEntryIds - entryId
                                        } else {
                                            favoritesSelectedEntryIds + entryId
                                        }
                                    },
                                    onSelectAllEntries = {
                                        favoritesSelectedEntryIds = sortedFavoriteEntries.map { it.id }.toSet()
                                    },
                                    onClearSelectedEntries = {
                                        favoritesSelectedEntryIds = emptySet()
                                    },
                                    onDeleteSelectedEntries = {
                                        if (favoritesSelectedEntryIds.isNotEmpty()) {
                                            showDeleteSelectedFavoritesConfirm = true
                                        }
                                    },
                                    canReorderEntries = favoritesSortMode == PlaylistEntrySortMode.Custom,
                                    draggingEntryId = favoritesDraggingEntryId,
                                    onDraggingEntryIdChange = { favoritesDraggingEntryId = it },
                                    isPlaylistActive = activePlaylist?.id == "__favorites__",
                                    activePlaylistEntryId = activePlaylistEntryId,
                                    activeSourceId = currentPlaybackSourceId,
                                    currentSubtuneIndex = currentSubtuneIndex,
                                    onEntryClick = onOpenFavorite,
                                    onPlayPlaylist = onPlayFavoritePlaylist,
                                    onShufflePlaylist = onShuffleFavoritePlaylist,
                                    onDeletePlaylist = {},
                                    canDeletePlaylist = false,
                                    onRenamePlaylist = {},
                                    canRenamePlaylist = false,
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
                                    onExportPlaylist = {
                                        onExportPlaylistAction(favoritesAsStoredPlaylist(libraryState.favorites))
                                    },
                                    onSharePlaylist = {
                                        onSharePlaylistAction(favoritesAsStoredPlaylist(libraryState.favorites))
                                    },
                                    onDuplicatePlaylist = {
                                        playlistPendingDuplicate = favoritesAsStoredPlaylist(libraryState.favorites)
                                    },
                                    isHomePinned = isPlaylistPinnedToHome(FAVORITES_PLAYLIST_ID),
                                    onToggleHomePin = {
                                        togglePlaylistHomePin(FAVORITES_PLAYLIST_ID, "Favorites")
                                    },
                                    onRefreshPlaylistMetadata = { refreshPlaylistMetadataAction(FAVORITES_PLAYLIST_ID) },
                                    onRefreshEntryMetadata = refreshTrackMetadataAction,
                                    isWatch = isWatch,
                                    autoMosaicEnabled = autoGenerateMosaics,
                                    coverGenerationMode = coverGenerationMode,
                                    onBack = {
                                        if (favoritesEditModeEnabled) {
                                            favoritesEditModeEnabled = false
                                            favoritesSelectedEntryIds = emptySet()
                                            favoritesDraggingEntryId = null
                                        } else {
                                            favoritesEditModeEnabled = false
                                            favoritesSelectedEntryIds = emptySet()
                                            favoritesDraggingEntryId = null
                                            destination = PlaylistsSurfaceDestination.Library
                                        }
                                    }
                                )
                            }
                            PlaylistSelectionFloatingBar(
                                isVisible = favoritesEditModeEnabled && favoritesSelectedEntryIds.isNotEmpty() && !isWatch,
                                selectedCount = favoritesSelectedEntryIds.size,
                                bottomPadding = bottomContentPadding,
                                onDelete = { showDeleteSelectedFavoritesConfirm = true },
                                onRefreshMetadata = {
                                    val targetIds = favoritesSelectedEntryIds
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Refreshing metadata…", Toast.LENGTH_SHORT).show()
                                        val (succeeded, total) = PlaylistMetadataRefresher.refreshPlaylistTracks(
                                            context = context,
                                            playlistId = FAVORITES_PLAYLIST_ID,
                                            targetEntryIds = targetIds,
                                            playlistLibraryStateProvider = { libraryState },
                                            onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
                                        )
                                        val msg = when {
                                            total == 0 -> "No tracks to refresh"
                                            succeeded == total -> "Refreshed metadata for $total tracks"
                                            succeeded > 0 -> "Refreshed $succeeded of $total tracks"
                                            else -> "Failed to refresh metadata"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                    PlaylistsSurfaceDestination.StoredPlaylist -> {
                        val playlist = selectedStoredPlaylist
                            ?: libraryState.playlists.firstOrNull { it.id == selectedStoredPlaylistId }
                        if (playlist != null) {
                            val sortedStoredPlaylistEntries = sortPlaylistEntries(
                                entries = playlist.entries,
                                sortMode = storedPlaylistSortMode
                            )
                            val sortedStoredPlaylist = playlist.copy(entries = sortedStoredPlaylistEntries)
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(actualInnerPadding),
                                    contentPadding = watchDetailContentPadding,
                                    verticalArrangement = if (isWatch) Arrangement.spacedBy(6.dp) else Arrangement.spacedBy(0.dp)
                                ) {
                                    playlistDetailContent(
                                        title = playlist.title,
                                        entries = sortedStoredPlaylistEntries,
                                        heroIcon = Icons.Default.LibraryMusic,
                                        emptyBody = "This playlist has no tracks.",
                                        selectedSortMode = storedPlaylistSortMode,
                                        onSortModeSelected = { storedPlaylistSortMode = it },
                                        isEditMode = storedPlaylistEditModeEnabled,
                                        onEditModeChanged = { enabled ->
                                            storedPlaylistEditModeEnabled = enabled
                                            if (!enabled) {
                                                storedPlaylistDraggingEntryId = null
                                                storedPlaylistSelectedEntryIds = emptySet()
                                            }
                                        },
                                        selectedEntryIds = storedPlaylistSelectedEntryIds,
                                        onToggleSelectEntry = { entryId ->
                                            storedPlaylistSelectedEntryIds = if (storedPlaylistSelectedEntryIds.contains(entryId)) {
                                                storedPlaylistSelectedEntryIds - entryId
                                            } else {
                                                storedPlaylistSelectedEntryIds + entryId
                                            }
                                        },
                                        onSelectAllEntries = {
                                            storedPlaylistSelectedEntryIds = sortedStoredPlaylistEntries.map { it.id }.toSet()
                                        },
                                        onClearSelectedEntries = {
                                            storedPlaylistSelectedEntryIds = emptySet()
                                        },
                                        onDeleteSelectedEntries = {
                                            if (storedPlaylistSelectedEntryIds.isNotEmpty()) {
                                                showDeleteSelectedStoredPlaylistEntriesConfirm = true
                                            }
                                        },
                                        showAddAction = true,
                                        onAddClick = { showAddTracksSourceSheet = true },
                                        showEditAction = true,
                                        showDeleteAllEntriesAction = true,
                                        canReorderEntries = storedPlaylistSortMode == PlaylistEntrySortMode.Custom,
                                        draggingEntryId = storedPlaylistDraggingEntryId,
                                        onDraggingEntryIdChange = { storedPlaylistDraggingEntryId = it },
                                        isPlaylistActive = activePlaylist?.id == playlist.id,
                                        activePlaylistEntryId = activePlaylistEntryId,
                                        activeSourceId = currentPlaybackSourceId,
                                        currentSubtuneIndex = currentSubtuneIndex,
                                        onEntryClick = { entry -> onOpenStoredPlaylistEntry(entry, sortedStoredPlaylist) },
                                        onPlayPlaylist = { onPlayStoredPlaylist(sortedStoredPlaylist) },
                                        onShufflePlaylist = { onShuffleStoredPlaylist(sortedStoredPlaylist) },
                                        onDeletePlaylist = { playlistPendingDelete = playlist },
                                        canDeletePlaylist = true,
                                        onRenamePlaylist = { playlistPendingRename = playlist },
                                        canRenamePlaylist = true,
                                        onDeleteAllEntries = { showDeleteAllStoredPlaylistEntriesConfirm = true },
                                        onPlayEntry = { entry -> onOpenStoredPlaylistEntry(entry, sortedStoredPlaylist) },
                                        onPlayEntryAsCached = { entry ->
                                            onPlayStoredPlaylistTrackAsCached(entry, sortedStoredPlaylist)
                                        },
                                        onDeleteEntry = { entry ->
                                            onDeleteStoredPlaylistEntry(entry, playlist.id)
                                        },
                                        onMoveEntry = { entry, offset ->
                                            onMoveStoredPlaylistEntry(entry, playlist.id, offset)
                                        },
                                        onOpenEntryLocation = onOpenFavoriteTrackLocation,
                                        onShareEntry = onShareFavoriteTrack,
                                        onCopyEntrySource = onCopyFavoriteTrackSource,
                                        onOpenEntryInfo = { entry ->
                                            trackInfoDialogState = buildPlaylistTrackInfoDialogState(
                                                playlistTitle = playlist.title,
                                                entry = entry,
                                                networkNodes = networkNodes
                                            )
                                        },
                                        showPlayAsCachedAction = true,
                                        showLocationAction = true,
                                        showShareAction = true,
                                        showCopySourceAction = true,
                                        showInfoAction = true,
                                        onExportPlaylist = { onExportPlaylistAction(sortedStoredPlaylist) },
                                        onSharePlaylist = { onSharePlaylistAction(sortedStoredPlaylist) },
                                        onDuplicatePlaylist = { playlistPendingDuplicate = playlist },
                                        isPlaylistPinned = playlist.isPinned,
                                        onTogglePinPlaylist = { onTogglePinStoredPlaylist(playlist.id) },
                                        isHomePinned = isPlaylistPinnedToHome(playlist.id),
                                        onToggleHomePin = { togglePlaylistHomePin(playlist.id, playlist.title) },
                                        onRefreshPlaylistMetadata = { refreshPlaylistMetadataAction(playlist.id) },
                                        onRefreshEntryMetadata = refreshTrackMetadataAction,
                                        isWatch = isWatch,
                                        customArtworkUri = playlist.customArtworkUri,
                                        iconTintArgb = playlist.iconTintArgb,
                                        autoMosaicEnabled = autoGenerateMosaics && playlist.autoGenerateCover,
                                        coverGenerationMode = coverGenerationMode,
                                        onChangeCover = { playlistPendingCoverCustomization = playlist },
                                        onBack = {
                                            if (storedPlaylistEditModeEnabled) {
                                                storedPlaylistEditModeEnabled = false
                                                storedPlaylistSelectedEntryIds = emptySet()
                                                storedPlaylistDraggingEntryId = null
                                            } else {
                                                storedPlaylistEditModeEnabled = false
                                                storedPlaylistSelectedEntryIds = emptySet()
                                                storedPlaylistDraggingEntryId = null
                                                destination = PlaylistsSurfaceDestination.Library
                                            }
                                        }
                                    )
                                }
                                PlaylistSelectionFloatingBar(
                                    isVisible = storedPlaylistEditModeEnabled && storedPlaylistSelectedEntryIds.isNotEmpty() && !isWatch,
                                    selectedCount = storedPlaylistSelectedEntryIds.size,
                                    bottomPadding = bottomContentPadding,
                                    onDelete = { showDeleteSelectedStoredPlaylistEntriesConfirm = true },
                                    onRefreshMetadata = {
                                        val targetIds = storedPlaylistSelectedEntryIds
                                        val targetPlaylistId = playlist.id
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Refreshing metadata…", Toast.LENGTH_SHORT).show()
                                            val (succeeded, total) = PlaylistMetadataRefresher.refreshPlaylistTracks(
                                                context = context,
                                                playlistId = targetPlaylistId,
                                                targetEntryIds = targetIds,
                                                playlistLibraryStateProvider = { libraryState },
                                                onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
                                            )
                                            val msg = when {
                                                total == 0 -> "No tracks to refresh"
                                                succeeded == total -> "Refreshed metadata for $total tracks"
                                                succeeded > 0 -> "Refreshed $succeeded of $total tracks"
                                                else -> "Failed to refresh metadata"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                    PlaylistsSurfaceDestination.Library -> {
                        val sortedPlaylists = remember(libraryState.playlists) {
                            libraryState.playlists.sortedWith(
                                compareByDescending<StoredPlaylist> { it.isPinned }
                                    .thenByDescending { it.updatedAtMs }
                            )
                        }
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
                                            onDuplicate = {
                                                playlistPendingDuplicate = favoritesAsStoredPlaylist(libraryState.favorites)
                                            },
                                            onExport = {
                                                onExportPlaylistAction(favoritesAsStoredPlaylist(libraryState.favorites))
                                            },
                                            onShare = {
                                                onSharePlaylistAction(favoritesAsStoredPlaylist(libraryState.favorites))
                                            },
                                            isHomePinned = isPlaylistPinnedToHome(FAVORITES_PLAYLIST_ID),
                                            onToggleHomePin = {
                                                togglePlaylistHomePin(FAVORITES_PLAYLIST_ID, "Favorites")
                                            },
                                            onRefreshMetadata = { refreshPlaylistMetadataAction(FAVORITES_PLAYLIST_ID) },
                                            isWatch = true,
                                            favorites = libraryState.favorites,
                                            autoMosaicEnabled = autoGenerateMosaics,
                                            coverGenerationMode = coverGenerationMode
                                        )
                                    }
                                    if (sortedPlaylists.isEmpty()) {
                                        item {
                                            EmptySectionCard(
                                                title = "No playlists yet",
                                                body = "Playlists will appear here."
                                            )
                                        }
                                    } else {
                                        items(
                                            items = sortedPlaylists,
                                            key = { it.id }
                                        ) { playlist ->
                                            PlaylistCollectionRow(
                                                playlist = playlist,
                                                onClick = {
                                                    selectedStoredPlaylistId = playlist.id
                                                    destination = PlaylistsSurfaceDestination.StoredPlaylist
                                                },
                                                onRename = { playlistPendingRename = playlist },
                                                onDuplicate = { playlistPendingDuplicate = playlist },
                                                onDelete = { playlistPendingDelete = playlist },
                                                onExport = { onExportPlaylistAction(playlist) },
                                                onShare = { onSharePlaylistAction(playlist) },
                                                isPinned = playlist.isPinned,
                                                onTogglePin = { onTogglePinStoredPlaylist(playlist.id) },
                                                isHomePinned = isPlaylistPinnedToHome(playlist.id),
                                                onToggleHomePin = { togglePlaylistHomePin(playlist.id, playlist.title) },
                                                onRefreshMetadata = { refreshPlaylistMetadataAction(playlist.id) },
                                                autoMosaicEnabled = autoGenerateMosaics && playlist.autoGenerateCover,
                                                coverGenerationMode = coverGenerationMode,
                                                onChangeCover = { playlistPendingCoverCustomization = playlist },
                                                isPlaying = activePlaylist?.id != null && activePlaylist.id == playlist.id,
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
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                when (libraryTabs[page]) {
                                    LibrarySurfaceTab.Playlists -> {
                                           PlaylistsLibraryTabPage(
                                              libraryState = libraryState,
                                              currentFolderId = selectedPlaylistFolderId,
                                              bottomContentPadding = bottomContentPadding,
                                              listState = surfaceState.playlistsTabListState,
                                              sortMode = libraryPlaylistSortMode,
                                              onSortModeSelected = { mode ->
                                                  libraryPlaylistSortMode = mode
                                                  prefs.edit().putString(AppPreferenceKeys.LIBRARY_PLAYLIST_SORT_MODE, mode.storageValue).apply()
                                                  if (mode != PlaylistSortMode.Custom) {
                                                      playlistsEditModeEnabled = false
                                                  }
                                              },
                                              editModeEnabled = playlistsEditModeEnabled,
                                              onEditModeEnabledChange = { enabled ->
                                                  playlistsEditModeEnabled = enabled
                                                  if (!enabled) playlistsDraggingId = null
                                              },
                                              draggingPlaylistId = playlistsDraggingId,
                                              onDraggingPlaylistIdChange = { playlistsDraggingId = it },
                                              onMoveStoredPlaylist = { playlist, offset ->
                                                  onPlaylistLibraryStateChanged(
                                                      moveStoredPlaylist(
                                                          state = libraryState,
                                                          playlistId = playlist.id,
                                                          offset = offset,
                                                          targetFolderId = selectedPlaylistFolderId
                                                      )
                                                  )
                                              },
                                              onOpenFavorites = { destination = PlaylistsSurfaceDestination.Favorites },
                                             onOpenPlaylist = { playlist ->
                                                 selectedStoredPlaylistId = playlist.id
                                                 destination = PlaylistsSurfaceDestination.StoredPlaylist
                                             },
                                             onOpenFolder = { folder ->
                                                 selectedPlaylistFolderId = folder.id
                                             },
                                             onNavigateToFolder = { folderId ->
                                                 selectedPlaylistFolderId = folderId
                                             },
                                             onRenameFolder = { folder -> folderPendingRename = folder },
                                             onDeleteFolder = { folder -> folderPendingDelete = folder },
                                             onMoveFolder = { folder -> folderPendingMove = folder },
                                             onTogglePinFolder = { folder ->
                                                 onPlaylistLibraryStateChanged(togglePinPlaylistFolder(libraryState, folder.id))
                                             },
                                             onRenamePlaylist = { playlist -> playlistPendingRename = playlist },
                                             onDuplicatePlaylist = { playlist -> playlistPendingDuplicate = playlist },
                                             onDeletePlaylist = { playlist -> playlistPendingDelete = playlist },
                                             onMovePlaylist = { playlist -> playlistPendingMove = playlist },
                                             onExportPlaylist = onExportPlaylistAction,
                                             onSharePlaylist = onSharePlaylistAction,
                                             onTogglePinPlaylist = { playlist -> onTogglePinStoredPlaylist(playlist.id) },
                                             isPlaylistHomePinned = { playlistId -> isPlaylistPinnedToHome(playlistId) },
                                             onTogglePlaylistHomePin = { playlistId, title -> togglePlaylistHomePin(playlistId, title) },
                                             onRefreshPlaylistMetadata = refreshPlaylistMetadataAction,
                                             autoMosaicEnabled = autoGenerateMosaics,
                                             coverGenerationMode = coverGenerationMode,
                                             onChangePlaylistCover = { playlist -> playlistPendingCoverCustomization = playlist },
                                             activePlaylistId = activePlaylist?.id
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
                            if (!isWatch && playlistFabExpanded &&
                                currentDestination == PlaylistsSurfaceDestination.Library &&
                                !librarySearchActive &&
                                libraryTabs.getOrNull(selectedTabIndex) == LibrarySurfaceTab.Playlists
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { playlistFabExpanded = false }
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isWatch &&
                                    currentDestination == PlaylistsSurfaceDestination.Library &&
                                    !librarySearchActive &&
                                    libraryTabs.getOrNull(selectedTabIndex) == LibrarySurfaceTab.Playlists,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(
                                        end = 16.dp,
                                        bottom = 16.dp + miniPlayerFabLift(bottomContentPadding)
                                    )
                                ) {
                                    val item1Progress by animateFloatAsState(
                                        targetValue = if (playlistFabExpanded) 1f else 0f,
                                        animationSpec = if (playlistFabExpanded) {
                                            tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        } else {
                                            tween(durationMillis = 120, easing = FastOutLinearInEasing)
                                        },
                                        label = "item1_progress"
                                    )
                                    val item2Progress by animateFloatAsState(
                                        targetValue = if (playlistFabExpanded) 1f else 0f,
                                        animationSpec = if (playlistFabExpanded) {
                                            tween(durationMillis = 220, delayMillis = 35, easing = FastOutSlowInEasing)
                                        } else {
                                            tween(durationMillis = 100, easing = FastOutLinearInEasing)
                                        },
                                        label = "item2_progress"
                                    )
                                    val item3Progress by animateFloatAsState(
                                        targetValue = if (playlistFabExpanded) 1f else 0f,
                                        animationSpec = if (playlistFabExpanded) {
                                            tween(durationMillis = 260, delayMillis = 70, easing = FastOutSlowInEasing)
                                        } else {
                                            tween(durationMillis = 80, easing = FastOutLinearInEasing)
                                        },
                                        label = "item3_progress"
                                    )

                                    if (item3Progress > 0f) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = playlistFabExpanded
                                            ) {
                                                playlistFabExpanded = false
                                                showImportPickerChoiceSheet = true
                                            }
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                tonalElevation = 3.dp,
                                                shadowElevation = 3.dp,
                                                modifier = Modifier.graphicsLayer {
                                                    alpha = item3Progress
                                                    translationX = (1f - item3Progress) * 16.dp.toPx()
                                                }
                                            ) {
                                                Text(
                                                    text = "Import playlist",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Box(
                                                modifier = Modifier.width(56.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SmallFloatingActionButton(
                                                    onClick = {
                                                        playlistFabExpanded = false
                                                        showImportPickerChoiceSheet = true
                                                    },
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.graphicsLayer {
                                                        alpha = item3Progress
                                                        scaleX = item3Progress
                                                        scaleY = item3Progress
                                                        translationY = (1f - item3Progress) * 24.dp.toPx()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FileOpen,
                                                        contentDescription = "Import playlist"
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (item2Progress > 0f) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = playlistFabExpanded
                                            ) {
                                                playlistFabExpanded = false
                                                showCreateFolderDialog = true
                                            }
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                tonalElevation = 3.dp,
                                                shadowElevation = 3.dp,
                                                modifier = Modifier.graphicsLayer {
                                                    alpha = item2Progress
                                                    translationX = (1f - item2Progress) * 16.dp.toPx()
                                                }
                                            ) {
                                                Text(
                                                    text = "New folder",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Box(
                                                modifier = Modifier.width(56.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SmallFloatingActionButton(
                                                    onClick = {
                                                        playlistFabExpanded = false
                                                        showCreateFolderDialog = true
                                                    },
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.graphicsLayer {
                                                        alpha = item2Progress
                                                        scaleX = item2Progress
                                                        scaleY = item2Progress
                                                        translationY = (1f - item2Progress) * 24.dp.toPx()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CreateNewFolder,
                                                        contentDescription = "New folder"
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (item1Progress > 0f) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = playlistFabExpanded
                                            ) {
                                                playlistFabExpanded = false
                                                showCreatePlaylistDialog = true
                                            }
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                tonalElevation = 3.dp,
                                                shadowElevation = 3.dp,
                                                modifier = Modifier.graphicsLayer {
                                                    alpha = item1Progress
                                                    translationX = (1f - item1Progress) * 16.dp.toPx()
                                                }
                                            ) {
                                                Text(
                                                    text = "Create playlist",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Box(
                                                modifier = Modifier.width(56.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SmallFloatingActionButton(
                                                    onClick = {
                                                        playlistFabExpanded = false
                                                        showCreatePlaylistDialog = true
                                                    },
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.graphicsLayer {
                                                        alpha = item1Progress
                                                        scaleX = item1Progress
                                                        scaleY = item1Progress
                                                        translationY = (1f - item1Progress) * 24.dp.toPx()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "Create playlist"
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    FloatingActionButton(
                                        onClick = { playlistFabExpanded = !playlistFabExpanded },
                                        containerColor = if (playlistFabExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (playlistFabExpanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        val rotation by animateFloatAsState(
                                            targetValue = if (playlistFabExpanded) 45f else 0f,
                                            label = "fab_rotation"
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = if (playlistFabExpanded) "Close" else "Add playlist",
                                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
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
    if (showDeleteAllStoredPlaylistEntriesConfirm) {
        if (isWatch) {
            WatchDialogContainer(
                title = "Clear playlist?",
                onDismissRequest = { showDeleteAllStoredPlaylistEntriesConfirm = false }
            ) {
                Text(
                    text = "This will remove every track from this playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        showDeleteAllStoredPlaylistEntriesConfirm = false
                        selectedStoredPlaylistId?.let { onDeleteAllStoredPlaylistEntries(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Clear all")
                }
                TextButton(
                    onClick = { showDeleteAllStoredPlaylistEntriesConfirm = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteAllStoredPlaylistEntriesConfirm = false },
                title = { Text("Clear playlist?") },
                text = { Text("This will remove every track from this playlist.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAllStoredPlaylistEntriesConfirm = false
                            selectedStoredPlaylistId?.let { onDeleteAllStoredPlaylistEntries(it) }
                        }
                    ) {
                        Text("Clear all")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllStoredPlaylistEntriesConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    if (showDeleteSelectedFavoritesConfirm) {
        val count = favoritesSelectedEntryIds.size
        if (isWatch) {
            WatchDialogContainer(
                title = "Remove tracks?",
                onDismissRequest = { showDeleteSelectedFavoritesConfirm = false }
            ) {
                Text(
                    text = "Remove $count ${if (count == 1) "track" else "tracks"} from Favorites?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        showDeleteSelectedFavoritesConfirm = false
                        val toDelete = favoritesSelectedEntryIds.toSet()
                        favoritesSelectedEntryIds = emptySet()
                        onDeleteFavoriteTracks(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Remove")
                }
                TextButton(
                    onClick = { showDeleteSelectedFavoritesConfirm = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedFavoritesConfirm = false },
                title = { Text(if (count == 1) "Remove track from Favorites?" else "Remove $count tracks from Favorites?") },
                text = { Text("Are you sure you want to remove ${if (count == 1) "this track" else "the selected tracks"} from Favorites?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteSelectedFavoritesConfirm = false
                            val toDelete = favoritesSelectedEntryIds.toSet()
                            favoritesSelectedEntryIds = emptySet()
                            onDeleteFavoriteTracks(toDelete)
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedFavoritesConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    if (showDeleteSelectedStoredPlaylistEntriesConfirm) {
        val count = storedPlaylistSelectedEntryIds.size
        if (isWatch) {
            WatchDialogContainer(
                title = "Remove tracks?",
                onDismissRequest = { showDeleteSelectedStoredPlaylistEntriesConfirm = false }
            ) {
                Text(
                    text = "Remove $count ${if (count == 1) "track" else "tracks"} from this playlist?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        showDeleteSelectedStoredPlaylistEntriesConfirm = false
                        val toDelete = storedPlaylistSelectedEntryIds.toSet()
                        storedPlaylistSelectedEntryIds = emptySet()
                        selectedStoredPlaylistId?.let { playlistId ->
                            onDeleteStoredPlaylistEntries(playlistId, toDelete)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Remove")
                }
                TextButton(
                    onClick = { showDeleteSelectedStoredPlaylistEntriesConfirm = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedStoredPlaylistEntriesConfirm = false },
                title = { Text(if (count == 1) "Remove track from playlist?" else "Remove $count tracks from playlist?") },
                text = { Text("Are you sure you want to remove ${if (count == 1) "this track" else "the selected tracks"} from this playlist?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteSelectedStoredPlaylistEntriesConfirm = false
                            val toDelete = storedPlaylistSelectedEntryIds.toSet()
                            storedPlaylistSelectedEntryIds = emptySet()
                            selectedStoredPlaylistId?.let { playlistId ->
                                onDeleteStoredPlaylistEntries(playlistId, toDelete)
                            }
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedStoredPlaylistEntriesConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    duplicateTrackPromptState?.let { prompt ->
        val isSingle = prompt.totalCount == 1
        val isAllDuplicates = prompt.duplicateCount == prompt.totalCount
        val dialogTitle = if (isSingle || isAllDuplicates) "Already in playlist" else "Duplicate tracks"
        val dialogBody = when {
            isSingle -> "\"${prompt.firstDuplicateTitle ?: "This track"}\" is already in this playlist. Do you want to add it again?"
            isAllDuplicates -> "All ${prompt.duplicateCount} selected tracks are already in this playlist. Do you want to add them again?"
            else -> "${prompt.duplicateCount} of the ${prompt.totalCount} selected tracks are already in this playlist. Do you want to add them anyway, or skip duplicates?"
        }
        if (isWatch) {
            WatchDialogContainer(
                title = dialogTitle,
                onDismissRequest = { duplicateTrackPromptState = null }
            ) {
                Text(
                    text = dialogBody,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        val action = prompt.onAddAnyway
                        duplicateTrackPromptState = null
                        action()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Add anyway")
                }
                if (prompt.onSkipDuplicates != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = {
                            val action = prompt.onSkipDuplicates
                            duplicateTrackPromptState = null
                            action()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Skip duplicates")
                    }
                }
                TextButton(
                    onClick = { duplicateTrackPromptState = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { duplicateTrackPromptState = null },
                title = { Text(dialogTitle) },
                text = { Text(dialogBody) },
                confirmButton = {
                    Button(
                        onClick = {
                            val action = prompt.onAddAnyway
                            duplicateTrackPromptState = null
                            action()
                        }
                    ) {
                        Text("Add anyway")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { duplicateTrackPromptState = null }) {
                            Text("Cancel")
                        }
                        if (prompt.onSkipDuplicates != null) {
                            FilledTonalButton(
                                onClick = {
                                    val action = prompt.onSkipDuplicates
                                    duplicateTrackPromptState = null
                                    action()
                                }
                            ) {
                                Text("Skip duplicates")
                            }
                        }
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
    if (showCreatePlaylistDialog) {
        NewPlaylistDialog(
            existingTitles = remember(libraryState.playlists) {
                libraryState.playlists.map { it.title }.toSet()
            },
            onConfirm = { title ->
                val playlistId = onCreatePlaylist(title)
                selectedPlaylistFolderId?.let { folderId ->
                    onPlaylistLibraryStateChanged(
                        movePlaylistToFolder(
                            if (libraryState.playlists.none { it.id == playlistId }) {
                                libraryState.copy(
                                    playlists = listOf(
                                        StoredPlaylist(
                                            id = playlistId,
                                            title = title,
                                            format = PlaylistStoredFormat.Internal,
                                            sourceIdHint = null,
                                            entries = emptyList(),
                                            updatedAtMs = System.currentTimeMillis()
                                        )
                                    ) + libraryState.playlists
                                )
                            } else libraryState,
                            playlistId,
                            folderId
                        )
                    )
                }
                showCreatePlaylistDialog = false
                selectedStoredPlaylistId = playlistId
                destination = PlaylistsSurfaceDestination.StoredPlaylist
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }
    if (showImportPickerChoiceSheet) {
        FilePickerChoiceSheet(
            title = "Import playlist",
            subtitle = "Choose how to browse for playlist files",
            onSelectSaf = {
                showImportPickerChoiceSheet = false
                importPlaylistLauncher.launch(arrayOf("*/*"))
            },
            onSelectBuiltIn = {
                showImportPickerChoiceSheet = false
                showBuiltInPlaylistPicker = true
            },
            onDismiss = { showImportPickerChoiceSheet = false }
        )
    }
    if (showBuiltInPlaylistPicker) {
        StorageFilePickerSheet(
            title = "Import playlist",
            singleSelect = true,
            fileFilter = { isSupportedPlaylistFile(it) },
            fileIcon = Icons.Default.LibraryMusic,
            emptyText = "No folders or playlist files (.m3u, .m3u8)",
            onConfirmFiles = { files ->
                showBuiltInPlaylistPicker = false
                val file = files.firstOrNull() ?: return@StorageFilePickerSheet
                val doc = parsePlaylistDocument(file, allowUnresolvedFiles = true)
                if (doc != null && doc.entries.isNotEmpty()) {
                    pendingImportPlaylistDocument = doc
                } else {
                    Toast.makeText(context, "No valid tracks found in playlist", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showBuiltInPlaylistPicker = false }
        )
    }
    pendingImportPlaylistDocument?.let { doc ->
        NewPlaylistDialog(
            existingTitles = remember(libraryState.playlists) {
                libraryState.playlists.map { it.title }.toSet()
            },
            initialTitle = doc.title,
            onConfirm = { title ->
                val playlistId = onCreatePlaylist(title)
                onAppendStoredPlaylistEntries(playlistId, doc.entries)
                selectedPlaylistFolderId?.let { folderId ->
                    onPlaylistLibraryStateChanged(
                        movePlaylistToFolder(
                            if (libraryState.playlists.none { it.id == playlistId }) {
                                libraryState.copy(
                                    playlists = listOf(
                                        StoredPlaylist(
                                            id = playlistId,
                                            title = title,
                                            format = PlaylistStoredFormat.Internal,
                                            sourceIdHint = null,
                                            entries = emptyList(),
                                            updatedAtMs = System.currentTimeMillis()
                                        )
                                    ) + libraryState.playlists
                                )
                            } else libraryState,
                            playlistId,
                            folderId
                        )
                    )
                }
                pendingImportPlaylistDocument = null
                selectedStoredPlaylistId = playlistId
                destination = PlaylistsSurfaceDestination.StoredPlaylist
                Toast.makeText(
                    context,
                    "Imported ${doc.entries.size} tracks into $title",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { pendingImportPlaylistDocument = null }
        )
    }
    playlistPendingDelete?.let { playlist ->
        if (isWatch) {
            WatchDialogContainer(
                title = "Delete playlist?",
                onDismissRequest = { playlistPendingDelete = null }
            ) {
                Text(
                    text = "Are you sure you want to delete \"${playlist.title}\"?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        val id = playlist.id
                        playlistPendingDelete = null
                        if (selectedStoredPlaylistId == id) {
                            selectedStoredPlaylistId = null
                            destination = PlaylistsSurfaceDestination.Library
                        }
                        onDeleteStoredPlaylist(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = { playlistPendingDelete = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            FloatingActionDialog(
                title = "Delete playlist?",
                onDismiss = { playlistPendingDelete = null },
                confirmText = "Delete",
                confirmIsDestructive = true,
                onConfirm = {
                    val id = playlist.id
                    playlistPendingDelete = null
                    if (selectedStoredPlaylistId == id) {
                        selectedStoredPlaylistId = null
                        destination = PlaylistsSurfaceDestination.Library
                    }
                    onDeleteStoredPlaylist(id)
                }
            ) {
                Text(
                    text = "Are you sure you want to delete \"${playlist.title}\"? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    playlistPendingRename?.let { playlist ->
        RenamePlaylistDialog(
            currentTitle = playlist.title,
            onConfirm = { newTitle ->
                val id = playlist.id
                playlistPendingRename = null
                onRenameStoredPlaylist(id, newTitle)
            },
            onDismiss = { playlistPendingRename = null }
        )
    }
    playlistPendingDuplicate?.let { playlist ->
        val copyCandidate = remember(playlist.title, libraryState.playlists) {
            val base = "${playlist.title} (Copy)"
            val titles = libraryState.playlists.map { it.title }.toSet()
            var candidate = base
            var suffix = 2
            while (candidate in titles) {
                candidate = "$base $suffix"
                suffix += 1
            }
            candidate
        }
        NewPlaylistDialog(
            existingTitles = remember(libraryState.playlists) {
                libraryState.playlists.map { it.title }.toSet()
            },
            dialogTitle = "Duplicate playlist",
            confirmText = "Duplicate",
            initialTitle = copyCandidate,
            onConfirm = { title ->
                val duplicated = duplicateStoredPlaylist(playlist, title)
                val playlistId = onCreatePlaylist(duplicated.title)
                if (duplicated.entries.isNotEmpty()) {
                    onAppendStoredPlaylistEntries(playlistId, duplicated.entries)
                }
                duplicated.folderId?.let { folderId ->
                    onPlaylistLibraryStateChanged(
                        movePlaylistToFolder(
                            if (libraryState.playlists.none { it.id == playlistId }) {
                                libraryState.copy(
                                    playlists = listOf(
                                        StoredPlaylist(
                                            id = playlistId,
                                            title = duplicated.title,
                                            format = duplicated.format,
                                            sourceIdHint = duplicated.sourceIdHint,
                                            entries = duplicated.entries,
                                            updatedAtMs = System.currentTimeMillis()
                                        )
                                    ) + libraryState.playlists
                                )
                            } else libraryState,
                            playlistId,
                            folderId
                        )
                    )
                }
                playlistPendingDuplicate = null
                selectedStoredPlaylistId = playlistId
                destination = PlaylistsSurfaceDestination.StoredPlaylist
            },
            onDismiss = { playlistPendingDuplicate = null }
        )
    }
    if (showCreateFolderDialog) {
        NewFolderDialog(
            existingTitles = remember(libraryState.folders, selectedPlaylistFolderId) {
                libraryState.folders
                    .filter { it.parentFolderId == selectedPlaylistFolderId }
                    .map { it.title }
                    .toSet()
            },
            onConfirm = { name ->
                showCreateFolderDialog = false
                val (updatedState, _) = createPlaylistFolder(libraryState, name, selectedPlaylistFolderId)
                onPlaylistLibraryStateChanged(updatedState)
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
    folderPendingRename?.let { folder ->
        RenameFolderDialog(
            currentTitle = folder.title,
            onConfirm = { newName ->
                val targetId = folder.id
                folderPendingRename = null
                val updatedState = renamePlaylistFolder(libraryState, targetId, newName)
                onPlaylistLibraryStateChanged(updatedState)
            },
            onDismiss = { folderPendingRename = null }
        )
    }
    folderPendingDelete?.let { folder ->
        val childPlaylistsCount = remember(libraryState.playlists, libraryState.folders, folder.id) {
            val descendantFolderIds = getDescendantFolderIds(libraryState.folders, folder.id)
            libraryState.playlists.count { it.folderId in descendantFolderIds }
        }
        DeleteFolderDialog(
            folderTitle = folder.title,
            hasContents = childPlaylistsCount > 0,
            onConfirm = { deletePlaylists ->
                val targetId = folder.id
                folderPendingDelete = null
                if (selectedPlaylistFolderId == targetId ||
                    (selectedPlaylistFolderId != null &&
                        targetId in resolveFolderPath(libraryState.folders, selectedPlaylistFolderId).map { it.id })
                ) {
                    selectedPlaylistFolderId = folder.parentFolderId
                }
                val updatedState = deletePlaylistFolder(libraryState, targetId, deletePlaylists)
                onPlaylistLibraryStateChanged(updatedState)
            },
            onDismiss = { folderPendingDelete = null }
        )
    }
    playlistPendingMove?.let { playlist ->
        MoveToFolderDialog(
            itemTitle = playlist.title,
            currentFolderId = playlist.folderId,
            allFolders = libraryState.folders,
            onSelectFolder = { targetFolderId ->
                val playlistId = playlist.id
                playlistPendingMove = null
                val updatedState = movePlaylistToFolder(libraryState, playlistId, targetFolderId)
                onPlaylistLibraryStateChanged(updatedState)
            },
            onDismiss = { playlistPendingMove = null }
        )
    }
    folderPendingMove?.let { folder ->
        val disallowed = remember(folder.id, libraryState.folders) {
            getDescendantFolderIds(libraryState.folders, folder.id) + folder.id
        }
        MoveToFolderDialog(
            itemTitle = folder.title,
            currentFolderId = folder.parentFolderId,
            allFolders = libraryState.folders,
            disallowedFolderIds = disallowed,
            onSelectFolder = { targetFolderId ->
                val folderId = folder.id
                folderPendingMove = null
                val updatedState = movePlaylistFolder(libraryState, folderId, targetFolderId)
                onPlaylistLibraryStateChanged(updatedState)
            },
            onDismiss = { folderPendingMove = null }
        )
    }
    playlistPendingCoverCustomization?.let { playlist ->
        PlaylistCoverCustomizerDialog(
            playlist = playlist,
            onDismissRequest = { playlistPendingCoverCustomization = null },
            onPickImage = {
                coverImagePickerLauncher.launch(arrayOf("image/*"))
            },
            onRotateImage = {
                if (!playlist.customArtworkUri.isNullOrBlank()) {
                    val file = File(playlist.customArtworkUri)
                    if (rotatePlaylistCoverFile(file, 90f)) {
                        val updated = updateStoredPlaylistCover(
                            state = libraryState,
                            playlistId = playlist.id,
                            customArtworkUri = playlist.customArtworkUri,
                            iconTintArgb = playlist.iconTintArgb
                        )
                        onPlaylistLibraryStateChanged(updated)
                        playlistPendingCoverCustomization = updated.playlists.firstOrNull { it.id == playlist.id }
                    }
                }
            },
            onRemoveImage = {
                if (!playlist.customArtworkUri.isNullOrBlank()) {
                    try {
                        File(playlist.customArtworkUri).delete()
                    } catch (_: Throwable) {}
                }
                val updated = updateStoredPlaylistCover(
                    state = libraryState,
                    playlistId = playlist.id,
                    customArtworkUri = null,
                    iconTintArgb = playlist.iconTintArgb
                )
                onPlaylistLibraryStateChanged(updated)
                playlistPendingCoverCustomization = updated.playlists.firstOrNull { it.id == playlist.id }
            },
            onSelectTint = { tintArgb ->
                val updated = updateStoredPlaylistCover(
                    state = libraryState,
                    playlistId = playlist.id,
                    customArtworkUri = playlist.customArtworkUri,
                    iconTintArgb = tintArgb,
                    autoGenerateCover = playlist.autoGenerateCover
                )
                onPlaylistLibraryStateChanged(updated)
                playlistPendingCoverCustomization = updated.playlists.firstOrNull { it.id == playlist.id }
            },
            onToggleAutoGenerateCover = { enabled ->
                val updated = updateStoredPlaylistCover(
                    state = libraryState,
                    playlistId = playlist.id,
                    customArtworkUri = playlist.customArtworkUri,
                    iconTintArgb = playlist.iconTintArgb,
                    autoGenerateCover = enabled
                )
                onPlaylistLibraryStateChanged(updated)
                playlistPendingCoverCustomization = updated.playlists.firstOrNull { it.id == playlist.id }
            },
            autoMosaicEnabled = autoGenerateMosaics,
            coverGenerationMode = coverGenerationMode,
            isWatch = isWatch
        )
    }
    libraryContextTracks?.let { contextTracks ->
        AddToPlaylistChooserDialog(
            playlists = libraryState.playlists,
            favorites = libraryState.favorites,
            pendingSources = contextTracks.map { it.path }.toSet(),
            onConfirm = { playlistId, newTitle ->
                if (playlistId == FAVORITES_PLAYLIST_ID) {
                    onAddLibraryTracksToFavorites(contextTracks)
                } else {
                    onAddLibraryTracksToPlaylist(contextTracks, playlistId, newTitle)
                }
            },
            onRemoveFromPlaylist = { playlistId ->
                if (playlistId == FAVORITES_PLAYLIST_ID) {
                    onRemoveLibraryTracksFromFavorites(contextTracks)
                } else {
                    contextTracks.singleOrNull()?.let { track ->
                        onRemoveSourceFromPlaylist(track.path, playlistId)
                    }
                }
            },
            onDismiss = { libraryContextTracks = null }
        )
    }
    if (showAddTracksSourceSheet && selectedStoredPlaylist != null) {
        val targetPlaylist = selectedStoredPlaylist
        val targetPlaylistId = targetPlaylist.id
        AddTracksSourceSheet(
            playlistTitle = targetPlaylist.title,
            currentTrack = currentQueuedTrack,
            onAddCurrentTrack = {
                currentQueuedTrack?.let { track ->
                    val isDuplicate = playlistContainsTrack(
                        entries = targetPlaylist.entries,
                        source = track.source,
                        subtuneIndex = track.subtuneIndex
                    )
                    val addAction = {
                        val entryToAdd = track.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            addedAtMs = System.currentTimeMillis()
                        )
                        onAppendStoredPlaylistEntries(targetPlaylistId, listOf(entryToAdd))
                        Toast.makeText(context, "Added to ${targetPlaylist.title}", Toast.LENGTH_SHORT).show()
                    }
                    if (isDuplicate) {
                        duplicateTrackPromptState = DuplicateTrackPromptState(
                            playlistTitle = targetPlaylist.title,
                            duplicateCount = 1,
                            firstDuplicateTitle = track.title.ifBlank { "This track" },
                            totalCount = 1,
                            onAddAnyway = addAction
                        )
                    } else {
                        addAction()
                    }
                }
            },
            onSelectLibrary = { showAddFromLibrarySheet = true },
            onSelectStorage = { showAddFromStorageSheet = true },
            onSelectNetwork = { showAddFromNetworkSheet = true },
            onSelectDirectUrl = { showAddDirectUrlDialog = true },
            onDismiss = { showAddTracksSourceSheet = false }
        )
    }
    if (showAddFromLibrarySheet && selectedStoredPlaylist != null) {
        val targetPlaylist = selectedStoredPlaylist
        val targetPlaylistId = targetPlaylist.id
        AddFromLibraryPickerSheet(
            onConfirm = { selectedTracks ->
                showAddFromLibrarySheet = false
                val duplicates = selectedTracks.filter { track ->
                    playlistContainsTrack(targetPlaylist.entries, track.path)
                }
                val addAllAction = {
                    onAddLibraryTracksToPlaylist(selectedTracks, targetPlaylistId, "")
                }
                if (duplicates.isNotEmpty()) {
                    val nonDuplicates = selectedTracks.filterNot { track ->
                        playlistContainsTrack(targetPlaylist.entries, track.path)
                    }
                    duplicateTrackPromptState = DuplicateTrackPromptState(
                        playlistTitle = targetPlaylist.title,
                        duplicateCount = duplicates.size,
                        firstDuplicateTitle = duplicates.firstOrNull()?.title?.ifBlank {
                            duplicates.firstOrNull()?.path?.substringAfterLast('/')
                        },
                        totalCount = selectedTracks.size,
                        onAddAnyway = addAllAction,
                        onSkipDuplicates = if (nonDuplicates.isNotEmpty()) {
                            { onAddLibraryTracksToPlaylist(nonDuplicates, targetPlaylistId, "") }
                        } else null
                    )
                } else {
                    addAllAction()
                }
            },
            onDismiss = { showAddFromLibrarySheet = false }
        )
    }
    if (showAddFromStorageSheet && selectedStoredPlaylist != null) {
        val targetPlaylist = selectedStoredPlaylist
        val targetPlaylistId = targetPlaylist.id
        AddFromStoragePickerSheet(
            onConfirm = { selectedFiles ->
                showAddFromStorageSheet = false
                val newEntries = selectedFiles.map { file ->
                    PlaylistTrackEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        source = file.absolutePath,
                        title = inferredDisplayTitleForName(file.name),
                        addedAtMs = System.currentTimeMillis()
                    )
                }
                val duplicates = newEntries.filter { entry ->
                    playlistContainsTrack(targetPlaylist.entries, entry.source)
                }
                val addAllAction = {
                    onAppendStoredPlaylistEntries(targetPlaylistId, newEntries)
                }
                if (duplicates.isNotEmpty()) {
                    val nonDuplicates = newEntries.filterNot { entry ->
                        playlistContainsTrack(targetPlaylist.entries, entry.source)
                    }
                    duplicateTrackPromptState = DuplicateTrackPromptState(
                        playlistTitle = targetPlaylist.title,
                        duplicateCount = duplicates.size,
                        firstDuplicateTitle = duplicates.firstOrNull()?.title,
                        totalCount = newEntries.size,
                        onAddAnyway = addAllAction,
                        onSkipDuplicates = if (nonDuplicates.isNotEmpty()) {
                            { onAppendStoredPlaylistEntries(targetPlaylistId, nonDuplicates) }
                        } else null
                    )
                } else {
                    addAllAction()
                }
            },
            onDismiss = { showAddFromStorageSheet = false }
        )
    }
    if (showAddFromNetworkSheet && selectedStoredPlaylist != null) {
        val targetPlaylist = selectedStoredPlaylist
        val targetPlaylistId = targetPlaylist.id
        AddFromNetworkPickerSheet(
            networkNodes = networkNodes,
            onConfirm = { selectedTracks ->
                showAddFromNetworkSheet = false
                val duplicates = selectedTracks.filter { entry ->
                    playlistContainsTrack(targetPlaylist.entries, entry.source, entry.subtuneIndex)
                }
                val addAllAction = {
                    onAppendStoredPlaylistEntries(targetPlaylistId, selectedTracks)
                }
                if (duplicates.isNotEmpty()) {
                    val nonDuplicates = selectedTracks.filterNot { entry ->
                        playlistContainsTrack(targetPlaylist.entries, entry.source, entry.subtuneIndex)
                    }
                    duplicateTrackPromptState = DuplicateTrackPromptState(
                        playlistTitle = targetPlaylist.title,
                        duplicateCount = duplicates.size,
                        firstDuplicateTitle = duplicates.firstOrNull()?.title,
                        totalCount = selectedTracks.size,
                        onAddAnyway = addAllAction,
                        onSkipDuplicates = if (nonDuplicates.isNotEmpty()) {
                            { onAppendStoredPlaylistEntries(targetPlaylistId, nonDuplicates) }
                        } else null
                    )
                } else {
                    addAllAction()
                }
            },
            onDismiss = { showAddFromNetworkSheet = false }
        )
    }
    if (showAddDirectUrlDialog && selectedStoredPlaylist != null) {
        val targetPlaylist = selectedStoredPlaylist
        val targetPlaylistId = targetPlaylist.id
        AddDirectUrlDialog(
            onConfirm = { url, title, artist ->
                showAddDirectUrlDialog = false
                val entryTitle = title ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Network Stream" }
                val newEntry = PlaylistTrackEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    source = url,
                    title = entryTitle,
                    artist = artist,
                    addedAtMs = System.currentTimeMillis()
                )
                val isDuplicate = playlistContainsTrack(targetPlaylist.entries, url)
                val addAction = {
                    onAppendStoredPlaylistEntries(targetPlaylistId, listOf(newEntry))
                }
                if (isDuplicate) {
                    duplicateTrackPromptState = DuplicateTrackPromptState(
                        playlistTitle = targetPlaylist.title,
                        duplicateCount = 1,
                        firstDuplicateTitle = entryTitle,
                        totalCount = 1,
                        onAddAnyway = addAction
                    )
                } else {
                    addAction()
                }
            },
            onDismiss = { showAddDirectUrlDialog = false }
        )
    }
}

@Composable
private fun PlaylistsLibraryTabPage(
    libraryState: PlaylistLibraryState,
    currentFolderId: String? = null,
    bottomContentPadding: Dp,
    listState: LazyListState,
    sortMode: PlaylistSortMode = PlaylistSortMode.RecentlyUpdated,
    onSortModeSelected: (PlaylistSortMode) -> Unit = {},
    editModeEnabled: Boolean = false,
    onEditModeEnabledChange: (Boolean) -> Unit = {},
    draggingPlaylistId: String? = null,
    onDraggingPlaylistIdChange: (String?) -> Unit = {},
    onMoveStoredPlaylist: (StoredPlaylist, Int) -> Unit = { _, _ -> },
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (StoredPlaylist) -> Unit,
    onOpenFolder: (PlaylistFolder) -> Unit = {},
    onNavigateToFolder: (String?) -> Unit = {},
    onRenameFolder: (PlaylistFolder) -> Unit = {},
    onDeleteFolder: (PlaylistFolder) -> Unit = {},
    onMoveFolder: (PlaylistFolder) -> Unit = {},
    onTogglePinFolder: (PlaylistFolder) -> Unit = {},
    onRenamePlaylist: (StoredPlaylist) -> Unit = {},
    onDuplicatePlaylist: (StoredPlaylist) -> Unit = {},
    onDeletePlaylist: (StoredPlaylist) -> Unit = {},
    onMovePlaylist: (StoredPlaylist) -> Unit = {},
    onExportPlaylist: (StoredPlaylist) -> Unit = {},
    onSharePlaylist: (StoredPlaylist) -> Unit = {},
    onTogglePinPlaylist: (StoredPlaylist) -> Unit = {},
    isPlaylistHomePinned: (String) -> Boolean = { false },
    onTogglePlaylistHomePin: (String, String) -> Unit = { _, _ -> },
    onRefreshPlaylistMetadata: ((String) -> Unit)? = null,
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    onChangePlaylistCover: ((StoredPlaylist) -> Unit)? = null,
    activePlaylistId: String? = null,
    isWatch: Boolean = false
) {
    val currentFolders = remember(libraryState.folders, currentFolderId) {
        libraryState.folders
            .filter { it.parentFolderId == currentFolderId }
            .sortedWith(
                compareByDescending<PlaylistFolder> { it.isPinned }
                    .thenBy { it.title.lowercase() }
            )
    }
    val currentPlaylists = remember(libraryState.playlists, currentFolderId, sortMode) {
        val scoped = libraryState.playlists.filter { it.folderId == currentFolderId }
        val pinned = scoped.filter { it.isPinned }
        val unpinned = scoped.filter { !it.isPinned }
        val sortedPinned = if (sortMode == PlaylistSortMode.Custom) pinned else sortStoredPlaylists(pinned, sortMode)
        val sortedUnpinned = sortStoredPlaylists(unpinned, sortMode)
        sortedPinned + sortedUnpinned
    }
    val folderPath = remember(libraryState.folders, currentFolderId) {
        resolveFolderPath(libraryState.folders, currentFolderId)
    }
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
        if (currentFolderId == null) {
            item {
                FavoritesCollectionRow(
                    favoriteCount = libraryState.favorites.size,
                    onClick = onOpenFavorites,
                    onDuplicate = { onDuplicatePlaylist(favoritesAsStoredPlaylist(libraryState.favorites)) },
                    onExport = { onExportPlaylist(favoritesAsStoredPlaylist(libraryState.favorites)) },
                    onShare = { onSharePlaylist(favoritesAsStoredPlaylist(libraryState.favorites)) },
                    isHomePinned = isPlaylistHomePinned(FAVORITES_PLAYLIST_ID),
                    onToggleHomePin = { onTogglePlaylistHomePin(FAVORITES_PLAYLIST_ID, "Favorites") },
                    onRefreshMetadata = onRefreshPlaylistMetadata?.let { { it(FAVORITES_PLAYLIST_ID) } },
                    favorites = libraryState.favorites,
                    autoMosaicEnabled = autoMosaicEnabled,
                    coverGenerationMode = coverGenerationMode,
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
        } else {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onNavigateToFolder(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All Playlists", style = MaterialTheme.typography.labelMedium)
                        }
                        for (folder in folderPath) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val isCurrent = folder.id == currentFolderId
                            TextButton(
                                onClick = { if (!isCurrent) onNavigateToFolder(folder.id) },
                                enabled = !isCurrent,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = folder.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!isWatch && (currentFolders.isNotEmpty() || currentPlaylists.isNotEmpty())) {
            item {
                PlaylistsLibraryHeaderRow(
                    playlistCount = currentPlaylists.size,
                    folderCount = currentFolders.size,
                    sortMode = sortMode,
                    onSortModeSelected = onSortModeSelected,
                    editModeEnabled = editModeEnabled,
                    onEditModeEnabledChange = onEditModeEnabledChange,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
        if (currentFolders.isEmpty() && currentPlaylists.isEmpty()) {
            item {
                if (!isWatch) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                EmptySectionCard(
                    title = if (currentFolderId == null) "No playlists yet" else "Empty folder",
                    body = if (currentFolderId == null) "Playlists you create will show up here." else "Add playlists or subfolders here using the + button."
                )
            }
        } else {
            items(
                items = currentFolders,
                key = { "folder_${it.id}" }
            ) { folder ->
                FolderCollectionRow(
                    folder = folder,
                    playlistCount = libraryState.playlists.count { it.folderId == folder.id },
                    subfolderCount = libraryState.folders.count { it.parentFolderId == folder.id },
                    onClick = { onOpenFolder(folder) },
                    onRename = { onRenameFolder(folder) },
                    onDelete = { onDeleteFolder(folder) },
                    onMove = { onMoveFolder(folder) },
                    isPinned = folder.isPinned,
                    onTogglePin = { onTogglePinFolder(folder) },
                    isWatch = isWatch
                )
                if (!isWatch) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
            items(
                items = currentPlaylists,
                key = { "playlist_${it.id}" }
            ) { playlist ->
                PlaylistCollectionRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) },
                    onRename = { onRenamePlaylist(playlist) },
                    onDuplicate = { onDuplicatePlaylist(playlist) },
                    onDelete = { onDeletePlaylist(playlist) },
                    onExport = { onExportPlaylist(playlist) },
                    onShare = { onSharePlaylist(playlist) },
                    onMoveToFolder = { onMovePlaylist(playlist) },
                    isPinned = playlist.isPinned,
                    onTogglePin = { onTogglePinPlaylist(playlist) },
                    isHomePinned = isPlaylistHomePinned(playlist.id),
                    onToggleHomePin = { onTogglePlaylistHomePin(playlist.id, playlist.title) },
                    onRefreshMetadata = onRefreshPlaylistMetadata?.let { { it(playlist.id) } },
                    reorderEnabled = editModeEnabled && sortMode == PlaylistSortMode.Custom,
                    isDragged = draggingPlaylistId == playlist.id,
                    onDragStart = { onDraggingPlaylistIdChange(playlist.id) },
                    onDragStep = { direction ->
                        if (direction > 0) {
                            onMoveStoredPlaylist(playlist, 1)
                        } else if (direction < 0) {
                            onMoveStoredPlaylist(playlist, -1)
                        }
                    },
                    onDragEnd = { onDraggingPlaylistIdChange(null) },
                    autoMosaicEnabled = autoMosaicEnabled && playlist.autoGenerateCover,
                    coverGenerationMode = coverGenerationMode,
                    onChangeCover = onChangePlaylistCover?.let { { it(playlist) } },
                    isPlaying = activePlaylistId != null && activePlaylistId == playlist.id,
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
private fun PlaylistsLibraryHeaderRow(
    playlistCount: Int,
    folderCount: Int,
    sortMode: PlaylistSortMode,
    onSortModeSelected: (PlaylistSortMode) -> Unit,
    editModeEnabled: Boolean,
    onEditModeEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val countText = remember(playlistCount, folderCount) {
        val parts = mutableListOf<String>()
        if (folderCount > 0) {
            parts += if (folderCount == 1) "1 folder" else "$folderCount folders"
        }
        if (playlistCount > 0) {
            parts += if (playlistCount == 1) "1 playlist" else "$playlistCount playlists"
        }
        if (parts.isEmpty()) "0 playlists" else parts.joinToString(" • ")
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editModeEnabled) {
                PlaylistActionPill(
                    label = "Done",
                    icon = Icons.Default.Check,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onEditModeEnabledChange(false) }
                )
            } else if (sortMode == PlaylistSortMode.Custom && playlistCount > 1) {
                PlaylistActionPill(
                    label = "Reorder",
                    icon = Icons.Default.DragIndicator,
                    onClick = { onEditModeEnabledChange(true) }
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
                    PlaylistSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = if (mode == sortMode) {
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

private fun libraryTrackCountLabel(count: Int): String =
    if (count == 1) "1 track" else "$count tracks"

private fun libraryAlbumCountLabel(count: Int): String =
    if (count == 1) "1 album" else "$count albums"

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
            val filesLabel = if (syncState.scannedFiles == 1) "1 file checked" else "${syncState.scannedFiles} files checked"
            val tracksLabel = if (syncState.indexedTracks == 1) "1 track indexed" else "${syncState.indexedTracks} tracks indexed"
            Text(
                text = "$filesLabel · $tracksLabel",
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
                    text = if (album.artist.isBlank()) {
                        libraryTrackCountLabel(album.trackCount)
                    } else {
                        "${album.artist} · ${libraryTrackCountLabel(album.trackCount)}"
                    },
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
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AlbumArtworkBox(artworkPath = artworkPath)
                }
                if (isActive) {
                    PlaylistPlayingBadge(
                        isWatch = false,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        } else if (isActive) {
            // No artwork: the track number slot carries the playing badge,
            // centered like the (now centered) track number.
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                PlaylistPlayingBadge(isWatch = false, cornerOffset = false)
            }
        } else {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
private fun ArtistAlbumYearSectionHeader(
    year: Int,
    albumCount: Int,
    topPadding: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                top = topPadding,
                end = 12.dp,
                bottom = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (year > 0) year.toString() else "Unknown year",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = libraryAlbumCountLabel(albumCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
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
    albumsGridState: LazyGridState,
    albumLayout: AlbumCollectionLayout,
    onAlbumLayoutChanged: (AlbumCollectionLayout) -> Unit,
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
                text = "${libraryAlbumCountLabel(albums.size)} · ${libraryTrackCountLabel(totalTracks)}",
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
                    val yearGroups = remember(albums) {
                        albums.sortedByDescending { it.year }.groupBy { it.year }.entries.toList()
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 4.dp,
                                end = 16.dp
                            )
                        ) {
                            AlbumLayoutToggleRow(
                                layout = albumLayout,
                                onLayoutChanged = onAlbumLayoutChanged
                            )
                        }
                        if (albumLayout == AlbumCollectionLayout.Grid) {
                            LazyVerticalGrid(
                                state = albumsGridState,
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
                                yearGroups.forEachIndexed { groupIndex, (year, yearAlbums) ->
                                    item(
                                        key = "artist-year-grid:$year",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        ArtistAlbumYearSectionHeader(
                                            year = year,
                                            albumCount = yearAlbums.size,
                                            topPadding = if (groupIndex > 0) 12.dp else 0.dp
                                        )
                                    }
                                    gridItems(
                                        yearAlbums,
                                        key = { "grid:${it.name}|${it.artist}" }
                                    ) { album ->
                                        AlbumLibraryGridCard(
                                            album = album,
                                            onClick = { onOpenAlbum(album) }
                                        )
                                    }
                                }
                            }
                        } else {
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
                        yearGroups.forEachIndexed { groupIndex, (year, yearAlbums) ->
                            item(key = "artist-year:$year") {
                                ArtistAlbumYearSectionHeader(
                                    year = year,
                                    albumCount = yearAlbums.size,
                                    topPadding = if (groupIndex > 0) 12.dp else 4.dp
                                )
                            }
                            items(
                                items = yearAlbums,
                                key = { "${it.name}|${it.artist}" }
                            ) { album ->
                                AlbumLibraryListRow(album = album, onClick = { onOpenAlbum(album) })
                            }
                        }
                    }
                    }
                }
                }
                ArtistContentMode.Tracks -> {
                    val albumGroups = remember(tracks) { tracks.groupBy { it.album }.entries.toList() }
                    val groupOffsets = remember(albumGroups) {
                        albumGroups.runningFold(0) { offset, group -> offset + group.value.size }
                    }
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
                            albumGroups.forEachIndexed { groupIndex, (groupKey, groupTracks) ->
                                val groupAlbum = albums.find { it.rawName == groupKey }
                                val groupName = groupKey.ifBlank { LibraryContract.UNKNOWN_ALBUM }
                                item(key = "artist-album:$groupKey") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(
                                                enabled = groupAlbum != null,
                                                onClick = { groupAlbum?.let(onOpenAlbum) }
                                            )
                                            .padding(
                                                start = 12.dp,
                                                top = if (groupIndex > 0) 12.dp else 4.dp,
                                                end = 12.dp,
                                                bottom = 4.dp
                                            ),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = groupName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        val groupYear = groupAlbum?.year ?: 0
                                        Text(
                                            text = buildString {
                                                if (groupYear > 0) append("$groupYear · ")
                                                append(libraryTrackCountLabel(groupTracks.size))
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        if (groupAlbum != null) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Open album",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(
                                    items = groupTracks,
                                    key = { _, track -> track.path }
                                ) { index, track ->
                                    LibraryTrackListRow(
                                        position = if (track.trackNo > 0) track.trackNo else index + 1,
                                        title = track.title,
                                        subtitleArtist = track.artist,
                                        durationMs = track.durationMs,
                                        isActive = activeSourceId != null && activeSourceId == track.path,
                                        onClick = {
                                            onPlayTracks(
                                                tracks,
                                                groupOffsets[groupIndex] + index,
                                                artist
                                            )
                                        },
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
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (album.artist.isBlank()) {
                    libraryTrackCountLabel(album.trackCount)
                } else {
                    "${album.artist} · ${libraryTrackCountLabel(album.trackCount)}"
                },
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
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${libraryAlbumCountLabel(artist.albumCount)} · ${libraryTrackCountLabel(artist.trackCount)}",
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
    val context = LocalContext.current
    var bitmap by remember(artworkPath) {
        mutableStateOf(peekLibraryThumbnail(artworkPath))
    }
    LaunchedEffect(artworkPath) {
        if (bitmap != null) return@LaunchedEffect
        val path = artworkPath ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            loadLibraryThumbnail(context, path)
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
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (album.artist.isBlank()) {
                    libraryTrackCountLabel(album.trackCount)
                } else {
                    "${album.artist} · ${libraryTrackCountLabel(album.trackCount)}"
                },
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
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${libraryAlbumCountLabel(artist.albumCount)} · ${libraryTrackCountLabel(artist.trackCount)}",
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
                shape = RoundedCornerShape(12.dp),
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
    selectedEntryIds: Set<String> = emptySet(),
    onToggleSelectEntry: (String) -> Unit = {},
    onSelectAllEntries: () -> Unit = {},
    onClearSelectedEntries: () -> Unit = {},
    onDeleteSelectedEntries: () -> Unit = {},
    showAddAction: Boolean = true,
    onAddClick: () -> Unit = {},
    showEditAction: Boolean = true,
    showDeleteAllEntriesAction: Boolean = true,
    canReorderEntries: Boolean,
    draggingEntryId: String?,
    onDraggingEntryIdChange: (String?) -> Unit,
    isPlaylistActive: Boolean = false,
    activePlaylistEntryId: String? = null,
    activeSourceId: String?,
    currentSubtuneIndex: Int,
    onEntryClick: (PlaylistTrackEntry) -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onRenamePlaylist: () -> Unit = {},
    canRenamePlaylist: Boolean = false,
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
    onExportPlaylist: (() -> Unit)? = null,
    onSharePlaylist: (() -> Unit)? = null,
    onDuplicatePlaylist: (() -> Unit)? = null,
    isPlaylistPinned: Boolean = false,
    onTogglePinPlaylist: (() -> Unit)? = null,
    isHomePinned: Boolean = false,
    onToggleHomePin: (() -> Unit)? = null,
    onRefreshPlaylistMetadata: (() -> Unit)? = null,
    onRefreshEntryMetadata: ((PlaylistTrackEntry) -> Unit)? = null,
    isWatch: Boolean = false,
    customArtworkUri: String? = null,
    iconTintArgb: Long? = null,
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    onChangeCover: (() -> Unit)? = null,
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
                onRenamePlaylist = onRenamePlaylist,
                canRenamePlaylist = canRenamePlaylist,
                onDuplicatePlaylist = onDuplicatePlaylist,
                onDeleteAllEntries = onDeleteAllEntries,
                onExportPlaylist = onExportPlaylist,
                onSharePlaylist = onSharePlaylist,
                isPlaylistPinned = isPlaylistPinned,
                onTogglePinPlaylist = onTogglePinPlaylist,
                isHomePinned = isHomePinned,
                onToggleHomePin = onToggleHomePin,
                onRefreshPlaylistMetadata = onRefreshPlaylistMetadata,
                customArtworkUri = customArtworkUri,
                iconTintArgb = iconTintArgb,
                autoMosaicEnabled = autoMosaicEnabled,
                coverGenerationMode = coverGenerationMode,
                onChangeCover = onChangeCover,
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
                onAddClick = onAddClick,
                showEditAction = showEditAction,
                showDeleteAllEntriesAction = showDeleteAllEntriesAction,
                onPlayPlaylist = onPlayPlaylist,
                onShufflePlaylist = onShufflePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                canDeletePlaylist = canDeletePlaylist,
                onRenamePlaylist = onRenamePlaylist,
                canRenamePlaylist = canRenamePlaylist,
                onDuplicatePlaylist = onDuplicatePlaylist,
                onDeleteAllEntries = onDeleteAllEntries,
                onExportPlaylist = onExportPlaylist,
                onSharePlaylist = onSharePlaylist,
                isPlaylistPinned = isPlaylistPinned,
                onTogglePinPlaylist = onTogglePinPlaylist,
                isHomePinned = isHomePinned,
                onToggleHomePin = onToggleHomePin,
                onRefreshPlaylistMetadata = onRefreshPlaylistMetadata,
                selectedEntryIds = selectedEntryIds,
                onSelectAll = onSelectAllEntries,
                onClearSelection = onClearSelectedEntries,
                onDeleteSelected = onDeleteSelectedEntries,
                customArtworkUri = customArtworkUri,
                iconTintArgb = iconTintArgb,
                autoMosaicEnabled = autoMosaicEnabled,
                coverGenerationMode = coverGenerationMode,
                onChangeCover = onChangeCover
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
                if (showAddAction) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add tracks")
                    }
                }
            }
        }
    } else {
        itemsIndexed(
            items = entries,
            key = { _, entry -> entry.id }
        ) { index, entry ->
            val isActive = isPlaylistActive && !activeSourceId.isNullOrBlank() && (
                if (!activePlaylistEntryId.isNullOrBlank()) {
                    entry.id == activePlaylistEntryId
                } else {
                    playlistEntryMatchesPlayback(
                        entry = entry,
                        activeSourceId = activeSourceId,
                        currentSubtuneIndex = currentSubtuneIndex
                    )
                }
            )
            val canMoveUp = index > 0
            val canMoveDown = index < entries.lastIndex
            PlaylistTrackRow(
                entry = entry,
                isActive = isActive,
                isDragged = draggingEntryId == entry.id,
                editModeEnabled = isEditMode,
                isSelected = selectedEntryIds.contains(entry.id),
                onToggleSelect = { onToggleSelectEntry(entry.id) },
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
                onRefreshMetadata = { onRefreshEntryMetadata?.invoke(entry) },
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
    onRenamePlaylist: () -> Unit = {},
    canRenamePlaylist: Boolean = false,
    onDuplicatePlaylist: (() -> Unit)? = null,
    onDeleteAllEntries: () -> Unit,
    onExportPlaylist: (() -> Unit)? = null,
    onSharePlaylist: (() -> Unit)? = null,
    isPlaylistPinned: Boolean = false,
    onTogglePinPlaylist: (() -> Unit)? = null,
    isHomePinned: Boolean = false,
    onToggleHomePin: (() -> Unit)? = null,
    onRefreshPlaylistMetadata: (() -> Unit)? = null,
    onBack: () -> Unit,
    customArtworkUri: String? = null,
    iconTintArgb: Long? = null,
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    onChangeCover: (() -> Unit)? = null
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
            customArtworkUri = customArtworkUri,
            iconTintArgb = iconTintArgb,
            heroIcon = heroIcon,
            modifier = Modifier.size(48.dp),
            iconSize = 24.dp,
            autoMosaicEnabled = autoMosaicEnabled,
            coverGenerationMode = coverGenerationMode
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onPlayPlaylist,
                modifier = Modifier.size(42.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play all",
                    modifier = Modifier.size(22.dp)
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
            if (canDeletePlaylist || canRenamePlaylist || showDeleteAllEntriesAction || onExportPlaylist != null || onSharePlaylist != null || onTogglePinPlaylist != null || onToggleHomePin != null || onChangeCover != null) {
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
            if (onTogglePinPlaylist != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onTogglePinPlaylist()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (isPlaylistPinned) "Unpin from top" else "Pin to top")
                }
            }
            if (onToggleHomePin != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onToggleHomePin()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (isHomePinned) "Unpin from home" else "Pin to home")
                }
            }
            if (canRenamePlaylist) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onRenamePlaylist()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Rename playlist")
                }
            }
            if (onExportPlaylist != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onExportPlaylist()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Export to file")
                }
            }
            if (onSharePlaylist != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onSharePlaylist()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Share playlist")
                }
            }
            if (onDuplicatePlaylist != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onDuplicatePlaylist()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Duplicate playlist")
                }
            }
            if (onRefreshPlaylistMetadata != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onRefreshPlaylistMetadata()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Refresh metadata")
                }
            }
            if (onChangeCover != null) {
                FilledTonalButton(
                    onClick = {
                        showMoreActionsDialog = false
                        onChangeCover()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Change cover…")
                }
            }
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
    onAddClick: () -> Unit = {},
    showEditAction: Boolean,
    showDeleteAllEntriesAction: Boolean,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    canDeletePlaylist: Boolean,
    onRenamePlaylist: () -> Unit = {},
    canRenamePlaylist: Boolean = false,
    onDeleteAllEntries: () -> Unit,
    onExportPlaylist: (() -> Unit)? = null,
    onSharePlaylist: (() -> Unit)? = null,
    onDuplicatePlaylist: (() -> Unit)? = null,
    isPlaylistPinned: Boolean = false,
    onTogglePinPlaylist: (() -> Unit)? = null,
    isHomePinned: Boolean = false,
    onToggleHomePin: (() -> Unit)? = null,
    onRefreshPlaylistMetadata: (() -> Unit)? = null,
    selectedEntryIds: Set<String> = emptySet(),
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    customArtworkUri: String? = null,
    iconTintArgb: Long? = null,
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    onChangeCover: (() -> Unit)? = null
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
            customArtworkUri = customArtworkUri,
            iconTintArgb = iconTintArgb,
            heroIcon = heroIcon,
            modifier = Modifier.size(220.dp),
            iconSize = 68.dp,
            autoMosaicEnabled = autoMosaicEnabled,
            coverGenerationMode = coverGenerationMode,
            isLarge = true
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
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
                    if (onChangeCover != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Change cover…",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Palette,
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
                                onChangeCover()
                            }
                        )
                    }
                    if (onRefreshPlaylistMetadata != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Refresh metadata",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
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
                                onRefreshPlaylistMetadata()
                            }
                        )
                    }
                    if (onTogglePinPlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isPlaylistPinned) "Unpin from top" else "Pin to top",
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
                                onTogglePinPlaylist()
                            }
                        )
                    }
                    if (onToggleHomePin != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isHomePinned) "Unpin from home" else "Pin to home",
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
                                onToggleHomePin()
                            }
                        )
                    }
                    if (canRenamePlaylist) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Rename playlist",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
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
                                onRenamePlaylist()
                            }
                        )
                    }
                    if (onExportPlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Export to file\u2026",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Save,
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
                                onExportPlaylist()
                            }
                        )
                    }
                    if (onSharePlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Share playlist\u2026",
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
                                onSharePlaylist()
                            }
                        )
                    }
                    if (onDuplicatePlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Duplicate playlist",
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
                                onDuplicatePlaylist()
                            }
                        )
                    }
                    if (canDeletePlaylist) {
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
                    }
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
            if (isEditMode) {
                PlaylistActionPill(
                    label = "Done",
                    icon = Icons.Default.Check,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onEditModeChanged(false) }
                )
                if (entries.isNotEmpty()) {
                    val allSelected = selectedEntryIds.size == entries.size
                    PlaylistActionPill(
                        label = if (allSelected) "Deselect all" else "Select all",
                        icon = if (allSelected) Icons.Default.Close else Icons.Default.SelectAll,
                        onClick = {
                            if (allSelected) onClearSelection() else onSelectAll()
                        }
                    )
                    PlaylistActionPill(
                        label = if (selectedEntryIds.isNotEmpty()) "Remove (${selectedEntryIds.size})" else "Remove",
                        icon = Icons.Default.Delete,
                        enabled = selectedEntryIds.isNotEmpty(),
                        containerColor = if (selectedEntryIds.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selectedEntryIds.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        onClick = onDeleteSelected
                    )
                }
            } else {
                if (showAddAction) {
                    PlaylistActionPill(
                        label = "Add",
                        icon = Icons.Default.Add,
                        onClick = onAddClick
                    )
                }
                if (showEditAction) {
                    PlaylistActionPill(
                        label = "Edit",
                        icon = Icons.Default.Edit,
                        onClick = { onEditModeChanged(true) }
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
}

@Composable
private fun PlaylistActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val pillShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .clip(pillShape)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        color = containerColor,
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
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PlaylistSelectionFloatingBar(
    isVisible: Boolean,
    selectedCount: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onDelete: () -> Unit,
    onRefreshMetadata: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.padding(bottom = bottomPadding + 16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCount == 1) "1 track selected" else "$selectedCount tracks selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onRefreshMetadata != null) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onRefreshMetadata,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove")
                }
            }
        }
    }
}

internal val PLAYLIST_COVER_TINT_PALETTE: List<Long?> = listOf(
    null,
    0xFFE53935L,
    0xFFF4511EL,
    0xFFFB8C00L,
    0xFFFFB300L,
    0xFF7CB342L,
    0xFF43A047L,
    0xFF00897BL,
    0xFF00ACC1L,
    0xFF1E88E5L,
    0xFF5E35B1L,
    0xFF8E24AAL,
    0xFFD81B60L,
    0xFF546E7AL
)

@Composable
private fun PlaylistCoverTintSwatch(
    tintArgb: Long?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val swatchColor = tintArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.5.dp else 1.dp
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (tintArgb == null) {
            Icon(
                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Palette,
                contentDescription = "Default tint",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        } else if (isSelected) {
            val iconTint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PlaylistCoverCustomTintSwatch(
    customArgb: Long?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFE53935),
                Color(0xFFFFB300),
                Color(0xFF43A047),
                Color(0xFF00ACC1),
                Color(0xFF1E88E5),
                Color(0xFF8E24AA),
                Color(0xFFE53935)
            )
        )
    }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.5.dp else 1.dp
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(rainbowBrush)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected && customArgb != null) {
            val customColor = Color(customArgb)
            val iconTint = if (customColor.luminance() > 0.5f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(customColor)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Custom tint selected",
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Custom tint",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
internal fun PlaylistCoverCustomizerDialog(
    playlist: StoredPlaylist,
    onDismissRequest: () -> Unit,
    onPickImage: () -> Unit,
    onRotateImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSelectTint: (Long?) -> Unit,
    onToggleAutoGenerateCover: (Boolean) -> Unit = {},
    autoMosaicEnabled: Boolean,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    isWatch: Boolean = false
) {
    var showCustomColorPicker by remember { mutableStateOf(false) }

    if (isWatch) {
        WatchDialogContainer(
            title = "Playlist cover",
            onDismissRequest = onDismissRequest
        ) {
            FilledTonalButton(
                onClick = {
                    onDismissRequest()
                    onPickImage()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (playlist.customArtworkUri != null) "Change image" else "Choose image")
            }
            if (playlist.customArtworkUri != null) {
                FilledTonalButton(
                    onClick = onRotateImage,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Rotate image")
                }
                FilledTonalButton(
                    onClick = onRemoveImage,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Remove image")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleAutoGenerateCover(!playlist.autoGenerateCover) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Generate cover",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = playlist.autoGenerateCover,
                    onCheckedChange = onToggleAutoGenerateCover
                )
            }
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
        return
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Playlist cover",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlaylistCoverArt(
                    entries = playlist.entries,
                    customArtworkUri = playlist.customArtworkUri,
                    iconTintArgb = playlist.iconTintArgb,
                    modifier = Modifier.size(130.dp),
                    shape = RoundedCornerShape(16.dp),
                    iconSize = 48.dp,
                    autoMosaicEnabled = autoMosaicEnabled && playlist.autoGenerateCover,
                    coverGenerationMode = coverGenerationMode,
                    coverRevision = playlist.updatedAtMs,
                    isLarge = true
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPickImage,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Image")
                    }
                    if (playlist.customArtworkUri != null) {
                        OutlinedIconButton(
                            onClick = onRotateImage,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate image",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        OutlinedIconButton(
                            onClick = onRemoveImage,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove image",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleAutoGenerateCover(!playlist.autoGenerateCover) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-generate cover",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Generate cover artwork from playlist tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = playlist.autoGenerateCover,
                        onCheckedChange = onToggleAutoGenerateCover
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Icon tint",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val row1 = remember { PLAYLIST_COVER_TINT_PALETTE.take(5) }
                    val row2 = remember { PLAYLIST_COVER_TINT_PALETTE.slice(5 until 10) }
                    val row3 = remember { PLAYLIST_COVER_TINT_PALETTE.drop(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row1.forEach { tintValue ->
                            PlaylistCoverTintSwatch(
                                tintArgb = tintValue,
                                isSelected = playlist.iconTintArgb == tintValue,
                                onClick = { onSelectTint(tintValue) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row2.forEach { tintValue ->
                            PlaylistCoverTintSwatch(
                                tintArgb = tintValue,
                                isSelected = playlist.iconTintArgb == tintValue,
                                onClick = { onSelectTint(tintValue) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row3.forEach { tintValue ->
                            PlaylistCoverTintSwatch(
                                tintArgb = tintValue,
                                isSelected = playlist.iconTintArgb == tintValue,
                                onClick = { onSelectTint(tintValue) }
                            )
                        }
                        val isCustomSelected = playlist.iconTintArgb != null && playlist.iconTintArgb !in PLAYLIST_COVER_TINT_PALETTE
                        PlaylistCoverCustomTintSwatch(
                            customArgb = if (isCustomSelected) playlist.iconTintArgb else null,
                            isSelected = isCustomSelected,
                            onClick = { showCustomColorPicker = true }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done")
            }
        }
    )

    if (showCustomColorPicker) {
        val initialInt = playlist.iconTintArgb?.toInt() ?: 0xFF4A5FBE.toInt()
        ColorPickerDialog(
            title = "Custom icon tint",
            initialArgb = initialInt,
            onDismiss = { showCustomColorPicker = false },
            onConfirm = { chosenArgb ->
                showCustomColorPicker = false
                val chosenLong = chosenArgb.toLong() and 0xFFFFFFFFL
                onSelectTint(chosenLong)
            }
        )
    }
}

@Composable
internal fun PlaylistCoverArt(
    entries: List<PlaylistTrackEntry>,
    customArtworkUri: String? = null,
    iconTintArgb: Long? = null,
    heroIcon: ImageVector? = Icons.Default.LibraryMusic,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    iconSize: Dp = 36.dp,
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    coverRevision: Long = 0L,
    isLarge: Boolean = false
) {
    val context = LocalContext.current

    val customBitmap = produceState<ImageBitmap?>(
        initialValue = null,
        key1 = customArtworkUri,
        key2 = coverRevision
    ) {
        if (customArtworkUri.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(customArtworkUri)
                if (file.exists() && file.isFile && file.length() > 0L) {
                    BitmapFactory.decodeFile(file.absolutePath)?.apply {
                        setHasMipMap(true)
                    }?.asImageBitmap()
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }.value

    val entryKeys = remember(entries) {
        entries.take(30).map { it.id to (it.artworkThumbnailCacheKey ?: it.source) }
    }
    val mosaicStateKey = remember(customArtworkUri, autoMosaicEnabled, coverGenerationMode, isLarge, entryKeys) {
        listOf(customArtworkUri, autoMosaicEnabled, coverGenerationMode, isLarge, entryKeys)
    }
    val mosaicArtworks = produceState<List<ImageBitmap>>(
        initialValue = emptyList(),
        key1 = mosaicStateKey
    ) {
        if (!customArtworkUri.isNullOrBlank() || !autoMosaicEnabled || coverGenerationMode == PlaylistCoverGenerationMode.Never || entries.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            resolvePlaylistCoverArtworks(
                context = context,
                entries = entries,
                maxCount = 4,
                preferLarge = isLarge
            )
        }
    }.value

    val commonIcon = resolveCommonPlaylistFormatIcon(entries)
    val fallbackIcon = commonIcon ?: (heroIcon ?: Icons.Default.LibraryMusic)
    val customTint = iconTintArgb?.let { Color(it) }
    val hasGeneratedCover = autoMosaicEnabled && when (coverGenerationMode) {
        PlaylistCoverGenerationMode.Never -> false
        PlaylistCoverGenerationMode.AtLeastFour -> mosaicArtworks.size >= 4
        PlaylistCoverGenerationMode.AtLeastOne -> mosaicArtworks.isNotEmpty()
    }
    val containerColor = when {
        customBitmap != null || hasGeneratedCover -> MaterialTheme.colorScheme.surfaceContainerHighest
        customTint != null -> customTint.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor
    ) {
        when {
            customBitmap != null -> {
                Image(
                    bitmap = customBitmap,
                    contentDescription = "Playlist cover",
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
            autoMosaicEnabled && coverGenerationMode != PlaylistCoverGenerationMode.Never && mosaicArtworks.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Image(
                            bitmap = mosaicArtworks[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        )
                        Image(
                            bitmap = mosaicArtworks[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Image(
                            bitmap = mosaicArtworks[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        )
                        Image(
                            bitmap = mosaicArtworks[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
            autoMosaicEnabled && coverGenerationMode == PlaylistCoverGenerationMode.AtLeastOne && mosaicArtworks.isNotEmpty() -> {
                Image(
                    bitmap = mosaicArtworks[0],
                    contentDescription = "Playlist cover",
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
            customTint != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconSize > 32.dp) {
                        Box(
                            modifier = Modifier
                                .size(iconSize * 1.65f)
                                .clip(CircleShape)
                                .background(customTint.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = fallbackIcon,
                                contentDescription = null,
                                tint = customTint,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = customTint,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
            iconSize > 32.dp -> {
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
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

internal fun resolvePlaylistCoverArtworks(
    context: Context,
    entries: List<PlaylistTrackEntry>,
    maxCount: Int = 4,
    preferLarge: Boolean = false
): List<ImageBitmap> {
    val results = mutableListOf<ImageBitmap>()
    val seenArtworkKeys = mutableSetOf<String>()
    val cacheRoot = File(context.cacheDir, RECENT_ARTWORK_CACHE_DIR)

    var checkedCount = 0
    for (entry in entries) {
        if (entry.source.isBlank()) continue
        checkedCount++
        if (checkedCount > 40 && results.size < 4) {
            break
        }
        val cacheKey = entry.artworkThumbnailCacheKey?.takeIf { it.isNotBlank() }
            ?: run {
                val normalized = normalizeSourceIdentity(entry.source)?.trim().orEmpty()
                if (normalized.isNotBlank()) {
                    val key = "${sha1Hex(normalized)}.jpg"
                    if (File(cacheRoot, key).exists()) key else null
                } else null
            }
        val effectiveKey = cacheKey ?: run {
            if (results.size < maxCount) {
                ensureRecentArtworkCached(
                    context = context,
                    sourceId = entry.source,
                    requireLarge = preferLarge
                )
            } else null
        } ?: continue

        if (!seenArtworkKeys.add(effectiveKey)) continue

        val file = recentArtworkFile(context, effectiveKey, preferLarge = preferLarge)
            ?: if (preferLarge) {
                ensureRecentArtworkCached(
                    context = context,
                    sourceId = entry.source,
                    requireLarge = true
                )
                recentArtworkFile(context, effectiveKey, preferLarge = true)
            } else null

        if (file != null && file.exists() && file.isFile && file.length() > 0L) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.apply {
                    setHasMipMap(true)
                }
                if (bitmap != null) {
                    results.add(bitmap.asImageBitmap())
                    if (results.size >= maxCount) {
                        break
                    }
                }
            } catch (_: Throwable) {}
        }
    }
    return results
}

@Composable
internal fun resolveCommonPlaylistFormatIcon(entries: List<PlaylistTrackEntry>): ImageVector? {
    if (entries.isEmpty()) return null
    val firstSource = entries.firstOrNull { it.source.isNotBlank() }?.source ?: return null
    val firstExt = playlistEntrySourceExtension(firstSource)
    if (firstExt.isBlank()) return null
    val allSame = entries.all { entry ->
        if (entry.source.isBlank()) true
        else playlistEntrySourceExtension(entry.source).equals(firstExt, ignoreCase = true)
    }
    if (!allSame) return null
    return placeholderArtworkIconForFile(
        file = File("dummy.$firstExt"),
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
}

internal fun playlistEntrySourceExtension(source: String): String {
    val leaf = source.substringAfterLast('/').substringAfterLast('\\')
    return leaf.substringAfterLast('.', missingDelimiterValue = "").trim().lowercase(Locale.ROOT)
}

@Composable
private fun FavoritesCollectionRow(
    favoriteCount: Int,
    onClick: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isHomePinned: Boolean = false,
    onToggleHomePin: (() -> Unit)? = null,
    onRefreshMetadata: (() -> Unit)? = null,
    favorites: List<PlaylistTrackEntry> = emptyList(),
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
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
        leadingContent = {
            PlaylistCoverArt(
                entries = favorites,
                heroIcon = Icons.Default.Star,
                modifier = Modifier.size(if (isWatch) 34.dp else 56.dp),
                shape = RoundedCornerShape(if (isWatch) 10.dp else 12.dp),
                iconSize = if (isWatch) 18.dp else 30.dp,
                autoMosaicEnabled = autoMosaicEnabled,
                coverGenerationMode = coverGenerationMode
            )
        },
        onClick = onClick,
        onDuplicate = onDuplicate,
        onExport = onExport,
        onShare = onShare,
        isHomePinned = isHomePinned,
        onToggleHomePin = onToggleHomePin,
        onRefreshMetadata = onRefreshMetadata,
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

private fun playlistTrackCountLabel(trackCount: Int): String =
    when (trackCount) {
        0 -> "No tracks yet"
        1 -> "1 track"
        else -> "$trackCount tracks"
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRow(
    entry: PlaylistTrackEntry,
    isActive: Boolean,
    isDragged: Boolean,
    editModeEnabled: Boolean,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
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
    onRefreshMetadata: () -> Unit = {},
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
                    when {
                        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        editModeEnabled && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    }
                )
                .combinedClickable(
                    onClick = if (editModeEnabled) onToggleSelect else onClick,
                    onLongClick = { wearActionSheetOpen = true }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editModeEnabled) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }
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
                FilledTonalButton(
                    onClick = {
                        wearActionSheetOpen = false
                        onRefreshMetadata()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Refresh metadata")
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
        val rowHighlightColor by animateColorAsState(
            targetValue = when {
                isDragged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                editModeEnabled && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else -> Color.Transparent
            },
            animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
            label = "playlistRowHighlight"
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
                    .background(rowHighlightColor)
                    .let { base ->
                        if (editModeEnabled) {
                            base.clickable(onClick = onToggleSelect)
                        } else {
                            base.clickable(onClick = onClick)
                        }
                    }
                    .padding(start = if (editModeEnabled) 2.dp else 6.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editModeEnabled) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                }
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
                                        text = "Remove from playlist",
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
                                    text = "Refresh metadata",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
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
                                onRefreshMetadata()
                            }
                        )
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
                sourceId = entry.source,
                requestUrlHint = entry.requestUrlHint
            )
        }
    }.value
    val artwork = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = artworkThumbnailCacheKey
    ) {
        value = withContext(Dispatchers.IO) {
            val artworkFile = recentArtworkThumbnailFile(context, artworkThumbnailCacheKey)
            if (artworkFile != null) {
                BitmapFactory.decodeFile(artworkFile.absolutePath)?.asImageBitmap()
            } else {
                peekCachedArtworkBitmapForSource(
                    displayFile = null,
                    sourceId = entry.source,
                    requestUrl = entry.requestUrlHint
                )?.asImageBitmap()
            }
        }
    }.value
    val chipSize = if (isWatch) 32.dp else 46.dp
    val iconSize = if (isWatch) 18.dp else 28.dp
    Box(
        modifier = Modifier.size(chipSize),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
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
        if (isActive) {
            PlaylistPlayingBadge(
                isWatch = isWatch,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

/** Playback arrow badge overlaid on a playlist/track leading chip. Caller positions it. */
@Composable
private fun PlaylistPlayingBadge(
    isWatch: Boolean,
    modifier: Modifier = Modifier,
    cornerOffset: Boolean = true
) {
    val badgeSize = if (isWatch) 13.dp else 17.dp
    val badgeIconSize = if (isWatch) 8.dp else 11.dp
    Box(
        modifier = modifier
            .then(if (cornerOffset) Modifier.offset(x = 3.dp, y = 3.dp) else Modifier)
            .size(badgeSize)
            .background(color = MaterialTheme.colorScheme.surface, shape = CircleShape)
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

@Composable
private fun PlaylistCollectionRow(
    playlist: StoredPlaylist,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    isHomePinned: Boolean = false,
    onToggleHomePin: (() -> Unit)? = null,
    onRefreshMetadata: (() -> Unit)? = null,
    reorderEnabled: Boolean = false,
    isDragged: Boolean = false,
    onDragStart: (() -> Unit)? = null,
    onDragStep: ((Int) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    autoMosaicEnabled: Boolean = true,
    coverGenerationMode: PlaylistCoverGenerationMode = PlaylistCoverGenerationMode.AtLeastFour,
    onChangeCover: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isWatch: Boolean = false
) {
    PlaylistLibraryFlatRow(
        modifier = Modifier
            .fillMaxWidth(),
        title = playlist.title,
        subtitle = if (playlist.format == PlaylistStoredFormat.Internal) {
            playlistTrackCountLabel(playlist.entries.size)
        } else {
            "${playlistTrackCountLabel(playlist.entries.size)} • ${playlist.format.label}"
        },
        icon = Icons.Default.LibraryMusic,
        iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        leadingContent = {
            PlaylistCoverArt(
                entries = playlist.entries,
                customArtworkUri = playlist.customArtworkUri,
                iconTintArgb = playlist.iconTintArgb,
                heroIcon = Icons.Default.LibraryMusic,
                modifier = Modifier.size(if (isWatch) 34.dp else 56.dp),
                shape = RoundedCornerShape(if (isWatch) 10.dp else 12.dp),
                iconSize = if (isWatch) 18.dp else 30.dp,
                autoMosaicEnabled = autoMosaicEnabled && playlist.autoGenerateCover,
                coverGenerationMode = coverGenerationMode
            )
        },
        onClick = onClick,
        onRename = onRename,
        onDuplicate = onDuplicate,
        onDelete = onDelete,
        onExport = onExport,
        onShare = onShare,
        onMoveToFolder = onMoveToFolder,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        isHomePinned = isHomePinned,
        onToggleHomePin = onToggleHomePin,
        onRefreshMetadata = onRefreshMetadata,
        reorderEnabled = reorderEnabled,
        isDragged = isDragged,
        onDragStart = onDragStart,
        onDragStep = onDragStep,
        onDragEnd = onDragEnd,
        onChangeCover = onChangeCover,
        isPlaying = isPlaying,
        isWatch = isWatch
    )
}

@Composable
private fun FolderCollectionRow(
    folder: PlaylistFolder,
    playlistCount: Int,
    subfolderCount: Int,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    isWatch: Boolean = false
) {
    val subtitle = remember(playlistCount, subfolderCount) {
        val parts = mutableListOf<String>()
        if (subfolderCount > 0) {
            parts += if (subfolderCount == 1) "1 folder" else "$subfolderCount folders"
        }
        if (playlistCount > 0) {
            parts += if (playlistCount == 1) "1 playlist" else "$playlistCount playlists"
        }
        if (parts.isEmpty()) "Empty folder" else parts.joinToString(" • ")
    }
    PlaylistLibraryFlatRow(
        modifier = Modifier.fillMaxWidth(),
        title = folder.title,
        subtitle = subtitle,
        icon = Icons.Default.Folder,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onClick,
        onRename = onRename,
        onDelete = onDelete,
        onMoveToFolder = onMove,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
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
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    isHomePinned: Boolean = false,
    onToggleHomePin: (() -> Unit)? = null,
    onRefreshMetadata: (() -> Unit)? = null,
    reorderEnabled: Boolean = false,
    isDragged: Boolean = false,
    onDragStart: (() -> Unit)? = null,
    onDragStep: ((Int) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onChangeCover: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isWatch: Boolean = false
) {
    var wearActionsOpen by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    if (isWatch) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onRename != null || onDuplicate != null || onDelete != null || onExport != null || onShare != null || onTogglePin != null || onToggleHomePin != null || onRefreshMetadata != null || onMoveToFolder != null || onChangeCover != null) {
                        { wearActionsOpen = true }
                    } else null
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
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
                }
                if (isPlaying) {
                    PlaylistPlayingBadge(
                        isWatch = true,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (wearActionsOpen) {
            WatchDialogContainer(
                title = title,
                onDismissRequest = { wearActionsOpen = false }
            ) {
                if (onChangeCover != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onChangeCover()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Change cover…")
                    }
                }
                if (onTogglePin != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onTogglePin()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isPinned) "Unpin from top" else "Pin to top")
                    }
                }
                if (onToggleHomePin != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onToggleHomePin()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isHomePinned) "Unpin from home" else "Pin to home")
                    }
                }
                if (onRename != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onRename()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Rename")
                    }
                }
                if (onDuplicate != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onDuplicate()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Duplicate")
                    }
                }
                if (onExport != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onExport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Export")
                    }
                }
                if (onShare != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onShare()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Share")
                    }
                }
                if (onRefreshMetadata != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onRefreshMetadata()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Refresh metadata")
                    }
                }
                if (onMoveToFolder != null) {
                    FilledTonalButton(
                        onClick = {
                            wearActionsOpen = false
                            onMoveToFolder()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Move to folder…")
                    }
                }
                if (onDelete != null) {
                    Button(
                        onClick = {
                            wearActionsOpen = false
                            onDelete()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(
                    onClick = { wearActionsOpen = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .let { if (isDragged) it.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) else it }
                .clickable(onClick = onClick)
                .padding(start = 6.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(12.dp),
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
                }
                if (isPlaying) {
                    PlaylistPlayingBadge(
                        isWatch = false,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (reorderEnabled && onDragStart != null && onDragStep != null && onDragEnd != null) {
                PlaylistTrackReorderHandle(
                    reorderEnabled = true,
                    isDragged = isDragged,
                    onDragStart = onDragStart,
                    onDragStep = onDragStep,
                    onDragEnd = onDragEnd
                )
            }
            if (onRename != null || onDuplicate != null || onDelete != null || onExport != null || onShare != null || onTogglePin != null || onToggleHomePin != null || onRefreshMetadata != null || onMoveToFolder != null || onChangeCover != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = { menuExpanded = true }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Playlist options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onChangeCover != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Change cover…",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
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
                                    onChangeCover()
                                }
                            )
                        }
                        if (onTogglePin != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isPinned) "Unpin from top" else "Pin to top",
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
                                    onTogglePin()
                                }
                            )
                        }
                        if (onToggleHomePin != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isHomePinned) "Unpin from home" else "Pin to home",
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
                                    onToggleHomePin()
                                }
                            )
                        }
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Rename",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
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
                                    onRename()
                                }
                            )
                        }
                        if (onDuplicate != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Duplicate",
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
                                    onDuplicate()
                                }
                            )
                        }
                        if (onExport != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Export to file\u2026",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Save,
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
                                    onExport()
                                }
                            )
                        }
                        if (onShare != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Share playlist\u2026",
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
                        if (onRefreshMetadata != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Refresh metadata",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
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
                                    onRefreshMetadata()
                                }
                            )
                        }
                        if (onMoveToFolder != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Move to folder…",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
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
                                    onMoveToFolder()
                                }
                            )
                        }
                        if (onDelete != null) {
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
                    }
                }
            }
        }
    }
}

private fun playlistPageTrackSubtitle(entry: PlaylistTrackEntry): String {
    val parts = mutableListOf<String>()

    val durationText = entry.durationSecondsOverride
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { seconds -> formatPlaylistInfoDuration(seconds) }
        ?: "-:--"
    parts += durationText

    val rawArtist = entry.artist?.trim()?.takeIf { it.isNotBlank() }
    val hasValidArtist = !rawArtist.isNullOrBlank() &&
        !rawArtist.equals("Unknown artist", ignoreCase = true) &&
        !rawArtist.equals("No metadata yet", ignoreCase = true)

    val rawAlbum = entry.album?.trim()?.takeIf { it.isNotBlank() }
    val hasValidAlbum = !rawAlbum.isNullOrBlank() &&
        !rawAlbum.equals("Unknown album", ignoreCase = true)

    val artistText = if (hasValidArtist) rawArtist else "Unknown Artist"
    parts += artistText

    if (hasValidAlbum) {
        parts += rawAlbum
    }

    return parts.joinToString(" • ")
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

private data class DuplicateTrackPromptState(
    val playlistTitle: String,
    val duplicateCount: Int,
    val firstDuplicateTitle: String?,
    val totalCount: Int,
    val onAddAnyway: () -> Unit,
    val onSkipDuplicates: (() -> Unit)? = null
)

