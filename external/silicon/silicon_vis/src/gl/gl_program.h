#pragma once

#include "gl_platform.h"
#include <string>

namespace silicon::vis::gl {

class GlProgram {
public:
    GlProgram() = default;
    ~GlProgram();

    bool compileAndLink(const char* vertexSource, const char* fragmentSource);
    void use() const;
    void release();

    bool isReady() const { return programId_ != 0; }
    GLuint getId() const { return programId_; }

    GLint getUniformLoc(const char* name) const;
    GLint getAttribLoc(const char* name) const;

private:
    GLuint compileShader(GLenum type, const char* source);

    GLuint programId_ = 0;
    GLuint vertexShaderId_ = 0;
    GLuint fragmentShaderId_ = 0;
};

} // namespace silicon::vis::gl
