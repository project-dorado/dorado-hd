package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.data.db.HistoryEntity
import com.heretek.dorado_hd.data.db.PinEntity
import com.heretek.dorado_hd.data.db.RatingEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.model.PinKind
import com.heretek.dorado_hd.data.model.Rating
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class QuickplayCard(
    val kind: PinKind,
    val refId: Long,
    val label: String,
    val subLabel: String,
    val artAlbumId: Long,
)

class QuickplayRepository(private val db: DoradoDatabase) {
    fun pins(): Flow<List<QuickplayCard>> = db.pinDao().pins().map { list ->
        list.map { it.toCard() }
    }

    fun history(limit: Int = 24): Flow<List<QuickplayCard>> = db.historyDao().recent(limit).map { list ->
        list.map { it.toCard() }
    }

    suspend fun pin(kind: PinKind, refId: Long, label: String, subLabel: String, artAlbumId: Long) {
        db.pinDao().pin(
            PinEntity(
                kind = kind.name,
                refId = refId,
                label = label,
                subLabel = subLabel,
                artAlbumId = artAlbumId,
                pinnedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun unpin(kind: PinKind, refId: Long) = db.pinDao().unpin(kind.name, refId)
    suspend fun isPinned(kind: PinKind, refId: Long) = db.pinDao().isPinned(kind.name, refId) > 0

    suspend fun recordHistory(kind: PinKind, refId: Long, label: String, subLabel: String, artAlbumId: Long) {
        db.historyDao().insert(
            HistoryEntity(
                kind = kind.name,
                refId = refId,
                label = label,
                subLabel = subLabel,
                artAlbumId = artAlbumId,
                playedAt = System.currentTimeMillis(),
            ),
        )
        db.historyDao().trim(keep = 30)
    }

    fun ratingOf(mediaId: Long): Flow<Rating> = db.ratingDao().ratingOf(mediaId).map { Rating.from(it ?: 0) }

    /** Bulk ratings for Smart DJ-style shuffle ordering (canon §4). */
    suspend fun ratings(): Map<Long, Int> = db.ratingDao().all().associate { it.mediaId to it.rating }

    suspend fun setRating(mediaId: Long, rating: Rating) {
        if (rating == Rating.NONE) {
            db.ratingDao().clear(mediaId)
        } else {
            db.ratingDao().set(RatingEntity(mediaId, rating.value))
        }
    }

    private fun PinEntity.toCard() = QuickplayCard(
        kind = PinKind.valueOf(kind),
        refId = refId,
        label = label,
        subLabel = subLabel,
        artAlbumId = artAlbumId,
    )

    private fun HistoryEntity.toCard() = QuickplayCard(
        kind = PinKind.valueOf(kind),
        refId = refId,
        label = label,
        subLabel = subLabel,
        artAlbumId = artAlbumId,
    )
}
