package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.content.SharedPreferences
import com.flopster101.siliconplayer.AppPreferenceKeys
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ProjectMPack(
    val id: String,
    val label: String,
    val description: String,
    val url: String,
    val sizeLabel: String,
    val requiresTextures: Boolean = false
)

object ProjectMPresetDownloader {
    const val PREF_DOWNLOAD_PROMPT_DISMISSED = "visualization_projectm_download_prompt_dismissed"

    val PACKS = listOf(
        ProjectMPack(
            id = "downloaded_milkdrop_original",
            label = "MilkDrop Original",
            description = "Original Winamp presets + textures (recommended)",
            url = "https://github.com/projectM-visualizer/presets-milkdrop-original/archive/refs/heads/master.zip",
            sizeLabel = "~2 MB + 80 MB textures",
            requiresTextures = true
        ),
        ProjectMPack(
            id = "downloaded_textures",
            label = "MilkDrop Textures",
            description = "Texture pack required by most presets",
            url = "https://github.com/projectM-visualizer/presets-milkdrop-texture-pack/archive/refs/heads/master.zip",
            sizeLabel = "~80 MB",
            requiresTextures = false
        ),
        ProjectMPack(
            id = "downloaded_cream_of_the_crop",
            label = "Cream of the Crop",
            description = "9.8k curated presets by ISOSCELES",
            url = "https://github.com/projectM-visualizer/presets-cream-of-the-crop/archive/refs/heads/master.zip",
            sizeLabel = "~40 MB",
            requiresTextures = true
        ),
        ProjectMPack(
            id = "downloaded_projectm_classic",
            label = "Classic projectM",
            description = "4k presets from projectM < 3.1.12",
            url = "https://github.com/projectM-visualizer/presets-projectm-classic/archive/refs/heads/master.zip",
            sizeLabel = "~15 MB",
            requiresTextures = true
        ),
        ProjectMPack(
            id = "downloaded_en_d",
            label = "En D",
            description = "~50 presets by En D",
            url = "https://github.com/projectM-visualizer/presets-en-d/archive/refs/heads/master.zip",
            sizeLabel = "~1 MB",
            requiresTextures = true
        )
    )

    fun downloadedDir(context: Context, packId: String): File =
        File(context.filesDir, "projectm_packs/$packId")

    fun isDownloaded(context: Context, packId: String): Boolean {
        val dir = downloadedDir(context, packId)
        if (!dir.isDirectory) return false
        // Consider downloaded if contains at least one file
        return dir.listFiles()?.isNotEmpty() == true
    }

    fun downloadedPacks(context: Context): List<ProjectMPack> =
        PACKS.filter { isDownloaded(context, it.id) }

    fun isAnyDownloaded(context: Context): Boolean =
        PACKS.any { isDownloaded(context, it.id) }

    suspend fun download(
        context: Context,
        pack: ProjectMPack,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val dir = downloadedDir(context, pack.id)
            val tmpZip = File(context.cacheDir, "${pack.id}.zip.tmp")
            val tmpDir = File(context.cacheDir, "${pack.id}.extract.tmp")
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            if (tmpZip.exists()) tmpZip.delete()

            val url = URL(pack.url)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SiliconPlayer")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(RuntimeException("HTTP ${conn.responseCode}"))
            }
            val total = conn.contentLengthLong.takeIf { it > 0 }
            var downloaded = 0L
            conn.inputStream.use { input ->
                BufferedInputStream(input).use { bin ->
                    FileOutputStream(tmpZip).use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = bin.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            conn.disconnect()
            conn = null

            // Extract zip, stripping top-level folder
            coroutineContext.ensureActive()
            ZipInputStream(BufferedInputStream(tmpZip.inputStream())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val name = entry.name
                    // Strip top-level dir (e.g., presets-milkdrop-original-master/)
                    val stripped = name.substringAfter('/', missingDelimiterValue = "")
                    if (stripped.isEmpty()) {
                        // Top-level dir entry
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(tmpDir, stripped)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            val buf = ByteArray(8192)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = zis.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()

            // Move to final dir
            if (dir.exists()) dir.deleteRecursively()
            dir.parentFile?.mkdirs()
            // Rename tmpDir to dir (fallback to copy if rename fails)
            if (!tmpDir.renameTo(dir)) {
                dir.mkdirs()
                tmpDir.copyRecursively(dir, overwrite = true)
                tmpDir.deleteRecursively()
            }
            Result.success(dir)
        } catch (e: Throwable) {
            try { conn?.disconnect() } catch (_: Throwable) {}
            // Clean partial files on cancel
            if (e is kotlinx.coroutines.CancellationException) {
                try { File(context.cacheDir, "${pack.id}.zip.tmp").delete() } catch (_: Throwable) {}
                try { File(context.cacheDir, "${pack.id}.extract.tmp").deleteRecursively() } catch (_: Throwable) {}
            }
            Result.failure(e)
        }
    }

    fun remove(context: Context, packId: String) {
        try {
            val dir = downloadedDir(context, packId)
            if (dir.exists()) dir.deleteRecursively()
        } catch (_: Throwable) {}
    }

    fun ensureTexturesForPack(context: Context, packId: String) {
        if (!isDownloaded(context, "downloaded_textures")) return
        val texDir = downloadedDir(context, "downloaded_textures")
        val packDir = downloadedDir(context, packId)
        if (!packDir.isDirectory || !texDir.isDirectory) return
        // Merge textures into pack dir if missing (avoid re-copy)
        texDir.listFiles()?.forEach { src ->
            val dest = File(packDir, src.name)
            if (!dest.exists()) {
                try {
                    if (src.isDirectory) src.copyRecursively(dest, overwrite = false)
                    else src.copyTo(dest)
                } catch (_: Throwable) {}
            }
        }
    }
}
