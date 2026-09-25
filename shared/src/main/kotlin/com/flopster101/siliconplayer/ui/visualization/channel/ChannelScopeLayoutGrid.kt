package com.flopster101.siliconplayer.ui.visualization.channel

import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import kotlin.math.ceil

fun resolveChannelGrid(channels: Int, strategy: VisualizationChannelScopeLayout): Pair<Int, Int> {
    if (channels <= 1) return 1 to 1
    return when (strategy) {
        VisualizationChannelScopeLayout.ColumnFirst -> {
            val cols = when {
                channels <= 4 -> 1
                channels <= 12 -> 2
                channels <= 24 -> 3
                else -> 4
            }
            val rows = ceil(channels / cols.toDouble()).toInt().coerceAtLeast(1)
            cols to rows
        }
        VisualizationChannelScopeLayout.BalancedTwoColumn -> {
            val rows = ceil(channels / 2.0).toInt().coerceAtLeast(1)
            2 to rows
        }
    }
}
