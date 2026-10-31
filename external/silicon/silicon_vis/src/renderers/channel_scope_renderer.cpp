#include "channel_scope_renderer.h"
#include "vk/vk_primitives.h"
#include "vk/vk_dispatch.h"
#include <cmath>
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace silicon::vis {

namespace {
constexpr float kWaveCrtSoftnessPx = 0.75f;
constexpr float kWaveCrtColumnOverlapPx = 0.5f;
constexpr float kWaveFastSoftnessPx = 1.0f;

// Text slots are sized from the 8sp reference size, matching the layout
// metrics the settings previews quote.
constexpr float kChannelSlotDp = 26.0f;
constexpr float kNoteSlotDp = 24.0f;
constexpr float kVolumeSlotDp = 30.0f;
constexpr float kEffectSlotDp = 20.0f;
constexpr float kChipEstimateDp = 60.0f;
constexpr float kIdentifierEstimateDp = 28.0f;
constexpr float kIdentifierBothEstimateDp = 48.0f;
constexpr float kSeparatorEstimateDp = 8.0f;
constexpr int32_t kAmigaLeftFlag = 1 << 1;
constexpr int32_t kAmigaRightFlag = 1 << 2;

const char* const kNoteNames[12] = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
};
const char* const kInternationalNoteNames[12] = {
    "Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"
};

std::string formatNoteName(int32_t note, int32_t format) {
    if (note <= 0) return {};
    const int32_t index = (note - 1) % 12;
    const int32_t octave = (note - 1) / 12;
    const char* name = format == 1 ? kInternationalNoteNames[index] : kNoteNames[index];
    return std::string(name) + std::to_string(octave);
}

std::string formatVolume(int32_t volume) {
    char buffer[8];
    std::snprintf(buffer, sizeof(buffer), "V%03d", std::clamp(volume, 0, 999));
    return buffer;
}

std::string formatEffect(int32_t letterAscii, int32_t param) {
    if (letterAscii <= 0 || param < 0) return "---";
    const int32_t clampedParam = std::clamp(param, 0, 255);
    char buffer[8];
    if (letterAscii >= 0x100) {
        std::snprintf(buffer, sizeof(buffer), "%02X%02X", letterAscii & 0xFF, clampedParam);
    } else {
        std::snprintf(buffer, sizeof(buffer), "%c%02X", static_cast<char>(letterAscii), clampedParam);
    }
    return buffer;
}

std::string truncateToWidth(
    const gl::GlFontAtlas& atlas,
    const std::string& text,
    float maxWidthPx,
    float scale
) {
    if (maxWidthPx <= 0.0f) return {};
    if (atlas.measureTextWidth(text, scale) <= maxWidthPx) return text;
    const std::string ellipsis = "\xE2\x80\xA6";
    const float ellipsisWidth = atlas.measureTextWidth(ellipsis, scale);
    if (ellipsisWidth > maxWidthPx) return {};
    const float availableForChars = maxWidthPx - ellipsisWidth;
    for (size_t length = text.size(); length > 0; --length) {
        const std::string candidate = text.substr(0, length - 1);
        if (atlas.measureTextWidth(candidate, scale) <= availableForChars) return candidate + ellipsis;
    }
    return {};
}
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
    int32_t triggerAlgorithm,
    int32_t waveRenderMode,
    bool waveformClipping
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
    triggerAlgorithm_ = triggerAlgorithm;
    waveRenderMode_ = std::clamp(waveRenderMode, 0, 2);
    waveformClipping_ = waveformClipping;
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
    int32_t displaySamplesPerChannel,
    int32_t triggerWidthSamples
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
    const auto triggerIndices = triggerMode_ == 0 || totalSamplesPerChannel <= displaySamplesPerChannel
        ? std::vector<int32_t>{}
        : triggerEngine_.computeTriggerIndices(
            flatData, totalSamplesPerChannel, channelCount,
            triggerMode_, triggerAlgorithm_, triggerWidthSamples, 48000);
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
        const int bestIdx = triggerIndices[ch];

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

float ChannelScopeRenderer::updateVuLevel(int channel, const std::vector<float>& history, float deltaSeconds) {
    float peak = 0.0f;
    size_t step = std::max<size_t>(1, history.size() / 64);
    for (size_t i = 0; i < history.size(); i += step) {
        float val = std::abs(history[i]);
        if (val > peak) peak = val;
    }
    peak = std::clamp(peak, 0.0f, 1.0f);
    float& level = vuLevels_[channel];
    const float timeConstant = peak > level ? 0.05f : 1.5f;
    const float alpha = 1.0f - std::exp(-deltaSeconds / timeConstant);
    level += (peak - level) * alpha;
    return level;
}

void ChannelScopeRenderer::buildGeometry() {
    gridVertices_.clear();
    waveformVertices_.clear();
    vuTrackVertices_.clear();
    vuFillVertices_.clear();

    const auto now = std::chrono::steady_clock::now();
    const float deltaSeconds = lastVuUpdate_.time_since_epoch().count() == 0
        ? 1.0f / 60.0f
        : std::chrono::duration<float>(now - lastVuUpdate_).count();
    lastVuUpdate_ = now;

    int channels = static_cast<int>(channelHistories_.size());
    if (static_cast<int>(vuLevels_.size()) != channels) {
        vuLevels_.resize(channels, 0.0f);
    }
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
    waveformVertices_.reserve(channels * segmentsPerChannel * (waveRenderMode_ == 2 ? 18 : ((waveRenderMode_ == 1 && fastLinesEnabled_) ? 60 : 40)));
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
            // rasterizes aliased; MSAA resolves through the pipeline's
            // multisample framebuffer; Fast carries across-distance triplets
            // for the feathered wave-line shader. CRT uses columnar bars per
            // decimated sample (OVGen analog-scope stroke) with hard column edges.
            const auto& hist = channelHistories_[ch];
            if (hist.size() >= 2) {
                float stepX = cellW / static_cast<float>(hist.size() - 1);
                float maxAmp = cellH * 0.42f;
                const bool crtMode = waveRenderMode_ == 2;
                const float halfW = lineWidthPx_ * 0.5f;
                const float barHalf = halfW + kWaveCrtSoftnessPx;
                const float boundMargin = crtMode ? barHalf : halfW;
                float minY = cellTop + boundMargin;
                float maxY = cellBottom - boundMargin;
                if (minY > maxY) {
                    minY = centerY;
                    maxY = centerY;
                }

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
                        const float rawY = centerY - (hist[i] * maxAmp);
                        const float y = waveformClipping_ ? std::clamp(rawY, minY, maxY) : rawY;
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
                    const float lastYRaw = centerY - (hist[lastIdx] * maxAmp);
                    const float lastY = waveformClipping_ ? std::clamp(lastYRaw, minY, maxY) : lastYRaw;
                    if (px.empty() || px.back() != lastX || py.back() != lastY) {
                        px.push_back(lastX);
                        py.push_back(lastY);
                    }
                    const size_t n = px.size();
                    const bool feathered = waveRenderMode_ == 1 && fastLinesEnabled_;
                    if (n >= 2) {
                        for (size_t k = 0; k + 1 < n; ++k) {
                            float dx = px[k + 1] - px[k];
                            float dy = py[k + 1] - py[k];
                            // Double-wide libm: float sqrt/atan2 resolve past Debian-stable glibc.
                            const float segLen = std::sqrt(static_cast<double>(dx * dx + dy * dy));
                            if (segLen < 1e-4f) {
                                nx.push_back(0.0f);
                                ny.push_back(0.0f);
                                continue;
                            }
                            dx /= segLen;
                            dy /= segLen;
                            nx.push_back(dx);
                            ny.push_back(dy);
                            const float ox = -dy * halfW;
                            const float oy = dx * halfW;

                            waveformVertices_.push_back(px[k] + ox); waveformVertices_.push_back(py[k] + oy);
                            if (feathered) waveformVertices_.push_back(halfW);
                            waveformVertices_.push_back(px[k + 1] + ox); waveformVertices_.push_back(py[k + 1] + oy);
                            if (feathered) waveformVertices_.push_back(halfW);
                            waveformVertices_.push_back(px[k] - ox); waveformVertices_.push_back(py[k] - oy);
                            if (feathered) waveformVertices_.push_back(-halfW);

                            waveformVertices_.push_back(px[k + 1] + ox); waveformVertices_.push_back(py[k + 1] + oy);
                            if (feathered) waveformVertices_.push_back(halfW);
                            waveformVertices_.push_back(px[k + 1] - ox); waveformVertices_.push_back(py[k + 1] - oy);
                            if (feathered) waveformVertices_.push_back(-halfW);
                            waveformVertices_.push_back(px[k] - ox); waveformVertices_.push_back(py[k] - oy);
                            if (feathered) waveformVertices_.push_back(-halfW);
                        }

                        // Turn-point discs: cap the joins where consecutive
                        // segment directions differ.
                        for (size_t k = 1; k + 1 < n; ++k) {
                            const float d0x = nx[k - 1];
                            const float d0y = ny[k - 1];
                            const float d1x = nx[k];
                            const float d1y = ny[k];
                            if ((d0x == 0.0f && d0y == 0.0f) ||
                                (d1x == 0.0f && d1y == 0.0f)) continue;
                            const float dot = d0x * d1x + d0y * d1y;
                            if (dot > 0.999f) continue;

                            const float cx = px[k];
                            const float cy = py[k];
                            float prevX = cx - d0y * halfW;
                            float prevY = cy + d0x * halfW;
                            const float cross = d0x * d1y - d0y * d1x;
                            const float sweep = static_cast<float>(
                                std::atan2(static_cast<double>(cross), static_cast<double>(dot))
                            );
                            const int segments = std::max(
                                1,
                                static_cast<int>(std::ceil(std::abs(sweep) / (static_cast<float>(M_PI) / 10.0f)))
                            );
                            const float angleStep = sweep / segments;
                            const float cosStep = std::cos(angleStep);
                            const float sinStep = std::sin(angleStep);
                            float radialX = -d0y * halfW;
                            float radialY = d0x * halfW;
                            for (int s = 1; s <= segments; ++s) {
                                const float nextRadialX = radialX * cosStep - radialY * sinStep;
                                const float nextRadialY = radialX * sinStep + radialY * cosStep;
                                const float vx = cx + nextRadialX;
                                const float vy = cy + nextRadialY;
                                waveformVertices_.push_back(cx); waveformVertices_.push_back(cy);
                                if (feathered) waveformVertices_.push_back(0.0f);
                                waveformVertices_.push_back(prevX); waveformVertices_.push_back(prevY);
                                if (feathered) waveformVertices_.push_back(halfW);
                                waveformVertices_.push_back(vx); waveformVertices_.push_back(vy);
                                if (feathered) waveformVertices_.push_back(halfW);
                                prevX = vx;
                                prevY = vy;
                                radialX = nextRadialX;
                                radialY = nextRadialY;
                            }
                        }
                    }
                } else {
                    for (size_t i = 0; i + 1 < hist.size(); i += waveStride) {
                        size_t j = std::min(i + static_cast<size_t>(waveStride), hist.size() - 1);
                        float x0 = cellLeft + i * stepX;
                        float rawY0 = centerY - (hist[i] * maxAmp);
                        float y0 = waveformClipping_ ? std::clamp(rawY0, minY, maxY) : rawY0;
                        float x1 = cellLeft + j * stepX;

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

                float vuLevel = updateVuLevel(ch, hist, deltaSeconds);
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


std::vector<std::string> ChannelScopeRenderer::splitNewlineSeparated(const char* text) {
    std::vector<std::string> parts;
    if (!text) return parts;
    const std::string joined(text);
    size_t start = 0;
    while (true) {
        const size_t end = joined.find('\n', start);
        if (end == std::string::npos) {
            parts.push_back(joined.substr(start));
            return parts;
        }
        parts.push_back(joined.substr(start, end - start));
        start = end + 1;
    }
}

void ChannelScopeRenderer::setChannelNames(const char* newlineSeparatedNames) {
    channelNames_ = splitNewlineSeparated(newlineSeparatedNames);
}

void ChannelScopeRenderer::setIdentifierNames(
    const char* newlineSeparatedInstruments,
    const char* newlineSeparatedSamples
) {
    instrumentNames_ = splitNewlineSeparated(newlineSeparatedInstruments);
    sampleNames_ = splitNewlineSeparated(newlineSeparatedSamples);
}

void ChannelScopeRenderer::setTextFields(
    bool showChannel,
    bool showNote,
    bool showVolume,
    bool showEffectPrimary,
    bool showEffectSecondary,
    bool showChip,
    bool showInstrument,
    bool showSample,
    int32_t noteFormat
) {
    showChannel_ = showChannel;
    showNote_ = showNote;
    showVolume_ = showVolume;
    showEffectPrimary_ = showEffectPrimary;
    showEffectSecondary_ = showEffectSecondary;
    showChip_ = showChip;
    showInstrument_ = showInstrument;
    showSample_ = showSample;
    noteFormat_ = noteFormat;
}

int ChannelScopeRenderer::effectSlotCount() const {
    return (showEffectPrimary_ ? 1 : 0) + (showEffectSecondary_ ? 1 : 0);
}

std::string ChannelScopeRenderer::lookupChannelName(int32_t channel, const SiliconVisChannelTextState* state) const {
    const int32_t preferred = state ? state->channelIndex : channel;
    if (preferred >= 0 && preferred < static_cast<int32_t>(channelNames_.size()) &&
        !channelNames_[preferred].empty()) {
        return channelNames_[preferred];
    }
    if (channel >= 0 && channel < static_cast<int32_t>(channelNames_.size())) {
        return channelNames_[channel];
    }
    return {};
}

ChannelScopeRenderer::TextFields ChannelScopeRenderer::buildTextFields(
    int32_t channel,
    int32_t sideCounts[2]
) const {
    TextFields fields;
    const SiliconVisChannelTextState* state =
        channel < static_cast<int32_t>(textStates_.size()) ? &textStates_[channel] : nullptr;
    const int32_t flags = state ? state->flags : 0;

    if (showChannel_) {
        fields.channel = lookupChannelName(channel, state);
        if (fields.channel.empty()) {
            const bool isLeft = (flags & kAmigaLeftFlag) != 0;
            const bool isRight = (flags & kAmigaRightFlag) != 0;
            if (isLeft && ++sideCounts[0] <= 2) {
                fields.channel = "L" + std::to_string(sideCounts[0]);
            } else if (isRight && ++sideCounts[1] <= 2) {
                fields.channel = "R" + std::to_string(sideCounts[1]);
            } else {
                fields.channel = "Ch " + std::to_string(channel + 1);
            }
        }
    }
    if (showChip_) {
        fields.chip = lookupChannelName(channel, state);
        if (fields.chip == fields.channel) fields.chip.clear();
    }
    if (showNote_) {
        fields.note = formatNoteName(state ? state->note : -1, noteFormat_);
        if (fields.note.empty()) fields.note = "--";
    }
    if (showVolume_) {
        fields.volume = formatVolume(state ? state->volume : 0);
    }
    if (showEffectPrimary_) {
        fields.effectPrimary = formatEffect(
            state ? state->effectPrimaryLetterAscii : 0,
            state ? state->effectPrimaryParam : -1
        );
    }
    if (showEffectSecondary_) {
        fields.effectSecondary = formatEffect(
            state ? state->effectSecondaryLetterAscii : 0,
            state ? state->effectSecondaryParam : -1
        );
    }
    if ((showInstrument_ || showSample_) && state) {
        std::string identifier;
        if (showInstrument_ && state->instrumentIndex > 0) {
            identifier = "I#" + std::to_string(state->instrumentIndex);
            const int32_t index = state->instrumentIndex;
            if (index < static_cast<int32_t>(instrumentNames_.size()) && !instrumentNames_[index].empty()) {
                identifier += " " + instrumentNames_[index];
            }
        }
        if (showSample_ && state->sampleIndex > 0) {
            if (!identifier.empty()) identifier += " / ";
            identifier += "S#" + std::to_string(state->sampleIndex);
            const int32_t index = state->sampleIndex;
            if (index < static_cast<int32_t>(sampleNames_.size()) && !sampleNames_[index].empty()) {
                identifier += " " + sampleNames_[index];
            }
        }
        fields.identifier = identifier;
    }
    return fields;
}

void ChannelScopeRenderer::buildTextGeometry() {
    textBatcher_.clear();
    if (!textEnabled_) return;

    int channels = static_cast<int>(channelHistories_.size());
    if (channels <= 0 || widthPx_ <= 0 || heightPx_ <= 0) return;

    int cols = 1, rows = 1;
    resolveGrid(channels, cols, rows);
    cols = std::max(1, cols);
    rows = std::max(1, rows);

    float cellW = static_cast<float>(widthPx_) / static_cast<float>(cols);
    float cellH = static_cast<float>(heightPx_) / static_cast<float>(rows);

    float cellWidthDp = cellW / density_;
    int selectedSp = std::clamp(textSizeSp_, 6, 32);
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
    float noteSlotW = kNoteSlotDp * slotScale * density_;
    float volSlotW = kVolumeSlotDp * slotScale * density_;
    float effSlotW = kEffectSlotDp * slotScale * density_;
    float itemSpacing = 2.0f * density_;

    const float shadowOffset = std::clamp(scale * 2.8f, 0.75f, 3.0f);
    const float shadowA = 0.50f * alpha_;
    int32_t sideCounts[2] = {0, 0};

    auto textColor = [this](uint32_t argb) {
        gl::Color4f c = gl::argbToColor4f(argb);
        c.a *= alpha_;
        return c;
    };

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
            float prevTrail = 0.0f;

            // Center the dot in the visual gap left by centered slot text.
            auto drawSeparator = [&](float nextLead) {
                if (!hasPrev || cursorX >= maxRight) return;
                float bulletW = fontAtlas_.measureTextWidth("\xE2\x80\xA2", scale);
                if (cursorX + bulletW + itemSpacing > maxRight) return;
                float bulletX = cursorX + (nextLead - prevTrail) * 0.5f;
                float lower = cursorX - itemSpacing;
                float upper = std::max(lower, cursorX + itemSpacing + nextLead);
                bulletX = std::clamp(bulletX, lower, upper);
                gl::Color4f c = textColor(palette_.separatorArgb);
                textBatcher_.addText(fontAtlas_, "\xE2\x80\xA2", bulletX, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - bulletX);
                cursorX += bulletW + itemSpacing;
            };

            const TextFields fields = buildTextFields(ch, sideCounts);

            // 1. Channel label
            if (!fields.channel.empty() && cursorX < maxRight) {
                const std::string text = truncateToWidth(fontAtlas_, fields.channel, maxRight - cursorX, scale);
                if (!text.empty()) {
                    gl::Color4f c = textColor(palette_.channelArgb);
                    float dw = textBatcher_.addText(fontAtlas_, text, cursorX, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - cursorX);
                    cursorX += dw + itemSpacing;
                    hasPrev = true;
                    prevTrail = 0.0f;
                }
            }

            // 2. Note (centered in its slot)
            if (!fields.note.empty()) {
                float lead = (noteSlotW - fontAtlas_.measureTextWidth(fields.note, scale)) * 0.5f;
                drawSeparator(lead);
                if (cursorX + noteSlotW <= maxRight) {
                    gl::Color4f c = textColor(palette_.noteArgb);
                    textBatcher_.addText(fontAtlas_, fields.note, cursorX + lead, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - cursorX);
                    cursorX += noteSlotW + itemSpacing;
                    hasPrev = true;
                    prevTrail = lead;
                }
            }

            // 3. Volume
            if (!fields.volume.empty()) {
                float lead = (volSlotW - fontAtlas_.measureTextWidth(fields.volume, scale)) * 0.5f;
                drawSeparator(lead);
                if (cursorX + volSlotW <= maxRight) {
                    gl::Color4f c = textColor(palette_.volumeArgb);
                    textBatcher_.addText(fontAtlas_, fields.volume, cursorX + lead, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - cursorX);
                    cursorX += volSlotW + itemSpacing;
                    hasPrev = true;
                    prevTrail = lead;
                }
            }

            // 4. Effects
            const std::string* effects[2] = {nullptr, nullptr};
            int effectCount = 0;
            if (showEffectPrimary_) effects[effectCount++] = &fields.effectPrimary;
            if (showEffectSecondary_) effects[effectCount++] = &fields.effectSecondary;
            for (int i = 0; i < effectCount; ++i) {
                float lead = (effSlotW - fontAtlas_.measureTextWidth(*effects[i], scale)) * 0.5f;
                drawSeparator(lead);
                if (cursorX + effSlotW <= maxRight) {
                    gl::Color4f c = textColor(palette_.effectArgb);
                    textBatcher_.addText(fontAtlas_, *effects[i], cursorX + lead, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - cursorX);
                    cursorX += effSlotW + itemSpacing;
                    hasPrev = true;
                    prevTrail = lead;
                }
            }

            // 5. Chip
            if (!fields.chip.empty()) {
                drawSeparator(0.0f);
                if (maxRight - cursorX > effectiveTextPx / 4.0f) {
                    const std::string text = truncateToWidth(fontAtlas_, fields.chip, maxRight - cursorX, scale);
                    if (!text.empty()) {
                        gl::Color4f c = textColor(palette_.channelArgb);
                        float dw = textBatcher_.addText(fontAtlas_, text, cursorX, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - cursorX);
                        cursorX += dw + itemSpacing;
                        hasPrev = true;
                        prevTrail = 0.0f;
                    }
                }
            }

            // 6. Instrument / sample
            if (!fields.identifier.empty()) {
                drawSeparator(0.0f);
                if (maxRight - cursorX > effectiveTextPx / 4.0f) {
                    const std::string text = truncateToWidth(fontAtlas_, fields.identifier, maxRight - cursorX, scale);
                    if (!text.empty()) {
                        gl::Color4f c = textColor(palette_.instrumentOrSampleArgb);
                        textBatcher_.addText(fontAtlas_, text, cursorX, originY, scale, c.r, c.g, c.b, c.a, shadowEnabled_, 0, 0, 0, shadowA, shadowOffset, maxRight - cursorX);
                    }
                }
            }
        }
    }
}

void ChannelScopeRenderer::drawText() {
    buildTextGeometry();
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
    const float scale = static_cast<float>(sp) / 8.0f;
    int fieldCount = 0;
    float width = 0.0f;
    if (showChannel_) {
        width += kChannelSlotDp * scale;
        fieldCount++;
    }
    if (showNote_) {
        width += kNoteSlotDp * scale;
        fieldCount++;
    }
    if (showVolume_) {
        width += kVolumeSlotDp * scale;
        fieldCount++;
    }
    const int effects = effectSlotCount();
    for (int i = 0; i < effects; ++i) {
        width += kEffectSlotDp * scale;
        fieldCount++;
    }
    if (showChip_) {
        width += kChipEstimateDp * scale;
        fieldCount++;
    }
    if (showInstrument_ || showSample_) {
        width += (showInstrument_ && showSample_) ? kIdentifierBothEstimateDp * scale : kIdentifierEstimateDp * scale;
        fieldCount++;
    }
    const int separators = std::max(0, fieldCount - 1);
    width += static_cast<float>(separators) * (kSeparatorEstimateDp * scale);
    width += static_cast<float>(separators) * 3.0f;
    width += paddingDp * 2.0f;
    width += 4.0f;
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
        if (waveRenderMode_ == 0 || (waveRenderMode_ == 1 && !fastLinesEnabled_)) {
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
                waveRenderMode_ == 2 ? kWaveCrtSoftnessPx : kWaveFastSoftnessPx,
                static_cast<float>(widthPx_),
                static_cast<float>(heightPx_)
            );
        }
    }
    // 4. Labels
    drawText();
}

void ChannelScopeRenderer::renderVk(
    void* cmdBuffer,
    void* pipelines,
    void* dynamicVertexBuffer,
    uint64_t fontDescriptorSet,
    float width,
    float height
) {
    if (!cmdBuffer || !pipelines || !dynamicVertexBuffer || width <= 0.0f || height <= 0.0f) return;

    VkCommandBuffer cmd = static_cast<VkCommandBuffer>(cmdBuffer);
    auto* prims = static_cast<vk::VkPrimitivePipelines*>(pipelines);
    auto* dynBuffer = static_cast<vk::VkDynamicVertexBuffer*>(dynamicVertexBuffer);
    const auto& table = vk::VkLoader::table();

    VkDescriptorSet fontDesc{};
    std::memcpy(&fontDesc, &fontDescriptorSet, sizeof(VkDescriptorSet));

    buildGeometry();
    if (fontDesc != VK_NULL_HANDLE) {
        buildTextGeometry();
    }

    VkBuffer buffer = dynBuffer->getBuffer();

    auto withAlpha = [this](uint32_t argb) {
        const float a = ((argb >> 24) & 0xFF) / 255.0f * alpha_;
        const uint32_t ai = static_cast<uint32_t>(std::clamp(a, 0.0f, 1.0f) * 255.0f);
        return (argb & 0x00FFFFFF) | (ai << 24);
    };

    // 1. VU Tracks
    if (vuEnabled_ && !vuTrackVertices_.empty()) {
        size_t sizeBytes = vuTrackVertices_.size() * sizeof(float);
        size_t offset = dynBuffer->allocate(vuTrackVertices_.data(), sizeBytes);
        if (offset != static_cast<size_t>(-1)) {
            uint32_t trackColor = (vuColorArgb_ & 0x00FFFFFF) | 0x40000000;
            prims->bindFlatTriangles(cmd, width, height, withAlpha(trackColor));
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, static_cast<uint32_t>(vuTrackVertices_.size() / 2), 1, 0, 0);
        }
    }

    // 2. VU Fills
    if (vuEnabled_ && !vuFillVertices_.empty()) {
        size_t sizeBytes = vuFillVertices_.size() * sizeof(float);
        size_t offset = dynBuffer->allocate(vuFillVertices_.data(), sizeBytes);
        if (offset != static_cast<size_t>(-1)) {
            prims->bindFlatTriangles(cmd, width, height, withAlpha(vuColorArgb_));
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, static_cast<uint32_t>(vuFillVertices_.size() / 2), 1, 0, 0);
        }
    }

    // 3. Grid Lines
    if (!gridVertices_.empty()) {
        size_t sizeBytes = gridVertices_.size() * sizeof(float);
        size_t offset = dynBuffer->allocate(gridVertices_.data(), sizeBytes);
        if (offset != static_cast<size_t>(-1)) {
            prims->bindFlatLines(cmd, width, height, withAlpha(gridColorArgb_), gridWidthPx_);
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, static_cast<uint32_t>(gridVertices_.size() / 2), 1, 0, 0);
        }
    }

    // 4. Waveforms
    if (!waveformVertices_.empty()) {
        size_t sizeBytes = waveformVertices_.size() * sizeof(float);
        size_t offset = dynBuffer->allocate(waveformVertices_.data(), sizeBytes);
        if (offset != static_cast<size_t>(-1)) {
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            if (waveRenderMode_ == 0 || (waveRenderMode_ == 1 && !fastLinesEnabled_)) {
                prims->bindFlatTriangles(cmd, width, height, withAlpha(lineColorArgb_));
                table.vkCmdDraw(cmd, static_cast<uint32_t>(waveformVertices_.size() / 2), 1, 0, 0);
            } else {
                float halfWidth = lineWidthPx_ * 0.5f;
                float softness = (waveRenderMode_ == 2) ? kWaveCrtSoftnessPx : kWaveFastSoftnessPx;
                prims->bindWaveLines(cmd, width, height, withAlpha(lineColorArgb_), halfWidth, softness);
                table.vkCmdDraw(cmd, static_cast<uint32_t>(waveformVertices_.size() / 3), 1, 0, 0);
            }
        }
    }

    // 5. Text
    if (fontDesc != VK_NULL_HANDLE) {
        if (textBatcher_.getVertexCount() > 0) {
            size_t sizeBytes = textBatcher_.getVertexCount() * 8 * sizeof(float);
            size_t offset = dynBuffer->allocate(textBatcher_.getBufferData(), sizeBytes);
            if (offset != static_cast<size_t>(-1)) {
                prims->bindText(cmd, width, height, fontDesc);
                VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
                table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
                table.vkCmdDraw(cmd, static_cast<uint32_t>(textBatcher_.getVertexCount()), 1, 0, 0);
            }
        }
    }
}

} // namespace silicon::vis
