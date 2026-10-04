#pragma once

#include "vk_context.h"
#include "vk_primitives.h"
#include "silicon/vis/vis_types.h"
#include <vector>

namespace silicon::vis::vk {

class VkArtworkRenderer {
public:
    VkArtworkRenderer() = default;
    ~VkArtworkRenderer() { release(); }

    bool init(VkContext* context, VkDescriptorSetLayout texDescLayout);
    void release();

    void setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height);
    void clearArtwork();
    void setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height);
    void clearIcon();
    void setTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType);
    void setContrastMode(SiliconVisContrastMode mode) { contrastMode_ = mode; }
    void setContrastScrim(uint32_t argb) { contrastScrimArgb_ = argb; }
    void setShowArtworkBackground(bool show) { showArtworkBackground_ = show; }
    void setMonochromeTarget(bool enabled) { monoTarget_ = enabled; }

    void draw(VkCommandBuffer cmd,
              VkPrimitivePipelines* pipelines,
              VkDynamicVertexBuffer* dynBuffer,
              float surfaceWidth,
              float surfaceHeight,
              float density);

private:
    struct TextureResource {
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
        int32_t width = 0;
        int32_t height = 0;
    };

    struct ContentState {
        TextureResource artwork;
        TextureResource blur;
        TextureResource icon;
        VkDescriptorSet artworkDescSet = VK_NULL_HANDLE;
        VkDescriptorSet blurDescSet = VK_NULL_HANDLE;
        VkDescriptorSet iconDescSet = VK_NULL_HANDLE;
        uint32_t primaryArgb = 0xFFFFFFFF;
        uint32_t surfaceArgb = 0xFF121212;
        bool ownsArtwork = false;
        bool ownsBlur = false;
        bool ownsIcon = false;
    };

    bool uploadTexture(const uint8_t* rgbaPixels, int32_t width, int32_t height, TextureResource& outRes, VkDescriptorSet descSet);
    void releaseTexture(TextureResource& res);
    void releasePrevState();
    ContentState currentState() const;

    void ensureArtworkTexture();
    void ensureIconTexture();

    void drawArtworkOrFallback(VkCommandBuffer cmd,
                               VkPrimitivePipelines* pipelines,
                               VkDynamicVertexBuffer* dynBuffer,
                               const ContentState& state,
                               float surfaceWidth,
                               float surfaceHeight,
                               float density,
                               float alpha);

    void drawBlurFill(VkCommandBuffer cmd,
                      VkPrimitivePipelines* pipelines,
                      VkDynamicVertexBuffer* dynBuffer,
                      const ContentState& state,
                      float surfaceWidth,
                      float surfaceHeight,
                      float alpha);

    void drawGradientBackground(VkCommandBuffer cmd,
                                VkPrimitivePipelines* pipelines,
                                VkDynamicVertexBuffer* dynBuffer,
                                const ContentState& state,
                                float surfaceWidth,
                                float surfaceHeight,
                                float density,
                                bool drawCircle,
                                float monoMix,
                                float alpha);

    void drawContrastBackdrop(VkCommandBuffer cmd,
                              VkPrimitivePipelines* pipelines,
                              VkDynamicVertexBuffer* dynBuffer,
                              float surfaceWidth,
                              float surfaceHeight);

    VkContext* context_ = nullptr;
    VkDescriptorSetLayout texDescLayout_ = VK_NULL_HANDLE;
    VkSampler sampler_ = VK_NULL_HANDLE;
    VkDescriptorPool descPool_ = VK_NULL_HANDLE;
    VkDescriptorSet liveArtworkDescSet_ = VK_NULL_HANDLE;
    VkDescriptorSet liveBlurDescSet_ = VK_NULL_HANDLE;
    VkDescriptorSet liveIconDescSet_ = VK_NULL_HANDLE;
    VkDescriptorSet prevArtworkDescSet_ = VK_NULL_HANDLE;
    VkDescriptorSet prevBlurDescSet_ = VK_NULL_HANDLE;
    VkDescriptorSet prevIconDescSet_ = VK_NULL_HANDLE;

    ContentState prev_;
    long long fadeStartNs_ = -1;
    bool themeDirty_ = false;
    uint32_t pendingPrimaryColorArgb_ = 0xFFFFFFFF;
    uint32_t pendingSurfaceColorArgb_ = 0xFF121212;
    int32_t pendingPlaceholderIconType_ = 1;

    TextureResource artwork_;
    TextureResource blur_;
    std::vector<uint8_t> pendingArtworkPixels_;
    int32_t artworkWidth_ = 0;
    int32_t artworkHeight_ = 0;
    bool artworkTextureDirty_ = false;

    TextureResource icon_;
    std::vector<uint8_t> pendingIconPixels_;
    int32_t iconWidth_ = 0;
    int32_t iconHeight_ = 0;
    bool iconTextureDirty_ = false;

    uint32_t primaryColorArgb_ = 0xFFFFFFFF;
    uint32_t surfaceColorArgb_ = 0xFF121212;
    int32_t placeholderIconType_ = 1;
    SiliconVisContrastMode contrastMode_ = SILICON_VIS_CONTRAST_NONE;
    uint32_t contrastScrimArgb_ = 0xFF000000;
    bool showArtworkBackground_ = true;
    bool monoTarget_ = false;
    float monoMix_ = 0.0f;
    long long monoLastNs_ = 0;
};

} // namespace silicon::vis::vk
