#pragma once

#include "gl_platform.h"
#include "gl_program.h"
#include "silicon/vis/vis_types.h"
#include <vector>

namespace silicon::vis::gl {

class GlArtworkRenderer {
public:
    GlArtworkRenderer() = default;
    ~GlArtworkRenderer() { release(); }

    bool init();
    void release();

    void setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height);
    void clearArtwork();
    void setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height);
    void clearIcon();
    void setTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType);
    void setContrastMode(SiliconVisContrastMode mode);
    void setContrastScrim(uint32_t argb) { contrastScrimArgb_ = argb; }
    void setShowArtworkBackground(bool show) { showArtworkBackground_ = show; }

    void draw(float surfaceWidth, float surfaceHeight, float density);

private:
    void drawSolidBackground(uint32_t colorArgb);
    void drawArtworkOrFallback(float surfaceWidth, float surfaceHeight, float density);
    void drawContrastBackdrop(float surfaceWidth, float surfaceHeight);

    void ensureArtworkTexture();
    void ensureIconTexture();

    GlProgram bgProgram_;
    GLint bgResLoc_ = -1;
    GLint bgCenterColorLoc_ = -1;
    GLint bgEdgeColorLoc_ = -1;
    GLint bgCircleColorLoc_ = -1;
    GLint bgCircleRadiusLoc_ = -1;
    GLint bgPosLoc_ = -1;

    GlProgram texProgram_;
    GLint texResLoc_ = -1;
    GLint texColorLoc_ = -1;
    GLint texSamplerLoc_ = -1;
    GLint texPosLoc_ = -1;
    GLint texCoordLoc_ = -1;

    GlProgram contrastProgram_;
    GLint contrastResLoc_ = -1;
    GLint contrastModeLoc_ = -1;
    GLint contrastScrimColorLoc_ = -1;
    GLint contrastPosLoc_ = -1;
    GLint contrastCoordLoc_ = -1;

    GLuint artworkTextureId_ = 0;
    std::vector<uint8_t> pendingArtworkPixels_;
    int32_t artworkWidth_ = 0;
    int32_t artworkHeight_ = 0;
    bool artworkTextureDirty_ = false;

    GLuint iconTextureId_ = 0;
    std::vector<uint8_t> pendingIconPixels_;
    int32_t iconWidth_ = 0;
    int32_t iconHeight_ = 0;
    bool iconTextureDirty_ = false;

    uint32_t primaryColorArgb_ = 0xFFFFFFFF;
    uint32_t surfaceColorArgb_ = 0xFF121212;
    int32_t placeholderIconType_ = 1;
    SiliconVisContrastMode contrastMode_ = SILICON_VIS_CONTRAST_NONE;
    uint32_t contrastScrimArgb_ = 0xFF000000;
    bool showArtworkBackground_ = true;
};

} // namespace silicon::vis::gl
