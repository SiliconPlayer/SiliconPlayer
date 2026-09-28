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
internal val SettingsApplicationsIcon: ImageVector
    get() {
        if (_settingsApplications != null) {
            return _settingsApplications!!
        }
        _settingsApplications = ImageVector.Builder(
            name = "SettingsApplicationsIcon",
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
                moveTo(12.0f, 10.0f)
                curveTo(10.9f, 10.0f, 10.0f, 10.9f, 10.0f, 12.0f)
                reflectiveCurveTo(10.9f, 14.0f, 12.0f, 14.0f)
                reflectiveCurveTo(14.0f, 13.1f, 14.0f, 12.0f)
                reflectiveCurveTo(13.1f, 10.0f, 12.0f, 10.0f)
                close()
                moveTo(19.0f, 3.0f)
                horizontalLineTo(5.0f)
                curveTo(3.8899999999999997f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
                verticalLineTo(19.0f)
                curveTo(3.0f, 20.1f, 3.89f, 21.0f, 5.0f, 21.0f)
                horizontalLineTo(19.0f)
                curveTo(20.11f, 21.0f, 21.0f, 20.1f, 21.0f, 19.0f)
                verticalLineTo(5.0f)
                curveTo(21.0f, 3.9f, 20.11f, 3.0f, 19.0f, 3.0f)
                close()
                moveTo(17.25f, 12.0f)
                curveTo(17.25f, 12.23f, 17.23f, 12.46f, 17.2f, 12.68f)
                lineTo(18.68f, 13.84f)
                curveTo(18.81f, 13.95f, 18.85f, 14.14f, 18.759999999999998f, 14.29f)
                lineTo(17.36f, 16.71f)
                curveTo(17.27f, 16.86f, 17.09f, 16.92f, 16.93f, 16.86f)
                lineTo(15.19f, 16.16f)
                curveTo(14.83f, 16.44f, 14.43f, 16.67f, 14.01f, 16.85f)
                lineTo(13.75f, 18.700000000000003f)
                curveTo(13.72f, 18.870000000000005f, 13.57f, 19.000000000000004f, 13.4f, 19.000000000000004f)
                horizontalLineTo(10.600000000000001f)
                curveTo(10.430000000000001f, 19.000000000000004f, 10.280000000000001f, 18.870000000000005f, 10.250000000000002f, 18.710000000000004f)
                lineTo(9.990000000000002f, 16.860000000000003f)
                curveTo(9.560000000000002f, 16.680000000000003f, 9.170000000000002f, 16.450000000000003f, 8.810000000000002f, 16.17f)
                lineTo(7.070000000000002f, 16.87f)
                curveTo(6.910000000000002f, 16.93f, 6.730000000000002f, 16.87f, 6.640000000000002f, 16.720000000000002f)
                lineTo(5.240000000000002f, 14.300000000000002f)
                curveTo(5.150000000000002f, 14.150000000000002f, 5.190000000000002f, 13.960000000000003f, 5.320000000000002f, 13.850000000000003f)
                lineTo(6.8000000000000025f, 12.690000000000003f)
                curveTo(6.770000000000002f, 12.460000000000003f, 6.750000000000003f, 12.230000000000002f, 6.750000000000003f, 12.000000000000004f)
                curveTo(6.750000000000003f, 11.770000000000003f, 6.770000000000002f, 11.540000000000003f, 6.8000000000000025f, 11.320000000000004f)
                lineTo(5.320000000000002f, 10.160000000000004f)
                curveTo(5.190000000000002f, 10.050000000000004f, 5.150000000000002f, 9.860000000000003f, 5.240000000000002f, 9.710000000000004f)
                lineTo(6.640000000000002f, 7.2900000000000045f)
                curveTo(6.730000000000002f, 7.140000000000004f, 6.910000000000002f, 7.0800000000000045f, 7.070000000000002f, 7.140000000000004f)
                lineTo(8.810000000000002f, 7.840000000000004f)
                curveTo(9.170000000000002f, 7.560000000000004f, 9.570000000000002f, 7.3300000000000045f, 9.990000000000002f, 7.150000000000004f)
                lineTo(10.250000000000002f, 5.300000000000004f)
                curveTo(10.280000000000001f, 5.130000000000004f, 10.430000000000001f, 5.000000000000004f, 10.600000000000001f, 5.000000000000004f)
                horizontalLineTo(13.400000000000002f)
                curveTo(13.570000000000002f, 5.000000000000004f, 13.720000000000002f, 5.130000000000004f, 13.750000000000002f, 5.2900000000000045f)
                lineTo(14.010000000000002f, 7.140000000000004f)
                curveTo(14.440000000000001f, 7.320000000000004f, 14.830000000000002f, 7.550000000000004f, 15.190000000000001f, 7.830000000000004f)
                lineTo(16.93f, 7.1300000000000034f)
                curveTo(17.09f, 7.070000000000004f, 17.27f, 7.1300000000000034f, 17.36f, 7.280000000000004f)
                lineTo(18.759999999999998f, 9.700000000000003f)
                curveTo(18.849999999999998f, 9.850000000000003f, 18.81f, 10.040000000000003f, 18.68f, 10.150000000000002f)
                lineTo(17.2f, 11.310000000000002f)
                curveTo(17.23f, 11.540000000000003f, 17.25f, 11.770000000000003f, 17.25f, 12.000000000000002f)
                close()
            }
        }.build()
        return _settingsApplications!!
    }

private var _settingsApplications: ImageVector? = null
