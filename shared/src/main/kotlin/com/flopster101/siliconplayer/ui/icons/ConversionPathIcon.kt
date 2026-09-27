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
internal val ConversionPathIcon: ImageVector
    get() {
        if (_conversionPath != null) {
            return _conversionPath!!
        }
        _conversionPath = ImageVector.Builder(
            name = "ConversionPath",
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
                moveTo(19f, 21f)
                quadToRelative(-0.97f, 0f, -1.75f, -0.56f)
                quadTo(16.48f, 19.88f, 16.18f, 19f)
                horizontalLineTo(11f)
                quadTo(9.35f, 19f, 8.18f, 17.83f)
                reflectiveQuadTo(7f, 15f)
                reflectiveQuadTo(8.18f, 12.18f)
                reflectiveQuadTo(11f, 11f)
                horizontalLineToRelative(2f)
                quadToRelative(0.83f, 0f, 1.41f, -0.59f)
                reflectiveQuadTo(15f, 9f)
                quadTo(15f, 8.17f, 14.41f, 7.59f)
                reflectiveQuadTo(13f, 7f)
                horizontalLineTo(7.83f)
                quadTo(7.5f, 7.88f, 6.74f, 8.44f)
                reflectiveQuadTo(5f, 9f)
                quadTo(3.75f, 9f, 2.88f, 8.13f)
                reflectiveQuadTo(2f, 6f)
                reflectiveQuadTo(2.88f, 3.88f)
                reflectiveQuadTo(5f, 3f)
                quadTo(5.98f, 3f, 6.74f, 3.56f)
                reflectiveQuadTo(7.83f, 5f)
                horizontalLineTo(13f)
                quadToRelative(1.65f, 0f, 2.83f, 1.18f)
                reflectiveQuadTo(17f, 9f)
                reflectiveQuadToRelative(-1.17f, 2.82f)
                reflectiveQuadTo(13f, 13f)
                horizontalLineTo(11f)
                quadToRelative(-0.82f, 0f, -1.41f, 0.59f)
                quadTo(9f, 14.18f, 9f, 15f)
                reflectiveQuadToRelative(0.59f, 1.41f)
                reflectiveQuadTo(11f, 17f)
                horizontalLineToRelative(5.18f)
                quadToRelative(0.32f, -0.88f, 1.09f, -1.44f)
                quadTo(18.03f, 15f, 19f, 15f)
                quadToRelative(1.25f, 0f, 2.13f, 0.88f)
                reflectiveQuadTo(22f, 18f)
                reflectiveQuadToRelative(-0.88f, 2.13f)
                reflectiveQuadTo(19f, 21f)
                close()
                moveTo(5.71f, 6.71f)
                quadTo(6f, 6.43f, 6f, 6f)
                reflectiveQuadTo(5.71f, 5.29f)
                reflectiveQuadTo(5f, 5f)
                quadTo(4.58f, 5f, 4.29f, 5.29f)
                reflectiveQuadTo(4f, 6f)
                reflectiveQuadTo(4.29f, 6.71f)
                reflectiveQuadTo(5f, 7f)
                reflectiveQuadTo(5.71f, 6.71f)
                close()
            }
        }.build()
        return _conversionPath!!
    }

private var _conversionPath: ImageVector? = null
