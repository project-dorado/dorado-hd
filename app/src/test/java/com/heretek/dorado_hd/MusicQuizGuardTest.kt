package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.ui.apps.QuizEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Music-quiz guard: a mix that cannot field a round returns null (the UI now
 * surfaces the reason instead of silently doing nothing).
 */
@RunWith(RobolectricTestRunner::class)
class MusicQuizGuardTest {

    private val uri = android.net.Uri.parse("file:///quiz-guard")

    private fun track(id: Long, artist: String) = Track(
        mediaId = id,
        title = "song $id",
        artist = artist,
        artistId = 0,
        album = "album $id",
        albumId = id,
        genre = "rock",
        durationMs = 180_000,
        dateAdded = 0,
        trackNumber = 1,
        year = "2020",
        uri = uri,
    )

    @Test
    fun `an empty mix cannot start a round`() {
        assertNull(QuizEngine.startRound(emptyList()))
    }

    @Test
    fun `a mix below the option count cannot start a normal round`() {
        val pool = listOf(track(1, "a"), track(2, "b"), track(3, "c"))
        assertEquals(3, pool.count { QuizEngine.isContentValid(it) })
        assertNull(QuizEngine.startRound(pool, QuizEngine.Difficulty.NORMAL))
        // Hardcore needs six options, so four valid tracks still fail.
        assertNull(QuizEngine.startRound(pool + track(4, "d"), QuizEngine.Difficulty.HARDCORE))
    }

    @Test
    fun `a sufficient mix still starts`() {
        val pool = (1..6).map { track(it.toLong(), "artist $it") }
        assertNotNull(QuizEngine.startRound(pool, QuizEngine.Difficulty.NORMAL, seed = 4L))
    }
}
