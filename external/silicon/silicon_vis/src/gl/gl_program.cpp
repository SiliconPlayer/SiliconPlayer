#include "gl_program.h"
#include <vector>

namespace silicon::vis::gl {

GlProgram::~GlProgram() {
    release();
}

GLuint GlProgram::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    if (!shader) {
        VIS_LOGE("Failed creating shader type: %d", type);
        return 0;
    }
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
            VIS_LOGE("Shader compilation error: %s", infoLog.data());
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool GlProgram::compileAndLink(const char* vertexSource, const char* fragmentSource) {
    release();

    vertexShaderId_ = compileShader(GL_VERTEX_SHADER, vertexSource);
    if (!vertexShaderId_) return false;

    fragmentShaderId_ = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (!fragmentShaderId_) {
        release();
        return false;
    }

    programId_ = glCreateProgram();
    if (!programId_) {
        release();
        return false;
    }

    glAttachShader(programId_, vertexShaderId_);
    glAttachShader(programId_, fragmentShaderId_);
    glLinkProgram(programId_);

    GLint linked = 0;
    glGetProgramiv(programId_, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(programId_, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetProgramInfoLog(programId_, infoLen, nullptr, infoLog.data());
            VIS_LOGE("Program link error: %s", infoLog.data());
        }
        release();
        return false;
    }
    return true;
}

void GlProgram::use() const {
    if (programId_ != 0) {
        glUseProgram(programId_);
    }
}

void GlProgram::release() {
    if (programId_ != 0) {
        if (vertexShaderId_ != 0) {
            glDetachShader(programId_, vertexShaderId_);
            glDeleteShader(vertexShaderId_);
            vertexShaderId_ = 0;
        }
        if (fragmentShaderId_ != 0) {
            glDetachShader(programId_, fragmentShaderId_);
            glDeleteShader(fragmentShaderId_);
            fragmentShaderId_ = 0;
        }
        glDeleteProgram(programId_);
        programId_ = 0;
    }
}

GLint GlProgram::getUniformLoc(const char* name) const {
    if (!programId_) return -1;
    return glGetUniformLocation(programId_, name);
}

GLint GlProgram::getAttribLoc(const char* name) const {
    if (!programId_) return -1;
    return glGetAttribLocation(programId_, name);
}

} // namespace silicon::vis::gl
