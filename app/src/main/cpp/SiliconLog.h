#pragma once

// Every log in the app shares one tag; the module rides in the message.
#include <android/log.h>

#define SILICONPLAYER_LOG_TAG "SiliconPlayer"

#ifndef SP_LOG_MODULE
#define SP_LOG_MODULE "?"
#endif

#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, SILICONPLAYER_LOG_TAG, "[" SP_LOG_MODULE "] " fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, SILICONPLAYER_LOG_TAG, "[" SP_LOG_MODULE "] " fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN, SILICONPLAYER_LOG_TAG, "[" SP_LOG_MODULE "] " fmt, ##__VA_ARGS__)
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, SILICONPLAYER_LOG_TAG, "[" SP_LOG_MODULE "] " fmt, ##__VA_ARGS__)
