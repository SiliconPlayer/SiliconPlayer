#pragma once

#include <cstdint>
#include <cstddef>

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
