package com.heretek.dorado_hd.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure PIN hashing/verification for the device screen lock (canon §5,
 * parity audit §9 `GemSettingPinLockScene` / `HudPinLockScene`).
 *
 * A PIN is never stored or round-tripped: only a salted PBKDF2-HMAC-SHA256
 * hash plus its parameters are persisted (as base64 strings in DataStore).
 * [verify] recomputes the candidate hash and compares it in constant time.
 * Android's app-private DataStore is the storage boundary; this object touches
 * no I/O and is unit-testable on the JVM.
 */
object PinLock {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    const val DEFAULT_ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 16

    /** A stored credential: hash + salt + the iteration count used to derive it. */
    data class Stored(val hash: ByteArray, val salt: ByteArray, val iterations: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Stored) return false
            return iterations == other.iterations &&
                hash.contentEquals(other.hash) &&
                salt.contentEquals(other.salt)
        }

        override fun hashCode(): Int {
            var result = hash.contentHashCode()
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + iterations
            return result
        }
    }

    /** 4–6 decimal digits, the device's accepted PIN shape. */
    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(SALT_LENGTH_BYTES).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(isValid(pin)) { "pin must be $MIN_LENGTH-$MAX_LENGTH digits" }
        require(iterations >= 1) { "iterations must be >= 1" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Derives a fresh credential with a new random salt. */
    fun hashNew(
        pin: String,
        random: SecureRandom = SecureRandom(),
        iterations: Int = DEFAULT_ITERATIONS,
    ): Stored {
        val salt = newSalt(random)
        return Stored(hash(pin, salt, iterations), salt, iterations)
    }

    fun verify(pin: String, stored: Stored): Boolean {
        if (!isValid(pin)) return false
        val candidate = hash(pin, stored.salt, stored.iterations)
        return MessageDigest.isEqual(candidate, stored.hash)
    }

    /** Base64 for DataStore transport. Never encodes the PIN itself. */
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}

/**
 * Consecutive-failure rate limiter for PIN entry: after [maxAttempts] wrong
 * PINs the keypad is blocked for [cooldownMs]. Pure and clock-injectable so
 * the policy is unit-testable without sleeping.
 */
class PinAttemptLimiter(
    val maxAttempts: Int = 5,
    val cooldownMs: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var failures: Int = 0
        private set

    private var lockedUntilMs: Long = 0L

    fun remainingCooldownMs(): Long = (lockedUntilMs - clock()).coerceAtLeast(0L)

    fun isLocked(): Boolean = remainingCooldownMs() > 0L

    fun canAttempt(): Boolean = !isLocked()

    fun onFailure() {
        if (isLocked()) return
        failures++
        if (failures >= maxAttempts) {
            lockedUntilMs = clock() + cooldownMs
            failures = 0
        }
    }

    fun onSuccess() {
        failures = 0
        lockedUntilMs = 0L
    }
}
