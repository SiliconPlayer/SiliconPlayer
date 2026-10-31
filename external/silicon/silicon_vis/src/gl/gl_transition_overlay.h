#pragma once

#include "gl_platform.h"
#include "gl_program.h"
#include <cstdint>

namespace silicon::vis::gl {

/**
 * Keeps the last frame of the previous track so the channel scope can slide or
 * fade it out over the live one. Shared by every GL host: one copy per track
 * change, drawn into the same target the frame resolved into.
 */
class GlTransitionOverlay {
public:
    ~GlTransitionOverlay() { release(); }

    // Copies the current contents of sourceFbo (0 = window) into the snapshot.
    bool takeSnapshot(uint32_t sourceFbo, int32_t widthPx, int32_t heightPx);
    void draw(int32_t widthPx, int32_t heightPx, float offsetXPx, float alpha);
    // Drops the snapshot but keeps the program: the next track change reuses it.
    void clearSnapshot();
    void release();
    bool hasSnapshot() const { return textureId_ != 0; }

private:
    bool ensureProgram();

    GLuint textureId_ = 0;
    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    GlProgram program_;
    GLint positionLoc_ = -1;
    GLint texCoordLoc_ = -1;
    GLint resolutionLoc_ = -1;
    GLint offsetLoc_ = -1;
    GLint alphaLoc_ = -1;
    GLint samplerLoc_ = -1;
};

} // namespace silicon::vis::gl
