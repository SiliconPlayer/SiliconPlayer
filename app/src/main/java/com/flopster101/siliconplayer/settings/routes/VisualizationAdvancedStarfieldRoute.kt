package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.flopster101.siliconplayer.StarfieldPreset
import com.flopster101.siliconplayer.ui.screens.starfieldKeysFor
import com.flopster101.siliconplayer.ui.screens.starfieldPresetTuneFor

@Composable
internal fun VisualizationAdvancedStarfieldRouteContent() {
    val d = AppDefaults.Visualization.Starfield
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var activePreset by remember {
        mutableStateOf(
            StarfieldPreset.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET,
                    StarfieldPreset.ClassicAmiga.storageValue
                )
            )
        )
    }
    val tune = starfieldPresetTuneFor(activePreset)
    val keys = starfieldKeysFor(activePreset)

    var starCount by remember {
        mutableIntStateOf(
            prefs.getInt(keys.starCount, tune.starCount)
                .coerceIn(d.starCountRange.first, d.starCountRange.last)
        )
    }
    var speedCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.speedCenti, tune.speedCenti)
                .coerceIn(d.speedRangeCenti.first, d.speedRangeCenti.last)
        )
    }
    var fovCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.fovCenti, tune.fovCenti)
                .coerceIn(d.fovRangeCenti.first, d.fovRangeCenti.last)
        )
    }
    var nearMilli by remember {
        mutableIntStateOf(
            prefs.getInt(keys.nearMilli, tune.nearMilli)
                .coerceIn(d.nearRangeMilli.first, d.nearRangeMilli.last)
        )
    }
    var starColorArgb by remember {
        mutableIntStateOf(
            prefs.getInt(keys.starColorArgb, tune.starColorArgb)
        )
    }
    var baseSizeDeci by remember {
        mutableIntStateOf(
            prefs.getInt(keys.baseSizeDeci, tune.baseSizeDeci)
                .coerceIn(d.baseSizeRangeDeci.first, d.baseSizeRangeDeci.last)
        )
    }
    var sizeGrowthCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.sizeGrowthCenti, tune.sizeGrowthCenti)
                .coerceIn(d.sizeGrowthRangeCenti.first, d.sizeGrowthRangeCenti.last)
        )
    }
    var farDimPercent by remember {
        mutableIntStateOf(
            prefs.getInt(keys.farDimPercent, tune.farDimPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last)
        )
    }
    var softnessPercent by remember {
        mutableIntStateOf(
            prefs.getInt(keys.softnessPercent, tune.softnessPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last)
        )
    }
    var beatGlowPercent by remember {
        mutableIntStateOf(
            prefs.getInt(keys.beatGlowPercent, tune.beatGlowPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last)
        )
    }
    var glowSizeDeci by remember {
        mutableIntStateOf(
            prefs.getInt(keys.glowSizeDeci, tune.glowSizeDeci)
                .coerceIn(d.glowSizeRangeDeci.first, d.glowSizeRangeDeci.last)
        )
    }
    var trailPercent by remember {
        mutableIntStateOf(
            prefs.getInt(keys.trailPercent, tune.trailPercent)
                .coerceIn(d.trailRangePercent.first, d.trailRangePercent.last)
        )
    }
    var streaksEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(keys.streaksEnabled, tune.streaksEnabled)
        )
    }
    var squarePixelsEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(keys.squareEnabled, tune.squarePixelsEnabled)
        )
    }
    var streakLengthCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.streakLengthCenti, tune.streakLengthCenti)
                .coerceIn(d.streakLengthRangeCenti.first, d.streakLengthRangeCenti.last)
        )
    }
    var centerXCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.centerXCenti, tune.centerXCenti)
                .coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last)
        )
    }
    var centerYCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.centerYCenti, tune.centerYCenti)
                .coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last)
        )
    }
    var autoDriftEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(keys.autoDriftEnabled, tune.autoDriftEnabled)
        )
    }
    var beatFollowEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, d.beatFollowEnabled)
        )
    }
    var reactSpeedCenti by remember {
        mutableIntStateOf(
            prefs.getInt(keys.reactSpeedCenti, tune.reactSpeedCenti)
                .coerceIn(d.reactSpeedRangeCenti.first, d.reactSpeedRangeCenti.last)
        )
    }
    var flashPercent by remember {
        mutableIntStateOf(
            prefs.getInt(keys.flashPercent, tune.flashPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last)
        )
    }
    var contrastBackdropEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(keys.contrastBackdropEnabled, tune.contrastBackdropEnabled)
        )
    }

    var showPresetDialog by remember { mutableStateOf(false) }
    var showStarCountDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showFovDialog by remember { mutableStateOf(false) }
    var showNearDialog by remember { mutableStateOf(false) }
    var showStarColorDialog by remember { mutableStateOf(false) }
    var showBaseSizeDialog by remember { mutableStateOf(false) }
    var showSizeGrowthDialog by remember { mutableStateOf(false) }
    var showFarDimDialog by remember { mutableStateOf(false) }
    var showSoftnessDialog by remember { mutableStateOf(false) }
    var showBeatGlowDialog by remember { mutableStateOf(false) }
    var showGlowSizeDialog by remember { mutableStateOf(false) }
    var showTrailDialog by remember { mutableStateOf(false) }
    var showStreakLengthDialog by remember { mutableStateOf(false) }
    var showCenterXDialog by remember { mutableStateOf(false) }
    var showCenterYDialog by remember { mutableStateOf(false) }
    var showReactSpeedDialog by remember { mutableStateOf(false) }
    var showFlashDialog by remember { mutableStateOf(false) }

    PreferenceChangeSyncEffect(
        prefs = prefs,
        watchedKeys = setOf(
            keys.starCount,
            keys.speedCenti,
            keys.fovCenti,
            keys.nearMilli,
            keys.starColorArgb,
            keys.baseSizeDeci,
            keys.sizeGrowthCenti,
            keys.farDimPercent,
            keys.softnessPercent,
            keys.beatGlowPercent,
            keys.glowSizeDeci,
            keys.trailPercent,
            keys.streaksEnabled,
            keys.squareEnabled,
            keys.streakLengthCenti,
            keys.centerXCenti,
            keys.centerYCenti,
            keys.autoDriftEnabled,
            AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED,
            keys.reactSpeedCenti,
            keys.flashPercent,
            keys.contrastBackdropEnabled,
            AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET
        )
    ) {
        starCount = prefs.getInt(keys.starCount, tune.starCount)
            .coerceIn(d.starCountRange.first, d.starCountRange.last)
        speedCenti = prefs.getInt(keys.speedCenti, tune.speedCenti)
            .coerceIn(d.speedRangeCenti.first, d.speedRangeCenti.last)
        fovCenti = prefs.getInt(keys.fovCenti, tune.fovCenti)
            .coerceIn(d.fovRangeCenti.first, d.fovRangeCenti.last)
        nearMilli = prefs.getInt(keys.nearMilli, tune.nearMilli)
            .coerceIn(d.nearRangeMilli.first, d.nearRangeMilli.last)
        starColorArgb = prefs.getInt(keys.starColorArgb, tune.starColorArgb)
        baseSizeDeci = prefs.getInt(keys.baseSizeDeci, tune.baseSizeDeci)
            .coerceIn(d.baseSizeRangeDeci.first, d.baseSizeRangeDeci.last)
        sizeGrowthCenti = prefs.getInt(keys.sizeGrowthCenti, tune.sizeGrowthCenti)
            .coerceIn(d.sizeGrowthRangeCenti.first, d.sizeGrowthRangeCenti.last)
        farDimPercent = prefs.getInt(keys.farDimPercent, tune.farDimPercent)
            .coerceIn(d.percentRange.first, d.percentRange.last)
        softnessPercent = prefs.getInt(keys.softnessPercent, tune.softnessPercent)
            .coerceIn(d.percentRange.first, d.percentRange.last)
        beatGlowPercent = prefs.getInt(keys.beatGlowPercent, tune.beatGlowPercent)
            .coerceIn(d.percentRange.first, d.percentRange.last)
        glowSizeDeci = prefs.getInt(keys.glowSizeDeci, tune.glowSizeDeci)
            .coerceIn(d.glowSizeRangeDeci.first, d.glowSizeRangeDeci.last)
        trailPercent = prefs.getInt(keys.trailPercent, tune.trailPercent)
            .coerceIn(d.trailRangePercent.first, d.trailRangePercent.last)
        streaksEnabled = prefs.getBoolean(keys.streaksEnabled, tune.streaksEnabled)
        squarePixelsEnabled = prefs.getBoolean(keys.squareEnabled, tune.squarePixelsEnabled)
        streakLengthCenti = prefs.getInt(keys.streakLengthCenti, tune.streakLengthCenti)
            .coerceIn(d.streakLengthRangeCenti.first, d.streakLengthRangeCenti.last)
        centerXCenti = prefs.getInt(keys.centerXCenti, tune.centerXCenti)
            .coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last)
        centerYCenti = prefs.getInt(keys.centerYCenti, tune.centerYCenti)
            .coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last)
        autoDriftEnabled = prefs.getBoolean(keys.autoDriftEnabled, tune.autoDriftEnabled)
        beatFollowEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, d.beatFollowEnabled)
        reactSpeedCenti = prefs.getInt(keys.reactSpeedCenti, tune.reactSpeedCenti)
            .coerceIn(d.reactSpeedRangeCenti.first, d.reactSpeedRangeCenti.last)
        flashPercent = prefs.getInt(keys.flashPercent, tune.flashPercent)
            .coerceIn(d.percentRange.first, d.percentRange.last)
        contrastBackdropEnabled = prefs.getBoolean(
            keys.contrastBackdropEnabled, tune.contrastBackdropEnabled)
        activePreset = StarfieldPreset.fromStorage(
            prefs.getString(
                AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET,
                StarfieldPreset.ClassicAmiga.storageValue
            )
        )
    }



    fun applyFactoryTune(preset: StarfieldPreset) {
        val factory = starfieldPresetTuneFor(preset)
        val slotKeys = starfieldKeysFor(preset)
        starCount = factory.starCount
        speedCenti = factory.speedCenti
        fovCenti = factory.fovCenti
        nearMilli = factory.nearMilli
        starColorArgb = factory.starColorArgb
        baseSizeDeci = factory.baseSizeDeci
        sizeGrowthCenti = factory.sizeGrowthCenti
        farDimPercent = factory.farDimPercent
        softnessPercent = factory.softnessPercent
        beatGlowPercent = factory.beatGlowPercent
        glowSizeDeci = factory.glowSizeDeci
        trailPercent = factory.trailPercent
        streaksEnabled = factory.streaksEnabled
        squarePixelsEnabled = factory.squarePixelsEnabled
        streakLengthCenti = factory.streakLengthCenti
        centerXCenti = factory.centerXCenti
        centerYCenti = factory.centerYCenti
        autoDriftEnabled = factory.autoDriftEnabled
        reactSpeedCenti = factory.reactSpeedCenti
        flashPercent = factory.flashPercent
        contrastBackdropEnabled = factory.contrastBackdropEnabled
        prefs.edit()
            .putInt(slotKeys.starCount, factory.starCount)
            .putInt(slotKeys.speedCenti, factory.speedCenti)
            .putInt(slotKeys.fovCenti, factory.fovCenti)
            .putInt(slotKeys.nearMilli, factory.nearMilli)
            .putInt(slotKeys.starColorArgb, factory.starColorArgb)
            .putInt(slotKeys.baseSizeDeci, factory.baseSizeDeci)
            .putInt(slotKeys.sizeGrowthCenti, factory.sizeGrowthCenti)
            .putInt(slotKeys.farDimPercent, factory.farDimPercent)
            .putInt(slotKeys.softnessPercent, factory.softnessPercent)
            .putInt(slotKeys.beatGlowPercent, factory.beatGlowPercent)
            .putInt(slotKeys.glowSizeDeci, factory.glowSizeDeci)
            .putInt(slotKeys.trailPercent, factory.trailPercent)
            .putBoolean(slotKeys.streaksEnabled, factory.streaksEnabled)
            .putBoolean(slotKeys.squareEnabled, factory.squarePixelsEnabled)
            .putInt(slotKeys.streakLengthCenti, factory.streakLengthCenti)
            .putInt(slotKeys.centerXCenti, factory.centerXCenti)
            .putInt(slotKeys.centerYCenti, factory.centerYCenti)
            .putBoolean(slotKeys.autoDriftEnabled, factory.autoDriftEnabled)
            .putInt(slotKeys.reactSpeedCenti, factory.reactSpeedCenti)
            .putInt(slotKeys.flashPercent, factory.flashPercent)
            .putBoolean(slotKeys.contrastBackdropEnabled, factory.contrastBackdropEnabled)
            .apply()
    }

    Column {
        SettingsSectionLabel("Preset")
        SettingsValuePickerCard(
            title = "Flight preset",
            description = "Preset slot under edit. Tuning below rewrites it.",
            value = activePreset.label,
            onClick = { showPresetDialog = true }
        )
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Reset preset",
            description = "Restore the factory tune for ${activePreset.label}.",
            icon = Icons.Default.Refresh,
            onClick = { applyFactoryTune(activePreset) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Field")
        SettingsValuePickerCard(
            title = "Star count",
            description = "Number of stars in flight.",
            value = "$starCount",
            onClick = { showStarCountDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Flight speed",
            description = "Base forward speed. Beat follow adds energy on top.",
            value = String.format(Locale.US, "%.2f", speedCenti / 100f),
            onClick = { showSpeedDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Field of view",
            description = "Projection spread. Higher values push stars wider.",
            value = String.format(Locale.US, "%.2f", fovCenti / 100f),
            onClick = { showFovDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Near plane",
            description = "Recycle distance. Stars respawn far past this point.",
            value = String.format(Locale.US, "%.3f", nearMilli / 1000f),
            onClick = { showNearDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Look")
        SettingsValuePickerCard(
            title = "Star color",
            description = "Tint applied to every star. White by default.",
            value = String.format(Locale.US, "#%06X", starColorArgb and 0xFFFFFF),
            onClick = { showStarColorDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Star size",
            description = "Base size calibrated for a 1080p canvas.",
            value = String.format(Locale.US, "%.1f px", baseSizeDeci / 10f),
            onClick = { showBaseSizeDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Size growth",
            description = "How much near stars grow as they approach.",
            value = String.format(Locale.US, "%.2f", sizeGrowthCenti / 100f),
            onClick = { showSizeGrowthDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Far dim",
            description = "How much distant stars fade into the background.",
            value = "$farDimPercent%",
            onClick = { showFarDimDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Softness",
            description = "Sprite edge softness from crisp to hazy.",
            value = "$softnessPercent%",
            onClick = { showSoftnessDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Square pixels",
            description = "Unaliased squares instead of soft dots.",
            checked = squarePixelsEnabled,
            onCheckedChange = { enabled ->
                squarePixelsEnabled = enabled
                prefs.edit().putBoolean(keys.squareEnabled, enabled).apply()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Bloom glow")
        SettingsValuePickerCard(
            title = "Halo strength",
            description = "Post-processed halo riding the beat. Needs beat follow.",
            value = "$beatGlowPercent%",
            onClick = { showBeatGlowDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Halo spread",
            description = "Halo size around bright stars.",
            value = String.format(Locale.US, "%.1f×", glowSizeDeci / 10f),
            onClick = { showGlowSizeDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Trails")
        SettingsValuePickerCard(
            title = "Trail persistence",
            description = "Motion blur length. Trails composite over artwork.",
            value = "$trailPercent%",
            onClick = { showTrailDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Warp streaks",
            description = "Velocity lines on fast stars. Lines replace dots.",
            checked = streaksEnabled,
            onCheckedChange = { enabled ->
                streaksEnabled = enabled
                prefs.edit().putBoolean(keys.streaksEnabled, enabled).apply()
            }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Streak length",
            description = "Line length multiplier for warp streaks.",
            value = String.format(Locale.US, "%.2f", streakLengthCenti / 100f),
            onClick = { showStreakLengthDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Motion")
        SettingsValuePickerCard(
            title = "Center X",
            description = "Horizontal vanishing point offset.",
            value = String.format(Locale.US, "%+.2f", centerXCenti / 100f),
            onClick = { showCenterXDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Center Y",
            description = "Vertical vanishing point offset.",
            value = String.format(Locale.US, "%+.2f", centerYCenti / 100f),
            onClick = { showCenterYDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Auto drift",
            description = "Slow wander of the vanishing point.",
            checked = autoDriftEnabled,
            onCheckedChange = { enabled ->
                autoDriftEnabled = enabled
                prefs.edit().putBoolean(keys.autoDriftEnabled, enabled).apply()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Beat follow")
        PlayerSettingToggleCard(
            title = "Follow the beat",
            description = "Ride beat energy for speed and glow. Off by default.",
            checked = beatFollowEnabled,
            onCheckedChange = { enabled ->
                beatFollowEnabled = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, enabled).apply()
            }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Speed reaction",
            description = "How much the beat pushes flight speed.",
            value = String.format(Locale.US, "%.2f×", reactSpeedCenti / 100f),
            onClick = { showReactSpeedDialog = true }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Brightness flash",
            description = "How much the beat lifts star brightness.",
            value = "$flashPercent%",
            onClick = { showFlashDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Backdrop")
        PlayerSettingToggleCard(
            title = "Contrast backdrop",
            description = "Dark scrim behind stars for readability over artwork.",
            checked = contrastBackdropEnabled,
            onCheckedChange = { enabled ->
                contrastBackdropEnabled = enabled
                prefs.edit().putBoolean(keys.contrastBackdropEnabled, enabled).apply()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }


    if (showPresetDialog) {
        SettingsSingleChoiceDialog(
            title = "Flight preset",
            selectedValue = activePreset,
            options = StarfieldPreset.entries.map { preset ->
                ChoiceDialogOption(value = preset, label = preset.label)
            },
            onSelected = { preset ->
                activePreset = preset
                prefs.edit().putString(
                    AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET,
                    preset.storageValue
                ).apply()
                showPresetDialog = false
            },
            onDismiss = { showPresetDialog = false }
        )
    }
    if (showStarCountDialog) {
        SteppedIntSliderDialog(
            title = "Star count",
            unitLabel = "stars",
            range = d.starCountRange,
            step = 10,
            currentValue = starCount,
            onDismiss = { showStarCountDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.starCountRange.first, d.starCountRange.last)
                starCount = clamped
                prefs.edit().putInt(keys.starCount, clamped).apply()
                showStarCountDialog = false
            }
        )
    }
    if (showSpeedDialog) {
        SteppedIntSliderDialog(
            title = "Flight speed",
            unitLabel = "",
            range = d.speedRangeCenti,
            step = 1,
            currentValue = speedCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.2f", v / 100f) },
            onDismiss = { showSpeedDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.speedRangeCenti.first, d.speedRangeCenti.last)
                speedCenti = clamped
                prefs.edit().putInt(keys.speedCenti, clamped).apply()
                showSpeedDialog = false
            }
        )
    }
    if (showFovDialog) {
        SteppedIntSliderDialog(
            title = "Field of view",
            unitLabel = "",
            range = d.fovRangeCenti,
            step = 5,
            currentValue = fovCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.2f", v / 100f) },
            onDismiss = { showFovDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.fovRangeCenti.first, d.fovRangeCenti.last)
                fovCenti = clamped
                prefs.edit().putInt(keys.fovCenti, clamped).apply()
                showFovDialog = false
            }
        )
    }
    if (showNearDialog) {
        SteppedIntSliderDialog(
            title = "Near plane",
            unitLabel = "",
            range = d.nearRangeMilli,
            step = 5,
            currentValue = nearMilli,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.3f", v / 1000f) },
            onDismiss = { showNearDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.nearRangeMilli.first, d.nearRangeMilli.last)
                nearMilli = clamped
                prefs.edit().putInt(keys.nearMilli, clamped).apply()
                showNearDialog = false
            }
        )
    }
    if (showStarColorDialog) {
        VisualizationRgbColorPickerDialog(
            title = "Star color",
            initialArgb = starColorArgb,
            onDismiss = { showStarColorDialog = false },
            onConfirm = { argb ->
                starColorArgb = argb
                prefs.edit().putInt(keys.starColorArgb, argb).apply()
                showStarColorDialog = false
            }
        )
    }
    if (showBaseSizeDialog) {
        SteppedIntSliderDialog(
            title = "Star size",
            unitLabel = "px",
            range = d.baseSizeRangeDeci,
            step = 1,
            currentValue = baseSizeDeci,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.1f px", v / 10f) },
            onDismiss = { showBaseSizeDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.baseSizeRangeDeci.first, d.baseSizeRangeDeci.last)
                baseSizeDeci = clamped
                prefs.edit().putInt(keys.baseSizeDeci, clamped).apply()
                showBaseSizeDialog = false
            }
        )
    }
    if (showSizeGrowthDialog) {
        SteppedIntSliderDialog(
            title = "Size growth",
            unitLabel = "",
            range = d.sizeGrowthRangeCenti,
            step = 5,
            currentValue = sizeGrowthCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.2f", v / 100f) },
            onDismiss = { showSizeGrowthDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.sizeGrowthRangeCenti.first, d.sizeGrowthRangeCenti.last)
                sizeGrowthCenti = clamped
                prefs.edit().putInt(keys.sizeGrowthCenti, clamped).apply()
                showSizeGrowthDialog = false
            }
        )
    }
    if (showFarDimDialog) {
        SteppedIntSliderDialog(
            title = "Far dim",
            unitLabel = "%",
            range = d.percentRange,
            step = 1,
            currentValue = farDimPercent,
            onDismiss = { showFarDimDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.percentRange.first, d.percentRange.last)
                farDimPercent = clamped
                prefs.edit().putInt(keys.farDimPercent, clamped).apply()
                showFarDimDialog = false
            }
        )
    }
    if (showSoftnessDialog) {
        SteppedIntSliderDialog(
            title = "Softness",
            unitLabel = "%",
            range = d.percentRange,
            step = 1,
            currentValue = softnessPercent,
            onDismiss = { showSoftnessDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.percentRange.first, d.percentRange.last)
                softnessPercent = clamped
                prefs.edit().putInt(keys.softnessPercent, clamped).apply()
                showSoftnessDialog = false
            }
        )
    }
    if (showBeatGlowDialog) {
        SteppedIntSliderDialog(
            title = "Halo strength",
            unitLabel = "%",
            range = d.percentRange,
            step = 1,
            currentValue = beatGlowPercent,
            onDismiss = { showBeatGlowDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.percentRange.first, d.percentRange.last)
                beatGlowPercent = clamped
                prefs.edit().putInt(keys.beatGlowPercent, clamped).apply()
                showBeatGlowDialog = false
            }
        )
    }
    if (showGlowSizeDialog) {
        SteppedIntSliderDialog(
            title = "Halo spread",
            unitLabel = "×",
            range = d.glowSizeRangeDeci,
            step = 1,
            currentValue = glowSizeDeci,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.1f×", v / 10f) },
            onDismiss = { showGlowSizeDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.glowSizeRangeDeci.first, d.glowSizeRangeDeci.last)
                glowSizeDeci = clamped
                prefs.edit().putInt(keys.glowSizeDeci, clamped).apply()
                showGlowSizeDialog = false
            }
        )
    }
    if (showTrailDialog) {
        SteppedIntSliderDialog(
            title = "Trail persistence",
            unitLabel = "%",
            range = d.trailRangePercent,
            step = 1,
            currentValue = trailPercent,
            onDismiss = { showTrailDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.trailRangePercent.first, d.trailRangePercent.last)
                trailPercent = clamped
                prefs.edit().putInt(keys.trailPercent, clamped).apply()
                showTrailDialog = false
            }
        )
    }
    if (showStreakLengthDialog) {
        SteppedIntSliderDialog(
            title = "Streak length",
            unitLabel = "",
            range = d.streakLengthRangeCenti,
            step = 5,
            currentValue = streakLengthCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.2f", v / 100f) },
            onDismiss = { showStreakLengthDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.streakLengthRangeCenti.first, d.streakLengthRangeCenti.last)
                streakLengthCenti = clamped
                prefs.edit().putInt(keys.streakLengthCenti, clamped).apply()
                showStreakLengthDialog = false
            }
        )
    }
    if (showCenterXDialog) {
        SteppedIntSliderDialog(
            title = "Center X",
            unitLabel = "",
            range = d.centerRangeCenti,
            step = 1,
            currentValue = centerXCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%+.2f", v / 100f) },
            onDismiss = { showCenterXDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last)
                centerXCenti = clamped
                prefs.edit().putInt(keys.centerXCenti, clamped).apply()
                showCenterXDialog = false
            }
        )
    }
    if (showCenterYDialog) {
        SteppedIntSliderDialog(
            title = "Center Y",
            unitLabel = "",
            range = d.centerRangeCenti,
            step = 1,
            currentValue = centerYCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%+.2f", v / 100f) },
            onDismiss = { showCenterYDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last)
                centerYCenti = clamped
                prefs.edit().putInt(keys.centerYCenti, clamped).apply()
                showCenterYDialog = false
            }
        )
    }
    if (showReactSpeedDialog) {
        SteppedIntSliderDialog(
            title = "Speed reaction",
            unitLabel = "×",
            range = d.reactSpeedRangeCenti,
            step = 5,
            currentValue = reactSpeedCenti,
            valueLabelFormatter = { v -> String.format(Locale.US, "%.2f×", v / 100f) },
            onDismiss = { showReactSpeedDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.reactSpeedRangeCenti.first, d.reactSpeedRangeCenti.last)
                reactSpeedCenti = clamped
                prefs.edit().putInt(keys.reactSpeedCenti, clamped).apply()
                showReactSpeedDialog = false
            }
        )
    }
    if (showFlashDialog) {
        SteppedIntSliderDialog(
            title = "Brightness flash",
            unitLabel = "%",
            range = d.percentRange,
            step = 1,
            currentValue = flashPercent,
            onDismiss = { showFlashDialog = false },
            onConfirm = { value ->
                val clamped = value.coerceIn(d.percentRange.first, d.percentRange.last)
                flashPercent = clamped
                prefs.edit().putInt(keys.flashPercent, clamped).apply()
                showFlashDialog = false
            }
        )
    }
}
