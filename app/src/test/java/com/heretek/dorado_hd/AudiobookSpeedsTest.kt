package com.heretek.dorado_hd

import com.heretek.dorado_hd.media.AudiobookSpeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Speed-step mapping for the audiobook selector (roadmap D4). */
class AudiobookSpeedsTest {

    @Test
    fun `offers the five selector steps`() {
        assertEquals(listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f), AudiobookSpeeds.STEPS)
    }

    @Test
    fun `nearest snaps any value onto a step`() {
        assertEquals(0.75f, AudiobookSpeeds.nearest(0.6f))
        assertEquals(1.0f, AudiobookSpeeds.nearest(1.1f))
        assertEquals(1.25f, AudiobookSpeeds.nearest(1.3f))
        assertEquals(1.5f, AudiobookSpeeds.nearest(1.6f))
        assertEquals(2.0f, AudiobookSpeeds.nearest(3.0f))
        // Ties resolve to the lower step (deterministic).
        assertEquals(1.0f, AudiobookSpeeds.nearest(1.125f))
    }

    @Test
    fun `labels drop trailing zeros`() {
        assertEquals("1×", AudiobookSpeeds.label(1.0f))
        assertEquals("0.75×", AudiobookSpeeds.label(0.75f))
        assertEquals("1.25×", AudiobookSpeeds.label(1.25f))
        assertEquals("2×", AudiobookSpeeds.label(2.0f))
        // A non-step label still reports the nearest offered step.
        assertEquals("1.5×", AudiobookSpeeds.label(1.4f))
    }

    @Test
    fun `is step recognises exact steps only`() {
        assertTrue(AudiobookSpeeds.isStep(0.75f))
        assertTrue(AudiobookSpeeds.isStep(2.0f))
        assertFalse(AudiobookSpeeds.isStep(1.1f))
    }

    @Test
    fun `clamp keeps playback speeds sane`() {
        assertEquals(0.25f, AudiobookSpeeds.clamp(0.0f))
        assertEquals(3.0f, AudiobookSpeeds.clamp(9.0f))
        assertEquals(1.5f, AudiobookSpeeds.clamp(1.5f))
    }
}
