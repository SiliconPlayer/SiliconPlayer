#pragma once

#if defined(__ANDROID__)
    #include <EGL/egl.h>
    #include <GLES2/gl2.h>
    #include <GLES2/gl2ext.h>
    #ifndef GL_READ_FRAMEBUFFER
    #define GL_READ_FRAMEBUFFER 0x8CA8
    #endif
    #ifndef GL_DRAW_FRAMEBUFFER
    #define GL_DRAW_FRAMEBUFFER 0x8CA9
    #endif
    #include <android/log.h>
    #define VIS_LOG_TAG "SiliconVis"
    #define VIS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, VIS_LOG_TAG, __VA_ARGS__)
    #define VIS_LOGW(...) __android_log_print(ANDROID_LOG_WARN, VIS_LOG_TAG, __VA_ARGS__)
    #define VIS_LOGI(...) __android_log_print(ANDROID_LOG_INFO, VIS_LOG_TAG, __VA_ARGS__)
#elif defined(__APPLE__)
    #include <OpenGL/gl.h>
    #include <cstdio>
    #define VIS_LOGE(...) fprintf(stderr, "[SiliconVis ERROR] " __VA_ARGS__)
    #define VIS_LOGW(...) fprintf(stderr, "[SiliconVis WARN] " __VA_ARGS__)
    #define VIS_LOGI(...) fprintf(stdout, "[SiliconVis INFO] " __VA_ARGS__)
#else
    #define GL_GLEXT_PROTOTYPES
    #include <GL/gl.h>
    #include <GL/glext.h>
    #include <cstdio>
    #define VIS_LOGE(...) fprintf(stderr, "[SiliconVis ERROR] " __VA_ARGS__)
    #define VIS_LOGW(...) fprintf(stderr, "[SiliconVis WARN] " __VA_ARGS__)
    #define VIS_LOGI(...) fprintf(stdout, "[SiliconVis INFO] " __VA_ARGS__)
#endif
