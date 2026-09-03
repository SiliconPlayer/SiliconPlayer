#pragma once

#include "vis_types.h"

#ifdef __cplusplus
namespace silicon::vis { class IVisualizerRenderer; }
#endif

#if defined(_WIN32) || defined(__CYGWIN__)
  #if defined(SILICON_VIS_BUILDING_DLL)
    #define SILICON_VIS_API __declspec(dllexport)
  #else
    #define SILICON_VIS_API __declspec(dllimport)
  #endif
#else
  #if defined(__GNUC__) && __GNUC__ >= 4
    #define SILICON_VIS_API __attribute__((visibility("default")))
  #else
    #define SILICON_VIS_API
  #endif
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef void* SiliconVisHandle;

// Engine lifecycle
SILICON_VIS_API SiliconVisHandle silicon_vis_create(void);
SILICON_VIS_API void silicon_vis_destroy(SiliconVisHandle handle);

SILICON_VIS_API bool silicon_vis_init_gl(SiliconVisHandle handle);
SILICON_VIS_API void silicon_vis_resize(SiliconVisHandle handle, int32_t widthPx, int32_t heightPx, float density);
SILICON_VIS_API void silicon_vis_release_gl(SiliconVisHandle handle);
SILICON_VIS_API void silicon_vis_set_audio_provider(SiliconVisHandle handle, void* audioProvider);

// Mode selection
SILICON_VIS_API void silicon_vis_set_mode(SiliconVisHandle handle, SiliconVisMode mode);
SILICON_VIS_API SiliconVisMode silicon_vis_get_mode(SiliconVisHandle handle);

// Background Artwork & Fallback
SILICON_VIS_API void silicon_vis_set_artwork_pixels(SiliconVisHandle handle, const uint8_t* rgbaPixels, int32_t width, int32_t height);
SILICON_VIS_API void silicon_vis_clear_artwork(SiliconVisHandle handle);
SILICON_VIS_API void silicon_vis_set_icon_pixels(SiliconVisHandle handle, const uint8_t* rgbaPixels, int32_t width, int32_t height);
SILICON_VIS_API void silicon_vis_clear_icon(SiliconVisHandle handle);
SILICON_VIS_API void silicon_vis_set_artwork_theme(SiliconVisHandle handle, uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType);
SILICON_VIS_API void silicon_vis_set_contrast_mode(SiliconVisHandle handle, SiliconVisContrastMode contrastMode);
SILICON_VIS_API void silicon_vis_set_contrast_scrim(SiliconVisHandle handle, uint32_t argb);
SILICON_VIS_API void silicon_vis_set_show_artwork_background(SiliconVisHandle handle, bool show);
SILICON_VIS_API void silicon_vis_set_visual_alpha(SiliconVisHandle handle, float alpha);

// Font Atlas
SILICON_VIS_API void silicon_vis_set_font_atlas(
    SiliconVisHandle handle,
    const uint8_t* rgbaPixels,
    int32_t width,
    int32_t height,
    float baseFontSizePx,
    float lineHeightPx,
    const void* glyphs,
    int32_t glyphCount
);

// Audio Feeds
SILICON_VIS_API void silicon_vis_push_pcm(SiliconVisHandle handle, const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate);
SILICON_VIS_API void silicon_vis_push_fft(SiliconVisHandle handle, const float* magnitudes, int32_t binCount);
SILICON_VIS_API void silicon_vis_set_vu_levels(SiliconVisHandle handle, float left, float right);
SILICON_VIS_API void silicon_vis_push_channel_scope_history(SiliconVisHandle handle, int32_t channel, const float* history, int32_t sampleCount);
SILICON_VIS_API void silicon_vis_push_channel_scope_all_histories(SiliconVisHandle handle, int32_t channelCount, int32_t samplesPerChannel, const float* flatData);
SILICON_VIS_API void silicon_vis_set_channel_scope_text_states(SiliconVisHandle handle, const SiliconVisChannelTextState* states, int32_t channelCount);

// Options & Parameters
SILICON_VIS_API void silicon_vis_set_channel_scope_options(
    SiliconVisHandle handle,
    SiliconVisChannelLayout layout,
    SiliconVisTextAnchor anchor,
    SiliconVisVuAnchor vuAnchor,
    bool vuEnabled,
    int32_t textSizeSp,
    float paddingPx,
    uint32_t gridColorArgb,
    float gridWidthPx,
    uint32_t lineColorArgb,
    float lineWidthPx,
    uint32_t vuColorArgb,
    const SiliconVisTextPalette* palette,
    bool shadowEnabled,
    bool hideWhenOverflow,
    int32_t windowMs,
    int32_t gainPercent,
    bool dcRemovalEnabled,
    int32_t triggerMode,
    SiliconVisWaveRenderMode waveRenderMode
);

SILICON_VIS_API void silicon_vis_set_oscilloscope_options(
    SiliconVisHandle handle,
    bool stereo,
    int32_t windowMs,
    int32_t triggerMode,
    uint32_t waveColorArgb,
    float lineWidthPx,
    uint32_t gridColorArgb,
    float gridWidthPx,
    bool showCenterLine,
    bool showGrid
);

SILICON_VIS_API void silicon_vis_set_bars_options(
    SiliconVisHandle handle,
    int32_t barCount,
    float smoothing,
    uint32_t startColorArgb,
    uint32_t endColorArgb,
    float cornerRadiusPx,
    bool showFrequencyGuide,
    uint32_t guideColorArgb
);

SILICON_VIS_API void silicon_vis_set_vu_meters_options(
    SiliconVisHandle handle,
    bool stereo,
    int anchor,
    float smoothing,
    uint32_t fillColorArgb,
    uint32_t trackColorArgb,
    uint32_t labelColorArgb
);

// Render frame (calls active visualizer + background inside current GL context)
SILICON_VIS_API void silicon_vis_render(SiliconVisHandle handle);

#ifdef __cplusplus
} // extern "C"

namespace silicon::vis {
class IVisualizationAudioProvider;
// Registers a plugin renderer; the pipeline takes ownership and keys it by
// renderer->getMode(). Custom modes start at SILICON_VIS_MODE_CUSTOM_PLUGIN.
SILICON_VIS_API void silicon_vis_register_plugin_renderer(SiliconVisHandle handle, IVisualizerRenderer* renderer);
// Returns the audio provider attached via silicon_vis_set_audio_provider, or null.
SILICON_VIS_API IVisualizationAudioProvider* silicon_vis_get_audio_provider(SiliconVisHandle handle);
}
#endif
