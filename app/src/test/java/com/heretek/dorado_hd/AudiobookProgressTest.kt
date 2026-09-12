package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.AudiobookProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Progress/bookmark codec and time formatting (roadmap D4). */
class AudiobookProgressTest {

    @Test
    fun `encode writes part and position`() {
        assertEquals("2:12000", AudiobookProgress.encode(AudiobookProgress.Position(2, 12_000L)))
        assertEquals("0:0", AudiobookProgress.encode(AudiobookProgress.Position(0, 0L)))
    }

    @Test
    fun `encode maps missing or negative parts to none`() {
        assertEquals(AudiobookProgress.NONE, AudiobookProgress.encode(null))
        assertEquals(AudiobookProgress.NONE, AudiobookProgress.encode(AudiobookProgress.Position(-1, 500L)))
        // A negative offset is clamped, a negative part is "not started".
        assertEquals("1:0", AudiobookProgress.encode(AudiobookProgress.Position(1, -50L)))
    }

    @Test
    fun `decode round trips valid positions`() {
        val position = AudiobookProgress.Position(7, 3_600_000L)
        assertEquals(position, AudiobookProgress.decode(AudiobookProgress.encode(position)))
        assertEquals(AudiobookProgress.Position(0, 0L), AudiobookProgress.decode("0:0"))
    }

    @Test
    fun `decode rejects none and malformed input`() {
        assertNull(AudiobookProgress.decode(null))
        assertNull(AudiobookProgress.decode(""))
        assertNull(AudiobookProgress.decode(AudiobookProgress.NONE))
        assertNull(AudiobookProgress.decode("-1:0"))
        assertNull(AudiobookProgress.decode("garbage"))
        assertNull(AudiobookProgress.decode("2"))
        assertNull(AudiobookProgress.decode("2:"))
        assertNull(AudiobookProgress.decode("x:10"))
        assertNull(AudiobookProgress.decode("2:-1"))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(150_000L, AudiobookProgress.remainingMs(90_000L, 240_000L))
        assertEquals(0L, AudiobookProgress.remainingMs(300_000L, 240_000L))
    }

    @Test
    fun `format renders minutes or hours`() {
        assertEquals("0:00", AudiobookProgress.format(0L))
        assertEquals("1:15", AudiobookProgress.format(75_400L))
        assertEquals("1:02:03", AudiobookProgress.format(3_723_000L))
        assertEquals("0:00", AudiobookProgress.format(-10L))
    }
}
