package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.AudiosurfEngine
import com.heretek.dorado_hd.ui.apps.games.AudiosurfMode
import com.heretek.dorado_hd.ui.apps.games.RideStatus
import com.heretek.dorado_hd.ui.apps.games.TrackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-27 regression: a music-on ride whose player stalls must fall back to the
 * wall clock after the grace period and still reach FINISH.
 */
class AudiosurfStallFallbackTest {

    @Test
    fun `advancing player target wins`() {
        assertEquals(
            4_200L,
            AudiosurfEngine.syncedTarget(
                currentPositionMs = 4_000,
                playerPositionMs = 4_200,
                dtMs = 16,
                speedScale = 1f,
                stalledMs = 0,
            ),
        )
    }

    @Test
    fun `stalled player holds until the timeout then advances`() {
        assertEquals(
            4_000L,
            AudiosurfEngine.syncedTarget(4_000, 4_000, 16, 1f, 500),
        )
        assertEquals(
            4_016L,
            AudiosurfEngine.syncedTarget(
                4_000, 4_000, 16, 1f, AudiosurfEngine.STALL_TIMEOUT_MS,
            ),
        )
    }

    @Test
    fun `stalled music ride reaches FINISH`() {
        val profile = TrackProfile.synthetic(seed = 7, durationMs = 30_000L)
        val course = AudiosurfEngine.buildCourse(profile, AudiosurfMode.NORMAL, seed = 7)
        var state = AudiosurfEngine.startRide(AudiosurfEngine.newRide(course))
        var stalledMs = 0L
        var frames = 0
        while (state.status != RideStatus.FINISHED && frames < 20_000) {
            frames++
            stalledMs += 16
            val target = AudiosurfEngine.syncedTarget(
                currentPositionMs = state.trackPositionMs,
                playerPositionMs = 0L,
                dtMs = 16,
                speedScale = state.speedScale,
                stalledMs = stalledMs,
            )
            if (target > state.trackPositionMs) state = AudiosurfEngine.step(state, target)
        }
        assertEquals(RideStatus.FINISHED, state.status)
        assertTrue("fallback never engaged", stalledMs >= AudiosurfEngine.STALL_TIMEOUT_MS)
    }
}
