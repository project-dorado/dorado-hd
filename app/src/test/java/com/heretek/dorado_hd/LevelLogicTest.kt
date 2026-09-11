package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.LevelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Wave 1 level rules: 90·g degree mapping, the three clamp curves, bubble
 * squish, 0.1° readouts, peak-hold memory and the no-sensor fallback.
 */
class LevelLogicTest {

    @Test
    fun `accelerometer maps ninety degrees per g`() {
        val flat = LevelEngine.fromAccel(0.0, 0.0)
        assertEquals(0.0, flat.rollDeg, 1e-9)
        assertEquals(0.0, flat.pitchDeg, 1e-9)
        val tilted = LevelEngine.fromAccel(0.5, -0.25)
        assertEquals(45.0, tilted.rollDeg, 1e-9)
        assertEquals(-22.5, tilted.pitchDeg, 1e-9)
        assertEquals(25.0, tilted.bubbleX, 1e-9)
        assertEquals(-12.5, tilted.bubbleY, 1e-9)
    }

    @Test
    fun `offsets clamp at their travel limits`() {
        val state = LevelEngine.fromAccel(3.0, -3.0)
        assertEquals(LevelEngine.ROUND_TRAVEL, state.bubbleX, 1e-9)
        assertEquals(-LevelEngine.ROUND_TRAVEL, state.bubbleY, 1e-9)
        assertEquals(-LevelEngine.VERTICAL_TRAVEL, state.verticalOffset, 1e-9)
        assertEquals(-LevelEngine.HORIZONTAL_TRAVEL, state.horizontalOffset, 1e-9)
        val opposite = LevelEngine.fromAccel(-1.0, 1.0)
        assertEquals(-50.0, opposite.bubbleX, 1e-9)
        assertEquals(50.0, opposite.bubbleY, 1e-9)
        assertEquals(LevelEngine.HORIZONTAL_TRAVEL, opposite.horizontalOffset, 1e-9)
    }

    @Test
    fun `bubble squish halves at full tilt`() {
        val flat = LevelEngine.fromAccel(0.0, 0.0)
        assertEquals(1.0, flat.bubbleScaleX, 1e-9)
        assertEquals(1.0, flat.bubbleScaleY, 1e-9)
        val tilted = LevelEngine.fromAccel(1.0, 1.0)
        assertEquals(LevelEngine.MIN_BUBBLE_SCALE, tilted.bubbleScaleX, 1e-9)
        assertEquals(LevelEngine.MIN_BUBBLE_SCALE, tilted.bubbleScaleY, 1e-9)
        val mild = LevelEngine.fromAccel(0.4, 0.0)
        assertEquals(0.8, mild.bubbleScaleX, 1e-9)
        assertEquals(1.0, mild.bubbleScaleY, 1e-9)
    }

    @Test
    fun `jitter stays inside the device limits`() {
        val state = LevelEngine.fromAccel(0.42, -0.77)
        assertTrue(abs(state.verticalJitter) <= LevelEngine.VERTICAL_JITTER_PX + 1e-9)
        assertTrue(abs(state.horizontalJitter) >= LevelEngine.HORIZONTAL_JITTER_MIN_PX - 1e-9)
        assertTrue(abs(state.horizontalJitter) <= LevelEngine.HORIZONTAL_JITTER_MAX_PX + 1e-9)
    }

    @Test
    fun `readout rounds to one decimal`() {
        assertEquals("12.3\u00B0", LevelEngine.formatDegrees(12.34))
        assertEquals("12.4\u00B0", LevelEngine.formatDegrees(12.36))
        assertEquals("-0.0\u00B0", LevelEngine.formatDegrees(-0.04))
        assertEquals("90.0\u00B0", LevelEngine.formatDegrees(90.0))
    }

    @Test
    fun `snapshot freezes and clear releases the reading`() {
        val live = LevelEngine.fromAccel(0.3, 0.1)
        val saved = LevelEngine.snapshot(live)
        assertTrue(saved.hasMemory)
        assertEquals(live.rollDeg, saved.savedRoll!!, 1e-9)
        assertEquals(live.pitchDeg, saved.savedPitch!!, 1e-9)
        assertEquals(live.bubbleX, saved.savedBubbleX, 1e-9)
        // A later snapshot copies from whatever state it is handed.
        val moved = LevelEngine.snapshot(LevelEngine.fromAccel(0.8, -0.6))
        assertEquals(40.0, moved.savedBubbleX, 1e-9)
        val cleared = LevelEngine.clear(moved)
        assertFalse(cleared.hasMemory)
        assertNull(cleared.savedRoll)
        assertNull(cleared.savedPitch)
    }

    @Test
    fun `absent sensor state is flagged`() {
        assertFalse(LevelEngine.absent().available)
        assertTrue(LevelEngine.fromAccel(0.0, 0.0).available)
    }

    @Test
    fun `tilt converts into the accelerometer mapping`() {
        val flat = LevelEngine.fromTilt(0.0, 0.0)
        assertTrue(flat.available)
        assertEquals(0.0, flat.rollDeg, 1e-9)
        assertEquals(0.0, flat.pitchDeg, 1e-9)
        // rememberTilt's pitch is atan2(-x, …), so +22.5° means ax ≈ -sin(22.5°).
        val pitched = LevelEngine.fromTilt(0.0, 22.5)
        assertEquals(-90.0 * sin(Math.toRadians(22.5)), pitched.rollDeg, 1e-9)
    }
}
