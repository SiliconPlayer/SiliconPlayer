package com.flopster101.siliconplayer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val GithubIcon: ImageVector
    get() {
        if (_github != null) {
            return _github!!
        }
        _github = ImageVector.Builder(
            name = "GithubIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12.0f, 2.0f)
                curveTo(6.477f, 2.0f, 2.0f, 6.484f, 2.0f, 12.017f)
                curveTo(2.0f, 16.442f, 4.865f, 20.197f, 8.839f, 21.521f)
                curveTo(9.339f, 21.613f, 9.521f, 21.304f, 9.521f, 21.038f)
                curveTo(9.521f, 20.801f, 9.513f, 20.17f, 9.508f, 19.335f)
                curveTo(6.726f, 19.94f, 6.139f, 17.992f, 6.139f, 17.992f)
                curveTo(5.685f, 16.834f, 5.029f, 16.526f, 5.029f, 16.526f)
                curveTo(4.121f, 15.906f, 5.098f, 15.918f, 5.098f, 15.918f)
                curveTo(6.101f, 15.988f, 6.628f, 16.95f, 6.628f, 16.95f)
                curveTo(7.52f, 18.48f, 8.969f, 18.038f, 9.538f, 17.782f)
                curveTo(9.63f, 17.135f, 9.888f, 16.694f, 10.174f, 16.444f)
                curveTo(7.954f, 16.191f, 5.619f, 15.331f, 5.619f, 11.493f)
                curveTo(5.619f, 10.4f, 6.009f, 9.505f, 6.648f, 8.805f)
                curveTo(6.545f, 8.552f, 6.202f, 7.533f, 6.746f, 6.155f)
                curveTo(6.746f, 6.155f, 7.586f, 5.885f, 9.496f, 7.181f)
                curveTo(10.294f, 6.959f, 11.144f, 6.848f, 11.994f, 6.844f)
                curveTo(12.844f, 6.848f, 13.694f, 6.959f, 14.494f, 7.181f)
                curveTo(16.403f, 5.885f, 17.241f, 6.155f, 17.241f, 6.155f)
                curveTo(17.787f, 7.533f, 17.443f, 8.552f, 17.341f, 8.805f)
                curveTo(17.981f, 9.505f, 18.369f, 10.4f, 18.369f, 11.493f)
                curveTo(18.369f, 15.341f, 16.03f, 16.188f, 13.803f, 16.436f)
                curveTo(14.162f, 16.745f, 14.481f, 17.356f, 14.481f, 18.291f)
                curveTo(14.481f, 19.629f, 14.469f, 20.71f, 14.469f, 21.038f)
                curveTo(14.469f, 21.306f, 14.649f, 21.618f, 15.157f, 21.52f)
                curveTo(19.129f, 20.193f, 21.992f, 16.44f, 21.992f, 12.017f)
                curveTo(21.992f, 6.484f, 17.515f, 2.0f, 12.0f, 2.0f)
                close()
            }
        }.build()
        return _github!!
    }

private var _github: ImageVector? = null
