package com.heretek.dorado_hd.data.repo

import android.net.Uri
import androidx.room.withTransaction
import com.heretek.dorado_hd.data.db.AudiobookEntity
import com.heretek.dorado_hd.data.db.AudiobookPartEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.model.Audiobook
import com.heretek.dorado_hd.data.model.AudiobookPart
import com.heretek.dorado_hd.data.model.AudiobookProgress
import com.heretek.dorado_hd.data.scan.AudiobookGrouping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed audiobook store (roadmap D4). The scanner calls [rebuild] after
 * each library pass; books are matched by their stable `groupKey`, so a rescan
 * refreshes titles/parts without losing resume or bookmark positions.
 */
class AudiobookRepository(private val db: DoradoDatabase) {

    fun books(): Flow<List<Audiobook>> =
        db.audiobookDao().books().map { rows -> rows.map { it.toModel() } }

    fun book(id: Long): Flow<Audiobook?> =
        db.audiobookDao().book(id).map { it?.toModel() }

    fun parts(bookId: Long): Flow<List<AudiobookPart>> =
        db.audiobookDao().parts(bookId).map { rows -> rows.map { it.toModel() } }

    suspend fun bookNow(id: Long): Audiobook? = db.audiobookDao().bookNow(id)?.toModel()

    suspend fun partsNow(bookId: Long): List<AudiobookPart> =
        db.audiobookDao().partsNow(bookId).map { it.toModel() }

    suspend fun saveResume(bookId: Long, partIndex: Int, positionMs: Long) {
        db.audiobookDao().setResume(
            bookId,
            AudiobookProgress.encode(AudiobookProgress.Position(partIndex, positionMs)),
            System.currentTimeMillis(),
        )
    }

    suspend fun saveBookmark(bookId: Long, partIndex: Int, positionMs: Long) {
        db.audiobookDao().setBookmark(
            bookId,
            AudiobookProgress.encode(AudiobookProgress.Position(partIndex, positionMs)),
            System.currentTimeMillis(),
        )
    }

    suspend fun clearBookmark(bookId: Long) {
        db.audiobookDao().setBookmark(bookId, AudiobookProgress.NONE, System.currentTimeMillis())
    }

    /**
     * Replaces the audiobook catalog with a fresh scan. Kept books retain their
     * ids, resume and bookmark; vanished books are dropped with their parts.
     */
    suspend fun rebuild(groups: List<AudiobookGrouping.Book>) = db.withTransaction {
        val dao = db.audiobookDao()
        val existing = dao.allBooks().associateBy { it.groupKey }
        val keepKeys = groups.map { it.key }.toSet()
        val staleIds = existing.values.filter { it.groupKey !in keepKeys }.map { it.id }
        if (staleIds.isNotEmpty()) dao.deleteBooks(staleIds)

        // Parts are rebuilt wholesale inside the transaction: a media file that
        // moved between books (its old book still exists) would otherwise trip
        // the unique `mediaId` index.
        dao.deleteAllParts()

        val now = System.currentTimeMillis()
        for (group in groups) {
            val prior = existing[group.key]
            val bookId = if (prior == null) {
                dao.insertBook(
                    AudiobookEntity(
                        groupKey = group.key,
                        title = group.title,
                        author = group.author,
                        albumId = group.albumId,
                        partCount = group.parts.size,
                        totalDurationMs = group.totalDurationMs,
                        resume = AudiobookProgress.NONE,
                        bookmark = AudiobookProgress.NONE,
                        updatedAt = now,
                    ),
                )
            } else {
                dao.updateBook(
                    prior.copy(
                        title = group.title,
                        author = group.author,
                        albumId = group.albumId,
                        partCount = group.parts.size,
                        totalDurationMs = group.totalDurationMs,
                        updatedAt = now,
                    ),
                )
                prior.id
            }
            dao.insertParts(
                group.parts.mapIndexed { index, file ->
                    AudiobookPartEntity(
                        bookId = bookId,
                        mediaId = file.mediaId,
                        position = index,
                        title = file.title,
                        durationMs = file.durationMs,
                        uri = file.uri,
                    )
                },
            )
        }
    }

    private fun AudiobookEntity.toModel() = Audiobook(
        id = id,
        title = title,
        author = author,
        albumId = albumId,
        partCount = partCount,
        totalDurationMs = totalDurationMs,
        resume = AudiobookProgress.decode(resume),
        bookmark = AudiobookProgress.decode(bookmark),
    )

    private fun AudiobookPartEntity.toModel() = AudiobookPart(
        id = id,
        bookId = bookId,
        mediaId = mediaId,
        index = position,
        title = title,
        durationMs = durationMs,
        uri = Uri.parse(uri),
    )
}
