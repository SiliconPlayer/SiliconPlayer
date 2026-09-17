package com.flopster101.siliconplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.util.Locale

internal fun resolveFileFromViewIntent(context: Context?, intent: Intent?): File? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    return try {
        val file = when (uri.scheme) {
            "file" -> File(uri.path ?: return null)
            "content" -> {
                val path = queryRealPathFromUri(context, uri)
                if (path != null) File(path) else null
            }
            else -> null
        }
        if (file?.exists() == true) file else null
    } catch (_: Exception) {
        null
    }
}

internal fun resolveFileFromViewIntent(contentResolver: ContentResolver, intent: Intent?): File? {
    return resolveFileFromViewIntent(null as Context?, intent)
}

internal fun queryRealPathFromUri(context: Context?, uri: Uri): String? {
    if (uri.scheme == "file") {
        return uri.path
    }
    if (uri.scheme != "content") {
        return null
    }

    if (context != null) {
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val resolved = resolveDocId(context, uri.authority, docId)
                if (resolved != null) return resolved
            }
        }
        runCatching {
            if (DocumentsContract.isTreeUri(uri)) {
                val treeId = DocumentsContract.getTreeDocumentId(uri)
                val resolved = resolveDocId(context, uri.authority, treeId)
                if (resolved != null) return resolved
            }
        }
    }

    val pathStringFallback = resolveExternalStoragePathFallback(context, uri)
    if (pathStringFallback != null) {
        return pathStringFallback
    }

    val resolver = context?.contentResolver
    if (resolver != null) {
        val mediaData = queryContentResolverData(resolver, uri)
        if (mediaData != null) return mediaData
    }

    return null
}

internal fun queryRealPathFromUri(contentResolver: ContentResolver, uri: Uri): String? {
    val fallback = resolveExternalStoragePathFallback(null, uri)
    if (fallback != null) return fallback
    return queryContentResolverData(contentResolver, uri)
}

internal fun resolveDocId(context: Context?, authority: String?, docId: String): String? {
    if (docId.isBlank()) return null
    return when (authority) {
        "com.android.externalstorage.documents" -> resolveExternalStorageDocId(context, docId)
        "com.android.providers.downloads.documents" -> context?.let { resolveDownloadsDocId(it, docId) }
        "com.android.providers.media.documents" -> context?.let { resolveMediaDocId(it, docId) }
        else -> null
    }
}

internal fun resolveExternalStorageDocId(context: Context?, docId: String): String? {
    val split = docId.split(':', limit = 2)
    val type = split.getOrNull(0) ?: return null
    val relativePath = split.getOrNull(1).orEmpty().removePrefix("/")

    if (type.equals("primary", ignoreCase = true)) {
        val primaryDir = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?: File("/storage/emulated/0")
        val file = if (relativePath.isBlank()) primaryDir else File(primaryDir, relativePath)
        return file.absolutePath
    }

    val directFile = File("/storage/$type", relativePath)
    if (directFile.exists()) {
        return directFile.absolutePath
    }

    if (context != null) {
        val roots = runCatching {
            context.getExternalFilesDirs(null).mapNotNull { dir ->
                dir?.let { resolveStorageRootFromAppDir(it) }
            }
        }.getOrDefault(emptyList())

        for (root in roots) {
            if (root.name.equals(type, ignoreCase = true)) {
                val candidate = if (relativePath.isBlank()) root else File(root, relativePath)
                return candidate.absolutePath
            }
            val candidate = File(root, relativePath)
            if (candidate.exists()) {
                return candidate.absolutePath
            }
        }
    }

    return directFile.absolutePath
}

private fun resolveDownloadsDocId(context: Context, docId: String): String? {
    if (docId.startsWith("raw:")) {
        val rawPath = docId.removePrefix("raw:")
        if (rawPath.isNotBlank()) return rawPath
    }
    if (docId.startsWith("msf:")) {
        val msfId = docId.removePrefix("msf:")
        return queryContentResolverData(
            contentResolver = context.contentResolver,
            uri = MediaStore.Files.getContentUri("external"),
            selection = "${BaseColumns._ID}=?",
            selectionArgs = arrayOf(msfId)
        )
    }
    val numericId = docId.toLongOrNull()
    if (numericId != null) {
        val downloadUri = ContentUris.withAppendedId(
            Uri.parse("content://downloads/public_downloads"),
            numericId
        )
        val data = queryContentResolverData(context.contentResolver, downloadUri)
        if (data != null) return data

        return queryContentResolverData(
            contentResolver = context.contentResolver,
            uri = MediaStore.Files.getContentUri("external"),
            selection = "${BaseColumns._ID}=?",
            selectionArgs = arrayOf(docId)
        )
    }
    return null
}

private fun resolveMediaDocId(context: Context, docId: String): String? {
    val split = docId.split(':', limit = 2)
    val type = split.getOrNull(0)?.lowercase(Locale.ROOT) ?: return null
    val id = split.getOrNull(1) ?: return null
    val contentUri = when (type) {
        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Files.getContentUri("external")
    }
    return queryContentResolverData(
        contentResolver = context.contentResolver,
        uri = contentUri,
        selection = "${BaseColumns._ID}=?",
        selectionArgs = arrayOf(id)
    )
}

internal fun resolveExternalStoragePathFallback(context: Context?, uri: Uri): String? {
    val path = uri.path ?: return null
    val marker = when {
        path.contains("/document/") -> "/document/"
        path.contains("/tree/") -> "/tree/"
        else -> null
    } ?: return null

    val afterMarker = path.substringAfterLast(marker)
    if (afterMarker.contains(':')) {
        val decoded = runCatching { Uri.decode(afterMarker) }.getOrDefault(afterMarker)
        return resolveExternalStorageDocId(context, decoded)
    }
    return null
}

private fun queryContentResolverData(
    contentResolver: ContentResolver,
    uri: Uri,
    selection: String? = null,
    selectionArgs: Array<String>? = null
): String? {
    return runCatching {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0) {
                    val path = cursor.getString(index)
                    if (!path.isNullOrBlank() && path.startsWith("/")) {
                        return@use path
                    }
                }
            }
            null
        }
    }.getOrNull()
}
