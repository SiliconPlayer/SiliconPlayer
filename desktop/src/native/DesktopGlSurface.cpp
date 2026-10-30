#include <jni.h>
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include "gl/gl_platform.h"
#include <GL/glx.h>
#include <cstdlib>
#include <cstring>
#include <cstddef>
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
#ifndef GL_PIXEL_PACK_BUFFER
#define GL_PIXEL_PACK_BUFFER 0x88EB
#endif
#ifndef GL_STREAM_READ
#define GL_STREAM_READ 0x88E1
#endif
#ifndef GL_READ_ONLY
#define GL_READ_ONLY 0x88B8
#endif

typedef void (*PFNGLGENFRAMEBUFFERSPROC)(GLsizei n, GLuint* framebuffers);
typedef void (*PFNGLBINDFRAMEBUFFERPROC)(GLenum target, GLuint framebuffer);
typedef void (*PFNGLFRAMEBUFFERTEXTURE2DPROC)(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level);
typedef void (*PFNGLDELETEFRAMEBUFFERSPROC)(GLsizei n, const GLuint* framebuffers);
typedef GLenum (*PFNGLCHECKFRAMEBUFFERSTATUSPROC)(GLenum target);
typedef void (*PFNGLGENBUFFERSPROC)(GLsizei n, GLuint* buffers);
typedef void (*PFNGLDELETEBUFFERSPROC)(GLsizei n, const GLuint* buffers);
typedef void (*PFNGLBINDBUFFERSPROC)(GLenum target, GLuint buffer);
typedef void (*PFNGLBUFFERDATAPROC)(GLenum target, std::ptrdiff_t size, const void* data, GLenum usage);
typedef void* (*PFNGLMAPBUFFERPROC)(GLenum target, GLenum access);
typedef GLboolean (*PFNGLUNMAPBUFFERPROC)(GLenum target);

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
    GLuint pixelPackBuffers[3] = {0, 0, 0};
    int nextPixelPackBuffer = 0;
    int pendingPixelPackBuffers[3] = {0, 0, 0};
    int pendingPixelPackHead = 0;
    int pendingPixelPackCount = 0;
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
    PFNGLGENBUFFERSPROC glGenBuffers = nullptr;
    PFNGLDELETEBUFFERSPROC glDeleteBuffers = nullptr;
    PFNGLBINDBUFFERSPROC glBindBuffer = nullptr;
    PFNGLBUFFERDATAPROC glBufferData = nullptr;
    PFNGLMAPBUFFERPROC glMapBuffer = nullptr;
    PFNGLUNMAPBUFFERPROC glUnmapBuffer = nullptr;

    bool supportsAsyncReadback() const {
        return pixelPackBuffers[0] && pixelPackBuffers[1] && pixelPackBuffers[2] &&
            glDeleteBuffers && glBindBuffer &&
            glBufferData && glMapBuffer && glUnmapBuffer;
    }

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
    ctx->glGenBuffers = getGlProc<PFNGLGENBUFFERSPROC>("glGenBuffers");
    ctx->glDeleteBuffers = getGlProc<PFNGLDELETEBUFFERSPROC>("glDeleteBuffers");
    ctx->glBindBuffer = getGlProc<PFNGLBINDBUFFERSPROC>("glBindBuffer");
    ctx->glBufferData = getGlProc<PFNGLBUFFERDATAPROC>("glBufferData");
    ctx->glMapBuffer = getGlProc<PFNGLMAPBUFFERPROC>("glMapBuffer");
    ctx->glUnmapBuffer = getGlProc<PFNGLUNMAPBUFFERPROC>("glUnmapBuffer");

    if (ctx->glGenFramebuffers) {
        ctx->glGenFramebuffers(1, &ctx->fbo);
        glGenTextures(1, &ctx->colorTex);
    }
    if (ctx->glGenBuffers && ctx->glDeleteBuffers && ctx->glBindBuffer && ctx->glBufferData &&
        ctx->glMapBuffer && ctx->glUnmapBuffer) {
        ctx->glGenBuffers(3, ctx->pixelPackBuffers);
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
    jboolean captureOutput,
    jboolean asyncReadback,
    jfloat transitionOffsetX,
    jfloat transitionAlpha
) {
    if (!hostHandle || !visHandle || width <= 0 || height <= 0 || (captureOutput && !outBuffer)) return JNI_FALSE;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);
    auto* outPixels = captureOutput
        ? static_cast<uint8_t*>(env->GetDirectBufferAddress(outBuffer))
        : nullptr;
    if (captureOutput && !outPixels) return JNI_FALSE;

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
        ctx->pendingPixelPackHead = 0;
        ctx->pendingPixelPackCount = 0;
        ctx->nextPixelPackBuffer = 0;
        if (ctx->supportsAsyncReadback()) {
            const auto byteCount = static_cast<std::ptrdiff_t>(width) * height * 4;
            for (GLuint buffer : ctx->pixelPackBuffers) {
                ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
                ctx->glBufferData(GL_PIXEL_PACK_BUFFER, byteCount, nullptr, GL_STREAM_READ);
            }
            ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
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
    const int stride = width * 4;
    if (asyncReadback && ctx->supportsAsyncReadback()) {
        const int writeIndex = ctx->nextPixelPackBuffer;
        ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, ctx->pixelPackBuffers[writeIndex]);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        const int queueTail = (ctx->pendingPixelPackHead + ctx->pendingPixelPackCount) % 3;
        ctx->pendingPixelPackBuffers[queueTail] = writeIndex;
        ++ctx->pendingPixelPackCount;
        ctx->nextPixelPackBuffer = (writeIndex + 1) % 3;
        if (!captureOutput) return JNI_TRUE;

        if (ctx->pendingPixelPackCount <= 2) return JNI_FALSE;
        const int outputIndex = ctx->pendingPixelPackBuffers[ctx->pendingPixelPackHead];
        ctx->pendingPixelPackHead = (ctx->pendingPixelPackHead + 1) % 3;
        --ctx->pendingPixelPackCount;
        ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, ctx->pixelPackBuffers[outputIndex]);
        const auto* readPixels = static_cast<const uint8_t*>(
            ctx->glMapBuffer(GL_PIXEL_PACK_BUFFER, GL_READ_ONLY)
        );
        if (!readPixels) {
            ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            return JNI_FALSE;
        }

        for (int y = 0; y < height; ++y) {
            const uint8_t* srcRow = readPixels + (height - 1 - y) * stride;
            uint8_t* dstRow = outPixels + y * stride;
            std::memcpy(dstRow, srcRow, stride);
        }
        const GLboolean unmapped = ctx->glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
        ctx->glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        if (!unmapped) return JNI_FALSE;
    } else if (captureOutput) {
        const size_t byteCount = static_cast<size_t>(width) * height * 4;
        if (ctx->readBackBuffer.size() < byteCount) {
            ctx->readBackBuffer.resize(byteCount);
        }
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, ctx->readBackBuffer.data());
        for (int y = 0; y < height; ++y) {
            const uint8_t* srcRow = ctx->readBackBuffer.data() + (height - 1 - y) * stride;
            uint8_t* dstRow = outPixels + y * stride;
            std::memcpy(dstRow, srcRow, stride);
        }
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
    if (ctx->glDeleteBuffers &&
        (ctx->pixelPackBuffers[0] || ctx->pixelPackBuffers[1] || ctx->pixelPackBuffers[2])) {
        ctx->glDeleteBuffers(3, ctx->pixelPackBuffers);
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
