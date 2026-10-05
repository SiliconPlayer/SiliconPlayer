package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.LocalToastHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class HomeRouteState(
    val recentFoldersLimit: Int,
    val recentFilesLimit: Int,
    val pressBackTwiceToExit: Boolean
)

internal data class HomeRouteActions(
    val onRecentFoldersLimitChanged: (Int) -> Unit,
    val onRecentFilesLimitChanged: (Int) -> Unit,
    val onPressBackTwiceToExitChanged: (Boolean) -> Unit
)

internal data class FileBrowserRouteState(
    val rememberBrowserLocation: Boolean,
    val showParentDirectoryEntry: Boolean,
    val showFileIconChipBackground: Boolean,
    val sortArchivesBeforeFiles: Boolean,
    val browserNameSortMode: BrowserNameSortMode
)

internal data class FileBrowserRouteActions(
    val onRememberBrowserLocationChanged: (Boolean) -> Unit,
    val onShowParentDirectoryEntryChanged: (Boolean) -> Unit,
    val onShowFileIconChipBackgroundChanged: (Boolean) -> Unit,
    val onSortArchivesBeforeFilesChanged: (Boolean) -> Unit,
    val onBrowserNameSortModeChanged: (BrowserNameSortMode) -> Unit,
    val onClearThumbnailCache: (() -> Int)? = null
)

internal data class MiscRouteActions(
    val onClearRecentHistory: () -> Unit
)

internal data class NetworkRouteActions(
    val onClearSavedNetworkSources: () -> Unit
)

internal data class UiRouteState(
    val themeMode: ThemeMode,
    val useMonet: Boolean,
    val monetAvailable: Boolean
)

internal data class UiRouteActions(
    val onThemeModeChanged: (ThemeMode) -> Unit,
    val onUseMonetChanged: (Boolean) -> Unit
)

@Composable
internal fun HomeRouteContent(
    state: HomeRouteState,
    actions: HomeRouteActions
) {
    val prefs = LocalAppPreferences.current
    var showFolderLimitDialog by remember { mutableStateOf(false) }
    var showFileLimitDialog by remember { mutableStateOf(false) }
    var perSubtuneRecentRows by remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.RECENTS_PER_SUBTUNE_ROWS, false))
    }

    SettingsSectionLabel("Recents")
    SettingsItemCard(
        title = "Recent folders limit",
        description = "${state.recentFoldersLimit} folders",
        icon = Icons.Default.Folder,
        onClick = { showFolderLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Recent files limit",
        description = "${state.recentFilesLimit} files",
        icon = Icons.Default.MusicNote,
        onClick = { showFileLimitDialog = true }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "One recents row per subtune",
        description = "List every played subtune of multi-subtune songs as its own row instead of updating the song's row to the last played subtune.",
        checked = perSubtuneRecentRows,
        onCheckedChange = {
            perSubtuneRecentRows = it
            prefs.edit().putBoolean(AppPreferenceKeys.RECENTS_PER_SUBTUNE_ROWS, it).apply()
        }
    )

    SettingsSectionLabel("Navigation")
    PlayerSettingToggleCard(
        title = "Press back twice to exit",
        description = "Require pressing back twice quickly on the Home screen to exit the app.",
        checked = state.pressBackTwiceToExit,
        onCheckedChange = actions.onPressBackTwiceToExitChanged
    )

    if (showFolderLimitDialog) {
        SettingsTextInputDialog(
            title = "Recent folders limit",
            fieldLabel = "Folders",
            initialValue = state.recentFoldersLimit.toString(),
            supportingText = "1-50 (default 3)",
            keyboardType = KeyboardType.Number,
            sanitizer = { value -> value.filter { it.isDigit() }.take(2) },
            onDismiss = { showFolderLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed in 1..50) {
                    actions.onRecentFoldersLimitChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showFileLimitDialog) {
        SettingsTextInputDialog(
            title = "Recent files limit",
            fieldLabel = "Files",
            initialValue = state.recentFilesLimit.toString(),
            supportingText = "1-50 (default 5)",
            keyboardType = KeyboardType.Number,
            sanitizer = { value -> value.filter { it.isDigit() }.take(2) },
            onDismiss = { showFileLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed in 1..50) {
                    actions.onRecentFilesLimitChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }
}

@Composable
internal fun MiscRouteContent(
    actions: MiscRouteActions
) {
    SettingsSectionLabel("Utilities")
    SettingsItemCard(
        title = "Clear home recents",
        description = "Remove recent folders and recently played shortcuts from the Home screen.",
        icon = Icons.Default.DeleteForever,
        onClick = actions.onClearRecentHistory
    )
}

@Composable
internal fun NetworkRouteContent(
    actions: NetworkRouteActions
) {
    var showClearSavedSourcesConfirm by remember { mutableStateOf(false) }

    SettingsSectionLabel("Saved network sources")
    SettingsItemCard(
        title = "Clear saved sources",
        description = "Remove all saved network folders, remote sources, and their saved credentials.",
        icon = Icons.Default.DeleteForever,
        onClick = { showClearSavedSourcesConfirm = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Future examples and providers",
        description = "Built-in examples and provider toggles will be added here later.",
        icon = Icons.Default.Public,
        onClick = {}
    )

    if (showClearSavedSourcesConfirm) {
        SettingsConfirmDialog(
            title = "Clear saved network sources?",
            message = "This removes all user-saved network folders, remote sources, and their saved credentials.",
            confirmLabel = "Clear",
            onDismiss = { showClearSavedSourcesConfirm = false },
            onConfirm = actions.onClearSavedNetworkSources
        )
    }
}

@Composable
internal fun FileBrowserRouteContent(
    state: FileBrowserRouteState,
    actions: FileBrowserRouteActions
) {
    val prefs = LocalAppPreferences.current
    val toastHandler = LocalToastHandler.current
    var showUnsupportedFiles by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_UNSUPPORTED_FILES,
                AppDefaults.Browser.showUnsupportedFiles
            )
        )
    }
    var showPreviewFiles by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_PREVIEW_FILES,
                AppDefaults.Browser.showPreviewFiles
            )
        )
    }
    var showHiddenFilesAndFolders by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS,
                AppDefaults.Browser.showHiddenFilesAndFolders
            )
        )
    }
    var showLocalThumbnailPreviews by remember {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.BROWSER_SHOW_LOCAL_THUMBNAIL_PREVIEWS,
                AppDefaults.Browser.showLocalThumbnailPreviews
            )
        )
    }

    SettingsSectionLabel("Browser behavior")
    PlayerSettingToggleCard(
        title = "Remember browser location",
        description = "Restore last storage and folder when reopening the library browser.",
        checked = state.rememberBrowserLocation,
        onCheckedChange = actions.onRememberBrowserLocationChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show parent directory entry (..)",
        description = "Show a '..' row at the top of file lists to go one level up.",
        checked = state.showParentDirectoryEntry,
        onCheckedChange = actions.onShowParentDirectoryEntryChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show file icon chip background",
        description = "Draw the same rounded chip background behind file icons as folders.",
        checked = state.showFileIconChipBackground,
        onCheckedChange = actions.onShowFileIconChipBackgroundChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show local music thumbnails",
        description = "Slowly load cached artwork previews for local playable files in folder lists.",
        checked = showLocalThumbnailPreviews,
        onCheckedChange = {
            showLocalThumbnailPreviews = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_LOCAL_THUMBNAIL_PREVIEWS, it).apply()
        }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Clear thumbnail preview cache",
        description = "Delete cached local file browser and library artwork thumbnails.",
        icon = Icons.Default.DeleteForever,
        onClick = {
            val deleted = actions.onClearThumbnailCache?.invoke() ?: 0
            toastHandler.showToast(
                if (deleted > 0) "Cleared $deleted thumbnail previews" else "Thumbnail preview cache is already empty"
            )
        }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show unsupported files",
        description = "Display files that no enabled decoder core can open.",
        checked = showUnsupportedFiles,
        onCheckedChange = {
            showUnsupportedFiles = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_UNSUPPORTED_FILES, it).apply()
        }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show text and image files",
        description = "Display previewable text/image files in browser lists.",
        checked = showPreviewFiles,
        onCheckedChange = {
            showPreviewFiles = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_PREVIEW_FILES, it).apply()
        }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Show hidden files and folders",
        description = "Display hidden entries (such as dot-prefixed names).",
        checked = showHiddenFilesAndFolders,
        onCheckedChange = {
            showHiddenFilesAndFolders = it
            prefs.edit().putBoolean(AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS, it).apply()
        }
    )
    SettingsRowSpacer()
    BrowserNameSortModeSelectorCard(
        selectedMode = state.browserNameSortMode,
        onSelectedModeChanged = actions.onBrowserNameSortModeChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Sort ZIP archives before files",
        description = "List ZIP archives after folders but before regular files.",
        checked = state.sortArchivesBeforeFiles,
        onCheckedChange = actions.onSortArchivesBeforeFilesChanged
    )
}

@Composable
internal fun UiRouteContent(
    state: UiRouteState,
    actions: UiRouteActions
) {
    ThemeModeSelectorCard(
        selectedMode = state.themeMode,
        onSelectedModeChanged = actions.onThemeModeChanged
    )
    // Unavailable hides instead of showing a dead row, like the other §7.10 flags.
    if (state.monetAvailable) {
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Use Monet colors",
            description = "Use Android wallpaper-based dynamic colors instead of SiliconPlayer's custom palette.",
            checked = state.useMonet,
            onCheckedChange = actions.onUseMonetChanged
        )
    }
}
