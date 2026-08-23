package com.flopster101.siliconplayer

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScaffoldShell(
    route: SettingsRoute,
    selectedPluginName: String?,
    pluginPriorityEditMode: Boolean,
    onBack: () -> Unit,
    onTogglePluginPriorityEditMode: () -> Unit,
    onRequestPluginReset: (String) -> Unit,
    onResetVisualizationBarsSettings: () -> Unit,
    onResetVisualizationOscilloscopeSettings: () -> Unit,
    onResetVisualizationVuSettings: () -> Unit,
    onResetVisualizationChannelScopeSettings: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val isRound = LocalConfiguration.current.isScreenRound
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val titleContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                val enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 4 },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 210,
                        delayMillis = 60,
                        easing = LinearOutSlowInEasing
                    )
                )
                val exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutLinearInEasing
                    )
                )
                enter togetherWith exit
            },
            label = "settingsTopBarTitle"
        ) { targetRoute ->
            val topTitle = settingsSecondaryTitle(targetRoute, selectedPluginName) ?: "Settings"
            Text(
                text = topTitle,
                style = if (isWatch) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    val navigationIconContent: @Composable () -> Unit = {
        IconButton(
            onClick = onBack,
            modifier = if (isWatch) Modifier.size(32.dp) else Modifier
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = if (isWatch) Modifier.size(18.dp) else Modifier.size(24.dp)
            )
        }
    }

    val actionsContent: @Composable RowScope.() -> Unit = {
        if (route == SettingsRoute.AudioPlugins) {
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = if (pluginPriorityEditMode) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            ) {
                IconButton(
                    onClick = onTogglePluginPriorityEditMode,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = if (pluginPriorityEditMode) "Finish reorder mode" else "Edit core order",
                        tint = if (pluginPriorityEditMode) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else if (route == SettingsRoute.PluginDetail && selectedPluginName != null) {
            IconButton(
                onClick = { onRequestPluginReset(selectedPluginName) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Reset core settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else if (
            route == SettingsRoute.VisualizationBasicBars ||
            route == SettingsRoute.VisualizationBasicOscilloscope ||
            route == SettingsRoute.VisualizationBasicVuMeters ||
            route == SettingsRoute.VisualizationAdvancedChannelScope
        ) {
            IconButton(
                onClick = {
                    when (route) {
                        SettingsRoute.VisualizationBasicBars -> onResetVisualizationBarsSettings()
                        SettingsRoute.VisualizationBasicOscilloscope -> onResetVisualizationOscilloscopeSettings()
                        SettingsRoute.VisualizationBasicVuMeters -> onResetVisualizationVuSettings()
                        SettingsRoute.VisualizationAdvancedChannelScope -> onResetVisualizationChannelScopeSettings()
                        else -> Unit
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Reset visualization settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    Scaffold(
        modifier = if (isWatch) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isWatch) {
                val topTitle = settingsSecondaryTitle(route, selectedPluginName) ?: "Settings"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (isRound) 24.dp else 10.dp,
                            start = if (isRound) 24.dp else 12.dp,
                            end = if (isRound) 24.dp else 12.dp,
                            bottom = 6.dp
                        )
                ) {
                    Text(
                        text = topTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onBack)
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        actionsContent()
                    }
                }
            } else {
                LargeTopAppBar(
                    title = titleContent,
                    navigationIcon = navigationIconContent,
                    actions = actionsContent,
                    scrollBehavior = scrollBehavior
                )
            }
        },
        content = content
    )
}
