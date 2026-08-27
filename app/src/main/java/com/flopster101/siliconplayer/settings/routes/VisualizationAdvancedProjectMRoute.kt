package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPack
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetDownloader
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

@Composable
internal fun VisualizationAdvancedProjectMRouteContent(
    onOpenPresetPacks: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    val enabledSets = remember(context, prefs) { ProjectMPresetSets.enabledSets(context, prefs) }
    var presetCount by remember { mutableStateOf<Int?>(null) }
    var indexing by remember { mutableStateOf(false) }
    var randomStart by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)) }
    var presetDurationSeconds by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, AppDefaults.Visualization.ProjectM.presetDurationSeconds.toString())?.toDoubleOrNull() ?: AppDefaults.Visualization.ProjectM.presetDurationSeconds)
    }
    var hardCutEnabled by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, AppDefaults.Visualization.ProjectM.hardCutEnabled)) }
    var hardCutSensitivity by remember { mutableStateOf(prefs.getFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, AppDefaults.Visualization.ProjectM.hardCutSensitivity)) }
    var rotationRandom by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, AppDefaults.Visualization.ProjectM.rotationRandom)) }
    var meshSize by remember { mutableStateOf(prefs.getInt(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, AppDefaults.Visualization.ProjectM.defaultMeshSize(context))) }
    var aspectCorrection by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, AppDefaults.Visualization.ProjectM.aspectCorrection)) }
    var fpsMode by remember { mutableStateOf(VisualizationOscFpsMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, AppDefaults.Visualization.ProjectM.fpsMode.storageValue))) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showMeshDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showDownloadPrompt by remember { mutableStateOf(false) }
    var pendingDownloadPack by remember { mutableStateOf<ProjectMPack?>(null) }
    var pendingRemovePack by remember { mutableStateOf<ProjectMPack?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadProgressBytes by remember { mutableStateOf(0L) }
    var downloadTotalBytes by remember { mutableStateOf<Long?>(null) }
    var downloadSpeedBps by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadedVersion by remember { mutableStateOf(0) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var showPromptProgress by remember { mutableStateOf(false) }

    fun scanCounts() {
        indexing = true
        scope.launch {
            val total = withContext(Dispatchers.Default) {
                enabledSets.sumOf { ProjectMPresetSets.presetCountForIndexed(context, it) }
            }
            presetCount = total
            indexing = false
        }
    }

    fun reindex() {
        indexing = true
        scope.launch {
            withContext(Dispatchers.Default) {
                try { ProjectMPresetSets.reindex(context, prefs) } catch (_: Throwable) { }
            }
            val total = withContext(Dispatchers.Default) {
                enabledSets.sumOf { ProjectMPresetSets.presetCountForIndexed(context, it) }
            }
            presetCount = total
            indexing = false
        }
    }

    LaunchedEffect(Unit) {
        scanCounts()
        val dismissed = prefs.getBoolean(ProjectMPresetDownloader.PREF_DOWNLOAD_PROMPT_DISMISSED, false)
        if (!dismissed && !ProjectMPresetDownloader.isAnyDownloaded(context)) {
            showDownloadPrompt = true
        }
    }

    fun plural(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"

    val packsDescription = "${plural(enabledSets.size, "pack")} · ${presetCount?.let { plural(it, "preset") } ?: "… presets"}"
    val reindexDescription = if (indexing) "Re-indexing…" else "Rescan the enabled preset folders."

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadingId = null
        showPromptProgress = false
    }

    fun startDownload(pack: ProjectMPack, fromPrompt: Boolean = false) {
        if (downloadingId != null) return
        downloadingId = pack.id
        downloadError = null
        downloadProgressBytes = 0L
        downloadTotalBytes = null
        downloadSpeedBps = null
        showPromptProgress = fromPrompt
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()
        val job = scope.launch(Dispatchers.IO) {
            val texturePack = ProjectMPresetDownloader.PACKS.firstOrNull { it.id == "downloaded_textures" }
            if (pack.requiresTextures && texturePack != null && !ProjectMPresetDownloader.isDownloaded(context, texturePack.id)) {
                val rTex = ProjectMPresetDownloader.download(context, texturePack) { dl, tot ->
                    downloadProgressBytes = dl
                    downloadTotalBytes = tot
                    val now = System.currentTimeMillis()
                    val dt = now - lastTime
                    if (dt >= 500) {
                        val dBytes = dl - lastBytes
                        downloadSpeedBps = if (dt > 0) (dBytes * 1000 / dt) else null
                        lastBytes = dl
                        lastTime = now
                    }
                }
                if (rTex.isFailure) {
                    withContext(Dispatchers.Main) {
                        if (kotlin.coroutines.cancellation.CancellationException::class.java.isInstance(rTex.exceptionOrNull())) {
                            downloadError = null
                        } else {
                            downloadError = rTex.exceptionOrNull()?.message ?: "Texture download failed"
                        }
                        downloadingId = null
                        showPromptProgress = false
                    }
                    return@launch
                }
            }
            val result = ProjectMPresetDownloader.download(context, pack) { dl, tot ->
                downloadProgressBytes = dl
                downloadTotalBytes = tot
                val now = System.currentTimeMillis()
                val dt = now - lastTime
                if (dt >= 500) {
                    val dBytes = dl - lastBytes
                    downloadSpeedBps = if (dt > 0) (dBytes * 1000 / dt) else null
                    lastBytes = dl
                    lastTime = now
                }
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    if (pack.requiresTextures) ProjectMPresetDownloader.ensureTexturesForPack(context, pack.id)
                    ProjectMPresetSets.setEnabled(prefs, pack.id, true)
                    try { ProjectMPresetSets.reindex(context, prefs) } catch (_: Throwable) {}
                    downloadedVersion++
                    scanCounts()
                } else {
                    val ex = result.exceptionOrNull()
                    if (ex is kotlinx.coroutines.CancellationException) {
                        downloadError = null
                    } else {
                        downloadError = ex?.message ?: "Download failed"
                    }
                }
                downloadingId = null
                showPromptProgress = false
                downloadJob = null
            }
        }
        downloadJob = job
        job.invokeOnCompletion { downloadJob = null }
    }

    Column {
        if (downloadError != null) {
            Text(
                text = downloadError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            SettingsRowSpacer()
        }
        SettingsSectionLabel("Downloads")
        SettingsRowSpacer()
        for ((index, pack) in ProjectMPresetDownloader.PACKS.filter { it.id != "downloaded_textures" }.withIndex()) {
            val isDownloaded = remember(downloadedVersion) { ProjectMPresetDownloader.isDownloaded(context, pack.id) }
            val isDownloading = downloadingId == pack.id
            val progressFraction = if (isDownloading && downloadTotalBytes != null && downloadTotalBytes!! > 0) downloadProgressBytes.toFloat() / downloadTotalBytes!!.toFloat() else null

            SettingsRowContainer(
                onClick = {
                    if (isDownloading) {
                        cancelDownload()
                    } else if (!isDownloaded) {
                        pendingDownloadPack = pack
                    } else {
                        pendingRemovePack = pack
                    }
                }
            ) {
                Icon(
                    imageVector = when {
                        isDownloaded -> Icons.Default.FolderCopy
                        isDownloading -> Icons.Default.Download
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = pack.label, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isDownloading) {
                        val sizeLabel = downloadTotalBytes?.let { tot -> "${formatBytes(downloadProgressBytes)} / ${formatBytes(tot)}" } ?: formatBytes(downloadProgressBytes)
                        val speedLabel = downloadSpeedBps?.let { "${formatBytes(it)}/s" } ?: ""
                        val percentLabel = downloadTotalBytes?.let { tot -> if (tot > 0) "${(downloadProgressBytes * 100 / tot).toInt()}%" else "" } ?: ""
                        val detail = listOf(sizeLabel, speedLabel, percentLabel).filter { it.isNotEmpty() }.joinToString(" • ")
                        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (progressFraction != null) {
                            LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    } else if (isDownloaded) {
                        Text(text = "Downloaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(text = pack.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (isDownloading) {
                    IconButton(onClick = { cancelDownload() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                } else if (isDownloaded) {
                    IconButton(onClick = { pendingRemovePack = pack }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
            if (index < ProjectMPresetDownloader.PACKS.filter { it.id != "downloaded_textures" }.size - 1) SettingsRowSpacer()
        }
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Presets")
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Configure preset packs",
            description = packsDescription,
            icon = Icons.Default.FolderCopy,
            onClick = onOpenPresetPacks
        )
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Re-index presets",
            description = reindexDescription,
            icon = Icons.Default.Refresh,
            onClick = { reindex() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Playback")
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Random preset on start",
            description = "Start with a random preset each time the visualizer opens.",
            checked = randomStart,
            onCheckedChange = { enabled ->
                randomStart = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, enabled).apply()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Transitions")
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Preset duration",
            description = "Time before switching to the next preset.",
            value = "${presetDurationSeconds.toInt()}s",
            onClick = { showDurationDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Hard cut",
            description = "Allow hard cuts on beat. Sensitivity controls how easily they trigger.",
            checked = hardCutEnabled,
            onCheckedChange = { enabled ->
                hardCutEnabled = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, enabled).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetHardCutEnabled(enabled) } catch (_: Throwable) {}
            }
        )
        if (hardCutEnabled) {
            SettingsRowSpacer()
            SettingsValuePickerCard(
                title = "Hard cut sensitivity",
                description = "Beat sensitivity for hard cuts.",
                value = String.format(Locale.US, "%.1f", hardCutSensitivity),
                onClick = { showSensitivityDialog = true }
            )
        }
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Random rotation",
            description = "Pick the next preset randomly instead of sequentially.",
            checked = rotationRandom,
            onCheckedChange = { enabled ->
                rotationRandom = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, enabled).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetRotationRandom(enabled) } catch (_: Throwable) {}
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Rendering")
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Mesh size",
            description = "Resolution of the warp mesh.",
            value = "${meshSize}x${meshSize}",
            onClick = { showMeshDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Aspect correction",
            description = "Correct for non-square pixels.",
            checked = aspectCorrection,
            onCheckedChange = { enabled ->
                aspectCorrection = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, enabled).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetAspectCorrection(enabled) } catch (_: Throwable) {}
            }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Frame rate",
            description = "Target frame rate for projectM.",
            value = fpsMode.label,
            onClick = { showFpsDialog = true }
        )
    }
    if (showDurationDialog) {
        SteppedIntSliderDialog(
            title = "Preset duration",
            unitLabel = "s",
            range = 5..120,
            step = 1,
            currentValue = presetDurationSeconds.toInt(),
            onDismiss = { showDurationDialog = false },
            onConfirm = { v ->
                presetDurationSeconds = v.toDouble()
                prefs.edit().putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, v.toDouble().toString()).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetPresetDuration(v.toDouble()) } catch (_: Throwable) {}
                showDurationDialog = false
            }
        )
    }
    if (showSensitivityDialog) {
        SteppedIntSliderDialog(
            title = "Hard cut sensitivity",
            unitLabel = "",
            range = 0..50,
            step = 1,
            currentValue = (hardCutSensitivity * 10).toInt(),
            valueLabelFormatter = { v -> String.format(Locale.US, "%.1f", v / 10.0) },
            onDismiss = { showSensitivityDialog = false },
            onConfirm = { v ->
                val f = v / 10.0f
                hardCutSensitivity = f
                prefs.edit().putFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, f).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetHardCutSensitivity(f) } catch (_: Throwable) {}
                showSensitivityDialog = false
            }
        )
    }
    if (showMeshDialog) {
        SettingsSingleChoiceDialog(
            title = "Mesh size",
            selectedValue = meshSize,
            options = listOf(32, 48, 64, 96, 128).map { s -> ChoiceDialogOption(value = s, label = "${s}x${s}") },
            onSelected = { s ->
                meshSize = s
                prefs.edit().putInt(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, s).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetMeshSize(s) } catch (_: Throwable) {}
                showMeshDialog = false
            },
            onDismiss = { showMeshDialog = false }
        )
    }
    if (showFpsDialog) {
        SettingsSingleChoiceDialog(
            title = "Frame rate",
            selectedValue = fpsMode,
            options = VisualizationOscFpsMode.entries.map { m -> ChoiceDialogOption(value = m, label = m.label) },
            onSelected = { m ->
                fpsMode = m
                prefs.edit().putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, m.storageValue).apply()
                val fps = when (m) {
                    VisualizationOscFpsMode.Default -> 35
                    VisualizationOscFpsMode.Fps60 -> 60
                    VisualizationOscFpsMode.NativeRefresh -> 0
                }
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetFps(fps) } catch (_: Throwable) {}
                showFpsDialog = false
            },
            onDismiss = { showFpsDialog = false }
        )
    }
    pendingDownloadPack?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingDownloadPack = null },
            title = { Text("Download ${pack.label}?") },
            text = { Text("Download ${pack.label} (${pack.sizeLabel}) from GitHub into internal storage?${if (pack.requiresTextures) " Textures will be fetched automatically." else ""}") },
            confirmButton = {
                TextButton(onClick = {
                    val p = pendingDownloadPack
                    pendingDownloadPack = null
                    if (p != null) startDownload(p, fromPrompt = false)
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadPack = null }) { Text("Cancel") }
            }
        )
    }
    pendingRemovePack?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingRemovePack = null },
            title = { Text("Remove ${pack.label}?") },
            text = { Text("Remove ${pack.label} and its presets from internal storage?") },
            confirmButton = {
                TextButton(onClick = {
                    val p = pendingRemovePack
                    pendingRemovePack = null
                    if (p != null) {
                        ProjectMPresetDownloader.remove(context, p.id)
                        ProjectMPresetSets.setEnabled(prefs, p.id, false)
                        try { ProjectMPresetSets.reindex(context, prefs) } catch (_: Throwable) {}
                        downloadedVersion++
                        scanCounts()
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovePack = null }) { Text("Cancel") }
            }
        )
    }
    if (showDownloadPrompt) {
        AlertDialog(
            onDismissRequest = { showDownloadPrompt = false },
            title = { Text("Download MilkDrop presets?") },
            text = {
                Text("projectM ships with 37 test presets.\n\nDownload the original MilkDrop collection + textures (~80 MB) from GitHub into internal storage?\n\nYou can download more packs in settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadPrompt = false
                    val pack = ProjectMPresetDownloader.PACKS.first { it.id == "downloaded_milkdrop_original" }
                    startDownload(pack, fromPrompt = true)
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean(ProjectMPresetDownloader.PREF_DOWNLOAD_PROMPT_DISMISSED, true).apply()
                    showDownloadPrompt = false
                }) { Text("Not now") }
            }
        )
    }
    if (showPromptProgress && downloadingId != null) {
        val title = ProjectMPresetDownloader.PACKS.firstOrNull { it.id == downloadingId }?.label ?: "Downloading"
        val progressFraction = if (downloadTotalBytes != null && downloadTotalBytes!! > 0) downloadProgressBytes.toFloat() / downloadTotalBytes!!.toFloat() else null
        AlertDialog(
            onDismissRequest = {},
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (downloadTotalBytes != null && downloadTotalBytes!! > 0) "Downloading…" else "Connecting…",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (progressFraction != null) {
                        LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val sizeLabel = downloadTotalBytes?.let { tot -> "${formatBytes(downloadProgressBytes)} / ${formatBytes(tot)}" } ?: formatBytes(downloadProgressBytes)
                    Text(text = sizeLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    downloadTotalBytes?.let { tot ->
                        if (tot > 0) Text(text = "${(downloadProgressBytes * 100 / tot).toInt()}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    downloadSpeedBps?.takeIf { it > 0 }?.let { speed ->
                        Text(text = "${formatBytes(speed)}/s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { cancelDownload() }) { Text("Cancel") }
            }
        )
    }
}
