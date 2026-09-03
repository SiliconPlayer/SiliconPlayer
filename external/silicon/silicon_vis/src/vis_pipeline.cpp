#include "vis_pipeline.h"
#include <algorithm>

namespace silicon::vis {

SiliconVisPipeline::SiliconVisPipeline() = default;

SiliconVisPipeline::~SiliconVisPipeline() {
    releaseMsaaTarget();
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
    releaseMsaaTarget();
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

bool SiliconVisPipeline::wantsMsaa() const {
    return currentMode_ == SILICON_VIS_MODE_CHANNEL_SCOPE &&
            channelScope_.getWaveRenderMode() == 1;
}

bool SiliconVisPipeline::probeMsaaSupport() {
    if (msaaProbed_) return msaaSupported_;
    msaaProbed_ = true;
    GLint maxSamples = 0;
    glGetIntegerv(GL_MAX_SAMPLES, &maxSamples);
    msaaSupported_ = maxSamples >= 4;
    return msaaSupported_;
}

bool SiliconVisPipeline::ensureMsaaTarget(int32_t width, int32_t height) {
    if (msaaFbo_ != 0 && msaaWidth_ == width && msaaHeight_ == height && msaaSamples_ > 0) {
        return true;
    }
    releaseMsaaTarget();
    if (!probeMsaaSupport()) {
        return false;
    }
    msaaSamples_ = 4;

    glGenFramebuffers(1, &msaaFbo_);
    glGenRenderbuffers(1, &msaaColorRb_);
    glBindRenderbuffer(GL_RENDERBUFFER, msaaColorRb_);
    glRenderbufferStorageMultisample(GL_RENDERBUFFER, msaaSamples_, GL_RGBA8, width, height);
    glGenRenderbuffers(1, &msaaDepthRb_);
    glBindRenderbuffer(GL_RENDERBUFFER, msaaDepthRb_);
    glRenderbufferStorageMultisample(GL_RENDERBUFFER, msaaSamples_, GL_DEPTH_COMPONENT16, width, height);

    glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo_);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColorRb_);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msaaDepthRb_);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        VIS_LOGW("Scope MSAA: framebuffer incomplete (0x%x), falling back", status);
        releaseMsaaTarget();
        return false;
    }

    msaaWidth_ = width;
    msaaHeight_ = height;
    return true;
}

void SiliconVisPipeline::releaseMsaaTarget() {
    if (msaaFbo_ != 0) glDeleteFramebuffers(1, &msaaFbo_);
    if (msaaColorRb_ != 0) glDeleteRenderbuffers(1, &msaaColorRb_);
    if (msaaDepthRb_ != 0) glDeleteRenderbuffers(1, &msaaDepthRb_);
    msaaFbo_ = 0;
    msaaColorRb_ = 0;
    msaaDepthRb_ = 0;
    msaaWidth_ = 0;
    msaaHeight_ = 0;
    msaaSamples_ = 0;
}

void SiliconVisPipeline::render() {
    if (!glInitialized_ || widthPx_ <= 0 || heightPx_ <= 0) return;

    if (audioProvider_) {
        switch (currentMode_) {
            case SILICON_VIS_MODE_OSCILLOSCOPE: {
                const int windowMs = oscilloscope_.getWindowMs();
                const int triggerMode = oscilloscope_.getTriggerMode();
                audioProvider_->getWaveformScope(0, windowMs, triggerMode, nativeWaveformL_);
                if (oscilloscope_.isStereo()) {
                    audioProvider_->getWaveformScope(1, windowMs, triggerMode, nativeWaveformR_);
                    oscilloscope_.setWaveforms(
                        nativeWaveformL_.data(), static_cast<int32_t>(nativeWaveformL_.size()),
                        nativeWaveformR_.data(), static_cast<int32_t>(nativeWaveformR_.size())
                    );
                } else {
                    oscilloscope_.setWaveforms(
                        nativeWaveformL_.data(), static_cast<int32_t>(nativeWaveformL_.size()),
                        nullptr, 0
                    );
                }
                break;
            }
            case SILICON_VIS_MODE_BARS: {
                audioProvider_->getFftBars(nativeFftBars_);
                bars_.pushFft(nativeFftBars_.data(), static_cast<int32_t>(nativeFftBars_.size()));
                break;
            }
            case SILICON_VIS_MODE_VU_METERS: {
                float left = 0.0f, right = 0.0f;
                audioProvider_->getVuLevels(left, right);
                vuMeters_.setVuLevels(left, right);
                break;
            }
            case SILICON_VIS_MODE_CHANNEL_SCOPE: {
                int chCount = 0;
                const int windowMs = channelScope_.getWindowMs();
                int displaySamples = (48000 * windowMs) / 1000;
                displaySamples = std::clamp(displaySamples, 64, 2048);
                const int fetchSamples = displaySamples * 2;
                audioProvider_->getChannelScopeHistories(fetchSamples, 0, nativeFlatScope_, chCount);
                if (chCount > 0 && !nativeFlatScope_.empty()) {
                    channelScope_.setAllChannelHistories(chCount, fetchSamples, nativeFlatScope_.data(), displaySamples);
                }
                break;
            }
            default:
                break;
        }
    }

    glViewport(0, 0, widthPx_, heightPx_);

    // The AA wave mode needs hardware multisampling: the window surface has a
    // fixed sample config, so render into an MSAA FBO and resolve to the
    // default framebuffer. Other modes draw directly.
    const bool useMsaa = wantsMsaa() && probeMsaaSupport() && ensureMsaaTarget(widthPx_, heightPx_);
    if (useMsaa) {
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo_);
    }

    // 1. Render Artwork / Radial Fallback & Contrast Backdrop
    artworkRenderer_.draw(static_cast<float>(widthPx_), static_cast<float>(heightPx_), density_);

    // 2. Render Active Visualizer
    IVisualizerRenderer* active = getActiveRenderer();
    if (active) {
        active->setAlpha(visualAlpha_);
        active->render();
    }

    if (useMsaa) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo_);
        glBlitFramebuffer(
                0, 0, msaaWidth_, msaaHeight_,
                0, 0, widthPx_, heightPx_,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}

} // namespace silicon::vis
