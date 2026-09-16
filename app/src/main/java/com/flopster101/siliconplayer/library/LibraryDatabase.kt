package com.flopster101.siliconplayer.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import java.util.Locale

@Entity(
    tableName = "library_tracks",
    indices = [Index("dedupKey")]
)
data class LibraryTrackEntity(
    @PrimaryKey val path: String,
    val sourceId: String,
    val dedupKey: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNo: Int,
    val discNo: Int,
    val durationMs: Long,
    val year: Int,
    val format: String,
    val sizeBytes: Long,
    val mtimeMs: Long,
    val addedAtMs: Long
)

@Entity(tableName = "library_sources")
data class LibrarySourceEntity(
    @PrimaryKey val id: String,
    val enabled: Boolean,
    val lastSyncMs: Long = 0L
)

data class LibraryAlbumRow(
    val name: String,
    val artist: String,
    val distinctArtists: Int,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int,
    val artworkPath: String?
)

data class LibraryArtistRow(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artworkPath: String?
)

data class LibraryTrackSourcePair(
    val path: String,
    val sourceId: String
)

/**
 * Display-time identity for cross-source dedup: the same file can be indexed
 * by MediaStore and the storage scanner under textually different absolute
 * paths (for example `/sdcard/Music/x.mp3` versus
 * `/storage/emulated/0/Music/x.mp3`), which the path primary key alone
 * cannot collapse. The key folds `.`/`..` segments, well-known
 * emulated-storage aliases and case (Android media volumes are
 * case-insensitive); it is computed at insert time and only ever read.
 */
internal fun libraryDedupKeyForPath(path: String): String {
    val absolute = path.startsWith("/")
    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when {
            segment.isEmpty() || segment == "." -> Unit
            segment == ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    var normalized = (if (absolute) "/" else "") + segments.joinToString("/")
    for ((prefix, replacement) in DEDUP_PATH_ALIASES) {
        if (normalized.startsWith(prefix)) {
            normalized = replacement + normalized.removePrefix(prefix)
            break
        }
    }
    return normalized.lowercase(Locale.ROOT)
}

private val DEDUP_PATH_ALIASES = listOf(
    "/sdcard/" to "/storage/emulated/0/",
    "/mnt/sdcard/" to "/storage/emulated/0/",
    "/storage/emulated/legacy/" to "/storage/emulated/0/"
)

object LibraryContract {
    const val SOURCE_MEDIASTORE = "mediastore"
    const val SOURCE_SCANNER = "scanner"

    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN_ARTIST = "Unknown artist"
    const val VARIOUS_ARTISTS = "Various artists"
}

data class LibraryAlbum(
    val name: String,
    val artist: String,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int,
    val artworkPath: String?,
    val rawName: String = name
)

data class LibraryArtist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artworkPath: String?
)

data class LibraryCollections(
    val albums: List<LibraryAlbum>,
    val artists: List<LibraryArtist>,
    val trackCount: Int,
    val tracks: List<LibraryTrackEntity> = emptyList()
) {
    companion object {
        val Empty = LibraryCollections(
            albums = emptyList(),
            artists = emptyList(),
            trackCount = 0
        )
    }
}

@Dao
internal interface LibraryTrackDao {
    @Query("SELECT * FROM library_tracks WHERE sourceId = :sourceId")
    suspend fun tracksForSource(sourceId: String): List<LibraryTrackEntity>

    @Query("SELECT COUNT(*) FROM library_tracks")
    suspend fun trackCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM library_tracks t
        WHERE t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        """
    )
    suspend fun enabledTrackCount(dedupe: Boolean): Int

    @Query("SELECT COUNT(*) FROM library_tracks WHERE sourceId = :sourceId")
    suspend fun trackCountForSource(sourceId: String): Long

    @Query("SELECT path, sourceId FROM library_tracks")
    suspend fun trackSourcePairs(): List<LibraryTrackSourcePair>

    @Query(
        """
        SELECT t.* FROM library_tracks t
        WHERE COALESCE(t.album, '') = :album
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND COALESCE(u.album, '') = :album
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        ORDER BY COALESCE(NULLIF(t.albumArtist, ''), NULLIF(t.artist, ''), '') ASC,
                 t.discNo ASC, t.trackNo ASC, t.title COLLATE NOCASE ASC
        """
    )
    suspend fun albumTracks(album: String, dedupe: Boolean): List<LibraryTrackEntity>

    @Query(
        """
        SELECT COALESCE(t.album, '') AS name,
               :artist AS artist,
               COUNT(DISTINCT COALESCE(NULLIF(t.albumArtist, ''), NULLIF(t.artist, ''), '')) AS distinctArtists,
               COUNT(*) AS trackCount,
               SUM(t.durationMs) AS durationMs,
               MAX(t.year) AS year,
               MIN(t.path) AS artworkPath
        FROM library_tracks t
        WHERE t.albumArtist = :artist OR (COALESCE(t.albumArtist, '') = '' AND t.artist = :artist)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND (u.albumArtist = :artist OR (COALESCE(u.albumArtist, '') = '' AND u.artist = :artist))
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        GROUP BY COALESCE(t.album, '')
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun artistAlbumRows(artist: String, dedupe: Boolean): List<LibraryAlbumRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<LibraryTrackEntity>)

    @Query("DELETE FROM library_tracks WHERE path IN (:paths)")
    suspend fun deleteTracksByPath(paths: List<String>)

    @Query(
        """
        SELECT COALESCE(t.album, '') AS name,
               MIN(COALESCE(NULLIF(t.albumArtist, ''), NULLIF(t.artist, ''), '')) AS artist,
               COUNT(DISTINCT COALESCE(NULLIF(t.albumArtist, ''), NULLIF(t.artist, ''), '')) AS distinctArtists,
               COUNT(*) AS trackCount,
               SUM(t.durationMs) AS durationMs,
               MAX(t.year) AS year,
               MIN(t.path) AS artworkPath
        FROM library_tracks t
        WHERE t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        GROUP BY COALESCE(t.album, '')
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun albumRows(dedupe: Boolean): List<LibraryAlbumRow>

    @Query(
        """
        SELECT COALESCE(t.album, '') AS name,
               MIN(COALESCE(NULLIF(t.albumArtist, ''), NULLIF(t.artist, ''), '')) AS artist,
               COUNT(DISTINCT COALESCE(NULLIF(t.albumArtist, ''), NULLIF(t.artist, ''), '')) AS distinctArtists,
               COUNT(*) AS trackCount,
               SUM(t.durationMs) AS durationMs,
               MAX(t.year) AS year,
               MIN(t.path) AS artworkPath
        FROM library_tracks t
        WHERE COALESCE(t.album, '') LIKE :pattern ESCAPE '\'
          AND t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND COALESCE(u.album, '') LIKE :pattern ESCAPE '\'
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        GROUP BY COALESCE(t.album, '')
        ORDER BY name COLLATE NOCASE ASC
        LIMIT 24
        """
    )
    suspend fun searchAlbumRows(pattern: String, dedupe: Boolean): List<LibraryAlbumRow>

    @Query(
        """
        SELECT t.artist AS name, COUNT(*) AS trackCount,
               COUNT(DISTINCT COALESCE(t.album, '')) AS albumCount,
               MIN(t.path) AS artworkPath
        FROM library_tracks t
        WHERE t.artist != ''
          AND t.artist LIKE :pattern ESCAPE '\'
          AND t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND u.artist != ''
                AND u.artist LIKE :pattern ESCAPE '\'
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        GROUP BY t.artist
        ORDER BY name COLLATE NOCASE ASC
        LIMIT 24
        """
    )
    suspend fun searchArtistRows(pattern: String, dedupe: Boolean): List<LibraryArtistRow>

    @Query(
        """
        SELECT t.* FROM library_tracks t
        WHERE t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND (
                    u.title LIKE :pattern ESCAPE '\'
                    OR u.artist LIKE :pattern ESCAPE '\'
                    OR COALESCE(u.album, '') LIKE :pattern ESCAPE '\'
                )
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
          AND (
              t.title LIKE :pattern ESCAPE '\'
              OR t.artist LIKE :pattern ESCAPE '\'
              OR COALESCE(t.album, '') LIKE :pattern ESCAPE '\'
          )
        ORDER BY t.title COLLATE NOCASE ASC
        LIMIT 100
        """
    )
    suspend fun searchTracks(pattern: String, dedupe: Boolean): List<LibraryTrackEntity>

    @Query(
        """
        SELECT t.* FROM library_tracks t
        WHERE t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        ORDER BY t.title COLLATE NOCASE ASC, t.artist COLLATE NOCASE ASC
        """
    )
    suspend fun allTracks(dedupe: Boolean): List<LibraryTrackEntity>

    @Query(
        """
        SELECT t.* FROM library_tracks t
        WHERE (t.albumArtist = :artist OR (COALESCE(t.albumArtist, '') = '' AND t.artist = :artist))
          AND t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND (u.albumArtist = :artist OR (COALESCE(u.albumArtist, '') = '' AND u.artist = :artist))
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        ORDER BY COALESCE(t.album, '') COLLATE NOCASE ASC,
                 t.discNo ASC, t.trackNo ASC, t.title COLLATE NOCASE ASC
        """
    )
    suspend fun artistTracks(artist: String, dedupe: Boolean): List<LibraryTrackEntity>

    @Query(
        """
        SELECT t.artist AS name, COUNT(*) AS trackCount,
               COUNT(DISTINCT COALESCE(t.album, '')) AS albumCount,
               MIN(t.path) AS artworkPath
        FROM library_tracks t
        WHERE t.artist != ''
          AND t.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (:dedupe = 0 OR NOT EXISTS (
              SELECT 1 FROM library_tracks u
              WHERE u.sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
                AND u.artist != ''
                AND u.dedupKey = t.dedupKey
                AND (
                  CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END <
                      CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                  OR (
                    CASE u.sourceId WHEN 'scanner' THEN 0 ELSE 1 END =
                        CASE t.sourceId WHEN 'scanner' THEN 0 ELSE 1 END
                    AND u.path < t.path
                  )
              )
          ))
        GROUP BY t.artist
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun artistRows(dedupe: Boolean): List<LibraryArtistRow>
}

@Dao
internal interface LibrarySourceDao {
    @Query("SELECT * FROM library_sources WHERE id = :id")
    suspend fun source(id: String): LibrarySourceEntity?

    @Query("SELECT * FROM library_sources ORDER BY id")
    suspend fun allSources(): List<LibrarySourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: LibrarySourceEntity)

    @Query("UPDATE library_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setSourceEnabled(id: String, enabled: Boolean)

    @Query("UPDATE library_sources SET lastSyncMs = :syncedAtMs WHERE id = :id")
    suspend fun setSourceSynced(id: String, syncedAtMs: Long)
}

@Database(
    entities = [LibraryTrackEntity::class, LibrarySourceEntity::class],
    version = 3,
    exportSchema = false
)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun trackDao(): LibraryTrackDao
    abstract fun sourceDao(): LibrarySourceDao

    companion object {
        @Volatile
        private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "library.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
