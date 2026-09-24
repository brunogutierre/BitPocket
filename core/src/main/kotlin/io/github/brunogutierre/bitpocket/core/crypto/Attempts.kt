package io.github.brunogutierre.bitpocket.core.crypto

import java.nio.ByteBuffer
import java.time.Duration

/** A reading of the clocks used for lockouts (Android: `SystemClock` and `Settings.Global.BOOT_COUNT`). */
data class ClockReading(
    /** Monotonic time since boot, unaffected by wall-clock changes. */
    val elapsedRealtimeMs: Long,
    val bootCount: Int,
)

fun interface UnlockClock {
    fun now(): ClockReading
}

/**
 * Failed unlock attempts, shared by all slots so it does not reveal which slot exists.
 * [lockoutRemainingMs] is the lockout left at the anchor ([bootCount], [elapsedAtMs]), i.e. at
 * the last write; the wall clock is never used, so changing it cannot shorten a lockout.
 */
data class AttemptsState(
    val failedAttempts: Int,
    val lockoutRemainingMs: Long,
    val bootCount: Int,
    val elapsedAtMs: Long,
) {
    init {
        require(failedAttempts >= 0) { "failedAttempts must be >= 0" }
        require(lockoutRemainingMs >= 0) { "lockoutRemainingMs must be >= 0" }
    }

    /** Fixed-size encoding; the platform store encrypts it (device-bound, no PIN needed). */
    fun encode(): ByteArray =
        ByteBuffer
            .allocate(SIZE)
            .put(VERSION)
            .putInt(failedAttempts)
            .putLong(lockoutRemainingMs)
            .putInt(bootCount)
            .putLong(elapsedAtMs)
            .array()

    companion object {
        val NONE = AttemptsState(failedAttempts = 0, lockoutRemainingMs = 0, bootCount = 0, elapsedAtMs = 0)
        const val SIZE = 32
        private const val VERSION: Byte = 1

        /** @throws IllegalArgumentException for a malformed record. */
        fun decode(bytes: ByteArray): AttemptsState {
            require(bytes.size == SIZE) { "attempts record must be $SIZE bytes" }
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.get() == VERSION) { "Unsupported attempts record version" }
            return AttemptsState(buffer.int, buffer.long, buffer.int, buffer.long)
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

    /**
     * Lockout left at [now]. In the same boot, elapsed realtime is subtracted (never negative);
     * after a reboot the stored remaining time applies in full, since the time spent before the
     * reboot cannot be measured without the wall clock.
     */
    fun remaining(
        state: AttemptsState,
        now: ClockReading,
    ): Duration {
        val elapsed =
            if (now.bootCount == state.bootCount) (now.elapsedRealtimeMs - state.elapsedAtMs).coerceAtLeast(0) else 0
        return Duration.ofMillis((state.lockoutRemainingMs - elapsed).coerceAtLeast(0))
    }

    /** [state] re-anchored at [now], keeping its remaining lockout (so it survives reboots). */
    fun anchoredAt(
        state: AttemptsState,
        now: ClockReading,
    ) = state.copy(
        lockoutRemainingMs = remaining(state, now).toMillis(),
        bootCount = now.bootCount,
        elapsedAtMs = now.elapsedRealtimeMs,
    )

    /** State after one more failure at [now] (the counter saturates instead of overflowing). */
    fun afterFailure(
        state: AttemptsState,
        now: ClockReading,
    ): AttemptsState {
        val failed = if (state.failedAttempts == Int.MAX_VALUE) Int.MAX_VALUE else state.failedAttempts + 1
        return AttemptsState(failed, delayAfter(failed).toMillis(), now.bootCount, now.elapsedRealtimeMs)
    }

    private companion object {
        const val MAX_DOUBLINGS = 20
    }
}
