#include <jni.h>
#include <string>
#include "miniaudio.h"

// Desktop-only: reports the default playback device name (e.g. the PulseAudio
// sink or ALSA card miniaudio would open) so the UI can show a real output
// name instead of a hardcoded placeholder. Uses a transient context; the
// engine's own stream context is intentionally left untouched.
extern "C" {

JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_NativeBridge_getAudioOutputRouteName(JNIEnv* env, jobject /* thiz */) {
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
    return env->NewStringUTF(name.c_str());
}

} // extern "C"
