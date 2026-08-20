#pragma once

#include "vis_types.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef void* SiliconVisHandle;

// Engine lifecycle
SiliconVisHandle silicon_vis_create(void);
void silicon_vis_destroy(SiliconVisHandle handle);

bool silicon_vis_init_gl(SiliconVisHandle handle);
void silicon_vis_resize(SiliconVisHandle handle, int32_t widthPx, int32_t heightPx, float density);
void silicon_vis_release_gl(SiliconVisHandle handle);

// Mode selection
void silicon_vis_set_mode(SiliconVisHandle handle, SiliconVisMode mode);
SiliconVisMode silicon_vis_get_mode(SiliconVisHandle handle);

// Background Artwork & Fallback
void silicon_vis_set_artwork_pixels(SiliconVisHandle handle, const uint8_t* rgbaPixels, int32_t width, int32_t height);
void silicon_vis_clear_artwork(SiliconVisHandle handle);
void silicon_vis_set_icon_pixels(SiliconVisHandle handle, const uint8_t* rgbaPixels, int32_t width, int32_t height);
void silicon_vis_clear_icon(SiliconVisHandle handle);
void silicon_vis_set_artwork_theme(SiliconVisHandle handle, uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType);
void silicon_vis_set_contrast_mode(SiliconVisHandle handle, SiliconVisContrastMode contrastMode);
void silicon_vis_set_show_artwork_background(SiliconVisHandle handle, bool show);

// Font Atlas
void silicon_vis_set_font_atlas(
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
void silicon_vis_push_pcm(SiliconVisHandle handle, const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate);
void silicon_vis_push_fft(SiliconVisHandle handle, const float* magnitudes, int32_t binCount);
void silicon_vis_set_vu_levels(SiliconVisHandle handle, float left, float right);
void silicon_vis_push_channel_scope_history(SiliconVisHandle handle, int32_t channel, const float* history, int32_t sampleCount);
void silicon_vis_push_channel_scope_all_histories(SiliconVisHandle handle, int32_t channelCount, int32_t samplesPerChannel, const float* flatData);
void silicon_vis_set_channel_scope_text_states(SiliconVisHandle handle, const SiliconVisChannelTextState* states, int32_t channelCount);

// Options & Parameters
void silicon_vis_set_channel_scope_options(
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
    bool hideWhenOverflow
);

void silicon_vis_set_oscilloscope_options(
    SiliconVisHandle handle,
    bool stereo,
    uint32_t waveColorArgb,
    float lineWidthPx,
    uint32_t gridColorArgb,
    float gridWidthPx,
    bool showCenterLine,
    bool showGrid
);

void silicon_vis_set_bars_options(
    SiliconVisHandle handle,
    int32_t barCount,
    uint32_t startColorArgb,
    uint32_t endColorArgb,
    float cornerRadiusPx,
    bool showFrequencyGuide,
    uint32_t guideColorArgb
);

void silicon_vis_set_vu_meters_options(
    SiliconVisHandle handle,
    bool stereo,
    bool topPlacement,
    uint32_t fillColorArgb,
    uint32_t trackColorArgb,
    uint32_t labelColorArgb
);

// Render frame (calls active visualizer + background inside current GL context)
void silicon_vis_render(SiliconVisHandle handle);

#ifdef __cplusplus
}
#endif
