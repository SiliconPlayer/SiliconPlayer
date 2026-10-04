#include "vk_pipeline.h"
#include "gl/gl_font_atlas.h"
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

} // namespace

SiliconVisVulkanPipeline::SiliconVisVulkanPipeline() = default;

SiliconVisVulkanPipeline::~SiliconVisVulkanPipeline() {
    release();
}

bool SiliconVisVulkanPipeline::init(uint32_t width, uint32_t height, void* nativeWindow) {
    release();
    width_ = std::max(1u, width);
    height_ = std::max(1u, height);

    if (!context_.init()) {
        return false;
    }

    VkRenderPass initialRenderPass = VK_NULL_HANDLE;
    VkSampleCountFlagBits samples = VK_SAMPLE_COUNT_1_BIT;

    if (nativeWindow != nullptr) {
#if defined(__ANDROID__)
        isSurfaceMode_ = true;
        VkSurfaceKHR surface = context_.createAndroidSurface(static_cast<ANativeWindow*>(nativeWindow));
        if (surface == VK_NULL_HANDLE) {
            release();
            return false;
        }
        if (!swapchain_.init(&context_, surface, width_, height_, 4)) {
            release();
            return false;
        }
        auto ext = swapchain_.getExtent();
        width_ = ext.width;
        height_ = ext.height;
        initialRenderPass = swapchain_.getRenderPass();
        samples = swapchain_.getMsaaSamples();
#else
        release();
        return false;
#endif
    } else {
        isSurfaceMode_ = false;
        uint32_t reqSamples = 4;
        if (reqSamples >= 4 && (context_.getCapabilities().maxMsaaSamples >= 4)) {
            msaaSamples_ = VK_SAMPLE_COUNT_4_BIT;
        } else if (reqSamples >= 2 && (context_.getCapabilities().maxMsaaSamples >= 2)) {
            msaaSamples_ = VK_SAMPLE_COUNT_2_BIT;
        } else {
            msaaSamples_ = VK_SAMPLE_COUNT_1_BIT;
        }
        if (!createOffscreenRenderPass()) {
            release();
            return false;
        }
        if (!createOffscreenResources()) {
            release();
            return false;
        }
        initialRenderPass = offscreenRenderPass_;
        samples = msaaSamples_;
    }

    if (!pipelines_.init(&context_, initialRenderPass, 0, samples)) {
        release();
        return false;
    }

    // 2 MB host-visible dynamic ring buffer for vertex streaming
    if (!vertexBuffer_.init(&context_, 2 * 1024 * 1024)) {
        release();
        return false;
    }

    if (!fontAtlas_.init(&context_, pipelines_.getTextDescLayout())) {
        release();
        return false;
    }

    if (!artworkRenderer_.init(&context_, pipelines_.getTextDescLayout())) {
        release();
        return false;
    }

    std::vector<uint8_t> defaultFontRgba;
    int fontW = 0, fontH = 0;
    gl::GlFontAtlas defaultAtlas;
    defaultAtlas.getDefaultAtlasRgba(defaultFontRgba, fontW, fontH);
    fontAtlas_.updateAtlas(defaultFontRgba.data(), fontW, fontH);

    if (!createSyncObjects()) {
        release();
        return false;
    }

    initialized_ = true;
    return true;
}

void SiliconVisVulkanPipeline::release() {
    if (!initialized_) return;

    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    if (device != VK_NULL_HANDLE && table.vkDeviceWaitIdle) {
        table.vkDeviceWaitIdle(device);
    }

    cleanupSyncObjects();
    cleanupOffscreenResources();
    cleanupSnapshot();

    if (offscreenRenderPass_ != VK_NULL_HANDLE && device != VK_NULL_HANDLE) {
        table.vkDestroyRenderPass(device, offscreenRenderPass_, nullptr);
        offscreenRenderPass_ = VK_NULL_HANDLE;
    }

    artworkRenderer_.release();
    fontAtlas_.release();
    vertexBuffer_.release();
    pipelines_.release();
    swapchain_.release();
    context_.release();

    initialized_ = false;
    isSurfaceMode_ = false;
    width_ = 0;
    height_ = 0;
    msaaSamples_ = VK_SAMPLE_COUNT_1_BIT;
    hasRenderedFrame_ = false;
}

bool SiliconVisVulkanPipeline::resize(uint32_t width, uint32_t height, float density) {
    if (!initialized_ || width == 0 || height == 0) return false;
    cleanupSnapshot();
    hasRenderedFrame_ = false;
    width_ = width;
    height_ = height;
    density_ = std::max(1.0f, density);

    if (isSurfaceMode_) {
        bool ok = swapchain_.resize(width_, height_);
        if (ok) {
            auto ext = swapchain_.getExtent();
            width_ = ext.width;
            height_ = ext.height;
        }
        return ok;
    } else {
        const auto& table = VkLoader::table();
        table.vkDeviceWaitIdle(context_.getDevice());
        cleanupOffscreenResources();
        return createOffscreenResources();
    }
}

bool SiliconVisVulkanPipeline::createSyncObjects() {
    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    VkCommandPool cmdPool = context_.getCommandPool();

    VkCommandBufferAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = cmdPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = kMaxFramesInFlight;

    if (table.vkAllocateCommandBuffers(device, &allocInfo, commandBuffers_) != VK_SUCCESS) {
        return false;
    }

    VkSemaphoreCreateInfo semInfo{};
    semInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    for (int i = 0; i < kMaxFramesInFlight; ++i) {
        if (table.vkCreateSemaphore(device, &semInfo, nullptr, &imageAvailableSemaphores_[i]) != VK_SUCCESS ||
            table.vkCreateSemaphore(device, &semInfo, nullptr, &renderFinishedSemaphores_[i]) != VK_SUCCESS ||
            table.vkCreateFence(device, &fenceInfo, nullptr, &inFlightFences_[i]) != VK_SUCCESS) {
            return false;
        }
    }
    currentFrameIndex_ = 0;
    return true;
}

void SiliconVisVulkanPipeline::cleanupSyncObjects() {
    if (!context_.isInitialized()) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    VkCommandPool cmdPool = context_.getCommandPool();

    for (int i = 0; i < kMaxFramesInFlight; ++i) {
        if (imageAvailableSemaphores_[i] != VK_NULL_HANDLE) {
            table.vkDestroySemaphore(device, imageAvailableSemaphores_[i], nullptr);
            imageAvailableSemaphores_[i] = VK_NULL_HANDLE;
        }
        if (renderFinishedSemaphores_[i] != VK_NULL_HANDLE) {
            table.vkDestroySemaphore(device, renderFinishedSemaphores_[i], nullptr);
            renderFinishedSemaphores_[i] = VK_NULL_HANDLE;
        }
        if (inFlightFences_[i] != VK_NULL_HANDLE) {
            table.vkDestroyFence(device, inFlightFences_[i], nullptr);
            inFlightFences_[i] = VK_NULL_HANDLE;
        }
    }

    if (commandBuffers_[0] != VK_NULL_HANDLE && cmdPool != VK_NULL_HANDLE) {
        table.vkFreeCommandBuffers(device, cmdPool, kMaxFramesInFlight, commandBuffers_);
        for (int i = 0; i < kMaxFramesInFlight; ++i) commandBuffers_[i] = VK_NULL_HANDLE;
    }
}

bool SiliconVisVulkanPipeline::createOffscreenRenderPass() {
    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();

    if (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT) {
        VkAttachmentDescription msaaColorAttachment{};
        msaaColorAttachment.format = VK_FORMAT_R8G8B8A8_UNORM;
        msaaColorAttachment.samples = msaaSamples_;
        msaaColorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        msaaColorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        msaaColorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        msaaColorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        msaaColorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        msaaColorAttachment.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkAttachmentReference colorAttachmentRef{};
        colorAttachmentRef.attachment = 0;
        colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkAttachmentDescription resolveAttachment{};
        resolveAttachment.format = VK_FORMAT_R8G8B8A8_UNORM;
        resolveAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        resolveAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        resolveAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        resolveAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        resolveAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        resolveAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        resolveAttachment.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkAttachmentReference resolveAttachmentRef{};
        resolveAttachmentRef.attachment = 1;
        resolveAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorAttachmentRef;
        subpass.pResolveAttachments = &resolveAttachmentRef;

        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
        dependency.dstSubpass = 0;
        dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.srcAccessMask = 0;
        dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkAttachmentDescription attachments[2] = { msaaColorAttachment, resolveAttachment };
        VkRenderPassCreateInfo renderPassInfo{};
        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        renderPassInfo.attachmentCount = 2;
        renderPassInfo.pAttachments = attachments;
        renderPassInfo.subpassCount = 1;
        renderPassInfo.pSubpasses = &subpass;
        renderPassInfo.dependencyCount = 1;
        renderPassInfo.pDependencies = &dependency;

        return table.vkCreateRenderPass(device, &renderPassInfo, nullptr, &offscreenRenderPass_) == VK_SUCCESS;
    } else {
        VkAttachmentDescription colorAttachment{};
        colorAttachment.format = VK_FORMAT_R8G8B8A8_UNORM;
        colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        colorAttachment.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkAttachmentReference colorAttachmentRef{};
        colorAttachmentRef.attachment = 0;
        colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorAttachmentRef;

        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
        dependency.dstSubpass = 0;
        dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.srcAccessMask = 0;
        dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkRenderPassCreateInfo renderPassInfo{};
        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        renderPassInfo.attachmentCount = 1;
        renderPassInfo.pAttachments = &colorAttachment;
        renderPassInfo.subpassCount = 1;
        renderPassInfo.pSubpasses = &subpass;
        renderPassInfo.dependencyCount = 1;
        renderPassInfo.pDependencies = &dependency;

        return table.vkCreateRenderPass(device, &renderPassInfo, nullptr, &offscreenRenderPass_) == VK_SUCCESS;
    }
}

bool SiliconVisVulkanPipeline::createOffscreenResources() {
    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    VkPhysicalDevice physDev = context_.getPhysicalDevice();

    // 1. Offscreen Image
    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent.width = width_;
    imageInfo.extent.height = height_;
    imageInfo.extent.depth = 1;
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (table.vkCreateImage(device, &imageInfo, nullptr, &offscreenImage_) != VK_SUCCESS) {
        return false;
    }

    VkMemoryRequirements memReqs{};
    table.vkGetImageMemoryRequirements(device, offscreenImage_, &memReqs);

    uint32_t memType = findMemoryType(physDev, memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = memType;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &offscreenMemory_) != VK_SUCCESS) {
        return false;
    }
    table.vkBindImageMemory(device, offscreenImage_, offscreenMemory_, 0);

    // 2. Image View
    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = offscreenImage_;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    if (table.vkCreateImageView(device, &viewInfo, nullptr, &offscreenImageView_) != VK_SUCCESS) {
        return false;
    }

    // 3. MSAA Image and View
    if (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT) {
        VkImageCreateInfo msaaImageInfo{};
        msaaImageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        msaaImageInfo.imageType = VK_IMAGE_TYPE_2D;
        msaaImageInfo.extent.width = width_;
        msaaImageInfo.extent.height = height_;
        msaaImageInfo.extent.depth = 1;
        msaaImageInfo.mipLevels = 1;
        msaaImageInfo.arrayLayers = 1;
        msaaImageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
        msaaImageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        msaaImageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        msaaImageInfo.usage = VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        msaaImageInfo.samples = msaaSamples_;
        msaaImageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

        if (table.vkCreateImage(device, &msaaImageInfo, nullptr, &msaaImage_) != VK_SUCCESS) {
            return false;
        }

        VkMemoryRequirements msaaReqs{};
        table.vkGetImageMemoryRequirements(device, msaaImage_, &msaaReqs);

        uint32_t msaaMemType = findMemoryType(
            physDev, msaaReqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT
        );
        if (msaaMemType == 0xFFFFFFFF) {
            msaaMemType = findMemoryType(physDev, msaaReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        }

        VkMemoryAllocateInfo msaaAlloc{};
        msaaAlloc.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        msaaAlloc.allocationSize = msaaReqs.size;
        msaaAlloc.memoryTypeIndex = msaaMemType;

        if (table.vkAllocateMemory(device, &msaaAlloc, nullptr, &msaaMemory_) != VK_SUCCESS) {
            return false;
        }
        table.vkBindImageMemory(device, msaaImage_, msaaMemory_, 0);

        VkImageViewCreateInfo msaaViewInfo{};
        msaaViewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        msaaViewInfo.image = msaaImage_;
        msaaViewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        msaaViewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
        msaaViewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        msaaViewInfo.subresourceRange.baseMipLevel = 0;
        msaaViewInfo.subresourceRange.levelCount = 1;
        msaaViewInfo.subresourceRange.baseArrayLayer = 0;
        msaaViewInfo.subresourceRange.layerCount = 1;

        if (table.vkCreateImageView(device, &msaaViewInfo, nullptr, &msaaImageView_) != VK_SUCCESS) {
            return false;
        }
    }

    // 4. Framebuffer
    VkFramebufferCreateInfo fbInfo{};
    fbInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fbInfo.renderPass = offscreenRenderPass_;
    VkImageView attachments[2];
    if (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT) {
        attachments[0] = msaaImageView_;
        attachments[1] = offscreenImageView_;
        fbInfo.attachmentCount = 2;
        fbInfo.pAttachments = attachments;
    } else {
        fbInfo.attachmentCount = 1;
        fbInfo.pAttachments = &offscreenImageView_;
    }
    fbInfo.width = width_;
    fbInfo.height = height_;
    fbInfo.layers = 1;

    if (table.vkCreateFramebuffer(device, &fbInfo, nullptr, &offscreenFramebuffer_) != VK_SUCCESS) {
        return false;
    }

    // 5. Readback buffer for CPU Skia readback
    size_t bufferSize = static_cast<size_t>(width_ * height_ * 4);
    VkBufferCreateInfo bufInfo{};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size = bufferSize;
    bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (table.vkCreateBuffer(device, &bufInfo, nullptr, &readbackBuffer_) != VK_SUCCESS) {
        return false;
    }

    table.vkGetBufferMemoryRequirements(device, readbackBuffer_, &memReqs);
    uint32_t hostMemType = findMemoryType(
        physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
    );

    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = hostMemType;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &readbackMemory_) != VK_SUCCESS) {
        return false;
    }

    table.vkBindBufferMemory(device, readbackBuffer_, readbackMemory_, 0);
    table.vkMapMemory(device, readbackMemory_, 0, bufferSize, 0, &readbackMapped_);

    return true;
}

void SiliconVisVulkanPipeline::cleanupOffscreenResources() {
    if (!context_.isInitialized()) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();

    if (readbackMapped_) {
        table.vkUnmapMemory(device, readbackMemory_);
        readbackMapped_ = nullptr;
    }
    if (readbackBuffer_ != VK_NULL_HANDLE) {
        table.vkDestroyBuffer(device, readbackBuffer_, nullptr);
        readbackBuffer_ = VK_NULL_HANDLE;
    }
    if (readbackMemory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, readbackMemory_, nullptr);
        readbackMemory_ = VK_NULL_HANDLE;
    }

    if (offscreenFramebuffer_ != VK_NULL_HANDLE) {
        table.vkDestroyFramebuffer(device, offscreenFramebuffer_, nullptr);
        offscreenFramebuffer_ = VK_NULL_HANDLE;
    }
    if (offscreenImageView_ != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, offscreenImageView_, nullptr);
        offscreenImageView_ = VK_NULL_HANDLE;
    }
    if (offscreenImage_ != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, offscreenImage_, nullptr);
        offscreenImage_ = VK_NULL_HANDLE;
    }
    if (offscreenMemory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, offscreenMemory_, nullptr);
        offscreenMemory_ = VK_NULL_HANDLE;
    }

    if (msaaImageView_ != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, msaaImageView_, nullptr);
        msaaImageView_ = VK_NULL_HANDLE;
    }
    if (msaaImage_ != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, msaaImage_, nullptr);
        msaaImage_ = VK_NULL_HANDLE;
    }
    if (msaaMemory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, msaaMemory_, nullptr);
        msaaMemory_ = VK_NULL_HANDLE;
    }
}

bool SiliconVisVulkanPipeline::beginFrame() {
    if (!initialized_) return false;

    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    if (!table.vkWaitForFences || device == VK_NULL_HANDLE) return false;

    table.vkWaitForFences(device, 1, &inFlightFences_[currentFrameIndex_], VK_TRUE, UINT64_MAX);

    // Reset per-frame vertex stream buffer
    vertexBuffer_.reset();

    VkFramebuffer targetFramebuffer = VK_NULL_HANDLE;
    VkRenderPass targetRenderPass = VK_NULL_HANDLE;

    if (isSurfaceMode_) {
        bool outOfDate = false;
        if (!swapchain_.acquireNextImage(imageAvailableSemaphores_[currentFrameIndex_], currentImageIndex_, outOfDate)) {
            if (outOfDate) {
                if (swapchain_.resize(width_, height_)) {
                    auto ext = swapchain_.getExtent();
                    width_ = ext.width;
                    height_ = ext.height;
                }
            }
            return false;
        }
        targetFramebuffer = swapchain_.getFramebuffer(currentImageIndex_);
        targetRenderPass = swapchain_.getRenderPass();
    } else {
        targetFramebuffer = offscreenFramebuffer_;
        targetRenderPass = offscreenRenderPass_;
    }

    table.vkResetFences(device, 1, &inFlightFences_[currentFrameIndex_]);

    VkCommandBuffer cmd = commandBuffers_[currentFrameIndex_];
    table.vkResetCommandBuffer(cmd, 0);

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

    table.vkBeginCommandBuffer(cmd, &beginInfo);

    // Begin Render Pass
    VkClearValue clearValues[2]{};
    for (int i = 0; i < 2; ++i) {
        clearValues[i].color.float32[0] = clearColor_[0];
        clearValues[i].color.float32[1] = clearColor_[1];
        clearValues[i].color.float32[2] = clearColor_[2];
        clearValues[i].color.float32[3] = clearColor_[3];
    }

    bool hasMsaa = isSurfaceMode_ ? (swapchain_.getMsaaSamples() > VK_SAMPLE_COUNT_1_BIT)
                                  : (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT);

    VkRenderPassBeginInfo passInfo{};
    passInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    passInfo.renderPass = targetRenderPass;
    passInfo.framebuffer = targetFramebuffer;
    passInfo.renderArea.offset = {0, 0};
    passInfo.renderArea.extent = {width_, height_};
    passInfo.clearValueCount = hasMsaa ? 2 : 1;
    passInfo.pClearValues = clearValues;

    table.vkCmdBeginRenderPass(cmd, &passInfo, VK_SUBPASS_CONTENTS_INLINE);

    VkViewport viewport{};
    viewport.x = 0.0f;
    viewport.y = 0.0f;
    viewport.width = static_cast<float>(width_);
    viewport.height = static_cast<float>(height_);
    viewport.minDepth = 0.0f;
    viewport.maxDepth = 1.0f;
    table.vkCmdSetViewport(cmd, 0, 1, &viewport);

    VkRect2D scissor{};
    scissor.offset = {0, 0};
    scissor.extent = {width_, height_};
    table.vkCmdSetScissor(cmd, 0, 1, &scissor);

    currentCmd_ = cmd;
    return true;
}

void SiliconVisVulkanPipeline::endFrame() {
    if (!initialized_ || currentCmd_ == VK_NULL_HANDLE) return;

    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    VkQueue queue = context_.getGraphicsQueue();
    if (!table.vkCmdEndRenderPass || queue == VK_NULL_HANDLE) return;

    table.vkCmdEndRenderPass(currentCmd_);

    if (!isSurfaceMode_ && readbackBuffer_ != VK_NULL_HANDLE) {
        // Transition offscreen image to TRANSFER_SRC_OPTIMAL
        VkImageMemoryBarrier barrier{};
        barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        barrier.oldLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = offscreenImage_;
        barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        barrier.subresourceRange.baseMipLevel = 0;
        barrier.subresourceRange.levelCount = 1;
        barrier.subresourceRange.baseArrayLayer = 0;
        barrier.subresourceRange.layerCount = 1;

        table.vkCmdPipelineBarrier(currentCmd_, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

        VkBufferImageCopy region{};
        region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        region.imageSubresource.layerCount = 1;
        region.imageExtent = {width_, height_, 1};

        table.vkCmdCopyImageToBuffer(currentCmd_, offscreenImage_, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readbackBuffer_, 1, &region);

        // Transition back
        barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        barrier.newLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        table.vkCmdPipelineBarrier(currentCmd_, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
    }

    table.vkEndCommandBuffer(currentCmd_);

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;

    VkPipelineStageFlags waitStages[] = { VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT };
    if (isSurfaceMode_) {
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = &imageAvailableSemaphores_[currentFrameIndex_];
        submitInfo.pWaitDstStageMask = waitStages;
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = &renderFinishedSemaphores_[currentFrameIndex_];
    }

    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &currentCmd_;

    table.vkQueueSubmit(queue, 1, &submitInfo, inFlightFences_[currentFrameIndex_]);

    if (isSurfaceMode_) {
        bool outOfDate = false;
        swapchain_.present(queue, currentImageIndex_, renderFinishedSemaphores_[currentFrameIndex_], outOfDate);
        if (outOfDate) {
            if (swapchain_.resize(width_, height_)) {
                auto ext = swapchain_.getExtent();
                width_ = ext.width;
                height_ = ext.height;
            }
        }
    } else {
        table.vkWaitForFences(device, 1, &inFlightFences_[currentFrameIndex_], VK_TRUE, UINT64_MAX);
    }

    lastRenderedImageIndex_ = currentImageIndex_;
    hasRenderedFrame_ = true;

    currentCmd_ = VK_NULL_HANDLE;
    currentFrameIndex_ = (currentFrameIndex_ + 1) % kMaxFramesInFlight;
}

bool SiliconVisVulkanPipeline::copyPixelsToBuffer(void* outRgbaBuffer, size_t bufferSize) {
    if (!initialized_ || !readbackMapped_ || !outRgbaBuffer) return false;
    size_t expectedSize = static_cast<size_t>(width_ * height_ * 4);
    if (bufferSize < expectedSize) return false;

    memcpy(outRgbaBuffer, readbackMapped_, expectedSize);
    return true;
}

void SiliconVisVulkanPipeline::cleanupSnapshot() {
    if (!context_.isInitialized()) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();

    hasSnapshot_ = false;
    snapshotWidth_ = 0;
    snapshotHeight_ = 0;

    if (snapshotDescriptorPool_ != VK_NULL_HANDLE) {
        table.vkDestroyDescriptorPool(device, snapshotDescriptorPool_, nullptr);
        snapshotDescriptorPool_ = VK_NULL_HANDLE;
        snapshotDescriptorSet_ = VK_NULL_HANDLE;
    }
    if (snapshotSampler_ != VK_NULL_HANDLE) {
        table.vkDestroySampler(device, snapshotSampler_, nullptr);
        snapshotSampler_ = VK_NULL_HANDLE;
    }
    if (snapshotImageView_ != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, snapshotImageView_, nullptr);
        snapshotImageView_ = VK_NULL_HANDLE;
    }
    if (snapshotImage_ != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, snapshotImage_, nullptr);
        snapshotImage_ = VK_NULL_HANDLE;
    }
    if (snapshotMemory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, snapshotMemory_, nullptr);
        snapshotMemory_ = VK_NULL_HANDLE;
    }
}

void SiliconVisVulkanPipeline::releaseTransitionSnapshot() {
    cleanupSnapshot();
}

bool SiliconVisVulkanPipeline::takeTransitionSnapshot() {
    if (!initialized_ || !hasRenderedFrame_ || width_ == 0 || height_ == 0 || isSurfaceMode_) return false;

    const auto& table = VkLoader::table();
    VkDevice device = context_.getDevice();
    VkPhysicalDevice physDev = context_.getPhysicalDevice();
    VkQueue queue = context_.getGraphicsQueue();
    if (device == VK_NULL_HANDLE || queue == VK_NULL_HANDLE) return false;

    table.vkDeviceWaitIdle(device);

    VkFormat srcFormat = isSurfaceMode_ ? swapchain_.getFormat().format : VK_FORMAT_R8G8B8A8_UNORM;
    VkImage srcImage = isSurfaceMode_ ? swapchain_.getImage(lastRenderedImageIndex_) : offscreenImage_;
    VkImageLayout srcOldLayout = isSurfaceMode_ ? VK_IMAGE_LAYOUT_PRESENT_SRC_KHR : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    if (srcImage == VK_NULL_HANDLE) return false;

    if (snapshotWidth_ != width_ || snapshotHeight_ != height_ || snapshotImage_ == VK_NULL_HANDLE) {
        cleanupSnapshot();

        VkImageCreateInfo imageInfo{};
        imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        imageInfo.imageType = VK_IMAGE_TYPE_2D;
        imageInfo.extent.width = width_;
        imageInfo.extent.height = height_;
        imageInfo.extent.depth = 1;
        imageInfo.mipLevels = 1;
        imageInfo.arrayLayers = 1;
        imageInfo.format = srcFormat;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        imageInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
        imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

        if (table.vkCreateImage(device, &imageInfo, nullptr, &snapshotImage_) != VK_SUCCESS) {
            return false;
        }

        VkMemoryRequirements memReqs{};
        table.vkGetImageMemoryRequirements(device, snapshotImage_, &memReqs);

        uint32_t memType = findMemoryType(physDev, memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (memType == 0xFFFFFFFF) {
            cleanupSnapshot();
            return false;
        }

        VkMemoryAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocInfo.allocationSize = memReqs.size;
        allocInfo.memoryTypeIndex = memType;

        if (table.vkAllocateMemory(device, &allocInfo, nullptr, &snapshotMemory_) != VK_SUCCESS) {
            cleanupSnapshot();
            return false;
        }
        table.vkBindImageMemory(device, snapshotImage_, snapshotMemory_, 0);

        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = snapshotImage_;
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = srcFormat;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;

        if (table.vkCreateImageView(device, &viewInfo, nullptr, &snapshotImageView_) != VK_SUCCESS) {
            cleanupSnapshot();
            return false;
        }

        VkSamplerCreateInfo samplerInfo{};
        samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
        samplerInfo.magFilter = VK_FILTER_LINEAR;
        samplerInfo.minFilter = VK_FILTER_LINEAR;
        samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;

        if (table.vkCreateSampler(device, &samplerInfo, nullptr, &snapshotSampler_) != VK_SUCCESS) {
            cleanupSnapshot();
            return false;
        }

        VkDescriptorPoolSize poolSize{};
        poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        poolSize.descriptorCount = 1;

        VkDescriptorPoolCreateInfo poolInfo{};
        poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        poolInfo.poolSizeCount = 1;
        poolInfo.pPoolSizes = &poolSize;
        poolInfo.maxSets = 1;

        if (table.vkCreateDescriptorPool(device, &poolInfo, nullptr, &snapshotDescriptorPool_) != VK_SUCCESS) {
            cleanupSnapshot();
            return false;
        }

        VkDescriptorSetLayout layout = pipelines_.getTextDescLayout();
        VkDescriptorSetAllocateInfo dsAlloc{};
        dsAlloc.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dsAlloc.descriptorPool = snapshotDescriptorPool_;
        dsAlloc.descriptorSetCount = 1;
        dsAlloc.pSetLayouts = &layout;

        if (table.vkAllocateDescriptorSets(device, &dsAlloc, &snapshotDescriptorSet_) != VK_SUCCESS) {
            cleanupSnapshot();
            return false;
        }

        VkDescriptorImageInfo descImageInfo{};
        descImageInfo.sampler = snapshotSampler_;
        descImageInfo.imageView = snapshotImageView_;
        descImageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkWriteDescriptorSet writeDesc{};
        writeDesc.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writeDesc.dstSet = snapshotDescriptorSet_;
        writeDesc.dstBinding = 0;
        writeDesc.dstArrayElement = 0;
        writeDesc.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        writeDesc.descriptorCount = 1;
        writeDesc.pImageInfo = &descImageInfo;

        table.vkUpdateDescriptorSets(device, 1, &writeDesc, 0, nullptr);

        snapshotWidth_ = width_;
        snapshotHeight_ = height_;
    }

    VkCommandPool cmdPool = context_.getCommandPool();
    VkCommandBufferAllocateInfo cmdAlloc{};
    cmdAlloc.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cmdAlloc.commandPool = cmdPool;
    cmdAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cmdAlloc.commandBufferCount = 1;

    VkCommandBuffer cmd = VK_NULL_HANDLE;
    if (table.vkAllocateCommandBuffers(device, &cmdAlloc, &cmd) != VK_SUCCESS) {
        return false;
    }

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    table.vkBeginCommandBuffer(cmd, &beginInfo);

    VkImageMemoryBarrier barriers[2]{};
    barriers[0].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barriers[0].oldLayout = srcOldLayout;
    barriers[0].newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barriers[0].srcAccessMask = (srcOldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) ? 0 : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    barriers[0].dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    barriers[0].srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barriers[0].dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barriers[0].image = srcImage;
    barriers[0].subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};

    barriers[1].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barriers[1].oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    barriers[1].newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barriers[1].srcAccessMask = 0;
    barriers[1].dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barriers[1].srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barriers[1].dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barriers[1].image = snapshotImage_;
    barriers[1].subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};

    VkPipelineStageFlags srcStage = (srcOldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
        ? VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    table.vkCmdPipelineBarrier(cmd, srcStage | VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 2, barriers);

    VkImageCopy copyRegion{};
    copyRegion.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copyRegion.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copyRegion.extent = {width_, height_, 1};

    table.vkCmdCopyImage(cmd, srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, snapshotImage_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &copyRegion);

    barriers[0].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barriers[0].newLayout = srcOldLayout;
    barriers[0].srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    barriers[0].dstAccessMask = (srcOldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) ? 0 : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    barriers[1].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barriers[1].newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barriers[1].srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barriers[1].dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

    VkPipelineStageFlags dstStage = (srcOldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
        ? VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    table.vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, dstStage | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 2, barriers);

    table.vkEndCommandBuffer(cmd);

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;

    table.vkQueueSubmit(queue, 1, &submitInfo, VK_NULL_HANDLE);
    table.vkQueueWaitIdle(queue);

    table.vkFreeCommandBuffers(device, cmdPool, 1, &cmd);

    hasSnapshot_ = true;
    return true;
}

void SiliconVisVulkanPipeline::drawTransition(float offsetX, float alpha) {
    if (!initialized_ || !hasSnapshot_ || currentCmd_ == VK_NULL_HANDLE || alpha <= 0.001f) {
        return;
    }

    const float w = static_cast<float>(width_);
    const float h = static_cast<float>(height_);
    const float verts[] = {
        0.0f, 0.0f, 0.0f, 0.0f,
        w,    0.0f, 1.0f, 0.0f,
        0.0f, h,    0.0f, 1.0f,

        w,    0.0f, 1.0f, 0.0f,
        w,    h,    1.0f, 1.0f,
        0.0f, h,    0.0f, 1.0f,
    };

    size_t offsetBytes = vertexBuffer_.allocate(verts, sizeof(verts));
    if (offsetBytes == static_cast<size_t>(-1)) {
        return;
    }

    const auto& table = VkLoader::table();
    VkBuffer buf = vertexBuffer_.getBuffer();
    VkDeviceSize vkOffset = offsetBytes;
    table.vkCmdBindVertexBuffers(currentCmd_, 0, 1, &buf, &vkOffset);

    pipelines_.bindTransition(currentCmd_, w, h, offsetX, alpha, snapshotDescriptorSet_);
    table.vkCmdDraw(currentCmd_, 6, 1, 0, 0);
}

} // namespace silicon::vis::vk
