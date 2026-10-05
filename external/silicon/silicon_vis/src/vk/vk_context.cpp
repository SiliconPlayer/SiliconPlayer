#include "vk_context.h"
#include "gl/gl_platform.h"
#include <cstring>
#include <algorithm>

namespace silicon::vis::vk {

VkContext::VkContext() = default;

VkContext::~VkContext() {
    release();
}

bool VkContext::init(bool enableValidation) {
    if (initialized_) return true;

    if (!VkLoader::init()) {
        return false;
    }

    if (!createInstance(enableValidation)) {
        release();
        return false;
    }

    if (!selectPhysicalDevice()) {
        release();
        return false;
    }

    probeCapabilities();

    if (!createLogicalDevice()) {
        release();
        return false;
    }

    if (!createCommandPool()) {
        release();
        return false;
    }

    initialized_ = true;
    return true;
}

void VkContext::release() {
    const auto& table = VkLoader::table();

    if (device_ != VK_NULL_HANDLE) {
        if (table.vkDeviceWaitIdle) table.vkDeviceWaitIdle(device_);

        if (commandPool_ != VK_NULL_HANDLE && table.vkDestroyCommandPool) {
            table.vkDestroyCommandPool(device_, commandPool_, nullptr);
            commandPool_ = VK_NULL_HANDLE;
        }

        if (table.vkDestroyDevice) {
            table.vkDestroyDevice(device_, nullptr);
        }
        device_ = VK_NULL_HANDLE;
    }

    if (instance_ != VK_NULL_HANDLE) {
        if (table.vkDestroyInstance) {
            table.vkDestroyInstance(instance_, nullptr);
        }
        instance_ = VK_NULL_HANDLE;
    }

    initialized_ = false;
    capabilities_ = DeviceCapabilities{};
}

bool VkContext::createInstance(bool enableValidation) {
    const auto& table = VkLoader::table();

    uint32_t instanceVersion = VK_API_VERSION_1_1;
    if (table.vkEnumerateInstanceVersion) {
        table.vkEnumerateInstanceVersion(&instanceVersion);
    }

    // Target the highest supported apiVersion up to 1.3
    uint32_t targetApiVersion = VK_API_VERSION_1_1;
    if (instanceVersion >= VK_API_VERSION_1_3) {
        targetApiVersion = VK_API_VERSION_1_3;
    } else if (instanceVersion >= VK_API_VERSION_1_2) {
        targetApiVersion = VK_API_VERSION_1_2;
    }

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "SiliconPlayer";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "silicon_vis";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = targetApiVersion;

    // Available extensions
    uint32_t extCount = 0;
    table.vkEnumerateInstanceExtensionProperties(nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> availableExts(extCount);
    if (extCount > 0) {
        table.vkEnumerateInstanceExtensionProperties(nullptr, &extCount, availableExts.data());
    }

    auto isExtAvailable = [&](const char* name) {
        for (const auto& ext : availableExts) {
            if (strcmp(ext.extensionName, name) == 0) return true;
        }
        return false;
    };

    std::vector<const char*> enabledExtensions;
    if (isExtAvailable(VK_KHR_SURFACE_EXTENSION_NAME)) {
        enabledExtensions.push_back(VK_KHR_SURFACE_EXTENSION_NAME);
    }
#if defined(__ANDROID__)
    if (isExtAvailable("VK_KHR_android_surface")) {
        enabledExtensions.push_back("VK_KHR_android_surface");
    }
#endif

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(enabledExtensions.size());
    createInfo.ppEnabledExtensionNames = enabledExtensions.data();

    VkResult res = table.vkCreateInstance(&createInfo, nullptr, &instance_);
    if (res != VK_SUCCESS || !instance_) {
        // Fallback retry with VK_API_VERSION_1_1 if a higher version failed
        if (targetApiVersion > VK_API_VERSION_1_1) {
            appInfo.apiVersion = VK_API_VERSION_1_1;
            res = table.vkCreateInstance(&createInfo, nullptr, &instance_);
        }
        if (res != VK_SUCCESS || !instance_) {
            return false;
        }
    }

    return VkLoader::loadInstanceFunctions(instance_);
}

bool VkContext::selectPhysicalDevice() {
    const auto& table = VkLoader::table();

    uint32_t deviceCount = 0;
    table.vkEnumeratePhysicalDevices(instance_, &deviceCount, nullptr);
    if (deviceCount == 0) return false;

    std::vector<VkPhysicalDevice> devices(deviceCount);
    table.vkEnumeratePhysicalDevices(instance_, &deviceCount, devices.data());

    VkPhysicalDevice selected = VK_NULL_HANDLE;
    int bestScore = -1;

    for (VkPhysicalDevice dev : devices) {
        VkPhysicalDeviceProperties props{};
        table.vkGetPhysicalDeviceProperties(dev, &props);
        if (props.apiVersion < VK_API_VERSION_1_1) continue;

        uint32_t queueFamilyCount = 0;
        table.vkGetPhysicalDeviceQueueFamilyProperties(dev, &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        table.vkGetPhysicalDeviceQueueFamilyProperties(dev, &queueFamilyCount, queueFamilies.data());

        uint32_t graphicsIndex = 0xFFFFFFFF;
        for (uint32_t i = 0; i < queueFamilyCount; ++i) {
            if (queueFamilies[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
                graphicsIndex = i;
                break;
            }
        }
        if (graphicsIndex == 0xFFFFFFFF) continue;

        // Software rasterizers enumerate alongside real GPUs; never prefer them.
        int score = 2;
        if (props.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) score = 3;
        else if (props.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) score = 0;
        VIS_LOGI("VisVk device: %s type=%d score=%d", props.deviceName, static_cast<int>(props.deviceType), score);

        if (score > bestScore) {
            bestScore = score;
            selected = dev;
            capabilities_.graphicsQueueFamilyIndex = graphicsIndex;
            capabilities_.apiVersion = props.apiVersion;
        }
    }

    physicalDevice_ = selected;
    return physicalDevice_ != VK_NULL_HANDLE;
}

void VkContext::probeCapabilities() {
    const auto& table = VkLoader::table();

    VkPhysicalDeviceProperties props{};
    table.vkGetPhysicalDeviceProperties(physicalDevice_, &props);
    std::strncpy(capabilities_.deviceName, props.deviceName, sizeof(capabilities_.deviceName) - 1);
    capabilities_.deviceName[sizeof(capabilities_.deviceName) - 1] = '\0';

    // Available device extensions
    uint32_t extCount = 0;
    table.vkEnumerateDeviceExtensionProperties(physicalDevice_, nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> availableExts(extCount);
    if (extCount > 0) {
        table.vkEnumerateDeviceExtensionProperties(physicalDevice_, nullptr, &extCount, availableExts.data());
    }

    auto isDevExtAvailable = [&](const char* name) {
        for (const auto& ext : availableExts) {
            if (strcmp(ext.extensionName, name) == 0) return true;
        }
        return false;
    };

    capabilities_.hasTimelineSemaphores = (props.apiVersion >= VK_API_VERSION_1_2) ||
                                          isDevExtAvailable("VK_KHR_timeline_semaphore");
    capabilities_.hasHostQueryReset = (props.apiVersion >= VK_API_VERSION_1_2) ||
                                      isDevExtAvailable("VK_EXT_host_query_reset");
    capabilities_.hasDynamicRendering = (props.apiVersion >= VK_API_VERSION_1_3) ||
                                        isDevExtAvailable("VK_KHR_dynamic_rendering");
    capabilities_.hasExtendedDynamicState = (props.apiVersion >= VK_API_VERSION_1_3) ||
                                            isDevExtAvailable("VK_EXT_extended_dynamic_state");
    capabilities_.hasSynchronization2 = (props.apiVersion >= VK_API_VERSION_1_3) ||
                                        isDevExtAvailable("VK_KHR_synchronization2");

    // Assign Tier
    if (props.apiVersion >= VK_API_VERSION_1_3 && capabilities_.hasDynamicRendering) {
        capabilities_.tier = VulkanTier::Tier3_Modern13;
    } else if (props.apiVersion >= VK_API_VERSION_1_2 || capabilities_.hasTimelineSemaphores) {
        capabilities_.tier = VulkanTier::Tier2_Enhanced12;
    } else {
        capabilities_.tier = VulkanTier::Tier1_Base11;
    }

    // MSAA support check
    VkSampleCountFlags counts = props.limits.framebufferColorSampleCounts;
    if (counts & VK_SAMPLE_COUNT_4_BIT) {
        capabilities_.maxMsaaSamples = 4;
    } else if (counts & VK_SAMPLE_COUNT_2_BIT) {
        capabilities_.maxMsaaSamples = 2;
    } else {
        capabilities_.maxMsaaSamples = 1;
    }
}

bool VkContext::createLogicalDevice() {
    const auto& table = VkLoader::table();

    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo{};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = capabilities_.graphicsQueueFamilyIndex;
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &queuePriority;

    std::vector<const char*> enabledExtensions;
    enabledExtensions.push_back(VK_KHR_SWAPCHAIN_EXTENSION_NAME);

    // Conditionally enable extensions for Tier 2/3 if running below 1.3/1.2 core
    uint32_t extCount = 0;
    table.vkEnumerateDeviceExtensionProperties(physicalDevice_, nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> availableExts(extCount);
    if (extCount > 0) {
        table.vkEnumerateDeviceExtensionProperties(physicalDevice_, nullptr, &extCount, availableExts.data());
    }

    auto isDevExtAvailable = [&](const char* name) {
        for (const auto& ext : availableExts) {
            if (strcmp(ext.extensionName, name) == 0) return true;
        }
        return false;
    };

    if (capabilities_.apiVersion < VK_API_VERSION_1_2 && isDevExtAvailable("VK_KHR_timeline_semaphore")) {
        enabledExtensions.push_back("VK_KHR_timeline_semaphore");
    }
    if (capabilities_.apiVersion < VK_API_VERSION_1_2 && isDevExtAvailable("VK_EXT_host_query_reset")) {
        enabledExtensions.push_back("VK_EXT_host_query_reset");
    }
    if (capabilities_.apiVersion < VK_API_VERSION_1_3 && isDevExtAvailable("VK_KHR_dynamic_rendering")) {
        enabledExtensions.push_back("VK_KHR_dynamic_rendering");
    }
    if (capabilities_.apiVersion < VK_API_VERSION_1_3 && isDevExtAvailable("VK_EXT_extended_dynamic_state")) {
        enabledExtensions.push_back("VK_EXT_extended_dynamic_state");
    }
    if (capabilities_.apiVersion < VK_API_VERSION_1_3 && isDevExtAvailable("VK_KHR_synchronization2")) {
        enabledExtensions.push_back("VK_KHR_synchronization2");
    }

    VkPhysicalDeviceFeatures deviceFeatures{};
    table.vkGetPhysicalDeviceFeatures(physicalDevice_, &deviceFeatures);
    VkPhysicalDeviceFeatures enabledFeatures{};
    enabledFeatures.wideLines = deviceFeatures.wideLines;

    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.pEnabledFeatures = &enabledFeatures;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(enabledExtensions.size());
    createInfo.ppEnabledExtensionNames = enabledExtensions.data();

    // Setup 1.2 / 1.3 pNext structures if core supported
    VkPhysicalDeviceVulkan13Features features13{};
    features13.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
    features13.dynamicRendering = capabilities_.hasDynamicRendering ? VK_TRUE : VK_FALSE;
    features13.synchronization2 = capabilities_.hasSynchronization2 ? VK_TRUE : VK_FALSE;

    VkPhysicalDeviceVulkan12Features features12{};
    features12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
    features12.timelineSemaphore = capabilities_.hasTimelineSemaphores ? VK_TRUE : VK_FALSE;
    features12.hostQueryReset = capabilities_.hasHostQueryReset ? VK_TRUE : VK_FALSE;

    if (capabilities_.apiVersion >= VK_API_VERSION_1_3) {
        features13.pNext = &features12;
        createInfo.pNext = &features13;
    } else if (capabilities_.apiVersion >= VK_API_VERSION_1_2) {
        createInfo.pNext = &features12;
    }

    VkResult res = table.vkCreateDevice(physicalDevice_, &createInfo, nullptr, &device_);
    if (res != VK_SUCCESS || !device_) {
        // If pNext 1.2/1.3 features failed on an older driver, retry with base createInfo
        if (createInfo.pNext != nullptr) {
            createInfo.pNext = nullptr;
            res = table.vkCreateDevice(physicalDevice_, &createInfo, nullptr, &device_);
        }
        if (res != VK_SUCCESS || !device_) {
            return false;
        }
    }

    if (!VkLoader::loadDeviceFunctions(device_)) {
        return false;
    }

    table.vkGetDeviceQueue(device_, capabilities_.graphicsQueueFamilyIndex, 0, &graphicsQueue_);
    return graphicsQueue_ != VK_NULL_HANDLE;
}

bool VkContext::createCommandPool() {
    const auto& table = VkLoader::table();

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = capabilities_.graphicsQueueFamilyIndex;

    return table.vkCreateCommandPool(device_, &poolInfo, nullptr, &commandPool_) == VK_SUCCESS;
}

#if defined(__ANDROID__)
VkSurfaceKHR VkContext::createAndroidSurface(ANativeWindow* window) {
    if (!instance_ || !window) return VK_NULL_HANDLE;
    const auto& table = VkLoader::table();
    if (!table.vkCreateAndroidSurfaceKHR) return VK_NULL_HANDLE;

    VkAndroidSurfaceCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    createInfo.window = window;

    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkResult res = table.vkCreateAndroidSurfaceKHR(instance_, &createInfo, nullptr, &surface);
    return (res == VK_SUCCESS) ? surface : VK_NULL_HANDLE;
}
#endif

void VkContext::destroySurface(VkSurfaceKHR surface) {
    if (surface == VK_NULL_HANDLE || !instance_) return;
    const auto& table = VkLoader::table();
    if (table.vkDestroySurfaceKHR) {
        table.vkDestroySurfaceKHR(instance_, surface, nullptr);
    }
}

bool VkContext::querySurfaceSupport(
    VkSurfaceKHR surface,
    uint32_t& outMinImages,
    VkSurfaceFormatKHR& outFormat,
    VkPresentModeKHR& outPresentMode
) {
    if (!physicalDevice_ || surface == VK_NULL_HANDLE) return false;
    const auto& table = VkLoader::table();

    VkBool32 supported = VK_FALSE;
    table.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice_, capabilities_.graphicsQueueFamilyIndex, surface, &supported);
    if (!supported) return false;

    VkSurfaceCapabilitiesKHR caps{};
    table.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice_, surface, &caps);
    // One spare image over the minimum: with only the minimum, a vsync-paced
    // producer serializes acquire against present and halves the frame rate.
    outMinImages = std::max(3u, caps.minImageCount + 1);
    if (caps.maxImageCount > 0 && outMinImages > caps.maxImageCount) {
        outMinImages = caps.maxImageCount;
    }

    uint32_t formatCount = 0;
    table.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice_, surface, &formatCount, nullptr);
    if (formatCount == 0) {
        return false;
    }
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    table.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice_, surface, &formatCount, formats.data());
    // Only 8-bit RGBA-order UNORM formats: exotic surface formats (packed
    // 10-bit, SINT/SNORM, YUV) are reported on some drivers but their
    // swapchain images fail gralloc allocation, wedging the canvas black.
    // UNORM (not _SRGB) matches the pipeline: shaders emit display-ready
    // values, so an sRGB image format would double-brighten the scene.
    const VkFormat ranked[] = {
        VK_FORMAT_R8G8B8A8_UNORM,
        VK_FORMAT_B8G8R8A8_UNORM,
    };
    bool found = false;
    for (VkFormat want : ranked) {
        for (const auto& f : formats) {
            if (f.format == want && f.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                outFormat = f;
                found = true;
                break;
            }
        }
        if (found) break;
    }
    if (!found) {
        VK_VIS_LOGE("no allocatable surface format (count=%u first=%d)", formatCount, (int)formats[0].format);
        return false;
    }

    outPresentMode = VK_PRESENT_MODE_FIFO_KHR; // Guaranteed by spec
    uint32_t presentModeCount = 0;
    table.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice_, surface, &presentModeCount, nullptr);
    if (presentModeCount > 0) {
        std::vector<VkPresentModeKHR> modes(presentModeCount);
        table.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice_, surface, &presentModeCount, modes.data());
        for (const auto& m : modes) {
            // Mailbox provides lowest latency non-tearing display if available
            if (m == VK_PRESENT_MODE_MAILBOX_KHR) {
                outPresentMode = m;
                break;
            }
        }
    }

    return true;
}

} // namespace silicon::vis::vk
