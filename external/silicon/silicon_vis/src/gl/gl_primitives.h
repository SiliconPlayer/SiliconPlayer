#pragma once

#include "gl_platform.h"
#include "gl_program.h"
#include <vector>
#include <cstdint>

namespace silicon::vis::gl {

struct Vertex2D {
    float x;
    float y;
    float u;
    float v;
};

struct Color4f {
    float r;
    float g;
    float b;
    float a;
};

inline Color4f argbToColor4f(uint32_t argb) {
    Color4f c;
    c.a = ((argb >> 24) & 0xFF) / 255.0f;
    c.r = ((argb >> 16) & 0xFF) / 255.0f;
    c.g = ((argb >> 8) & 0xFF) / 255.0f;
    c.b = (argb & 0xFF) / 255.0f;
    return c;
}

class GlPrimitives {
public:
    static void screenToNdc(float screenX, float screenY, float surfaceW, float surfaceH, float& ndcX, float& ndcY);
    
    // Generates 6 2D position vertices (2 triangles) for an axis-aligned rectangle
    static void generateRectTrianglesNdc(
        float x, float y, float w, float h,
        float surfaceW, float surfaceH,
        float* outPositions6x2
    );

    // Generates 6 textured quad vertices (2 pos + 2 uv per vertex = 24 floats)
    static void generateTexturedQuad(
        float x, float y, float w, float h,
        float u0, float v0, float u1, float v1,
        float* outVertices6x4
    );

    // Generates rounded rect geometry (triangle fan / strip)
    static int generateRoundedRectTriangles(
        float x, float y, float w, float h,
        float radius, int cornerSegments,
        std::vector<float>& outVertices
    );

    // Appends rounded rect geometry directly to an existing vertex buffer
    static int appendRoundedRectTriangles(
        float x, float y, float w, float h,
        float radius, int cornerSegments,
        std::vector<float>& outVertices
    );
};

class GlFlatColorRenderer {
public:
    GlFlatColorRenderer() = default;
    ~GlFlatColorRenderer() { release(); }

    bool init();
    void release();

    void drawTriangles(const float* positions2D, int vertexCount, uint32_t colorArgb, float surfaceW, float surfaceH);
    void drawLines(const float* positions2D, int vertexCount, uint32_t colorArgb, float lineWidth, float surfaceW, float surfaceH);

    void setAlpha(float alpha) { alpha_ = alpha; }

private:
    GlProgram program_;
    GLint posLoc_ = -1;
    GLint resLoc_ = -1;
    GLint colorLoc_ = -1;
    float alpha_ = 1.0f;
};

} // namespace silicon::vis::gl
