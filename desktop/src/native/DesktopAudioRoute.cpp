#include <jni.h>
#include <cctype>
#include <cstdint>
#include <cstddef>
#include <string>
#include <dlfcn.h>
#include <unistd.h>
#include "miniaudio.h"

// Desktop-only: reports the default playback device name (e.g. the PulseAudio
// sink or ALSA card miniaudio would open) so the UI can show a real output
// name instead of a hardcoded placeholder. Uses a transient context; the
// engine's own stream context is intentionally left untouched.
//
// Route class (speaker vs headphones vs USB vs Bluetooth vs HDMI) comes from
// the PulseAudio default sink's active port when libpulse answers, and falls
// back to default-device name heuristics otherwise. libpulse is dlopened, not
// linked, so systems without it keep working through the fallback.

// Ordinals must match AudioOutputRouteType declaration order.
enum {
    ROUTE_SPEAKER = 0,
    ROUTE_HEADPHONES = 1,
    ROUTE_USB = 2,
    ROUTE_BLUETOOTH = 3,
    ROUTE_HDMI = 4,
    ROUTE_SPDIF = 5
};

static std::string queryDefaultDeviceName() {
    std::string name;
    ma_context context;
    if (ma_context_init(nullptr, 0, nullptr, &context) == MA_SUCCESS) {
        ma_device_info* playbackInfos = nullptr;
        ma_uint32 playbackCount = 0;
        if (ma_context_get_devices(&context, &playbackInfos, &playbackCount, nullptr, nullptr) == MA_SUCCESS &&
            playbackInfos != nullptr) {
            for (ma_uint32 i = 0; i < playbackCount; ++i) {
                if (playbackInfos[i].isDefault) {
                    name = playbackInfos[i].name;
                    break;
                }
            }
            if (name.empty() && playbackCount > 0) {
                name = playbackInfos[0].name;
            }
        }
        ma_context_uninit(&context);
    }
    return name;
}

static bool containsNoCase(const char* haystack, const char* needle) {
    if (haystack == nullptr || needle == nullptr || *needle == '\0') return false;
    for (const char* h = haystack; *h != '\0'; ++h) {
        const char* a = h;
        const char* b = needle;
        while (*a != '\0' && *b != '\0' &&
               std::tolower((unsigned char)*a) == std::tolower((unsigned char)*b)) {
            ++a;
            ++b;
        }
        if (*b == '\0') return true;
    }
    return false;
}

static bool startsWithNoCase(const char* str, const char* prefix) {
    if (str == nullptr || prefix == nullptr) return false;
    while (*prefix != '\0') {
        if (*str == '\0') return false;
        if (std::tolower((unsigned char)*str) != std::tolower((unsigned char)*prefix)) return false;
        ++str;
        ++prefix;
    }
    return true;
}

// Name-heuristics fallback: Pulse/PipeWire sink names and ALSA card names
// both tend to carry the transport (usb, bluez, hdmi) in the string.
static int classifyDeviceName(const std::string& name) {
    const char* n = name.c_str();
    if (containsNoCase(n, "bluez") || containsNoCase(n, "bluetooth")) return ROUTE_BLUETOOTH;
    if (containsNoCase(n, "usb")) return ROUTE_USB;
    if (containsNoCase(n, "hdmi") || containsNoCase(n, "displayport")) return ROUTE_HDMI;
    if (containsNoCase(n, "iec958") || containsNoCase(n, "spdif") ||
        containsNoCase(n, "s/pdif") || containsNoCase(n, "digital"))
        return ROUTE_SPDIF;
    if (containsNoCase(n, "headphone") || containsNoCase(n, "headset")) return ROUTE_HEADPHONES;
    return ROUTE_SPEAKER;
}

// --- Minimal libpulse client (dlopened) -----------------------------------
// Only the struct prefixes we actually read are defined; the rest of each
// struct is deliberately opaque. Offsets are asserted so a layout drift
// fails loudly at build time instead of misreading at runtime.

struct pa_mainloop;
struct pa_mainloop_api;
struct pa_context;
struct pa_operation;

struct PaSampleSpec {
    int32_t format;
    uint32_t rate;
    uint8_t channels;
    uint8_t reserved[3];
};
static_assert(sizeof(PaSampleSpec) == 12, "pa_sample_spec layout");

struct PaServerInfoPrefix {
    const char* user_name;
    const char* host_name;
    const char* server_version;
    const char* server_name;
    PaSampleSpec sample_spec;
    const char* default_sink_name;
};
static_assert(offsetof(PaServerInfoPrefix, default_sink_name) == 48, "pa_server_info layout");

struct PaSinkInfoPrefix {
    const char* name;              // 0
    uint32_t index;                // 8
    const char* description;       // 16
    PaSampleSpec sample_spec;      // 24
    uint8_t channel_map[132];      // 36
    uint32_t module;               // 168
    uint8_t cvolume[132];          // 172
    int32_t muted;                 // 304
    uint32_t monitor_source;       // 308
    const char* monitor_source_name; // 312
    uint64_t latency;              // 320
    const char* driver;            // 328
    int32_t flags;                 // 336
    const void* proplist;          // 344
    uint64_t configured_latency;   // 352
    uint32_t base_volume;          // 360
    int32_t state;                 // 364
    uint32_t n_volume_steps;       // 368
    uint32_t card;                 // 372
    uint32_t n_ports;              // 376
    void* ports;                   // 384
    void* active_port;             // 392
};
static_assert(offsetof(PaSinkInfoPrefix, active_port) == 392, "pa_sink_info layout");

struct PaPortPrefix {
    const char* name;  // offset 0 by header definition
};

enum { PA_CONTEXT_READY = 4, PA_CONTEXT_FAILED = 5, PA_CONTEXT_TERMINATED = 6 };
enum { PA_OPERATION_DONE = 1 };

struct PulseApi {
    void* handle = nullptr;
    pa_mainloop* (*mainloop_new)() = nullptr;
    pa_mainloop_api* (*mainloop_get_api)(pa_mainloop*) = nullptr;
    int (*mainloop_iterate)(pa_mainloop*, int, int*) = nullptr;
    void (*mainloop_free)(pa_mainloop*) = nullptr;
    pa_context* (*context_new)(pa_mainloop_api*, const char*) = nullptr;
    int (*context_connect)(pa_context*, const char*, uint32_t, const void*) = nullptr;
    int (*context_get_state)(pa_context*) = nullptr;
    pa_operation* (*context_get_server_info)(pa_context*, void (*)(pa_context*, const PaServerInfoPrefix*, void*), void*) = nullptr;
    pa_operation* (*context_get_sink_info_by_name)(pa_context*, const char*, void (*)(pa_context*, const PaSinkInfoPrefix*, int, void*), void*) = nullptr;
    void (*context_disconnect)(pa_context*) = nullptr;
    void (*context_unref)(pa_context*) = nullptr;
    int (*operation_get_state)(pa_operation*) = nullptr;
    void (*operation_unref)(pa_operation*) = nullptr;
};

static PulseApi loadPulseApi() {
    PulseApi api;
    api.handle = dlopen("libpulse.so.0", RTLD_NOW | RTLD_LOCAL);
    if (api.handle == nullptr) {
        api.handle = dlopen("libpulse.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (api.handle == nullptr) return api;
    bool ok = true;
    ok &= (api.mainloop_new = (decltype(api.mainloop_new))dlsym(api.handle, "pa_mainloop_new")) != nullptr;
    ok &= (api.mainloop_get_api = (decltype(api.mainloop_get_api))dlsym(api.handle, "pa_mainloop_get_api")) != nullptr;
    ok &= (api.mainloop_iterate = (decltype(api.mainloop_iterate))dlsym(api.handle, "pa_mainloop_iterate")) != nullptr;
    ok &= (api.mainloop_free = (decltype(api.mainloop_free))dlsym(api.handle, "pa_mainloop_free")) != nullptr;
    ok &= (api.context_new = (decltype(api.context_new))dlsym(api.handle, "pa_context_new")) != nullptr;
    ok &= (api.context_connect = (decltype(api.context_connect))dlsym(api.handle, "pa_context_connect")) != nullptr;
    ok &= (api.context_get_state = (decltype(api.context_get_state))dlsym(api.handle, "pa_context_get_state")) != nullptr;
    ok &= (api.context_get_server_info = (decltype(api.context_get_server_info))dlsym(api.handle, "pa_context_get_server_info")) != nullptr;
    ok &= (api.context_get_sink_info_by_name = (decltype(api.context_get_sink_info_by_name))dlsym(api.handle, "pa_context_get_sink_info_by_name")) != nullptr;
    ok &= (api.context_disconnect = (decltype(api.context_disconnect))dlsym(api.handle, "pa_context_disconnect")) != nullptr;
    ok &= (api.context_unref = (decltype(api.context_unref))dlsym(api.handle, "pa_context_unref")) != nullptr;
    ok &= (api.operation_get_state = (decltype(api.operation_get_state))dlsym(api.handle, "pa_operation_get_state")) != nullptr;
    ok &= (api.operation_unref = (decltype(api.operation_unref))dlsym(api.handle, "pa_operation_unref")) != nullptr;
    if (!ok) {
        dlclose(api.handle);
        api.handle = nullptr;
    }
    return api;
}

struct PulseProbe {
    char sinkName[256] = {0};
    bool sinkDone = false;
    char portName[256] = {0};
    bool portDone = false;
};

static void copyBounded(char* dst, size_t dstSize, const char* src) {
    if (dst == nullptr || dstSize == 0 || src == nullptr) return;
    // Only trust printable ASCII; anything else means we misread the ABI.
    size_t len = 0;
    while (len + 1 < dstSize && src[len] != '\0') {
        unsigned char c = (unsigned char)src[len];
        if (c < 0x20 || c > 0x7E) return;
        dst[len] = (char)c;
        ++len;
    }
    if (src[len] != '\0') return;  // unterminated or too long: distrust
    dst[len] = '\0';
}

static void serverInfoCb(pa_context*, const PaServerInfoPrefix* info, void* userdata) {
    PulseProbe* probe = (PulseProbe*)userdata;
    if (info != nullptr) {
        copyBounded(probe->sinkName, sizeof(probe->sinkName), info->default_sink_name);
    }
    probe->sinkDone = true;
}

static void sinkInfoCb(pa_context*, const PaSinkInfoPrefix* info, int eol, void* userdata) {
    PulseProbe* probe = (PulseProbe*)userdata;
    if (eol < 0 || info == nullptr) {
        probe->portDone = true;
        return;
    }
    if (eol > 0 || probe->portDone) {
        probe->portDone = true;
        return;
    }
    const PaPortPrefix* port = (const PaPortPrefix*)info->active_port;
    if (port != nullptr && info->n_ports > 0) {
        copyBounded(probe->portName, sizeof(probe->portName), port->name);
    }
    probe->portDone = true;
}

static int classifyPulsePort(const char* port) {
    if (port == nullptr || *port == '\0') return -1;
    if (startsWithNoCase(port, "analog-output-speaker")) return ROUTE_SPEAKER;
    if (startsWithNoCase(port, "analog-output-headphones") ||
        startsWithNoCase(port, "analog-output-headset")) return ROUTE_HEADPHONES;
    if (startsWithNoCase(port, "hdmi-output")) return ROUTE_HDMI;
    if (startsWithNoCase(port, "iec958")) return ROUTE_SPDIF;
    if (containsNoCase(port, "bluez") || containsNoCase(port, "bluetooth")) return ROUTE_BLUETOOTH;
    if (containsNoCase(port, "usb")) return ROUTE_USB;
    // Classic bluez card ports carry no bluez tag of their own.
    if (containsNoCase(port, "headset-output") || containsNoCase(port, "handsfree") ||
        containsNoCase(port, "a2dp"))
        return ROUTE_BLUETOOTH;
    return -1;  // bare analog-output and friends: let the device-name fallback decide
}

// Returns -1 when PulseAudio is unreachable or unreadable.
static int queryPulseActivePortClass() {
    static PulseApi api = loadPulseApi();
    if (api.handle == nullptr) return -1;

    PulseProbe probe;
    pa_mainloop* ml = api.mainloop_new();
    if (ml == nullptr) return -1;
    pa_mainloop_api* mlApi = api.mainloop_get_api(ml);
    pa_context* ctx = api.context_new(mlApi, "SiliconPlayer-route-probe");
    if (ctx == nullptr) {
        api.mainloop_free(ml);
        return -1;
    }
    int result = -1;
    if (api.context_connect(ctx, nullptr, 0, nullptr) >= 0) {
        int state = 0;
        for (int i = 0; i < 400 && state != PA_CONTEXT_READY; ++i) {
            int retval = 0;
            api.mainloop_iterate(ml, 0, &retval);
            state = api.context_get_state(ctx);
            if (state == PA_CONTEXT_FAILED || state == PA_CONTEXT_TERMINATED) break;
            usleep(5000);
        }
        if (state == PA_CONTEXT_READY) {
            pa_operation* op = api.context_get_server_info(ctx, serverInfoCb, &probe);
            if (op != nullptr) {
                for (int i = 0; i < 200 && !probe.sinkDone; ++i) {
                    int retval = 0;
                    api.mainloop_iterate(ml, 0, &retval);
                    usleep(5000);
                }
                api.operation_unref(op);
            }
            if (probe.sinkDone && probe.sinkName[0] != '\0') {
                op = api.context_get_sink_info_by_name(ctx, probe.sinkName, sinkInfoCb, &probe);
                if (op != nullptr) {
                    for (int i = 0; i < 200 && !probe.portDone; ++i) {
                        int retval = 0;
                        api.mainloop_iterate(ml, 0, &retval);
                        usleep(5000);
                    }
                    api.operation_unref(op);
                }
                if (probe.portDone && probe.portName[0] != '\0') {
                    result = classifyPulsePort(probe.portName);
                }
            }
        }
        api.context_disconnect(ctx);
    }
    api.context_unref(ctx);
    api.mainloop_free(ml);
    return result;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getAudioOutputRouteName(JNIEnv* env, jobject /* thiz */) {
    std::string name = queryDefaultDeviceName();
    return env->NewStringUTF(name.c_str());
}

JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getAudioOutputRouteClass(JNIEnv* /* env */, jobject /* thiz */) {
    int fromPulse = queryPulseActivePortClass();
    if (fromPulse >= 0) return (jint)fromPulse;
    return (jint)classifyDeviceName(queryDefaultDeviceName());
}

} // extern "C"
