package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.ToastHandler
import com.flopster101.siliconplayer.settings.porting.PortingFileIo
import com.flopster101.siliconplayer.settings.porting.PortingFileSource
import com.flopster101.siliconplayer.settings.porting.PortingFileTarget
import com.flopster101.siliconplayer.settings.porting.PortingShareRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop settings porting IO via the XDG FileChooser portal. Without a
 * session bus the pickers return null, so the flow degrades to a toast.
 */
@Composable
internal fun rememberDesktopPortingFileIo(): PortingFileIo {
    val toastHandler = LocalToastHandler.current
    return remember(toastHandler) {
        object : PortingFileIo {
            override suspend fun pickExportTarget(defaultName: String): PortingFileTarget? {
                val path = withContext(Dispatchers.IO) {
                    DesktopPortingPortal.saveFile("Export settings as\u2026", defaultName)
                } ?: run { toastUnavailable(toastHandler); return null }
                return PortingFileTarget(path)
            }

            override suspend fun writeTarget(target: PortingFileTarget, data: ByteArray) {
                withContext(Dispatchers.IO) {
                    File(target.pathOrUri).writeBytes(data)
                }
            }

            override suspend fun pickImportSource(): PortingFileSource? {
                val path = withContext(Dispatchers.IO) {
                    DesktopPortingPortal.openFile("Import settings file (.spxs)")
                } ?: run { toastUnavailable(toastHandler); return null }
                val file = File(path)
                if (!file.isFile) {
                    toastHandler.showToast("Cannot read that settings file")
                    return null
                }
                return PortingFileSource(file.name, withContext(Dispatchers.IO) { file.readBytes() })
            }

            override suspend fun shareExport(portingData: PortingShareRequest) {
                // No desktop share sheet: the save dialog is the closest flow.
                val target = pickExportTarget(portingData.fileName) ?: return
                writeTarget(target, portingData.bytes)
                toastHandler.showToast("Saved ${portingData.fileName}")
            }

            override fun toast(message: String) {
                toastHandler.showToast(message)
            }
        }
    }
}

private fun toastUnavailable(toastHandler: ToastHandler) {
    toastHandler.showToast("No file picker available (requires XDG desktop portal)")
}
