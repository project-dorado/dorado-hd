package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.data.db.AlarmEntity
import com.heretek.dorado_hd.data.db.AppointmentEntity
import com.heretek.dorado_hd.data.db.GameScoreEntity
import com.heretek.dorado_hd.data.db.NoteEntity
import com.heretek.dorado_hd.data.db.PodcastEpisodeEntity
import com.heretek.dorado_hd.data.db.PodcastEpisodeFlat
import com.heretek.dorado_hd.data.db.PodcastFeedEntity
import com.heretek.dorado_hd.data.db.RadioStationEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesRepository(private val db: DoradoDatabase) {
    fun notes(): Flow<List<NoteEntity>> = db.noteDao().all()
    suspend fun add(title: String, body: String): Long =
        db.noteDao().insert(NoteEntity(title = title, body = body, modifiedAt = System.currentTimeMillis()))
    suspend fun update(id: Long, title: String, body: String) {
        db.noteDao().insert(NoteEntity(id = id, title = title, body = body, modifiedAt = System.currentTimeMillis()))
    }
    suspend fun delete(id: Long) = db.noteDao().delete(id)
}

class CalendarRepository(private val db: DoradoDatabase) {
    fun appointments(): Flow<List<AppointmentEntity>> = db.appointmentDao().all()
    suspend fun inRange(from: Long, to: Long): List<AppointmentEntity> = db.appointmentDao().inRange(from, to)
    suspend fun add(title: String, notes: String?, startAt: Long, endAt: Long?): Long =
        db.appointmentDao().insert(AppointmentEntity(title = title, notes = notes, startAt = startAt, endAt = endAt))
    suspend fun delete(id: Long) = db.appointmentDao().delete(id)
}

class AlarmRepository(private val db: DoradoDatabase) {
    fun alarms(): Flow<List<AlarmEntity>> = db.alarmDao().all()
    suspend fun byId(id: Long): AlarmEntity? = db.alarmDao().byId(id)
    suspend fun add(alarm: AlarmEntity): Long = db.alarmDao().insert(alarm)
    suspend fun setEnabled(id: Long, enabled: Boolean) = db.alarmDao().setEnabled(id, enabled)
    suspend fun setTime(id: Long, hour: Int, minute: Int) = db.alarmDao().setTime(id, hour, minute)
    suspend fun delete(id: Long) = db.alarmDao().delete(id)
}

class RadioRepository(private val db: DoradoDatabase) {
    fun stations(): Flow<List<RadioStationEntity>> = db.radioDao().all()
    suspend fun add(station: RadioStationEntity): Long = db.radioDao().insert(station)
    suspend fun delete(id: Long) = db.radioDao().delete(id)
    suspend fun touch(id: Long) = db.radioDao().touch(id, System.currentTimeMillis())

    suspend fun seedDefaultsIfEmpty() {
        if (db.radioDao().count() > 0) return
        for (s in DEFAULTS) {
            db.radioDao().insert(RadioStationEntity(name = s.first, frequencyKhz = s.second, streamUrl = s.third, isPreset = true, lastPlayedAt = 0))
        }
    }

    companion object {
        /** A handful of community-stream presets to make the dial usable out of the box. */
        val DEFAULTS: List<Triple<String, Int, String>> = listOf(
            Triple("KEXP", 90700, "https://kexp-mp3-128.streamguys1.com/kexp128.mp3"),
            Triple("WFMU", 91100, "https://stream0.wfmu.org/freeform-128k.mp3"),
            Triple("SomaFM Groove Salad", 0, "https://ice1.somafm.com/groovesalad-128-mp3"),
            Triple("SomaFM Indie Pop", 0, "https://ice1.somafm.com/indiepop-128-mp3"),
            Triple("SomaFM DEF CON Radio", 0, "https://ice1.somafm.com/defcon-128-mp3"),
        )
    }
}

class PodcastRepository(private val db: DoradoDatabase) {
    fun feeds(): Flow<List<PodcastFeedEntity>> = db.podcastDao().feeds()
    suspend fun feed(id: Long): PodcastFeedEntity? = db.podcastDao().feed(id)
    suspend fun addFeed(f: PodcastFeedEntity): Long = db.podcastDao().insertFeed(f)
    suspend fun deleteFeed(id: Long) = db.podcastDao().deleteFeed(id)
    fun episodes(feedId: Long): Flow<List<PodcastEpisodeEntity>> = db.podcastDao().episodesFor(feedId)
    fun episodesFlat(): Flow<List<PodcastEpisodeFlat>> = db.podcastDao().episodesFlat()
    suspend fun episode(id: Long): PodcastEpisodeEntity? = db.podcastDao().episode(id)
    suspend fun addEpisodes(es: List<PodcastEpisodeEntity>) = db.podcastDao().insertEpisodes(es)
    suspend fun setPosition(id: Long, pos: Long) = db.podcastDao().setPosition(id, pos)
    suspend fun markPlayed(id: Long) = db.podcastDao().markPlayed(id)
}

class GameRepository(private val db: DoradoDatabase) {
    fun top(game: String, limit: Int = 10): Flow<List<GameScoreEntity>> = db.gameScoreDao().top(game, limit)
    suspend fun record(game: String, score: Int, meta: String?): Long =
        db.gameScoreDao().insert(
            GameScoreEntity(
                game = game,
                score = score,
                meta = meta,
                playedAt = System.currentTimeMillis(),
            ),
        )
}
