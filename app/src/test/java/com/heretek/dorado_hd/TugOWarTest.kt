package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.TugInputMode
import com.heretek.dorado_hd.ui.apps.games.TugOWarEngine
import com.heretek.dorado_hd.ui.apps.games.TugRhythm
import com.heretek.dorado_hd.ui.apps.games.TugSide
import com.heretek.dorado_hd.ui.apps.games.TugState
import com.heretek.dorado_hd.ui.apps.games.TugStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TugOWarTest {

    private fun newMatch(hotseat: Boolean = false) =
        TugOWarEngine.newMatch(TugInputMode.TAP, hotseat)

    /** Force a round to a winner by pinning one side's velocity. */
    private fun playRound(state: TugState, winner: TugSide): TugState {
        var current = state
        var guard = 0
        while (current.status == TugStatus.PLAYING && guard++ < 100) {
            current = TugOWarEngine.step(
                current.copy(
                    player = current.player.copy(velocity = if (winner == TugSide.PLAYER) 400.0 else 0.0),
                    opponent = current.opponent.copy(velocity = if (winner == TugSide.OPPONENT) 400.0 else 0.0),
                ),
                250L,
            )
        }
        return current
    }

    @Test
    fun `second pull establishes the rhythm interval`() {
        var state = newMatch()
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 1_000L)
        assertEquals(2.0, state.player.velocity, 1e-9)
        assertEquals(null, state.player.intervalMs)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 2_000L)
        assertEquals(4.0, state.player.velocity, 1e-9)
        assertEquals(1_000L, state.player.intervalMs)
    }

    @Test
    fun `rhythm window accepts on beat and inside tolerance`() {
        var state = newMatch()
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 1_000L)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 2_000L)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 3_000L)
        assertEquals(6.0, state.player.velocity, 1e-9)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 3_700L)
        assertEquals(8.0, state.player.velocity, 1e-9)
    }

    @Test
    fun `mistimed pull applies double penalty and clamps at zero`() {
        var state = newMatch()
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 1_000L)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 2_000L)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 2_100L)
        assertEquals(0.0, state.player.velocity, 1e-9)
    }

    @Test
    fun `idle velocity decays one per second after half a second`() {
        var state = TugOWarEngine.pull(newMatch(), TugSide.PLAYER, 0L)
        state = TugOWarEngine.step(state, 400L)
        assertEquals(2.0, state.player.velocity, 1e-4)
        state = TugOWarEngine.step(state, 200L)
        assertEquals(1.8, state.player.velocity, 1e-4)
    }

    @Test
    fun `rope reaches the player win threshold`() {
        val state = TugOWarEngine.step(
            newMatch().copy(player = TugRhythm(velocity = 200.0)),
            1_000L,
        )
        assertEquals(TugStatus.ROUND_OVER, state.status)
        assertEquals(TugSide.PLAYER, state.roundWinner)
        assertEquals(1, state.playerScore)
    }

    @Test
    fun `rope reaches the opponent win threshold`() {
        val state = TugOWarEngine.step(
            newMatch().copy(opponent = TugRhythm(velocity = 500.0)),
            1_000L,
        )
        assertEquals(TugStatus.ROUND_OVER, state.status)
        assertEquals(TugSide.OPPONENT, state.roundWinner)
    }

    @Test
    fun `single player difficulty ramps by five hundredths per win`() {
        var state = newMatch()
        state = playRound(state, TugSide.PLAYER)
        state = TugOWarEngine.continueRound(state)
        state = playRound(state, TugSide.PLAYER)
        state = TugOWarEngine.continueRound(state)
        assertEquals(0.15, state.difficulty, 1e-9)
        assertEquals(2, state.playerScore)
    }

    @Test
    fun `single player loss resets difficulty and streak`() {
        var state = newMatch()
        state = playRound(state, TugSide.PLAYER)
        state = TugOWarEngine.continueRound(state)
        state = playRound(state, TugSide.PLAYER)
        state = TugOWarEngine.continueRound(state)
        assertEquals(2, state.playerScore)
        state = playRound(state, TugSide.OPPONENT)
        assertEquals(0.05, state.difficulty, 1e-9)
        assertEquals(0, state.playerScore)
    }

    @Test
    fun `ten rounds end the match with the higher tally`() {
        var state = newMatch()
        repeat(TugOWarEngine.ROUND_COUNT) {
            state = playRound(state, TugSide.PLAYER)
            if (state.status == TugStatus.ROUND_OVER) state = TugOWarEngine.continueRound(state)
        }
        assertEquals(TugStatus.MATCH_OVER, state.status)
        assertEquals(TugSide.PLAYER, state.matchWinner)
        assertEquals(10, state.playerScore)
    }

    @Test
    fun `hotseat keeps both tallies across ten rounds`() {
        var state = newMatch(hotseat = true)
        repeat(10) { index ->
            state = playRound(state, if (index < 6) TugSide.PLAYER else TugSide.OPPONENT)
            if (state.status == TugStatus.ROUND_OVER) state = TugOWarEngine.continueRound(state)
        }
        assertEquals(TugStatus.MATCH_OVER, state.status)
        assertEquals(6, state.playerScore)
        assertEquals(4, state.opponentScore)
        assertEquals(TugSide.PLAYER, state.matchWinner)
    }

    @Test
    fun `tap zones follow the strip rules`() {
        val single = newMatch()
        assertTrue(TugOWarEngine.tapRegisters(single, TugSide.PLAYER, 379f))
        assertFalse(TugOWarEngine.tapRegisters(single, TugSide.PLAYER, 377f))
        assertFalse(TugOWarEngine.tapRegisters(single, TugSide.OPPONENT, 100f))
        val hotseat = newMatch(hotseat = true)
        assertTrue(TugOWarEngine.tapRegisters(hotseat, TugSide.OPPONENT, 101f))
        assertFalse(TugOWarEngine.tapRegisters(hotseat, TugSide.OPPONENT, 103f))
    }

    @Test
    fun `drag needs fifty pixels downward in the right half`() {
        val state = newMatch(hotseat = true)
        assertTrue(TugOWarEngine.dragRegisters(state, TugSide.PLAYER, 100f, 160f, 200f))
        assertFalse(TugOWarEngine.dragRegisters(state, TugSide.PLAYER, 100f, 140f, 200f))
        assertFalse(TugOWarEngine.dragRegisters(state, TugSide.PLAYER, 100f, 160f, 100f))
        assertTrue(TugOWarEngine.dragRegisters(state, TugSide.OPPONENT, 100f, 160f, 90f))
        assertFalse(TugOWarEngine.dragRegisters(newMatch(), TugSide.OPPONENT, 100f, 160f, 90f))
    }

    @Test
    fun `shake threshold matches the sum of axis deltas`() {
        assertTrue(TugOWarEngine.isShake(0.2, 0.2, 0.2))
        assertFalse(TugOWarEngine.isShake(0.1, 0.1, 0.1))
        assertTrue(TugOWarEngine.isShakeFromTilt(20.0, 15.0))
        assertFalse(TugOWarEngine.isShakeFromTilt(10.0, 10.0))
    }

    @Test
    fun `rope tiers track the velocity difference`() {
        assertEquals(0, TugOWarEngine.ropeTier(9.0))
        assertEquals(1, TugOWarEngine.ropeTier(11.0))
        assertEquals(1, TugOWarEngine.ropeTier(-11.0))
        assertEquals(2, TugOWarEngine.ropeTier(21.0))
        assertEquals(3, TugOWarEngine.ropeTier(-31.0))
    }

    @Test
    fun `shake tint scales with rope displacement`() {
        val centred = newMatch()
        assertEquals(0, TugOWarEngine.shakeTintAlpha(centred))
        val pulled = centred.copy(ropeY = TugOWarEngine.ROPE_PLAYER_WIN)
        assertEquals(TugOWarEngine.SHAKE_TINT_MAX, TugOWarEngine.shakeTintAlpha(pulled))
        assertFalse(TugOWarEngine.shakeTintBlue(pulled))
        assertTrue(TugOWarEngine.shakeTintBlue(centred.copy(ropeY = TugOWarEngine.ROPE_OPPONENT_WIN)))
    }

    @Test
    fun `state survives an encode decode round trip`() {
        var state = newMatch(hotseat = true)
        state = TugOWarEngine.pull(state, TugSide.PLAYER, 1_000L)
        state = TugOWarEngine.pull(state, TugSide.OPPONENT, 1_400L)
        val decoded = TugOWarEngine.decode(TugOWarEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(state, decoded)
    }
}
