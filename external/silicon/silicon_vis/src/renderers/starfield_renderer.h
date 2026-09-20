#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "gl/gl_primitives.h"
#include "gl/gl_program.h"
#include <chrono>
#include <vector>

namespace silicon::vis {

class StarfieldRenderer : public IVisualizerRenderer {
public:
    StarfieldRenderer();
    ~StarfieldRenderer() override { releaseGl(); }

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_STARFIELD; }
    const char* getName() const override { return "Starfield"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density = 1.0f) override;
    void render() override;
    void releaseGl() override;

    void setAlpha(float alpha) override { alpha_ = alpha; }
    void setEnergy(float energy) { energy_ = energy; }
    void setBassLevel(float bass) { bass_ = bass; }

    void setOptions(
        int32_t starCount,
        float speed,
        float fov,
        float nearPlane,
        uint32_t starColorArgb,
        float baseSizePx,
        float sizeGrowth,
        float farDim,
        float softness,
        float beatGlow,
        float glowSize,
        float trailPersistence,
        bool streaks,
        float streakLength,
        float centerX,
        float centerY,
        bool autoDrift,
        bool beatFollow,
        float reactSpeed,
        float flash,
        bool squareStars
    );

private:
    void ensureStars(int32_t count);
    void respawn(int32_t index, bool atFar);
    bool ensureTrailTarget();
    void releaseTrailTarget();
    void drawPoints(
        const float* pos, const float* size, const float* alpha, int32_t count,
        float soft, float globalAlpha, float brightness);
    void drawTrailComposite();
    void drawBloomComposite(float strength);

    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    float density_ = 1.0f;
    float alpha_ = 1.0f;
    float energy_ = 0.0f;
    float energySmooth_ = 0.0f;
    float fast_ = 0.0f;
    float slow_ = 0.0f;
    float driveSmooth_ = 0.0f;
    float bass_ = 0.0f;

    int32_t starCount_ = 350;
    float speed_ = 0.30f;
    float fov_ = 1.0f;
    float nearPlane_ = 0.06f;
    uint32_t starColorArgb_ = 0xFFFFFFFF;
    float baseSizePx_ = 2.4f;
    float sizeGrowth_ = 1.2f;
    float farDim_ = 0.6f;
    float softness_ = 0.25f;
    float beatGlow_ = 0.0f;
    float glowSize_ = 3.0f;
    float trailPersistence_ = 0.0f;
    bool streaks_ = false;
    bool squareStars_ = false;
    float streakLength_ = 1.0f;
    float centerX_ = 0.0f;
    float centerY_ = 0.0f;
    bool autoDrift_ = false;
    bool beatFollow_ = false;
    float reactSpeed_ = 0.0f;
    float flash_ = 0.0f;

    std::vector<float> starX_;
    std::vector<float> starY_;
    std::vector<float> starZ_;
    std::vector<float> starAge_;
    std::vector<float> pos_;
    std::vector<float> size_;
    std::vector<float> alphaArr_;
    std::vector<float> lineVerts_;

    gl::GlProgram pointProgram_;
    GLint pointPosLoc_ = -1;
    GLint pointSizeLoc_ = -1;
    GLint pointAlphaLoc_ = -1;
    GLint pointResLoc_ = -1;
    GLint pointColorLoc_ = -1;
    GLint pointSoftLoc_ = -1;
    GLint pointSquareLoc_ = -1;
    GLint pointGlobalAlphaLoc_ = -1;

    gl::GlProgram blitProgram_;
    GLint blitNdcLoc_ = -1;
    GLint blitTexLoc_ = -1;
    GLint blitAlphaLoc_ = -1;

    gl::GlProgram fadeProgram_;
    GLint fadeNdcLoc_ = -1;
    GLint fadeAlphaLoc_ = -1;

    gl::GlProgram bloomDownProgram_;
    GLint bloomDownNdcLoc_ = -1;
    GLint bloomDownTexLoc_ = -1;
    GLint bloomDownThreshLoc_ = -1;
    gl::GlProgram kawaseProgram_;
    GLint kawaseNdcLoc_ = -1;
    GLint kawaseTexLoc_ = -1;
    GLint kawasePxLoc_ = -1;
    GLuint bloomFboC_ = 0;
    GLuint bloomTexC_ = 0;
    GLuint bloomFboD_ = 0;
    GLuint bloomTexD_ = 0;
    GLuint bloomFboE_ = 0;
    GLuint bloomTexE_ = 0;
    GLuint bloomFboF_ = 0;
    GLuint bloomTexF_ = 0;
    int32_t bloomQW_ = 0;
    int32_t bloomQH_ = 0;
    int32_t bloomEW_ = 0;
    int32_t bloomEH_ = 0;
    gl::GlProgram bloomAddProgram_;
    GLint bloomAddNdcLoc_ = -1;
    GLint bloomAddTexLoc_ = -1;
    GLint bloomAddAlphaLoc_ = -1;

    gl::GlFlatColorRenderer flatRenderer_;
    float maxPointSize_ = 1.0f;

    GLuint trailFbo_ = 0;
    GLuint trailTexture_ = 0;
    int32_t trailWidth_ = 0;
    int32_t trailHeight_ = 0;
    GLuint bloomFboA_ = 0;
    GLuint bloomTexA_ = 0;
    GLuint bloomFboB_ = 0;
    GLuint bloomTexB_ = 0;
    int32_t bloomWidth_ = 0;
    int32_t bloomHeight_ = 0;

    std::chrono::steady_clock::time_point lastTime_{};
    bool clockValid_ = false;
    float elapsed_ = 0.0f;
};

} // namespace silicon::vis
