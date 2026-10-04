#pragma once

#include "vk_dispatch.h"
#include "vk_types.h"
#include <vector>
#include <string>

namespace silicon::vis::vk {

class VkContext {
public:
    VkContext();
    ~VkContext();

    bool init(bool enableValidation = false);
    void release();

    bool isInitialized() const { return initialized_; }
    const DeviceCapabilities& getCapabilities() const { return capabilities_; }

    VkInstance getInstance() const { return instance_; }
    VkPhysicalDevice getPhysicalDevice() const { return physicalDevice_; }
    VkDevice getDevice() const { return device_; }
    VkQueue getGraphicsQueue() const { return graphicsQueue_; }
    uint32_t getGraphicsQueueFamily() const { return capabilities_.graphicsQueueFamilyIndex; }
    VkCommandPool getCommandPool() const { return commandPool_; }

    // Surface creation helpers
#if defined(__ANDROID__)
    VkSurfaceKHR createAndroidSurface(struct ANativeWindow* window);
#endif
    void destroySurface(VkSurfaceKHR surface);

    // Swapchain support queries
    bool querySurfaceSupport(VkSurfaceKHR surface, uint32_t& outMinImages, VkSurfaceFormatKHR& outFormat, VkPresentModeKHR& outPresentMode);

private:
    bool createInstance(bool enableValidation);
    bool selectPhysicalDevice();
    void probeCapabilities();
    bool createLogicalDevice();
    bool createCommandPool();

    bool initialized_ = false;
    VkInstance instance_ = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice_ = VK_NULL_HANDLE;
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue graphicsQueue_ = VK_NULL_HANDLE;
    VkCommandPool commandPool_ = VK_NULL_HANDLE;
    DeviceCapabilities capabilities_{};
};

} // namespace silicon::vis::vk
