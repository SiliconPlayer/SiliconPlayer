#include "vk_dispatch.h"
#include <dlfcn.h>
#include <cstdio>

namespace silicon::vis::vk {

void* VkLoader::libraryHandle_ = nullptr;
VkDispatchTable VkLoader::table_{};
bool VkLoader::isLoaded_ = false;

bool VkLoader::init() {
    if (isLoaded_) return true;

    const char* libraryNames[] = {
#if defined(__ANDROID__)
        "libvulkan.so"
#else
        "libvulkan.so.1",
        "libvulkan.so"
#endif
    };

    for (const char* name : libraryNames) {
        libraryHandle_ = dlopen(name, RTLD_NOW | RTLD_LOCAL);
        if (libraryHandle_) break;
    }

    if (!libraryHandle_) return false;

    table_.vkGetInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
        dlsym(libraryHandle_, "vkGetInstanceProcAddr")
    );
    if (!table_.vkGetInstanceProcAddr) {
        dlclose(libraryHandle_);
        libraryHandle_ = nullptr;
        return false;
    }

#define LOAD_GLOBAL(fn) \
    table_.fn = reinterpret_cast<PFN_##fn>(table_.vkGetInstanceProcAddr(VK_NULL_HANDLE, #fn))

    LOAD_GLOBAL(vkCreateInstance);
    LOAD_GLOBAL(vkEnumerateInstanceVersion);
    LOAD_GLOBAL(vkEnumerateInstanceExtensionProperties);

#undef LOAD_GLOBAL

    if (!table_.vkCreateInstance) {
        dlclose(libraryHandle_);
        libraryHandle_ = nullptr;
        return false;
    }

    isLoaded_ = true;
    return true;
}

void VkLoader::shutdown() {
    if (libraryHandle_) {
        dlclose(libraryHandle_);
        libraryHandle_ = nullptr;
    }
    table_ = VkDispatchTable{};
    isLoaded_ = false;
}

bool VkLoader::isLoaded() {
    return isLoaded_;
}

const VkDispatchTable& VkLoader::table() {
    return table_;
}

bool VkLoader::loadInstanceFunctions(VkInstance instance) {
    if (!instance || !table_.vkGetInstanceProcAddr) return false;

#define LOAD_INST(fn) \
    table_.fn = reinterpret_cast<PFN_##fn>(table_.vkGetInstanceProcAddr(instance, #fn))

    LOAD_INST(vkDestroyInstance);
    LOAD_INST(vkEnumeratePhysicalDevices);
    LOAD_INST(vkGetPhysicalDeviceProperties);
    LOAD_INST(vkGetPhysicalDeviceFeatures);
    LOAD_INST(vkGetPhysicalDeviceFeatures2);
    LOAD_INST(vkGetPhysicalDeviceQueueFamilyProperties);
    LOAD_INST(vkGetPhysicalDeviceMemoryProperties);
    LOAD_INST(vkGetPhysicalDeviceFormatProperties);
    LOAD_INST(vkCreateDevice);
    LOAD_INST(vkGetDeviceProcAddr);
    LOAD_INST(vkEnumerateDeviceExtensionProperties);

    LOAD_INST(vkDestroySurfaceKHR);
    LOAD_INST(vkGetPhysicalDeviceSurfaceSupportKHR);
    LOAD_INST(vkGetPhysicalDeviceSurfaceCapabilitiesKHR);
    LOAD_INST(vkGetPhysicalDeviceSurfaceFormatsKHR);
    LOAD_INST(vkGetPhysicalDeviceSurfacePresentModesKHR);

#if defined(__ANDROID__)
    LOAD_INST(vkCreateAndroidSurfaceKHR);
#endif

#undef LOAD_INST

    return table_.vkCreateDevice && table_.vkGetDeviceProcAddr;
}

bool VkLoader::loadDeviceFunctions(VkDevice device) {
    if (!device || !table_.vkGetDeviceProcAddr) return false;

#define LOAD_DEV(fn) \
    table_.fn = reinterpret_cast<PFN_##fn>(table_.vkGetDeviceProcAddr(device, #fn))

    LOAD_DEV(vkDestroyDevice);
    LOAD_DEV(vkGetDeviceQueue);
    LOAD_DEV(vkDeviceWaitIdle);
    LOAD_DEV(vkQueueWaitIdle);
    LOAD_DEV(vkQueueSubmit);

    LOAD_DEV(vkCreateSwapchainKHR);
    LOAD_DEV(vkDestroySwapchainKHR);
    LOAD_DEV(vkGetSwapchainImagesKHR);
    LOAD_DEV(vkAcquireNextImageKHR);
    LOAD_DEV(vkQueuePresentKHR);

    LOAD_DEV(vkCreateCommandPool);
    LOAD_DEV(vkDestroyCommandPool);
    LOAD_DEV(vkResetCommandPool);
    LOAD_DEV(vkAllocateCommandBuffers);
    LOAD_DEV(vkFreeCommandBuffers);
    LOAD_DEV(vkBeginCommandBuffer);
    LOAD_DEV(vkEndCommandBuffer);
    LOAD_DEV(vkResetCommandBuffer);

    LOAD_DEV(vkCreateBuffer);
    LOAD_DEV(vkDestroyBuffer);
    LOAD_DEV(vkGetBufferMemoryRequirements);
    LOAD_DEV(vkAllocateMemory);
    LOAD_DEV(vkFreeMemory);
    LOAD_DEV(vkBindBufferMemory);
    LOAD_DEV(vkMapMemory);
    LOAD_DEV(vkUnmapMemory);
    LOAD_DEV(vkFlushMappedMemoryRanges);

    LOAD_DEV(vkCreateImage);
    LOAD_DEV(vkDestroyImage);
    LOAD_DEV(vkGetImageMemoryRequirements);
    LOAD_DEV(vkBindImageMemory);
    LOAD_DEV(vkCreateImageView);
    LOAD_DEV(vkDestroyImageView);
    LOAD_DEV(vkCreateSampler);
    LOAD_DEV(vkDestroySampler);

    LOAD_DEV(vkCreateShaderModule);
    LOAD_DEV(vkDestroyShaderModule);
    LOAD_DEV(vkCreatePipelineLayout);
    LOAD_DEV(vkDestroyPipelineLayout);
    LOAD_DEV(vkCreateGraphicsPipelines);
    LOAD_DEV(vkDestroyPipeline);

    LOAD_DEV(vkCreateRenderPass);
    LOAD_DEV(vkDestroyRenderPass);
    LOAD_DEV(vkCreateFramebuffer);
    LOAD_DEV(vkDestroyFramebuffer);

    LOAD_DEV(vkCreateDescriptorSetLayout);
    LOAD_DEV(vkDestroyDescriptorSetLayout);
    LOAD_DEV(vkCreateDescriptorPool);
    LOAD_DEV(vkDestroyDescriptorPool);
    LOAD_DEV(vkAllocateDescriptorSets);
    LOAD_DEV(vkUpdateDescriptorSets);

    LOAD_DEV(vkCreateSemaphore);
    LOAD_DEV(vkDestroySemaphore);
    LOAD_DEV(vkCreateFence);
    LOAD_DEV(vkDestroyFence);
    LOAD_DEV(vkWaitForFences);
    LOAD_DEV(vkResetFences);

    LOAD_DEV(vkCmdBeginRenderPass);
    LOAD_DEV(vkCmdEndRenderPass);
    LOAD_DEV(vkCmdBindPipeline);
    LOAD_DEV(vkCmdBindVertexBuffers);
    LOAD_DEV(vkCmdBindDescriptorSets);
    LOAD_DEV(vkCmdPushConstants);
    LOAD_DEV(vkCmdSetViewport);
    LOAD_DEV(vkCmdSetScissor);
    LOAD_DEV(vkCmdDraw);
    LOAD_DEV(vkCmdPipelineBarrier);
    LOAD_DEV(vkCmdCopyBufferToImage);
    LOAD_DEV(vkCmdCopyImage);

    // Tier 2 (VK 1.2 / extensions)
    LOAD_DEV(vkWaitSemaphores);
    if (!table_.vkWaitSemaphores) {
        table_.vkWaitSemaphores = reinterpret_cast<PFN_vkWaitSemaphores>(
            table_.vkGetDeviceProcAddr(device, "vkWaitSemaphoresKHR")
        );
    }
    LOAD_DEV(vkSignalSemaphore);
    if (!table_.vkSignalSemaphore) {
        table_.vkSignalSemaphore = reinterpret_cast<PFN_vkSignalSemaphore>(
            table_.vkGetDeviceProcAddr(device, "vkSignalSemaphoreKHR")
        );
    }
    LOAD_DEV(vkGetSemaphoreCounterValue);
    if (!table_.vkGetSemaphoreCounterValue) {
        table_.vkGetSemaphoreCounterValue = reinterpret_cast<PFN_vkGetSemaphoreCounterValue>(
            table_.vkGetDeviceProcAddr(device, "vkGetSemaphoreCounterValueKHR")
        );
    }

    LOAD_DEV(vkCreateQueryPool);
    LOAD_DEV(vkDestroyQueryPool);
    LOAD_DEV(vkResetQueryPool);
    LOAD_DEV(vkResetQueryPoolEXT);
    LOAD_DEV(vkGetQueryPoolResults);
    LOAD_DEV(vkCmdWriteTimestamp);
    LOAD_DEV(vkCmdResetQueryPool);

    // Tier 3 (VK 1.3 / extensions)
    LOAD_DEV(vkCmdBeginRendering);
    if (!table_.vkCmdBeginRendering) {
        table_.vkCmdBeginRendering = reinterpret_cast<PFN_vkCmdBeginRendering>(
            table_.vkGetDeviceProcAddr(device, "vkCmdBeginRenderingKHR")
        );
    }
    LOAD_DEV(vkCmdEndRendering);
    if (!table_.vkCmdEndRendering) {
        table_.vkCmdEndRendering = reinterpret_cast<PFN_vkCmdEndRendering>(
            table_.vkGetDeviceProcAddr(device, "vkCmdEndRenderingKHR")
        );
    }
    LOAD_DEV(vkCmdSetLineWidth);
    LOAD_DEV(vkCmdPipelineBarrier2);
    if (!table_.vkCmdPipelineBarrier2) {
        table_.vkCmdPipelineBarrier2 = reinterpret_cast<PFN_vkCmdPipelineBarrier2>(
            table_.vkGetDeviceProcAddr(device, "vkCmdPipelineBarrier2KHR")
        );
    }

#undef LOAD_DEV

    return table_.vkQueueSubmit != nullptr;
}

} // namespace silicon::vis::vk
