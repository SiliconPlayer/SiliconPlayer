package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.GroupedRowCard
import com.flopster101.siliconplayer.TrackTextInputActive
import com.flopster101.siliconplayer.VerticalScrollbarTrack
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.audio.EffectChain
import com.flopster101.siliconplayer.audio.EffectChainScopeTarget
import com.flopster101.siliconplayer.audio.EffectDescriptor
import com.flopster101.siliconplayer.audio.EffectNode
import com.flopster101.siliconplayer.audio.EffectNodeIds
import com.flopster101.siliconplayer.audio.EffectParamDescriptor
import com.flopster101.siliconplayer.audio.EffectParamType
import com.flopster101.siliconplayer.audio.EffectPreset
import com.flopster101.siliconplayer.audio.createDefaultEffectNode
import com.flopster101.siliconplayer.audio.findEffectDescriptor
import com.flopster101.siliconplayer.onSizeChangedDeferred
import com.flopster101.siliconplayer.platform.isWatchDevice
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import com.flopster101.siliconplayer.rememberScrollStateScrollbarDragHandler
import com.flopster101.siliconplayer.ui.theme.Radius
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PRESET_EMPTY_ID = "__empty__"
private const val PRESET_CUSTOM_ID = "__custom__"

private val reverbPresetNames = listOf(
    "GM Plate",
    "GM Small Room",
    "GM Medium Room",
    "GM Large Room",
    "GM Medium Hall",
    "GM Large Hall",
    "Generic",
    "Padded Cell",
    "Room",
    "Bathroom",
    "Living Room",
    "Stone Room",
    "Auditorium",
    "Concert Hall",
    "Cave",
    "Arena",
    "Hangar",
    "Carpeted Hallway",
    "Hallway",
    "Stone Corridor",
    "Alley",
    "Forest",
    "City",
    "Mountains",
    "Quarry",
    "Plain",
    "Parking Lot",
    "Sewer Pipe",
    "Underwater"
)

private sealed interface EffectPresetModalState {
    data object CopyFromScope : EffectPresetModalState
    data object SaveChoice : EffectPresetModalState
    data class SaveNew(val initialName: String = "") : EffectPresetModalState
    data object DeleteConfirm : EffectPresetModalState
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AudioEffectsDialog(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    currentPlaylistName: String?,
    currentSongName: String?,
    selectedScope: EffectChainScopeTarget,
    globalChain: EffectChain,
    coreChain: EffectChain?,
    playlistChain: EffectChain?,
    songChain: EffectChain?,
    availableEffects: List<EffectDescriptor>,
    bypassAllEffects: Boolean = false,
    presets: List<EffectPreset> = emptyList(),
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onSelectedScopeChange: (EffectChainScopeTarget) -> Unit,
    onGlobalChainChange: (EffectChain) -> Unit,
    onCoreChainChange: (EffectChain?) -> Unit,
    onPlaylistChainChange: (EffectChain?) -> Unit,
    onSongChainChange: (EffectChain?) -> Unit,
    onBypassAllEffectsChange: (Boolean) -> Unit = {},
    onPresetsChange: (List<EffectPreset>) -> Unit = {},
    onResetVolumeTab: () -> Unit,
    onResetScopeChain: (EffectChainScopeTarget) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    TrackTextInputActive()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = remember { listOf("Volume", "Effects") }
    var pendingResetTarget by remember { mutableStateOf<String?>(null) }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Audio effects",
            onDismissRequest = onDismiss
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTabIndex) {
                0 -> VolumeTabContent(
                    masterVolumeDb = masterVolumeDb,
                    pluginVolumeDb = pluginVolumeDb,
                    songVolumeDb = songVolumeDb,
                    ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
                    hasActiveCore = hasActiveCore,
                    hasActiveSong = hasActiveSong,
                    currentCoreName = currentCoreName,
                    onMasterVolumeChange = onMasterVolumeChange,
                    onPluginVolumeChange = onPluginVolumeChange,
                    onSongVolumeChange = onSongVolumeChange,
                    onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange
                )
                else -> WatchEffectChainTabContent(
                    masterVolumeDb = masterVolumeDb,
                    pluginVolumeDb = pluginVolumeDb,
                    songVolumeDb = songVolumeDb,
                    ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
                    hasActiveCore = hasActiveCore,
                    hasActivePlaylist = hasActivePlaylist,
                    hasActiveSong = hasActiveSong,
                    currentCoreName = currentCoreName,
                    currentPlaylistName = currentPlaylistName,
                    currentSongName = currentSongName,
                    selectedScope = selectedScope,
                    globalChain = globalChain,
                    coreChain = coreChain,
                    playlistChain = playlistChain,
                    songChain = songChain,
                    availableEffects = availableEffects,
                    bypassAllEffects = bypassAllEffects,
                    presets = presets,
                    onMasterVolumeChange = onMasterVolumeChange,
                    onPluginVolumeChange = onPluginVolumeChange,
                    onSongVolumeChange = onSongVolumeChange,
                    onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
                    onSelectedScopeChange = onSelectedScopeChange,
                    onGlobalChainChange = onGlobalChainChange,
                    onCoreChainChange = onCoreChainChange,
                    onPlaylistChainChange = onPlaylistChainChange,
                    onSongChainChange = onSongChainChange,
                    onBypassAllEffectsChange = onBypassAllEffectsChange,
                    onPresetsChange = onPresetsChange
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = Radius.Control
            ) {
                Text("OK")
            }
            FilledTonalButton(
                onClick = {
                    pendingResetTarget = if (selectedTabIndex == 0) {
                        "volume"
                    } else {
                        "scope_${selectedScope.storageKey}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = Radius.Control
            ) {
                Text("Reset")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    } else {
        FloatingActionDialog(
            title = "Audio effects",
            onDismiss = onDismiss,
            dismissText = "Cancel",
            confirmText = "Done",
            confirmIcon = Icons.Default.Check,
            onConfirm = onConfirm,
            bodyMaxHeight = 760.dp,
            headerAction = {
                DialogResetButton(
                    text = "Reset",
                    onClick = { pendingResetTarget = "scope_${selectedScope.storageKey}" }
                )
            }
        ) {
            EffectChainEditorSection(
                masterVolumeDb = masterVolumeDb,
                pluginVolumeDb = pluginVolumeDb,
                songVolumeDb = songVolumeDb,
                ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
                hasActiveCore = hasActiveCore,
                hasActivePlaylist = hasActivePlaylist,
                hasActiveSong = hasActiveSong,
                currentCoreName = currentCoreName,
                currentPlaylistName = currentPlaylistName,
                currentSongName = currentSongName,
                selectedScope = selectedScope,
                globalChain = globalChain,
                coreChain = coreChain,
                playlistChain = playlistChain,
                songChain = songChain,
                availableEffects = availableEffects,
                bypassAllEffects = bypassAllEffects,
                presets = presets,
                onMasterVolumeChange = onMasterVolumeChange,
                onPluginVolumeChange = onPluginVolumeChange,
                onSongVolumeChange = onSongVolumeChange,
                onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
                onSelectedScopeChange = onSelectedScopeChange,
                onGlobalChainChange = onGlobalChainChange,
                onCoreChainChange = onCoreChainChange,
                onPlaylistChainChange = onPlaylistChainChange,
                onSongChainChange = onSongChainChange,
                onBypassAllEffectsChange = onBypassAllEffectsChange,
                onPresetsChange = onPresetsChange
            )
        }
    }

    if (pendingResetTarget != null) {
        val resetLabel = when (pendingResetTarget) {
            "volume" -> "volume parameters"
            "scope_core" -> "effect chain and volume for the current core"
            "scope_playlist" -> "effect chain for the active playlist"
            "scope_song" -> "effect chain and volume for the current song"
            else -> "Global effect chain and Master volume"
        }
        val confirmReset = {
            when (pendingResetTarget) {
                "volume" -> onResetVolumeTab()
                "scope_core" -> {
                    onPluginVolumeChange(0f)
                    onResetScopeChain(EffectChainScopeTarget.Core)
                }
                "scope_playlist" -> onResetScopeChain(EffectChainScopeTarget.Playlist)
                "scope_song" -> {
                    onSongVolumeChange(0f)
                    onIgnoreCoreVolumeForSongChange(false)
                    onResetScopeChain(EffectChainScopeTarget.Song)
                }
                else -> {
                    onMasterVolumeChange(0f)
                    onResetScopeChain(EffectChainScopeTarget.Global)
                }
            }
            pendingResetTarget = null
        }
        if (isWatchDevice()) {
            WatchDialogContainer(
                title = "Confirm reset",
                onDismissRequest = { pendingResetTarget = null }
            ) {
                Text(
                    text = "Reset $resetLabel?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = confirmReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radius.Control
                ) {
                    Text("Reset")
                }
                TextButton(
                    onClick = { pendingResetTarget = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        } else {
            AlertDialog(
                modifier = adaptiveDialogModifier(),
                properties = adaptiveDialogProperties(),
                onDismissRequest = { pendingResetTarget = null },
                title = { Text("Confirm reset") },
                text = { Text("Reset $resetLabel?") },
                dismissButton = {
                    TextButton(onClick = { pendingResetTarget = null }) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    TextButton(onClick = confirmReset) {
                        Text("Reset")
                    }
                }
            )
        }
    }
}

@Composable
private fun EffectChainEditorSection(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    currentPlaylistName: String?,
    currentSongName: String?,
    selectedScope: EffectChainScopeTarget,
    globalChain: EffectChain,
    coreChain: EffectChain?,
    playlistChain: EffectChain?,
    songChain: EffectChain?,
    availableEffects: List<EffectDescriptor>,
    bypassAllEffects: Boolean,
    presets: List<EffectPreset>,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onSelectedScopeChange: (EffectChainScopeTarget) -> Unit,
    onGlobalChainChange: (EffectChain) -> Unit,
    onCoreChainChange: (EffectChain?) -> Unit,
    onPlaylistChainChange: (EffectChain?) -> Unit,
    onSongChainChange: (EffectChain?) -> Unit,
    onBypassAllEffectsChange: (Boolean) -> Unit,
    onPresetsChange: (List<EffectPreset>) -> Unit
) {
    val customSavedChains = remember {
        mutableStateMapOf(
            EffectChainScopeTarget.Global to globalChain,
            EffectChainScopeTarget.Core to (coreChain ?: EffectChain.Empty),
            EffectChainScopeTarget.Playlist to (playlistChain ?: EffectChain.Empty),
            EffectChainScopeTarget.Song to (songChain ?: EffectChain.Empty)
        )
    }

    fun inferPresetIdForChain(chain: EffectChain?): String {
        val effective = chain ?: EffectChain.Empty
        if (effective == EffectChain.Empty) return PRESET_EMPTY_ID
        val matched = presets.firstOrNull { it.chain == effective }
        return matched?.id ?: PRESET_CUSTOM_ID
    }

    val selectedPresetIds = remember {
        mutableStateMapOf(
            EffectChainScopeTarget.Global to inferPresetIdForChain(globalChain),
            EffectChainScopeTarget.Core to inferPresetIdForChain(coreChain),
            EffectChainScopeTarget.Playlist to inferPresetIdForChain(playlistChain),
            EffectChainScopeTarget.Song to inferPresetIdForChain(songChain)
        )
    }

    var activeModal by remember { mutableStateOf<EffectPresetModalState?>(null) }

    fun scopeDisplayLabel(target: EffectChainScopeTarget): String = when (target) {
        EffectChainScopeTarget.Global -> "Global"
        EffectChainScopeTarget.Core ->
            if (!currentCoreName.isNullOrBlank()) "Current core ($currentCoreName)" else "Current core"
        EffectChainScopeTarget.Playlist ->
            if (!currentPlaylistName.isNullOrBlank()) "Playlist ($currentPlaylistName)" else "Playlist"
        EffectChainScopeTarget.Song ->
            if (!currentSongName.isNullOrBlank()) "Song ($currentSongName)" else "Song"
    }

    fun chainForScope(target: EffectChainScopeTarget): EffectChain = when (target) {
        EffectChainScopeTarget.Global -> globalChain
        EffectChainScopeTarget.Core -> coreChain ?: EffectChain.Empty
        EffectChainScopeTarget.Playlist -> playlistChain ?: EffectChain.Empty
        EffectChainScopeTarget.Song -> songChain ?: EffectChain.Empty
    }

    val currentScopeChain = chainForScope(selectedScope)
    val currentPresetId = selectedPresetIds[selectedScope] ?: inferPresetIdForChain(currentScopeChain)
    val currentCustomSaved = customSavedChains[selectedScope] ?: EffectChain.Empty
    val canDeleteCurrentPreset =
        (currentPresetId == PRESET_CUSTOM_ID && currentCustomSaved != EffectChain.Empty) ||
            presets.any { it.id == currentPresetId }

    fun commitScopeChain(next: EffectChain?) {
        when (selectedScope) {
            EffectChainScopeTarget.Global -> onGlobalChainChange(next ?: EffectChain.Empty)
            EffectChainScopeTarget.Core -> if (hasActiveCore) onCoreChainChange(next)
            EffectChainScopeTarget.Playlist -> if (hasActivePlaylist) onPlaylistChainChange(next)
            EffectChainScopeTarget.Song -> if (hasActiveSong) onSongChainChange(next)
        }
    }

    fun onActiveChainMutated(next: EffectChain) {
        customSavedChains[selectedScope] = next
        selectedPresetIds[selectedScope] =
            if (next == EffectChain.Empty) PRESET_EMPTY_ID else PRESET_CUSTOM_ID
        commitScopeChain(next)
    }

    fun onSelectPreset(presetId: String) {
        selectedPresetIds[selectedScope] = presetId
        when (presetId) {
            PRESET_EMPTY_ID -> {
                commitScopeChain(
                    if (selectedScope == EffectChainScopeTarget.Global) {
                        EffectChain.Empty
                    } else if (
                        (selectedScope == EffectChainScopeTarget.Core && coreChain != null) ||
                        (selectedScope == EffectChainScopeTarget.Playlist && playlistChain != null) ||
                        (selectedScope == EffectChainScopeTarget.Song && songChain != null)
                    ) {
                        EffectChain.Empty
                    } else {
                        null
                    }
                )
            }
            PRESET_CUSTOM_ID -> {
                val restored = (customSavedChains[selectedScope] ?: EffectChain.Empty).withFreshNodeUids()
                commitScopeChain(restored)
            }
            else -> {
                val preset = presets.firstOrNull { it.id == presetId } ?: return
                commitScopeChain(preset.chain.withFreshNodeUids())
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                var scopeDropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    CompactLabeledDropdownField(
                        label = "Scope",
                        selectedText = scopeDisplayLabel(selectedScope),
                        onClick = { scopeDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = scopeDropdownExpanded,
                        onDismissRequest = { scopeDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        val availableTargets = buildList {
                            add(EffectChainScopeTarget.Global)
                            if (hasActiveCore) add(EffectChainScopeTarget.Core)
                            if (hasActivePlaylist) add(EffectChainScopeTarget.Playlist)
                            if (hasActiveSong) add(EffectChainScopeTarget.Song)
                        }
                        availableTargets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(scopeDisplayLabel(target), fontSize = 13.sp) },
                                onClick = {
                                    scopeDropdownExpanded = false
                                    onSelectedScopeChange(target)
                                }
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { activeModal = EffectPresetModalState.CopyFromScope },
                    modifier = Modifier.height(36.dp),
                    shape = Radius.Control,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Copy from…",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                var presetDropdownExpanded by remember { mutableStateOf(false) }
                val currentPresetLabel = when (currentPresetId) {
                    PRESET_EMPTY_ID -> "(empty)"
                    PRESET_CUSTOM_ID -> "Custom"
                    else -> presets.firstOrNull { it.id == currentPresetId }?.name ?: "Custom"
                }

                Box(modifier = Modifier.weight(1f)) {
                    CompactLabeledDropdownField(
                        label = "Preset",
                        selectedText = currentPresetLabel,
                        onClick = { presetDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = presetDropdownExpanded,
                        onDismissRequest = { presetDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text("(empty)", fontSize = 13.sp) },
                            onClick = {
                                presetDropdownExpanded = false
                                onSelectPreset(PRESET_EMPTY_ID)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom", fontSize = 13.sp) },
                            onClick = {
                                presetDropdownExpanded = false
                                onSelectPreset(PRESET_CUSTOM_ID)
                            }
                        )
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name, fontSize = 13.sp) },
                                onClick = {
                                    presetDropdownExpanded = false
                                    onSelectPreset(preset.id)
                                }
                            )
                        }
                    }
                }

                CompactOutlinedActionIconButton(
                    icon = Icons.Default.Save,
                    contentDescription = "Save preset",
                    enabled = true,
                    isDanger = false,
                    onClick = {
                        val isNamedPreset = presets.any { it.id == currentPresetId }
                        activeModal = if (isNamedPreset) {
                            EffectPresetModalState.SaveChoice
                        } else {
                            EffectPresetModalState.SaveNew()
                        }
                    }
                )

                CompactOutlinedActionIconButton(
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Delete preset",
                    enabled = canDeleteCurrentPreset,
                    isDanger = true,
                    onClick = {
                        if (canDeleteCurrentPreset) {
                            activeModal = EffectPresetModalState.DeleteConfirm
                        }
                    }
                )
            }
        }

        EffectChainPipelineEditor(
            masterVolumeDb = masterVolumeDb,
            pluginVolumeDb = pluginVolumeDb,
            songVolumeDb = songVolumeDb,
            ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
            hasActiveCore = hasActiveCore,
            hasActivePlaylist = hasActivePlaylist,
            hasActiveSong = hasActiveSong,
            selectedScope = selectedScope,
            globalChain = globalChain,
            coreChain = coreChain,
            playlistChain = playlistChain,
            songChain = songChain,
            availableEffects = availableEffects,
            bypassAllEffects = bypassAllEffects,
            onMasterVolumeChange = onMasterVolumeChange,
            onPluginVolumeChange = onPluginVolumeChange,
            onSongVolumeChange = onSongVolumeChange,
            onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
            onBypassAllEffectsChange = onBypassAllEffectsChange,
            onMutateChain = { next -> onActiveChainMutated(next) },
            onToggleScopeOverride = { enabled ->
                commitScopeChain(if (enabled) currentCustomSaved.withFreshNodeUids() else null)
            }
        )
    }

    when (val modal = activeModal) {
        EffectPresetModalState.CopyFromScope -> {
            val otherScopes = buildList {
                if (selectedScope != EffectChainScopeTarget.Global) add(EffectChainScopeTarget.Global)
                if (hasActiveCore && selectedScope != EffectChainScopeTarget.Core) add(EffectChainScopeTarget.Core)
                if (hasActivePlaylist && selectedScope != EffectChainScopeTarget.Playlist) add(EffectChainScopeTarget.Playlist)
                if (hasActiveSong && selectedScope != EffectChainScopeTarget.Song) add(EffectChainScopeTarget.Song)
            }
            AlertDialog(
                modifier = adaptiveDialogModifier(),
                properties = adaptiveDialogProperties(),
                onDismissRequest = { activeModal = null },
                title = { Text("Copy chain from") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        otherScopes.forEach { srcScope ->
                            val srcChain = chainForScope(srcScope)
                            val count = srcChain.nodes.size
                            val countText = when (count) {
                                0 -> "Empty"
                                1 -> "1 effect"
                                else -> "$count effects"
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onActiveChainMutated(srcChain.withFreshNodeUids())
                                        activeModal = null
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = scopeDisplayLabel(srcScope),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = countText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeModal = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        EffectPresetModalState.SaveChoice -> {
            val activePreset = presets.firstOrNull { it.id == currentPresetId }
            if (activePreset == null) {
                activeModal = null
            } else {
                AlertDialog(
                    modifier = adaptiveDialogModifier(),
                    properties = adaptiveDialogProperties(),
                    onDismissRequest = { activeModal = null },
                    title = { Text("Save preset") },
                    text = {
                        Text("Overwrite \"${activePreset.name}\" or save as a new preset?")
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { activeModal = null }) {
                                Text("Cancel")
                            }
                            TextButton(
                                onClick = {
                                    activeModal = EffectPresetModalState.SaveNew()
                                }
                            ) {
                                Text("New preset")
                            }
                        }
                    },
                    confirmButton = {
                        FilledTonalButton(
                            onClick = {
                                val updated = presets.map { existing ->
                                    if (existing.id == activePreset.id) {
                                        existing.copy(chain = currentScopeChain.withFreshNodeUids())
                                    } else {
                                        existing
                                    }
                                }
                                onPresetsChange(updated)
                                activeModal = null
                            },
                            shape = Radius.ControlCompact
                        ) {
                            Text("Overwrite")
                        }
                    }
                )
            }
        }

        is EffectPresetModalState.SaveNew -> {
            var presetNameInput by remember(modal) { mutableStateOf(modal.initialName) }
            AlertDialog(
                modifier = adaptiveDialogModifier(),
                properties = adaptiveDialogProperties(),
                onDismissRequest = { activeModal = null },
                title = { Text("New preset") },
                text = {
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("Preset name") },
                        singleLine = true,
                        shape = Radius.Control,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                dismissButton = {
                    TextButton(onClick = { activeModal = null }) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            val cleanName = presetNameInput.trim()
                                .ifEmpty { "Preset ${presets.size + 1}" }
                            val newId = "preset_${System.currentTimeMillis()}"
                            val newPreset = EffectPreset(
                                id = newId,
                                name = cleanName,
                                chain = currentScopeChain.withFreshNodeUids()
                            )
                            onPresetsChange(presets + newPreset)
                            selectedPresetIds[selectedScope] = newId
                            activeModal = null
                        },
                        shape = Radius.ControlCompact
                    ) {
                        Text("Save")
                    }
                }
            )
        }

        EffectPresetModalState.DeleteConfirm -> {
            val isCustom = currentPresetId == PRESET_CUSTOM_ID
            val activePreset = presets.firstOrNull { it.id == currentPresetId }
            val dialogTitle = if (isCustom) "Clear Custom preset?" else "Delete preset?"
            val dialogPrompt = if (isCustom) {
                "Clear all changes in Custom and return it to empty?"
            } else {
                "Delete preset \"${activePreset?.name ?: ""}\"?"
            }
            AlertDialog(
                modifier = adaptiveDialogModifier(),
                properties = adaptiveDialogProperties(),
                onDismissRequest = { activeModal = null },
                title = { Text(dialogTitle) },
                text = { Text(dialogPrompt) },
                dismissButton = {
                    TextButton(onClick = { activeModal = null }) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            if (isCustom) {
                                customSavedChains[selectedScope] = EffectChain.Empty
                                selectedPresetIds[selectedScope] = PRESET_EMPTY_ID
                                commitScopeChain(
                                    if (selectedScope == EffectChainScopeTarget.Global) EffectChain.Empty else null
                                )
                            } else if (activePreset != null) {
                                val remaining = presets.filterNot { it.id == activePreset.id }
                                onPresetsChange(remaining)
                                EffectChainScopeTarget.entries.forEach { target ->
                                    if (selectedPresetIds[target] == activePreset.id) {
                                        val activeTargetChain = chainForScope(target)
                                        customSavedChains[target] = activeTargetChain
                                        selectedPresetIds[target] =
                                            if (activeTargetChain == EffectChain.Empty) PRESET_EMPTY_ID else PRESET_CUSTOM_ID
                                    }
                                }
                            }
                            activeModal = null
                        },
                        shape = Radius.ControlCompact,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(if (isCustom) "Clear" else "Delete")
                    }
                }
            )
        }

        null -> Unit
    }
}

@Composable
private fun CompactLabeledDropdownField(
    label: String,
    selectedText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(Radius.Control)
                .clickable(onClick = onClick),
            shape = Radius.Control,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactOutlinedActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    isDanger: Boolean,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isDanger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(Radius.Control)
            .let { base ->
                if (enabled) base.clickable(onClick = onClick) else base
            },
        shape = Radius.Control,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.35f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun WatchEffectChainTabContent(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    currentPlaylistName: String?,
    currentSongName: String?,
    selectedScope: EffectChainScopeTarget,
    globalChain: EffectChain,
    coreChain: EffectChain?,
    playlistChain: EffectChain?,
    songChain: EffectChain?,
    availableEffects: List<EffectDescriptor>,
    bypassAllEffects: Boolean,
    presets: List<EffectPreset>,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onSelectedScopeChange: (EffectChainScopeTarget) -> Unit,
    onGlobalChainChange: (EffectChain) -> Unit,
    onCoreChainChange: (EffectChain?) -> Unit,
    onPlaylistChainChange: (EffectChain?) -> Unit,
    onSongChainChange: (EffectChain?) -> Unit,
    onBypassAllEffectsChange: (Boolean) -> Unit,
    onPresetsChange: (List<EffectPreset>) -> Unit
) {
    val scrollState = rememberScrollState()
    val chipScrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val thumbFraction = remember(scrollState.maxValue, viewportHeightPx) {
        if (viewportHeightPx <= 0f) 1f else {
            val contentHeight = viewportHeightPx + scrollState.maxValue.toFloat()
            (viewportHeightPx / contentHeight).coerceIn(0.08f, 1f)
        }
    }
    val offsetFraction = remember(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = scrollState,
        label = "audioEffectsDspScrollbarAlpha"
    )
    val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)

    fun commitWatchScopeChain(next: EffectChain?) {
        when (selectedScope) {
            EffectChainScopeTarget.Global -> onGlobalChainChange(next ?: EffectChain.Empty)
            EffectChainScopeTarget.Core -> if (hasActiveCore) onCoreChainChange(next)
            EffectChainScopeTarget.Playlist -> if (hasActivePlaylist) onPlaylistChainChange(next)
            EffectChainScopeTarget.Song -> if (hasActiveSong) onSongChainChange(next)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChangedDeferred { viewportHeightPx = it.height.toFloat() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedScope == EffectChainScopeTarget.Global,
                        onClick = { onSelectedScopeChange(EffectChainScopeTarget.Global) },
                        label = { Text("Global") }
                    )
                    FilterChip(
                        selected = selectedScope == EffectChainScopeTarget.Core,
                        onClick = { if (hasActiveCore) onSelectedScopeChange(EffectChainScopeTarget.Core) },
                        enabled = hasActiveCore,
                        label = { Text(currentCoreName ?: "Core") }
                    )
                    FilterChip(
                        selected = selectedScope == EffectChainScopeTarget.Playlist,
                        onClick = { if (hasActivePlaylist) onSelectedScopeChange(EffectChainScopeTarget.Playlist) },
                        enabled = hasActivePlaylist,
                        label = { Text(currentPlaylistName ?: "Playlist") }
                    )
                    FilterChip(
                        selected = selectedScope == EffectChainScopeTarget.Song,
                        onClick = { if (hasActiveSong) onSelectedScopeChange(EffectChainScopeTarget.Song) },
                        enabled = hasActiveSong,
                        label = { Text(currentSongName ?: "Song") }
                    )
                }

                EffectChainPipelineEditor(
                    masterVolumeDb = masterVolumeDb,
                    pluginVolumeDb = pluginVolumeDb,
                    songVolumeDb = songVolumeDb,
                    ignoreCoreVolumeForSong = ignoreCoreVolumeForSong,
                    hasActiveCore = hasActiveCore,
                    hasActivePlaylist = hasActivePlaylist,
                    hasActiveSong = hasActiveSong,
                    selectedScope = selectedScope,
                    globalChain = globalChain,
                    coreChain = coreChain,
                    playlistChain = playlistChain,
                    songChain = songChain,
                    availableEffects = availableEffects,
                    bypassAllEffects = bypassAllEffects,
                    onMasterVolumeChange = onMasterVolumeChange,
                    onPluginVolumeChange = onPluginVolumeChange,
                    onSongVolumeChange = onSongVolumeChange,
                    onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
                    onBypassAllEffectsChange = onBypassAllEffectsChange,
                    onMutateChain = { next -> commitWatchScopeChain(next) },
                    onToggleScopeOverride = { enabled ->
                        commitWatchScopeChain(if (enabled) EffectChain.Empty else null)
                    }
                )
            }

            if (scrollState.maxValue > 0 && viewportHeightPx > 0f) {
                VerticalScrollbarTrack(
                    thumbFraction = thumbFraction,
                    offsetFraction = offsetFraction,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(4.dp)
                        .fillMaxHeight()
                        .graphicsLayer(alpha = scrollbarAlpha),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                    onDragFractionChanged = dragToFraction
                )
            }
        }
    }
}

@Composable
private fun EffectChainPipelineEditor(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean,
    selectedScope: EffectChainScopeTarget,
    globalChain: EffectChain,
    coreChain: EffectChain?,
    playlistChain: EffectChain?,
    songChain: EffectChain?,
    availableEffects: List<EffectDescriptor>,
    bypassAllEffects: Boolean,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onBypassAllEffectsChange: (Boolean) -> Unit,
    onMutateChain: (EffectChain) -> Unit,
    onToggleScopeOverride: (Boolean) -> Unit
) {
    val selectedScopeCustomChain: EffectChain? = when (selectedScope) {
        EffectChainScopeTarget.Global -> globalChain
        EffectChainScopeTarget.Core -> coreChain
        EffectChainScopeTarget.Playlist -> playlistChain
        EffectChainScopeTarget.Song -> songChain
    }
    val isGlobal = selectedScope == EffectChainScopeTarget.Global
    val isScopeOverridden = isGlobal || selectedScopeCustomChain != null
    val displayedChain = selectedScopeCustomChain ?: EffectChain.Empty

    var inputAnchorExpanded by rememberSaveable(selectedScope.storageKey) { mutableStateOf(false) }
    var masterAnchorExpanded by rememberSaveable(selectedScope.storageKey) { mutableStateOf(false) }
    var expandedNodeUids by remember(selectedScope) { mutableStateOf(setOf<Long>()) }
    var draggedNodeUid by remember { mutableStateOf<Long?>(null) }
    var showAddEffectMenu by remember(selectedScope) { mutableStateOf(false) }

    val overrideLabel = when (selectedScope) {
        EffectChainScopeTarget.Core -> "Use custom chain for this core"
        EffectChainScopeTarget.Playlist -> "Use custom chain for this playlist"
        EffectChainScopeTarget.Song -> "Use custom chain for this song"
        EffectChainScopeTarget.Global -> ""
    }

    val inputGainDisplayDb = when (selectedScope) {
        EffectChainScopeTarget.Core -> pluginVolumeDb
        EffectChainScopeTarget.Song -> songVolumeDb
        else -> displayedChain.input.gainDb
    }
    val masterGainDisplayDb = when (selectedScope) {
        EffectChainScopeTarget.Global -> masterVolumeDb
        else -> displayedChain.master.gainDb
    }

    val inputSubtitle = buildString {
        append("Dry signal • ")
        append(if (displayedChain.input.mono) "Mono downmix" else "Stereo")
        append(" • ${formatDbLabel(inputGainDisplayDb)}")
    }

    val masterSubtitle = buildString {
        append("Final output • ")
        append(if (displayedChain.master.mono) "Mono downmix" else "Stereo")
        append(" • ${formatDbLabel(masterGainDisplayDb)}")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GroupedRowCard(isFirst = true, isLast = isGlobal) {
                SingleLineToggleRow(
                    title = "Bypass all effects",
                    checked = bypassAllEffects,
                    onCheckedChange = onBypassAllEffectsChange,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            if (!isGlobal) {
                GroupedRowCard(isFirst = false, isLast = true) {
                    SingleLineToggleRow(
                        title = overrideLabel,
                        checked = isScopeOverridden,
                        onCheckedChange = onToggleScopeOverride,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (bypassAllEffects) 0.45f else 1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AnchorNodeCard(
                title = "Input",
                badgeText = "IN",
                subtitle = inputSubtitle,
                monoChecked = displayedChain.input.mono,
                isExpanded = inputAnchorExpanded,
                isFirst = true,
                isLast = false,
                onToggleExpand = { inputAnchorExpanded = !inputAnchorExpanded },
                onMonoCheckedChange = { mono ->
                    onMutateChain(displayedChain.withInputMono(mono))
                },
                extraDrawerContent = {
                    when (selectedScope) {
                        EffectChainScopeTarget.Core -> {
                            CompactParamSliderRow(
                                label = "Core volume",
                                value = pluginVolumeDb,
                                valueRange = -20f..20f,
                                step = 1f,
                                valueText = formatDbLabel(pluginVolumeDb),
                                enabled = hasActiveCore,
                                onValueChange = onPluginVolumeChange
                            )
                        }
                        EffectChainScopeTarget.Song -> {
                            CompactParamSliderRow(
                                label = "Song volume",
                                value = songVolumeDb,
                                valueRange = -20f..20f,
                                step = 1f,
                                valueText = formatDbLabel(songVolumeDb),
                                enabled = hasActiveSong,
                                onValueChange = onSongVolumeChange
                            )
                            SingleLineToggleRow(
                                title = "Ignore core volume for this song",
                                checked = ignoreCoreVolumeForSong,
                                enabled = hasActiveSong,
                                onCheckedChange = onIgnoreCoreVolumeForSongChange
                            )
                        }
                        else -> {
                            CompactParamSliderRow(
                                label = "Gain",
                                value = displayedChain.input.gainDb,
                                valueRange = -20f..20f,
                                step = 1f,
                                valueText = formatDbLabel(displayedChain.input.gainDb),
                                enabled = true,
                                onValueChange = { gain ->
                                    onMutateChain(displayedChain.withInputGainDb(gain))
                                }
                            )
                        }
                    }
                }
            )

            displayedChain.nodes.forEachIndexed { index, node ->
                key(node.uid) {
                    val descriptor = findEffectDescriptor(node.id, availableEffects)
                    val isExpanded = node.uid in expandedNodeUids
                    EffectNodeCard(
                        node = node,
                        descriptor = descriptor,
                        totalNodes = displayedChain.nodes.size,
                        isExpanded = isExpanded,
                        isDragged = draggedNodeUid == node.uid,
                        onToggleExpand = {
                            expandedNodeUids = if (isExpanded) {
                                expandedNodeUids - node.uid
                            } else {
                                expandedNodeUids + node.uid
                            }
                        },
                        onEnabledChange = { enabled ->
                            onMutateChain(displayedChain.setNodeEnabledAt(index, enabled))
                        },
                        onRemove = {
                            expandedNodeUids = expandedNodeUids - node.uid
                            onMutateChain(displayedChain.removeNodeAt(index))
                        },
                        onDragStart = { draggedNodeUid = node.uid },
                        onDragStep = { step ->
                            val currentIdx = displayedChain.nodes.indexOfFirst { it.uid == node.uid }
                            if (currentIdx >= 0) {
                                val targetIdx = (currentIdx + step).coerceIn(0, displayedChain.nodes.lastIndex)
                                if (targetIdx != currentIdx) {
                                    onMutateChain(displayedChain.moveNode(currentIdx, targetIdx))
                                }
                            }
                        },
                        onDragEnd = { draggedNodeUid = null },
                        onParamChange = { paramId, value ->
                            onMutateChain(
                                displayedChain.setNodeParamAt(
                                    index = index,
                                    paramId = paramId,
                                    value = value,
                                    descriptors = availableEffects
                                )
                            )
                        },
                        onMonoChange = { mono ->
                            onMutateChain(displayedChain.setNodeMonoAt(index, mono))
                        }
                    )
                }
            }

            GroupedRowCard(isFirst = false, isLast = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    TextButton(
                        onClick = { showAddEffectMenu = !showAddEffectMenu },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Radius.ControlCompact,
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(
                            text = "＋ Add effect",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(
                        visible = showAddEffectMenu,
                        enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            shape = Radius.Card,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                availableEffects.forEach { descriptor ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                val newNode = createDefaultEffectNode(descriptor.id, availableEffects)
                                                if (newNode != null) {
                                                    val nextChain = displayedChain.copy(nodes = displayedChain.nodes + newNode)
                                                    onMutateChain(nextChain)
                                                    expandedNodeUids = expandedNodeUids + newNode.uid
                                                }
                                                showAddEffectMenu = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = descriptor.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "＋",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnchorNodeCard(
                title = "Master",
                badgeText = "OUT",
                subtitle = masterSubtitle,
                monoChecked = displayedChain.master.mono,
                isExpanded = masterAnchorExpanded,
                isFirst = false,
                isLast = true,
                onToggleExpand = { masterAnchorExpanded = !masterAnchorExpanded },
                onMonoCheckedChange = { mono ->
                    onMutateChain(displayedChain.withMasterMono(mono))
                },
                extraDrawerContent = {
                    if (selectedScope == EffectChainScopeTarget.Global) {
                        CompactParamSliderRow(
                            label = "Master volume",
                            value = masterVolumeDb,
                            valueRange = -20f..20f,
                            step = 1f,
                            valueText = formatDbLabel(masterVolumeDb),
                            enabled = true,
                            onValueChange = onMasterVolumeChange
                        )
                    } else {
                        CompactParamSliderRow(
                            label = "Gain",
                            value = displayedChain.master.gainDb,
                            valueRange = -20f..20f,
                            step = 1f,
                            valueText = formatDbLabel(displayedChain.master.gainDb),
                            enabled = true,
                            onValueChange = { gain ->
                                onMutateChain(displayedChain.withMasterGainDb(gain))
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun AnchorNodeCard(
    title: String,
    badgeText: String,
    subtitle: String,
    monoChecked: Boolean,
    isExpanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleExpand: () -> Unit,
    onMonoCheckedChange: (Boolean) -> Unit,
    extraDrawerContent: @Composable () -> Unit = {}
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "anchorArrowRotation"
    )
    GroupedRowCard(isFirst = isFirst, isLast = isLast) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        letterSpacing = 0.4.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    extraDrawerContent()
                    SingleLineToggleRow(
                        title = "Mono downmix",
                        checked = monoChecked,
                        onCheckedChange = onMonoCheckedChange
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectNodeCard(
    node: EffectNode,
    descriptor: EffectDescriptor?,
    totalNodes: Int,
    isExpanded: Boolean,
    isDragged: Boolean,
    onToggleExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onParamChange: (String, Float) -> Unit,
    onMonoChange: (Boolean) -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "nodeArrowRotation"
    )
    val contentAlpha = if (node.enabled) 1f else 0.45f

    GroupedRowCard(isFirst = false, isLast = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let { base ->
                    if (isDragged) {
                        base.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    } else {
                        base
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EffectNodeReorderHandle(
                    reorderEnabled = totalNodes > 1,
                    isDragged = isDragged,
                    onDragStart = onDragStart,
                    onDragStep = onDragStep,
                    onDragEnd = onDragEnd
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleExpand)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(contentAlpha)
                    ) {
                        Text(
                            text = descriptor?.displayName ?: node.id,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatEffectNodeSubtitle(node, descriptor),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                )

                CompactMd3Switch(
                    checked = node.enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    descriptor?.params.orEmpty().forEach { param ->
                        EffectParamControlRow(
                            effectId = node.id,
                            param = param,
                            rawValue = node.params[param.id] ?: param.defaultValue,
                            enabled = node.enabled,
                            onValueChange = { newVal -> onParamChange(param.id, newVal) }
                        )
                    }

                    if (descriptor?.supportsDryWet == true && descriptor.params.none { it.id == "mix" }) {
                        val mixPercent = ((node.params["mix"] ?: 1f) * 100f).roundToInt().coerceIn(0, 100)
                        CompactParamSliderRow(
                            label = "Mix",
                            value = mixPercent.toFloat(),
                            valueRange = 0f..100f,
                            step = 1f,
                            valueText = "$mixPercent%",
                            enabled = node.enabled,
                            onValueChange = { pct ->
                                onParamChange("mix", (pct / 100f).coerceIn(0f, 1f))
                            }
                        )
                    }

                    SingleLineToggleRow(
                        title = "Mono downmix",
                        checked = node.options.mono,
                        enabled = node.enabled,
                        onCheckedChange = onMonoChange
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRemove),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.32f))
                        ) {
                            Text(
                                text = "Remove",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectNodeReorderHandle(
    reorderEnabled: Boolean,
    isDragged: Boolean,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit
) {
    var dragSwapRemainderPx by remember { mutableFloatStateOf(0f) }
    val dragSwapThresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
    val handleTint by animateColorAsState(
        targetValue = when {
            !reorderEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
            isDragged -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "effectNodeDragHandleTint"
    )
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 30.dp)
            .clip(RoundedCornerShape(6.dp))
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
        Canvas(modifier = Modifier.size(18.dp)) {
            val dotRadius = 1.8.dp.toPx()
            val colLeft = size.width * 0.36f
            val colRight = size.width * 0.64f
            val rows = floatArrayOf(size.height * 0.25f, size.height * 0.50f, size.height * 0.75f)
            for (y in rows) {
                drawCircle(color = handleTint, radius = dotRadius, center = Offset(colLeft, y))
                drawCircle(color = handleTint, radius = dotRadius, center = Offset(colRight, y))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SingleLineToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (enabled) {
                    base.clickable { onCheckedChange(!checked) }
                } else {
                    base
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        CompactMd3Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompactMd3Switch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer(scaleX = 0.82f, scaleY = 0.82f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun EffectParamControlRow(
    effectId: String,
    param: EffectParamDescriptor,
    rawValue: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    when (param.type) {
        EffectParamType.Bool -> {
            SingleLineToggleRow(
                title = param.label,
                checked = rawValue >= 0.5f,
                enabled = enabled,
                onCheckedChange = { checked -> onValueChange(if (checked) 1f else 0f) }
            )
        }
        EffectParamType.Enum -> {
            val intVal = rawValue.roundToInt()
            val maxIdx = (param.enumCount - 1).coerceAtLeast(param.maxValue.roundToInt())
            val names = if (effectId == EffectNodeIds.OPENMPT_REVERB && param.id == "preset") {
                reverbPresetNames
            } else {
                (0..maxIdx).map { "Option $it" }
            }
            CompactEnumDropdownRow(
                label = param.label,
                options = names,
                value = intVal,
                enabled = enabled,
                onValueChange = { idx -> onValueChange(idx.toFloat()) }
            )
        }
        EffectParamType.Int -> {
            val minInt = param.minValue.roundToInt()
            val maxInt = param.maxValue.roundToInt().coerceAtLeast(minInt)
            val stepInt = param.step.roundToInt().coerceAtLeast(1)
            val currentInt = rawValue.roundToInt().coerceIn(minInt, maxInt)
            val cleanLabel = when {
                effectId == EffectNodeIds.OPENMPT_SURROUND && param.id == "delayMs" -> "Delay"
                effectId == EffectNodeIds.OPENMPT_BITCRUSH && param.id == "bits" -> "Bit depth"
                else -> param.label
            }
            val valueText = when {
                effectId == EffectNodeIds.OPENMPT_BITCRUSH && param.id == "bits" -> "$currentInt-bit"
                param.id.endsWith("Ms") -> "$currentInt ms"
                else -> "$currentInt"
            }
            CompactParamSliderRow(
                label = cleanLabel,
                value = currentInt.toFloat(),
                valueRange = minInt.toFloat()..maxInt.toFloat(),
                step = stepInt.toFloat(),
                valueText = valueText,
                enabled = enabled,
                onValueChange = { v -> onValueChange(v.roundToInt().toFloat()) }
            )
        }
        EffectParamType.Float -> {
            val step = if (param.step > 0f) param.step else 0.5f
            val clamped = rawValue.coerceIn(param.minValue, param.maxValue)
            val cleanLabel = if (effectId == EffectNodeIds.GAIN && param.id == "gainDb") "Gain" else param.label
            val valueText = if (param.id.endsWith("Db", ignoreCase = true) || param.label.contains("dB", ignoreCase = true)) {
                val sign = if (clamped > 0.05f) "+" else ""
                String.format(Locale.US, "%s%.1f dB", sign, clamped)
            } else {
                String.format(Locale.US, "%.1f", clamped)
            }
            CompactParamSliderRow(
                label = cleanLabel,
                value = clamped,
                valueRange = param.minValue..param.maxValue,
                step = step,
                valueText = valueText,
                enabled = enabled,
                onValueChange = onValueChange
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompactParamSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueText: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(76.dp)
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { raw ->
                    val snapped = if (step > 0f) {
                        ((raw / step).roundToInt() * step)
                            .coerceIn(valueRange.start, valueRange.endInclusive)
                    } else {
                        raw.coerceIn(valueRange.start, valueRange.endInclusive)
                    }
                    onValueChange(snapped)
                },
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
        Text(
            text = valueText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
private fun CompactEnumDropdownRow(
    label: String,
    options: List<String>,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    var expanded by remember { mutableStateOf(false) }
    val safeValue = value.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val selectedName = options.getOrElse(safeValue) { "$safeValue" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            modifier = Modifier.width(76.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(Radius.Control)
                    .let { base ->
                        if (enabled) base.clickable { expanded = true } else base
                    },
                shape = Radius.Control,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$safeValue - $selectedName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                options.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text("$index - $name", fontSize = 12.sp) },
                        onClick = {
                            onValueChange(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatDbLabel(valueDb: Float): String {
    val clean = if (abs(valueDb) < 0.05f) 0f else valueDb
    return String.format(Locale.US, "%.1f dB", clean)
}

private fun formatEffectNodeSubtitle(
    node: EffectNode,
    descriptor: EffectDescriptor?
): String {
    if (!node.enabled) return "Bypassed"
    val parts = ArrayList<String>()
    when (node.id) {
        EffectNodeIds.OPENMPT_BASS -> {
            val depth = (node.params["depth"] ?: 2f).roundToInt()
            val range = (node.params["range"] ?: 2f).roundToInt()
            parts.add("Depth $depth")
            parts.add("Range $range")
        }
        EffectNodeIds.OPENMPT_SURROUND -> {
            val depth = (node.params["depth"] ?: 8f).roundToInt()
            val delay = (node.params["delayMs"] ?: 20f).roundToInt()
            parts.add("Depth $depth")
            parts.add("$delay ms")
        }
        EffectNodeIds.OPENMPT_REVERB -> {
            val depth = (node.params["depth"] ?: 6f).roundToInt()
            val preset = (node.params["preset"] ?: 0f).roundToInt().coerceIn(0, reverbPresetNames.lastIndex)
            parts.add("Depth $depth")
            parts.add(reverbPresetNames[preset])
        }
        EffectNodeIds.OPENMPT_BITCRUSH -> {
            val bits = (node.params["bits"] ?: 16f).roundToInt()
            parts.add("$bits-bit")
        }
        EffectNodeIds.GAIN -> {
            val db = node.params["gainDb"] ?: 0f
            val sign = if (db > 0.05f) "+" else ""
            parts.add(String.format(Locale.US, "%s%.1f dB", sign, db))
        }
        else -> {
            descriptor?.params.orEmpty().forEach { p ->
                val v = node.params[p.id] ?: p.defaultValue
                if (p.isInteger) {
                    parts.add("${p.label} ${v.roundToInt()}")
                } else {
                    parts.add(String.format(Locale.US, "%s %.1f", p.label, v))
                }
            }
        }
    }
    if (node.options.mono) {
        parts.add("Mono")
    }
    return parts.joinToString(" • ").ifEmpty { "Active" }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeTabContent(
    masterVolumeDb: Float,
    pluginVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    hasActiveCore: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VolumeSliderRow(
            label = "Master Volume",
            valueDb = masterVolumeDb,
            onValueChange = onMasterVolumeChange
        )

        VolumeSliderRow(
            label = "Core Volume",
            valueDb = pluginVolumeDb,
            onValueChange = onPluginVolumeChange,
            enabled = hasActiveCore
        )

        VolumeSliderRow(
            label = "Song Volume",
            valueDb = songVolumeDb,
            onValueChange = onSongVolumeChange,
            enabled = hasActiveSong
        )

        CompositionLocalProvider(
            LocalMinimumInteractiveComponentEnforcement provides false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ignore Core volume for this song",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = ignoreCoreVolumeForSong,
                    onCheckedChange = onIgnoreCoreVolumeForSongChange,
                    enabled = hasActiveSong
                )
            }
        }

        Text(
            text = "Core: ${currentCoreName ?: "(none)"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = if (valueDb == 0f) "0.0 dB" else String.format(Locale.US, "%.1f dB", valueDb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onValueChange((valueDb - 1f).coerceIn(-20f, 20f)) },
                modifier = Modifier.size(36.dp),
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label"
                )
            }

            Slider(
                value = valueDb,
                onValueChange = onValueChange,
                valueRange = -20f..20f,
                steps = 39,
                modifier = Modifier.weight(1f),
                enabled = enabled
            )

            IconButton(
                onClick = { onValueChange((valueDb + 1f).coerceIn(-20f, 20f)) },
                modifier = Modifier.size(36.dp),
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label"
                )
            }
        }
    }
}
