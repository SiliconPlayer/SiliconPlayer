#include "vk_artwork_renderer.h"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

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

void makeTexturedQuad(float x, float y, float w, float h,
                      float u0, float v0, float u1, float v1,
                      float* out24Floats) {
    float x0 = x;
    float y0 = y;
    float x1 = x + w;
    float y1 = y + h;

    out24Floats[0] = x0; out24Floats[1] = y0; out24Floats[2] = u0; out24Floats[3] = v0;
    out24Floats[4] = x1; out24Floats[5] = y0; out24Floats[6] = u1; out24Floats[7] = v0;
    out24Floats[8] = x0; out24Floats[9] = y1; out24Floats[10] = u0; out24Floats[11] = v1;

    out24Floats[12] = x1; out24Floats[13] = y0; out24Floats[14] = u1; out24Floats[15] = v0;
    out24Floats[16] = x1; out24Floats[17] = y1; out24Floats[18] = u1; out24Floats[19] = v1;
    out24Floats[20] = x0; out24Floats[21] = y1; out24Floats[22] = u0; out24Floats[23] = v1;
}

bool buildBlurThumb(const std::vector<uint8_t>& src, int32_t srcW, int32_t srcH,
                    std::vector<uint8_t>& dst, int32_t& dstW, int32_t& dstH) {
    if (src.empty() || srcW <= 0 || srcH <= 0) return false;
    constexpr int32_t kThumbLongEdge = 48;
    const int32_t longEdge = std::max(srcW, srcH);
    const int32_t thumbLong = std::min(kThumbLongEdge, longEdge);
    dstW = std::max<int32_t>(1, static_cast<int32_t>(static_cast<int64_t>(srcW) * thumbLong / longEdge));
    dstH = std::max<int32_t>(1, static_cast<int32_t>(static_cast<int64_t>(srcH) * thumbLong / longEdge));
    dst.assign(static_cast<size_t>(dstW) * static_cast<size_t>(dstH) * 4, 0);
    for (int32_t y = 0; y < dstH; ++y) {
        const int32_t y0 = static_cast<int32_t>(static_cast<int64_t>(y) * srcH / dstH);
        const int32_t y1 = std::max(y0 + 1, static_cast<int32_t>(static_cast<int64_t>(y + 1) * srcH / dstH));
        for (int32_t x = 0; x < dstW; ++x) {
            const int32_t x0 = static_cast<int32_t>(static_cast<int64_t>(x) * srcW / dstW);
            const int32_t x1 = std::max(x0 + 1, static_cast<int32_t>(static_cast<int64_t>(x + 1) * srcW / dstW));
            int64_t r = 0;
            int64_t g = 0;
            int64_t b = 0;
            int64_t a = 0;
            for (int32_t sy = y0; sy < y1; ++sy) {
                for (int32_t sx = x0; sx < x1; ++sx) {
                    const uint8_t* px = &src[(static_cast<size_t>(sy) * static_cast<size_t>(srcW) + static_cast<size_t>(sx)) * 4];
                    r += px[0];
                    g += px[1];
                    b += px[2];
                    a += px[3];
                }
            }
            const int64_t n = static_cast<int64_t>(x1 - x0) * (y1 - y0);
            uint8_t* out = &dst[(static_cast<size_t>(y) * static_cast<size_t>(dstW) + static_cast<size_t>(x)) * 4];
            out[0] = static_cast<uint8_t>(r / n);
            out[1] = static_cast<uint8_t>(g / n);
            out[2] = static_cast<uint8_t>(b / n);
            out[3] = static_cast<uint8_t>(a / n);
        }
    }
    const std::vector<uint8_t> pre = dst;
    for (int32_t y = 0; y < dstH; ++y) {
        for (int32_t x = 0; x < dstW; ++x) {
            int64_t r = 0;
            int64_t g = 0;
            int64_t b = 0;
            int64_t a = 0;
            int n = 0;
            for (int32_t ky = -1; ky <= 1; ++ky) {
                for (int32_t kx = -1; kx <= 1; ++kx) {
                    const int32_t sx = x + kx;
                    const int32_t sy = y + ky;
                    if (sx < 0 || sy < 0 || sx >= dstW || sy >= dstH) continue;
                    const uint8_t* px = &pre[(static_cast<size_t>(sy) * static_cast<size_t>(dstW) + static_cast<size_t>(sx)) * 4];
                    r += px[0];
                    g += px[1];
                    b += px[2];
                    a += px[3];
                    ++n;
                }
            }
            uint8_t* out = &dst[(static_cast<size_t>(y) * static_cast<size_t>(dstW) + static_cast<size_t>(x)) * 4];
            out[0] = static_cast<uint8_t>(r / n);
            out[1] = static_cast<uint8_t>(g / n);
            out[2] = static_cast<uint8_t>(b / n);
            out[3] = static_cast<uint8_t>(a / n);
        }
    }
    return true;
}

void bleedTransparentTexels(std::vector<uint8_t>& rgba, int32_t width, int32_t height) {
    for (int pass = 0; pass < 4; ++pass) {
        bool changed = false;
        for (int32_t y = 0; y < height; ++y) {
            for (int32_t x = 0; x < width; ++x) {
                uint8_t* px = &rgba[(static_cast<size_t>(y) * width + x) * 4];
                if (px[3] != 0) continue;
                int r = 0;
                int g = 0;
                int b = 0;
                int n = 0;
                const int dx[4] = {-1, 1, 0, 0};
                const int dy[4] = {0, 0, -1, 1};
                for (int i = 0; i < 4; ++i) {
                    const int32_t nx = x + dx[i];
                    const int32_t ny = y + dy[i];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    const uint8_t* q = &rgba[(static_cast<size_t>(ny) * width + nx) * 4];
                    if (q[3] == 0) continue;
                    r += q[0];
                    g += q[1];
                    b += q[2];
                    ++n;
                }
                if (n == 0) continue;
                px[0] = static_cast<uint8_t>(r / n);
                px[1] = static_cast<uint8_t>(g / n);
                px[2] = static_cast<uint8_t>(b / n);
                changed = true;
            }
        }
        if (!changed) break;
    }
}

} // namespace

bool VkArtworkRenderer::init(VkContext* context, VkDescriptorSetLayout texDescLayout) {
    release();
    context_ = context;
    texDescLayout_ = texDescLayout;

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    VkDescriptorPoolSize poolSize{};
    poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    poolSize.descriptorCount = 6;

    VkDescriptorPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    poolInfo.maxSets = 6;

    if (table.vkCreateDescriptorPool(device, &poolInfo, nullptr, &descPool_) != VK_SUCCESS) {
        return false;
    }

    VkDescriptorSetLayout layouts[6] = {
        texDescLayout_, texDescLayout_, texDescLayout_,
        texDescLayout_, texDescLayout_, texDescLayout_
    };

    VkDescriptorSetAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocInfo.descriptorPool = descPool_;
    allocInfo.descriptorSetCount = 6;
    allocInfo.pSetLayouts = layouts;

    VkDescriptorSet sets[6]{};
    if (table.vkAllocateDescriptorSets(device, &allocInfo, sets) != VK_SUCCESS) {
        release();
        return false;
    }

    liveArtworkDescSet_ = sets[0];
    liveBlurDescSet_ = sets[1];
    liveIconDescSet_ = sets[2];
    prevArtworkDescSet_ = sets[3];
    prevBlurDescSet_ = sets[4];
    prevIconDescSet_ = sets[5];

    VkSamplerCreateInfo samplerInfo{};
    samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;

    if (table.vkCreateSampler(device, &samplerInfo, nullptr, &sampler_) != VK_SUCCESS) {
        release();
        return false;
    }

    return true;
}

void VkArtworkRenderer::release() {
    releasePrevState();
    releaseTexture(artwork_);
    releaseTexture(blur_);
    releaseTexture(icon_);

    if (context_) {
        const auto& table = VkLoader::table();
        VkDevice device = context_->getDevice();

        if (sampler_ != VK_NULL_HANDLE) {
            table.vkDestroySampler(device, sampler_, nullptr);
            sampler_ = VK_NULL_HANDLE;
        }
        if (descPool_ != VK_NULL_HANDLE) {
            table.vkDestroyDescriptorPool(device, descPool_, nullptr);
            descPool_ = VK_NULL_HANDLE;
        }
        liveArtworkDescSet_ = VK_NULL_HANDLE;
        liveBlurDescSet_ = VK_NULL_HANDLE;
        liveIconDescSet_ = VK_NULL_HANDLE;
        prevArtworkDescSet_ = VK_NULL_HANDLE;
        prevBlurDescSet_ = VK_NULL_HANDLE;
        prevIconDescSet_ = VK_NULL_HANDLE;
        context_ = nullptr;
    }
}

void VkArtworkRenderer::releaseTexture(TextureResource& res) {
    if (!context_ || res.image == VK_NULL_HANDLE) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    if (res.view != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, res.view, nullptr);
        res.view = VK_NULL_HANDLE;
    }
    if (res.image != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, res.image, nullptr);
        res.image = VK_NULL_HANDLE;
    }
    if (res.memory != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, res.memory, nullptr);
        res.memory = VK_NULL_HANDLE;
    }
    res.width = 0;
    res.height = 0;
}

void VkArtworkRenderer::releasePrevState() {
    if (prev_.ownsArtwork) releaseTexture(prev_.artwork);
    if (prev_.ownsBlur) releaseTexture(prev_.blur);
    if (prev_.ownsIcon) releaseTexture(prev_.icon);
    prev_ = {};
}

VkArtworkRenderer::ContentState VkArtworkRenderer::currentState() const {
    ContentState s;
    s.artwork = artwork_;
    s.blur = blur_;
    s.icon = icon_;
    s.artworkDescSet = liveArtworkDescSet_;
    s.blurDescSet = liveBlurDescSet_;
    s.iconDescSet = liveIconDescSet_;
    s.primaryArgb = primaryColorArgb_;
    s.surfaceArgb = surfaceColorArgb_;
    return s;
}

bool VkArtworkRenderer::uploadTexture(const uint8_t* rgbaPixels, int32_t width, int32_t height, TextureResource& outRes, VkDescriptorSet descSet) {
    if (!context_ || !rgbaPixels || width <= 0 || height <= 0 || descSet == VK_NULL_HANDLE) {
        return false;
    }

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    VkPhysicalDevice physDev = context_->getPhysicalDevice();
    VkCommandPool cmdPool = context_->getCommandPool();
    VkQueue queue = context_->getGraphicsQueue();

    table.vkDeviceWaitIdle(device);
    releaseTexture(outRes);

    size_t imageSize = static_cast<size_t>(width * height * 4);

    VkBuffer stagingBuffer = VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory = VK_NULL_HANDLE;

    VkBufferCreateInfo bufInfo{};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size = imageSize;
    bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (table.vkCreateBuffer(device, &bufInfo, nullptr, &stagingBuffer) != VK_SUCCESS) {
        return false;
    }

    VkMemoryRequirements memReqs{};
    table.vkGetBufferMemoryRequirements(device, stagingBuffer, &memReqs);

    uint32_t stagingMemType = findMemoryType(
        physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
    );
    if (stagingMemType == 0xFFFFFFFF) {
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        return false;
    }

    VkMemoryAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = stagingMemType;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &stagingMemory) != VK_SUCCESS) {
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        return false;
    }

    table.vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0);

    void* mapped = nullptr;
    table.vkMapMemory(device, stagingMemory, 0, imageSize, 0, &mapped);
    std::memcpy(mapped, rgbaPixels, imageSize);
    table.vkUnmapMemory(device, stagingMemory);

    VkImageCreateInfo imgInfo{};
    imgInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imgInfo.imageType = VK_IMAGE_TYPE_2D;
    imgInfo.extent.width = static_cast<uint32_t>(width);
    imgInfo.extent.height = static_cast<uint32_t>(height);
    imgInfo.extent.depth = 1;
    imgInfo.mipLevels = 1;
    imgInfo.arrayLayers = 1;
    imgInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imgInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imgInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imgInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
    imgInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imgInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (table.vkCreateImage(device, &imgInfo, nullptr, &outRes.image) != VK_SUCCESS) {
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        table.vkFreeMemory(device, stagingMemory, nullptr);
        return false;
    }

    table.vkGetImageMemoryRequirements(device, outRes.image, &memReqs);

    uint32_t devMemType = findMemoryType(physDev, memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (devMemType == 0xFFFFFFFF) {
        releaseTexture(outRes);
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        table.vkFreeMemory(device, stagingMemory, nullptr);
        return false;
    }

    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = devMemType;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &outRes.memory) != VK_SUCCESS) {
        releaseTexture(outRes);
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        table.vkFreeMemory(device, stagingMemory, nullptr);
        return false;
    }

    table.vkBindImageMemory(device, outRes.image, outRes.memory, 0);

    VkCommandBufferAllocateInfo cmdAlloc{};
    cmdAlloc.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cmdAlloc.commandPool = cmdPool;
    cmdAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cmdAlloc.commandBufferCount = 1;

    VkCommandBuffer copyCmd = VK_NULL_HANDLE;
    table.vkAllocateCommandBuffers(device, &cmdAlloc, &copyCmd);

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    table.vkBeginCommandBuffer(copyCmd, &beginInfo);

    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = outRes.image;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount = 1;
    barrier.srcAccessMask = 0;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;

    table.vkCmdPipelineBarrier(copyCmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

    VkBufferImageCopy region{};
    region.bufferOffset = 0;
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.mipLevel = 0;
    region.imageSubresource.baseArrayLayer = 0;
    region.imageSubresource.layerCount = 1;
    region.imageOffset = {0, 0, 0};
    region.imageExtent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1};

    table.vkCmdCopyBufferToImage(copyCmd, stagingBuffer, outRes.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

    table.vkCmdPipelineBarrier(copyCmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

    table.vkEndCommandBuffer(copyCmd);

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &copyCmd;

    table.vkQueueSubmit(queue, 1, &submitInfo, VK_NULL_HANDLE);
    table.vkQueueWaitIdle(queue);

    table.vkFreeCommandBuffers(device, cmdPool, 1, &copyCmd);
    table.vkDestroyBuffer(device, stagingBuffer, nullptr);
    table.vkFreeMemory(device, stagingMemory, nullptr);

    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = outRes.image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    if (table.vkCreateImageView(device, &viewInfo, nullptr, &outRes.view) != VK_SUCCESS) {
        releaseTexture(outRes);
        return false;
    }

    VkDescriptorImageInfo descImg{};
    descImg.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    descImg.imageView = outRes.view;
    descImg.sampler = sampler_;

    VkWriteDescriptorSet writeSet{};
    writeSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writeSet.dstSet = descSet;
    writeSet.dstBinding = 0;
    writeSet.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    writeSet.descriptorCount = 1;
    writeSet.pImageInfo = &descImg;

    table.vkUpdateDescriptorSets(device, 1, &writeSet, 0, nullptr);

    outRes.width = width;
    outRes.height = height;
    return true;
}

void VkArtworkRenderer::setArtworkPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!rgbaPixels || width <= 0 || height <= 0) {
        clearArtwork();
        return;
    }
    pendingArtworkPixels_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    artworkWidth_ = width;
    artworkHeight_ = height;
    artworkTextureDirty_ = true;
}

void VkArtworkRenderer::clearArtwork() {
    pendingArtworkPixels_.clear();
    artworkWidth_ = 0;
    artworkHeight_ = 0;
    artworkTextureDirty_ = true;
}

void VkArtworkRenderer::setIconPixels(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!rgbaPixels || width <= 0 || height <= 0) {
        clearIcon();
        return;
    }
    pendingIconPixels_.assign(rgbaPixels, rgbaPixels + (width * height * 4));
    iconWidth_ = width;
    iconHeight_ = height;
    iconTextureDirty_ = true;
}

void VkArtworkRenderer::clearIcon() {
    pendingIconPixels_.clear();
    iconWidth_ = 0;
    iconHeight_ = 0;
    iconTextureDirty_ = true;
}

void VkArtworkRenderer::setTheme(uint32_t primaryColorArgb, uint32_t surfaceColorArgb, int32_t placeholderIconType) {
    themeReceived_ = true;
    if (primaryColorArgb == primaryColorArgb_ &&
        surfaceColorArgb == surfaceColorArgb_ &&
        placeholderIconType == placeholderIconType_) {
        return;
    }
    pendingPrimaryColorArgb_ = primaryColorArgb;
    pendingSurfaceColorArgb_ = surfaceColorArgb;
    pendingPlaceholderIconType_ = placeholderIconType;
    themeDirty_ = true;
}

void VkArtworkRenderer::ensureArtworkTexture() {
    if (!artworkTextureDirty_) return;
    artworkTextureDirty_ = false;

    releaseTexture(artwork_);
    releaseTexture(blur_);

    if (!pendingArtworkPixels_.empty() && artworkWidth_ > 0 && artworkHeight_ > 0) {
        uploadTexture(pendingArtworkPixels_.data(), artworkWidth_, artworkHeight_, artwork_, liveArtworkDescSet_);

        std::vector<uint8_t> thumb;
        int32_t thumbW = 0;
        int32_t thumbH = 0;
        if (buildBlurThumb(pendingArtworkPixels_, artworkWidth_, artworkHeight_, thumb, thumbW, thumbH)) {
            uploadTexture(thumb.data(), thumbW, thumbH, blur_, liveBlurDescSet_);
        }
    }
}

void VkArtworkRenderer::ensureIconTexture() {
    if (!iconTextureDirty_) return;
    iconTextureDirty_ = false;

    releaseTexture(icon_);

    if (!pendingIconPixels_.empty() && iconWidth_ > 0 && iconHeight_ > 0) {
        bleedTransparentTexels(pendingIconPixels_, iconWidth_, iconHeight_);
        uploadTexture(pendingIconPixels_.data(), iconWidth_, iconHeight_, icon_, liveIconDescSet_);
    }
}

void VkArtworkRenderer::draw(VkCommandBuffer cmd,
                             VkPrimitivePipelines* pipelines,
                             VkDynamicVertexBuffer* dynBuffer,
                             float surfaceWidth,
                             float surfaceHeight,
                             float density) {
    if (!context_ || !pipelines || !dynBuffer || surfaceWidth <= 0.0f || surfaceHeight <= 0.0f) return;

    const long long nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    const float dt = monoLastNs_ == 0 ? 0.0f
        : std::min(static_cast<float>(nowNs - monoLastNs_) * 1e-9f, 0.1f);
    monoLastNs_ = nowNs;
    const float step = dt / 0.4f;
    monoMix_ = monoTarget_
        ? std::min(1.0f, monoMix_ + step)
        : std::max(0.0f, monoMix_ - step);

    if (!showArtworkBackground_) {
        if (themeDirty_) {
            themeDirty_ = false;
            primaryColorArgb_ = pendingPrimaryColorArgb_;
            surfaceColorArgb_ = pendingSurfaceColorArgb_;
            placeholderIconType_ = pendingPlaceholderIconType_;
        }
        releasePrevState();
        fadeStartNs_ = -1;
        return;
    }

    if (!themeReceived_) {
        // Pre-theme frames must not show the unthemed default backdrop;
        // the pass clear color stays on screen.
        return;
    }

    if (artworkTextureDirty_ || iconTextureDirty_ || themeDirty_) {
        releasePrevState();
        prev_ = currentState();
        const auto& table = VkLoader::table();
        VkDevice device = context_->getDevice();

        if (artworkTextureDirty_) {
            prev_.ownsArtwork = true;
            prev_.ownsBlur = true;
            prev_.artworkDescSet = prevArtworkDescSet_;
            prev_.blurDescSet = prevBlurDescSet_;

            if (artwork_.view != VK_NULL_HANDLE) {
                VkDescriptorImageInfo descImg{};
                descImg.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                descImg.imageView = artwork_.view;
                descImg.sampler = sampler_;

                VkWriteDescriptorSet writeSet{};
                writeSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
                writeSet.dstSet = prevArtworkDescSet_;
                writeSet.dstBinding = 0;
                writeSet.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                writeSet.descriptorCount = 1;
                writeSet.pImageInfo = &descImg;
                table.vkUpdateDescriptorSets(device, 1, &writeSet, 0, nullptr);
            }
            if (blur_.view != VK_NULL_HANDLE) {
                VkDescriptorImageInfo descImg{};
                descImg.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                descImg.imageView = blur_.view;
                descImg.sampler = sampler_;

                VkWriteDescriptorSet writeSet{};
                writeSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
                writeSet.dstSet = prevBlurDescSet_;
                writeSet.dstBinding = 0;
                writeSet.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                writeSet.descriptorCount = 1;
                writeSet.pImageInfo = &descImg;
                table.vkUpdateDescriptorSets(device, 1, &writeSet, 0, nullptr);
            }

            artwork_ = {};
            blur_ = {};
        }
        if (iconTextureDirty_) {
            prev_.ownsIcon = true;
            prev_.iconDescSet = prevIconDescSet_;

            if (icon_.view != VK_NULL_HANDLE) {
                VkDescriptorImageInfo descImg{};
                descImg.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                descImg.imageView = icon_.view;
                descImg.sampler = sampler_;

                VkWriteDescriptorSet writeSet{};
                writeSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
                writeSet.dstSet = prevIconDescSet_;
                writeSet.dstBinding = 0;
                writeSet.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                writeSet.descriptorCount = 1;
                writeSet.pImageInfo = &descImg;
                table.vkUpdateDescriptorSets(device, 1, &writeSet, 0, nullptr);
            }

            icon_ = {};
        }
        if (themeDirty_) {
            themeDirty_ = false;
            primaryColorArgb_ = pendingPrimaryColorArgb_;
            surfaceColorArgb_ = pendingSurfaceColorArgb_;
            placeholderIconType_ = pendingPlaceholderIconType_;
        }
        if (prev_.artwork.image == VK_NULL_HANDLE && prev_.icon.image == VK_NULL_HANDLE) {
            fadeStartNs_ = -1;
        } else {
            fadeStartNs_ = nowNs;
        }
    }

    ensureArtworkTexture();
    ensureIconTexture();

    drawArtworkOrFallback(cmd, pipelines, dynBuffer, currentState(), surfaceWidth, surfaceHeight, density, 1.0f);

    constexpr float kFadeSeconds = 0.35f;
    if (fadeStartNs_ >= 0) {
        const float p = static_cast<float>(nowNs - fadeStartNs_) * 1e-9f / kFadeSeconds;
        if (p >= 1.0f) {
            fadeStartNs_ = -1;
            releasePrevState();
        } else {
            const float eased = p * p * (3.0f - 2.0f * p);
            drawArtworkOrFallback(cmd, pipelines, dynBuffer, prev_, surfaceWidth, surfaceHeight, density, 1.0f - eased);
        }
    }

    drawContrastBackdrop(cmd, pipelines, dynBuffer, surfaceWidth, surfaceHeight);
}

void VkArtworkRenderer::drawGradientBackground(VkCommandBuffer cmd,
                                               VkPrimitivePipelines* pipelines,
                                               VkDynamicVertexBuffer* dynBuffer,
                                               const ContentState& state,
                                               float surfaceWidth,
                                               float surfaceHeight,
                                               float density,
                                               bool drawCircle,
                                               float monoMix,
                                               float alpha) {
    const float primR = ((state.primaryArgb >> 16) & 0xFF) / 255.0f;
    const float primG = ((state.primaryArgb >> 8) & 0xFF) / 255.0f;
    const float primB = (state.primaryArgb & 0xFF) / 255.0f;

    const float surfR = ((state.surfaceArgb >> 16) & 0xFF) / 255.0f;
    const float surfG = ((state.surfaceArgb >> 8) & 0xFF) / 255.0f;
    const float surfB = (state.surfaceArgb & 0xFF) / 255.0f;

    const float k = 1.0f - monoMix;
    float centerColor[4] = {
        ((surfR * 0.72f) + (primR * 0.28f)) * k,
        ((surfG * 0.72f) + (primG * 0.28f)) * k,
        ((surfB * 0.72f) + (primB * 0.28f)) * k,
        1.0f
    };
    float edgeColor[4] = {
        surfR * k,
        surfG * k,
        surfB * k,
        1.0f
    };

    float circleRadiusPx = std::min(60.0f * std::max(1.0f, density), std::min(surfaceWidth, surfaceHeight) * 0.35f);
    const float discR = primR + (1.0f - primR) * monoMix;
    const float discG = primG + (1.0f - primG) * monoMix;
    const float discB = primB + (1.0f - primB) * monoMix;
    float circleColor[4] = {
        discR,
        discG,
        discB,
        drawCircle ? 0.14f : 0.0f
    };

    float quad[24];
    makeTexturedQuad(0.0f, 0.0f, surfaceWidth, surfaceHeight, 0.0f, 0.0f, 1.0f, 1.0f, quad);

    size_t offset = dynBuffer->allocate(quad, sizeof(quad));
    if (offset == static_cast<size_t>(-1)) return;

    const auto& table = VkLoader::table();
    VkBuffer buf = dynBuffer->getBuffer();
    VkDeviceSize vkOffset = offset;
    table.vkCmdBindVertexBuffers(cmd, 0, 1, &buf, &vkOffset);

    pipelines->bindArtworkBg(cmd, surfaceWidth, surfaceHeight, centerColor, edgeColor, circleColor, circleRadiusPx, alpha);
    table.vkCmdDraw(cmd, 6, 1, 0, 0);
}

void VkArtworkRenderer::drawBlurFill(VkCommandBuffer cmd,
                                     VkPrimitivePipelines* pipelines,
                                     VkDynamicVertexBuffer* dynBuffer,
                                     const ContentState& state,
                                     float surfaceWidth,
                                     float surfaceHeight,
                                     float alpha) {
    if (state.blur.image == VK_NULL_HANDLE || state.artwork.width <= 0 || state.artwork.height <= 0) return;
    if (surfaceWidth <= 0.0f || surfaceHeight <= 0.0f) return;

    const float artAspect = static_cast<float>(state.artwork.width) / static_cast<float>(state.artwork.height);
    const float canvasAspect = surfaceWidth / surfaceHeight;
    if (artAspect == canvasAspect) return;

    float u0 = 0.0f;
    float v0 = 0.0f;
    float u1 = 1.0f;
    float v1 = 1.0f;
    if (artAspect > canvasAspect) {
        const float frac = canvasAspect / artAspect;
        u0 = (1.0f - frac) * 0.5f;
        u1 = 1.0f - u0;
    } else {
        const float frac = artAspect / canvasAspect;
        v0 = (1.0f - frac) * 0.5f;
        v1 = 1.0f - v0;
    }

    float quad[24];
    makeTexturedQuad(0.0f, 0.0f, surfaceWidth, surfaceHeight, u0, v0, u1, v1, quad);

    size_t offset = dynBuffer->allocate(quad, sizeof(quad));
    if (offset == static_cast<size_t>(-1)) return;

    const auto& table = VkLoader::table();
    VkBuffer buf = dynBuffer->getBuffer();
    VkDeviceSize vkOffset = offset;
    table.vkCmdBindVertexBuffers(cmd, 0, 1, &buf, &vkOffset);

    pipelines->bindArtworkTex(cmd, surfaceWidth, surfaceHeight, 1.0f, 1.0f, 1.0f, alpha, 0.0f, state.blurDescSet);
    table.vkCmdDraw(cmd, 6, 1, 0, 0);
}

void VkArtworkRenderer::drawArtworkOrFallback(VkCommandBuffer cmd,
                                             VkPrimitivePipelines* pipelines,
                                             VkDynamicVertexBuffer* dynBuffer,
                                             const ContentState& state,
                                             float surfaceWidth,
                                             float surfaceHeight,
                                             float density,
                                             float alpha) {
    if (alpha <= 0.001f) return;

    if (state.artwork.image != VK_NULL_HANDLE && state.artwork.width > 0 && state.artwork.height > 0) {
        drawGradientBackground(cmd, pipelines, dynBuffer, state, surfaceWidth, surfaceHeight, density, false, 0.0f, alpha);
        drawBlurFill(cmd, pipelines, dynBuffer, state, surfaceWidth, surfaceHeight, alpha);

        float imgW = static_cast<float>(state.artwork.width);
        float imgH = static_cast<float>(state.artwork.height);
        float scale = std::min(surfaceWidth / imgW, surfaceHeight / imgH);
        float dstW = imgW * scale;
        float dstH = imgH * scale;
        float dstX = (surfaceWidth - dstW) * 0.5f;
        float dstY = (surfaceHeight - dstH) * 0.5f;

        float quad[24];
        makeTexturedQuad(dstX, dstY, dstW, dstH, 0.0f, 0.0f, 1.0f, 1.0f, quad);

        size_t offset = dynBuffer->allocate(quad, sizeof(quad));
        if (offset != static_cast<size_t>(-1)) {
            const auto& table = VkLoader::table();
            VkBuffer buf = dynBuffer->getBuffer();
            VkDeviceSize vkOffset = offset;
            table.vkCmdBindVertexBuffers(cmd, 0, 1, &buf, &vkOffset);

            pipelines->bindArtworkTex(cmd, surfaceWidth, surfaceHeight, 1.0f, 1.0f, 1.0f, alpha, 0.0f, state.artworkDescSet);
            table.vkCmdDraw(cmd, 6, 1, 0, 0);
        }
    } else {
        drawGradientBackground(cmd, pipelines, dynBuffer, state, surfaceWidth, surfaceHeight, density, true, monoMix_, alpha);

        float circleRadiusPx = std::min(60.0f * std::max(1.0f, density), std::min(surfaceWidth, surfaceHeight) * 0.35f);
        if (state.icon.image != VK_NULL_HANDLE && state.icon.width > 0 && state.icon.height > 0) {
            float iconSizePx = std::min(64.0f * std::max(1.0f, density), circleRadiusPx * (64.0f / 60.0f));
            float iconX = (surfaceWidth - iconSizePx) * 0.5f;
            float iconY = (surfaceHeight - iconSizePx) * 0.5f;

            float iconQuad[24];
            makeTexturedQuad(iconX, iconY, iconSizePx, iconSizePx, 0.0f, 0.0f, 1.0f, 1.0f, iconQuad);

            size_t offset = dynBuffer->allocate(iconQuad, sizeof(iconQuad));
            if (offset != static_cast<size_t>(-1)) {
                const auto& table = VkLoader::table();
                VkBuffer buf = dynBuffer->getBuffer();
                VkDeviceSize vkOffset = offset;
                table.vkCmdBindVertexBuffers(cmd, 0, 1, &buf, &vkOffset);

                pipelines->bindArtworkTex(cmd, surfaceWidth, surfaceHeight, 1.0f, 1.0f, 1.0f, alpha, monoMix_, state.iconDescSet);
                table.vkCmdDraw(cmd, 6, 1, 0, 0);
            }
        }
    }
}

void VkArtworkRenderer::drawContrastBackdrop(VkCommandBuffer cmd,
                                             VkPrimitivePipelines* pipelines,
                                             VkDynamicVertexBuffer* dynBuffer,
                                             float surfaceWidth,
                                             float surfaceHeight) {
    if (contrastMode_ == SILICON_VIS_CONTRAST_NONE) return;

    float quad[24];
    makeTexturedQuad(0.0f, 0.0f, surfaceWidth, surfaceHeight, 0.0f, 0.0f, 1.0f, 1.0f, quad);

    size_t offset = dynBuffer->allocate(quad, sizeof(quad));
    if (offset == static_cast<size_t>(-1)) return;

    const auto& table = VkLoader::table();
    VkBuffer buf = dynBuffer->getBuffer();
    VkDeviceSize vkOffset = offset;
    table.vkCmdBindVertexBuffers(cmd, 0, 1, &buf, &vkOffset);

    pipelines->bindContrast(cmd, surfaceWidth, surfaceHeight, static_cast<int32_t>(contrastMode_), contrastScrimArgb_);
    table.vkCmdDraw(cmd, 6, 1, 0, 0);
}

} // namespace silicon::vis::vk
