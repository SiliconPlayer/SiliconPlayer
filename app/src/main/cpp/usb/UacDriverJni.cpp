#include <jni.h>
#include <android/log.h>
#include "UacDriver.h"

#define TAG "UacDriverJni"

namespace {
inline siliconplayer::usb::UacDriver& driver() {
    return siliconplayer::usb::getUacDriverInstance();
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeInit(JNIEnv*, jobject) {
    return driver().ensureContext() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeOpen(JNIEnv*, jobject, jint fd) {
    return driver().open(static_cast<int>(fd)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeClose(JNIEnv*, jobject) {
    driver().close();
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeIsOpen(JNIEnv*, jobject) {
    return driver().isOpen() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeStart(
    JNIEnv*, jobject, jint sampleRate, jint bitsPerSample, jint channels) {
    return driver().start(sampleRate, bitsPerSample, channels) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeStop(JNIEnv*, jobject) {
    driver().stop();
}

JNIEXPORT void JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeFlushRing(JNIEnv*, jobject) {
    driver().flushRing();
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeIsStreamingFormat(
    JNIEnv*, jobject, jint sampleRate, jint bitsPerSample, jint channels) {
    return driver().isStreamingFormat(sampleRate, bitsPerSample, channels) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeIsStreaming(JNIEnv*, jobject) {
    return driver().isStreaming() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeWrite(
    JNIEnv* env, jobject, jobject directBuffer, jint frames) {
    if (!directBuffer || frames <= 0) return 0;
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(directBuffer));
    if (!base) return 0;
    return driver().writePcm(base, frames);
}

JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeWritableFrames(JNIEnv*, jobject) {
    return driver().writableFrames();
}

JNIEXPORT jlong JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativePlayedFrames(JNIEnv*, jobject) {
    return static_cast<jlong>(driver().playedFrames());
}

JNIEXPORT jlong JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativePendingFrames(JNIEnv*, jobject) {
    long w = driver().writtenFrames();
    long p = driver().playedFrames();
    return static_cast<jlong>(w > p ? w - p : 0);
}

JNIEXPORT jint JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeLastErrorCode(JNIEnv*, jobject) {
    return static_cast<jint>(driver().lastError());
}

JNIEXPORT jstring JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeLastErrorDetail(JNIEnv* env, jobject) {
    std::string s = driver().lastErrorDetail();
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT jintArray JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeSupportedRates(JNIEnv* env, jobject) {
    auto ranges = driver().supportedRates();
    jintArray arr = env->NewIntArray(static_cast<jsize>(ranges.size() * 4));
    if (!arr || ranges.empty()) return arr;
    std::vector<jint> packed;
    packed.reserve(ranges.size() * 4);
    for (const auto& r : ranges) {
        packed.push_back(static_cast<jint>(r.clockId));
        packed.push_back(static_cast<jint>(r.minHz));
        packed.push_back(static_cast<jint>(r.maxHz));
        packed.push_back(static_cast<jint>(r.resHz));
    }
    env->SetIntArrayRegion(arr, 0, static_cast<jsize>(packed.size()), packed.data());
    return arr;
}

JNIEXPORT jlongArray JNICALL
Java_com_flopster101_siliconplayer_usb_UacDriverNative_nativeFormatDiagnostics(JNIEnv* env, jobject) {
    if (!driver().isStreaming()) return nullptr;
    const auto& f = driver().currentFormat();
    jlong values[13] = {
        static_cast<jlong>(f.sampleRateHz),
        static_cast<jlong>(f.bitsPerSample),
        static_cast<jlong>(f.channels),
        static_cast<jlong>(f.interfaceNumber),
        static_cast<jlong>(f.altSetting),
        static_cast<jlong>(f.endpointAddress),
        static_cast<jlong>(f.maxPacketSize),
        static_cast<jlong>(f.bInterval),
        static_cast<jlong>(f.uacVersion),
        static_cast<jlong>(f.clockSourceId),
        static_cast<jlong>(f.feedbackEndpointAddress),
        static_cast<jlong>(f.isHighSpeed ? 1 : 0),
        static_cast<jlong>(f.bytesPerSample),
    };
    jlongArray arr = env->NewLongArray(13);
    if (!arr) return nullptr;
    env->SetLongArrayRegion(arr, 0, 13, values);
    return arr;
}

} // extern "C"
