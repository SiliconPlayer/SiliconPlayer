#pragma once

#include <stdio.h>
#include <stdarg.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum android_LogPriority {
    ANDROID_LOG_UNKNOWN = 0,
    ANDROID_LOG_DEFAULT,
    ANDROID_LOG_VERBOSE,
    ANDROID_LOG_DEBUG,
    ANDROID_LOG_INFO,
    ANDROID_LOG_WARN,
    ANDROID_LOG_ERROR,
    ANDROID_LOG_FATAL,
    ANDROID_LOG_SILENT,
} android_LogPriority;

inline int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    (void)prio;
    va_list ap;
    va_start(ap, fmt);
    fprintf(stderr, "[%s] ", tag ? tag : "SiliconPlayer");
    int ret = vfprintf(stderr, fmt, ap);
    fprintf(stderr, "\n");
    va_end(ap);
    return ret;
}

inline int __android_log_vprint(int prio, const char* tag, const char* fmt, va_list ap) {
    (void)prio;
    fprintf(stderr, "[%s] ", tag ? tag : "SiliconPlayer");
    int ret = vfprintf(stderr, fmt, ap);
    fprintf(stderr, "\n");
    return ret;
}

#ifdef __cplusplus
}
#endif
