package io.github.brunogutierre.bitpocket.core.seed

import io.github.brunogutierre.bitpocket.core.crypto.Argon2idKdf
import io.github.brunogutierre.bitpocket.core.crypto.FakeKeyWrapper
import io.github.brunogutierre.bitpocket.core.crypto.InMemorySlotStorage
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.TEST_KDF_PARAMS
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BiometricRewrapTest {
    private val slots = SlotStore(InMemorySlotStorage(), Argon2idKdf, FakeKeyWrapper(), TEST_KDF_PARAMS)
    private val vault = FakeBiometricSeedVault()
    private val rewrap = BiometricRewrap(slots, vault)
    private val pin = "1111".toCharArray()
    private val entropy = ByteArray(16) { 5 }
    private val invalidated = SlotPayload(entropy, biometricSeed = byteArrayOf(1, 2, 3), SupportedNetwork.TESTNET4)

    private fun savedPayload(slot: SlotId): SlotPayload {
        val file = slots.read(slot)!!
        return SlotPayload.decode(slots.openPayload(slot, file, slots.openDek(slot, file, pin)!!))
    }

    @Test
    fun `resets the key, re-seals the PIN-fallback seed and saves the slot`() =
        runTest {
            val updated = rewrap.rewrap(SlotId.B, pin, invalidated)

            assertEquals(listOf("reset", "seal"), vault.calls)
            assertArrayEquals(vault.sealed(entropy), updated.biometricSeed)
            val saved = savedPayload(SlotId.B)
            assertArrayEquals(entropy, saved.seedEntropy)
            assertArrayEquals(updated.biometricSeed, saved.biometricSeed)
            assertEquals(SupportedNetwork.TESTNET4, saved.network)
        }

    @Test
    fun `a cancelled prompt drops the unreadable biometric copy and keeps the PIN copy`() =
        runTest {
            vault.failSeal = true

            assertThrows<IllegalStateException> { rewrap.rewrap(SlotId.A, pin, invalidated) }

            val saved = savedPayload(SlotId.A)
            assertNull(saved.biometricSeed)
            assertArrayEquals(entropy, saved.seedEntropy)
        }
}

private class FakeBiometricSeedVault : BiometricSeedVault {
    val calls = mutableListOf<String>()
    var failSeal = false

    fun sealed(seed: ByteArray) = byteArrayOf(0x42) + seed

    override fun resetKey() {
        calls += "reset"
    }

    override suspend fun seal(seed: ByteArray): ByteArray {
        calls += "seal"
        check(!failSeal) { "prompt cancelled" }
        return sealed(seed)
    }
}
