#include "vis_pipeline.h"
#include <algorithm>

namespace silicon::vis {

SiliconVisPipeline::SiliconVisPipeline() = default;

SiliconVisPipeline::~SiliconVisPipeline() {
    releaseGl();
}

bool SiliconVisPipeline::initGl() {
    if (glInitialized_) return true;
    if (!artworkRenderer_.init()) return false;
    if (!channelScope_.initGl()) return false;
    if (!oscilloscope_.initGl()) return false;
    if (!bars_.initGl()) return false;
    if (!vuMeters_.initGl()) return false;

    for (auto& [modeId, renderer] : pluginRenderers_) {
        if (renderer) renderer->initGl();
    }

    glInitialized_ = true;
    return true;
}

void SiliconVisPipeline::releaseGl() {
    if (!glInitialized_) return;
    artworkRenderer_.release();
    channelScope_.releaseGl();
    oscilloscope_.releaseGl();
    bars_.releaseGl();
    vuMeters_.releaseGl();

    for (auto& [modeId, renderer] : pluginRenderers_) {
        if (renderer) renderer->releaseGl();
    }

    glInitialized_ = false;
}

void SiliconVisPipeline::resize(int32_t widthPx, int32_t heightPx, float density) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    density_ = std::max(1.0f, density);

    glViewport(0, 0, widthPx_, heightPx_);

    channelScope_.resize(widthPx_, heightPx_, density_);
    oscilloscope_.resize(widthPx_, heightPx_, density_);
    bars_.resize(widthPx_, heightPx_, density_);
    vuMeters_.resize(widthPx_, heightPx_, density_);

    for (auto& [modeId, renderer] : pluginRenderers_) {
        if (renderer) renderer->resize(widthPx_, heightPx_, density_);
    }
}

void SiliconVisPipeline::setMode(SiliconVisMode mode) {
    currentMode_ = mode;
}

void SiliconVisPipeline::registerPluginRenderer(VisualizerRendererPtr renderer) {
    if (!renderer) return;
    int32_t modeId = static_cast<int32_t>(renderer->getMode());
    if (glInitialized_) {
        renderer->initGl();
        renderer->resize(widthPx_, heightPx_);
    }
    pluginRenderers_[modeId] = std::move(renderer);
}

void SiliconVisPipeline::setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    artworkRenderer_.setArtworkPixels(rgbaPixels, width, height);
}

void SiliconVisPipeline::clearArtwork() {
    artworkRenderer_.clearArtwork();
}

void SiliconVisPipeline::setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    artworkRenderer_.setIconPixels(rgbaPixels, width, height);
}

void SiliconVisPipeline::clearIcon() {
    artworkRenderer_.clearIcon();
}

void SiliconVisPipeline::setArtworkTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType) {
    artworkRenderer_.setTheme(primaryColorArgb, surfaceColorArgb, placeholderIconType);
}

void SiliconVisPipeline::setContrastMode(SiliconVisContrastMode contrastMode) {
    artworkRenderer_.setContrastMode(contrastMode);
}

void SiliconVisPipeline::setFontAtlas(
    const uint8_t* rgbaPixels,
    int32_t width,
    int32_t height,
    float baseFontSizePx,
    float lineHeightPx,
    const gl::Glyph* glyphs,
    int32_t glyphCount
) {
    channelScope_.getFontAtlas().loadCustomAtlas(rgbaPixels, width, height, baseFontSizePx, lineHeightPx, glyphs, glyphCount);
    vuMeters_.getFontAtlas().loadCustomAtlas(rgbaPixels, width, height, baseFontSizePx, lineHeightPx, glyphs, glyphCount);
}

void SiliconVisPipeline::pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) {
    oscilloscope_.pushPcm(pcmInterleaved, frames, channels, sampleRate);
    vuMeters_.pushPcm(pcmInterleaved, frames, channels, sampleRate);

    for (auto& [modeId, renderer] : pluginRenderers_) {
        if (renderer) renderer->pushPcm(pcmInterleaved, frames, channels, sampleRate);
    }
}

void SiliconVisPipeline::setVuLevels(float left, float right) {
    vuMeters_.setVuLevels(left, right);
}

void SiliconVisPipeline::pushFft(const float* magnitudes, int32_t binCount) {
    bars_.pushFft(magnitudes, binCount);

    for (auto& [modeId, renderer] : pluginRenderers_) {
        if (renderer) renderer->pushFft(magnitudes, binCount);
    }
}

void SiliconVisPipeline::pushChannelScopeHistory(int32_t channel, const float* history, int32_t sampleCount) {
    channelScope_.setChannelHistory(channel, history, sampleCount);
}

void SiliconVisPipeline::pushChannelScopeAllHistories(int32_t channelCount, int32_t samplesPerChannel, const float* flatData) {
    channelScope_.setAllChannelHistories(channelCount, samplesPerChannel, flatData);
}

void SiliconVisPipeline::setChannelScopeTextStates(const SiliconVisChannelTextState* states, int32_t count) {
    channelScope_.setTextStates(states, count);
}

IVisualizerRenderer* SiliconVisPipeline::getActiveRenderer() {
    switch (currentMode_) {
        case SILICON_VIS_MODE_CHANNEL_SCOPE:
            return &channelScope_;
        case SILICON_VIS_MODE_OSCILLOSCOPE:
            return &oscilloscope_;
        case SILICON_VIS_MODE_BARS:
            return &bars_;
        case SILICON_VIS_MODE_VU_METERS:
            return &vuMeters_;
        case SILICON_VIS_MODE_NONE:
            return nullptr;
        default: {
            auto it = pluginRenderers_.find(static_cast<int32_t>(currentMode_));
            if (it != pluginRenderers_.end()) {
                return it->second.get();
            }
            return nullptr;
        }
    }
}

void SiliconVisPipeline::render() {
    if (!glInitialized_ || widthPx_ <= 0 || heightPx_ <= 0) return;

    glViewport(0, 0, widthPx_, heightPx_);

    // 1. Render Artwork / Radial Fallback & Contrast Backdrop
    artworkRenderer_.draw(static_cast<float>(widthPx_), static_cast<float>(heightPx_), density_);

    // 2. Render Active Visualizer
    IVisualizerRenderer* active = getActiveRenderer();
    if (active) {
        active->render();
    }
}

} // namespace silicon::vis
