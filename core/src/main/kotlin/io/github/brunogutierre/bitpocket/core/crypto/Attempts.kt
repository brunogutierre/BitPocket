package io.github.brunogutierre.bitpocket.core.crypto

import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

/** Failed unlock attempts, shared by all slots so it does not reveal which slot exists. */
data class AttemptsState(
    val failedAttempts: Int,
    val lastFailureAt: Instant?,
) {
    /** Fixed-size encoding; the platform store encrypts it (device-bound, no PIN needed). */
    fun encode(): ByteArray =
        ByteBuffer
            .allocate(SIZE)
            .put(VERSION)
            .putInt(failedAttempts)
            .putLong(lastFailureAt?.toEpochMilli() ?: NO_FAILURE)
            .array()

    companion object {
        val NONE = AttemptsState(failedAttempts = 0, lastFailureAt = null)
        const val SIZE = 32
        private const val VERSION: Byte = 1
        private const val NO_FAILURE = Long.MIN_VALUE

        fun decode(bytes: ByteArray): AttemptsState {
            require(bytes.size == SIZE) { "attempts record must be $SIZE bytes" }
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.get() == VERSION) { "Unsupported attempts record version" }
            val failed = buffer.int
            val lastFailure = buffer.long.takeIf { it != NO_FAILURE }?.let(Instant::ofEpochMilli)
            return AttemptsState(failed, lastFailure)
        }
    }
}

/** Persists [AttemptsState]. It is rewritten on EVERY attempt, so its timestamp leaks nothing. */
interface AttemptsStore {
    /** Returns [AttemptsState.NONE] when nothing was stored yet. */
    fun read(): AttemptsState

    fun write(state: AttemptsState)
}

/**
 * Free attempts first, then exponentially growing delays up to [maxDelay]:
 * with the defaults, failures 1-4 are free, the 5th locks for 30 s, then 1 min, 2 min, ... 1 h.
 */
class BackoffPolicy(
    private val freeAttempts: Int = 4,
    private val baseDelay: Duration = Duration.ofSeconds(30),
    private val maxDelay: Duration = Duration.ofHours(1),
) {
    fun delayAfter(failedAttempts: Int): Duration {
        if (failedAttempts <= freeAttempts) return Duration.ZERO
        val doublings = (failedAttempts - freeAttempts - 1).coerceAtMost(MAX_DOUBLINGS)
        return baseDelay.multipliedBy(1L shl doublings).coerceAtMost(maxDelay)
    }

    /** When the next attempt is allowed, or null if it is allowed right away. */
    fun lockedUntil(state: AttemptsState): Instant? {
        val lastFailure = state.lastFailureAt ?: return null
        val delay = delayAfter(state.failedAttempts)
        return if (delay.isZero) null else lastFailure.plus(delay)
    }

    private companion object {
        const val MAX_DOUBLINGS = 20
    }
}
