#pragma once

#if defined(__ANDROID__)
    #include <GLES3/gl3.h>
    #include <android/log.h>
    #define VIS_LOG_TAG "SiliconPlayer"
    #define VIS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, VIS_LOG_TAG, __VA_ARGS__)
    #define VIS_LOGW(...) __android_log_print(ANDROID_LOG_WARN, VIS_LOG_TAG, __VA_ARGS__)
    #define VIS_LOGI(...) __android_log_print(ANDROID_LOG_INFO, VIS_LOG_TAG, __VA_ARGS__)
#elif defined(__APPLE__)
    #include <OpenGL/gl.h>
    #include <cstdio>
    #define VIS_LOGE(...) do { fprintf(stderr, "[SiliconPlayer ERROR] [SiliconVis] " __VA_ARGS__); fputc('\n', stderr); } while (0)
    #define VIS_LOGW(...) do { fprintf(stderr, "[SiliconPlayer WARN] [SiliconVis] " __VA_ARGS__); fputc('\n', stderr); } while (0)
    #define VIS_LOGI(...) do { fprintf(stdout, "[SiliconPlayer INFO] [SiliconVis] " __VA_ARGS__); fputc('\n', stdout); } while (0)
#else
    #define GL_GLEXT_PROTOTYPES
    #include <GL/gl.h>
    #include <GL/glext.h>
    #include <cstdio>
    #define VIS_LOGE(...) do { fprintf(stderr, "[SiliconPlayer ERROR] [SiliconVis] " __VA_ARGS__); fputc('\n', stderr); } while (0)
    #define VIS_LOGW(...) do { fprintf(stderr, "[SiliconPlayer WARN] [SiliconVis] " __VA_ARGS__); fputc('\n', stderr); } while (0)
    #define VIS_LOGI(...) do { fprintf(stdout, "[SiliconPlayer INFO] [SiliconVis] " __VA_ARGS__); fputc('\n', stdout); } while (0)
#endif
