package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class UnlockServiceTest {
    private val start = Instant.parse("2026-09-24T12:00:00Z")
    private val clock = MutableClock(start)
    private val keyWrapper = FakeKeyWrapper()
    private val kdf = CountingKdf()
    private val slots = SlotStore(InMemorySlotStorage(), kdf, keyWrapper, TEST_KDF_PARAMS)
    private val attempts = InMemoryAttemptsStore()
    private val service = UnlockService(slots, attempts, BackoffPolicy(), clock)

    private fun givenSlots() {
        slots.save(SlotId.A, "1111".toCharArray(), "main".toByteArray())
        slots.save(SlotId.B, "2222".toCharArray(), "decoy".toByteArray())
    }

    private fun unlockCountingWork(pin: String): Pair<UnlockResult, Int> {
        val kdfBefore = kdf.calls
        val unwrapBefore = keyWrapper.unwrapCalls
        val result = service.unlock(pin.toCharArray())
        assertEquals(kdf.calls - kdfBefore, keyWrapper.unwrapCalls - unwrapBefore, "one unwrap per KDF")
        return result to kdf.calls - kdfBefore
    }

    @Test
    fun `does the same KDF work for main, other-slot and wrong PINs`() {
        givenSlots()

        val (main, mainWork) = unlockCountingWork("1111")
        val (other, otherWork) = unlockCountingWork("2222")
        val (wrong, wrongWork) = unlockCountingWork("0000")

        assertEquals(SlotId.A, (main as UnlockResult.Unlocked).slot)
        assertArrayEquals("main".toByteArray(), main.payload)
        assertEquals(SlotId.B, (other as UnlockResult.Unlocked).slot)
        assertArrayEquals("decoy".toByteArray(), other.payload)
        assertEquals(UnlockResult.Wrong(lockedUntil = null), wrong)
        assertEquals(listOf(2, 2, 2), listOf(mainWork, otherWork, wrongWork))
    }

    @Test
    fun `locks out with growing delays after the free attempts`() {
        givenSlots()
        repeat(4) { assertEquals(UnlockResult.Wrong(null), service.unlock("0000".toCharArray())) }

        assertEquals(UnlockResult.Wrong(start.plusSeconds(30)), service.unlock("0000".toCharArray()))
        val (locked, work) = unlockCountingWork("1111")
        assertEquals(UnlockResult.LockedOut(start.plusSeconds(30)), locked, "even the right PIN waits")
        assertEquals(0, work, "no KDF while locked out")

        clock.now = start.plusSeconds(30)
        assertEquals(UnlockResult.Wrong(start.plusSeconds(90)), service.unlock("0000".toCharArray()))
    }

    @Test
    fun `success resets the counter`() {
        givenSlots()
        repeat(3) { service.unlock("0000".toCharArray()) }

        assertInstanceOf(UnlockResult.Unlocked::class.java, service.unlock("1111".toCharArray()))

        assertEquals(AttemptsState.NONE, attempts.read())
    }

    @Test
    fun `rewrites the attempts record on every attempt`() {
        givenSlots()
        service.unlock("0000".toCharArray())
        service.unlock("1111".toCharArray())
        repeat(5) { service.unlock("0000".toCharArray()) }
        val writesBefore = attempts.writes

        service.unlock("0000".toCharArray()) // locked out

        assertEquals(1, attempts.writes - writesBefore)
    }

    @Test
    fun `counts the attempt before running the KDF`() {
        val crashing =
            UnlockService(SlotStore(InMemorySlotStorage(), { _, _, _ -> error("killed") }, keyWrapper, TEST_KDF_PARAMS), attempts)

        assertThrows<IllegalStateException> { crashing.unlock("0000".toCharArray()) }

        assertEquals(1, attempts.read().failedAttempts)
    }

    @Test
    fun `wrong PIN with only one slot saved still runs the KDF twice`() {
        slots.save(SlotId.A, "1111".toCharArray(), "main".toByteArray())

        val (result, work) = unlockCountingWork("0000")

        assertEquals(UnlockResult.Wrong(null), result)
        assertEquals(2, work)
    }
}

class BackoffPolicyTest {
    private val policy = BackoffPolicy()

    @Test
    fun `follows the free-then-exponential schedule with a cap`() {
        val delays = (0..13).map { policy.delayAfter(it).seconds }

        assertEquals(listOf(0L, 0, 0, 0, 0, 30, 60, 120, 240, 480, 960, 1920, 3600, 3600), delays)
        assertEquals(Duration.ofHours(1), policy.delayAfter(Int.MAX_VALUE))
    }

    @Test
    fun `attempts record has a fixed-size round-trippable encoding`() {
        val state = AttemptsState(7, Instant.ofEpochMilli(1_790_000_000_000))

        assertEquals(AttemptsState.SIZE, state.encode().size)
        assertEquals(AttemptsState.SIZE, AttemptsState.NONE.encode().size)
        assertEquals(state, AttemptsState.decode(state.encode()))
        assertEquals(AttemptsState.NONE, AttemptsState.decode(AttemptsState.NONE.encode()))
        assertThrows<IllegalArgumentException> { AttemptsState.decode(ByteArray(3)) }
    }
}

class InMemoryAttemptsStore : AttemptsStore {
    private var state = AttemptsState.NONE
    var writes = 0
        private set

    override fun read() = state

    override fun write(state: AttemptsState) {
        writes++
        this.state = state
    }
}

class MutableClock(
    var now: Instant,
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId) = this
}
