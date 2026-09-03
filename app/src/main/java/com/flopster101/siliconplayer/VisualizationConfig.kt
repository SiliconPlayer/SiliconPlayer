package com.flopster101.siliconplayer

import android.app.ActivityManager
import android.content.Context

enum class VisualizationMode(
    val storageValue: String,
    val label: String
) {
    Off("off", "Off"),
    Bars("bars", "Bars"),
    Oscilloscope("oscilloscope", "Oscilloscope"),
    VuMeters("vu_meters", "VU meters"),
    ChannelScope("channel_scope", "Channel scope"),
    ProjectM("projectm", "projectM");

    companion object {
        fun fromStorage(value: String?): VisualizationMode {
            return entries.firstOrNull { it.storageValue == value } ?: Off
        }
    }
}

enum class VisualizationModeCategory {
    Basic,
    Advanced
}

val VisualizationMode.category: VisualizationModeCategory?
    get() = when (this) {
        VisualizationMode.Bars,
        VisualizationMode.Oscilloscope,
        VisualizationMode.VuMeters -> VisualizationModeCategory.Basic
        VisualizationMode.ChannelScope,
        VisualizationMode.ProjectM -> VisualizationModeCategory.Advanced
        VisualizationMode.Off -> null
    }

fun VisualizationMode.isBasicVisualizationMode(): Boolean =
    category == VisualizationModeCategory.Basic

fun VisualizationMode.isAdvancedVisualizationMode(): Boolean =
    category == VisualizationModeCategory.Advanced

enum class VisualizationRenderBackend(
    val storageValue: String,
    val label: String
) {
    Compose("compose", "Compose"),
    OpenGlTexture("opengl_texture", "OpenGL ES (TextureView)"),
    OpenGlSurface("opengl_surface", "OpenGL ES (SurfaceView)");

    companion object {
        fun fromStorage(value: String?, fallback: VisualizationRenderBackend): VisualizationRenderBackend {
            return when (value) {
                // Legacy migration: old GPU-canvas backend now maps to composited OpenGL backend.
                "gpu" -> OpenGlTexture
                // Legacy migration: old OpenGL value now maps to explicit SurfaceView backend.
                "opengl" -> OpenGlSurface
                else -> entries.firstOrNull { it.storageValue == value } ?: fallback
            }
        }
    }
}

fun visualizationRenderBackendForMode(mode: VisualizationMode): VisualizationRenderBackend {
    return when (mode) {
        VisualizationMode.Bars -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.Oscilloscope -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.VuMeters -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.ChannelScope -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.ProjectM -> VisualizationRenderBackend.OpenGlTexture
        VisualizationMode.Off -> VisualizationRenderBackend.Compose
    }
}

enum class VisualizationChannelScopeLayout(
    val storageValue: String,
    val label: String
) {
    ColumnFirst("column_first", "Column-first (4ch = 1x4)"),
    BalancedTwoColumn("balanced_two_column", "Balanced (4ch = 2x2)");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeLayout {
            return entries.firstOrNull { it.storageValue == value } ?: ColumnFirst
        }
    }
}

enum class VisualizationVuAnchor(
    val storageValue: String,
    val label: String
) {
    Top("top", "Top"),
    Center("center", "Center"),
    Bottom("bottom", "Bottom");

    companion object {
        fun fromStorage(value: String?): VisualizationVuAnchor {
            return entries.firstOrNull { it.storageValue == value } ?: Bottom
        }
    }
}

enum class VisualizationOscTriggerMode(
    val storageValue: String,
    val label: String,
    val nativeValue: Int
) {
    Off("off", "Off", 0),
    Rising("rising", "Rising edge", 1),
    Falling("falling", "Falling edge", 2);

    companion object {
        fun fromStorage(value: String?): VisualizationOscTriggerMode {
            return entries.firstOrNull { it.storageValue == value } ?: Off
        }
    }
}

enum class VisualizationChannelScopeTriggerAlgorithm(
    val storageValue: String,
    val label: String,
    val nativeValue: Int
) {
    Fast("fast", "Fast (zero-crossing)", 0),
    Accurate("accurate", "Accurate (correlation)", 1);

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTriggerAlgorithm {
            return entries.firstOrNull { it.storageValue == value } ?: Fast
        }
    }
}

enum class VisualizationChannelScopeWaveRenderMode(
    val storageValue: String,
    val label: String,
    val nativeValue: Int
) {
    Off("off", "Off", 0),
    Antialiased("antialiased", "Antialiased", 1),
    Crt("crt", "CRT", 2);

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeWaveRenderMode {
            return entries.firstOrNull { it.storageValue == value } ?: Antialiased
        }
    }
}

enum class VisualizationOscColorMode(
    val storageValue: String,
    val label: String
) {
    Artwork("artwork", "From artwork"),
    Monet("monet", "Monet accent"),
    White("white", "White"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?, fallback: VisualizationOscColorMode): VisualizationOscColorMode {
            return entries.firstOrNull { it.storageValue == value } ?: fallback
        }
    }
}

enum class VisualizationChannelScopeBackgroundMode(
    val storageValue: String,
    val label: String
) {
    AutoDarkAccent("auto_dark_accent", "Auto dark accent"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeBackgroundMode {
            return entries.firstOrNull { it.storageValue == value } ?: AutoDarkAccent
        }
    }
}

enum class VisualizationChannelScopeTextAnchor(
    val storageValue: String,
    val label: String
) {
    TopLeft("top_left", "Top left"),
    TopCenter("top_center", "Top center"),
    TopRight("top_right", "Top right"),
    BottomRight("bottom_right", "Bottom right"),
    BottomCenter("bottom_center", "Bottom center"),
    BottomLeft("bottom_left", "Bottom left");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTextAnchor {
            return entries.firstOrNull { it.storageValue == value } ?: TopLeft
        }
    }
}

enum class VisualizationNoteNameFormat(
    val storageValue: String,
    val label: String
) {
    American("american", "American (C, C#, D...)"),
    International("international", "International (Do, Do#, Re...)");

    companion object {
        fun fromStorage(value: String?): VisualizationNoteNameFormat {
            return entries.firstOrNull { it.storageValue == value } ?: American
        }
    }
}

enum class VisualizationChannelScopeTextColorMode(
    val storageValue: String,
    val label: String
) {
    Monet("monet", "Monet accent"),
    OpenMptInspired("openmpt_inspired", "OpenMPT-inspired"),
    White("white", "White"),
    Custom("custom", "Custom");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTextColorMode {
            return entries.firstOrNull { it.storageValue == value } ?: Monet
        }
    }
}

enum class VisualizationChannelScopeTextFont(
    val storageValue: String,
    val label: String
) {
    System("system", "System"),
    RaccoonSerif("raccoon_serif", "Raccoon Serif"),
    RaccoonMono("raccoon_mono", "Raccoon Mono"),
    RetroCuteMono("retro_cute_mono", "Retro Pixel Cute Mono"),
    RetroThick("retro_thick", "Retro Pixel Thick");

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTextFont {
            return entries.firstOrNull { it.storageValue == value } ?: RetroCuteMono
        }
    }
}

enum class VisualizationOscFpsMode(
    val storageValue: String,
    val label: String
) {
    Default("default", "30 fps (Default)"),
    Fps60("60fps", "60 fps"),
    NativeRefresh("native_refresh", "Screen refresh rate");

    companion object {
        fun fromStorage(value: String?): VisualizationOscFpsMode {
            return entries.firstOrNull { it.storageValue == value } ?: Default
        }
    }
}

enum class VisualizationProjectMResolutionMode(
    val storageValue: String,
    val label: String,
    // Maximum of the rendered dimensions in pixels (long edge). 0 = native.
    val maxLongEdgePx: Int
) {
    P360("360p", "360p", 640),
    P480("480p", "480p", 854),
    P720("720p", "720p", 1280),
    P1080("1080p", "1080p", 1920),
    Native("native", "Native screen", 0);

    companion object {
        fun fromStorage(value: String?): VisualizationProjectMResolutionMode {
            return entries.firstOrNull { it.storageValue == value } ?: P720
        }
    }
}

/**
 * projectM requires OpenGL ES 3.0. Availability is cached for the process
 * lifetime using the app context installed by NativeBridge at startup.
 */
@Volatile
private var cachedProjectMSupported: Boolean? = null

fun supportsProjectM(context: Context): Boolean {
    cachedProjectMSupported?.let { return it }
    val activityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val supported = activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    cachedProjectMSupported = supported
    return supported
}

fun supportsProjectM(): Boolean = supportsProjectM(NativeBridge.requireAppContext())
