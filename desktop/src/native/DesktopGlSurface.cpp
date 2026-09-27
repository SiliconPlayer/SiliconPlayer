#include <jni.h>
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include "gl/gl_platform.h"
#include <GL/glx.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <algorithm>
#include "silicon/vis/vis_api.h"
#include "gl/gl_program.h"
#include "ScopeTextOverlay.h"

#ifndef GL_FRAMEBUFFER
#define GL_FRAMEBUFFER 0x8D40
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#endif

typedef void (*PFNGLGENFRAMEBUFFERSPROC)(GLsizei n, GLuint* framebuffers);
typedef void (*PFNGLBINDFRAMEBUFFERPROC)(GLenum target, GLuint framebuffer);
typedef void (*PFNGLFRAMEBUFFERTEXTURE2DPROC)(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level);
typedef void (*PFNGLDELETEFRAMEBUFFERSPROC)(GLsizei n, const GLuint* framebuffers);
typedef GLenum (*PFNGLCHECKFRAMEBUFFERSTATUSPROC)(GLenum target);

namespace {

static const char* TRANSITION_VERT_SHADER =
    "attribute vec2 aPosition;\n"
    "attribute vec2 aTexCoord;\n"
    "uniform vec2 uResolution;\n"
    "uniform float uOffsetX;\n"
    "varying vec2 vTexCoord;\n"
    "void main() {\n"
    "    vTexCoord = aTexCoord;\n"
    "    vec2 pos = aPosition + vec2(uOffsetX, 0.0);\n"
    "    vec2 zeroToOne = pos / uResolution;\n"
    "    vec2 zeroToTwo = zeroToOne * 2.0;\n"
    "    vec2 clipSpace = zeroToTwo - 1.0;\n"
    "    gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);\n"
    "}\n";

static const char* TRANSITION_FRAG_SHADER =
    "#ifdef GL_ES\n"
    "precision mediump float;\n"
    "#endif\n"
    "varying vec2 vTexCoord;\n"
    "uniform sampler2D uSampler;\n"
    "uniform float uAlpha;\n"
    "void main() {\n"
    "    vec4 tex = texture2D(uSampler, vTexCoord);\n"
    "    gl_FragColor = vec4(tex.rgb, tex.a * uAlpha);\n"
    "}\n";

struct DesktopGlContext {
    Display* glDisplay = nullptr;
    Window dummyWindow = 0;
    GLXContext glContext = nullptr;
    GLuint fbo = 0;
    GLuint colorTex = 0;
    int width = 0;
    int height = 0;
    float density = 1.0f;
    std::vector<uint8_t> readBackBuffer;
    ScopeTextOverlay textOverlay;

    GLuint snapshotTex = 0;
    int snapshotWidth = 0;
    int snapshotHeight = 0;
    silicon::vis::gl::GlProgram transitionProgram;
    GLint transitionResLoc = -1;
    GLint transitionOffsetLoc = -1;
    GLint transitionAlphaLoc = -1;
    GLint transitionSamplerLoc = -1;
    GLint transitionPosLoc = -1;
    GLint transitionCoordLoc = -1;

    PFNGLGENFRAMEBUFFERSPROC glGenFramebuffers = nullptr;
    PFNGLBINDFRAMEBUFFERPROC glBindFramebuffer = nullptr;
    PFNGLFRAMEBUFFERTEXTURE2DPROC glFramebufferTexture2D = nullptr;
    PFNGLDELETEFRAMEBUFFERSPROC glDeleteFramebuffers = nullptr;
    PFNGLCHECKFRAMEBUFFERSTATUSPROC glCheckFramebufferStatus = nullptr;

    bool takeSnapshot() {
        if (width <= 0 || height <= 0 || !colorTex) return false;
        if (snapshotTex == 0) {
            glGenTextures(1, &snapshotTex);
        }
        std::swap(colorTex, snapshotTex);
        snapshotWidth = width;
        snapshotHeight = height;

        glBindTexture(GL_TEXTURE_2D, colorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        if (glBindFramebuffer && glFramebufferTexture2D) {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
        }
        return true;
    }

    void releaseSnapshot() {
        if (snapshotTex != 0) {
            glDeleteTextures(1, &snapshotTex);
            snapshotTex = 0;
        }
        snapshotWidth = 0;
        snapshotHeight = 0;
    }

    void drawTransition(float surfaceWidth, float surfaceHeight, float offsetXPx, float alpha) {
        if (snapshotTex == 0 || snapshotWidth != static_cast<int>(surfaceWidth) || snapshotHeight != static_cast<int>(surfaceHeight) || alpha <= 0.001f) {
            return;
        }
        if (!transitionProgram.isReady()) {
            if (!transitionProgram.compileAndLink(TRANSITION_VERT_SHADER, TRANSITION_FRAG_SHADER)) {
                return;
            }
            transitionResLoc = transitionProgram.getUniformLoc("uResolution");
            transitionOffsetLoc = transitionProgram.getUniformLoc("uOffsetX");
            transitionAlphaLoc = transitionProgram.getUniformLoc("uAlpha");
            transitionSamplerLoc = transitionProgram.getUniformLoc("uSampler");
            transitionPosLoc = transitionProgram.getAttribLoc("aPosition");
            transitionCoordLoc = transitionProgram.getAttribLoc("aTexCoord");
        }

        const float w = surfaceWidth;
        const float h = surfaceHeight;
        const float verts[] = {
            // x, y, u, v
            0.0f, 0.0f, 0.0f, 1.0f,
            w,    0.0f, 1.0f, 1.0f,
            0.0f, h,    0.0f, 0.0f,
            w,    0.0f, 1.0f, 1.0f,
            w,    h,    1.0f, 0.0f,
            0.0f, h,    0.0f, 0.0f
        };

        transitionProgram.use();
        glUniform2f(transitionResLoc, w, h);
        glUniform1f(transitionOffsetLoc, offsetXPx);
        glUniform1f(transitionAlphaLoc, std::clamp(alpha, 0.0f, 1.0f));
        glUniform1i(transitionSamplerLoc, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, snapshotTex);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glEnableVertexAttribArray(transitionPosLoc);
        glVertexAttribPointer(transitionPosLoc, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), verts);

        glEnableVertexAttribArray(transitionCoordLoc);
        glVertexAttribPointer(transitionCoordLoc, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), verts + 2);

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(transitionPosLoc);
        glDisableVertexAttribArray(transitionCoordLoc);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_BLEND);
    }
};

template <typename T>
T getGlProc(const char* name) {
    void* proc = reinterpret_cast<void*>(glXGetProcAddressARB(reinterpret_cast<const GLubyte*>(name)));
    return reinterpret_cast<T>(proc);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeInit(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong visHandle
) {
    Display* glDisplay = XOpenDisplay(nullptr);
    if (!glDisplay) {
        fprintf(stderr, "[DesktopGlSurface] Failed to open X11 display\n");
        return 0;
    }

    int screen = DefaultScreen(glDisplay);
    static int visualAttribs[] = {
        GLX_RGBA,
        GLX_RED_SIZE, 8,
        GLX_GREEN_SIZE, 8,
        GLX_BLUE_SIZE, 8,
        GLX_ALPHA_SIZE, 8,
        None
    };

    XVisualInfo* vi = glXChooseVisual(glDisplay, screen, visualAttribs);
    if (!vi) {
        fprintf(stderr, "[DesktopGlSurface] glXChooseVisual failed\n");
        XCloseDisplay(glDisplay);
        return 0;
    }

    GLXContext glContext = glXCreateContext(glDisplay, vi, nullptr, GL_TRUE);
    if (!glContext) {
        fprintf(stderr, "[DesktopGlSurface] glXCreateContext failed\n");
        XFree(vi);
        XCloseDisplay(glDisplay);
        return 0;
    }

    Window dummyWindow = XCreateSimpleWindow(
        glDisplay,
        RootWindow(glDisplay, screen),
        0, 0, 1, 1, 0, 0, 0
    );

    XFree(vi);

    if (!glXMakeCurrent(glDisplay, dummyWindow, glContext)) {
        fprintf(stderr, "[DesktopGlSurface] glXMakeCurrent failed\n");
        glXDestroyContext(glDisplay, glContext);
        XDestroyWindow(glDisplay, dummyWindow);
        XCloseDisplay(glDisplay);
        return 0;
    }

    auto* ctx = new DesktopGlContext();
    ctx->glDisplay = glDisplay;
    ctx->dummyWindow = dummyWindow;
    ctx->glContext = glContext;

    ctx->glGenFramebuffers = getGlProc<PFNGLGENFRAMEBUFFERSPROC>("glGenFramebuffers");
    ctx->glBindFramebuffer = getGlProc<PFNGLBINDFRAMEBUFFERPROC>("glBindFramebuffer");
    ctx->glFramebufferTexture2D = getGlProc<PFNGLFRAMEBUFFERTEXTURE2DPROC>("glFramebufferTexture2D");
    ctx->glDeleteFramebuffers = getGlProc<PFNGLDELETEFRAMEBUFFERSPROC>("glDeleteFramebuffers");
    ctx->glCheckFramebufferStatus = getGlProc<PFNGLCHECKFRAMEBUFFERSTATUSPROC>("glCheckFramebufferStatus");

    if (ctx->glGenFramebuffers) {
        ctx->glGenFramebuffers(1, &ctx->fbo);
        glGenTextures(1, &ctx->colorTex);
    }

    if (visHandle != 0) {
        silicon_vis_init_gl(reinterpret_cast<SiliconVisHandle>(visHandle));
    }

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeRenderFrame(
    JNIEnv* env,
    jobject /* thiz */,
    jlong hostHandle,
    jlong visHandle,
    jint width,
    jint height,
    jfloat density,
    jobject outBuffer,
    jfloat transitionOffsetX,
    jfloat transitionAlpha
) {
    if (!hostHandle || !visHandle || width <= 0 || height <= 0 || !outBuffer) return JNI_FALSE;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);

    if (!glXMakeCurrent(ctx->glDisplay, ctx->dummyWindow, ctx->glContext)) {
        return JNI_FALSE;
    }

    if (ctx->width != width || ctx->height != height || ctx->density != density) {
        ctx->releaseSnapshot();
        ctx->width = width;
        ctx->height = height;
        ctx->density = density;

        glBindTexture(GL_TEXTURE_2D, ctx->colorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        if (ctx->glBindFramebuffer && ctx->glFramebufferTexture2D) {
            ctx->glBindFramebuffer(GL_FRAMEBUFFER, ctx->fbo);
            ctx->glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ctx->colorTex, 0);
        }

        silicon_vis_resize(reinterpret_cast<SiliconVisHandle>(visHandle), width, height, density);
        silicon_vis_set_render_target_fbo(reinterpret_cast<SiliconVisHandle>(visHandle), ctx->fbo);
    }

    if (ctx->glBindFramebuffer) {
        ctx->glBindFramebuffer(GL_FRAMEBUFFER, ctx->fbo);
    }
    glViewport(0, 0, width, height);

    silicon_vis_render(reinterpret_cast<SiliconVisHandle>(visHandle));

    ctx->textOverlay.draw(width, height);

    if (transitionAlpha > 0.001f) {
        ctx->drawTransition(static_cast<float>(width), static_cast<float>(height), transitionOffsetX, transitionAlpha);
    }

    uint8_t* outPixels = static_cast<uint8_t*>(env->GetDirectBufferAddress(outBuffer));
    if (!outPixels) return JNI_FALSE;

    const size_t byteCount = static_cast<size_t>(width) * height * 4;
    if (ctx->readBackBuffer.size() < byteCount) {
        ctx->readBackBuffer.resize(byteCount);
    }

    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, ctx->readBackBuffer.data());

    // Flip vertically from OpenGL bottom-left to Skia top-left
    const int stride = width * 4;
    for (int y = 0; y < height; ++y) {
        const uint8_t* srcRow = ctx->readBackBuffer.data() + (height - 1 - y) * stride;
        uint8_t* dstRow = outPixels + y * stride;
        std::memcpy(dstRow, srcRow, stride);
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeUploadScopeAtlas(
    JNIEnv* env,
    jobject /* thiz */,
    jlong hostHandle,
    jobject pixelBuffer,
    jint width,
    jint height,
    jfloat baseFontSizePx,
    jfloat lineHeightPx,
    jobject glyphBuffer,
    jint glyphCount
) {
    if (!hostHandle || !pixelBuffer || !glyphBuffer || width <= 0 || height <= 0 || glyphCount <= 0) {
        return JNI_FALSE;
    }
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);
    if (!glXMakeCurrent(ctx->glDisplay, ctx->dummyWindow, ctx->glContext)) {
        return JNI_FALSE;
    }
    const uint8_t* pixels = static_cast<const uint8_t*>(env->GetDirectBufferAddress(pixelBuffer));
    const void* glyphs = env->GetDirectBufferAddress(glyphBuffer);
    if (!pixels || !glyphs) return JNI_FALSE;
    return ctx->textOverlay.uploadAtlas(pixels, width, height, baseFontSizePx, lineHeightPx, glyphs, glyphCount)
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeSetScopeTextQuads(
    JNIEnv* env,
    jobject /* thiz */,
    jlong hostHandle,
    jfloatArray quadArray,
    jint floatCount
) {
    if (!hostHandle) return;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);
    if (!quadArray || floatCount <= 0) {
        ctx->textOverlay.setQuads(nullptr, 0);
        return;
    }
    jfloat* quads = env->GetFloatArrayElements(quadArray, nullptr);
    if (!quads) return;
    ctx->textOverlay.setQuads(quads, floatCount);
    env->ReleaseFloatArrayElements(quadArray, quads, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeTakeTransitionSnapshot(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong hostHandle
) {
    if (!hostHandle) return JNI_FALSE;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);
    if (!glXMakeCurrent(ctx->glDisplay, ctx->dummyWindow, ctx->glContext)) {
        return JNI_FALSE;
    }
    return ctx->takeSnapshot() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeReleaseTransitionSnapshot(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong hostHandle
) {
    if (!hostHandle) return;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);
    if (!glXMakeCurrent(ctx->glDisplay, ctx->dummyWindow, ctx->glContext)) {
        return;
    }
    ctx->releaseSnapshot();
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_DesktopGlSurface_nativeDestroy(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong hostHandle,
    jlong visHandle
) {
    if (!hostHandle) return;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);

    glXMakeCurrent(ctx->glDisplay, ctx->dummyWindow, ctx->glContext);

    ctx->releaseSnapshot();
    ctx->transitionProgram.release();
    ctx->textOverlay.release();

    if (visHandle != 0) {
        silicon_vis_release_gl(reinterpret_cast<SiliconVisHandle>(visHandle));
    }

    if (ctx->fbo && ctx->glDeleteFramebuffers) {
        ctx->glDeleteFramebuffers(1, &ctx->fbo);
    }
    if (ctx->colorTex) {
        glDeleteTextures(1, &ctx->colorTex);
    }

    glXMakeCurrent(ctx->glDisplay, None, nullptr);
    if (ctx->glContext) {
        glXDestroyContext(ctx->glDisplay, ctx->glContext);
    }
    if (ctx->dummyWindow) {
        XDestroyWindow(ctx->glDisplay, ctx->dummyWindow);
    }
    if (ctx->glDisplay) {
        XCloseDisplay(ctx->glDisplay);
    }

    delete ctx;
}

} // extern "C"
