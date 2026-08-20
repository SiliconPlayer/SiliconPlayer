#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "gl/gl_primitives.h"
#include <vector>

namespace silicon::vis {

class OscilloscopeRenderer : public IVisualizerRenderer {
public:
    OscilloscopeRenderer();
    ~OscilloscopeRenderer() override { releaseGl(); }

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_OSCILLOSCOPE; }
    const char* getName() const override { return "Oscilloscope"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density = 1.0f) override;
    void render() override;
    void releaseGl() override;

    void pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) override;

    void setOptions(
        bool stereo,
        uint32_t waveColorArgb,
        float lineWidthPx,
        uint32_t gridColorArgb,
        float gridWidthPx,
        bool showCenterLine,
        bool showGrid
    );

private:
    void buildGeometry();

    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    float density_ = 1.0f;

    bool stereo_ = false;
    uint32_t waveColorArgb_ = 0xFF80D8FF;
    float lineWidthPx_ = 2.0f;
    uint32_t gridColorArgb_ = 0x40FFFFFF;
    float gridWidthPx_ = 1.0f;
    bool showCenterLine_ = true;
    bool showGrid_ = true;

    std::vector<float> pcmLeft_;
    std::vector<float> pcmRight_;

    gl::GlFlatColorRenderer flatRenderer_;
    std::vector<float> gridLines_;
    std::vector<float> waveLinesLeft_;
    std::vector<float> waveLinesRight_;
};

} // namespace silicon::vis
