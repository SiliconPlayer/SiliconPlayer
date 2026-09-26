package com.flopster101.siliconplayer

import kotlinx.coroutines.CancellationException

internal sealed interface RemoteExportRequest {
    val sourceId: String
    val preferredFileName: String
}

internal data class HttpRemoteExportRequest(
    override val sourceId: String,
    val requestUrl: String,
    override val preferredFileName: String
) : RemoteExportRequest

internal data class SmbRemoteExportRequest(
    override val sourceId: String,
    val smbSpec: SmbSourceSpec,
    override val preferredFileName: String
) : RemoteExportRequest

internal class RemoteExportCancelledException(
    message: String = "Cancelled"
) : CancellationException(message)
