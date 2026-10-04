#include "vk_font_atlas.h"
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

} // namespace

VkFontAtlas::~VkFontAtlas() {
    release();
}

bool VkFontAtlas::init(VkContext* context, VkDescriptorSetLayout descLayout) {
    release();
    context_ = context;
    descLayout_ = descLayout;

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    VkDescriptorPoolSize poolSize{};
    poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    poolSize.descriptorCount = 1;

    VkDescriptorPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    poolInfo.maxSets = 1;

    if (table.vkCreateDescriptorPool(device, &poolInfo, nullptr, &descPool_) != VK_SUCCESS) {
        return false;
    }

    VkDescriptorSetAllocateInfo allocInfo{};
    allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocInfo.descriptorPool = descPool_;
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &descLayout_;

    if (table.vkAllocateDescriptorSets(device, &allocInfo, &descriptorSet_) != VK_SUCCESS) {
        release();
        return false;
    }

    // Create Sampler (Linear filtering, clamp to edge)
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

void VkFontAtlas::release() {
    cleanupImage();

    if (!context_) return;
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
    descriptorSet_ = VK_NULL_HANDLE;
    descLayout_ = VK_NULL_HANDLE;
    context_ = nullptr;
}

void VkFontAtlas::cleanupImage() {
    if (!context_) return;
    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();

    if (imageView_ != VK_NULL_HANDLE) {
        table.vkDestroyImageView(device, imageView_, nullptr);
        imageView_ = VK_NULL_HANDLE;
    }
    if (image_ != VK_NULL_HANDLE) {
        table.vkDestroyImage(device, image_, nullptr);
        image_ = VK_NULL_HANDLE;
    }
    if (memory_ != VK_NULL_HANDLE) {
        table.vkFreeMemory(device, memory_, nullptr);
        memory_ = VK_NULL_HANDLE;
    }
    width_ = 0;
    height_ = 0;
}

bool VkFontAtlas::updateAtlas(const uint8_t* rgbaPixels, int32_t width, int32_t height) {
    if (!context_ || !rgbaPixels || width <= 0 || height <= 0 || descriptorSet_ == VK_NULL_HANDLE) {
        return false;
    }

    const auto& table = VkLoader::table();
    VkDevice device = context_->getDevice();
    VkPhysicalDevice physDev = context_->getPhysicalDevice();
    VkCommandPool cmdPool = context_->getCommandPool();
    VkQueue queue = context_->getGraphicsQueue();

    table.vkDeviceWaitIdle(device);
    cleanupImage();

    width_ = width;
    height_ = height;
    size_t imageSize = static_cast<size_t>(width * height * 4);

    // 1. Staging buffer
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
    memcpy(mapped, rgbaPixels, imageSize);
    table.vkUnmapMemory(device, stagingMemory);

    // 2. Device Image
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

    if (table.vkCreateImage(device, &imgInfo, nullptr, &image_) != VK_SUCCESS) {
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        table.vkFreeMemory(device, stagingMemory, nullptr);
        return false;
    }

    table.vkGetImageMemoryRequirements(device, image_, &memReqs);

    uint32_t devMemType = findMemoryType(physDev, memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (devMemType == 0xFFFFFFFF) {
        cleanupImage();
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        table.vkFreeMemory(device, stagingMemory, nullptr);
        return false;
    }

    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = devMemType;

    if (table.vkAllocateMemory(device, &allocInfo, nullptr, &memory_) != VK_SUCCESS) {
        cleanupImage();
        table.vkDestroyBuffer(device, stagingBuffer, nullptr);
        table.vkFreeMemory(device, stagingMemory, nullptr);
        return false;
    }

    table.vkBindImageMemory(device, image_, memory_, 0);

    // 3. One-shot command buffer copy
    VkCommandBufferAllocateInfo cmdAlloc{};
    cmdAlloc.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cmdAlloc.commandPool = cmdPool;
    cmdAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cmdAlloc.commandBufferCount = 1;

    VkCommandBuffer cmd = VK_NULL_HANDLE;
    table.vkAllocateCommandBuffers(device, &cmdAlloc, &cmd);

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    table.vkBeginCommandBuffer(cmd, &beginInfo);

    // Transition to DST_OPTIMAL
    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image_;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount = 1;
    barrier.srcAccessMask = 0;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;

    table.vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

    VkBufferImageCopy region{};
    region.bufferOffset = 0;
    region.bufferRowLength = 0;
    region.bufferImageHeight = 0;
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.mipLevel = 0;
    region.imageSubresource.baseArrayLayer = 0;
    region.imageSubresource.layerCount = 1;
    region.imageOffset = {0, 0, 0};
    region.imageExtent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1};

    table.vkCmdCopyBufferToImage(cmd, stagingBuffer, image_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    // Transition to SHADER_READ_ONLY_OPTIMAL
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

    table.vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

    table.vkEndCommandBuffer(cmd);

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;

    table.vkQueueSubmit(queue, 1, &submitInfo, VK_NULL_HANDLE);
    table.vkQueueWaitIdle(queue);

    table.vkFreeCommandBuffers(device, cmdPool, 1, &cmd);
    table.vkDestroyBuffer(device, stagingBuffer, nullptr);
    table.vkFreeMemory(device, stagingMemory, nullptr);

    // 4. Image view
    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = image_;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    if (table.vkCreateImageView(device, &viewInfo, nullptr, &imageView_) != VK_SUCCESS) {
        cleanupImage();
        return false;
    }

    // 5. Update descriptor set
    VkDescriptorImageInfo descImgInfo{};
    descImgInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    descImgInfo.imageView = imageView_;
    descImgInfo.sampler = sampler_;

    VkWriteDescriptorSet writeSet{};
    writeSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    writeSet.dstSet = descriptorSet_;
    writeSet.dstBinding = 0;
    writeSet.dstArrayElement = 0;
    writeSet.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    writeSet.descriptorCount = 1;
    writeSet.pImageInfo = &descImgInfo;

    table.vkUpdateDescriptorSets(device, 1, &writeSet, 0, nullptr);
    return true;
}

} // namespace silicon::vis::vk
