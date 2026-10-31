#include "gl_transition_overlay.h"
#include <algorithm>

namespace silicon::vis::gl {

static const char* TRANSITION_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    uniform vec2 uResolution;
    uniform float uOffsetX;
    varying vec2 vTexCoord;
    void main() {
        vec2 pos = aPosition + vec2(uOffsetX, 0.0);
        vec2 zeroToOne = pos / uResolution;
        vec2 clipSpace = zeroToOne * 2.0 - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)";

static const char* TRANSITION_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uSampler;
    uniform float uAlpha;
    void main() {
        vec4 tex = texture2D(uSampler, vTexCoord);
        gl_FragColor = vec4(tex.rgb, tex.a * uAlpha);
    }
)";

bool GlTransitionOverlay::takeSnapshot(uint32_t sourceFbo, int32_t widthPx, int32_t heightPx) {
    if (widthPx <= 0 || heightPx <= 0) return false;

    const bool resized = textureId_ == 0 || widthPx_ != widthPx || heightPx_ != heightPx;
    if (textureId_ == 0) {
        glGenTextures(1, &textureId_);
        if (textureId_ == 0) return false;
    }
    glBindTexture(GL_TEXTURE_2D, textureId_);
    if (resized) {
        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_RGBA, widthPx, heightPx, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, nullptr
        );
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        widthPx_ = widthPx;
        heightPx_ = heightPx;
    }
    // One copy per track change, so unlike a per-frame copy this cannot leak
    // render-target memory on the ANGLE/Vulkan EGL drivers.
    glBindFramebuffer(GL_FRAMEBUFFER, sourceFbo);
    glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, widthPx, heightPx);
    glBindTexture(GL_TEXTURE_2D, 0);
    return true;
}

void GlTransitionOverlay::draw(int32_t widthPx, int32_t heightPx, float offsetXPx, float alpha) {
    if (!hasSnapshot() || alpha <= 0.001f || widthPx <= 0 || heightPx <= 0) return;
    if (widthPx_ != widthPx || heightPx_ != heightPx) return;
    if (!ensureProgram()) return;

    const float w = static_cast<float>(widthPx);
    const float h = static_cast<float>(heightPx);
    // Positions in px with a top-left origin; v is flipped because the copy
    // source starts at the bottom-left.
    const float verts[] = {
        0.0f, 0.0f, 0.0f, 1.0f,
        w,    0.0f, 1.0f, 1.0f,
        0.0f, h,    0.0f, 0.0f,
        w,    0.0f, 1.0f, 1.0f,
        w,    h,    1.0f, 0.0f,
        0.0f, h,    0.0f, 0.0f
    };

    program_.use();
    glUniform2f(resolutionLoc_, w, h);
    glUniform1f(offsetLoc_, offsetXPx);
    glUniform1f(alphaLoc_, std::clamp(alpha, 0.0f, 1.0f));
    glUniform1i(samplerLoc_, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId_);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glEnableVertexAttribArray(positionLoc_);
    glVertexAttribPointer(positionLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), verts);
    glEnableVertexAttribArray(texCoordLoc_);
    glVertexAttribPointer(texCoordLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), verts + 2);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(positionLoc_);
    glDisableVertexAttribArray(texCoordLoc_);
    glDisable(GL_BLEND);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void GlTransitionOverlay::clearSnapshot() {
    if (textureId_ != 0) {
        glDeleteTextures(1, &textureId_);
        textureId_ = 0;
    }
    widthPx_ = 0;
    heightPx_ = 0;
}

void GlTransitionOverlay::release() {
    clearSnapshot();
    program_.release();
}

bool GlTransitionOverlay::ensureProgram() {
    if (program_.isReady()) return true;
    if (!program_.compileAndLink(TRANSITION_VERTEX_SHADER, TRANSITION_FRAGMENT_SHADER)) {
        return false;
    }
    positionLoc_ = program_.getAttribLoc("aPosition");
    texCoordLoc_ = program_.getAttribLoc("aTexCoord");
    resolutionLoc_ = program_.getUniformLoc("uResolution");
    offsetLoc_ = program_.getUniformLoc("uOffsetX");
    alphaLoc_ = program_.getUniformLoc("uAlpha");
    samplerLoc_ = program_.getUniformLoc("uSampler");
    return positionLoc_ >= 0 && texCoordLoc_ >= 0;
}

} // namespace silicon::vis::gl
