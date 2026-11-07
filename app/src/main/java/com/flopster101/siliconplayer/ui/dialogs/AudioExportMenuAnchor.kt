package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.audioExportMenuAnchor(
    scope: ExposedDropdownMenuBoxScope,
    enabled: Boolean
): Modifier = with(scope) {
    menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
}
