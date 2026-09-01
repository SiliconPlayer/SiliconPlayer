package com.flopster101.siliconplayer.usb

/**
 * Strategy for scaling the output volume sent to the USB DAC in Direct UAC mode.
 */
enum class DirectUacVolumeMode(
    val storageValue: String,
    val displayName: String,
    val description: String
) {
    None(
        storageValue = "none",
        displayName = "None",
        description = "Pure bit-perfect output with no software volume attenuation (0 dBFS)."
    ),
    System(
        storageValue = "system",
        displayName = "System volume",
        description = "Scales USB output with Android media volume buttons."
    ),
    Manual(
        storageValue = "manual",
        displayName = "Manual",
        description = "Adjust volume with a dedicated slider in the audio output details."
    );

    companion object {
        fun fromStorage(value: String?): DirectUacVolumeMode {
            return entries.firstOrNull { it.storageValue == value } ?: System
        }
    }
}
