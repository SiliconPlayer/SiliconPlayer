#pragma once

#include <cstdint>
#include <vector>
#include "gl/gl_font_atlas.h"

// Desktop-owned text overlay for the channel scope. The shared pipeline only
// carries stub label logic, so positioned glyph quads built JVM-side from the
// shared layout engine are drawn here in the same GL pass, after the pipeline
// renders and before the frame is read back. Pure GL; no Compose involved.
struct ScopeTextOverlay {
    silicon::vis::gl::GlFontAtlas atlas;
    silicon::vis::gl::GlTextProgram program;
    std::vector<float> verts;
    bool atlasReady = false;

    bool uploadAtlas(
        const uint8_t* rgbaPixels,
        int width,
        int height,
        float baseFontSizePx,
        float lineHeightPx,
        const void* glyphs,
        int glyphCount
    );
    void setQuads(const float* data, int floatCount);
    void draw(int surfaceWidth, int surfaceHeight);
    void release();
};
