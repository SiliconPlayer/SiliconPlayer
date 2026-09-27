#include "ScopeTextOverlay.h"

bool ScopeTextOverlay::uploadAtlas(
    const uint8_t* rgbaPixels,
    int width,
    int height,
    float baseFontSizePx,
    float lineHeightPx,
    const void* glyphs,
    int glyphCount
) {
    if (!rgbaPixels || width <= 0 || height <= 0 || !glyphs || glyphCount <= 0) return false;
    if (!program.init()) return false;
    atlasReady = atlas.loadCustomAtlas(
        rgbaPixels,
        width,
        height,
        baseFontSizePx,
        lineHeightPx,
        static_cast<const silicon::vis::gl::Glyph*>(glyphs),
        glyphCount
    );
    if (!atlasReady) verts.clear();
    return atlasReady;
}

void ScopeTextOverlay::setQuads(const float* data, int floatCount) {
    verts.clear();
    if (data && floatCount > 0) {
        verts.assign(data, data + floatCount);
    }
}

void ScopeTextOverlay::draw(int surfaceWidth, int surfaceHeight) {
    if (!atlasReady || verts.empty() || surfaceWidth <= 0 || surfaceHeight <= 0) return;
    if (!program.init()) return;
    program.draw(
        verts.data(),
        static_cast<int>(verts.size() / 8),
        atlas,
        static_cast<float>(surfaceWidth),
        static_cast<float>(surfaceHeight)
    );
}

void ScopeTextOverlay::release() {
    verts.clear();
    atlasReady = false;
    atlas.release();
    program.release();
}
