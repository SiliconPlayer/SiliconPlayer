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
internal val ResetSettingsIcon: ImageVector
    get() {
        if (_resetSettings != null) return _resetSettings!!
        _resetSettings = ImageVector.Builder(
            name = "reset_settings",
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
                moveTo(13f, 15.75f)
                verticalLineToRelative(-1.5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(13f)
                close()
                moveTo(14.5f, 21f)
                verticalLineTo(19.75f)
                horizontalLineTo(13f)
                verticalLineToRelative(-1.5f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(17f)
                horizontalLineTo(16f)
                verticalLineTo(21f)
                horizontalLineTo(14.5f)
                close()
                moveTo(17f, 19.75f)
                verticalLineToRelative(-1.5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(17f)
                close()
                moveTo(18f, 17f)
                verticalLineTo(13f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.25f)
                horizontalLineTo(21f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(19.5f)
                verticalLineTo(17f)
                horizontalLineTo(18f)
                close()
                moveToRelative(2.78f, -7f)
                horizontalLineTo(18.7f)
                quadTo(18.05f, 7.8f, 16.23f, 6.4f)
                reflectiveQuadTo(12f, 5f)
                quadTo(9.08f, 5f, 7.04f, 7.04f)
                reflectiveQuadTo(5f, 12f)
                quadToRelative(0f, 1.8f, 0.81f, 3.3f)
                reflectiveQuadTo(8f, 17.75f)
                verticalLineTo(15f)
                horizontalLineToRelative(2f)
                verticalLineTo(21f)
                horizontalLineTo(4f)
                verticalLineTo(19f)
                horizontalLineTo(6.35f)
                quadTo(4.8f, 17.75f, 3.9f, 15.94f)
                reflectiveQuadTo(3f, 12f)
                quadTo(3f, 10.13f, 3.71f, 8.49f)
                reflectiveQuadTo(5.64f, 5.64f)
                quadTo(6.85f, 4.42f, 8.49f, 3.71f)
                reflectiveQuadTo(12f, 3f)
                quadToRelative(3.23f, 0f, 5.66f, 1.99f)
                quadTo(20.1f, 6.97f, 20.78f, 10f)
                close()
            }
        }.build()
        return _resetSettings!!
    }

private var _resetSettings: ImageVector? = null
