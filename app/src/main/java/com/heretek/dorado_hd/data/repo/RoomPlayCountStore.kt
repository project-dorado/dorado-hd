package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.analysis.PlayCountStore
import com.heretek.dorado_hd.data.db.PlayCountDao

/** Room-backed play-count persistence (M9.2b). */
class RoomPlayCountStore(private val dao: PlayCountDao) : PlayCountStore {

    override suspend fun increment(mediaId: Long, nowMillis: Long) = dao.increment(mediaId, nowMillis)

    override suspend fun counts(): Map<Long, Int> =
        dao.all().associate { it.mediaId to it.count }
}
