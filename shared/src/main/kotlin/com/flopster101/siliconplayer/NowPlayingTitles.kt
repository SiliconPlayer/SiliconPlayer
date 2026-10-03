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
