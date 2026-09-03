#include <jni.h>
#include "silicon/vis/vis_api.h"
#include "ProjectMVisualizer.h"
#include <vector>
#include <cstring>

extern "C" {

// The plugin is owned by the pipeline of the handle it was registered with.
// A newer registration on a different handle implies the old pipeline died.
static ProjectMVisualizer* s_projectMPlugin = nullptr;
static jlong s_projectMRegisteredHandle = 0;
// Last active preset across surface teardowns; cleared on decoder release.
static std::string s_projectMLastPreset;

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeAttachProjectM(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobjectArray setIds,
    jobjectArray setDirs,
    jstring startPresetKey
) {
    if (!handle) return;
    jsize setCount = setIds ? env->GetArrayLength(setIds) : 0;
    if (setDirs && env->GetArrayLength(setDirs) != setCount) return;

    std::vector<std::pair<std::string, std::string>> sets;
    sets.reserve(static_cast<size_t>(setCount));
    for (jsize i = 0; i < setCount; ++i) {
        auto idObj = static_cast<jstring>(env->GetObjectArrayElement(setIds, i));
        auto dirObj = static_cast<jstring>(env->GetObjectArrayElement(setDirs, i));
        if (!idObj || !dirObj) {
            if (idObj) env->DeleteLocalRef(idObj);
            if (dirObj) env->DeleteLocalRef(dirObj);
            continue;
        }
        const char* idC = env->GetStringUTFChars(idObj, nullptr);
        const char* dirC = env->GetStringUTFChars(dirObj, nullptr);
        if (idC && dirC) {
            sets.emplace_back(idC, dirC);
        }
        if (idC) env->ReleaseStringUTFChars(idObj, idC);
        if (dirC) env->ReleaseStringUTFChars(dirObj, dirC);
        env->DeleteLocalRef(idObj);
        env->DeleteLocalRef(dirObj);
    }

    std::string startKey;
    if (startPresetKey) {
        const char* keyC = env->GetStringUTFChars(startPresetKey, nullptr);
        if (keyC) {
            startKey = keyC;
            env->ReleaseStringUTFChars(startPresetKey, keyC);
        }
    }
    // A preset selected in a previous session wins over the persisted one.
    const std::string& effectiveStart = !s_projectMLastPreset.empty() ? s_projectMLastPreset : startKey;

    if (s_projectMPlugin && s_projectMRegisteredHandle == handle) {
        s_projectMPlugin->setPresetSets(sets);
        s_projectMPlugin->setStartPreset(effectiveStart);
    } else {
        auto* visHandle = reinterpret_cast<SiliconVisHandle>(handle);
        s_projectMPlugin = new ProjectMVisualizer(silicon::vis::silicon_vis_get_audio_provider(visHandle));
        s_projectMPlugin->setPresetSets(sets);
        s_projectMPlugin->setStartPreset(effectiveStart);
        silicon_vis_register_plugin_renderer(visHandle, s_projectMPlugin);
        s_projectMRegisteredHandle = handle;
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeAttachProjectMWithKeys(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobjectArray setIds,
    jobjectArray setDirs,
    jobjectArray presetKeys,
    jstring startPresetKey
) {
    if (!handle) return;
    jsize setCount = setIds ? env->GetArrayLength(setIds) : 0;
    if (setDirs && env->GetArrayLength(setDirs) != setCount) return;
    std::vector<std::pair<std::string, std::string>> sets;
    sets.reserve(static_cast<size_t>(setCount));
    for (jsize i = 0; i < setCount; ++i) {
        auto idObj = static_cast<jstring>(env->GetObjectArrayElement(setIds, i));
        auto dirObj = static_cast<jstring>(env->GetObjectArrayElement(setDirs, i));
        if (!idObj || !dirObj) {
            if (idObj) env->DeleteLocalRef(idObj);
            if (dirObj) env->DeleteLocalRef(dirObj);
            continue;
        }
        const char* idC = env->GetStringUTFChars(idObj, nullptr);
        const char* dirC = env->GetStringUTFChars(dirObj, nullptr);
        if (idC && dirC) sets.emplace_back(idC, dirC);
        if (idC) env->ReleaseStringUTFChars(idObj, idC);
        if (dirC) env->ReleaseStringUTFChars(dirObj, dirC);
        env->DeleteLocalRef(idObj);
        env->DeleteLocalRef(dirObj);
    }
    std::vector<std::string> keys;
    if (presetKeys) {
        jsize kc = env->GetArrayLength(presetKeys);
        keys.reserve(static_cast<size_t>(kc));
        for (jsize i = 0; i < kc; ++i) {
            auto kObj = static_cast<jstring>(env->GetObjectArrayElement(presetKeys, i));
            if (!kObj) continue;
            const char* kcStr = env->GetStringUTFChars(kObj, nullptr);
            if (kcStr) keys.emplace_back(kcStr);
            if (kcStr) env->ReleaseStringUTFChars(kObj, kcStr);
            env->DeleteLocalRef(kObj);
        }
    }
    std::string startKey;
    if (startPresetKey) {
        const char* keyC = env->GetStringUTFChars(startPresetKey, nullptr);
        if (keyC) { startKey = keyC; env->ReleaseStringUTFChars(startPresetKey, keyC); }
    }
    const std::string& effectiveStart = !s_projectMLastPreset.empty() ? s_projectMLastPreset : startKey;
    if (s_projectMPlugin && s_projectMRegisteredHandle == handle) {
        if (!keys.empty()) s_projectMPlugin->setPresetKeys(sets, keys);
        else s_projectMPlugin->setPresetSets(sets);
        s_projectMPlugin->setStartPreset(effectiveStart);
    } else {
        auto* visHandle = reinterpret_cast<SiliconVisHandle>(handle);
        s_projectMPlugin = new ProjectMVisualizer(silicon::vis::silicon_vis_get_audio_provider(visHandle));
        if (!keys.empty()) s_projectMPlugin->setPresetKeys(sets, keys);
        else s_projectMPlugin->setPresetSets(sets);
        s_projectMPlugin->setStartPreset(effectiveStart);
        silicon_vis_register_plugin_renderer(visHandle, s_projectMPlugin);
        s_projectMRegisteredHandle = handle;
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeDetachProjectM(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle || s_projectMRegisteredHandle != handle) return;
    if (s_projectMPlugin) {
        s_projectMLastPreset = s_projectMPlugin->currentPresetKey();
    }
    s_projectMPlugin = nullptr;
    s_projectMRegisteredHandle = 0;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeClearProjectMLastPreset(
    JNIEnv* env,
    jobject /* thiz */
) {
    s_projectMLastPreset.clear();
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMNextPreset(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean smoothTransition
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->nextPreset(smoothTransition == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMPreviousPreset(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean smoothTransition
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->previousPreset(smoothTransition == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetPresetLocked(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean locked
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setPresetLocked(locked == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMIsPresetLocked(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return JNI_FALSE;
    return s_projectMPlugin->isPresetLocked() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetPresetDuration(
    JNIEnv* env,
    jobject /* thiz */,
    jdouble seconds
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setPresetDuration(seconds);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetHardCutEnabled(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean enabled
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setHardCutEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetHardCutSensitivity(
    JNIEnv* env,
    jobject /* thiz */,
    jfloat sensitivity
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setHardCutSensitivity(sensitivity);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetRotationRandom(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean random
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setRotationRandom(random == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetMeshSize(
    JNIEnv* env,
    jobject /* thiz */,
    jint size
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setMeshSize(size);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetAspectCorrection(
    JNIEnv* env,
    jobject /* thiz */,
    jboolean enabled
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setAspectCorrection(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetFps(
    JNIEnv* env,
    jobject /* thiz */,
    jint fps
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setFps(fps);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMSetMaxResolution(
    JNIEnv* env,
    jobject /* thiz */,
    jint maxLongEdgePx
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return;
    s_projectMPlugin->setMaxResolutionPx(maxLongEdgePx);
}

JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMGetPresetName(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return nullptr;
    return env->NewStringUTF(s_projectMPlugin->currentPresetName().c_str());
}

JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMGetPresetKeys(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return nullptr;
    const auto keys = s_projectMPlugin->presetKeys();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(keys.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(keys.size()); ++i) {
        jstring value = env->NewStringUTF(keys[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(stringClass);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMGetPresetSetIds(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return nullptr;
    const auto setIds = s_projectMPlugin->presetSetIds();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(setIds.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(setIds.size()); ++i) {
        jstring value = env->NewStringUTF(setIds[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(stringClass);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMGetCurrentPresetKey(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle) return nullptr;
    std::string current = s_projectMPlugin->currentPresetKey();
    if (current.empty()) return nullptr;
    return env->NewStringUTF(current.c_str());
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeProjectMLoadPreset(
    JNIEnv* env,
    jobject /* thiz */,
    jstring presetKey,
    jboolean smoothTransition
) {
    if (!s_projectMPlugin || !s_projectMRegisteredHandle || !presetKey) return;
    const char* keyC = env->GetStringUTFChars(presetKey, nullptr);
    if (!keyC) return;
    s_projectMPlugin->loadPresetKey(keyC, smoothTransition == JNI_TRUE);
    env->ReleaseStringUTFChars(presetKey, keyC);
}

JNIEXPORT jlong JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeCreate(
    JNIEnv* env,
    jobject /* thiz */
) {
    SiliconVisHandle handle = silicon_vis_create();
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeDestroy(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle) return;
    silicon_vis_destroy(reinterpret_cast<SiliconVisHandle>(handle));
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeInitGl(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle) return JNI_FALSE;
    return silicon_vis_init_gl(reinterpret_cast<SiliconVisHandle>(handle)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeResize(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint widthPx,
    jint heightPx,
    jfloat density
) {
    if (!handle) return;
    silicon_vis_resize(reinterpret_cast<SiliconVisHandle>(handle), widthPx, heightPx, density);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeReleaseGl(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle) return;
    // Keep the last live preset for the next attach.
    if (s_projectMPlugin && s_projectMRegisteredHandle == handle) {
        const std::string lastKey = s_projectMPlugin->currentPresetKey();
        if (!lastKey.empty()) s_projectMLastPreset = lastKey;
    }
    silicon_vis_release_gl(reinterpret_cast<SiliconVisHandle>(handle));
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetMode(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint mode
) {
    if (!handle) return;
    silicon_vis_set_mode(reinterpret_cast<SiliconVisHandle>(handle), static_cast<SiliconVisMode>(mode));
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetVisualAlpha(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jfloat alpha
) {
    if (!handle) return;
    silicon_vis_set_visual_alpha(reinterpret_cast<SiliconVisHandle>(handle), alpha);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetArtworkPixels(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject byteBuffer,
    jint width,
    jint height
) {
    if (!handle) return;
    if (!byteBuffer || width <= 0 || height <= 0) {
        silicon_vis_clear_artwork(reinterpret_cast<SiliconVisHandle>(handle));
        return;
    }
    const uint8_t* pixels = static_cast<const uint8_t*>(env->GetDirectBufferAddress(byteBuffer));
    if (pixels) {
        silicon_vis_set_artwork_pixels(reinterpret_cast<SiliconVisHandle>(handle), pixels, width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeClearArtwork(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle) return;
    silicon_vis_clear_artwork(reinterpret_cast<SiliconVisHandle>(handle));
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetArtworkTheme(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint primaryArgb,
    jint surfaceArgb,
    jint iconType
) {
    if (!handle) return;
    silicon_vis_set_artwork_theme(
        reinterpret_cast<SiliconVisHandle>(handle),
        static_cast<uint32_t>(primaryArgb),
        static_cast<uint32_t>(surfaceArgb),
        iconType
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetContrastMode(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint contrastMode
) {
    if (!handle) return;
    silicon_vis_set_contrast_mode(
        reinterpret_cast<SiliconVisHandle>(handle),
        static_cast<SiliconVisContrastMode>(contrastMode)
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetContrastScrim(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint argb
) {
    if (!handle) return;
    silicon_vis_set_contrast_scrim(
        reinterpret_cast<SiliconVisHandle>(handle),
        static_cast<uint32_t>(argb)
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetShowArtworkBackground(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jboolean show
) {
    if (!handle) return;
    silicon_vis_set_show_artwork_background(
        reinterpret_cast<SiliconVisHandle>(handle),
        show ? true : false
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativePushPcm(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jfloatArray pcmArray,
    jint frames,
    jint channels,
    jint sampleRate
) {
    if (!handle || !pcmArray || frames <= 0) return;
    jfloat* pcm = env->GetFloatArrayElements(pcmArray, nullptr);
    if (pcm) {
        silicon_vis_push_pcm(reinterpret_cast<SiliconVisHandle>(handle), pcm, frames, channels, sampleRate);
        env->ReleaseFloatArrayElements(pcmArray, pcm, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativePushFft(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jfloatArray fftArray,
    jint binCount
) {
    if (!handle || !fftArray || binCount <= 0) return;
    jfloat* fft = env->GetFloatArrayElements(fftArray, nullptr);
    if (fft) {
        silicon_vis_push_fft(reinterpret_cast<SiliconVisHandle>(handle), fft, binCount);
        env->ReleaseFloatArrayElements(fftArray, fft, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetIconPixels(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject byteBuffer,
    jint width,
    jint height
) {
    if (!handle) return;
    if (!byteBuffer || width <= 0 || height <= 0) {
        silicon_vis_clear_icon(reinterpret_cast<SiliconVisHandle>(handle));
        return;
    }
    const uint8_t* pixels = static_cast<const uint8_t*>(env->GetDirectBufferAddress(byteBuffer));
    if (pixels) {
        silicon_vis_set_icon_pixels(reinterpret_cast<SiliconVisHandle>(handle), pixels, width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeClearIcon(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle) return;
    silicon_vis_clear_icon(reinterpret_cast<SiliconVisHandle>(handle));
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetFontAtlas(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject byteBuffer,
    jint width,
    jint height,
    jfloat baseFontSizePx,
    jfloat lineHeightPx,
    jobject glyphBuffer,
    jint glyphCount
) {
    if (!handle || !byteBuffer || !glyphBuffer || width <= 0 || height <= 0 || glyphCount <= 0) return;
    const uint8_t* pixels = static_cast<const uint8_t*>(env->GetDirectBufferAddress(byteBuffer));
    const void* glyphs = env->GetDirectBufferAddress(glyphBuffer);
    if (pixels && glyphs) {
        silicon_vis_set_font_atlas(
            reinterpret_cast<SiliconVisHandle>(handle),
            pixels,
            width,
            height,
            baseFontSizePx,
            lineHeightPx,
            glyphs,
            glyphCount
        );
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetVuLevels(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jfloat left,
    jfloat right
) {
    if (!handle) return;
    silicon_vis_set_vu_levels(reinterpret_cast<SiliconVisHandle>(handle), left, right);
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativePushChannelScopeHistory(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint channel,
    jfloatArray historyArray,
    jint sampleCount
) {
    if (!handle || !historyArray || sampleCount <= 0) return;
    jfloat* hist = env->GetFloatArrayElements(historyArray, nullptr);
    if (hist) {
        silicon_vis_push_channel_scope_history(reinterpret_cast<SiliconVisHandle>(handle), channel, hist, sampleCount);
        env->ReleaseFloatArrayElements(historyArray, hist, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativePushChannelScopeAllHistories(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint channelCount,
    jint samplesPerChannel,
    jfloatArray flatArray
) {
    if (!handle || !flatArray || channelCount <= 0 || samplesPerChannel <= 0) return;
    jfloat* flat = env->GetFloatArrayElements(flatArray, nullptr);
    if (flat) {
        silicon_vis_push_channel_scope_all_histories(
            reinterpret_cast<SiliconVisHandle>(handle),
            channelCount,
            samplesPerChannel,
            flat
        );
        env->ReleaseFloatArrayElements(flatArray, flat, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetChannelScopeOptions(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint layout,
    jint anchor,
    jint vuAnchor,
    jboolean vuEnabled,
    jint textSizeSp,
    jfloat paddingPx,
    jint gridColorArgb,
    jfloat gridWidthPx,
    jint lineColorArgb,
    jfloat lineWidthPx,
    jint vuColorArgb,
    jint chArgb,
    jint noteArgb,
    jint volArgb,
    jint effArgb,
    jint instArgb,
    jint sepArgb,
    jboolean shadowEnabled,
    jboolean hideWhenOverflow,
    jint windowMs,
    jint gainPercent,
    jboolean dcRemovalEnabled,
    jint triggerMode,
    jint waveRenderMode
) {
    if (!handle) return;
    SiliconVisTextPalette pal;
    pal.channelArgb = static_cast<uint32_t>(chArgb);
    pal.noteArgb = static_cast<uint32_t>(noteArgb);
    pal.volumeArgb = static_cast<uint32_t>(volArgb);
    pal.effectArgb = static_cast<uint32_t>(effArgb);
    pal.instrumentOrSampleArgb = static_cast<uint32_t>(instArgb);
    pal.separatorArgb = static_cast<uint32_t>(sepArgb);

    silicon_vis_set_channel_scope_options(
        reinterpret_cast<SiliconVisHandle>(handle),
        static_cast<SiliconVisChannelLayout>(layout),
        static_cast<SiliconVisTextAnchor>(anchor),
        static_cast<SiliconVisVuAnchor>(vuAnchor),
        vuEnabled,
        textSizeSp,
        paddingPx,
        static_cast<uint32_t>(gridColorArgb),
        gridWidthPx,
        static_cast<uint32_t>(lineColorArgb),
        lineWidthPx,
        static_cast<uint32_t>(vuColorArgb),
        &pal,
        shadowEnabled,
        hideWhenOverflow,
        windowMs,
        gainPercent,
        dcRemovalEnabled,
        triggerMode,
        static_cast<SiliconVisWaveRenderMode>(waveRenderMode)
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetOscilloscopeOptions(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jboolean stereo,
    jint windowMs,
    jint triggerMode,
    jint waveColorArgb,
    jfloat lineWidthPx,
    jint gridColorArgb,
    jfloat gridWidthPx,
    jboolean showCenterLine,
    jboolean showGrid
) {
    if (!handle) return;
    silicon_vis_set_oscilloscope_options(
        reinterpret_cast<SiliconVisHandle>(handle),
        stereo,
        windowMs,
        triggerMode,
        static_cast<uint32_t>(waveColorArgb),
        lineWidthPx,
        static_cast<uint32_t>(gridColorArgb),
        gridWidthPx,
        showCenterLine,
        showGrid
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetBarsOptions(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jint barCount,
    jfloat smoothing,
    jint startColorArgb,
    jint endColorArgb,
    jfloat cornerRadiusPx,
    jboolean showFrequencyGuide,
    jint guideColorArgb
) {
    if (!handle) return;
    silicon_vis_set_bars_options(
        reinterpret_cast<SiliconVisHandle>(handle),
        barCount,
        smoothing,
        static_cast<uint32_t>(startColorArgb),
        static_cast<uint32_t>(endColorArgb),
        cornerRadiusPx,
        showFrequencyGuide,
        static_cast<uint32_t>(guideColorArgb)
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeSetVuMetersOptions(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jboolean stereo,
    jint anchor,
    jfloat smoothing,
    jint fillColorArgb,
    jint trackColorArgb,
    jint labelColorArgb
) {
    if (!handle) return;
    silicon_vis_set_vu_meters_options(
        reinterpret_cast<SiliconVisHandle>(handle),
        stereo,
        anchor,
        smoothing,
        static_cast<uint32_t>(fillColorArgb),
        static_cast<uint32_t>(trackColorArgb),
        static_cast<uint32_t>(labelColorArgb)
    );
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_ui_visualization_gl_SiliconVisNativeBridge_nativeRender(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle
) {
    if (!handle) return;
    silicon_vis_render(reinterpret_cast<SiliconVisHandle>(handle));
}

} // extern "C"

extern "C" __attribute__((visibility("default"))) void silicon_vis_projectm_clear_last_preset() {
    s_projectMLastPreset.clear();
}
