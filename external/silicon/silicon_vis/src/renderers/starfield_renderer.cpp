#include "starfield_renderer.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <ctime>

namespace silicon::vis {

static const char* STAR_POINT_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute float aSize;
    attribute float aAlpha;
    uniform vec2 uResolution;
    varying float vAlpha;
    void main() {
        vec2 ndc = (aPosition / uResolution) * 2.0 - 1.0;
        ndc.y = -ndc.y;
        gl_Position = vec4(ndc, 0.0, 1.0);
        gl_PointSize = aSize;
        vAlpha = aAlpha;
    }
)";

static const char* STAR_POINT_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform vec3 uColor;
    uniform float uSoft;
    uniform float uGlobalAlpha;
    uniform float uSquare;
    varying float vAlpha;
    void main() {
        if (uSquare > 0.5) {
            gl_FragColor = vec4(uColor, vAlpha * uGlobalAlpha);
            return;
        }
        vec2 pc = gl_PointCoord * 2.0 - 1.0;
        float d = length(pc);
        if (d > 1.0) discard;
        float edge = mix(0.12, 0.9, uSoft);
        float a = 1.0 - smoothstep(1.0 - edge, 1.0, d);
        float core = 1.0 - smoothstep(0.0, mix(0.25, 0.9, uSoft), d);
        a = max(a * 0.85, core);
        gl_FragColor = vec4(uColor, a * vAlpha * uGlobalAlpha);
    }
)";

static const char* TRAIL_BLIT_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aNdc;
    varying vec2 vUv;
    void main() {
        vUv = aNdc * 0.5 + 0.5;
        gl_Position = vec4(aNdc, 0.0, 1.0);
    }
)";

static const char* TRAIL_BLIT_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform sampler2D uTex;
    uniform float uAlpha;
    varying vec2 vUv;
    void main() {
        vec4 t = texture2D(uTex, vUv);
        gl_FragColor = vec4(t.rgb, t.a * uAlpha);
    }
)";

static const char* TRAIL_FADE_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aNdc;
    void main() {
        gl_Position = vec4(aNdc, 0.0, 1.0);
    }
)";

static const char* TRAIL_FADE_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform float uAlpha;
    void main() {
        gl_FragColor = vec4(0.0, 0.0, 0.0, uAlpha);
    }
)";

static const char* BLOOM_QUAD_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aNdc;
    varying vec2 vUv;
    void main() {
        vUv = aNdc * 0.5 + 0.5;
        gl_Position = vec4(aNdc, 0.0, 1.0);
    }
)";

static const char* BLOOM_DOWN_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform sampler2D uTex;
    uniform float uThresh;
    varying vec2 vUv;
    void main() {
        vec3 c = texture2D(uTex, vUv).rgb;
        float l = dot(c, vec3(0.299, 0.587, 0.114));
        float k = smoothstep(uThresh, uThresh + 0.25, l);
        // Gain against quarter-res dilution: one texel averages 16
        // screen pixels, so small stars would otherwise never pass.
        gl_FragColor = vec4(c * k * 2.0, 1.0);
    }
)";

// Dual-Kawase kernel: center + 4 diagonals, always adjacent texels.
// Radius comes from pyramid depth, so quality never depends on size.
static const char* KAWASE_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform sampler2D uTex;
    uniform vec2 uPx;
    varying vec2 vUv;
    void main() {
        vec3 c = texture2D(uTex, vUv).rgb * 0.2;
        c += texture2D(uTex, vUv + vec2(-1.0, -1.0) * uPx).rgb * 0.2;
        c += texture2D(uTex, vUv + vec2( 1.0, -1.0) * uPx).rgb * 0.2;
        c += texture2D(uTex, vUv + vec2(-1.0,  1.0) * uPx).rgb * 0.2;
        c += texture2D(uTex, vUv + vec2( 1.0,  1.0) * uPx).rgb * 0.2;
        gl_FragColor = vec4(c, 1.0);
    }
)";

static const char* BLOOM_ADD_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform sampler2D uTex;
    uniform float uAlpha;
    varying vec2 vUv;
    void main() {
        vec3 c = texture2D(uTex, vUv).rgb * uAlpha;
        // Hash dither: breaks 8-bit banding contours in the dark
        // falloff, which otherwise read as a grid. +/-0.5 LSB.
        float d = fract(sin(dot(vUv * vec2(1234.5, 987.6),
            vec2(12.9898, 78.233))) * 43758.5453);
        gl_FragColor = vec4(c + (d - 0.5) * (1.5 / 255.0), 1.0);
    }
)";

StarfieldRenderer::StarfieldRenderer() {
    starX_.reserve(512);
    starY_.reserve(512);
    starZ_.reserve(512);
}

bool StarfieldRenderer::initGl() {
    bool ready = pointProgram_.isReady() && blitProgram_.isReady() && fadeProgram_.isReady() &&
        bloomDownProgram_.isReady() && bloomAddProgram_.isReady() &&
        kawaseProgram_.isReady() && flatRenderer_.init();
    if (ready) return true;
    if (!pointProgram_.compileAndLink(STAR_POINT_VERTEX_SHADER, STAR_POINT_FRAGMENT_SHADER)) return false;
    pointPosLoc_ = pointProgram_.getAttribLoc("aPosition");
    pointSizeLoc_ = pointProgram_.getAttribLoc("aSize");
    pointAlphaLoc_ = pointProgram_.getAttribLoc("aAlpha");
    pointResLoc_ = pointProgram_.getUniformLoc("uResolution");
    pointColorLoc_ = pointProgram_.getUniformLoc("uColor");
    pointSoftLoc_ = pointProgram_.getUniformLoc("uSoft");
    pointSquareLoc_ = pointProgram_.getUniformLoc("uSquare");
    pointGlobalAlphaLoc_ = pointProgram_.getUniformLoc("uGlobalAlpha");
    if (!blitProgram_.compileAndLink(TRAIL_BLIT_VERTEX_SHADER, TRAIL_BLIT_FRAGMENT_SHADER)) return false;
    blitNdcLoc_ = blitProgram_.getAttribLoc("aNdc");
    blitTexLoc_ = blitProgram_.getUniformLoc("uTex");
    blitAlphaLoc_ = blitProgram_.getUniformLoc("uAlpha");
    if (!fadeProgram_.compileAndLink(TRAIL_FADE_VERTEX_SHADER, TRAIL_FADE_FRAGMENT_SHADER)) return false;
    fadeNdcLoc_ = fadeProgram_.getAttribLoc("aNdc");
    fadeAlphaLoc_ = fadeProgram_.getUniformLoc("uAlpha");
    if (!bloomDownProgram_.compileAndLink(BLOOM_QUAD_VERTEX_SHADER, BLOOM_DOWN_FRAGMENT_SHADER)) return false;
    bloomDownNdcLoc_ = bloomDownProgram_.getAttribLoc("aNdc");
    bloomDownTexLoc_ = bloomDownProgram_.getUniformLoc("uTex");
    bloomDownThreshLoc_ = bloomDownProgram_.getUniformLoc("uThresh");
    if (!bloomAddProgram_.compileAndLink(BLOOM_QUAD_VERTEX_SHADER, BLOOM_ADD_FRAGMENT_SHADER)) return false;
    bloomAddNdcLoc_ = bloomAddProgram_.getAttribLoc("aNdc");
    bloomAddTexLoc_ = bloomAddProgram_.getUniformLoc("uTex");
    bloomAddAlphaLoc_ = bloomAddProgram_.getUniformLoc("uAlpha");
    if (!kawaseProgram_.compileAndLink(BLOOM_QUAD_VERTEX_SHADER, KAWASE_FRAGMENT_SHADER)) return false;
    kawaseNdcLoc_ = kawaseProgram_.getAttribLoc("aNdc");
    kawaseTexLoc_ = kawaseProgram_.getUniformLoc("uTex");
    kawasePxLoc_ = kawaseProgram_.getUniformLoc("uPx");
    if (!flatRenderer_.init()) return false;
    GLint range[2] = {1, 1};
    glGetIntegerv(GL_ALIASED_POINT_SIZE_RANGE, range);
    maxPointSize_ = static_cast<float>(std::max(1, range[1]));
    std::srand(static_cast<unsigned>(std::time(nullptr)));
    return true;
}

void StarfieldRenderer::releaseGl() {
    releaseTrailTarget();
    pointProgram_.release();
    blitProgram_.release();
    fadeProgram_.release();
    bloomDownProgram_.release();
    bloomAddProgram_.release();
    kawaseProgram_.release();
    flatRenderer_.release();
    clockValid_ = false;
}

void StarfieldRenderer::resize(int32_t widthPx, int32_t heightPx, float density) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    density_ = std::max(1.0f, density);
    releaseTrailTarget();
}

void StarfieldRenderer::setOptions(
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
) {
    starCount_ = std::clamp(starCount, 1, 2048);
    speed_ = std::clamp(speed, 0.0f, 4.0f);
    fov_ = std::clamp(fov, 0.1f, 4.0f);
    nearPlane_ = std::clamp(nearPlane, 0.005f, 0.5f);
    starColorArgb_ = starColorArgb;
    baseSizePx_ = std::clamp(baseSizePx, 0.5f, 32.0f);
    sizeGrowth_ = std::clamp(sizeGrowth, 0.0f, 8.0f);
    farDim_ = std::clamp(farDim, 0.0f, 1.0f);
    softness_ = std::clamp(softness, 0.0f, 1.0f);
    beatGlow_ = std::clamp(beatGlow, 0.0f, 1.0f);
    glowSize_ = std::clamp(glowSize, 1.0f, 8.0f);
    trailPersistence_ = std::clamp(trailPersistence, 0.0f, 0.98f);
    streaks_ = streaks;
    streakLength_ = std::clamp(streakLength, 0.0f, 8.0f);
    centerX_ = std::clamp(centerX, -1.0f, 1.0f);
    centerY_ = std::clamp(centerY, -1.0f, 1.0f);
    autoDrift_ = autoDrift;
    beatFollow_ = beatFollow;
    reactSpeed_ = std::clamp(reactSpeed, 0.0f, 6.0f);
    squareStars_ = squareStars;
    flash_ = std::clamp(flash, 0.0f, 1.0f);
}

void StarfieldRenderer::ensureStars(int32_t count) {
    const int32_t old = static_cast<int32_t>(starX_.size());
    if (old == count) return;
    starX_.resize(count);
    starY_.resize(count);
    starZ_.resize(count);
    starAge_.resize(count);
    for (int32_t i = old; i < count; ++i) {
        starX_[i] = static_cast<float>(std::rand()) / RAND_MAX * 2.0f - 1.0f;
        starY_[i] = static_cast<float>(std::rand()) / RAND_MAX * 2.0f - 1.0f;
        starZ_[i] = nearPlane_ + (static_cast<float>(std::rand()) / RAND_MAX) * (1.0f - nearPlane_);
        // Steady-state ages: the field opens mixed, never flashing on.
        starAge_[i] = (static_cast<float>(std::rand()) / RAND_MAX) * 2.0f;
    }
    pos_.resize(static_cast<size_t>(count) * 2);
    size_.resize(count);
    alphaArr_.resize(count);
    lineVerts_.resize(static_cast<size_t>(count) * 4);
}

void StarfieldRenderer::respawn(int32_t index, bool atFar) {
    starAge_[index] = 0.0f;
    starX_[index] = static_cast<float>(std::rand()) / RAND_MAX * 2.0f - 1.0f;
    starY_[index] = static_cast<float>(std::rand()) / RAND_MAX * 2.0f - 1.0f;
    if (atFar) {
        starZ_[index] = 0.92f + (static_cast<float>(std::rand()) / RAND_MAX) * 0.08f;
    } else {
        starZ_[index] = nearPlane_ + (static_cast<float>(std::rand()) / RAND_MAX) * (1.0f - nearPlane_);
    }
}

static bool createColorTarget(GLuint& texOut, GLuint& fboOut, int32_t w, int32_t h, GLint filter) {
    GLuint tex = 0;
    GLuint fbo = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        if (fbo != 0) glDeleteFramebuffers(1, &fbo);
        if (tex != 0) glDeleteTextures(1, &tex);
        return false;
    }
    texOut = tex;
    fboOut = fbo;
    return true;
}

bool StarfieldRenderer::ensureTrailTarget() {
    // Half-res bloom: quarter-res upscaled its texels into visible
    // blocks on dense fields. Still trivially cheap (a few small quads).
    const int32_t bw = std::max(1, widthPx_ / 2);
    const int32_t bh = std::max(1, heightPx_ / 2);
    const int32_t qw = std::max(1, bw / 2);
    const int32_t qh = std::max(1, bh / 2);
    const int32_t ew = std::max(1, qw / 2);
    const int32_t eh = std::max(1, qh / 2);
    if (trailFbo_ != 0 && trailWidth_ == widthPx_ && trailHeight_ == heightPx_ &&
        bloomFboA_ != 0 && bloomWidth_ == bw && bloomHeight_ == bh &&
        bloomFboC_ != 0 && bloomQW_ == qw && bloomQH_ == qh &&
        bloomFboE_ != 0 && bloomEW_ == ew && bloomEH_ == eh) {
        return true;
    }
    releaseTrailTarget();
    if (!createColorTarget(trailTexture_, trailFbo_, widthPx_, heightPx_, GL_LINEAR)) {
        releaseTrailTarget();
        return false;
    }
    if (!createColorTarget(bloomTexA_, bloomFboA_, bw, bh, GL_LINEAR) ||
        !createColorTarget(bloomTexB_, bloomFboB_, bw, bh, GL_LINEAR) ||
        !createColorTarget(bloomTexC_, bloomFboC_, qw, qh, GL_LINEAR) ||
        !createColorTarget(bloomTexD_, bloomFboD_, qw, qh, GL_LINEAR) ||
        !createColorTarget(bloomTexE_, bloomFboE_, ew, eh, GL_LINEAR) ||
        !createColorTarget(bloomTexF_, bloomFboF_, ew, eh, GL_LINEAR)) {
        releaseTrailTarget();
        return false;
    }
    trailWidth_ = widthPx_;
    trailHeight_ = heightPx_;
    bloomWidth_ = bw;
    bloomHeight_ = bh;
    bloomQW_ = qw;
    bloomQH_ = qh;
    bloomEW_ = ew;
    bloomEH_ = eh;
    glBindFramebuffer(GL_FRAMEBUFFER, trailFbo_);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return true;
}

void StarfieldRenderer::releaseTrailTarget() {
    if (trailFbo_ != 0) glDeleteFramebuffers(1, &trailFbo_);
    if (trailTexture_ != 0) glDeleteTextures(1, &trailTexture_);
    if (bloomFboA_ != 0) glDeleteFramebuffers(1, &bloomFboA_);
    if (bloomTexA_ != 0) glDeleteTextures(1, &bloomTexA_);
    if (bloomFboB_ != 0) glDeleteFramebuffers(1, &bloomFboB_);
    if (bloomTexB_ != 0) glDeleteTextures(1, &bloomTexB_);
    if (bloomFboC_ != 0) glDeleteFramebuffers(1, &bloomFboC_);
    if (bloomTexC_ != 0) glDeleteTextures(1, &bloomTexC_);
    if (bloomFboD_ != 0) glDeleteFramebuffers(1, &bloomFboD_);
    if (bloomTexD_ != 0) glDeleteTextures(1, &bloomTexD_);
    if (bloomFboE_ != 0) glDeleteFramebuffers(1, &bloomFboE_);
    if (bloomTexE_ != 0) glDeleteTextures(1, &bloomTexE_);
    if (bloomFboF_ != 0) glDeleteFramebuffers(1, &bloomFboF_);
    if (bloomTexF_ != 0) glDeleteTextures(1, &bloomTexF_);
    trailFbo_ = 0;
    trailTexture_ = 0;
    trailWidth_ = 0;
    trailHeight_ = 0;
    bloomFboA_ = 0;
    bloomTexA_ = 0;
    bloomFboB_ = 0;
    bloomTexB_ = 0;
    bloomWidth_ = 0;
    bloomHeight_ = 0;
    bloomFboC_ = 0;
    bloomTexC_ = 0;
    bloomFboD_ = 0;
    bloomTexD_ = 0;
    bloomFboE_ = 0;
    bloomTexE_ = 0;
    bloomFboF_ = 0;
    bloomTexF_ = 0;
    bloomQW_ = 0;
    bloomQH_ = 0;
    bloomEW_ = 0;
    bloomEH_ = 0;
}

void StarfieldRenderer::drawPoints(
    const float* pos, const float* size, const float* alpha, int32_t count,
    float soft, float globalAlpha, float brightness
) {
    if (count <= 0 || !pos || !size || !alpha) return;
    const float ga = globalAlpha * alpha_;
    if (ga <= 0.0f) return;
    gl::Color4f c = gl::argbToColor4f(starColorArgb_);
    // Flash rides color gain, not alpha: alpha caps at 1 and would eat it.
    const float b = std::clamp(brightness, 0.0f, 2.5f);
    c.r = std::min(c.r * b, 2.5f);
    c.g = std::min(c.g * b, 2.5f);
    c.b = std::min(c.b * b, 2.5f);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    pointProgram_.use();
    glUniform2f(pointResLoc_, static_cast<float>(widthPx_), static_cast<float>(heightPx_));
    glUniform3f(pointColorLoc_, c.r, c.g, c.b);
    glUniform1f(pointSoftLoc_, soft);
    glUniform1f(pointSquareLoc_, squareStars_ ? 1.0f : 0.0f);
    glUniform1f(pointGlobalAlphaLoc_, ga);

    glEnableVertexAttribArray(pointPosLoc_);
    glVertexAttribPointer(pointPosLoc_, 2, GL_FLOAT, GL_FALSE, 0, pos);
    glEnableVertexAttribArray(pointSizeLoc_);
    glVertexAttribPointer(pointSizeLoc_, 1, GL_FLOAT, GL_FALSE, 0, size);
    glEnableVertexAttribArray(pointAlphaLoc_);
    glVertexAttribPointer(pointAlphaLoc_, 1, GL_FLOAT, GL_FALSE, 0, alpha);
    glDrawArrays(GL_POINTS, 0, count);
    glDisableVertexAttribArray(pointPosLoc_);
    glDisableVertexAttribArray(pointSizeLoc_);
    glDisableVertexAttribArray(pointAlphaLoc_);
}

void StarfieldRenderer::drawTrailComposite() {
    static const float tri[6] = {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f};
    if (alpha_ <= 0.0f) return;
    // The trail target holds premultiplied content (normal blending over
    // transparent black), so composite it premultiplied: using SRC_ALPHA
    // here would apply coverage twice and eat soft edges and trails.
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, trailTexture_);

    blitProgram_.use();
    glUniform1i(blitTexLoc_, 0);
    glUniform1f(blitAlphaLoc_, alpha_);
    glEnableVertexAttribArray(blitNdcLoc_);
    glVertexAttribPointer(blitNdcLoc_, 2, GL_FLOAT, GL_FALSE, 0, tri);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(blitNdcLoc_);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void StarfieldRenderer::drawBloomComposite(float strength) {
    static const float tri[6] = {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f};
    if (strength <= 0.0f || alpha_ <= 0.0f) return;
    if (bloomWidth_ <= 0 || bloomHeight_ <= 0) return;
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    // Bright-pass downsample into A.
    glBindFramebuffer(GL_FRAMEBUFFER, bloomFboA_);
    glViewport(0, 0, bloomWidth_, bloomHeight_);
    glDisable(GL_BLEND);
    glBindTexture(GL_TEXTURE_2D, trailTexture_);
    bloomDownProgram_.use();
    glUniform1i(bloomDownTexLoc_, 0);
    glUniform1f(bloomDownThreshLoc_, 0.08f);
    glEnableVertexAttribArray(bloomDownNdcLoc_);
    glVertexAttribPointer(bloomDownNdcLoc_, 2, GL_FLOAT, GL_FALSE, 0, tri);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(bloomDownNdcLoc_);

    // Dual-Kawase pyramid: radius comes from depth, so quality never
    // depends on size. A keeps the tight glow; E accumulates the wide
    // dispersed halo, upsampled back to half res in B. The size slider
    // scales tap offsets at every level together.
    const float k = glowSize_ / 3.0f;
    kawaseProgram_.use();
    glUniform1i(kawaseTexLoc_, 0);
    glEnableVertexAttribArray(kawaseNdcLoc_);
    glVertexAttribPointer(kawaseNdcLoc_, 2, GL_FLOAT, GL_FALSE, 0, tri);
    // Down half -> quarter -> eighth.
    glBindFramebuffer(GL_FRAMEBUFFER, bloomFboC_);
    glViewport(0, 0, bloomQW_, bloomQH_);
    glBindTexture(GL_TEXTURE_2D, bloomTexA_);
    glUniform2f(kawasePxLoc_, k / static_cast<float>(bloomWidth_), k / static_cast<float>(bloomHeight_));
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindFramebuffer(GL_FRAMEBUFFER, bloomFboE_);
    glViewport(0, 0, bloomEW_, bloomEH_);
    glBindTexture(GL_TEXTURE_2D, bloomTexC_);
    glUniform2f(kawasePxLoc_, k / static_cast<float>(bloomQW_), k / static_cast<float>(bloomQH_));
    glDrawArrays(GL_TRIANGLES, 0, 3);
    // Two smoothing iterations at the eighth level, ping-pong E/F.
    glBindFramebuffer(GL_FRAMEBUFFER, bloomFboF_);
    glBindTexture(GL_TEXTURE_2D, bloomTexE_);
    glUniform2f(kawasePxLoc_, k / static_cast<float>(bloomEW_), k / static_cast<float>(bloomEH_));
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindFramebuffer(GL_FRAMEBUFFER, bloomFboE_);
    glBindTexture(GL_TEXTURE_2D, bloomTexF_);
    glUniform2f(kawasePxLoc_, k / static_cast<float>(bloomEW_), k / static_cast<float>(bloomEH_));
    glDrawArrays(GL_TRIANGLES, 0, 3);
    // Upsample wide halo eighth -> half into B.
    glBindFramebuffer(GL_FRAMEBUFFER, bloomFboB_);
    glViewport(0, 0, bloomWidth_, bloomHeight_);
    glBindTexture(GL_TEXTURE_2D, bloomTexE_);
    glUniform2f(kawasePxLoc_, k / static_cast<float>(bloomEW_), k / static_cast<float>(bloomEH_));
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(kawaseNdcLoc_);
    glBindTexture(GL_TEXTURE_2D, 0);

    // Additive composite over the screen image.
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, widthPx_, heightPx_);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE);
    bloomAddProgram_.use();
    glUniform1i(bloomAddTexLoc_, 0);
    glEnableVertexAttribArray(bloomAddNdcLoc_);
    glVertexAttribPointer(bloomAddNdcLoc_, 2, GL_FLOAT, GL_FALSE, 0, tri);
    glBindTexture(GL_TEXTURE_2D, bloomTexA_);
    glUniform1f(bloomAddAlphaLoc_, std::min(2.0f, strength * 0.4f) * alpha_);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindTexture(GL_TEXTURE_2D, bloomTexB_);
    glUniform1f(bloomAddAlphaLoc_, strength * alpha_);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(bloomAddNdcLoc_);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void StarfieldRenderer::render() {
    if (widthPx_ <= 0 || heightPx_ <= 0 || !pointProgram_.isReady()) return;

    const auto now = std::chrono::steady_clock::now();
    float dt = 0.0f;
    if (clockValid_) {
        dt = std::chrono::duration<float>(now - lastTime_).count();
        dt = std::clamp(dt, 0.0f, 0.05f);
    }
    lastTime_ = now;
    clockValid_ = true;
    elapsed_ += dt;

    const float vuRaw = beatFollow_ ? std::clamp(energy_, 0.0f, 1.0f) : 0.0f;
    const float bassRaw = beatFollow_ ? std::clamp(bass_, 0.0f, 1.0f) : 0.0f;
    // Peak-hold with a fast release; the drive leaves headroom so only
    // true peaks hit full scale and the field pumps instead of parking
    // at maximum. Kicks live in the low end, so bass punches through
    // above broadband RMS.
    const float target = std::min(1.0f, std::max(vuRaw, bassRaw * 1.25f));
    if (target > energySmooth_) {
        energySmooth_ = target;
    } else if (dt > 0.0f) {
        energySmooth_ += (target - energySmooth_) * std::min(1.0f, dt * 5.0f);
    }
    // Onset novelty like the bars path: a fast follower minus a slow
    // average spikes per hit even when the absolute level is pinned
    // by a loud master. Speed and flash ride the hits; glow/flash
    // sustain rides the slower envelope below.
    if (dt > 0.0f) {
        fast_ += (target - fast_) * std::min(1.0f, dt * 25.0f);
        // Section-jump snap: a floor far below the signal means we opened
        // mid-loudness (or the song dropped in), not a beat onset. Snap
        // instead of surfing a ~20s adaptation that would dull every hit.
        if (target - slow_ > 0.6f) {
            slow_ = target;
            fast_ = target;
        } else {
            // Floor tracker: falls to quiet gaps but only creeps up, so a
            // steady beat train never lifts the reference and dulls novelty.
            const float slowRate = (target < slow_) ? dt * 0.5f : dt * 0.05f;
            slow_ += (target - slow_) * std::min(1.0f, slowRate);
        }
    } else {
        fast_ = target;
        slow_ = target;
    }
    const float energy = beatFollow_ ? std::pow(energySmooth_, 0.75f) : 0.0f;
    const float novelty = beatFollow_
        ? std::clamp((fast_ - slow_) * 7.0f, 0.0f, 1.0f) : 0.0f;
    ensureStars(starCount_);
    const int32_t count = static_cast<int32_t>(starX_.size());
    if (count <= 0) return;

    // The pause fade doubles as a brake: speed eases with visibility.
    const float vt = std::clamp(alpha_, 0.0f, 1.0f);
    const float brake = vt * vt * (3.0f - 2.0f * vt);
    // Expander curve: mids duck, peaks pass. Keeps the loud cruise
    // while restoring the full punch of each hit.
    const float reactTerm = std::pow(std::max(energy * 0.5f, novelty), 1.3f);
    const float spd = speed_ * (1.0f + reactSpeed_ * reactTerm) * brake;
    const float flashBoost = 1.0f + flash_ * std::max(energy, novelty) * 1.5f;
    float driftX = 0.0f;
    float driftY = 0.0f;
    if (autoDrift_) {
        driftX = std::sin(elapsed_ * 0.24f) * 0.12f;
        driftY = std::cos(elapsed_ * 0.17f) * 0.10f;
    }
    const float cx = centerX_ + driftX;
    const float cy = centerY_ + driftY;
    const float w = static_cast<float>(widthPx_);
    const float h = static_cast<float>(heightPx_);
    const float halfW = w * 0.5f;
    const float halfH = h * 0.5f;
    // Authored px at 1080p; taller viewports must not inflate stars:
    // same screen, same px == same physical size.
    const float sizeK = std::min(h, 1080.0f) / 1080.0f;
    const float depthRange = std::max(1e-3f, 1.0f - nearPlane_);
    int32_t lineCount = 0;

    for (int32_t i = 0; i < count; ++i) {
        if (dt > 0.0f) {
            starAge_[i] += dt;
            starZ_[i] -= spd * dt;
            if (starZ_[i] <= nearPlane_) {
                respawn(i, true);
            } else {
                const float s = fov_ / starZ_[i];
                if (starX_[i] * s > 1.15f || starX_[i] * s < -1.15f ||
                    starY_[i] * s > 1.15f || starY_[i] * s < -1.15f) {
                    respawn(i, false);
                }
            }
        }
        const float z = std::max(1e-3f, starZ_[i]);
        const float s = fov_ / z;
        const float x = halfW + (cx + starX_[i] * s) * halfW;
        const float y = halfH + (cy + starY_[i] * s) * halfH;
        const float depth = 1.0f - (z - nearPlane_) / depthRange;
        const float sz = std::min(maxPointSize_, std::max(1.0f, baseSizePx_ * sizeK * (1.0f + sizeGrowth_ * depth * depth)));
        const float al = std::clamp(1.0f - farDim_ * (1.0f - depth), 0.0f, 1.0f) * flashBoost;
        // Spawn fade-in plus a quick fade-out on the final approach so
        // stars never pop in or out of existence.
        const float fadeIn = std::min(1.0f, starAge_[i] / 0.35f);
        const float fadeOut = std::clamp((z - nearPlane_) / (depthRange * 0.10f), 0.0f, 1.0f);
        const float env = fadeIn * fadeOut;
        pos_[static_cast<size_t>(i) * 2] = x;
        pos_[static_cast<size_t>(i) * 2 + 1] = y;
        size_[i] = sz;
        alphaArr_[i] = std::min(1.0f, al) * env;
        if (streaks_ && env > 0.004f) {
            // Lines take no per-star alpha: grow/shrink the streak instead.
            const float zp = z + spd * 0.016f * streakLength_ * 8.0f;
            const float sp = fov_ / zp;
            const float tx = halfW + (cx + starX_[i] * sp) * halfW;
            const float ty = halfH + (cy + starY_[i] * sp) * halfH;
            const size_t o = static_cast<size_t>(lineCount) * 4;
            lineVerts_[o] = x + (tx - x) * env;
            lineVerts_[o + 1] = y + (ty - y) * env;
            lineVerts_[o + 2] = x;
            lineVerts_[o + 3] = y;
            ++lineCount;
        }
    }

    const float gk = beatGlow_ * std::max(energy, novelty);
    const bool wantBloom = beatGlow_ > 0.0f && gk > 0.004f && alpha_ > 0.0f;
    // Bloom needs an offscreen source even with trails off: reuse the
    // trail target as a fresh framebuffer (persistence 0 wipes it clean).
    const bool useFbo = (trailPersistence_ > 0.003f || wantBloom) && ensureTrailTarget();
    glBindFramebuffer(GL_FRAMEBUFFER, useFbo ? trailFbo_ : 0);
    glViewport(0, 0, widthPx_, heightPx_);
    if (useFbo && dt > 0.0f) {
        const float fade = std::min(1.0f, 1.0f - std::pow(trailPersistence_, dt * 60.0f));
        static const float tri[6] = {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f};
        glEnable(GL_BLEND);
        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        fadeProgram_.use();
        glUniform1f(fadeAlphaLoc_, fade);
        glEnableVertexAttribArray(fadeNdcLoc_);
        glVertexAttribPointer(fadeNdcLoc_, 2, GL_FLOAT, GL_FALSE, 0, tri);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisableVertexAttribArray(fadeNdcLoc_);
    }

    if (streaks_ && lineCount > 0) {
        flatRenderer_.setAlpha(0.55f * std::min(flashBoost, 1.5f) * alpha_);
        flatRenderer_.drawLines(lineVerts_.data(), lineCount * 2, starColorArgb_, 1.0f, w, h);
        flatRenderer_.setAlpha(alpha_);
    }
    // Streak flight draws lines only; the tip dots read as beads.
    if (!streaks_) {
        drawPoints(
            pos_.data(), size_.data(), alphaArr_.data(), count, softness_, 1.0f,
            std::min(flashBoost, 3.0f));
    }

    if (useFbo) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, widthPx_, heightPx_);
        drawTrailComposite();
        if (wantBloom) drawBloomComposite(std::min(2.0f, gk * 2.5f));
    }
}

} // namespace silicon::vis
