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
    jboolean asyncReadback
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
    silicon_vis_draw_transition(reinterpret_cast<SiliconVisHandle>(visHandle));
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
