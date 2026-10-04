#pragma once

#include "vk_context.h"
#include <vector>

namespace silicon::vis::vk {

class VkSwapchain {
public:
    VkSwapchain() = default;
    ~VkSwapchain();

    bool init(VkContext* context, VkSurfaceKHR surface, uint32_t width, uint32_t height, uint32_t msaaSamples = 1);
    void release();

    bool resize(uint32_t width, uint32_t height);

    // Acquires next swapchain image. Returns true on success.
    // If outOfDate is set to true, the caller should resize().
    bool acquireNextImage(VkSemaphore signalSemaphore, uint32_t& outImageIndex, bool& outOutOfDate);

    // Presents the image. Returns true on success.
    // If outOfDate is set to true, the caller should resize().
    bool present(VkQueue queue, uint32_t imageIndex, VkSemaphore waitSemaphore, bool& outOutOfDate);

    VkRenderPass getRenderPass() const { return renderPass_; }
    VkFramebuffer getFramebuffer(uint32_t imageIndex) const {
        return (imageIndex < framebuffers_.size()) ? framebuffers_[imageIndex] : VK_NULL_HANDLE;
    }
    VkExtent2D getExtent() const { return extent_; }
    VkFormat getImageFormat() const { return format_.format; }
    VkSampleCountFlagBits getMsaaSamples() const { return msaaSamples_; }
    uint32_t getImageCount() const { return static_cast<uint32_t>(images_.size()); }

private:
    bool createSwapchain(uint32_t width, uint32_t height);
    bool createImageViews();
    bool createMsaaResources();
    bool createRenderPass();
    bool createFramebuffers();
    void cleanupSwapchain();

    VkContext* context_ = nullptr;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain_ = VK_NULL_HANDLE;
    VkSurfaceFormatKHR format_{};
    VkPresentModeKHR presentMode_ = VK_PRESENT_MODE_FIFO_KHR;
    VkExtent2D extent_{};

    VkSampleCountFlagBits msaaSamples_ = VK_SAMPLE_COUNT_1_BIT;
    VkImage msaaColorImage_ = VK_NULL_HANDLE;
    VkDeviceMemory msaaColorMemory_ = VK_NULL_HANDLE;
    VkImageView msaaColorImageView_ = VK_NULL_HANDLE;

    std::vector<VkImage> images_;
    std::vector<VkImageView> imageViews_;
    std::vector<VkFramebuffer> framebuffers_;
    VkRenderPass renderPass_ = VK_NULL_HANDLE;
};

} // namespace silicon::vis::vk
