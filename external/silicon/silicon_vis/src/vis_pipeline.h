#pragma once

#include "silicon/vis/vis_types.h"
#include "silicon/vis/IVisualizerRenderer.h"
#include "silicon/vis/IVisualizationAudioProvider.h"
#include "gl/gl_artwork_renderer.h"
#include "renderers/channel_scope_renderer.h"
#include "renderers/oscilloscope_renderer.h"
#include "renderers/bars_renderer.h"
#include "renderers/vu_meters_renderer.h"
#include <memory>
#include <unordered_map>
#include <vector>

namespace silicon::vis {

class SiliconVisPipeline {
public:
    SiliconVisPipeline();
    ~SiliconVisPipeline();

    bool initGl();
    void resize(int32_t widthPx, int32_t heightPx, float density);
    void releaseGl();

    void setAudioProvider(IVisualizationAudioProvider* provider) { audioProvider_ = provider; }
    IVisualizationAudioProvider* getAudioProvider() const { return audioProvider_; }

    void setMode(SiliconVisMode mode);
    SiliconVisMode getMode() const { return currentMode_; }

    void registerPluginRenderer(VisualizerRendererPtr renderer);

    // Background & Artwork
    void setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height);
    void clearArtwork();
    void setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height);
    void clearIcon();
    void setArtworkTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType);
    void setContrastMode(SiliconVisContrastMode contrastMode);
    void setContrastScrim(uint32_t argb) { artworkRenderer_.setContrastScrim(argb); }
    void setShowArtworkBackground(bool show) { artworkRenderer_.setShowArtworkBackground(show); }

    // Font Atlas
    void setFontAtlas(
        const uint8_t* rgbaPixels,
        int32_t width,
        int32_t height,
        float baseFontSizePx,
        float lineHeightPx,
        const gl::Glyph* glyphs,
        int32_t glyphCount
    );

    void setVisualAlpha(float alpha) { visualAlpha_ = std::max(0.0f, std::min(1.0f, alpha)); }

    // Audio feeds
    void pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate);
    void pushFft(const float* magnitudes, int32_t binCount);
    void setVuLevels(float left, float right);
    void pushChannelScopeHistory(int32_t channel, const float* history, int32_t sampleCount);
    void pushChannelScopeAllHistories(int32_t channelCount, int32_t samplesPerChannel, const float* flatData);
    void setChannelScopeTextStates(const SiliconVisChannelTextState* states, int32_t count);

    // Options
    ChannelScopeRenderer& getChannelScopeRenderer() { return channelScope_; }
    OscilloscopeRenderer& getOscilloscopeRenderer() { return oscilloscope_; }
    BarsRenderer& getBarsRenderer() { return bars_; }
    VuMetersRenderer& getVuMetersRenderer() { return vuMeters_; }

    void render();

private:
    IVisualizerRenderer* getActiveRenderer();

    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    float density_ = 1.0f;
    bool glInitialized_ = false;
    float visualAlpha_ = 1.0f;

    SiliconVisMode currentMode_ = SILICON_VIS_MODE_NONE;
    IVisualizationAudioProvider* audioProvider_ = nullptr;

    std::vector<float> nativeWaveformL_;
    std::vector<float> nativeWaveformR_;
    std::vector<float> nativeFftBars_;
    std::vector<float> nativeFlatScope_;
    std::vector<int32_t> nativeTextStates_;

    gl::GlArtworkRenderer artworkRenderer_;
    ChannelScopeRenderer channelScope_;
    OscilloscopeRenderer oscilloscope_;
    BarsRenderer bars_;
    VuMetersRenderer vuMeters_;

    std::unordered_map<int32_t, VisualizerRendererPtr> pluginRenderers_;
};

} // namespace silicon::vis
