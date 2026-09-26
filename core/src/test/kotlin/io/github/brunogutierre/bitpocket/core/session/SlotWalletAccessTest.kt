package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.Argon2idKdf
import io.github.brunogutierre.bitpocket.core.crypto.FakeKeyWrapper
import io.github.brunogutierre.bitpocket.core.crypto.FakeUnlockClock
import io.github.brunogutierre.bitpocket.core.crypto.InMemoryAttemptsStore
import io.github.brunogutierre.bitpocket.core.crypto.InMemorySlotStorage
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.crypto.KeyWrapper
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.TEST_KDF_PARAMS
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SlotWalletAccessTest {
    private val storage = InMemorySlotStorage()
    private val session = Session()
    private val entropy = ByteArray(16) { 3 }

    private fun access(keyWrapper: KeyWrapper = FakeKeyWrapper()): SlotWalletAccess {
        val slots = SlotStore(storage, Argon2idKdf, keyWrapper, TEST_KDF_PARAMS)
        return SlotWalletAccess(slots, UnlockService(slots, InMemoryAttemptsStore(), FakeUnlockClock()), session)
    }

    @Test
    fun `creating saves slot A without a biometric copy and opens the session`() {
        val access = access()

        access.create("123456".toCharArray(), entropy, SupportedNetwork.TESTNET4)

        assertTrue(SlotId.A in storage.files && SlotId.B in storage.files)
        val wallet = session.wallet!!
        assertEquals(SlotId.A, wallet.slot)
        assertArrayEquals(entropy, wallet.payload.seedEntropy)
        assertNull(wallet.payload.biometricSeed)
        assertEquals(SupportedNetwork.TESTNET4, wallet.payload.network)
    }

    @Test
    fun `locking wipes the seed and unlocking restores it`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        val unlockedSeed = session.wallet!!.payload.seedEntropy

        session.lock()

        assertFalse(session.isUnlocked)
        assertArrayEquals(ByteArray(16), unlockedSeed)
        assertEquals(UnlockOutcome.WrongPin(Duration.ZERO), access.unlock("000000".toCharArray()))
        assertEquals(UnlockOutcome.Unlocked, access.unlock("123456".toCharArray()))
        assertArrayEquals(entropy, session.wallet!!.payload.seedEntropy)
    }

    @Test
    fun `reports lockouts and unusable device keys`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        repeat(4) { access.unlock("000000".toCharArray()) }

        assertEquals(UnlockOutcome.WrongPin(Duration.ofSeconds(30)), access.unlock("000000".toCharArray()))
        assertEquals(UnlockOutcome.LockedOut(Duration.ofSeconds(30)), access.unlock("123456".toCharArray()))

        val broken =
            object : KeyWrapper {
                override fun wrap(plaintext: ByteArray) = throw KeyUnavailableException("device locked")

                override fun unwrap(wrapped: ByteArray) = throw KeyUnavailableException("device locked")
            }
        assertEquals(UnlockOutcome.Failed, access(broken).unlock("123456".toCharArray()))
    }
}
