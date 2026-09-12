package com.heretek.dorado_hd

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.data.db.AudiobookEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.db.MIGRATION_6_7
import com.heretek.dorado_hd.data.model.AudiobookProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the v6→v7 audiobook migration (roadmap D4):
 *  1. the SQL emitted by [MIGRATION_6_7] is byte-for-byte (modulo whitespace)
 *     what Room generated for the two new entities, and
 *  2. a real v6 database upgrades and serves audiobook queries.
 */
@RunWith(RobolectricTestRunner::class)
class AudiobookMigrationTest {

    @Test
    fun `migration mirrors the generated room schema`() {
        val generated = Regex("""execSQL\("((?:[^"\\]|\\.)*)"\)""")
            .findAll(generatedImpl().readText())
            .map { it.groupValues[1] }
            .filter { it.startsWith("CREATE") }
            .filter { "`audiobooks`" in it || "`audiobook_parts`" in it }
            .map(::normalize)
            .toSet()
        assertEquals("expected five generated statements", 5, generated.size)

        val recorded = mutableListOf<String>()
        val recorder = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1 && args[0] is String) {
                recorded += args[0] as String
            }
            null
        } as SupportSQLiteDatabase
        MIGRATION_6_7.migrate(recorder)

        assertEquals(generated, recorded.map(::normalize).toSet())
    }

    @Test
    fun `a v6 database upgrades and serves audiobook queries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "audiobook-migration-test.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { raw ->
            V6_SCHEMA.forEach { raw.execSQL(it) }
            raw.version = 6
        }

        val db = Room.databaseBuilder(context, DoradoDatabase::class.java, name)
            .addMigrations(MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val id = db.audiobookDao().insertBook(
                    AudiobookEntity(
                        groupKey = "dir:/Books/Alpha",
                        title = "Alpha",
                        author = "Jane Author",
                        albumId = 7L,
                        partCount = 1,
                        totalDurationMs = 60_000L,
                        resume = AudiobookProgress.NONE,
                        bookmark = AudiobookProgress.NONE,
                        updatedAt = 1L,
                    ),
                )
                assertNotNull(id)
                assertEquals(1, db.audiobookDao().books().first().size)
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun generatedImpl(): File {
        val candidates = listOf(
            File("build/generated/ksp/debug/kotlin/com/heretek/dorado_hd/data/db/DoradoDatabase_Impl.kt"),
            File("app/build/generated/ksp/debug/kotlin/com/heretek/dorado_hd/data/db/DoradoDatabase_Impl.kt"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("generated DoradoDatabase_Impl.kt not found; run compileDebugKotlin first")
    }

    private fun normalize(sql: String): String =
        sql.replace(Regex("\\s+"), " ").replace("( ", "(").replace(" )", ")").trim()

    private companion object {
        /**
         * The full v6 schema, copied from the v6 Room-generated `createAllTables`
         * (the v7 migration only adds the two audiobook tables).
         */
        val V6_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `tracks` (`mediaId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `artistId` INTEGER NOT NULL, `album` TEXT NOT NULL, `albumId` INTEGER NOT NULL, `genre` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `trackNumber` INTEGER NOT NULL, `year` TEXT NOT NULL, `uri` TEXT NOT NULL, PRIMARY KEY(`mediaId`))",
            "CREATE INDEX IF NOT EXISTS `index_tracks_albumId` ON `tracks` (`albumId`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_artistId` ON `tracks` (`artistId`)",
            "CREATE INDEX IF NOT EXISTS `index_tracks_title` ON `tracks` (`title`)",
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dateCreated` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlistId` INTEGER NOT NULL, `mediaId` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaId`))",
            "CREATE TABLE IF NOT EXISTS `ratings` (`mediaId` INTEGER NOT NULL, `rating` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
            "CREATE TABLE IF NOT EXISTS `pins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `refId` INTEGER NOT NULL, `label` TEXT NOT NULL, `subLabel` TEXT NOT NULL, `artAlbumId` INTEGER NOT NULL, `pinnedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `refId` INTEGER NOT NULL, `label` TEXT NOT NULL, `subLabel` TEXT NOT NULL, `artAlbumId` INTEGER NOT NULL, `playedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `artist_images` (`artistKey` TEXT NOT NULL, `imagePath` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`artistKey`))",
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `modifiedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_notes_modifiedAt` ON `notes` (`modifiedAt`)",
            "CREATE TABLE IF NOT EXISTS `appointments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT, `startAt` INTEGER NOT NULL, `endAt` INTEGER)",
            "CREATE INDEX IF NOT EXISTS `index_appointments_startAt` ON `appointments` (`startAt`)",
            "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `label` TEXT NOT NULL, `alarmKind` INTEGER NOT NULL, `refId` INTEGER NOT NULL, `daysOfWeek` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `radio_stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `frequencyKhz` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `isPreset` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `podcast_feeds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `description` TEXT NOT NULL, `subscribedAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_podcast_feeds_feedUrl` ON `podcast_feeds` (`feedUrl`)",
            "CREATE TABLE IF NOT EXISTS `podcast_episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedId` INTEGER NOT NULL, `title` TEXT NOT NULL, `pubAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `enclosureUrl` TEXT NOT NULL, `played` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_podcast_episodes_feedId` ON `podcast_episodes` (`feedId`)",
            "CREATE INDEX IF NOT EXISTS `index_podcast_episodes_pubAt` ON `podcast_episodes` (`pubAt`)",
            "CREATE TABLE IF NOT EXISTS `game_scores` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `game` TEXT NOT NULL, `score` INTEGER NOT NULL, `meta` TEXT, `playedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_game_scores_game` ON `game_scores` (`game`)",
            "CREATE INDEX IF NOT EXISTS `index_game_scores_playedAt` ON `game_scores` (`playedAt`)",
            "CREATE TABLE IF NOT EXISTS `track_features` (`mediaId` INTEGER NOT NULL, `bpm` REAL NOT NULL, `energy` REAL NOT NULL, `valence` REAL NOT NULL, `acousticness` REAL NOT NULL, `danceability` REAL NOT NULL, `spectralCentroid` REAL NOT NULL, `analyzedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
            "CREATE TABLE IF NOT EXISTS `scrobble_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `artist` TEXT NOT NULL, `title` TEXT NOT NULL, `album` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `timestampSec` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `play_counts` (`mediaId` INTEGER NOT NULL, `count` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
            "CREATE TABLE IF NOT EXISTS `app_state` (`app` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`app`))",
        )
    }
}
