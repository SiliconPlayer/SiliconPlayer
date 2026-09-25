package com.flopster101.siliconplayer.ui.screens

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.StarfieldPreset

internal data class StarfieldModeKeys(
    val starCount: String,
    val speedCenti: String,
    val fovCenti: String,
    val nearMilli: String,
    val starColorArgb: String,
    val baseSizeDeci: String,
    val sizeGrowthCenti: String,
    val farDimPercent: String,
    val softnessPercent: String,
    val beatGlowPercent: String,
    val glowSizeDeci: String,
    val trailPercent: String,
    val streaksEnabled: String,
    val streakLengthCenti: String,
    val centerXCenti: String,
    val centerYCenti: String,
    val autoDriftEnabled: String,
    val reactSpeedCenti: String,
    val flashPercent: String,
    val contrastBackdropEnabled: String,
    val squareEnabled: String
)

internal fun starfieldKeysFor(preset: StarfieldPreset): StarfieldModeKeys = when (preset) {
    StarfieldPreset.Warp -> StarfieldModeKeys(
        starCount = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_STAR_COUNT,
        speedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_SPEED_CENTI,
        fovCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_FOV_CENTI,
        nearMilli = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_NEAR_MILLI,
        starColorArgb = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_STAR_COLOR_ARGB,
        baseSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_BASE_SIZE_DECI,
        sizeGrowthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_SIZE_GROWTH_CENTI,
        farDimPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_FAR_DIM_PERCENT,
        softnessPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_SOFTNESS_PERCENT,
        beatGlowPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_BEAT_GLOW_PERCENT,
        glowSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_GLOW_SIZE_DECI,
        trailPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_TRAIL_PERCENT,
        streaksEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_STREAKS_ENABLED,
        streakLengthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_STREAK_LENGTH_CENTI,
        centerXCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_CENTER_X_CENTI,
        centerYCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_CENTER_Y_CENTI,
        autoDriftEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_AUTO_DRIFT_ENABLED,
        reactSpeedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_REACT_SPEED_CENTI,
        flashPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_FLASH_PERCENT,
        contrastBackdropEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_CONTRAST_BACKDROP_ENABLED,
        squareEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_WARP_SQUARE_ENABLED
    )
    StarfieldPreset.SnowDrift -> StarfieldModeKeys(
        starCount = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_STAR_COUNT,
        speedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_SPEED_CENTI,
        fovCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_FOV_CENTI,
        nearMilli = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_NEAR_MILLI,
        starColorArgb = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_STAR_COLOR_ARGB,
        baseSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_BASE_SIZE_DECI,
        sizeGrowthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_SIZE_GROWTH_CENTI,
        farDimPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_FAR_DIM_PERCENT,
        softnessPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_SOFTNESS_PERCENT,
        beatGlowPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_BEAT_GLOW_PERCENT,
        glowSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_GLOW_SIZE_DECI,
        trailPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_TRAIL_PERCENT,
        streaksEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_STREAKS_ENABLED,
        streakLengthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_STREAK_LENGTH_CENTI,
        centerXCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_CENTER_X_CENTI,
        centerYCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_CENTER_Y_CENTI,
        autoDriftEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_AUTO_DRIFT_ENABLED,
        reactSpeedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_REACT_SPEED_CENTI,
        flashPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_FLASH_PERCENT,
        contrastBackdropEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_CONTRAST_BACKDROP_ENABLED,
        squareEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_SNOW_SQUARE_ENABLED
    )
    StarfieldPreset.BeatRider -> StarfieldModeKeys(
        starCount = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_STAR_COUNT,
        speedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_SPEED_CENTI,
        fovCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOV_CENTI,
        nearMilli = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_NEAR_MILLI,
        starColorArgb = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_STAR_COLOR_ARGB,
        baseSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_BASE_SIZE_DECI,
        sizeGrowthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_SIZE_GROWTH_CENTI,
        farDimPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FAR_DIM_PERCENT,
        softnessPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_SOFTNESS_PERCENT,
        beatGlowPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_BEAT_GLOW_PERCENT,
        glowSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_GLOW_SIZE_DECI,
        trailPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_TRAIL_PERCENT,
        streaksEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_STREAKS_ENABLED,
        streakLengthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_STREAK_LENGTH_CENTI,
        centerXCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_CENTER_X_CENTI,
        centerYCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_CENTER_Y_CENTI,
        autoDriftEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_AUTO_DRIFT_ENABLED,
        reactSpeedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_REACT_SPEED_CENTI,
        flashPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FLASH_PERCENT,
        contrastBackdropEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_CONTRAST_BACKDROP_ENABLED,
        squareEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_SQUARE_ENABLED
    )
    else -> StarfieldModeKeys(
        starCount = AppPreferenceKeys.VISUALIZATION_STARFIELD_STAR_COUNT,
        speedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SPEED_CENTI,
        fovCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_FOV_CENTI,
        nearMilli = AppPreferenceKeys.VISUALIZATION_STARFIELD_NEAR_MILLI,
        starColorArgb = AppPreferenceKeys.VISUALIZATION_STARFIELD_STAR_COLOR_ARGB,
        baseSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_BASE_SIZE_DECI,
        sizeGrowthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_SIZE_GROWTH_CENTI,
        farDimPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_FAR_DIM_PERCENT,
        softnessPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_SOFTNESS_PERCENT,
        beatGlowPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_GLOW_PERCENT,
        glowSizeDeci = AppPreferenceKeys.VISUALIZATION_STARFIELD_GLOW_SIZE_DECI,
        trailPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_TRAIL_PERCENT,
        streaksEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_STREAKS_ENABLED,
        streakLengthCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_STREAK_LENGTH_CENTI,
        centerXCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_CENTER_X_CENTI,
        centerYCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_CENTER_Y_CENTI,
        autoDriftEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_AUTO_DRIFT_ENABLED,
        reactSpeedCenti = AppPreferenceKeys.VISUALIZATION_STARFIELD_REACT_SPEED_CENTI,
        flashPercent = AppPreferenceKeys.VISUALIZATION_STARFIELD_FLASH_PERCENT,
        contrastBackdropEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_CONTRAST_BACKDROP_ENABLED,
        squareEnabled = AppPreferenceKeys.VISUALIZATION_STARFIELD_SQUARE_ENABLED
    )
}

internal fun starfieldPresetTuneFor(preset: StarfieldPreset): AppDefaults.Visualization.StarfieldPresetTune =
    when (preset) {
        StarfieldPreset.Warp -> AppDefaults.Visualization.Starfield.warp
        StarfieldPreset.SnowDrift -> AppDefaults.Visualization.Starfield.snow
        StarfieldPreset.BeatRider -> AppDefaults.Visualization.Starfield.beatRider
        else -> AppDefaults.Visualization.Starfield.classic
    }
