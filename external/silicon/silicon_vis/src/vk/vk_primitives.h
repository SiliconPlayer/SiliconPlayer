#pragma once

#include "vk_context.h"
#include <cstdint>
#include <vector>

namespace silicon::vis::vk {

struct PushConstantFlat {
    float color[4];
    float resolution[2];
};

struct PushConstantWaveLine {
    float color[4];
    float resolution[2];
    float halfWidth;
    float softness;
};

struct PushConstantText {
    float resolution[2];
};

struct PushConstantTransition {
    float resolution[2];
    float offsetX;
    float alpha;
};

struct PushConstantArtworkBg {
    float centerColor[4];
    float edgeColor[4];
    float circleColor[4];
    float resolution[2];
    float circleRadius;
    float alpha;
};

struct PushConstantArtworkTex {
    float color[4];
    float resolution[2];
    float mono;
    float pad;
};

struct PushConstantContrast {
    float scrimColor[4];
    float resolution[2];
    int32_t mode;
    float pad;
};

struct PushConstantStarPoint {
    float color[3];
    float soft;
    float globalAlpha;
    float square;
    float resolution[2];
};

struct PushConstantStarBlit {
    float alpha;
    float pad[3];
};

struct PushConstantStarAdd {
    float strength;
    float pad[3];
};

struct PushConstantStarDown {
    float thresh;
    float pad[3];
};

struct PushConstantStarKawase {
    float px[2];
    float pad[2];
};

class VkDynamicVertexBuffer {
public:
    VkDynamicVertexBuffer() = default;
    ~VkDynamicVertexBuffer();

    bool init(VkContext* context, size_t capacityBytes);
    void release();

    // Allocates bytes from the ring buffer. Returns offset, or (size_t)-1 on failure.
    size_t allocate(const void* data, size_t sizeBytes);
    void reset();

    VkBuffer getBuffer() const { return buffer_; }

private:
    VkContext* context_ = nullptr;
    VkBuffer buffer_ = VK_NULL_HANDLE;
    VkDeviceMemory memory_ = VK_NULL_HANDLE;
    uint8_t* mappedPtr_ = nullptr;
    size_t capacityBytes_ = 0;
    size_t currentOffset_ = 0;
};

class VkPrimitivePipelines {
public:
    VkPrimitivePipelines() = default;
    ~VkPrimitivePipelines();

    bool init(VkContext* context, VkRenderPass renderPass, uint32_t subpass = 0, VkSampleCountFlagBits samples = VK_SAMPLE_COUNT_1_BIT);
    void release();

    // Rebuilds only the pipelines against a new render pass / sample count,
    // keeping shader modules and layouts (and their descriptor sets) alive.
    bool recreatePipelines(VkRenderPass renderPass, uint32_t subpass, VkSampleCountFlagBits samples);

    void bindFlatTriangles(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb);
    void bindFlatLines(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb, float lineWidth);
    void bindWaveLines(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb, float halfWidth, float softness);
    void bindText(VkCommandBuffer cmd, float width, float height, VkDescriptorSet fontDescriptorSet);
    void bindTransition(VkCommandBuffer cmd, float width, float height, float offsetX, float alpha, VkDescriptorSet snapshotDescriptorSet);
    void bindArtworkBg(VkCommandBuffer cmd, float width, float height,
                       const float centerColor[4], const float edgeColor[4], const float circleColor[4],
                       float circleRadius, float alpha);
    void bindArtworkTex(VkCommandBuffer cmd, float width, float height,
                        float r, float g, float b, float a, float mono,
                        VkDescriptorSet texDescriptorSet);
    void bindContrast(VkCommandBuffer cmd, float width, float height,
                      int32_t mode, uint32_t scrimColorArgb);
    void bindStarBlit(VkCommandBuffer cmd, float width, float height,
                      float alpha, VkDescriptorSet texDescriptorSet);
    void bindStarAdd(VkCommandBuffer cmd, float width, float height,
                     float strength, VkDescriptorSet texDescriptorSet);

    VkPipelineLayout getFlatPipelineLayout() const { return flatLayout_; }
    VkPipelineLayout getWavePipelineLayout() const { return waveLayout_; }
    VkPipelineLayout getTextPipelineLayout() const { return textLayout_; }
    VkPipelineLayout getTransitionPipelineLayout() const { return transitionLayout_; }
    VkPipelineLayout getArtworkBgPipelineLayout() const { return artworkBgLayout_; }
    VkPipelineLayout getArtworkTexPipelineLayout() const { return artworkTexLayout_; }
    VkPipelineLayout getContrastPipelineLayout() const { return contrastLayout_; }
    VkPipelineLayout getStarBlitLayout() const { return starBlitLayout_; }
    VkPipelineLayout getStarAddLayout() const { return starAddLayout_; }
    VkDescriptorSetLayout getTextDescLayout() const { return textDescLayout_; }

private:
    bool createShaders();
    bool createLayouts();
    bool createPipelines(VkRenderPass renderPass, uint32_t subpass, VkSampleCountFlagBits samples);
    void destroyPipelines();

    VkContext* context_ = nullptr;

    // Shader modules
    VkShaderModule flatVertShader_ = VK_NULL_HANDLE;
    VkShaderModule flatFragShader_ = VK_NULL_HANDLE;
    VkShaderModule waveVertShader_ = VK_NULL_HANDLE;
    VkShaderModule waveFragShader_ = VK_NULL_HANDLE;
    VkShaderModule textVertShader_ = VK_NULL_HANDLE;
    VkShaderModule textFragShader_ = VK_NULL_HANDLE;
    VkShaderModule transitionVertShader_ = VK_NULL_HANDLE;
    VkShaderModule transitionFragShader_ = VK_NULL_HANDLE;
    VkShaderModule artworkBgVertShader_ = VK_NULL_HANDLE;
    VkShaderModule artworkBgFragShader_ = VK_NULL_HANDLE;
    VkShaderModule artworkTexVertShader_ = VK_NULL_HANDLE;
    VkShaderModule artworkTexFragShader_ = VK_NULL_HANDLE;
    VkShaderModule contrastVertShader_ = VK_NULL_HANDLE;
    VkShaderModule contrastFragShader_ = VK_NULL_HANDLE;
    VkShaderModule starBlitVertShader_ = VK_NULL_HANDLE;
    VkShaderModule starBlitFragShader_ = VK_NULL_HANDLE;
    VkShaderModule starAddFragShader_ = VK_NULL_HANDLE;

    // Layouts
    VkPipelineLayout flatLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout waveLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout textLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout transitionLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout artworkBgLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout artworkTexLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout contrastLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout starBlitLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout starAddLayout_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout textDescLayout_ = VK_NULL_HANDLE;

    // Pipelines
    VkPipeline flatTrianglesPipeline_ = VK_NULL_HANDLE;
    VkPipeline flatLinesPipeline_ = VK_NULL_HANDLE;
    VkPipeline waveLinesPipeline_ = VK_NULL_HANDLE;
    VkPipeline textPipeline_ = VK_NULL_HANDLE;
    VkPipeline transitionPipeline_ = VK_NULL_HANDLE;
    VkPipeline artworkBgPipeline_ = VK_NULL_HANDLE;
    VkPipeline artworkTexPipeline_ = VK_NULL_HANDLE;
    VkPipeline contrastPipeline_ = VK_NULL_HANDLE;
    VkPipeline starBlitPipeline_ = VK_NULL_HANDLE;
    VkPipeline starAddPipeline_ = VK_NULL_HANDLE;
};

} // namespace silicon::vis::vk
