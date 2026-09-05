package com.flopster101.siliconplayer

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun isWatchDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
}

internal val Configuration.isRoundScreenCompat: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isScreenRound()

@Composable
internal fun Modifier.onSizeChangedDeferred(onSizeChanged: (IntSize) -> Unit): Modifier {
    val view = LocalView.current
    return onSizeChanged { size ->
        view.post { onSizeChanged(size) }
    }
}

@Composable
internal fun Modifier.onGloballyPositionedDeferred(onPositioned: (LayoutCoordinates) -> Unit): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coords ->
        view.post {
            // The deferred run can land after the node detached (e.g. the
            // screen was popped during the post), where coordinate queries
            // would throw IllegalStateException.
            if (coords.isAttached) onPositioned(coords)
        }
    }
}

@Composable
internal fun WatchDialogContainer(
    title: String? = null,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isRound = configuration.isRoundScreenCompat || configuration.screenWidthDp == configuration.screenHeightDp
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = if (isRound) 14.dp else 10.dp,
                        end = if (isRound) 14.dp else 10.dp,
                        top = if (isRound) 24.dp else 12.dp,
                        bottom = if (isRound) 48.dp else 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .then(
                                if (isRound) Modifier.widthIn(max = 120.dp) else Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                    )
                }
                content()
            }
        }
    }
}

@Composable
internal fun adaptiveDialogModifier(): Modifier {
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val configuration = LocalConfiguration.current
    val isRound = configuration.isRoundScreenCompat

    if (isWatch) {
        return Modifier.fillMaxWidth(if (isRound) 0.94f else 0.96f)
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (!isLandscape) {
        // Portrait baseline matches standard MD3 dialog margins.
        return Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 520.dp)
    }

    val landscapeWidthFraction = when {
        configuration.screenWidthDp >= 1400 -> 0.50f
        configuration.screenWidthDp >= 1100 -> 0.56f
        configuration.screenWidthDp >= 840 -> 0.62f
        else -> 0.68f
    }
    val landscapeMaxWidth = when {
        configuration.screenWidthDp >= 1400 -> 900.dp
        configuration.screenWidthDp >= 1100 -> 860.dp
        else -> 800.dp
    }

    return Modifier
        .fillMaxWidth(landscapeWidthFraction)
        .widthIn(max = landscapeMaxWidth)
}

internal fun adaptiveDialogProperties(): DialogProperties {
    return DialogProperties(usePlatformDefaultWidth = false)
}
