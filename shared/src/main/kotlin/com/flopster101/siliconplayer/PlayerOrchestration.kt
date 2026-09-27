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

internal fun pluginNameForCoreName(coreName: String?): String? {
    return canonicalDecoderNameForAlias(coreName)
}

internal fun canOpenCoreSettingsForDecoder(decoderName: String?): Boolean {
    return pluginNameForCoreName(decoderName) != null
}
