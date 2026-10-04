#pragma once

#include "vk_context.h"
#include "vk_swapchain.h"
#include "vk_primitives.h"
#include "vk_font_atlas.h"
#include <vector>

namespace silicon::vis::vk {

class SiliconVisVulkanPipeline {
public:
    SiliconVisVulkanPipeline();
    ~SiliconVisVulkanPipeline();

    bool init(uint32_t width, uint32_t height, void* nativeWindow = nullptr);
    void release();
    bool resize(uint32_t width, uint32_t height, float density = 1.0f);

    bool beginFrame();
    void endFrame();

    void setClearColor(float r, float g, float b, float a = 1.0f) {
        clearColor_[0] = r;
        clearColor_[1] = g;
        clearColor_[2] = b;
        clearColor_[3] = a;
    }

    VkCommandBuffer getCurrentCommandBuffer() const { return currentCmd_; }
    VkContext* getContext() { return &context_; }
    VkPrimitivePipelines& getPipelines() { return pipelines_; }
    VkDynamicVertexBuffer& getVertexBuffer() { return vertexBuffer_; }
    VkFontAtlas& getFontAtlas() { return fontAtlas_; }
    VkSwapchain* getSwapchain() { return isSurfaceMode_ ? &swapchain_ : nullptr; }

    bool isSurfaceMode() const { return isSurfaceMode_; }
    bool isReady() const { return initialized_; }
    uint32_t getWidth() const { return width_; }
    uint32_t getHeight() const { return height_; }
    float getDensity() const { return density_; }

    // Readback for offscreen desktop rendering
    bool copyPixelsToBuffer(void* outRgbaBuffer, size_t bufferSize);

private:
    bool createSyncObjects();
    void cleanupSyncObjects();

    bool createOffscreenResources();
    void cleanupOffscreenResources();

    bool initialized_ = false;
    bool isSurfaceMode_ = false;
    uint32_t width_ = 0;
    uint32_t height_ = 0;
    float density_ = 1.0f;
    float clearColor_[4] = {0.0f, 0.0f, 0.0f, 1.0f};

    VkContext context_;
    VkSwapchain swapchain_;
    VkPrimitivePipelines pipelines_;
    VkDynamicVertexBuffer vertexBuffer_;
    VkFontAtlas fontAtlas_;

    static constexpr int kMaxFramesInFlight = 2;
    VkCommandBuffer commandBuffers_[kMaxFramesInFlight]{};
    VkSemaphore imageAvailableSemaphores_[kMaxFramesInFlight]{};
    VkSemaphore renderFinishedSemaphores_[kMaxFramesInFlight]{};
    VkFence inFlightFences_[kMaxFramesInFlight]{};
    int currentFrameIndex_ = 0;
    uint32_t currentImageIndex_ = 0;
    VkCommandBuffer currentCmd_ = VK_NULL_HANDLE;

    // Offscreen resources (Desktop headless mode)
    VkImage offscreenImage_ = VK_NULL_HANDLE;
    VkDeviceMemory offscreenMemory_ = VK_NULL_HANDLE;
    VkImageView offscreenImageView_ = VK_NULL_HANDLE;
    VkRenderPass offscreenRenderPass_ = VK_NULL_HANDLE;
    VkFramebuffer offscreenFramebuffer_ = VK_NULL_HANDLE;
    VkBuffer readbackBuffer_ = VK_NULL_HANDLE;
    VkDeviceMemory readbackMemory_ = VK_NULL_HANDLE;
    void* readbackMapped_ = nullptr;
};

} // namespace silicon::vis::vk
