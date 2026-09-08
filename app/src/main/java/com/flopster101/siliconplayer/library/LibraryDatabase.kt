package com.flopster101.siliconplayer.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Entity(tableName = "library_tracks")
data class LibraryTrackEntity(
    @PrimaryKey val path: String,
    val sourceId: String,
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
    val trackCount: Int
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

    @Query("SELECT COUNT(*) FROM library_tracks WHERE sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)")
    suspend fun enabledTrackCount(): Int

    @Query("SELECT COUNT(*) FROM library_tracks WHERE sourceId = :sourceId")
    suspend fun trackCountForSource(sourceId: String): Long

    @Query("SELECT path, sourceId FROM library_tracks")
    suspend fun trackSourcePairs(): List<LibraryTrackSourcePair>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE COALESCE(album, '') = :album
        ORDER BY COALESCE(NULLIF(albumArtist, ''), NULLIF(artist, ''), '') ASC,
                 discNo ASC, trackNo ASC, title COLLATE NOCASE ASC
        """
    )
    suspend fun albumTracks(album: String): List<LibraryTrackEntity>

    @Query(
        """
        SELECT COALESCE(album, '') AS name,
               :artist AS artist,
               COUNT(DISTINCT COALESCE(NULLIF(albumArtist, ''), NULLIF(artist, ''), '')) AS distinctArtists,
               COUNT(*) AS trackCount,
               SUM(durationMs) AS durationMs,
               MAX(year) AS year,
               MIN(path) AS artworkPath
        FROM library_tracks
        WHERE albumArtist = :artist OR (COALESCE(albumArtist, '') = '' AND artist = :artist)
        GROUP BY COALESCE(album, '')
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun artistAlbumRows(artist: String): List<LibraryAlbumRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<LibraryTrackEntity>)

    @Query("DELETE FROM library_tracks WHERE path IN (:paths)")
    suspend fun deleteTracksByPath(paths: List<String>)

    @Query(
        """
        SELECT COALESCE(album, '') AS name,
               MIN(COALESCE(NULLIF(albumArtist, ''), NULLIF(artist, ''), '')) AS artist,
               COUNT(DISTINCT COALESCE(NULLIF(albumArtist, ''), NULLIF(artist, ''), '')) AS distinctArtists,
               COUNT(*) AS trackCount,
               SUM(durationMs) AS durationMs,
               MAX(year) AS year,
               MIN(path) AS artworkPath
        FROM library_tracks
        WHERE sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
        GROUP BY COALESCE(album, '')
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun albumRows(): List<LibraryAlbumRow>

    @Query(
        """
        SELECT COALESCE(album, '') AS name,
               MIN(COALESCE(NULLIF(albumArtist, ''), NULLIF(artist, ''), '')) AS artist,
               COUNT(DISTINCT COALESCE(NULLIF(albumArtist, ''), NULLIF(artist, ''), '')) AS distinctArtists,
               COUNT(*) AS trackCount,
               SUM(durationMs) AS durationMs,
               MAX(year) AS year,
               MIN(path) AS artworkPath
        FROM library_tracks
        WHERE COALESCE(album, '') LIKE :pattern ESCAPE '\'
          AND sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
        GROUP BY COALESCE(album, '')
        ORDER BY name COLLATE NOCASE ASC
        LIMIT 24
        """
    )
    suspend fun searchAlbumRows(pattern: String): List<LibraryAlbumRow>

    @Query(
        """
        SELECT artist AS name, COUNT(*) AS trackCount,
               COUNT(DISTINCT COALESCE(album, '')) AS albumCount,
               MIN(path) AS artworkPath
        FROM library_tracks
        WHERE artist != ''
          AND artist LIKE :pattern ESCAPE '\'
          AND sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
        GROUP BY artist
        ORDER BY name COLLATE NOCASE ASC
        LIMIT 24
        """
    )
    suspend fun searchArtistRows(pattern: String): List<LibraryArtistRow>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
          AND (
              title LIKE :pattern ESCAPE '\'
              OR artist LIKE :pattern ESCAPE '\'
              OR COALESCE(album, '') LIKE :pattern ESCAPE '\'
          )
        ORDER BY title COLLATE NOCASE ASC
        LIMIT 100
        """
    )
    suspend fun searchTracks(pattern: String): List<LibraryTrackEntity>

    @Query(
        """
        SELECT artist AS name, COUNT(*) AS trackCount,
               COUNT(DISTINCT COALESCE(album, '')) AS albumCount,
               MIN(path) AS artworkPath
        FROM library_tracks
        WHERE artist != ''
          AND sourceId IN (SELECT id FROM library_sources WHERE enabled = 1)
        GROUP BY artist
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    suspend fun artistRows(): List<LibraryArtistRow>
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
    version = 2,
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
