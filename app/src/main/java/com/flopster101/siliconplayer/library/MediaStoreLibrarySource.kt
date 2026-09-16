package com.flopster101.siliconplayer.library

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal object MediaStoreLibrarySource {

    private val PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ARTIST,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DISC_NUMBER,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.DATA
    )

    private const val COL_ID = 0
    private const val COL_TITLE = 1
    private const val COL_ARTIST = 2
    private const val COL_ALBUM = 3
    private const val COL_ALBUM_ARTIST = 4
    private const val COL_TRACK = 5
    private const val COL_DISC = 6
    private const val COL_DURATION = 7
    private const val COL_YEAR = 8
    private const val COL_SIZE = 9
    private const val COL_DATE_MODIFIED = 10
    private const val COL_DATA = 11

    suspend fun sync(context: Context, dao: LibraryTrackDao): Int = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val fetched = LinkedHashMap<String, LibraryTrackEntity>()
        val addedAtMs = System.currentTimeMillis()

        context.contentResolver.query(collection, PROJECTION, selection, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(COL_DATA) ?: continue
                if (path.isBlank()) continue
                val durationMs = cursor.getLong(COL_DURATION)
                if (durationMs <= 0L) continue
                val title = cursor.getString(COL_TITLE)?.takeIf { it.isNotBlank() }
                    ?: File(path).nameWithoutExtension
                val format = File(path).extension.uppercase()
                fetched[path] = LibraryTrackEntity(
                    path = path,
                    sourceId = LibraryContract.SOURCE_MEDIASTORE,
                    dedupKey = libraryDedupKeyForPath(path),
                    title = title,
                    artist = cursor.getString(COL_ARTIST)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: LibraryContract.UNKNOWN_ARTIST,
                    albumArtist = cursor.getString(COL_ALBUM_ARTIST)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: "",
                    album = cursor.getString(COL_ALBUM)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: LibraryContract.UNKNOWN_ALBUM,
                    trackNo = cursor.getInt(COL_TRACK) % 1000,
                    discNo = cursor.getInt(COL_DISC),
                    durationMs = durationMs,
                    year = cursor.getInt(COL_YEAR),
                    format = format,
                    sizeBytes = cursor.getLong(COL_SIZE),
                    mtimeMs = cursor.getLong(COL_DATE_MODIFIED) * 1000L,
                    addedAtMs = addedAtMs
                )
            }
        } ?: return@withContext 0

        val existing = dao.tracksForSource(LibraryContract.SOURCE_MEDIASTORE).associateBy { it.path }
        val changed = fetched.values.filter { fresh ->
            val stale = existing[fresh.path]
            stale == null || stale.mtimeMs != fresh.mtimeMs || stale.sizeBytes != fresh.sizeBytes
        }
        val removed = existing.keys - fetched.keys
        if (changed.isNotEmpty()) dao.upsertTracks(changed)
        if (removed.isNotEmpty()) dao.deleteTracksByPath(removed.toList())
        changed.size
    }
}
