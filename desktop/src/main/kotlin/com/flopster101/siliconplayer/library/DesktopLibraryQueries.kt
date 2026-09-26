package com.flopster101.siliconplayer.library

// In-memory mirrors of the Room queries; single source, no dedupe clauses.

private val NoCaseOrder = String.CASE_INSENSITIVE_ORDER

private fun LibraryTrackEntity.displayArtist(): String =
    albumArtist.ifBlank { artist }

internal fun libraryCollections(tracks: List<LibraryTrackEntity>): LibraryCollections {
    if (tracks.isEmpty()) return LibraryCollections.Empty
    val albums = tracks.groupBy { it.album }.map { (rawName, group) ->
        val artists = group.map { it.displayArtist() }.filter { it.isNotBlank() }.distinct()
        LibraryAlbum(
            name = rawName.ifBlank { LibraryContract.UNKNOWN_ALBUM },
            artist = when {
                artists.size > 1 -> LibraryContract.VARIOUS_ARTISTS
                artists.size == 1 -> artists[0]
                else -> LibraryContract.UNKNOWN_ARTIST
            },
            trackCount = group.size,
            durationMs = group.sumOf { it.durationMs },
            year = group.maxOf { it.year },
            artworkPath = group.minOf { it.path },
            rawName = rawName
        )
    }.sortedWith { left, right -> NoCaseOrder.compare(left.name, right.name) }
    val artists = tracks.filter { it.artist.isNotBlank() }.groupBy { it.artist }.map { (name, group) ->
        LibraryArtist(
            name = name,
            trackCount = group.size,
            albumCount = group.map { it.album }.distinct().size,
            artworkPath = group.minOf { it.path }
        )
    }.sortedWith { left, right -> NoCaseOrder.compare(left.name, right.name) }
    return LibraryCollections(
        albums = albums,
        artists = artists,
        trackCount = tracks.size,
        tracks = tracks.sortedWith(
            compareBy(NoCaseOrder, { t: LibraryTrackEntity -> t.title }).thenBy(NoCaseOrder, { t: LibraryTrackEntity -> t.artist })
        )
    )
}

internal fun librarySearch(tracks: List<LibraryTrackEntity>, rawQuery: String): LibrarySearchResults {
    val query = rawQuery.trim()
    if (query.isEmpty()) {
        return LibrarySearchResults(query, emptyList(), emptyList(), emptyList())
    }
    // SQL LIKE %pattern% is case-insensitive for ASCII; contains(ignoreCase)
    // is the in-memory equivalent.
    val albums = tracks.groupBy { it.album }
        .filter { (name, _) -> name.contains(query, ignoreCase = true) }
        .map { (rawName, group) ->
            val artists = group.map { it.displayArtist() }.filter { it.isNotBlank() }.distinct()
            LibraryAlbum(
                name = rawName.ifBlank { LibraryContract.UNKNOWN_ALBUM },
                artist = when {
                    artists.size > 1 -> LibraryContract.VARIOUS_ARTISTS
                    artists.size == 1 -> artists[0]
                    else -> LibraryContract.UNKNOWN_ARTIST
                },
                trackCount = group.size,
                durationMs = group.sumOf { it.durationMs },
                year = group.maxOf { it.year },
                artworkPath = group.minOf { it.path },
                rawName = rawName
            )
        }
        .sortedWith { left, right -> NoCaseOrder.compare(left.name, right.name) }
        .take(24)
    val artists = tracks.filter { it.artist.isNotBlank() && it.artist.contains(query, ignoreCase = true) }
        .groupBy { it.artist }
        .map { (name, group) ->
            LibraryArtist(
                name = name,
                trackCount = group.size,
                albumCount = group.map { it.album }.distinct().size,
                artworkPath = group.minOf { it.path }
            )
        }
        .sortedWith { left, right -> NoCaseOrder.compare(left.name, right.name) }
        .take(24)
    val matched = tracks.filter { track ->
        track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true)
    }.sortedWith { left, right -> NoCaseOrder.compare(left.title, right.title) }.take(100)
    return LibrarySearchResults(query = query, albums = albums, artists = artists, tracks = matched)
}

internal fun libraryAlbumTracks(tracks: List<LibraryTrackEntity>, album: String): List<LibraryTrackEntity> =
    tracks.filter { it.album == album }.sortedWith(
        compareBy(NoCaseOrder, { t: LibraryTrackEntity -> t.displayArtist() })
            .thenBy { it.discNo }
            .thenBy { it.trackNo }
            .thenBy(NoCaseOrder) { it.title }
    )

internal fun libraryArtistTracks(tracks: List<LibraryTrackEntity>, artist: String): List<LibraryTrackEntity> =
    tracks.filter { it.albumArtist == artist || (it.albumArtist.isBlank() && it.artist == artist) }
        .sortedWith(
            compareBy(NoCaseOrder, { t: LibraryTrackEntity -> t.album })
                .thenBy { it.discNo }
                .thenBy { it.trackNo }
                .thenBy(NoCaseOrder) { it.title }
        )

internal fun libraryArtistAlbums(tracks: List<LibraryTrackEntity>, artist: String): List<LibraryAlbum> =
    tracks.filter { it.albumArtist == artist || (it.albumArtist.isBlank() && it.artist == artist) }
        .groupBy { it.album }
        .map { (rawName, group) ->
            LibraryAlbum(
                name = rawName.ifBlank { LibraryContract.UNKNOWN_ALBUM },
                artist = artist,
                trackCount = group.size,
                durationMs = group.sumOf { it.durationMs },
                year = group.maxOf { it.year },
                artworkPath = group.minOf { it.path },
                rawName = rawName
            )
        }
        .sortedWith { left, right -> NoCaseOrder.compare(left.name, right.name) }
