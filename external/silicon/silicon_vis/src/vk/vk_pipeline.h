#pragma once

#include "vk_context.h"
#include "vk_swapchain.h"
#include "vk_primitives.h"
#include "vk_font_atlas.h"
#include "vk_artwork_renderer.h"
#include <vector>

namespace silicon::vis::vk {

class SiliconVisVulkanPipeline {
public:
    SiliconVisVulkanPipeline();
    ~SiliconVisVulkanPipeline();

    bool init(uint32_t width, uint32_t height, void* nativeWindow = nullptr);
    void release();
    bool resize(uint32_t width, uint32_t height, float density = 1.0f);

    // Selects 4x MSAA (true) or no MSAA (false). Pre-init it only caches the
    // choice; post-init it rebuilds the swapchain and pipelines in place.
    void setMsaaEnabled(bool enabled);

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
    VkArtworkRenderer& getArtworkRenderer() { return artworkRenderer_; }
    VkSwapchain* getSwapchain() { return isSurfaceMode_ ? &swapchain_ : nullptr; }

    bool isSurfaceMode() const { return isSurfaceMode_; }
    bool isReady() const { return initialized_; }
    uint32_t getWidth() const { return width_; }
    uint32_t getHeight() const { return height_; }
    float getDensity() const { return density_; }

    // Artwork & Contrast
    void setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
        artworkRenderer_.setArtworkPixels(rgbaPixels, width, height);
    }
    void clearArtwork() {
        artworkRenderer_.clearArtwork();
    }
    void setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
        artworkRenderer_.setIconPixels(rgbaPixels, width, height);
    }
    void clearIcon() {
        artworkRenderer_.clearIcon();
    }
    void setTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType) {
        artworkRenderer_.setTheme(primaryColorArgb, surfaceColorArgb, placeholderIconType);
    }
    void setContrastMode(SiliconVisContrastMode mode) {
        artworkRenderer_.setContrastMode(mode);
    }
    void setContrastScrim(uint32_t argb) {
        artworkRenderer_.setContrastScrim(argb);
    }
    void setShowArtworkBackground(bool show) {
        artworkRenderer_.setShowArtworkBackground(show);
    }
    void setMonochromeTarget(bool enabled) {
        artworkRenderer_.setMonochromeTarget(enabled);
    }
    void drawArtwork(VkCommandBuffer cmd, float density) {
        artworkRenderer_.draw(cmd, &pipelines_, &vertexBuffer_, static_cast<float>(width_), static_cast<float>(height_), density);
    }

    // Readback for offscreen desktop rendering
    bool copyPixelsToBuffer(void* outRgbaBuffer, size_t bufferSize);

    // Transition snapshot support
    bool takeTransitionSnapshot();
    void releaseTransitionSnapshot();
    void drawTransition(float offsetX, float alpha);
    bool hasTransitionSnapshot() const { return hasSnapshot_; }

private:
    bool createSyncObjects();
    void cleanupSyncObjects();

    bool createOffscreenResources();
    void cleanupOffscreenResources();
    bool createOffscreenRenderPass();
    void cleanupSnapshot();

    bool initialized_ = false;
    bool isSurfaceMode_ = false;
    bool msaaEnabled_ = true;
    uint32_t width_ = 0;
    uint32_t height_ = 0;
    float density_ = 1.0f;
    float clearColor_[4] = {0.0f, 0.0f, 0.0f, 1.0f};

    VkContext context_;
    VkSwapchain swapchain_;
    VkPrimitivePipelines pipelines_;
    VkDynamicVertexBuffer vertexBuffer_;
    VkFontAtlas fontAtlas_;
    VkArtworkRenderer artworkRenderer_;

    static constexpr int kMaxFramesInFlight = 2;
    VkCommandBuffer commandBuffers_[kMaxFramesInFlight]{};
    VkSemaphore imageAvailableSemaphores_[kMaxFramesInFlight]{};
    VkSemaphore renderFinishedSemaphores_[kMaxFramesInFlight]{};
    VkFence inFlightFences_[kMaxFramesInFlight]{};
    int currentFrameIndex_ = 0;
    uint32_t currentImageIndex_ = 0;
    VkCommandBuffer currentCmd_ = VK_NULL_HANDLE;

    bool hasRenderedFrame_ = false;
    uint32_t lastRenderedImageIndex_ = 0;

    // Transition snapshot resources
    bool hasSnapshot_ = false;
    uint32_t snapshotWidth_ = 0;
    uint32_t snapshotHeight_ = 0;
    VkImage snapshotImage_ = VK_NULL_HANDLE;
    VkDeviceMemory snapshotMemory_ = VK_NULL_HANDLE;
    VkImageView snapshotImageView_ = VK_NULL_HANDLE;
    VkSampler snapshotSampler_ = VK_NULL_HANDLE;
    VkDescriptorPool snapshotDescriptorPool_ = VK_NULL_HANDLE;
    VkDescriptorSet snapshotDescriptorSet_ = VK_NULL_HANDLE;

    // Offscreen resources (Desktop headless mode)
    VkSampleCountFlagBits msaaSamples_ = VK_SAMPLE_COUNT_1_BIT;
    VkImage msaaImage_ = VK_NULL_HANDLE;
    VkDeviceMemory msaaMemory_ = VK_NULL_HANDLE;
    VkImageView msaaImageView_ = VK_NULL_HANDLE;
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
