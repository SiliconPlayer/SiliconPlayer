package com.flopster101.siliconplayer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

internal sealed class DirectStreamOpenResult {
    data class Success(
        val snapshot: NativeTrackSnapshot,
        val decoderName: String?
    ) : DirectStreamOpenResult()
    data class Fallback(val reason: String) : DirectStreamOpenResult()
}

internal data class ManualRemoteOpenSuccess(
    val displayFile: File,
    val sourceId: String,
    val requestUrl: String,
    val snapshot: NativeTrackSnapshot,
    val decoderName: String?
)

internal sealed class ManualRemoteOpenResult {
    data class Success(val value: ManualRemoteOpenSuccess) : ManualRemoteOpenResult()
    object DownloadCancelled : ManualRemoteOpenResult()
    data class Failed(val reason: String) : ManualRemoteOpenResult()
}

internal suspend fun tryOpenDirectStreamForManualSource(
    requestUrl: String,
    initialSubtuneIndex: Int?,
    timeoutMs: Long = 20_000L
): DirectStreamOpenResult {
    return try {
        val openResult = withContext(Dispatchers.IO) {
            withTimeout(timeoutMs) {
                runWithNativeAudioSession {
                    loadTrackSnapshotForSelection(
                        path = requestUrl,
                        initialSubtuneIndex = initialSubtuneIndex
                    ).snapshot
                }
            }
        }
        val snapshot = openResult
        val decoderName = snapshot.decoderName
        if (snapshotAppearsValid(snapshot)) {
            DirectStreamOpenResult.Success(snapshot, decoderName)
        } else {
            DirectStreamOpenResult.Fallback("direct streaming returned no playable metadata")
        }
    } catch (_: TimeoutCancellationException) {
        DirectStreamOpenResult.Fallback("direct streaming timed out")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        DirectStreamOpenResult.Fallback(
            "direct streaming error (${t::class.java.simpleName}: ${t.message ?: "unknown"})"
        )
    }
}
