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
