package com.flopster101.siliconplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val IconDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBAC4FF),
    onPrimary = Color(0xFF11267D),
    primaryContainer = IconBlueDark,
    onPrimaryContainer = IconBlueLight,
    secondary = Color(0xFFE1B8F0),
    onSecondary = Color(0xFF45255A),
    secondaryContainer = IconLavenderDark,
    onSecondaryContainer = IconLavenderLight,
    tertiary = Color(0xFFFFB68B),
    onTertiary = Color(0xFF552009),
    tertiaryContainer = IconPeachDark,
    onTertiaryContainer = IconPeachLight,
    surfaceTint = Color(0xFFB7A8FF),
    background = IconNightDeep,
    onBackground = Color(0xFFE7E2F6),
    surface = IconNightSurface,
    onSurface = Color(0xFFE7E2F6),
    surfaceDim = IconNightDeep,
    surfaceBright = Color(0xFF444A69),
    surfaceContainerLowest = Color(0xFF141726),
    surfaceContainerLow = IconNightSurfaceLow,
    surfaceContainer = Color(0xFF2B3048),
    surfaceContainerHigh = IconNightSurfaceHigh,
    surfaceContainerHighest = IconNightSurfaceHighest,
    surfaceVariant = Color(0xFF484D69),
    onSurfaceVariant = Color(0xFFC8C5DD),
    outline = Color(0xFF9491AC),
    outlineVariant = Color(0xFF454A65)
)

val IconLightColorScheme = lightColorScheme(
    primary = Color(0xFF4A5FBE),
    onPrimary = Color.White,
    primaryContainer = IconBlueLight,
    onPrimaryContainer = Color(0xFF08174A),
    secondary = Color(0xFF745089),
    onSecondary = Color.White,
    secondaryContainer = IconLavenderLight,
    onSecondaryContainer = Color(0xFF2D123D),
    tertiary = Color(0xFF92522E),
    onTertiary = Color.White,
    tertiaryContainer = IconPeachLight,
    onTertiaryContainer = Color(0xFF351000),
    surfaceTint = Color(0xFF6D63C8),
    background = IconDayBackground,
    onBackground = Color(0xFF20233A),
    surface = IconDaySurface,
    onSurface = Color(0xFF20233A),
    surfaceDim = Color(0xFFB9C4EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = IconDaySurfaceLow,
    surfaceContainer = Color(0xFFCFD7FC),
    surfaceContainerHigh = IconDaySurfaceHigh,
    surfaceContainerHighest = IconDaySurfaceHighest,
    surfaceVariant = Color(0xFFC1CCF2),
    onSurfaceVariant = Color(0xFF4A5880),
    outline = Color(0xFF6475A8),
    outlineVariant = Color(0xFFA5B3E2)
)

val SiliconPlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SiliconPlayerBaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = if (darkTheme) IconDarkColorScheme else IconLightColorScheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SiliconPlayerShapes,
        content = content
    )
}
