#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "gl/gl_primitives.h"
#include "gl/gl_font_atlas.h"
#include <vector>

namespace silicon::vis {

class VuMetersRenderer : public IVisualizerRenderer {
public:
    VuMetersRenderer();
    ~VuMetersRenderer() override { releaseGl(); }

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_VU_METERS; }
    const char* getName() const override { return "VU Meters"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density = 1.0f) override;
    void render() override;
    void releaseGl() override;

    void pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) override;
    void setVuLevels(float left, float right);

    void setOptions(
        bool stereo,
        bool topPlacement,
        uint32_t fillColorArgb,
        uint32_t trackColorArgb,
        uint32_t labelColorArgb
    );

    gl::GlFontAtlas& getFontAtlas() { return fontAtlas_; }

private:
    void buildGeometry();
    void drawLabels();

    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    float density_ = 1.0f;

    bool stereo_ = true;
    bool topPlacement_ = false;
    uint32_t fillColorArgb_ = 0xFF76FF03;
    uint32_t trackColorArgb_ = 0x40FFFFFF;
    uint32_t labelColorArgb_ = 0xFFCCCCCC;

    float leftPeak_ = 0.0f;
    float rightPeak_ = 0.0f;

    gl::GlFlatColorRenderer flatRenderer_;
    gl::GlFontAtlas fontAtlas_;
    gl::GlTextBatchBuilder textBatcher_;
    gl::GlTextProgram textProgram_;

    std::vector<float> trackVertices_;
    std::vector<float> fillVertices_;
};

} // namespace silicon::vis
