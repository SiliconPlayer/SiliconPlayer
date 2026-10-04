package com.flopster101.siliconplayer


enum class VisualizationMode(
    val storageValue: String,
    val label: String
) {
    Off("off", "Off"),
    Bars("bars", "Bars"),
    Oscilloscope("oscilloscope", "Oscilloscope"),
    VuMeters("vu_meters", "VU meters"),
    ChannelScope("channel_scope", "Channel scope"),
    Starfield("starfield", "Starfield"),
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
        VisualizationMode.Starfield,
        VisualizationMode.ProjectM -> VisualizationModeCategory.Advanced
        VisualizationMode.Off -> null
    }

fun VisualizationMode.isBasicVisualizationMode(): Boolean =
    category == VisualizationModeCategory.Basic

fun VisualizationMode.isAdvancedVisualizationMode(): Boolean =
    category == VisualizationModeCategory.Advanced

enum class StarfieldPreset(val storageValue: String, val label: String) {
    ClassicAmiga("classic", "Classic Amiga"),
    Warp("warp", "Warp"),
    SnowDrift("snow", "Snow drift"),
    BeatRider("beat", "Beat rider");

    companion object {
        fun fromStorage(value: String?): StarfieldPreset {
            return entries.firstOrNull { it.storageValue == value } ?: ClassicAmiga
        }
    }
}

enum class VisualizationRenderBackend(
    val storageValue: String,
    val label: String
) {
    Compose("compose", "Compose"),
    OpenGlTexture("opengl_texture", "OpenGL ES (TextureView)"),
    OpenGlSurface("opengl_surface", "OpenGL ES (SurfaceView)"),
    VulkanTexture("vulkan_texture", "Vulkan (TextureView)"),
    VulkanSurface("vulkan_surface", "Vulkan (SurfaceView)");

    companion object {
        fun fromStorage(value: String?, fallback: VisualizationRenderBackend): VisualizationRenderBackend {
            return when (value) {
                // Legacy migration: old GPU-canvas backend now maps to composited OpenGL backend.
                "gpu" -> OpenGlTexture
                // Legacy migration: old OpenGL value now maps to explicit SurfaceView backend.
                "opengl" -> OpenGlSurface
                "vulkan", "vulkan_surface" -> VulkanSurface
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
        VisualizationMode.Starfield -> VisualizationRenderBackend.OpenGlTexture
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

enum class VisualizationChannelScopeAntialiasMethod(
    val storageValue: String,
    val label: String,
    val nativeValue: Int
) {
    Msaa("msaa", "MSAA", 0),
    Fast("fast", "Fast", 1);

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeAntialiasMethod {
            return entries.firstOrNull { it.storageValue == value } ?: Msaa
        }
    }
}

enum class VisualizationChannelScopeTrackTransition(
    val storageValue: String,
    val label: String,
    val nativeValue: Int
) {
    Instant("instant", "Instant", 0),
    SlideFade("slide_fade", "Slide-fade reveal", 1),
    Crossfade("crossfade", "Crossfade", 2);

    companion object {
        fun fromStorage(value: String?): VisualizationChannelScopeTrackTransition {
            return entries.firstOrNull { it.storageValue == value } ?: Crossfade
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
    Black("black", "Black"),
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

enum class VisualizationPerformanceMode(
    val storageValue: String,
    val label: String,
    val description: String
) {
    Auto("auto", "Auto (Recommended)", "Automatically selects the best performance profile based on device CPU capabilities."),
    HighPerformance("high_performance", "High performance", "Elevates thread priority to maintain high FPS on constrained devices."),
    Balanced("balanced", "Balanced", "Standard UI display priority with good balance between performance and battery life."),
    PowerSaving("power_saving", "Power saving", "Lowers thread priority to maximize battery life.");

    companion object {
        fun fromStorage(value: String?): VisualizationPerformanceMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class VisualizationFullscreenMode(val storageValue: String, val label: String) {
    Complete("complete", "Complete"),
    Compact("compact", "Compact"),
    SuperCompact("super_compact", "Super compact");

    companion object {
        fun fromStorage(value: String?): VisualizationFullscreenMode {
            return entries.firstOrNull { it.storageValue == value } ?: Complete
        }
    }
}
