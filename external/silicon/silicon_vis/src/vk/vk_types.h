#pragma once

#include <cstdint>
#include <cstddef>

#if defined(__ANDROID__)
#include <android/log.h>
#define VK_VIS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SiliconPlayer", "[SiliconVisVk] " __VA_ARGS__)
#define VK_VIS_LOGW(...) __android_log_print(ANDROID_LOG_WARN, "SiliconPlayer", "[SiliconVisVk] " __VA_ARGS__)
#define VK_VIS_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SiliconPlayer", "[SiliconVisVk] " __VA_ARGS__)
#else
#include <cstdio>
#define VK_VIS_LOGE(...) do { fprintf(stderr, "[SiliconPlayer ERROR] [SiliconVisVk] " __VA_ARGS__); fputc('\n', stderr); } while (0)
#define VK_VIS_LOGW(...) do { fprintf(stderr, "[SiliconPlayer WARN] [SiliconVisVk] " __VA_ARGS__); fputc('\n', stderr); } while (0)
#define VK_VIS_LOGI(...) do { fprintf(stdout, "[SiliconPlayer INFO] [SiliconVisVk] " __VA_ARGS__); fputc('\n', stdout); } while (0)
#endif

namespace silicon::vis::vk {

enum class VulkanTier {
    None = 0,
    Tier1_Base11 = 1,
    Tier2_Enhanced12 = 2,
    Tier3_Modern13 = 3
};

struct DeviceCapabilities {
    VulkanTier tier = VulkanTier::None;
    uint32_t apiVersion = 0;
    char deviceName[256]{};
    bool hasTimelineSemaphores = false;
    bool hasHostQueryReset = false;
    bool hasDynamicRendering = false;
    bool hasExtendedDynamicState = false;
    bool hasSynchronization2 = false;
    uint32_t graphicsQueueFamilyIndex = 0;
    uint32_t maxMsaaSamples = 1;
};

} // namespace silicon::vis::vk
