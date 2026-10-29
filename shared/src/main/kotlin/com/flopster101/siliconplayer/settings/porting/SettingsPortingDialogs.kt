package com.flopster101.siliconplayer.settings.porting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.isWatchDevice
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.LocalAppVersionInfo
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import kotlinx.coroutines.launch

internal val LocalPortingFileIo = androidx.compose.runtime.staticCompositionLocalOf<PortingFileIo> {
    throw IllegalStateException("No PortingFileIo provided on this platform")
}

/**
 * Shared export/import flow host. State handling lives here; the platform
 * provides [LocalPortingFileIo] (file pickers/share toasts) and an optional
 * [LocalPortingStateRefresh] after values are written.
 */
@Composable
internal fun SettingsPortingDialogHost(
    visible: Boolean,
    prefs: AppPreferences,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val io = LocalPortingFileIo.current
    val scope = rememberCoroutineScope()
    val catalog = remember(prefs) { buildSettingsPortingCatalog(prefs) }
    var mode by remember { mutableStateOf<PortingMode>(PortingMode.Tree) }
    val selection = remember(catalog) {
        mutableStateMapOf<String, Boolean>().apply {
            allEntries(catalog).forEach { put(it.entry.id, it.entry.defaultSelected) }
        }
    }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var importText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun selectedIds() = PortingSelection(
        selectedEntryIds = allEntries(catalog)
            .filter { it.children.isEmpty() && selection[it.entry.id] == true }
            .map { it.entry.id }
            .toSet()
    )

    fun toggleEntry(id: String) {
        val holder = allEntries(catalog).firstOrNull { it.entry.id == id } ?: return
        val targets = listOf(holder) + descendants(holder)
        val shouldSelect = targets.any { selection[it.entry.id] != true }
        targets.forEach { selection[it.entry.id] = shouldSelect }
    }

    when (mode) {
        is PortingMode.Tree -> PortingTreeDialog(
            catalog = catalog,
            selection = selection,
            onToggle = ::toggleEntry,
            onToggleAll = { onSelect ->
                allEntries(catalog).forEach { selection[it.entry.id] = onSelect }
            },
            busy = busy,
            onDismiss = onDismiss,
            onShare = {
                busy = true
                scope.launch {
                    runCatching {
                        val result = SettingsPortingEngine.buildExport(prefs, catalog, selectedIds())
                        if (result is ExportResult.Ok) {
                            io.shareExport(
                                PortingShareRequest(
                                    bytes = result.text.encodeToByteArray(),
                                    fileName = defaultPortingFileName(PortingVersionProvider.appVersion)
                                )
                            )
                            io.toast("Shared ${result.keyCount} values")
                        } else if (result is ExportResult.Failed) {
                            io.toast(result.message)
                        }
                    }.onFailure { io.toast("Export failed: ${it.message}") }
                    busy = false
                }
            },
            onExport = {
                busy = true
                scope.launch {
                    runCatching {
                        val result = SettingsPortingEngine.buildExport(prefs, catalog, selectedIds())
                        if (result is ExportResult.Ok) {
                            val target = io
                                .pickExportTarget(defaultPortingFileName(PortingVersionProvider.appVersion))
                            if (target != null) {
                                io.writeTarget(target, result.text.encodeToByteArray())
                                io.toast("Exported ${result.keyCount} values")
                            }
                        } else if (result is ExportResult.Failed) {
                            io.toast(result.message)
                        }
                    }.onFailure { io.toast("Export failed: ${it.message}") }
                    busy = false
                }
            },
            onImport = {
                busy = true
                scope.launch {
                    runCatching {
                        val source = io.pickImportSource() ?: return@runCatching
                        val text = source.bytes.decodeToString()
                        val preview = SettingsPortingEngine.prepareImport(
                            prefs, catalog, text, PortingPlatformInfo.current
                        ) as? ImportPreview.Ok
                        if (preview == null) {
                            io.toast("Cannot read that settings file")
                        } else {
                            importPreview = preview
                            importText = text
                            mode = PortingMode.Confirm
                        }
                    }.onFailure { io.toast("Import failed: ${it.message}") }
                    busy = false
                }
            }
        )
        is PortingMode.Confirm -> ImportConfirmDialog(
            preview = importPreview as? ImportPreview.Ok ?: return,
            selection = selection,
            onToggle = ::toggleEntry,
            onDismiss = {
                mode = PortingMode.Tree
                importPreview = null
                importText = ""
            },
            onConfirm = {
                val preview = importPreview as ImportPreview.Ok
                busy = true
                scope.launch {
                    runCatching {
                        val skipped = SettingsPortingEngine.applyImport(
                            prefs, catalog, importText, selectedIds(), PortingPlatformInfo.current
                        )
                        pushDecoderStateToEngine(prefs)
                        persistDecoderStateAfterImport(prefs)
                        io.toast(
                            if (skipped.isEmpty()) "Imported"
                            else "Imported with ${skipped.size} value(s) skipped"
                        )
                        mode = PortingMode.Tree
                        importPreview = null
                        importText = ""
                    }.onFailure { io.toast("Import failed: ${it.message}") }
                    busy = false
                }
            }
        )
    }
}

internal sealed interface PortingMode {
    data object Tree : PortingMode
    data object Confirm : PortingMode
}

private fun allEntries(catalog: List<PortingSection>): List<PortingNode.EntryHolder> =
    catalog.flatMap { section -> section.children.flatMap(::flattenEntries) }

private fun flattenEntries(node: PortingNode): List<PortingNode.EntryHolder> =
    when (node) {
        is PortingNode.Label -> emptyList()
        is PortingNode.EntryHolder -> listOf(node) + node.children.flatMap(::flattenEntries)
    }

private fun descendants(node: PortingNode.EntryHolder): List<PortingNode.EntryHolder> =
    node.children.flatMap { child -> listOf(child) + descendants(child) }

@Composable
private fun PortingTreeDialog(
    catalog: List<PortingSection>,
    selection: Map<String, Boolean>,
    onToggle: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    busy: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier().heightIn(max = 640.dp),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Export and import settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Choose exactly what to save from your settings, or what to bring in from a settings file (.spxs) — anything you haven't changed from the app's defaults is not written to the export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { onToggleAll(true) }) { Text("Select all") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onToggleAll(false) }) { Text("Deselect all") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${allEntries(catalog).count { it.children.isEmpty() && selection[it.entry.id] == true }} entries selected",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                catalog.forEach { section -> PortingSectionView(section, selection, onToggle) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onShare, enabled = !busy) { Text("Share") }
                TextButton(onClick = onExport, enabled = !busy) { Text("Export…") }
                TextButton(onClick = onImport, enabled = !busy) { Text("Import…") }
            }
        }
    )
}

@Composable
private fun PortingSectionView(
    section: PortingSection,
    selection: Map<String, Boolean>,
    onToggle: (String) -> Unit
) {
    // Section header - like the settings page's own "(S)" labels.
    Text(
        text = section.label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp)
    )
    section.children.forEach { child ->
        when (child) {
            is PortingNode.Label -> {
                Text(
                    text = child.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)
                )
            }
            is PortingNode.EntryHolder -> PortingEntryRow(child, 0, selection, onToggle)
        }
    }
}

@Composable
private fun PortingEntryRow(
    node: PortingNode.EntryHolder,
    depth: Int,
    selection: Map<String, Boolean>,
    onToggle: (String) -> Unit,
    isLastChild: Boolean = false
) {
    val selectable = listOf(node) + descendants(node)
    val checked = selectable.all { selection[it.entry.id] == true }
    val indeterminate = !checked && selectable.any { selection[it.entry.id] == true }
    val connectorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(node.entry.id) }
            .drawBehind {
                val centerY = size.height / 2f
                val incomingX = 16.dp.toPx() + (depth - 1).coerceAtLeast(0) * 20.dp.toPx()
                if (depth > 0) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(incomingX, 0f),
                        end = Offset(incomingX, if (isLastChild) centerY else size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = connectorColor,
                        start = Offset(incomingX, centerY),
                        end = Offset(26.dp.toPx() + depth * 20.dp.toPx(), centerY),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                if (node.children.isNotEmpty()) {
                    val outgoingX = 16.dp.toPx() + depth * 20.dp.toPx()
                    drawLine(
                        color = connectorColor,
                        start = Offset(outgoingX, centerY),
                        end = Offset(outgoingX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .padding(start = (16 + depth * 20).dp, top = 2.dp, bottom = 2.dp)
    ) {
        TriStateCheckbox(
            state = when {
                indeterminate -> ToggleableState.Indeterminate
                checked -> ToggleableState.On
                else -> ToggleableState.Off
            },
            onClick = { onToggle(node.entry.id) }
        )
        Spacer(Modifier.width(8.dp))
        Text(node.entry.label, style = MaterialTheme.typography.bodyMedium)
    }
    node.children.forEachIndexed { index, child ->
        PortingEntryRow(
            child,
            depth + 1,
            selection,
            onToggle,
            isLastChild = index == node.children.lastIndex
        )
    }
}

@Composable
private fun ImportConfirmDialog(
    preview: ImportPreview.Ok,
    selection: Map<String, Boolean>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val appInfo = LocalAppVersionInfo.current
    val versionDiffers = preview.meta.configVersion != SETTINGS_PORTING_CONFIG_VERSION
    val appDiffers = preview.meta.appVersion.isNotBlank() && preview.meta.appVersion != appInfo.versionName
    AlertDialog(
        modifier = adaptiveDialogModifier().heightIn(max = 640.dp),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = { Text("Import these settings?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "From ${preview.meta.appVersion.ifBlank { "an unknown version" }} (${preview.meta.appPlatform.ifBlank { "unknown platform" }}). Anything unavailable on this platform is skipped and listed below before importing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (versionDiffers) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Settings file format differs (file v${preview.meta.configVersion}, this build v${SETTINGS_PORTING_CONFIG_VERSION}). Unknown entries are skipped.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (appDiffers) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "App version differs (file ${preview.meta.appVersion}, this build ${appInfo.versionName}).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (preview.skipped.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "${preview.skipped.size} values will be skipped:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    val bySection = preview.skipped.groupBy { it.sectionLabel to it.entryLabel }
                    bySection.forEach { (id, items) ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = id.first + if (id.second.isNotBlank()) " — ${id.second}" else "",
                            style = MaterialTheme.typography.titleSmall
                        )
                        items.take(6).forEach { item ->
                            Text(
                                text = item.key + " · " + item.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (items.size > 6) {
                            Text(
                                text = "…and ${items.size - 6} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                preview.sections.forEach { sec ->
                    Text(
                        text = sec.sectionLabel + " — " + sec.entryLabel,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "${sec.toApply.size} values applied, ${sec.resetToDefault.size} reset to defaults",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Confirm import") }
        }
    )
}

internal fun defaultPortingFileName(appVersion: String): String =
    "SiliconPlayer_Settings_${PortingPlatformInfo.displayName}_$appVersion.$SETTINGS_PORTING_EXTENSION"
