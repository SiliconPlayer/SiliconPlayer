package com.flopster101.siliconplayer

// Tests must not feed the machine's real playback device: route the shared
// native engine to miniaudio's null backend before starting it.
fun useSilentTestAudioOutput() {
    NativeBridge.setAudioPipelineConfig(
        AudioBackendPreference.NullAudio.nativeValue,
        AudioPerformanceMode.None.nativeValue,
        AudioBufferPreset.Large.nativeValue,
        AudioResamplerPreference.BuiltIn.nativeValue,
        false
    )
}
