package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.ui.apps.QuizEngine
import com.heretek.dorado_hd.ui.apps.QuizEngine.Difficulty
import com.heretek.dorado_hd.ui.apps.QuizEngine.Kind
import com.heretek.dorado_hd.ui.apps.QuizEngine.Phase
import com.heretek.dorado_hd.ui.apps.QuizEngine.QuizState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class QuizEngineTest {

    private val uri = android.net.Uri.parse("file:///quiz")

    private fun track(
        id: Long,
        title: String = "song $id",
        artist: String = "artist ${id % 6}",
        album: String = "album ${id / 6}",
        albumId: Long = id / 6,
        durationMs: Long = 180_000,
    ) = Track(id, title, artist, 0, album, albumId, "rock", durationMs, 0, 1, "2020", uri)

    /** 6 artists x 6 albums x 6 tracks — every question kind is feasible. */
    private fun library(): List<Track> = (1..36).map { track(it.toLong()) }

    private fun playRound(tracks: List<Track>, difficulty: Difficulty, seed: Long, bonus: Boolean): QuizState {
        var state = QuizEngine.startRound(tracks, difficulty, seed, bonusEnabled = bonus) ?: error("expected round")
        var guard = 0
        while (state.phase != Phase.RESULTS && guard++ < 300) {
            state = when (state.phase) {
                Phase.QUESTION -> QuizEngine.answer(state, state.question!!.answerIndex)
                Phase.REVEAL -> QuizEngine.proceed(state, tracks)
                Phase.BONUS -> {
                    val answered = QuizEngine.answerBonus(state, tracks, state.bonusQuestion!!.answerIndex)
                    QuizEngine.endBonus(answered)
                }
                Phase.RESULTS -> state
            }
        }
        return state
    }

    @Test
    fun `round is twenty questions and pays ten points each`() {
        val tracks = library()
        val done = playRound(tracks, Difficulty.NORMAL, seed = 42L, bonus = true)
        assertEquals(20, done.totalQuestions)
        assertEquals(20, done.questionNumber)
        assertEquals(20, done.correct)
        assertEquals(20 * 10 + 5, done.score)
    }

    @Test
    fun `difficulty selects four or six distinct options`() {
        val tracks = library()
        val normal = QuizEngine.startRound(tracks, Difficulty.NORMAL, seed = 1L)!!
        val hardcore = QuizEngine.startRound(tracks, Difficulty.HARDCORE, seed = 1L)!!
        assertEquals(4, normal.question!!.options.size)
        assertEquals(6, hardcore.question!!.options.size)
        assertEquals(4, normal.optionCount)
        assertEquals(6, hardcore.optionCount)
    }

    @Test
    fun `every question kind offers distinct options containing the answer`() {
        val tracks = library()
        val seen = mutableSetOf<Kind>()
        for (seed in 0L until 300L) {
            val question = QuizEngine.nextQuestion(tracks, Difficulty.NORMAL, rng = Random(seed)) ?: continue
            seen += question.kind
            assertEquals("options must be distinct", question.options.size, question.options.distinct().size)
            assertEquals(question.answer, question.options[question.answerIndex])
            assertTrue(question.answerIndex in question.options.indices)
        }
        assertEquals(Kind.entries.toSet(), seen)
    }

    @Test
    fun `album art never follows album art`() {
        val tracks = library()
        var state = QuizEngine.startRound(tracks, Difficulty.NORMAL, seed = 7L, bonusEnabled = false)!!
        val kinds = mutableListOf<Kind>()
        var guard = 0
        while (state.phase != Phase.RESULTS && guard++ < 300) {
            if (state.phase == Phase.QUESTION) kinds += state.question!!.kind
            state = when (state.phase) {
                Phase.QUESTION -> QuizEngine.answer(state, state.question!!.answerIndex)
                Phase.REVEAL -> QuizEngine.proceed(state, tracks)
                else -> state
            }
        }
        assertTrue("expected questions", kinds.isNotEmpty())
        for (i in 1 until kinds.size) {
            assertFalse(
                "album art at $i must not follow album art",
                kinds[i - 1] == Kind.ALBUM_ART && kinds[i] == Kind.ALBUM_ART,
            )
        }
        // Explicit guard against the chained-question builder.
        for (seed in 0L until 50L) {
            val q = QuizEngine.nextQuestion(tracks, Difficulty.NORMAL, previousWasAlbumArt = true, rng = Random(seed))
            assertTrue(q == null || q.kind != Kind.ALBUM_ART)
        }
    }

    @Test
    fun `points are seconds remaining capped at ten`() {
        assertEquals(10, QuizEngine.pointsFor(15f))
        assertEquals(10, QuizEngine.pointsFor(9.99f))
        assertEquals(4, QuizEngine.pointsFor(3.2f))
        assertEquals(1, QuizEngine.pointsFor(0f))
        val tracks = library()
        var state = QuizEngine.startRound(tracks, Difficulty.NORMAL, seed = 9L)!!
        state = QuizEngine.tick(state, 7f)
        val left = state.secondsLeft
        val answered = QuizEngine.answer(state, state.question!!.answerIndex)
        assertEquals(QuizEngine.pointsFor(left), answered.points)
    }

    @Test
    fun `hint limit is four on normal and two on hardcore`() {
        val tracks = library()
        var normal = QuizEngine.startRound(tracks, Difficulty.NORMAL, seed = 3L)!!
        repeat(4) { normal = QuizEngine.useHint(normal, tracks) }
        assertEquals(0, normal.hintsLeft)
        val normalPast = QuizEngine.useHint(normal, tracks)
        assertEquals(0, normalPast.hintsLeft)
        assertEquals(4, normalPast.hintsUsed)
        assertEquals(15f, normalPast.secondsLeft)

        var hard = QuizEngine.startRound(tracks, Difficulty.HARDCORE, seed = 3L)!!
        repeat(2) { hard = QuizEngine.useHint(hard, tracks) }
        assertEquals(0, hard.hintsLeft)
        assertEquals(0, QuizEngine.useHint(hard, tracks).hintsLeft)
    }

    @Test
    fun `bonus round banks five points per correct hit`() {
        val tracks = library()
        var state = QuizEngine.startRound(tracks, Difficulty.NORMAL, seed = 11L, bonusEnabled = true)!!
        var guard = 0
        while (state.phase != Phase.BONUS && guard++ < 100) {
            state = when (state.phase) {
                Phase.QUESTION -> QuizEngine.answer(state, state.question!!.answerIndex)
                Phase.REVEAL -> QuizEngine.proceed(state, tracks)
                else -> state
            }
        }
        assertEquals(Phase.BONUS, state.phase)
        repeat(3) {
            state = QuizEngine.answerBonus(state, tracks, state.bonusQuestion!!.answerIndex)
        }
        assertEquals(3, state.bonusCorrect)
        val ended = QuizEngine.endBonus(state)
        assertEquals(Phase.RESULTS, ended.phase)
        assertEquals(15, ended.score - state.score)
        assertEquals(15, ended.bonusPoints)
    }

    @Test
    fun `expired content aborts the round at twenty five`() {
        val tracks = library()
        val expired = (1..25).map { i -> track(500L + i, title = "", artist = "", album = "") }
        assertNull(QuizEngine.startRound(tracks + expired, Difficulty.NORMAL, seed = 5L))
        assertNotNull(QuizEngine.startRound(tracks + expired.dropLast(1), Difficulty.NORMAL, seed = 5L))
    }

    @Test
    fun `high scores keep the best per mix and round trip`() {
        val key = QuizEngine.highScoreKey("MegaMix", Difficulty.NORMAL)
        assertEquals("megamix/normal", key)
        var scores: Map<String, Int> = emptyMap()
        scores = QuizEngine.updateHighScore(scores, key, 70)
        scores = QuizEngine.updateHighScore(scores, key, 40)
        scores = QuizEngine.updateHighScore(scores, key, 95)
        assertEquals(95, scores[key])
        val decoded = QuizEngine.decodeHighScores(QuizEngine.encodeHighScores(scores))
        assertEquals(scores, decoded)
    }

    @Test
    fun `tick reveals on timeout with no points`() {
        val tracks = library()
        var state = QuizEngine.startRound(tracks, Difficulty.NORMAL, seed = 2L)!!
        state = QuizEngine.tick(state, 15f)
        assertEquals(Phase.REVEAL, state.phase)
        assertNull(state.chosenIndex)
        assertEquals(0, state.points)
        assertEquals(0, state.score)
    }
}
