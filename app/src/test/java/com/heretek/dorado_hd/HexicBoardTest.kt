package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.HEX_COLORS
import com.heretek.dorado_hd.ui.apps.games.HexBoard
import com.heretek.dorado_hd.ui.apps.games.HexColor
import com.heretek.dorado_hd.ui.apps.games.HexCursor
import com.heretek.dorado_hd.ui.apps.games.HexCursorKind
import com.heretek.dorado_hd.ui.apps.games.HexPiece
import com.heretek.dorado_hd.ui.apps.games.HexPos
import com.heretek.dorado_hd.ui.apps.games.HexicDifficulty
import com.heretek.dorado_hd.ui.apps.games.HexicEngine
import com.heretek.dorado_hd.ui.apps.games.HexicGame
import com.heretek.dorado_hd.ui.apps.games.HexicMatchKind
import com.heretek.dorado_hd.ui.apps.games.HexicMode
import com.heretek.dorado_hd.ui.apps.games.HexicReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexicBoardTest {

    private fun p(hue: HexColor, star: Boolean = false, bomb: Int = -1) = HexPiece(hue, star, bomb)

    private fun board(vararg cells: Pair<HexPos, HexPiece?>): HexBoard =
        HexicEngine.emptyBoard().updated(cells.toMap())

    private fun fullBoard(hues: (Int, Int) -> HexColor): HexBoard {
        val updates = HashMap<HexPos, HexPiece?>()
        for (col in 0 until HexicEngine.COLUMNS) {
            for (row in 0 until HexicEngine.cellCount(col)) {
                updates[HexPos(col, row)] = HexPiece(hues(col, row))
            }
        }
        return HexicEngine.emptyBoard().updated(updates)
    }

    @Test
    fun `hexic board is the ten column 85 cell cluster`() {
        val board = HexicEngine.emptyBoard()
        assertEquals(10, board.width)
        var total = 0
        for (col in 0 until board.width) {
            val height = HexicEngine.cellCount(col)
            assertEquals(if (col % 2 == 0) 8 else 9, height)
            total += height
        }
        assertEquals(85, total)
        assertEquals(85, HexicEngine.allPositions().size)
    }

    @Test
    fun `hexic adjacency is symmetric and six way`() {
        val board = HexicEngine.emptyBoard()
        for (pos in HexicEngine.allPositions()) {
            val neighbors = HexicEngine.neighbors(pos).filter { board.contains(it) }
            assertTrue("six neighbours expected at $pos", neighbors.size <= 6)
            for (neighbor in neighbors) {
                assertTrue(
                    "adjacency must be symmetric for $pos/$neighbor",
                    HexicEngine.neighbors(neighbor).contains(pos),
                )
            }
        }
    }

    @Test
    fun `hexic standard cursors rotate a three cell triangle`() {
        val even = HexicEngine.standardCells(HexPos(0, 0), half = false)
        assertEquals(
            listOf(HexPos(0, 0), HexPos(1, 0), HexPos(1, 1)),
            even,
        )
        val odd = HexicEngine.standardCells(HexPos(1, 0), half = false)
        assertEquals(
            listOf(HexPos(1, 0), HexPos(2, 0), HexPos(1, 1)),
            odd,
        )
        val board = board(
            even[0] to p(HexColor.A),
            even[1] to p(HexColor.B),
            even[2] to p(HexColor.C),
        )
        val rotated = HexicEngine.rotate(board, HexCursor(HexPos(0, 0)), clockwise = true)
        assertEquals(HexColor.C, rotated[even[0]]?.hue)
        assertEquals(HexColor.A, rotated[even[1]]?.hue)
        assertEquals(HexColor.B, rotated[even[2]]?.hue)
    }

    @Test
    fun `hexic flower cursor rotates six ring pieces around a fixed center`() {
        val center = HexPos(1, 1)
        val ring = HexicEngine.flowerRing(center)
        assertEquals(6, ring.size)
        assertFalse(ring.contains(center))
        ring.forEach { assertTrue(HexicEngine.neighbors(center).contains(it)) }
        val cells = HashMap<HexPos, HexPiece?>()
        ring.forEachIndexed { index, pos -> cells[pos] = p(HEX_COLORS[index]) }
        cells[center] = p(HexColor.G, star = true)
        val board = HexicEngine.emptyBoard().updated(cells)
        val rotated = HexicEngine.rotate(board, HexCursor(center, HexCursorKind.FLOWER), clockwise = true)
        assertEquals(HexColor.G, rotated[center]?.hue)
        assertTrue(rotated[center]!!.star)
        assertEquals(1, ring.count { rotated[it]?.hue == HEX_COLORS[0] })
    }

    @Test
    fun `hexic cluster score follows the official formula`() {
        val b = board(
            HexPos(0, 0) to p(HexColor.A),
            HexPos(1, 0) to p(HexColor.A),
            HexPos(1, 1) to p(HexColor.A),
        )
        val matches = HexicEngine.findMatches(b)
        assertEquals(1, matches.size)
        assertEquals(HexicMatchKind.CLUSTER, matches.first().kind)
        assertEquals(15, HexicEngine.scoreFor(b, HexicEngine.levelFor(HexicMode.MARATHON, 1)))
        assertEquals(75, HexicEngine.scoreFor(b, HexicEngine.levelFor(HexicMode.MARATHON, 5)))
    }

    @Test
    fun `hexic star in a cluster multiplies the score`() {
        val b = board(
            HexPos(0, 0) to p(HexColor.A, star = true),
            HexPos(1, 0) to p(HexColor.A),
            HexPos(1, 1) to p(HexColor.A),
        )
        val match = HexicEngine.findMatches(b).single()
        assertEquals(HexicMatchKind.BONUS, match.kind)
        assertEquals(30, HexicEngine.scoreFor(b, HexicEngine.levelFor(HexicMode.MARATHON, 1)))
    }

    @Test
    fun `hexic three stars score as a three bonus even across hues`() {
        val b = board(
            HexPos(0, 0) to p(HexColor.A, star = true),
            HexPos(1, 0) to p(HexColor.B, star = true),
            HexPos(1, 1) to p(HexColor.C, star = true),
        )
        val match = HexicEngine.findMatches(b).single()
        assertEquals(HexicMatchKind.THREE_BONUS, match.kind)
        assertEquals(300, HexicEngine.scoreFor(b, HexicEngine.levelFor(HexicMode.MARATHON, 1)))
    }

    @Test
    fun `hexic bomb plus star trio is a bomb bonus`() {
        val b = board(
            HexPos(0, 0) to p(HexColor.A, star = true),
            HexPos(1, 0) to p(HexColor.A, bomb = 3),
            HexPos(1, 1) to p(HexColor.A),
        )
        val match = HexicEngine.findMatches(b).single()
        assertEquals(HexicMatchKind.BOMB_BONUS, match.kind)
        assertEquals(5, HexicEngine.scoreFor(b, HexicEngine.levelFor(HexicMode.MARATHON, 1)))
    }

    @Test
    fun `hexic flower converts the centre and scores level times base`() {
        val center = HexPos(1, 1)
        val cells = HashMap<HexPos, HexPiece?>()
        HexicEngine.flowerRing(center).forEach { cells[it] = p(HexColor.A) }
        cells[center] = p(HexColor.B)
        val b = HexicEngine.emptyBoard().updated(cells)
        val match = HexicEngine.findMatches(b).single()
        assertEquals(HexicMatchKind.FLOWER, match.kind)
        assertEquals(1000, HexicEngine.scoreFor(b, HexicEngine.levelFor(HexicMode.MARATHON, 1)))
        val resolved = HexicEngine.resolve(b, HexicEngine.levelFor(HexicMode.MARATHON, 1), seed = 3)
        assertEquals(1, resolved.board.columns.sumOf { col -> col.count { it?.hue == HexColor.FLOWER } })
        assertTrue(resolved.gained >= 1000)
    }

    @Test
    fun `hexic pearl cluster is an instant win`() {
        val b = board(
            HexPos(0, 0) to p(HexColor.PEARL1),
            HexPos(1, 0) to p(HexColor.PEARL2),
            HexPos(1, 1) to p(HexColor.PEARL1),
        )
        assertEquals(HexicMatchKind.PEARL_CLUSTER, HexicEngine.findMatches(b).single().kind)
        val resolved = HexicEngine.resolve(b, HexicEngine.levelFor(HexicMode.MARATHON, 1), seed = 4)
        assertTrue(resolved.won)
        assertEquals(HexicReason.PEARL_CLUSTER, resolved.reason)
        assertTrue(resolved.gained >= 50000)
    }

    @Test
    fun `hexic level table matches the device curve`() {
        val marathon = HexicEngine.levelTable(HexicMode.MARATHON)
        assertEquals(7, marathon.size)
        assertEquals(listOf(5, 5, 5, 6, 6, 7, 7), marathon.map { it.colors })
        assertEquals((1..7).toList(), marathon.map { it.multiplier })
        assertEquals(listOf(false, false, true, true, true, true, true), marathon.map { it.allowBombs })
        assertEquals(10, marathon[6].bombFreq)
        assertEquals(6, marathon[6].bombCount)
        assertEquals(1, marathon[6].bombGrace)
        assertEquals(-1, marathon[6].combos)
        assertEquals(50.0, marathon[0].startSeconds, 0.0)
        assertEquals(0.5, marathon[0].validMoveSeconds, 0.0)
        assertEquals(-0.3, marathon[0].invalidMoveSeconds, 0.0)
        val survival = HexicEngine.levelTable(HexicMode.SURVIVAL)
        assertTrue(survival.none { it.allowBombs })
        assertEquals(4, survival[0].colors)
        assertEquals(HexicDifficulty.NORMAL.startLevel, 1)
        assertEquals(HexicDifficulty.HARD.startLevel, 3)
        assertEquals(HexicDifficulty.EXPERT.startLevel, 5)
    }

    @Test
    fun `hexic game is deterministic for a seed`() {
        val a = HexicEngine.newGame(HexicMode.MARATHON, HexicDifficulty.NORMAL, seed = 7)
        val b = HexicEngine.newGame(HexicMode.MARATHON, HexicDifficulty.NORMAL, seed = 7)
        assertEquals(HexicEngine.encode(a), HexicEngine.encode(b))
        val hinted = HexicEngine.hints(a.board)
        if (hinted.isNotEmpty()) {
            val nextA = HexicEngine.rotateGame(a, hinted.first(), clockwise = true)
            val nextB = HexicEngine.rotateGame(b, hinted.first(), clockwise = true)
            assertEquals(HexicEngine.encode(nextA), HexicEngine.encode(nextB))
        }
    }

    @Test
    fun `hexic state round trips through the save codec`() {
        val game = HexicEngine.newGame(HexicMode.TIMED, HexicDifficulty.HARD, seed = 11)
        val encoded = HexicEngine.encode(game)
        val decoded = HexicEngine.decode(encoded)
        assertNotNull(decoded)
        assertEquals(encoded, HexicEngine.encode(decoded!!))
        assertEquals(10, decoded.board.width)
        assertEquals(85, decoded.board.countPieces())
    }

    @Test
    fun `hexic timed mode rewards clears and punishes misses`() {
        val hintBoard = board(
            HexPos(0, 0) to p(HexColor.A),
            HexPos(1, 0) to p(HexColor.A),
            HexPos(1, 1) to p(HexColor.B),
            HexPos(1, 2) to p(HexColor.A),
        )
        assertTrue(HexicEngine.findMatches(hintBoard).isEmpty())
        val game = HexicGame(
            mode = HexicMode.TIMED,
            difficulty = HexicDifficulty.NORMAL,
            level = 1,
            board = hintBoard,
            timeLeft = 50.0,
            combosLeft = 50,
            seed = 2,
            bombCountdown = 15,
            bombGrace = 5,
        )
        val hintCursor = HexCursor(HexPos(0, 0))
        val rotated = HexicEngine.rotate(hintBoard, hintCursor, clockwise = true)
        assertEquals(HexicMatchKind.CLUSTER, HexicEngine.findMatches(rotated).single().kind)
        val scored = HexicEngine.rotateGame(game, hintCursor, clockwise = true)
        assertTrue(scored.timerStarted)
        assertTrue("clears add clock", scored.timeLeft > 50.0)
        assertTrue("clears score", scored.score >= 15)

        val quiet = board(
            HexPos(4, 0) to p(HexColor.B),
            HexPos(5, 0) to p(HexColor.C),
            HexPos(5, 1) to p(HexColor.D),
        )
        assertTrue(HexicEngine.findMatches(quiet).isEmpty())
        val cursor = HexicEngine.rotations(quiet).first { HexicEngine.cursorRotatable(quiet, it) }
        val coldGame = game.copy(board = quiet, score = 0, timeLeft = 50.0, timerStarted = false, seed = 9)
        val cold = HexicEngine.rotateGame(coldGame, cursor, clockwise = true)
        assertFalse("the clock waits for a valid rotation", cold.timerStarted)
        assertEquals(50.0, cold.timeLeft, 0.0)
        val quietGame = coldGame.copy(timerStarted = true)
        val missed = HexicEngine.rotateGame(quietGame, cursor, clockwise = true)
        assertTrue(missed.timerStarted)
        assertFalse(missed.score > 0)
        assertTrue("misses drain clock", missed.timeLeft < 50.0)
    }

    @Test
    fun `hexic survival locks every piece and ends as a loss`() {
        val full = fullBoard { col, row -> HEX_COLORS[(col + row) % 7] }
        var game = HexicGame(
            mode = HexicMode.SURVIVAL,
            difficulty = HexicDifficulty.NORMAL,
            level = 1,
            board = full,
            survivalLevel = 1,
            seed = 5,
        )
        repeat(84) { game = HexicEngine.survivalStep(game) }
        assertEquals(84, game.lockProgress)
        assertFalse(game.lost)
        game = HexicEngine.survivalStep(game)
        assertTrue(game.lost)
        assertEquals(HexicReason.LOCKED, game.reason)
    }
}
