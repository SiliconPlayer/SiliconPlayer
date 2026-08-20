#pragma once

#include "gl_platform.h"
#include "gl_program.h"
#include <vector>
#include <string>
#include <unordered_map>
#include <cstdint>

namespace silicon::vis::gl {

struct Glyph {
    char32_t codepoint;
    float u0, v0, u1, v1;
    float widthPx;
    float heightPx;
    float advanceX;
    float ascentPx;
};

class GlFontAtlas {
public:
    GlFontAtlas();
    ~GlFontAtlas() { release(); }

    bool init();
    void release();

    bool loadCustomAtlas(
        const uint8_t* rgbaPixels,
        int32_t width,
        int32_t height,
        float baseFontSizePx,
        float lineHeightPx,
        const Glyph* glyphs,
        int32_t glyphCount
    );

    const Glyph* getGlyph(char32_t codepoint) const;
    float measureTextWidth(const std::string& utf8Text, float scale) const;
    void bindTexture(GLenum textureUnit = GL_TEXTURE0) const;

    float getLineHeightPx() const { return lineHeightPx_; }
    float getBaseFontSizePx() const { return baseFontSizePx_; }

private:
    void generateAtlasTexture();

    GLuint textureId_ = 0;
    float baseFontSizePx_ = 32.0f;
    float lineHeightPx_ = 32.0f;
    std::unordered_map<char32_t, Glyph> glyphs_;
    Glyph fallbackGlyph_{};
};

class GlTextBatchBuilder {
public:
    GlTextBatchBuilder(size_t initialCapacityQuads = 512);

    void clear();
    size_t getVertexCount() const { return vertexCount_; }
    const float* getBufferData() const { return data_.data(); }

    float addText(
        const GlFontAtlas& atlas,
        const std::string& utf8Text,
        float startX,
        float startY,
        float scale,
        float r, float g, float b, float a,
        bool shadow = false,
        float shadowR = 0.0f, float shadowG = 0.0f, float shadowB = 0.0f, float shadowA = 0.75f,
        float shadowOffsetPx = 1.5f,
        float maxWidthPx = 1e9f
    );

private:
    void addGlyphQuad(
        const Glyph& glyph,
        float x, float y, float scale,
        float r, float g, float b, float a
    );

    std::vector<float> data_; // 8 floats per vertex: (x, y, u, v, r, g, b, a)
    size_t vertexCount_ = 0;
};

class GlTextProgram {
public:
    GlTextProgram() = default;
    ~GlTextProgram() { release(); }

    bool init();
    void release();
    bool isReady() const { return program_.isReady(); }

    void draw(
        const float* vertexData,
        int vertexCount,
        const GlFontAtlas& atlas,
        float surfaceWidth,
        float surfaceHeight
    );

private:
    GlProgram program_;
    GLint posLoc_ = -1;
    GLint texCoordLoc_ = -1;
    GLint colorLoc_ = -1;
    GLint resLoc_ = -1;
    GLint samplerLoc_ = -1;
};

} // namespace silicon::vis::gl
