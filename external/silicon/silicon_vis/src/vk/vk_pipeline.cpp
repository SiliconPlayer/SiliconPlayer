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
        if (!createOffscreenResources()) {
            release();
            return false;
        }
        initialRenderPass = offscreenRenderPass_;
        samples = VK_SAMPLE_COUNT_1_BIT;
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

    fontAtlas_.release();
    vertexBuffer_.release();
    pipelines_.release();
    swapchain_.release();
    context_.release();

    initialized_ = false;
    isSurfaceMode_ = false;
    width_ = 0;
    height_ = 0;
}

bool SiliconVisVulkanPipeline::resize(uint32_t width, uint32_t height, float density) {
    if (!initialized_ || width == 0 || height == 0) return false;
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

    // 3. Render Pass
    VkAttachmentDescription colorAttachment{};
    colorAttachment.format = VK_FORMAT_R8G8B8A8_UNORM;
    colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
    colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    colorAttachment.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkAttachmentReference colorAttachmentRef{};
    colorAttachmentRef.attachment = 0;
    colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorAttachmentRef;

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 1;
    renderPassInfo.pAttachments = &colorAttachment;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;

    if (table.vkCreateRenderPass(device, &renderPassInfo, nullptr, &offscreenRenderPass_) != VK_SUCCESS) {
        return false;
    }

    // 4. Framebuffer
    VkFramebufferCreateInfo fbInfo{};
    fbInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fbInfo.renderPass = offscreenRenderPass_;
    fbInfo.attachmentCount = 1;
    fbInfo.pAttachments = &offscreenImageView_;
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
    if (offscreenRenderPass_ != VK_NULL_HANDLE) {
        table.vkDestroyRenderPass(device, offscreenRenderPass_, nullptr);
        offscreenRenderPass_ = VK_NULL_HANDLE;
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
    VkClearValue clearColor{};
    clearColor.color.float32[0] = clearColor_[0];
    clearColor.color.float32[1] = clearColor_[1];
    clearColor.color.float32[2] = clearColor_[2];
    clearColor.color.float32[3] = clearColor_[3];

    VkRenderPassBeginInfo passInfo{};
    passInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    passInfo.renderPass = targetRenderPass;
    passInfo.framebuffer = targetFramebuffer;
    passInfo.renderArea.offset = {0, 0};
    passInfo.renderArea.extent = {width_, height_};
    passInfo.clearValueCount = 1;
    passInfo.pClearValues = &clearColor;

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

} // namespace silicon::vis::vk
