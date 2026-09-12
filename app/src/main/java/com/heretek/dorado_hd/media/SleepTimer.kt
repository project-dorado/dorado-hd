package com.heretek.dorado_hd.media

/**
 * Pure sleep-timer math (post-device extension, canon §10). Kept free of
 * Android so arming/expiry is unit-testable.
 */
object SleepTimer {

    /** Deadline for [minutes] from [elapsedNowMs]; 0 or less means disarmed. */
    fun deadlineMs(elapsedNowMs: Long, minutes: Int): Long =
        if (minutes <= 0) 0L else elapsedNowMs + minutes * 60_000L

    /** Milliseconds left, never negative. A disarmed deadline yields 0. */
    fun remainingMs(deadlineMs: Long, elapsedNowMs: Long): Long {
        if (deadlineMs <= 0L) return 0L
        return (deadlineMs - elapsedNowMs).coerceAtLeast(0L)
    }

    /** True once the deadline has passed (disarmed deadlines never expire). */
    fun expired(deadlineMs: Long, elapsedNowMs: Long): Boolean =
        deadlineMs > 0L && elapsedNowMs >= deadlineMs
}
