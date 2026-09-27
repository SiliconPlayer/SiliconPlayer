package com.flopster101.siliconplayer.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.platform.AudioInspectorSupport
import com.flopster101.siliconplayer.platform.BitPerfectSectionParams

// Desktop has no USB/bit-perfect routing UI; the inspector renders the shared
// signal chain and stream metrics with material-icon artwork fallbacks.
internal object DesktopAudioInspectorSupport : AudioInspectorSupport {
    override val inactiveBackendLabel: String = "miniaudio"
    override val fileArtworkIcon: @Composable (DecoderArtworkHint?) -> Unit = @Composable { _ ->
        Icon(
            imageVector = Icons.Default.AudioFile,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
    override val decoderNodeIcon: @Composable () -> Unit = @Composable {
        Icon(
            imageVector = Icons.Default.GraphicEq,
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
