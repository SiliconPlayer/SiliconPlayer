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
internal val FileExportIcon: ImageVector
    get() {
        if (_fileExport != null) return _fileExport!!
        _fileExport = ImageVector.Builder(
            name = "file_export",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 12f)
                close()
                moveTo(5.05f, 22.38f)
                lineTo(3.65f, 20.95f)
                lineTo(6.6f, 18f)
                horizontalLineTo(4.35f)
                verticalLineTo(16f)
                horizontalLineTo(10f)
                verticalLineTo(21.65f)
                horizontalLineTo(8f)
                verticalLineTo(19.43f)
                lineTo(5.05f, 22.38f)
                close()
                moveTo(12f, 22f)
                verticalLineTo(20f)
                horizontalLineToRelative(6f)
                verticalLineTo(9f)
                horizontalLineTo(13f)
                verticalLineTo(4f)
                horizontalLineTo(6f)
                verticalLineTo(14f)
                horizontalLineTo(4f)
                verticalLineTo(4f)
                quadTo(4f, 3.17f, 4.59f, 2.59f)
                reflectiveQuadTo(6f, 2f)
                horizontalLineToRelative(8f)
                lineToRelative(6f, 6f)
                verticalLineTo(20f)
                quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                reflectiveQuadTo(18f, 22f)
                horizontalLineTo(12f)
                close()
            }
        }.build()
        return _fileExport!!
    }

private var _fileExport: ImageVector? = null
