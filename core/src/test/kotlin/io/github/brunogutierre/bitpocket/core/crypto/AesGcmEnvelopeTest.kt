package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AesGcmEnvelopeTest {
    private val key = ByteArray(32) { 7 }
    private val plaintext = "seed material".toByteArray()
    private val aad = "slot-header".toByteArray()

    @Test
    fun `matches a Wycheproof AES-256-GCM vector`() {
        // Wycheproof aes_gcm_test.json, tcId 100:
        // https://github.com/C2SP/wycheproof/blob/main/testvectors_v1/aes_gcm_test.json
        val key = "b279f57e19c8f53f2f963f5f2519fdb7c1779be2ca2b3ae8e1128b7d6c627fc4".hex()
        val nonce = "98bc2c7438d5cd7665d76f6e".hex()
        val msg = "fcc515b294408c8645c9183e3f4ecee5127846d1".hex()
        val expected = "eb5500e3825952866d911253f8de860c00831c81" + "ecb660e1fb0541ec41e8d68a64141b3a"

        val sealed = AesGcm.encrypt(key, nonce, msg, aad = "c0".hex())

        assertEquals(expected, sealed.hex())
        assertArrayEquals(msg, AesGcm.decrypt(key, nonce, sealed, aad = "c0".hex()))
    }

    @Test
    fun `round-trips with a versioned layout and random nonces`() {
        val first = AesGcmEnvelope.seal(key, plaintext, aad)
        val second = AesGcmEnvelope.seal(key, plaintext, aad)

        assertEquals(AesGcmEnvelope.VERSION, first[0])
        assertEquals(AesGcmEnvelope.sealedSize(plaintext.size), first.size)
        assertFalse(first.contentEquals(second), "nonce must be random")
        assertArrayEquals(plaintext, AesGcmEnvelope.open(key, first, aad))
    }

    @Test
    fun `any flipped byte is detected`() {
        val sealed = AesGcmEnvelope.seal(key, plaintext, aad)

        for (index in sealed.indices) {
            val tampered = sealed.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertThrows<AuthenticationFailedException>("byte $index") { AesGcmEnvelope.open(key, tampered, aad) }
        }
    }

    @Test
    fun `wrong key, wrong AAD or truncated data fail`() {
        val sealed = AesGcmEnvelope.seal(key, plaintext, aad)

        assertThrows<AuthenticationFailedException> { AesGcmEnvelope.open(ByteArray(32), sealed, aad) }
        assertThrows<AuthenticationFailedException> { AesGcmEnvelope.open(key, sealed, "other".toByteArray()) }
        assertThrows<AuthenticationFailedException> { AesGcmEnvelope.open(key, sealed.copyOf(10), aad) }
    }
}
