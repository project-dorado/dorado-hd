package com.heretek.dorado_hd.data.model

import android.net.Uri

/**
 * One ordered part (file) of an audiobook. Snapshots the metadata needed to
 * play the part so the audiobook tables survive library rescans independent of
 * the music `tracks` table.
 */
data class AudiobookPart(
    val id: Long,
    val bookId: Long,
    val mediaId: Long,
    val index: Int,
    val title: String,
    val durationMs: Long,
    val uri: Uri,
) {
    /** Maps the part onto the shared queue/transport model. */
    fun toTrack(book: Audiobook): Track = Track(
        mediaId = mediaId,
        title = title,
        artist = book.author,
        artistId = 0,
        album = book.title,
        albumId = book.albumId,
        genre = AUDIOBOOK_GENRE,
        durationMs = durationMs,
        dateAdded = 0,
        trackNumber = index + 1,
        year = "",
        uri = uri,
    )

    companion object {
        /** Genre stamped on audiobook queue items (see PlaybackSource.AUDIOBOOK). */
        const val AUDIOBOOK_GENRE = "audiobook"
    }
}

/** A book: ordered parts plus the persisted resume/bookmark positions. */
data class Audiobook(
    val id: Long,
    val title: String,
    val author: String,
    val albumId: Long,
    val partCount: Int,
    val totalDurationMs: Long,
    val resume: AudiobookProgress.Position?,
    val bookmark: AudiobookProgress.Position?,
) {
    /** "not started", "part 3 of 12" — the book-list progress line. */
    val progressLabel: String
        get() = resume?.let { "part ${it.partIndex + 1} of $partCount" } ?: "not started"
}

/**
 * Pure codec for the resume / bookmark positions persisted on `audiobooks`.
 * A position is "part index + millisecond offset"; the stored form is
 * `"<partIndex>:<positionMs>"` and [NONE] means "no saved position".
 */
object AudiobookProgress {
    data class Position(val partIndex: Int, val positionMs: Long)

    const val NONE = "-1:0"

    fun encode(position: Position?): String {
        if (position == null || position.partIndex < 0) return NONE
        return "${position.partIndex}:${position.positionMs.coerceAtLeast(0L)}"
    }

    fun decode(raw: String?): Position? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == NONE) return null
        val pieces = text.split(':', limit = 2)
        if (pieces.size != 2) return null
        val partIndex = pieces[0].toIntOrNull() ?: return null
        val positionMs = pieces[1].toLongOrNull() ?: return null
        if (partIndex < 0 || positionMs < 0L) return null
        return Position(partIndex, positionMs)
    }

    /** Time left in the current part, never negative. */
    fun remainingMs(positionMs: Long, durationMs: Long): Long =
        (durationMs - positionMs).coerceAtLeast(0L)

    /** "12:34" / "1:02:03" — the compact player's time-remaining label. */
    fun format(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
