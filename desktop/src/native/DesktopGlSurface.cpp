#include <jni.h>
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <GL/gl.h>
#include <GL/glx.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <algorithm>
#include "silicon/vis/vis_api.h"

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

    PFNGLGENFRAMEBUFFERSPROC glGenFramebuffers = nullptr;
    PFNGLBINDFRAMEBUFFERPROC glBindFramebuffer = nullptr;
    PFNGLFRAMEBUFFERTEXTURE2DPROC glFramebufferTexture2D = nullptr;
    PFNGLDELETEFRAMEBUFFERSPROC glDeleteFramebuffers = nullptr;
    PFNGLCHECKFRAMEBUFFERSTATUSPROC glCheckFramebufferStatus = nullptr;
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
    jobject outBuffer
) {
    if (!hostHandle || !visHandle || width <= 0 || height <= 0 || !outBuffer) return JNI_FALSE;
    auto* ctx = reinterpret_cast<DesktopGlContext*>(hostHandle);

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

        silicon_vis_resize(reinterpret_cast<SiliconVisHandle>(visHandle), width, height, density);
        silicon_vis_set_render_target_fbo(reinterpret_cast<SiliconVisHandle>(visHandle), ctx->fbo);
    }

    if (ctx->glBindFramebuffer) {
        ctx->glBindFramebuffer(GL_FRAMEBUFFER, ctx->fbo);
    }
    glViewport(0, 0, width, height);

    silicon_vis_render(reinterpret_cast<SiliconVisHandle>(visHandle));

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
