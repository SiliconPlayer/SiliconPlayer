package com.flopster101.siliconplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/*
 * Pixel devices use variable-*-emphasized families for their round font styles.
 * Let's use it to make the app look a bit nicer on devices that have these styles.
 * If the font family does not exist, Compose simply falls back to the default font.
 */
private fun TextStyle.emphasized(name: String): TextStyle = copy(
    fontFamily = FontFamily(Font(DeviceFontFamilyName(name), weight = fontWeight ?: FontWeight.Normal))
)

val ExpressiveTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.emphasized("variable-display-large-emphasized"),
        displayMedium = base.displayMedium.emphasized("variable-display-medium-emphasized"),
        displaySmall = base.displaySmall.emphasized("variable-display-small-emphasized"),
        headlineLarge = base.headlineLarge.emphasized("variable-headline-large-emphasized"),
        headlineMedium = base.headlineMedium.emphasized("variable-headline-medium-emphasized"),
        headlineSmall = base.headlineSmall.emphasized("variable-headline-small-emphasized"),
        titleLarge = base.titleLarge.emphasized("variable-title-large-emphasized"),
        titleSmall = base.titleSmall.emphasized("variable-title-small-emphasized")
    )
}

// Round the category titles (SettingsSectionLabel)
val ExpressiveSectionLabelStyle: TextStyle = Typography().labelLarge.emphasized("variable-label-large-emphasized")

// Round the home page section headers ("Recent folders", "Recently played")
val ExpressiveSectionHeaderStyle: TextStyle = Typography().titleMedium.emphasized("variable-title-medium-emphasized")
