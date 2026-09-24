package io.github.brunogutierre.bitpocket.core.seed

import io.github.brunogutierre.bitpocket.core.crypto.SlotFile
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class SlotPayloadTest {
    private val entropy = ByteArray(32) { it.toByte() }

    @ParameterizedTest
    @EnumSource(SupportedNetwork::class)
    fun `round-trips with and without the biometric copy`(network: SupportedNetwork) {
        val withBlob = SlotPayload(entropy, ByteArray(61) { 9 }, network)

        val decoded = SlotPayload.decode(withBlob.encode())
        val withoutBlob = SlotPayload.decode(decoded.withBiometricSeed(null).encode())

        assertArrayEquals(entropy, decoded.seedEntropy)
        assertArrayEquals(ByteArray(61) { 9 }, decoded.biometricSeed)
        assertEquals(network, decoded.network)
        assertNull(withoutBlob.biometricSeed)
        assertTrue(withBlob.encode().size <= SlotFile.MAX_PAYLOAD_BYTES)
    }

    @Test
    fun `uses stable network ids`() {
        assertEquals(1, SlotPayload(ByteArray(16), null, SupportedNetwork.SIGNET).encode()[1].toInt())
        assertEquals(2, SlotPayload(ByteArray(16), null, SupportedNetwork.TESTNET4).encode()[1].toInt())
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 15, 17, 33])
    fun `rejects entropy that is not a BIP39 size`(size: Int) {
        assertThrows<IllegalArgumentException> { SlotPayload(ByteArray(size), null, SupportedNetwork.SIGNET) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["version", "network", "truncated", "trailing"])
    fun `rejects malformed payloads`(corruption: String) {
        val valid = SlotPayload(entropy, null, SupportedNetwork.SIGNET).encode()
        val corrupted =
            when (corruption) {
                "version" -> valid.copyOf().also { it[0] = 9 }
                "network" -> valid.copyOf().also { it[1] = 99 }
                "truncated" -> valid.copyOf(valid.size - 1)
                else -> valid + 0
            }

        assertThrows<IllegalArgumentException> { SlotPayload.decode(corrupted) }
    }
}
