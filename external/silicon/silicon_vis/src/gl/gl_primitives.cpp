#include "gl_primitives.h"
#include <cmath>

namespace silicon::vis::gl {

static const char* FLAT_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    uniform vec2 uResolution;
    void main() {
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
    }
)";

static const char* FLAT_FRAGMENT_SHADER = R"(
    precision mediump float;
    uniform vec4 uColor;
    void main() {
        gl_FragColor = uColor;
    }
)";

void GlPrimitives::screenToNdc(float screenX, float screenY, float surfaceW, float surfaceH, float& ndcX, float& ndcY) {
    ndcX = (screenX / surfaceW) * 2.0f - 1.0f;
    ndcY = 1.0f - (screenY / surfaceH) * 2.0f;
}

void GlPrimitives::generateRectTrianglesNdc(
    float x, float y, float w, float h,
    float surfaceW, float surfaceH,
    float* outPositions6x2
) {
    float x0 = x;
    float y0 = y;
    float x1 = x + w;
    float y1 = y + h;

    float ndcX0, ndcY0, ndcX1, ndcY1;
    screenToNdc(x0, y0, surfaceW, surfaceH, ndcX0, ndcY0);
    screenToNdc(x1, y1, surfaceW, surfaceH, ndcX1, ndcY1);

    // Triangle 1
    outPositions6x2[0] = ndcX0; outPositions6x2[1] = ndcY0;
    outPositions6x2[2] = ndcX1; outPositions6x2[3] = ndcY0;
    outPositions6x2[4] = ndcX0; outPositions6x2[5] = ndcY1;

    // Triangle 2
    outPositions6x2[6] = ndcX1; outPositions6x2[7] = ndcY0;
    outPositions6x2[8] = ndcX1; outPositions6x2[9] = ndcY1;
    outPositions6x2[10] = ndcX0; outPositions6x2[11] = ndcY1;
}

void GlPrimitives::generateTexturedQuad(
    float x, float y, float w, float h,
    float u0, float v0, float u1, float v1,
    float* outVertices6x4
) {
    float x0 = x;
    float y0 = y;
    float x1 = x + w;
    float y1 = y + h;

    // 6 vertices * 4 floats (pos.x, pos.y, uv.u, uv.v)
    // T1: (x0,y0), (x1,y0), (x0,y1)
    outVertices6x4[0] = x0; outVertices6x4[1] = y0; outVertices6x4[2] = u0; outVertices6x4[3] = v0;
    outVertices6x4[4] = x1; outVertices6x4[5] = y0; outVertices6x4[6] = u1; outVertices6x4[7] = v0;
    outVertices6x4[8] = x0; outVertices6x4[9] = y1; outVertices6x4[10] = u0; outVertices6x4[11] = v1;

    // T2: (x1,y0), (x1,y1), (x0,y1)
    outVertices6x4[12] = x1; outVertices6x4[13] = y0; outVertices6x4[14] = u1; outVertices6x4[15] = v0;
    outVertices6x4[16] = x1; outVertices6x4[17] = y1; outVertices6x4[18] = u1; outVertices6x4[19] = v1;
    outVertices6x4[20] = x0; outVertices6x4[21] = y1; outVertices6x4[22] = u0; outVertices6x4[23] = v1;
}

int GlPrimitives::generateRoundedRectTriangles(
    float x, float y, float w, float h,
    float radius, int cornerSegments,
    std::vector<float>& outVertices
) {
    radius = std::min(radius, std::min(w, h) * 0.5f);
    if (radius <= 0.5f || cornerSegments < 2) {
        // Fall back to standard quad (6 vertices = 12 floats)
        outVertices.resize(12);
        float x1 = x + w;
        float y1 = y + h;
        outVertices[0] = x;  outVertices[1] = y;
        outVertices[2] = x1; outVertices[3] = y;
        outVertices[4] = x;  outVertices[5] = y1;
        outVertices[6] = x1; outVertices[7] = y;
        outVertices[8] = x1; outVertices[9] = y1;
        outVertices[10] = x; outVertices[11] = y1;
        return 6;
    }

    outVertices.clear();
    // Center point of rounded rectangle for triangle fan
    float cx = x + w * 0.5f;
    float cy = y + h * 0.5f;

    std::vector<float> perimeter;
    perimeter.reserve((cornerSegments * 4 + 4) * 2);

    auto addArc = [&](float arcCx, float arcCy, float startAngle, float endAngle) {
        for (int i = 0; i <= cornerSegments; ++i) {
            float t = static_cast<float>(i) / static_cast<float>(cornerSegments);
            float angle = startAngle + t * (endAngle - startAngle);
            perimeter.push_back(arcCx + std::cos(angle) * radius);
            perimeter.push_back(arcCy + std::sin(angle) * radius);
        }
    };

    // 4 corners: Top-Right, Bottom-Right, Bottom-Left, Top-Left
    float pi = 3.14159265358979323846f;
    addArc(x + w - radius, y + radius, -pi * 0.5f, 0.0f);
    addArc(x + w - radius, y + h - radius, 0.0f, pi * 0.5f);
    addArc(x + radius, y + h - radius, pi * 0.5f, pi);
    addArc(x + radius, y + radius, pi, pi * 1.5f);

    size_t pointCount = perimeter.size() / 2;
    for (size_t i = 0; i < pointCount; ++i) {
        size_t next = (i + 1) % pointCount;
        outVertices.push_back(cx);
        outVertices.push_back(cy);
        outVertices.push_back(perimeter[i * 2]);
        outVertices.push_back(perimeter[i * 2 + 1]);
        outVertices.push_back(perimeter[next * 2]);
        outVertices.push_back(perimeter[next * 2 + 1]);
    }

    return static_cast<int>(outVertices.size() / 2);
}

bool GlFlatColorRenderer::init() {
    if (program_.isReady()) return true;
    if (!program_.compileAndLink(FLAT_VERTEX_SHADER, FLAT_FRAGMENT_SHADER)) {
        return false;
    }
    posLoc_ = program_.getAttribLoc("aPosition");
    resLoc_ = program_.getUniformLoc("uResolution");
    colorLoc_ = program_.getUniformLoc("uColor");
    return true;
}

void GlFlatColorRenderer::release() {
    program_.release();
}

void GlFlatColorRenderer::drawTriangles(
    const float* positions2D, int vertexCount,
    uint32_t colorArgb, float surfaceW, float surfaceH
) {
    if (!program_.isReady() || vertexCount <= 0) return;
    Color4f c = argbToColor4f(colorArgb);
    if (c.a <= 0.0f) return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    program_.use();
    glUniform2f(resLoc_, surfaceW, surfaceH);
    glUniform4f(colorLoc_, c.r, c.g, c.b, c.a);

    glEnableVertexAttribArray(posLoc_);
    glVertexAttribPointer(posLoc_, 2, GL_FLOAT, GL_FALSE, 0, positions2D);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    glDisableVertexAttribArray(posLoc_);
}

void GlFlatColorRenderer::drawLines(
    const float* positions2D, int vertexCount,
    uint32_t colorArgb, float lineWidth,
    float surfaceW, float surfaceH
) {
    if (!program_.isReady() || vertexCount <= 0) return;
    Color4f c = argbToColor4f(colorArgb);
    if (c.a <= 0.0f) return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glLineWidth(std::max(1.0f, lineWidth));

    program_.use();
    glUniform2f(resLoc_, surfaceW, surfaceH);
    glUniform4f(colorLoc_, c.r, c.g, c.b, c.a);

    glEnableVertexAttribArray(posLoc_);
    glVertexAttribPointer(posLoc_, 2, GL_FLOAT, GL_FALSE, 0, positions2D);
    glDrawArrays(GL_LINES, 0, vertexCount);
    glDisableVertexAttribArray(posLoc_);
}

} // namespace silicon::vis::gl
