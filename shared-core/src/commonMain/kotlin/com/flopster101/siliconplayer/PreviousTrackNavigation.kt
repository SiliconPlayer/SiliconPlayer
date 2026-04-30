package com.flopster101.siliconplayer

const val PREVIOUS_RESTART_THRESHOLD_SECONDS = 3.0

enum class PreviousTrackAction {
    RestartCurrent,
    PlayPreviousTrack,
    NoAction
}

fun shouldRestartCurrentTrackOnPrevious(
    previousRestartsAfterThreshold: Boolean,
    hasTrackLoaded: Boolean,
    positionSeconds: Double
): Boolean {
    return previousRestartsAfterThreshold &&
        hasTrackLoaded &&
        positionSeconds > PREVIOUS_RESTART_THRESHOLD_SECONDS
}

fun resolvePreviousTrackAction(
    previousRestartsAfterThreshold: Boolean,
    hasTrackLoaded: Boolean,
    positionSeconds: Double,
    hasPreviousTrack: Boolean
): PreviousTrackAction {
    val shouldRestartCurrent = shouldRestartCurrentTrackOnPrevious(
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        hasTrackLoaded = hasTrackLoaded,
        positionSeconds = positionSeconds
    )
    if (shouldRestartCurrent) return PreviousTrackAction.RestartCurrent
    if (hasPreviousTrack) return PreviousTrackAction.PlayPreviousTrack
    if (hasTrackLoaded) return PreviousTrackAction.RestartCurrent
    return PreviousTrackAction.NoAction
}
