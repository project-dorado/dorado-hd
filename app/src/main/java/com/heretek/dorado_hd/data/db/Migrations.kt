package com.heretek.dorado_hd.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room v1 → v2: preserves all existing data (pins, ratings, playlists, history)
 * and adds tables for the mini-app platform (notes, appointments, alarms,
 * radio_stations, podcast_feeds, podcast_episodes, game_scores) — canon §8.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `notes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `modifiedAt` INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_modifiedAt` ON `notes` (`modifiedAt`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `appointments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `notes` TEXT,
                `startAt` INTEGER NOT NULL,
                `endAt` INTEGER
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_startAt` ON `appointments` (`startAt`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `alarms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `label` TEXT NOT NULL,
                `alarmKind` INTEGER NOT NULL,
                `refId` INTEGER NOT NULL,
                `daysOfWeek` INTEGER NOT NULL
            )""",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `radio_stations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `frequencyKhz` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `isPreset` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER NOT NULL
            )""",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `podcast_feeds` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `feedUrl` TEXT NOT NULL,
                `artworkUrl` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `subscribedAt` INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_podcast_feeds_feedUrl` ON `podcast_feeds` (`feedUrl`)",
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `podcast_episodes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `feedId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `pubAt` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `enclosureUrl` TEXT NOT NULL,
                `played` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_podcast_episodes_feedId` ON `podcast_episodes` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_podcast_episodes_pubAt` ON `podcast_episodes` (`pubAt`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `game_scores` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `game` TEXT NOT NULL,
                `score` INTEGER NOT NULL,
                `meta` TEXT,
                `playedAt` INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_scores_game` ON `game_scores` (`game`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_scores_playedAt` ON `game_scores` (`playedAt`)")
    }
}

/**
 * Room v2 → v3: M9.3 cached audio-feature vectors (on-device similarity and
 * dynamic mixes). Existing data is untouched; analyzed tracks backfill lazily.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `track_features` (
                `mediaId` INTEGER PRIMARY KEY NOT NULL,
                `bpm` REAL NOT NULL,
                `energy` REAL NOT NULL,
                `valence` REAL NOT NULL,
                `acousticness` REAL NOT NULL,
                `danceability` REAL NOT NULL,
                `spectralCentroid` REAL NOT NULL,
                `analyzedAt` INTEGER NOT NULL
            )""",
        )
    }
}

/**
 * Room v3 → v4: M9.4 offline Last.fm scrobble queue. Existing data untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `scrobble_queue` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `artist` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `album` TEXT NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `timestampSec` INTEGER NOT NULL
            )""",
        )
    }
}

/**
 * Room v4 → v5: M9.2b persisted per-track play counts. Existing data untouched;
 * counts start at zero and accrue from the next playback.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `play_counts` (
                `mediaId` INTEGER PRIMARY KEY NOT NULL,
                `count` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER NOT NULL
            )""",
        )
    }
}

/**
 * Room v5 → v6: official-app reimplementation program. A per-app opaque state
 * blob (`app_state`) backs save/resume, board snapshots and app settings.
 * Existing data untouched.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `app_state` (
                `app` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`app`)
            )""",
        )
    }
}

/**
 * Room v6 → v7: audiobooks (roadmap D4). `audiobooks` groups ordered parts
 * (`audiobook_parts`) by the scanner's stable `groupKey`; the resume/bookmark
 * positions persist there as `AudiobookProgress` codecs. Existing data
 * untouched. The SQL mirrors Room's expected v7 schema exactly.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `audiobooks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `groupKey` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT NOT NULL,
                `albumId` INTEGER NOT NULL,
                `partCount` INTEGER NOT NULL,
                `totalDurationMs` INTEGER NOT NULL,
                `resume` TEXT NOT NULL,
                `bookmark` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audiobooks_groupKey` ON `audiobooks` (`groupKey`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `audiobook_parts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bookId` INTEGER NOT NULL,
                `mediaId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `uri` TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audiobook_parts_bookId` ON `audiobook_parts` (`bookId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audiobook_parts_mediaId` ON `audiobook_parts` (`mediaId`)")
    }
}

/**
 * Room v7 → v8: local-first Zune inbox cache (roadmap D2). Reads come from the
 * `inbox_messages` table; a cloud sync refreshes the rows while `isRead` stays
 * on-device. Existing data untouched. The SQL mirrors Room's expected v8
 * schema exactly so `RoomOpenHelper` validation passes.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `inbox_messages` (
                `id` TEXT NOT NULL,
                `senderAccountId` TEXT,
                `senderTag` TEXT NOT NULL,
                `recipientTag` TEXT NOT NULL,
                `subject` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `isRead` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_inbox_messages_recipientTag` ON `inbox_messages` (`recipientTag`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_inbox_messages_createdAt` ON `inbox_messages` (`createdAt`)",
        )
    }
}
