package com.flopster101.siliconplayer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt

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

// Fullscreen keeps the counter inside the title line but sets it smaller
// and dimmer, so it never reads as part of the title itself.
fun badgedTitleWithSubduedCounter(
    leadText: String,
    subtuneBadge: String?,
    titleStyle: TextStyle,
    badgeColor: Color = Color.White.copy(alpha = 0.62f)
): AnnotatedString {
    if (subtuneBadge == null) return AnnotatedString(leadText)
    return buildAnnotatedString {
        append(leadText)
        append(" ")
        withStyle(SpanStyle(color = badgeColor, fontSize = titleStyle.fontSize * 0.8f)) {
            append(subtuneBadge)
        }
    }
}

fun formatTime(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

// Estimated lengths (e.g. the 3:00 module fallback) carry a trailing
// "?" so they never pose as exact.
fun formatDurationWithUnknown(durationSeconds: Double, reliable: Boolean): String {
    val text = formatTime(durationSeconds)
    return if (reliable) text else "$text?"
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
