#include "vis_pipeline.h"
#include "vk/vk_pipeline.h"
#include <algorithm>
#include <cstring>

namespace silicon::vis {

SiliconVisPipeline::SiliconVisPipeline() = default;

SiliconVisPipeline::~SiliconVisPipeline() {
    releaseMsaaTarget();
    releaseGl();
    releaseVulkan();
}

bool SiliconVisPipeline::initGl() {
    if (glInitialized_) return true;

#if !defined(__ANDROID__)
    #ifndef GL_PROGRAM_POINT_SIZE
    #define GL_PROGRAM_POINT_SIZE 0x8642
    #endif
    glEnable(GL_PROGRAM_POINT_SIZE);
#endif

    if (!artworkRenderer_.init()) return false;
    if (!channelScope_.initGl()) return false;
    if (!oscilloscope_.initGl()) return false;
    if (!bars_.initGl()) return false;
    if (!starfield_.initGl()) return false;
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
    starfield_.releaseGl();
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
    starfield_.resize(widthPx_, heightPx_, density_);
    vuMeters_.resize(widthPx_, heightPx_, density_);

    for (auto& [modeId, renderer] : pluginRenderers_) {
        if (renderer) renderer->resize(widthPx_, heightPx_, density_);
    }
}

void SiliconVisPipeline::setMode(SiliconVisMode mode) {
    currentMode_ = mode;
}

void SiliconVisPipeline::setChannelScopeAntialiasMethod(int32_t method) {
    scopeAntialiasMethod_ = method;
    channelScope_.setFastLinesEnabled(method == 1);
    if (vulkanPipeline_) {
        vulkanPipeline_->setMsaaEnabled(method != 1);
    }
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
    if (rgbaPixels && width > 0 && height > 0) {
        cachedArtworkWidth_ = width;
        cachedArtworkHeight_ = height;
        cachedArtworkRgba_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    } else {
        cachedArtworkWidth_ = 0;
        cachedArtworkHeight_ = 0;
        cachedArtworkRgba_.clear();
    }
    artworkRenderer_.setArtworkPixels(rgbaPixels, width, height);
    if (vulkanPipeline_) {
        vulkanPipeline_->setArtworkPixels(rgbaPixels, width, height);
    }
}

void SiliconVisPipeline::clearArtwork() {
    cachedArtworkWidth_ = 0;
    cachedArtworkHeight_ = 0;
    cachedArtworkRgba_.clear();
    artworkRenderer_.clearArtwork();
    if (vulkanPipeline_) {
        vulkanPipeline_->clearArtwork();
    }
}

void SiliconVisPipeline::setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (rgbaPixels && width > 0 && height > 0) {
        cachedIconWidth_ = width;
        cachedIconHeight_ = height;
        cachedIconRgba_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    } else {
        cachedIconWidth_ = 0;
        cachedIconHeight_ = 0;
        cachedIconRgba_.clear();
    }
    artworkRenderer_.setIconPixels(rgbaPixels, width, height);
    if (vulkanPipeline_) {
        vulkanPipeline_->setIconPixels(rgbaPixels, width, height);
    }
}

void SiliconVisPipeline::clearIcon() {
    cachedIconWidth_ = 0;
    cachedIconHeight_ = 0;
    cachedIconRgba_.clear();
    artworkRenderer_.clearIcon();
    if (vulkanPipeline_) {
        vulkanPipeline_->clearIcon();
    }
}

void SiliconVisPipeline::setArtworkTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType) {
    primaryColorArgb_ = primaryColorArgb;
    surfaceColorArgb_ = surfaceColorArgb;
    placeholderIconType_ = placeholderIconType;
    artworkRenderer_.setTheme(primaryColorArgb, surfaceColorArgb, placeholderIconType);
    if (vulkanPipeline_) {
        float r = ((surfaceColorArgb >> 16) & 0xFF) / 255.0f;
        float g = ((surfaceColorArgb >> 8) & 0xFF) / 255.0f;
        float b = (surfaceColorArgb & 0xFF) / 255.0f;
        vulkanPipeline_->setClearColor(r, g, b, 1.0f);
        vulkanPipeline_->setTheme(primaryColorArgb, surfaceColorArgb, placeholderIconType);
    }
}

void SiliconVisPipeline::setContrastMode(SiliconVisContrastMode contrastMode) {
    contrastMode_ = contrastMode;
    artworkRenderer_.setContrastMode(contrastMode);
    if (vulkanPipeline_) {
        vulkanPipeline_->setContrastMode(contrastMode);
    }
}

void SiliconVisPipeline::setContrastScrim(uint32_t argb) {
    contrastScrimArgb_ = argb;
    artworkRenderer_.setContrastScrim(argb);
    if (vulkanPipeline_) {
        vulkanPipeline_->setContrastScrim(argb);
    }
}

void SiliconVisPipeline::setShowArtworkBackground(bool show) {
    showArtworkBackground_ = show;
    artworkRenderer_.setShowArtworkBackground(show);
    if (vulkanPipeline_) {
        vulkanPipeline_->setShowArtworkBackground(show);
    }
}

void SiliconVisPipeline::setBackdropMonochrome(bool enabled) {
    backdropMonochrome_ = enabled;
    artworkRenderer_.setMonochromeTarget(enabled);
    if (vulkanPipeline_) {
        vulkanPipeline_->setMonochromeTarget(enabled);
    }
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
    if (rgbaPixels && width > 0 && height > 0) {
        customFontWidth_ = width;
        customFontHeight_ = height;
        customFontRgba_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    }
    channelScope_.getFontAtlas().loadCustomAtlas(rgbaPixels, width, height, baseFontSizePx, lineHeightPx, glyphs, glyphCount);
    vuMeters_.getFontAtlas().loadCustomAtlas(rgbaPixels, width, height, baseFontSizePx, lineHeightPx, glyphs, glyphCount);
    if (vulkanPipeline_ && vulkanPipeline_->isReady() && rgbaPixels && width > 0 && height > 0) {
        vulkanPipeline_->getFontAtlas().updateAtlas(rgbaPixels, width, height);
    }
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

void SiliconVisPipeline::setTextQuads(const float* quads, int32_t vertexCount) {
    channelScope_.setTextQuads(quads, vertexCount);
}

IVisualizerRenderer* SiliconVisPipeline::getActiveRenderer() {
    switch (currentMode_) {
        case SILICON_VIS_MODE_CHANNEL_SCOPE:
            return &channelScope_;
        case SILICON_VIS_MODE_OSCILLOSCOPE:
            return &oscilloscope_;
        case SILICON_VIS_MODE_BARS:
            return &bars_;
        case SILICON_VIS_MODE_STARFIELD:
            return &starfield_;
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
    // Fast is a channel-scope-only opt-out; starfield additionally
    // skips the resolve on proprietary Mali drivers, where it leaves
    // edge residue behind. Every other GPU keeps the smoother path.
    if (currentMode_ == SILICON_VIS_MODE_STARFIELD && msaaStarfieldBlocked_) return false;
    return currentMode_ != SILICON_VIS_MODE_CHANNEL_SCOPE || scopeAntialiasMethod_ != 1;
}

bool SiliconVisPipeline::probeMsaaSupport() {
    if (msaaProbed_) return msaaSupported_;
    msaaProbed_ = true;
    const char* renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    const bool isMali = renderer != nullptr && std::strstr(renderer, "Mali") != nullptr;
    // Panfrost exposes a Mali renderer string but resolves cleanly.
    const bool isPanfrost = (renderer != nullptr && std::strstr(renderer, "Panfrost") != nullptr) ||
        (version != nullptr && std::strstr(version, "Mesa") != nullptr);
    msaaStarfieldBlocked_ = isMali && !isPanfrost;
    GLint maxSamples = 0;
    glGetIntegerv(GL_MAX_SAMPLES, &maxSamples);
    // Two-sample devices still resolve visibly smoother edges; cap at 4,
    // past which resolve bandwidth starts to cost on older GPUs.
    msaaMaxSamples_ = maxSamples >= 2 ? std::min<GLint>(maxSamples, 4) : 0;
    msaaSupported_ = msaaMaxSamples_ > 0;
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
    msaaSamples_ = msaaMaxSamples_;

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
        VIS_LOGW("Vis MSAA: framebuffer incomplete (0x%x), falling back", status);
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
            case SILICON_VIS_MODE_STARFIELD: {
                float left = 0.0f, right = 0.0f;
                audioProvider_->getVuLevels(left, right);
                starfield_.setEnergy(std::max(left, right));
                // Kicks live in the low end: feed the lowest spectrum
                // bins so bass punches through above broadband RMS.
                audioProvider_->getFftBars(nativeFftBars_);
                float bass = 0.0f;
                const size_t bassBins = std::min(nativeFftBars_.size(), size_t{48});
                for (size_t i = 0; i < bassBins; ++i) bass = std::max(bass, nativeFftBars_[i]);
                starfield_.setBassLevel(bass);
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

    // Modes resolve through the multisample target when the GPU offers
    // one: traces, bars, grids and glyph edges all antialias at once.
    // Starfield opts out on proprietary Mali drivers (edge residue); fast-lines
    // mode (channel scope opt-out) feathers traces in-shader instead
    // and draws directly; devices without MSAA fall
    // back to direct rendering with feathered traces automatically.
    const bool useMsaa = wantsMsaa() && probeMsaaSupport() &&
        ensureMsaaTarget(widthPx_, heightPx_);
    if (useMsaa) {
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo_);
    } else {
        glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(targetFbo_));
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
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, static_cast<GLuint>(targetFbo_));
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo_);
        glBlitFramebuffer(
            0, 0, msaaWidth_, msaaHeight_,
            0, 0, widthPx_, heightPx_,
            GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, static_cast<GLuint>(targetFbo_));
    }
}

bool SiliconVisPipeline::initVulkan(uint32_t width, uint32_t height, void* nativeWindow) {
    if (!vulkanPipeline_) {
        vulkanPipeline_ = std::make_unique<vk::SiliconVisVulkanPipeline>();
    }
    vulkanPipeline_->setMsaaEnabled(scopeAntialiasMethod_ != 1);
    widthPx_ = static_cast<int32_t>(width);
    heightPx_ = static_cast<int32_t>(height);
    bool ok = vulkanPipeline_->init(width, height, nativeWindow);
    if (ok) {
        widthPx_ = static_cast<int32_t>(vulkanPipeline_->getWidth());
        heightPx_ = static_cast<int32_t>(vulkanPipeline_->getHeight());
        channelScope_.resize(widthPx_, heightPx_, density_);
        bars_.resize(widthPx_, heightPx_, density_);
        starfield_.resize(widthPx_, heightPx_, density_);
        starfield_.setMaxPointSizePx(vulkanPipeline_->getStarfield().getMaxPointSize());
        float r = ((surfaceColorArgb_ >> 16) & 0xFF) / 255.0f;
        float g = ((surfaceColorArgb_ >> 8) & 0xFF) / 255.0f;
        float b = (surfaceColorArgb_ & 0xFF) / 255.0f;
        vulkanPipeline_->setClearColor(r, g, b, 1.0f);
        vulkanPipeline_->setTheme(primaryColorArgb_, surfaceColorArgb_, placeholderIconType_);
        vulkanPipeline_->setContrastMode(contrastMode_);
        vulkanPipeline_->setContrastScrim(contrastScrimArgb_);
        vulkanPipeline_->setShowArtworkBackground(showArtworkBackground_);
        vulkanPipeline_->setMonochromeTarget(backdropMonochrome_);
        if (cachedArtworkWidth_ > 0 && !cachedArtworkRgba_.empty()) {
            vulkanPipeline_->setArtworkPixels(cachedArtworkRgba_.data(), cachedArtworkWidth_, cachedArtworkHeight_);
        }
        if (cachedIconWidth_ > 0 && !cachedIconRgba_.empty()) {
            vulkanPipeline_->setIconPixels(cachedIconRgba_.data(), cachedIconWidth_, cachedIconHeight_);
        }
        if (!customFontRgba_.empty()) {
            vulkanPipeline_->getFontAtlas().updateAtlas(customFontRgba_.data(), customFontWidth_, customFontHeight_);
        }
    }
    return ok;
}

void SiliconVisPipeline::resizeVulkan(uint32_t width, uint32_t height, float density) {
    widthPx_ = static_cast<int32_t>(width);
    heightPx_ = static_cast<int32_t>(height);
    density_ = std::max(1.0f, density);
    if (vulkanPipeline_) {
        vulkanPipeline_->resize(width, height, density);
        widthPx_ = static_cast<int32_t>(vulkanPipeline_->getWidth());
        heightPx_ = static_cast<int32_t>(vulkanPipeline_->getHeight());
    }
    channelScope_.resize(widthPx_, heightPx_, density_);
    starfield_.resize(widthPx_, heightPx_, density_);
}

void SiliconVisPipeline::releaseVulkan() {
    if (vulkanPipeline_) {
        vulkanPipeline_->release();
        vulkanPipeline_.reset();
    }
}

bool SiliconVisPipeline::isVulkanReady() const {
    return vulkanPipeline_ && vulkanPipeline_->isReady();
}

bool SiliconVisPipeline::readbackVulkan(void* outRgbaBuffer, size_t bufferSize) {
    if (!vulkanPipeline_) return false;
    return vulkanPipeline_->copyPixelsToBuffer(outRgbaBuffer, bufferSize);
}

void SiliconVisPipeline::renderVulkan() {
    if (!vulkanPipeline_ || !vulkanPipeline_->isReady()) return;

    if (audioProvider_) {
        switch (currentMode_) {
            case SILICON_VIS_MODE_BARS: {
                audioProvider_->getFftBars(nativeFftBars_);
                bars_.pushFft(nativeFftBars_.data(), static_cast<int32_t>(nativeFftBars_.size()));
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
            case SILICON_VIS_MODE_STARFIELD: {
                float left = 0.0f, right = 0.0f;
                audioProvider_->getVuLevels(left, right);
                starfield_.setEnergy(std::max(left, right));
                audioProvider_->getFftBars(nativeFftBars_);
                float bass = 0.0f;
                const size_t bassBins = std::min(nativeFftBars_.size(), size_t{48});
                for (size_t i = 0; i < bassBins; ++i) bass = std::max(bass, nativeFftBars_[i]);
                starfield_.setBassLevel(bass);
                break;
            }
            default:
                break;
        }
    }

    if (currentMode_ == SILICON_VIS_MODE_STARFIELD) {
        renderStarfieldVulkan();
        return;
    }

    if (!vulkanPipeline_->beginFrame()) {
        return;
    }

    VkCommandBuffer cmd = vulkanPipeline_->getCurrentCommandBuffer();
    float w = static_cast<float>(vulkanPipeline_->getWidth());
    float h = static_cast<float>(vulkanPipeline_->getHeight());

    vulkanPipeline_->drawArtwork(cmd, density_);

    if (currentMode_ == SILICON_VIS_MODE_CHANNEL_SCOPE) {
        if (widthPx_ != static_cast<int32_t>(w) || heightPx_ != static_cast<int32_t>(h)) {
            widthPx_ = static_cast<int32_t>(w);
            heightPx_ = static_cast<int32_t>(h);
            channelScope_.resize(widthPx_, heightPx_, density_);
            starfield_.resize(widthPx_, heightPx_, density_);
            starfield_.setMaxPointSizePx(vulkanPipeline_->getStarfield().getMaxPointSize());
        }
        channelScope_.renderVk(
            cmd,
            &vulkanPipeline_->getPipelines(),
            &vulkanPipeline_->getVertexBuffer(),
            (uint64_t)(uintptr_t)vulkanPipeline_->getFontAtlas().getDescriptorSet(),
            w,
            h
        );
    }

    if (currentMode_ == SILICON_VIS_MODE_BARS) {
        if (widthPx_ != static_cast<int32_t>(w) || heightPx_ != static_cast<int32_t>(h)) {
            widthPx_ = static_cast<int32_t>(w);
            heightPx_ = static_cast<int32_t>(h);
            bars_.resize(widthPx_, heightPx_, density_);
        }
        bars_.setAlpha(visualAlpha_);
        bars_.renderVk(
            cmd,
            &vulkanPipeline_->getPipelines(),
            &vulkanPipeline_->getVertexBuffer(),
            w,
            h
        );
    }

    if (transitionAlpha_ > 0.001f && vulkanPipeline_->hasTransitionSnapshot()) {
        vulkanPipeline_->drawTransition(transitionOffsetX_, transitionAlpha_);
    }

    vulkanPipeline_->endFrame();
}

void SiliconVisPipeline::renderStarfieldVulkan() {
    if (!vulkanPipeline_->beginFrameNoPass()) {
        return;
    }

    VkCommandBuffer cmd = vulkanPipeline_->getCurrentCommandBuffer();
    float w = static_cast<float>(vulkanPipeline_->getWidth());
    float h = static_cast<float>(vulkanPipeline_->getHeight());
    if (widthPx_ != static_cast<int32_t>(w) || heightPx_ != static_cast<int32_t>(h)) {
        widthPx_ = static_cast<int32_t>(w);
        heightPx_ = static_cast<int32_t>(h);
        starfield_.resize(widthPx_, heightPx_, density_);
    }

    // Trail and bloom passes run before the main pass begins.
    starfield_.setAlpha(visualAlpha_);
    starfield_.simulate();
    vk::StarVkFrame frame{};
    frame.pointCount = starfield_.getPointCount();
    frame.pos = starfield_.getPositions();
    frame.size = starfield_.getSizes();
    frame.alpha = starfield_.getAlphas();
    frame.lineCount = starfield_.getLineCount();
    frame.lineVerts = starfield_.getLineVerts();
    frame.starColorArgb = starfield_.getStarColorArgb();
    frame.softness = starfield_.getSoftness();
    frame.globalAlpha = starfield_.getGlobalAlpha();
    frame.brightness = starfield_.getFlashBoost();
    frame.squareStars = starfield_.usesSquareStars();
    frame.fadeAlpha = starfield_.getFadeAlpha();
    frame.wantBloom = starfield_.wantsBloom();
    frame.bloomStrength = starfield_.getBloomStrength();
    frame.glowK = starfield_.getGlowK();
    const bool trailsOk = vulkanPipeline_->getStarfield().renderTrails(
        cmd, &vulkanPipeline_->getVertexBuffer(), frame,
        vulkanPipeline_->getWidth(), vulkanPipeline_->getHeight());

    vulkanPipeline_->beginMainPass();
    vulkanPipeline_->drawArtwork(cmd, density_);
    if (trailsOk) {
        vulkanPipeline_->getStarfield().composite(
            cmd, &vulkanPipeline_->getPipelines(), &vulkanPipeline_->getVertexBuffer(), frame);
    }

    if (transitionAlpha_ > 0.001f && vulkanPipeline_->hasTransitionSnapshot()) {
        vulkanPipeline_->drawTransition(transitionOffsetX_, transitionAlpha_);
    }

    vulkanPipeline_->endFrame();
}

bool SiliconVisPipeline::takeTransitionSnapshotVulkan() {
    if (!vulkanPipeline_) return false;
    return vulkanPipeline_->takeTransitionSnapshot();
}

void SiliconVisPipeline::releaseTransitionSnapshotVulkan() {
    if (vulkanPipeline_) {
        vulkanPipeline_->releaseTransitionSnapshot();
    }
}

void SiliconVisPipeline::setTransitionVulkan(float offsetX, float alpha) {
    transitionOffsetX_ = offsetX;
    transitionAlpha_ = alpha;
}

} // namespace silicon::vis
