package com.flopster101.siliconplayer

data class ResolvedNowPlayingText(
    val title: String,
    val album: String
)

// When a file holds several subtunes, the file-level title describes the
// collection, so it moves to the album line and the current subtune name
// becomes the track title. Decoders without per-subtune names either return
// blank or echo the file title (AdPlug, cRSID); both keep today's display.
// Badge shown next to the track title while a multi-subtune file plays.
fun subtuneBadgeText(currentSubtuneIndex: Int, subtuneCount: Int): String? {
    if (subtuneCount <= 1) return null
    val shownIndex = (currentSubtuneIndex + 1).coerceIn(1, subtuneCount)
    return "[$shownIndex/$subtuneCount]"
}

// Second-line text under a track title: artist and album combined,
// blank when neither exists so the line is hidden entirely.
fun formatArtistAlbumLine(artist: String, album: String): String {
    return when {
        album.isNotBlank() && artist.isNotBlank() -> "$artist • $album"
        album.isNotBlank() -> album
        else -> artist
    }
}

fun formatBadgedTitle(title: String, subtuneBadge: String?): String {
    return if (subtuneBadge != null) "$title $subtuneBadge" else title
}

fun resolveSubtuneNowPlayingText(
    fileTitle: String,
    fileAlbum: String,
    subtuneCount: Int,
    subtuneTitle: String
): ResolvedNowPlayingText {
    if (subtuneCount > 1) {
        val trimmedSubtuneTitle = subtuneTitle.trim()
        val trimmedFileTitle = fileTitle.trim()
        if (trimmedSubtuneTitle.isNotEmpty() && trimmedSubtuneTitle != trimmedFileTitle) {
            return ResolvedNowPlayingText(
                title = trimmedSubtuneTitle,
                album = trimmedFileTitle.ifEmpty { fileAlbum }
            )
        }
    }
    return ResolvedNowPlayingText(title = fileTitle, album = fileAlbum)
}
