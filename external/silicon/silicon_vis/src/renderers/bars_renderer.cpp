#include "bars_renderer.h"
#include <cmath>
#include <algorithm>

namespace silicon::vis {

BarsRenderer::BarsRenderer() {
    smoothedMagnitudes_.resize(32, 0.0f);
}

bool BarsRenderer::initGl() {
    return flatRenderer_.init();
}

void BarsRenderer::releaseGl() {
    flatRenderer_.release();
}

void BarsRenderer::resize(int32_t widthPx, int32_t heightPx, float density) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    density_ = std::max(1.0f, density);
}

void BarsRenderer::pushFft(const float* magnitudes, int32_t binCount) {
    if (!magnitudes || binCount <= 0) return;
    if (static_cast<int32_t>(smoothedMagnitudes_.size()) != barCount_) {
        smoothedMagnitudes_.resize(barCount_, 0.0f);
    }

    for (int i = 0; i < barCount_; ++i) {
        float lowFraction = static_cast<float>(i) / static_cast<float>(barCount_);
        float highFraction = static_cast<float>(i + 1) / static_cast<float>(barCount_);

        int lowIdx = std::clamp(static_cast<int>(lowFraction * binCount), 0, binCount - 1);
        int highIdx = std::clamp(static_cast<int>(highFraction * binCount), lowIdx + 1, binCount);

        float sum = 0.0f;
        for (int k = lowIdx; k < highIdx; ++k) {
            sum += magnitudes[k];
        }
        float avg = sum / static_cast<float>(highIdx - lowIdx);
        if (smoothing_ > 0.0f) {
            smoothedMagnitudes_[i] = (smoothedMagnitudes_[i] * smoothing_) + (avg * (1.0f - smoothing_));
        } else {
            smoothedMagnitudes_[i] = avg;
        }
    }
}

void BarsRenderer::setOptions(
    int32_t barCount,
    float smoothing,
    uint32_t startColorArgb,
    uint32_t endColorArgb,
    float cornerRadiusPx,
    bool showFrequencyGuide,
    uint32_t guideColorArgb
) {
    barCount_ = std::clamp(barCount, 8, 128);
    smoothing_ = std::clamp(smoothing, 0.0f, 0.98f);
    startColorArgb_ = startColorArgb;
    endColorArgb_ = endColorArgb;
    cornerRadiusPx_ = cornerRadiusPx;
    showFrequencyGuide_ = showFrequencyGuide;
    guideColorArgb_ = guideColorArgb;
}

void BarsRenderer::buildGeometry() {
    barVertices_.clear();
    guideLines_.clear();

    if (widthPx_ <= 0 || heightPx_ <= 0 || barCount_ <= 0) return;

    float w = static_cast<float>(widthPx_);
    float h = static_cast<float>(heightPx_);
    float gapPx = (w / static_cast<float>(barCount_)) * 0.18f;
    float barW = std::max(1.0f, (w - (gapPx * static_cast<float>(barCount_ - 1))) / static_cast<float>(barCount_));

    barVertices_.reserve(barCount_ * 96);

    for (int i = 0; i < barCount_; ++i) {
        float mag = (i < static_cast<int>(smoothedMagnitudes_.size())) ? smoothedMagnitudes_[i] : 0.0f;
        float barH = std::max(2.0f, std::min(h, mag * h));
        float x = i * (barW + gapPx);
        float y = h - barH;
        float r = std::min(cornerRadiusPx_, std::min(barW * 0.45f, barH * 0.5f));

        gl::GlPrimitives::appendRoundedRectTriangles(x, y, barW, barH, r, 3, barVertices_);
    }

    if (showFrequencyGuide_) {
        guideLines_.reserve(12);
        float fractions[3] = { 0.15f, 0.50f, 0.85f };
        for (float f : fractions) {
            float x = w * f;
            guideLines_.push_back(x); guideLines_.push_back(0.0f);
            guideLines_.push_back(x); guideLines_.push_back(h);
        }
    }
}

void BarsRenderer::render() {
    buildGeometry();

    if (!guideLines_.empty()) {
        flatRenderer_.drawLines(
            guideLines_.data(),
            static_cast<int>(guideLines_.size() / 2),
            guideColorArgb_,
            1.0f,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }

    if (!barVertices_.empty()) {
        flatRenderer_.drawTriangles(
            barVertices_.data(),
            static_cast<int>(barVertices_.size() / 2),
            startColorArgb_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
}

} // namespace silicon::vis
