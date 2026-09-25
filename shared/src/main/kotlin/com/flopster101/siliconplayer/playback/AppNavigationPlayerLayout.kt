package com.flopster101.siliconplayer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.platform.LocalIsWatchDevice
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo

@Composable
internal fun rememberMiniPlayerListInset(
    currentView: MainView,
    isPlayerSurfaceVisible: Boolean
): Dp {
    val isWatch = LocalIsWatchDevice.current
    val isRound = LocalWindowSizeInfo.current.isRound
    val defaultInset = if (isWatch) (if (isRound) 70.dp else 52.dp) else 108.dp
    val target = when {
        currentView == MainView.Browser && isPlayerSurfaceVisible -> defaultInset
        currentView == MainView.Network && isPlayerSurfaceVisible -> defaultInset
        currentView == MainView.Playlists && isPlayerSurfaceVisible -> defaultInset
        currentView == MainView.Home && isPlayerSurfaceVisible -> defaultInset
        currentView == MainView.Settings && isPlayerSurfaceVisible -> defaultInset
        else -> 0.dp
    }
    return animateDpAsState(
        targetValue = target,
        label = "miniPlayerListInset"
    ).value
}

internal fun miniPlayerFabLift(bottomContentPadding: Dp): Dp {
    if (bottomContentPadding <= 0.dp) return 0.dp
    return bottomContentPadding * (70f / 108f)
}
