#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "gl/gl_primitives.h"
#include <vector>

namespace silicon::vis {

class BarsRenderer : public IVisualizerRenderer {
public:
    BarsRenderer();
    ~BarsRenderer() override { releaseGl(); }

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_BARS; }
    const char* getName() const override { return "Spectrum Bars"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density = 1.0f) override;
    void render() override;
    void releaseGl() override;

    void pushFft(const float* magnitudes, int32_t binCount) override;

    void setOptions(
        int32_t barCount,
        float smoothing,
        uint32_t startColorArgb,
        uint32_t endColorArgb,
        float cornerRadiusPx,
        bool showFrequencyGuide,
        uint32_t guideColorArgb
    );

private:
    void buildGeometry();

    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    float density_ = 1.0f;

    int32_t barCount_ = 32;
    float smoothing_ = 0.5f;
    uint32_t startColorArgb_ = 0xFF80D8FF;
    uint32_t endColorArgb_ = 0xFF40C4FF;
    float cornerRadiusPx_ = 4.0f;
    bool showFrequencyGuide_ = false;
    uint32_t guideColorArgb_ = 0x40FFFFFF;

    std::vector<float> smoothedMagnitudes_;
    gl::GlFlatColorRenderer flatRenderer_;
    std::vector<float> barVertices_;
    std::vector<float> guideLines_;
};

} // namespace silicon::vis
