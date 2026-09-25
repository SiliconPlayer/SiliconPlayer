package com.flopster101.siliconplayer.platform

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File
import java.util.Locale

private enum class MountKindHint {
    SD,
    USB
}

private data class RemovableVolumeCandidate(
    val root: File,
    val description: String,
    val hasUsbMarker: Boolean,
    val hasSdMarker: Boolean,
    val mountKindHint: MountKindHint?
)

internal fun detectAndroidStorageLocations(context: Context): List<PlatformStorageLocation> {
    val results = mutableListOf<PlatformStorageLocation>()
    val seenPaths = mutableSetOf<String>()

    fun addLocation(kind: StorageLocationKind, typeLabel: String, name: String, directory: File) {
        val normalizedPath = directory.absolutePath
        if (normalizedPath in seenPaths) return
        if (!directory.exists() || !directory.isDirectory) return
        results += PlatformStorageLocation(
            id = normalizedPath,
            kind = kind,
            typeLabel = typeLabel,
            name = name,
            directory = directory
        )
        seenPaths += normalizedPath
    }

    addLocation(
        kind = StorageLocationKind.ROOT,
        typeLabel = "Root",
        name = "/",
        directory = File("/")
    )

    val internalStorage = Environment.getExternalStorageDirectory()
    addLocation(
        kind = StorageLocationKind.INTERNAL,
        typeLabel = "Internal storage",
        name = internalStorage.absolutePath,
        directory = internalStorage
    )

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            if (Environment.isExternalStorageRemovable(externalDir)) return@forEach
            val volumeRoot = resolveVolumeRoot(externalDir) ?: return@forEach
            addLocation(
                kind = StorageLocationKind.INTERNAL,
                typeLabel = "Internal storage",
                name = volumeRoot.absolutePath,
                directory = volumeRoot
            )
        }

    val storageManager: StorageManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(StorageManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    }
    val removableFromVolumes = mutableSetOf<String>()
    val volumeCandidates = mutableListOf<RemovableVolumeCandidate>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        storageManager?.storageVolumes.orEmpty().forEach { volume ->
            if (!volume.isRemovable) return@forEach
            val volumeRoot = resolveStorageVolumeRoot(volume) ?: return@forEach
            val description = volume.getDescription(context).orEmpty().trim()
            val detectionText = "${description.lowercase(Locale.ROOT)} ${volumeRoot.absolutePath.lowercase(Locale.ROOT)}"
            volumeCandidates += RemovableVolumeCandidate(
                root = volumeRoot,
                description = description,
                hasUsbMarker = detectionText.contains("usb") || detectionText.contains("otg"),
                hasSdMarker = detectionText.contains("sd card") ||
                    detectionText.contains("sdcard") ||
                    detectionText.contains(" microsd") ||
                    detectionText.contains("sd "),
                mountKindHint = detectMountKindHint(volumeRoot)
            )
        }
    }

    volumeCandidates.forEach { candidate ->
        val isUsb = when {
            candidate.hasUsbMarker && !candidate.hasSdMarker -> true
            candidate.hasSdMarker && !candidate.hasUsbMarker -> false
            candidate.mountKindHint == MountKindHint.USB -> true
            candidate.mountKindHint == MountKindHint.SD -> false
            else -> false
        }
        val label = candidate.description.ifBlank { candidate.root.name.ifBlank { "Volume" } }
        val typeLabel = if (isUsb) "$label (USB)" else "$label (SD)"
        addLocation(
            kind = if (isUsb) StorageLocationKind.USB else StorageLocationKind.SD,
            typeLabel = typeLabel,
            name = candidate.root.absolutePath,
            directory = candidate.root
        )
        removableFromVolumes += candidate.root.absolutePath
    }

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveVolumeRoot(externalDir) ?: return@forEach
            if (volumeRoot.absolutePath in removableFromVolumes) return@forEach
            val pathLower = volumeRoot.absolutePath.lowercase(Locale.ROOT)
            val isRemovable = Environment.isExternalStorageRemovable(externalDir)
            if (!isRemovable) return@forEach
            val mountKindHint = detectMountKindHint(volumeRoot)

            val isUsb = when {
                pathLower.contains("usb") || pathLower.contains("otg") -> true
                pathLower.contains("sd") -> false
                mountKindHint == MountKindHint.USB -> true
                mountKindHint == MountKindHint.SD -> false
                else -> false
            }
            val label = volumeRoot.name.ifBlank { "Volume" }
            val typeLabel = if (isUsb) "$label (USB)" else "$label (SD)"
            addLocation(
                kind = if (isUsb) StorageLocationKind.USB else StorageLocationKind.SD,
                typeLabel = typeLabel,
                name = volumeRoot.absolutePath,
                directory = volumeRoot
            )
        }

    return results
}

private fun resolveStorageVolumeRoot(volume: StorageVolume): File? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        volume.directory?.let { directory ->
            if (directory.exists() && directory.isDirectory) return directory
        }
    }
    runCatching {
        val method = StorageVolume::class.java.getMethod("getPathFile")
        (method.invoke(volume) as? File)?.let { file ->
            if (file.exists() && file.isDirectory) return file
        }
    }
    runCatching {
        val method = StorageVolume::class.java.getMethod("getPath")
        val path = method.invoke(volume) as? String
        path?.let { File(it) }?.let { file ->
            if (file.exists() && file.isDirectory) return file
        }
    }
    return null
}

private fun resolveVolumeRoot(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

private fun detectMountKindHint(volumeRoot: File): MountKindHint? {
    val rootName = volumeRoot.name.lowercase(Locale.ROOT)
    val rootPath = volumeRoot.absolutePath.lowercase(Locale.ROOT)
    return when {
        rootName.contains("usb") || rootPath.contains("usb") -> MountKindHint.USB
        rootName.contains("sd") || rootPath.contains("sd") -> MountKindHint.SD
        else -> null
    }
}
