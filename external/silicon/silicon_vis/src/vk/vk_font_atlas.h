#pragma once

#include "vk_context.h"
#include <cstdint>

namespace silicon::vis::vk {

class VkFontAtlas {
public:
    VkFontAtlas() = default;
    ~VkFontAtlas();

    bool init(VkContext* context, VkDescriptorSetLayout descLayout);
    void release();

    bool updateAtlas(const uint8_t* rgbaPixels, int32_t width, int32_t height);

    VkDescriptorSet getDescriptorSet() const { return descriptorSet_; }
    bool isReady() const { return image_ != VK_NULL_HANDLE && descriptorSet_ != VK_NULL_HANDLE; }

private:
    void cleanupImage();

    VkContext* context_ = nullptr;
    VkDescriptorSetLayout descLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool descPool_ = VK_NULL_HANDLE;
    VkDescriptorSet descriptorSet_ = VK_NULL_HANDLE;

    VkImage image_ = VK_NULL_HANDLE;
    VkDeviceMemory memory_ = VK_NULL_HANDLE;
    VkImageView imageView_ = VK_NULL_HANDLE;
    VkSampler sampler_ = VK_NULL_HANDLE;

    int32_t width_ = 0;
    int32_t height_ = 0;
};

} // namespace silicon::vis::vk
