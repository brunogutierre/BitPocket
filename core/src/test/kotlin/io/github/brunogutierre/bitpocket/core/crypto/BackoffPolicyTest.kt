package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class BackoffPolicyTest {
    private val policy = BackoffPolicy()
    private val clock = FakeUnlockClock()

    private fun lockedState(failures: Int) = policy.afterFailure(AttemptsState.NONE.copy(failedAttempts = failures - 1), clock.now())

    @Test
    fun `follows the free-then-exponential schedule with a cap`() {
        val delays = (0..13).map { policy.delayAfter(it).seconds }

        assertEquals(listOf(0L, 0, 0, 0, 0, 30, 60, 120, 240, 480, 960, 1920, 3600, 3600), delays)
        assertEquals(Duration.ofHours(1), policy.delayAfter(Int.MAX_VALUE))
    }

    @Test
    fun `counts down with elapsed realtime in the same boot`() {
        val state = lockedState(failures = 5)

        clock.advanceSeconds(10)

        assertEquals(Duration.ofSeconds(20), policy.remaining(state, clock.now()))
    }

    @Test
    fun `stays locked if elapsed realtime goes backwards`() {
        val state = lockedState(failures = 5)

        clock.elapsedRealtimeMs -= 60_000

        assertEquals(Duration.ofSeconds(30), policy.remaining(state, clock.now()))
    }

    @Test
    fun `after a reboot the remaining lockout applies in full and keeps counting once re-anchored`() {
        val state = lockedState(failures = 6)
        clock.advanceSeconds(40)
        val anchored = policy.anchoredAt(state, clock.now()) // 20 s left, e.g. written by a locked-out attempt

        clock.bootCount++
        clock.elapsedRealtimeMs = 5_000 // fresh boot
        assertEquals(Duration.ofSeconds(20), policy.remaining(anchored, clock.now()))

        val reanchored = policy.anchoredAt(anchored, clock.now())
        clock.advanceSeconds(15)
        assertEquals(Duration.ofSeconds(5), policy.remaining(reanchored, clock.now()))
    }

    @Test
    fun `the failure counter saturates instead of overflowing`() {
        val state = policy.afterFailure(AttemptsState.NONE.copy(failedAttempts = Int.MAX_VALUE), clock.now())

        assertEquals(Int.MAX_VALUE, state.failedAttempts)
        assertEquals(Duration.ofHours(1).toMillis(), state.lockoutRemainingMs)
    }
}
