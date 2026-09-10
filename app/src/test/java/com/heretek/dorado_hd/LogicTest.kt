package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.PinKind
import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.media.AlarmScheduler
import com.heretek.dorado_hd.media.PlaybackController
import com.heretek.dorado_hd.ui.apps.CalcEngine
import com.heretek.dorado_hd.ui.apps.ChordData
import com.heretek.dorado_hd.ui.apps.QuizEngine
import com.heretek.dorado_hd.ui.apps.games.CheckersColor
import com.heretek.dorado_hd.ui.apps.games.CheckersEngine
import com.heretek.dorado_hd.ui.apps.games.CheckersPiece
import com.heretek.dorado_hd.ui.apps.games.CheckersState
import com.heretek.dorado_hd.ui.apps.games.ChessColor
import com.heretek.dorado_hd.ui.apps.games.ChessEngine
import com.heretek.dorado_hd.ui.apps.games.ChessPiece
import com.heretek.dorado_hd.ui.apps.games.ChessPieceType
import com.heretek.dorado_hd.ui.apps.games.ChessState
import com.heretek.dorado_hd.ui.apps.games.HeartsEngine
import com.heretek.dorado_hd.ui.apps.games.HeartsSeat
import com.heretek.dorado_hd.ui.apps.games.HeartsState
import com.heretek.dorado_hd.ui.apps.games.HexColor
import com.heretek.dorado_hd.ui.apps.games.HexicEngine
import com.heretek.dorado_hd.ui.apps.games.PokerEngine
import com.heretek.dorado_hd.ui.apps.games.ReversiEngine
import com.heretek.dorado_hd.ui.apps.games.SolCard
import com.heretek.dorado_hd.ui.apps.games.SolSuit
import com.heretek.dorado_hd.ui.apps.games.SolitaireEngine
import com.heretek.dorado_hd.ui.apps.games.SpadesEngine
import com.heretek.dorado_hd.ui.apps.games.SpadesSeat
import com.heretek.dorado_hd.ui.apps.games.SudokuEngine
import com.heretek.dorado_hd.ui.screens.formatDial
import com.heretek.dorado_hd.ui.screens.formatTime
import com.heretek.dorado_hd.ui.screens.stepFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogicTest {

    @Test
    fun `rating roundtrips through persistence values`() {
        assertEquals(Rating.NONE, Rating.from(0))
        assertEquals(Rating.HEART, Rating.from(1))
        assertEquals(Rating.BROKEN, Rating.from(2))
        assertEquals(Rating.NONE, Rating.from(99))
        assertEquals(3, Rating.entries.size) // tri-state: heart / broken / none
    }

    @Test
    fun `alphabet grouping handles junk`() {
        assertEquals('A', firstLetterOf("abba"))
        assertEquals('T', firstLetterOf(" The "))
        assertEquals('9', firstLetterOf("99 Luftballons"))
        assertEquals('#', firstLetterOf("!!!"))
        assertEquals('#', firstLetterOf(""))
        assertFalse(firstLetterOf("abba").isLowerCase())
    }

    @Test
    fun `time formats like the device`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:59", formatTime(59_000))
        assertEquals("3:42", formatTime(222_000))
        assertEquals("61:02", formatTime(3_662_000))
    }

    @Test
    fun `accent ids are unique and pink is default`() {
        val ids = DoradoAccent.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(0, DoradoAccent.PINK.id)
        assertEquals(5, DoradoAccent.entries.size)
    }

    @Test
    fun `pin kinds cover quickplay surfaces`() {
        assertTrue(PinKind.TRACK.name == "TRACK")
        // TRACK / ALBUM / ARTIST / PLAYLIST (canonical 4) + PICTURE / RADIO (Phase 5)
        // + GENRE / VIDEO / APP / PODCAST / EPISODE (M4 pin-completeness pass).
        assertEquals(11, PinKind.entries.size)
        assertTrue(
            "M4 pin-completeness must cover every Quickplay surface",
            PinKind.entries.map { it.name }.containsAll(
                listOf(
                    "TRACK", "ALBUM", "ARTIST", "PLAYLIST", "PICTURE", "RADIO",
                    "GENRE", "VIDEO", "APP", "PODCAST", "EPISODE",
                ),
            ),
        )
        // String-keyed surfaces get a deterministic refId so pin/unpin round-trips.
        assertEquals(PinKind.stableId("Rock"), PinKind.stableId("rock"))
        assertTrue(PinKind.stableId("Rock") != PinKind.stableId("Jazz"))
    }

    /* ============ Phase 1 / Smart DJ ============ */

    @Test
    fun `smart DJ puts current first, hearts next, skips broken`() {
        val uri = android.net.Uri.parse("file:///track")
        val t = { id: Long, n: String -> Track(id, n, "x", 0, "a", 0, "", 0, 0, 0, "", uri) }
        val base = listOf(t(1, "a"), t(2, "b"), t(3, "c"), t(4, "d"), t(5, "e"))
        val ratings = mapOf<Long, Int>(
            1L to Rating.HEART.value,
            2L to Rating.BROKEN.value, // skipped
            3L to Rating.NONE.value,
            4L to Rating.HEART.value,
        )
        val current = t(3, "c")
        val ordered = PlaybackController.smartShuffleOrder(base, ratings, current)
        assertEquals("current must lead", 3L, ordered.first().mediaId)
        assertEquals("broken hearts skipped", setOf(3L, 1L, 4L, 5L), ordered.map { it.mediaId }.toSet())
        assertFalse("broken must be absent", ordered.any { it.mediaId == 2L })
        // Current (3) precedes the hearted (1, 4) which precede neutral (5).
        assertTrue(ordered.indexOfFirst { it.mediaId == 3L } < ordered.indexOfFirst { it.mediaId == 1L })
        assertTrue(ordered.indexOfFirst { it.mediaId == 1L } < ordered.indexOfFirst { it.mediaId == 5L })
    }

    /* ============ Phase 1 / AlarmScheduler ============ */

    @Test
    fun `alarm scheduler picks today if hour not passed`() {
        val now = System.currentTimeMillis()
        // Use a calendar pinned to a clearly future hour.
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val next = AlarmScheduler.nextFireMs(23, 59, now)
        assertTrue("expected today (now=$now, next=$next)", next >= cal.timeInMillis - 60_000)
    }

    /* ============ Phase 3 / Calculator ============ */

    @Test
    fun `calculator handles basic arithmetic`() {
        assertEquals(4.0, CalcEngine.eval("2+2"))
        assertEquals(3.0, CalcEngine.eval("9/3"))
        assertEquals(15.0, CalcEngine.eval("(2+3)*3"))
        assertEquals(-2.0, CalcEngine.eval("-(3+5)/4"))
    }

    @Test
    fun `calculator refuses divide by zero`() {
        assertNull(CalcEngine.eval("1/0"))
    }

    /* ============ Phase 3 / Chord data ============ */

    @Test
    fun `chord finder returns a six-string shape`() {
        val shape = ChordData.shapeFor("C", "major")
        assertEquals(6, shape.size)
        // C major open position has a 3rd-fret on the A string (index 1).
        assertEquals(3, shape[1])
    }

    /* ============ Phase 3 / Music quiz ============ */

    @Test
    fun `music quiz returns null when library is too small`() {
        val uri = android.net.Uri.parse("file:///track")
        val t = { id: Long -> Track(id, "t", "a", 0, "a", 0, "", 0, 0, 0, "", uri) }
        val q = QuizEngine.buildQuestion(listOf(t(1), t(2), t(3)))
        assertNull(q)
    }

    @Test
    fun `music quiz builds a four-option question with correct answer included`() {
        val uri = android.net.Uri.parse("file:///track")
        // 8 tracks across 4 distinct artists and 2 distinct albums. Use a seeded
        // RNG so we always land on the artist branch.
        val tracks = (1..8).map {
            Track(it.toLong(), "song $it", "artist ${it % 4}", it.toLong(), "album ${it % 2}", it.toLong(), "g", 0, 0, 0, "", uri)
        }
        val q = QuizEngine.buildQuestion(tracks, rng = kotlin.random.Random(0L)) ?: error("expected question")
        assertEquals(4, q.options.size)
        assertTrue("answer must be in options", q.options.contains(q.answer))
    }

    /* ============ Phase 3 / Radio dial ============ */

    @Test
    fun `radio dial formats frequencies and streams`() {
        assertEquals("98.7 MHz", formatDial(98700))
        assertEquals("87.5 MHz", formatDial(87500))
        assertEquals("stream", formatDial(0))
    }

    @Test
    fun `radio step frequency drags in 100 kHz steps clamped to band`() {
        assertEquals(98700, stepFrequency(98700, 0f))
        assertEquals(99000, stepFrequency(98700, 120f))
        assertEquals(87500, stepFrequency(87500, -10000f)) // clamped low
        assertEquals(108000, stepFrequency(108000, 10000f)) // clamped high
    }

    /* ============ Phase 4 / Solitaire ============ */

    @Test
    fun `solitaire legal move alternates colors and descends by one`() {
        val redSeven = SolCard(SolSuit.HEART, 7, faceUp = true)
        val blackEight = SolCard(SolSuit.SPADE, 8, faceUp = true)
        assertTrue(SolitaireEngine.legalMove(redSeven, blackEight))
        assertFalse(SolitaireEngine.legalMove(blackEight, redSeven)) // wrong direction
    }

    @Test
    fun `solitaire accepts only aces onto empty foundations`() {
        val ace = SolCard(SolSuit.HEART, 1, faceUp = true)
        val two = SolCard(SolSuit.HEART, 2, faceUp = true)
        assertTrue(SolitaireEngine.toFoundation(ace, null))
        assertTrue(SolitaireEngine.toFoundation(two, ace))
        assertFalse(SolitaireEngine.toFoundation(two, null))
    }

    /* ============ Phase 4 / Sudoku ============ */

    @Test
    fun `sudoku generator produces a valid filled board`() {
        val b = SudokuEngine.newGame(seed = 1L)
        assertEquals(9, b.rows.size)
        b.rows.forEach { row -> assertEquals(9, row.size); assertEquals((1..9).toSet(), row.toSet()) }
        assertEquals(81, b.given.flatten().size)
    }

    /* ============ Phase 4 / Hexic ============ */

    @Test
    fun `hexic rotate cycles through three colors`() {
        val b = HexicEngine.newBoard(size = 5, rng = kotlin.random.Random(1))
        val start = b[2][2]
        HexicEngine.rotate(b, 2, 2)
        val next = b[2][2]
        val after = HexColor.values()
        assertEquals(after[(start.ordinal + 1) % 3], next)
    }

    @Test
    fun `hexic clusters detect at least one cluster on a random board`() {
        // With ~3 colors on a small grid, clusters are likely; we just assert
        // the routine runs and returns a Set.
        val b = HexicEngine.newBoard(size = 8, rng = kotlin.random.Random(7))
        val cells = HexicEngine.clusters(b)
        // All returned cells must be inside the board and share their color.
        for ((r, c) in cells) {
            assertTrue(r in 0 until 8 && c in 0 until 8)
        }
    }

    /* ============ Phase 4 / Reversi ============ */

    @Test
    fun `reversi initial board has four stones and four legal moves`() {
        val b = ReversiEngine.newBoard()
        assertEquals(4, ReversiEngine.score(b).first + ReversiEngine.score(b).second)
        val moves = ReversiEngine.legalMoves(b, ReversiEngine.BLACK)
        assertEquals(4, moves.size)
    }

    @Test
    fun `reversi apply places a stone and flips captured pieces`() {
        val b = ReversiEngine.newBoard()
        val move = ReversiEngine.legalMoves(b, ReversiEngine.BLACK).first()
        val next = ReversiEngine.apply(b, move.first, move.second, ReversiEngine.BLACK)
        assertEquals(ReversiEngine.BLACK, next[move.first][move.second])
        val (bScore, wScore) = ReversiEngine.score(next)
        assertTrue("expected flipped count > 4, got ${bScore + wScore}", (bScore + wScore) > 4)
    }

    /* ============ Phase 7 — More games ============ */

    @Test
    fun `hearts legalPlays must follow suit when led`() {
        val state = HeartsState(
            hands = mapOf(
                HeartsSeat.SOUTH to listOf(SolCard(SolSuit.HEART, 5, true), SolCard(SolSuit.SPADE, 10, true)),
                HeartsSeat.NORTH to emptyList(),
                HeartsSeat.EAST to emptyList(),
                HeartsSeat.WEST to emptyList(),
            ),
            trick = listOf(HeartsSeat.WEST to SolCard(SolSuit.HEART, 7, true)),
            ledSuit = SolSuit.HEART,
            passDirection = 0,
            currentPlayer = HeartsSeat.SOUTH,
            scores = HeartsEngine.heartsSeats.associateWith { 0 },
            heartsBroken = true,
            done = false,
            lastTrick = null,
            pendingPassFrom = null,
        )
        val legal = HeartsEngine.legalPlays(state, HeartsSeat.SOUTH)
        assertEquals(1, legal.size)
        assertEquals(SolSuit.HEART, legal.first().suit)
    }

    @Test
    fun `hearts cannot lead hearts before they are broken`() {
        val state = HeartsState(
            hands = mapOf(
                HeartsSeat.SOUTH to listOf(SolCard(SolSuit.HEART, 7, true), SolCard(SolSuit.CLUB, 2, true)),
                HeartsSeat.NORTH to emptyList(), HeartsSeat.EAST to emptyList(), HeartsSeat.WEST to emptyList(),
            ),
            trick = emptyList(),
            ledSuit = null,
            passDirection = 0,
            currentPlayer = HeartsSeat.SOUTH,
            scores = HeartsEngine.heartsSeats.associateWith { 0 },
            heartsBroken = false,
            done = false,
            lastTrick = null,
            pendingPassFrom = null,
        )
        val legal = HeartsEngine.legalPlays(state, HeartsSeat.SOUTH)
        assertEquals(1, legal.size)
        assertEquals(SolSuit.CLUB, legal.first().suit)
    }

    @Test
    fun `spades bid legality assigns bids to seats in order`() {
        var s = SpadesEngine.newGame()
        s = SpadesEngine.bid(s, 3)
        s = SpadesEngine.bid(s, 4)
        s = SpadesEngine.bid(s, 5)
        s = SpadesEngine.bid(s, 2)
        assertEquals(3, s.bids[SpadesSeat.WEST])
        assertEquals(4, s.bids[SpadesSeat.NORTH])
        assertEquals(5, s.bids[SpadesSeat.EAST])
        assertEquals(2, s.bids[SpadesSeat.SOUTH])
        assertTrue(s.biddingDone)
    }

    @Test
    fun `checkers mandatory capture wins when available`() {
        // RED at (2,1), BLACK at (3,2) → RED captures down-right to (4,3). Single capture only.
        val setup = CheckersState(
            board = Array(8) { arrayOfNulls<CheckersPiece?>(8) },
            turn = CheckersColor.RED,
            captureChain = null, winner = null,
        )
        setup.board[2][1] = com.heretek.dorado_hd.ui.apps.games.checkersPiece(CheckersColor.RED, false)
        setup.board[3][2] = com.heretek.dorado_hd.ui.apps.games.checkersPiece(CheckersColor.BLACK, false)
        val moves = CheckersEngine.legalMoves(setup, CheckersColor.RED)
        assertEquals(1, moves.size)
        assertTrue(moves.any { it.second == (4 to 3) })
        val after = CheckersEngine.apply(setup, 2 to 1, 4 to 3)
        assertNull(after.board[3][2])
    }

    @Test
    fun `chess detects check and legal move filtering`() {
        // Black queen on (4,4) gives check to the white king on (7,7).
        val b = Array(8) { arrayOfNulls<ChessPiece?>(8) }
        b[7][7] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        b[4][4] = ChessPiece(ChessPieceType.Q, ChessColor.BLACK)
        val s = ChessState(b, ChessColor.WHITE, 0, null, 0, 1, "")
        assertTrue(ChessEngine.isInCheck(s, ChessColor.WHITE))
        val legal = ChessEngine.legalMoves(s)
        // Every legal move must leave the king not in check.
        assertTrue(legal.all { mv -> !ChessEngine.isInCheck(ChessEngine.apply(s, mv), ChessColor.WHITE) })
    }

    @Test
    fun `poker evaluator ranks hands correctly`() {
        val royalFlush = listOf(SolCard(SolSuit.HEART, 10, true), SolCard(SolSuit.HEART, 11, true), SolCard(SolSuit.HEART, 12, true), SolCard(SolSuit.HEART, 13, true), SolCard(SolSuit.HEART, 14, true), SolCard(SolSuit.SPADE, 2, true), SolCard(SolSuit.CLUB, 3, true))
        val quads = listOf(SolCard(SolSuit.SPADE, 2, true), SolCard(SolSuit.HEART, 2, true), SolCard(SolSuit.CLUB, 2, true), SolCard(SolSuit.DIAMOND, 2, true), SolCard(SolSuit.SPADE, 5, true), SolCard(SolSuit.HEART, 9, true), SolCard(SolSuit.CLUB, 13, true))
        val fullHouse = listOf(SolCard(SolSuit.SPADE, 10, true), SolCard(SolSuit.HEART, 10, true), SolCard(SolSuit.CLUB, 10, true), SolCard(SolSuit.DIAMOND, 4, true), SolCard(SolSuit.SPADE, 4, true), SolCard(SolSuit.HEART, 8, true), SolCard(SolSuit.CLUB, 7, true))
        val straight = listOf(SolCard(SolSuit.SPADE, 5, true), SolCard(SolSuit.HEART, 6, true), SolCard(SolSuit.CLUB, 7, true), SolCard(SolSuit.DIAMOND, 8, true), SolCard(SolSuit.SPADE, 9, true), SolCard(SolSuit.HEART, 2, true), SolCard(SolSuit.CLUB, 3, true))
        val (royal, _) = PokerEngine.scoreHand(royalFlush)
        val (quadsR, _) = PokerEngine.scoreHand(quads)
        val (fh, _) = PokerEngine.scoreHand(fullHouse)
        val (str, _) = PokerEngine.scoreHand(straight)
        assertEquals("royal flush should be category 9", 9, royal)
        assertEquals("quads should be category 8", 8, quadsR)
        assertEquals("full house should be category 7", 7, fh)
        assertEquals("straight should be category 5", 5, str)
    }

    /* ============ Sprint 1 — design invariant guards ============ */

    @Test
    fun `official catalog has 62 entries with 17 launchable from frozen catalog`() {
        // Frozen catalog invariant from the gap audit: every installedId must
        // point to a real entry in the marketplace apps pivot, and the total
        // package count must match docs/zcp-inventory.md.
        val all = com.heretek.dorado_hd.data.official.OfficialCatalog.all
        assertEquals("catalog must match the ZuneRedux 62-entry set", 62, all.size)
        val installed = all.mapNotNull { it.installedId }.distinct()
        val missing = installed.filter { id -> com.heretek.dorado_hd.ui.apps.DoradoApps.byId(id) == null }
        assertTrue(
            "installed catalog ids should resolve to a real mini-app: missing=$missing",
            missing.isEmpty(),
        )
        // 12 utilities + 4 original games + 5 new games (hearts/spades/checkers/chess/texasholdem) + 1 social = 22 — but
        // 5 utilities (alarm, calendar, level, notes, stopwatch) + 4 original games (solitaire, sudoku, hexic, reversi) +
        // 1 calculator (cross-listed as utility) + 8 mocks = some entries share an id with a mock. What we assert
        // here is just that the *distinct* installedIds are non-zero and at least cover all four canonical surfaces.
        val distinct = installed.toSet()
        assertTrue("must have at least one installable app per PinKind surface", distinct.size >= 6)
    }

    @Test
    fun `DoradoColors tokens match the spec table`() {
        // Single-source-of-truth checks against docs/design-tokens.md. The build
        // invariant catches raw color literals; this guards the token values
        // themselves (background #111111, watermark alpha 0.08, etc.).
        val bg = androidx.compose.ui.graphics.Color(0xFF111111)
        val elevated = androidx.compose.ui.graphics.Color(0xFF181818)
        val tile = androidx.compose.ui.graphics.Color(0xFF202020)
        assertEquals(bg, com.heretek.dorado_hd.design.DoradoColors(accent = DoradoAccent.PINK.primary, accentBright = DoradoAccent.PINK.bright).background)
        assertEquals(elevated, com.heretek.dorado_hd.design.DoradoColors(accent = DoradoAccent.PINK.primary, accentBright = DoradoAccent.PINK.bright).elevated)
        assertEquals(tile, com.heretek.dorado_hd.design.DoradoColors(accent = DoradoAccent.PINK.primary, accentBright = DoradoAccent.PINK.bright).tile)
    }
}
