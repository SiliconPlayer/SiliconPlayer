package com.flopster101.siliconplayer.ui.dialogs

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VerticalScrollbarTrack
import com.flopster101.siliconplayer.applyDecoderPriorityOrder
import com.flopster101.siliconplayer.onSizeChangedDeferred
import com.flopster101.siliconplayer.rememberLazyListScrollbarDragHandler
import java.io.File

// Moves the selected core above the current winner for the file's
// extension. False when nothing changes (already winning, no claimant).
private fun promoteDecoderForFile(file: File, selected: String, prefs: SharedPreferences): Boolean {
    val winner = NativeBridge.getDecoderClaimantsForFile(file.absolutePath).firstOrNull() ?: return false
    if (selected == winner) return false
    val order = NativeBridge.getRegisteredDecoderNames().toMutableList()
    if (!order.remove(selected)) return false
    val winnerIndex = order.indexOf(winner)
    if (winnerIndex < 0) return false
    order.add(winnerIndex, selected)
    applyDecoderPriorityOrder(order, prefs)
    return true
}

@Composable
private fun PlayWithOptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            // Indent to the radio column inside the list box.
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}

@Composable
internal fun PlayWithDialog(
    file: File,
    prefs: SharedPreferences,
    showDontAskAgain: Boolean = false,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val claimants = remember(file) {
        NativeBridge.getDecoderClaimantsForFile(file.absolutePath).toList()
    }
    val allEnabled = remember(file) {
        NativeBridge.getRegisteredDecoderNames().filter { NativeBridge.isDecoderEnabled(it) }
    }
    var showAll by remember(file) { mutableStateOf(false) }
    var alwaysPrioritize by remember(file) { mutableStateOf(false) }
    var dontAskAgain by remember(file) { mutableStateOf(false) }
    val rows = if (showAll) allEnabled else claimants
    var selected by remember(file, showAll) {
        mutableStateOf(if (claimants.isEmpty()) allEnabled.firstOrNull() else claimants.first())
    }
    // Prioritizing needs something to overtake and a core that actually
    // claims the extension; experimental picks only get a one-shot open.
    val prioritizeAllowed = claimants.size >= 2 && selected != null && selected in claimants

    val listState = rememberLazyListState()
    var listViewportHeightPx by remember { mutableFloatStateOf(0f) }
    val lazyListLayoutInfo = listState.layoutInfo
    val visibleItems = lazyListLayoutInfo.visibleItemsInfo
    val totalItems = lazyListLayoutInfo.totalItemsCount
    val averageItemSizePx = if (visibleItems.isNotEmpty()) {
        visibleItems.map { it.size }.average().toFloat()
    } else {
        0f
    }
    val hasScrollableResults = averageItemSizePx > 0f && totalItems > visibleItems.size
    val thumbFraction = if (hasScrollableResults && listViewportHeightPx > 0f) {
        ((visibleItems.size * averageItemSizePx) / listViewportHeightPx).coerceIn(0.05f, 1f)
    } else {
        1f
    }
    val offsetFraction = if (hasScrollableResults) {
        val firstVisible = visibleItems.firstOrNull()
        val scrollRange = ((totalItems - visibleItems.size) * averageItemSizePx).coerceAtLeast(1f)
        val scrolledPx = if (firstVisible != null) {
            (firstVisible.index * averageItemSizePx + firstVisible.offset).coerceAtLeast(0f)
        } else {
            0f
        }
        (scrolledPx / scrollRange).coerceIn(0f, 1f)
    } else {
        0f
    }
    val onListDragToFraction = rememberLazyListScrollbarDragHandler(
        listState = listState,
        totalItems = totalItems,
        visibleCount = visibleItems.size,
        averageItemSizePx = averageItemSizePx
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play with...") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // Fixed-height contained list (extension-picker pattern) so the
                // dialog never changes size with the number of choices.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    if (rows.isEmpty()) {
                        Text(
                            text = "No compatible cores found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            // Short lists center so the fixed box is not top-hugged.
                            verticalArrangement = if (rows.size <= 7) {
                                Arrangement.Center
                            } else {
                                Arrangement.Top
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChangedDeferred { listViewportHeightPx = it.height.toFloat() }
                                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 18.dp)
                        ) {
                            items(items = rows, key = { it }) { decoderName ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selected = decoderName }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selected == decoderName,
                                        onClick = { selected = decoderName },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = decoderName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (hasScrollableResults && listViewportHeightPx > 0f) {
                            VerticalScrollbarTrack(
                                thumbFraction = thumbFraction,
                                offsetFraction = offsetFraction,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(10.dp)
                                    .fillMaxHeight(),
                                onDragFractionChanged = { onListDragToFraction(it) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                PlayWithOptionRow(
                    label = "Show all cores",
                    checked = showAll,
                    onCheckedChange = { showAll = it }
                )
                PlayWithOptionRow(
                    label = "Always prioritize this core",
                    checked = alwaysPrioritize && prioritizeAllowed,
                    enabled = prioritizeAllowed,
                    onCheckedChange = { alwaysPrioritize = it }
                )
                if (showDontAskAgain) {
                    PlayWithOptionRow(
                        label = "Don't ask again",
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    val chosen = selected ?: return@TextButton
                    if (alwaysPrioritize && prioritizeAllowed) {
                        if (promoteDecoderForFile(file, chosen, prefs)) {
                            Toast.makeText(
                                context,
                                "Prioritized $chosen for this format",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else if (chosen != claimants.firstOrNull()) {
                        NativeBridge.prioritizeNextOpenWith(chosen)
                    }
                    if (dontAskAgain) {
                        prefs.edit()
                            .putBoolean(AppPreferenceKeys.PLAY_WITH_EXTERNAL_OPEN_DIALOG, false)
                            .apply()
                    }
                    onPlay()
                }
            ) {
                Text("Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
