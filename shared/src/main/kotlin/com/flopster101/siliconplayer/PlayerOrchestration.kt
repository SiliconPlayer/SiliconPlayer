@file:JvmName("SharedPlayerOrchestrationKt")
package com.flopster101.siliconplayer

import java.io.File

internal fun currentTrackIndexForList(
    selectedFile: File?,
    visiblePlayableFiles: List<File>
): Int {
    val currentPath = selectedFile?.absolutePath ?: return -1
    return visiblePlayableFiles.indexOfFirst { it.absolutePath == currentPath }
}

internal fun adjacentTrackForOffset(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    offset: Int
): File? {
    val index = currentTrackIndexForList(selectedFile, visiblePlayableFiles)
    if (index < 0) return null
    val targetIndex = index + offset
    if (targetIndex !in visiblePlayableFiles.indices) return null
    return visiblePlayableFiles[targetIndex]
}

internal fun shouldRestartCurrentTrackOnPrevious(
    previousRestartsAfterThreshold: Boolean,
    hasTrackLoaded: Boolean,
    positionSeconds: Double
): Boolean {
    return previousRestartsAfterThreshold &&
        hasTrackLoaded &&
        positionSeconds > PREVIOUS_RESTART_THRESHOLD_SECONDS
}

// Single definition of the prev/next transport decision: multi-subtune
// selections step through subtunes, plain tracks step through the queue.
internal data class SubtuneTransportState(
    val hasTrack: Boolean = false,
    val currentSubtuneIndex: Int = 0,
    val subtuneCount: Int = 0,
    val canPreviousSubtune: Boolean = false,
    val canNextSubtune: Boolean = false,
    val canPreviousTrack: Boolean = false,
    val canNextTrack: Boolean = false,
    val previousRestartsAfterThreshold: Boolean = true
) {
    val useSubtuneTransport: Boolean get() = subtuneCount > 1
    val hasSubtuneBefore: Boolean
        get() = useSubtuneTransport && currentSubtuneIndex > 0 && canPreviousSubtune
    val hasSubtuneAfter: Boolean
        get() = useSubtuneTransport && currentSubtuneIndex < subtuneCount - 1 && canNextSubtune
    val previousEnabled: Boolean
        get() = if (useSubtuneTransport) hasTrack else hasTrack && canPreviousTrack
    val nextEnabled: Boolean
        get() = if (useSubtuneTransport) hasTrack else hasTrack && canNextTrack
    fun previousWantsRestart(positionSeconds: Double): Boolean {
        return useSubtuneTransport && shouldRestartCurrentTrackOnPrevious(
            previousRestartsAfterThreshold = previousRestartsAfterThreshold,
            hasTrackLoaded = hasTrack,
            positionSeconds = positionSeconds
        )
    }
}

internal fun pluginNameForCoreName(coreName: String?): String? {
    return canonicalDecoderNameForAlias(coreName)
}

internal fun canOpenCoreSettingsForDecoder(decoderName: String?): Boolean {
    return pluginNameForCoreName(decoderName) != null
}
