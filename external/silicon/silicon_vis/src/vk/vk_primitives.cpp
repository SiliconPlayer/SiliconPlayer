#include "vk_primitives.h"
#include "shaders/vk_spv_shaders.h"
#include <cstring>
#include <algorithm>

namespace silicon::vis::vk {

namespace {

uint32_t findMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter, VkMemoryPropertyFlags properties) {
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

void unpackColor(uint32_t argb, float* outColor) {
    outColor[3] = ((argb >> 24) & 0xFF) / 255.0f;
    outColor[0] = ((argb >> 16) & 0xFF) / 255.0f;
    outColor[1] = ((argb >> 8) & 0xFF) / 255.0f;
    outColor[2] = (argb & 0xFF) / 255.0f;
}

VkShaderModule createShaderModule(VkDevice device, const uint32_t* code, size_t sizeBytes) {
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

} // namespace

VkDynamicVertexBuffer::~VkDynamicVertexBuffer() {
    release();
}

bool VkDynamicVertexBuffer::init(VkContext* context, size_t capacityBytes) {
    release();
    context_ = context;
    capacityBytes_ = capacityBytes;

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = capacityBytes;
    bufferInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (table.vkCreateBuffer(device, &bufferInfo, nullptr, &buffer_) != VK_SUCCESS) {
        return false;
    }

    VkMemoryRequirements memReqs{};
    table.vkGetBufferMemoryRequirements(device, buffer_, &memReqs);

    uint32_t memTypeIndex = findMemoryType(
        context_->getPhysicalDevice(),
        memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
    );
    if (memTypeIndex == 0xFFFFFFFF) {
        table.vkDestroyBuffer(device, buffer_, nullptr);
        buffer_ = VK_NULL_HANDLE;
        return false;
    }

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = memTypeIndex;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &memory_) != VK_SUCCESS) {
        table.vkDestroyBuffer(device, buffer_, nullptr);
        buffer_ = VK_NULL_HANDLE;
        return false;
    }

    table.vkBindBufferMemory(device, buffer_, memory_, 0);

    void* ptr = nullptr;
    if (table.vkMapMemory(device, memory_, 0, capacityBytes, 0, &ptr) != VK_SUCCESS) {
        release();
        return false;
    }
    mappedPtr_ = static_cast<uint8_t*>(ptr);
    currentOffset_ = 0;
    return true;
}

void VkDynamicVertexBuffer::release() {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    if (mappedPtr_) {
        table.vkUnmapMemory(device, memory_);
        mappedPtr_ = nullptr;
    }
    if (buffer_ != VK_NULL_HANDLE) {
        table.vkDestroyBuffer(device, buffer_, nullptr);
        buffer_ = VK_NULL_HANDLE;
    }
    if (memory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, memory_, nullptr);
        memory_ = VK_NULL_HANDLE;
    }
    capacityBytes_ = 0;
    currentOffset_ = 0;
    context_ = nullptr;
}

size_t VkDynamicVertexBuffer::allocate(const void* data, size_t sizeBytes) {
    if (!mappedPtr_ || sizeBytes == 0) return static_cast<size_t>(-1);

    // 16-byte alignment
    size_t alignedOffset = (currentOffset_ + 15) & ~static_cast<size_t>(15);
    if (alignedOffset + sizeBytes > capacityBytes_) {
        // Wrapped around or full
        return static_cast<size_t>(-1);
    }

    memcpy(mappedPtr_ + alignedOffset, data, sizeBytes);
    currentOffset_ = alignedOffset + sizeBytes;
    return alignedOffset;
}

void VkDynamicVertexBuffer::reset() {
    currentOffset_ = 0;
}

// -----------------------------------------------------------------------------
// VkPrimitivePipelines
// -----------------------------------------------------------------------------

VkPrimitivePipelines::~VkPrimitivePipelines() {
    release();
}

bool VkPrimitivePipelines::init(VkContext* context, VkRenderPass renderPass, uint32_t subpass, VkSampleCountFlagBits samples) {
    release();
    context_ = context;

    if (!createShaders()) return false;
    if (!createLayouts()) return false;
    if (!createPipelines(renderPass, subpass, samples)) return false;

    return true;
}

void VkPrimitivePipelines::release() {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    auto destroyPipe = [&](VkPipeline& p) {
        if (p != VK_NULL_HANDLE) { table.vkDestroyPipeline(device, p, nullptr); p = VK_NULL_HANDLE; }
    };
    auto destroyLayout = [&](VkPipelineLayout& l) {
        if (l != VK_NULL_HANDLE) { table.vkDestroyPipelineLayout(device, l, nullptr); l = VK_NULL_HANDLE; }
    };
    auto destroyShader = [&](VkShaderModule& s) {
        if (s != VK_NULL_HANDLE) { table.vkDestroyShaderModule(device, s, nullptr); s = VK_NULL_HANDLE; }
    };

    destroyPipe(flatTrianglesPipeline_);
    destroyPipe(flatLinesPipeline_);
    destroyPipe(waveLinesPipeline_);
    destroyPipe(textPipeline_);
    destroyPipe(transitionPipeline_);
    destroyPipe(artworkBgPipeline_);
    destroyPipe(artworkTexPipeline_);
    destroyPipe(contrastPipeline_);

    destroyLayout(flatLayout_);
    destroyLayout(waveLayout_);
    destroyLayout(textLayout_);
    destroyLayout(transitionLayout_);
    destroyLayout(artworkBgLayout_);
    destroyLayout(artworkTexLayout_);
    destroyLayout(contrastLayout_);

    if (textDescLayout_ != VK_NULL_HANDLE) {
        table.vkDestroyDescriptorSetLayout(device, textDescLayout_, nullptr);
        textDescLayout_ = VK_NULL_HANDLE;
    }

    destroyShader(flatVertShader_);
    destroyShader(flatFragShader_);
    destroyShader(waveVertShader_);
    destroyShader(waveFragShader_);
    destroyShader(textVertShader_);
    destroyShader(textFragShader_);
    destroyShader(transitionVertShader_);
    destroyShader(transitionFragShader_);
    destroyShader(artworkBgVertShader_);
    destroyShader(artworkBgFragShader_);
    destroyShader(artworkTexVertShader_);
    destroyShader(artworkTexFragShader_);
    destroyShader(contrastVertShader_);
    destroyShader(contrastFragShader_);

    context_ = nullptr;
}

bool VkPrimitivePipelines::createShaders() {
    VkDevice device = context_->getDevice();
    using namespace shaders;

    flatVertShader_ = createShaderModule(device, kFlatVertSpv, kFlatVertSpvSize);
    flatFragShader_ = createShaderModule(device, kFlatFragSpv, kFlatFragSpvSize);
    waveVertShader_ = createShaderModule(device, kWaveLineVertSpv, kWaveLineVertSpvSize);
    waveFragShader_ = createShaderModule(device, kWaveLineFragSpv, kWaveLineFragSpvSize);
    textVertShader_ = createShaderModule(device, kTextVertSpv, kTextVertSpvSize);
    textFragShader_ = createShaderModule(device, kTextFragSpv, kTextFragSpvSize);
    transitionVertShader_ = createShaderModule(device, kTransitionVertSpv, kTransitionVertSpvSize);
    transitionFragShader_ = createShaderModule(device, kTransitionFragSpv, kTransitionFragSpvSize);
    artworkBgVertShader_ = createShaderModule(device, kArtworkBgVertSpv, kArtworkBgVertSpvSize);
    artworkBgFragShader_ = createShaderModule(device, kArtworkBgFragSpv, kArtworkBgFragSpvSize);
    artworkTexVertShader_ = createShaderModule(device, kArtworkTexVertSpv, kArtworkTexVertSpvSize);
    artworkTexFragShader_ = createShaderModule(device, kArtworkTexFragSpv, kArtworkTexFragSpvSize);
    contrastVertShader_ = createShaderModule(device, kContrastVertSpv, kContrastVertSpvSize);
    contrastFragShader_ = createShaderModule(device, kContrastFragSpv, kContrastFragSpvSize);

    return flatVertShader_ && flatFragShader_ && waveVertShader_ && waveFragShader_ &&
           textVertShader_ && textFragShader_ && transitionVertShader_ && transitionFragShader_ &&
           artworkBgVertShader_ && artworkBgFragShader_ && artworkTexVertShader_ && artworkTexFragShader_ &&
           contrastVertShader_ && contrastFragShader_;
}

bool VkPrimitivePipelines::createLayouts() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    // Flat pipeline layout (Push constants: 24 bytes)
    VkPushConstantRange flatRange{};
    flatRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    flatRange.offset = 0;
    flatRange.size = sizeof(PushConstantFlat);

    VkPipelineLayoutCreateInfo flatLayoutInfo{};
    flatLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    flatLayoutInfo.pushConstantRangeCount = 1;
    flatLayoutInfo.pPushConstantRanges = &flatRange;

    if (table.vkCreatePipelineLayout(device, &flatLayoutInfo, nullptr, &flatLayout_) != VK_SUCCESS) {
        return false;
    }

    // Wave line pipeline layout (Push constants: 32 bytes)
    VkPushConstantRange waveRange{};
    waveRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    waveRange.offset = 0;
    waveRange.size = sizeof(PushConstantWaveLine);

    VkPipelineLayoutCreateInfo waveLayoutInfo{};
    waveLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    waveLayoutInfo.pushConstantRangeCount = 1;
    waveLayoutInfo.pPushConstantRanges = &waveRange;

    if (table.vkCreatePipelineLayout(device, &waveLayoutInfo, nullptr, &waveLayout_) != VK_SUCCESS) {
        return false;
    }

    // Text pipeline layout (Descriptor Set 0: Font Sampler + Push constants: 8 bytes)
    VkDescriptorSetLayoutBinding samplerBinding{};
    samplerBinding.binding = 0;
    samplerBinding.descriptorCount = 1;
    samplerBinding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    samplerBinding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

    VkDescriptorSetLayoutCreateInfo descLayoutInfo{};
    descLayoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    descLayoutInfo.bindingCount = 1;
    descLayoutInfo.pBindings = &samplerBinding;

    if (table.vkCreateDescriptorSetLayout(device, &descLayoutInfo, nullptr, &textDescLayout_) != VK_SUCCESS) {
        return false;
    }

    VkPushConstantRange textRange{};
    textRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    textRange.offset = 0;
    textRange.size = sizeof(PushConstantText);

    VkPipelineLayoutCreateInfo textLayoutInfo{};
    textLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    textLayoutInfo.setLayoutCount = 1;
    textLayoutInfo.pSetLayouts = &textDescLayout_;
    textLayoutInfo.pushConstantRangeCount = 1;
    textLayoutInfo.pPushConstantRanges = &textRange;

    if (table.vkCreatePipelineLayout(device, &textLayoutInfo, nullptr, &textLayout_) != VK_SUCCESS) {
        return false;
    }

    // Transition pipeline layout (Descriptor Set 0: Snapshot Sampler + Push constants: 16 bytes)
    VkPushConstantRange transitionRange{};
    transitionRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    transitionRange.offset = 0;
    transitionRange.size = sizeof(PushConstantTransition);

    VkPipelineLayoutCreateInfo transitionLayoutInfo{};
    transitionLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    transitionLayoutInfo.setLayoutCount = 1;
    transitionLayoutInfo.pSetLayouts = &textDescLayout_;
    transitionLayoutInfo.pushConstantRangeCount = 1;
    transitionLayoutInfo.pPushConstantRanges = &transitionRange;

    if (table.vkCreatePipelineLayout(device, &transitionLayoutInfo, nullptr, &transitionLayout_) != VK_SUCCESS) {
        return false;
    }

    // ArtworkBg pipeline layout (Push constants: 64 bytes)
    VkPushConstantRange bgRange{};
    bgRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    bgRange.offset = 0;
    bgRange.size = sizeof(PushConstantArtworkBg);

    VkPipelineLayoutCreateInfo bgLayoutInfo{};
    bgLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    bgLayoutInfo.pushConstantRangeCount = 1;
    bgLayoutInfo.pPushConstantRanges = &bgRange;

    if (table.vkCreatePipelineLayout(device, &bgLayoutInfo, nullptr, &artworkBgLayout_) != VK_SUCCESS) {
        return false;
    }

    // ArtworkTex pipeline layout (Descriptor Set 0: Sampler + Push constants: 32 bytes)
    VkPushConstantRange texRange{};
    texRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    texRange.offset = 0;
    texRange.size = sizeof(PushConstantArtworkTex);

    VkPipelineLayoutCreateInfo texLayoutInfo{};
    texLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    texLayoutInfo.setLayoutCount = 1;
    texLayoutInfo.pSetLayouts = &textDescLayout_;
    texLayoutInfo.pushConstantRangeCount = 1;
    texLayoutInfo.pPushConstantRanges = &texRange;

    if (table.vkCreatePipelineLayout(device, &texLayoutInfo, nullptr, &artworkTexLayout_) != VK_SUCCESS) {
        return false;
    }

    // Contrast pipeline layout (Push constants: 32 bytes)
    VkPushConstantRange contrastRange{};
    contrastRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    contrastRange.offset = 0;
    contrastRange.size = sizeof(PushConstantContrast);

    VkPipelineLayoutCreateInfo contrastLayoutInfo{};
    contrastLayoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    contrastLayoutInfo.pushConstantRangeCount = 1;
    contrastLayoutInfo.pPushConstantRanges = &contrastRange;

    return table.vkCreatePipelineLayout(device, &contrastLayoutInfo, nullptr, &contrastLayout_) == VK_SUCCESS;
}

bool VkPrimitivePipelines::createPipelines(VkRenderPass renderPass, uint32_t subpass, VkSampleCountFlagBits samples) {
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
    multisampling.rasterizationSamples = samples;

    VkPipelineColorBlendAttachmentState colorBlendAttachment{};
    colorBlendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                          VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    colorBlendAttachment.blendEnable = VK_TRUE;
    colorBlendAttachment.srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA;
    colorBlendAttachment.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    colorBlendAttachment.colorBlendOp = VK_BLEND_OP_ADD;
    colorBlendAttachment.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
    colorBlendAttachment.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    colorBlendAttachment.alphaBlendOp = VK_BLEND_OP_ADD;

    VkPipelineColorBlendStateCreateInfo colorBlending{};
    colorBlending.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    colorBlending.attachmentCount = 1;
    colorBlending.pAttachments = &colorBlendAttachment;

    std::vector<VkDynamicState> standardDynamicStates = {
        VK_DYNAMIC_STATE_VIEWPORT,
        VK_DYNAMIC_STATE_SCISSOR
    };

    VkPipelineDynamicStateCreateInfo dynamicState{};
    dynamicState.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
    dynamicState.dynamicStateCount = static_cast<uint32_t>(standardDynamicStates.size());
    dynamicState.pDynamicStates = standardDynamicStates.data();

    std::vector<VkDynamicState> lineDynamicStates = standardDynamicStates;
    if (context_->getCapabilities().hasExtendedDynamicState) {
        lineDynamicStates.push_back(VK_DYNAMIC_STATE_LINE_WIDTH);
    }
    VkPipelineDynamicStateCreateInfo lineDynamicState = dynamicState;
    lineDynamicState.dynamicStateCount = static_cast<uint32_t>(lineDynamicStates.size());
    lineDynamicState.pDynamicStates = lineDynamicStates.data();

    // 1. Flat Triangles Pipeline (2D pos: vec2)
    VkVertexInputBindingDescription flatBinding{};
    flatBinding.binding = 0;
    flatBinding.stride = 2 * sizeof(float);
    flatBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription flatPosAttr{};
    flatPosAttr.location = 0;
    flatPosAttr.binding = 0;
    flatPosAttr.format = VK_FORMAT_R32G32_SFLOAT;
    flatPosAttr.offset = 0;

    VkPipelineVertexInputStateCreateInfo flatVertexInput{};
    flatVertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    flatVertexInput.vertexBindingDescriptionCount = 1;
    flatVertexInput.pVertexBindingDescriptions = &flatBinding;
    flatVertexInput.vertexAttributeDescriptionCount = 1;
    flatVertexInput.pVertexAttributeDescriptions = &flatPosAttr;

    VkPipelineInputAssemblyStateCreateInfo triAssembly{};
    triAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    triAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkPipelineShaderStageCreateInfo flatStages[2]{};
    flatStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    flatStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    flatStages[0].module = flatVertShader_;
    flatStages[0].pName = "main";
    flatStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    flatStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    flatStages[1].module = flatFragShader_;
    flatStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo flatTriPipeInfo{};
    flatTriPipeInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    flatTriPipeInfo.stageCount = 2;
    flatTriPipeInfo.pStages = flatStages;
    flatTriPipeInfo.pVertexInputState = &flatVertexInput;
    flatTriPipeInfo.pInputAssemblyState = &triAssembly;
    flatTriPipeInfo.pViewportState = &viewportState;
    flatTriPipeInfo.pRasterizationState = &rasterizer;
    flatTriPipeInfo.pMultisampleState = &multisampling;
    flatTriPipeInfo.pColorBlendState = &colorBlending;
    flatTriPipeInfo.pDynamicState = &dynamicState;
    flatTriPipeInfo.layout = flatLayout_;
    flatTriPipeInfo.renderPass = renderPass;
    flatTriPipeInfo.subpass = subpass;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &flatTriPipeInfo, nullptr, &flatTrianglesPipeline_) != VK_SUCCESS) {
        return false;
    }

    // 2. Flat Lines Pipeline (same vertex layout, LINE_LIST topology)
    VkPipelineInputAssemblyStateCreateInfo lineAssembly{};
    lineAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    lineAssembly.topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;

    VkGraphicsPipelineCreateInfo flatLinePipeInfo = flatTriPipeInfo;
    flatLinePipeInfo.pInputAssemblyState = &lineAssembly;
    flatLinePipeInfo.pDynamicState = &lineDynamicState;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &flatLinePipeInfo, nullptr, &flatLinesPipeline_) != VK_SUCCESS) {
        return false;
    }

    // 3. Wave Lines Pipeline (pos: vec2, dist: float -> stride: 3 * float)
    VkVertexInputBindingDescription waveBinding{};
    waveBinding.binding = 0;
    waveBinding.stride = 3 * sizeof(float);
    waveBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription waveAttrs[2]{};
    waveAttrs[0].location = 0;
    waveAttrs[0].binding = 0;
    waveAttrs[0].format = VK_FORMAT_R32G32_SFLOAT;
    waveAttrs[0].offset = 0;
    waveAttrs[1].location = 1;
    waveAttrs[1].binding = 0;
    waveAttrs[1].format = VK_FORMAT_R32_SFLOAT;
    waveAttrs[1].offset = 2 * sizeof(float);

    VkPipelineVertexInputStateCreateInfo waveVertexInput{};
    waveVertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    waveVertexInput.vertexBindingDescriptionCount = 1;
    waveVertexInput.pVertexBindingDescriptions = &waveBinding;
    waveVertexInput.vertexAttributeDescriptionCount = 2;
    waveVertexInput.pVertexAttributeDescriptions = waveAttrs;

    VkPipelineShaderStageCreateInfo waveStages[2]{};
    waveStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    waveStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    waveStages[0].module = waveVertShader_;
    waveStages[0].pName = "main";
    waveStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    waveStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    waveStages[1].module = waveFragShader_;
    waveStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo wavePipeInfo = flatTriPipeInfo;
    wavePipeInfo.pStages = waveStages;
    wavePipeInfo.pVertexInputState = &waveVertexInput;
    wavePipeInfo.layout = waveLayout_;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &wavePipeInfo, nullptr, &waveLinesPipeline_) != VK_SUCCESS) {
        return false;
    }

    // 4. Text Pipeline (pos: vec2, uv: vec2, color: vec4 -> stride: 8 * float)
    VkVertexInputBindingDescription textBinding{};
    textBinding.binding = 0;
    textBinding.stride = 8 * sizeof(float);
    textBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription textAttrs[3]{};
    textAttrs[0].location = 0;
    textAttrs[0].binding = 0;
    textAttrs[0].format = VK_FORMAT_R32G32_SFLOAT;
    textAttrs[0].offset = 0;
    textAttrs[1].location = 1;
    textAttrs[1].binding = 0;
    textAttrs[1].format = VK_FORMAT_R32G32_SFLOAT;
    textAttrs[1].offset = 2 * sizeof(float);
    textAttrs[2].location = 2;
    textAttrs[2].binding = 0;
    textAttrs[2].format = VK_FORMAT_R32G32B32A32_SFLOAT;
    textAttrs[2].offset = 4 * sizeof(float);

    VkPipelineVertexInputStateCreateInfo textVertexInput{};
    textVertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    textVertexInput.vertexBindingDescriptionCount = 1;
    textVertexInput.pVertexBindingDescriptions = &textBinding;
    textVertexInput.vertexAttributeDescriptionCount = 3;
    textVertexInput.pVertexAttributeDescriptions = textAttrs;

    VkPipelineShaderStageCreateInfo textStages[2]{};
    textStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    textStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    textStages[0].module = textVertShader_;
    textStages[0].pName = "main";
    textStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    textStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    textStages[1].module = textFragShader_;
    textStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo textPipeInfo = flatTriPipeInfo;
    textPipeInfo.pStages = textStages;
    textPipeInfo.pVertexInputState = &textVertexInput;
    textPipeInfo.layout = textLayout_;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &textPipeInfo, nullptr, &textPipeline_) != VK_SUCCESS) {
        return false;
    }

    // 5. Transition Pipeline (2D pos: vec2, 2D uv: vec2)
    VkVertexInputBindingDescription transBinding{};
    transBinding.binding = 0;
    transBinding.stride = 4 * sizeof(float);
    transBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription transAttrs[2]{};
    transAttrs[0].location = 0;
    transAttrs[0].binding = 0;
    transAttrs[0].format = VK_FORMAT_R32G32_SFLOAT;
    transAttrs[0].offset = 0;
    transAttrs[1].location = 1;
    transAttrs[1].binding = 0;
    transAttrs[1].format = VK_FORMAT_R32G32_SFLOAT;
    transAttrs[1].offset = 2 * sizeof(float);

    VkPipelineVertexInputStateCreateInfo transVertexInput{};
    transVertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    transVertexInput.vertexBindingDescriptionCount = 1;
    transVertexInput.pVertexBindingDescriptions = &transBinding;
    transVertexInput.vertexAttributeDescriptionCount = 2;
    transVertexInput.pVertexAttributeDescriptions = transAttrs;

    VkPipelineShaderStageCreateInfo transStages[2]{};
    transStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    transStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    transStages[0].module = transitionVertShader_;
    transStages[0].pName = "main";
    transStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    transStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    transStages[1].module = transitionFragShader_;
    transStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo transPipeInfo = flatTriPipeInfo;
    transPipeInfo.pStages = transStages;
    transPipeInfo.pVertexInputState = &transVertexInput;
    transPipeInfo.layout = transitionLayout_;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &transPipeInfo, nullptr, &transitionPipeline_) != VK_SUCCESS) {
        return false;
    }

    VkPipelineShaderStageCreateInfo bgStages[2]{};
    bgStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    bgStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    bgStages[0].module = artworkBgVertShader_;
    bgStages[0].pName = "main";
    bgStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    bgStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    bgStages[1].module = artworkBgFragShader_;
    bgStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo bgPipeInfo = transPipeInfo;
    bgPipeInfo.pStages = bgStages;
    bgPipeInfo.layout = artworkBgLayout_;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &bgPipeInfo, nullptr, &artworkBgPipeline_) != VK_SUCCESS) {
        return false;
    }

    VkPipelineShaderStageCreateInfo texStages[2]{};
    texStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    texStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    texStages[0].module = artworkTexVertShader_;
    texStages[0].pName = "main";
    texStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    texStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    texStages[1].module = artworkTexFragShader_;
    texStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo texPipeInfo = transPipeInfo;
    texPipeInfo.pStages = texStages;
    texPipeInfo.layout = artworkTexLayout_;

    if (table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &texPipeInfo, nullptr, &artworkTexPipeline_) != VK_SUCCESS) {
        return false;
    }

    VkPipelineShaderStageCreateInfo contrastStages[2]{};
    contrastStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    contrastStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    contrastStages[0].module = contrastVertShader_;
    contrastStages[0].pName = "main";
    contrastStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    contrastStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    contrastStages[1].module = contrastFragShader_;
    contrastStages[1].pName = "main";

    VkGraphicsPipelineCreateInfo contrastPipeInfo = transPipeInfo;
    contrastPipeInfo.pStages = contrastStages;
    contrastPipeInfo.layout = contrastLayout_;

    return table.vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &contrastPipeInfo, nullptr, &contrastPipeline_) == VK_SUCCESS;
}

void VkPrimitivePipelines::bindFlatTriangles(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, flatTrianglesPipeline_);

    PushConstantFlat pc{};
    unpackColor(colorArgb, pc.color);
    pc.resolution[0] = width;
    pc.resolution[1] = height;

    table.vkCmdPushConstants(cmd, flatLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindFlatLines(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb, float lineWidth) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, flatLinesPipeline_);

    if (context_->getCapabilities().hasExtendedDynamicState && table.vkCmdSetLineWidth) {
        table.vkCmdSetLineWidth(cmd, lineWidth);
    }

    PushConstantFlat pc{};
    unpackColor(colorArgb, pc.color);
    pc.resolution[0] = width;
    pc.resolution[1] = height;

    table.vkCmdPushConstants(cmd, flatLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindWaveLines(VkCommandBuffer cmd, float width, float height, uint32_t colorArgb, float halfWidth, float softness) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, waveLinesPipeline_);

    PushConstantWaveLine pc{};
    unpackColor(colorArgb, pc.color);
    pc.resolution[0] = width;
    pc.resolution[1] = height;
    pc.halfWidth = halfWidth;
    pc.softness = softness;

    table.vkCmdPushConstants(cmd, waveLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindText(VkCommandBuffer cmd, float width, float height, VkDescriptorSet fontDescriptorSet) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, textPipeline_);

    if (fontDescriptorSet != VK_NULL_HANDLE) {
        table.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, textLayout_, 0, 1, &fontDescriptorSet, 0, nullptr);
    }

    PushConstantText pc{};
    pc.resolution[0] = width;
    pc.resolution[1] = height;

    table.vkCmdPushConstants(cmd, textLayout_, VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindTransition(VkCommandBuffer cmd, float width, float height, float offsetX, float alpha, VkDescriptorSet snapshotDescriptorSet) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, transitionPipeline_);

    if (snapshotDescriptorSet != VK_NULL_HANDLE) {
        table.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, transitionLayout_, 0, 1, &snapshotDescriptorSet, 0, nullptr);
    }

    PushConstantTransition pc{};
    pc.resolution[0] = width;
    pc.resolution[1] = height;
    pc.offsetX = offsetX;
    pc.alpha = alpha;

    table.vkCmdPushConstants(cmd, transitionLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindArtworkBg(VkCommandBuffer cmd, float width, float height,
                                         const float centerColor[4], const float edgeColor[4], const float circleColor[4],
                                         float circleRadius, float alpha) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, artworkBgPipeline_);

    PushConstantArtworkBg pc{};
    std::memcpy(pc.centerColor, centerColor, 4 * sizeof(float));
    std::memcpy(pc.edgeColor, edgeColor, 4 * sizeof(float));
    std::memcpy(pc.circleColor, circleColor, 4 * sizeof(float));
    pc.resolution[0] = width;
    pc.resolution[1] = height;
    pc.circleRadius = circleRadius;
    pc.alpha = alpha;

    table.vkCmdPushConstants(cmd, artworkBgLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindArtworkTex(VkCommandBuffer cmd, float width, float height,
                                          float r, float g, float b, float a, float mono,
                                          VkDescriptorSet texDescriptorSet) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, artworkTexPipeline_);

    if (texDescriptorSet != VK_NULL_HANDLE) {
        table.vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, artworkTexLayout_, 0, 1, &texDescriptorSet, 0, nullptr);
    }

    PushConstantArtworkTex pc{};
    pc.color[0] = r;
    pc.color[1] = g;
    pc.color[2] = b;
    pc.color[3] = a;
    pc.resolution[0] = width;
    pc.resolution[1] = height;
    pc.mono = mono;
    pc.pad = 0.0f;

    table.vkCmdPushConstants(cmd, artworkTexLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

void VkPrimitivePipelines::bindContrast(VkCommandBuffer cmd, float width, float height,
                                        int32_t mode, uint32_t scrimColorArgb) {
    const auto& table = VkLoader::table();
    table.vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, contrastPipeline_);

    PushConstantContrast pc{};
    pc.scrimColor[0] = ((scrimColorArgb >> 16) & 0xFF) / 255.0f;
    pc.scrimColor[1] = ((scrimColorArgb >> 8) & 0xFF) / 255.0f;
    pc.scrimColor[2] = (scrimColorArgb & 0xFF) / 255.0f;
    pc.scrimColor[3] = ((scrimColorArgb >> 24) & 0xFF) / 255.0f;
    pc.resolution[0] = width;
    pc.resolution[1] = height;
    pc.mode = mode;
    pc.pad = 0.0f;

    table.vkCmdPushConstants(cmd, contrastLayout_, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
}

} // namespace silicon::vis::vk
