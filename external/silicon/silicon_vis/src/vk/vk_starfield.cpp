#include "vk_starfield.h"
#include "shaders/vk_spv_shaders.h"
#include <algorithm>
#include <cstring>

namespace silicon::vis::vk {

namespace {

uint32_t findStarMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter, VkMemoryPropertyFlags properties) {
    const auto& table = VkLoader::table();
    VkPhysicalDeviceMemoryProperties memProperties{};
    table.vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);
    for (uint32_t i = 0; i < memProperties.memoryTypeCount; ++i) {
        if ((typeFilter & (1 << i)) && (memProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    return 0xFFFFFFFF;
}

VkShaderModule createStarShader(VkDevice device, const uint32_t* code, size_t sizeBytes) {
    const auto& table = VkLoader::table();
    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = sizeBytes;
    createInfo.pCode = code;
    VkShaderModule module = VK_NULL_HANDLE;
    if (table.vkCreateShaderModule(device, &createInfo, nullptr, &module) != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }
    return module;
}

void unpackStarColor(uint32_t argb, float brightness, float* outRgb) {
    const float b = std::clamp(brightness, 0.0f, 2.5f);
    outRgb[0] = std::min(((argb >> 16) & 0xFF) / 255.0f * b, 2.5f);
    outRgb[1] = std::min(((argb >> 8) & 0xFF) / 255.0f * b, 2.5f);
    outRgb[2] = std::min((argb & 0xFF) / 255.0f * b, 2.5f);
}

} // namespace

VkStarfieldRenderer::~VkStarfieldRenderer() {
    release();
}

bool VkStarfieldRenderer::init(VkContext* context) {
    release();
    if (!context || !context->isInitialized()) return false;
    context_ = context;

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    VkPhysicalDeviceProperties props{};
    table.vkGetPhysicalDeviceProperties(context_->getPhysicalDevice(), &props);
    maxPointSize_ = std::max(1.0f, props.limits.pointSizeRange[1]);

    VkSamplerCreateInfo samplerInfo{};
    samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.maxAnisotropy = 1.0f;
    samplerInfo.minLod = 0.0f;
    samplerInfo.maxLod = 0.0f;
    if (table.vkCreateSampler(device, &samplerInfo, nullptr, &sampler_) != VK_SUCCESS) {
        release();
        return false;
    }

    if (!createDescriptorPool()) {
        release();
        return false;
    }

    using namespace shaders;
    blitVertShader_ = createStarShader(device, kStarBlitVertSpv, kStarBlitVertSpvSize);
    flatVertShader_ = createStarShader(device, kFlatVertSpv, kFlatVertSpvSize);
    flatFragShader_ = createStarShader(device, kFlatFragSpv, kFlatFragSpvSize);
    pointVertShader_ = createStarShader(device, kStarPointVertSpv, kStarPointVertSpvSize);
    pointFragShader_ = createStarShader(device, kStarPointFragSpv, kStarPointFragSpvSize);
    fadeFragShader_ = createStarShader(device, kStarFadeFragSpv, kStarFadeFragSpvSize);
    downFragShader_ = createStarShader(device, kStarDownFragSpv, kStarDownFragSpvSize);
    kawaseFragShader_ = createStarShader(device, kStarKawaseFragSpv, kStarKawaseFragSpvSize);
    if (!blitVertShader_ || !flatVertShader_ || !flatFragShader_ ||
        !pointVertShader_ || !pointFragShader_ || !fadeFragShader_ ||
        !downFragShader_ || !kawaseFragShader_) {
        release();
        return false;
    }

    VkDescriptorSetLayoutBinding samplerBinding{};
    samplerBinding.binding = 0;
    samplerBinding.descriptorCount = 1;
    samplerBinding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    samplerBinding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo descLayoutInfo{};
    descLayoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    descLayoutInfo.bindingCount = 1;
    descLayoutInfo.pBindings = &samplerBinding;
    if (table.vkCreateDescriptorSetLayout(device, &descLayoutInfo, nullptr, &descLayout_) != VK_SUCCESS) {
        release();
        return false;
    }

    auto makeLayout = [&](size_t pushSize, VkPipelineLayout& out) {
        VkPushConstantRange range{};
        range.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        range.offset = 0;
        range.size = static_cast<uint32_t>(pushSize);
        VkPipelineLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &range;
        return table.vkCreatePipelineLayout(device, &layoutInfo, nullptr, &out) == VK_SUCCESS;
    };
    if (!makeLayout(sizeof(PushConstantStarPoint), pointLayout_) ||
        !makeLayout(sizeof(PushConstantFlat), fadeLayout_)) {
        release();
        return false;
    }

    auto makeTexLayout = [&](size_t pushSize, VkPipelineLayout& out) {
        VkPushConstantRange range{};
        range.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
        range.offset = 0;
        range.size = static_cast<uint32_t>(pushSize);
        VkPipelineLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        layoutInfo.setLayoutCount = 1;
        layoutInfo.pSetLayouts = &descLayout_;
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &range;
        return table.vkCreatePipelineLayout(device, &layoutInfo, nullptr, &out) == VK_SUCCESS;
    };
    // Blit layout kept for a shared fullscreen-triangle vertex input.
    if (!makeTexLayout(sizeof(PushConstantStarBlit), blitLayout_) ||
        !makeTexLayout(sizeof(PushConstantStarDown), downLayout_) ||
        !makeTexLayout(sizeof(PushConstantStarKawase), kawaseLayout_)) {
        release();
        return false;
    }

    if (!createPasses()) {
        release();
        return false;
    }
    return true;
}

void VkStarfieldRenderer::release() {
    if (!context_) return;
    releaseTargets();
    destroyPasses();
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    auto destroyLayout = [&](VkPipelineLayout& l) {
        if (l != VK_NULL_HANDLE) { table.vkDestroyPipelineLayout(device, l, nullptr); l = VK_NULL_HANDLE; }
    };
    auto destroyShader = [&](VkShaderModule& s) {
        if (s != VK_NULL_HANDLE) { table.vkDestroyShaderModule(device, s, nullptr); s = VK_NULL_HANDLE; }
    };
    destroyLayout(pointLayout_);
    destroyLayout(fadeLayout_);
    destroyLayout(blitLayout_);
    destroyLayout(downLayout_);
    destroyLayout(kawaseLayout_);
    if (descLayout_ != VK_NULL_HANDLE) {
        table.vkDestroyDescriptorSetLayout(device, descLayout_, nullptr);
        descLayout_ = VK_NULL_HANDLE;
    }
    destroyShader(blitVertShader_);
    destroyShader(flatVertShader_);
    destroyShader(flatFragShader_);
    destroyShader(pointVertShader_);
    destroyShader(pointFragShader_);
    destroyShader(fadeFragShader_);
    destroyShader(downFragShader_);
    destroyShader(kawaseFragShader_);
    if (descriptorPool_ != VK_NULL_HANDLE) {
        table.vkDestroyDescriptorPool(device, descriptorPool_, nullptr);
        descriptorPool_ = VK_NULL_HANDLE;
    }
    if (sampler_ != VK_NULL_HANDLE) {
        table.vkDestroySampler(device, sampler_, nullptr);
        sampler_ = VK_NULL_HANDLE;
    }
    pointScratch_.clear();
    pointScratch_.shrink_to_fit();
    maxPointSize_ = 1.0f;
    context_ = nullptr;
}

bool VkStarfieldRenderer::createPasses() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    auto makePass = [&](VkAttachmentLoadOp loadOp, VkImageLayout initial, VkRenderPass& out) {
        VkAttachmentDescription color{};
        color.format = VK_FORMAT_R8G8B8A8_UNORM;
        color.samples = VK_SAMPLE_COUNT_1_BIT;
        color.loadOp = loadOp;
        color.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        color.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        color.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        color.initialLayout = initial;
        color.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        VkAttachmentReference ref{};
        ref.attachment = 0;
        ref.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &ref;
        VkRenderPassCreateInfo passInfo{};
        passInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        passInfo.attachmentCount = 1;
        passInfo.pAttachments = &color;
        passInfo.subpassCount = 1;
        passInfo.pSubpasses = &subpass;
        return table.vkCreateRenderPass(device, &passInfo, nullptr, &out) == VK_SUCCESS;
    };
    // First frame after a resize clears undefined contents; later frames
    // preserve trails with LOAD.
    if (!makePass(VK_ATTACHMENT_LOAD_OP_CLEAR, VK_IMAGE_LAYOUT_UNDEFINED, trailClearPass_)) return false;
    if (!makePass(VK_ATTACHMENT_LOAD_OP_LOAD, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, trailLoadPass_)) return false;

    VkAttachmentDescription bloomColor{};
    bloomColor.format = VK_FORMAT_R8G8B8A8_UNORM;
    bloomColor.samples = VK_SAMPLE_COUNT_1_BIT;
    bloomColor.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    bloomColor.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    bloomColor.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    bloomColor.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    bloomColor.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    bloomColor.finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    VkAttachmentReference bloomRef{};
    bloomRef.attachment = 0;
    bloomRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkSubpassDescription bloomSubpass{};
    bloomSubpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    bloomSubpass.colorAttachmentCount = 1;
    bloomSubpass.pColorAttachments = &bloomRef;
    VkRenderPassCreateInfo bloomPassInfo{};
    bloomPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    bloomPassInfo.attachmentCount = 1;
    bloomPassInfo.pAttachments = &bloomColor;
    bloomPassInfo.subpassCount = 1;
    bloomPassInfo.pSubpasses = &bloomSubpass;
    return table.vkCreateRenderPass(device, &bloomPassInfo, nullptr, &bloomPass_) == VK_SUCCESS;
}

void VkStarfieldRenderer::destroyPasses() {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    for (VkRenderPass* p : {&trailClearPass_, &trailLoadPass_, &bloomPass_}) {
        if (*p != VK_NULL_HANDLE) { table.vkDestroyRenderPass(device, *p, nullptr); *p = VK_NULL_HANDLE; }
    }
}

bool VkStarfieldRenderer::createTarget(Target& target, uint32_t width, uint32_t height) {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    VkPhysicalDevice physDev = context_->getPhysicalDevice();

    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent.width = width;
    imageInfo.extent.height = height;
    imageInfo.extent.depth = 1;
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (table.vkCreateImage(device, &imageInfo, nullptr, &target.image) != VK_SUCCESS) return false;

    VkMemoryRequirements memReqs{};
    table.vkGetImageMemoryRequirements(device, target.image, &memReqs);
    uint32_t memType = findStarMemoryType(physDev, memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (memType == 0xFFFFFFFF) return false;
    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = memType;
    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &target.memory) != VK_SUCCESS) return false;
    table.vkBindImageMemory(device, target.image, target.memory, 0);

    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = target.image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.layerCount = 1;
    if (table.vkCreateImageView(device, &viewInfo, nullptr, &target.view) != VK_SUCCESS) return false;

    target.width = width;
    target.height = height;
    return true;
}

void VkStarfieldRenderer::destroyTarget(Target& target) {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    if (target.framebuffer != VK_NULL_HANDLE) {
        table.vkDestroyFramebuffer(device, target.framebuffer, nullptr);
        target.framebuffer = VK_NULL_HANDLE;
    }
    if (target.view != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, target.view, nullptr);
        target.view = VK_NULL_HANDLE;
    }
    if (target.image != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, target.image, nullptr);
        target.image = VK_NULL_HANDLE;
    }
    if (target.memory != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, target.memory, nullptr);
        target.memory = VK_NULL_HANDLE;
    }
    target.set = VK_NULL_HANDLE;
    target.width = 0;
    target.height = 0;
}

void VkStarfieldRenderer::releaseTargets() {
    destroyOffscreenPipelines();
    for (Target* t : {&trail_, &bloomA_, &bloomB_, &bloomC_, &bloomE_, &bloomF_}) {
        destroyTarget(*t);
    }
    width_ = 0;
    height_ = 0;
    targetsReady_ = false;
    trailNeedsClear_ = true;
    trailReadable_ = false;
}

bool VkStarfieldRenderer::ensureTargets(uint32_t width, uint32_t height) {
    if (targetsReady_ && width_ == width && height_ == height) return true;
    releaseTargets();
    if (width == 0 || height == 0) return false;

    const uint32_t bw = std::max(1u, width / 2);
    const uint32_t bh = std::max(1u, height / 2);
    const uint32_t qw = std::max(1u, bw / 2);
    const uint32_t qh = std::max(1u, bh / 2);
    const uint32_t ew = std::max(1u, qw / 2);
    const uint32_t eh = std::max(1u, qh / 2);

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    auto makeFramebuffer = [&](Target& target, VkRenderPass pass) {
        VkFramebufferCreateInfo fbInfo{};
        fbInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fbInfo.renderPass = pass;
        fbInfo.attachmentCount = 1;
        fbInfo.pAttachments = &target.view;
        fbInfo.width = target.width;
        fbInfo.height = target.height;
        fbInfo.layers = 1;
        return table.vkCreateFramebuffer(device, &fbInfo, nullptr, &target.framebuffer) == VK_SUCCESS;
    };

    if (!createTarget(trail_, width, height) || !makeFramebuffer(trail_, trailLoadPass_) ||
        !createTarget(bloomA_, bw, bh) || !makeFramebuffer(bloomA_, bloomPass_) ||
        !createTarget(bloomB_, bw, bh) || !makeFramebuffer(bloomB_, bloomPass_) ||
        !createTarget(bloomC_, qw, qh) || !makeFramebuffer(bloomC_, bloomPass_) ||
        !createTarget(bloomE_, ew, eh) || !makeFramebuffer(bloomE_, bloomPass_) ||
        !createTarget(bloomF_, ew, eh) || !makeFramebuffer(bloomF_, bloomPass_)) {
        releaseTargets();
        return false;
    }

    if (!createOffscreenPipelines() || !allocateSets()) {
        releaseTargets();
        return false;
    }

    width_ = width;
    height_ = height;
    targetsReady_ = true;
    trailNeedsClear_ = true;
    trailReadable_ = false;
    return true;
}

bool VkStarfieldRenderer::createDescriptorPool() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    if (descriptorPool_ != VK_NULL_HANDLE) {
        table.vkDestroyDescriptorPool(device, descriptorPool_, nullptr);
        descriptorPool_ = VK_NULL_HANDLE;
    }
    VkDescriptorPoolSize poolSize{};
    poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    poolSize.descriptorCount = 6;
    VkDescriptorPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    poolInfo.maxSets = 6;
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    return table.vkCreateDescriptorPool(device, &poolInfo, nullptr, &descriptorPool_) == VK_SUCCESS;
}

bool VkStarfieldRenderer::allocateSets() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    // No pool reset in the loader; rebuild the pool per target generation.
    if (!createDescriptorPool()) return false;

    Target* targets[6] = {&trail_, &bloomA_, &bloomB_, &bloomC_, &bloomE_, &bloomF_};
    VkDescriptorSetLayout layouts[6] = {descLayout_, descLayout_, descLayout_, descLayout_, descLayout_, descLayout_};
    VkDescriptorSetAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocInfo.descriptorPool = descriptorPool_;
    allocInfo.descriptorSetCount = 6;
    allocInfo.pSetLayouts = layouts;
    VkDescriptorSet sets[6]{};
    if (table.vkAllocateDescriptorSets(device, &allocInfo, sets) != VK_SUCCESS) return false;

    for (int i = 0; i < 6; ++i) {
        VkDescriptorImageInfo imageInfo{};
        imageInfo.sampler = sampler_;
        imageInfo.imageView = targets[i]->view;
        imageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        VkWriteDescriptorSet write{};
        write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        write.dstSet = sets[i];
        write.dstBinding = 0;
        write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.pImageInfo = &imageInfo;
        table.vkUpdateDescriptorSets(device, 1, &write, 0, nullptr);
        targets[i]->set = sets[i];
    }
    return true;
}

bool VkStarfieldRenderer::createOffscreenPipelines() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    VkPipelineViewportStateCreateInfo viewportState{};
    viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.scissorCount = 1;

    VkPipelineRasterizationStateCreateInfo rasterizer{};
    rasterizer.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
    rasterizer.cullMode = VK_CULL_MODE_NONE;
    rasterizer.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    rasterizer.lineWidth = 1.0f;

    VkPipelineMultisampleStateCreateInfo multisampling{};
    multisampling.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisampling.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    auto blendState = [&](bool enable, VkBlendFactor src, VkBlendFactor dst, VkPipelineColorBlendStateCreateInfo& out,
                          VkPipelineColorBlendAttachmentState& att) {
        att.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                             VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        att.blendEnable = enable ? VK_TRUE : VK_FALSE;
        att.srcColorBlendFactor = src;
        att.dstColorBlendFactor = dst;
        att.colorBlendOp = VK_BLEND_OP_ADD;
        att.srcAlphaBlendFactor = src;
        att.dstAlphaBlendFactor = dst;
        att.alphaBlendOp = VK_BLEND_OP_ADD;
        out.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
        out.attachmentCount = 1;
        out.pAttachments = &att;
    };
    VkPipelineColorBlendAttachmentState standardAtt{};
    VkPipelineColorBlendStateCreateInfo standardBlend{};
    blendState(true, VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, standardBlend, standardAtt);
    VkPipelineColorBlendAttachmentState fadeAtt{};
    VkPipelineColorBlendStateCreateInfo fadeBlend{};
    blendState(true, VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, fadeBlend, fadeAtt);
    VkPipelineColorBlendAttachmentState opaqueAtt{};
    VkPipelineColorBlendStateCreateInfo opaqueBlend{};
    blendState(false, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO, opaqueBlend, opaqueAtt);

    VkDynamicState dynStates[2] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamicState{};
    dynamicState.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
    dynamicState.dynamicStateCount = 2;
    dynamicState.pDynamicStates = dynStates;

    VkGraphicsPipelineCreateInfo base{};
    base.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    base.pViewportState = &viewportState;
    base.pRasterizationState = &rasterizer;
    base.pMultisampleState = &multisampling;
    base.pDynamicState = &dynamicState;

    auto stages = [&](VkShaderModule vs, VkShaderModule fs, VkPipelineShaderStageCreateInfo* out) {
        out[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        out[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        out[0].module = vs;
        out[0].pName = "main";
        out[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        out[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        out[1].module = fs;
        out[1].pName = "main";
    };

    // Star points: pos vec2 + size + alpha, POINT_LIST topology.
    VkVertexInputBindingDescription pointBinding{};
    pointBinding.binding = 0;
    pointBinding.stride = 4 * sizeof(float);
    pointBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
    VkVertexInputAttributeDescription pointAttrs[3]{};
    pointAttrs[0].location = 0;
    pointAttrs[0].format = VK_FORMAT_R32G32_SFLOAT;
    pointAttrs[1].location = 1;
    pointAttrs[1].binding = 0;
    pointAttrs[1].format = VK_FORMAT_R32_SFLOAT;
    pointAttrs[1].offset = 2 * sizeof(float);
    pointAttrs[2].location = 2;
    pointAttrs[2].binding = 0;
    pointAttrs[2].format = VK_FORMAT_R32_SFLOAT;
    pointAttrs[2].offset = 3 * sizeof(float);
    VkPipelineVertexInputStateCreateInfo pointInput{};
    pointInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    pointInput.vertexBindingDescriptionCount = 1;
    pointInput.pVertexBindingDescriptions = &pointBinding;
    pointInput.vertexAttributeDescriptionCount = 3;
    pointInput.pVertexAttributeDescriptions = pointAttrs;
    VkPipelineInputAssemblyStateCreateInfo pointAssembly{};
    pointAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    pointAssembly.topology = VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
    VkPipelineShaderStageCreateInfo pointStages[2]{};
    stages(pointVertShader_, pointFragShader_, pointStages);
    VkGraphicsPipelineCreateInfo pointInfo = base;
    pointInfo.stageCount = 2;
    pointInfo.pStages = pointStages;
    pointInfo.pVertexInputState = &pointInput;
    pointInfo.pInputAssemblyState = &pointAssembly;
    pointInfo.pColorBlendState = &standardBlend;
    pointInfo.layout = pointLayout_;
    pointInfo.renderPass = trailLoadPass_;
    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pointInfo, nullptr, &pointPipeline_) != VK_SUCCESS) {
        return false;
    }

    // Pixel-space vec2 vertex layout shared by lines and fade.
    VkVertexInputBindingDescription flatBinding{};
    flatBinding.binding = 0;
    flatBinding.stride = 2 * sizeof(float);
    flatBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
    VkVertexInputAttributeDescription flatAttr{};
    flatAttr.format = VK_FORMAT_R32G32_SFLOAT;
    VkPipelineVertexInputStateCreateInfo flatInput{};
    flatInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    flatInput.vertexBindingDescriptionCount = 1;
    flatInput.pVertexBindingDescriptions = &flatBinding;
    flatInput.vertexAttributeDescriptionCount = 1;
    flatInput.pVertexAttributeDescriptions = &flatAttr;
    VkPipelineInputAssemblyStateCreateInfo lineAssembly{};
    lineAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    lineAssembly.topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;

    // Streak lines: pixel-space segments, LINE_LIST topology.
    VkPipelineShaderStageCreateInfo lineStages[2]{};
    stages(flatVertShader_, flatFragShader_, lineStages);
    VkGraphicsPipelineCreateInfo lineInfo = base;
    lineInfo.stageCount = 2;
    lineInfo.pStages = lineStages;
    lineInfo.pVertexInputState = &flatInput;
    lineInfo.pInputAssemblyState = &lineAssembly;
    lineInfo.pColorBlendState = &standardBlend;
    lineInfo.layout = fadeLayout_;
    lineInfo.renderPass = trailLoadPass_;
    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &lineInfo, nullptr, &linePipeline_) != VK_SUCCESS) {
        return false;
    }

    // Trail fade: fullscreen fade-to-black quad with inverted blending.
    VkPipelineInputAssemblyStateCreateInfo triAssembly{};
    triAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    triAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    VkPipelineShaderStageCreateInfo fadeStages[2]{};
    stages(flatVertShader_, fadeFragShader_, fadeStages);
    VkGraphicsPipelineCreateInfo fadeInfo = lineInfo;
    fadeInfo.pStages = fadeStages;
    fadeInfo.pInputAssemblyState = &triAssembly;
    fadeInfo.pColorBlendState = &fadeBlend;
    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &fadeInfo, nullptr, &fadePipeline_) != VK_SUCCESS) {
        return false;
    }

    // NDC-space vec2 vertex layout shared by downsample and Kawase.
    VkVertexInputBindingDescription blitBinding{};
    blitBinding.binding = 0;
    blitBinding.stride = 2 * sizeof(float);
    blitBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
    VkVertexInputAttributeDescription blitAttr{};
    blitAttr.format = VK_FORMAT_R32G32_SFLOAT;
    VkPipelineVertexInputStateCreateInfo blitInput{};
    blitInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    blitInput.vertexBindingDescriptionCount = 1;
    blitInput.pVertexBindingDescriptions = &blitBinding;
    blitInput.vertexAttributeDescriptionCount = 1;
    blitInput.pVertexAttributeDescriptions = &blitAttr;

    VkPipelineShaderStageCreateInfo downStages[2]{};
    stages(blitVertShader_, downFragShader_, downStages);
    VkGraphicsPipelineCreateInfo downInfo = base;
    downInfo.stageCount = 2;
    downInfo.pStages = downStages;
    downInfo.pVertexInputState = &blitInput;
    downInfo.pInputAssemblyState = &triAssembly;
    downInfo.pColorBlendState = &opaqueBlend;
    downInfo.layout = downLayout_;
    downInfo.renderPass = bloomPass_;
    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &downInfo, nullptr, &downPipeline_) != VK_SUCCESS) {
        return false;
    }

    VkPipelineShaderStageCreateInfo kawaseStages[2]{};
    stages(blitVertShader_, kawaseFragShader_, kawaseStages);
    VkGraphicsPipelineCreateInfo kawaseInfo = downInfo;
    kawaseInfo.pStages = kawaseStages;
    kawaseInfo.layout = kawaseLayout_;
    return table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &kawaseInfo, nullptr, &kawasePipeline_) == VK_SUCCESS;
}

void VkStarfieldRenderer::destroyOffscreenPipelines() {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    for (VkPipeline* p : {&pointPipeline_, &linePipeline_, &fadePipeline_, &downPipeline_, &kawasePipeline_}) {
        if (*p != VK_NULL_HANDLE) { table.vkDestroyPipeline(device, *p, nullptr); *p = VK_NULL_HANDLE; }
    }
}

void VkStarfieldRenderer::barrier(VkCommandBuffer cmd, VkImage image,
             VkImageLayout oldLayout, VkImageLayout newLayout,
             VkAccessFlags srcAccess, VkAccessFlags dstAccess,
             VkPipelineStageFlags srcStage, VkPipelineStageFlags dstStage) {
    const auto& table = VkLoader::table();
    VkImageMemoryBarrier b{};
    b.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    b.oldLayout = oldLayout;
    b.newLayout = newLayout;
    b.srcAccessMask = srcAccess;
    b.dstAccessMask = dstAccess;
    b.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.image = image;
    b.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    b.subresourceRange.levelCount = 1;
    b.subresourceRange.layerCount = 1;
    table.vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, 0, nullptr, 0, nullptr, 1, &b);
}

void VkStarfieldRenderer::readable(VkCommandBuffer cmd, Target& target) {
    barrier(cmd, target.image,
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
}

void VkStarfieldRenderer::writable(VkCommandBuffer cmd, Target& target) {
    barrier(cmd, target.image,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
}

void VkStarfieldRenderer::setViewport(VkCommandBuffer cmd, uint32_t width, uint32_t height) {
    const auto& table = VkLoader::table();
    VkViewport viewport{};
    viewport.width = static_cast<float>(width);
    viewport.height = static_cast<float>(height);
    viewport.maxDepth = 1.0f;
    table.vkCmdSetViewport(cmd, 0, 1, &viewport);
    VkRect2D scissor{};
    scissor.extent = {width, height};
    table.vkCmdSetScissor(cmd, 0, 1, &scissor);
}

void VkStarfieldRenderer::beginTargetPass(VkCommandBuffer cmd, VkRenderPass pass,
                                          Target& target, const float* clearColor) {
    const auto& table = VkLoader::table();
    VkRenderPassBeginInfo passInfo{};
    passInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    passInfo.renderPass = pass;
    passInfo.framebuffer = target.framebuffer;
    passInfo.renderArea.extent = {target.width, target.height};
    VkClearValue clear{};
    if (clearColor) {
        clear.color.float32[0] = clearColor[0];
        clear.color.float32[1] = clearColor[1];
        clear.color.float32[2] = clearColor[2];
        clear.color.float32[3] = clearColor[3];
        passInfo.clearValueCount = 1;
        passInfo.pClearValues = &clear;
    }
    table.vkCmdBeginRenderPass(cmd, &passInfo, VK_SUBPASS_CONTENTS_INLINE);
    setViewport(cmd, target.width, target.height);
}

void VkStarfieldRenderer::drawFullscreenTri(VkCommandBuffer cmd, VkDynamicVertexBuffer* dynBuffer) {
    static const float tri[6] = {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f};
    size_t offset = dynBuffer->allocate(tri, sizeof(tri));
    if (offset == static_cast<size_t>(-1)) return;
    const auto& table = VkLoader::table();
    VkBuffer buffer = dynBuffer->getBuffer();
    VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
    table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
    table.vkCmdDraw(cmd, 3, 1, 0, 0);
}

bool VkStarfieldRenderer::renderTrails(VkCommandBuffer cmd, VkDynamicVertexBuffer* dynBuffer,
                  const StarVkFrame& frame, uint32_t width, uint32_t height) {
    if (!cmd || !dynBuffer || width == 0 || height == 0) return false;
    if (!ensureTargets(width, height)) return false;
    const auto& table = VkLoader::table();
    VkBuffer buffer = dynBuffer->getBuffer();

    if (!trailNeedsClear_) {
        writable(cmd, trail_);
    }
    trailReadable_ = false;

    // Trail accumulation pass: fade, streaks, then points.
    static const float transparent[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    beginTargetPass(cmd, trailNeedsClear_ ? trailClearPass_ : trailLoadPass_, trail_,
                    trailNeedsClear_ ? transparent : nullptr);

    if (frame.fadeAlpha > 0.0f) {
        const float w = static_cast<float>(width);
        const float h = static_cast<float>(height);
        const float tri[6] = {0.0f, 0.0f, 3.0f * w, 0.0f, 0.0f, 3.0f * h};
        size_t offset = dynBuffer->allocate(tri, sizeof(tri));
        if (offset != static_cast<size_t>(-1)) {
            table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, fadePipeline_);
            PushConstantFlat pc{};
            pc.color[3] = frame.fadeAlpha;
            pc.resolution[0] = w;
            pc.resolution[1] = h;
            table.vkCmdPushConstants(cmd, fadeLayout_,
                                     VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                                     0, sizeof(pc), &pc);
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, 3, 1, 0, 0);
        }
    }

    if (frame.globalAlpha > 0.0f && frame.lineCount > 0 && frame.lineVerts) {
        size_t sizeBytes = static_cast<size_t>(frame.lineCount) * 4 * sizeof(float);
        size_t offset = dynBuffer->allocate(frame.lineVerts, sizeBytes);
        if (offset != static_cast<size_t>(-1)) {
            table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, linePipeline_);
            PushConstantFlat pc{};
            unpackStarColor(frame.starColorArgb, 1.0f, pc.color);
            pc.color[3] = 0.55f * std::min(frame.brightness, 1.5f) * frame.globalAlpha;
            pc.resolution[0] = static_cast<float>(width);
            pc.resolution[1] = static_cast<float>(height);
            table.vkCmdPushConstants(cmd, fadeLayout_,
                                     VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                                     0, sizeof(pc), &pc);
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, static_cast<uint32_t>(frame.lineCount) * 2, 1, 0, 0);
        }
    }

    if (frame.globalAlpha > 0.0f && frame.pointCount > 0 && frame.pos && frame.size && frame.alpha) {
        const size_t n = static_cast<size_t>(frame.pointCount);
        if (pointScratch_.size() < n * 4) pointScratch_.resize(n * 4);
        for (size_t i = 0; i < n; ++i) {
            pointScratch_[i * 4] = frame.pos[i * 2];
            pointScratch_[i * 4 + 1] = frame.pos[i * 2 + 1];
            pointScratch_[i * 4 + 2] = frame.size[i];
            pointScratch_[i * 4 + 3] = frame.alpha[i];
        }
        size_t offset = dynBuffer->allocate(pointScratch_.data(), n * 4 * sizeof(float));
        if (offset != static_cast<size_t>(-1)) {
            table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pointPipeline_);
            PushConstantStarPoint pc{};
            unpackStarColor(frame.starColorArgb, frame.brightness, pc.color);
            pc.soft = frame.softness;
            pc.globalAlpha = frame.globalAlpha;
            pc.square = frame.squareStars ? 1.0f : 0.0f;
            pc.resolution[0] = static_cast<float>(width);
            pc.resolution[1] = static_cast<float>(height);
            table.vkCmdPushConstants(cmd, pointLayout_,
                                     VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                                     0, sizeof(pc), &pc);
            VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);
            table.vkCmdDraw(cmd, static_cast<uint32_t>(frame.pointCount), 1, 0, 0);
        }
    }
    table.vkCmdEndRenderPass(cmd);

    if (frame.wantBloom && frame.bloomStrength > 0.0f) {
        readable(cmd, trail_);
        trailReadable_ = true;

        auto kawaseTo = [&](Target& dst, VkDescriptorSet srcSet, float px, float py) {
            beginTargetPass(cmd, bloomPass_, dst, nullptr);
            table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, kawasePipeline_);
            table.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                          kawaseLayout_, 0, 1, &srcSet, 0, nullptr);
            PushConstantStarKawase pc{};
            pc.px[0] = px;
            pc.px[1] = py;
            table.vkCmdPushConstants(cmd, kawaseLayout_, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
            drawFullscreenTri(cmd, dynBuffer);
            table.vkCmdEndRenderPass(cmd);
            readable(cmd, dst);
        };

        // Bright-pass downsample into A.
        beginTargetPass(cmd, bloomPass_, bloomA_, nullptr);
        table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, downPipeline_);
        table.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                      downLayout_, 0, 1, &trail_.set, 0, nullptr);
        PushConstantStarDown downPc{};
        downPc.thresh = 0.08f;
        table.vkCmdPushConstants(cmd, downLayout_, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(downPc), &downPc);
        drawFullscreenTri(cmd, dynBuffer);
        table.vkCmdEndRenderPass(cmd);
        readable(cmd, bloomA_);

        // Dual-Kawase pyramid: half -> quarter -> eighth, two smoothing
        // iterations at the eighth level, wide halo back up to half.
        const float k = frame.glowK;
        const float bw = static_cast<float>(bloomA_.width);
        const float bh = static_cast<float>(bloomA_.height);
        const float qw = static_cast<float>(bloomC_.width);
        const float qh = static_cast<float>(bloomC_.height);
        const float ew = static_cast<float>(bloomE_.width);
        const float eh = static_cast<float>(bloomE_.height);
        kawaseTo(bloomC_, bloomA_.set, k / bw, k / bh);
        kawaseTo(bloomE_, bloomC_.set, k / qw, k / qh);
        kawaseTo(bloomF_, bloomE_.set, k / ew, k / eh);
        kawaseTo(bloomE_, bloomF_.set, k / ew, k / eh);
        kawaseTo(bloomB_, bloomE_.set, k / ew, k / eh);
    }

    if (!trailReadable_) {
        readable(cmd, trail_);
        trailReadable_ = true;
    }
    trailNeedsClear_ = false;
    return true;
}

void VkStarfieldRenderer::composite(VkCommandBuffer cmd, VkPrimitivePipelines* prims,
               VkDynamicVertexBuffer* dynBuffer, const StarVkFrame& frame) {
    if (!cmd || !prims || !dynBuffer || frame.globalAlpha <= 0.0f) return;
    static const float tri[6] = {-1.0f, -1.0f, 3.0f, -1.0f, -1.0f, 3.0f};
    size_t offset = dynBuffer->allocate(tri, sizeof(tri));
    if (offset == static_cast<size_t>(-1)) return;
    const auto& table = VkLoader::table();
    VkBuffer buffer = dynBuffer->getBuffer();
    VkDeviceSize bufOffset = static_cast<VkDeviceSize>(offset);
    table.vkCmdBindVertexBuffers(cmd, 0, 1, &buffer, &bufOffset);

    prims->bindStarBlit(cmd, 0.0f, 0.0f, frame.globalAlpha, trail_.set);
    table.vkCmdDraw(cmd, 3, 1, 0, 0);

    if (frame.wantBloom && frame.bloomStrength > 0.0f) {
        prims->bindStarAdd(cmd, 0.0f, 0.0f, std::min(2.0f, frame.bloomStrength * 0.4f) * frame.globalAlpha, bloomA_.set);
        table.vkCmdDraw(cmd, 3, 1, 0, 0);
        prims->bindStarAdd(cmd, 0.0f, 0.0f, frame.bloomStrength * frame.globalAlpha, bloomB_.set);
        table.vkCmdDraw(cmd, 3, 1, 0, 0);
    }
}

} // namespace silicon::vis::vk
