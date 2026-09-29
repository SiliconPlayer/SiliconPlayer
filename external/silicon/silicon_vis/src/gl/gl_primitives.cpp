#include "gl_primitives.h"
#include <algorithm>
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

int GlPrimitives::appendRoundedRectTriangles(
    float x, float y, float w, float h,
    float radius, int cornerSegments,
    std::vector<float>& outVertices
) {
    radius = std::min(radius, std::min(w, h) * 0.5f);
    if (radius <= 0.5f || cornerSegments < 2) {
        float x1 = x + w;
        float y1 = y + h;
        outVertices.push_back(x);  outVertices.push_back(y);
        outVertices.push_back(x1); outVertices.push_back(y);
        outVertices.push_back(x);  outVertices.push_back(y1);
        outVertices.push_back(x1); outVertices.push_back(y);
        outVertices.push_back(x1); outVertices.push_back(y1);
        outVertices.push_back(x);  outVertices.push_back(y1);
        return 6;
    }

    float cx = x + w * 0.5f;
    float cy = y + h * 0.5f;

    constexpr int kMaxPerimeterPoints = 32;
    float perimeterX[kMaxPerimeterPoints];
    float perimeterY[kMaxPerimeterPoints];
    int pointCount = 0;

    int segs = std::clamp(cornerSegments, 2, 6);
    float pi = 3.14159265358979323846f;

    auto addArc = [&](float arcCx, float arcCy, float startAngle, float endAngle) {
        for (int i = 0; i <= segs; ++i) {
            if (pointCount >= kMaxPerimeterPoints) break;
            float t = static_cast<float>(i) / static_cast<float>(segs);
            float angle = startAngle + t * (endAngle - startAngle);
            perimeterX[pointCount] = arcCx + std::cos(angle) * radius;
            perimeterY[pointCount] = arcCy + std::sin(angle) * radius;
            pointCount++;
        }
    };

    addArc(x + w - radius, y + radius, -pi * 0.5f, 0.0f);
    addArc(x + w - radius, y + h - radius, 0.0f, pi * 0.5f);
    addArc(x + radius, y + h - radius, pi * 0.5f, pi);
    addArc(x + radius, y + radius, pi, pi * 1.5f);

    for (int i = 0; i < pointCount; ++i) {
        int next = (i + 1) % pointCount;
        outVertices.push_back(cx);
        outVertices.push_back(cy);
        outVertices.push_back(perimeterX[i]);
        outVertices.push_back(perimeterY[i]);
        outVertices.push_back(perimeterX[next]);
        outVertices.push_back(perimeterY[next]);
    }

    return pointCount * 3;
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
    if (!program_.isReady() || vertexCount <= 0 || !positions2D) return;
    Color4f c = argbToColor4f(colorArgb);
    c.a *= alpha_;
    if (c.a <= 0.0f) return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

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
    if (!program_.isReady() || vertexCount <= 0 || !positions2D) return;
    Color4f c = argbToColor4f(colorArgb);
    c.a *= alpha_;
    if (c.a <= 0.0f) return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glLineWidth(std::max(1.0f, lineWidth));
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    program_.use();
    glUniform2f(resLoc_, surfaceW, surfaceH);
    glUniform4f(colorLoc_, c.r, c.g, c.b, c.a);

    glEnableVertexAttribArray(posLoc_);
    glVertexAttribPointer(posLoc_, 2, GL_FLOAT, GL_FALSE, 0, positions2D);
    glDrawArrays(GL_LINES, 0, vertexCount);
    glDisableVertexAttribArray(posLoc_);
}

static const char* WAVE_LINE_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute float aDist;
    uniform vec2 uResolution;
    varying float vDist;
    void main() {
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
        vDist = aDist;
    }
)";

static const char* WAVE_LINE_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying float vDist;
    uniform vec4 uColor;
    uniform float uHalfWidth;
    uniform float uSoftness;
    void main() {
        float d = abs(vDist);
        float inner = max(uHalfWidth - uSoftness, 0.0);
        float outer = uHalfWidth + uSoftness;
        float core = 1.0 - smoothstep(inner, outer, d);
        gl_FragColor = vec4(uColor.rgb, uColor.a * core);
    }
)";

bool GlWaveLineRenderer::init() {
    if (program_.isReady()) return true;
    if (!program_.compileAndLink(WAVE_LINE_VERTEX_SHADER, WAVE_LINE_FRAGMENT_SHADER)) {
        return false;
    }
    posLoc_ = program_.getAttribLoc("aPosition");
    distLoc_ = program_.getAttribLoc("aDist");
    resLoc_ = program_.getUniformLoc("uResolution");
    colorLoc_ = program_.getUniformLoc("uColor");
    halfWidthLoc_ = program_.getUniformLoc("uHalfWidth");
    softnessLoc_ = program_.getUniformLoc("uSoftness");
    return true;
}

void GlWaveLineRenderer::release() {
    program_.release();
}

void GlWaveLineRenderer::draw(
    const float* vertices3, int vertexCount,
    uint32_t colorArgb,
    float halfWidthPx, float softnessPx,
    float surfaceW, float surfaceH
) {
    if (!program_.isReady() || vertexCount <= 0 || !vertices3) return;
    Color4f c = argbToColor4f(colorArgb);
    c.a *= alpha_;
    if (c.a <= 0.0f) return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    program_.use();
    glUniform2f(resLoc_, surfaceW, surfaceH);
    glUniform4f(colorLoc_, c.r, c.g, c.b, c.a);
    glUniform1f(halfWidthLoc_, halfWidthPx);
    glUniform1f(softnessLoc_, softnessPx);

    const GLsizei stride = 3 * sizeof(GLfloat);
    glEnableVertexAttribArray(posLoc_);
    glVertexAttribPointer(posLoc_, 2, GL_FLOAT, GL_FALSE, stride, vertices3);
    glEnableVertexAttribArray(distLoc_);
    glVertexAttribPointer(distLoc_, 1, GL_FLOAT, GL_FALSE, stride, vertices3 + 2);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    glDisableVertexAttribArray(posLoc_);
    glDisableVertexAttribArray(distLoc_);
}

void appendRoundJoinRibbon(std::vector<float>& outPairs, const float* positions2D, size_t pointCount, float halfWidthPx) {
    if (!positions2D || pointCount < 2 || halfWidthPx <= 0.0f) return;
    // Channel scope stroke idiom: per-segment quads plus a disc fan wherever
    // the path turns, so corners keep full width. Opaque flat finish; MSAA
    // resolves the silhouette.
    std::vector<float> cx;
    std::vector<float> cy;
    cx.reserve(pointCount);
    cy.reserve(pointCount);
    for (size_t i = 0; i < pointCount; ++i) {
        const float x = positions2D[i * 2];
        const float y = positions2D[i * 2 + 1];
        if (!cx.empty()) {
            const float ddx = x - cx.back();
            const float ddy = y - cy.back();
            if (ddx * ddx + ddy * ddy < 1e-8f) continue;
        }
        cx.push_back(x);
        cy.push_back(y);
    }
    // Keep the tail sample; a dropped endpoint shortens the trace.
    const float lastX = positions2D[(pointCount - 1) * 2];
    const float lastY = positions2D[(pointCount - 1) * 2 + 1];
    if (cx.empty() || cx.back() != lastX || cy.back() != lastY) {
        cx.push_back(lastX);
        cy.push_back(lastY);
    }
    const size_t n = cx.size();
    if (n < 2) return;
    for (size_t k = 0; k + 1 < n; ++k) {
        float dx = cx[k + 1] - cx[k];
        float dy = cy[k + 1] - cy[k];
        // Double-wide libm: float sqrt/atan2 resolve past Debian-stable glibc.
        const float len = std::sqrt(static_cast<double>(dx * dx + dy * dy));
        if (len < 1e-4f) continue;
        dx /= len;
        dy /= len;
        const float ox = -dy * halfWidthPx;
        const float oy = dx * halfWidthPx;
        outPairs.push_back(cx[k] + ox); outPairs.push_back(cy[k] + oy);
        outPairs.push_back(cx[k + 1] + ox); outPairs.push_back(cy[k + 1] + oy);
        outPairs.push_back(cx[k] - ox); outPairs.push_back(cy[k] - oy);
        outPairs.push_back(cx[k + 1] + ox); outPairs.push_back(cy[k + 1] + oy);
        outPairs.push_back(cx[k + 1] - ox); outPairs.push_back(cy[k + 1] - oy);
        outPairs.push_back(cx[k] - ox); outPairs.push_back(cy[k] - oy);
    }
    const int segments = 10;
    for (size_t k = 1; k + 1 < n; ++k) {
        float d0x = cx[k] - cx[k - 1];
        float d0y = cy[k] - cy[k - 1];
        float d1x = cx[k + 1] - cx[k];
        float d1y = cy[k + 1] - cy[k];
        const float l0 = std::sqrt(static_cast<double>(d0x * d0x + d0y * d0y));
        const float l1 = std::sqrt(static_cast<double>(d1x * d1x + d1y * d1y));
        if (l0 < 1e-4f || l1 < 1e-4f) continue;
        d0x /= l0;
        d0y /= l0;
        d1x /= l1;
        d1y /= l1;
        const float dot = d0x * d1x + d0y * d1y;
        if (dot > 0.999f) continue;
        const float px = cx[k];
        const float py = cy[k];
        float prevX = px - d0y * halfWidthPx;
        float prevY = py + d0x * halfWidthPx;
        const float endX = px - d1y * halfWidthPx;
        const float endY = py + d1x * halfWidthPx;
        const float angle0 = std::atan2(static_cast<double>(prevY - py), static_cast<double>(prevX - px));
        const float angle1 = std::atan2(static_cast<double>(endY - py), static_cast<double>(endX - px));
        float sweep = angle1 - angle0;
        if (sweep > static_cast<float>(M_PI)) sweep -= 2.0f * static_cast<float>(M_PI);
        if (sweep < -static_cast<float>(M_PI)) sweep += 2.0f * static_cast<float>(M_PI);
        for (int s = 1; s <= segments; ++s) {
            const float a = angle0 + sweep * (static_cast<float>(s) / segments);
            const float vx = px + std::cos(a) * halfWidthPx;
            const float vy = py + std::sin(a) * halfWidthPx;
            outPairs.push_back(px); outPairs.push_back(py);
            outPairs.push_back(prevX); outPairs.push_back(prevY);
            outPairs.push_back(vx); outPairs.push_back(vy);
            prevX = vx;
            prevY = vy;
        }
    }
}

} // namespace silicon::vis::gl
