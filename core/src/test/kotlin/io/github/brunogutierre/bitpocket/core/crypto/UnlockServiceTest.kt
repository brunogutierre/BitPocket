package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class UnlockServiceTest {
    private val clock = FakeUnlockClock()
    private val keyWrapper = FakeKeyWrapper()
    private val kdf = CountingKdf()
    private val storage = InMemorySlotStorage()
    private val slots = SlotStore(storage, kdf, keyWrapper, TEST_KDF_PARAMS)
    private val attempts = InMemoryAttemptsStore()
    private val service = UnlockService(slots, attempts, clock)

    private data class Work(
        val kdfCalls: Int,
        val unwraps: Int,
    )

    private fun unlockMeasuringWork(pin: String): Pair<UnlockResult, Work> {
        val kdfBefore = kdf.calls
        val unwrapsBefore = keyWrapper.unwrapCalls
        val result = service.unlock(pin.toCharArray())
        return result to Work(kdf.calls - kdfBefore, keyWrapper.unwrapCalls - unwrapsBefore)
    }

    private fun givenSlots() {
        slots.save(SlotId.A, "1111".toCharArray(), "main".toByteArray())
        slots.save(SlotId.B, "2222".toCharArray(), "decoy".toByteArray())
    }

    private fun wrongPin() = service.unlock("0000".toCharArray())

    @Test
    fun `does the same work for main, other-slot and wrong PINs`() {
        givenSlots()

        val (main, mainWork) = unlockMeasuringWork("1111")
        val (other, otherWork) = unlockMeasuringWork("2222")
        val (wrong, wrongWork) = unlockMeasuringWork("0000")

        assertEquals(SlotId.A, (main as UnlockResult.Unlocked).slot)
        assertArrayEquals("main".toByteArray(), main.payload)
        assertEquals(SlotId.B, (other as UnlockResult.Unlocked).slot)
        assertArrayEquals("decoy".toByteArray(), other.payload)
        assertEquals(UnlockResult.Wrong(Duration.ZERO), wrong)
        assertEquals(listOf(Work(2, 2), Work(2, 2), Work(2, 2)), listOf(mainWork, otherWork, wrongWork))
    }

    @Test
    fun `locks out after the free attempts, rewriting the record on every attempt`() {
        givenSlots()
        repeat(4) { assertEquals(UnlockResult.Wrong(Duration.ZERO), wrongPin()) }
        assertEquals(UnlockResult.Wrong(Duration.ofSeconds(30)), wrongPin())
        val writesBefore = attempts.writes

        clock.advanceSeconds(10)
        val (locked, work) = unlockMeasuringWork("1111")

        assertEquals(UnlockResult.LockedOut(Duration.ofSeconds(20)), locked, "even the right PIN waits")
        assertEquals(Work(0, 0), work, "no KDF while locked out")
        assertEquals(writesBefore + 1, attempts.writes)
        clock.advanceSeconds(20)
        assertEquals(UnlockResult.Wrong(Duration.ofSeconds(60)), wrongPin())
    }

    @Test
    fun `a reboot does not shorten a lockout`() {
        givenSlots()
        repeat(5) { wrongPin() }

        clock.bootCount++
        clock.elapsedRealtimeMs = 1_000

        assertEquals(UnlockResult.LockedOut(Duration.ofSeconds(30)), service.unlock("1111".toCharArray()))
    }

    @Test
    fun `success resets the counter`() {
        givenSlots()
        repeat(3) { wrongPin() }

        assertInstanceOf(UnlockResult.Unlocked::class.java, service.unlock("1111".toCharArray()))

        assertEquals(AttemptsState.NONE, attempts.read())
    }

    @Test
    fun `counts the attempt before running the KDF`() {
        val crashing = UnlockService(SlotStore(storage, { _, _, _ -> error("killed") }, keyWrapper), attempts, clock)

        assertThrows<IllegalStateException> { crashing.unlock("0000".toCharArray()) }

        assertEquals(1, attempts.read().failedAttempts)
    }

    @Test
    fun `wipes the PIN keys derived for both slots`() {
        givenSlots()
        kdf.derivedKeys.clear()

        service.unlock("1111".toCharArray())

        assertEquals(2, kdf.derivedKeys.size)
        kdf.derivedKeys.forEach { key -> assertArrayEquals(ByteArray(KdfParams.KEY_BYTES), key) }
    }
}
