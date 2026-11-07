package com.flopster101.siliconplayer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollingTopAppBarTitle(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    collapsedStart: Dp = 56.dp,
    endPadding: Dp = 64.dp,
    title: @Composable () -> Unit
) {
    val fraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val style = lerpTextStyle(
        MaterialTheme.typography.headlineMedium,
        MaterialTheme.typography.titleLarge,
        fraction
    )
    val bottomCenter = lerpDp(38.dp, 32.dp, fraction)
    val start = lerpDp(16.dp, collapsedStart, fraction)
    Box(
        modifier = modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            ProvideTextStyle(style) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = start, end = endPadding)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.place(
                                    0,
                                    -(bottomCenter.roundToPx() - placeable.height / 2)
                                )
                            }
                        }
                ) {
                    title()
                }
            }
        }
    }
}
