package com.heretek.dorado_hd.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        RatingEntity::class,
        PinEntity::class,
        HistoryEntity::class,
        ArtistImageEntity::class,
        NoteEntity::class,
        AppointmentEntity::class,
        AlarmEntity::class,
        RadioStationEntity::class,
        PodcastFeedEntity::class,
        PodcastEpisodeEntity::class,
        GameScoreEntity::class,
        TrackFeatureEntity::class,
        ScrobbleEntity::class,
        PlayCountEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class DoradoDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun ratingDao(): RatingDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun pinDao(): PinDao
    abstract fun historyDao(): HistoryDao
    abstract fun artistImageDao(): ArtistImageDao
    abstract fun noteDao(): NoteDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun alarmDao(): AlarmDao
    abstract fun radioDao(): RadioDao
    abstract fun podcastDao(): PodcastDao
    abstract fun gameScoreDao(): GameScoreDao
    abstract fun trackFeatureDao(): TrackFeatureDao
    abstract fun scrobbleDao(): ScrobbleDao
    abstract fun playCountDao(): PlayCountDao

    companion object {
        @Volatile
        private var instance: DoradoDatabase? = null

        fun get(context: Context): DoradoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, DoradoDatabase::class.java, "dorado_hd.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
