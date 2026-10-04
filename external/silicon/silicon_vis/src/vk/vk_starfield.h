#pragma once

#include "vk_context.h"
#include "vk_primitives.h"
#include <vector>

namespace silicon::vis::vk {

// CPU-simulated frame inputs for the Vulkan starfield backend. Filled by
// StarfieldRenderer::simulate(); the backend only records GPU work.
struct StarVkFrame {
    int32_t pointCount = 0;
    const float* pos = nullptr;
    const float* size = nullptr;
    const float* alpha = nullptr;
    int32_t lineCount = 0;
    const float* lineVerts = nullptr;
    uint32_t starColorArgb = 0xFFFFFFFF;
    float softness = 0.25f;
    float globalAlpha = 1.0f;
    float brightness = 1.0f;
    bool squareStars = false;
    float fadeAlpha = 0.0f;
    bool wantBloom = false;
    float bloomStrength = 0.0f;
    float glowK = 1.0f;
};

class VkStarfieldRenderer {
public:
    VkStarfieldRenderer() = default;
    ~VkStarfieldRenderer();

    bool init(VkContext* context);
    void release();

    // Records trail fade, star points and streak lines plus the Kawase
    // bloom pyramid. Requires no active render pass; leaves sampled
    // images readable for composite().
    bool renderTrails(VkCommandBuffer cmd, VkDynamicVertexBuffer* dynBuffer,
                      const StarVkFrame& frame, uint32_t width, uint32_t height);
    // Composites the trail target (and bloom halo) into the caller's
    // active main render pass via the shared blit pipelines.
    void composite(VkCommandBuffer cmd, VkPrimitivePipelines* prims,
                   VkDynamicVertexBuffer* dynBuffer, const StarVkFrame& frame);

    float getMaxPointSize() const { return maxPointSize_; }

private:
    struct Target {
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
        VkFramebuffer framebuffer = VK_NULL_HANDLE;
        VkDescriptorSet set = VK_NULL_HANDLE;
        uint32_t width = 0;
        uint32_t height = 0;
    };

    bool ensureTargets(uint32_t width, uint32_t height);
    void releaseTargets();
    bool createTarget(Target& target, uint32_t width, uint32_t height);
    void destroyTarget(Target& target);
    bool createPasses();
    void destroyPasses();
    bool createOffscreenPipelines();
    void destroyOffscreenPipelines();
    bool createDescriptorPool();
    bool allocateSets();
    void readable(VkCommandBuffer cmd, Target& target);
    void writable(VkCommandBuffer cmd, Target& target);
    void beginTargetPass(VkCommandBuffer cmd, VkRenderPass pass,
                         Target& target, const float* clearColor);
    void barrier(VkCommandBuffer cmd, VkImage image,
                 VkImageLayout oldLayout, VkImageLayout newLayout,
                 VkAccessFlags srcAccess, VkAccessFlags dstAccess,
                 VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage);
    void setViewport(VkCommandBuffer cmd, uint32_t width, uint32_t height);
    void drawFullscreenTri(VkCommandBuffer cmd, VkDynamicVertexBuffer* dynBuffer);

    VkContext* context_ = nullptr;
    float maxPointSize_ = 1.0f;

    uint32_t width_ = 0;
    uint32_t height_ = 0;
    bool targetsReady_ = false;
    bool trailNeedsClear_ = true;
    bool trailReadable_ = false;

    Target trail_{};
    Target bloomA_{};
    Target bloomB_{};
    Target bloomC_{};
    Target bloomE_{};
    Target bloomF_{};

    VkRenderPass trailClearPass_ = VK_NULL_HANDLE;
    VkRenderPass trailLoadPass_ = VK_NULL_HANDLE;
    VkRenderPass bloomPass_ = VK_NULL_HANDLE;

    VkSampler sampler_ = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool_ = VK_NULL_HANDLE;

    // Scratch for interleaving star point attributes; resized on growth.
    std::vector<float> pointScratch_;

    VkShaderModule blitVertShader_ = VK_NULL_HANDLE;
    VkShaderModule flatVertShader_ = VK_NULL_HANDLE;
    VkShaderModule flatFragShader_ = VK_NULL_HANDLE;
    VkShaderModule pointVertShader_ = VK_NULL_HANDLE;
    VkShaderModule pointFragShader_ = VK_NULL_HANDLE;
    VkShaderModule fadeFragShader_ = VK_NULL_HANDLE;
    VkShaderModule downFragShader_ = VK_NULL_HANDLE;
    VkShaderModule kawaseFragShader_ = VK_NULL_HANDLE;

    VkPipelineLayout pointLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout fadeLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout blitLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout downLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout kawaseLayout_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout descLayout_ = VK_NULL_HANDLE;

    VkPipeline pointPipeline_ = VK_NULL_HANDLE;
    VkPipeline linePipeline_ = VK_NULL_HANDLE;
    VkPipeline fadePipeline_ = VK_NULL_HANDLE;
    VkPipeline downPipeline_ = VK_NULL_HANDLE;
    VkPipeline kawasePipeline_ = VK_NULL_HANDLE;
};

} // namespace silicon::vis::vk
