package com.heretek.dorado_hd

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.model.AudiobookProgress
import com.heretek.dorado_hd.data.repo.AudiobookRepository
import com.heretek.dorado_hd.data.scan.AudiobookGrouping
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Room round-trip for [AudiobookRepository] (mirrors AudioFeatureStoreTest). */
@RunWith(RobolectricTestRunner::class)
class AudiobookStoreTest {

    private lateinit var db: DoradoDatabase
    private lateinit var repo: AudiobookRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AudiobookRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(
        key: String,
        title: String,
        author: String = "Jane Author",
        mediaIds: List<Long> = listOf(1L, 2L, 3L),
    ) = AudiobookGrouping.Book(
        key = key,
        title = title,
        author = author,
        albumId = 7L,
        source = AudiobookGrouping.Source.DIRECTORY,
        parts = mediaIds.mapIndexed { index, mediaId ->
            AudiobookGrouping.File(
                mediaId = mediaId,
                title = "Part ${index + 1}",
                artist = author,
                album = title,
                albumId = 7L,
                genre = "unknown",
                durationMs = 60_000L,
                dateAdded = 0L,
                path = "/Books/$title/${index + 1}.mp3",
                uri = "content://media/external/audio/media/$mediaId",
            )
        },
    )

    @Test
    fun `rebuild persists books and ordered parts`() = runBlocking {
        repo.rebuild(listOf(book("dir:/a", "Alpha", mediaIds = listOf(11, 12, 13))))

        val books = repo.books().first()
        assertEquals(1, books.size)
        assertEquals("Alpha", books.single().title)
        assertEquals("Jane Author", books.single().author)
        assertEquals(3, books.single().partCount)
        assertEquals(180_000L, books.single().totalDurationMs)

        val parts = repo.parts(books.single().id).first()
        assertEquals(listOf("Part 1", "Part 2", "Part 3"), parts.map { it.title })
        assertEquals(listOf(0, 1, 2), parts.map { it.index })
        assertEquals(listOf(11L, 12L, 13L), parts.map { it.mediaId })
        assertEquals(Uri.parse("content://media/external/audio/media/11"), parts.first().uri)
    }

    @Test
    fun `resume and bookmark round trip through the codec`() = runBlocking {
        repo.rebuild(listOf(book("dir:/a", "Alpha")))
        val id = repo.books().first().single().id

        repo.saveResume(id, 2, 12_000L)
        repo.saveBookmark(id, 1, 500L)

        val stored = repo.book(id).first()!!
        assertEquals(AudiobookProgress.Position(2, 12_000L), stored.resume)
        assertEquals(AudiobookProgress.Position(1, 500L), stored.bookmark)
        assertEquals("part 3 of 3", stored.progressLabel)

        repo.clearBookmark(id)
        assertNull(repo.book(id).first()!!.bookmark)
        assertEquals(AudiobookProgress.Position(2, 12_000L), repo.book(id).first()!!.resume)
    }

    @Test
    fun `rescan preserves resume and replaces parts for the same group key`() = runBlocking {
        repo.rebuild(listOf(book("dir:/a", "Alpha", mediaIds = listOf(1, 2, 3))))
        val alphaBefore = repo.books().first().single()
        repo.saveResume(alphaBefore.id, 1, 9_000L)

        repo.rebuild(
            listOf(
                book("dir:/a", "Alpha Revised", mediaIds = listOf(21, 22)),
                book("dir:/b", "Beta", mediaIds = listOf(31)),
            ),
        )

        val books = repo.books().first()
        assertEquals(listOf("Alpha Revised", "Beta"), books.map { it.title })
        val alpha = books.first { it.title == "Alpha Revised" }
        assertEquals(alphaBefore.id, alpha.id)
        assertEquals(2, alpha.partCount)
        assertEquals(AudiobookProgress.Position(1, 9_000L), alpha.resume)
        assertEquals(listOf(21L, 22L), repo.parts(alpha.id).first().map { it.mediaId })
        assertNull(alpha.bookmark)
    }

    @Test
    fun `rebuild drops vanished books and their parts`() = runBlocking {
        repo.rebuild(
            listOf(
                book("dir:/a", "Alpha", mediaIds = listOf(1, 2)),
                book("dir:/b", "Beta", mediaIds = listOf(3, 4)),
            ),
        )
        val betaId = repo.books().first().first { it.title == "Beta" }.id

        repo.rebuild(listOf(book("dir:/a", "Alpha", mediaIds = listOf(1, 2))))

        val books = repo.books().first()
        assertEquals(listOf("Alpha"), books.map { it.title })
        assertTrue(repo.parts(betaId).first().isEmpty())
        assertNull(repo.bookNow(betaId))
    }
}
