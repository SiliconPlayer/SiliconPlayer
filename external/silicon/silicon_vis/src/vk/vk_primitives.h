#pragma once

#include "vk_context.h"
#include <cstdint>
#include <vector>

namespace silicon::vis::vk {

struct PushConstantFlat {
    float resolution[2];
    float color[4];
};

struct PushConstantWaveLine {
    float resolution[2];
    float color[4];
    float halfWidth;
    float softness;
};

struct PushConstantText {
    float resolution[2];
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

    void bindFlatTriangles(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb);
    void bindFlatLines(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb, float lineWidth);
    void bindWaveLines(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb, float halfWidth, float softness);
    void bindText(VkCommandBuffer cmd, float width, float height, VkDescriptorSet fontDescriptorSet);

    VkPipelineLayout getFlatPipelineLayout() const { return flatLayout_; }
    VkPipelineLayout getWavePipelineLayout() const { return waveLayout_; }
    VkPipelineLayout getTextPipelineLayout() const { return textLayout_; }
    VkDescriptorSetLayout getTextDescLayout() const { return textDescLayout_; }

private:
    bool createShaders();
    bool createLayouts();
    bool createPipelines(VkRenderPass renderPass, uint32_t subpass, VkSampleCountFlagBits samples);

    VkContext* context_ = nullptr;

    // Shader modules
    VkShaderModule flatVertShader_ = VK_NULL_HANDLE;
    VkShaderModule flatFragShader_ = VK_NULL_HANDLE;
    VkShaderModule waveVertShader_ = VK_NULL_HANDLE;
    VkShaderModule waveFragShader_ = VK_NULL_HANDLE;
    VkShaderModule textVertShader_ = VK_NULL_HANDLE;
    VkShaderModule textFragShader_ = VK_NULL_HANDLE;

    // Layouts
    VkPipelineLayout flatLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout waveLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout textLayout_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout textDescLayout_ = VK_NULL_HANDLE;

    // Pipelines
    VkPipeline flatTrianglesPipeline_ = VK_NULL_HANDLE;
    VkPipeline flatLinesPipeline_ = VK_NULL_HANDLE;
    VkPipeline waveLinesPipeline_ = VK_NULL_HANDLE;
    VkPipeline textPipeline_ = VK_NULL_HANDLE;
};

} // namespace silicon::vis::vk
