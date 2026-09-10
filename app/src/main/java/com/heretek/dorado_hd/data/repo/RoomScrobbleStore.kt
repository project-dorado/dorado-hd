package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.data.db.ScrobbleDao
import com.heretek.dorado_hd.data.db.ScrobbleEntity
import com.heretek.dorado_hd.scrobble.PendingScrobble
import com.heretek.dorado_hd.scrobble.Scrobble
import com.heretek.dorado_hd.scrobble.ScrobbleStore

/** Room-backed offline scrobble queue. */
class RoomScrobbleStore(private val dao: ScrobbleDao) : ScrobbleStore {

    override suspend fun enqueue(scrobble: Scrobble) {
        dao.insert(
            ScrobbleEntity(
                artist = scrobble.artist,
                title = scrobble.title,
                album = scrobble.album,
                durationSeconds = scrobble.durationSeconds,
                timestampSec = scrobble.timestampSec,
            ),
        )
    }

    override suspend fun pending(limit: Int): List<PendingScrobble> =
        dao.pending(limit).map {
            PendingScrobble(
                id = it.id,
                scrobble = Scrobble(it.artist, it.title, it.album, it.durationSeconds, it.timestampSec),
            )
        }

    override suspend fun delete(ids: List<Long>) = dao.delete(ids)

    override suspend fun count(): Int = dao.count()
}
