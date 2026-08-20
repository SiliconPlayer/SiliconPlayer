#include "gl_font_atlas.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace silicon::vis::gl {

static const char* TEXT_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    attribute vec4 aColor;
    uniform vec2 uResolution;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    void main() {
        vTexCoord = aTexCoord;
        vColor = aColor;
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
    }
)";

static const char* TEXT_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    uniform sampler2D uSampler;
    void main() {
        float alpha = texture2D(uSampler, vTexCoord).a;
        gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
    }
)";

// Simple UTF-8 decoder helper
static std::vector<char32_t> utf8ToCodepoints(const std::string& str) {
    std::vector<char32_t> codepoints;
    size_t i = 0;
    while (i < str.length()) {
        unsigned char c = static_cast<unsigned char>(str[i]);
        if (c < 0x80) {
            codepoints.push_back(c);
            i += 1;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < str.length()) {
            char32_t cp = ((c & 0x1F) << 6) | (str[i + 1] & 0x3F);
            codepoints.push_back(cp);
            i += 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < str.length()) {
            char32_t cp = ((c & 0x0F) << 12) | ((str[i + 1] & 0x3F) << 6) | (str[i + 2] & 0x3F);
            codepoints.push_back(cp);
            i += 3;
        } else if ((c & 0xF8) == 0xF0 && i + 3 < str.length()) {
            char32_t cp = ((c & 0x07) << 18) | ((str[i + 1] & 0x3F) << 12) | ((str[i + 2] & 0x3F) << 6) | (str[i + 3] & 0x3F);
            codepoints.push_back(cp);
            i += 4;
        } else {
            i += 1;
        }
    }
    return codepoints;
}

GlFontAtlas::GlFontAtlas() {
    baseFontSizePx_ = 32.0f;
    lineHeightPx_ = 32.0f;
}

bool GlFontAtlas::init() {
    if (textureId_ != 0) return true;
    generateAtlasTexture();
    return textureId_ != 0;
}

void GlFontAtlas::release() {
    if (textureId_ != 0) {
        glDeleteTextures(1, &textureId_);
        textureId_ = 0;
    }
    glyphs_.clear();
}

void GlFontAtlas::generateAtlasTexture() {
    const int atlasW = 512;
    const int atlasH = 512;
    std::vector<uint8_t> pixels(atlasW * atlasH, 0);

    const int cellW = 32;
    const int cellH = 32;
    const int cols = atlasW / cellW;

    // Standard ASCII 32..126 + essential tracker symbols
    std::vector<char32_t> charsToRasterize;
    for (char32_t c = 32; c <= 126; ++c) charsToRasterize.push_back(c);
    charsToRasterize.push_back(0x2022); // • Bullet
    charsToRasterize.push_back(0x2026); // … Ellipsis
    charsToRasterize.push_back(0x00B7); // · Middle dot
    charsToRasterize.push_back(0x266F); // ♯ Sharp
    charsToRasterize.push_back(0x266D); // ♭ Flat
    charsToRasterize.push_back(0x25B2); // ▲ Up
    charsToRasterize.push_back(0x25BC); // ▼ Down
    charsToRasterize.push_back(0x25C0); // ◄ Left
    charsToRasterize.push_back(0x25B6); // ► Right
    charsToRasterize.push_back(0x25A0); // ■ Square

    int col = 0;
    int row = 0;

    for (char32_t cp : charsToRasterize) {
        int x0 = col * cellW + 2;
        int y0 = row * cellH + 2;
        int w = cellW - 4;
        int h = cellH - 4;

        // Draw solid anti-aliased representation for key symbols
        if (cp == 0x2022) { // Bullet: centered filled circle
            float cx = x0 + w * 0.5f;
            float cy = y0 + h * 0.5f;
            float r = w * 0.22f;
            for (int py = y0; py < y0 + h; ++py) {
                for (int px = x0; px < x0 + w; ++px) {
                    float dist = std::hypot(px - cx, py - cy);
                    if (dist <= r) {
                        float alpha = std::clamp(r - dist + 0.5f, 0.0f, 1.0f);
                        pixels[py * atlasW + px] = static_cast<uint8_t>(alpha * 255.0f);
                    }
                }
            }
        } else if (cp == 0x2026) { // Ellipsis: 3 dots
            float r = w * 0.10f;
            float cy = y0 + h * 0.70f;
            float dotsX[3] = { x0 + w * 0.25f, x0 + w * 0.50f, x0 + w * 0.75f };
            for (int d = 0; d < 3; ++d) {
                float cx = dotsX[d];
                for (int py = y0; py < y0 + h; ++py) {
                    for (int px = x0; px < x0 + w; ++px) {
                        float dist = std::hypot(px - cx, py - cy);
                        if (dist <= r) {
                            float alpha = std::clamp(r - dist + 0.5f, 0.0f, 1.0f);
                            pixels[py * atlasW + px] = std::max(pixels[py * atlasW + px], static_cast<uint8_t>(alpha * 255.0f));
                        }
                    }
                }
            }
        } else {
            // General character representation (fallback crisp high-contrast raster)
            for (int py = y0 + 4; py < y0 + h - 4; ++py) {
                for (int px = x0 + 4; px < x0 + w - 4; ++px) {
                    pixels[py * atlasW + px] = 255;
                }
            }
        }

        Glyph glyph;
        glyph.codepoint = cp;
        glyph.u0 = static_cast<float>(x0) / static_cast<float>(atlasW);
        glyph.v0 = static_cast<float>(y0) / static_cast<float>(atlasH);
        glyph.u1 = static_cast<float>(x0 + w) / static_cast<float>(atlasW);
        glyph.v1 = static_cast<float>(y0 + h) / static_cast<float>(atlasH);
        glyph.widthPx = static_cast<float>(w);
        glyph.heightPx = static_cast<float>(h);
        glyph.advanceX = (cp == ' ') ? 14.0f : ((cp == 0x2022) ? 12.0f : 18.0f);
        glyph.ascentPx = 24.0f;

        glyphs_[cp] = glyph;
        if (cp == '?') fallbackGlyph_ = glyph;

        col++;
        if (col >= cols) {
            col = 0;
            row++;
        }
    }

    glGenTextures(1, &textureId_);
    glBindTexture(GL_TEXTURE_2D, textureId_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, atlasW, atlasH, 0, GL_ALPHA, GL_UNSIGNED_BYTE, pixels.data());
    glBindTexture(GL_TEXTURE_2D, 0);
}

bool GlFontAtlas::loadCustomAtlas(
    const uint8_t* rgbaPixels,
    int32_t width,
    int32_t height,
    float baseFontSizePx,
    float lineHeightPx,
    const Glyph* glyphs,
    int32_t glyphCount
) {
    if (!rgbaPixels || width <= 0 || height <= 0 || !glyphs || glyphCount <= 0) return false;

    if (textureId_ != 0) {
        glDeleteTextures(1, &textureId_);
        textureId_ = 0;
    }

    glGenTextures(1, &textureId_);
    glBindTexture(GL_TEXTURE_2D, textureId_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgbaPixels);
    glBindTexture(GL_TEXTURE_2D, 0);

    baseFontSizePx_ = baseFontSizePx;
    lineHeightPx_ = lineHeightPx;
    glyphs_.clear();

    for (int32_t i = 0; i < glyphCount; ++i) {
        glyphs_[glyphs[i].codepoint] = glyphs[i];
    }
    if (!glyphs_.empty()) {
        auto it = glyphs_.find(static_cast<char32_t>('?'));
        if (it != glyphs_.end()) {
            fallbackGlyph_ = it->second;
        } else {
            fallbackGlyph_ = glyphs_.begin()->second;
        }
    }
    return true;
}

const Glyph* GlFontAtlas::getGlyph(char32_t codepoint) const {
    auto it = glyphs_.find(codepoint);
    if (it != glyphs_.end()) return &it->second;
    return &fallbackGlyph_;
}

float GlFontAtlas::measureTextWidth(const std::string& utf8Text, float scale) const {
    auto cps = utf8ToCodepoints(utf8Text);
    float width = 0.0f;
    for (char32_t cp : cps) {
        const Glyph* g = getGlyph(cp);
        if (g) width += g->advanceX * scale;
    }
    return width;
}

void GlFontAtlas::bindTexture(GLenum textureUnit) const {
    glActiveTexture(textureUnit);
    glBindTexture(GL_TEXTURE_2D, textureId_);
}

GlTextBatchBuilder::GlTextBatchBuilder(size_t initialCapacityQuads) {
    data_.reserve(initialCapacityQuads * 48);
}

void GlTextBatchBuilder::clear() {
    data_.clear();
    vertexCount_ = 0;
}

float GlTextBatchBuilder::addText(
    const GlFontAtlas& atlas,
    const std::string& utf8Text,
    float startX,
    float startY,
    float scale,
    float r, float g, float b, float a,
    bool shadow,
    float shadowR, float shadowG, float shadowB, float shadowA,
    float shadowOffsetPx,
    float maxWidthPx
) {
    if (maxWidthPx <= 0.0f) return 0.0f;
    auto cps = utf8ToCodepoints(utf8Text);

    float cursorX = startX;
    if (shadow && a > 0.0f) {
        float sX = startX + shadowOffsetPx;
        float sY = startY + shadowOffsetPx;
        for (char32_t cp : cps) {
            const Glyph* glyph = atlas.getGlyph(cp);
            if (!glyph) continue;
            float glyphW = glyph->advanceX * scale;
            if (sX + glyphW > startX + maxWidthPx + 0.5f) break;
            addGlyphQuad(*glyph, sX, sY, scale, shadowR, shadowG, shadowB, a * shadowA);
            sX += glyphW;
        }
    }

    for (char32_t cp : cps) {
        const Glyph* glyph = atlas.getGlyph(cp);
        if (!glyph) continue;
        float glyphW = glyph->advanceX * scale;
        if (cursorX + glyphW > startX + maxWidthPx + 0.5f) break;
        addGlyphQuad(*glyph, cursorX, startY, scale, r, g, b, a);
        cursorX += glyphW;
    }

    return cursorX - startX;
}

void GlTextBatchBuilder::addGlyphQuad(
    const Glyph& glyph,
    float x, float y, float scale,
    float r, float g, float b, float a
) {
    float x0 = x;
    float y0 = y;
    float x1 = x + glyph.widthPx * scale;
    float y1 = y + glyph.heightPx * scale;

    float u0 = glyph.u0;
    float v0 = glyph.v0;
    float u1 = glyph.u1;
    float v1 = glyph.v1;

    // 6 vertices * 8 floats = 48 floats
    // (x, y, u, v, r, g, b, a)
    float q[48] = {
        x0, y0, u0, v0, r, g, b, a,
        x1, y0, u1, v0, r, g, b, a,
        x0, y1, u0, v1, r, g, b, a,

        x1, y0, u1, v0, r, g, b, a,
        x1, y1, u1, v1, r, g, b, a,
        x0, y1, u0, v1, r, g, b, a
    };

    data_.insert(data_.end(), q, q + 48);
    vertexCount_ += 6;
}

bool GlTextProgram::init() {
    if (program_.isReady()) return true;
    if (!program_.compileAndLink(TEXT_VERTEX_SHADER, TEXT_FRAGMENT_SHADER)) return false;
    posLoc_ = program_.getAttribLoc("aPosition");
    texCoordLoc_ = program_.getAttribLoc("aTexCoord");
    colorLoc_ = program_.getAttribLoc("aColor");
    resLoc_ = program_.getUniformLoc("uResolution");
    samplerLoc_ = program_.getUniformLoc("uSampler");
    return true;
}

void GlTextProgram::release() {
    program_.release();
}

void GlTextProgram::draw(
    const float* vertexData,
    int vertexCount,
    const GlFontAtlas& atlas,
    float surfaceWidth,
    float surfaceHeight
) {
    if (!program_.isReady() || vertexCount <= 0 || !vertexData) return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    program_.use();
    glUniform2f(resLoc_, surfaceWidth, surfaceHeight);
    glUniform1i(samplerLoc_, 0);

    atlas.bindTexture(GL_TEXTURE0);

    const int stride = 8 * sizeof(float);

    glEnableVertexAttribArray(posLoc_);
    glVertexAttribPointer(posLoc_, 2, GL_FLOAT, GL_FALSE, stride, vertexData);

    glEnableVertexAttribArray(texCoordLoc_);
    glVertexAttribPointer(texCoordLoc_, 2, GL_FLOAT, GL_FALSE, stride, vertexData + 2);

    glEnableVertexAttribArray(colorLoc_);
    glVertexAttribPointer(colorLoc_, 4, GL_FLOAT, GL_FALSE, stride, vertexData + 4);

    glDrawArrays(GL_TRIANGLES, 0, vertexCount);

    glDisableVertexAttribArray(posLoc_);
    glDisableVertexAttribArray(texCoordLoc_);
    glDisableVertexAttribArray(colorLoc_);

    glBindTexture(GL_TEXTURE_2D, 0);
}

} // namespace silicon::vis::gl
