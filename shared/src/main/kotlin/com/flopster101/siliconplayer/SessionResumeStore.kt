package com.flopster101.siliconplayer

import org.json.JSONObject
import java.io.File

// Last-played session prefill, kept as its own domain file so scalar prefs
// stay out of it. Restored paused at the saved position, never auto-played;
// mirrors Android's SESSION_RESUME_* checkpoint plus active-playlist context,
// except the playlist is referenced by id (looked up in the library file)
// instead of embedding a JSON snapshot.
internal data class SessionResumeSnapshot(
    val sourceId: String,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val playlistId: String?,
    val entryId: String?,
    val shuffleActive: Boolean
)

internal const val SESSION_RESUME_FILE_NAME = "session_resume.json"

// Matches Android's resume epsilon: looping cores can report elapsed time
// slightly past the declared duration.
internal const val SESSION_RESUME_POSITION_EPSILON_SECONDS = 0.05

internal fun sessionResumeFile(configDir: File): File =
    File(configDir, SESSION_RESUME_FILE_NAME)

// A null snapshot deletes the file: an explicit stop leaves nothing to
// resume, mirroring Android clearing its session path + checkpoint on stop.
internal fun writeSessionResumeSnapshot(configDir: File, snapshot: SessionResumeSnapshot?) {
    val file = sessionResumeFile(configDir)
    if (snapshot == null) {
        deleteStoreFile(file)
        return
    }
    val json = JSONObject()
        .put("sourceId", snapshot.sourceId)
        .put("positionSeconds", snapshot.positionSeconds)
        .put("durationSeconds", snapshot.durationSeconds)
    if (snapshot.playlistId != null) json.put("playlistId", snapshot.playlistId)
    if (snapshot.entryId != null) json.put("entryId", snapshot.entryId)
    if (snapshot.shuffleActive) json.put("shuffleActive", true)
    writeTextAtomic(file, json.toString())
}

internal fun SessionResumeSnapshot.hasValidPosition(): Boolean =
    durationSeconds > 0.0 &&
        positionSeconds >= 0.0 &&
        positionSeconds <= durationSeconds + SESSION_RESUME_POSITION_EPSILON_SECONDS

internal fun readSessionResumeSnapshot(configDir: File): SessionResumeSnapshot? {
    val raw = firstParsableJson(readCandidateTexts(sessionResumeFile(configDir)), isObject = true)
        ?: return null
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val sourceId = json.optString("sourceId").trim().takeUnless { it.isBlank() } ?: return null
    return SessionResumeSnapshot(
        sourceId = sourceId,
        positionSeconds = json.optDouble("positionSeconds", 0.0),
        durationSeconds = json.optDouble("durationSeconds", 0.0),
        playlistId = json.optString("playlistId", null)?.trim()?.takeUnless { it.isBlank() },
        entryId = json.optString("entryId", null)?.trim()?.takeUnless { it.isBlank() },
        shuffleActive = json.optBoolean("shuffleActive", false)
    )
}
