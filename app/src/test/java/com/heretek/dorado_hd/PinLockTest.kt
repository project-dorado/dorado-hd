package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.security.PinAttemptLimiter
import com.heretek.dorado_hd.data.security.PinLock
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Track C — screen-lock PIN hashing and attempt rate limiting. */
class PinLockTest {

    // Low iteration count keeps the unit tests fast; production uses
    // PinLock.DEFAULT_ITERATIONS.
    private val iterations = 1_000

    @Test
    fun `pin shape accepts 4 to 6 digits only`() {
        assertTrue(PinLock.isValid("1234"))
        assertTrue(PinLock.isValid("123456"))
        assertFalse(PinLock.isValid("123"))
        assertFalse(PinLock.isValid("1234567"))
        assertFalse(PinLock.isValid("12a4"))
        assertFalse(PinLock.isValid(""))
    }

    @Test
    fun `correct pin verifies and incorrect pin does not`() {
        val stored = PinLock.hashNew("4821", SecureRandom(), iterations)

        assertTrue(PinLock.verify("4821", stored))
        assertFalse(PinLock.verify("1234", stored))
        assertFalse(PinLock.verify("4822", stored))
    }

    @Test
    fun `no plaintext is stored or returned`() {
        val pin = "90210"
        val stored = PinLock.hashNew(pin, SecureRandom(), iterations)

        assertFalse("hash must not equal the raw pin", stored.hash.contentEquals(pin.toByteArray()))
        assertFalse("stored record must not contain the pin", PinLock.encode(stored.hash).contains(pin))
        assertFalse("salt must not be the pin", stored.salt.contentEquals(pin.toByteArray()))
    }

    @Test
    fun `the same pin hashes differently with random salts`() {
        val a = PinLock.hashNew("1234", SecureRandom(), iterations)
        val b = PinLock.hashNew("1234", SecureRandom(), iterations)

        assertNotEquals(a, b)
        assertFalse(a.hash.contentEquals(b.hash))
        assertTrue(PinLock.verify("1234", a))
        assertTrue(PinLock.verify("1234", b))
    }

    @Test
    fun `iteration count travels with the credential`() {
        val stored = PinLock.hashNew("1111", SecureRandom(), iterations)

        assertEquals(iterations, stored.iterations)
        assertTrue(PinLock.verify("1111", stored))
    }

    @Test
    fun `limiter locks after the configured number of failures`() {
        var now = 0L
        val limiter = PinAttemptLimiter(maxAttempts = 5, cooldownMs = 30_000L, clock = { now })

        repeat(4) { limiter.onFailure() }
        assertFalse(limiter.isLocked())
        assertEquals(4, limiter.failures)

        limiter.onFailure()
        assertTrue(limiter.isLocked())
        assertEquals(30_000L, limiter.remainingCooldownMs())
        assertFalse(limiter.canAttempt())

        // Additional failures during the cooldown do not extend it.
        limiter.onFailure()
        assertEquals(30_000L, limiter.remainingCooldownMs())
    }

    @Test
    fun `cooldown counts down and then resets`() {
        var now = 100_000L
        val limiter = PinAttemptLimiter(maxAttempts = 2, cooldownMs = 30_000L, clock = { now })

        limiter.onFailure()
        limiter.onFailure()
        assertTrue(limiter.isLocked())
        assertEquals(30_000L, limiter.remainingCooldownMs())

        now += 10_000L
        assertEquals(20_000L, limiter.remainingCooldownMs())
        assertTrue(limiter.isLocked())

        now += 20_000L
        assertFalse(limiter.isLocked())
        assertTrue(limiter.canAttempt())
        assertEquals(0, limiter.failures)

        limiter.onSuccess()
        assertEquals(0, limiter.failures)
    }
}
