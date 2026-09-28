package com.flopster101.siliconplayer.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.platform.AudioInspectorSupport
import com.flopster101.siliconplayer.platform.BitPerfectSectionParams
import com.flopster101.siliconplayer.ui.icons.FileGameIcon
import com.flopster101.siliconplayer.ui.icons.FileTrackedIcon
import com.flopster101.siliconplayer.ui.icons.PlaceholderTrackerChipIcon

// Desktop has no USB/bit-perfect routing UI; the inspector renders the shared
// signal chain and stream metrics with the shared file/chip artwork icons.
internal object DesktopAudioInspectorSupport : AudioInspectorSupport {
    override val inactiveBackendLabel: String = "miniaudio"
    override val fileArtworkIcon: @Composable (DecoderArtworkHint?) -> Unit = @Composable { hint ->
        Icon(
            imageVector = when (hint) {
                DecoderArtworkHint.TrackedFile -> FileTrackedIcon
                DecoderArtworkHint.GameFile -> FileGameIcon
                else -> Icons.Default.AudioFile
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
    override val decoderNodeIcon: @Composable () -> Unit = @Composable {
        Icon(
            imageVector = PlaceholderTrackerChipIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
    override val bitPerfectSection: @Composable (BitPerfectSectionParams) -> Unit = {}
    @Composable
    override fun rememberBitPerfectActive(bitPerfectEnabled: Boolean, routeIsUsb: Boolean): Boolean = false
    @Composable
    override fun rememberUsbSinkFormatText(): String? = null
}
