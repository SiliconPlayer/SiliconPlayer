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
internal val PlaceholderTrackerChipIcon: ImageVector
    get() {
        if (_placeholderTrackerChip != null) {
            return _placeholderTrackerChip!!
        }
        _placeholderTrackerChip = ImageVector.Builder(
            name = "PlaceholderTrackerChipIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960.0f,
            viewportHeight = 960.0f
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
                moveTo(240.0f, 600.0f)
                horizontalLineTo(320.0f)
                verticalLineTo(360.0f)
                horizontalLineTo(240.0f)
                verticalLineTo(600.0f)
                close()
                moveTo(440.0f, 600.0f)
                horizontalLineTo(520.0f)
                verticalLineTo(360.0f)
                horizontalLineTo(440.0f)
                verticalLineTo(600.0f)
                close()
                moveTo(640.0f, 600.0f)
                horizontalLineTo(720.0f)
                verticalLineTo(360.0f)
                horizontalLineTo(640.0f)
                verticalLineTo(600.0f)
                close()
                moveTo(160.0f, 680.0f)
                horizontalLineTo(800.0f)
                verticalLineTo(280.0f)
                horizontalLineTo(160.0f)
                verticalLineTo(680.0f)
                close()
                moveTo(160.0f, 680.0f)
                verticalLineTo(280.0f)
                verticalLineTo(680.0f)
                close()
                moveTo(200.0f, 840.0f)
                verticalLineTo(760.0f)
                horizontalLineTo(160.0f)
                quadTo(127.0f, 760.0f, 103.5f, 736.5f)
                reflectiveQuadTo(80.0f, 680.0f)
                verticalLineTo(280.0f)
                quadTo(80.0f, 247.0f, 103.5f, 223.5f)
                reflectiveQuadTo(160.0f, 200.0f)
                horizontalLineTo(200.0f)
                verticalLineTo(120.0f)
                horizontalLineTo(280.0f)
                verticalLineTo(200.0f)
                horizontalLineTo(440.0f)
                verticalLineTo(120.0f)
                horizontalLineTo(520.0f)
                verticalLineTo(200.0f)
                horizontalLineTo(680.0f)
                verticalLineTo(120.0f)
                horizontalLineTo(760.0f)
                verticalLineTo(200.0f)
                horizontalLineTo(800.0f)
                quadTo(833.0f, 200.0f, 856.5f, 223.5f)
                reflectiveQuadTo(880.0f, 280.0f)
                verticalLineTo(680.0f)
                quadTo(880.0f, 713.0f, 856.5f, 736.5f)
                reflectiveQuadTo(800.0f, 760.0f)
                horizontalLineTo(760.0f)
                verticalLineTo(840.0f)
                horizontalLineTo(680.0f)
                verticalLineTo(760.0f)
                horizontalLineTo(520.0f)
                verticalLineTo(840.0f)
                horizontalLineTo(440.0f)
                verticalLineTo(760.0f)
                horizontalLineTo(280.0f)
                verticalLineTo(840.0f)
                horizontalLineTo(200.0f)
                close()
            }
        }.build()
        return _placeholderTrackerChip!!
    }

private var _placeholderTrackerChip: ImageVector? = null
