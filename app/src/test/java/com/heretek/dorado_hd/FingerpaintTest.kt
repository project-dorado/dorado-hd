package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.FP_ALPHA_PRESETS
import com.heretek.dorado_hd.ui.apps.games.FP_BRUSH_WIDTHS
import com.heretek.dorado_hd.ui.apps.games.FP_PALETTE_SIZE
import com.heretek.dorado_hd.ui.apps.games.FP_ROUND_MS
import com.heretek.dorado_hd.ui.apps.games.FP_TEXT_SIZE_MAX
import com.heretek.dorado_hd.ui.apps.games.FP_TEXT_SIZE_MIN
import com.heretek.dorado_hd.ui.apps.games.FingerpaintBrush
import com.heretek.dorado_hd.ui.apps.games.FingerpaintEngine
import com.heretek.dorado_hd.ui.apps.games.FingerpaintPhase
import com.heretek.dorado_hd.ui.apps.games.FingerpaintPoint
import com.heretek.dorado_hd.ui.apps.games.FingerpaintStroke
import com.heretek.dorado_hd.ui.apps.games.FingerpaintTool
import com.heretek.dorado_hd.ui.apps.games.FingerpaintWordBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerpaintTest {

    private fun strokeAt(y: Float, color: Int = 0): FingerpaintStroke {
        var stroke = FingerpaintEngine.beginStroke(0, FingerpaintBrush().withColor(color), 0f, y)
        stroke = FingerpaintEngine.extendStroke(stroke, 12f, y)
        stroke = FingerpaintEngine.extendStroke(stroke, 24f, y)
        return stroke
    }

    @Test
    fun `stroke add undo redo and clear`() {
        var document = FingerpaintEngine.document()
        document = FingerpaintEngine.addStroke(document, strokeAt(10f))
        assertEquals(1, document.strokes.size)
        assertTrue(FingerpaintEngine.canUndo(document))
        assertFalse(FingerpaintEngine.canRedo(document))

        val undone = FingerpaintEngine.undo(document)
        assertTrue(undone.strokes.isEmpty())
        assertTrue(FingerpaintEngine.canRedo(undone))
        assertFalse(FingerpaintEngine.canUndo(undone))

        val redone = FingerpaintEngine.redo(undone)
        assertEquals(1, redone.strokes.size)
        assertEquals(document.strokes, redone.strokes)

        val cleared = FingerpaintEngine.clear(redone)
        assertTrue(cleared.strokes.isEmpty())
        val restored = FingerpaintEngine.undo(cleared)
        assertEquals(1, restored.strokes.size)
    }

    @Test
    fun `simplification keeps endpoints and corners`() {
        val straight = (0..10).map { FingerpaintPoint(it.toFloat(), 0f) }
        val simplified = FingerpaintEngine.simplify(straight, 1f)
        assertEquals(listOf(straight.first(), straight.last()), simplified)

        val corner = listOf(
            FingerpaintPoint(0f, 0f),
            FingerpaintPoint(5f, 10f),
            FingerpaintPoint(10f, 0f),
        )
        assertEquals(3, FingerpaintEngine.simplify(corner, 1f).size)
    }

    @Test
    fun `fast strokes thin and tiny jitter is ignored`() {
        assertTrue(FingerpaintEngine.pressureFor(1f) > FingerpaintEngine.pressureFor(30f))
        assertEquals(0.35f, FingerpaintEngine.pressureFor(300f), 0.001f)

        val stroke = FingerpaintEngine.beginStroke(0, FingerpaintBrush(), 5f, 5f)
        assertEquals(1, FingerpaintEngine.extendStroke(stroke, 5.1f, 5f).points.size)
        assertEquals(2, FingerpaintEngine.extendStroke(stroke, 25f, 5f).points.size)
    }

    @Test
    fun `palette and brush state clamp to their ladders`() {
        val brush = FingerpaintBrush()
        assertEquals(0, brush.withColor(-4).colorIndex)
        assertEquals(FP_PALETTE_SIZE - 1, brush.withColor(99).colorIndex)
        assertEquals(FP_BRUSH_WIDTHS.first(), brush.withWidth(-1).width)
        assertEquals(FP_BRUSH_WIDTHS.last(), brush.withWidth(99).width)
        assertEquals(FP_ALPHA_PRESETS.first(), brush.withAlpha(-2).alpha)
        assertEquals(FP_ALPHA_PRESETS.last(), brush.withAlpha(99).alpha)
        assertEquals(FP_TEXT_SIZE_MIN, brush.withTextSize(-10).textSize)
        assertEquals(FP_TEXT_SIZE_MAX, brush.withTextSize(9999).textSize)
        assertEquals(FingerpaintTool.ERASER, brush.withTool(FingerpaintTool.ERASER).tool)
    }

    @Test
    fun `eraser removes touched strokes and stays undoable`() {
        var document = FingerpaintEngine.document()
        document = FingerpaintEngine.addStroke(document, strokeAt(0f))
        document = FingerpaintEngine.addStroke(document, strokeAt(200f))

        val eraser = FingerpaintStroke(
            id = 0,
            colorIndex = 0,
            width = 12f,
            alpha = 100,
            tool = FingerpaintTool.ERASER,
            points = listOf(FingerpaintPoint(20f, 0f)),
        )
        val erased = FingerpaintEngine.addStroke(document, eraser)
        assertEquals(1, erased.strokes.size)
        assertEquals(200f, erased.strokes[0].points[0].y, 0.01f)

        val undone = FingerpaintEngine.undo(erased)
        assertEquals(2, undone.strokes.size)
    }

    @Test
    fun `word bank keeps five non-empty categories and picks deterministically`() {
        assertEquals(5, FingerpaintWordBank.categories.size)
        FingerpaintWordBank.categories.forEachIndexed { index, _ ->
            assertTrue(FingerpaintWordBank.count(index) > 0)
        }
        val picked = FingerpaintWordBank.pick(1234, 1)
        assertEquals(picked, FingerpaintWordBank.pick(1234, 1))
        assertTrue(FingerpaintWordBank.categories.contains(picked.category))
        assertTrue(FingerpaintWordBank.words.getValue(picked.category).contains(picked.text))
        assertEquals(picked.text, FingerpaintWordBank.wordAt(
            FingerpaintWordBank.categories.indexOf(picked.category),
            FingerpaintWordBank.words.getValue(picked.category).indexOf(picked.text),
        ))
    }

    @Test
    fun `score rewards earlier guesses`() {
        assertEquals(100, FingerpaintEngine.scoreFor(FP_ROUND_MS))
        assertEquals(50, FingerpaintEngine.scoreFor(FP_ROUND_MS / 2))
        assertEquals(0, FingerpaintEngine.scoreFor(0L))
        assertEquals(0, FingerpaintEngine.scoreFor(-100L))
        assertEquals(100, FingerpaintEngine.scoreFor(FP_ROUND_MS * 2))
    }

    @Test
    fun `timer steps to round over and advances the drawer`() {
        var session = FingerpaintEngine.startRound(FingerpaintEngine.newSession(listOf("a", "b"), 5), 5)
        var round = session.roundState!!
        assertEquals(FingerpaintPhase.DRAW, session.phase)
        assertEquals(FP_ROUND_MS, round.remainingMs)
        assertEquals(0, session.drawer)

        session = FingerpaintEngine.tick(session, 30_000)
        round = session.roundState!!
        assertEquals(60_000L, round.remainingMs)

        session = FingerpaintEngine.tick(session, 60_000)
        round = session.roundState!!
        assertEquals(FingerpaintPhase.ROUND_OVER, session.phase)
        assertFalse(round.running)
        assertEquals("time up!", round.message)
        assertEquals(1, session.history.size)
        assertEquals(1, session.history[0].round)

        session = FingerpaintEngine.advance(session)
        assertEquals(2, session.round)
        assertEquals(1, session.drawer)
        assertEquals(FingerpaintPhase.DRAW, session.phase)
        assertTrue(session.document.strokes.isEmpty())

        session = FingerpaintEngine.tick(session, FP_ROUND_MS)
        session = FingerpaintEngine.advance(session)
        assertEquals(FingerpaintPhase.GAME_OVER, session.phase)
        assertEquals(2, session.history.size)
    }

    @Test
    fun `each player scores at most once and the round ends when everyone has`() {
        var session = FingerpaintEngine.startRound(
            FingerpaintEngine.newSession(listOf("a", "b", "c"), 21),
            21,
        )
        val word = session.roundState!!.word
        session = FingerpaintEngine.tick(session, 30_000)

        session = FingerpaintEngine.guess(session, 1, "totally wrong")
        assertEquals(0, session.players[1].score)
        assertEquals(false, session.roundState!!.lastGuessCorrect)
        assertEquals(FingerpaintPhase.DRAW, session.phase)

        session = FingerpaintEngine.guess(session, 1, "  ${word.uppercase()}  ")
        assertEquals(67, session.players[1].score)
        assertTrue(session.roundState!!.lastGuessCorrect == true)

        session = FingerpaintEngine.guess(session, 1, word)
        assertEquals(67, session.players[1].score)

        val drawerGuess = FingerpaintEngine.guess(session, 0, word)
        assertEquals(0, drawerGuess.players[0].score)

        session = FingerpaintEngine.guess(session, 2, word)
        assertEquals(67, session.players[2].score)
        assertEquals(FingerpaintPhase.ROUND_OVER, session.phase)
        assertEquals(setOf(1, 2), session.roundState!!.correct)
        assertNull(FingerpaintEngine.currentGuesser(session))
    }

    @Test
    fun `guess normalization trims ignores case and caps length`() {
        assertEquals("hello", FingerpaintEngine.normalizeGuess("  HeLLo  "))
        assertEquals("", FingerpaintEngine.normalizeGuess("   "))
        assertEquals(100, FingerpaintEngine.normalizeGuess("x".repeat(150)).length)
    }

    @Test
    fun `session serialization round trips`() {
        var session = FingerpaintEngine.newSession(listOf("alpha", "beta"), seed = 7)
        session = FingerpaintEngine.startRound(session, 7)

        var document = session.document
        document = FingerpaintEngine.addStroke(document, strokeAt(0f))
        document = FingerpaintEngine.addStroke(document, strokeAt(40f, color = 3))
        document = FingerpaintEngine.undo(document)
        session = session.copy(document = document)

        session = FingerpaintEngine.tick(session, 30_000)
        session = FingerpaintEngine.guess(session, 1, session.roundState!!.word)

        val decoded = FingerpaintEngine.decode(FingerpaintEngine.encode(session))
        assertEquals(session, decoded)
        assertNull(FingerpaintEngine.decode(null))
        assertNull(FingerpaintEngine.decode("not a session"))
        assertNull(FingerpaintEngine.decode("fp1;broken"))
    }
}
