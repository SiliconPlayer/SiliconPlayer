package com.flopster101.siliconplayer.settings.porting

/**
 * Bump whenever the shape of exported/imported settings changes in a way an
 * older SiliconPlayer could mishandle (new value type, structural move, etc).
 * Additive catalog growth does NOT require a bump because unknown entries and
 * unknown keys are always skipped and reported instead of written blind.
 */
internal const val SETTINGS_PORTING_CONFIG_VERSION = 1

internal const val SETTINGS_PORTING_FORMAT_ID = "siliconplayer.settings"

internal const val SETTINGS_PORTING_EXTENSION = "spxs"
internal const val SETTINGS_PORTING_MIME = "application/x-siliconplayer-settings"
