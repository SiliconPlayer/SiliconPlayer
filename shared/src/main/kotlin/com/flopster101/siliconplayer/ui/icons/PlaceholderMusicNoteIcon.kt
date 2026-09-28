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
internal val PlaceholderMusicNoteIcon: ImageVector
    get() {
        if (_placeholderMusicNote != null) {
            return _placeholderMusicNote!!
        }
        _placeholderMusicNote = ImageVector.Builder(
            name = "PlaceholderMusicNoteIcon",
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
                moveTo(12.0f, 3.0f)
                verticalLineTo(13.55f)
                curveTo(11.41f, 13.21f, 10.73f, 13.0f, 10.0f, 13.0f)
                curveTo(7.79f, 13.0f, 6.0f, 14.79f, 6.0f, 17.0f)
                reflectiveCurveTo(7.79f, 21.0f, 10.0f, 21.0f)
                reflectiveCurveTo(14.0f, 19.21f, 14.0f, 17.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(18.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(12.0f)
                close()
            }
        }.build()
        return _placeholderMusicNote!!
    }

private var _placeholderMusicNote: ImageVector? = null
