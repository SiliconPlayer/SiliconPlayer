#include "gl_artwork_renderer.h"
#include "gl_primitives.h"
#include <algorithm>
#include <cmath>

namespace silicon::vis::gl {

static const char* BG_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    uniform vec2 uResolution;
    varying vec2 vPosition;
    void main() {
        vPosition = aPosition;
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
    }
)";

static const char* BG_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vPosition;
    uniform vec2 uResolution;
    uniform vec4 uCenterColor;
    uniform vec4 uEdgeColor;
    uniform vec4 uCircleColor;
    uniform float uCircleRadius;

    void main() {
        vec2 center = uResolution * 0.5;
        float maxDist = length(center);
        float dist = distance(vPosition, center);
        float t = clamp(dist / max(maxDist, 1.0), 0.0, 1.0);
        vec4 bg = mix(uCenterColor, uEdgeColor, t * t);

        if (dist <= uCircleRadius) {
            float edgeDist = uCircleRadius - dist;
            float alpha = clamp(edgeDist / 1.5, 0.0, 1.0) * uCircleColor.a;
            gl_FragColor = mix(bg, vec4(uCircleColor.rgb, 1.0), alpha);
        } else {
            gl_FragColor = bg;
        }
    }
)";

static const char* TEX_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    uniform vec2 uResolution;
    varying vec2 vTexCoord;
    void main() {
        vTexCoord = aTexCoord;
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
    }
)";

static const char* TEX_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uSampler;
    uniform vec4 uColor;
    void main() {
        vec4 tex = texture2D(uSampler, vTexCoord);
        gl_FragColor = tex * uColor;
    }
)";

static const char* CONTRAST_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    uniform vec2 uResolution;
    varying vec2 vTexCoord;
    void main() {
        vTexCoord = aTexCoord;
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
    }
)";

static const char* CONTRAST_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform vec2 uResolution;
    uniform int uMode;
    uniform vec4 uScrimColor;

    void main() {
        float alpha = 0.0;
        float y = vTexCoord.y;

        if (uMode == 1) { // Bars
            float curve = y * y * (3.0 - 2.0 * y);
            alpha = mix(0.18, 0.58, curve);
        } else if (uMode == 2) { // Oscilloscope Mono
            float distFromCenter = abs(y - 0.5) * 2.0;
            alpha = mix(0.48, 0.12, distFromCenter);
        } else if (uMode == 3) { // Oscilloscope Stereo
            float dist1 = abs(y - 0.25) * 4.0;
            float dist2 = abs(y - 0.75) * 4.0;
            float band = min(dist1, dist2);
            alpha = mix(0.48, 0.12, clamp(band, 0.0, 1.0));
        } else if (uMode == 4) { // VU Meters Top
            float topDist = y / 0.35;
            alpha = mix(0.55, 0.0, clamp(topDist, 0.0, 1.0));
        } else if (uMode == 5) { // VU Meters Bottom
            float btmDist = (1.0 - y) / 0.35;
            alpha = mix(0.55, 0.0, clamp(btmDist, 0.0, 1.0));
        } else if (uMode == 6) { // Channel Scope
            alpha = 0.28;
        }

        gl_FragColor = vec4(uScrimColor.rgb, uScrimColor.a * alpha);
    }
)";

bool GlArtworkRenderer::init() {
    if (!bgProgram_.compileAndLink(BG_VERTEX_SHADER, BG_FRAGMENT_SHADER)) return false;
    bgResLoc_ = bgProgram_.getUniformLoc("uResolution");
    bgCenterColorLoc_ = bgProgram_.getUniformLoc("uCenterColor");
    bgEdgeColorLoc_ = bgProgram_.getUniformLoc("uEdgeColor");
    bgCircleColorLoc_ = bgProgram_.getUniformLoc("uCircleColor");
    bgCircleRadiusLoc_ = bgProgram_.getUniformLoc("uCircleRadius");
    bgPosLoc_ = bgProgram_.getAttribLoc("aPosition");

    if (!texProgram_.compileAndLink(TEX_VERTEX_SHADER, TEX_FRAGMENT_SHADER)) return false;
    texResLoc_ = texProgram_.getUniformLoc("uResolution");
    texColorLoc_ = texProgram_.getUniformLoc("uColor");
    texSamplerLoc_ = texProgram_.getUniformLoc("uSampler");
    texPosLoc_ = texProgram_.getAttribLoc("aPosition");
    texCoordLoc_ = texProgram_.getAttribLoc("aTexCoord");

    if (!contrastProgram_.compileAndLink(CONTRAST_VERTEX_SHADER, CONTRAST_FRAGMENT_SHADER)) return false;
    contrastResLoc_ = contrastProgram_.getUniformLoc("uResolution");
    contrastModeLoc_ = contrastProgram_.getUniformLoc("uMode");
    contrastScrimColorLoc_ = contrastProgram_.getUniformLoc("uScrimColor");
    contrastPosLoc_ = contrastProgram_.getAttribLoc("aPosition");
    contrastCoordLoc_ = contrastProgram_.getAttribLoc("aTexCoord");

    return true;
}

void GlArtworkRenderer::release() {
    if (artworkTextureId_ != 0) {
        glDeleteTextures(1, &artworkTextureId_);
        artworkTextureId_ = 0;
    }
    if (iconTextureId_ != 0) {
        glDeleteTextures(1, &iconTextureId_);
        iconTextureId_ = 0;
    }
    bgProgram_.release();
    texProgram_.release();
    contrastProgram_.release();
}

void GlArtworkRenderer::setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!rgbaPixels || width <= 0 || height <= 0) {
        clearArtwork();
        return;
    }
    pendingArtworkPixels_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    artworkWidth_ = width;
    artworkHeight_ = height;
    artworkTextureDirty_ = true;
}

void GlArtworkRenderer::clearArtwork() {
    pendingArtworkPixels_.clear();
    artworkWidth_ = 0;
    artworkHeight_ = 0;
    artworkTextureDirty_ = true;
}

void GlArtworkRenderer::setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!rgbaPixels || width <= 0 || height <= 0) {
        clearIcon();
        return;
    }
    pendingIconPixels_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    iconWidth_ = width;
    iconHeight_ = height;
    iconTextureDirty_ = true;
}

void GlArtworkRenderer::clearIcon() {
    pendingIconPixels_.clear();
    iconWidth_ = 0;
    iconHeight_ = 0;
    iconTextureDirty_ = true;
}

void GlArtworkRenderer::setTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType) {
    primaryColorArgb_ = primaryColorArgb;
    surfaceColorArgb_ = surfaceColorArgb;
    placeholderIconType_ = placeholderIconType;
}

void GlArtworkRenderer::setContrastMode(SiliconVisContrastMode mode) {
    contrastMode_ = mode;
}

void GlArtworkRenderer::ensureArtworkTexture() {
    if (!artworkTextureDirty_) return;
    artworkTextureDirty_ = false;

    if (artworkTextureId_ != 0) {
        glDeleteTextures(1, &artworkTextureId_);
        artworkTextureId_ = 0;
    }

    if (!pendingArtworkPixels_.empty() && artworkWidth_ > 0 && artworkHeight_ > 0) {
        glGenTextures(1, &artworkTextureId_);
        glBindTexture(GL_TEXTURE_2D, artworkTextureId_);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, artworkWidth_, artworkHeight_, 0, GL_RGBA, GL_UNSIGNED_BYTE, pendingArtworkPixels_.data());
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}

void GlArtworkRenderer::ensureIconTexture() {
    if (!iconTextureDirty_) return;
    iconTextureDirty_ = false;

    if (iconTextureId_ != 0) {
        glDeleteTextures(1, &iconTextureId_);
        iconTextureId_ = 0;
    }

    if (!pendingIconPixels_.empty() && iconWidth_ > 0 && iconHeight_ > 0) {
        glGenTextures(1, &iconTextureId_);
        glBindTexture(GL_TEXTURE_2D, iconTextureId_);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, iconWidth_, iconHeight_, 0, GL_RGBA, GL_UNSIGNED_BYTE, pendingIconPixels_.data());
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}

void GlArtworkRenderer::drawSolidBackground(uint32_t colorArgb) {
    Color4f c = argbToColor4f(colorArgb);
    glClearColor(c.r, c.g, c.b, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
}

void GlArtworkRenderer::draw(float surfaceWidth, float surfaceHeight, float density) {
    if (!showArtworkBackground_) {
        drawSolidBackground(surfaceColorArgb_);
        return;
    }
    ensureArtworkTexture();
    ensureIconTexture();
    drawArtworkOrFallback(surfaceWidth, surfaceHeight, density);
    drawContrastBackdrop(surfaceWidth, surfaceHeight);
}

void GlArtworkRenderer::drawGradientBackground(float surfaceWidth, float surfaceHeight, float density, bool drawCircle) {
    Color4f prim = argbToColor4f(primaryColorArgb_);
    Color4f surf = argbToColor4f(surfaceColorArgb_);

    float centerR = (surf.r * 0.72f) + (prim.r * 0.28f);
    float centerG = (surf.g * 0.72f) + (prim.g * 0.28f);
    float centerB = (surf.b * 0.72f) + (prim.b * 0.28f);

    float circleRadiusPx = std::min(60.0f * std::max(1.0f, density), std::min(surfaceWidth, surfaceHeight) * 0.35f);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    bgProgram_.use();
    glUniform2f(bgResLoc_, surfaceWidth, surfaceHeight);
    glUniform4f(bgCenterColorLoc_, centerR, centerG, centerB, 1.0f);
    glUniform4f(bgEdgeColorLoc_, surf.r, surf.g, surf.b, 1.0f);
    glUniform4f(bgCircleColorLoc_, prim.r, prim.g, prim.b, drawCircle ? 0.14f : 0.0f);
    glUniform1f(bgCircleRadiusLoc_, circleRadiusPx);

    float fullQuad[12] = {
        0.0f, 0.0f,
        surfaceWidth, 0.0f,
        0.0f, surfaceHeight,
        surfaceWidth, 0.0f,
        surfaceWidth, surfaceHeight,
        0.0f, surfaceHeight
    };

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(bgPosLoc_);
    glVertexAttribPointer(bgPosLoc_, 2, GL_FLOAT, GL_FALSE, 0, fullQuad);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(bgPosLoc_);
}

void GlArtworkRenderer::drawArtworkOrFallback(float surfaceWidth, float surfaceHeight, float density) {
    if (artworkTextureId_ != 0 && artworkWidth_ > 0 && artworkHeight_ > 0) {
        drawGradientBackground(surfaceWidth, surfaceHeight, density, /*drawCircle=*/false);
        // Draw real artwork textured quad (aspect fit, centered)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        texProgram_.use();
        glUniform2f(texResLoc_, surfaceWidth, surfaceHeight);
        glUniform4f(texColorLoc_, 1.0f, 1.0f, 1.0f, 1.0f);
        glUniform1i(texSamplerLoc_, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, artworkTextureId_);

        float imgW = static_cast<float>(artworkWidth_);
        float imgH = static_cast<float>(artworkHeight_);
        float scale = std::min(surfaceWidth / imgW, surfaceHeight / imgH);
        float dstW = imgW * scale;
        float dstH = imgH * scale;
        float dstX = (surfaceWidth - dstW) * 0.5f;
        float dstY = (surfaceHeight - dstH) * 0.5f;

        float quad[24];
        GlPrimitives::generateTexturedQuad(dstX, dstY, dstW, dstH, 0.0f, 0.0f, 1.0f, 1.0f, quad);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glEnableVertexAttribArray(texPosLoc_);
        glVertexAttribPointer(texPosLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), quad);

        glEnableVertexAttribArray(texCoordLoc_);
        glVertexAttribPointer(texCoordLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), &quad[2]);

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(texPosLoc_);
        glDisableVertexAttribArray(texCoordLoc_);
        glBindTexture(GL_TEXTURE_2D, 0);
    } else {
        drawGradientBackground(surfaceWidth, surfaceHeight, density, /*drawCircle=*/true);
        // Draw centered placeholder icon inside the circle disc if available
        float circleRadiusPx = std::min(60.0f * std::max(1.0f, density), std::min(surfaceWidth, surfaceHeight) * 0.35f);
        if (iconTextureId_ != 0 && iconWidth_ > 0 && iconHeight_ > 0) {
            float iconSizePx = std::min(72.0f * std::max(1.0f, density), circleRadiusPx * 1.25f);
            float iconX = (surfaceWidth - iconSizePx) * 0.5f;
            float iconY = (surfaceHeight - iconSizePx) * 0.5f;

            texProgram_.use();
            glUniform2f(texResLoc_, surfaceWidth, surfaceHeight);
            glUniform4f(texColorLoc_, 1.0f, 1.0f, 1.0f, 1.0f);
            glUniform1i(texSamplerLoc_, 0);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, iconTextureId_);

            float iconQuad[24];
            GlPrimitives::generateTexturedQuad(iconX, iconY, iconSizePx, iconSizePx, 0.0f, 0.0f, 1.0f, 1.0f, iconQuad);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glEnableVertexAttribArray(texPosLoc_);
            glVertexAttribPointer(texPosLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), iconQuad);

            glEnableVertexAttribArray(texCoordLoc_);
            glVertexAttribPointer(texCoordLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), &iconQuad[2]);

            glDrawArrays(GL_TRIANGLES, 0, 6);

            glDisableVertexAttribArray(texPosLoc_);
            glDisableVertexAttribArray(texCoordLoc_);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }
}

void GlArtworkRenderer::drawContrastBackdrop(float surfaceWidth, float surfaceHeight) {
    if (contrastMode_ == SILICON_VIS_CONTRAST_NONE || !contrastProgram_.isReady()) return;

    int modeInt = static_cast<int>(contrastMode_);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    contrastProgram_.use();
    glUniform2f(contrastResLoc_, surfaceWidth, surfaceHeight);
    glUniform1i(contrastModeLoc_, modeInt);
    Color4f scrim = argbToColor4f(contrastScrimArgb_);
    glUniform4f(contrastScrimColorLoc_, scrim.r, scrim.g, scrim.b, scrim.a);

    float quad[24];
    GlPrimitives::generateTexturedQuad(0.0f, 0.0f, surfaceWidth, surfaceHeight, 0.0f, 0.0f, 1.0f, 1.0f, quad);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(contrastPosLoc_);
    glVertexAttribPointer(contrastPosLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), quad);

    glEnableVertexAttribArray(contrastCoordLoc_);
    glVertexAttribPointer(contrastCoordLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), &quad[2]);

    glDrawArrays(GL_TRIANGLES, 0, 6);

    glDisableVertexAttribArray(contrastPosLoc_);
    glDisableVertexAttribArray(contrastCoordLoc_);
}

} // namespace silicon::vis::gl
