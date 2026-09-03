#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "gl/gl_primitives.h"
#include "gl/gl_font_atlas.h"
#include <vector>
#include <string>

namespace silicon::vis {

class ChannelScopeRenderer : public IVisualizerRenderer {
public:
    ChannelScopeRenderer();
    ~ChannelScopeRenderer() override { releaseGl(); }

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_CHANNEL_SCOPE; }
    const char* getName() const override { return "Channel Scope"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density = 1.0f) override;
    void render() override;
    void releaseGl() override;

    void setOptions(
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
        int32_t windowMs = 30,
        int32_t gainPercent = 100,
        bool dcRemovalEnabled = true,
        int32_t triggerMode = 0,
        int32_t waveRenderMode = 1
    );

    int32_t getWindowMs() const { return windowMs_; }
    int32_t getGainPercent() const { return gainPercent_; }
    bool isDcRemovalEnabled() const { return dcRemovalEnabled_; }
    int32_t getTriggerMode() const { return triggerMode_; }
    int32_t getWaveRenderMode() const { return waveRenderMode_; }

    void setChannelHistory(int32_t channel, const float* history, int32_t sampleCount);
    void setAllChannelHistories(int32_t channelCount, int32_t samplesPerChannel, const float* flatData, int32_t displaySamplesPerChannel = 0);
    void setTextStates(const SiliconVisChannelTextState* states, int32_t count);

    gl::GlFontAtlas& getFontAtlas() { return fontAtlas_; }

private:
    void resolveGrid(int channelCount, int& outCols, int& outRows) const;
    void buildGeometry();
    void drawVuBars();
    void drawText();

    float computeVuLevel(const std::vector<float>& history) const;
    int computeAutoTextSizeSp(int selectedSp, int minimumSp, float cellWidthDp, float paddingDp) const;
    float estimateTextWidthDp(int sp, float paddingDp) const;
    std::string truncateWithEllipsis(const std::string& text, float maxWidthPx, float scale) const;

    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    float density_ = 1.0f;

    SiliconVisChannelLayout layout_ = SILICON_VIS_LAYOUT_AUTO;
    SiliconVisTextAnchor anchor_ = SILICON_VIS_TEXT_ANCHOR_TOP_LEFT;
    SiliconVisVuAnchor vuAnchor_ = SILICON_VIS_VU_ANCHOR_TOP;
    bool vuEnabled_ = true;
    int32_t textSizeSp_ = 8;
    float paddingPx_ = 6.0f;
    uint32_t gridColorArgb_ = 0x66FFFFFF;
    float gridWidthPx_ = 1.0f;
    uint32_t lineColorArgb_ = 0xFF80D8FF;
    float lineWidthPx_ = 1.5f;
    uint32_t vuColorArgb_ = 0xFF76FF03;
    SiliconVisTextPalette palette_{};
    bool shadowEnabled_ = true;
    bool hideWhenOverflow_ = false;
    int32_t windowMs_ = 30;
    int32_t gainPercent_ = 100;
    bool dcRemovalEnabled_ = true;
    int32_t triggerMode_ = 0;
    int32_t waveRenderMode_ = 1;

    std::vector<std::vector<float>> channelHistories_;
    std::vector<SiliconVisChannelTextState> textStates_;

    gl::GlFlatColorRenderer flatRenderer_;
    gl::GlWaveLineRenderer waveRenderer_;
    gl::GlFontAtlas fontAtlas_;
    gl::GlTextBatchBuilder textBatcher_;
    gl::GlTextProgram textProgram_;

    std::vector<float> gridVertices_;
    std::vector<float> waveformVertices_;
    std::vector<float> vuTrackVertices_;
    std::vector<float> vuFillVertices_;
};

} // namespace silicon::vis
