#include "vk_swapchain.h"
#include <algorithm>

namespace silicon::vis::vk {

namespace {

VkSampleCountFlagBits resolveMsaaSamples(VkContext* context, uint32_t requested) {
    int32_t deviceMax = context ? context->getCapabilities().maxMsaaSamples : 1;
    if (requested >= 4 && deviceMax >= 4) return VK_SAMPLE_COUNT_4_BIT;
    if (requested >= 2 && deviceMax >= 2) return VK_SAMPLE_COUNT_2_BIT;
    return VK_SAMPLE_COUNT_1_BIT;
}

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

VkSwapchain::~VkSwapchain() {
    release();
}

bool VkSwapchain::init(VkContext* context, VkSurfaceKHR surface, uint32_t width, uint32_t height, uint32_t msaaSamples) {
    release();
    context_ = context;
    surface_ = surface;

    msaaSamples_ = resolveMsaaSamples(context_, msaaSamples);

    return resize(width, height);
}

void VkSwapchain::release() {
    cleanupSwapchain();

    if (context_) {
        const auto& table = VkLoader::table();
        if (renderPass_ != VK_NULL_HANDLE) {
            table.vkDestroyRenderPass(context_->getDevice(), renderPass_, nullptr);
            renderPass_ = VK_NULL_HANDLE;
        }
        if (swapchain_ != VK_NULL_HANDLE) {
            table.vkDestroySwapchainKHR(context_->getDevice(), swapchain_, nullptr);
            swapchain_ = VK_NULL_HANDLE;
        }
        if (surface_ != VK_NULL_HANDLE) {
            context_->destroySurface(surface_);
            surface_ = VK_NULL_HANDLE;
        }
        context_ = nullptr;
    } else {
        renderPass_ = VK_NULL_HANDLE;
        swapchain_ = VK_NULL_HANDLE;
        surface_ = VK_NULL_HANDLE;
    }
}

void VkSwapchain::cleanupSwapchain() {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    for (auto fb : framebuffers_) {
        if (fb != VK_NULL_HANDLE) table.vkDestroyFramebuffer(device, fb, nullptr);
    }
    framebuffers_.clear();

    for (auto view : imageViews_) {
        if (view != VK_NULL_HANDLE) table.vkDestroyImageView(device, view, nullptr);
    }
    imageViews_.clear();
    images_.clear();

    if (msaaColorImageView_ != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, msaaColorImageView_, nullptr);
        msaaColorImageView_ = VK_NULL_HANDLE;
    }
    if (msaaColorImage_ != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, msaaColorImage_, nullptr);
        msaaColorImage_ = VK_NULL_HANDLE;
    }
    if (msaaColorMemory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, msaaColorMemory_, nullptr);
        msaaColorMemory_ = VK_NULL_HANDLE;
    }
}

bool VkSwapchain::setMsaaSamples(uint32_t msaaSamples) {
    VkSampleCountFlagBits want = resolveMsaaSamples(context_, msaaSamples);
    if (want == msaaSamples_) return true;
    msaaSamples_ = want;
    if (surface_ == VK_NULL_HANDLE || swapchain_ == VK_NULL_HANDLE) return true;
    const auto& table = VkLoader::table();
    table.vkDestroyRenderPass(context_->getDevice(), renderPass_, nullptr);
    renderPass_ = VK_NULL_HANDLE;
    return resize(extent_.width, extent_.height);
}

bool VkSwapchain::resize(uint32_t width, uint32_t height) {
    if (!context_ || surface_ == VK_NULL_HANDLE || width == 0 || height == 0) return false;

    const auto& table = VkLoader::table();
    table.vkDeviceWaitIdle(context_->getDevice());

    cleanupSwapchain();

    if (!createSwapchain(width, height)) return false;
    if (!createImageViews()) return false;
    if (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT && !createMsaaResources()) return false;
    if (renderPass_ == VK_NULL_HANDLE && !createRenderPass()) return false;
    if (!createFramebuffers()) return false;

    return true;
}

bool VkSwapchain::createSwapchain(uint32_t width, uint32_t height) {
    const auto& table = VkLoader::table();
    VkPhysicalDevice physDev = context_->getPhysicalDevice();
    VkDevice device = context_->getDevice();

    VkSurfaceCapabilitiesKHR caps{};
    table.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physDev, surface_, &caps);

    VkSurfaceTransformFlagBitsKHR preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    if (!(caps.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)) {
        preTransform = caps.currentTransform;
    }

    if (caps.currentExtent.width != 0xFFFFFFFF && caps.currentExtent.width > 0) {
        extent_ = caps.currentExtent;
    } else {
        extent_.width = std::clamp(width, caps.minImageExtent.width, caps.maxImageExtent.width);
        extent_.height = std::clamp(height, caps.minImageExtent.height, caps.maxImageExtent.height);
    }

    if (preTransform == VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR && width > 0 && height > 0) {
        if ((width > height && extent_.width < extent_.height) ||
            (width < height && extent_.width > extent_.height)) {
            std::swap(extent_.width, extent_.height);
        }
    }

    uint32_t minImages = 0;
    if (!context_->querySurfaceSupport(surface_, minImages, format_, presentMode_)) {
        return false;
    }

    VkCompositeAlphaFlagBitsKHR compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    VkCompositeAlphaFlagBitsKHR compositeAlphaFlags[] = {
#if defined(__ANDROID__)
        VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
#endif
        VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
    };
    for (auto flag : compositeAlphaFlags) {
        if (caps.supportedCompositeAlpha & flag) {
            compositeAlpha = flag;
            break;
        }
    }

    VkSwapchainCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    createInfo.surface = surface_;
    createInfo.minImageCount = minImages;
    createInfo.imageFormat = format_.format;
    createInfo.imageColorSpace = format_.colorSpace;
    createInfo.imageExtent = extent_;
    createInfo.imageArrayLayers = 1;
    VkImageUsageFlags usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    if (caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) {
        usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    }
    createInfo.imageUsage = usage;
    createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    createInfo.preTransform = preTransform;
    createInfo.compositeAlpha = compositeAlpha;
    createInfo.presentMode = presentMode_;
    createInfo.clipped = VK_TRUE;
    createInfo.oldSwapchain = swapchain_;

    VkSwapchainKHR newSwapchain = VK_NULL_HANDLE;
    if (table.vkCreateSwapchainKHR(device, &createInfo, nullptr, &newSwapchain) != VK_SUCCESS) {
        return false;
    }

    if (swapchain_ != VK_NULL_HANDLE) {
        table.vkDestroySwapchainKHR(device, swapchain_, nullptr);
    }
    swapchain_ = newSwapchain;

    uint32_t imageCount = 0;
    table.vkGetSwapchainImagesKHR(device, swapchain_, &imageCount, nullptr);
    images_.resize(imageCount);
    table.vkGetSwapchainImagesKHR(device, swapchain_, &imageCount, images_.data());

    VK_VIS_LOGI("swapchain %ux%u format=%d mode=%d images=%u",
                extent_.width, extent_.height,
                (int)format_.format, (int)presentMode_, imageCount);
    return !images_.empty();
}

bool VkSwapchain::createImageViews() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    imageViews_.resize(images_.size());
    for (size_t i = 0; i < images_.size(); ++i) {
        VkImageViewCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        createInfo.image = images_[i];
        createInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        createInfo.format = format_.format;
        createInfo.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        createInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        createInfo.subresourceRange.baseMipLevel = 0;
        createInfo.subresourceRange.levelCount = 1;
        createInfo.subresourceRange.baseArrayLayer = 0;
        createInfo.subresourceRange.layerCount = 1;

        if (table.vkCreateImageView(device, &createInfo, nullptr, &imageViews_[i]) != VK_SUCCESS) {
            return false;
        }
    }
    return true;
}

bool VkSwapchain::createMsaaResources() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    VkPhysicalDevice physDev = context_->getPhysicalDevice();

    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent.width = extent_.width;
    imageInfo.extent.height = extent_.height;
    imageInfo.extent.depth = 1;
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = format_.format;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    imageInfo.samples = msaaSamples_;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (table.vkCreateImage(device, &imageInfo, nullptr, &msaaColorImage_) != VK_SUCCESS) {
        return false;
    }

    VkMemoryRequirements memReqs{};
    table.vkGetImageMemoryRequirements(device, msaaColorImage_, &memReqs);

    uint32_t memType = findMemoryType(
        physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT
    );
    if (memType == 0xFFFFFFFF) {
        memType = findMemoryType(physDev, memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    }

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = memType;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &msaaColorMemory_) != VK_SUCCESS) {
        return false;
    }

    table.vkBindImageMemory(device, msaaColorImage_, msaaColorMemory_, 0);

    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = msaaColorImage_;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = format_.format;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    return table.vkCreateImageView(device, &viewInfo, nullptr, &msaaColorImageView_) == VK_SUCCESS;
}

bool VkSwapchain::createRenderPass() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    if (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT) {
        // MSAA attachment (index 0)
        VkAttachmentDescription msaaColorAttachment{};
        msaaColorAttachment.format = format_.format;
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

        // Resolve attachment (index 1) - resolves directly to swapchain image in tile memory
        VkAttachmentDescription resolveAttachment{};
        resolveAttachment.format = format_.format;
        resolveAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        resolveAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        resolveAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        resolveAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        resolveAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        resolveAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        resolveAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

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

        return table.vkCreateRenderPass(device, &renderPassInfo, nullptr, &renderPass_) == VK_SUCCESS;
    } else {
        // Non-MSAA direct swapchain attachment
        VkAttachmentDescription colorAttachment{};
        colorAttachment.format = format_.format;
        colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

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

        return table.vkCreateRenderPass(device, &renderPassInfo, nullptr, &renderPass_) == VK_SUCCESS;
    }
}

bool VkSwapchain::createFramebuffers() {
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    framebuffers_.resize(imageViews_.size());
    for (size_t i = 0; i < imageViews_.size(); ++i) {
        std::vector<VkImageView> attachments;
        if (msaaSamples_ > VK_SAMPLE_COUNT_1_BIT) {
            attachments.push_back(msaaColorImageView_);
            attachments.push_back(imageViews_[i]);
        } else {
            attachments.push_back(imageViews_[i]);
        }

        VkFramebufferCreateInfo fbInfo{};
        fbInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fbInfo.renderPass = renderPass_;
        fbInfo.attachmentCount = static_cast<uint32_t>(attachments.size());
        fbInfo.pAttachments = attachments.data();
        fbInfo.width = extent_.width;
        fbInfo.height = extent_.height;
        fbInfo.layers = 1;

        if (table.vkCreateFramebuffer(device, &fbInfo, nullptr, &framebuffers_[i]) != VK_SUCCESS) {
            return false;
        }
    }
    return true;
}

VkSwapchain::AcquireResult VkSwapchain::acquireNextImage(VkSemaphore signalSemaphore, uint32_t& outImageIndex) {
    if (swapchain_ == VK_NULL_HANDLE || !context_) return AcquireResult::Retry;

    const auto& table = VkLoader::table();
    // Bounded wait: an infinite wait here wedges the render thread when the
    // window dies mid-frame (surface teardown races a pending acquire).
    constexpr uint64_t kAcquireTimeoutNs = 1000000000ULL;
    VkResult res = table.vkAcquireNextImageKHR(
        context_->getDevice(),
        swapchain_,
        kAcquireTimeoutNs,
        signalSemaphore,
        VK_NULL_HANDLE,
        &outImageIndex
    );

    switch (res) {
        case VK_SUCCESS:
        case VK_SUBOPTIMAL_KHR:
            return AcquireResult::Ok;
        case VK_TIMEOUT:
        case VK_NOT_READY:
            return AcquireResult::Retry;
        case VK_ERROR_OUT_OF_DATE_KHR:
            return AcquireResult::Retry;
        case VK_ERROR_SURFACE_LOST_KHR:
        case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR:
            return AcquireResult::SurfaceLost;
        default:
            return AcquireResult::Retry;
    }
}

VkSwapchain::PresentResult VkSwapchain::present(VkQueue queue, uint32_t imageIndex, VkSemaphore waitSemaphore) {
    if (swapchain_ == VK_NULL_HANDLE || !context_) return PresentResult::Retry;

    const auto& table = VkLoader::table();

    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = (waitSemaphore != VK_NULL_HANDLE) ? 1 : 0;
    presentInfo.pWaitSemaphores = (waitSemaphore != VK_NULL_HANDLE) ? &waitSemaphore : nullptr;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &swapchain_;
    presentInfo.pImageIndices = &imageIndex;

    VkResult res = table.vkQueuePresentKHR(queue, &presentInfo);
    if (res == VK_SUCCESS) return PresentResult::Ok;
    // Suboptimal still presents correctly; only a real out-of-date or error
    // justifies the waitIdle + full resource rebuild.
    if (res == VK_SUBOPTIMAL_KHR) return PresentResult::Suboptimal;
    if (res == VK_ERROR_OUT_OF_DATE_KHR) return PresentResult::Retry;
    if (res == VK_ERROR_SURFACE_LOST_KHR) return PresentResult::SurfaceLost;
    return PresentResult::Retry;
}

} // namespace silicon::vis::vk
