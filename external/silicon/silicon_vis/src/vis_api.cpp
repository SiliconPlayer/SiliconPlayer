#include "silicon/vis/vis_api.h"
#include "vis_pipeline.h"

using namespace silicon::vis;

extern "C" {

SiliconVisHandle silicon_vis_create(void) {
    auto* pipeline = new SiliconVisPipeline();
    return static_cast<SiliconVisHandle>(pipeline);
}

void silicon_vis_destroy(SiliconVisHandle handle) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    delete pipeline;
}

bool silicon_vis_init_gl(SiliconVisHandle handle) {
    if (!handle) return false;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    return pipeline->initGl();
}

void silicon_vis_resize(SiliconVisHandle handle, int32_t widthPx, int32_t heightPx, float density) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->resize(widthPx, heightPx, density);
}

void silicon_vis_release_gl(SiliconVisHandle handle) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->releaseGl();
}

void silicon_vis_set_audio_provider(SiliconVisHandle handle, void* audioProvider) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setAudioProvider(static_cast<silicon::vis::IVisualizationAudioProvider*>(audioProvider));
}

void silicon_vis_set_mode(SiliconVisHandle handle, SiliconVisMode mode) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setMode(mode);
}

SiliconVisMode silicon_vis_get_mode(SiliconVisHandle handle) {
    if (!handle) return SILICON_VIS_MODE_NONE;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    return pipeline->getMode();
}

void silicon_vis_set_artwork_pixels(SiliconVisHandle handle, const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setArtworkPixels(rgbaPixels, width, height);
}

void silicon_vis_clear_artwork(SiliconVisHandle handle) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->clearArtwork();
}

void silicon_vis_set_icon_pixels(SiliconVisHandle handle, const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setIconPixels(rgbaPixels, width, height);
}

void silicon_vis_clear_icon(SiliconVisHandle handle) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->clearIcon();
}

void silicon_vis_set_artwork_theme(SiliconVisHandle handle, uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setArtworkTheme(primaryColorArgb, surfaceColorArgb, placeholderIconType);
}

void silicon_vis_set_contrast_mode(SiliconVisHandle handle, SiliconVisContrastMode contrastMode) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setContrastMode(contrastMode);
}

void silicon_vis_set_contrast_scrim(SiliconVisHandle handle, uint32_t argb) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setContrastScrim(argb);
}

void silicon_vis_set_show_artwork_background(SiliconVisHandle handle, bool show) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setShowArtworkBackground(show);
}

void silicon_vis_set_font_atlas(
    SiliconVisHandle handle,
    const uint8_t* rgbaPixels,
    int32_t width,
    int32_t height,
    float baseFontSizePx,
    float lineHeightPx,
    const void* glyphs,
    int32_t glyphCount
) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setFontAtlas(
        rgbaPixels,
        width,
        height,
        baseFontSizePx,
        lineHeightPx,
        static_cast<const gl::Glyph*>(glyphs),
        glyphCount
    );
}

void silicon_vis_push_pcm(SiliconVisHandle handle, const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->pushPcm(pcmInterleaved, frames, channels, sampleRate);
}

void silicon_vis_set_vu_levels(SiliconVisHandle handle, float left, float right) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setVuLevels(left, right);
}

void silicon_vis_push_fft(SiliconVisHandle handle, const float* magnitudes, int32_t binCount) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->pushFft(magnitudes, binCount);
}

void silicon_vis_push_channel_scope_history(SiliconVisHandle handle, int32_t channel, const float* history, int32_t sampleCount) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->pushChannelScopeHistory(channel, history, sampleCount);
}

void silicon_vis_push_channel_scope_all_histories(SiliconVisHandle handle, int32_t channelCount, int32_t samplesPerChannel, const float* flatData) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->pushChannelScopeAllHistories(channelCount, samplesPerChannel, flatData);
}

void silicon_vis_set_channel_scope_text_states(SiliconVisHandle handle, const SiliconVisChannelTextState* states, int32_t channelCount) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->setChannelScopeTextStates(states, channelCount);
}

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
    bool hideWhenOverflow,
    int32_t windowMs,
    int32_t gainPercent,
    bool dcRemovalEnabled,
    int32_t triggerMode
) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->getChannelScopeRenderer().setOptions(
        layout, anchor, vuAnchor, vuEnabled, textSizeSp, paddingPx,
        gridColorArgb, gridWidthPx, lineColorArgb, lineWidthPx,
        vuColorArgb, palette, shadowEnabled, hideWhenOverflow,
        windowMs, gainPercent, dcRemovalEnabled, triggerMode
    );
}

void silicon_vis_set_oscilloscope_options(
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
) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->getOscilloscopeRenderer().setOptions(
        stereo, windowMs, triggerMode, waveColorArgb, lineWidthPx, gridColorArgb, gridWidthPx,
        showCenterLine, showGrid
    );
}

void silicon_vis_set_bars_options(
    SiliconVisHandle handle,
    int32_t barCount,
    float smoothing,
    uint32_t startColorArgb,
    uint32_t endColorArgb,
    float cornerRadiusPx,
    bool showFrequencyGuide,
    uint32_t guideColorArgb
) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->getBarsRenderer().setOptions(
        barCount, smoothing, startColorArgb, endColorArgb, cornerRadiusPx,
        showFrequencyGuide, guideColorArgb
    );
}

void silicon_vis_set_vu_meters_options(
    SiliconVisHandle handle,
    bool stereo,
    int anchor,
    float smoothing,
    uint32_t fillColorArgb,
    uint32_t trackColorArgb,
    uint32_t labelColorArgb
) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->getVuMetersRenderer().setOptions(
        stereo, anchor, smoothing, fillColorArgb, trackColorArgb, labelColorArgb
    );
}

void silicon_vis_render(SiliconVisHandle handle) {
    if (!handle) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->render();
}

} // extern "C"

namespace silicon::vis {

void silicon_vis_register_plugin_renderer(SiliconVisHandle handle, IVisualizerRenderer* renderer) {
    if (!handle || !renderer) return;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    pipeline->registerPluginRenderer(VisualizerRendererPtr(renderer));
}

IVisualizationAudioProvider* silicon_vis_get_audio_provider(SiliconVisHandle handle) {
    if (!handle) return nullptr;
    auto* pipeline = static_cast<SiliconVisPipeline*>(handle);
    return pipeline->getAudioProvider();
}

} // namespace silicon::vis
