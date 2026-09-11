package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BOWLING_BLACKJACK
import com.heretek.dorado_hd.ui.apps.games.BOWLING_BALL_RADIUS
import com.heretek.dorado_hd.ui.apps.games.BOWLING_LANE_HALF
import com.heretek.dorado_hd.ui.apps.games.BOWLING_MAX_GOLF_SCORE
import com.heretek.dorado_hd.ui.apps.games.BOWLING_MAX_GOLF_SHOTS
import com.heretek.dorado_hd.ui.apps.games.BOWLING_NUM_BLACKJACK_FRAMES
import com.heretek.dorado_hd.ui.apps.games.BOWLING_NUM_PINS
import com.heretek.dorado_hd.ui.apps.games.BowlingBall
import com.heretek.dorado_hd.ui.apps.games.BowlingCard
import com.heretek.dorado_hd.ui.apps.games.BowlingEngine
import com.heretek.dorado_hd.ui.apps.games.BowlingLane
import com.heretek.dorado_hd.ui.apps.games.BowlingLength
import com.heretek.dorado_hd.ui.apps.games.BowlingMode
import com.heretek.dorado_hd.ui.apps.games.BowlingPhase
import com.heretek.dorado_hd.ui.apps.games.BowlingRival
import com.heretek.dorado_hd.ui.apps.games.BowlingSave
import com.heretek.dorado_hd.ui.apps.games.BowlingSeries
import com.heretek.dorado_hd.ui.apps.games.BowlingThrow
import com.heretek.dorado_hd.ui.apps.games.FrameMark
import com.heretek.dorado_hd.ui.apps.games.pinHomeX
import com.heretek.dorado_hd.ui.apps.games.pinHomeZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BowlingTest {

    private val straight = BowlingThrow(aim = 0f, speed = 1f, angle = 0f, spin = 0f, accuracy = 1f)

    private fun card(mode: BowlingMode = BowlingMode.EXHIBITION, length: BowlingLength = BowlingLength.FULL) =
        BowlingEngine.newCard(mode, length)

    private fun rolled(vararg rolls: Int, mode: BowlingMode = BowlingMode.EXHIBITION, length: BowlingLength = BowlingLength.FULL): BowlingCard {
        var c = card(mode, length)
        for (r in rolls) c = BowlingEngine.applyRoll(c, r)
        return c
    }

    private fun only(vararg indices: Int): List<Boolean> =
        List(BOWLING_NUM_PINS) { it in indices }

    private val noneStanding = List(BOWLING_NUM_PINS) { false }

    /* ------------------------------ scoring ------------------------------ */

    @Test
    fun `perfect game scores 300`() {
        val game = rolled(*IntArray(12) { 10 })
        assertTrue(game.finished)
        assertEquals(300, BowlingEngine.totalScore(game))
        assertEquals(
            List(10) { (it + 1) * 30 },
            BowlingEngine.scores(game).map { it },
        )
    }

    @Test
    fun `all spares score 150`() {
        val rolls = IntArray(21) { if (it % 2 == 0) 5 else 5 }
        val game = rolled(*rolls)
        assertTrue(game.finished)
        assertEquals(150, BowlingEngine.totalScore(game))
    }

    @Test
    fun `strike adds the next two rolls`() {
        val game = rolled(10, 3, 4)
        assertEquals(17, BowlingEngine.scores(game)[0])
        assertEquals(24, BowlingEngine.totalScore(game))
    }

    @Test
    fun `spare adds the next roll`() {
        val game = rolled(5, 5, 3, 0)
        assertEquals(13, BowlingEngine.scores(game)[0])
    }

    @Test
    fun `tenth frame strike bonus rolls`() {
        var game = rolled(*IntArray(18) { 0 })
        game = BowlingEngine.applyRoll(game, 10)
        game = BowlingEngine.applyRoll(game, 7)
        game = BowlingEngine.applyRoll(game, 2)
        assertTrue(game.finished)
        assertEquals(19, BowlingEngine.totalScore(game))
    }

    @Test
    fun `tenth frame double strike scores 24`() {
        var game = rolled(*IntArray(18) { 0 })
        game = BowlingEngine.applyRoll(game, 10)
        game = BowlingEngine.applyRoll(game, 10)
        game = BowlingEngine.applyRoll(game, 4)
        assertEquals(24, BowlingEngine.totalScore(game))
    }

    @Test
    fun `tenth frame spare allows one bonus roll`() {
        var game = rolled(*IntArray(18) { 0 })
        game = BowlingEngine.applyRoll(game, 5)
        game = BowlingEngine.applyRoll(game, 5)
        assertEquals(0, BowlingEngine.totalScore(game))
        game = BowlingEngine.applyRoll(game, 7)
        assertTrue(game.finished)
        assertEquals(17, BowlingEngine.totalScore(game))
    }

    @Test
    fun `open tenth frame ends after two rolls`() {
        var game = rolled(*IntArray(18) { 0 })
        game = BowlingEngine.applyRoll(game, 3)
        game = BowlingEngine.applyRoll(game, 4)
        assertTrue(game.finished)
        assertEquals(7, BowlingEngine.totalScore(game))
    }

    @Test
    fun `frame marks and display strings`() {
        val game = rolled(10, 5, 5, 3, 0)
        assertEquals(FrameMark.STRIKE, BowlingEngine.frameMark(game, 0))
        assertEquals(FrameMark.SPARE, BowlingEngine.frameMark(game, 1))
        assertEquals(FrameMark.NONE, BowlingEngine.frameMark(game, 2))
        assertEquals(listOf("X"), BowlingEngine.displayRolls(game, 0))
        assertEquals(listOf("5", "/"), BowlingEngine.displayRolls(game, 1))
        assertEquals(listOf("3", "-"), BowlingEngine.displayRolls(game, 2))
    }

    /* ------------------------------ golf / blackjack ------------------------------ */

    @Test
    fun `golf scores total shots and ends when pins fall`() {
        var game = card(BowlingMode.GOLF)
        game = BowlingEngine.applyRoll(game, 3)
        game = BowlingEngine.applyRoll(game, 4)
        game = BowlingEngine.applyRoll(game, 3)
        assertEquals(1, game.frameIndex)
        assertEquals(10, BowlingEngine.golfShots(game))
    }

    @Test
    fun `golf hole caps at five shots`() {
        var game = card(BowlingMode.GOLF)
        repeat(BOWLING_MAX_GOLF_SHOTS) { game = BowlingEngine.applyRoll(game, 1) }
        assertEquals(BOWLING_MAX_GOLF_SHOTS, game.frames[0].rolls.size)
        assertEquals(1, game.frameIndex)
    }

    @Test
    fun `blackjack busts over 21`() {
        var game = card(BowlingMode.BLACKJACK)
        game = BowlingEngine.applyRoll(game, 10)
        game = BowlingEngine.applyRoll(game, 8)
        assertFalse(game.finished)
        game = BowlingEngine.applyRoll(game, 5)
        assertTrue(game.finished)
        assertTrue(BowlingEngine.isBust(game))
        assertEquals(3, BOWLING_NUM_BLACKJACK_FRAMES)
        assertEquals(23, game.totalPins)
    }

    @Test
    fun `blackjack close scores win`() {
        val match = BowlingEngine.newMatch(
            BowlingMode.BLACKJACK, BowlingLength.FULL, BowlingLane.PINERY,
            BowlingBall.COMET, BowlingRival.WREN, seed = 7,
        )
        val finished = match.copy(
            card = rolled(10, 6, 5, mode = BowlingMode.BLACKJACK),
            dealerRolls = listOf(7, 6, 6),
            dealerTotal = 19,
            phase = BowlingPhase.FINISHED,
        )
        assertTrue(BowlingEngine.matchWon(finished))
    }

    /* ------------------------------ rack reset ------------------------------ */

    @Test
    fun `rack resets after strike and spare only`() {
        var game = rolled(10)
        assertTrue(BowlingEngine.shouldResetPins(game, 0))
        game = rolled(5)
        assertFalse(BowlingEngine.shouldResetPins(game, 0))
        game = rolled(5, 5)
        assertTrue(BowlingEngine.shouldResetPins(game, 0))
        game = rolled(0, 10)
        assertTrue(BowlingEngine.shouldResetPins(game, 0))
        game = rolled(7, 1)
        assertTrue(BowlingEngine.shouldResetPins(game, 0))
    }

    @Test
    fun `tenth frame keeps pins after a strike then non strike`() {
        var game = rolled(*IntArray(18) { 0 })
        game = BowlingEngine.applyRoll(game, 10)
        game = BowlingEngine.applyRoll(game, 3)
        assertFalse(BowlingEngine.shouldResetPins(game, 9))
    }

    @Test
    fun `golf keeps pins within a hole and resets on the next`() {
        var game = card(BowlingMode.GOLF)
        game = BowlingEngine.applyRoll(game, 4)
        assertFalse(BowlingEngine.shouldResetPins(game, 0))
        game = BowlingEngine.applyRoll(game, 6)
        assertTrue(BowlingEngine.shouldResetPins(game, 0))
    }

    /* ------------------------------ mode tables ------------------------------ */

    @Test
    fun `mode rule tables`() {
        val exhibition = BowlingEngine.rulesFor(BowlingMode.EXHIBITION, BowlingLength.FULL)
        assertEquals(10, exhibition.frames)
        assertEquals(2, exhibition.rollsPerFrame)
        assertTrue(exhibition.bonusRolls)
        assertTrue(exhibition.strikeSpareBonus)
        assertFalse(exhibition.lowerWins)
        assertEquals(null, exhibition.targetTotal)

        val half = BowlingEngine.rulesFor(BowlingMode.EXHIBITION, BowlingLength.HALF)
        assertEquals(5, half.frames)

        val blackjack = BowlingEngine.rulesFor(BowlingMode.BLACKJACK, BowlingLength.FULL)
        assertEquals(3, blackjack.frames)
        assertEquals(1, blackjack.rollsPerFrame)
        assertFalse(blackjack.strikeSpareBonus)
        assertEquals(21, blackjack.targetTotal)
        assertEquals(BOWLING_BLACKJACK, blackjack.targetTotal)

        val golf = BowlingEngine.rulesFor(BowlingMode.GOLF, BowlingLength.FULL)
        assertEquals(10, golf.frames)
        assertTrue(golf.lowerWins)
        assertEquals(BOWLING_MAX_GOLF_SHOTS, golf.maxShotsPerFrame)
        assertEquals(10, golf.targetTotal)
        assertEquals(999, BOWLING_MAX_GOLF_SCORE)
    }

    /* ------------------------------ physics ------------------------------ */

    @Test
    fun `ball topples the head pin`() {
        val shot = BowlingEngine.simulateShot(BowlingLane.ORBIT, only(0), straight, BowlingBall.COMET, seed = 1)
        assertTrue(shot.done)
        assertEquals(1, shot.felledCount())
        assertFalse(shot.gutter)
    }

    @Test
    fun `ball sends the head pin down lane`() {
        val shot = BowlingEngine.simulateShot(BowlingLane.ORBIT, only(0), straight, BowlingBall.COMET, seed = 2)
        val head = shot.pins[0]
        assertTrue(head.z > pinHomeZ(0))
        assertEquals(0, head.index)
        assertTrue(pinHomeX(0) == 0f)
    }

    @Test
    fun `ball pin transfer sends the neighbour down the rack`() {
        val single = BowlingEngine.simulateShot(BowlingLane.ORBIT, only(0), straight, BowlingBall.COMET, seed = 3)
        val rack = BowlingEngine.simulateShot(BowlingLane.ORBIT, only(0, 1), straight, BowlingBall.COMET, seed = 3)
        assertEquals(1, single.felledCount())
        assertTrue("pin 2 should be carried by the chain", rack.felledCount() >= 2)
    }

    @Test
    fun `pin on pin chain topples a neighbour`() {
        val start = BowlingEngine.startShot(
            BowlingLane.ORBIT, only(0, 1), straight, BowlingBall.COMET, seed = 4,
        )
        val dx = start.pins[1].x - start.pins[0].x
        val dz = start.pins[1].z - start.pins[0].z
        val len = kotlin.math.hypot(dx, dz)
        val moving = start.copy(
            ball = start.ball.copy(done = true, vx = 0f, vz = 0f),
            pins = start.pins.mapIndexed { i, pin ->
                if (i == 0) pin.copy(vx = dx / len * 400f, vz = dz / len * 400f) else pin
            },
        )
        val after = BowlingEngine.tickShot(moving, 32)
        assertFalse(after.pins[1].standing)
        assertEquals(1, after.felledCount())
        assertTrue(after.pins[1].tilt > 0f)
    }

    @Test
    fun `hook bends the ball both ways`() {
        val plus = BowlingEngine.simulateShot(
            BowlingLane.ORBIT, noneStanding,
            straight.copy(spin = 0.3f), BowlingBall.COMET, seed = 5,
        )
        val zero = BowlingEngine.simulateShot(
            BowlingLane.ORBIT, noneStanding, straight, BowlingBall.COMET, seed = 5,
        )
        val minus = BowlingEngine.simulateShot(
            BowlingLane.ORBIT, noneStanding,
            straight.copy(spin = -0.3f), BowlingBall.COMET, seed = 5,
        )
        assertTrue(plus.ball.x > 3f)
        assertTrue(minus.ball.x < -3f)
        assertTrue(zero.ball.x > minus.ball.x && zero.ball.x < plus.ball.x)
    }

    @Test
    fun `oil pattern delays the hook`() {
        val oily = BowlingEngine.simulateShot(
            BowlingLane.DUNES, noneStanding,
            straight.copy(spin = 0.4f), BowlingBall.COMET, seed = 6,
        )
        val dry = BowlingEngine.simulateShot(
            BowlingLane.ARCADE, noneStanding,
            straight.copy(spin = 0.4f), BowlingBall.COMET, seed = 6,
        )
        assertTrue(dry.ball.x > oily.ball.x)
    }

    @Test
    fun `gutter ball fells nothing`() {
        val shot = BowlingEngine.simulateShot(
            BowlingLane.PINERY, List(BOWLING_NUM_PINS) { true },
            BowlingThrow(aim = 1f, speed = 1f, angle = 1f, spin = 0f), BowlingBall.COMET, seed = 7,
        )
        assertTrue(shot.gutter)
        assertTrue(shot.ball.inGutter)
        assertEquals(0, shot.felledCount())
    }

    @Test
    fun `seeded simulation is deterministic`() {
        val a = BowlingEngine.simulateShot(BowlingLane.GROTTO, List(BOWLING_NUM_PINS) { true }, straight, BowlingBall.JADE, seed = 11)
        val b = BowlingEngine.simulateShot(BowlingLane.GROTTO, List(BOWLING_NUM_PINS) { true }, straight, BowlingBall.JADE, seed = 11)
        assertEquals(a, b)
        assertEquals(a.pins, b.pins)
    }

    @Test
    fun `sub-step chunking is time independent`() {
        val start = BowlingEngine.startShot(BowlingLane.ARCADE, List(BOWLING_NUM_PINS) { true }, straight, BowlingBall.TIDE, seed = 12)
        val chunked = BowlingEngine.tickShot(BowlingEngine.tickShot(start, 16), 16)
        val single = BowlingEngine.tickShot(start, 32)
        assertEquals(single, chunked)
    }

    @Test
    fun `cpu throw is deterministic and in range`() {
        val a = BowlingEngine.cpuThrow(BowlingRival.VESPER, seed = 21, index = 0)
        val b = BowlingEngine.cpuThrow(BowlingRival.VESPER, seed = 21, index = 0)
        assertEquals(a, b)
        assertTrue(a.speed in 0.35f..0.95f)
        assertTrue(a.aim in -1f..1f)
        assertTrue(a.spin in -1f..1f)
        val other = BowlingEngine.cpuThrow(BowlingRival.VESPER, seed = 22, index = 0)
        assertNotEquals(a, other)
    }

    @Test
    fun `throw classifier clamps aim speed angle and spin`() {
        val wild = BowlingEngine.startShot(
            BowlingLane.PINERY, List(BOWLING_NUM_PINS) { true },
            BowlingThrow(aim = 5f, speed = 5f, angle = -9f, spin = -9f), BowlingBall.COMET, seed = 61,
        )
        assertEquals(BOWLING_LANE_HALF - BOWLING_BALL_RADIUS, wild.ball.x)
        assertTrue(wild.ball.vz > 700f)
        assertTrue(wild.ball.vx >= -60f)
        assertTrue(wild.ball.spin >= -1.1f && wild.ball.spin <= 1.1f)

        val tame = BowlingEngine.startShot(
            BowlingLane.PINERY, List(BOWLING_NUM_PINS) { true },
            BowlingThrow(aim = -9f, speed = -3f, angle = 0f, spin = 0f), BowlingBall.COMET, seed = 62,
        )
        assertEquals(-(BOWLING_LANE_HALF - BOWLING_BALL_RADIUS), tame.ball.x)
        assertTrue(tame.ball.vz in 250f..350f)
    }

    @Test
    fun `five original lanes carry distinct physics parameters`() {
        assertEquals(5, BowlingLane.entries.size)
        assertEquals(5, BowlingBall.entries.size)
        assertEquals(5, BowlingRival.entries.size)
        for (lane in BowlingLane.entries) {
            assertTrue(lane.friction > 0f)
            assertTrue(lane.hook > 0f)
            assertTrue(lane.oilLength in 0f..1f)
        }
        assertEquals(5, BowlingLane.entries.map { it.label }.toSet().size)
    }

    /* ------------------------------ match flow ------------------------------ */

    @Test
    fun `match advances to completion`() {
        var match = BowlingEngine.newMatch(
            BowlingMode.EXHIBITION, BowlingLength.FULL, BowlingLane.PINERY,
            BowlingBall.COMET, BowlingRival.WREN, seed = 31,
        )
        var shots = 0
        while (!match.card.finished && shots++ < 40) {
            match = BowlingEngine.release(match, straight)
            var ticks = 0
            while (match.phase == BowlingPhase.ROLLING && ticks++ < 600) {
                match = BowlingEngine.advance(match, 32)
            }
            if (match.phase == BowlingPhase.SETTLED) match = BowlingEngine.nextShot(match)
        }
        assertTrue(match.card.finished)
        assertTrue(match.card.frames.all { it.rolls.isNotEmpty() })
        assertTrue(BowlingEngine.totalScore(match.card) in 0..300)
    }

    @Test
    fun `blackjack match produces three dealer rolls`() {
        var match = BowlingEngine.newMatch(
            BowlingMode.BLACKJACK, BowlingLength.FULL, BowlingLane.GROTTO,
            BowlingBall.EMBER, BowlingRival.MORROW, seed = 41,
        )
        var shots = 0
        while (match.phase != BowlingPhase.FINISHED && shots++ < 12) {
            match = BowlingEngine.release(match, straight)
            var ticks = 0
            while (match.phase == BowlingPhase.ROLLING && ticks++ < 600) {
                match = BowlingEngine.advance(match, 32)
            }
            if (match.phase == BowlingPhase.SETTLED) match = BowlingEngine.nextShot(match)
        }
        assertTrue(match.phase == BowlingPhase.FINISHED)
        assertEquals(BOWLING_NUM_BLACKJACK_FRAMES, match.dealerRolls.size)
        assertEquals(match.dealerRolls.sum(), match.dealerTotal)
    }

    /* ------------------------------ storage ------------------------------ */

    @Test
    fun `save roundtrip preserves the card and rack`() {
        var game = card()
        game = BowlingEngine.applyRoll(game, 10)
        game = BowlingEngine.applyRoll(game, 4)
        val mask = only(0, 1, 5)
        val save = BowlingSave(game, mask)
        val decoded = BowlingEngine.decodeSave(BowlingEngine.encodeSave(save))
        assertNotNull(decoded)
        assertEquals(save.card, decoded!!.card)
        assertEquals(mask, decoded.standing)
    }

    @Test
    fun `series state roundtrips and records results`() {
        val series = BowlingSeries(played = 3, wins = 2, best = 187, strikes = 9, spares = 7, turkeys = 1)
        assertEquals(series, BowlingEngine.decodeSeries(BowlingEngine.encodeSeries(series)))
        assertEquals(BowlingSeries(), BowlingEngine.decodeSeries("garbage"))
        val match = BowlingEngine.newMatch(
            BowlingMode.EXHIBITION, BowlingLength.FULL, BowlingLane.DUNES,
            BowlingBall.LASER, BowlingRival.DASH, seed = 51,
        )
        val grown = BowlingEngine.applySeries(BowlingSeries(), match)
        assertEquals(1, grown.played)
        assertEquals(0, grown.best)
    }
}
