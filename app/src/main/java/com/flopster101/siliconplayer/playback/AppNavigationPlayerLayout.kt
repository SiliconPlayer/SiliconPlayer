package com.flopster101.siliconplayer

import android.content.pm.PackageManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberMiniPlayerListInset(
    currentView: MainView,
    isPlayerSurfaceVisible: Boolean
): Dp {
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val isRound = LocalConfiguration.current.isRoundScreenCompat
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
