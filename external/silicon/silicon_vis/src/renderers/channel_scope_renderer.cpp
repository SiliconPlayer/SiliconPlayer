#include "channel_scope_renderer.h"
#include <cmath>
#include <algorithm>

namespace silicon::vis {

ChannelScopeRenderer::ChannelScopeRenderer() {
    palette_.channelArgb = 0xFFCCCCCC;
    palette_.noteArgb = 0xFF80D8FF;
    palette_.volumeArgb = 0xFFB9F6CA;
    palette_.effectArgb = 0xFFFFD180;
    palette_.instrumentOrSampleArgb = 0xFFEA80FC;
    palette_.separatorArgb = 0x88FFFFFF;
}

bool ChannelScopeRenderer::initGl() {
    if (!flatRenderer_.init()) return false;
    if (!fontAtlas_.init()) return false;
    if (!textProgram_.init()) return false;
    return true;
}

void ChannelScopeRenderer::releaseGl() {
    flatRenderer_.release();
    fontAtlas_.release();
    textProgram_.release();
}

void ChannelScopeRenderer::resize(int32_t widthPx, int32_t heightPx, float density) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    density_ = std::max(1.0f, density);
}

void ChannelScopeRenderer::setOptions(
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
    layout_ = layout;
    anchor_ = anchor;
    vuAnchor_ = vuAnchor;
    vuEnabled_ = vuEnabled;
    textSizeSp_ = textSizeSp;
    paddingPx_ = paddingPx;
    gridColorArgb_ = gridColorArgb;
    gridWidthPx_ = gridWidthPx;
    lineColorArgb_ = lineColorArgb;
    lineWidthPx_ = lineWidthPx;
    vuColorArgb_ = vuColorArgb;
    if (palette) palette_ = *palette;
    shadowEnabled_ = shadowEnabled;
    hideWhenOverflow_ = hideWhenOverflow;
    windowMs_ = std::clamp(windowMs, 5, 100);
    gainPercent_ = std::clamp(gainPercent, 10, 500);
    dcRemovalEnabled_ = dcRemovalEnabled;
    triggerMode_ = triggerMode;
}

void ChannelScopeRenderer::setChannelHistory(int32_t channel, const float* history, int32_t sampleCount) {
    if (channel < 0 || !history || sampleCount <= 0) return;
    if (channel >= static_cast<int32_t>(channelHistories_.size())) {
        channelHistories_.resize(channel + 1);
    }
    channelHistories_[channel].assign(history, history + sampleCount);
}

void ChannelScopeRenderer::setAllChannelHistories(
    int32_t channelCount,
    int32_t totalSamplesPerChannel,
    const float* flatData,
    int32_t displaySamplesPerChannel
) {
    if (channelCount <= 0 || totalSamplesPerChannel <= 0 || !flatData) {
        channelHistories_.clear();
        return;
    }
    if (static_cast<int32_t>(channelHistories_.size()) != channelCount) {
        channelHistories_.resize(channelCount);
    }
    if (displaySamplesPerChannel <= 0 || displaySamplesPerChannel > totalSamplesPerChannel) {
        displaySamplesPerChannel = totalSamplesPerChannel;
    }

    const float gain = static_cast<float>(gainPercent_) / 100.0f;
    const int displayHalf = displaySamplesPerChannel / 2;
    const bool rising = (triggerMode_ == 1);

    for (int32_t ch = 0; ch < channelCount; ++ch) {
        auto& hist = channelHistories_[ch];
        hist.resize(displaySamplesPerChannel);
        const float* src = flatData + (ch * totalSamplesPerChannel);

        if (triggerMode_ == 0 || totalSamplesPerChannel <= displaySamplesPerChannel) {
            int start = (totalSamplesPerChannel - displaySamplesPerChannel) / 2;
            if (dcRemovalEnabled_) {
                float sum = 0.0f;
                for (int i = 0; i < displaySamplesPerChannel; ++i) sum += src[start + i];
                const float dc = sum / static_cast<float>(displaySamplesPerChannel);
                for (int i = 0; i < displaySamplesPerChannel; ++i) {
                    hist[i] = (src[start + i] - dc) * gain;
                }
            } else if (gainPercent_ != 100) {
                for (int i = 0; i < displaySamplesPerChannel; ++i) {
                    hist[i] = src[start + i] * gain;
                }
            } else {
                std::copy(src + start, src + start + displaySamplesPerChannel, hist.begin());
            }
            continue;
        }

        // Trigger mode (1 = rising, 2 = falling): search outward from center
        int bestIdx = totalSamplesPerChannel / 2;
        float absMax = 0.0f;
        for (int i = 0; i < totalSamplesPerChannel; ++i) {
            float a = std::abs(src[i]);
            if (a > absMax) absMax = a;
        }

        if (absMax >= 0.005f) {
            int lo = totalSamplesPerChannel / 4;
            int hi = (3 * totalSamplesPerChannel) / 4;
            int bestDist = totalSamplesPerChannel;
            int center = totalSamplesPerChannel / 2;

            for (int i = lo + 1; i < hi; ++i) {
                bool crossed = rising ? (src[i - 1] <= 0.0f && src[i] > 0.0f)
                                      : (src[i - 1] >= 0.0f && src[i] < 0.0f);
                if (crossed) {
                    int dist = std::abs(i - center);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestIdx = i;
                    }
                }
            }
        }

        int start = std::clamp(bestIdx - displayHalf, 0, totalSamplesPerChannel - displaySamplesPerChannel);
        if (dcRemovalEnabled_) {
            float sum = 0.0f;
            for (int i = 0; i < displaySamplesPerChannel; ++i) sum += src[start + i];
            const float dc = sum / static_cast<float>(displaySamplesPerChannel);
            for (int i = 0; i < displaySamplesPerChannel; ++i) {
                hist[i] = (src[start + i] - dc) * gain;
            }
        } else if (gainPercent_ != 100) {
            for (int i = 0; i < displaySamplesPerChannel; ++i) {
                hist[i] = src[start + i] * gain;
            }
        } else {
            std::copy(src + start, src + start + displaySamplesPerChannel, hist.begin());
        }
    }
}

void ChannelScopeRenderer::setTextStates(const SiliconVisChannelTextState* states, int32_t count) {
    if (!states || count <= 0) {
        textStates_.clear();
        return;
    }
    textStates_.assign(states, states + count);
}

void ChannelScopeRenderer::resolveGrid(int channelCount, int& outCols, int& outRows) const {
    if (channelCount <= 0) { outCols = 1; outRows = 1; return; }
    switch (layout_) {
        case SILICON_VIS_LAYOUT_SINGLE_COLUMN:
            outCols = 1; outRows = channelCount; return;
        case SILICON_VIS_LAYOUT_DUAL_COLUMN:
            outCols = 2; outRows = (channelCount + 1) / 2; return;
        case SILICON_VIS_LAYOUT_THREE_COLUMN:
            outCols = 3; outRows = (channelCount + 2) / 3; return;
        case SILICON_VIS_LAYOUT_FOUR_COLUMN:
            outCols = 4; outRows = (channelCount + 3) / 4; return;
        case SILICON_VIS_LAYOUT_AUTO:
        default:
            if (channelCount <= 4) { outCols = 1; outRows = channelCount; }
            else if (channelCount <= 12) { outCols = 2; outRows = (channelCount + 1) / 2; }
            else if (channelCount <= 24) { outCols = 3; outRows = (channelCount + 2) / 3; }
            else { outCols = 4; outRows = (channelCount + 3) / 4; }
            return;
    }
}

float ChannelScopeRenderer::computeVuLevel(const std::vector<float>& history) const {
    if (history.empty()) return 0.0f;
    float peak = 0.0f;
    size_t step = std::max<size_t>(1, history.size() / 64);
    for (size_t i = 0; i < history.size(); i += step) {
        float val = std::abs(history[i]);
        if (val > peak) peak = val;
    }
    return std::clamp(peak, 0.0f, 1.0f);
}

void ChannelScopeRenderer::buildGeometry() {
    gridVertices_.clear();
    waveformVertices_.clear();
    vuTrackVertices_.clear();
    vuFillVertices_.clear();

    int channels = static_cast<int>(channelHistories_.size());
    if (channels <= 0 || widthPx_ <= 0 || heightPx_ <= 0) return;

    int cols = 1, rows = 1;
    resolveGrid(channels, cols, rows);
    cols = std::max(1, cols);
    rows = std::max(1, rows);

    float cellW = static_cast<float>(widthPx_) / static_cast<float>(cols);
    float cellH = static_cast<float>(heightPx_) / static_cast<float>(rows);

    // 1. Grid vertical & horizontal separator lines
    for (int c = 1; c < cols; ++c) {
        float x = c * cellW;
        gridVertices_.push_back(x); gridVertices_.push_back(0.0f);
        gridVertices_.push_back(x); gridVertices_.push_back(static_cast<float>(heightPx_));
    }
    for (int r = 1; r < rows; ++r) {
        float y = r * cellH;
        gridVertices_.push_back(0.0f); gridVertices_.push_back(y);
        gridVertices_.push_back(static_cast<float>(widthPx_)); gridVertices_.push_back(y);
    }

    // 2. Waveforms & Mini VU geometry per cell
    float vuHeight = std::max(2.0f, std::floor(2.0f * density_));
    float halfGrid = gridWidthPx_ * 0.5f;

    for (int col = 0; col < cols; ++col) {
        for (int row = 0; row < rows; ++row) {
            int ch = col * rows + row;
            if (ch >= channels) continue;

            float cellLeft = col * cellW;
            float cellTop = row * cellH;
            float cellRight = cellLeft + cellW;
            float cellBottom = cellTop + cellH;
            float centerY = (cellTop + cellBottom) * 0.5f;

            // Waveform lines
            const auto& hist = channelHistories_[ch];
            if (hist.size() >= 2) {
                float stepX = cellW / static_cast<float>(hist.size() - 1);
                float maxAmp = cellH * 0.42f;

                for (size_t i = 0; i < hist.size() - 1; ++i) {
                    float x0 = cellLeft + i * stepX;
                    float y0 = centerY - (hist[i] * maxAmp);
                    float x1 = cellLeft + (i + 1) * stepX;
                    float y1 = centerY - (hist[i + 1] * maxAmp);

                    waveformVertices_.push_back(x0); waveformVertices_.push_back(y0);
                    waveformVertices_.push_back(x1); waveformVertices_.push_back(y1);
                }
            }

            // VU meter track and level fill quads (flush with cell boundaries)
            if (vuEnabled_) {
                float leftMargin = (col > 0) ? halfGrid : 0.0f;
                float rightMargin = (col < cols - 1) ? halfGrid : 0.0f;
                float topMargin = (row > 0) ? halfGrid : 0.0f;
                float bottomMargin = (row < rows - 1) ? halfGrid : 0.0f;

                float trackX = cellLeft + leftMargin;
                float usableW = std::max(0.0f, cellW - leftMargin - rightMargin);
                float trackY = (vuAnchor_ == SILICON_VIS_VU_ANCHOR_TOP)
                    ? (cellTop + topMargin)
                    : (cellBottom - vuHeight - bottomMargin);

                float trackQuad[12] = {
                    trackX, trackY,
                    trackX + usableW, trackY,
                    trackX, trackY + vuHeight,
                    trackX + usableW, trackY,
                    trackX + usableW, trackY + vuHeight,
                    trackX, trackY + vuHeight
                };
                vuTrackVertices_.insert(vuTrackVertices_.end(), trackQuad, trackQuad + 12);

                float vuLevel = computeVuLevel(hist);
                float fillW = usableW * vuLevel;
                if (fillW > 0.0f) {
                    float fillQuad[12] = {
                        trackX, trackY,
                        trackX + fillW, trackY,
                        trackX, trackY + vuHeight,
                        trackX + fillW, trackY,
                        trackX + fillW, trackY + vuHeight,
                        trackX, trackY + vuHeight
                    };
                    vuFillVertices_.insert(vuFillVertices_.end(), fillQuad, fillQuad + 12);
                }
            }
        }
    }
}

void ChannelScopeRenderer::drawVuBars() {
    if (!vuEnabled_) return;
    uint32_t trackColor = (vuColorArgb_ & 0x00FFFFFF) | 0x40000000;
    if (!vuTrackVertices_.empty()) {
        flatRenderer_.drawTriangles(
            vuTrackVertices_.data(),
            static_cast<int>(vuTrackVertices_.size() / 2),
            trackColor,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
    if (!vuFillVertices_.empty()) {
        flatRenderer_.drawTriangles(
            vuFillVertices_.data(),
            static_cast<int>(vuFillVertices_.size() / 2),
            vuColorArgb_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
}

void ChannelScopeRenderer::drawText() {
    int channels = static_cast<int>(channelHistories_.size());
    if (channels <= 0 || widthPx_ <= 0 || heightPx_ <= 0) return;

    int cols = 1, rows = 1;
    resolveGrid(channels, cols, rows);
    cols = std::max(1, cols);
    rows = std::max(1, rows);

    float cellW = static_cast<float>(widthPx_) / static_cast<float>(cols);
    float cellH = static_cast<float>(heightPx_) / static_cast<float>(rows);

    float cellWidthDp = cellW / density_;
    float paddingDp = std::max(2.0f, paddingPx_ / density_);
    int selectedSp = std::clamp(textSizeSp_, 6, 22);
    int minSp = std::max(6, selectedSp - 6);

    int effectiveSp = computeAutoTextSizeSp(selectedSp, minSp, cellWidthDp, paddingDp);
    if (hideWhenOverflow_ && estimateTextWidthDp(effectiveSp, paddingDp) > cellWidthDp) {
        return;
    }

    float effectiveTextPx = static_cast<float>(effectiveSp) * density_;
    float scale = effectiveTextPx / fontAtlas_.getBaseFontSizePx();
    float lineHeight = fontAtlas_.getLineHeightPx() * scale;
    float slotScale = static_cast<float>(effectiveSp) / 8.0f;
    float noteSlotW = 24.0f * slotScale * density_;
    float volSlotW = 30.0f * slotScale * density_;
    float effSlotW = 20.0f * slotScale * density_;
    float itemSpacing = 2.0f * density_;

    textBatcher_.clear();

    for (int col = 0; col < cols; ++col) {
        for (int row = 0; row < rows; ++row) {
            int ch = col * rows + row;
            if (ch >= channels) continue;

            float cellLeft = col * cellW;
            float cellTop = row * cellH;
            float cellRight = cellLeft + cellW;
            float cellBottom = cellTop + cellH;
            float maxRight = cellRight - paddingPx_;

            float originY = (anchor_ == SILICON_VIS_TEXT_ANCHOR_TOP_LEFT ||
                             anchor_ == SILICON_VIS_TEXT_ANCHOR_TOP_CENTER ||
                             anchor_ == SILICON_VIS_TEXT_ANCHOR_TOP_RIGHT)
                ? (cellTop + paddingPx_ * 0.42f)
                : (cellBottom - lineHeight - paddingPx_);

            float cursorX = cellLeft + paddingPx_;
            bool hasPrev = false;

            auto drawSeparator = [&]() {
                if (!hasPrev || cursorX >= maxRight) return;
                float bulletW = fontAtlas_.measureTextWidth("•", scale);
                if (cursorX + bulletW + itemSpacing > maxRight) return;
                gl::Color4f c = gl::argbToColor4f(palette_.separatorArgb);
                textBatcher_.addText(fontAtlas_, "•", cursorX, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, 0.75f, 1.5f, maxRight - cursorX);
                cursorX += bulletW + itemSpacing;
            };

            // 1. Channel label "Ch X"
            std::string chStr = "Ch " + std::to_string(ch + 1);
            if (cursorX < maxRight) {
                gl::Color4f c = gl::argbToColor4f(palette_.channelArgb);
                float dw = textBatcher_.addText(fontAtlas_, chStr, cursorX, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, 0.75f, 1.5f, maxRight - cursorX);
                cursorX += dw + itemSpacing;
                hasPrev = true;
            }

            // 2. Note (centered in noteSlotW)
            if (ch < static_cast<int>(textStates_.size())) {
                const auto& st = textStates_[ch];
                std::string noteStr = (st.note >= 0) ? "C-4" : "--"; // Example note display
                drawSeparator();
                if (cursorX + noteSlotW <= maxRight) {
                    gl::Color4f c = gl::argbToColor4f(palette_.noteArgb);
                    float tw = fontAtlas_.measureTextWidth(noteStr, scale);
                    float tx = cursorX + (noteSlotW - tw) * 0.5f;
                    textBatcher_.addText(fontAtlas_, noteStr, tx, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, 0.75f, 1.5f, maxRight - tx);
                    cursorX += noteSlotW + itemSpacing;
                    hasPrev = true;
                }
            }
        }
    }

    if (textBatcher_.getVertexCount() > 0) {
        textProgram_.draw(
            textBatcher_.getBufferData(),
            static_cast<int>(textBatcher_.getVertexCount()),
            fontAtlas_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
}

int ChannelScopeRenderer::computeAutoTextSizeSp(int selectedSp, int minimumSp, float cellWidthDp, float paddingDp) const {
    if (estimateTextWidthDp(selectedSp, paddingDp) <= cellWidthDp) return selectedSp;
    int size = selectedSp;
    while (size > minimumSp && estimateTextWidthDp(size, paddingDp) > cellWidthDp) {
        size--;
    }
    return size;
}

float ChannelScopeRenderer::estimateTextWidthDp(int sp, float paddingDp) const {
    float scale = static_cast<float>(sp) / 8.0f;
    float width = (26.0f + 24.0f + 30.0f) * scale + (2 * 8.0f * scale) + (2 * 3.0f) + (paddingDp * 2.0f) + 4.0f;
    return width;
}

void ChannelScopeRenderer::render() {
    buildGeometry();

    // 1. VU meters below grid
    drawVuBars();

    // 2. Scope grid lines
    if (!gridVertices_.empty()) {
        flatRenderer_.drawLines(
            gridVertices_.data(),
            static_cast<int>(gridVertices_.size() / 2),
            gridColorArgb_,
            gridWidthPx_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }

    // 3. Scope waveforms
    if (!waveformVertices_.empty()) {
        flatRenderer_.drawLines(
            waveformVertices_.data(),
            static_cast<int>(waveformVertices_.size() / 2),
            lineColorArgb_,
            lineWidthPx_,
            static_cast<float>(widthPx_),
            static_cast<float>(heightPx_)
        );
    }
}

} // namespace silicon::vis
