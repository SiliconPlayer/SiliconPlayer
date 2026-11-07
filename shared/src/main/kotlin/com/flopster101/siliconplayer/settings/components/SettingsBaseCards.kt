package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.theme.Radius
import com.flopster101.siliconplayer.platform.LocalIsWatchDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private enum class SettingsRowRole {
    Standalone,
    Top,
    Middle,
    Bottom
}

private sealed interface SettingsRowSequenceEntry {
    val token: Any
    var top: Float?

    data class Row(
        override val token: Any,
        val roleState: MutableState<SettingsRowRole>,
        override var top: Float? = null
    ) : SettingsRowSequenceEntry

    data class Spacer(
        override val token: Any,
        override var top: Float? = null
    ) : SettingsRowSequenceEntry

    data class SectionBoundary(
        override val token: Any,
        override var top: Float? = null
    ) : SettingsRowSequenceEntry
}

private class SettingsRowSequencer {
    private val entries = mutableListOf<SettingsRowSequenceEntry>()
    private val claimedSections = mutableMapOf<String, Boolean>()
    private var hasClaimedFirstSection = false

    fun claimFirstSection(text: String): Boolean {
        claimedSections[text]?.let { return it }
        val first = !hasClaimedFirstSection
        hasClaimedFirstSection = true
        claimedSections[text] = first
        return first
    }

    fun onSectionBoundary(token: Any) {
        addEntry(SettingsRowSequenceEntry.SectionBoundary(token))
    }

    fun onRow(token: Any, roleState: MutableState<SettingsRowRole>) {
        addEntry(SettingsRowSequenceEntry.Row(token, roleState))
    }

    fun onSpacer(token: Any) {
        addEntry(SettingsRowSequenceEntry.Spacer(token))
    }

    fun onPositioned(token: Any, top: Float) {
        val entry = entries.firstOrNull { it.token === token } ?: return
        if (entry.top == top) return
        entry.top = top
        updateRowRoles()
    }

    fun onDisposed(token: Any) {
        if (entries.removeAll { it.token === token }) {
            updateRowRoles()
        }
    }

    private fun addEntry(entry: SettingsRowSequenceEntry) {
        entries.removeAll { it.token === entry.token }
        entries += entry
        updateRowRoles()
    }

    private fun updateRowRoles() {
        var previousRow: SettingsRowSequenceEntry.Row? = null
        var hasSpacer = false
        entries.sortedBy { it.top ?: Float.MAX_VALUE }.forEach { entry ->
            when (entry) {
                is SettingsRowSequenceEntry.SectionBoundary -> {
                    previousRow = null
                    hasSpacer = false
                }
                is SettingsRowSequenceEntry.Spacer -> {
                    if (previousRow != null) hasSpacer = true
                }
                is SettingsRowSequenceEntry.Row -> {
                    val previous = previousRow
                    if (previous != null && hasSpacer) {
                        previous.roleState.value = when (previous.roleState.value) {
                            SettingsRowRole.Standalone -> SettingsRowRole.Top
                            SettingsRowRole.Bottom -> SettingsRowRole.Middle
                            SettingsRowRole.Top -> SettingsRowRole.Top
                            SettingsRowRole.Middle -> SettingsRowRole.Middle
                        }
                        entry.roleState.value = SettingsRowRole.Bottom
                    } else {
                        entry.roleState.value = SettingsRowRole.Standalone
                    }
                    previousRow = entry
                    hasSpacer = false
                }
            }
        }
    }
}

private val LocalSettingsRowSequencer = compositionLocalOf<SettingsRowSequencer?> { null }
private val SettingsOuterCorner = Radius.PanelSize
private val SettingsInnerCorner = Radius.InnerSize
private val SettingsInterRowSpacing = 2.dp
private val SettingsSectionLabelStartInset = 8.dp
private val SettingsSectionTopSpacing = 20.dp
private val SettingsSectionBottomSpacing = 8.dp

// Overridable so the Android theme can use the rounded font style
internal val LocalSectionLabelStyle = compositionLocalOf<androidx.compose.ui.text.TextStyle?> { null }
internal val LocalSectionHeaderStyle = compositionLocalOf<androidx.compose.ui.text.TextStyle?> { null }

@Composable
internal fun SettingsRowsHost(content: @Composable () -> Unit) {
    val sequencer = remember { SettingsRowSequencer() }
    CompositionLocalProvider(LocalSettingsRowSequencer provides sequencer) {
        content()
    }
}

@Composable
internal fun SettingsRowContainer(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isWatch = LocalIsWatchDevice.current
    val outerCorner = if (isWatch) 16.dp else SettingsOuterCorner
    val sequencer = LocalSettingsRowSequencer.current
    val sequenceToken = remember { Any() }
    val rowRole = remember { mutableStateOf(SettingsRowRole.Standalone) }
    DisposableEffect(sequencer) {
        sequencer?.onRow(sequenceToken, rowRole)
        onDispose { sequencer?.onDisposed(sequenceToken) }
    }
    val shape = when (rowRole.value) {
        SettingsRowRole.Standalone -> RoundedCornerShape(outerCorner)
        SettingsRowRole.Top -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = SettingsInnerCorner,
            bottomEnd = SettingsInnerCorner
        )
        SettingsRowRole.Middle -> RoundedCornerShape(SettingsInnerCorner)
        SettingsRowRole.Bottom -> RoundedCornerShape(
            topStart = SettingsInnerCorner,
            topEnd = SettingsInnerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                sequencer?.onPositioned(sequenceToken, coordinates.positionInRoot().y)
            }
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isWatch) 12.dp else 16.dp,
                    vertical = if (isWatch) 9.dp else 13.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun GroupedRowCard(
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val shape = Radius.cardSegment(isFirst = isFirst, isLast = isLast)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        content()
    }
}

@Composable
internal fun SettingsRowSpacer() {
    val sequencer = LocalSettingsRowSequencer.current
    val sequenceToken = remember { Any() }
    DisposableEffect(sequencer) {
        sequencer?.onSpacer(sequenceToken)
        onDispose { sequencer?.onDisposed(sequenceToken) }
    }
    Spacer(
        modifier = Modifier
            .height(SettingsInterRowSpacing)
            .onGloballyPositioned { coordinates ->
                sequencer?.onPositioned(sequenceToken, coordinates.positionInRoot().y)
            }
    )
}

@Composable
internal fun SettingsSectionLabel(text: String, forceTopSpacing: Boolean = false) {
    val isWatch = LocalIsWatchDevice.current
    val sequencer = LocalSettingsRowSequencer.current
    val sequenceToken = remember { Any() }
    val isFirstSection = remember(sequencer) { sequencer?.claimFirstSection(text) ?: true }
    DisposableEffect(sequencer, text) {
        sequencer?.onSectionBoundary(sequenceToken)
        onDispose { sequencer?.onDisposed(sequenceToken) }
    }
    if (forceTopSpacing || !isFirstSection) {
        Spacer(modifier = Modifier.height(SettingsSectionTopSpacing))
    }
    Text(
        text = text,
        style = if (isWatch) MaterialTheme.typography.labelMedium else LocalSectionLabelStyle.current ?: MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = SettingsSectionLabelStartInset)
            .onGloballyPositioned { coordinates ->
                sequencer?.onPositioned(sequenceToken, coordinates.positionInRoot().y)
            }
    )
    Spacer(modifier = Modifier.height(SettingsSectionBottomSpacing))
}

@Composable
internal fun SettingsItemCard(
    title: String,
    description: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    titleColor: Color? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val isWatch = LocalIsWatchDevice.current
    val contentAlpha = if (enabled) 1f else 0.38f
    SettingsRowContainer(onClick = onClick, enabled = enabled) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 12.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = if (isWatch) Modifier.size(20.dp) else Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (isWatch) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = (titleColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = contentAlpha)
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 12.dp))
            trailingContent()
        } else if (showChevron) {
            Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = if (isWatch) Modifier.size(20.dp) else Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun SettingsValuePickerCard(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val isWatch = LocalIsWatchDevice.current
    val contentAlpha = if (enabled) 1f else 0.38f
    SettingsRowContainer(
        onClick = onClick,
        enabled = enabled
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (isWatch) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
        Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 12.dp))
        Text(
            text = value,
            style = if (isWatch) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        )
    }
}

@Composable
internal fun PlayerSettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    descriptionColor: Color? = null,
    badgeText: String? = null,
    errorText: String? = null
) {
    val isWatch = LocalIsWatchDevice.current
    var localChecked by remember(title) { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        localChecked = checked
    }

    SettingsRowContainer(
        onClick = {
            if (!enabled) return@SettingsRowContainer
            val toggled = !localChecked
            localChecked = toggled
            onCheckedChange(toggled)
        },
        enabled = enabled
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (isWatch) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val targetDescColor = descriptionColor ?: if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = targetDescColor
                )
            }
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            if (!errorText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Error: $errorText",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 12.dp))
        Switch(
            checked = localChecked,
            onCheckedChange = { newValue ->
                if (!enabled) return@Switch
                localChecked = newValue
                onCheckedChange(newValue)
            },
            enabled = enabled
        )
    }
}
