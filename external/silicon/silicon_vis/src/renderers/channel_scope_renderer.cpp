#include "channel_scope_renderer.h"
#include <cmath>
#include <algorithm>

namespace silicon::vis {

namespace {
constexpr float kWaveCrtSoftnessPx = 0.75f;
constexpr float kWaveCrtColumnOverlapPx = 0.5f;
}

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
    if (!waveRenderer_.init()) return false;
    if (!fontAtlas_.init()) return false;
    if (!textProgram_.init()) return false;
    return true;
}

void ChannelScopeRenderer::releaseGl() {
    flatRenderer_.release();
    waveRenderer_.release();
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
    int32_t triggerMode,
    int32_t waveRenderMode
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
    gainPercent_ = std::clamp(gainPercent, 1, 10000);
    dcRemovalEnabled_ = dcRemovalEnabled;
    triggerMode_ = triggerMode;
    waveRenderMode_ = std::clamp(waveRenderMode, 0, 2);
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

    // Sub-pixel segments flood the rasterizer on narrow cells; decimate to
    // roughly one segment per pixel.
    int waveStride = 1;
    size_t histSize = 0;
    if (!channelHistories_.empty()) {
        histSize = channelHistories_.front().size();
        if (histSize >= 2) {
            const float stepX = cellW / static_cast<float>(histSize - 1);
            if (stepX > 0.0f && stepX < 1.0f) {
                waveStride = std::max(1, static_cast<int>(1.0f / stepX));
            }
        }
    }
    const size_t segmentsPerChannel = histSize >= 2
            ? ((histSize - 2) / waveStride) + 1
            : 0;
    waveformVertices_.reserve(channels * segmentsPerChannel * (waveRenderMode_ == 2 ? 18 : 40));
    gridVertices_.reserve(static_cast<size_t>(cols + rows) * 4);
    vuTrackVertices_.reserve(static_cast<size_t>(channels) * 12);

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

            // Waveforms. Off and AA share one miter-joined ribbon per channel —
            // a single triangle strip with shared vertices, the GL equivalent of
            // a stroked path: no seams, no missing joins, filled corners. Off
            // rasterizes aliased; AA renders through the pipeline's multisample
            // framebuffer. CRT uses columnar bars per decimated sample (OVGen
            // analog-scope stroke) with hard column edges.
            const auto& hist = channelHistories_[ch];
            if (hist.size() >= 2) {
                float stepX = cellW / static_cast<float>(hist.size() - 1);
                float maxAmp = cellH * 0.42f;
                const bool crtMode = waveRenderMode_ == 2;
                const float halfW = lineWidthPx_ * 0.5f;
                const float barHalf = halfW + kWaveCrtSoftnessPx;

                if (!crtMode) {
                    // Round-join stroke, the GL equivalent of Agg's default:
                    // per-segment quads with shared edge vertices, plus a screen-
                    // aligned disc at every point where the direction turns. The
                    // union has exactly lineWidth width everywhere regardless of
                    // angle, so vertical runs keep uniform weight, and corners
                    // are filled by the discs.
                    static thread_local std::vector<float> px, py, nx, ny;
                    px.clear(); py.clear(); nx.clear(); ny.clear();
                    for (size_t i = 0; i < hist.size(); i += waveStride) {
                        const float x = cellLeft + i * stepX;
                        const float y = centerY - (hist[i] * maxAmp);
                        if (!px.empty()) {
                            const float ddx = x - px.back();
                            const float ddy = y - py.back();
                            if (ddx * ddx + ddy * ddy < 1e-8f) continue;
                        }
                        px.push_back(x);
                        py.push_back(y);
                    }
                    const size_t lastIdx = hist.size() - 1;
                    const float lastX = cellLeft + lastIdx * stepX;
                    const float lastY = centerY - (hist[lastIdx] * maxAmp);
                    if (px.empty() || px.back() != lastX || py.back() != lastY) {
                        px.push_back(lastX);
                        py.push_back(lastY);
                    }
                    const size_t n = px.size();
                    if (n >= 2) {
                        for (size_t k = 0; k + 1 < n; ++k) {
                            float dx = px[k + 1] - px[k];
                            float dy = py[k + 1] - py[k];
                            const float segLen = std::sqrt(dx * dx + dy * dy);
                            if (segLen < 1e-4f) continue;
                            dx /= segLen;
                            dy /= segLen;
                            const float ox = -dy * halfW;
                            const float oy = dx * halfW;

                            waveformVertices_.push_back(px[k] + ox); waveformVertices_.push_back(py[k] + oy);
                            waveformVertices_.push_back(px[k + 1] + ox); waveformVertices_.push_back(py[k + 1] + oy);
                            waveformVertices_.push_back(px[k] - ox); waveformVertices_.push_back(py[k] - oy);

                            waveformVertices_.push_back(px[k + 1] + ox); waveformVertices_.push_back(py[k + 1] + oy);
                            waveformVertices_.push_back(px[k + 1] - ox); waveformVertices_.push_back(py[k + 1] - oy);
                            waveformVertices_.push_back(px[k] - ox); waveformVertices_.push_back(py[k] - oy);
                        }

                        // Turn-point discs: cap the joins where consecutive
                        // segment directions differ.
                        const int segments = 10;
                        for (size_t k = 1; k + 1 < n; ++k) {
                            float d0x = px[k] - px[k - 1];
                            float d0y = py[k] - py[k - 1];
                            float d1x = px[k + 1] - px[k];
                            float d1y = py[k + 1] - py[k];
                            const float l0 = std::sqrt(d0x * d0x + d0y * d0y);
                            const float l1 = std::sqrt(d1x * d1x + d1y * d1y);
                            if (l0 < 1e-4f || l1 < 1e-4f) continue;
                            d0x /= l0; d0y /= l0; d1x /= l1; d1y /= l1;
                            const float dot = d0x * d1x + d0y * d1y;
                            if (dot > 0.999f) continue;

                            const float cx = px[k];
                            const float cy = py[k];
                            float prevX = cx - d0y * halfW;
                            float prevY = cy + d0x * halfW;
                            const float endX = cx - d1y * halfW;
                            const float endY = cy + d1x * halfW;
                            float angle0 = std::atan2(prevY - cy, prevX - cx);
                            float angle1 = std::atan2(endY - cy, endX - cx);
                            float sweep = angle1 - angle0;
                            if (sweep > (float)M_PI) sweep -= 2.0f * (float)M_PI;
                            if (sweep < -(float)M_PI) sweep += 2.0f * (float)M_PI;
                            for (int s = 1; s <= segments; ++s) {
                                const float a = angle0 + sweep * (static_cast<float>(s) / segments);
                                const float vx = cx + std::cos(a) * halfW;
                                const float vy = cy + std::sin(a) * halfW;
                                waveformVertices_.push_back(cx); waveformVertices_.push_back(cy);
                                waveformVertices_.push_back(prevX); waveformVertices_.push_back(prevY);
                                waveformVertices_.push_back(vx); waveformVertices_.push_back(vy);
                                prevX = vx;
                                prevY = vy;
                            }
                        }
                    }
                } else {
                for (size_t i = 0; i + 1 < hist.size(); i += waveStride) {
                    size_t j = std::min(i + static_cast<size_t>(waveStride), hist.size() - 1);
                    float x0 = cellLeft + i * stepX;
                    float y0 = centerY - (hist[i] * maxAmp);
                    float x1 = cellLeft + j * stepX;
                    float y1 = centerY - (hist[j] * maxAmp);

                    float bx1 = x1 + kWaveCrtColumnOverlapPx;

                    waveformVertices_.push_back(x0); waveformVertices_.push_back(y0 - barHalf);
                    waveformVertices_.push_back(-barHalf);
                    waveformVertices_.push_back(bx1); waveformVertices_.push_back(y0 - barHalf);
                    waveformVertices_.push_back(-barHalf);
                    waveformVertices_.push_back(x0); waveformVertices_.push_back(y0 + barHalf);
                    waveformVertices_.push_back(barHalf);

                    waveformVertices_.push_back(bx1); waveformVertices_.push_back(y0 - barHalf);
                    waveformVertices_.push_back(-barHalf);
                    waveformVertices_.push_back(bx1); waveformVertices_.push_back(y0 + barHalf);
                    waveformVertices_.push_back(barHalf);
                    waveformVertices_.push_back(x0); waveformVertices_.push_back(y0 + barHalf);
                    waveformVertices_.push_back(barHalf);
                }
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
    int selectedSp = std::clamp(textSizeSp_, 6, 22);
    int minSp = std::max(6, selectedSp - 6);

    // Fixed padding swallows narrow cells at high channel counts; cap it per axis.
    float padX = std::min(paddingPx_, cellW * 0.12f);
    float padY = std::min(paddingPx_, cellH * 0.18f);
    float effPadDp = padX / density_;

    int effectiveSp = computeAutoTextSizeSp(selectedSp, minSp, cellWidthDp, effPadDp);
    if (hideWhenOverflow_ && estimateTextWidthDp(effectiveSp, effPadDp) > cellWidthDp) {
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
            float maxRight = cellRight - padX;

            float originY = (anchor_ == SILICON_VIS_TEXT_ANCHOR_TOP_LEFT ||
                             anchor_ == SILICON_VIS_TEXT_ANCHOR_TOP_CENTER ||
                             anchor_ == SILICON_VIS_TEXT_ANCHOR_TOP_RIGHT)
                ? (cellTop + padY * 0.42f)
                : (cellBottom - lineHeight - padY);

            float cursorX = cellLeft + padX;
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
        if (waveRenderMode_ == 0 || waveRenderMode_ == 1) {
            flatRenderer_.drawTriangles(
                waveformVertices_.data(),
                static_cast<int>(waveformVertices_.size() / 2),
                lineColorArgb_,
                static_cast<float>(widthPx_),
                static_cast<float>(heightPx_)
            );
        } else {
            waveRenderer_.draw(
                waveformVertices_.data(),
                static_cast<int>(waveformVertices_.size() / 3),
                lineColorArgb_,
                lineWidthPx_ * 0.5f,
                kWaveCrtSoftnessPx,
                static_cast<float>(widthPx_),
                static_cast<float>(heightPx_)
            );
        }
    }
}

} // namespace silicon::vis
