#include "oscilloscope_renderer.h"
#include <cmath>
#include <algorithm>

namespace silicon::vis {

OscilloscopeRenderer::OscilloscopeRenderer() {
    pcmLeft_.resize(512, 0.0f);
    pcmRight_.resize(512, 0.0f);
}

bool OscilloscopeRenderer::initGl() {
    return flatRenderer_.init();
}

void OscilloscopeRenderer::releaseGl() {
    flatRenderer_.release();
}

void OscilloscopeRenderer::resize(int32_t widthPx, int32_t heightPx, float density) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    density_ = std::max(1.0f, density);
}

void OscilloscopeRenderer::pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) {
    if (!pcmInterleaved || frames <= 0) return;
    int count = std::min(frames, 512);
    pcmLeft_.resize(count);
    pcmRight_.resize(count);

    if (channels >= 2) {
        for (int i = 0; i < count; ++i) {
            pcmLeft_[i] = pcmInterleaved[i * 2];
            pcmRight_[i] = pcmInterleaved[i * 2 + 1];
        }
    } else {
        for (int i = 0; i < count; ++i) {
            pcmLeft_[i] = pcmInterleaved[i];
            pcmRight_[i] = pcmInterleaved[i];
        }
    }
}

void OscilloscopeRenderer::setOptions(
    bool stereo,
    uint32_t waveColorArgb,
    float lineWidthPx,
    uint32_t gridColorArgb,
    float gridWidthPx,
    bool showCenterLine,
    bool showGrid
) {
    stereo_ = stereo;
    waveColorArgb_ = waveColorArgb;
    lineWidthPx_ = lineWidthPx;
    gridColorArgb_ = gridColorArgb;
    gridWidthPx_ = gridWidthPx;
    showCenterLine_ = showCenterLine;
    showGrid_ = showGrid;
}

void OscilloscopeRenderer::buildGeometry() {
    gridLines_.clear();
    waveLinesLeft_.clear();
    waveLinesRight_.clear();

    if (widthPx_ <= 0 || heightPx_ <= 0) return;
    float w = static_cast<float>(widthPx_);
    float h = static_cast<float>(heightPx_);

    // 1. Grid & Centerline
    if (showGrid_) {
        // Vertical grid lines
        for (int i = 1; i < 4; ++i) {
            float x = w * (static_cast<float>(i) / 4.0f);
            gridLines_.push_back(x); gridLines_.push_back(0.0f);
            gridLines_.push_back(x); gridLines_.push_back(h);
        }
    }

    if (showCenterLine_) {
        if (stereo_) {
            float midY1 = h * 0.25f;
            float midY2 = h * 0.75f;
            float sepY = h * 0.50f;

            gridLines_.push_back(0.0f); gridLines_.push_back(midY1);
            gridLines_.push_back(w); gridLines_.push_back(midY1);

            gridLines_.push_back(0.0f); gridLines_.push_back(midY2);
            gridLines_.push_back(w); gridLines_.push_back(midY2);

            gridLines_.push_back(0.0f); gridLines_.push_back(sepY);
            gridLines_.push_back(w); gridLines_.push_back(sepY);
        } else {
            float midY = h * 0.5f;
            gridLines_.push_back(0.0f); gridLines_.push_back(midY);
            gridLines_.push_back(w); gridLines_.push_back(midY);
        }
    }

    // 2. Waveforms
    if (stereo_) {
        float midY1 = h * 0.25f;
        float midY2 = h * 0.75f;
        float maxAmp = h * 0.22f;

        if (pcmLeft_.size() >= 2) {
            float stepX = w / static_cast<float>(pcmLeft_.size() - 1);
            for (size_t i = 0; i < pcmLeft_.size() - 1; ++i) {
                waveLinesLeft_.push_back(i * stepX);
                waveLinesLeft_.push_back(midY1 - pcmLeft_[i] * maxAmp);
                waveLinesLeft_.push_back((i + 1) * stepX);
                waveLinesLeft_.push_back(midY1 - pcmLeft_[i + 1] * maxAmp);
            }
        }
        if (pcmRight_.size() >= 2) {
            float stepX = w / static_cast<float>(pcmRight_.size() - 1);
            for (size_t i = 0; i < pcmRight_.size() - 1; ++i) {
                waveLinesRight_.push_back(i * stepX);
                waveLinesRight_.push_back(midY2 - pcmRight_[i] * maxAmp);
                waveLinesRight_.push_back((i + 1) * stepX);
                waveLinesRight_.push_back(midY2 - pcmRight_[i + 1] * maxAmp);
            }
        }
    } else {
        float midY = h * 0.5f;
        float maxAmp = h * 0.44f;
        if (pcmLeft_.size() >= 2) {
            float stepX = w / static_cast<float>(pcmLeft_.size() - 1);
            for (size_t i = 0; i < pcmLeft_.size() - 1; ++i) {
                waveLinesLeft_.push_back(i * stepX);
                waveLinesLeft_.push_back(midY - pcmLeft_[i] * maxAmp);
                waveLinesLeft_.push_back((i + 1) * stepX);
                waveLinesLeft_.push_back(midY - pcmLeft_[i + 1] * maxAmp);
            }
        }
    }
}

void OscilloscopeRenderer::render() {
    buildGeometry();

    if (!gridLines_.empty()) {
        flatRenderer_.drawLines(
            gridLines_.data(),
            static_cast<int>(gridLines_.size() / 2),
            gridColorArgb_,
            gridWidthPx_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }

    if (!waveLinesLeft_.empty()) {
        flatRenderer_.drawLines(
            waveLinesLeft_.data(),
            static_cast<int>(waveLinesLeft_.size() / 2),
            waveColorArgb_,
            lineWidthPx_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }

    if (!waveLinesRight_.empty()) {
        flatRenderer_.drawLines(
            waveLinesRight_.data(),
            static_cast<int>(waveLinesRight_.size() / 2),
            waveColorArgb_,
            lineWidthPx_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
}

} // namespace silicon::vis
