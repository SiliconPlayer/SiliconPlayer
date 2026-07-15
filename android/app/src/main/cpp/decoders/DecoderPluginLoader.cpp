#include "DecoderPluginLoader.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <dlfcn.h>
#include <mutex>
#include <thread>
#include <unordered_map>
#include <utility>

#define LOG_TAG "DecoderPluginLoader"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
using CreateDecoderFn = AudioDecoder* (*)();
using Clock = std::chrono::steady_clock;
constexpr auto kUnloadDelay = std::chrono::seconds(5);

struct LoadedPlugin {
    std::string libraryName;
    void* handle = nullptr;
    CreateDecoderFn createDecoder = nullptr;

    ~LoadedPlugin() {
        if (handle != nullptr) {
            LOGD("Unloading decoder plugin: %s", libraryName.c_str());
            dlclose(handle);
        }
    }
};

struct PluginSlot {
    std::shared_ptr<LoadedPlugin> plugin;
    int activeLeases = 0;
    Clock::time_point unloadAfter = Clock::time_point::max();
};

class DecoderPluginLoaderImpl {
public:
    struct PluginLease {
        DecoderPluginLoaderImpl* owner = nullptr;
        std::string libraryName;
        std::shared_ptr<LoadedPlugin> plugin;

        ~PluginLease() {
            if (owner != nullptr) {
                owner->release(libraryName);
            }
        }
    };

    DecoderPluginLoaderImpl() {
        worker = std::thread([this] { unloadWorkerLoop(); });
    }

    ~DecoderPluginLoaderImpl() {
        {
            std::lock_guard<std::mutex> lock(mutex);
            stopping = true;
        }
        cv.notify_one();
        if (worker.joinable()) {
            worker.join();
        }
        plugins.clear();
    }

    std::unique_ptr<AudioDecoder> createDecoder(const std::string& libraryName) {
        auto lease = acquire(libraryName);
        if (!lease) {
            return nullptr;
        }

        auto* plugin = static_cast<PluginLease*>(lease.get())->plugin.get();
        AudioDecoder* rawDecoder = plugin->createDecoder();
        if (rawDecoder == nullptr) {
            LOGE("Plugin returned null decoder: %s", libraryName.c_str());
            return nullptr;
        }

        rawDecoder->attachDynamicLibraryLease(std::move(lease));
        LOGD("Created decoder from plugin: %s", libraryName.c_str());
        return std::unique_ptr<AudioDecoder>(rawDecoder);
    }

private:
    std::shared_ptr<void> acquire(const std::string& libraryName) {
        std::lock_guard<std::mutex> lock(mutex);
        auto& slot = plugins[libraryName];
        if (!slot.plugin) {
            slot.plugin = loadPlugin(libraryName);
            if (!slot.plugin) {
                plugins.erase(libraryName);
                return {};
            }
        }

        slot.activeLeases += 1;
        slot.unloadAfter = Clock::time_point::max();
        LOGD("Acquired decoder plugin lease: %s active=%d", libraryName.c_str(), slot.activeLeases);

        auto lease = std::make_shared<PluginLease>();
        lease->owner = this;
        lease->libraryName = libraryName;
        lease->plugin = slot.plugin;
        return lease;
    }

    void release(const std::string& libraryName) {
        {
            std::lock_guard<std::mutex> lock(mutex);
            auto it = plugins.find(libraryName);
            if (it == plugins.end()) {
                return;
            }
            auto& slot = it->second;
            if (slot.activeLeases > 0) {
                slot.activeLeases -= 1;
            }
            if (slot.activeLeases == 0) {
                slot.unloadAfter = Clock::now() + kUnloadDelay;
                LOGD("Released final decoder plugin lease, unload scheduled: %s delay=%llds",
                     libraryName.c_str(),
                     static_cast<long long>(std::chrono::duration_cast<std::chrono::seconds>(kUnloadDelay).count()));
            } else {
                LOGD("Released decoder plugin lease: %s active=%d", libraryName.c_str(), slot.activeLeases);
            }
        }
        cv.notify_one();
    }

    std::shared_ptr<LoadedPlugin> loadPlugin(const std::string& libraryName) {
        LOGD("Loading decoder plugin: %s", libraryName.c_str());
        void* handle = dlopen(libraryName.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (handle == nullptr) {
            LOGE("dlopen failed for %s: %s", libraryName.c_str(), dlerror());
            return {};
        }

        dlerror();
        auto createDecoder = reinterpret_cast<CreateDecoderFn>(
                dlsym(handle, "siliconplayer_create_decoder")
        );
        const char* symbolError = dlerror();
        if (symbolError != nullptr || createDecoder == nullptr) {
            LOGE("dlsym failed for %s: %s", libraryName.c_str(), symbolError ? symbolError : "null symbol");
            dlclose(handle);
            return {};
        }

        auto plugin = std::make_shared<LoadedPlugin>();
        plugin->libraryName = libraryName;
        plugin->handle = handle;
        plugin->createDecoder = createDecoder;
        LOGD("Loaded decoder plugin: %s", libraryName.c_str());
        return plugin;
    }

    void unloadWorkerLoop() {
        std::unique_lock<std::mutex> lock(mutex);
        while (!stopping) {
            auto nextWake = Clock::time_point::max();
            const auto now = Clock::now();

            for (auto it = plugins.begin(); it != plugins.end();) {
                const auto& slot = it->second;
                if (slot.activeLeases == 0 && slot.unloadAfter <= now) {
                    LOGD("Decoder plugin unload delay elapsed: %s", it->first.c_str());
                    it = plugins.erase(it);
                } else {
                    if (slot.activeLeases == 0) {
                        nextWake = std::min(nextWake, slot.unloadAfter);
                    }
                    ++it;
                }
            }

            if (nextWake == Clock::time_point::max()) {
                cv.wait(lock);
            } else {
                cv.wait_until(lock, nextWake);
            }
        }
    }

    std::mutex mutex;
    std::condition_variable cv;
    std::unordered_map<std::string, PluginSlot> plugins;
    std::thread worker;
    bool stopping = false;
};
} // namespace

DecoderPluginLoader& DecoderPluginLoader::getInstance() {
    static DecoderPluginLoader instance;
    return instance;
}

DecoderPluginLoader::DecoderPluginLoader() = default;
DecoderPluginLoader::~DecoderPluginLoader() = default;

std::unique_ptr<AudioDecoder> DecoderPluginLoader::createDecoder(const std::string& libraryName) {
    static DecoderPluginLoaderImpl impl;
    return impl.createDecoder(libraryName);
}
